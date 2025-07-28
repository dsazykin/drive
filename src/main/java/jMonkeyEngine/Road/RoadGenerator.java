package jMonkeyEngine.Road;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.font.BitmapText;
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
import jMonkeyEngine.Chunks.ChunkCoord;
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoadGenerator extends SimpleApplication {
    BulletAppState bulletAppState;
    ExecutorService executor;

    TerrainGenerator generator;
    ChunkManager manager;
    RoadConstuctor constuctor;

    private BitmapText chunkX;
    private BitmapText chunkZ;

    private List<Vector2f> pathPoints = Collections.synchronizedList(new ArrayList<>());
    private final float segmentLength = 10f;
    private float currentAngle = 0f;
    private float turnVelocity = 0f;// in degrees
    private Vector2f currentPosition = new Vector2f(0, 0);

    private Random rand = new Random();
    private final float maxTurnAngle = 15f;

    private final int CHUNK_SIZE = 100;
    private final float SCALE = 20f;

    private final float ROAD_WIDTH = 10f;

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

        generator = new TerrainGenerator(bulletAppState, rootNode, assetManager, this, this, executor, CHUNK_SIZE, SCALE, 1, 987654567L);
        this.manager =
                new ChunkManager(rootNode, bulletAppState, generator, this, this, executor, CHUNK_SIZE,
                                 SCALE, 1);
        generator.setChunkManager(manager);
        this.constuctor = new RoadConstuctor(CHUNK_SIZE, SCALE, ROAD_WIDTH, this, assetManager);

        setUpLight();
        generator.CreateTerrain();

        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(300);

        cam.setLocation(new Vector3f(32, 500, 32));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

        guiFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        chunkX = new BitmapText(guiFont, false);
        chunkX.setSize(guiFont.getCharSet().getRenderedSize());
        chunkX.setLocalTranslation(10, cam.getHeight() - 10, 0);
        guiNode.attachChild(chunkX);

        chunkZ = new BitmapText(guiFont, false);
        chunkZ.setSize(guiFont.getCharSet().getRenderedSize());
        chunkZ.setLocalTranslation(10, cam.getHeight() - 50, 0);
        guiNode.attachChild(chunkZ);
    }

    @Override
    public void simpleUpdate(float tpf) {
        manager.updateChunks(cam.getLocation());

        chunkX.setText(String.format("X Coord: %.1f", Math.floor(cam.getLocation().x / ((CHUNK_SIZE - 1) * (SCALE / 4)))));
        chunkZ.setText(String.format("Z Coord: %.1f", Math.floor(cam.getLocation().z / ((CHUNK_SIZE - 1) * (SCALE / 4)))));
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

            // dark gray color for road
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

            boolean rightAngle = false;

            if (pathPoints.size() >= 2) {
                Vector2f secondLast = pathPoints.get(pathPoints.size() - 2);
                Vector2f lastDir = last.subtract(secondLast).normalize();
                Vector2f newDir = next.subtract(last).normalize();

                float dot = lastDir.dot(newDir);
                float angleBetween = FastMath.acos(dot); // in radians
                float maxTurnRadians = FastMath.DEG_TO_RAD * maxTurnAngle;

                if (angleBetween < maxTurnRadians) {
                    rightAngle = true;
                }
            } else {
                rightAngle = true;
            }

            if ((next.getY() > last.getY()) && (rightAngle)) {
                rightDirection = true;
            }
        }
        pathPoints.add(next);
    }

    public List<Vector2f> getPointsInChunk(ChunkCoord chunk, int chunkSize) {
        float startX = chunk.x * chunkSize;
        float endX = startX + chunkSize;
        float startZ = chunk.z * chunkSize;
        float endZ = startZ + chunkSize;

        List<Vector2f> segment = new ArrayList<>();

        List<Vector2f> snapshot;
        synchronized (pathPoints) {
            snapshot = new ArrayList<>(pathPoints);
        }
        for (Vector2f p : snapshot) {
            if (p.x >= startX && p.x < endX && p.y >= startZ && p.y < endZ) {
                segment.add(p);
            }
        }

        return segment;
    }

    public Vector2f furthestPoint() {
        if (pathPoints.isEmpty()) return new Vector2f(0, 0); // Or null, or throw an exception
        return pathPoints.get(pathPoints.size() - 1);
    }

    public List<Geometry> buildRoad(ChunkCoord chunk, float[][] terrain) {
        List<Geometry> roads;
        if (chunk.x == 0 && chunk.z == 0) {
            generateInitialPath();
        }

        // Only extend path if necessary
        Vector2f chunkCenter = getChunkCenter(chunk, CHUNK_SIZE * (SCALE / 4));
        while (furthestPoint().distance(chunkCenter) < (CHUNK_SIZE * (SCALE / 4)) * 1.5f) {
            extendPath();
        }

        // Now fetch road points
        List<Vector2f> roadPoints = getPointsInChunk(chunk, (int) (CHUNK_SIZE * (SCALE / 4)));

        if (roadPoints.size() >= 2) {
            //System.out.println("chunk: (" + chunk.x + ", " + chunk.z + ")");
//            System.out.println("first point in chunk: " + roadPoints.get(0));
//            System.out.println("last point in chunk: " + roadPoints.get(roadPoints.size() - 1));
            roads = constuctor.onChunkLoad(chunk, roadPoints, terrain);
        } else {
            roads = null;
        }
        return roads;
    }

    public Vector2f getChunkCenter(ChunkCoord chunk, float chunkSize) {
        float centerX = chunk.x * chunkSize + chunkSize / 2f;
        float centerZ = chunk.z * chunkSize + chunkSize / 2f;
        return new Vector2f(centerX, centerZ);
    }

    public Vector2f getPrevPoint(Vector2f point) {
        return pathPoints.get(pathPoints.indexOf(point) - 2);
    }
}
