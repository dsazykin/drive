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

    // Car preview
    private Node carPreviewNode;
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

    jMonkeyEngine.Entities.Cars.Car car;

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

        // Initialize car preview (if not already done)
        if (carPreviewNode == null) {
            initCarPreview();
        } else {
            // Update position in case camera moved
            updateCarPreviewPosition();
        }

        // Load the car preview
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

        // Update car preview position to follow camera and rotate
        if (showingCarSelection && carPreviewNode != null) {
            updateCarPreviewPosition();

            // Rotate car slowly
            if (currentCarModel != null) {
                carRotation += tpf * 0.5f;
                Quaternion rotation = new Quaternion();
                rotation.fromAngles(0, carRotation, 0);
                currentCarModel.setLocalRotation(rotation);
            }
        }
    }

    private void updateCarPreviewPosition() {
        // Position car preview in front of camera
        Vector3f camPos = cam.getLocation();
        Vector3f camDir = cam.getDirection().normalize();

        // Place car 25 units in front of camera, slightly above
        Vector3f carPos = camPos.add(camDir.mult(15f));
        carPos.y += 2.5f; // Lift it up 5 units above camera direction
        if (car != null) {
            car.getCarNode().setLocalTranslation(carPos);
            car.getControl().setPhysicsLocation(carPos);
        }

    }

    private void initCarPreview() {
        if (carPreviewNode != null) {
            return; // Already initialized
        }

        // Create a node for the car preview
        carPreviewNode = new Node("CarPreview");

        // Add lights to preview
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.8f));
        carPreviewNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(-1, -1, -1).normalizeLocal());
        carPreviewNode.addLight(dl);

        // Attach the preview node to the background scene
        backgroundNode.attachChild(carPreviewNode);

        // Position it in front of camera
        updateCarPreviewPosition();

        System.out.println("Car preview node initialized at: " + carPreviewNode.getLocalTranslation());
    }

    private void loadCarPreview(String carName) {
        // Clear existing car model
        if (car != null && car.getCarNode() != null) {
            carPreviewNode.detachChild(car.getCarNode());
            System.out.println("Removed previous car model");
        }

        System.out.println("Loading car preview for: " + carName);

        try {
            // Instantiate the actual car object
            car = instantiateCarByName(carName);

            if (car == null) {
                System.err.println("Failed to instantiate car: " + carName);
                return;
            }

            System.out.println("Car instantiated: " + car.getClass().getName());

            // Load the car (this creates the model)
            car.load(assetManager, bulletAppState);

            System.out.println("Car loaded and initialized");

            // Get the car's node
            currentCarModel = car.getCarNode();

            System.out.println("Car node retrieved: " + currentCarModel);
            if (currentCarModel != null) {
                System.out.println("Car node has " + ((Node) currentCarModel).getChildren().size() + " children");
            }

            // Scale up the car
            currentCarModel.setLocalScale(1f);
            currentCarModel.setLocalTranslation(0, 0, 0);
            carRotation = 0f;
            carPreviewNode.attachChild(currentCarModel);

            // Force update
            carPreviewNode.updateGeometricState();

            System.out.println("Successfully loaded car model: " + carName);
            System.out.println("Distance from camera: " + cam.getLocation().distance(currentCarModel.getWorldTranslation()));
        } catch (Exception e) {
            System.err.println("Failed to load car preview for " + carName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private jMonkeyEngine.Entities.Cars.Car instantiateCarByName(String carName) {
        switch (carName) {
            case "Nismo":
                return new jMonkeyEngine.Entities.Cars.VehicleModels.Nismo();
            case "GrandTourer":
                return new jMonkeyEngine.Entities.Cars.VehicleModels.GrandTourer();
            case "PickupTruck":
                return new jMonkeyEngine.Entities.Cars.VehicleModels.PickupTruck();
            case "Rotator":
                return new jMonkeyEngine.Entities.Cars.VehicleModels.Rotator();
            default:
                return new jMonkeyEngine.Entities.Cars.VehicleModels.Nismo();
        }
    }

    private void cleanupCarPreview() {
        if (carPreviewNode != null) {
            carPreviewNode.detachAllChildren();
            carPreviewNode.removeFromParent();
            carPreviewNode = null;
        }
        currentCarModel = null;
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

