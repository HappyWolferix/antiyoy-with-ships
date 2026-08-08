package yio.tro.antiyoy.gameplay;

import yio.tro.antiyoy.gameplay.data_storage.EncodeableYio;
import yio.tro.antiyoy.stuff.PointYio;
import yio.tro.antiyoy.factor_yio.FactorYio;
import yio.tro.antiyoy.gameplay.rules.GameRules;
import yio.tro.antiyoy.stuff.Yio;

import java.util.ArrayList;

/**
 * Created by yiotro on 24.05.2015.
 */
public class Unit implements EncodeableYio{

    public Hex lastHex, currentHex;
    public final PointYio currentPos;
    public final FactorYio moveFactor;
    public int strength;
    // set when the unit boards a port; while true the unit stands on water hexes and its move
    // zone is the sea. Cleared the moment it steps back onto ordinary land.
    public boolean ship;
    // the port hex this ship sailed from. While the unit is at sea its upkeep is paid by the
    // province that owns this hex; if that province falls, the ship is cut off and sinks.
    public Hex originHex;
    final GameController gameController;
    boolean readyToMove;
    public float jumpPos, jumpGravity, jumpDy, jumpStartingImpulse;


    public Unit(GameController gameController, Hex currentHex, int strength) {
        this.gameController = gameController;
        this.currentHex = currentHex;
        this.strength = strength;
        moveFactor = new FactorYio();
        moveFactor.setValues(1, 0);
        lastHex = currentHex;
        jumpStartingImpulse = 0.015f;
        currentPos = new PointYio();
        updateCurrentPos();
    }


    boolean canMoveToFriendlyHex(Hex hex) {
        if (hex == currentHex) return false;
        if (hex.objectInside == Obj.PORT) return !hex.containsUnit(); // boarding, not trampling
        if (hex.containsBuilding()) return false;
        if (hex.containsUnit() && !gameController.ruleset.canMergeUnits(this, hex.unit)) return false;
        return true;
    }


    public boolean moveToHex(Hex targetHex) {
        // stepping onto one's own port is how a unit becomes a ship - the port must survive the visit
        boolean docking = targetHex.active && targetHex.objectInside == Obj.PORT && targetHex.sameFraction(currentHex);

        if (!docking && targetHex.sameFraction(currentHex) && targetHex.containsBuilding()) return false;

        gameController.ruleset.onUnitMoveToHex(this, targetHex);
        if (targetHex.containsObject() && !docking) {
            gameController.cleanOutHex(targetHex); // unit crushes object
            gameController.updateCacheOnceAfterSomeTime();
        }
        stopJumping();
        setReadyToMove(false);
        lastHex = currentHex;
        currentHex = targetHex;
        moveFactor.setValues(0, 0);
        moveFactor.appear(1, 4);
        lastHex.unit = null;
        if (!lastHex.active) {
            // the water hex borrowed the ship's fraction while occupied; hand it back to the sea
            lastHex.fraction = GameRules.NEUTRAL_FRACTION;
        }
        targetHex.unit = this;
        updateShipState(docking);
//        YioGdxGame.say("anim hexes: " + gameController.animHexes.size() + "        selected hexes: " + gameController.selectedHexes.size());
//        this was wonderful bug. Hexes were added to list several times which caused method move() to be called to many times

        return true;
    }


    public int getFraction() {
        return currentHex.fraction;
    }


    /**
     * The province this ship's supply line leads back to. Resolved lazily through the origin
     * hex because Province objects are recreated on every split/unite. Returns null when the
     * supply line is severed - the origin hex was lost to another fraction or its province
     * no longer exists.
     */
    public Province getOriginProvince() {
        if (originHex == null) return null;
        if (!originHex.active) return null;
        if (originHex.fraction != getFraction()) return null;
        return gameController.fieldManager.getProvinceByHex(originHex);
    }


    /**
     * Sailing on water keeps the ship flag; touching land in any way - landing, disembarking,
     * capturing an enemy coastal hex - takes it away. Docking at a port (own or captured) grants it.
     */
    private void updateShipState(boolean docking) {
        if (docking) {
            ship = true;
            originHex = currentHex;
            return;
        }
        if (currentHex.active) {
            ship = false;
        }
    }


    void updateCurrentPos() {
        currentPos.x = lastHex.pos.x + moveFactor.get() * (currentHex.pos.x - lastHex.pos.x);
        currentPos.y = lastHex.pos.y + moveFactor.get() * (currentHex.pos.y - lastHex.pos.y);
    }


    public void setReadyToMove(boolean readyToMove) {
        this.readyToMove = readyToMove;
    }


    public boolean isReadyToMove() {
        return readyToMove;
    }


    public Unit getSnapshotCopy() {
        Unit copy = new Unit(gameController, currentHex, strength);
        copy.readyToMove = readyToMove;
        copy.ship = ship;
        copy.originHex = originHex;
        return copy;
    }


    public void startJumping() {
        if (GameRules.replayMode) return;

        jumpPos = 0;
        jumpDy = jumpStartingImpulse;
        jumpGravity = 0.001f;
    }


    public void stopJumping() {
        jumpPos = 0;
        jumpDy = 0;
        jumpGravity = 0;
    }


    void move() {
        moveFactor.move();
        updateCurrentPos();
    }


    void moveJumpAnim() {
        jumpDy -= jumpGravity;
        jumpPos += jumpDy;
        if (jumpPos < 0) {
            jumpPos = 0;
            jumpDy = jumpStartingImpulse;
        }
    }


    @Override
    public String encode() {
        int origin1 = originHex == null ? -1 : originHex.index1;
        int origin2 = originHex == null ? -1 : originHex.index2;
        return currentHex.index1 + " " + currentHex.index2 + " " + strength + " " + isReadyToMove() + " " + ship
                + " " + currentHex.fraction + " " + origin1 + " " + origin2;
    }


    @Override
    public void decode(String source) {
        String[] split = source.split(" ");
        boolean ready = Boolean.valueOf(split[3]);
        if (ready) {
            setReadyToMove(true);
            startJumping();
        } else {
            setReadyToMove(false);
            stopJumping();
        }
        if (split.length > 4) {
            ship = Boolean.valueOf(split[4]);
        }
        if (split.length > 5 && !currentHex.active) {
            // a ship at sea lends its fraction to the water hex it occupies; inactive hexes
            // are not part of the land section, so the borrowed fraction is restored here
            currentHex.fraction = Integer.valueOf(split[5]);
        }
        if (split.length > 7) {
            int origin1 = Integer.valueOf(split[6]);
            int origin2 = Integer.valueOf(split[7]);
            if (origin1 >= 0) {
                originHex = gameController.fieldManager.getHex(origin1, origin2);
            }
        }
    }


    @Override
    public String toString() {
        return "[Unit: s" +
                strength + " on " +
                currentHex +
                "]";
    }
}
