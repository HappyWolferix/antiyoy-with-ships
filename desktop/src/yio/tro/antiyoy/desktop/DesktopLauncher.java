package yio.tro.antiyoy.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import yio.tro.antiyoy.PlatformType;
import yio.tro.antiyoy.YioGdxGame;

/**
 * Desktop entry point. The upstream repo only contains the core module, so
 * this launcher is not part of the original sources.
 *
 * Antiyoy is a portrait phone game, so the window defaults to a phone-ish
 * aspect ratio. Nothing in the core module ever assigns YioGdxGame.platformType,
 * so the launcher has to do it before the application starts.
 */
public class DesktopLauncher {

    private static final int WIDTH = 540;
    private static final int HEIGHT = 960;


    public static void main(String[] args) {
        YioGdxGame.platformType = PlatformType.pc;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Antiyoy");
        config.setWindowedMode(WIDTH, HEIGHT);
        config.setWindowSizeLimits(300, 500, 2000, 3000);
        config.useVsync(true);

        new Lwjgl3Application(new YioGdxGame(), config);
    }
}
