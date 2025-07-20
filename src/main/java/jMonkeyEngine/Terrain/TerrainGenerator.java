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

public class TerrainGenerator extends SimpleApplication {

    BulletAppState bulletAppState;
    HeightMapGenerator heightMap;

    public static void main(String[] args) {
        TerrainGenerator app = new TerrainGenerator();
        app.start();
    }

    private float[][] generateHeightMap(int size, double scale) throws IOException {
        this.heightMap = new HeightMapGenerator();
        return heightMap.generateHeightmap(size, size, 1234L, scale);
    }

    private Mesh generateChunkMesh(int chunkX, int chunkZ, int size, double scale)
            throws IOException {
        float[][] terrain = generateHeightMap(size, scale);

        Mesh mesh = new Mesh();

        Vector3f[] vertices = new Vector3f[size * size];
        int vertexIndex = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float height = terrain[x][z];
                vertices[vertexIndex++] = new Vector3f(
                        (float) x,
                        (float) (height * scale),
                        (float) z
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

        // generate indices here for triangles...
        // skipped for brevity, but follow standard grid mesh indexing.

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indices));
        mesh.updateBound();
        return mesh;
    }

    @Override
    public void simpleInitApp() {
        //viewPort.setBackgroundColor(new ColorRGBA(0.7f, 0.8f, 1f, 1f));
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(100);

        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);
        bulletAppState.setDebugEnabled(true);

        CreateTerrain();
        setUpLight();

        cam.setLocation(new Vector3f(32, 50, 32));  // Adjust based on size and height
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    private void CreateTerrain() {
        Mesh mesh = null;
        try {
            mesh = generateChunkMesh(0, 0, 1024, 50);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Geometry chunkGeom = new Geometry("Chunk", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", ColorRGBA.Green);
        mat.setColor("Specular", ColorRGBA.White);
        mat.setFloat("Shininess", 64f);
        chunkGeom.setMaterial(mat);

        MeshCollisionShape terrainShape = new MeshCollisionShape(mesh);
        RigidBodyControl chunkPhysics = new RigidBodyControl(terrainShape, 0);
        chunkGeom.addControl(chunkPhysics);
        bulletAppState.getPhysicsSpace().add(chunkPhysics);
        rootNode.attachChild(chunkGeom);
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
