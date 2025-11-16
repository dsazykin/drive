package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import jMonkeyEngine.Entities.Cars.VehicleModels.Vehicle;

/**
 * Abstract base class for all vehicle implementations.
 * Handles physics simulation, steering, acceleration, and weight transfer.
 */
public abstract class Car {
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

    private final VehicleControl control;
    private final Node carNode;
    protected final Vehicle car;

    private float steeringValue = 0;
    private float accelerationValue = 0;
    private float targetSteeringValue = 0;

    private final PhysicsConfig physicsConfig;

    private boolean accelerating = false;
    private boolean breaking = false;

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
                           float cgHeight, float mass) {
            this(maxSpeed, accelerationConstant, maxReverse, reverseConstant,
                 wheelBase, trackWidth, cgHeight, mass, 0.31f, 2.0f, 0.015f);
        }

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

    protected Car(AssetManager assetManager, PhysicsSpace physicsSpace,
                  Vehicle car, PhysicsConfig config) {
        this.car = car;
        this.physicsConfig = config;

        car.load(assetManager);

        this.control = car.getVehicleControl();
        this.carNode = car.getNode();
        carNode.addControl(control);
        physicsSpace.add(control);
    }

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
        return (physicsConfig.mass * (control.getAngularVelocity().y * velocity) * physicsConfig.cgHeight)
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

        control.getWheel(0).setFrictionSlip(FastMath.clamp(frontLeftFriction, MIN_FRICTION, MAX_FRICTION));
        control.getWheel(1).setFrictionSlip(FastMath.clamp(frontRightFriction, MIN_FRICTION, MAX_FRICTION));
        control.getWheel(2).setFrictionSlip(FastMath.clamp(backLeftFriction, MIN_FRICTION, MAX_FRICTION));
        control.getWheel(3).setFrictionSlip(FastMath.clamp(backRightFriction, MIN_FRICTION, MAX_FRICTION));
    }

    public void move(float velocity, float speed) {
        float resistance = calculateResistance(velocity);
        float netForce = calculateAcceleration(velocity, resistance);

        if (isAccelerating()) {
            control.brake(0f);
            setAccelerationValue(netForce);
            control.accelerate(accelerationValue / ACCELERATION_DIVISOR);
        } else if (isBreaking()) {
            if (speed > 0.1) {
                setAccelerationValue(0f);
                control.accelerate(accelerationValue);
                control.brake(BRAKE_FORCE);
            } else {
                setAccelerationValue(-calculateReverseAcceleration(-velocity, resistance));
                control.accelerate(accelerationValue / ACCELERATION_DIVISOR);
            }
        } else if (velocity > MIN_CREEP_VELOCITY) { // Apply resistance only if moving
            control.accelerate(-resistance);
        } else {
            // Prevent creeping backwards/forwards numerically
            control.accelerate(0f);
            control.brake(IDLE_BRAKE); // Apply a tiny brake to zero out residual velocity
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

        control.steer(steeringValue);
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
        return control;
    }

    public Node getCarNode() {
        return carNode;
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
}
