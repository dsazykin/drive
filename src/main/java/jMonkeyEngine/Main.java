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

/**
 * Example 9 - How to make walls and floors solid.
 * This collision code uses Physics and a custom Action Listener.
 * @author normen, with edits by Zathras
 */
public class Main extends SimpleApplication
        implements ActionListener {

    BulletAppState bulletAppState;

    private Car car;

    private BitmapText speedText;
    private BitmapText accText;

    private Vector3f cameraPos = new Vector3f(); // current interpolated camera position

    public static void main(String[] args) {
        Main app = new Main();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(100);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        bulletAppState.setDebugEnabled(true);

        setUpKeys();
        setUpLight();
        loadScene();
        initCar();

        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt"); // default font
        speedText = new BitmapText(guiFont, false);
        speedText.setSize(guiFont.getCharSet().getRenderedSize());
        speedText.setLocalTranslation(10, cam.getHeight() - 10, 0); // top-left corner
        guiNode.attachChild(speedText);

        accText = new BitmapText(guiFont, false);
        accText.setSize(guiFont.getCharSet().getRenderedSize());
        accText.setLocalTranslation(10, cam.getHeight() - 30, 0); // top-left corner
        guiNode.attachChild(accText);
    }

    private void loadScene() {
        assetManager.registerLocator(
                "https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/jmonkeyengine/town.zip",
                HttpZipLocator.class);

        Spatial sceneModel = assetManager.loadModel("main.scene");
        sceneModel.setLocalScale(4f);

        CollisionShape sceneShape = CollisionShapeFactory.createMeshShape(sceneModel);
        sceneModel.addControl(new com.jme3.bullet.control.RigidBodyControl(sceneShape, 0));
        bulletAppState.getPhysicsSpace()
                .add(sceneModel.getControl(com.jme3.bullet.control.RigidBodyControl.class));
        rootNode.attachChild(sceneModel);
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
        VehicleControl control = car.getControl();

        // 1. Get current speed
        float speed = FastMath.abs(control.getCurrentVehicleSpeedKmHour());

        float speedMs = speed / 3.6f;
        float acceleration = car.getAccelerationConstant() * (car.getMaxSpeed() - speedMs);

        float airDensity = 1.225f; // kg/m3
        float dragCoefficient = 0.31f;
        float frontalArea = 2.0f; // m2
        float rollingResistanceCoefficient = 0.015f;
        float gravity = 9.81f;

        // Calculate Drag
        float dragForce = 0.5f * airDensity * dragCoefficient * frontalArea * speedMs * speedMs;

        // Calculate Rolling Resistance
        float rollingResistance = rollingResistanceCoefficient * car.getMass() * gravity;

        // Total Resistance
        float totalResistance = dragForce + rollingResistance;

        float engineForce = car.getMass() * acceleration;
        float netForce = engineForce - totalResistance;

        if (car.isAccelerating()) {
            control.brake(0f);
            car.setAccelerationValue(-netForce);
            control.accelerate(car.getAccelerationValue() / 3.7f);
        } else if (car.isBreaking()) {
            car.setAccelerationValue(0f);
            control.accelerate(car.getAccelerationValue());
            control.brake(200f);
        }else if (speedMs > 0.5f) { // Apply resistance only if moving
            control.accelerate(totalResistance);
        } else {
            // Prevent creeping backwards/forwards numerically
            control.accelerate(0f);
            control.brake(1f); // Apply a tiny brake to zero out residual velocity
        }

        if (car.getTargetSteeringValue() != 0f && speed > 100f) {
            float maxHoldSteer = 0.6f; // tune this
            car.setTargetSteeringValue(FastMath.clamp(car.getTargetSteeringValue(), -maxHoldSteer, maxHoldSteer));
        }

        // 2. Compute steer factor based on speed
        float maxSteer = 0.8f;
        float minSteer = 0.2f;
        // Max steering at 0 km/h, minimal at 100+
        float steerFactor = FastMath.clamp(1.0f / (1.0f + (speed * speed / 25000f)), minSteer, maxSteer);

        // 3. Interpolate toward target
        float interpSpeed;
        if (speed < 150f) {
            interpSpeed = (car.getTargetSteeringValue() == 0f) ? 0.06f : 0.03f;
        } else {
            interpSpeed = (car.getTargetSteeringValue() == 0f) ? 0.01f + (speed / 3000f) : 0.03f;
        }
        float smoothedSteering = FastMath.interpolateLinear(interpSpeed, car.getSteeringValue(), car.getTargetSteeringValue());
        car.setSteeringValue(smoothedSteering);

        // 4. Scale it based on steerFactor
        // Dynamically clamp the final steering angle
        float maxAllowedSteering = FastMath.clamp(1f - (speed / 100f), 0.15f, maxSteer);  // or 0.8f if you want it more generous
        float scaledSteering = FastMath.clamp(car.getSteeringValue(), -maxAllowedSteering, maxAllowedSteering) * steerFactor;

        // 5. Apply to vehicle
        control.steer(scaledSteering);

        Vector3f forward = control.getForwardVector(null).normalizeLocal();

        // === SMOOTH CAMERA FOLLOW ===
        Vector3f targetCamPos = control.getPhysicsLocation()
                .add(forward.negate().mult(-10f)) // 20 units behind
                .add(0, 6f, 0);

        // Interpolate camera position
        float lerpSpeed = 5f; // higher = faster
        cameraPos.interpolateLocal(targetCamPos, lerpSpeed * tpf);
        cam.setLocation(cameraPos);

        // Look at the player (can be smoothed as well if needed)
        cam.lookAt(control.getPhysicsLocation().add(0, 2f, 0), Vector3f.UNIT_Y);

        speedText.setText(String.format("Speed: %.1f km/h", speed));
        accText.setText(String.format("Acceleration: %.1f", car.getAccelerationValue() / car.getMass()));
    }
}