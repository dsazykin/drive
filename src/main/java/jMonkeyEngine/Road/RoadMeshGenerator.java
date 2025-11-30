package jMonkeyEngine.Road;

import com.jme3.asset.AssetManager;
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
    private final float ROAD_THICKNESS = 0.5f;
    private final AssetManager assetManager;

    public RoadMeshGenerator(AssetManager assetManager, float scale, int maxHeight) {
        this.assetManager = assetManager;
        this.SCALE = scale;
        this.MAX_HEIGHT = maxHeight;
    }

    /**
     * Generate a 3D road mesh from road points within a chunk.
     */
    public Geometry generateRoadGeometry(List<Node> roadPoints, ChunkCoord chunk, float[][] heightmap) {
        if (roadPoints == null || roadPoints.size() < 2) {
            return null;
        }

        // Interpolate points for smooth curves
        List<Vector3f> smoothPath = interpolateRoadPath(roadPoints, heightmap);

        if (smoothPath.size() < 2) {
            return null;
        }

        List<Vector3f> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Vector2f> uvs = new ArrayList<>();

        float halfWidth = ROAD_WIDTH / 2f;

        // Generate road segments from smoothed path
        for (int i = 0; i < smoothPath.size() - 1; i++) {
            Vector3f current = smoothPath.get(i);
            Vector3f next = smoothPath.get(i + 1);

            // Calculate direction vector
            float dx = next.x - current.x;
            float dz = next.z - current.z;
            float segLength = (float) Math.sqrt(dx * dx + dz * dz);

            if (segLength < 0.01f) continue;

            dx /= segLength;
            dz /= segLength;

            // Perpendicular vector for width
            float px = -dz;
            float pz = dx;

            // Heights are already in the interpolated points
            float currentHeight = current.y + ROAD_THICKNESS;
            float nextHeight = next.y + ROAD_THICKNESS;

            // Create quad for this segment (top surface)
            int baseIndex = vertices.size();

            // Current segment vertices (4 corners of the quad)
            Vector3f v0 = new Vector3f(
                current.x + px * halfWidth * (SCALE / 16),
                currentHeight,
                current.z + pz * halfWidth * (SCALE / 16)
            );
            Vector3f v1 = new Vector3f(
                current.x - px * halfWidth * (SCALE / 16),
                currentHeight,
                current.z - pz * halfWidth * (SCALE / 16)
            );
            Vector3f v2 = new Vector3f(
                next.x + px * halfWidth * (SCALE / 16),
                nextHeight,
                next.z + pz * halfWidth * (SCALE / 16)
            );
            Vector3f v3 = new Vector3f(
                next.x - px * halfWidth * (SCALE / 16),
                nextHeight,
                next.z - pz * halfWidth * (SCALE / 16)
            );

            vertices.add(v0);
            vertices.add(v1);
            vertices.add(v2);
            vertices.add(v3);

            // Top surface triangles
            indices.add(baseIndex);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 1);

            indices.add(baseIndex + 1);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 3);

            // Calculate normal (pointing up for top surface)
            Vector3f edge1 = v1.subtract(v0);
            Vector3f edge2 = v2.subtract(v0);
            Vector3f normal = edge1.cross(edge2).normalizeLocal();

            normals.add(normal);
            normals.add(normal);
            normals.add(normal);
            normals.add(normal);

            // UV coordinates for texture mapping
            float u = (float) i / (smoothPath.size() - 1);
            float uNext = (float) (i + 1) / (smoothPath.size() - 1);

            uvs.add(new Vector2f(u, 0));
            uvs.add(new Vector2f(u, 1));
            uvs.add(new Vector2f(uNext, 0));
            uvs.add(new Vector2f(uNext, 1));
        }

        if (vertices.isEmpty()) {
            return null;
        }

        // Create mesh
        Mesh mesh = new Mesh();

        Vector3f[] vertArray = vertices.toArray(new Vector3f[0]);
        Vector3f[] normalArray = normals.toArray(new Vector3f[0]);
        Vector2f[] uvArray = uvs.toArray(new Vector2f[0]);

        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }

        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(vertArray));
        mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(normalArray));
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(uvArray));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(indexArray));

        mesh.updateBound();

        // Create geometry
        Geometry roadGeom = new Geometry("Road_" + chunk.x + "_" + chunk.z, mesh);

        // Create material
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f)); // Dark gray road
        mat.setColor("Ambient", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        mat.setBoolean("UseMaterialColors", true);

        roadGeom.setMaterial(mat);

        // Position relative to chunk
        roadGeom.setLocalTranslation(
            chunk.x * (199f) * (SCALE / 16),
            0f,
            chunk.z * (199f) * (SCALE / 16)
        );

        return roadGeom;
    }

    /**
     * Sample height from heightmap with bounds checking.
     */
    private float sampleHeight(float[][] heightmap, int x, int y) {
        if (x < 0 || y < 0 || x >= heightmap.length || y >= heightmap[0].length) {
            return 0;
        }
        float height = heightmap[x][y];
        // Remove road marker if present
        if (height > 1) {
            height = height - (float) Math.floor(height);
        }
        return height;
    }

    /**
     * Interpolate road path using Catmull-Rom splines for smooth curves.
     */
    private List<Vector3f> interpolateRoadPath(List<Node> roadPoints, float[][] heightmap) {
        List<Vector3f> smoothPath = new ArrayList<>();

        if (roadPoints.size() < 2) {
            return smoothPath;
        }

        // Convert road points to world space positions
        List<Vector3f> controlPoints = new ArrayList<>();
        for (Node node : roadPoints) {
            float x = node.x * (SCALE / 16);
            float z = node.y * (SCALE / 16);
            float height = sampleHeight(heightmap, node.x, node.y) * MAX_HEIGHT;
            controlPoints.add(new Vector3f(x, height, z));
        }

        // Number of interpolated points between each pair of control points
        int subdivisions = 8;

        // Generate smooth curve using Catmull-Rom spline
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            // Get the four control points for this segment
            Vector3f p0 = (i > 0) ? controlPoints.get(i - 1) : controlPoints.get(i);
            Vector3f p1 = controlPoints.get(i);
            Vector3f p2 = controlPoints.get(i + 1);
            Vector3f p3 = (i < controlPoints.size() - 2) ? controlPoints.get(i + 2) : controlPoints.get(i + 1);

            // Interpolate between p1 and p2
            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;
                Vector3f point = catmullRomInterpolate(p0, p1, p2, p3, t);
                smoothPath.add(point);
            }
        }

        // Add the final point
        smoothPath.add(controlPoints.get(controlPoints.size() - 1));

        return smoothPath;
    }

    /**
     * Catmull-Rom spline interpolation.
     * Returns a point on the curve between p1 and p2, given parameter t (0 to 1).
     */
    private Vector3f catmullRomInterpolate(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;

        // Catmull-Rom matrix coefficients
        float coef0 = -0.5f * t3 + t2 - 0.5f * t;
        float coef1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
        float coef2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
        float coef3 = 0.5f * t3 - 0.5f * t2;

        float x = coef0 * p0.x + coef1 * p1.x + coef2 * p2.x + coef3 * p3.x;
        float y = coef0 * p0.y + coef1 * p1.y + coef2 * p2.y + coef3 * p3.y;
        float z = coef0 * p0.z + coef1 * p1.z + coef2 * p2.z + coef3 * p3.z;

        return new Vector3f(x, y, z);
    }
}

