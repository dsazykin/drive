package jMonkeyEngine;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.scene.Node;
import com.jme3.ui.Picture;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.controls.ActionListener;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Container;

public class MainMenuState extends BaseAppState {

    private Node guiNode = new Node("MainMenu");

    private Container menu;

    @Override
    protected void initialize(Application app) {
        SimpleApplication sapp = (SimpleApplication) app;

        menu = new Container();

        menu.addChild(new Label("Drive"));

        Button play = menu.addChild(new Button("Play"));
        play.addClickCommands(source -> {
            getStateManager().detach(this);
            getStateManager().attach(new GameplayState());
        });

        Button quit = menu.addChild(new Button("Quit"));
        quit.addClickCommands(source -> app.stop());

        // Attach to guiNode first so it has a size
        sapp.getGuiNode().attachChild(menu);

        // Center it
        centerMenu(sapp);
    }

    public void centerMenu(SimpleApplication app) {
        float width = app.getCamera().getWidth();
        float height = app.getCamera().getHeight();

        float menuWidth = menu.getPreferredSize().x;
        float menuHeight = menu.getPreferredSize().y;

        menu.setLocalTranslation(
                (width - menuWidth) / 2f,
                (height + menuHeight) / 2f,
                0
        );
    }

    @Override
    protected void cleanup(Application app) {
        // Remove menu when this state is detached
        ((SimpleApplication) app).getGuiNode().detachChild(menu);
    }

    @Override
    protected void onEnable() {
        ((com.jme3.app.SimpleApplication) getApplication()).getGuiNode().attachChild(guiNode);

        getApplication().getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        guiNode.removeFromParent();

        getApplication().getInputManager().setCursorVisible(false);
    }
}

