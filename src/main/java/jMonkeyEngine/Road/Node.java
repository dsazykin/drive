package jMonkeyEngine.Road;

public class Node implements Comparable<Node> {
    public int x;
    public int y;
    public float height;
    public float gCost; // cost from start
    public float fCost; // gCost + heuristic
    public Node parent;
    public int dxFromParent, dyFromParent;
    public float dirMag;

    public Node(int x, int y) {
        this(x, y, 0, 0, 0, null, 0, 0);
    }

    public Node(int x, int y, float height, float gCost, float fCost, Node parent) {
        this(x, y, height, gCost, fCost, parent, 0, 0);
    }

    public Node(int x, int y, float height, float gCost, float fCost, Node parent, int dx,
                int dy) {
        this.x = x;
        this.y = y;
        this.height = height;
        this.gCost = gCost;
        this.fCost = fCost;
        this.parent = parent;
        this.dxFromParent = dx;
        this.dyFromParent = dy;
        this.dirMag = (float) Math.sqrt(dxFromParent * dxFromParent + dyFromParent * dyFromParent);
    }

    @Override
    public int compareTo(Node other) {
        return Float.compare(this.fCost, other.fCost);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Node) {
            Node o = (Node) obj;
            return this.x == o.x && this.y == o.y;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return x * 31 + y;
    }

    @Override
    public String toString() {
        return "Node(" + x + ", " + y + ")";
    }
}