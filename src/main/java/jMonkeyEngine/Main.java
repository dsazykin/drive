package jMonkeyEngine;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.style.BaseStyles;

public class Main extends SimpleApplication {

    public static void main(String[] args) {
        Main app = new Main();

        AppSettings settings = new AppSettings(true);
        settings.setTitle("My Game");
        settings.setResolution(1280, 720);
        settings.setResizable(true);

        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        GuiGlobals.initialize(this);
        BaseStyles.loadGlassStyle();
        GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");

        stateManager.attach(new MainMenuState());
    }

    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);

        MainMenuState ms = stateManager.getState(MainMenuState.class);
        if (ms != null) {
            ms.centerMenu(this);
            return;
        }

        GameplayState gs = stateManager.getState(GameplayState.class);
        if (gs != null) {
            if (gs.guiLoaded) {
                gs.reloadGUI();
            }
        }
    }

    @Override
    public void simpleUpdate(float tpf) {

    }

    @Override
    public void stop() {
        GameplayState gs = stateManager.getState(GameplayState.class);
        if (gs != null) {
            gs.executor.shutdown();
        }
        super.stop();
    }
}