package jMonkeyEngine.Entities;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsVehicle;
import com.jme3.bullet.objects.VehicleWheel;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.logging.Logger;

/**
 * A single wheel of a Vehicle, including its suspension and brakes.
 *
 * Derived from the Wheel class in the Advanced Vehicles project.
 */
public class Wheel {
    // *************************************************************************
    // constants and loggers

    /**
     * message logger for this class
     */
    final public static Logger logger
            = Logger.getLogger(Wheel.class.getName());
    // *************************************************************************
    // fields

    /**
     * additional linear damping applied to the chassis when this wheel has
     * traction
     */
    final private float extraDamping;
    /**
     * grip degradation: 1 = full grip the tire allows, 0 = worn-out tire
     */
    private float grip = 1f;
    /**
     * index among the physics body's wheels (&ge;0)
     */
    final private int wheelIndex;

    private PacejkaTireModel tireModel;
    /**
     * physics body to which this Wheel is added
     */
    final private PhysicsVehicle body;
    /**
     * relationship to the steering system (not null)
     */
    private Steering steering;
    /**
     * parameters of the associated suspension spring
     */
    final private Suspension suspension;
    /**
     * Vehicle that contains this Wheel
     */
    final private Vehicle vehicle;
    /**
     * wheel's physics object
     */
    final private VehicleWheel vehicleWheel;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a wheel (added to the engine body) with the specified
     * parameters.
     *
     * @param vehicle the Vehicle to which this Wheel will be added (not null,
     * alias created)
     * @param wheelIndex the index among the engine body's wheels (&ge;0)
     * @param steering relationship to the steering system (not null)
     * @param suspension the suspension spring (not null, alias created)
     * @param extraDamping the additional linear damping (&ge;0, &lt;1)
     */
    public Wheel(Vehicle vehicle, int wheelIndex, Steering steering,
                 Suspension suspension, float extraDamping) {
        this(vehicle, vehicle.getVehicleControl(), wheelIndex,
             steering, suspension, extraDamping);
    }

    /**
     * Instantiate a wheel with the specified parameters.
     *
     * @param vehicle the Vehicle to which this Wheel will be added (not null,
     * alias created)
     * @param body the physics body to which this Wheel will be added (not null,
     * alias created)
     * @param wheelIndex the index among the body's wheels (&ge;0)
     * @param steering relationship to the steering system (not null)
     * @param suspension the suspension spring (not null, alias created)
     * @param extraDamping the additional linear damping (&ge;0, &lt;1)
     */
    public Wheel(Vehicle vehicle, PhysicsVehicle body, int wheelIndex,
                 Steering steering, Suspension suspension,
                 float extraDamping) {

        this.vehicle = vehicle;
        this.body = body;
        this.wheelIndex = wheelIndex;
        vehicleWheel = body.getWheel(wheelIndex);
        assert vehicleWheel != null;

        this.steering = steering;
        this.suspension = suspension;
        this.extraDamping = extraDamping;

        setFriction(1f);
    }
    // *************************************************************************
    // new methods exposed

    // Pacejka

    /**
     * Determine the wheel's diameter.
     *
     * @return the diameter (in meters)
     */
    public float getDiameter() {
        return vehicleWheel.getWheelSpatial().getLocalScale().y;
        // All 3 components should all be the same.
    }

    /**
     * Determine the friction between this wheel's tire and the ground.
     *
     * @return the coefficient of friction
     */
    public float getFriction() {
        return vehicleWheel.getFrictionSlip();
    }

    /**
     * Determine the tire's grip.
     *
     * @return the fraction of the original grip remaining (&ge;0, &le;1)
     */
    public float getGrip() {
        return grip;
    }

    /**
     * Access the wheel's Suspension.
     *
     * @return the pre-existing instance (not null)
     */
    public Suspension getSuspension() {
        assert suspension != null;
        return suspension;
    }

    /**
     * Access the tire model.
     *
     * @return the pre-existing instance
     */
    public PacejkaTireModel getTireModel() {
        return tireModel;
    }

    /**
     * Access the Vehicle that contains this Wheel.
     *
     * @return the pre-existing instance (not null)
     */
    public Vehicle getVehicle() {
        assert vehicle != null;
        return vehicle;
    }

    /**
     * Access the wheel's physics object.
     *
     * @return the pre-existing instance (not null)
     */
    public VehicleWheel getVehicleWheel() {
        assert vehicleWheel != null;
        return vehicleWheel;
    }

    /**
     * Alter the wheel's diameter.
     *
     * @param diameter the desired diameter (in meters, &gt;0)
     */
    public void setDiameter(float diameter) {

        vehicleWheel.getWheelSpatial().setLocalScale(diameter);
        vehicleWheel.setRadius(diameter / 2);
    }

    /**
     * Alter the friction between this wheel's tire and the ground.
     *
     * @param friction the desired coefficient of friction
     */
    public void setFriction(float friction) {
        vehicleWheel.setFrictionSlip(friction);
    }

    /**
     * Alter the tire's grip.
     *
     * @param grip the fraction of the original grip remaining (&ge;0, &le;1,
     * default=1)
     */
    public void setGrip(float grip) {
        this.grip = grip;
    }

    /**
     * Alter the tire model.
     *
     * @param tireModel the desired model (alias created)
     */
    public void setTireModel(PacejkaTireModel tireModel) {
        this.tireModel = tireModel;
    }
}
