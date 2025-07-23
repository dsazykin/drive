package jMonkeyEngine;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.plugins.HttpZipLocator;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.control.CharacterControl;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.font.BitmapText;
import com.jme3.input.ChaseCamera;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.*;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import jMonkeyEngine.Entities.Car;
import jMonkeyEngine.Terrain.ChunkManager;
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

    private Car car;

    private BitmapText speedText;
    private BitmapText frontLeftText;
    private BitmapText frontRightText;
    private BitmapText rearLeftText;
    private BitmapText rearRightText;
    private BitmapText loadingText;

    private boolean loadingDone = false;

    private Vector3f cameraPos = new Vector3f();

    int chunkSize = 50;
    float scale = 25f;
    int renderDistance = 5;
    long seed = 1234L;

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        enablePlayerControls(false);
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(100);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        generator = new TerrainGenerator(bulletAppState, rootNode, assetManager, this, executor, chunkSize, scale, renderDistance, seed);
        this.manager =
                new ChunkManager(rootNode, bulletAppState, generator, this, executor, chunkSize,
                                 scale, renderDistance);
        generator.setChunkManager(manager);

        setUpKeys();
        setUpLight();
        loadScene();
        initCar();

        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt"); // default font
        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0); // top-left corner
        guiNode.attachChild(speedText);

        frontLeftText = new BitmapText(guiFont, false);
        frontLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        frontLeftText.setLocalTranslation(10, cam.getHeight() - 50, 0); // top-left corner
        guiNode.attachChild(frontLeftText);

        frontRightText = new BitmapText(guiFont, false);
        frontRightText.setSize(guiFont.getCharSet().getRenderedSize());
        frontRightText.setLocalTranslation(130, cam.getHeight() - 50, 0); // top-left corner
        guiNode.attachChild(frontRightText);

        rearLeftText = new BitmapText(guiFont, false);
        rearLeftText.setSize(guiFont.getCharSet().getRenderedSize());
        rearLeftText.setLocalTranslation(10, cam.getHeight() - 70, 0); // top-left corner
        guiNode.attachChild(rearLeftText);

        rearRightText = new BitmapText(guiFont, false);
        rearRightText.setSize(guiFont.getCharSet().getRenderedSize());
        rearRightText.setLocalTranslation(130, cam.getHeight() - 70, 0); // top-left corner
        guiNode.attachChild(rearRightText);

        loadingText = new BitmapText(guiFont, false);
        loadingText.setSize(guiFont.getCharSet().getRenderedSize());
        loadingText.setText("Loading terrain...");
        loadingText.setLocalTranslation(300, 300, 0);
        guiNode.attachChild(loadingText);
    }

    private void loadScene() {
//        assetManager.registerLocator(
//                "https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/jmonkeyengine/town.zip",
//                HttpZipLocator.class);
//
//        Spatial sceneModel = assetManager.loadModel("main.scene");
//        sceneModel.setLocalScale(4f);
//
//        CollisionShape sceneShape = CollisionShapeFactory.createMeshShape(sceneModel);
//        sceneModel.addControl(new com.jme3.bullet.control.RigidBodyControl(sceneShape, 0));
//        bulletAppState.getPhysicsSpace()
//                .add(sceneModel.getControl(com.jme3.bullet.control.RigidBodyControl.class));
//        rootNode.attachChild(sceneModel);

        generator.CreateTerrain();
    }

    private void initCar() {
        car = new Car(assetManager, bulletAppState.getPhysicsSpace());
        rootNode.attachChild(car.getCarNode());
    }

    private void setUpLight() {
        // We add light so we see the scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(1.3f));
        rootNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        rootNode.addLight(dl);
    }

    private void setUpKeys() {
        inputManager.addMapping("Left", new KeyTrigger(KeyInput.KEY_LEFT));
        inputManager.addMapping("Right", new KeyTrigger(KeyInput.KEY_RIGHT));
        inputManager.addMapping("Up", new KeyTrigger(KeyInput.KEY_UP));
        inputManager.addMapping("Down", new KeyTrigger(KeyInput.KEY_DOWN));
        inputManager.addListener(this, "Left");
        inputManager.addListener(this, "Right");
        inputManager.addListener(this, "Up");
        inputManager.addListener(this, "Down");
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
        } else if (binding.equals("Up")) {
            car.setAccelerating(value);
        } else if (binding.equals("Down")) {
            car.setBreaking(value);
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
            manager.updateChunks(cam.getLocation());
            VehicleControl control = car.getControl();

            // 1. Get current speed
            float speed = -control.getCurrentVehicleSpeedKmHour();
            float velocity = speed / 3.6f;

            float resistance = calculateResistance(velocity);
            float netForce = calculateAcceleration(velocity, resistance);

            // Forward/Backward weight transfer
            float weightTransferLongitudinal =
                    (car.getMass() * (car.getAccelerationValue() / car.getMass()) * car.getCgHeight()) /
                            car.getWheelBase();

            // Lateral (side-to-side) weight transfer during turns
            float weightTransferLateral =
                    (car.getMass() * (control.getAngularVelocity().y * velocity) * car.getCgHeight()) /
                            car.getTrackWidth();

            float totalMass = car.getMass();
            float gravity = 9.81f;
            float staticFrontLoad = totalMass * gravity * 0.5f;
            float staticRearLoad = totalMass * gravity * 0.5f;
            float staticLeftLoad = totalMass * gravity * 0.5f;
            float staticRightLoad = totalMass * gravity * 0.5f;

            float frontLoad = staticFrontLoad - weightTransferLongitudinal / 2f;
            float rearLoad = staticRearLoad + weightTransferLongitudinal / 2f;

            float leftLoad = staticLeftLoad - weightTransferLateral / 2f;
            float rightLoad = staticRightLoad + weightTransferLateral / 2f;

            // Normalize loads to static weight for scaling
            float frontLoadFactor = frontLoad / staticFrontLoad;
            float rearLoadFactor = rearLoad / staticRearLoad;
            float leftLoadFactor = leftLoad / staticLeftLoad;
            float rightLoadFactor = rightLoad / staticRightLoad;

            float speedFactor = 1f / (1 - (3 * FastMath.log(1 / (0.0002f * FastMath.abs(speed) + 1)) ));
            float frontLeftFriction = (3f * (frontLoadFactor * leftLoadFactor * 1.1f)) + 0.5f;
            float frontRightFriction = (3f * (frontLoadFactor * rightLoadFactor * 1.1f)) + 0.5f;
            float backLeftFriction = (3.5f * (rearLoadFactor * leftLoadFactor * 1.1f)) + 0.5f;
            float backRightFriction = (3.5f * (rearLoadFactor * rightLoadFactor * 1.1f)) + 0.5f;

            // Apply normalized load to base friction
            control.getWheel(0).setFrictionSlip(FastMath.clamp(frontLeftFriction * speedFactor, 0.5f, 100f));
            control.getWheel(1).setFrictionSlip(FastMath.clamp(frontRightFriction * speedFactor, 0.5f, 100f));

            control.getWheel(2).setFrictionSlip(FastMath.clamp(backLeftFriction * speedFactor, 1f, 100f));
            control.getWheel(3).setFrictionSlip(FastMath.clamp(backRightFriction * speedFactor, 1f, 100f));

            if (car.isAccelerating()) {
                control.brake(0f);
                car.setAccelerationValue(-netForce);
                control.accelerate(car.getAccelerationValue() / 3.7f);
            } else if (car.isBreaking()) {
                if (speed > 0.1) {
                    car.setAccelerationValue(0f);
                    control.accelerate(car.getAccelerationValue());
                    control.brake(200f);
                } else {
                    car.setAccelerationValue(calculateReverseAcceleration(-velocity, resistance));
                    control.accelerate(car.getAccelerationValue() / 3.7f);
                }
            } else if (velocity > 0.5f) { // Apply resistance only if moving
                control.accelerate(resistance);
            } else {
                // Prevent creeping backwards/forwards numerically
                control.accelerate(0f);
                control.brake(1f); // Apply a tiny brake to zero out residual velocity
            }

            // Define max steering angle regardless of speed
            float MAX_STEERING_ANGLE = 0.8f;

            // Responsiveness factor: lower at high speeds
            float steeringResponse;
            if (car.getTargetSteeringValue() == 0f) {
                steeringResponse = 2f;
            } else {
                steeringResponse = FastMath.clamp(1f / (1f + (speed * speed / 2750)), 0.05f, 1f);
            }

            // Gradually approach the target steering
            float deltaSteering = (car.getTargetSteeringValue() - car.getSteeringValue()) * steeringResponse * tpf * 5f;
            car.setSteeringValue(car.getSteeringValue() + deltaSteering);

            // Clamp final steering within max
            car.setSteeringValue(FastMath.clamp(car.getSteeringValue(), -MAX_STEERING_ANGLE, MAX_STEERING_ANGLE));

            // 5. Apply to vehicle
            control.steer(car.getSteeringValue());

            Vector3f angularVelocity = control.getAngularVelocity();
            Vector3f angularDamping = new Vector3f(
                    angularVelocity.x * -0.5f,
                    angularVelocity.y * -10f,  // stronger yaw damping
                    angularVelocity.z * -0.5f
            );
            control.applyTorque(angularDamping);

            Vector3f forward = control.getForwardVector(null).normalizeLocal();

            // === SMOOTH CAMERA FOLLOW ===
            Vector3f targetCamPos =
                    control.getPhysicsLocation().add(forward.negate().mult(-10f)) // 20 units behind
                            .add(0, 6f, 0);

            // Interpolate camera position
            float lerpSpeed = 5f; // higher = faster
            cameraPos.interpolateLocal(targetCamPos, lerpSpeed * tpf);
            cam.setLocation(cameraPos);

            // Look at the player (can be smoothed as well if needed)
            cam.lookAt(control.getPhysicsLocation().add(0, 2f, 0), Vector3f.UNIT_Y);

            speedText.setText(String.format("Speed: %.1f km/h", speed));

            frontLeftText.setText(String.format("FL: %.1f", control.getWheel(0).getFrictionSlip()));
            frontRightText.setText(String.format("FR: %.1f", control.getWheel(1).getFrictionSlip()));
            rearLeftText.setText(String.format("RL: %.1f", control.getWheel(2).getFrictionSlip()));
            rearRightText.setText(String.format("RR: %.1f", control.getWheel(3).getFrictionSlip()));
        }
    }

    @Override
    public void stop() {
        executor.shutdown();
        super.stop();
    }

    private float calculateAcceleration(float velocity, float resistance) {
        float acceleration = car.getAccelerationConstant() * (car.getMaxSpeed() - velocity);

        float engineForce = car.getMass() * acceleration;

        return engineForce - resistance;
    }

    private float calculateReverseAcceleration(float velocity, float resistance) {
        float acceleration = car.getReverseConstant() * (car.getMaxReverse() - velocity);

        float engineForce = car.getMass() * acceleration;

        return engineForce - resistance;
    }

    private float calculateResistance (float velocity) {
        float airDensity = 1.225f; // kg/m3
        float dragCoefficient = 0.31f;
        float frontalArea = 2.0f; // m2
        float rollingResistanceCoefficient = 0.015f;
        float gravity = 9.81f;

        // Calculate Drag
        float dragForce = 0.5f * airDensity * dragCoefficient * frontalArea * velocity * velocity;

        // Calculate Rolling Resistance
        float rollingResistance = rollingResistanceCoefficient * car.getMass() * gravity;

        // Total Resistance
        return dragForce + rollingResistance;
    }
}