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
    private final int PARENT_SIZE;
    private final float SCALE;
    private final int RENDER_DISTANCE;

    Set<ChunkCoord> loadingChunks = ConcurrentHashMap.newKeySet();
    Set<ChunkCoord> loadingHeightmaps = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkCoord, Geometry> loadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, HashMap<ChunkCoord, Geometry>> generatedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, float[][]> generatedHeightmaps = new ConcurrentHashMap<>();

    public ChunkManager(BulletAppState bulletAppState, Node rootNode, RoadGenerator road,
                        TerrainGenerator generator, SimpleApplication main, ExecutorService executor,
                        int chunkSize, int parentSize, float scale, int renderDistance) {
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.generator = generator;
        this.road = road;
        this.main = main;
        this.executor = executor;
        this.CHUNK_SIZE = chunkSize;
        this.PARENT_SIZE = parentSize;
        this.SCALE = scale;
        this.RENDER_DISTANCE = renderDistance;
    }

    public void addChunk(ChunkCoord thisChunk, HashMap<ChunkCoord, Geometry> children, float[][] heightmap) {
        for (ChunkCoord chunk : children.keySet()) {
            loadedChunks.put(chunk, children.get(chunk));
        }
        generatedChunks.put(thisChunk, children);
        generatedHeightmaps.put(thisChunk, heightmap);
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

                            ChunkCoord parent = getParentChunk(chunk);

                            if (!generatedHeightmaps.containsKey(parent) && !loadingHeightmaps.contains(parent)) {
                                loadingHeightmaps.add(parent);
                                System.out.println("generating heightmap for: " + parent);
                                generatedHeightmaps.put(parent, generator.generateHeightMap(parent));
                                loadingHeightmaps.remove(parent);
                                System.out.println("generated heightmap for: " + parent);
                            }

                            if (generatedChunks.containsKey(parent)) {
                                chunkGeom = generatedChunks.get(parent).get(chunk);
                            } else {
                                float[][] terrain = generatedHeightmaps.get(parent);
                                if (terrain == null) return;
                                if (parent.z == 0 && parent.x == road.currentXChunk) {
                                    List<jMonkeyEngine.Road.Node> pathPoints =
                                            road.getRoadPointsInChunk(terrain, 0, road.lastZCoord,
                                                                      PARENT_SIZE - 1,
                                                                      PARENT_SIZE / 2);
                                    generator.updateHeightMap(terrain, pathPoints);
                                }
                                generatedChunks.put(parent, splitIntoChildren(terrain, parent));
                                chunkGeom = generatedChunks.get(parent).get(chunk);
                            }

                            loadedChunks.put(chunk, chunkGeom);
                            loadingChunks.remove(chunk);

                            main.enqueue(() -> {
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

    private ChunkCoord getParentChunk(ChunkCoord childChunk) {
        int parentX = Math.floorDiv(childChunk.x * CHUNK_SIZE, PARENT_SIZE);
        int parentZ = Math.floorDiv(childChunk.z * CHUNK_SIZE, PARENT_SIZE);
        return new ChunkCoord(parentX, parentZ);
    }

    public HashMap<ChunkCoord, Geometry> splitIntoChildren(float[][] parentHeightmap, ChunkCoord parentCoord) {
        HashMap<ChunkCoord, Geometry> children = new HashMap<>();

        for (int cz = 0; cz < PARENT_SIZE; cz += CHUNK_SIZE) {
            for (int cx = 0; cx < PARENT_SIZE; cx += CHUNK_SIZE) {
                float[][] childHeightmap = extractChildHeightmap(parentHeightmap, cx, cz);
                Mesh mesh = generator.generateChunkMesh(childHeightmap);
                ChunkCoord childCoord = new ChunkCoord(
                        parentCoord.x * (PARENT_SIZE / CHUNK_SIZE) + (cx / CHUNK_SIZE),
                        parentCoord.z * (PARENT_SIZE / CHUNK_SIZE) + (cz / CHUNK_SIZE)
                );
                Geometry geom = generator.createGeometry(childCoord, mesh);
                children.put(childCoord, geom);
                System.out.println("generated mesh for child: " + childCoord);
            }
        }
        return children;
    }

    private float[][] extractChildHeightmap(float[][] parent, int cx, int cz) {
        float[][] child = new float[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                child[x][z] = parent[cx + x][cz + z];
            }
        }

        return child;
    }

}