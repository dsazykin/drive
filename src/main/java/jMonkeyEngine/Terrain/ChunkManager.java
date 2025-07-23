package jMonkeyEngine.Terrain;

import com.jme3.asset.AssetManager;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import jMonkeyEngine.Main;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class ChunkManager {
    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final TerrainGenerator generator;
    private final Main main;
    private final ExecutorService executor;

    private final int chunkSize;
    private final double scale;
    private final int renderDistance;
    private final Set<String> loadingChunks = new HashSet<>();

    private final Map<String, Geometry> loadedChunks = new HashMap<>();

    public ChunkManager(Node rootNode, BulletAppState bulletAppState,
                        TerrainGenerator generator, Main main, ExecutorService executor, int chunkSize,
                        double scale, int renderDistance) {
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.generator = generator;
        this.main = main;
        this.executor = executor;
        this.chunkSize = chunkSize;
        this.scale = scale;
        this.renderDistance = renderDistance;
    }

    private String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "_" + chunkZ;
    }

    public void addChunk(int chunkX, int chunkZ, Geometry chunk) {
        String key = chunkKey(chunkX, chunkZ);
        loadedChunks.put(key, chunk);
    }

    public void updateChunks(Vector3f playerPos) {
        int playerChunkX = (int) Math.floor(playerPos.x / ((chunkSize - 1) * (scale / 4)));
        int playerChunkZ = (int) Math.floor(playerPos.z / ((chunkSize - 1) * (scale / 4)));

        Set<String> neededChunks = new HashSet<>();

        for (int dz = -renderDistance; dz <= renderDistance; dz++) {
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                String key = chunkKey(chunkX, chunkZ);
                neededChunks.add(key);

                if (!loadedChunks.containsKey(key) && !loadingChunks.contains(key)) {
                    loadingChunks.add(key);
                    executor.submit(() -> {
                        try {
                            Mesh mesh = generator.generateChunkMesh(chunkX, chunkZ, chunkSize, scale);
                            Geometry chunk = generator.createGeometry(chunkX, chunkZ, mesh);

                            main.enqueue(() -> {
                                loadedChunks.put(key, chunk);
                                loadingChunks.remove(key);
                                rootNode.attachChild(chunk);
                                bulletAppState.getPhysicsSpace().add(chunk.getControl(RigidBodyControl.class));
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        }

        // Unload chunks that are no longer needed
        loadedChunks.entrySet().removeIf(entry -> {
            if (!neededChunks.contains(entry.getKey())) {
                Geometry chunk = entry.getValue();
                chunk.removeFromParent();
                bulletAppState.getPhysicsSpace().remove(chunk);
                return true;
            }
            return false;
        });
    }

}

