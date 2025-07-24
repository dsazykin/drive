package jMonkeyEngine.Chunks;

public class ChunkCoord {
    public final int x;
    public final int z;

    public ChunkCoord(int x, int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkCoord)) return false;
        ChunkCoord other = (ChunkCoord) obj;
        return this.x == other.x && this.z == other.z;
    }

    @Override
    public int hashCode() {
        return 31 * x + z;
    }

    @Override
    public String toString() {
        return "ChunkCoord(" + x + ", " + z + ")";
    }
}