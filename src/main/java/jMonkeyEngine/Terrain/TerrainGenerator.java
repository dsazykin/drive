package jMonkeyEngine.Terrain;

import com.jme3.app.SimpleApplication;
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
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Road.RoadGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class TerrainGenerator{

    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final AssetManager assetManager;
    private final HeightMapGenerator heightMap;
    private ChunkManager manager;
    private final RoadGenerator road;
    private final SimpleApplication main;
    private final ExecutorService executor;

    private final int CHUNK_SIZE;
    private final int PARENT_SIZE;
    private final float SCALE;
    private final Long SEED;
    private final int MAX_HEIGHT;

    private List<Future<?>> chunkTasks;

    public TerrainGenerator(BulletAppState bulletAppState,
                            Node rootNode, AssetManager assetManager, RoadGenerator road, SimpleApplication main,
                            ExecutorService executor, int chunkSize, int parentSize, float SCALE, Long seed,
                            int maxHeight) {
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        this.road = road;
        this.main = main;
        this.executor = executor;
        this.CHUNK_SIZE = chunkSize;
        this.PARENT_SIZE = parentSize;
        this.SCALE = SCALE;
        this.SEED = seed;
        MAX_HEIGHT = maxHeight;
        this.heightMap = new HeightMapGenerator(SEED, PARENT_SIZE, SCALE);
    }

    public void setChunkManager(ChunkManager manager) {
        this.manager = manager;
    }

    public float[][] generateHeightMap(ChunkCoord chunk) throws IOException {
        return heightMap.generateHeightmap(chunk.x, chunk.z);
    }

    public void updateHeightMap(float[][] terrain, List<jMonkeyEngine.Road.Node> pathPoints) {
        heightMap.applyRoadFlattening(terrain, pathPoints);
    }

    public Mesh generateChunkMesh(float[][] terrain, int cx, int cz){
        Mesh mesh = new Mesh();

        Vector3f[] vertices = new Vector3f[CHUNK_SIZE * CHUNK_SIZE];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int vertexIndex = 0;
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                float height = terrain[cx + x][cz + z];

                ColorRGBA color;
                if (height < 0.1f) {
                    color = new ColorRGBA(0f, 0f, 1f, 1f); // Blue (water)
                } else if (height < 0.2f) {
                    color = new ColorRGBA(211f / 255f, 169f / 255f, 108f / 255f, 1f); // Beach (sand yellow)
                } else if (height < 0.3f) {
                    color = new ColorRGBA(34f / 255f, 175f / 255f, 34f / 255f, 1f); // Light grass
                } else if (height < 0.4f) {
                    color = new ColorRGBA(34f / 255f, 125f / 255f, 34f / 255f, 1f); // Mid grass
                } else if (height < 0.5f) {
                    color = new ColorRGBA(34f / 255f, 100f / 255f, 25f / 255f, 1f); // Darker grass
                } else if (height < 0.6f) {
                    color = new ColorRGBA(75f / 255f, 80f / 255f, 30f / 255f, 1f); // Desaturated grass
                } else if (height < 0.7f) {
                    color = new ColorRGBA(90f / 255f, 75f / 255f, 20f / 255f, 1f); // Grass-dirt blend
                } else if (height < 0.8f) {
                    color = new ColorRGBA(110f / 255f, 70f / 255f, 20f / 255f, 1f); // Dirtier terrain
                } else if (height < 0.9f) {
                    color = new ColorRGBA(139f / 255f, 69f / 255f, 19f / 255f, 1f); // Mountain (brown)
                } else {
                    color = new ColorRGBA(1f, 1f, 1f, 1f); // Snow (white)
                }

                if (height > 1) {
                    height = height - (float)Math.floor(height);
                    color = new ColorRGBA(120f / 255f, 120f / 255f, 120f / 255f, 1f);
                }

                colors[vertexIndex] = color;

                vertices[vertexIndex++] = new Vector3f(
                        (x * (SCALE / 16)),
                        height * MAX_HEIGHT,
                        (z * (SCALE / 16))
                );

            }
        }

        int[] indices = new int[(CHUNK_SIZE - 1) * (CHUNK_SIZE - 1) * 6];
        int indexCount = 0;
        for (int z = 0; z < CHUNK_SIZE - 1; z++) {
            for (int x = 0; x < CHUNK_SIZE - 1; x++) {
                int topLeft = z * CHUNK_SIZE + x;
                int topRight = topLeft + 1;
                int bottomLeft = topLeft + CHUNK_SIZE;
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

        Vector2f[] uvs = new Vector2f[CHUNK_SIZE * CHUNK_SIZE];
        int index = 0;
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                uvs[index++] = new Vector2f((float)x / (CHUNK_SIZE - 1), (float)z / (CHUNK_SIZE - 1));
            }
        }

        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs));

        Vector3f[] normals = computeNormals(vertices, indices);
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals));

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.updateBound();

        return mesh;
    }

    private Vector3f[] computeNormals(Vector3f[] vertices, int[] indices) {
        Vector3f[] normals = new Vector3f[vertices.length];
        for (int i = 0; i < normals.length; i++) {
            normals[i] = new Vector3f(0, 0, 0);
        }

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            Vector3f v0 = vertices[i0];
            Vector3f v1 = vertices[i1];
            Vector3f v2 = vertices[i2];

            Vector3f edge1 = v1.subtract(v0);
            Vector3f edge2 = v2.subtract(v0);
            Vector3f normal = edge1.cross(edge2).normalizeLocal();

            normals[i0].addLocal(normal);
            normals[i1].addLocal(normal);
            normals[i2].addLocal(normal);
        }

        for (Vector3f n : normals) {
            n.normalizeLocal();
        }

        return normals;
    }

    public Geometry createGeometry(ChunkCoord chunk, Mesh mesh) {
        Geometry chunkGeom = new Geometry("Chunk_" + chunk.x + "_" + chunk.z, mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseVertexColor", true);

        chunkGeom.setMaterial(mat);

        chunkGeom.setLocalTranslation(
                chunk.x * (CHUNK_SIZE - 1f) * (SCALE / 16),
                0,
                chunk.z * (CHUNK_SIZE - 1f) * (SCALE / 16)
        );

        MeshCollisionShape terrainShape = new MeshCollisionShape(mesh);
        RigidBodyControl chunkPhysics = new RigidBodyControl(terrainShape, 0);
        chunkGeom.addControl(chunkPhysics);

        mesh.updateBound();
        mesh.updateCounts();
        //TangentBinormalGenerator.generate(mesh);

        return chunkGeom;
    }

    public void CreateTerrain() {
        chunkTasks = new ArrayList<>();

        final ChunkCoord chunk = new ChunkCoord(0, 0);

            try {
                float[][] terrain = generateHeightMap(chunk);
                List<jMonkeyEngine.Road.Node> pathPoints =
                        road.getRoadPointsInChunk(terrain, 0, PARENT_SIZE / 2, PARENT_SIZE - 1,
                                                  PARENT_SIZE / 2);
                updateHeightMap(terrain, pathPoints);

                ConcurrentHashMap<ChunkCoord, Geometry> children = new ConcurrentHashMap<>();
                ChunkCoord childCoord;

                for (int x = 0; x < PARENT_SIZE / CHUNK_SIZE; x++) {
                    for (int z = 0; z < PARENT_SIZE / CHUNK_SIZE; z++) {
                        childCoord = new ChunkCoord(x, z);
                        children.put(childCoord, manager.getChild(terrain, chunk, childCoord));
                    }
                }

                manager.addChunk(chunk, children, terrain);

                main.enqueue(() -> {
                    Geometry chunkGeom;

                    for (ChunkCoord child : children.keySet()) {
                        chunkGeom = children.get(child);
                        rootNode.attachChild(chunkGeom);
                        bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));
                    }

                    return null;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }

    }

    public List<Future<?>> getChunkTasks() {
        return chunkTasks;
    }

    public float getSpawnHeight() {
        float[][] spawnChunk = null;
        try {
            spawnChunk =
                    generateHeightMap(new ChunkCoord(0, 0));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return spawnChunk[0][CHUNK_SIZE / 2] * MAX_HEIGHT;
    }
}