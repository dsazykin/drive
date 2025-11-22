package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.objects.VehicleWheel;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import jMonkeyEngine.Entities.Cars.CarComponents.Steering;
import jMonkeyEngine.Entities.Cars.CarComponents.Suspension;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.Wheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.WheelModel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Abstract base class for all vehicle implementations.
 * Handles physics simulation, steering, acceleration, weight transfer, and vehicle structure.
 * Merged from Vehicle and Car classes to provide complete vehicle functionality.
 */
public abstract class Car implements PhysicsTickListener {
    // *************************************************************************
    // Constants from Vehicle class

    /**
     * factor to convert km/hr to miles per hour.
     */
    final public static float KPH_TO_MPH = 0.62137f;
    /**
     * factor to convert km/hr to wu/sec.
     */
    final public static float KPH_TO_WUPS = 0.277778f;
    /**
     * message logger for this class.
     */
    final public static Logger logger = Logger.getLogger(Car.class.getName());

    // *************************************************************************
    // Physics Constants

    private static final float GRAVITY = 9.81f;
    private static final float AIR_DENSITY = 1.225f; // kg/m³
    private static final float MAX_STEERING_ANGLE = 0.8f;
    private static final float STEERING_RETURN_SPEED = 2f;
    private static final float STEERING_INTERPOLATION_FACTOR = 5f;
    private static final float BRAKE_FORCE = 200f;
    private static final float IDLE_BRAKE = 1f;
    private static final float MIN_CREEP_VELOCITY = 0.5f;
    private static final float ACCELERATION_DIVISOR = 3.7f;

    // Wheel friction constants
    private static final float FRONT_BASE_FRICTION = 2.6f;
    private static final float REAR_BASE_FRICTION = 3.1f;
    private static final float FRICTION_LOAD_MULTIPLIER = 1.1f;
    private static final float MAX_FRICTION = 100f;
    private static final float MIN_FRICTION = 0f;

    // *************************************************************************
    // Fields from both classes

    // Vehicle control and node
    private VehicleControl vehicleControl;
    private final Node node;

    // Physics configuration
    private final PhysicsConfig physicsConfig;

    // Movement state
    private float steeringValue = 0;
    private float accelerationValue = 0;
    private float targetSteeringValue = 0;
    private boolean accelerating = false;
    private boolean breaking = false;

    // Vehicle structure from Vehicle class
    /**
     * linear damping due to air resistance on the chassis (&ge;0, &lt;1).
     */
    private float chassisDamping;
    /**
     * the fraction of the total mass in each body (each element &ge;0, &le;1).
     * or null if not determined yet
     */
    private float[] massFractions;
    /**
     * ratio of the steeringWheelAngle to the turn angle of any wheels used for steering.
     */
    private float steeringRatio = 2f;
    /**
     * rotation of the steering wheel, handlebars, or tiller (in radians,
     * negative&rarr;left, 0&rarr;neutral, positive&rarr;right).
     */
    private float steeringWheelAngle;
    /**
     * support the chassis and configure acceleration, steering, and braking.
     */
    final private List<Wheel> wheels = new ArrayList<>(4);
    /**
     * temporary storage for the vehicle's orientation.
     */
    final private static Matrix3f tmpOrientation = new Matrix3f();
    /**
     * computer-graphics (C-G) model to visualize the whole Vehicle except for
     * its wheels.
     */
    private Spatial chassis;
    /**
     * descriptive name (not null).
     */
    final private String name;
    /**
     * default transform of each body relative to the engine body, or null if
     * transforms have not yet been determined.
     */
    private Transform[] relativeTransforms;


    // *************************************************************************
    // Constructors

    /**
     * Instantiate a Car with the specified name and no physics configuration yet.
     * Subclasses should call this constructor and then configure the vehicle.
     *
     * @param name the desired name (not null)
     */
    protected Car(String name) {
        this.name = name;
        this.node = new Node("Car: " + name);
        this.physicsConfig = null;
    }

    /**
     * Instantiate a Car with physics configuration for subclasses that use PhysicsConfig.
     *
     * @param name the desired name (not null)
     * @param config the physics configuration (not null)
     */
    protected Car(String name, PhysicsConfig config) {
        this.name = name;
        this.node = new Node("Car: " + name);
        this.physicsConfig = config;
    }

