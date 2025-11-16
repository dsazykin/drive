package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import java.util.List;

/**
 * Spline-based road terrain modifier that flattens terrain along the road path.
 * More efficient than point-by-point flattening.
 */
public class SplineTerrainFlattener {
    
    private static final float ROAD_WIDTH = 10.0f;
    private static final float BLEND_DISTANCE = 15.0f;  // Distance over which to blend with natural terrain
    
    /**
     * Apply road flattening to a heightmap using spline-based road points.
     * This is much more efficient than plotting thousands of individual points.
     * 
     * @param heightmap The terrain heightmap to modify
     * @param roadPoints List of Vector3f points along the road spline
     * @param chunkWorldX World X coordinate of the chunk's origin
     * @param chunkWorldZ World Z coordinate of the chunk's origin
     * @param chunkSize Size of the chunk
     * @param worldScale Scale factor for world coordinates
     */
    public static void flattenTerrainAlongSpline(float[][] heightmap, List<Vector3f> roadPoints,
                                                  float chunkWorldX, float chunkWorldZ,
                                                  int chunkSize, float worldScale) {
        if (roadPoints.isEmpty()) return;
        
        int rows = heightmap.length;
        int cols = heightmap[0].length;
        
        // Create a distance field for efficient road influence calculation
        float[][] roadHeights = new float[rows][cols];
        float[][] distanceToRoad = new float[rows][cols];
        
        // Initialize with max distance
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                distanceToRoad[x][z] = Float.MAX_VALUE;
                roadHeights[x][z] = 0;
            }
        }
        
        // For each point in the heightmap, find distance to nearest road point
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                // Convert heightmap coordinates to world coordinates
                float worldX = chunkWorldX + x * worldScale;
                float worldZ = chunkWorldZ + z * worldScale;
                
                // Find closest point on the road
                float minDist = Float.MAX_VALUE;
                float closestRoadHeight = 0;
                
                for (int i = 0; i < roadPoints.size() - 1; i++) {
                    Vector3f p1 = roadPoints.get(i);
                    Vector3f p2 = roadPoints.get(i + 1);
                    
                    // Calculate distance from point to line segment
                    float dist = pointToSegmentDistance(worldX, worldZ, p1.x, p1.z, p2.x, p2.z);
                    
                    if (dist < minDist) {
                        minDist = dist;
                        // Interpolate road height
                        float t = getClosestPointOnSegment(worldX, worldZ, p1.x, p1.z, p2.x, p2.z);
                        closestRoadHeight = p1.y * (1 - t) + p2.y * t;
                    }
                }
                
                distanceToRoad[x][z] = minDist;
                roadHeights[x][z] = closestRoadHeight;
            }
        }
        
        // Apply flattening with smooth blending
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                float dist = distanceToRoad[x][z];
                
                if (dist < ROAD_WIDTH / 2) {
                    // Within road width - completely flat
                    heightmap[x][z] = roadHeights[x][z];
                } else if (dist < ROAD_WIDTH / 2 + BLEND_DISTANCE) {
                    // In blend zone - interpolate between road height and natural terrain
                    float blendFactor = (dist - ROAD_WIDTH / 2) / BLEND_DISTANCE;
                    blendFactor = smoothstep(blendFactor);  // Smooth S-curve blending
                    
                    float originalHeight = heightmap[x][z];
                    heightmap[x][z] = roadHeights[x][z] * (1 - blendFactor) + 
                                     originalHeight * blendFactor;
                }
                // Beyond blend distance - leave terrain unchanged
            }
        }
        
        // Apply smoothing pass to the road area for better quality
        smoothRoadArea(heightmap, distanceToRoad, ROAD_WIDTH / 2);
    }
    
    /**
     * Calculate distance from a point to a line segment
     */
    private static float pointToSegmentDistance(float px, float pz, 
                                                float ax, float az, 
                                                float bx, float bz) {
        float t = getClosestPointOnSegment(px, pz, ax, az, bx, bz);
        
        float closestX = ax + t * (bx - ax);
        float closestZ = az + t * (bz - az);
        
        float dx = px - closestX;
        float dz = pz - closestZ;
        
        return (float) Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Get the parameter t [0,1] of the closest point on segment AB to point P
     */
    private static float getClosestPointOnSegment(float px, float pz,
                                                   float ax, float az,
                                                   float bx, float bz) {
        float abx = bx - ax;
        float abz = bz - az;
        float apx = px - ax;
        float apz = pz - az;
        
        float ab2 = abx * abx + abz * abz;
        float ap_ab = apx * abx + apz * abz;
        
        if (ab2 == 0) return 0;
        
        float t = ap_ab / ab2;
        return Math.max(0, Math.min(1, t));
    }
    
    /**
     * Smooth S-curve function for better blending
     */
    private static float smoothstep(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
    
    /**
     * Apply smoothing to the road area for better quality
     */
    private static void smoothRoadArea(float[][] heightmap, float[][] distanceToRoad, float roadRadius) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;
        float[][] smoothed = new float[rows][cols];
        
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                smoothed[x][z] = heightmap[x][z];
            }
        }
        
        // Apply smoothing only to road area
        for (int x = 1; x < rows - 1; x++) {
            for (int z = 1; z < cols - 1; z++) {
                if (distanceToRoad[x][z] < roadRadius) {
                    // Simple box blur for smoothing
                    float sum = 0;
                    int count = 0;
                    
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            sum += heightmap[x + dx][z + dz];
                            count++;
                        }
                    }
                    
                    smoothed[x][z] = sum / count;
                }
            }
        }
        
        // Copy smoothed values back
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                if (distanceToRoad[x][z] < roadRadius) {
                    heightmap[x][z] = smoothed[x][z];
                }
            }
        }
    }
}
