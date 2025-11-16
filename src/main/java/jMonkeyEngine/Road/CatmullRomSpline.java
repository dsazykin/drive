package jMonkeyEngine.Road;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Catmull-Rom spline implementation for smooth road curves.
 * Generates smooth curves that pass through all control points.
 */
public class CatmullRomSpline {
    private List<Vector3f> controlPoints;
    private float tension = 0.5f;  // 0.5 = standard Catmull-Rom
    
    public CatmullRomSpline() {
        this.controlPoints = new ArrayList<>();
    }
    
    public void addControlPoint(Vector3f point) {
        controlPoints.add(point);
    }
    
    public void setControlPoints(List<Vector3f> points) {
        this.controlPoints = new ArrayList<>(points);
    }
    
    public List<Vector3f> getControlPoints() {
        return new ArrayList<>(controlPoints);
    }
    
    /**
     * Interpolate a point on the spline.
     * @param segment The segment index (between control points)
     * @param t Parameter from 0 to 1 within the segment
     * @return The interpolated point
     */
    public Vector3f interpolate(int segment, float t) {
        if (controlPoints.size() < 2) {
            return controlPoints.isEmpty() ? new Vector3f(0, 0, 0) : controlPoints.get(0).clone();
        }
        
        // Get the 4 control points for Catmull-Rom interpolation
        Vector3f p0 = getControlPoint(segment - 1);
        Vector3f p1 = getControlPoint(segment);
        Vector3f p2 = getControlPoint(segment + 1);
        Vector3f p3 = getControlPoint(segment + 2);
        
        // Catmull-Rom formula
        float t2 = t * t;
        float t3 = t2 * t;
        
        Vector3f result = new Vector3f();
        
        // Calculate coefficients
        float c0 = -tension * t3 + 2.0f * tension * t2 - tension * t;
        float c1 = (2.0f - tension) * t3 + (tension - 3.0f) * t2 + 1.0f;
        float c2 = (tension - 2.0f) * t3 + (3.0f - 2.0f * tension) * t2 + tension * t;
        float c3 = tension * t3 - tension * t2;
        
        result.x = c0 * p0.x + c1 * p1.x + c2 * p2.x + c3 * p3.x;
        result.y = c0 * p0.y + c1 * p1.y + c2 * p2.y + c3 * p3.y;
        result.z = c0 * p0.z + c1 * p1.z + c2 * p2.z + c3 * p3.z;
        
        return result;
    }
    
    /**
     * Get control point with wrapping/clamping for endpoints
     */
    private Vector3f getControlPoint(int index) {
        if (index < 0) {
            // Extend the first segment backwards
            return controlPoints.get(0).clone();
        } else if (index >= controlPoints.size()) {
            // Extend the last segment forwards
            return controlPoints.get(controlPoints.size() - 1).clone();
        }
        return controlPoints.get(index);
    }
    
    /**
     * Generate a list of points along the spline with the specified density
     * @param pointsPerSegment Number of points to generate per segment
     * @return List of interpolated points
     */
    public List<Vector3f> generatePoints(int pointsPerSegment) {
        List<Vector3f> points = new ArrayList<>();
        
        if (controlPoints.size() < 2) {
            return points;
        }
        
        for (int i = 0; i < controlPoints.size() - 1; i++) {
            for (int j = 0; j < pointsPerSegment; j++) {
                float t = (float) j / pointsPerSegment;
                points.add(interpolate(i, t));
            }
        }
        
        // Add the final point
        points.add(controlPoints.get(controlPoints.size() - 1).clone());
        
        return points;
    }
    
    /**
     * Calculate the total length of the spline
     */
    public float getLength() {
        if (controlPoints.size() < 2) return 0;
        
        float length = 0;
        List<Vector3f> points = generatePoints(10);
        
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i).distance(points.get(i - 1));
        }
        
        return length;
    }
}
