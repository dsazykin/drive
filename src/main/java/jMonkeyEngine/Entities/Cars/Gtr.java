package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.control.VehicleControl;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import jMonkeyEngine.Entities.Cars.VehicleModels.Nismo;
import jMonkeyEngine.Entities.Cars.VehicleModels.Vehicle;

public class Gtr extends Car{
    public Gtr(AssetManager assetManager, PhysicsSpace physicsSpace, Nismo car) {
        super(assetManager, physicsSpace, car,
                320f / 3.6f, 0.1387753516f,
                16.667f, 0.287534238854f,
                2.78f, 1.6f,
                0.5f, 1735);
    }
}