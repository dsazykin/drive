package jMonkeyEngine.Road;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import jMonkeyEngine.Terrain.OpenSimplex2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RoadGenerator {
    private List<Vector2f> pathPoints = Collections.synchronizedList(new ArrayList<>());
    private Vector2f currentPosition = new Vector2f(0, 0);

    private final int CHUNK_SIZE;
    private final float SCALE;
    private final long SEED;

    private final float STEP_SIZE = 0.001f;
    private final float FREQ = 0.2f;

    public RoadGenerator(int chunkSize, float scale,
                         long seed) {
        pathPoints.add(currentPosition.clone()); // starting point

        CHUNK_SIZE = chunkSize;
        SCALE = scale;
        SEED = seed;
    }

    private float angleAt(Vector2f pos) {
        return (float) (OpenSimplex2.noise2(SEED + 5678, pos.x * FREQ, pos.y * FREQ) * Math.PI);
    }

    // Returns the portion of the road that intersects the chunk bounds
    public List<Vector2f> getRoadPointsInChunk(int chunkX, int chunkZ) {
        List<Vector2f> result = new ArrayList<>();

        // World bounds of this chunk
        float minX = chunkX * (CHUNK_SIZE - 1) / SCALE;
        float maxX = minX + (CHUNK_SIZE - 1) / SCALE;
        float minZ = chunkZ * (CHUNK_SIZE - 1) / SCALE;
        float maxZ = minZ + (CHUNK_SIZE - 1) / SCALE;

        // Trace from a start point (e.g., (0,0)) until we're well past the current chunk
        Vector2f pos = new Vector2f(0, 0);

        for (int i = 0; i < 20000; i++) {
            float x = pos.x;
            float z = pos.y;

            // Only keep road points that fall within this chunk
            if (x >= minX - 1 && x <= maxX + 1 && z >= minZ - 1 && z <= maxZ + 1) {
                result.add(pos.clone());
            }

            // Stop once we're well beyond this chunk (to limit trace length)
            if (x > maxX + 2 && z > maxZ + 2) break;

            // March to next point
            float angle = angleAt(pos);
            Vector2f dir = new Vector2f(FastMath.cos(angle), FastMath.sin(angle)).normalizeLocal();
            pos = pos.add(dir.mult(STEP_SIZE));
        }

        return result;
    }
}
