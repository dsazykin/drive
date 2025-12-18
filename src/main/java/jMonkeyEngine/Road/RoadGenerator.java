package jMonkeyEngine.Road;

import com.jme3.scene.Geometry;
import jMonkeyEngine.Chunks.ChunkCoord;
import jMonkeyEngine.Chunks.ChunkManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RoadGenerator {

    ChunkManager manager;

    public int currentXChunk = 0;
    public int currentZChunk = 0;
    public Integer lastZCoord = null;
    public Integer lastXCoord = null;
    public boolean verticalExitUp = false;
    public boolean verticalExitDown = false;

    public Node lastRoadNode = null;
    public ChunkCoord lastChunkCoord = null;
    public float[][] lastHeightmap = null;

    // Default to moving positive X for the very first chunk
    private float lastExitDx = 1.0f;
    private float lastExitDy = 0.0f;

    // Tuning constants
    private static final float TURN_WEIGHT = 50f;   // penalty strength for turning
    private static final float NO_UTURN_COS = 0.2588190451f; // disallow turns sharper than ~75 degrees
    private final int MIN_CONTINUATION = 10; // minimum distance to travel into next chunk

    private boolean isInMainChunk = true;
    private final ConcurrentHashMap<ChunkCoord, float[][]> heightmaps = new ConcurrentHashMap<>();
    
    public void setManager(ChunkManager mgr) {
        this.manager = mgr;
    }

    // Cache offsets per radius to avoid recomputation
    private final Map<Integer, List<int[]>> offsetCache = new HashMap<>();

    private List<int[]> generateOffsets(int radius) {
        return offsetCache.computeIfAbsent(radius, r -> {
            List<int[]> offsets = new ArrayList<>();

            // CHANGE 1: Start 'dx' at 1 instead of -r.
            // This ensures every move has a positive X component (Forward).
            for (int dx = 1; dx <= r; dx++) {

                for (int dy = -r; dy <= r; dy++) {
                    // (No need to check for 0,0 anymore since dx is never 0)

                    double dist = Math.sqrt(dx * dx + dy * dy);

                    // Select points that are roughly 'r' distance away (the arc)
                    if (Math.abs(dist - r) < 0.5) {
                        offsets.add(new int[]{dx, dy});
                    }
                }
            }
            return Collections.unmodifiableList(offsets);
        });
    }

    public HashMap<ChunkCoord, List<Node>> getRoadPointsInChunk(float[][] heightmap, List<Node> existingPoints, int startX, int startY, int goalX, ChunkCoord chunk) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;

        heightmaps.put(chunk, heightmap);

        verticalExitUp = false;
        verticalExitDown = false;

        PriorityQueue<Node> currentSet = new PriorityQueue<>();
        HashMap<ChunkCoord, boolean[][]> visitedMaps = new HashMap<>();
        HashMap<ChunkCoord, Node[][]> nodeMaps = new HashMap<>();

        visitedMaps.put(chunk, new boolean[rows][cols]);
        nodeMaps.put(chunk, new Node[rows][cols]);

        if (existingPoints != null && !existingPoints.isEmpty()) {
            // SCENARIO A: Resuming from existing path (Neighbor -> Current)

            // 1. Load ALL existing points into the map so we don't overlap them
            for (Node node : existingPoints) {
                // Ensure the node belongs to this chunk (sanity check)
                if (node.chunk.equals(chunk)) {
                    visitedMaps.get(chunk)[node.x][node.y] = true;
                    nodeMaps.get(chunk)[node.x][node.y] = node;
                }
            }

            // 2. Pick the last node as the "Start" (The Frontier)
            Node tip = existingPoints.get(existingPoints.size() - 1);
            existingPoints.get(0).parent = null;

            // 3. Add to Queue to resume A*
            currentSet.add(tip);

        } else {
            // SCENARIO B: Fresh Start (First Chunk)

            Node start = new Node(startX, startY, getRoadHeight(startX, startY, heightmap), 0,
                                  Math.abs(startX - goalX), null, chunk);

            // Default forward momentum for the very first node
            start.dxFromParent = 1;
            start.dyFromParent = 0;
            start.dirMag = 1;

            currentSet.add(start);
            nodeMaps.get(chunk)[startX][startY] = start;
        }

        boolean enteredFromTop = (startY >= cols - 1);
        boolean enteredFromBottom = (startY <= 0);
        final int MIN_PROGRESS = 30;

        List<int[]> directions = generateOffsets(10);

        while (!currentSet.isEmpty()) {
            Node current = currentSet.poll();

            ChunkCoord currentChunk = current.chunk;

            int distanceFromStart = Math.abs(current.y - startY);
            boolean canExitThroughEntry = distanceFromStart >= MIN_PROGRESS;

            if (current.parent != null && !currentChunk.equals(chunk)) {
                List<Node> fullPath = null;

                int dX = currentChunk.x - chunk.x;
                int dZ = currentChunk.z - chunk.z;

                if (dX > 0) {
                    if (current.x >= MIN_CONTINUATION) {
                        fullPath = reconstructPath(current);
                    }
                } else if(dZ > 0) {
                    if (current.y >= MIN_CONTINUATION) {
                        if (!enteredFromBottom || canExitThroughEntry) {
                            verticalExitDown = true;
                            fullPath = reconstructPath(current);
                        }
                    }
                } else if (dZ < 0) {
                    if (current.y <= cols - 1 - MIN_CONTINUATION) {
                        if (!enteredFromTop || canExitThroughEntry) {
                            verticalExitUp = true;
                            fullPath = reconstructPath(current);
                        }
                    }
                }

                if (fullPath != null) {
                    HashMap<ChunkCoord, List<Node>> pathsByChunk = new HashMap<>();
                    for (Node node : fullPath) {
                        if (!pathsByChunk.containsKey(node.chunk)) {
                            pathsByChunk.put(node.chunk, new ArrayList<>());
                        } else {
                            pathsByChunk.get(node.chunk).add(node);
                        }
                    }
                    return pathsByChunk;
                }
            }

            float[][] currentHeightmap = heightmaps.get(currentChunk);

            for (int[] dir : directions) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];

                int chunkOffsetX = Math.floorDiv(nx, rows);
                int chunkOffsetY = Math.floorDiv(ny, cols);

                ChunkCoord targetChunk = new ChunkCoord(currentChunk.x + chunkOffsetX,
                                                        currentChunk.z + chunkOffsetY);
                float[][] targetHeightmap = currentHeightmap;

                if (!targetChunk.equals(currentChunk)) {
                    nx = Math.floorMod(nx, rows);
                    ny = Math.floorMod(ny, cols);

                    if (!visitedMaps.containsKey(targetChunk)) {
                        visitedMaps.put(targetChunk, new boolean[rows][cols]);
                        nodeMaps.put(targetChunk, new Node[rows][cols]);
                    }

                    if (!heightmaps.containsKey(targetChunk)) {
                        heightmaps.put(targetChunk, manager.generateTerrain(targetChunk));
                    }

                    targetHeightmap = heightmaps.get(targetChunk);
                }

                if (visitedMaps.get(targetChunk)[nx][ny]) continue;

                int dx = dir[0];
                int dy = dir[1];
                if (dx < 0) continue;

                float stepLen = (float) Math.sqrt(dx * dx + dy * dy);
                if (stepLen <= 1e-6f) continue;
                float newDirX = dx / stepLen;
                float newDirY = dy / stepLen;

                // Previous normalized direction
                float prevDirX, prevDirY;

                // We use the values we stored in the node (which now includes start node data)
                if (current.dirMag <= 1e-6f) {
                    // Fallback (should rarely happen now due to injection)
                    prevDirX = newDirX;
                    prevDirY = newDirY;
                } else {
                    prevDirX = current.dxFromParent / current.dirMag;
                    prevDirY = current.dyFromParent / current.dirMag;
                }

                float cos = prevDirX * newDirX + prevDirY * newDirY;
                if (cos < -1f) cos = -1f; else if (cos > 1f) cos = 1f;

                if (cos < NO_UTURN_COS) continue;

                // Optional: You might want to multiply TURN_WEIGHT by 1.5f specifically
                // if (current == start) to STRONGLY discourage bending at the chunk seam.
                float turnPenalty = TURN_WEIGHT * (1f - cos) * stepLen;

                float heightWeight = 50f * (rows * 2);
                float h1 = currentHeightmap[current.x][current.y];
                float h2 = targetHeightmap[nx][ny];

                float heightDiff = Math.abs(h1 - h2);

                float slopeAtNext = getSlopePenalty(nx, ny, targetHeightmap);

                float SLOPE_WEIGHT = 50f * (rows * 2);
                float terrainCost = (heightWeight * heightDiff) + (SLOPE_WEIGHT * slopeAtNext);

                float xProgressBonus = dx > 0 ? -30f * dx : 0f;
                float baseCost = stepLen * 10f + xProgressBonus;

                float moveCost = baseCost + turnPenalty + terrainCost;

                float tentativeG = current.gCost + moveCost;

                float roadHeight = getRoadHeight(nx, ny, targetHeightmap);
                Node neighbor = nodeMaps.get(targetChunk)[nx][ny];
                if (neighbor == null || tentativeG < neighbor.gCost) {
                    int chunkDifferenceX = targetChunk.x - chunk.x;
                    int globalNodeX = (chunkDifferenceX * rows) + nx;

                    float h = heuristic(globalNodeX, goalX);

                    // Store dx/dy in the neighbor so it can pass it to its children
                    neighbor = new Node(nx, ny, roadHeight, tentativeG, tentativeG + h, current,
                                        dx, dy, targetChunk);

                    // Ensure magnitude is cached
                    neighbor.dirMag = stepLen;

                    nodeMaps.get(targetChunk)[nx][ny] = neighbor;
                    currentSet.add(neighbor);
                    visitedMaps.get(targetChunk)[nx][ny] = true;
                }
            }
        }
        HashMap<ChunkCoord, List<Node>> result = new HashMap<>();
        if (existingPoints != null) result.put(chunk, new ArrayList<>(existingPoints));
        return result;
    }

    private float getRoadHeight(int x, int y, float[][] terrain) {
        float sum = terrain[x][y];
        int points = 1;
        if (x + 1 < terrain.length) { sum += terrain[x + 1][y]; points++; }
        if (x - 1 >= 0) { sum += terrain[x - 1][y]; points++; }
        if (y + 1 < terrain[0].length) { sum += terrain[x][y + 1]; points++; }
        if (y - 1 >= 0) { sum += terrain[x][y - 1]; points++; }
        return sum / points;
    }

    private float getSlopePenalty(int x, int y, float[][] heightmap) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;
        float h = heightmap[x][y];
        float dx = 0f, dz = 0f;
        int count = 0;
        if (x > 0 && x < rows - 1) { dx = (heightmap[x + 1][y] - heightmap[x - 1][y]) / 2f; count++; }
        if (y > 0 && y < cols - 1) { dz = (heightmap[x][y + 1] - heightmap[x][y - 1]) / 2f; count++; }
        if (count == 0) return 0f;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private float heuristic(int x1, int x2) {
        return Math.abs(x1 - x2);
    }

    private List<Node> reconstructPath(Node end) {
        if (verticalExitDown) {
            currentZChunk++;
            lastXCoord = end.x;
        } else if (verticalExitUp) {
            currentZChunk--;
            lastXCoord = end.x;
        } else {
            currentXChunk++;
            lastZCoord = end.y;
        }

        List<Node> path = new ArrayList<>();
        Node cur = end;
        while (cur != null) {
            path.add(cur);
            cur = cur.parent;
        }
        Collections.reverse(path);

        if (path.size() >= 2) {
            Node last = path.get(path.size() - 1);
            Node secondToLast = path.get(path.size() - 2);

            // Calculate vector
            lastExitDx = last.x - secondToLast.x;
            lastExitDy = last.y - secondToLast.y;
        } else {
            // Fallback for tiny paths (rare)
            lastExitDx = 1.0f;
            lastExitDy = 0.0f;
        }

        return path;
    }
}