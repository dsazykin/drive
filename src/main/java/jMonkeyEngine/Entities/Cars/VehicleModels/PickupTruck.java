package jMonkeyEngine.Entities.Cars.VehicleModels;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.math.Vector3f;
import jMonkeyEngine.Entities.Cars.CarComponents.Steering;
import jMonkeyEngine.Entities.Cars.CarComponents.Suspension;
import jMonkeyEngine.Entities.Cars.CarComponents.Tire.Tire01;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.RangerWheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.Wheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.WheelModel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An example Vehicle, built around mauro.zampaoli's "Ford Ranger" model.
 */
public class PickupTruck extends Vehicle {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger2
            = Logger.getLogger(PickupTruck.class.getName());
    // *************************************************************************
    // constructors

    public PickupTruck() {
        super("Pickup Truck");
    }
    // *************************************************************************
    // Vehicle methods

    /**
     * Load this Vehicle from assets.
     *
     * @param assetManager for loading assets (not null)
     */
    @Override
    public void load(AssetManager assetManager) {
        /*
         * Load the C-G model with everything except the wheels.
         * Bullet refers to this as the "chassis".
         */
        float mass = 1_550f; // in kilos
        float linearDamping = 0.01f;
        setChassis("ford_ranger", "pickup", assetManager, mass, linearDamping);

        float diameter = 0.8f;
        WheelModel lFrontWheel = new RangerWheel(diameter);
        WheelModel rFrontWheel = new RangerWheel(diameter);
        WheelModel lRearWheel = new RangerWheel(diameter);
        WheelModel rRearWheel = new RangerWheel(diameter);
        lFrontWheel.load(assetManager);
        rFrontWheel.load(assetManager);
        lRearWheel.load(assetManager);
        rRearWheel.load(assetManager);
        /*
         * By convention, wheels are modeled for the left side, so
         * wheel models for the right side require a 180-degree rotation.
         */
        rFrontWheel.flip();
        rRearWheel.flip();
        /*
         * Add the wheels to the vehicle.
         * For rear-wheel steering, it will be necessary to "flip" the steering.
         */
        float wheelX = 0.75f; // half of the axle track
        float axleY = 0.45f; // height of the axles relative to vehicle's CoG
        float frontZ = 1.76f;
        float rearZ = -1.42f;
        float mainBrake = 4_000f; // all 4 wheels
        float parkingBrake = 25_000f; // in rear only
        float damping = 0.04f; // extra linear damping
        addWheel(lFrontWheel, new Vector3f(+wheelX, axleY, frontZ),
                 Steering.DIRECT, damping);
        addWheel(rFrontWheel, new Vector3f(-wheelX, axleY, frontZ),
                 Steering.DIRECT, damping);
        addWheel(lRearWheel, new Vector3f(+wheelX, axleY, rearZ),
                 Steering.UNUSED, damping);
        addWheel(rRearWheel, new Vector3f(-wheelX, axleY, rearZ),
                 Steering.UNUSED, damping);
        /*
         * Configure the suspension.
         *
         * This vehicle applies the same settings to each wheel,
         * but that isn't required.
         */
        for (Wheel wheel : listWheels()) {
            Suspension suspension = wheel.getSuspension();

            suspension.setMaxTravelCm(1_000f);

            // how much weight the suspension can take before it bottoms out
            // Setting this too low will make the wheels sink into the ground.
            suspension.setMaxForce(20_000f);

            // the stiffness of the suspension
            // Setting this too low can cause odd behavior.
            suspension.setStiffness(20f);
        }

        // Give each wheel a tire with friction.
        for (Wheel wheel : listWheels()) {
            wheel.setTireModel(new Tire01());
            wheel.setFriction(1f);
        }

        build(); // must be invoked last, to complete the Vehicle
    }

    @Override
    public void prePhysicsTick(PhysicsSpace physicsSpace, float v) {

    }
}
