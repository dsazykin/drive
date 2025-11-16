package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import jMonkeyEngine.Entities.Cars.VehicleModels.GrandTourer;

public class GT extends Car {
    public GT(AssetManager assetManager, PhysicsSpace physicsSpace, GrandTourer car) {
        super(assetManager, physicsSpace, car,
              new PhysicsConfig(
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
              )
        );
    }
}