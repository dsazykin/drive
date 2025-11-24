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
import java.io.IOException;
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
    private final ConcurrentHashMap<ChunkCoord, ConcurrentHashMap<ChunkCoord, Geometry>> generatedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, float[][]> generatedHeightmaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, List<jMonkeyEngine.Road.Node>> generatedRoads =
            new ConcurrentHashMap<>();

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

    public void addChunk(ChunkCoord thisChunk, ConcurrentHashMap<ChunkCoord, Geometry> children,
                         float[][] heightmap, List<jMonkeyEngine.Road.Node> nodes) {
        for (ChunkCoord chunk : children.keySet()) {
            loadedChunks.put(chunk, children.get(chunk));
        }
        generatedChunks.put(thisChunk, children);
        generatedHeightmaps.put(thisChunk, heightmap);
        generatedRoads.put(thisChunk, nodes);
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
                                float[][] terrain = generator.generateHeightMap(parent);

                                // Generate road if this parent chunk is on the road's path
                                // Road progresses along X-axis but can move between Z-chunks
                                if (parent.x == road.currentXChunk && parent.z == road.currentZChunk) {
                                    int startZ = road.lastZCoord;

                                    // Check if we're entering from a previous Z-chunk
                                    ChunkCoord prevZChunk = new ChunkCoord(parent.x, parent.z - 1);
                                    ChunkCoord nextZChunk = new ChunkCoord(parent.x, parent.z + 1);

                                    // If previous Z-chunk exists and has a road, we're entering from south
                                    if (generatedRoads.containsKey(prevZChunk)) {
                                        List<jMonkeyEngine.Road.Node> prevRoad = generatedRoads.get(prevZChunk);
                                        if (!prevRoad.isEmpty()) {
                                            jMonkeyEngine.Road.Node lastNode = prevRoad.get(prevRoad.size() - 1);
                                            // Road is crossing from previous Z-chunk, start at bottom with same Z-coord
                                            startZ = 0;
                                        }
                                    } else if (generatedRoads.containsKey(nextZChunk)) {
                                        // Coming back from next Z-chunk (edge case, but handle it)
                                        List<jMonkeyEngine.Road.Node> nextRoad = generatedRoads.get(nextZChunk);
                                        if (!nextRoad.isEmpty()) {
                                            jMonkeyEngine.Road.Node firstNode = nextRoad.get(0);
                                            startZ = PARENT_SIZE - 1;
                                        }
                                    }

                                    List<jMonkeyEngine.Road.Node> pathPoints =
                                            road.getRoadPointsInChunk(terrain, 0, startZ,
                                                                      PARENT_SIZE - 1,
                                                                      PARENT_SIZE / 2);
                                    generator.updateHeightMap(terrain, pathPoints);
                                    generatedRoads.put(parent, pathPoints);

                                    // Update Z-chunk position if road crossed a Z boundary
                                    jMonkeyEngine.Road.Node lastNode = pathPoints.get(pathPoints.size() - 1);
                                    if (lastNode.y >= PARENT_SIZE - 1) {
                                        // Road exited through north boundary, move to next Z-chunk
                                        road.currentZChunk += 1;
                                    } else if (lastNode.y <= 0) {
                                        // Road exited through south boundary, move to previous Z-chunk
                                        road.currentZChunk -= 1;
                                    }
                                }

                                generatedHeightmaps.put(parent, terrain);
                                loadingHeightmaps.remove(parent);
                            }

                            ConcurrentHashMap<ChunkCoord, Geometry> children = new ConcurrentHashMap<>();
                            if (generatedChunks.containsKey(parent)) {
                                children = generatedChunks.get(parent);
                            }

                            if (children.containsKey(chunk)) {
                                chunkGeom = children.get(chunk);
                            } else {
                                float[][] terrain = generatedHeightmaps.get(parent);
                                if (terrain == null) {
                                    loadingChunks.remove(chunk);
                                    return;
                                }
                                chunkGeom = getChild(terrain, parent, chunk);

                                if (generatedChunks.containsKey(parent)) {
                                    generatedChunks.get(parent).put(chunk, chunkGeom);
                                } else {
                                    children.put(chunk, chunkGeom);
                                    generatedChunks.put(parent, children);
                                }

                            }

                            loadedChunks.put(chunk, chunkGeom);
                            loadingChunks.remove(chunk);

                            main.enqueue(() -> {
                                rootNode.attachChild(chunkGeom);
                                bulletAppState.getPhysicsSpace().add(
                                        chunkGeom.getControl(RigidBodyControl.class));

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

    public Geometry getChild(float[][] parentHeightmap, ChunkCoord parentCoord, ChunkCoord childCoord) {
        int localChildX = childCoord.x - parentCoord.x * (PARENT_SIZE / CHUNK_SIZE);
        int localChildZ = childCoord.z - parentCoord.z * (PARENT_SIZE / CHUNK_SIZE);

        int cx = localChildX * CHUNK_SIZE;
        int cz = localChildZ * CHUNK_SIZE;

        Mesh mesh = generator.generateChunkMesh(parentHeightmap, cx, cz);

        return generator.createGeometry(childCoord, mesh);
    }

    public float getHeight(int MAX_HEIGHT, int x, int z, ChunkCoord chunk) {
        float[][] heightMap = generatedHeightmaps.get(chunk);
        return (heightMap[x][z] - 2) * MAX_HEIGHT;
    }

    public Vector3f getCamDirection(float height) {
        List<jMonkeyEngine.Road.Node> nodes = generatedRoads.get(new ChunkCoord(0, 0));
        jMonkeyEngine.Road.Node point = nodes.get(40);
        System.out.println(point.x * (SCALE / 16));
        System.out.println(point.y * (SCALE / 16));
        return new Vector3f(point.x * (SCALE / 16), height - 15,
                            point.y * (SCALE / 16));
    }

    public List<jMonkeyEngine.Road.Node> getRoadPoints(ChunkCoord chunk) {
        return generatedRoads.get(chunk);
    }

    public ConcurrentHashMap<ChunkCoord, Geometry> getChunk(ChunkCoord chunk) {
        return generatedChunks.get(chunk);
    }
}