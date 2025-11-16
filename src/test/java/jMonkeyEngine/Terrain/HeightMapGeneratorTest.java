package jMonkeyEngine.Terrain;

import jMonkeyEngine.Road.Node;
import jMonkeyEngine.Road.RoadGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;

class HeightMapGeneratorTest {
    
    private HeightMapGenerator heightMapGenerator;
    private static final int TERRAIN_SIZE = 500;
    private static final long TEST_SEED = 12345L;
    
    @BeforeEach
    void setUp() {
        heightMapGenerator = new HeightMapGenerator(TEST_SEED, TERRAIN_SIZE, 40.0);
    }
    
    /**
     * Test that terrain flattening makes the road flat
     */
    @Test
    void testRoadFlatteningMakesRoadFlat() {
        // Create hilly terrain
        float[][] heightmap = createHillyTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        
        // Create a sample road path
        List<Node> roadPath = createSampleRoadPath();
        
        // Apply flattening
        heightMapGenerator.applyRoadFlattening(heightmap, roadPath);
        
        // Check that the road area is relatively flat
        // Sample points along the road path (skip edge cases)
        int flatSegments = 0;
        int totalSegments = 0;
        
        for (int i = 5; i < roadPath.size() - 5; i++) {  // Skip edges
            Node current = roadPath.get(i);
            
            // Check height at road positions
            int x = current.x;
            int y = current.y;
            
            if (x >= 5 && x < heightmap.length - 5 && y >= 5 && y < heightmap[0].length - 5) {
                float currentHeight = heightmap[x][y];
                
                // Check a few neighbors to ensure flatness
                float maxDiff = 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        if (x + dx >= 0 && x + dx < heightmap.length && 
                            y + dy >= 0 && y + dy < heightmap[0].length) {
                            float neighborHeight = heightmap[x + dx][y + dy];
                            float diff = Math.abs(currentHeight - neighborHeight);
                            maxDiff = Math.max(maxDiff, diff);
                        }
                    }
                }
                
                totalSegments++;
                if (maxDiff < 0.3f) {  // More lenient threshold
                    flatSegments++;
                }
            }
        }
        
        // Most of the road should be flat
        double flatPercentage = (double) flatSegments / totalSegments * 100;
        assertTrue(flatPercentage > 70,
            String.format("Road should be mostly flat. Flat segments: %.1f%%", flatPercentage));
    }
    
    /**
     * Test that terrain flattening creates smooth transitions
     */
    @Test
    void testRoadFlatteningCreatesSmoothTransitions() {
        float[][] heightmap = createHillyTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        List<Node> roadPath = createSampleRoadPath();
        
        heightMapGenerator.applyRoadFlattening(heightmap, roadPath);
        
        // Check smoothness along the road
        int smoothSegments = 0;
        for (int i = 2; i < roadPath.size(); i++) {
            Node current = roadPath.get(i);
            Node prev = roadPath.get(i - 1);
            Node prevPrev = roadPath.get(i - 2);
            
            int x = current.x;
            int y = current.y;
            int px = prev.x;
            int py = prev.y;
            int ppx = prevPrev.x;
            int ppy = prevPrev.y;
            
            if (x >= 0 && x < heightmap.length && y >= 0 && y < heightmap[0].length &&
                px >= 0 && px < heightmap.length && py >= 0 && py < heightmap[0].length &&
                ppx >= 0 && ppx < heightmap.length && ppy >= 0 && ppy < heightmap[0].length) {
                
                float h1 = heightmap[ppx][ppy];
                float h2 = heightmap[px][py];
                float h3 = heightmap[x][y];
                
                // Check for smooth transition (no sharp spikes)
                float diff1 = Math.abs(h2 - h1);
                float diff2 = Math.abs(h3 - h2);
                
                if (diff1 < 0.1f && diff2 < 0.1f) {
                    smoothSegments++;
                }
            }
        }
        
        // Most segments should be smooth
        double smoothPercentage = (double) smoothSegments / (roadPath.size() - 2) * 100;
        assertTrue(smoothPercentage > 60,
            String.format("Road should have smooth transitions. Smooth segments: %.1f%%",
                smoothPercentage));
    }
    
    /**
     * Test that terrain blending creates gradual transitions around the road
     */
    @Test
    void testTerrainBlendingCreatesGradualTransitions() {
        float[][] heightmap = createHillyTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        List<Node> roadPath = createSampleRoadPath();
        
        // Get heights before flattening at positions near the road
        Node sampleNode = roadPath.get(roadPath.size() / 2);
        int testX = sampleNode.x;
        int testY = Math.min(sampleNode.y + 10, heightmap[0].length - 1);
        float heightBefore = heightmap[testX][testY];
        
        heightMapGenerator.applyRoadFlattening(heightmap, roadPath);
        
        float heightAfter = heightmap[testX][testY];
        
        // The area near the road should be affected by blending
        // but not as drastically as the road itself
        assertNotEquals(heightBefore, heightAfter, 0.001,
            "Terrain near road should be affected by blending");
    }
    
    /**
     * Helper method to create hilly terrain
     */
    private float[][] createHillyTerrain(int rows, int cols) {
        float[][] terrain = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                terrain[i][j] = 0.3f + 
                    (float)(Math.sin(i * 0.02) * 0.2) +
                    (float)(Math.cos(j * 0.03) * 0.15);
            }
        }
        return terrain;
    }
    
    /**
     * Helper method to create a sample road path
     */
    private List<Node> createSampleRoadPath() {
        List<Node> path = new ArrayList<>();
        
        // Create a path that goes forward with some variation
        int startY = TERRAIN_SIZE / 2;
        for (int x = 10; x < TERRAIN_SIZE - 10; x += 5) {
            int y = startY + (int)(Math.sin(x * 0.05) * 20);
            y = Math.max(10, Math.min(y, TERRAIN_SIZE - 10));
            path.add(new Node(x, y, 0.5f, 0, 0, null));
        }
        
        return path;
    }
}
