package jMonkeyEngine.Terrain;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import jMonkeyEngine.Main;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TerrainGenerator{

    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final AssetManager assetManager;
    private final HeightMapGenerator heightMap;
    private ChunkManager manager;
    private final Main main;
    private final ExecutorService executor;

    private final int chunkSize;
    private final float scale;
    private final int renderDistance; // Grid size will be (2 * renderDistance - 1)^2
    private final Long seed;

    private List<Future<?>> chunkTasks;

    public TerrainGenerator(BulletAppState bulletAppState,
                            Node rootNode, AssetManager assetManager, Main main, ExecutorService executor, int chunkSize, float scale,
                            int renderDistance, Long seed) {
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        this.main = main;
        this.executor = executor;
        this.chunkSize = chunkSize;
        this.scale = scale;
        this.renderDistance = renderDistance;
        this.seed = seed;

        this.heightMap = new HeightMapGenerator();
    }

    public void setChunkManager(ChunkManager manager) {
        this.manager = manager;
    }

    private float[][] generateHeightMap(int size, double scale, int chunkX, int chunkZ) throws IOException {
        return heightMap.generateHeightmap(size, size, 1234L, scale, chunkX, chunkZ);
    }

    protected Mesh generateChunkMesh(int chunkX, int chunkZ, int size, double scale)
            throws IOException {
        float[][] terrain = generateHeightMap(size, scale, chunkX, chunkZ);

        Mesh mesh = new Mesh();

        Vector3f[] vertices = new Vector3f[size * size];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int vertexIndex = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float height = terrain[x][z];

                ColorRGBA color;
                if (height < 0.05f) {
                    color = new ColorRGBA(0f, 0f, 1f, 1f); // Blue (water)
                } else if (height < 0.8f) {
                    color = new ColorRGBA(0f, 1f, 0f, 1f); // Green (grass)
                } else if (height < 0.9f) {
                    color = new ColorRGBA(0.5f, 0.35f, 0.05f, 1f); // Brown (dirt)
                } else {
                    color = new ColorRGBA(1f, 1f, 1f, 1f); // White (snow)
                }
                colors[vertexIndex] = color;

                vertices[vertexIndex++] = new Vector3f(
                        (float) (x * (scale / 4)),
                        height * 50,
                        (float) (z * (scale / 4))
                );

            }
        }

        int[] indices = new int[(size - 1) * (size - 1) * 6];
        int indexCount = 0;
        for (int z = 0; z < size - 1; z++) {
            for (int x = 0; x < size - 1; x++) {
                int topLeft = z * size + x;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + size;
                int bottomRight = bottomLeft + 1;

                // First triangle
                indices[indexCount++] = topLeft;
                indices[indexCount++] = bottomLeft;
                indices[indexCount++] = topRight;

                // Second triangle
                indices[indexCount++] = topRight;
                indices[indexCount++] = bottomLeft;
                indices[indexCount++] = bottomRight;
            }
        }

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.updateBound();
        return mesh;
    }

    protected Geometry createGeometry(int finalChunkX, int finalChunkZ, Mesh mesh) {
        Geometry chunkGeom = new Geometry("Chunk_" + finalChunkX + "_" + finalChunkZ, mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        chunkGeom.setMaterial(mat);

        chunkGeom.setLocalTranslation(
                finalChunkX * (chunkSize - 1) * (scale / 4),
                0,
                finalChunkZ * (chunkSize - 1) * (scale / 4)
        );

        MeshCollisionShape terrainShape = new MeshCollisionShape(mesh);
        RigidBodyControl chunkPhysics = new RigidBodyControl(terrainShape, 0);
        chunkGeom.addControl(chunkPhysics);

        return chunkGeom;
    }

//    public void simpleInitApp() {
//        cam.setLocation(new Vector3f(32, 500, 32));
//        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
//    }

    public void CreateTerrain() {
        chunkTasks = new ArrayList<>();

        for (int chunkZ = -renderDistance; chunkZ <= renderDistance; chunkZ++) {
            for (int chunkX = -renderDistance; chunkX <= renderDistance; chunkX++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                Future<?> future = executor.submit(() -> {
                    try {
                        Mesh mesh = generateChunkMesh(finalChunkX, finalChunkZ, chunkSize, scale);
                        Geometry chunkGeom = createGeometry(finalChunkX, finalChunkZ, mesh);

                        main.enqueue(() -> {
                            manager.addChunk(finalChunkX, finalChunkZ, chunkGeom);
                            rootNode.attachChild(chunkGeom);
                            bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));
                            return null;
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                chunkTasks.add(future);
            }
        }
    }

    public List<Future<?>> getChunkTasks() {
        return chunkTasks;
    }
}
