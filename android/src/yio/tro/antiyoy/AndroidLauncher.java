package yio.tro.antiyoy;

import android.os.Bundle;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/**
 * Android entry point. Like the desktop launcher, this is not part of the
 * upstream sources - the repo only ships the core module.
 *
 * Nothing in core ever assigns YioGdxGame.platformType, so the launcher has to
 * do it before the application starts.
 */
public class AndroidLauncher extends AndroidApplication {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        YioGdxGame.platformType = PlatformType.android;

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useImmersiveMode = true;

        initialize(new YioGdxGame(), config);
    }
}
