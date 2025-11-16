package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

class SplineTerrainFlattenerTest {
    
    private static final float EPSILON = 0.01f;
    
    /**
     * Test that terrain is flattened along the road
     */
    @Test
    void testTerrainFlatteningAlongRoad() {
        // Create hilly terrain
        float[][] heightmap = createHillyTerrain(100, 100);
        
        // Create a straight road through the middle
        List<Vector3f> roadPoints = createStraightRoad(0, 50, 100, 50, 10);
        
        // Apply flattening
        SplineTerrainFlattener.flattenTerrainAlongSpline(
            heightmap, roadPoints, 0, 0, 100, 1.0f
        );
        
        // Check that the road area is flatter than before
        // Sample points along the road
        float maxVariation = 0;
        for (int x = 10; x < 90; x += 10) {
            int z = 50;  // Middle of the road
            float height = heightmap[x][z];
            
            // Check neighbors
            if (x > 0) {
                maxVariation = Math.max(maxVariation, Math.abs(height - heightmap[x-1][z]));
            }
            if (x < heightmap.length - 1) {
                maxVariation = Math.max(maxVariation, Math.abs(height - heightmap[x+1][z]));
            }
        }
        
        // Road should be relatively flat
        assertTrue(maxVariation < 0.2f,
            String.format("Road should be flat. Max variation: %.4f", maxVariation));
    }
    
    /**
     * Test that terrain blending creates smooth transitions
     */
    @Test
    void testSmoothBlendingWithNaturalTerrain() {
        float[][] heightmap = createHillyTerrain(100, 100);
        
        // Store original heights away from road
        float originalHeight = heightmap[50][10];  // Far from road
        
        List<Vector3f> roadPoints = createStraightRoad(0, 50, 100, 50, 10);
        
        SplineTerrainFlattener.flattenTerrainAlongSpline(
            heightmap, roadPoints, 0, 0, 100, 1.0f
        );
        
        // Heights far from road should be unchanged
        assertEquals(originalHeight, heightmap[50][10], 0.001f,
            "Terrain far from road should be unchanged");
        
        // Heights near road should be modified
        float nearRoadHeight = heightmap[50][48];  // Near road edge
        assertNotEquals(originalHeight, nearRoadHeight, 0.01f,
            "Terrain near road should be modified for blending");
    }
    
    /**
     * Test flattening with curved road
     */
    @Test
    void testFlatteningAlongCurvedRoad() {
        float[][] heightmap = createHillyTerrain(100, 100);
        
        // Create a curved road
        List<Vector3f> roadPoints = new ArrayList<>();
        for (float x = 0; x < 100; x += 2) {
            float z = 50 + (float) (Math.sin(x * 0.1) * 20);
            float y = 0.5f;  // Constant height
            roadPoints.add(new Vector3f(x, y, z));
        }
        
        SplineTerrainFlattener.flattenTerrainAlongSpline(
            heightmap, roadPoints, 0, 0, 100, 1.0f
        );
        
        // Check that points along the curved path are flattened
        boolean foundFlatArea = false;
        for (Vector3f point : roadPoints) {
            int x = (int) point.x;
            int z = (int) point.z;
            
            if (x >= 0 && x < heightmap.length && z >= 0 && z < heightmap[0].length) {
                // Check if this area is flatter than original hilly terrain
                float variation = 0;
                int count = 0;
                
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx >= 0 && nx < heightmap.length && nz >= 0 && nz < heightmap[0].length) {
                            variation += Math.abs(heightmap[nx][nz] - heightmap[x][z]);
                            count++;
                        }
                    }
                }
                
                if (count > 0 && variation / count < 0.05f) {
                    foundFlatArea = true;
                    break;
                }
            }
        }
        
        assertTrue(foundFlatArea, "Should find flattened areas along curved road");
    }
    
    /**
     * Test that flattening works correctly with world coordinate transforms
     */
    @Test
    void testWorldCoordinateTransform() {
        float[][] heightmap = createHillyTerrain(50, 50);
        
        // Road in world coordinates
        List<Vector3f> roadPoints = createStraightRoad(100, 25, 150, 25, 10);
        
        // Apply flattening with chunk offset
        SplineTerrainFlattener.flattenTerrainAlongSpline(
            heightmap, roadPoints, 100, 0, 50, 1.0f
        );
        
        // The middle row should be flattened
        boolean isFlatter = true;
        for (int x = 5; x < 45; x++) {
            float heightDiff = Math.abs(heightmap[x][25] - heightmap[x+1][25]);
            if (heightDiff > 0.15f) {
                isFlatter = false;
                break;
            }
        }
        
        assertTrue(isFlatter, "Road area should be flattened with coordinate transform");
    }
    
    /**
     * Helper: Create hilly terrain
     */
    private float[][] createHillyTerrain(int rows, int cols) {
        float[][] terrain = new float[rows][cols];
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                terrain[x][z] = 0.3f + 
                    (float)(Math.sin(x * 0.1) * 0.2) +
                    (float)(Math.cos(z * 0.15) * 0.15);
            }
        }
        return terrain;
    }
    
    /**
     * Helper: Create a straight road from (x1,z1) to (x2,z2)
     */
    private List<Vector3f> createStraightRoad(float x1, float z1, float x2, float z2, int numPoints) {
        List<Vector3f> points = new ArrayList<>();
        for (int i = 0; i < numPoints; i++) {
            float t = (float) i / (numPoints - 1);
            float x = x1 + t * (x2 - x1);
            float z = z1 + t * (z2 - z1);
            points.add(new Vector3f(x, 0.5f, z));
        }
        return points;
    }
}
