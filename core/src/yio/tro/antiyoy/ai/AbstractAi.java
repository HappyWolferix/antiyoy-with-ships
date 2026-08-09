package yio.tro.antiyoy.ai;

import yio.tro.antiyoy.gameplay.GameController;
import yio.tro.antiyoy.stuff.Yio;

public abstract class AbstractAi {

    public GameController gameController;
    protected int fraction;
    protected final NavalStrategist navalStrategist;


    public AbstractAi(GameController gameController, int fraction) {
        this.gameController = gameController;
        this.fraction = fraction;
        this.navalStrategist = new NavalStrategist(gameController, fraction);
    }


    public abstract void perform();


    public int getFraction() {
        return fraction;
    }
}
