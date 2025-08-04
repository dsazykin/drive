package jMonkeyEngine.Road;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import jMonkeyEngine.Chunks.ChunkCoord;
import jMonkeyEngine.Terrain.OpenSimplex2;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoadGenerator {
    private List<Vector2f> pathPoints = Collections.synchronizedList(new ArrayList<>());
    private Vector2f currentPosition = new Vector2f(0, 0);

    private final int CHUNK_SIZE;
    private final float SCALE;
    private final long SEED;

    private final float STEP_SIZE = 7f;
    private final float FREQ = 0.1f;

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
    public List<Vector2f> getRoadPointsInChunk(int chunkX, int chunkZ, float[][] terrain) {
        List<Vector2f> result = new ArrayList<>();

        // World bounds of this chunk
        float minX = chunkX * (CHUNK_SIZE - 1);
        float maxX = minX + (CHUNK_SIZE - 1);
        float minZ = chunkZ * (CHUNK_SIZE - 1);
        float maxZ = minZ + (CHUNK_SIZE - 1);

        // Trace from a start point (e.g., (0,0)) until we're well past the current chunk
        Vector2f pos = new Vector2f(0, 0);

        for (int i = 0; i < 100; i++) {
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
            pos = findBestDirection(0, pos, terrain, 2);
//            Vector2f dir =
//                    new Vector2f(FastMath.cos(angle), FastMath.sin(angle)).normalizeLocal();
//            pos = pos.add(dir.mult(STEP_SIZE));
            //System.out.println("found best point");
        }

        return result;
    }

    private static final float ANGLE_STEP = 0.2f; // adjust for granularity
    private static final float ANGLE_RANGE = (float) (Math.PI / 2);

    private Vector2f findBestDirection(float angle, Vector2f pos, float[][] terrain, int depth) {
        Result best = recursiveSearch(angle, pos, terrain, depth, terrain[(int) pos.x][(int) pos.y], null);
        if (best == null || best.firstMove == null) {
            System.err.println("⚠️ No valid path found from position " + pos);
            return pos; // fallback: stay in place
        }
        return best.firstMove;
    }

    private Result recursiveSearch(float angle, Vector2f pos, float[][] terrain, int depth, float prevHeight, Vector2f firstMove) {
        if (depth == 0) {
            return new Result(0f, firstMove);
        }

        Result bestResult = null;

        for (float delta = -ANGLE_RANGE; delta <= ANGLE_RANGE; delta += ANGLE_STEP) {
            float newAngle = angle + delta;
            Vector2f dir =
                    new Vector2f(FastMath.cos(newAngle), FastMath.sin(newAngle)).normalizeLocal();
            Vector2f newPos = pos.add(dir.mult(STEP_SIZE));

            int x = Math.round(newPos.x);
            int y = Math.round(newPos.y);

            if (x < 0 || y < 0 || x >= terrain.length || y >= terrain[0].length)
                continue;

            float height = terrain[x][y];
            float heightCost = FastMath.abs(prevHeight - height) * 15f;
            float directionCost = (pos.x - newPos.x) * 0.05f;
            float currentStepCost = heightCost + directionCost;

//            System.out.println("angle: " + newAngle);
//            System.out.println("x: " + newPos.x + " y: " + newPos.y);
//            System.out.println("height cost: " + heightCost);
//            System.out.println("direction cost: " + directionCost);
//            System.out.println("step cost: " + currentStepCost);

            Vector2f initialMove = (firstMove == null) ? newPos : firstMove;

            Result recursive =
                    recursiveSearch(0, newPos, terrain, depth - 1, height, initialMove);
            if (recursive == null)
                continue;

            float totalCost = currentStepCost + recursive.totalCost;

            if (bestResult == null || totalCost < bestResult.totalCost) {
                bestResult = new Result(totalCost, initialMove);
            }

            //System.out.println("angle checked");
        }

        return bestResult;
    }

    private static class Result {
        float totalCost;
        Vector2f firstMove; // ← best direction to move from the original pos

        public Result(float totalCost, Vector2f firstMove) {
            this.totalCost = totalCost;
            this.firstMove = firstMove;
        }
    }

}
