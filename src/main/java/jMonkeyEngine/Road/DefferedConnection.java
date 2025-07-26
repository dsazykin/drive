package jMonkeyEngine.Road;

import com.jme3.math.Vector2f;
import jMonkeyEngine.Chunks.ChunkCoord;
import java.util.List;

class DeferredConnection {
    ChunkCoord thisChunk;
    ChunkCoord prevChunk;
    List<Vector2f> path;
    float[][] terrain;

    public DeferredConnection(ChunkCoord fromChunk, ChunkCoord toChunk,
                              List<Vector2f> path, float[][] terrain) {
        this.thisChunk = fromChunk;
        this.prevChunk = toChunk;
        this.path = path;
        this.terrain = terrain;
    }
}
