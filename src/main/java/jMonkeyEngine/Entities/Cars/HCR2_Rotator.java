package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import jMonkeyEngine.Entities.Cars.VehicleModels.Rotator;

public class HCR2_Rotator extends Car {
    public HCR2_Rotator(AssetManager assetManager, PhysicsSpace physicsSpace, Rotator car) {
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