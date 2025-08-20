package jMonkeyEngine.Entities;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.math.Vector3f;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An example Vehicle, built around iSteven's "Nissan GT-R" model.
 */
public class Nismo extends Vehicle {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger2
            = Logger.getLogger(Nismo.class.getName());
    // *************************************************************************
    // constructors

    public Nismo() {
        super("Nismo");
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
        float linearDamping = 0.002f;
        setChassis(
                "gtr_nismo", "scene.gltf", assetManager, mass, linearDamping);

        float diameter = 0.74f;
        WheelModel lFrontWheel = new DarkAlloyWheel(diameter);
        WheelModel rFrontWheel = new DarkAlloyWheel(diameter);
        WheelModel lRearWheel = new DarkAlloyWheel(diameter);
        WheelModel rRearWheel = new DarkAlloyWheel(diameter);
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
        float wheelX = 0.8f; // half of the axle track
        float axleY = 0.32f; // height of the axles relative to vehicle's CoG
        float frontZ = 1.42f;
        float rearZ = -1.36f;
        float damping = 0.009f; // extra linear damping
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

            // how much weight the suspension can take before it bottoms out
            // Setting this too low will make the wheels sink into the ground.
            suspension.setMaxForce(7_000f);

            // the stiffness of the suspension
            // Setting this too low can cause odd behavior.
            suspension.setStiffness(12.5f);

            // how fast the suspension will compress
            // 1 = slow, 0 = fast.
            suspension.setCompressDamping(0.3f);

            // how quickly the suspension will rebound back to height
            // 1 = slow, 0 = fast.
            suspension.setRelaxDamping(0.4f);
        }
        /*
         * Give each wheel a tire with friction.
         */
        for (Wheel wheel : listWheels()) {
            wheel.setTireModel(new Tire01());
            wheel.setFriction(1e6f);
        }

        build(); // must be invoked last, to complete the Vehicle
    }

    @Override
    public void prePhysicsTick(PhysicsSpace physicsSpace, float v) {

    }
}
