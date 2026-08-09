package yio.tro.antiyoy.ai;

import yio.tro.antiyoy.gameplay.GameController;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Environment assessment shared by both balancer AIs. They have no common balancer ancestor
 * (one extends the generic expert, the other the slay expert), so each owns an instance of this.
 * <p>
 * Modes are computed once per province per turn and cached. Province objects are rebuilt between
 * turns, so the cache must be cleared at the start of every move - never let it live across turns.
 */
class BalancerBrain {

    /** Below this share of frontline hexes facing fortified enemies a province still dares to attack. */
    public static final double FORTIFIED_FRONTIER_LIMIT = 0.5;

    /** A province needs this fraction of its size in free neighbouring land to go expanding. */
    public static final int NEUTRAL_LAND_DIVISOR = 8;

    /**
     * A neighbour weaker than this is not worth turtling against - otherwise a province with no army
     * yet (ownStrength 0) would turtle against a single peasant and hoard instead of playing.
     */
    public static final int MIN_TURTLE_THREAT = 4;

    private final GameController gameController;
    private final HashMap<Province, ProvinceMode> modeCache;
    private final ArrayList<Province> nearbyProvinces;
    private final ArrayList<Hex> flaggedHexes;


    BalancerBrain(GameController gameController) {
        this.gameController = gameController;
        modeCache = new HashMap<>();
        nearbyProvinces = new ArrayList<>();
        flaggedHexes = new ArrayList<>();
    }


    /** Must be called at the top of makeMove(): province objects from the last turn are stale. */
    void onMoveStarted() {
        modeCache.clear();
    }


    ProvinceMode getMode(Province province) {
        ProvinceMode cached = modeCache.get(province);
        if (cached != null) return cached;

        ProvinceMode mode = assessProvinceMode(province);
        mode.countOnce();
        modeCache.put(province, mode);
        return mode;
    }


    ProvinceMode assessProvinceMode(Province province) {
        int neutral = countAdjacentNeutralHexes(province);
        if (neutral >= Math.max(2, province.hexList.size() / NEUTRAL_LAND_DIVISOR)) return ProvinceMode.EXPAND;

        int ownStrength = getArmyStrength(province) + 2 * countObjects(province, Obj.TOWER);
        // Strongest single neighbour, not the sum of them: a province typically borders three or
        // four others, so summing compares one province against the whole board and reads as mortal
        // danger practically always. Provinces attack and get attacked one neighbour at a time.
        int enemyStrength = strongestNearbyEnemyStrength(province);
        if (enemyStrength >= MIN_TURTLE_THREAT) {
            if (enemyStrength > 3 * ownStrength / 2) return ProvinceMode.TURTLE;
            if (fortifiedFrontierRatio(province) > FORTIFIED_FRONTIER_LIMIT) return ProvinceMode.TURTLE;
        }

        return ProvinceMode.ECONOMY;
    }


    /**
     * Free land hexes touching the province. Water is inactive, so land adjacency falls out for free
     * and no ship logic is involved.
     */
    int countAdjacentNeutralHexes(Province province) {
        flaggedHexes.clear();

        for (Hex hex : province.hexList) {
            for (int i = 0; i < 6; i++) {
                Hex adjHex = hex.getAdjacentHex(i);
                if (!adjHex.active) continue;
                if (!adjHex.isNeutral()) continue;
                if (adjHex.flag) continue;
                adjHex.flag = true;
                flaggedHexes.add(adjHex);
            }
        }

        int count = flaggedHexes.size();
        for (Hex hex : flaggedHexes) {
            hex.flag = false;
        }
        flaggedHexes.clear();

        return count;
    }


    int strongestNearbyEnemyStrength(Province province) {
        updateNearbyProvinces(province);

        int max = 0;
        for (Province enemy : nearbyProvinces) {
            int strength = getArmyStrength(enemy)
                    + 2 * countObjects(enemy, Obj.TOWER)
                    + 3 * countObjects(enemy, Obj.STRONG_TOWER);
            if (strength > max) max = strength;
        }
        return max;
    }


    /**
     * Share of this province's frontline that looks into defended enemy hexes. A high ratio means
     * attacking would just feed units into towers.
     */
    double fortifiedFrontierRatio(Province province) {
        int frontline = 0;
        int fortified = 0;

        for (Hex hex : province.hexList) {
            if (!hex.active) continue;

            boolean isFrontline = false;
            boolean facesDefense = false;
            for (int i = 0; i < 6; i++) {
                Hex adjHex = hex.getAdjacentHex(i);
                if (!adjHex.active) continue;
                if (adjHex.sameFraction(hex)) continue;
                if (adjHex.isNeutral()) continue;

                isFrontline = true;
                if (adjHex.getDefenseNumber() > 0) facesDefense = true;
            }

            if (!isFrontline) continue;
            frontline++;
            if (facesDefense) fortified++;
        }

        if (frontline == 0) return 0;
        return (double) fortified / (double) frontline;
    }


    int getArmyStrength(Province province) {
        int sum = 0;
        for (Hex hex : province.hexList) {
            if (!hex.containsUnit()) continue;
            sum += hex.unit.strength;
        }
        return sum;
    }


    private int countObjects(Province province, int objectType) {
        int c = 0;
        for (Hex hex : province.hexList) {
            if (hex.objectInside == objectType) c++;
        }
        return c;
    }


    /**
     * Own list rather than the AI's nearbyProvinces field - the AI iterates over that one while
     * spending money, and clobbering it mid-loop would be a nasty aliasing bug.
     */
    private void updateNearbyProvinces(Province srcProvince) {
        nearbyProvinces.clear();

        for (Hex hex : srcProvince.hexList) {
            for (int i = 0; i < 6; i++) {
                Hex adjHex = hex.getAdjacentHex(i);
                if (!adjHex.active) continue;
                if (adjHex.isNeutral()) continue;
                if (adjHex.sameFraction(hex)) continue;

                Province province = gameController.fieldManager.getProvinceByHex(adjHex);
                if (province == null) continue;
                if (nearbyProvinces.contains(province)) continue;
                nearbyProvinces.add(province);
            }
        }
    }
}
