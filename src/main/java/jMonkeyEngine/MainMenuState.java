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
import jMonkeyEngine.Chunks.ChunkCoord;
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Entities.Cars.Car;
import jMonkeyEngine.Road.RoadGenerator;
import jMonkeyEngine.Terrain.TerrainGenerator;

import jMonkeyEngine.Terrain.TerrainSerializer;
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

    // Add animation variables
    private float menuAnimationProgress = 0f;
    private float menuTargetX = 0f;
    private float menuStartX = 0f;
    private boolean isAnimatingMenu = false;
    private static final float MENU_ANIMATION_SPEED = 3f;

    // Camera adjustment for car selection
    private float originalCameraDistance;
    private float targetCameraDistance;
    private boolean isAdjustingCamera = false;

    private static final String MENU_TERRAIN_NAME = "main_menu_terrain";

    private ExecutorService carLoadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isLoadingCar = false;
    private String carBeingLoaded = null;

    private ExecutorService terrainLoadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isLoadingTerrain = false;
    private String terrainBeingLoaded = null;

    @Override
    protected void initialize(Application app) {
        sapp = (SimpleApplication) app;
        cam = sapp.getCamera();
        assetManager = sapp.getAssetManager();

        // Set default camera target before terrain loads
        cameraTarget = new Vector3f((CHUNK_SIZE / 2f) * (SCALE / 16f), 20, (CHUNK_SIZE / 2f) * (SCALE / 16f));

        // Set up background terrain
        initBackgroundTerrain();

        // Create main menu
        createMainMenu();

        // Set up lights
        setUpLight();

        // Initialize camera position
        updateCameraPosition();

        originalCameraDistance = cameraDistance;
    }

    private void initBackgroundTerrain() {
        sapp.getRootNode().attachChild(backgroundNode);

        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        bulletAppState = new BulletAppState();
        sapp.getStateManager().attach(bulletAppState);
        bulletAppState.setEnabled(false);

        road = new RoadGenerator();
        generator = new TerrainGenerator(bulletAppState, backgroundNode, assetManager, road, sapp, executor,
                                         200, CHUNK_SIZE, SCALE, SEED, 200);
        manager = new ChunkManager(bulletAppState, backgroundNode, road, generator, sapp, executor,
                                   200, CHUNK_SIZE, SCALE, 1);
        generator.setChunkManager(manager);

        // Load on background thread
        terrainLoadExecutor.submit(() -> {
            try {
                // Only do file I/O on background thread
                boolean terrainExists = TerrainSerializer.terrainExists(MENU_TERRAIN_NAME);

                // Switch to render thread for all JME operations
                sapp.enqueue(() -> {
                    try {
                        isLoadingTerrain = true;
                        terrainBeingLoaded = MENU_TERRAIN_NAME;

                        boolean loaded = false;
                        if (terrainExists) {
                            loaded = generator.loadSavedTerrain(MENU_TERRAIN_NAME);
                            System.out.println("Loaded pre-generated menu terrain");
                        }

                        if (!loaded) {
                            System.out.println("Generating new menu terrain...");
                            generator.CreateTerrain();

                            // Save in background after generation
                            terrainLoadExecutor.submit(() -> {
                                generator.saveGeneratedTerrain(MENU_TERRAIN_NAME);
                            });
                        }

                        System.out.println("Saved generated geometries count: " +
                                                   generator.getGeneratedChildGeometries().size());

                        // Set camera target
                        float chunkCenterX = (CHUNK_SIZE / 2f) * (SCALE / 16f);
                        float chunkCenterZ = (CHUNK_SIZE / 2f) * (SCALE / 16f);
                        float centerHeight = manager.getHeight(200, 0, (int) (chunkCenterZ / (SCALE / 16f)),
                                                               new ChunkCoord(0, 0));
                        cameraTarget = new Vector3f(chunkCenterX, centerHeight + 20, chunkCenterZ);

                        // Set sky color
                        sapp.getViewPort().setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));

                    } catch (Exception e) {
                        System.err.println("Failed to load terrain: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        isLoadingTerrain = false;
                        terrainBeingLoaded = null;
                    }
                    return null;
                });

            } catch (Exception e) {
                System.err.println("Failed to check terrain: " + e.getMessage());
                e.printStackTrace();
                isLoadingTerrain = false;
                terrainBeingLoaded = null;
            }
        });
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

        // Start animation to move menu to the left
        animateMenuToLeft();

        // Zoom in camera slightly for better car view
        targetCameraDistance = cameraDistance * 0.7f;
        isAdjustingCamera = true;
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

            // Animate menu back to center
            animateMenuToCenter();

            // Reset camera distance
            targetCameraDistance = originalCameraDistance;
            isAdjustingCamera = true;
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

        // Animate camera distance
        if (isAdjustingCamera) {
            float diff = targetCameraDistance - cameraDistance;
            if (Math.abs(diff) < 0.1f) {
                cameraDistance = targetCameraDistance;
                isAdjustingCamera = false;
            } else {
                cameraDistance += diff * tpf * 2f;
            }
        }

        updateCameraPosition();

        // Animate menu position
        if (isAnimatingMenu) {
            menuAnimationProgress += tpf * MENU_ANIMATION_SPEED;
            if (menuAnimationProgress >= 1f) {
                menuAnimationProgress = 1f;
                isAnimatingMenu = false;
            }

            // Smooth interpolation (ease-in-out)
            float t = menuAnimationProgress;
            float smoothT = t * t * (3f - 2f * t);

            Container activeMenu = showingCarSelection ? carSelectionMenu : menu;
            float currentX = menuStartX + (menuTargetX - menuStartX) * smoothT;

            float height = cam.getHeight();
            float menuHeight = activeMenu.getPreferredSize().y;
            activeMenu.setLocalTranslation(currentX, (height + menuHeight) / 2f, 0);
        }

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

    private void animateMenuToLeft() {
        float width = cam.getWidth();
        float menuWidth = carSelectionMenu.getPreferredSize().x;

        // Current centered position
        menuStartX = (width - menuWidth) / 2f;

        // Target position: left side with padding
        menuTargetX = 50f;

        menuAnimationProgress = 0f;
        isAnimatingMenu = true;
    }

    private void animateMenuToCenter() {
        float width = cam.getWidth();
        float menuWidth = menu.getPreferredSize().x;

        // Current position (left side)
        menuStartX = 50f;

        // Target position: centered
        menuTargetX = (width - menuWidth) / 2f;

        menuAnimationProgress = 0f;
        isAnimatingMenu = true;
    }

    private void updateCarPreviewPosition() {
        // Position car preview on the right side of the screen
        Vector3f camPos = cam.getLocation();
        Vector3f camDir = cam.getDirection().normalize();
        Vector3f camRight = cam.getLeft().mult(-1).normalize();

        // Place car in front and to the right of camera
        Vector3f carPos = camPos.add(camDir.mult(10f));
        carPos = carPos.add(camRight.mult(1.25f)); // Offset to the right
        carPos.y += -0.25f; // Lower it up slightly

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
        // Prevent loading the same car twice
        if (isLoadingCar && carName.equals(carBeingLoaded)) {
            System.out.println("Already loading car: " + carName);
            return;
        }

        // Clear existing car model
        if (car != null && car.getCarNode() != null) {
            carPreviewNode.detachChild(car.getCarNode());
            System.out.println("Removed previous car model");
        }

        isLoadingCar = true;
        carBeingLoaded = carName;

        System.out.println("Loading car preview for: " + carName);

        // Load on background thread
        carLoadExecutor.submit(() -> {
            try {
                // Instantiate and load car on background thread
                jMonkeyEngine.Entities.Cars.Car loadedCar = instantiateCarByName(carName);

                if (loadedCar == null) {
                    System.err.println("Failed to instantiate car: " + carName);
                    isLoadingCar = false;
                    return;
                }

                System.out.println("Car instantiated: " + loadedCar.getClass().getName());

                // Load the car model (heavy operation)
                loadedCar.load(assetManager, bulletAppState);

                System.out.println("Car loaded successfully on background thread");

                // Attach to scene on main render thread
                sapp.enqueue(() -> {
                    try {
                        car = loadedCar;
                        currentCarModel = car.getCarNode();

                        if (currentCarModel != null) {
                            currentCarModel.setLocalScale(1f);
                            currentCarModel.setLocalTranslation(0, 0, 0);
                            carRotation = 0f;
                            carPreviewNode.attachChild(currentCarModel);
                            carPreviewNode.updateGeometricState();

                            System.out.println("Successfully attached car model: " + carName);
                            System.out.println("Car node has " + ((Node) currentCarModel).getChildren().size() + " children");
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to attach car: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        isLoadingCar = false;
                        carBeingLoaded = null;
                    }
                    return null;
                });

            } catch (Exception e) {
                System.err.println("Failed to load car preview for " + carName + ": " + e.getMessage());
                e.printStackTrace();
                isLoadingCar = false;
                carBeingLoaded = null;
            }
        });
    }

    private Car instantiateCarByName(String carName) {
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
        if (cameraTarget == null) {
            // Fallback position if terrain hasn't loaded yet
            cam.setLocation(new Vector3f(1200, 70, 1200));
            cam.lookAt(new Vector3f((CHUNK_SIZE / 2f) * (SCALE / 16f), 20, (CHUNK_SIZE / 2f) * (SCALE / 16f)), Vector3f.UNIT_Y);
            return;
        }

        float x = cameraTarget.x + FastMath.cos(cameraAngle) * cameraDistance;
        float z = cameraTarget.z + FastMath.sin(cameraAngle) * cameraDistance;
        float y = cameraTarget.y + 50;

        cam.setLocation(new Vector3f(x, y, z));
        cam.lookAt(cameraTarget, Vector3f.UNIT_Y);
    }

    public void centerMenu() {
        // Only center if not animating and not showing car selection
        if (!isAnimatingMenu && !showingCarSelection) {
            float width = cam.getWidth();
            float height = cam.getHeight();

            Container activeMenu = menu;

            float menuWidth = activeMenu.getPreferredSize().x;
            float menuHeight = activeMenu.getPreferredSize().y;

            activeMenu.setLocalTranslation(
                    (width - menuWidth) / 2f,
                    (height + menuHeight) / 2f,
                    0
            );
        }
    }

    @Override
    protected void cleanup(Application app) {
        // Clean up car preview
        cleanupCarPreview();

        // Shutdown terrain loading executor
        if (terrainLoadExecutor != null && !terrainLoadExecutor.isShutdown()) {
            terrainLoadExecutor.shutdownNow();
            terrainLoadExecutor = null;
        }

        // Shutdown car loading executor
        if (carLoadExecutor != null && !carLoadExecutor.isShutdown()) {
            carLoadExecutor.shutdownNow();
            carLoadExecutor = null;
        }

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
