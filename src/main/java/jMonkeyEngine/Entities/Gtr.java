package jMonkeyEngine.Entities;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.objects.VehicleWheel;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.debug.Arrow;
import jMonkeyEngine.Main;
import java.util.ArrayList;
import java.util.List;

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
        car = new Nismo();
        car.load(assetManager);

        carNode = car.getNode();
        control = car.getVehicleControl();

        physicsSpace.add(control);
    }

    // Physics Calculations

    public List<Float> weightTransfer(float velocity, float speed) {
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
        float frontLeftFriction = (3f * (frontLoadFactor * leftLoadFactor * 1.1f)) * speedFactor;
        float frontRightFriction = (3f * (frontLoadFactor * rightLoadFactor * 1.1f)) * speedFactor;
        float backLeftFriction = (3.5f * (rearLoadFactor * leftLoadFactor * 1.1f)) * speedFactor;
        float backRightFriction = (3.5f * (rearLoadFactor * rightLoadFactor * 1.1f)) * speedFactor;

        float staticWheelLoad = (totalMass * gravity) / 4f;

        float flLoad = staticWheelLoad
                - (weightTransferLongitudinal / 2f)  // front loses load under accel
                - (weightTransferLateral / 2f);     // left loses load in right turn

        float frLoad = staticWheelLoad
                - (weightTransferLongitudinal / 2f)
                + (weightTransferLateral / 2f);

        float rlLoad = staticWheelLoad
                + (weightTransferLongitudinal / 2f) // rear gains load under accel
                - (weightTransferLateral / 2f);

        float rrLoad = staticWheelLoad
                + (weightTransferLongitudinal / 2f)
                + (weightTransferLateral / 2f);


        // Apply normalized load to base friction
        frontLeftFriction = FastMath.clamp(frontLeftFriction, 0.5f,
                                                           100f);
        frontRightFriction = FastMath.clamp(frontRightFriction, 0.5f,
                                                           100f);

        backLeftFriction = FastMath.clamp(backLeftFriction, 1f,
                                                           100f);
        backRightFriction = FastMath.clamp(backRightFriction, 1f,
                                                           100f);

        List<Float> results = new ArrayList<>();
        results.add(flLoad);
        results.add(frLoad);
        results.add(rlLoad);
        results.add(rrLoad);

        return results;
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
//                angularVelocity.y * 1000f,  // stronger yaw damping
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

    public float computeSlipAngle(Vector3f wheelForward, Vector3f wheelVelocity) {
        // Project velocity onto ground plane
        Vector3f v = wheelVelocity.clone();
        v.y = 0;
        wheelForward = wheelForward.clone();
        wheelForward.y = 0;

        if (v.lengthSquared() < 1e-4f) {
            return 0f; // not moving, no slip
        }

        v.normalizeLocal();
        wheelForward.normalizeLocal();

        float dot = wheelForward.dot(v);
        float det = wheelForward.x * v.z - wheelForward.z * v.x; // 2D cross product
        return FastMath.atan2(det, dot); // signed slip angle in radians
    }

    public float computeSlipRatio(float wheelAngularVelocity, float wheelRadius,
                                  Vector3f wheelVelocity, Vector3f wheelForward) {
        float vLong = wheelVelocity.dot(wheelForward); // forward velocity component
        float wheelSpeed = wheelAngularVelocity * wheelRadius;

        if (Math.abs(vLong) < 0.5f) return 0f; // avoid divide by zero at standstill

        return (wheelSpeed - vLong) / Math.abs(vLong);
    }

    public void updatePhysics(VehicleWheel wheel, float physicsTpf) {
        // Get contact info
        Vector3f contactPoint = wheel.getCollisionLocation();
        if (contactPoint == null) return;
        Vector3f n = wheel.getCollisionNormal();
        if (n == null) n = Vector3f.UNIT_Y;

        // --- A) Build orthonormal wheel frame from ground normal ---
        Vector3f fRaw = wheel.getWheelSpatial().getWorldRotation().mult(Vector3f.UNIT_Z);
        Vector3f f = fRaw.subtract(n.mult(fRaw.dot(n))).normalizeLocal();  // forward in plane
        Vector3f r = n.cross(f).normalizeLocal();                          // right
        f = r.cross(n).normalizeLocal();                                   // re-orthogonalize

        // --- B) Relative velocity at contact ---
        Vector3f rel = contactPoint.subtract(control.getPhysicsLocation());
        Vector3f v = control.getLinearVelocity().add(control.getAngularVelocity().cross(rel));
        float vLong = v.dot(f);
        float vLat  = v.dot(r);

        // --- C) Slip definitions ---
        float slipAngle = FastMath.atan2(vLat, FastMath.abs(vLong) + 0.1f);
        float omega = wheel.getDeltaRotation() / physicsTpf;   // rad/s
        float vWheel = omega * wheel.getRadius();
        float slipRatio = (vWheel - vLong) / (FastMath.abs(vLong) + 0.5f);
        slipRatio = FastMath.clamp(slipRatio, -1.5f, 1.5f);

        // --- Normal load approximation ---
        float totalWeight = control.getMass() * 9.81f;      // N
        float load = totalWeight / 4;  // assume even distribution

        // tune scaling factor

        // --- D) Lateral force curve ---
        float muLat = 1.0f;
        float Cα = 15000f;   // half stiffness
        float peakA = 0.12f; // ~7°
        float absA = FastMath.abs(slipAngle);
        float FyLin = Cα * slipAngle;
        float Fy = (absA <= peakA)
                ? FyLin
                : (Cα * peakA) * (peakA / absA) * FastMath.sign(slipAngle);
        float FyMax = muLat * load;
        Fy = FastMath.clamp(Fy, -FyMax, FyMax);
        if (absA < 0.03f) Fy *= 0.2f;  // let it launch straight

        // --- E) Longitudinal force curve ---
        float muLong = 1.1f;
        float FxMax = muLong * load;
        float s = slipRatio;
        float a = 10f;
        float Fx = FxMax * ((a * s) / (1f + a * FastMath.abs(s)));
        if (FastMath.abs(s) > 0.6f) Fx *= 0.9f;

        // --- Inject car’s accel/brake forces ---
        float driveForce = accelerationValue;
        System.out.println(driveForce);
        float brakeForce = 0;

        // apply only to driven wheels

            Fx += driveForce;
            Fx -= Math.signum(vLong) * brakeForce;


        // --- F) Elliptical friction limit ---
        float ax = FxMax;
        float ay = FyMax * 0.8f;
        float scale = FastMath.sqrt((Fx*Fx)/(ax*ax) + (Fy*Fy)/(ay*ay));
        if (scale > 1f) {
            Fx /= scale;
            Fy /= scale;
        }

        // --- G) Apply our custom forces ---
        // (disable built-in tire friction elsewhere!)
        Vector3f force = f.clone().multLocal(Fx).addLocal(r.clone().multLocal(Fy));
        Vector3f forceLong = f.mult(Fx); // forward/back
        Vector3f forceLat  = r.mult(Fy); // sideways

        // Accel/brake goes into CoM (no roll-over explosions)
        control.applyCentralForce(forceLong);

        // Sideways force goes at wheel contact (so you can still drift)
        control.applyForce(forceLat, contactPoint.subtract(control.getPhysicsLocation()));


        // --- Debug ---
        System.out.printf(
                "[%s] vLong=%.2f vLat=%.2f slipAngle=%.2f° slipRatio=%.2f Fx=%.1f Fy=%.1f load=%.1f%n",
                wheel.getWheelSpatial().getName(),
                vLong, vLat, slipAngle * FastMath.RAD_TO_DEG, slipRatio,
                Fx, Fy, load
        );
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