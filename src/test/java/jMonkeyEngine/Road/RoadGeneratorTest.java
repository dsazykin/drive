package jMonkeyEngine.Road;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

class RoadGeneratorTest {
    
    private RoadGenerator roadGenerator;
    private static final int TERRAIN_SIZE = 500;
    
    @BeforeEach
    void setUp() {
        roadGenerator = new RoadGenerator();
    }
    
    /**
     * Test that the road always moves forward (away from origin)
     * by ensuring each node has a larger X coordinate than the previous one
     */
    @Test
    void testRoadAlwaysMovesForward() {
        float[][] heightmap = createFlatTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        
        List<Node> roadPath = roadGenerator.getRoadPointsInChunk(
            heightmap, 
            0, TERRAIN_SIZE / 2,    // start
            TERRAIN_SIZE - 1, TERRAIN_SIZE / 2  // goal
        );
        
        assertFalse(roadPath.isEmpty(), "Road path should not be empty");
        
        for (int i = 1; i < roadPath.size(); i++) {
            Node prev = roadPath.get(i - 1);
            Node current = roadPath.get(i);
            
            assertTrue(current.x > prev.x || (current.x == prev.x && i == roadPath.size() - 1),
                String.format("Road should always move forward. " +
                    "At index %d: prev.x=%d, current.x=%d", i, prev.x, current.x));
        }
    }
    
    /**
     * Test that the road never crosses over itself
     */
    @Test
    void testRoadNeverCrossesItself() {
        float[][] heightmap = createVariedTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        
        List<Node> roadPath = roadGenerator.getRoadPointsInChunk(
            heightmap,
            0, TERRAIN_SIZE / 2,
            TERRAIN_SIZE - 1, TERRAIN_SIZE / 2
        );
        
        assertFalse(roadPath.isEmpty(), "Road path should not be empty");
        
        Set<String> visitedPositions = new HashSet<>();
        
        for (Node node : roadPath) {
            String position = node.x + "," + node.y;
            assertFalse(visitedPositions.contains(position),
                String.format("Road crosses itself at position (%d, %d)", node.x, node.y));
            visitedPositions.add(position);
        }
    }
    
    /**
     * Test that the road reaches the goal
     */
    @Test
    void testRoadReachesGoal() {
        float[][] heightmap = createFlatTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        int goalX = TERRAIN_SIZE - 1;
        
        List<Node> roadPath = roadGenerator.getRoadPointsInChunk(
            heightmap,
            0, TERRAIN_SIZE / 2,
            goalX, TERRAIN_SIZE / 2
        );
        
        assertFalse(roadPath.isEmpty(), "Road path should not be empty");
        
        Node lastNode = roadPath.get(roadPath.size() - 1);
        assertTrue(lastNode.x >= goalX,
            String.format("Road should reach goal X=%d, but ended at X=%d", goalX, lastNode.x));
    }
    
    /**
     * Test that the road follows terrain contours on hilly terrain
     * by checking that it doesn't have excessive height changes
     */
    @Test
    void testRoadFollowsTerrainContours() {
        float[][] heightmap = createHillyTerrain(TERRAIN_SIZE, TERRAIN_SIZE);
        
        List<Node> roadPath = roadGenerator.getRoadPointsInChunk(
            heightmap,
            0, TERRAIN_SIZE / 2,
            TERRAIN_SIZE - 1, TERRAIN_SIZE / 2
        );
        
        assertFalse(roadPath.isEmpty(), "Road path should not be empty");
        
        // Count number of large height changes
        int largeHeightChanges = 0;
        for (int i = 1; i < roadPath.size(); i++) {
            Node prev = roadPath.get(i - 1);
            Node current = roadPath.get(i);
            
            float heightDiff = Math.abs(heightmap[current.x][current.y] - heightmap[prev.x][prev.y]);
            
            if (heightDiff > 0.1f) {  // Threshold for "large" change
                largeHeightChanges++;
            }
        }
        
        // The road should avoid most large height changes by following contours
        double percentageLargeChanges = (double) largeHeightChanges / roadPath.size() * 100;
        assertTrue(percentageLargeChanges < 30,
            String.format("Road should follow contours, but has %.1f%% large height changes",
                percentageLargeChanges));
    }
    
    /**
     * Helper method to create a flat terrain
     */
    private float[][] createFlatTerrain(int rows, int cols) {
        float[][] terrain = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                terrain[i][j] = 0.5f;  // Flat at mid-height
            }
        }
        return terrain;
    }
    
    /**
     * Helper method to create varied terrain with some height variation
     */
    private float[][] createVariedTerrain(int rows, int cols) {
        float[][] terrain = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // Create gentle variations
                terrain[i][j] = 0.4f + (float)(Math.sin(i * 0.05) * 0.1 + Math.cos(j * 0.05) * 0.1);
            }
        }
        return terrain;
    }
    
    /**
     * Helper method to create hilly terrain
     */
    private float[][] createHillyTerrain(int rows, int cols) {
        float[][] terrain = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // Create hills
                terrain[i][j] = 0.3f + 
                    (float)(Math.sin(i * 0.02) * 0.2) +
                    (float)(Math.cos(j * 0.03) * 0.15);
            }
        }
        return terrain;
    }
}
