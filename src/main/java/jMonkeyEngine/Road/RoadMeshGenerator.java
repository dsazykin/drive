package jMonkeyEngine.Road;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import jMonkeyEngine.Chunks.ChunkCoord;

import java.util.ArrayList;
import java.util.List;

public class RoadMeshGenerator {

    private final float SCALE;
    private final int MAX_HEIGHT;
    private final float ROAD_WIDTH = 6f;
    private final float yOffset = 0.05f; // small visual offset above terrain
    private final AssetManager assetManager;

    public RoadMeshGenerator(AssetManager assetManager, float scale, int maxHeight) {
        this.assetManager = assetManager;
        this.SCALE = scale;
        this.MAX_HEIGHT = maxHeight;
    }

    public Geometry generateRoadGeometry(List<Node> roadPoints, ChunkCoord chunk, float[][] heightmap) {
        if (roadPoints == null || roadPoints.size() < 2) return null;

        // Curved path in local chunk space (x,z in local units scaled by SCALE/16)
        List<Vector3f> smoothPath = interpolateRoadPath(roadPoints, heightmap);
        if (smoothPath.size() < 2) return null;

        List<Vector3f> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Vector2f> uvs = new ArrayList<>();

        float halfWidth = ROAD_WIDTH / 2f;
        float unit = (SCALE / 16f); // world units per heightmap cell

        for (int i = 0; i < smoothPath.size() - 1; i++) {
            Vector3f current = smoothPath.get(i);     // local space (x,z in world units within chunk)
            Vector3f next = smoothPath.get(i + 1);

            float dx = next.x - current.x;
            float dz = next.z - current.z;
            float segLength = (float) Math.sqrt(dx * dx + dz * dz);
            if (segLength < 0.01f) continue;

            dx /= segLength;
            dz /= segLength;

            float px = -dz;
            float pz = dx;

            // Left/right positions in local chunk space
            float cxLx = current.x + px * halfWidth * unit;
            float cxLz = current.z + pz * halfWidth * unit;
            float cxRx = current.x - px * halfWidth * unit;
            float cxRz = current.z - pz * halfWidth * unit;

            float nxLx = next.x + px * halfWidth * unit;
            float nxLz = next.z + pz * halfWidth * unit;
            float nxRx = next.x - px * halfWidth * unit;
            float nxRz = next.z - pz * halfWidth * unit;

            // Sample terrain heights at each vertex (local chunk space -> heightmap indices)
            float h_cL = sampleHeightBilinear(heightmap, cxLx / unit, cxLz / unit) * MAX_HEIGHT + yOffset;
            float h_cR = sampleHeightBilinear(heightmap, cxRx / unit, cxRz / unit) * MAX_HEIGHT + yOffset;
            float h_nL = sampleHeightBilinear(heightmap, nxLx / unit, nxLz / unit) * MAX_HEIGHT + yOffset;
            float h_nR = sampleHeightBilinear(heightmap, nxRx / unit, nxRz / unit) * MAX_HEIGHT + yOffset;

            int baseIndex = vertices.size();

            Vector3f v0 = new Vector3f(cxLx, h_cL, cxLz); // current left
            Vector3f v1 = new Vector3f(cxRx, h_cR, cxRz); // current right
            Vector3f v2 = new Vector3f(nxLx, h_nL, nxLz); // next left
            Vector3f v3 = new Vector3f(nxRx, h_nR, nxRz); // next right

            vertices.add(v0);
            vertices.add(v1);
            vertices.add(v2);
            vertices.add(v3);

            // Indices for the quad strip (top surface)
            indices.add(baseIndex);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 1);

            indices.add(baseIndex + 1);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 3);

            // Per-face normals (approximate upward)
            Vector3f edge1 = v1.subtract(v0);
            Vector3f edge2 = v2.subtract(v0);
            Vector3f normal = edge1.cross(edge2).normalizeLocal();
            normals.add(normal);
            normals.add(normal);
            normals.add(normal);
            normals.add(normal);

