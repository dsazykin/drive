package jMonkeyEngine;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Container;
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Road.RoadGenerator;
import jMonkeyEngine.Terrain.TerrainGenerator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainMenuState extends BaseAppState {

    private Node guiNode = new Node("MainMenu");
    private Node backgroundNode = new Node("Background");

    private Container menu;
    private Container carSelectionMenu;
    private Label selectedCarLabel;

    private SimpleApplication sapp;
    private Camera cam;
    private AssetManager assetManager;

    // Background terrain
    private BulletAppState bulletAppState;
    private TerrainGenerator generator;
    private ChunkManager manager;
    private RoadGenerator road;
    private ExecutorService executor;

    private float cameraAngle = 0f;
    private float cameraDistance = 150f;
    private Vector3f cameraTarget = new Vector3f(0, 50, 0);

    // Car selection
    private String selectedCar = "Nismo";
    private String[] availableCars = {"Nismo", "GrandTourer", "PickupTruck", "Rotator"};
    private int currentCarIndex = 0;

    private boolean showingCarSelection = false;

    private final int CHUNK_SIZE = 1000;
    private final float SCALE = 40f;
    private long SEED = 12345L; // Fixed seed for consistent menu terrain

    @Override
    protected void initialize(Application app) {
        sapp = (SimpleApplication) app;
        cam = sapp.getCamera();
        assetManager = sapp.getAssetManager();

        // Set up background terrain
        initBackgroundTerrain();

        // Create main menu
        createMainMenu();

        // Set up lights
        setUpLight();

        // Initialize camera position
        updateCameraPosition();
    }

    private void initBackgroundTerrain() {
        sapp.getRootNode().attachChild(backgroundNode);

        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        bulletAppState = new BulletAppState();
        sapp.getStateManager().attach(bulletAppState);
        bulletAppState.setEnabled(false); // No physics needed for menu

        road = new RoadGenerator();
        generator = new TerrainGenerator(bulletAppState, backgroundNode, assetManager, road, sapp, executor,
                200, CHUNK_SIZE, SCALE, SEED, 200);
        manager = new ChunkManager(bulletAppState, backgroundNode, road, generator, sapp, executor,
                200, CHUNK_SIZE, SCALE, 1);
        generator.setChunkManager(manager);

        // Generate initial terrain chunks
        generator.CreateTerrain();

        // Set sky color
        sapp.getViewPort().setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
    }

    private void setUpLight() {
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.5f));
        backgroundNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        backgroundNode.addLight(dl);
    }

    private void createMainMenu() {
        menu = new Container();

        Label title = menu.addChild(new Label("DRIVE", menu.getElementId().child("title")));
        title.setFontSize(48);

        Button startGame = menu.addChild(new Button("Start Game"));
        startGame.addClickCommands(source -> startGame());

        Button selectCar = menu.addChild(new Button("Select Car"));
        selectCar.addClickCommands(source -> showCarSelection());

        Button multiplayer = menu.addChild(new Button("Multiplayer (Coming Soon)"));
        multiplayer.setEnabled(false);

        Button quit = menu.addChild(new Button("Quit"));
        quit.addClickCommands(source -> sapp.stop());

        // Attach to guiNode
        sapp.getGuiNode().attachChild(menu);
        centerMenu();
    }

    private void showCarSelection() {
        if (carSelectionMenu == null) {
            createCarSelectionMenu();
        }

        menu.removeFromParent();
        sapp.getGuiNode().attachChild(carSelectionMenu);
        showingCarSelection = true;
        centerMenu();
    }

    private void createCarSelectionMenu() {
        carSelectionMenu = new Container();

        Label title = carSelectionMenu.addChild(new Label("Select Your Car"));
        title.setFontSize(32);

        // Current car display
        selectedCarLabel = carSelectionMenu.addChild(new Label(selectedCar));
        selectedCarLabel.setFontSize(24);

        // Previous/Next buttons
        Container navigationContainer = carSelectionMenu.addChild(new Container());

        Button prevButton = navigationContainer.addChild(new Button("< Previous"));
        prevButton.addClickCommands(source -> {
            currentCarIndex = (currentCarIndex - 1 + availableCars.length) % availableCars.length;
            selectedCar = availableCars[currentCarIndex];
            selectedCarLabel.setText(selectedCar);
        });

        Button nextButton = navigationContainer.addChild(new Button("Next >"));
        nextButton.addClickCommands(source -> {
            currentCarIndex = (currentCarIndex + 1) % availableCars.length;
            selectedCar = availableCars[currentCarIndex];
            selectedCarLabel.setText(selectedCar);
        });

        Button backButton = carSelectionMenu.addChild(new Button("Back"));
        backButton.addClickCommands(source -> {
            carSelectionMenu.removeFromParent();
            sapp.getGuiNode().attachChild(menu);
            showingCarSelection = false;
            centerMenu();
        });
    }

    private void startGame() {
        // Detach this state and show loading screen
        getStateManager().detach(this);
        getStateManager().attach(new LoadingState(selectedCar));
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);

        // Rotate camera around the terrain
        cameraAngle += tpf * 0.1f; // Slow rotation
        updateCameraPosition();
    }

    private void updateCameraPosition() {
        float x = cameraTarget.x + FastMath.cos(cameraAngle) * cameraDistance;
        float z = cameraTarget.z + FastMath.sin(cameraAngle) * cameraDistance;
        float y = cameraTarget.y + 50;

        cam.setLocation(new Vector3f(x, y, z));
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    public void centerMenu() {
        float width = cam.getWidth();
        float height = cam.getHeight();

        Container activeMenu = showingCarSelection ? carSelectionMenu : menu;

        float menuWidth = activeMenu.getPreferredSize().x;
        float menuHeight = activeMenu.getPreferredSize().y;

        activeMenu.setLocalTranslation(
                (width - menuWidth) / 2f,
                (height + menuHeight) / 2f,
                0
        );
    }

    @Override
    protected void cleanup(Application app) {
        // Clean up menus
        if (menu != null) {
            menu.removeFromParent();
            menu = null;
        }
        if (carSelectionMenu != null) {
            carSelectionMenu.removeFromParent();
            carSelectionMenu = null;
        }

        // Clean up background terrain
        if (backgroundNode != null) {
            backgroundNode.removeFromParent();
            backgroundNode = null;
        }

        // Clean up bullet state
        if (bulletAppState != null) {
            sapp.getStateManager().detach(bulletAppState);
            bulletAppState = null;
        }

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    protected void onEnable() {
        sapp.getGuiNode().attachChild(guiNode);
        sapp.getInputManager().setCursorVisible(true);
    }

    @Override
    protected void onDisable() {
        guiNode.removeFromParent();
        sapp.getInputManager().setCursorVisible(false);
    }
}

