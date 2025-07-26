package jMonkeyEngine.Road;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
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

    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final AssetManager assetManager;

    private ConcurrentHashMap<ChunkCoord, RoadEndpoint> exitPointMap = new ConcurrentHashMap<>();
    private List<DeferredConnection> unsyncedList = new ArrayList<>();
    private List<DeferredConnection> deferredJoins = Collections.synchronizedList(unsyncedList);

    public RoadConstuctor(int chunkSize, float scale, float roadWidth, Node rootNode,
                          BulletAppState bulletAppState, AssetManager assetManager) {
        CHUNK_SIZE = chunkSize;
        SCALE = scale;
        ROAD_WIDTH = roadWidth;
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.assetManager = assetManager;
    }

    protected Geometry buildRoad(List<Vector2f> path, float[][] terrain, ChunkCoord chunk,
                         Vector3f chunkEntryLeft, Vector3f chunkEntryRight) {

        boolean notFirst = (chunkEntryLeft != null || chunkEntryRight != null);
        System.out.println(notFirst);

        int extraVerts = notFirst ? 2 : 0;
        int vertexCount = path.size() * 2 + extraVerts;
        Vector3f[] vertices = new Vector3f[vertexCount];
        ColorRGBA[] colors = new ColorRGBA[vertexCount];

        if (notFirst) {
            vertices[0] = chunkEntryLeft;
            vertices[1] = chunkEntryRight;
            colors[0] = colors[1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
        }

        for (int i = 0; i < path.size(); i++) {
            Vector2f center = path.get(i);

            Vector2f dir;
            if (i < path.size() - 1) {
                dir = path.get(i + 1).subtract(center).normalize();
            } else if (i > 0) {
                dir = center.subtract(path.get(i - 1)).normalize();
            } else {
                dir = new Vector2f(1, 0); // fallback if path has only one point
            }

            Vector2f left2D = new Vector2f(-dir.y, dir.x).mult(ROAD_WIDTH / 2f);
            Vector2f leftPt = center.add(left2D);
            Vector2f rightPt = center.subtract(left2D);

            float centerHeight =
                    sampleHeight(center, terrain, chunk.x, chunk.z);
            float heightOffset = 0.05f;

            Vector3f leftVector = new Vector3f(leftPt.x, centerHeight + heightOffset, leftPt.y);
            Vector3f rightVector =
                    new Vector3f(rightPt.x, centerHeight + heightOffset, rightPt.y);

            int vi = i * 2 + extraVerts;
            vertices[vi] = leftVector;
            vertices[vi + 1] = rightVector;
            colors[vi] = colors[vi + 1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);

            // Save for next chunk to continue from here
            if (i == path.size() - 1) {
                exitPointMap.put(chunk,
                                 new RoadEndpoint(leftVector, rightVector));
            }
        }

        int segmentCount = path.size() - 1 + (notFirst ? 1 : 0);
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
        ChunkCoord prevChunk = getPrevChunk(path.get(0), path.get(1));
        System.out.println("suspected previous chunk: " + prevChunk);

        List<Geometry> roads = new ArrayList<>();

        if (exitPointMap.containsKey(prevChunk) || (thisChunk.x == 0 && thisChunk.z == 0) ) {
            // Build road with continuation
            Geometry road;
            if (!(thisChunk.x == 0 && thisChunk.z == 0)) {
                RoadEndpoint prev = exitPointMap.get(prevChunk);
                road = buildRoad(path, terrain, thisChunk, prev.left, prev.right);
            } else {
                road = buildRoad(path, terrain, thisChunk, null, null);
            }
            roads.add(road);
            System.out.println("built chunk: (" + thisChunk.x + ", " + thisChunk.z + ")");

            List<Geometry> dependentRoads = onRoadBuilt(thisChunk);
            System.out.println("built dependent roads for chunk: (" + thisChunk.x + ", " + thisChunk.z + ")");

            roads.addAll(dependentRoads);
            return roads;
//            rootNode.attachChild(road);
//            bulletAppState.getPhysicsSpace()
//                    .add(road.getControl(RigidBodyControl.class));
        } else {
            // Defer connection until prevChunk is available
            DeferredConnection deferred = new DeferredConnection(thisChunk, prevChunk, path, terrain);
            deferredJoins.add(deferred);
            return null;
        }
    }

    protected List<Geometry> onRoadBuilt(ChunkCoord thisChunk) {
        Iterator<DeferredConnection> it = deferredJoins.iterator();
        List<DeferredConnection> toRemove = new ArrayList<>();
        List<Geometry> roads = new ArrayList<>();

        while (it.hasNext()) {
            DeferredConnection dc = it.next();
            if (dc.prevChunk.equals(thisChunk)) {
                RoadEndpoint prev = exitPointMap.get(thisChunk);
                Geometry road = buildRoad(dc.path, dc.terrain, dc.thisChunk, prev.left, prev.right);
                roads.add(road);
                roads.addAll(onRoadBuilt(dc.thisChunk)); // recurse safely
                toRemove.add(dc); // defer removal
            }
        }

        deferredJoins.removeAll(toRemove); // modify list outside iteration
        return roads;
    }

    private float sampleHeight(Vector2f pos, float[][] terrain, int chunkX, int chunkZ) {
        float chunkOriginX = chunkX * CHUNK_SIZE * (SCALE / 4);
        float chunkOriginZ = chunkZ * CHUNK_SIZE * (SCALE / 4);

        // Convert world coordinates to local heightmap indices
        int localX = Math.round((pos.x - chunkOriginX) / (SCALE / 4));
        int localZ = Math.round((pos.y - chunkOriginZ) / (SCALE / 4));

        // Clamp to terrain bounds
        localX = Math.max(0, Math.min(terrain.length - 1, localX));
        localZ = Math.max(0, Math.min(terrain[0].length - 1, localZ));

        float rawHeight = terrain[localX][localZ];

        return rawHeight * 50f;
    }

    private ChunkCoord getPrevChunk(Vector2f first, Vector2f second) {
        Vector2f direction = first.subtract(second).normalize();

        float approxStepSize = first.distance(second);
        Vector2f previousPos = first.add(direction.mult(approxStepSize));

        int prevChunkX = (int) Math.floor(previousPos.x / (CHUNK_SIZE * (SCALE / 4)));
        int prevChunkZ = (int) Math.floor(previousPos.y / (CHUNK_SIZE * (SCALE / 4)));

        return new ChunkCoord(prevChunkX, prevChunkZ);
    }
}
