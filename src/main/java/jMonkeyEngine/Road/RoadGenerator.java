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
    private float segmentLength = 10f;
    private float currentAngle = 0f; // in degrees
    private Vector2f currentPosition = new Vector2f(0, 0);

    private Random rand = new Random();
    private float maxTurnAngle = 15f;
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

    private List<Vector2f> generateCurvedPath(Vector2f start, int segments, float segmentLength, float maxTurnRateDeg) {
        List<Vector2f> path = new ArrayList<>();
        path.add(start);

        float angle = 0f; // Initial forward direction
        Random rand = new Random();

        float turnVelocity = 0f;

        for (int i = 1; i < segments; i++) {
            // Gradual change to turning rate (simulate inertia)
            float targetTurn = (rand.nextFloat() * 2 - 1) * maxTurnRateDeg;
            turnVelocity += (targetTurn - turnVelocity) * 0.05f;  // smooth it
            angle += FastMath.DEG_TO_RAD * turnVelocity;

            float dx = FastMath.cos(angle) * segmentLength;
            float dz = FastMath.sin(angle) * segmentLength;

            Vector2f prev = path.get(i - 1);
            path.add(prev.add(new Vector2f(dx, dz)));
        }

        return path;
    }

    public void generateInitialPath() {
        while (furthestPoint().distance(new Vector2f(0, 0)) < 500 * 2.5f) {
            extendPath();
        }
    }

    public void extendPath() {
        Vector2f last = pathPoints.get(pathPoints.size() - 1);
        Vector2f dir;

        // If path has more than 1 point, compute direction
        if (pathPoints.size() >= 2) {
            dir = last.subtract(pathPoints.get(pathPoints.size() - 2)).normalize();
        } else {
            dir = new Vector2f(0, 1); // default forward (Z+) if only one point
        }

        // Slight random angle in radians
        float angleDeg = -maxTurnAngle + rand.nextFloat() * (2 * maxTurnAngle);
        float angleRad = FastMath.DEG_TO_RAD * angleDeg;

        // Rotate direction
        float cos = FastMath.cos(angleRad);
        float sin = FastMath.sin(angleRad);
        Vector2f rotated = new Vector2f(
                dir.x * cos - dir.y * sin,
                dir.x * sin + dir.y * cos
        ).normalize();

        // Compute new point
        Vector2f next = last.add(rotated.mult(segmentLength));
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

    public Geometry buildRoad(List<Vector2f> path, float width, float[][] terrain) {
        Vector3f[] vertices = new Vector3f[path.size() * 2];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int[] indices = new int[(path.size() - 1) * 6];

        for (int i = 0; i < path.size(); i++) {
            Vector2f center = path.get(i);

            // Direction vector (2D)
            Vector2f dir;
            if (i < path.size() - 1)
                dir = path.get(i + 1).subtract(center).normalize();
            else
                dir = center.subtract(path.get(i - 1)).normalize();

            // Perpendicular (left) vector in 2D
            Vector2f left2D = new Vector2f(-dir.y, dir.x).mult(width / 2f);

            // Left and right points in 2D
            Vector2f leftPt = center.add(left2D);
            Vector2f rightPt = center.subtract(left2D);

            // Sample terrain height
            float leftHeight = sampleHeight(leftPt, terrain);
            float rightHeight = sampleHeight(rightPt, terrain);

            // Build 3D vertices (x, y=height, z)
            vertices[i * 2] = new Vector3f(leftPt.x, leftHeight + 0.05f, leftPt.y);
            vertices[i * 2 + 1] = new Vector3f(rightPt.x, rightHeight + 0.05f, rightPt.y);

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

        System.out.println("created road at: ");

        return road;
    }

    private float sampleHeight(Vector2f pos, float[][] terrain) {
        int x = Math.round(pos.x);
        int z = Math.round(pos.y);

        // Clamp to terrain bounds
        x = Math.max(0, Math.min(terrain.length - 1, x));
        z = Math.max(0, Math.min(terrain[0].length - 1, z));

        return terrain[x][z] * 50f; // scale height if needed
    }

    public Vector2f furthestPoint() {
        if (pathPoints.isEmpty()) return new Vector2f(0, 0); // Or null, or throw an exception
        return pathPoints.get(pathPoints.size() - 1);
    }
}
