package yio.tro.antiyoy.gameplay.rules;

import yio.tro.antiyoy.gameplay.*;

public abstract class Ruleset {

    GameController gameController;


    public Ruleset(GameController gameController) {
        this.gameController = gameController;
    }


    public abstract boolean canSpawnPineOnHex(Hex hex);


    public abstract boolean canSpawnPalmOnHex(Hex hex);


    public abstract void onUnitAdd(Hex hex);


    public abstract void onTurnEnd();


    public abstract boolean canMergeUnits(Unit unit1, Unit unit2);


    public abstract int getHexIncome(Hex hex);


    /**
     * The part of getHexIncome() that the building on the hex is responsible for, excluding the hex's
     * own land income. Exists so the UI can advertise a farm as "+4" and a port as "+5" without
     * hardcoding either number, and without claiming a profit in rulesets that don't pay one.
     */
    public abstract int getBuildingIncome(Hex hex);


    public abstract int getHexTax(Hex hex);


    public abstract int getUnitTax(int strength);


    /**
     * Full upkeep of a concrete unit: base pay by strength plus the flat ship surcharge while
     * the unit has the ship flag (at sea or docked at a port). Every tax path that looks at an
     * actual unit - hex tax, ships at sea - must go through this instead of getUnitTax(int).
     */
    public int getUnitTax(Unit unit) {
        int tax = getUnitTax(unit.strength);
        if (unit.ship) {
            tax += GameRules.TAX_SHIP;
        }
        return tax;
    }


    public abstract boolean canBuildUnit(Province province, int strength);


    public abstract void onUnitMoveToHex(Unit unit, Hex hex);


    public abstract boolean canUnitAttackHex(int unitStrength, Hex hex);


    public int howManyTreesNearby(Hex hex) {
        if (!hex.active) return 0;
        int c = 0;
        for (int i = 0; i < 6; i++)
            if (hex.getAdjacentHex(i).containsTree()) c++;
        return c;
    }
}
