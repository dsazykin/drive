package jMonkeyEngine.Entities.Cars;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.PhysicsSpace;
import jMonkeyEngine.Entities.Cars.VehicleModels.GrandTourer;

public class GT extends Car {
    public GT(AssetManager assetManager, PhysicsSpace physicsSpace, GrandTourer car) {
        super(assetManager, physicsSpace, car,
              new PhysicsConfig(
                      171f / 3.6f,        // maxSpeed
                      0.1311915939f,      // accelerationConstant
                      7.0f,               // maxReverse
                      0.5f,               // reverseConstant
                      3.27f,              // wheelBase
                      1.62f,              // trackWidth
                      0.8f,               // cgHeight
                      2002,               // mass
                      0.40f,              // dragCoefficient
                      2.97f,              // frontalArea
                      0.018f              // rollingResistanceCoefficient
              )
        );
    }
}