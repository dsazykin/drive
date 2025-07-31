package jMonkeyEngine;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.*;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.AppSettings;
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Entities.Car;
import jMonkeyEngine.Road.RoadGenerator;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main extends SimpleApplication
        implements ActionListener {

    BulletAppState bulletAppState;
    ExecutorService executor;

    TerrainGenerator generator;
    ChunkManager manager;
    RoadGenerator road;

    private Car car;
    private float spawnHeight;

    private Node guiGroupNode;
    private BitmapText speedText;
    private BitmapText frontLeftText;
    private BitmapText frontRightText;
    private BitmapText rearLeftText;
    private BitmapText rearRightText;
    private BitmapText loadingText;
    private BitmapText chunkX;
    private BitmapText chunkZ;
    private BitmapText pauseText;

    private boolean loadingDone = false;
    private boolean isPaused = false;
    private Node pauseMenuNode;
    private boolean guiLoaded = false;

    private Vector3f cameraPos = new Vector3f();
    private boolean followCam = true;
    private boolean gui = false;

    private final int CHUNK_SIZE = 100;
    private final float SCALE = 40f;
    private long SEED;

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
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        SEED = FastMath.nextRandomInt(0, 1000000000);
        System.out.println("Seed: " + SEED);

        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        enablePlayerControls(false);
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(300);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        road = new RoadGenerator(CHUNK_SIZE, SCALE, SEED);
        generator = new TerrainGenerator(bulletAppState, rootNode, assetManager, road, this, executor,
                                         CHUNK_SIZE, SCALE, SEED, 200);
        this.manager =
                new ChunkManager(bulletAppState, rootNode, road, generator, this, executor, CHUNK_SIZE,
                                 SCALE, 3, 200);
        generator.setChunkManager(manager);

        spawnHeight = generator.getSpawnHeight() + 0.5f;

        guiGroupNode = new Node("guiGroupNode");

        setUpKeys();
        setUpLight();
        loadScene();
        initCar();
        loadGUI();
        initPauseMenu();
    }

    @Override
    public void reshape(int w, int h) {
        super.reshape(w, h);
        if (guiLoaded) {
            reloadGUI();
        }
    }

    private void loadGUI() {
        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");

        loadingText = new BitmapText(guiFont, false);
        loadingText.setSize(guiFont.getCharSet().getRenderedSize());
        loadingText.setText("Loading terrain...");
        loadingText.setLocalTranslation(300, 300, 0);
        guiNode.attachChild(loadingText);

        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0);
        guiNode.attachChild(speedText);

        frontLeftText = new BitmapText(guiFont, false);
        frontLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        frontLeftText.setLocalTranslation(10, cam.getHeight() - 50, 0);
        guiGroupNode.attachChild(frontLeftText);

        frontRightText = new BitmapText(guiFont, false);
        frontRightText.setSize(guiFont.getCharSet().getRenderedSize());
        frontRightText.setLocalTranslation(130, cam.getHeight() - 50, 0);
        guiGroupNode.attachChild(frontRightText);

        rearLeftText = new BitmapText(guiFont, false);
        rearLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        rearLeftText.setLocalTranslation(10, cam.getHeight() - 70, 0);
        guiGroupNode.attachChild(rearLeftText);

        rearRightText = new BitmapText(guiFont, false);
        rearRightText.setSize(guiFont.getCharSet().getRenderedSize());
        rearRightText.setLocalTranslation(130, cam.getHeight() - 70, 0); // top-left corner
        guiGroupNode.attachChild(rearRightText);

        chunkX = new BitmapText(guiFont, false);
        chunkX.setSize(guiFont.getCharSet().getRenderedSize());
        chunkX.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 10, 0);
        guiGroupNode.attachChild(chunkX);

        chunkZ = new BitmapText(guiFont, false);
        chunkZ.setSize(guiFont.getCharSet().getRenderedSize());
        chunkZ.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 30, 0);
        guiGroupNode.attachChild(chunkZ);

        guiLoaded = true;
    }

    private void reloadGUI() {
        guiGroupNode.detachAllChildren();
        pauseMenuNode.detachAllChildren();
        guiNode.detachChild(speedText);

        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0);
        guiNode.attachChild(speedText);

        frontLeftText = new BitmapText(guiFont, false);
        frontLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        frontLeftText.setLocalTranslation(10, cam.getHeight() - 50, 0);
        guiGroupNode.attachChild(frontLeftText);

        frontRightText = new BitmapText(guiFont, false);
        frontRightText.setSize(guiFont.getCharSet().getRenderedSize());
        frontRightText.setLocalTranslation(130, cam.getHeight() - 50, 0);
        guiGroupNode.attachChild(frontRightText);

        rearLeftText = new BitmapText(guiFont, false);
        rearLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        rearLeftText.setLocalTranslation(10, cam.getHeight() - 70, 0);
        guiGroupNode.attachChild(rearLeftText);

        rearRightText = new BitmapText(guiFont, false);
        rearRightText.setSize(guiFont.getCharSet().getRenderedSize());
        rearRightText.setLocalTranslation(130, cam.getHeight() - 70, 0);
        guiGroupNode.attachChild(rearRightText);

        chunkX = new BitmapText(guiFont, false);
        chunkX.setSize(guiFont.getCharSet().getRenderedSize());
        chunkX.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 10, 0);
        guiGroupNode.attachChild(chunkX);

        chunkZ = new BitmapText(guiFont, false);
        chunkZ.setSize(guiFont.getCharSet().getRenderedSize());
        chunkZ.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 30, 0);
        guiGroupNode.attachChild(chunkZ);

        pauseText = new BitmapText(guiFont);
        pauseText.setText("Game Paused\nPress ESC to Resume\nPress Q to Quit");
        pauseText.setLocalTranslation((float) cam.getWidth() / 4, (float) cam.getHeight() / 2, 0);
        pauseMenuNode.attachChild(pauseText);
    }

    private void initPauseMenu() {
        pauseMenuNode = new Node("PauseMenu");

        pauseText = new BitmapText(guiFont);
        pauseText.setText("Game Paused\nPress ESC to Resume\nPress Q to Quit");
        pauseText.setLocalTranslation((float) cam.getWidth() / 4, (float) cam.getHeight() / 2, 0);
        pauseMenuNode.attachChild(pauseText);

        guiNode.attachChild(pauseMenuNode);
        pauseMenuNode.setCullHint(Spatial.CullHint.Always);
    }

    private void loadScene() {
        generator.CreateTerrain();
    }

    private void initCar() {
        car = new Car(assetManager, bulletAppState.getPhysicsSpace());
        // Set desired spawn location
        Vector3f spawnPosition = new Vector3f(5f, spawnHeight, 5f);
        Quaternion rotation = new Quaternion();
        rotation.fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y);

        // Apply to the physics control (VehicleControl or similar)
        car.getControl().setPhysicsLocation(spawnPosition);
        car.getControl().setPhysicsRotation(rotation);

        // Optionally also apply to the visual node (if needed)
        car.getCarNode().setLocalTranslation(spawnPosition);
        car.getCarNode().rotate(rotation);

        rootNode.attachChild(car.getCarNode());
    }

    private void setUpLight() {
        // We add light so we see the scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        rootNode.addLight(dl);
    }

    private void setUpKeys() {
        inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);

        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Accelerate", new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("Break", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("Cam", new KeyTrigger(KeyInput.KEY_C));
        inputManager.addMapping("Reset", new KeyTrigger(KeyInput.KEY_R));
        inputManager.addMapping("GUI", new KeyTrigger(KeyInput.KEY_G));
        inputManager.addMapping("Pause", new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping("Quit", new KeyTrigger(KeyInput.KEY_Q));

        inputManager.addListener(this, "Left");
        inputManager.addListener(this, "Right");
        inputManager.addListener(this, "Accelerate");
        inputManager.addListener(this, "Break");
        inputManager.addListener(this, "Cam");
        inputManager.addListener(this, "Reset");
        inputManager.addListener(this, "GUI");
        inputManager.addListener(this, "Pause");
        inputManager.addListener(this, "Quit");
    }

    private void enablePlayerControls(boolean enabled) {
        flyCam.setEnabled(enabled);
        inputManager.setCursorVisible(!enabled);
    }

    @Override
    public void onAction(String binding, boolean value, float tpf) {
        if (binding.equals("Left")) {
            if (value) {
                car.setTargetSteeringValue(1f);
            } else {
                car.setTargetSteeringValue(0f);
            }
        } else if (binding.equals("Right")) {
            if (value) {
                car.setTargetSteeringValue(-1f);
            } else {
                car.setTargetSteeringValue(0f);
            }
        } else if (binding.equals("Accelerate")) {
            car.setAccelerating(value);
        } else if (binding.equals("Break")) {
            car.setBreaking(value);
        }

        if (binding.equals("Cam") && !value) {
            followCam = !followCam;
        }

        if (binding.equals("Reset") && !value) {
            VehicleControl control = car.getControl();
            control.setLinearVelocity(new Vector3f(0,0,0));
            control.setAngularVelocity(new Vector3f(0,0,0));
            Vector3f resetPosition = new Vector3f(5f, spawnHeight, 5f);
            car.getControl().setPhysicsLocation(resetPosition);
            car.getControl().setPhysicsRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y));

            car.getCarNode().setLocalTranslation(resetPosition);
            car.getCarNode().setLocalRotation(new Quaternion().fromAngleAxis(-FastMath.HALF_PI, Vector3f.UNIT_Y));
        }

        if (binding.equals("GUI") && !value) {
            gui = !gui;
            if (gui) {
                guiNode.attachChild(guiGroupNode);
            } else {
                guiNode.detachChild(guiGroupNode);
            }
        }

        if (binding.equals("Pause") && !value) {
           togglePause();
        }

        if (binding.equals("Quit") && !value && isPaused) {
           stop();
        }
    }

    private void togglePause() {
        isPaused = !isPaused;

        if (isPaused) {
            pauseMenuNode.setCullHint(Spatial.CullHint.Never); // Show menu
            inputManager.setCursorVisible(true);
            flyCam.setEnabled(false);
            bulletAppState.setEnabled(false); // Pause physics
            // Pause your own game logic here too
        } else {
            pauseMenuNode.setCullHint(Spatial.CullHint.Always); // Hide menu
            inputManager.setCursorVisible(false);
            flyCam.setEnabled(true);
            bulletAppState.setEnabled(true); // Resume physics
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (!loadingDone && generator.getChunkTasks() != null) {
            boolean allDone = generator.getChunkTasks().stream().allMatch(Future::isDone);
            if (allDone) {
                loadingDone = true;
                enqueue(() -> {
                    enqueue(() -> guiNode.detachChild(loadingText));
                    enablePlayerControls(true);
                    return null;
                });
            }
        } else {
            VehicleControl control = car.getControl();
            manager.updateChunks(cam.getLocation());

            // 1. Get current speed
            float speed = -control.getCurrentVehicleSpeedKmHour();
            float velocity = speed / 3.6f;

            car.weightTransfer(velocity, speed);
            car.move(velocity, speed);
            car.steer(speed, tpf);

            if (followCam) {
                followCam(tpf, control);
            }

            speedText.setText(String.format("Speed: %.1f km/h", speed));
            if (gui) {
                updateGUI(control);
            }
        }
    }

    private void followCam(float tpf, VehicleControl control) {
        Vector3f forward = control.getForwardVector(null).normalizeLocal();

        // === SMOOTH CAMERA FOLLOW ===
        Vector3f targetCamPos =
                control.getPhysicsLocation().add(forward.negate().mult(-10f)) // 20 units behind
                        .add(0, 4f, 0);

        // Interpolate camera position
        float lerpSpeed = 5f; // higher = faster
        cameraPos.interpolateLocal(targetCamPos, lerpSpeed * tpf);
        cam.setLocation(cameraPos);

        // Look at the player (can be smoothed as well if needed)
        cam.lookAt(control.getPhysicsLocation().add(0, 2f, 0), Vector3f.UNIT_Y);
    }

    private void updateGUI(VehicleControl control) {
        frontLeftText.setText(String.format("FL: %.1f", control.getWheel(0).getFrictionSlip()));
        frontRightText.setText(String.format("FR: %.1f", control.getWheel(1).getFrictionSlip()));
        rearLeftText.setText(String.format("RL: %.1f", control.getWheel(2).getFrictionSlip()));
        rearRightText.setText(String.format("RR: %.1f", control.getWheel(3).getFrictionSlip()));
        chunkX.setText(String.format("X Coord: %.1f",
                                     Math.floor(cam.getLocation().x / ((CHUNK_SIZE - 1) * (SCALE / 16)))));
        chunkZ.setText(String.format("Z Coord: %.1f",
                                     Math.floor(cam.getLocation().z / ((CHUNK_SIZE - 1) * (SCALE / 16)))));
    }

    @Override
    public void stop() {
        executor.shutdown();
        super.stop();
    }
}