package jMonkeyEngine.Chunks;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import jMonkeyEngine.Road.RoadGenerator;
import jMonkeyEngine.Terrain.TerrainGenerator;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class ChunkManager {
    private final Node rootNode;
    private final BulletAppState bulletAppState;
    private final TerrainGenerator generator;
    private final RoadGenerator road;
    private final SimpleApplication main;
    private final ExecutorService executor;

    private final int CHUNK_SIZE;
    private final float SCALE;
    private final int RENDER_DISTANCE;

    Set<ChunkCoord> loadingChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkCoord, Geometry> loadedChunks = new HashMap<>();
    private final ConcurrentHashMap<ChunkCoord, Geometry> generatedChunks = new ConcurrentHashMap<>();

    public ChunkManager(BulletAppState bulletAppState, Node rootNode, RoadGenerator road,
                        TerrainGenerator generator, SimpleApplication main, ExecutorService executor,
                        int chunkSize, float scale, int renderDistance) {
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.generator = generator;
        this.road = road;
        this.main = main;
        this.executor = executor;
        this.CHUNK_SIZE = chunkSize;
        this.SCALE = scale;
        this.RENDER_DISTANCE = renderDistance;
    }

    public void addChunk(ChunkCoord thisChunk, Geometry chunk) {
        loadedChunks.put(thisChunk, chunk);
        generatedChunks.put(thisChunk, chunk);
    }

    public void updateChunks(Vector3f playerPos) {
        int playerChunkX = (int) Math.floor(playerPos.x / ((CHUNK_SIZE - 1) * (SCALE / 16)));
        int playerChunkZ = (int) Math.floor(playerPos.z / ((CHUNK_SIZE - 1) * (SCALE / 16)));

        Set<ChunkCoord> neededChunks = new HashSet<>();

        for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
            for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                final ChunkCoord chunk = new ChunkCoord(chunkX, chunkZ);

                neededChunks.add(chunk);

                if (!loadedChunks.containsKey(chunk) && !loadingChunks.contains(chunk)) {
                    loadingChunks.add(chunk);
                    executor.submit(() -> {
                        try {
                            Geometry chunkGeom;

                            if (generatedChunks.containsKey(chunk)) {
                                chunkGeom = generatedChunks.get(chunk);
                            } else {
                                float[][] terrain = generator.generateHeightMap(chunk);
                                if (chunkZ == 0 && chunkX == road.currentXChunk) {
                                    List<jMonkeyEngine.Road.Node> pathPoints =
                                            road.getRoadPointsInChunk(terrain, 0, road.lastZCoord,
                                                                      CHUNK_SIZE - 1,
                                                                      CHUNK_SIZE / 2);
                                    generator.updateHeightMap(terrain, chunk, pathPoints);
                                }

                                Mesh mesh = generator.generateChunkMesh(terrain);
                                chunkGeom = generator.createGeometry(chunk, mesh);

                                generatedChunks.put(chunk, chunkGeom);
                            }

                            main.enqueue(() -> {
                                loadedChunks.put(chunk, chunkGeom);
                                loadingChunks.remove(chunk);

                                rootNode.attachChild(chunkGeom);
                                bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

                            });
                        } catch (Exception e) {
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