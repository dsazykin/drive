package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;

class RoadEndpoint {
    public final Vector3f left;
    public final Vector3f right;

    public RoadEndpoint(Vector3f left, Vector3f right) {
        this.left = left;
        this.right = right;
    }
}
