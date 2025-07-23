package jMonkeyEngine.Terrain;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import jMonkeyEngine.Road.RoadGenerator;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class ChunkManager {
    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final TerrainGenerator generator;
    private final RoadGenerator road;
    private final SimpleApplication main;
    private final ExecutorService executor;

    private final int chunkSize;
    private final float scale;
    private final int renderDistance;
    private final Set<String> loadingChunks = new HashSet<>();

    private final Map<String, Geometry> loadedChunks = new HashMap<>();

    public ChunkManager(Node rootNode, BulletAppState bulletAppState,
                        TerrainGenerator generator, RoadGenerator road, SimpleApplication main, ExecutorService executor,
                        int chunkSize,
                        float scale, int renderDistance) {
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.generator = generator;
        this.road = road;
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
                            float[][] terrain = generator.generateHeightMap(chunkSize, scale, chunkX, chunkZ);
                            Mesh mesh = generator.generateChunkMesh(terrain, chunkSize, scale);
                            Geometry chunk = generator.createGeometry(chunkX, chunkZ, mesh);

                            Geometry r = road.generateStraightRoad(50,5f, 2f, terrain, 0);
                            main.enqueue(() -> {
                                loadedChunks.put(key, chunk);
                                loadingChunks.remove(key);

                                rootNode.attachChild(chunk);
                                bulletAppState.getPhysicsSpace().add(chunk.getControl(RigidBodyControl.class));

                                r.setLocalTranslation(chunkX * (chunkSize - 1) * scale, 0, chunkZ * (chunkSize - 1) * scale);
                                rootNode.attachChild(r);
                                bulletAppState.getPhysicsSpace().add(r.getControl(RigidBodyControl.class));
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

