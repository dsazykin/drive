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
    private final int CHUNK_SIZE;
    private final int MAX_HEIGHT;
    private final float ROAD_WIDTH;
    private final float yOffset = 0.05f; // small visual offset above terrain
    private final AssetManager assetManager;

    public RoadMeshGenerator(AssetManager assetManager, float scale, int chunkSize, int maxHeight,
                             float roadWidth) {
        this.assetManager = assetManager;
        this.SCALE = scale;
        CHUNK_SIZE = chunkSize;
        this.MAX_HEIGHT = maxHeight;
        ROAD_WIDTH = roadWidth;
    }

    public Geometry generateRoadGeometry(List<Node> roadPoints, ChunkCoord chunk,
                                         float[][] heightmap, Node prevGhostNode, Node nextGhostNode) {

        if (roadPoints == null || roadPoints.size() < 2) return null;

        // 1. Generate the smooth path using Ghost Nodes for context
        List<Vector3f> smoothPath = interpolateRoadPath(roadPoints, chunk, heightmap, prevGhostNode, nextGhostNode);

        if (smoothPath.size() < 2) return null;

        List<Vector3f> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Vector3f> normals = new ArrayList<>();
        List<Vector2f> uvs = new ArrayList<>();

        float halfWidth = ROAD_WIDTH / 2f;
        float unit = (SCALE / 16f); // Ensure this matches your global scale

        // 2. Generate Mesh from Spline
        // We iterate through the whole smooth path which now strictly represents
        // the segment inside THIS chunk (thanks to filtering in interpolateRoadPath)
        for (int i = 0; i < smoothPath.size() - 1; i++) {
            Vector3f current = smoothPath.get(i);
            Vector3f next = smoothPath.get(i + 1);

            float dx = next.x - current.x;
            float dz = next.z - current.z;
            float segLength = (float) Math.sqrt(dx * dx + dz * dz);

            if (segLength < 0.001f) continue;

            dx /= segLength;
            dz /= segLength;

            // Perpendicular vector
            float px = -dz;
            float pz = dx;

            // Vertices: Left and Right
            float cxLx = current.x + px * halfWidth * unit;
            float cxLz = current.z + pz * halfWidth * unit;
            float cxRx = current.x - px * halfWidth * unit;
            float cxRz = current.z - pz * halfWidth * unit;

            float nxLx = next.x + px * halfWidth * unit;
            float nxLz = next.z + pz * halfWidth * unit;
            float nxRx = next.x - px * halfWidth * unit;
            float nxRz = next.z - pz * halfWidth * unit;

            // Sample Heights (Bilinear for smoothness)
            // Note: dividing by 'unit' converts back to local grid index space
            float h_cL = sampleHeightBilinear(heightmap, cxLx / unit, cxLz / unit) * MAX_HEIGHT + yOffset;
            float h_cR = sampleHeightBilinear(heightmap, cxRx / unit, cxRz / unit) * MAX_HEIGHT + yOffset;
            float h_nL = sampleHeightBilinear(heightmap, nxLx / unit, nxLz / unit) * MAX_HEIGHT + yOffset;
            float h_nR = sampleHeightBilinear(heightmap, nxRx / unit, nxRz / unit) * MAX_HEIGHT + yOffset;

            int baseIndex = vertices.size();

            vertices.add(new Vector3f(cxLx, h_cL, cxLz)); // 0: current left
            vertices.add(new Vector3f(cxRx, h_cR, cxRz)); // 1: current right
            vertices.add(new Vector3f(nxLx, h_nL, nxLz)); // 2: next left
            vertices.add(new Vector3f(nxRx, h_nR, nxRz)); // 3: next right

            // Indices (Two Triangles)
            indices.add(baseIndex);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 1);

            indices.add(baseIndex + 1);
            indices.add(baseIndex + 2);
            indices.add(baseIndex + 3);

            // Normals (Flat shaded for simplicity, or average for smooth)
            Vector3f edge1 = vertices.get(baseIndex+1).subtract(vertices.get(baseIndex));
            Vector3f edge2 = vertices.get(baseIndex+2).subtract(vertices.get(baseIndex));
            Vector3f normal = edge1.cross(edge2).normalizeLocal();

            normals.add(normal);
            normals.add(normal);
            normals.add(normal);
            normals.add(normal);

            // UVs
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

        // ... Material Setup ...
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.3f, 0.3f, 0.3f, 1f));
        mat.setBoolean("UseMaterialColors", true);
        roadGeom.setMaterial(mat);

        // Position the geometry globally
        // Important: The vertices are already in "Chunk Local" space (0 to CHUNK_SIZE * unit)
        // So we just move the geometry to the chunk's start position.
        roadGeom.setLocalTranslation(
                chunk.x * (CHUNK_SIZE - 1) * unit,
                0f,
                chunk.z * (CHUNK_SIZE - 1) * unit
        );

        // Physics
        MeshCollisionShape shape = new MeshCollisionShape(mesh);
        RigidBodyControl rbc = new RigidBodyControl(shape, 0f);
        roadGeom.addControl(rbc);

        return roadGeom;
    }

    private float sampleHeight(float[][] heightmap, int x, int y) {
        if (x < 0 || y < 0 || x >= CHUNK_SIZE || y >= CHUNK_SIZE) return 0f;
        float h = heightmap[x][y];
        if (h > 1f) h = h - (float) Math.floor(h); // strip road marker, keep base height
        return h;
    }

    // Bilinear sampling in heightmap index space (local chunk coordinates)
    private float sampleHeightBilinear(float[][] heightmap, float lx, float lz) {
        int maxX = CHUNK_SIZE - 1;
        int maxZ = CHUNK_SIZE - 1;

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

    private List<Vector3f> interpolateRoadPath(List<Node> roadPoints, ChunkCoord currentChunk,
                                               float[][] heightmap, Node prevGhost, Node nextGhost) {
        List<Vector3f> smoothPath = new ArrayList<>();
        if (roadPoints.size() < 2) return smoothPath;

        float unit = (SCALE / 16f);
        List<Vector3f> controlPoints = new ArrayList<>();

        // 1. Add Previous Ghost (if exists)
        // We must transform it from its own chunk space to the CURRENT chunk's local space
        if (prevGhost != null) {
            controlPoints.add(getRelativePosition(prevGhost, currentChunk, unit));
        } else {
            // Extrapolate backward if no ghost (start of world)
            Vector3f p0 = getRelativePosition(roadPoints.get(0), currentChunk, unit);
            Vector3f p1 = getRelativePosition(roadPoints.get(1), currentChunk, unit);
            controlPoints.add(p0.subtract(p1.subtract(p0)));
        }

        // 2. Add Current Points
        for (Node node : roadPoints) {
            controlPoints.add(getRelativePosition(node, currentChunk, unit));
        }

        // 3. Add Next Ghost (if exists)
        if (nextGhost != null) {
            controlPoints.add(getRelativePosition(nextGhost, currentChunk, unit));
        } else {
            // Extrapolate forward
            int n = roadPoints.size();
            Vector3f pLast = getRelativePosition(roadPoints.get(n - 1), currentChunk, unit);
            Vector3f pPrev = getRelativePosition(roadPoints.get(n - 2), currentChunk, unit);
            controlPoints.add(pLast.add(pLast.subtract(pPrev)));
        }

        // 4. Generate Spline
        // Control Points Array: [GhostPrev, Node0, Node1, ... NodeLast, GhostNext]
        // Indices:                 0         1      2          k         k+1

        // We want to mesh the segment from Node0 to NodeLast.
        // Catmull-Rom interpolates between p1 and p2 using (p0, p1, p2, p3).
        // Segment 1 (Node0 -> Node1): Needs indices (0, 1, 2, 3) -> (GhostPrev, Node0, Node1, Node2)

        // So we iterate starting at i=0.
        // The number of actual road segments is roadPoints.size() - 1.
        // So we loop exactly that many times.

        int subdivisions = 8;

        // Note: controlPoints has size = roadPoints.size() + 2 (Ghosts)
        // We want to generate segments connecting the "real" nodes.
        // The first real node is at index 1.

        for (int i = 0; i < roadPoints.size() - 1; i++) {
            // For the first segment (i=0), we want to interpolate between CP[1] and CP[2].
            // The control points needed are CP[0], CP[1], CP[2], CP[3].

            int p0_idx = i;
            int p1_idx = i + 1;
            int p2_idx = i + 2;
            int p3_idx = i + 3;

            // Safety check (should not happen if logic is correct)
            if (p3_idx >= controlPoints.size()) break;

            Vector3f p0 = controlPoints.get(p0_idx);
            Vector3f p1 = controlPoints.get(p1_idx);
            Vector3f p2 = controlPoints.get(p2_idx);
            Vector3f p3 = controlPoints.get(p3_idx);

            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;
                smoothPath.add(catmullRomInterpolate(p0, p1, p2, p3, t));
            }
        }

        // Add the very last point explicitly
        smoothPath.add(controlPoints.get(roadPoints.size()));

        return smoothPath;
    }

    private Vector3f getRelativePosition(Node node, ChunkCoord currentChunk, float unit) {
        // Calculate chunk offset (e.g., -1, 0, or +1)
        int chunkDx = node.chunk.x - currentChunk.x;
        int chunkDz = node.chunk.z - currentChunk.z;

        // Convert logic grid size (CHUNK_SIZE) to world units
        float chunkWidth = (CHUNK_SIZE - 1); // e.g. 100 indices = 99 segments

        // Base local position (as if it were in its own chunk)
        float lx = node.x;
        float lz = node.y; // Assuming node.y is the Z-index

        // Apply offset
        float worldX = (chunkDx * chunkWidth + lx) * unit;
        float worldZ = (chunkDz * chunkWidth + lz) * unit;

        // Use the node's stored height (converted to world height)
        // We DO NOT re-sample terrain here because ghost nodes might be out of bounds
        // of the current heightmap array.
        float worldY = node.height * MAX_HEIGHT;

        return new Vector3f(worldX, worldY, worldZ);
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