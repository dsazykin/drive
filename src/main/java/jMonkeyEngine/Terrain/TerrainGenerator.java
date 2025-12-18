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
import jMonkeyEngine.Road.RoadMeshGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import jMonkeyEngine.Terrain.TerrainSerializer.TerrainData;

public class TerrainGenerator{

    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final AssetManager assetManager;
    private final HeightMapGenerator heightMap;
    private ChunkManager manager;
    private final RoadGenerator road;
    private final RoadMeshGenerator roadMeshGenerator;
    private final SimpleApplication main;
    private final ExecutorService executor;

    private final int CHUNK_SIZE;
    private final float SCALE;
    private final Long SEED;
    private final int MAX_HEIGHT;
    private final float ROAD_WIDTH;

    private List<Future<?>> chunkTasks;

    // Saved data for reuse
    private ConcurrentHashMap<ChunkCoord, Geometry> generatedGeometries = new ConcurrentHashMap<>();
    private float[][] lastGeneratedTerrain;
    private List<jMonkeyEngine.Road.Node> lastPathPoints;

    public TerrainGenerator(BulletAppState bulletAppState,
                            Node rootNode, AssetManager assetManager, RoadGenerator road, SimpleApplication main,
                            ExecutorService executor, int chunkSize, float SCALE, Long seed,
                            int maxHeight, float roadWidth) {
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        this.road = road;
        this.main = main;
        this.executor = executor;
        this.CHUNK_SIZE = chunkSize;
        this.SCALE = SCALE;
        this.SEED = seed;
        MAX_HEIGHT = maxHeight;
        this.heightMap = new HeightMapGenerator(SEED, CHUNK_SIZE, SCALE, roadWidth);
        ROAD_WIDTH = roadWidth;
        this.roadMeshGenerator = new RoadMeshGenerator(assetManager, SCALE, CHUNK_SIZE, MAX_HEIGHT, ROAD_WIDTH);
    }

    public void setChunkManager(ChunkManager manager) {
        this.manager = manager;
    }

    public float[][] generateHeightMap(ChunkCoord chunk) {
        return heightMap.generateHeightmap(chunk.x, chunk.z);
    }

    public void updateHeightMap(float[][] terrain, List<jMonkeyEngine.Road.Node> pathPoints) {
        heightMap.applyRoadFlattening(terrain, pathPoints);
    }

    public Geometry generateRoadGeometry(List<jMonkeyEngine.Road.Node> pathPoints,
                                         ChunkCoord chunk, float[][] heightmap) {
        return roadMeshGenerator.generateRoadGeometry(pathPoints, chunk, heightmap);
    }

    public Mesh generateChunkMesh(float[][] terrain){
        Mesh mesh = new Mesh();

        Vector3f[] vertices = new Vector3f[CHUNK_SIZE * CHUNK_SIZE];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int vertexIndex = 0;
        for (int z = 0; z < CHUNK_SIZE; z++) {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                float height = terrain[x][z];

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

    public float[][] getLastGeneratedTerrain() {
        return lastGeneratedTerrain;
    }
    public List<jMonkeyEngine.Road.Node> getLastPathPoints() {
        return lastPathPoints;
    }

    /**
     * Save current generated terrain to disk.
     */
    public boolean saveGeneratedTerrain(String name) {
        if (lastGeneratedTerrain == null) {
            System.err.println("No terrain to save!");
            return false;
        }

        return TerrainSerializer.saveTerrain(
            name,
            lastGeneratedTerrain,
            generatedGeometries,
            lastPathPoints,
            CHUNK_SIZE,
            SCALE,
            SEED
        );
    }

    /**
     * Load and apply saved terrain.
     */
    public boolean loadSavedTerrain(String name) {
        TerrainData data = TerrainSerializer.loadTerrain(name);

        if (data == null) {
            return false;
        }

        // Verify compatibility
        if (data.chunkSize != CHUNK_SIZE) {
            System.err.println("Saved terrain size mismatch!");
            return false;
        }

        // Apply loaded data
        lastGeneratedTerrain = data.heightMap;
        lastPathPoints = data.pathPoints;

        // Regenerate geometries from heightmap
        ChunkCoord rootChunk = new ChunkCoord(0, 0);
        Geometry chunkGeom = manager.getChunk(rootChunk);

        generatedGeometries.clear();
        generatedGeometries.put(rootChunk, chunkGeom);

        manager.addChunk(rootChunk, chunkGeom, data.heightMap, data.pathPoints);

        main.enqueue(() -> {

                rootNode.attachChild(chunkGeom);
                bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

            return null;
        });

        System.out.println("Loaded terrain successfully");
        return true;
    }

    public void CreateTerrain() {
        chunkTasks = new ArrayList<>();

        final ChunkCoord chunk = new ChunkCoord(0, 0);
        try {
            float[][] terrain = generateHeightMap(chunk);
            HashMap<ChunkCoord, List<jMonkeyEngine.Road.Node>>
                    roadPointsInChunk = road.getRoadPointsInChunk(terrain, null, 0,
                                                                  CHUNK_SIZE / 2, 300, chunk);
            System.out.println(roadPointsInChunk.keySet());
            List<jMonkeyEngine.Road.Node> pathPoints = roadPointsInChunk.get(chunk);
            if (pathPoints == null) {
                pathPoints = new ArrayList<>();
            }
            updateHeightMap(terrain, pathPoints);

            Mesh mesh = generateChunkMesh(terrain);
            Geometry chunkGeom = createGeometry(chunk, mesh);

            // Save for later reuse
            generatedGeometries.clear();
            generatedGeometries.put(chunk, chunkGeom);
            lastGeneratedTerrain = terrain;
            lastPathPoints = pathPoints;

            manager.addChunk(chunk, chunkGeom, terrain, pathPoints);
            main.enqueue(() -> {

                rootNode.attachChild(chunkGeom);
                bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

                return null;
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public List<Future<?>> getChunkTasks() {
        return chunkTasks;
    }
}