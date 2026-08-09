package yio.tro.antiyoy.gameplay;

import yio.tro.antiyoy.gameplay.rules.GameRules;

import java.util.ArrayList;

public class MoveZoneDetection {

    private final FieldManager fieldManager;
    private ArrayList<Hex> result;
    private ArrayList<Hex> propagationList;
    private Hex tempHex;
    private Hex adjHex;


    public MoveZoneDetection(FieldManager fieldManager) {
        this.fieldManager = fieldManager;

        result = new ArrayList<>();
        propagationList = new ArrayList<>();
    }


    public static void unFlagAllHexesInArrayList(ArrayList<Hex> hexList) {
        for (int i = hexList.size() - 1; i >= 0; i--) {
            hexList.get(i).flag = false;
            hexList.get(i).inMoveZone = false;
        }
    }


    public ArrayList<Hex> detectMoveZoneForFarm() {
        fieldManager.moveZoneManager.clear();
        unFlagAllHexesInArrayList(fieldManager.activeHexes);
        result.clear();
        for (Hex hex : fieldManager.selectedProvince.hexList) {
            if (canBuildFarmOnHex(hex)) {
                hex.inMoveZone = true;
                result.add(hex);
            }
        }

        return result;
    }


    public static boolean canBuildFarmOnHex(Hex hex) {
        // colonies are pure land grabs - no economy buildings overseas
        if (hex.overseasPart) return false;
        return hex.hasThisSupportiveObjectNearby(Obj.FARM) || hex.hasThisSupportiveObjectNearby(Obj.TOWN);
    }


    /**
     * A port stands on the water itself: the target is an inactive (water) hex touching one of the
     * province's towns or farms. Building it will activate the hex into the province, which is also
     * why the check is province-aware - two same-fraction provinces may share a coastline, and only
     * the one whose town/farm touches the water may pay for the port there.
     */
    public static boolean canBuildPortOnHex(Province province, Hex hex) {
        if (hex == null || hex.isNullHex()) return false;
        if (hex.active) return false;
        if (!hex.canContainObjects) return false;
        if (hex.containsUnit()) return false; // a ship is moored there; no room for a harbour

        for (int dir = 0; dir < 6; dir++) {
            Hex adjHex = hex.getAdjacentHex(dir);
            if (!adjHex.active) continue;
            // a colony anchors a port on any of its coastal hexes; the motherland only on its economy
            if (!adjHex.overseasPart && adjHex.objectInside != Obj.TOWN && adjHex.objectInside != Obj.FARM) continue;
            if (!province.containsHex(adjHex)) continue;
            return true;
        }

        return false;
    }


    public ArrayList<Hex> detectMoveZoneForPort() {
        fieldManager.moveZoneManager.clear();
        unFlagAllHexesInArrayList(fieldManager.activeHexes);
        result.clear();
        for (Hex hex : fieldManager.selectedProvince.hexList) {
            if (!hex.overseasPart && hex.objectInside != Obj.TOWN && hex.objectInside != Obj.FARM) continue;
            for (int dir = 0; dir < 6; dir++) {
                adjHex = hex.getAdjacentHex(dir);
                if (adjHex.active || adjHex.isNullHex() || !adjHex.canContainObjects) continue;
                if (adjHex.inMoveZone) continue;

                // water carries no meaningful fraction; painting it neutral makes the
                // highlighted tile render as empty ground instead of some player's color
                adjHex.fraction = GameRules.NEUTRAL_FRACTION;
                adjHex.inMoveZone = true;
                result.add(adjHex);
            }
        }

        return result;
    }


