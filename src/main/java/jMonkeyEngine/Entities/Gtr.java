package jMonkeyEngine.Entities;

import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;

public class Gtr {
    private final VehicleControl control;
    private final Node carNode;
    Vehicle car;

    private float steeringValue = 0;
    private float accelerationValue = 0;
    private float targetSteeringValue = 0;

    private final float maxSpeed = 320f / 3.6f;
    private final float accelerationConstant = 0.1441128652f;

    private final float maxReverse = 16.667f;
    private final float reverseConstant = 0.2985932482f;

    private float wheelBase = 2.78f;
    private float trackWidth = 1.6f;
    private final float cgHeight = 0.5f;
    private final float mass = 1525;

    private boolean accelerating = false;
    private boolean breaking = false;

    public Gtr(AssetManager assetManager, PhysicsSpace physicsSpace) {
        float stiffness = 120.0f;
        float compValue = 0.2f;
        float dampValue = 0.3f;

        car = new Nismo();
        car.load(assetManager);

        carNode = car.getNode();
        control = car.getVehicleControl();

//        // Load the car visual model
//        carNode = (Node) assetManager.loadModel("Models/gtr_nismo/scene.gltf.j3o");
//
//        // Try to load the pre-made collision shape
//        CollisionShape chassisShape;
//        try {
//            chassisShape = (CollisionShape) assetManager.loadAsset(
//                    "Models/NissanGTR/shapes/chassis-shape.j3o"
//            );
//            chassisShape.setScale(carNode.getWorldScale());
//        } catch (AssetNotFoundException e) {
//            // If not found, generate from the mesh
//            chassisShape = CollisionShapeFactory.createDynamicMeshShape(carNode);
//        }
//
//        // Create a physics control for the car body
//        control = new VehicleControl(chassisShape, 1200f); // 1200 kg mass
//        control.setLinearDamping(0.05f); // aerodynamic drag
//
//        carNode.addControl(control);
//
//        carNode.setShadowMode(RenderQueue.ShadowMode.Cast);
//
//        control.setSuspensionCompression(compValue * 2.0f * FastMath.sqrt(stiffness));
//        control.setSuspensionDamping(dampValue * 2.0f * FastMath.sqrt(stiffness));
//        control.setSuspensionStiffness(stiffness);
//        control.setMaxSuspensionForce(10000);
//
//        Vector3f wheelDirection = new Vector3f(0, -1, 0);
//        Vector3f wheelAxle = new Vector3f(-1, 0, 0);
//
//        float wheelRadius = getWheelRadius("WheelFrontRight");
//        float back_wheel_h = (wheelRadius * 1.7f) - 1f;
//        float front_wheel_h = (wheelRadius * 1.9f) - 1f;
//
//        addWheel("WheelFrontRight", front_wheel_h, wheelRadius, true, wheelDirection, wheelAxle);
//        addWheel("WheelFrontLeft", front_wheel_h, wheelRadius, true, wheelDirection, wheelAxle);
//        addWheel("WheelBackRight", back_wheel_h, wheelRadius, false, wheelDirection, wheelAxle);
//        addWheel("WheelBackLeft", back_wheel_h, wheelRadius, false, wheelDirection, wheelAxle);
//
//        // Front wheels - more grip for stability
//        control.getWheel(0).setFrictionSlip(3.5f); // front left
//        control.getWheel(1).setFrictionSlip(3.5f); // front right
//
//        // Rear wheels - slightly less grip to prevent oversteer
//        control.getWheel(2).setFrictionSlip(4f); // rear left
//        control.getWheel(3).setFrictionSlip(4f); // rear right

        physicsSpace.add(control);
    }

    // Physics Calculations

    public void weightTransfer(float velocity, float speed) {
        // Forward/Backward weight transfer
        float weightTransferLongitudinal =
                (mass * (accelerationValue / mass) * cgHeight) /
                        wheelBase;

        // Lateral (side-to-side) weight transfer during turns
        float weightTransferLateral =
                (mass * (control.getAngularVelocity().y * velocity) * cgHeight) /
                        trackWidth;

        float totalMass = mass;
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
        float frontLeftFriction = (2.6f * (frontLoadFactor * leftLoadFactor * 1.1f)) * speedFactor;
        float frontRightFriction = (2.6f * (frontLoadFactor * rightLoadFactor * 1.1f)) * speedFactor;
        float backLeftFriction = (3.1f * (rearLoadFactor * leftLoadFactor * 1.1f)) * speedFactor;
        float backRightFriction = (3.1f * (rearLoadFactor * rightLoadFactor * 1.1f)) * speedFactor;

        // Apply normalized load to base friction
        control.getWheel(0).setFrictionSlip(FastMath.clamp(frontLeftFriction, 0f,
                                                           100f));
        control.getWheel(1).setFrictionSlip(FastMath.clamp(frontRightFriction, 0f,
                                                           100f));

        control.getWheel(2).setFrictionSlip(FastMath.clamp(backLeftFriction, -0f,
                                                           100f));
        control.getWheel(3).setFrictionSlip(FastMath.clamp(backRightFriction, 0f,
                                                           100f));
    }

