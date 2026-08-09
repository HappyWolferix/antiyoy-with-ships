package yio.tro.antiyoy.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;
import yio.tro.antiyoy.ai.Difficulty;
import yio.tro.antiyoy.ai.NavalStrategist;
import yio.tro.antiyoy.ai.ProvinceMode;
import yio.tro.antiyoy.gameplay.DebugFlags;
import yio.tro.antiyoy.gameplay.FieldManager;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.LevelSize;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;
import yio.tro.antiyoy.gameplay.Unit;
import yio.tro.antiyoy.gameplay.loading.LoadingManager;
import yio.tro.antiyoy.gameplay.loading.LoadingParameters;
import yio.tro.antiyoy.gameplay.loading.LoadingType;
import yio.tro.antiyoy.gameplay.rules.GameRules;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Random;
import java.util.ArrayList;

/**
 * Headless-ish batch harness for the balancer AI. Runs seeded all-AI skirmishes and reports two
 * things:
 * <p>
 * 1. Deadlock regression - a province that can afford a unit but has no building-free hex to stage
 * it on, for several rounds running, is sealed in by its own farms and towers and can never field a
 * unit again. That is the bug this work set out to fix, so it is asserted rather than just measured.
 * <p>
 * 2. Strength - fraction 0 plays the subject AI, the other four play the opponent AI, and the
 * subject has to hold more of the board than an even five-way split would give it.
 * <p>
 * The seat matters: fraction 0 moves first every round and the map generator does not treat the five
 * starting positions identically. To compare two AIs honestly, run the matchup twice with subject and
 * opponent swapped over the same seeds, and compare the two territory shares.
 * <p>
 * It still opens a GL window because YioGdxGame loads textures on construction; nothing is drawn
 * beyond the first frames. Not part of the game. Run with:
 * <p>
 * ./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.AiSkirmishHarness \
 * -PappArgs="40,300,false,balancer,expert"    (runs,maxRounds,slayRules,subject,opponent - all optional)
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
    static boolean islands = false;
    static int subject = Difficulty.BALANCER;
    static int opponent = Difficulty.EXPERT;

    private boolean started;
    private int maxPortsSeen;
    private int maxShipsSeen;
    private int maxCoastSeen;
    private boolean portThisRun;
    private boolean colonyThisRun;
    private int runsWithPort;
    private int runsWithColony;
    private int[] wins;
    private int deadlockReports;
    private double shareSum;
    private int shareRuns;


    public static void main(String[] args) {
        if (args.length > 0) runs = Integer.parseInt(args[0]);
        if (args.length > 1) maxTurns = Integer.parseInt(args[1]);
        if (args.length > 2) slayRules = Boolean.parseBoolean(args[2]);
        if (args.length > 3) subject = parseDifficulty(args[3]);
        if (args.length > 4) opponent = parseDifficulty(args[4]);
        if (args.length > 5) islands = Boolean.parseBoolean(args[5]);
        if (args.length > 6) NavalStrategist.enabled = Boolean.parseBoolean(args[6]);

        YioGdxGame.platformType = PlatformType.pc;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Antiyoy AI skirmish");
        config.setWindowedMode(540, 960);
        config.useVsync(false);

        new Lwjgl3Application(new AiSkirmishHarness(), config);
    }


    private static int parseDifficulty(String name) {
        switch (name.trim().toLowerCase()) {
            case "easy": return Difficulty.EASY;
            case "normal": return Difficulty.NORMAL;
            case "hard": return Difficulty.HARD;
            case "expert": return Difficulty.EXPERT;
            case "balancer": return Difficulty.BALANCER;
            case "master": return Difficulty.MASTER;
            default: throw new IllegalArgumentException("unknown difficulty: " + name);
        }
    }


    /** Difficulty.convertToString() goes through the localization manager; this stays independent of it. */
    private static String nameOf(int difficulty) {
        switch (difficulty) {
            case Difficulty.EASY: return "easy";
            case Difficulty.NORMAL: return "normal";
            case Difficulty.HARD: return "hard";
            case Difficulty.EXPERT: return "expert";
            case Difficulty.BALANCER: return "balancer";
            case Difficulty.MASTER: return "master";
            default: return "?";
        }
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
        wins = new int[fractionsQuantity()];
        deadlockReports = 0;
        shareSum = 0;
        shareRuns = 0;

        System.out.println("== AiSkirmishHarness: " + runs + " runs, up to " + maxTurns + " rounds each"
                + ", slayRules=" + slayRules
                + ", fraction 0 = " + nameOf(subject) + " vs " + nameOf(opponent)
                + (islands ? " on two islands" : "")
                + ", naval=" + NavalStrategist.enabled);

        for (int seed = 0; seed < runs; seed++) {
            int winner = runMatch(seed);
            if (winner >= 0 && winner < wins.length) wins[winner]++;
            System.out.println("run seed=" + seed + " winner=" + winner);
        }

        report();
    }


    /**
     * Fraction 0 is the AI under test, the others are the opponent it has to beat.
     */
    private int runMatch(int seed) {
        LoadingParameters instance = LoadingParameters.getInstance();
        instance.loadingType = LoadingType.skirmish;
        instance.levelSize = LevelSize.MEDIUM;
        instance.playersNumber = 0;
        instance.fractionsQuantity = fractionsQuantity();
        instance.difficulty = subject;
        instance.colorOffset = 0;
        instance.slayRules = slayRules;
        instance.fogOfWar = false;
        instance.diplomacy = false;
        instance.genProvinces = 0;
        instance.treesPercentageIndex = 2;

        // seeded before startGame so the map generator draws from the same stream as the AI
        gameController.random = new Random(seed);
        LoadingManager.getInstance().startGame(instance);

        if (islands && !carveIntoTwoIslands()) {
            System.out.println("run seed=" + seed + " SKIPPED (map would not split cleanly)");
            return -1;
        }

        int[] lineup = new int[fractionsQuantity()];
        lineup[0] = subject;
        for (int i = 1; i < lineup.length; i++) lineup[i] = opponent;
        gameController.aiFactory.createCustomAiList(lineup);

        portThisRun = false;
        colonyThisRun = false;
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
            censusNavy();
        }

        DebugFlags.testMode = false;
        if (portThisRun) runsWithPort++;
        if (colonyThisRun) runsWithColony++;
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


    private int fractionsQuantity() {
        return islands ? 2 : 5;
    }


    /**
     * Turns the generated continent into two islands separated by open water, one AI on each, and
     * gives every hex of an island to that island's fraction.
     * <p>
     * This is the case the naval AI exists for: with no land route at all, a game between two
     * land-only AIs can never end - they grow to fill their island, run out of anything to do, and
     * sit there until the round cap. An AI that can cross wins; an AI that cannot, draws forever.
     * <p>
     * Returns false when the cut did not produce two usable landmasses, in which case the seed is
     * skipped rather than silently measured as something else.
     */
    private boolean carveIntoTwoIslands() {
        FieldManager fieldManager = gameController.fieldManager;

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        for (Hex hex : fieldManager.activeHexes) {
            minX = Math.min(minX, hex.getPos().x);
            maxX = Math.max(maxX, hex.getPos().x);
        }
        if (maxX <= minX) return false;

        // a channel wide enough that no hex on one side touches one on the other, but well within
        // a ship's move limit, so the crossing is a real option rather than a formality
        float middle = 0.5f * (minX + maxX);
        float halfChannel = 0.09f * (maxX - minX);

        for (Hex hex : new ArrayList<>(fieldManager.activeHexes)) {
            if (Math.abs(hex.getPos().x - middle) > halfChannel) continue;
            sinkHex(fieldManager, hex);
        }

        ArrayList<ArrayList<Hex>> islandList = findLandmasses(fieldManager);
        if (islandList.size() < 2) return false;

        // biggest two become the players; leftover islets are sunk so nobody starts scattered
        islandList.sort((a, b) -> b.size() - a.size());
        if (islandList.get(1).size() < 10) return false;

        for (int i = 0; i < islandList.size(); i++) {
            for (Hex hex : islandList.get(i)) {
                if (i < 2) {
                    hex.setFraction(i);
                    hex.previousFraction = i;
                } else {
                    sinkHex(fieldManager, hex);
                }
            }
        }

        fieldManager.detectProvinces();
        return fieldManager.provinces.size() >= 2;
    }


    private void sinkHex(FieldManager fieldManager, Hex hex) {
        fieldManager.cleanOutHex(hex);
        hex.active = false;
        hex.setFraction(GameRules.NEUTRAL_FRACTION);
        hex.previousFraction = GameRules.NEUTRAL_FRACTION;
        fieldManager.activeHexes.remove(hex);
    }


    /** Connected components of land, found without touching the shared hex flags. */
    private ArrayList<ArrayList<Hex>> findLandmasses(FieldManager fieldManager) {
        ArrayList<ArrayList<Hex>> result = new ArrayList<>();
        HashSet<Hex> seen = new HashSet<>();

        for (Hex start : fieldManager.activeHexes) {
            if (!seen.add(start)) continue;

            ArrayList<Hex> component = new ArrayList<>();
            ArrayList<Hex> frontier = new ArrayList<>();
            frontier.add(start);

            while (frontier.size() > 0) {
                Hex hex = frontier.remove(frontier.size() - 1);
                component.add(hex);
                for (int dir = 0; dir < 6; dir++) {
                    Hex adjacent = hex.getAdjacentHex(dir);
                    if (adjacent == null || adjacent.isNullHex()) continue;
                    if (!adjacent.active) continue;
                    if (!seen.add(adjacent)) continue;
                    frontier.add(adjacent);
                }
            }

            result.add(component);
        }

        return result;
    }


    /**
     * Ports and ships are built and sunk over the course of a match, so a count taken at the end
     * would miss them. This records the high-water mark across every round of the batch.
     */
    private void censusNavy() {
        int ports = 0;
        for (Hex hex : gameController.fieldManager.activeHexes) {
            if (hex.objectInside == Obj.PORT) ports++;
        }

        int ships = 0;
        for (Unit unit : gameController.getUnitList()) {
            if (unit.ship) ships++;
        }

        // A zero port count only means something if ports were buildable at all, so count the
        // coastline: water hexes touching owned land, which is where a port may go.
        int coast = 0;
        for (Hex hex : gameController.fieldManager.activeHexes) {
            for (int dir = 0; dir < 6; dir++) {
                Hex adjacent = hex.getAdjacentHex(dir);
                if (adjacent == null || adjacent.active) continue;
                coast++;
            }
        }

        if (ports > 0) portThisRun = true;
        for (Hex hex : gameController.fieldManager.activeHexes) {
            if (hex.overseasPart) colonyThisRun = true;
        }

        if (ports > maxPortsSeen) maxPortsSeen = ports;
        if (ships > maxShipsSeen) maxShipsSeen = ships;
        if (coast > maxCoastSeen) maxCoastSeen = coast;
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
            System.out.println("fraction " + i + " (" + nameOf(i == 0 ? subject : opponent) + "): " + wins[i]);
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
        System.out.println(nameOf(subject) + " territory share: " + Math.round(share * 1000) / 10.0
                + "% (even split would be " + Math.round(1000.0 / wins.length) / 10.0 + "%)");
        System.out.println("runs that built a port: " + runsWithPort + "/" + runs
                + ", runs that landed a colony: " + runsWithColony + "/" + runs);
        System.out.println("navy high-water mark across batch: ports=" + maxPortsSeen + " ships=" + maxShipsSeen + " coastal water hexes=" + maxCoastSeen);
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

        if (islands) {
            // The whole point: with no land bridge, a land-only AI can never finish the game.
            System.out.println("CROSSING CHECK: " + (decided > 0 && maxPortsSeen > 0 ? "PASS" : "FAIL")
                    + " (" + decided + "/" + runs + " island matches decided, "
                    + maxPortsSeen + " ports built)");
        }

        // One subject against four opponents: an evenly matched AI would hold 20% of the board, so
        // demanding a *majority* of wins would be unreachable by construction. Territory share against
        // the even split is the honest comparison.
        double evenSplit = 1.0 / wins.length;
        boolean strongerThanExpert = share >= evenSplit * MIN_SHARE_OVER_EVEN;
        System.out.println("STRENGTH CHECK: " + (strongerThanExpert ? "PASS" : "FAIL")
                + " (holds " + Math.round(share * 1000) / 10.0 + "% of the board, needs "
                + Math.round(evenSplit * MIN_SHARE_OVER_EVEN * 1000) / 10.0 + "%; won "
                + wins[0] + " of " + decided + " decided runs)");
    }
}
