package yio.tro.antiyoy.menu.scenes;

import com.badlogic.gdx.Gdx;
import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;
import yio.tro.antiyoy.menu.Animation;
import yio.tro.antiyoy.menu.ButtonYio;
import yio.tro.antiyoy.menu.MenuControllerYio;
import yio.tro.antiyoy.menu.behaviors.Reaction;
import yio.tro.antiyoy.stuff.VersionManager;

import java.util.ArrayList;

public class SceneArticle extends AbstractScene{


    private ButtonYio infoPanel;


    public SceneArticle(MenuControllerYio menuControllerYio) {
        super(menuControllerYio);
    }


    public void create(String key, Reaction backButtonBehavior, int id_offset) {
        create(key, backButtonBehavior, id_offset, false);
    }


    /**
     * @param showVersion only the 'about game' article shows the build version,
     *                    the help articles don't.
     */
    public void create(String key, Reaction backButtonBehavior, int id_offset, boolean showVersion) {
        menuControllerYio.beginMenuCreation();

        menuControllerYio.getYioGdxGame().beginBackgroundChange(1, true, false);

        menuControllerYio.spawnBackButton(id_offset, backButtonBehavior);

        infoPanel = buttonFactory.getButton(generateRectangle(0.05, 0.1, 0.9, 0.7), id_offset + 1, null);

        infoPanel.cleatText();
        ArrayList<String> list = menuControllerYio.getArrayListFromString(getString(key));
        infoPanel.addManyLines(list);
        int lines = 18;
        int addedEmptyLines = lines - list.size();
        if (showVersion) {
            infoPanel.addTextLine(VersionManager.getInstance().getVersionString());
            addedEmptyLines--; // the version line takes one of the empty ones
        }
        for (int i = 0; i < addedEmptyLines; i++) {
            infoPanel.addTextLine(" ");
        }
        menuControllerYio.getButtonRenderer().renderButton(infoPanel);

        infoPanel.setTouchable(false);
        infoPanel.setAnimation(Animation.from_center);
        infoPanel.appearFactor.appear(2, 1.5);

        menuControllerYio.endMenuCreation();
    }


    @Override
    public void create() {
        create("info_array", getDefaultBackReaction(), 10, true);

        ButtonYio helpIndexButton = buttonFactory.getButton(generateRectangle(0.5, 0.9, 0.45, 0.07), 38123714, getString("help"));
        helpIndexButton.setReaction(Reaction.rbHelpIndex);
        helpIndexButton.setAnimation(Animation.up);

        ButtonYio moreInfoButton = buttonFactory.getButton(generateRectangle(0.65, 0.1, 0.3, 0.04), 38123717, getString("more"));
        moreInfoButton.setReaction(Reaction.rbSpecialThanksMenu);
        moreInfoButton.setAnimation(Animation.from_center);
        moreInfoButton.setShadow(false);
        moreInfoButton.setVisualHook(infoPanel);
        moreInfoButton.setTouchOffset(0.05f * Gdx.graphics.getHeight());
    }


    private Reaction getDefaultBackReaction() {
        if (YioGdxGame.platformType == PlatformType.ios) {
            return Reaction.rbMainMenu;
        }
        return Reaction.rbSettingsMenu;
    }
}