    /**
     * Configuration object for vehicle physics parameters.
     */
    protected static class PhysicsConfig {
        final float maxSpeed;
        final float accelerationConstant;
        final float maxReverse;
        final float reverseConstant;
        final float wheelBase;
        final float trackWidth;
        final float cgHeight;
        final float mass;
        final float dragCoefficient;
        final float frontalArea;
        final float rollingResistanceCoefficient;

        public PhysicsConfig(float maxSpeed, float accelerationConstant,
                           float maxReverse, float reverseConstant,
                           float wheelBase, float trackWidth,
                           float cgHeight, float mass,
                           float dragCoefficient, float frontalArea,
                           float rollingResistanceCoefficient) {
            this.maxSpeed = maxSpeed;
            this.accelerationConstant = accelerationConstant;
            this.maxReverse = maxReverse;
            this.reverseConstant = reverseConstant;
            this.wheelBase = wheelBase;
            this.trackWidth = trackWidth;
            this.cgHeight = cgHeight;
            this.mass = mass;
            this.dragCoefficient = dragCoefficient;
            this.frontalArea = frontalArea;
            this.rollingResistanceCoefficient = rollingResistanceCoefficient;
        }
    }

    // *************************************************************************
    // new methods exposed from Vehicle class

    /**
     * Add a single Wheel to the body associated with the Engine. TODO protect
     * method
     *
     * @param wheelModel the desired WheelModel (not null)
     * @param connectionLocation the location where the suspension connects to
     * the chassis (in chassis coordinates, not null, unaffected)
     * @param steering relationship to the steering system (not null)
     * @param extraDamping (&ge;0, &lt;1)
     * @return the new Wheel
     */
    public Wheel addWheel(WheelModel wheelModel, Vector3f connectionLocation,
                          Steering steering, float extraDamping) {
        Wheel result = addWheel(
                wheelModel, vehicleControl, connectionLocation, steering, extraDamping);

        return result;
    }

    /**
     * Determine the linear damping due to air resistance.
     *
     * @return a fraction (&ge;0, &lt;1)
     */
    public float chassisDamping() {
        assert chassisDamping >= 0f && chassisDamping < 1f : chassisDamping;
        return chassisDamping;
    }

    /**
     * Count how many wheels this Vehicle has.
     *
     * @return the count (&ge;0)
     */
    public int countWheels() {
        return wheels.size();
    }

    /**
     * Access the physics body associated with the Engine.
     *
     * @return the pre-existing instance
     */
    public VehicleControl getVehicleControl() {
        return vehicleControl;
    }

    /**
     * Access the computer-graphics (C-G) model for visualization.
     *
     * @return the pre-existing instance
     */
    public Spatial getChassis() {
        return chassis;
    }

    /**
     * Determine this vehicle's name.
     *
     * @return the descriptive name (not null)
     */
    public String getName() {
        return name;
    }

    /**
     * Access the indexed Wheel.
     *
     * @param index which Wheel to access (&ge;0)
     * @return the pre-existing instance
     */
    public Wheel getWheel(int index) {
        return wheels.get(index);
    }

    /**
     * Enumerate all wheels.
     *
     * @return a new array (not null)
     */
    public Wheel[] listWheels() {
        int numWheels = countWheels();
        Wheel[] result = new Wheel[numWheels];
        wheels.toArray(result);

        return result;
    }

    /**
     * Access the scene-graph subtree that visualizes this Vehicle.
     *
     * @return the pre-existing instance (not null)
     */
    public Node getNode() {
        return node;
    }

    /**
     * Load the assets of this Vehicle with physics.
     *
     * @param assetManager for loading assets (not null)
     */
    public void load(AssetManager assetManager, BulletAppState bulletAppState) {
        // subclasses should override
    }

    /**
     * Load the assets of this Vehicle without physics.
     *
     * @param assetManager for loading assets (not null)
     */
    public void load(AssetManager assetManager) {
        // subclasses should override
    }

    // *************************************************************************
    // new protected methods from Vehicle class

