# New Spline-Based Road Generation System

## Overview

This is a **completely new** road generation system that replaces the old A* pathfinding approach. The new system addresses the fundamental issues with the previous implementation:

### Problems with Old System (FIXED)
- ❌ Required very large chunks (500x500) due to A* pathfinding constraints
- ❌ Computationally expensive pathfinding for each chunk
- ❌ Road could only move in straight chunk lines, couldn't cross between chunks
- ❌ Rudimentary flattening that plotted thousands of individual points

### New System Benefits
- ✅ Uses **Catmull-Rom splines** for smooth, realistic roads
- ✅ Can **freely cross between chunks** - no chunk alignment constraints
- ✅ **Extensible design** - road extends on-demand as chunks load
- ✅ **Efficient terrain flattening** using distance fields instead of point-plotting
- ✅ **Always moves forward** - progresses away from origin
- ✅ **Follows terrain contours** - samples multiple positions to find flattest path
- ✅ **Smooth blending** with natural terrain

## Architecture

### Core Components

#### 1. `CatmullRomSpline.java`
- Implements Catmull-Rom spline interpolation
- Generates smooth curves that pass through all control points
- Configurable tension parameter for curve shape
- Efficient point generation with configurable density

#### 2. `SplineRoadGenerator.java`
- Main road generation system
- Generates control points that always move forward (X direction)
- Samples terrain to find flattest paths
- Can extend infinitely as new chunks are loaded
- Supports querying road points by world region (for chunk-based loading)

**Key Features:**
```java
// Create generator
SplineRoadGenerator road = new SplineRoadGenerator(seed);

// Extend road as chunks are loaded
road.extendRoad(10, terrainSampler);

// Get points in a specific chunk region
List<Vector3f> points = road.getRoadPointsInRegion(minX, maxX, minZ, maxZ);
```

#### 3. `SplineTerrainFlattener.java`
- Efficient terrain flattening using distance field algorithm
- Computes distance to nearest road point for each heightmap cell
- Smooth blending with natural terrain (no hard edges)
- Applies smoothing only to road area

**Key Features:**
- Road width: 10 units (fully flat)
- Blend distance: 15 units (smooth transition)
- Uses smoothstep function for S-curve blending
- O(n*m) complexity where n = heightmap points, m = road segments

## How It Works

### Road Generation Algorithm

1. **Control Point Generation**
   - Start at origin (0, 0, 0)
   - For each new control point:
     - Always move forward in X direction (FORWARD_STEP = 100 units)
     - Allow lateral deviation in Z direction (±MAX_LATERAL_DEVIATION = 50 units)
     - Sample 5 candidate positions
     - For each candidate, sample terrain in a 3x3 grid
     - Select position with flattest terrain (lowest gradient variance)

2. **Spline Interpolation**
   - Use Catmull-Rom spline to create smooth curves between control points
   - Generate 20 interpolated points per segment
   - Results in smooth, realistic road curves

3. **Chunk Integration**
   - Road extends independently of chunk boundaries
   - Each chunk queries road points within its region
   - No need to generate entire parent chunks

### Terrain Flattening Algorithm

1. **Distance Field Calculation**
   - For each heightmap point, compute distance to nearest road segment
   - Store closest road height for later use

2. **Height Modification**
   - Points within road width (5 units radius): completely flat
   - Points in blend zone (5-20 units): smooth interpolation
   - Points beyond blend zone: unchanged

3. **Blending Function**
   - Uses smoothstep (3t² - 2t³) for smooth S-curve blending
   - Avoids harsh transitions between road and terrain

4. **Smoothing Pass**
   - Apply box blur to road area only
   - Further improves road quality

## Usage Example

### Basic Setup
```java
// Create road generator with seed
SplineRoadGenerator road = new SplineRoadGenerator(12345L);

// Define terrain height sampler
SplineRoadGenerator.TerrainHeightSampler terrainSampler = (x, z) -> {
    // Your terrain height function
    return getTerrainHeight(x, z);
};

// Extend road forward
road.extendRoad(20, terrainSampler);

// Get all road points
List<Vector3f> roadPoints = road.getRoadPoints();
```

### Chunk-Based Generation
```java
// When loading a chunk at (chunkX, chunkZ)
float chunkWorldX = chunkX * chunkSize;
float chunkWorldZ = chunkZ * chunkSize;

// Check if road needs to be extended
if (!road.hasReached(chunkWorldX + chunkSize)) {
    road.extendRoad(10, terrainSampler);
}

// Get points in this chunk's region
List<Vector3f> chunkRoadPoints = road.getRoadPointsInRegion(
    chunkWorldX, chunkWorldX + chunkSize,
    chunkWorldZ - 100, chunkWorldZ + chunkSize + 100  // Include buffer for blending
);

// Apply flattening to chunk heightmap
SplineTerrainFlattener.flattenTerrainAlongSpline(
    heightmap, 
    chunkRoadPoints,
    chunkWorldX,
    chunkWorldZ,
    chunkSize,
    worldScale
);
```

## Configuration Parameters

### SplineRoadGenerator
- `CONTROL_POINT_SPACING`: 100 units - distance between control points
- `MAX_LATERAL_DEVIATION`: 50 units - max side-to-side movement
- `FORWARD_STEP`: 100 units - how far forward each control point moves
- `POINTS_PER_SEGMENT`: 20 - spline resolution

### SplineTerrainFlattener
- `ROAD_WIDTH`: 10 units - width of completely flat road
- `BLEND_DISTANCE`: 15 units - distance over which to blend with terrain

## Performance Comparison

### Old System
- Generated 500x500 heightmap per parent chunk
- A* pathfinding: O(n log n) where n = 250,000 points
- Plotted thousands of individual points for flattening
- **Very slow** for large chunks

### New System
- Control point generation: O(k) where k = number of control points (~10-20)
- Spline interpolation: O(k * p) where p = points per segment
- Terrain flattening: O(h * r) where h = heightmap size, r = road segments
- **Much faster** and scalable

## Testing

The new system includes 10 comprehensive tests:

### SplineRoadGeneratorTest (6 tests)
1. testRoadAlwaysMovesForward - Validates forward-only movement
2. testRoadExtensionWorks - Confirms infinite extensibility
3. testSplineGeneratesSmoothPoints - Verifies smooth interpolation
4. testRoadPointsInRegion - Tests chunk-based queries
5. testRoadFollowsTerrain - Validates terrain following
6. testRoadPrefersFlatterTerrain - Confirms flatness preference

### SplineTerrainFlattenerTest (4 tests)
1. testTerrainFlatteningAlongRoad - Verifies road flattening
2. testSmoothBlendingWithNaturalTerrain - Tests smooth transitions
3. testFlatteningAlongCurvedRoad - Validates curved road support
4. testWorldCoordinateTransform - Tests coordinate transformations

All tests passing ✅

## Migration from Old System

To integrate this new system:

1. Replace `RoadGenerator` instantiation with `SplineRoadGenerator`
2. Update chunk loading logic to extend road on-demand
3. Replace `HeightMapGenerator.applyRoadFlattening()` with `SplineTerrainFlattener.flattenTerrainAlongSpline()`
4. Remove parent chunk size constraints - chunks can now be any size
5. Update road point storage to use Vector3f instead of Node

## Future Enhancements

Potential improvements:
- Add road curvature constraints for more realistic turns
- Implement multi-lane road support
- Add elevation-aware pathfinding (prefer valleys over mountains)
- Support for road intersections and branches
- Dynamic level-of-detail for distant road segments