    /**
     * A ship's move zone is the sea: water hexes reachable within the move limit, plus the coastal
     * hexes it may land on. Water hexes are not in activeHexes, so their bookkeeping flags are
     * cleaned up locally instead of relying on unFlagAllHexesInArrayList.
     */
    public ArrayList<Hex> detectMoveZoneForShip(Unit unit) {
        fieldManager.moveZoneManager.clear();
        unFlagAllHexesInArrayList(fieldManager.activeHexes);
        result.clear();
        propagationList.clear();

        ArrayList<Hex> visitedWater = new ArrayList<>();
        Hex startHex = unit.currentHex;
        startHex.flag = true;
        startHex.moveZoneNumber = GameRules.SHIP_MOVE_LIMIT;
        if (!startHex.active) visitedWater.add(startHex);
        propagationList.add(startHex);

        while (propagationList.size() > 0) {
            tempHex = propagationList.remove(0);
            for (int dir = 0; dir < 6; dir++) {
                adjHex = tempHex.getAdjacentHex(dir);
                if (adjHex.isNullHex() || adjHex.flag) continue;
                adjHex.flag = true;

                if (adjHex.active) {
                    if (canShipLandOnHex(unit, adjHex)) {
                        adjHex.inMoveZone = true;
                        result.add(adjHex);
                    }
                    continue;
                }

                visitedWater.add(adjHex);
                if (!adjHex.canContainObjects) continue;
                if (adjHex.containsUnit()) continue;

                // water carries no meaningful fraction, but it may hold a stale one from a ship
                // that once anchored there; painting it neutral makes the highlighted tile render
                // as empty ground instead of some player's color (same trick as the port zone)
                adjHex.fraction = GameRules.NEUTRAL_FRACTION;
                adjHex.inMoveZone = true;
                result.add(adjHex);
                if (tempHex.moveZoneNumber > 1) {
                    adjHex.moveZoneNumber = tempHex.moveZoneNumber - 1;
                    propagationList.add(adjHex);
                }
            }
        }

        startHex.flag = false;
        for (Hex hex : visitedWater) {
            hex.flag = false;
        }

        return result;
    }


    private boolean canShipLandOnHex(Unit unit, Hex hex) {
        if (hex == unit.currentHex) return false;

        if (hex.sameFraction(unit.currentHex)) {
            return unit.canMoveToFriendlyHex(hex);
        }

        // an amphibious landing conquers the hex, and a conquered hex must join a province -
        // either a friendly one next to the beachhead, or (an overseas invasion) the province
        // the ship sailed from, provided its supply line home still holds
        if (fieldManager.getAdjacentProvince(hex, unit.getFraction()) == null
                && unit.getOriginProvince() == null) return false;

        return fieldManager.gameController.canUnitAttackHex(unit.strength, unit.getFraction(), hex);
    }


    // units can only spawn on own territory, so the zone is the province itself
    public ArrayList<Hex> detectMoveZoneForBuildingUnit(int strength) {
        fieldManager.moveZoneManager.clear();
        unFlagAllHexesInArrayList(fieldManager.activeHexes);
        result.clear();
        for (Hex hex : fieldManager.selectedProvince.hexList) {
            if (!canBuildUnitOnHex(hex, strength)) continue;
            hex.inMoveZone = true;
            result.add(hex);
        }

        return result;
    }


    private boolean canBuildUnitOnHex(Hex hex, int strength) {
        if (!hex.canHostBuiltUnit()) return false;
        if (hex.containsUnit() && !fieldManager.gameController.canMergeUnits(strength, hex.unit.strength)) return false;
        return true;
    }


    public ArrayList<Hex> detectMoveZone(Hex startHex, int strength) {
        return detectMoveZone(startHex, strength, 9001); // move limit is almost infinite
    }


    public ArrayList<Hex> detectMoveZone(Hex startHex, int strength, int moveLimit) {
        unFlagAllHexesInArrayList(fieldManager.activeHexes);
        beginDetection(startHex, moveLimit);

        while (propagationList.size() > 0) {
            iteratePropagation(startHex, strength);
        }

        return result;
    }


    private void iteratePropagation(Hex startHex, int strength) {
        tempHex = propagationList.get(0);
        propagationList.remove(0);

        tempHex.inMoveZone = true;
        result.add(tempHex);

        if (!tempHex.sameFraction(startHex)) return;
        if (tempHex.moveZoneNumber == 0) return;

        for (int dir = 0; dir < 6; dir++) {
            adjHex = tempHex.getAdjacentHex(dir);
            if (!adjHex.active) continue;
            if (adjHex.flag) continue;

            if (adjHex.sameFraction(startHex)) {
                propagationList.add(adjHex);
                adjHex.moveZoneNumber = tempHex.moveZoneNumber - 1;
                adjHex.flag = true;
            } else {
                if (fieldManager.gameController.canUnitAttackHex(strength, startHex.fraction, adjHex)) {
                    propagationList.add(adjHex);
                    adjHex.flag = true;
                }
            }
        }
    }


    private void beginDetection(Hex startHex, int moveLimit) {
        result.clear();
        propagationList.clear();
        propagationList.add(startHex);
        startHex.moveZoneNumber = moveLimit;
    }
}