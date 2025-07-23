package jMonkeyEngine.Road;

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
import jMonkeyEngine.Terrain.ChunkManager;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoadGenerator extends SimpleApplication {
    BulletAppState bulletAppState;
    ExecutorService executor;

    TerrainGenerator generator;
    ChunkManager manager;

    public static void main(String[] args) {
        RoadGenerator app = new RoadGenerator();
        app.start();
    }

    @Override
    public void simpleInitApp() {
        executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setEnabled(true);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

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

}
