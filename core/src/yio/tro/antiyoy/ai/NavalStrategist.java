package yio.tro.antiyoy.ai;

import yio.tro.antiyoy.gameplay.FieldManager;
import yio.tro.antiyoy.gameplay.GameController;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.MoveZoneDetection;
import yio.tro.antiyoy.gameplay.Obj;
import yio.tro.antiyoy.gameplay.Province;
import yio.tro.antiyoy.gameplay.Unit;
import yio.tro.antiyoy.gameplay.rules.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The naval half of an AI turn: build ports, launch ships from them, and sail those ships to land
 * somewhere worth landing. Written once and shared by the master and balancer AIs, which have
 * nothing else in common structurally - all it needs is a fraction and the game controller.
 * <p>
 * Ships are invisible to every land routine in the AI, and deliberately so: a ship at sea stands on
 * an inactive water hex, which belongs to no province, so the province-walking enumerations never
 * see it. That is why this class enumerates ships straight off the unit list, and why the land
 * passes filter with {@link #isLandUnit} - otherwise they would try to march a docked ship inland
 * and turn it back into a peasant.
 * <p>
 * Two things motivate a crossing, matching how a human plays the naval layer: neutral shore worth
 * colonising, and the enemy's economic centres - capitals and farms - which are usually held far
 * from the front line and left thinly defended precisely because they are unreachable by land.
 */
public class NavalStrategist {

    /**
     * Off switch for measurement: with this false the AIs behave exactly as they did before the
     * naval layer existed, which is what the harness compares against. Not used by the game.
     */
    public static boolean enabled = true;

    /**
     * How much profit a province keeps back for itself rather than committing to upkeep. Ports and
     * ships are bought until the province's remaining profit drops below this, so a rich province
     * fields a real invasion fleet and a poor one fields nothing. There is no fixed ceiling: the
     * port price doubles with each port built, which caps the harbour count on its own.
     */
    private static final int PROFIT_RESERVE = GameRules.TAX_SHIP;

    private final GameController gameController;
    private final FieldManager fieldManager;
    private final int fraction;


    public NavalStrategist(GameController gameController, int fraction) {
        this.gameController = gameController;
        this.fieldManager = gameController.fieldManager;
        this.fraction = fraction;
    }


    /**
     * Land routines must skip ships. A ship sitting in its home port is on a province hex and would
     * otherwise be picked up as an ordinary garrison unit and marched off inland, which silently
     * scraps it - stepping onto land clears the ship flag.
     */
    public static boolean isLandUnit(Unit unit) {
        return unit != null && !unit.ship;
    }


    /**
     * The province-scoped half: spend money on a port, and put a marine in it. Call once per owned
     * province, after the land spending has had its pick of the money.
     */
    public void performForProvince(Province province) {
        if (!enabled) return;
        if (province == null) return;
        if (province.getFraction() != fraction) return;

        // one sweep of the sea per province per turn: everything below reads this number instead of
        // running its own search, which is the difference between a cheap pass and a slideshow
        int targets = countTargetsAcrossProvinceWater(province);

        // Only a province with nowhere left to walk goes to sea. Money spent on a port is money not
        // spent on the front line, and measurement was blunt about it: letting provinces that still
        // had a land frontier put to sea cost the balancer a quarter of its territory share on
        // ordinary maps. If the enemy can be reached on foot, walk.
        if (targets > 0 && !hasLandFrontier(province)) {
            checkToBuildPorts(province);
            checkToAnchorCoast(province);
        }

        checkToLaunchShips(province);
    }


    /**
     * Foreign land reachable by sea from anywhere along this province's coast: one breadth-first
     * sweep seeded with every open-water hex touching the province at once. Uses its own visited set
     * rather than the shared hex flags, which the move-zone detector and the balancer's brain borrow.
     */
    private int countTargetsAcrossProvinceWater(Province province) {
        HashSet<Hex> visited = new HashSet<>();
        ArrayList<Hex> current = new ArrayList<>();

        for (Hex hex : province.hexList) {
            for (int dir = 0; dir < 6; dir++) {
                Hex water = hex.getAdjacentHex(dir);
                if (water == null || water.isNullHex()) continue;
                if (!water.isOpenWater()) continue;
                if (!visited.add(water)) continue;
                current.add(water);
            }
        }

        int targets = 0;
        while (current.size() > 0) {
            ArrayList<Hex> next = new ArrayList<>();
            for (Hex water : current) {
                for (int dir = 0; dir < 6; dir++) {
                    Hex adjacent = water.getAdjacentHex(dir);
                    if (adjacent == null || adjacent.isNullHex()) continue;
                    if (!visited.add(adjacent)) continue;

                    if (adjacent.active) {
                        if (adjacent.fraction != fraction) targets++;
                        continue;
                    }

                    if (!adjacent.isOpenWater()) continue;
                    next.add(adjacent);
                }
            }
            current = next;
        }

        return targets;
    }


    /**
     * The fleet-scoped half: every ship of this fraction, wherever it is. Call once per turn, after
     * every province has been handled.
     */
    public void moveShips() {
        if (!enabled) return;

        // one field for the whole fleet: sea distance to the nearest foreign coast, by water
        HashMap<Hex, Integer> seaDistance = buildSeaDistanceField();

        // moving a ship can sink or merge units, so iterate a copy of the list
        for (Unit unit : new ArrayList<>(gameController.getUnitList())) {
            if (!unit.ship) continue;
            if (unit.getFraction() != fraction) continue;
            if (!unit.isReadyToMove()) continue;
            moveSingleShip(unit, seaDistance);
        }
    }


    /**
     * Builds as many harbours as the province can pay for in one turn. More harbours mean more ships
     * launched per turn, which is what turns a token landing into an invasion - so the only limits
     * are the ones the economy imposes: the price doubles with every port, each port must leave the
     * fare for a marine behind it, and the upkeep already committed has to stay affordable.
     */
    private void checkToBuildPorts(Province province) {
        while (true) {
            if (!province.hasMoneyForPort()) return;

            // a port with no marine to put in it is a hole in the budget - keep back the fare
            if (province.money < province.getCurrentPortPrice() + GameRules.PRICE_UNIT) return;

            // the fleet is paid for every turn; getProfit() already counts the ships we own, so this
            // tightens by itself as the fleet grows
            if (province.getProfit() < PROFIT_RESERVE) return;

            Hex site = findBestPortSite(province);
            if (site == null) return;
            if (!fieldManager.buildPort(province, site)) return;
        }
    }


    /**
     * A port may only be built against one of the province's own towns or farms, and both tend to
     * sit inland - the land AI deliberately puts farms where they are surrounded by friendly hexes.
     * A province can therefore be ringed by usable coast and still have nowhere legal to put a port,
     * which on an island map means it can never leave, no matter how rich it gets.
     * <p>
     * So it builds the anchor first: a farm on the shore, which pays for itself as economy anyway
     * and makes the water beside it a legal port site from the next turn on.
     */
    private void checkToAnchorCoast(Province province) {
        if (GameRules.slayRules) return; // no farms under slay rules, so no anchor to build
        // deliberately not stopped by already owning a port: every extra harbour needs its own
        // town or farm on the shore, so the anchors have to keep coming for the fleet to grow
        if (findBestPortSite(province) != null) return; // a legal site already exists
        // An anchor is a farm the land AI did not ask for, put where the land AI would not put one -
        // it wants farms inland, ringed by friendly hexes. On an ordinary map a province drifts in
        // and out of "no land frontier" all game, and paying for shoreline farms during those spells
        // measurably cost the master a third of its territory share. So only a province that is
        // genuinely rich buys one: the money then is not money the front line was waiting for.
        if (province.money < 2 * province.getCurrentFarmPrice()) return;
        if (province.getProfit() < PROFIT_RESERVE) return;

        Hex spot = findCoastalFarmSpot(province);
        if (spot == null) return;

        fieldManager.buildFarm(province, spot);
    }


    private Hex findCoastalFarmSpot(Province province) {
        Hex best = null;
        int bestWater = 0;

        for (Hex hex : province.hexList) {
            if (!hex.isFree()) continue;
            if (!MoveZoneDetection.canBuildFarmOnHex(hex)) continue;

            int openness = countOpenWaterNeighbours(hex);
            if (openness <= bestWater) continue;

            bestWater = openness;
            best = hex;
        }

        return best;
    }


    /**
     * Which legal site to use, once the sea sweep has already decided that going to sea is worth it.
     * The most open water beside it means the most room for a ship to leave in.
     */
    private Hex findBestPortSite(Province province) {
        Hex best = null;
        int bestWater = 0;

        for (Hex hex : province.hexList) {
            for (int dir = 0; dir < 6; dir++) {
                Hex water = hex.getAdjacentHex(dir);
                if (water == null || water.isNullHex()) continue;
                if (!MoveZoneDetection.canBuildPortOnHex(province, water)) continue;

                int openness = countOpenWaterNeighbours(water);
                if (openness <= bestWater) continue;

                bestWater = openness;
                best = water;
            }
        }

        return best;
    }


    private int countOpenWaterNeighbours(Hex hex) {
        int count = 0;
        for (int dir = 0; dir < 6; dir++) {
            Hex adjacent = hex.getAdjacentHex(dir);
            if (adjacent == null || adjacent.isNullHex()) continue;
            if (adjacent.isOpenWater()) count++;
        }

        return count;
    }


    /** Any neighbouring land that is not ours - neutral to take, or an enemy to fight. */
    private boolean hasLandFrontier(Province province) {
        for (Hex hex : province.hexList) {
            for (int dir = 0; dir < 6; dir++) {
                Hex adjacent = hex.getAdjacentHex(dir);
                if (adjacent == null || adjacent.isNullHex()) continue;
                if (!adjacent.active) continue;
                if (adjacent.fraction != fraction) return true;
            }
        }

        return false;
    }


    /**
     * Launches from every harbour that has a berth free, not one ship per turn: a single marine
     * lands, takes one hex and dies to the counterattack, whereas several landing together take a
     * foothold that holds. A unit bought directly into a port is a ship the moment it appears.
     * <p>
     * The brake is upkeep, re-checked before every launch. getProfit() counts the ships already
     * afloat, so each new hull makes the next one harder to justify and the fleet settles at
     * whatever size the province can actually feed.
     */
    private void checkToLaunchShips(Province province) {
        for (Hex hex : province.hexList) {
            if (hex.objectInside != Obj.PORT) continue;
            if (hex.containsUnit()) continue;
            if (province.getProfit() < PROFIT_RESERVE) return;

            int strength = pickMarineStrength(province);
            if (strength == 0) return;

            fieldManager.buildUnit(province, hex, strength);
        }
    }


    private int pickMarineStrength(Province province) {
        for (int strength = 4; strength >= 1; strength--) {
            if (!province.canBuildUnit(strength)) continue;
            // an invasion is a burst, not a standing army: judged over a shorter runway than the
            // land logic uses, because a marine that lands pays for itself in territory
            if (!province.canAiAffordUnit(strength, 2)) continue;
            return strength;
        }

        return 0;
    }


    private void moveSingleShip(Unit unit, HashMap<Hex, Integer> seaDistance) {
        // the detector hands back a shared list that the next detection call will overwrite
        ArrayList<Hex> zone = new ArrayList<>(gameController.detectMoveZoneForShip(unit));
        if (zone.size() == 0) return;

        Hex landing = findBestLanding(zone);
        if (landing != null) {
            sail(unit, landing);
            return;
        }

        Hex approach = findApproach(unit, zone, seaDistance);
        if (approach != null) {
            sail(unit, approach);
        }
    }


    private void sail(Unit unit, Hex target) {
        // at sea the ship belongs to no province; moveUnit resolves the province for an amphibious
        // landing on its own, from the beachhead or from the port the ship sailed out of
        gameController.moveUnit(unit, target, gameController.getProvinceByHex(unit.currentHex));
    }


    private Hex findBestLanding(ArrayList<Hex> zone) {
        Hex best = null;
        int bestValue = 0;

        for (Hex hex : zone) {
            if (!hex.active) continue;

            int value = landingValue(hex);
            if (value <= bestValue) continue;

            bestValue = value;
            best = hex;
        }

        return best;
    }


    /**
     * What a beach is worth. Landing on our own coast is worth nothing - it only converts a ship
     * back into a peasant - so friendly hexes score zero and are skipped.
     */
    private int landingValue(Hex hex) {
        if (hex.fraction == fraction) {
            // reinforcing our own beachhead. A one-hex colony on a hostile island dies to the first
            // counterattack, and every invasion that dies leaves the game unwinnable from the sea -
            // so a second marine on an existing colony beats a second colony somewhere else. But
            // only while the beachhead still borders something to take: a fully captured island is
            // not a destination, just land on the way, and a ship must sail past it, not unload
            return hex.overseasPart && !hex.containsUnit() && countFreeNeighbours(hex) > 0 ? 8 : 0;
        }

        if (hex.fraction != GameRules.NEUTRAL_FRACTION) {
            switch (hex.objectInside) {
                case Obj.TOWN:
                    return 12; // the capital: taking it cuts the enemy province in half
                case Obj.FARM:
                    return 9; // the economic centre, usually parked safely behind the front line
                case Obj.PORT:
                    return 7; // denies them the same trick
                case Obj.TOWER:
                case Obj.STRONG_TOWER:
                    return 2; // defended by definition, and worth little once taken
                default:
                    return 4;
            }
        }

        // neutral shore - a colony. Room to grow is what makes the trip worth the fare
        return 3 + countFreeNeighbours(hex) / 2 + (touchesOurColony(hex) ? 4 : 0);
    }


    /** Landing beside a foothold we already hold widens it instead of starting a second doomed one. */
    private boolean touchesOurColony(Hex hex) {
        for (int dir = 0; dir < 6; dir++) {
            Hex adjacent = hex.getAdjacentHex(dir);
            if (adjacent == null || adjacent.isNullHex()) continue;
            if (!adjacent.active) continue;
            if (adjacent.fraction == fraction && adjacent.overseasPart) return true;
        }

        return false;
    }


    private int countFreeNeighbours(Hex hex) {
        int count = 0;
        for (int dir = 0; dir < 6; dir++) {
            Hex adjacent = hex.getAdjacentHex(dir);
            if (adjacent == null || adjacent.isNullHex()) continue;
            if (!adjacent.active) continue;
            if (adjacent.fraction == fraction) continue;
            count++;
        }

        return count;
    }


    /**
     * Nothing to land on this turn, so close the distance instead. Distance is sailing distance -
     * the breadth-first field built once per turn - not the straight line, so a ship facing a
     * friendly island in its way rounds it instead of stalling against the shore: the field already
     * flows around every obstacle, and following it downhill is the detour. Returns null when no
     * water hex in range is an improvement on where the ship already floats - drifting on the spot
     * costs upkeep and achieves nothing.
     */
    private Hex findApproach(Unit unit, ArrayList<Hex> zone, HashMap<Hex, Integer> seaDistance) {
        // a docked ship stands on land, which the field does not cover - any reachable water beats it
        Integer current = seaDistance.get(unit.currentHex);
        int bestDistance = current == null ? Integer.MAX_VALUE : current;
        Hex best = null;

        for (Hex hex : zone) {
            if (hex.active) continue;

            Integer distance = seaDistance.get(hex);
            if (distance == null) continue; // water from which no foreign coast can be reached
            if (distance >= bestDistance) continue;

            bestDistance = distance;
            best = hex;
        }

        return best;
    }


    /**
     * Sailing distance from every reachable water hex to the nearest foreign coast: a breadth-first
     * sweep over open water, seeded with the water lapping every foreign shore at once. Unbounded -
     * a destination on the far side of the map pulls ships just as surely as one next door.
     */
    private HashMap<Hex, Integer> buildSeaDistanceField() {
        HashMap<Hex, Integer> distance = new HashMap<>();
        ArrayList<Hex> current = new ArrayList<>();

        for (Hex hex : fieldManager.activeHexes) {
            if (hex.fraction == fraction) continue;
            for (int dir = 0; dir < 6; dir++) {
                Hex water = hex.getAdjacentHex(dir);
                if (water == null || water.isNullHex()) continue;
                if (!water.isOpenWater()) continue;
                if (distance.containsKey(water)) continue;
                distance.put(water, 0);
                current.add(water);
            }
        }

        int depth = 0;
        while (current.size() > 0) {
            depth++;
            ArrayList<Hex> next = new ArrayList<>();
            for (Hex water : current) {
                for (int dir = 0; dir < 6; dir++) {
                    Hex adjacent = water.getAdjacentHex(dir);
                    if (adjacent == null || adjacent.isNullHex()) continue;
                    if (!adjacent.isOpenWater()) continue;
                    if (distance.containsKey(adjacent)) continue;
                    distance.put(adjacent, depth);
                    next.add(adjacent);
                }
            }
            current = next;
        }

        return distance;
    }
}
