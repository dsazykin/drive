package jMonkeyEngine.Entities.Cars.VehicleModels;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.math.Vector3f;
import jMonkeyEngine.Entities.Cars.CarComponents.Steering;
import jMonkeyEngine.Entities.Cars.CarComponents.Suspension;
import jMonkeyEngine.Entities.Cars.CarComponents.Tire.Tire01;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.CruiserWheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.Wheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.WheelModel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An example Vehicle, built around Thomas Glenn Thorne's "Opel GT Retopo"
 * model.
 */
public class GrandTourer extends Vehicle {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger2
            = Logger.getLogger(GrandTourer.class.getName());
    // *************************************************************************
    // constructors

    public GrandTourer() {
        super("Grand Tourer");
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
        float mass = 1_525f; // in kilos
        float linearDamping = 0.006f;
        setChassis("GT", "scene.gltf", assetManager, mass, linearDamping);

        float diameter = 0.85f;
        WheelModel lFrontWheel = new CruiserWheel(diameter);
        WheelModel rFrontWheel = new CruiserWheel(diameter);
        WheelModel lRearWheel = new CruiserWheel(diameter);
        WheelModel rRearWheel = new CruiserWheel(diameter);
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
        float wheelX = 0.85f; // half of the axle track
        float frontY = 0.32f; // height of front axle relative to vehicle's CoG
        float rearY = 0.40f; // height of rear axle relative to vehicle's CoG
        float frontZ = 1.6f;
        float rearZ = -1.6f;
        float mainBrake = 6_000f; // in front only
        float parkingBrake = 25_000f; // in rear only
        float damping = 0.02f; // extra linear damping
        addWheel(lFrontWheel, new Vector3f(+wheelX, frontY, frontZ),
                 Steering.DIRECT, damping);
        addWheel(rFrontWheel, new Vector3f(-wheelX, frontY, frontZ),
                 Steering.DIRECT, damping);
        addWheel(lRearWheel, new Vector3f(+wheelX, rearY, rearZ),
                 Steering.UNUSED, damping);
        addWheel(rRearWheel, new Vector3f(-wheelX, rearY, rearZ),
                 Steering.UNUSED, damping);
        /*
         * Configure the suspension.
         *
         * This vehicle applies the same settings to each wheel,
         * but that isn't required.
         */
        for (Wheel wheel : listWheels()) {
            Suspension suspension = wheel.getSuspension();

            // how much weight the suspension can take before it bottoms out
            // Setting this too low will make the wheels sink into the ground.
            suspension.setMaxForce(8_000f);

            // the stiffness of the suspension
            // Setting this too low can cause odd behavior.
            suspension.setStiffness(10f);

            // how fast the suspension will compress
            // 1 = slow, 0 = fast.
            suspension.setCompressDamping(0.33f);

            // how quickly the suspension will rebound back to height
            // 1 = slow, 0 = fast.
            suspension.setRelaxDamping(0.45f);
        }

        // Give each wheel a tire with friction.
        for (Wheel wheel : listWheels()) {
            wheel.setTireModel(new Tire01());
            wheel.setFriction(1.6f);
        }

        build(); // must be invoked last, to complete the Vehicle
    }

    @Override
    public void prePhysicsTick(PhysicsSpace physicsSpace, float v) {

    }
}
