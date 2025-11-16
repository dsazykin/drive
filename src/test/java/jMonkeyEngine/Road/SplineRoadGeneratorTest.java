package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class SplineRoadGeneratorTest {
    
    private SplineRoadGenerator roadGenerator;
    private static final long TEST_SEED = 12345L;
    
    @BeforeEach
    void setUp() {
        roadGenerator = new SplineRoadGenerator(TEST_SEED);
    }
    
    /**
     * Test that road always moves forward (away from origin in X direction)
     */
    @Test
    void testRoadAlwaysMovesForward() {
        // Create a simple terrain sampler
        SplineRoadGenerator.TerrainHeightSampler flatTerrain = (x, z) -> 0.5f;
        
        // Extend the road
        roadGenerator.extendRoad(10, flatTerrain);
        
        List<Vector3f> controlPoints = roadGenerator.getControlPoints();
        
        assertTrue(controlPoints.size() >= 2, "Should have multiple control points");
        
        // Check that each control point has a larger X than the previous
        for (int i = 1; i < controlPoints.size(); i++) {
            Vector3f prev = controlPoints.get(i - 1);
            Vector3f current = controlPoints.get(i);
            
            assertTrue(current.x > prev.x,
                String.format("Road should move forward. Point %d: x=%.2f, previous x=%.2f",
                    i, current.x, prev.x));
        }
    }
    
    /**
     * Test that road can extend infinitely
     */
    @Test
    void testRoadExtensionWorks() {
        SplineRoadGenerator.TerrainHeightSampler flatTerrain = (x, z) -> 0.5f;
        
        float initialX = roadGenerator.getFurthestX();
        
        roadGenerator.extendRoad(5, flatTerrain);
        float afterFirst = roadGenerator.getFurthestX();
        
        roadGenerator.extendRoad(5, flatTerrain);
        float afterSecond = roadGenerator.getFurthestX();
        
        assertTrue(afterFirst > initialX, "First extension should increase X");
        assertTrue(afterSecond > afterFirst, "Second extension should increase X further");
    }
    
    /**
     * Test that spline generates smooth interpolated points
     */
    @Test
    void testSplineGeneratesSmoothPoints() {
        SplineRoadGenerator.TerrainHeightSampler flatTerrain = (x, z) -> 0.5f;
        
        roadGenerator.extendRoad(5, flatTerrain);
        
        List<Vector3f> roadPoints = roadGenerator.getRoadPoints();
        List<Vector3f> controlPoints = roadGenerator.getControlPoints();
        
        // Spline should generate many more points than control points
        assertTrue(roadPoints.size() > controlPoints.size() * 2,
            "Spline should interpolate many points between control points");
        
        // Check smoothness - no sudden jumps
        for (int i = 1; i < roadPoints.size(); i++) {
            float distance = roadPoints.get(i).distance(roadPoints.get(i - 1));
            assertTrue(distance < 50.0f,
                String.format("Points should be close together for smoothness. Distance: %.2f", distance));
        }
    }
    
    /**
     * Test that road can be queried by region (for chunk-based loading)
     */
    @Test
    void testRoadPointsInRegion() {
        SplineRoadGenerator.TerrainHeightSampler flatTerrain = (x, z) -> 0.5f;
        
        roadGenerator.extendRoad(20, flatTerrain);
        
        // Query a specific region
        float minX = 200;
        float maxX = 400;
        float minZ = -100;
        float maxZ = 100;
        
        List<Vector3f> regionPoints = roadGenerator.getRoadPointsInRegion(minX, maxX, minZ, maxZ);
        
        // All points should be within the region
        for (Vector3f point : regionPoints) {
            assertTrue(point.x >= minX && point.x <= maxX,
                String.format("Point X=%.2f should be in range [%.2f, %.2f]", point.x, minX, maxX));
            assertTrue(point.z >= minZ && point.z <= maxZ,
                String.format("Point Z=%.2f should be in range [%.2f, %.2f]", point.z, minZ, maxZ));
        }
    }
    
    /**
     * Test that road follows terrain when given varying heights
     */
    @Test
    void testRoadFollowsTerrain() {
        // Create hilly terrain
        SplineRoadGenerator.TerrainHeightSampler hillyTerrain = (x, z) -> {
            return 0.5f + (float) (Math.sin(x * 0.02) * 0.2);
        };
        
        roadGenerator.extendRoad(10, hillyTerrain);
        
        List<Vector3f> controlPoints = roadGenerator.getControlPoints();
        
        // Check that road heights vary (following terrain)
        boolean hasVariation = false;
        float firstHeight = controlPoints.get(0).y;
        
        for (Vector3f point : controlPoints) {
            if (Math.abs(point.y - firstHeight) > 0.1f) {
                hasVariation = true;
                break;
            }
        }
        
        assertTrue(hasVariation, "Road should follow terrain height variations");
    }
    
    /**
     * Test that road prefers flatter terrain
     */
    @Test
    void testRoadPrefersFlatterTerrain() {
        // Create terrain with a flat valley and steep hills
        SplineRoadGenerator.TerrainHeightSampler valleyTerrain = (x, z) -> {
            // Flat at z=0, steep elsewhere
            return 0.5f + Math.abs(z) * 0.01f;
        };
        
        roadGenerator.extendRoad(15, valleyTerrain);
        
        List<Vector3f> controlPoints = roadGenerator.getControlPoints();
        
        // Calculate average absolute Z deviation
        float avgAbsZ = 0;
        for (Vector3f point : controlPoints) {
            avgAbsZ += Math.abs(point.z);
        }
        avgAbsZ /= controlPoints.size();
        
        // Road should tend to stay near z=0 (the flatter area)
        assertTrue(avgAbsZ < 30.0f,
            String.format("Road should prefer flatter terrain. Average |Z|: %.2f", avgAbsZ));
    }
}
