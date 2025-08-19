package jMonkeyEngine.Entities;

import com.jme3.anim.AnimComposer;
import com.jme3.app.state.AppStateManager;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.animation.RagUtils;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.bullet.joints.PhysicsJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.objects.PhysicsVehicle;
import com.jme3.bullet.objects.VehicleWheel;
import com.jme3.bullet.objects.infos.RigidBodyMotionState;
import com.jme3.bullet.util.CollisionShapeFactory;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A vehicle based on Bullet's btRaycastVehicle, with a single Engine and a
 * single GearBox. In order to simulate articulated vehicles such as motorcycles
 * and semi-trailer trucks, a Vehicle may contain multiple physics bodies.
 *
 * Derived from the Car and Vehicle classes in the Advanced Vehicles project.
 */
abstract public class Vehicle
        implements PhysicsTickListener
         {
    // *************************************************************************
    // constants and loggers

    /**
     * factor to convert km/hr to miles per hour
     */
    final public static float KPH_TO_MPH = 0.62137f;
    /**
     * factor to convert km/hr to wu/sec
     */
    final public static float KPH_TO_WUPS = 0.277778f;
    /**
     * message logger for this class
     */
    final public static Logger logger
            = Logger.getLogger(Vehicle.class.getName());
    // *************************************************************************
    // fields

    /**
     * linear damping due to air resistance on the chassis (&ge;0, &lt;1)
     */
    private float chassisDamping;
    /**
     * the fraction of the total mass in each body (each element &ge;0, &le;1)
     * or null if not determined yet
     */
    private float[] massFractions;
    /**
     * ratio of the steeringWheelAngle to the turn angle of any wheels used for
     * steering
     */
    private float steeringRatio = 2f;
    /**
     * rotation of the steering wheel, handlebars, or tiller (in radians,
     * negative&rarr;left, 0&rarr;neutral, positive&rarr;right)
     */
    private float steeringWheelAngle;
    /**
     * physics body associated with the Engine
     */
    private VehicleControl engineBody;
    /**
     * support the chassis and configure acceleration, steering, and braking
     */
    final private List<Wheel> wheels = new ArrayList<>(4);
    /**
     * temporary storage for the vehicle's orientation
     */
    final private static Matrix3f tmpOrientation = new Matrix3f();
    /**
     * scene-graph subtree that represents this Vehicle
     */
    final private Node node;
    /**
     * computer-graphics (C-G) model to visualize the whole Vehicle except for
     * its wheels
     */
    private Spatial chassis;
    /**
     * descriptive name (not null)
     */
    final private String name;
    /**
     * default transform of each body relative to the engine body, or null if
     * transforms have not yet been determined
     */
    private Transform[] relativeTransforms;
    // *************************************************************************
    // constructors

    /**
     * Instantiate an unloaded Vehicle with the specified name.
     *
     * @param name the desired name (not null)
     */
    protected Vehicle(String name) {

        this.name = name;
        this.node = new Node("Vehicle: " + name);
    }
    // *************************************************************************
    // new methods exposed

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
                wheelModel, engineBody, connectionLocation, steering, extraDamping);

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
        return engineBody;
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

    // *************************************************************************
    // new protected methods

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
        this.engineBody = new VehicleControl(shape, mass);
        /*
         * Configure damping for the engine body,
         * to simulate drag due to air resistance.
         */
        engineBody.setLinearDamping(damping);

        controlledSpatial.addControl(engineBody);
    }

    // *************************************************************************
    // HasNode methods

    /**
     * Access the scene-graph subtree that visualizes this Vehicle.
     *
     * @return the pre-existing instance (not null)
     */
    public Node getNode() {
        return node;
    }
    // *************************************************************************
    // Loadable methods

    /**
     * Load the assets of this Vehicle.
     *
     * @param assetManager for loading assets (not null)
     */
    public void load(AssetManager assetManager) {
        // subclasses should override
    }
    // *************************************************************************
    // PhysicsTickListener methods

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

    private void updateRelativeTransforms() {
//        VehicleControl[] bodies = listBodies();
//        int numBodies = bodies.length;
//        this.relativeTransforms = new Transform[numBodies];
//
//        Transform e2r = engineBody.getSpatial().getWorldTransform(); // alias
//        Transform rootToEngine = e2r.invert();
//        for (int bodyIndex = 0; bodyIndex < numBodies; ++bodyIndex) {
//            VehicleControl body = bodies[bodyIndex];
//            Transform b2r = body.getSpatial().getWorldTransform(); // alias
//            Transform bodyToEngine = MyMath.combine(b2r, rootToEngine, null);
//            this.relativeTransforms[bodyIndex] = bodyToEngine;
//        }
    }
}
