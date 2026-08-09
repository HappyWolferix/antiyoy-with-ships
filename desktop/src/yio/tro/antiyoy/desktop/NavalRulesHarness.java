package yio.tro.antiyoy.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;
import yio.tro.antiyoy.ai.Difficulty;
import yio.tro.antiyoy.gameplay.DebugFlags;
import yio.tro.antiyoy.gameplay.FieldManager;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.LevelSize;
import yio.tro.antiyoy.gameplay.MoveZoneDetection;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;
import yio.tro.antiyoy.gameplay.Unit;
import yio.tro.antiyoy.gameplay.loading.LoadingManager;
import yio.tro.antiyoy.gameplay.loading.LoadingParameters;
import yio.tro.antiyoy.gameplay.loading.LoadingType;
import yio.tro.antiyoy.gameplay.rules.GameRules;

import java.util.ArrayList;
import java.util.Random;

/**
 * Scenario checks for the two naval rules that batch play exercises only by luck: what happens when
 * a harbour is attacked, and what happens to a colony whose motherland dies. Each test builds the
 * exact board state it needs on a freshly generated map and asserts the outcome, so a regression
 * shows up as a named FAIL rather than as a shifted win rate.
 * <p>
 * Not part of the game; it opens a GL window only because YioGdxGame loads textures on construction.
 * <p>
 * ./gradlew :desktop:run -PmainClass=yio.tro.antiyoy.desktop.NavalRulesHarness
 */
public class NavalRulesHarness extends YioGdxGame {

    /** How many generated maps to look through for one that fits a given scenario. */
    private static final int SETUP_ATTEMPTS = 40;

    private boolean started;
    private int failures;
    private int checks;


    public static void main(String[] args) {
        YioGdxGame.platformType = PlatformType.pc;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Antiyoy naval rules");
        config.setWindowedMode(540, 960);
        config.useVsync(false);

        new Lwjgl3Application(new NavalRulesHarness(), config);
    }


    @Override
    public void render() {
        super.render();

        if (started || gameController == null) return;
        started = true;

        try {
            testRaidedPortSinksAndShipStaysAfloat();
            testPortCannotBeBuiltUnderAShip();
            testOrphanedColonyBecomesItsOwnProvince();
        } catch (Exception e) {
            e.printStackTrace();
            failures++;
        }

        System.out.println();
        System.out.println("== naval rules: " + (checks - failures) + "/" + checks + " checks passed");
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");

        Gdx.app.exit();
    }


    private void check(String what, boolean condition) {
        checks++;
        if (!condition) failures++;
        System.out.println((condition ? "  ok   " : "  FAIL ") + what);
    }


    /**
     * A ship attacking an enemy harbour must destroy it, take the tile back to open water, and stay
     * a ship floating on it. The bug this guards against is the harbour being captured like ordinary
     * land, which left a manufactured land tile in the middle of the sea.
     */
    private void testRaidedPortSinksAndShipStaysAfloat() {
        System.out.println();
        System.out.println("-- a raided port sinks, and the raider stays afloat");
        FieldManager fieldManager = gameController.fieldManager;
        Hex portHex = null;
        Hex water = null;

        // map generation draws from an unseeded random in places, so a given seed does not always
        // produce a province with usable coast - keep looking rather than reporting a false failure
        for (int seed = 1; seed <= SETUP_ATTEMPTS && water == null; seed++) {
            loadBoard(seed);
            portHex = buildPortSomewhere(provinceWithFraction(0));
            if (portHex == null) continue;
            water = openWaterBeside(portHex);
        }

        if (water == null) {
            check("could set up a port and a raider beside it", false);
            return;
        }

        int activeBefore = fieldManager.activeHexes.size();

        // a strength-3 raider of another fraction, afloat on the water next to the harbour
        water.fraction = 1;
        Unit raider = fieldManager.addUnitWithoutCrushingObject(water, 3);
        raider.ship = true;
        raider.originHex = water;
        raider.setReadyToMove(true);

        gameController.moveUnit(raider, portHex, null);

        check("the harbour is gone", portHex.objectInside != Obj.PORT);
        check("the tile is water again", !portHex.active);
        check("the tile left the active list", fieldManager.activeHexes.size() == activeBefore - 1);
        check("the tile belongs to no province", fieldManager.getProvinceByHex(portHex) == null);
        check("the raider is still a ship", raider.ship);
        check("the raider floats where the harbour stood", raider.currentHex == portHex);
    }


    /** A harbour cannot be built on water that a ship is sitting on. */
    private void testPortCannotBeBuiltUnderAShip() {
        System.out.println();
        System.out.println("-- no harbour under a moored ship");
        FieldManager fieldManager = gameController.fieldManager;
        Province province = null;
        Hex site = null;

        for (int seed = 1; seed <= SETUP_ATTEMPTS && site == null; seed++) {
            loadBoard(seed);
            for (Province candidate : fieldManager.provinces) {
                site = portSite(candidate);
                if (site == null) continue;
                province = candidate;
                break;
            }
        }

        if (site == null) {
            check("could find a legal port site", false);
            return;
        }

        check("the site is legal while empty", MoveZoneDetection.canBuildPortOnHex(province, site));

        Unit moored = fieldManager.addUnitWithoutCrushingObject(site, 1);
        moored.ship = true;

        check("the site is refused with a ship on it", !MoveZoneDetection.canBuildPortOnHex(province, site));

        province.money = 999;
        check("building it is refused too", !fieldManager.buildPort(province, site));
    }


