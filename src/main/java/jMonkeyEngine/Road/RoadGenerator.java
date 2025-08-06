package jMonkeyEngine.Road;

import java.util.*;

public class RoadGenerator {

    public int currentXChunk = 0;
    public int lastZCoord;

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

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        boolean[][] visited = new boolean[rows][cols];
        Node[][] nodeMap = new Node[rows][cols];

        Node start = new Node(startX, startY, 0, heuristic(startX, startY, goalX, goalY),null);
        openSet.add(start);
        nodeMap[startX][startY] = start;

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.x == goalX) {
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

                    if (cosAngle > 0) {
                        float heightWeight = 10000.0f * (rows * 2);

                        float heightDiff = Math.abs(heightmap[current.x][current.y] - heightmap[nx][ny]);

                        float distance = (float) Math.sqrt(dx * dx + dy * dy);
                        float baseCost = distance * 10f;

                        float moveCost = baseCost + (heightWeight * heightDiff);

                        //                        float angleCos = (mag1 == 0 || mag2 == 0) ? 1 : dot / (mag1 * mag2);
                        //                        float anglePenalty = (1 - angleCos) * -10; // more penalty for sharper turns

                        float tentativeG = current.gCost + moveCost;

                        Node neighbor = nodeMap[nx][ny];
                        if (neighbor == null || tentativeG < neighbor.gCost) {
                            int h = heuristic(nx, ny, goalX, goalY);
                            neighbor = new Node(nx, ny, tentativeG, tentativeG + h, current, dx, dy);
                            nodeMap[nx][ny] = neighbor;
                            openSet.add(neighbor);
                        }
                    }
                }
            }
        }

        return Collections.emptyList(); // No path found
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
