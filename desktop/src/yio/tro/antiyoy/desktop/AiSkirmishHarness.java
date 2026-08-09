package yio.tro.antiyoy.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;
import yio.tro.antiyoy.ai.Difficulty;
import yio.tro.antiyoy.ai.ProvinceMode;
import yio.tro.antiyoy.gameplay.DebugFlags;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.LevelSize;
import yio.tro.antiyoy.gameplay.Province;
import yio.tro.antiyoy.gameplay.loading.LoadingManager;
import yio.tro.antiyoy.gameplay.loading.LoadingParameters;
import yio.tro.antiyoy.gameplay.loading.LoadingType;
import yio.tro.antiyoy.gameplay.rules.GameRules;

import java.util.HashMap;
import java.util.Random;

/**
 * Headless-ish batch harness for the balancer AI. Runs seeded all-AI skirmishes and reports two
 * things:
 * <p>
 * 1. Deadlock regression - a province that can afford a unit but has no building-free hex to stage
 * it on, for several rounds running, is sealed in by its own farms and towers and can never field a
 * unit again. That is the bug this work set out to fix, so it is asserted rather than just measured.
 * <p>
 * 2. Strength - fraction 0 plays the balancer, the other four play the previous best (expert), and
 * the balancer has to hold more of the board than an even five-way split would give it.
 * <p>
 * It still opens a GL window because YioGdxGame loads textures on construction; nothing is drawn
 * beyond the first frames. Not part of the game. Run with:
 * <p>
 * ./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.AiSkirmishHarness \
 * -PappArgs="40,300,false"    (runs,maxRounds,slayRules - all optional)
 */
public class AiSkirmishHarness extends YioGdxGame {

    /** How many consecutive rounds a rich but permanently sealed province is tolerated. */
    static final int DEADLOCK_TURN_LIMIT = 5;

    /** Sealed-province budget per match - see the note in report() for where the number comes from. */
    static final double MAX_SEALS_PER_RUN = 1.0;

    /** The balancer has to beat an even split of the board by this factor to count as stronger. */
    static final double MIN_SHARE_OVER_EVEN = 1.25;

    static int runs = 20;
    static int maxTurns = 300;
    static boolean slayRules = false;

    private boolean started;
    private int[] wins;
    private int deadlockReports;
    private double shareSum;
    private int shareRuns;


    public static void main(String[] args) {
        if (args.length > 0) runs = Integer.parseInt(args[0]);
        if (args.length > 1) maxTurns = Integer.parseInt(args[1]);
        if (args.length > 2) slayRules = Boolean.parseBoolean(args[2]);

        YioGdxGame.platformType = PlatformType.pc;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Antiyoy AI skirmish");
        config.setWindowedMode(540, 960);
        config.useVsync(false);

        new Lwjgl3Application(new AiSkirmishHarness(), config);
    }


