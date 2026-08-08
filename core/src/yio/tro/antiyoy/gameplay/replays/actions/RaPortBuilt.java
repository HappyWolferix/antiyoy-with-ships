package yio.tro.antiyoy.gameplay.replays.actions;

import yio.tro.antiyoy.gameplay.FieldManager;
import yio.tro.antiyoy.gameplay.GameController;
import yio.tro.antiyoy.gameplay.Hex;
import yio.tro.antiyoy.gameplay.Obj;

import java.util.ArrayList;

public class RaPortBuilt extends RepAction{

    Hex hex;
    int fraction;


    public RaPortBuilt(Hex hex, int fraction) {
        this.hex = hex;
        this.fraction = fraction;
    }


    @Override
    public void initType() {
        type = PORT_BUILT;
    }


    @Override
    public String saveInfo() {
        // fraction travels separately: at record time the hex is still water and carries no owner
        return convertHexToTwoTokens(hex) + fraction + " ";
    }


    @Override
    public void loadInfo(FieldManager fieldManager, String source) {
        ArrayList<String> strings = convertSourceStringToList(source);
        hex = getHexByTwoTokens(fieldManager, strings.get(0), strings.get(1));
        fraction = Integer.valueOf(strings.get(2));
    }


    @Override
    public void perform(GameController gameController) {
        FieldManager fieldManager = gameController.fieldManager;
        if (!hex.active) {
            hex.active = true;
            hex.setFraction(fraction);
            hex.previousFraction = fraction;
            fieldManager.activeHexes.add(hex);
        }
        fieldManager.addSolidObject(hex, Obj.PORT);
    }


    @Override
    public String toString() {
        return "[Port built: " +
                hex +
                "]";
    }
}
