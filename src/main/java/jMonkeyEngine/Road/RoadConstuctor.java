package jMonkeyEngine.Road;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import jMonkeyEngine.Chunks.ChunkCoord;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoadConstuctor {

    private final int CHUNK_SIZE;
    private final float SCALE;

    private final float ROAD_WIDTH;

    private final RoadGenerator generator;
    private final AssetManager assetManager;

    private ConcurrentHashMap<ChunkCoord, RoadEndpoint> exitPointMap = new ConcurrentHashMap<>();
    private List<DeferredConnection> deferredJoins = Collections.synchronizedList(new ArrayList<>());

    public RoadConstuctor(int chunkSize, float scale, float roadWidth, RoadGenerator generator,
                          AssetManager assetManager) {
        CHUNK_SIZE = chunkSize;
        SCALE = scale;
        ROAD_WIDTH = roadWidth;
        this.generator = generator;
        this.assetManager = assetManager;
    }

    protected Geometry buildRoad(List<Vector2f> path, float[][] terrain, ChunkCoord chunk,
                                 Vector3f chunkEntryLeft, Vector3f chunkEntryRight) {

        boolean notFirst = (chunkEntryLeft != null && chunkEntryRight != null);

        int extraVerts = notFirst ? 2 : 0;
        int vertexCount = path.size() * 2 + extraVerts;

        Vector3f[] vertices = new Vector3f[vertexCount];
        ColorRGBA[] colors = new ColorRGBA[vertexCount];

        int vertexIndex = 0;

        // Add previous chunk's end vertices
        if (notFirst) {
            vertices[vertexIndex] = chunkEntryLeft;
            vertices[vertexIndex + 1] = chunkEntryRight;
            colors[vertexIndex] = colors[vertexIndex + 1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
            vertexIndex += 2;
        }

        for (int i = 0; i < path.size(); i++) {
            Vector2f center = path.get(i);

            Vector2f dir;
            if (i < path.size() - 1) {
                dir = path.get(i + 1).subtract(center).normalize();
            } else if (i > 0) {
                dir = center.subtract(path.get(i - 1)).normalize();
            } else {
                dir = new Vector2f(1, 0); // fallback
            }

            Vector2f left2D = new Vector2f(-dir.y, dir.x).mult(ROAD_WIDTH / 2f);
            Vector2f leftPt = center.add(left2D);
            Vector2f rightPt = center.subtract(left2D);

            float height = Math.max(
                    sampleHeight(leftPt, terrain, chunk.x, chunk.z),
                    sampleHeight(rightPt, terrain, chunk.x, chunk.z)
            ) + 0.1f;

            Vector3f leftVector = new Vector3f(leftPt.x, height, leftPt.y);
            Vector3f rightVector = new Vector3f(rightPt.x, height, rightPt.y);

            vertices[vertexIndex] = leftVector;
            vertices[vertexIndex + 1] = rightVector;
            colors[vertexIndex] = colors[vertexIndex + 1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
            vertexIndex += 2;

            if (i == path.size() - 1) {
                exitPointMap.put(chunk, new RoadEndpoint(leftVector, rightVector));
            }
        }

        // total segments = number of vertex pairs - 1
        int segmentCount = (vertexCount / 2) - 1;
        int[] indices = new int[segmentCount * 6];

        int idx = 0;
        for (int i = 0; i < segmentCount; i++) {
            int v0 = i * 2;
            int v1 = v0 + 1;
            int v2 = v0 + 2;
            int v3 = v0 + 3;

            indices[idx++] = v0;
            indices[idx++] = v2;
            indices[idx++] = v1;

            indices[idx++] = v1;
            indices[idx++] = v2;
            indices[idx++] = v3;
        }

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.updateBound();

        Geometry road = new Geometry("Road", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        road.setMaterial(mat);

        RigidBodyControl rb = new RigidBodyControl(new MeshCollisionShape(mesh), 0);
        road.addControl(rb);

        return road;
    }

    protected List<Geometry> onChunkLoad(ChunkCoord thisChunk, List<Vector2f> path, float[][] terrain) {
        List<Geometry> roads = new ArrayList<>();

        // Check if this is the origin chunk
        if (!(thisChunk.x == 0 && thisChunk.z == 0)) {

            // If it isn't find out where the road came from and extend
            ChunkCoord prevChunk = getPrevChunk(path.get(0));
            //System.out.println("suspected previous chunk: " + prevChunk + "for: " + thisChunk);

            if (exitPointMap.containsKey(prevChunk)) {
                // Build road with continuation
                RoadEndpoint prev = exitPointMap.get(prevChunk);
                Geometry road = buildRoad(path, terrain, thisChunk, prev.left, prev.right);
                roads.add(road);
                //System.out.println("built chunk: (" + thisChunk.x + ", " + thisChunk.z + ")");

                //System.out.println("built dependent roads for chunk: (" + thisChunk.x + ", " +
                // thisChunk.z + ")");
            } else {
                // Defer connection until prevChunk is available
                DeferredConnection deferred = new DeferredConnection(thisChunk, prevChunk, path, terrain);
                deferredJoins.add(deferred);
                return null;
            }

            // If it is just build the road
        }  else {
            Geometry road = buildRoad(path, terrain, thisChunk, null, null);
            roads.add(road);
        }

        List<Geometry> dependentRoads = onRoadBuilt(thisChunk);
        roads.addAll(dependentRoads);

        return roads;
    }

    protected List<Geometry> onRoadBuilt(ChunkCoord thisChunk) {
        List<DeferredConnection> toProcess = new ArrayList<>();
        List<Geometry> roads = new ArrayList<>();

        // Step 1: Find all matches first (safe iteration)
        for (DeferredConnection dc : deferredJoins) {
            if (dc.prevChunk.equals(thisChunk)) {
                toProcess.add(dc);
            }
        }

        // Step 2: Process each one (no iterator active now)
        for (DeferredConnection dc : toProcess) {
            RoadEndpoint prev = exitPointMap.get(thisChunk);
            Geometry road = buildRoad(dc.path, dc.terrain, dc.thisChunk, prev.left, prev.right);
            roads.add(road);
            roads.addAll(onRoadBuilt(dc.thisChunk)); // safe recursion, no iterator involved
        }

        // Step 3: Remove them afterward
        deferredJoins.removeAll(toProcess);

        return roads;
    }

    private float sampleHeight(Vector2f pos, float[][] terrain, int chunkX, int chunkZ) {
        float chunkOriginX = chunkX * CHUNK_SIZE * (SCALE / 4f);
        float chunkOriginZ = chunkZ * CHUNK_SIZE * (SCALE / 4f);

        // Convert world position to heightmap space
        float fx = (pos.x - chunkOriginX) / (SCALE / 4f);
        float fz = (pos.y - chunkOriginZ) / (SCALE / 4f);

        int x = Math.round(fx);
        int z = Math.round(fz);

        float dx = fx - x;
        float dz = fz - z;

        // Clamp to safe bounds (make sure x+1 and z+1 are valid)
        x = Math.max(0, Math.min(terrain.length - 2, x));
        z = Math.max(0, Math.min(terrain[0].length - 2, z));

        // Get 4 surrounding heights
        float h00 = terrain[x][z];
        float h10 = terrain[x + 1][z];
        float h01 = terrain[x][z + 1];
        float h11 = terrain[x + 1][z + 1];

        // Bilinear interpolation
        float h0 = h00 * (1 - dx) + h10 * dx;
        float h1 = h01 * (1 - dx) + h11 * dx;
        float interpolatedHeight = h0 * (1 - dz) + h1 * dz;

        return interpolatedHeight * 50f; // Scale to world height
    }

    private ChunkCoord getPrevChunk(Vector2f first) {
        Vector2f prevPoint = generator.getPrevPoint(first);

        int prevChunkX = (int) Math.floor(prevPoint.x / (CHUNK_SIZE * (SCALE / 4)));
        int prevChunkZ = (int) Math.floor(prevPoint.y / (CHUNK_SIZE * (SCALE / 4)));

        return new ChunkCoord(prevChunkX, prevChunkZ);
    }
}
