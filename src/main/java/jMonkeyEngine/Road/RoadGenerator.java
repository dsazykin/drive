package jMonkeyEngine.Road;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import jMonkeyEngine.Terrain.ChunkManager;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoadGenerator extends SimpleApplication {
    BulletAppState bulletAppState;
    ExecutorService executor;

    TerrainGenerator generator;
    ChunkManager manager;

    private List<Vector2f> pathPoints = new ArrayList<>();
    private float segmentLength = 5f;
    private float currentAngle = 0f;
    private float turnVelocity = 0f;// in degrees
    private Vector2f currentPosition = new Vector2f(0, 0);

    private Random rand = new Random();
    private float maxTurnAngle = 10f;
    private float minTurnAngle = -15f;

    public static void main(String[] args) {
        RoadGenerator app = new RoadGenerator();
        app.start();
    }

    public RoadGenerator() {
        pathPoints.add(currentPosition.clone()); // starting point
    }

    @Override
    public void simpleInitApp() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setEnabled(true);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        bulletAppState.setDebugEnabled(true);

        generator = new TerrainGenerator(bulletAppState, rootNode, assetManager, this, this, executor, 50, 40, 3, 1234L);
        this.manager =
                new ChunkManager(rootNode, bulletAppState, generator, this, this, executor, 50,
                                 40, 3);
        generator.setChunkManager(manager);

        setUpLight();
        generator.CreateTerrain();

        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(300);

        cam.setLocation(new Vector3f(32, 500, 32));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    @Override
    public void simpleUpdate(float tpf) {
        manager.updateChunks(cam.getLocation());
    }

    private void setUpLight() {
        // We add light so we see the scene
        AmbientLight al = new AmbientLight();
        al.setColor(ColorRGBA.White.mult(1.3f));
        rootNode.addLight(al);

        DirectionalLight dl = new DirectionalLight();
        dl.setColor(ColorRGBA.White);
        dl.setDirection(new Vector3f(2.8f, -2.8f, -2.8f).normalizeLocal());
        rootNode.addLight(dl);
    }

    public Geometry generateStraightRoad(int length, float width, float scale, float[][] terrain, int zOffSet) {
        Vector3f[] vertices = new Vector3f[length * 2];
        int[] indices = new int[(length - 1) * 6];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];

        for (int i = 0; i < length; i++) {
            float height = (terrain[0][i] * 50f); // Sample noise-based terrain

            int zLocal = (int) (i * scale);

            // Two points per segment (left/right edges of the road)
            vertices[i * 2] = new Vector3f(-width / 2f, height + 0.05f, zLocal + zOffSet); // Left
            vertices[i * 2 + 1] = new Vector3f(width / 2f, height + 0.05f, zLocal + zOffSet); // Right

            // Optional: dark gray color for road
            colors[i * 2] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
            colors[i * 2 + 1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
        }

        // Build triangles
        int index = 0;
        for (int i = 0; i < length - 1; i++) {
            int v0 = i * 2;
            int v1 = v0 + 1;
            int v2 = v0 + 2;
            int v3 = v0 + 3;

            // Triangle 1
            indices[index++] = v0;
            indices[index++] = v2;
            indices[index++] = v1;

            // Triangle 2
            indices[index++] = v1;
            indices[index++] = v2;
            indices[index++] = v3;
        }

        Mesh roadMesh = new Mesh();
        roadMesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        roadMesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        roadMesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        roadMesh.updateBound();

        Geometry road = new Geometry("Road", roadMesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        road.setMaterial(mat);

        MeshCollisionShape roadShape = new MeshCollisionShape(roadMesh);
        RigidBodyControl roadPhysics = new RigidBodyControl(roadShape, 0);
        road.addControl(roadPhysics);

        return road;
    }

    public void generateInitialPath() {
        while (furthestPoint().distance(new Vector2f(0, 0)) < 500 * 2.5f) {
            extendPath();
        }
    }

    public void extendPath() {
        boolean rightDirection = false;
        Vector2f next = null;
        while (!rightDirection) {
            Vector2f last = pathPoints.get(pathPoints.size() - 1);

            // Random target turn in degrees, smoothed over time
            float targetTurnDeg = (rand.nextFloat() * 2 - 1) * maxTurnAngle;
            turnVelocity += (targetTurnDeg - turnVelocity) * 0.05f;  // Inertia-like smoothing

            // Update current direction angle
            currentAngle += FastMath.DEG_TO_RAD * turnVelocity;

            // Compute new offset
            float dx = FastMath.cos(currentAngle) * segmentLength;
            float dz = FastMath.sin(currentAngle) * segmentLength;

            next = last.add(new Vector2f(dx, dz));
            if (next.getY() > last.getY()) {
                rightDirection = true;
            }
        }
        pathPoints.add(next);
    }

    public List<Vector2f> getPointsInChunk(int chunkX, int chunkZ, int chunkSize) {
        float startX = chunkX * chunkSize;
        float endX = startX + chunkSize;
        float startZ = chunkZ * chunkSize;
        float endZ = startZ + chunkSize;

        List<Vector2f> segment = new ArrayList<>();
        for (Vector2f p : pathPoints) {
            if (p.x >= startX && p.x < endX && p.y >= startZ && p.y < endZ) {
                segment.add(p);
            }
        }
        return segment;
    }

    public Geometry buildRoad(List<Vector2f> path, float width, float[][] terrain, int chunkX, int chunkZ, int chunkSize, float scale) {
        Vector3f[] vertices = new Vector3f[path.size() * 2];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int[] indices = new int[(path.size() - 1) * 6];

        for (int i = 0; i < path.size(); i++) {
            Vector2f center = path.get(i);

            Vector2f dir;
            if (i < path.size() - 1)
                dir = path.get(i + 1).subtract(center).normalize();
            else
                dir = center.subtract(path.get(i - 1)).normalize();

            Vector2f left2D = new Vector2f(-dir.y, dir.x).mult(width / 2f);
            Vector2f leftPt = center.add(left2D);
            Vector2f rightPt = center.subtract(left2D);

            // Sample center height
            float centerHeight = sampleHeight(center, terrain, chunkX, chunkZ, chunkSize, scale);

            // Slight offset above the terrain
            float heightOffset = 0.05f;

            vertices[i * 2] = new Vector3f(leftPt.x, centerHeight + heightOffset, leftPt.y);
            vertices[i * 2 + 1] = new Vector3f(rightPt.x, centerHeight + heightOffset, rightPt.y);

            colors[i * 2] = colors[i * 2 + 1] = new ColorRGBA(0.2f, 0.2f, 0.2f, 1f);
        }

        int idx = 0;
        for (int i = 0; i < path.size() - 1; i++) {
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

    private float sampleHeight(Vector2f pos, float[][] terrain, int chunkX, int chunkZ, int chunkSize, float scale) {
        float chunkOriginX = chunkX * chunkSize * (scale / 4);
        float chunkOriginZ = chunkZ * chunkSize * (scale / 4);

        // Convert world coordinates to local heightmap indices
        int localX = Math.round((pos.x - chunkOriginX) / (scale / 4));
        int localZ = Math.round((pos.y - chunkOriginZ) / (scale / 4));

        // Clamp to terrain bounds
        localX = Math.max(0, Math.min(terrain.length - 1, localX));
        localZ = Math.max(0, Math.min(terrain[0].length - 1, localZ));

        float rawHeight = terrain[localX][localZ];

        return rawHeight * 50f;
    }

    public Vector2f furthestPoint() {
        if (pathPoints.isEmpty()) return new Vector2f(0, 0); // Or null, or throw an exception
        return pathPoints.get(pathPoints.size() - 1);
    }
}