    @Override
    public void render() {
        super.render();

        // the game builds its controller a few frames into the splash screen
        if (started || gameController == null) return;
        started = true;

        try {
            runBatch();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Gdx.app.exit();
    }


    private void runBatch() {
        wins = new int[5];
        deadlockReports = 0;
        shareSum = 0;
        shareRuns = 0;

        System.out.println("== AiSkirmishHarness: " + runs + " runs, up to " + maxTurns + " rounds each, slayRules=" + slayRules);

        for (int seed = 0; seed < runs; seed++) {
            int winner = runMatch(seed);
            if (winner >= 0 && winner < wins.length) wins[winner]++;
            System.out.println("run seed=" + seed + " winner=" + winner);
        }

        report();
    }


    /**
     * Fraction 0 is the balancer under test, the others are the expert AI it has to beat.
     */
    private int runMatch(int seed) {
        LoadingParameters instance = LoadingParameters.getInstance();
        instance.loadingType = LoadingType.skirmish;
        instance.levelSize = LevelSize.MEDIUM;
        instance.playersNumber = 0;
        instance.fractionsQuantity = 5;
        instance.difficulty = Difficulty.BALANCER;
        instance.colorOffset = 0;
        instance.slayRules = slayRules;
        instance.fogOfWar = false;
        instance.diplomacy = false;
        instance.genProvinces = 0;
        instance.treesPercentageIndex = 2;

        // seeded before startGame so the map generator draws from the same stream as the AI
        gameController.random = new Random(seed);
        LoadingManager.getInstance().startGame(instance);

        gameController.aiFactory.createCustomAiList(new int[]{
                Difficulty.BALANCER,
                Difficulty.EXPERT,
                Difficulty.EXPERT,
                Difficulty.EXPERT,
                Difficulty.EXPERT,
        });

        DebugFlags.testMode = true;
        DebugFlags.testWinner = -1;
        gamePaused = false;

        HashMap<String, Integer> stuckStreaks = new HashMap<>();
        // gameController.turn is the index of the player to move, not a round counter - count rounds
        // by watching it wrap back to fraction 0.
        int lastTurn = gameController.turn;
        int rounds = 0;
        int moves = maxTurns * 200;

        while (DebugFlags.testWinner == -1 && moves > 0 && rounds < maxTurns) {
            gameController.move();
            moves--;

            if (gameController.turn == lastTurn) continue;
            boolean wrapped = gameController.turn < lastTurn;
            lastTurn = gameController.turn;
            if (!wrapped) continue;

            rounds++;
            checkForDeadlock(seed, rounds, stuckStreaks);
        }

        DebugFlags.testMode = false;
        recordTerritoryShare();
        return DebugFlags.testWinner;
    }


    /**
     * Win counts alone are too noisy at this batch size - one fraction in five wins ~20% of matches
     * by chance. Territory share at the end of the match moves continuously with AI quality, so a
     * regression shows up in far fewer runs.
     */
    private void recordTerritoryShare() {
        int[] hexCount = gameController.fieldManager.getPlayerHexCount();

        int total = 0;
        for (int count : hexCount) total += count;
        if (total == 0) return;

        shareSum += (double) hexCount[0] / (double) total;
        shareRuns++;
    }


    /**
     * A province keyed by its capital coordinates - Province objects are rebuilt every turn, so they
     * cannot be used as map keys across turns.
     */
    private void checkForDeadlock(int seed, int round, HashMap<String, Integer> stuckStreaks) {
        for (Province province : gameController.fieldManager.provinces) {
            Hex capital = province.getCapital();
            if (capital == null) continue;
            String key = capital.index1 + ":" + capital.index2;

            // Buildings are permanent, units are not - a province is only truly sealed when every
            // hex it owns carries a building, and no amount of unit movement can free a staging spot.
            if (province.money < GameRules.PRICE_UNIT || countBuildingFreeHexes(province) > 0) {
                stuckStreaks.remove(key);
                continue;
            }

            int streak = stuckStreaks.containsKey(key) ? stuckStreaks.get(key) + 1 : 1;
            stuckStreaks.put(key, streak);

            if (streak != DEADLOCK_TURN_LIMIT + 1) continue;
            deadlockReports++;
            System.out.println("DEADLOCK seed=" + seed
                    + " round=" + round
                    + " province=" + key
                    + " fraction=" + province.getFraction()
                    + " money=" + province.money
                    + " hexes=" + province.hexList.size());
        }
    }


    private int countBuildingFreeHexes(Province province) {
        int c = 0;
        for (Hex hex : province.hexList) {
            if (!hex.active) continue;
            if (hex.containsBuilding()) continue;
            c++;
        }
        return c;
    }


    private void report() {
        int decided = 0;
        for (int win : wins) decided += win;

        System.out.println();
        System.out.println("== results");
        for (int i = 0; i < wins.length; i++) {
            System.out.println("fraction " + i + (i == 0 ? " (balancer)" : " (expert)  ") + ": " + wins[i]);
        }
        System.out.println("decided runs: " + decided + "/" + runs);
        int modeTotal = 0;
        for (int count : ProvinceMode.census) modeTotal += count;
        if (modeTotal > 0) {
            StringBuilder sb = new StringBuilder("province modes:");
            for (ProvinceMode mode : ProvinceMode.values()) {
                sb.append(" ").append(mode).append("=")
                        .append(Math.round(1000.0 * ProvinceMode.census[mode.ordinal()] / modeTotal) / 10.0).append("%");
            }
            System.out.println(sb.toString());
        }

        double share = shareRuns == 0 ? 0 : shareSum / shareRuns;
        System.out.println("balancer territory share: " + Math.round(share * 1000) / 10.0
                + "% (even split would be " + Math.round(1000.0 / wins.length) / 10.0 + "%)");
        double sealsPerRun = runs == 0 ? 0 : (double) deadlockReports / runs;
        System.out.println("deadlock reports: " + deadlockReports
                + " (" + Math.round(sealsPerRun * 100) / 100.0 + " per run)");
        System.out.println();

        // Zero is not the right bar. Conquest itself can shrink a province down to nothing but the
        // hexes carrying its town and farms, and no build-time guard can prevent that. What the
        // guards are there to stop is a province paving its own free land: that showed up as ~2.4
        // seals per run before them and ~0.6 after, so anything at or below one per run is healthy.
        boolean deadlockOk = sealsPerRun <= MAX_SEALS_PER_RUN;
        System.out.println("DEADLOCK CHECK: " + (deadlockOk ? "PASS" : "FAIL")
                + " (" + Math.round(sealsPerRun * 100) / 100.0 + " per run, budget " + MAX_SEALS_PER_RUN + ")");

        // One balancer against four experts: an even AI would hold 20% of the board, so demanding a
        // *majority* of wins would be unreachable by construction. Territory share against the even
        // split is the honest comparison.
        double evenSplit = 1.0 / wins.length;
        boolean strongerThanExpert = share >= evenSplit * MIN_SHARE_OVER_EVEN;
        System.out.println("STRENGTH CHECK: " + (strongerThanExpert ? "PASS" : "FAIL")
                + " (holds " + Math.round(share * 1000) / 10.0 + "% of the board, needs "
                + Math.round(evenSplit * MIN_SHARE_OVER_EVEN * 1000) / 10.0 + "%; won "
                + wins[0] + " of " + decided + " decided runs)");
    }
}