    /**
     * Add a single Wheel to the specified body.
     *
     * @param wheelModel the desired WheelModel (not null)
     * @param body the physics body to which the Wheel will be added (not null,
     * alias created)
     * @param connectionLocation the location where the suspension connects to
     * the chassis (in chassis coordinates, not null, unaffected)
     * @param steering wheel's relationship to the steering system (not null)
     * @param extraDamping (&ge;0, &lt;1)
     * @return the new Wheel
     */
    protected Wheel addWheel(WheelModel wheelModel, VehicleControl body,
                             Vector3f connectionLocation, Steering steering,
                             float extraDamping) {
        Node wheelNode = wheelModel.getWheelNode();
        Vector3f suspensionDirection = new Vector3f(0f, -1f, 0f);
        Vector3f axleDirection = new Vector3f(-1f, 0f, 0f);
        float restLength = 0.2f;
        float radius = wheelModel.radius();
        int wheelIndex = body.getNumWheels();
        VehicleWheel vehicleWheel = body.addWheel(
                wheelNode, connectionLocation, suspensionDirection,
                axleDirection, restLength, radius, steering != Steering.UNUSED);

        Suspension suspension = new Suspension(vehicleWheel);
        Wheel result = new Wheel(this, body, wheelIndex, steering, suspension,
                                 extraDamping);
        wheels.add(result);

        getNode().attachChild(wheelNode);

        return result;
    }

    /**
     * Should be invoked last, after all parts have been configured and added.
     */
    protected void build() {
        updateRelativeTransforms();
    }

    /**
     * Configure a single-body "chassis" that's loaded from J3O assets in the
     * customary folders. (Bullet refers to everything except the wheels as the
     * "chassis".)
     *
     * @param folderName the name of the folder containing the C-G model asset
     * (not null, not empty)
     * @param cgmBaseFileName the base filename of the C-G model asset (not
     * null, not empty)
     * @param assetManager to load assets (not null)
     * @param mass the mass of the chassis (in kilos, &gt;0)
     * @param damping the drag on the chassis due to air resistance (&ge;0,
     * &lt;1)
     */
    protected void setChassis(String folderName, String cgmBaseFileName,
                              AssetManager assetManager, float mass, float damping) {

        String assetPath
                = "/Models/" + folderName + "/" + cgmBaseFileName + ".j3o";
        Spatial cgmRoot = assetManager.loadModel(assetPath);

        assetPath = "/Models/" + folderName + "/shapes/chassis-shape.j3o";
        CollisionShape shape;
        try {
            shape = (CollisionShape) assetManager.loadAsset(assetPath);
            Vector3f scale = cgmRoot.getWorldScale();
            shape.setScale(scale);
        } catch (AssetNotFoundException exception) {
            shape = CollisionShapeFactory.createDynamicMeshShape(cgmRoot);
        }
        setChassis(cgmRoot, shape, mass, damping);
    }

    /**
     * Configure a single-body "chassis". (Bullet refers to everything except
     * the wheels as the "chassis".)
     *
     * @param cgmRoot the root of the C-G model to visualize the chassis (not
     * null, alias created)
     * @param shape the shape for the chassis (not null, alias created)
     * @param mass the mass of the chassis (in kilos, &gt;0)
     * @param damping the drag on the chassis due to air resistance (&ge;0,
     * &lt;1)
     */
    protected void setChassis(Spatial cgmRoot, CollisionShape shape,
                              float mass, float damping) {

        setChassis(cgmRoot, shape, mass, damping, node);
    }

    /**
     * Configure a single-body "chassis". (Bullet refers to everything except
     * the wheels as the "chassis".)
     *
     * @param cgmRoot the root of the C-G model to visualize the chassis (not
     * null, alias created)
     * @param shape the shape for the chassis (not null, alias created)
     * @param mass the mass of the chassis (in kilos, &gt;0)
     * @param damping the drag on the chassis due to air resistance (&ge;0,
     * &lt;1)
     * @param controlledSpatial the Spatial to which the physics control should
     * be added (not null)
     */
    protected void setChassis(Spatial cgmRoot, CollisionShape shape,
                              float mass, float damping, Spatial controlledSpatial) {

        this.chassisDamping = damping;
        this.chassis = cgmRoot;
        node.attachChild(cgmRoot);
        this.massFractions = null;

        // Create the physics body associated with the Engine.
        this.vehicleControl = new VehicleControl(shape, mass);
        /*
         * Configure damping for the engine body,
         * to simulate drag due to air resistance.
         */
        vehicleControl.setLinearDamping(damping);

        controlledSpatial.addControl(vehicleControl);
    }

    private void updateRelativeTransforms() {
        // Implementation can be added if needed for multi-body vehicles
    }

    public void init(BulletAppState bulletAppState) {
        bulletAppState.getPhysicsSpace().add(this.getVehicleControl());
        bulletAppState.getPhysicsSpace().addTickListener(this);
    }

