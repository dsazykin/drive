package jMonkeyEngine.Terrain;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TerrainGenerator extends SimpleApplication {

    BulletAppState bulletAppState;
    HeightMapGenerator heightMap;

    Thread thread1;
    Thread thread2;

    public static void main(String[] args) {
        TerrainGenerator app = new TerrainGenerator();
        app.start();
    }

    private float[][] generateHeightMap(int size, double scale, int chunkX, int chunkZ) throws IOException {
        return heightMap.generateHeightmap(size, size, 1234L, scale, chunkX, chunkZ);
    }

    private Mesh generateChunkMesh(int chunkX, int chunkZ, int size, double scale)
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
                        (float) ((chunkX * (size - 1) + x)), // world X coordinate
                        height * 50f, // height
                        (float) ((chunkZ * (size - 1) + z)) // world Z coordinate
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

    @Override
    public void simpleInitApp() {
        this.heightMap = new HeightMapGenerator();

        //viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(100);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        //bulletAppState.setDebugEnabled(true);

        CreateTerrain();
        setUpLight();

        cam.setLocation(new Vector3f(32, 50, 32));  // Adjust based on size and height
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    private void CreateTerrain() {
        int chunkSize = 200;
        float scale = 100f; // Or your preferred scale
        int renderRadius = 3; // Grid size will be (2 * renderRadius - 1)^2

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (int chunkZ = -renderRadius; chunkZ <= renderRadius; chunkZ++) {
            for (int chunkX = -renderRadius; chunkX <= renderRadius; chunkX++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                executor.submit(() -> {
                    try {
                        Mesh mesh = generateChunkMesh(finalChunkX, finalChunkZ, chunkSize, scale);

                        Geometry chunkGeom = new Geometry("Chunk_" + finalChunkX + "_" + finalChunkZ, mesh);
                        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
                        mat.setBoolean("VertexColor", true);
                        chunkGeom.setMaterial(mat);

                        MeshCollisionShape terrainShape = new MeshCollisionShape(mesh);
                        RigidBodyControl chunkPhysics = new RigidBodyControl(terrainShape, 0);
                        chunkGeom.addControl(chunkPhysics);

                        // Enqueue scene updates on render thread
                        enqueue(() -> {
                            bulletAppState.getPhysicsSpace().add(chunkPhysics);
                            rootNode.attachChild(chunkGeom);
                            return null;
                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        executor.shutdown();
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
}
