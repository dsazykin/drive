package jMonkeyEngine.Entities;

import com.jme3.asset.AssetManager;
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

public class Car {
    private final VehicleControl control;
    private final Node carNode;

    private float steeringValue = 0;
    private float accelerationValue = 0;
    private float targetSteeringValue = 0;
    private final float maxSpeed = (float) (320 / 3.6);
    private final float mass = 1640;
    private final float accelerationConstant = 0.1441128652f;

    private boolean accelerating = false;
    private boolean breaking = false;

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

    public Car(AssetManager assetManager, PhysicsSpace physicsSpace) {
        float stiffness = 120.0f;
        float compValue = 0.2f;
        float dampValue = 0.3f;

        carNode = (Node) assetManager.loadModel("Models/Car/Car.scene");
        carNode.setShadowMode(RenderQueue.ShadowMode.Cast);
        Geometry chassis = findGeom(carNode, "Car");

        CollisionShape carHull = CollisionShapeFactory.createDynamicMeshShape(chassis);
        control = new VehicleControl(carHull, mass);
        carNode.addControl(control);

        control.setSuspensionCompression(compValue * 2.0f * FastMath.sqrt(stiffness));
        control.setSuspensionDamping(dampValue * 2.0f * FastMath.sqrt(stiffness));
        control.setSuspensionStiffness(stiffness);
        control.setMaxSuspensionForce(10000);

        Vector3f wheelDirection = new Vector3f(0, -1, 0);
        Vector3f wheelAxle = new Vector3f(-1, 0, 0);

        float wheelRadius = getWheelRadius("WheelFrontRight");
        float back_wheel_h = (wheelRadius * 1.7f) - 1f;
        float front_wheel_h = (wheelRadius * 1.9f) - 1f;

        addWheel("WheelFrontRight", front_wheel_h, wheelRadius, true, wheelDirection, wheelAxle);
        addWheel("WheelFrontLeft", front_wheel_h, wheelRadius, true, wheelDirection, wheelAxle);
        addWheel("WheelBackRight", back_wheel_h, wheelRadius, false, wheelDirection, wheelAxle);
        addWheel("WheelBackLeft", back_wheel_h, wheelRadius, false, wheelDirection, wheelAxle);

        // Front wheels - more grip for stability
        control.getWheel(0).setFrictionSlip(3.5f); // front left
        control.getWheel(1).setFrictionSlip(3.5f); // front right

        // Rear wheels - slightly less grip to prevent oversteer
        control.getWheel(2).setFrictionSlip(4f); // rear left
        control.getWheel(3).setFrictionSlip(4f); // rear right

        physicsSpace.add(control);
    }

    private void addWheel(String name, float heightOffset, float radius, boolean front,
                          Vector3f direction, Vector3f axle) {
        Geometry wheel = findGeom(carNode, name);
        wheel.center();
        BoundingBox box = (BoundingBox) wheel.getModelBound();
        control.addWheel(wheel.getParent(), box.getCenter().add(0, -heightOffset, 0),
                         direction, axle, 0.2f, radius, front);
    }

    private float getWheelRadius(String name) {
        Geometry wheel = findGeom(carNode, name);
        wheel.center();
        BoundingBox box = (BoundingBox) wheel.getModelBound();
        return box.getYExtent();
    }

    private Geometry findGeom(Spatial spatial, String name) {
        if (spatial instanceof Node) {
            Node node = (Node) spatial;
            for (Spatial child : node.getChildren()) {
                Geometry result = findGeom(child, name);
                if (result != null) return result;
            }
        } else if (spatial instanceof Geometry && spatial.getName().startsWith(name)) {
            return (Geometry) spatial;
        }
        return null;
    }

    public VehicleControl getControl() {
        return control;
    }

    public Node getCarNode() {
        return carNode;
    }
}