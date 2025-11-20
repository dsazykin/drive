package jMonkeyEngine.Entities.Cars.VehicleModels;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.math.Vector3f;
import jMonkeyEngine.Entities.Cars.Car;
import jMonkeyEngine.Entities.Cars.CarComponents.Steering;
import jMonkeyEngine.Entities.Cars.CarComponents.Suspension;
import jMonkeyEngine.Entities.Cars.CarComponents.Tire.Tire01;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.RotatorFrontWheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.RotatorRearWheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.Wheel;
import jMonkeyEngine.Entities.Cars.CarComponents.Wheel.WheelModel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An example 3-wheel Vehicle, built around oakar258's "HCR2 Rotator" model.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class Rotator extends Car {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger2
            = Logger.getLogger(Rotator.class.getName());
    // *************************************************************************
    // constructors

    public Rotator() {
        super("Rotator", new PhysicsConfig(
                220f / 3.6f,        // maxSpeed
                0.0932516621f,      // accelerationConstant
                13.8f,              // maxReverse
                0.19321187354f,     // reverseConstant
                2.415f,             // wheelBase
                1.267f,             // trackWidth
                0.49f,              // cgHeight
                970,                // mass
                0.39f,              // dragCoefficient
                1.65f,              // frontalArea
                0.012f              // rollingResistanceCoefficient
        ));
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
        float mass = 525f; // in kilos
        float linearDamping = 0.02f;
        setChassis("hcr2_rotator", "chassis", assetManager, mass,
                linearDamping);

        float rearDiameter = 1.087f;
        float frontDiameter = 0.77f;
        WheelModel frontWheel = new RotatorFrontWheel(frontDiameter);
        WheelModel lRearWheel = new RotatorRearWheel(rearDiameter);
        WheelModel rRearWheel = new RotatorRearWheel(rearDiameter);
        frontWheel.load(assetManager);
        lRearWheel.load(assetManager);
        rRearWheel.load(assetManager);
        /*
         * By convention, wheels are modeled for the left side, so
         * wheel models for the right side require a 180-degree rotation.
         */
        rRearWheel.flip();
        /*
         * Add the wheels to the vehicle.
         */
        float wheelX = 0.972f; // half of the (rear) axle track
        float frontY = 0.09f; // height of front axle relative to vehicle's CoG
        float rearY = 0.25f; // height of rear axle relative to vehicle's CoG
        float frontZ = 2.239f;
        float rearZ = -1.15f;
        float mainBrake = 3_000f; // in front only
        float parkingBrake = 3_000f; // in front only
        float damping = 0.09f; // extra linear damping
        addWheel(frontWheel, new Vector3f(0f, frontY, frontZ),
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
            suspension.setMaxForce(12_000f);

            // the stiffness of the suspension
            // Setting this too low can cause odd behavior.
            suspension.setStiffness(24f);

            // how fast the suspension will compress
            // 1 = slow, 0 = fast.
            suspension.setCompressDamping(0.5f);

            // how quickly the suspension will rebound back to height
            // 1 = slow, 0 = fast.
            suspension.setRelaxDamping(0.65f);
        }

        // Give each wheel a tire with friction.
        for (Wheel wheel : listWheels()) {
            wheel.setTireModel(new Tire01());
            wheel.setFriction(1.3f);
        }

        build(); // must be invoked last, to complete the Vehicle
    }

    @Override
    public void prePhysicsTick(PhysicsSpace physicsSpace, float v) {

    }
}