    // *************************************************************************
    // Physics simulation methods (from original Car class)


    /**
     * Calculates and applies dynamic weight transfer based on acceleration and turning.
     */
    public void weightTransfer(float velocity, float speed) {
        float weightTransferLongitudinal = calculateLongitudinalWeightTransfer();
        float weightTransferLateral = calculateLateralWeightTransfer(velocity);

        float staticLoad = physicsConfig.mass * GRAVITY * 0.5f;

        float frontLoad = staticLoad - weightTransferLongitudinal / 2f;
        float rearLoad = staticLoad + weightTransferLongitudinal / 2f;
        float leftLoad = staticLoad - weightTransferLateral / 2f;
        float rightLoad = staticLoad + weightTransferLateral / 2f;

        float frontLoadFactor = frontLoad / staticLoad;
        float rearLoadFactor = rearLoad / staticLoad;
        float leftLoadFactor = leftLoad / staticLoad;
        float rightLoadFactor = rightLoad / staticLoad;

        float speedFactor = calculateSpeedFrictionFactor(speed);

        applyWheelFriction(frontLoadFactor, rearLoadFactor,
                          leftLoadFactor, rightLoadFactor, speedFactor);
    }

    private float calculateLongitudinalWeightTransfer() {
        return (physicsConfig.mass * (accelerationValue / physicsConfig.mass) * physicsConfig.cgHeight)
               / physicsConfig.wheelBase;
    }

    private float calculateLateralWeightTransfer(float velocity) {
        return (physicsConfig.mass * (vehicleControl.getAngularVelocity().y * velocity) * physicsConfig.cgHeight)
               / physicsConfig.trackWidth;
    }

    private float calculateSpeedFrictionFactor(float speed) {
        return 1f / (1f - (3f * FastMath.log(1f / (0.0002f * FastMath.abs(speed) + 1f))));
    }

    private void applyWheelFriction(float frontLoadFactor, float rearLoadFactor,
                                   float leftLoadFactor, float rightLoadFactor,
                                   float speedFactor) {
        float frontLeftFriction = (FRONT_BASE_FRICTION * (frontLoadFactor * leftLoadFactor * FRICTION_LOAD_MULTIPLIER)) * speedFactor;
        float frontRightFriction = (FRONT_BASE_FRICTION * (frontLoadFactor * rightLoadFactor * FRICTION_LOAD_MULTIPLIER)) * speedFactor;
        float backLeftFriction = (REAR_BASE_FRICTION * (rearLoadFactor * leftLoadFactor * FRICTION_LOAD_MULTIPLIER)) * speedFactor;
        float backRightFriction = (REAR_BASE_FRICTION * (rearLoadFactor * rightLoadFactor * FRICTION_LOAD_MULTIPLIER)) * speedFactor;

        vehicleControl.getWheel(0).setFrictionSlip(FastMath.clamp(frontLeftFriction, MIN_FRICTION, MAX_FRICTION));
        vehicleControl.getWheel(1).setFrictionSlip(FastMath.clamp(frontRightFriction, MIN_FRICTION, MAX_FRICTION));
        vehicleControl.getWheel(2).setFrictionSlip(FastMath.clamp(backLeftFriction, MIN_FRICTION, MAX_FRICTION));
        vehicleControl.getWheel(3).setFrictionSlip(FastMath.clamp(backRightFriction, MIN_FRICTION, MAX_FRICTION));
//        if (car.listWheels().length == 4) {
//            vehicleControl.getWheel(3)
//                    .setFrictionSlip(FastMath.clamp(backRightFriction, MIN_FRICTION, MAX_FRICTION));
//        }
    }

    public void move(float velocity, float speed) {
        float resistance = calculateResistance(velocity);
        float netForce = calculateAcceleration(velocity, resistance);

        if (isAccelerating()) {
            vehicleControl.brake(0f);
            setAccelerationValue(netForce);
            vehicleControl.accelerate(accelerationValue / ACCELERATION_DIVISOR);
        } else if (isBreaking()) {
            if (speed > 0.1) {
                setAccelerationValue(0f);
                vehicleControl.accelerate(accelerationValue);
                vehicleControl.brake(BRAKE_FORCE);
            } else {
                setAccelerationValue(-calculateReverseAcceleration(-velocity, resistance));
                vehicleControl.accelerate(accelerationValue / ACCELERATION_DIVISOR);
            }
        } else if (velocity > MIN_CREEP_VELOCITY) { // Apply resistance only if moving
            vehicleControl.accelerate(-resistance);
        } else {
            // Prevent creeping backwards/forwards numerically
            vehicleControl.accelerate(0f);
            vehicleControl.brake(IDLE_BRAKE); // Apply a tiny brake to zero out residual velocity
        }
    }

