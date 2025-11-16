package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import java.util.*;

/**
 * New spline-based road generator that can freely cross between chunks.
 * Generates smooth roads using Catmull-Rom splines and follows terrain contours.
 */
public class SplineRoadGenerator {
    
    private CatmullRomSpline roadSpline;
    private List<Vector3f> controlPoints;
    private Random random;
    private long seed;
    
    // Configuration
    private static final float CONTROL_POINT_SPACING = 100.0f;  // Distance between control points
    private static final float MAX_LATERAL_DEVIATION = 50.0f;   // Max side-to-side movement
    private static final float FORWARD_STEP = 100.0f;           // How far forward each control point goes
    private static final int POINTS_PER_SEGMENT = 20;           // Spline resolution
    
    public SplineRoadGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.roadSpline = new CatmullRomSpline();
        this.controlPoints = new ArrayList<>();
        
        // Initialize with starting point
        controlPoints.add(new Vector3f(0, 0, 0));
    }
    
    /**
     * Generate the next section of road extending forward from the current end point.
     * This can be called as chunks are loaded, allowing the road to extend infinitely.
     * 
     * @param numControlPoints Number of new control points to generate
     * @param terrainSampler Function to sample terrain height at a given (x, z) position
     */
    public void extendRoad(int numControlPoints, TerrainHeightSampler terrainSampler) {
        Vector3f lastPoint = controlPoints.get(controlPoints.size() - 1);
        Vector3f direction = new Vector3f(1, 0, 0); // Always move forward in X direction
        
        // If we have at least 2 points, calculate the current direction
        if (controlPoints.size() >= 2) {
            Vector3f prev = controlPoints.get(controlPoints.size() - 2);
            direction = lastPoint.subtract(prev).normalize();
            direction.y = 0; // Keep horizontal
        }
        
        for (int i = 0; i < numControlPoints; i++) {
            // Generate next control point
            Vector3f nextPoint = generateNextControlPoint(lastPoint, direction, terrainSampler);
            controlPoints.add(nextPoint);
            
            // Update direction for next iteration
            direction = nextPoint.subtract(lastPoint).normalize();
            direction.y = 0;
            
            lastPoint = nextPoint;
        }
        
        // Update the spline with new control points
        roadSpline.setControlPoints(controlPoints);
    }
    
    /**
     * Generate the next control point, considering terrain and maintaining forward movement
     */
    private Vector3f generateNextControlPoint(Vector3f currentPoint, Vector3f currentDirection, 
                                               TerrainHeightSampler terrainSampler) {
        // Always move forward (positive X), but allow lateral movement (Z)
        float forwardDistance = FORWARD_STEP;
        
        // Add some randomness to lateral movement, but tend to follow contours
        float lateralOffset = (random.nextFloat() - 0.5f) * 2.0f * MAX_LATERAL_DEVIATION;
        
        // Sample terrain in a few directions to find the flattest path
        float bestScore = Float.MAX_VALUE;
        Vector3f bestPoint = null;
        
        // Try several candidate positions
        for (int attempt = 0; attempt < 5; attempt++) {
            float testLateral = lateralOffset * (0.5f + 0.5f * attempt / 4.0f);
            
            // Calculate candidate position (always moving forward in X)
            Vector3f candidate = new Vector3f(
                currentPoint.x + forwardDistance,
                0,
                currentPoint.z + testLateral
            );
            
            // Sample terrain height at this position
            float height = terrainSampler != null ? 
                terrainSampler.getHeight(candidate.x, candidate.z) : 0;
            candidate.y = height;
            
            // Calculate score based on terrain steepness
            // Sample a few points around the candidate to check flatness
            float score = 0;
            int samples = 0;
            float sampleRadius = 20.0f;
            
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    float testX = candidate.x + dx * sampleRadius;
                    float testZ = candidate.z + dz * sampleRadius;
                    float testHeight = terrainSampler != null ? 
                        terrainSampler.getHeight(testX, testZ) : 0;
                    
                    // Penalize steep gradients
                    float gradient = Math.abs(testHeight - height);
                    score += gradient * gradient;
                    samples++;
                }
            }
            
            score /= samples;
            
            // Prefer flatter terrain
            if (score < bestScore) {
                bestScore = score;
                bestPoint = candidate;
            }
        }
        
        return bestPoint != null ? bestPoint : new Vector3f(
            currentPoint.x + forwardDistance,
            currentPoint.y,
            currentPoint.z
        );
    }
    
    /**
     * Get all points along the road spline with high resolution
     */
    public List<Vector3f> getRoadPoints() {
        return roadSpline.generatePoints(POINTS_PER_SEGMENT);
    }
    
    /**
     * Get road points within a specific world region (for chunk-based generation)
     */
    public List<Vector3f> getRoadPointsInRegion(float minX, float maxX, float minZ, float maxZ) {
        List<Vector3f> allPoints = getRoadPoints();
        List<Vector3f> regionPoints = new ArrayList<>();
        
        for (Vector3f point : allPoints) {
            if (point.x >= minX && point.x <= maxX && 
                point.z >= minZ && point.z <= maxZ) {
                regionPoints.add(point);
            }
        }
        
        return regionPoints;
    }
    
    /**
     * Get the control points of the road
     */
    public List<Vector3f> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    /**
     * Get the road spline
     */
    public CatmullRomSpline getSpline() {
        return roadSpline;
    }
    
    /**
     * Check if road generation has reached a certain X coordinate
     */
    public boolean hasReached(float xCoordinate) {
        if (controlPoints.isEmpty()) return false;
        return controlPoints.get(controlPoints.size() - 1).x >= xCoordinate;
    }
    
    /**
     * Get the furthest X coordinate the road has reached
     */
    public float getFurthestX() {
        if (controlPoints.isEmpty()) return 0;
        return controlPoints.get(controlPoints.size() - 1).x;
    }
    
    /**
     * Interface for sampling terrain height at arbitrary positions
     */
    public interface TerrainHeightSampler {
        float getHeight(float x, float z);
    }
}
