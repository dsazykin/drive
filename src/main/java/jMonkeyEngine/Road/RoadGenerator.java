package jMonkeyEngine.Road;

import java.util.*;

public class RoadGenerator {

    public int currentXChunk = 0;
    public int currentZChunk = 0;
    public Integer lastZCoord = null;
    public Integer lastXCoord = null;
    public boolean verticalExitUp = false;
    public boolean verticalExitDown = false;

    private List<int[]> generateOffsets(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                if (dx == 0 && dy == 0) continue;
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

        verticalExitUp = false;
        verticalExitDown = false;

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        boolean[][] visited = new boolean[rows][cols];
        Node[][] nodeMap = new Node[rows][cols];

        Node start = new Node(startX, startY, getRoadHeight(startX, startY, heightmap), 0, heuristic(startX, startY, goalX, goalY),null);
        openSet.add(start);
        nodeMap[startX][startY] = start;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            // Primary goal: reach the far X boundary (main direction)
            if (current.x >= goalX) {
                return reconstructPath(current);
            }

            // Allow exit through Z-boundaries if the path leads there
            if (current.y >= cols - 1) {
                verticalExitUp = true;
                return reconstructPath(current);
            } else if (current.y <= 0) {
                verticalExitDown = true;
                return reconstructPath(current);
            }

            //visited[current.x][current.y] = true;

            List<int[]> directions = generateOffsets(3);
            for (int[] dir : directions) {
                int nx = current.x + dir[0];
                int ny = current.y + dir[1];

                if (nx >= 0 && ny >= 0 && nx < rows && ny < cols && !visited[nx][ny]) {
                    int dx = dir[0];
                    int dy = dir[1];

                    // Discourage backward movement in X (we want to progress forward)
                    if (dx < 0) continue;

                    float dot = current.dxFromParent * dx + current.dyFromParent * dy;
                    float mag2 = dx * dx + dy * dy;

                    float cosAngle = dot / (current.dirMag * (float) Math.sqrt(mag2));

                    if (Double.isNaN(cosAngle)) {
                        cosAngle = 1;
                    }

                    if (cosAngle > 0) {
                        float heightWeight = 10000.0f * (rows * 2);

                        float heightDiff = Math.abs(heightmap[current.x][current.y] - heightmap[nx][ny]);

                        float distance = (float) Math.sqrt(dx * dx + dy * dy);

                        // Add bonus for X-progression (negative cost = preferred)
                        // This encourages the road to move along X-axis
                        float xProgressBonus = dx > 0 ? -30f * dx : 0;
                        float baseCost = distance * 10f + xProgressBonus;

                        float moveCost = baseCost + (heightWeight * heightDiff);

                        //                        float angleCos = (mag1 == 0 || mag2 == 0) ? 1 : dot / (mag1 * mag2);
                        //                        float anglePenalty = (1 - angleCos) * -10; // more penalty for sharper turns

                        float tentativeG = current.gCost + moveCost;

                        float roadHeight = getRoadHeight(nx, ny, heightmap);

                        Node neighbor = nodeMap[nx][ny];
                        if (neighbor == null || tentativeG < neighbor.gCost) {
                            float h = heuristic(nx, ny, goalX, goalY);
                            neighbor = new Node(nx, ny, roadHeight, tentativeG, tentativeG + h, current, dx, dy);
                            nodeMap[nx][ny] = neighbor;
                            openSet.add(neighbor);
                            visited[nx][ny] = true; // Mark as visited when adding to queue
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

        // Check if the road crossed into a different Z-chunk
        // Assuming PARENT_SIZE is accessible or passed in, we need to detect boundary crossing
        // If end.y is near 0, we entered from the south (previous Z-chunk was currentZChunk - 1)
        // If end.y is near PARENT_SIZE-1, we're exiting to the north (next Z-chunk will be currentZChunk + 1)
        // For now, we'll update this based on where we exited

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
