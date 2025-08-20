package jMonkeyEngine;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.FlyByCamera;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Entities.Gtr;
import jMonkeyEngine.Road.RoadGenerator;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameplayState extends BaseAppState implements ActionListener {

    SimpleApplication sapp;

    Node rootNode;
    Node guiNode;
    InputManager inputManager;
    AssetManager assetManager;
    FlyByCamera flyCam;
    Camera cam;
    BitmapFont guiFont;

    BulletAppState bulletAppState;
    ExecutorService executor;

    TerrainGenerator generator;
    ChunkManager manager;
    RoadGenerator road;

    private Gtr sportsCar;
    private Vector3f resetPoint;

    private Node gameplayRoot;
    private Node hud;

    private BitmapText speedText;
    private BitmapText frontLeftText;
    private BitmapText frontRightText;
    private BitmapText rearLeftText;
    private BitmapText rearRightText;
    private BitmapText chunkX;
    private BitmapText chunkZ;
    private BitmapText pauseText;
    private BitmapText startText;

    private boolean loadingDone = false;
    private boolean isPaused = false;
    private Node pauseMenuNode;
    public boolean guiLoaded = false;
    private boolean started = false;

    private Vector3f cameraPos = new Vector3f();
    private boolean followCam = false;
    private boolean gui = false;

    private final int CHUNK_SIZE = 1000;
    private final float SCALE = 40f;
    private long SEED;

    @Override
    protected void initialize(Application app) {
        sapp = (SimpleApplication) app;

        rootNode = sapp.getRootNode();
        guiNode = sapp.getGuiNode();
        inputManager = sapp.getInputManager();
        assetManager = sapp.getAssetManager();
        flyCam = sapp.getFlyByCamera();
        cam = sapp.getCamera();

        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");

        gameplayRoot = new Node("Gameplay Root");
        hud = new Node("hud");

        rootNode.attachChild(gameplayRoot);

        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        SEED = FastMath.nextRandomInt(0, 1000000000);
        System.out.println("Seed: " + SEED);

        sapp.getViewPort().setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        enablePlayerControls(false);

        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(0);

        bulletAppState = new BulletAppState();
        sapp.getStateManager().attach(bulletAppState);
        bulletAppState.setEnabled(false);

        road = new RoadGenerator();
        generator = new TerrainGenerator(bulletAppState, gameplayRoot, assetManager, road, sapp, executor,
                                         200, CHUNK_SIZE, SCALE, SEED, 200);
        this.manager =
                new ChunkManager(bulletAppState, gameplayRoot, road, generator, sapp, executor,
                                 200, CHUNK_SIZE, SCALE, 2);
        generator.setChunkManager(manager);

        loadScene();
        System.out.println("loaded terrain");

        float zSpawn = (CHUNK_SIZE / 2) * (SCALE / 16);
        float spawnHeight = manager.getSpawnHeight(200);
        System.out.println(zSpawn);
        System.out.println(spawnHeight);
        resetPoint = new Vector3f(5f, spawnHeight + 1f, zSpawn);
        System.out.println("got reset point");

        cam.setLocation(new Vector3f(5f, spawnHeight + 20, zSpawn));
        cam.lookAt(manager.getCamDirection(spawnHeight), Vector3f.UNIT_Y);

        setUpKeys();
        System.out.println("set up keys");
        setUpLight();
        System.out.println("set up light");

        loadGUI();
        System.out.println("loaded gui");
        initPauseMenu();
        System.out.println("loaded pause menu");
        
        initCar();
        System.out.println("loaded car");

        loadingDone = true;
        enablePlayerControls(true);
    }

    @Override
    protected void onEnable() {
        // Runs when the state is attached
    }

    @Override
    protected void onDisable() {
        // Runs when the state is detached (e.g. pause or exit)
    }

    @Override
    protected void cleanup(Application app) {
        if (gameplayRoot != null) {
            gameplayRoot.removeFromParent();
            gameplayRoot = null;
        }

        if (hud != null) {
            hud.removeFromParent();
            hud = null;
        }

        if (bulletAppState != null) {
            getStateManager().detach(bulletAppState);
            bulletAppState = null;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Override
    public void update(float tpf) {
        super.update(tpf);

        if (loadingDone) {
            VehicleControl control = sportsCar.getControl();
            manager.updateChunks(sportsCar.getCarNode().getWorldTranslation());

            // 1. Get current speed
            float speed = control.getCurrentVehicleSpeedKmHour();
            float velocity = speed / 3.6f;

            sportsCar.weightTransfer(velocity, speed);
            sportsCar.move(velocity, speed);
            sportsCar.steer(speed, tpf);

            if (followCam) {
                followCam(tpf, control);
            }

            speedText.setText(String.format("Speed: %.1f km/h", speed));
            if (gui) {
                updateGUI(control);
            }
        }
    }

    @Override
    public void onAction(String binding, boolean value, float tpf) {
        if (binding.equals("Left")) {
            if (value) {
                sportsCar.setTargetSteeringValue(1f);
            } else {
                sportsCar.setTargetSteeringValue(0f);
            }
        } else if (binding.equals("Right")) {
            if (value) {
                sportsCar.setTargetSteeringValue(-1f);
            } else {
                sportsCar.setTargetSteeringValue(0f);
            }
        } else if (binding.equals("Accelerate")) {
            sportsCar.setAccelerating(value);
        } else if (binding.equals("Break")) {
            sportsCar.setBreaking(value);
        }

        if (binding.equals("Cam") && !value) {
            followCam = !followCam;
            if (!started) {
                started = true;
                guiNode.detachChild(startText);
                flyCam.setMoveSpeed(300);
                bulletAppState.setEnabled(true);
            }
        }

        if (binding.equals("Reset") && !value) {
            VehicleControl control = sportsCar.getControl();
            control.setLinearVelocity(new Vector3f(0,0,0));
            control.setAngularVelocity(new Vector3f(0,0,0));
            sportsCar.getControl().setPhysicsLocation(resetPoint);
            sportsCar.getControl().setPhysicsRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));

            sportsCar.getCarNode().setLocalTranslation(resetPoint);
            sportsCar.getCarNode().setLocalRotation(new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y));
        }

        if (binding.equals("GUI") && !value) {
            gui = !gui;
            if (gui) {
                guiNode.attachChild(hud);
            } else {
                guiNode.detachChild(hud);
            }
        }

        if (binding.equals("Pause") && !value) {
            togglePause();
        }

        if (binding.equals("Quit") && !value && isPaused) {
            sapp.stop();
        }

        if (binding.equals("Debug") && !value) {
            bulletAppState.setDebugEnabled(!bulletAppState.isDebugEnabled());
        }
    }

    private void loadGUI() {
        startText = new BitmapText(guiFont, false);
        startText.setSize(guiFont.getCharSet().getRenderedSize());
        startText.setText("Press 'C' to start");
        startText.setLocalTranslation(((float) cam.getWidth() / 2) - (startText.getLineWidth() / 2),
                                      (float) cam.getHeight() / 2, 0);
        guiNode.attachChild(startText);

        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0);
        guiNode.attachChild(speedText);

        frontLeftText = new BitmapText(guiFont, false);
        frontLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        frontLeftText.setLocalTranslation(10, cam.getHeight() - 50, 0);
        hud.attachChild(frontLeftText);

        frontRightText = new BitmapText(guiFont, false);
        frontRightText.setSize(guiFont.getCharSet().getRenderedSize());
        frontRightText.setLocalTranslation(130, cam.getHeight() - 50, 0);
        hud.attachChild(frontRightText);

        rearLeftText = new BitmapText(guiFont, false);
        rearLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        rearLeftText.setLocalTranslation(10, cam.getHeight() - 70, 0);
        hud.attachChild(rearLeftText);

        rearRightText = new BitmapText(guiFont, false);
        rearRightText.setSize(guiFont.getCharSet().getRenderedSize());
        rearRightText.setLocalTranslation(130, cam.getHeight() - 70, 0);
        hud.attachChild(rearRightText);

        chunkX = new BitmapText(guiFont, false);
        chunkX.setSize(guiFont.getCharSet().getRenderedSize());
        chunkX.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 10, 0);
        hud.attachChild(chunkX);

        chunkZ = new BitmapText(guiFont, false);
        chunkZ.setSize(guiFont.getCharSet().getRenderedSize());
        chunkZ.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 30, 0);
        hud.attachChild(chunkZ);

        guiLoaded = true;
    }

    public void reloadGUI() {
        hud.detachAllChildren();
        pauseMenuNode.detachAllChildren();
        hud.detachChild(speedText);

        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0);
        hud.attachChild(speedText);

        frontLeftText = new BitmapText(guiFont, false);
        frontLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        frontLeftText.setLocalTranslation(10, cam.getHeight() - 50, 0);
        hud.attachChild(frontLeftText);

        frontRightText = new BitmapText(guiFont, false);
        frontRightText.setSize(guiFont.getCharSet().getRenderedSize());
        frontRightText.setLocalTranslation(130, cam.getHeight() - 50, 0);
        hud.attachChild(frontRightText);

        rearLeftText = new BitmapText(guiFont, false);
        rearLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        rearLeftText.setLocalTranslation(10, cam.getHeight() - 70, 0);
        hud.attachChild(rearLeftText);

        rearRightText = new BitmapText(guiFont, false);
        rearRightText.setSize(guiFont.getCharSet().getRenderedSize());
        rearRightText.setLocalTranslation(130, cam.getHeight() - 70, 0);
        hud.attachChild(rearRightText);

        chunkX = new BitmapText(guiFont, false);
        chunkX.setSize(guiFont.getCharSet().getRenderedSize());
        chunkX.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 10, 0);
        hud.attachChild(chunkX);

        chunkZ = new BitmapText(guiFont, false);
        chunkZ.setSize(guiFont.getCharSet().getRenderedSize());
        chunkZ.setLocalTranslation(cam.getWidth() - 100, cam.getHeight() - 30, 0);
        hud.attachChild(chunkZ);

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
        sportsCar = new Gtr(sapp.getAssetManager(), bulletAppState.getPhysicsSpace());
        // Set desired spawn location
        Quaternion rotation = new Quaternion();
        rotation.fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);

        // Apply to the physics control (VehicleControl or similar)
        sportsCar.getControl().setPhysicsLocation(resetPoint);
        sportsCar.getControl().setPhysicsRotation(rotation);

        // Optionally also apply to the visual node (if needed)
        sportsCar.getCarNode().setLocalTranslation(resetPoint);
        sportsCar.getCarNode().rotate(rotation);

        gameplayRoot.attachChild(sportsCar.getCarNode());
    }

    private void setUpLight() {
        // We add light so we see the scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(0.5f));
        gameplayRoot.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        gameplayRoot.addLight(dl);
    }

    private void setUpKeys() {
        InputManager inputManager = sapp.getInputManager();

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
        inputManager.addMapping("Debug", new KeyTrigger(KeyInput.KEY_L));

        inputManager.addListener(this, "Left");
        inputManager.addListener(this, "Right");
        inputManager.addListener(this, "Accelerate");
        inputManager.addListener(this, "Break");
        inputManager.addListener(this, "Cam");
        inputManager.addListener(this, "Reset");
        inputManager.addListener(this, "GUI");
        inputManager.addListener(this, "Pause");
        inputManager.addListener(this, "Quit");
        inputManager.addListener(this, "Debug");
    }

    private void enablePlayerControls(boolean enabled) {
        flyCam.setEnabled(enabled);
        inputManager.setCursorVisible(!enabled);
    }

    private void followCam(float tpf, VehicleControl control) {
        Vector3f forward = control.getForwardVector(null).normalizeLocal();

        // === SMOOTH CAMERA FOLLOW ===
        Vector3f targetCamPos =
                control.getPhysicsLocation().add(forward.negate().mult(10f)) // 20 units behind
                        .add(0, 4f, 0);

        // Interpolate camera position
        float lerpSpeed = 5f;
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
                                     Math.floor(cam.getLocation().x / ((200 - 1) * (SCALE / 16)))));
        chunkZ.setText(String.format("Z Coord: %.1f",
                                     Math.floor(cam.getLocation().z / ((200 - 1) * (SCALE / 16)))));
    }

    private void togglePause() {
        isPaused = !isPaused;

        if (isPaused) {
            pauseMenuNode.setCullHint(Spatial.CullHint.Never); // Show menu
            inputManager.setCursorVisible(true);
            flyCam.setEnabled(false);
            bulletAppState.setEnabled(false); // Pause physics
        } else {
            pauseMenuNode.setCullHint(Spatial.CullHint.Always); // Hide menu
            inputManager.setCursorVisible(false);
            flyCam.setEnabled(true);
            bulletAppState.setEnabled(true); // Resume physics
        }
    }
}
