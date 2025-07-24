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
import jMonkeyEngine.Chunks.ChunkManager;
import jMonkeyEngine.Road.RoadGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private final int chunkSize;
    private final float scale;
    private final int renderDistance; // Grid size will be (2 * renderDistance - 1)^2
    private final Long seed;

    private List<Future<?>> chunkTasks;

    public TerrainGenerator(BulletAppState bulletAppState,
                            Node rootNode, AssetManager assetManager, RoadGenerator road, SimpleApplication main,
                            ExecutorService executor, int chunkSize, float scale,
                            int renderDistance, Long seed) {
        this.bulletAppState = bulletAppState;
        this.rootNode = rootNode;
        this.assetManager = assetManager;
        this.road = road;
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

    public float[][] generateHeightMap(int size, double scale, int chunkX, int chunkZ) throws IOException {
        return heightMap.generateHeightmap(size, size, seed, scale, chunkX, chunkZ);
    }

    public Mesh generateChunkMesh(float[][] terrain, int size, double scale)
            throws IOException {
        Mesh mesh = new Mesh();

        Vector3f[] vertices = new Vector3f[size * size];
        ColorRGBA[] colors = new ColorRGBA[vertices.length];
        int vertexIndex = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float height = terrain[x][z];

                ColorRGBA color;
                if (height < 0.05f) {
                    color = new ColorRGBA(0f, 0.1f, 1f, 1f); // Blue (water)
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

    public Geometry createGeometry(int chunkX, int chunkZ, Mesh mesh) {
        Geometry chunkGeom = new Geometry("Chunk_" + chunkX + "_" + chunkZ, mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        chunkGeom.setMaterial(mat);

        chunkGeom.setLocalTranslation(
                chunkX * (chunkSize - 0.9f) * (scale / 4),
                0,
                chunkZ * (chunkSize - 0.9f) * (scale / 4)
        );

        MeshCollisionShape terrainShape = new MeshCollisionShape(mesh);
        RigidBodyControl chunkPhysics = new RigidBodyControl(terrainShape, 0);
        chunkGeom.addControl(chunkPhysics);

        return chunkGeom;
    }

    public void CreateTerrain() {
        chunkTasks = new ArrayList<>();

        for (int chunkZ = -renderDistance; chunkZ <= renderDistance; chunkZ++) {
            for (int chunkX = -renderDistance; chunkX <= renderDistance; chunkX++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                Future<?> future = executor.submit(() -> {
                    try {
                        float[][] terrain = generateHeightMap(chunkSize, scale, finalChunkX, finalChunkZ);
                        Mesh mesh = generateChunkMesh(terrain, chunkSize, scale);
                        Geometry chunkGeom = createGeometry(finalChunkX, finalChunkZ, mesh);


                        int zOffSet = (int) (finalChunkZ * ((chunkSize - 1) * (scale / 4)));
                        int xOffSet = (int) (finalChunkX * ((chunkSize - 1) * (scale / 4)));
//                        if (finalChunkX == 0) {
//                            r = road.generateStraightRoad(50, 10f, 10f, terrain, zOffSet);
//                            System.out.println("generated road");
//                        } else {
//                            r = null;
//                        }

                        main.enqueue(() -> {
                            manager.addChunk(finalChunkX, finalChunkZ, chunkGeom);
                            rootNode.attachChild(chunkGeom);
                            bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

//                            if (finalChunkX == 0) {
//                                rootNode.attachChild(r);
//                                bulletAppState.getPhysicsSpace()
//                                        .add(r.getControl(RigidBodyControl.class));
//                                System.out.println("Attaching road at Z = " + (finalChunkZ * chunkSize * (scale / 4f)));
//                            }

                            Geometry r;
                            if (finalChunkX == 0 && finalChunkZ == 0) {
                                road.generateInitialPath();
                            }

                            // Only extend path if necessary
                            Vector2f chunkCenter = getChunkCenter(finalChunkX, finalChunkZ, chunkSize * (scale / 4));
                            while (road.furthestPoint().distance(chunkCenter) < 500 * 1.5f) {
                                road.extendPath();
                            }

                            // Now fetch road points
                            List<Vector2f> roadPoints = road.getPointsInChunk(finalChunkX, finalChunkZ, (int) (chunkSize * (scale / 4)));

                            if (roadPoints.size() >= 2) {
                                System.out.println("chunk: (" + finalChunkX + ", " + finalChunkZ + ")");
                                System.out.println("first point in chunk: " + roadPoints.get(0));
                                System.out.println("last point in chunk: " + roadPoints.get(roadPoints.size() - 1));
                                r = road.buildRoad(roadPoints, 10f, terrain, finalChunkX, finalChunkZ, chunkSize, scale);
                            } else {
                                r = null;
                            }

                            if (r != null) {
                                rootNode.attachChild(r);
                                bulletAppState.getPhysicsSpace()
                                        .add(r.getControl(RigidBodyControl.class));
                            }
                            return null;
                        });
                    } catch (Exception e) {
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

    public Vector2f getChunkCenter(int chunkX, int chunkZ, float chunkSize) {
        float centerX = chunkX * chunkSize + chunkSize / 2f;
        float centerZ = chunkZ * chunkSize + chunkSize / 2f;
        return new Vector2f(centerX, centerZ);
    }

}
