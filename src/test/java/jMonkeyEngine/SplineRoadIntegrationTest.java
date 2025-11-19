package jMonkeyEngine;

import com.jme3.math.Vector3f;
import jMonkeyEngine.Road.SplineRoadGenerator;
import jMonkeyEngine.Terrain.HeightMapGenerator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify the spline road system works with terrain generation
 */
class SplineRoadIntegrationTest {
    
    @Test
    void testSplineRoadTerrainIntegration() {
        // Create components
        long seed = 12345L;
        int chunkSize = 500;
        float scale = 40f;
        
        HeightMapGenerator heightMapGen = new HeightMapGenerator(seed, chunkSize, scale);
        SplineRoadGenerator roadGen = new SplineRoadGenerator(seed);
        
        // Create terrain sampler
        SplineRoadGenerator.TerrainHeightSampler sampler = 
            (x, z) -> heightMapGen.sampleTerrainHeight(x, z);
        
        // Generate initial road
        roadGen.extendRoad(10, sampler);
        
        // Verify road was generated
        assertTrue(roadGen.getControlPoints().size() >= 10, 
            "Should have at least 10 control points");
        
        // Verify road moves forward
        float startX = roadGen.getControlPoints().get(0).x;
        float endX = roadGen.getControlPoints().get(roadGen.getControlPoints().size() - 1).x;
        assertTrue(endX > startX, "Road should move forward");
        
        // Verify we can get road points
        java.util.List<Vector3f> roadPoints = roadGen.getRoadPoints();
        assertFalse(roadPoints.isEmpty(), "Should have generated road points");
        
        // Verify road points are in expected range
        for (Vector3f point : roadPoints) {
            assertTrue(point.x >= 0, "Road X coordinate should be non-negative");
            assertTrue(point.y >= 0 && point.y <= 1.0f, 
                "Road Y (height) should be in terrain range [0, 1]");
        }
        
        System.out.println("Integration test passed:");
        System.out.println("  Control points: " + roadGen.getControlPoints().size());
        System.out.println("  Road points: " + roadPoints.size());
        System.out.println("  Road extends from X=" + startX + " to X=" + endX);
    }
    
    @Test
    void testSplineRoadExtensionOnDemand() {
        long seed = 12345L;
        int chunkSize = 500;
        float scale = 40f;
        
        HeightMapGenerator heightMapGen = new HeightMapGenerator(seed, chunkSize, scale);
        SplineRoadGenerator roadGen = new SplineRoadGenerator(seed);
        
        SplineRoadGenerator.TerrainHeightSampler sampler = 
            (x, z) -> heightMapGen.sampleTerrainHeight(x, z);
        
        // Initial generation
        roadGen.extendRoad(5, sampler);
        float firstX = roadGen.getFurthestX();
        
        // Extend further
        roadGen.extendRoad(10, sampler);
        float secondX = roadGen.getFurthestX();
        
        assertTrue(secondX > firstX, "Road should extend further after second call");
        
        // Verify road reaches expected distance
        assertTrue(secondX > 500, "Road should extend beyond 500 units after 15 control points");
        
        System.out.println("Extension test passed:");
        System.out.println("  After 5 points: X=" + firstX);
        System.out.println("  After 15 points: X=" + secondX);
    }
}