    public void steer(float speed, float tpf) {
        float steeringResponse = calculateSteeringResponse(speed);
        float deltaSteering = (targetSteeringValue - steeringValue) * steeringResponse * tpf * STEERING_INTERPOLATION_FACTOR;

        setSteeringValue(FastMath.clamp(
            steeringValue + deltaSteering,
            -MAX_STEERING_ANGLE,
            MAX_STEERING_ANGLE
        ));

        vehicleControl.steer(steeringValue);
    }

    private float calculateSteeringResponse(float speed) {
        if (targetSteeringValue == 0f) {
            return STEERING_RETURN_SPEED;
        }
        return FastMath.clamp(1f / (1f + (speed * speed / 2750f)), 0.05f, 1f);
    }

    private float calculateAcceleration(float velocity, float resistance) {
        float acceleration = physicsConfig.accelerationConstant * (physicsConfig.maxSpeed - velocity);
        float engineForce = physicsConfig.mass * acceleration;
        return engineForce - resistance;
    }

    private float calculateReverseAcceleration(float velocity, float resistance) {
        float acceleration = physicsConfig.reverseConstant * (physicsConfig.maxReverse - velocity);
        float engineForce = physicsConfig.mass * acceleration;
        return engineForce - resistance;
    }

    private float calculateResistance(float velocity) {
        float dragForce = 0.5f * AIR_DENSITY * physicsConfig.dragCoefficient *
                         physicsConfig.frontalArea * velocity * velocity;
        float rollingResistance = physicsConfig.rollingResistanceCoefficient *
                                 physicsConfig.mass * GRAVITY;
        return dragForce + rollingResistance;
    }

    // Getters

    public VehicleControl getControl() {
        return vehicleControl;
    }

    public Node getCarNode() {
        return node;
    }

    public float getAccelerationValue() {
        return accelerationValue;
    }

    public float getSteeringValue() {
        return steeringValue;
    }

    public float getTargetSteeringValue() {
        return targetSteeringValue;
    }

    public float getMaxSpeed() {
        return physicsConfig.maxSpeed;
    }

    public float getAccelerationConstant() {
        return physicsConfig.accelerationConstant;
    }

    public float getMass() {
        return physicsConfig.mass;
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    public boolean isBreaking() {
        return breaking;
    }

    public float getWheelBase() {
        return physicsConfig.wheelBase;
    }

    public float getTrackWidth() {
        return physicsConfig.trackWidth;
    }

    public float getCgHeight() {
        return physicsConfig.cgHeight;
    }

    public float getMaxReverse() {
        return physicsConfig.maxReverse;
    }

    public float getReverseConstant() {
        return physicsConfig.reverseConstant;
    }

    // Setters

    public void setAccelerationValue(float accelerationValue) {
        this.accelerationValue = accelerationValue;
    }

    public void setSteeringValue(float steeringValue) {
        this.steeringValue = steeringValue;
    }

    public void setTargetSteeringValue(float targetSteeringValue) {
        this.targetSteeringValue = targetSteeringValue;
    }

    public void setAccelerating(boolean accelerating) {
        this.accelerating = accelerating;
    }

    public void setBreaking(boolean breaking) {
        this.breaking = breaking;
    }

    // *************************************************************************
    // PhysicsTickListener methods

    /**
     * Callback from Bullet, invoked just before the physics is stepped.
     *
     * @param space the space that is about to be stepped (not null)
     * @param timeStep the time per physics step (in seconds, &ge;0)
     */
    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        // do nothing
    }

    /**
     * Callback from Bullet, invoked just after the physics has been stepped.
     *
     * @param space the space that was just stepped (not null)
     * @param timeStep the time per physics step (in seconds, &ge;0)
     */
    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        // do nothing
    }

    // *************************************************************************
    // Object methods

    /**
     * Represent this instance as a String.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String className = getClass().getSimpleName();
        int hashCode = hashCode();
        String result = className + "@" + Integer.toHexString(hashCode);

        return result;
    }
}
