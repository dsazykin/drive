package jMonkeyEngine;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;

public class LoadingState extends BaseAppState {

    private Node loadingNode;
    private BitmapText loadingText;
    private BitmapText progressText;

    private SimpleApplication sapp;
    private Camera cam;
    private BitmapFont guiFont;

    private String selectedCar;
    private float loadingProgress = 0f;
    private float loadingTimer = 0f;
    private static final float LOADING_DURATION = 3f; // 3 seconds loading simulation

    private boolean loadingStarted = false;

    public LoadingState(String selectedCar) {
        this.selectedCar = selectedCar;
    }

    @Override
    protected void initialize(Application app) {
        sapp = (SimpleApplication) app;
        cam = sapp.getCamera();
        guiFont = sapp.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        loadingNode = new Node("LoadingScreen");

        // Create loading UI with standard BitmapText for simplicity
        loadingText = new BitmapText(guiFont);
        loadingText.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        loadingText.setText("Loading...");
        loadingText.setColor(ColorRGBA.White);

        // Center the loading text
        float textWidth = loadingText.getLineWidth();
        loadingText.setLocalTranslation(
            (cam.getWidth() - textWidth) / 2f,
            cam.getHeight() / 2f + 50,
            0
        );

        progressText = new BitmapText(guiFont);
        progressText.setSize(guiFont.getCharSet().getRenderedSize());
        progressText.setText("0%");
        progressText.setColor(ColorRGBA.White);

        loadingNode.attachChild(loadingText);
        loadingNode.attachChild(progressText);

        sapp.getGuiNode().attachChild(loadingNode);

        // Set background to dark
        sapp.getViewPort().setBackgroundColor(new ColorRGBA(0.1f, 0.1f, 0.1f, 1f));
    }

    @Override
    protected void onEnable() {
        loadingStarted = true;
    }

    @Override
    protected void onDisable() {
    }

    @Override
    protected void cleanup(Application app) {
        if (loadingNode != null) {
            loadingNode.removeFromParent();
            loadingNode = null;
        }
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);

        if (loadingStarted) {
            loadingTimer += tpf;
            loadingProgress = Math.min(loadingTimer / LOADING_DURATION, 1f);

            // Update progress text
            int percentage = (int) (loadingProgress * 100);
            progressText.setText(percentage + "%");

            // Center progress text
            float textWidth = progressText.getLineWidth();
            progressText.setLocalTranslation(
                (cam.getWidth() - textWidth) / 2f,
                cam.getHeight() / 2f,
                0
            );

            // When loading is complete, start the game
            if (loadingProgress >= 1f) {
                loadingStarted = false;
                getStateManager().detach(this);

                // Create and attach gameplay state
                GameplayState gameplayState = new GameplayState();
                gameplayState.setSelectedCar(selectedCar);
                getStateManager().attach(gameplayState);
            }
        }
    }
}

