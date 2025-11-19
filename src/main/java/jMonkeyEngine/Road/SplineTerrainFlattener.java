package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import java.util.List;

/**
 * Spline-based road terrain modifier that flattens terrain along the road path.
 * Optimized for performance with on-the-fly calculations.
 */
public class SplineTerrainFlattener {
    
    private static final float ROAD_WIDTH = 10.0f;
    private static final float BLEND_DISTANCE = 15.0f;  // Distance over which to blend with natural terrain
    
    /**
     * Apply road flattening to a heightmap using spline-based road points.
     * Optimized version that only processes cells near the road.
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
        
        float maxInfluenceDistance = ROAD_WIDTH / 2 + BLEND_DISTANCE;
        
        // Optimized: Process on-the-fly without creating large arrays
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                // Convert heightmap coordinates to world coordinates
                float worldX = chunkWorldX + x * worldScale;
                float worldZ = chunkWorldZ + z * worldScale;
                
                // Find closest point on the road (only check nearby segments)
                float minDist = Float.MAX_VALUE;
                float closestRoadHeight = 0;
                
                for (int i = 0; i < roadPoints.size() - 1; i++) {
                    Vector3f p1 = roadPoints.get(i);
                    Vector3f p2 = roadPoints.get(i + 1);
                    
                    // Quick bounding box check for optimization
                    float minX = Math.min(p1.x, p2.x) - maxInfluenceDistance;
                    float maxX = Math.max(p1.x, p2.x) + maxInfluenceDistance;
                    float minZ = Math.min(p1.z, p2.z) - maxInfluenceDistance;
                    float maxZ = Math.max(p1.z, p2.z) + maxInfluenceDistance;
                    
                    if (worldX < minX || worldX > maxX || worldZ < minZ || worldZ > maxZ) {
                        continue;  // Skip segments too far away
                    }
                    
                    // Calculate distance from point to line segment
                    float dist = pointToSegmentDistance(worldX, worldZ, p1.x, p1.z, p2.x, p2.z);
                    
                    if (dist < minDist) {
                        minDist = dist;
                        // Interpolate road height
                        float t = getClosestPointOnSegment(worldX, worldZ, p1.x, p1.z, p2.x, p2.z);
                        closestRoadHeight = p1.y * (1 - t) + p2.y * t;
                    }
                }
                
                // Only apply flattening if within influence distance
                if (minDist < maxInfluenceDistance) {
                    if (minDist < ROAD_WIDTH / 2) {
                        // Within road width - completely flat at road height
                        heightmap[x][z] = closestRoadHeight;
                    } else {
                        // In blend zone - interpolate between road height and natural terrain
                        float blendFactor = (minDist - ROAD_WIDTH / 2) / BLEND_DISTANCE;
                        blendFactor = smoothstep(blendFactor);  // Smooth S-curve blending
                        
                        float originalHeight = heightmap[x][z];
                        heightmap[x][z] = closestRoadHeight * (1 - blendFactor) + 
                                         originalHeight * blendFactor;
                    }
                }
            }
        }
        
        // Apply a simple smoothing pass to the modified areas
        smoothRoadAreas(heightmap, roadPoints, chunkWorldX, chunkWorldZ, worldScale);
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
     * Apply simple smoothing to areas near the road
     */
    private static void smoothRoadAreas(float[][] heightmap, List<Vector3f> roadPoints,
                                        float chunkWorldX, float chunkWorldZ, float worldScale) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;
        float[][] smoothed = new float[rows][cols];
        
        // Copy original
        for (int x = 0; x < rows; x++) {
            for (int z = 0; z < cols; z++) {
                smoothed[x][z] = heightmap[x][z];
            }
        }
        
        // Apply light smoothing only near road
        float smoothRadius = ROAD_WIDTH;
        
        for (int x = 1; x < rows - 1; x++) {
            for (int z = 1; z < cols - 1; z++) {
                float worldX = chunkWorldX + x * worldScale;
                float worldZ = chunkWorldZ + z * worldScale;
                
                // Quick check if near any road point
                boolean nearRoad = false;
                for (Vector3f point : roadPoints) {
                    float dx = worldX - point.x;
                    float dz = worldZ - point.z;
                    if (dx * dx + dz * dz < smoothRadius * smoothRadius) {
                        nearRoad = true;
                        break;
                    }
                }
                
                if (nearRoad) {
                    // Simple 3x3 box blur
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
                heightmap[x][z] = smoothed[x][z];
            }
        }
    }
}
