package jMonkeyEngine.Road;

import java.util.*;

public class RoadGenerator {

    public int currentXChunk = 0;
    public int currentZChunk = 0;
    public Integer lastZCoord = null;
    public Integer lastXCoord = null;
    public boolean verticalExitUp = false;
    public boolean verticalExitDown = false;

    // Tuning constants
    private static final float TURN_WEIGHT = 200f;   // penalty strength for turning
    private static final float NO_UTURN_COS = -0.25f; // disallow turns sharper than ~104 degrees

    // Cache offsets per radius to avoid recomputation
    private final Map<Integer, List<int[]>> offsetCache = new HashMap<>();

    private List<int[]> generateOffsets(int radius) {
        return offsetCache.computeIfAbsent(radius, r -> {
            List<int[]> offsets = new ArrayList<>();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (dx == 0 && dy == 0)
                        continue;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (Math.abs(dist - r) < 0.5) {
                        offsets.add(new int[]{dx, dy});
                    }
                }
            }
            for (int[] offset : offsets) {
                System.out.println(Arrays.toString(offset));
            }
            return Collections.unmodifiableList(offsets);
        });
    }

    public List<Node> getRoadPointsInChunk(float[][] heightmap, int startX, int startY, int goalX, int goalY) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;

        verticalExitUp = false;
        verticalExitDown = false;

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        boolean[][] visited = new boolean[rows][cols];
        Node[][] nodeMap = new Node[rows][cols];

        Node start = new Node(startX, startY, getRoadHeight(startX, startY, heightmap), 0,
                              heuristic(startX, startY, goalX, goalY), null);
        openSet.add(start);
        nodeMap[startX][startY] = start;

        boolean enteredFromTop = (startY >= cols - 1);
        boolean enteredFromBottom = (startY <= 0);
        final int MIN_PROGRESS = 30;

        // Ring step candidates (example radius)
        List<int[]> directions = generateOffsets(10);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            if (current.x >= goalX) {
                return reconstructPath(current);
            }

            int distanceFromStart = Math.abs(current.y - startY);
            if (current.parent != null) {
                boolean canExitThroughEntry = distanceFromStart >= MIN_PROGRESS;
                if (current.y <= 0) {
                    if (!enteredFromBottom || canExitThroughEntry) {
                        verticalExitDown = true;
                        return reconstructPath(current);
                    }
                } else if (current.y >= cols - 1) {
                    if (!enteredFromTop || canExitThroughEntry) {
                        verticalExitUp = true;
                        return reconstructPath(current);
                    }
                }
            }

            for (int[] dir : directions) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];
                if (nx < 0 || ny < 0 || nx >= rows || ny >= cols || visited[nx][ny]) continue;

                int dx = dir[0];
                int dy = dir[1];
                if (dx < 0) continue; // discourage backward X progress

                // Step length and normalized new direction
                float stepLen = (float) Math.sqrt(dx * dx + dy * dy);
                if (stepLen <= 1e-6f) continue;
                float newDirX = dx / stepLen;
                float newDirY = dy / stepLen;

                // Previous normalized direction
                float prevDirX, prevDirY;
                if (current.dirMag <= 1e-6f) {
                    // At the start, no penalty
                    prevDirX = newDirX;
                    prevDirY = newDirY;
                } else {
                    prevDirX = current.dxFromParent / current.dirMag;
                    prevDirY = current.dyFromParent / current.dirMag;
                }

                // Cosine of turn angle, clamp for stability
                float cos = prevDirX * newDirX + prevDirY * newDirY;
                if (cos < -1f) cos = -1f; else if (cos > 1f) cos = 1f;

                // Optional guard against hard U-turns
                if (cos < NO_UTURN_COS) continue;

                // Smooth turning penalty: ~0 for straight, grows with angle
                // For small angles: (1 - cos) ~= 0.5 * angle^2
                float turnPenalty = TURN_WEIGHT * (1f - cos) * stepLen;

                // Terrain + distance + forward progress
                float heightWeight = 100.0f * (rows * 2);
                float heightDiff = Math.abs(heightmap[current.x][current.y] - heightmap[nx][ny]);
                float slopeAtNext = getSlopePenalty(nx, ny, heightmap);

                // Combine both: height change over step + slope at destination
                float SLOPE_WEIGHT = 50f * (rows * 2);
                float terrainCost = (heightWeight * heightDiff) + (SLOPE_WEIGHT * slopeAtNext);

                float xProgressBonus = dx > 0 ? -30f * dx : 0f;
                float baseCost = stepLen * 10f + xProgressBonus;

                float moveCost = baseCost + turnPenalty + terrainCost;

                float tentativeG = current.gCost + moveCost;

                float roadHeight = getRoadHeight(nx, ny, heightmap);
                Node neighbor = nodeMap[nx][ny];
                if (neighbor == null || tentativeG < neighbor.gCost) {
                    float h = heuristic(nx, ny, goalX, goalY);
                    neighbor = new Node(nx, ny, roadHeight, tentativeG, tentativeG + h, current, dx, dy);
                    nodeMap[nx][ny] = neighbor;
                    openSet.add(neighbor);
                    visited[nx][ny] = true;
                }
            }
        }
        return Collections.emptyList();
    }

    private float getRoadHeight(int x, int y, float[][] terrain) {
        float sum = terrain[x][y];
        int points = 1;
        if (x + 1 < terrain.length) {
            sum += terrain[x + 1][y];
            points++;
        }
        if (x - 1 >= 0) {
            sum += terrain[x - 1][y];
            points++;
        }
        if (y + 1 < terrain[0].length) {
            sum += terrain[x][y + 1];
            points++;
        }
        if (y - 1 >= 0) {
            sum += terrain[x][y - 1];
            points++;
        }
        return sum / points;
    }

    private float getSlopePenalty(int x, int y, float[][] heightmap) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;

        float h = heightmap[x][y];
        float dx = 0f, dz = 0f;
        int count = 0;

        // Central differences for gradient estimation
        if (x > 0 && x < rows - 1) {
            dx = (heightmap[x + 1][y] - heightmap[x - 1][y]) / 2f;
            count++;
        }
        if (y > 0 && y < cols - 1) {
            dz = (heightmap[x][y + 1] - heightmap[x][y - 1]) / 2f;
            count++;
        }

        if (count == 0) return 0f;

        // Slope magnitude (rise over run in grid units)
        float slopeMag = (float) Math.sqrt(dx * dx + dz * dz);
        return slopeMag;
    }

    private float heuristic(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private List<Node> reconstructPath(Node end) {
        if (verticalExitUp) {
            currentZChunk++;
            lastXCoord = end.x;
        } else if (verticalExitDown) {
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
        return path;
    }
}