            float u = (float) i / (smoothPath.size() - 1);
            float uNext = (float) (i + 1) / (smoothPath.size() - 1);
            uvs.add(new Vector2f(u, 0));
            uvs.add(new Vector2f(u, 1));
            uvs.add(new Vector2f(uNext, 0));
            uvs.add(new Vector2f(uNext, 1));
        }

        if (vertices.isEmpty()) return null;

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertices.toArray(new Vector3f[0])));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normals.toArray(new Vector3f[0])));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvs.toArray(new Vector2f[0])));
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) indexArray[i] = indices.get(i);
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indexArray));
        mesh.updateBound();

        Geometry roadGeom = new Geometry("Road_" + chunk.x + "_" + chunk.z, mesh);

        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        mat.setBoolean("UseMaterialColors", true);
        roadGeom.setMaterial(mat);

        // Place mesh at chunk world origin; vertices are in local chunk space
        roadGeom.setLocalTranslation(
                chunk.x * 199f * unit,
                0f,
                chunk.z * 199f * unit
        );

        // Physics matches mesh shape (static)
        MeshCollisionShape shape = new MeshCollisionShape(mesh);
        RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
        roadGeom.addControl(rbc);

        return roadGeom;
    }

    private float sampleHeight(float[][] heightmap, int x, int y) {
        if (x < 0 || y < 0 || x >= heightmap.length || y >= heightmap[0].length) return 0f;
        float h = heightmap[x][y];
        if (h > 1f) h = h - (float) Math.floor(h); // strip road marker, keep base height
        return h;
    }

    // Bilinear sampling in heightmap index space (local chunk coordinates)
    private float sampleHeightBilinear(float[][] heightmap, float lx, float lz) {
        int maxX = heightmap.length - 1;
        int maxZ = heightmap[0].length - 1;

        int x0 = clamp((int) Math.floor(lx), 0, maxX);
        int z0 = clamp((int) Math.floor(lz), 0, maxZ);
        int x1 = clamp(x0 + 1, 0, maxX);
        int z1 = clamp(z0 + 1, 0, maxZ);

        float tx = Math.max(0f, Math.min(1f, lx - x0));
        float tz = Math.max(0f, Math.min(1f, lz - z0));

        float h00 = sampleHeight(heightmap, x0, z0);
        float h10 = sampleHeight(heightmap, x1, z0);
        float h01 = sampleHeight(heightmap, x0, z1);
        float h11 = sampleHeight(heightmap, x1, z1);

        float h0 = h00 + tx * (h10 - h00);
        float h1 = h01 + tx * (h11 - h01);
        return h0 + tz * (h1 - h0);
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private List<Vector3f> interpolateRoadPath(List<Node> roadPoints, float[][] heightmap) {
        List<Vector3f> smoothPath = new ArrayList<>();
        if (roadPoints.size() < 2) return smoothPath;

        float unit = (SCALE / 16f);
        List<Vector3f> controlPoints = new ArrayList<>();
        for (Node node : roadPoints) {
            float x = node.x * unit;
            float z = node.y * unit;
            float height = sampleHeight(heightmap, node.x, node.y) * MAX_HEIGHT;
            controlPoints.add(new Vector3f(x, height, z));
        }

        int subdivisions = 8;
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            Vector3f p0 = (i > 0) ? controlPoints.get(i - 1) : controlPoints.get(i);
            Vector3f p1 = controlPoints.get(i);
            Vector3f p2 = controlPoints.get(i + 1);
            Vector3f p3 = (i < controlPoints.size() - 2) ? controlPoints.get(i + 2) : controlPoints.get(i + 1);
            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;
                smoothPath.add(catmullRomInterpolate(p0, p1, p2, p3, t));
            }
        }
        smoothPath.add(controlPoints.get(controlPoints.size() - 1));
        return smoothPath;
    }

    private Vector3f catmullRomInterpolate(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        float c0 = -0.5f * t3 + t2 - 0.5f * t;
        float c1 =  1.5f * t3 - 2.5f * t2 + 1.0f;
        float c2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float c3 =  0.5f * t3 - 0.5f * t2;
        return new Vector3f(
                c0 * p0.x + c1 * p1.x + c2 * p2.x + c3 * p3.x,
                c0 * p0.y + c1 * p1.y + c2 * p2.y + c3 * p3.y,
                c0 * p0.z + c1 * p1.z + c2 * p2.z + c3 * p3.z
        );
    }
}