package jMonkeyEngine.Road;

import java.util.*;

public class RoadGenerator {

    public int currentXChunk = 0;
    public int lastZCoord;

    private List<int[]> generateOffsets(int radius) {
        List<int[]> offsets = new ArrayList<>();
        // Only generate forward-moving offsets (dx > 0) to ensure road moves away from origin
        for (int dx = 1; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(dist - radius) < 0.5) {
                    offsets.add(new int[]{dx, dy});
                }
            }
        }
        return offsets;
    }

    public List<Node> getRoadPointsInChunk(float[][] heightmap, int startX, int startY, int goalX, int goalY) {
        int rows = heightmap.length;
        int cols = heightmap[0].length;

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        boolean[][] visited = new boolean[rows][cols];
        Node[][] nodeMap = new Node[rows][cols];

        Node start = new Node(startX, startY, getRoadHeight(startX, startY, heightmap), 0, heuristic(startX, startY, goalX, goalY),null);
        openSet.add(start);
        nodeMap[startX][startY] = start;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.x >= goalX) {
                return reconstructPath(current);
            }

            visited[current.x][current.y] = true;

            List<int[]> directions = generateOffsets(5);
            for (int[] dir : directions) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];

                if (nx >= 0 && ny >= 0 && nx < rows && ny < cols && !visited[nx][ny]) {
                    int dx = dir[0];
                    int dy = dir[1];

                    float dot = current.dxFromParent * dx + current.dyFromParent * dy;
                    float mag2 = dx * dx + dy * dy;

                    float cosAngle = dot / (current.dirMag * (float) Math.sqrt(mag2));

                    if (Double.isNaN(cosAngle)) {
                        cosAngle = 1;
                    }

                    // Allow forward movement (cosAngle check is now less restrictive since we only generate forward offsets)
                    if (cosAngle > -0.5) {
                        // Improved height following: penalize steep changes but favor following contours
                        float currentHeight = heightmap[current.x][current.y];
                        float nextHeight = heightmap[nx][ny];
                        float heightDiff = Math.abs(currentHeight - nextHeight);
                        
                        // Calculate slope direction relative to movement direction
                        float slopeGradient = nextHeight - currentHeight;
                        
                        // Heavy penalty for steep gradients (going across terrain)
                        float heightPenalty = heightDiff * heightDiff * 50000.0f;
                        
                        // Additional penalty for sudden elevation changes
                        if (heightDiff > 0.05f) {
                            heightPenalty *= 3.0f;
                        }

                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        float baseCost = distance * 10f;

                        // Penalize sharp turns to create smoother roads
                        float turnPenalty = (1 - cosAngle) * 500f;

                        float moveCost = baseCost + heightPenalty + turnPenalty;

                        float tentativeG = current.gCost + moveCost;

                        float roadHeight = getRoadHeight(nx, ny, heightmap);

                        Node neighbor = nodeMap[nx][ny];
                        if (neighbor == null || tentativeG < neighbor.gCost) {
                            int h = heuristic(nx, ny, goalX, goalY);
                            neighbor = new Node(nx, ny, roadHeight, tentativeG, tentativeG + h, current, dx, dy);
                            nodeMap[nx][ny] = neighbor;
                            openSet.add(neighbor);
                        }
                    }
                }
            }
        }

        return Collections.emptyList(); // No path found
    }

    private float getRoadHeight(int x, int y, float[][] terrain) {
        float points = 1;
        float sum = 0;

        sum += terrain[x][y];

        if (x + 1 < terrain.length) {
            sum += terrain[x + 1][y];
            points += 1;
        }

        if (x - 1 >= 0) {
            sum += terrain[x - 1][y];
            points += 1;
        }

        if (y + 1 < terrain[0].length) {
            sum += terrain[x][y + 1];
            points += 1;
        }

        if (y - 1 >= 0) {
            sum += terrain[x][y - 1];
            points += 1;
        }

        return sum / points;
    }

    private int heuristic(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    private List<Node> reconstructPath(Node end) {
        lastZCoord = end.y;
        currentXChunk += 1;

        List<Node> path = new ArrayList<>();
        Node current = end;
        while (current != null) {
            path.add(current);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
}