    public void move(float velocity, float speed) {
        float resistance = calculateResistance(velocity);
        float netForce = calculateAcceleration(velocity, resistance);

        if (isAccelerating()) {
            control.brake(0f);
            setAccelerationValue(netForce);
            control.accelerate(accelerationValue / 3.7f);
        } else if (isBreaking()) {
            if (speed > 0.1) {
                setAccelerationValue(0f);
                control.accelerate(accelerationValue);
                control.brake(200f);
            } else {
                setAccelerationValue(-calculateReverseAcceleration(-velocity, resistance));
                control.accelerate(accelerationValue / 3.7f);
            }
        } else if (velocity > 0.5f) { // Apply resistance only if moving
            control.accelerate(-resistance);
        } else {
            // Prevent creeping backwards/forwards numerically
            control.accelerate(0f);
            control.brake(1f); // Apply a tiny brake to zero out residual velocity
        }
    }

    public void steer(float speed, float tpf) {
        // Define max steering angle regardless of speed
        float MAX_STEERING_ANGLE = 0.8f;

        // Responsiveness factor: lower at high speeds
        float steeringResponse;
        if (targetSteeringValue == 0f) {
            steeringResponse = 2f;
        } else {
            steeringResponse = FastMath.clamp(1f / (1f + (speed * speed / 2750)), 0.05f, 1f);
        }

        // Gradually approach the target steering
        float deltaSteering = (targetSteeringValue - steeringValue) * steeringResponse * tpf * 5f;
        setSteeringValue(steeringValue + deltaSteering);

        // Clamp final steering within max
        setSteeringValue(FastMath.clamp(steeringValue, -MAX_STEERING_ANGLE, MAX_STEERING_ANGLE));

        // 5. Apply to vehicle
        control.steer(steeringValue);

//        Vector3f angularVelocity = control.getAngularVelocity();
//        Vector3f angularDamping = new Vector3f(
//                angularVelocity.x * -0.5f,
//                angularVelocity.y * -10f,  // stronger yaw damping
//                angularVelocity.z * -0.5f
//        );
//        control.applyTorque(angularDamping);
    }

    private float calculateAcceleration(float velocity, float resistance) {
        float acceleration = accelerationConstant * (maxSpeed - velocity);

        float engineForce = mass * acceleration;

        return engineForce - resistance;
    }

    private float calculateReverseAcceleration(float velocity, float resistance) {
        float acceleration = reverseConstant * (maxReverse - velocity);

        float engineForce = mass * acceleration;

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
        float rollingResistance = rollingResistanceCoefficient * mass * gravity;

        // Total Resistance
        return dragForce + rollingResistance;
    }


    // Getters and Setters

    public VehicleControl getControl() {
        return control;
    }

    public Node getCarNode() {
        return carNode;
    }

    public void setAccelerationValue(float accelerationValue) {
        this.accelerationValue = accelerationValue;
    }

    public float getAccelerationValue() {
        return accelerationValue;
    }

    public void setSteeringValue(float steeringValue) {
        this.steeringValue = steeringValue;
    }

    public float getSteeringValue() {
        return steeringValue;
    }

    public void setTargetSteeringValue(float targetSteeringValue) {
        this.targetSteeringValue = targetSteeringValue;
    }

    public float getTargetSteeringValue() {
        return targetSteeringValue;
    }

    public float getMaxSpeed() {
        return maxSpeed;
    }

    public float getAccelerationConstant() {
        return accelerationConstant;
    }

    public float getMass() {
        return mass;
    }

    public void setAccelerating(boolean accelerating) {
        this.accelerating = accelerating;
    }

    public boolean isAccelerating() {
        return accelerating;
    }

    public void setBreaking(boolean breaking) {
        this.breaking = breaking;
    }

    public boolean isBreaking() {
        return breaking;
    }

    public float getWheelBase() {
        return wheelBase;
    }

    public float getTrackWidth() {
        return trackWidth;
    }

    public float getCgHeight() {
        return cgHeight;
    }

    public float getMaxReverse() {
        return maxReverse;
    }

    public float getReverseConstant() {
        return reverseConstant;
    }
}