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
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture2D;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.Container;
import com.simsilica.lemur.component.QuadBackgroundComponent;
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

    // Car preview
    private Node carPreviewNode;
    private Camera previewCam;
    private com.jme3.renderer.ViewPort previewViewport;
    private float carRotation = 0f;
    private Spatial currentCarModel;

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
    private Vector3f cameraTarget;

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

        // Set camera target to chunk center
        float chunkCenterX = 0; // Center of chunk 0,0
        float chunkCenterZ = (CHUNK_SIZE / 2f) * (SCALE / 16f);
        float centerHeight = manager.getHeight(200, 0, (int)(chunkCenterZ / (SCALE / 16f)), new jMonkeyEngine.Chunks.ChunkCoord(0, 0));
        cameraTarget = new Vector3f(chunkCenterX, centerHeight + 20, chunkCenterZ);

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

        // Initialize car preview
        initCarPreview();
        loadCarPreview(selectedCar);

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
            loadCarPreview(selectedCar);
        });

        Button nextButton = navigationContainer.addChild(new Button("Next >"));
        nextButton.addClickCommands(source -> {
            currentCarIndex = (currentCarIndex + 1) % availableCars.length;
            selectedCar = availableCars[currentCarIndex];
            selectedCarLabel.setText(selectedCar);
            loadCarPreview(selectedCar);
        });

        Button backButton = carSelectionMenu.addChild(new Button("Back"));
        backButton.addClickCommands(source -> {
            carSelectionMenu.removeFromParent();
            sapp.getGuiNode().attachChild(menu);
            showingCarSelection = false;
            cleanupCarPreview();
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

        // Rotate car preview if in selection menu
        if (showingCarSelection && currentCarModel != null) {
            carRotation += tpf * 0.5f; // Rotate car slowly
            currentCarModel.setLocalRotation(new Quaternion().fromAngles(0, carRotation, 0));
        }
    }

    private void initCarPreview() {
        if (previewViewport != null) {
            return; // Already initialized
        }

        // Create a separate scene for car preview
        carPreviewNode = new Node("CarPreview");

        // Create preview camera
        previewCam = new Camera(256, 256);
        previewCam.setFrustumPerspective(45f, 1f, 0.1f, 100f);
        previewCam.setLocation(new Vector3f(8, 3, 8));
        previewCam.lookAt(new Vector3f(0, 1, 0), Vector3f.UNIT_Y);

        // Create viewport for preview
        previewViewport = sapp.getRenderManager().createMainView("CarPreview", previewCam);
        previewViewport.setClearFlags(true, true, true);
        previewViewport.setBackgroundColor(new ColorRGBA(0.2f, 0.2f, 0.3f, 1f));
        previewViewport.attachScene(carPreviewNode);

        // Add lights to preview scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.8f));
        carPreviewNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        carPreviewNode.addLight(dl);

        // Create a picture element to display the preview
        Texture2D previewTexture = new Texture2D(256, 256, com.jme3.texture.Image.Format.RGBA8);
        previewViewport.setOutputFrameBuffer(null);

        // Position preview on screen (top right area)
        com.jme3.ui.Picture previewPicture = new com.jme3.ui.Picture("CarPreviewPicture");
        previewPicture.setTexture(assetManager, previewTexture, true);
        previewPicture.setWidth(300);
        previewPicture.setHeight(300);
        previewPicture.setPosition(cam.getWidth() - 350, cam.getHeight() - 350);

        // Add border/background
        QuadBackgroundComponent border = new QuadBackgroundComponent(new ColorRGBA(0.3f, 0.3f, 0.4f, 0.9f));

        sapp.getGuiNode().attachChild(previewPicture);
    }

    private void loadCarPreview(String carName) {
        // Clear existing car model
        if (currentCarModel != null) {
            carPreviewNode.detachChild(currentCarModel);
            currentCarModel = null;
        }

        // Load the car model based on selection using the actual model paths
        String modelFolder = getCarModelFolder(carName);
        if (modelFolder != null) {
            try {
                currentCarModel = assetManager.loadModel(modelFolder + "/scene.gltf");
                currentCarModel.setLocalScale(1f);
                currentCarModel.setLocalTranslation(0, 0, 0);
                carRotation = 0f;
                carPreviewNode.attachChild(currentCarModel);
            } catch (Exception e) {
                System.err.println("Failed to load car preview for " + carName + ": " + e.getMessage());
                // Create a simple placeholder if model fails to load
            }
        }
    }

    private String getCarModelFolder(String carName) {
        switch (carName) {
            case "Nismo":
                return "gtr_nismo";
            case "GrandTourer":
                return "grand_tourer";
            case "PickupTruck":
                return "pickup_truck";
            case "Rotator":
                return "rotator";
            default:
                return "gtr_nismo";
        }
    }

    private void cleanupCarPreview() {
        if (previewViewport != null) {
            sapp.getRenderManager().removeMainView(previewViewport);
            previewViewport = null;
        }

        if (carPreviewNode != null) {
            carPreviewNode.detachAllChildren();
            carPreviewNode = null;
        }

        currentCarModel = null;

        // Remove preview picture from GUI
        Spatial previewPicture = sapp.getGuiNode().getChild("CarPreviewPicture");
        if (previewPicture != null) {
            previewPicture.removeFromParent();
        }
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
        // Clean up car preview
        cleanupCarPreview();

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

