package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import jMonkeyEngine.Entities.Cars.VehicleModels.Nismo;

public class Gtr extends Car {
    public Gtr(AssetManager assetManager, PhysicsSpace physicsSpace, Nismo car) {
        super(assetManager, physicsSpace, car,
            new PhysicsConfig(
                320f / 3.6f,        // maxSpeed
                0.1387753516f,      // accelerationConstant
                16.667f,            // maxReverse
                0.287534238854f,    // reverseConstant
                2.78f,              // wheelBase
                1.6f,               // trackWidth
                0.5f,               // cgHeight
                1735,               // mass
                0.26f,               // dragCoefficient
                2.15f,               // frontalArea
                0.015f              // rollingResistanceCoefficient
            )
        );
    }
}