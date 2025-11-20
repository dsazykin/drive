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

    private final String selectedCar;

    private boolean loadingStarted = false;
    private boolean gameplayInitialized = false;

    private String currentLoadingStage = "Initializing...";

    public LoadingState(String selectedCar) {
        this.selectedCar = selectedCar;
    }

    @Override
    protected void initialize(Application app) {
        sapp = (SimpleApplication) app;
        cam = sapp.getCamera();
        guiFont = sapp.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        loadingNode = new Node("LoadingScreen");

        // Create loading title
        loadingText = new BitmapText(guiFont);
        loadingText.setSize(guiFont.getCharSet().getRenderedSize() * 2);
        loadingText.setText("Loading");
        loadingText.setColor(ColorRGBA.White);

        // Center the loading text
        float textWidth = loadingText.getLineWidth();
        loadingText.setLocalTranslation(
            (cam.getWidth() - textWidth) / 2f,
            cam.getHeight() / 2f + 80,
            0
        );

        // Create stage text
        progressText = new BitmapText(guiFont);
        progressText.setSize(guiFont.getCharSet().getRenderedSize());
        progressText.setText("Initializing...");
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

        // Start loading the gameplay state in the background
        new Thread(() -> {
            try {
                updateLoadingStage("Creating world...");
                Thread.sleep(100); // Small delay to show the message

                // Create and initialize gameplay state
                final GameplayState gameplayState = new GameplayState();
                gameplayState.setSelectedCar(selectedCar);
                gameplayState.setLoadingCallback(new GameplayState.LoadingCallback() {
                    @Override
                    public void onLoadingStageChanged(String stage) {
                        updateLoadingStage(stage);
                    }

                    @Override
                    public void onLoadingComplete() {
                        gameplayInitialized = true;
                    }
                });

                // Attach the state on the main thread
                sapp.enqueue(() -> {
                    getStateManager().attach(gameplayState);
                    return null;
                });

            } catch (Exception e) {
                e.printStackTrace();
                updateLoadingStage("Error loading game: " + e.getMessage());
            }
        }).start();
    }

    public void updateLoadingStage(String stage) {
        currentLoadingStage = stage;
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
            // Update stage text
            progressText.setText(currentLoadingStage);

            // Center the stage text
            float textWidth = progressText.getLineWidth();
            progressText.setLocalTranslation(
                (cam.getWidth() - textWidth) / 2f,
                cam.getHeight() / 2f,
                0
            );

            // When loading is complete, remove this state
            if (gameplayInitialized) {
                loadingStarted = false;
                getStateManager().detach(this);
            }
        }
    }
}