    /**
     * A colony whose mainland province is wiped out becomes a province in its own right - with a
     * capital and an empty treasury - instead of collapsing into rubble.
     */
    private void testOrphanedColonyBecomesItsOwnProvince() {
        System.out.println();
        System.out.println("-- an orphaned colony stands on its own");
        FieldManager fieldManager = gameController.fieldManager;
        Province mainland = null;
        ArrayList<Hex> colony = null;

        for (int seed = 1; seed <= SETUP_ATTEMPTS && colony == null; seed++) {
            loadBoard(seed);
            mainland = provinceWithFraction(0);
            if (mainland == null || mainland.hexList.size() < 3) continue;
            colony = findDetachedPair(mainland);
        }

        if (colony == null) {
            check("could set up a mainland province and a detached colony", false);
            return;
        }
        mainland.money = 250;
        for (Hex hex : colony) {
            hex.setFraction(0);
            hex.previousFraction = 0;
            hex.overseasPart = true;
        }

        // the motherland falls - and it is only dead if every mainland hex of the fraction is gone,
        // not just the biggest province, or the colony is simply adopted by whatever is left
        for (Hex hex : new ArrayList<>(fieldManager.activeHexes)) {
            if (hex.fraction != 0) continue;
            if (colony.contains(hex)) continue;
            hex.setFraction(1);
            hex.previousFraction = 1;
            hex.overseasPart = false;
        }

        fieldManager.detectProvinces();

        Province survivor = fieldManager.getProvinceByHex(colony.get(0));
        check("the colony survives as a province", survivor != null);
        if (survivor == null) return;

        check("it holds both of its hexes", survivor.hexList.size() == colony.size());
        check("it is no longer a colony", !colony.get(0).overseasPart && !colony.get(1).overseasPart);
        check("it has a capital", survivor.getCapital() != null);
        check("it inherits no money", survivor.money == 0);
        check("its land is intact", colony.get(0).active && colony.get(1).active);
    }


    private void loadBoard(int seed) {
        LoadingParameters instance = LoadingParameters.getInstance();
        instance.loadingType = LoadingType.skirmish;
        instance.levelSize = LevelSize.MEDIUM;
        instance.playersNumber = 0;
        instance.fractionsQuantity = 5;
        instance.difficulty = Difficulty.BALANCER;
        instance.colorOffset = 0;
        instance.slayRules = false;
        instance.fogOfWar = false;
        instance.diplomacy = false;
        instance.genProvinces = 0;
        instance.treesPercentageIndex = 2;

        gameController.random = new Random(seed);
        LoadingManager.getInstance().startGame(instance);
        DebugFlags.testMode = true;
        gamePaused = true;
    }


    private Province provinceWithFraction(int fraction) {
        Province best = null;
        for (Province province : gameController.fieldManager.provinces) {
            if (province.getFraction() != fraction) continue;
            if (best == null || province.hexList.size() > best.hexList.size()) best = province;
        }

        return best;
    }


    private Hex portSite(Province province) {
        for (Hex hex : province.hexList) {
            for (int dir = 0; dir < 6; dir++) {
                Hex water = hex.getAdjacentHex(dir);
                if (water == null || water.isNullHex()) continue;
                if (!MoveZoneDetection.canBuildPortOnHex(province, water)) continue;
                return water;
            }
        }

        return null;
    }


    private Hex buildPortSomewhere(Province province) {
        if (province == null) return null;

        Hex site = portSite(province);
        if (site == null) return null;

        province.money = 999;
        if (!gameController.fieldManager.buildPort(province, site)) return null;

        return site;
    }


    private Hex openWaterBeside(Hex hex) {
        for (int dir = 0; dir < 6; dir++) {
            Hex adjacent = hex.getAdjacentHex(dir);
            if (adjacent == null || adjacent.isNullHex()) continue;
            if (!adjacent.isOpenWater()) continue;
            if (adjacent.containsUnit()) continue;
            return adjacent;
        }

        return null;
    }


    /** Two adjacent active hexes that touch nothing belonging to the given province. */
    private ArrayList<Hex> findDetachedPair(Province mainland) {
        for (Hex hex : gameController.fieldManager.activeHexes) {
            if (mainland.containsHex(hex)) continue;
            if (touchesProvince(hex, mainland)) continue;

            for (int dir = 0; dir < 6; dir++) {
                Hex partner = hex.getAdjacentHex(dir);
                if (partner == null || partner.isNullHex() || !partner.active) continue;
                if (mainland.containsHex(partner)) continue;
                if (touchesProvince(partner, mainland)) continue;

                ArrayList<Hex> pair = new ArrayList<>();
                pair.add(hex);
                pair.add(partner);
                return pair;
            }
        }

        return null;
    }


    private boolean touchesProvince(Hex hex, Province province) {
        for (int dir = 0; dir < 6; dir++) {
            Hex adjacent = hex.getAdjacentHex(dir);
            if (adjacent == null || adjacent.isNullHex()) continue;
            if (province.containsHex(adjacent)) return true;
        }

        return false;
    }
}
