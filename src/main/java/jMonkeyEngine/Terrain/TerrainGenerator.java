package jMonkeyEngine.Terrain;

import com.jme3.app.SimpleApplication;
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
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TerrainGenerator extends SimpleApplication {

    BulletAppState bulletAppState;
    HeightMapGenerator heightMap;
    ChunkManager manager;
    ExecutorService executor;

    int chunkSize = 16;
    float scale = 4f;
    int renderDistance = 2; // Grid size will be (2 * renderDistance - 1)^2

    private List<Future<?>> chunkTasks;
    private boolean loadingDone = false;
    private BitmapText loadingText;

    public static void main(String[] args) {
        TerrainGenerator app = new TerrainGenerator();
        app.start();
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

//                vertices[vertexIndex++] = new Vector3f(
//                        (float) ((chunkX * (size - 1) + x) * scale), // world X coordinate
//                        (float) (height * scale), // height
//                        (float) ((chunkZ * (size - 1) + z) * scale) // world Z coordinate
//                );

                vertices[vertexIndex++] = new Vector3f(
                        (float) (x * (scale / 4)),
                        (float) (height * (scale / 4)),
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

    @Override
    public void simpleInitApp() {
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        //bulletAppState.setDebugEnabled(true);

        this.heightMap = new HeightMapGenerator();
        this.manager =
                new ChunkManager(rootNode, assetManager, bulletAppState, this, executor, chunkSize,
                                 scale, renderDistance);

        //viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        enablePlayerControls(false);
        flyCam.setMoveSpeed(300);

        loadingText = new BitmapText(guiFont, false);
        loadingText.setSize(guiFont.getCharSet().getRenderedSize());
        loadingText.setText("Loading terrain...");
        loadingText.setLocalTranslation(300, 300, 0);
        guiNode.attachChild(loadingText);

        CreateTerrain();
        setUpLight();

        cam.setLocation(new Vector3f(32, 500, 32));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    @Override
    public void simpleUpdate(float tpf) {
        if (!loadingDone && chunkTasks != null) {
            boolean allDone = chunkTasks.stream().allMatch(Future::isDone);
            if (allDone) {
                loadingDone = true;
                enqueue(() -> {
                    enqueue(() -> guiNode.detachChild(loadingText));
                    enablePlayerControls(true);
                    return null;
                });
            }
        } else {
            manager.updateChunks(cam.getLocation());
        }
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

    private void enablePlayerControls(boolean enabled) {
        flyCam.setEnabled(enabled);
        inputManager.setCursorVisible(!enabled);
    }

    private void CreateTerrain() {
        chunkTasks = new ArrayList<>();

        for (int chunkZ = -renderDistance; chunkZ <= renderDistance; chunkZ++) {
            for (int chunkX = -renderDistance; chunkX <= renderDistance; chunkX++) {
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                Future<?> future = executor.submit(() -> {
                    try {
                        Mesh mesh = generateChunkMesh(finalChunkX, finalChunkZ, chunkSize, scale);
                        Geometry chunkGeom = createGeometry(finalChunkX, finalChunkZ, mesh);

                        enqueue(() -> {
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
        //executor.shutdown();
    }

}
