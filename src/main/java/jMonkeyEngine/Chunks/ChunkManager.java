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
    private final float SCALE;
    private final int RENDER_DISTANCE;

    Set<ChunkCoord> loadingChunks = ConcurrentHashMap.newKeySet();
    Set<ChunkCoord> loadingHeightmaps = ConcurrentHashMap.newKeySet();
    Set<ChunkCoord> loadingRoads = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<ChunkCoord, Geometry> loadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, Geometry> loadedRoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, Geometry> generatedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, float[][]> generatedHeightmaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, List<jMonkeyEngine.Road.Node>> generatedRoads =
            new ConcurrentHashMap<>();

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

    public void addChunk(ChunkCoord thisChunk, Geometry geometry,
                         float[][] heightmap, List<jMonkeyEngine.Road.Node> nodes) {
        generatedChunks.put(thisChunk, geometry);
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
                            if (!generatedHeightmaps.containsKey(chunk) && !loadingHeightmaps.contains(chunk)) {
                                generateTerrain(chunk);
                            }
                            float[][] terrain = generatedHeightmaps.get(chunk);

                            if (terrain == null) {
                                generateTerrain(chunk);
                            }

                            // Generate road if this parent chunk is on the road's path
                            if (chunk.x == road.currentXChunk && chunk.z == road.currentZChunk) {
                                generateRoad(terrain, chunk);
                            }

                            addChunk(terrain, chunk);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            loadingChunks.remove(chunk);
                        }
                    });
                } else if (loadedChunks.containsKey(chunk)) {
                    executor.submit(() -> {
                        try {
                            float[][] terrain = generatedHeightmaps.get(chunk);

                            if (terrain == null) {
                                generateTerrain(chunk);
                            }

                            // Generate road if this parent chunk is on the road's path
                            if (chunk.x == road.currentXChunk && chunk.z == road.currentZChunk && !loadingRoads.contains(chunk)) {
                                loadingRoads.add(chunk);

                                Geometry geometry = generatedChunks.get(chunk);
                                Geometry oldRoadGeom = loadedRoads.get(chunk);

                                main.enqueue(() -> {
                                    geometry.removeFromParent();
                                    bulletAppState.getPhysicsSpace().remove(geometry);

                                    // Remove old road physics
                                    if (oldRoadGeom != null) {
                                        RigidBodyControl oldRoadPhysics = oldRoadGeom.getControl(RigidBodyControl.class);
                                        if (oldRoadPhysics != null) {
                                            bulletAppState.getPhysicsSpace().remove(oldRoadPhysics);
                                        }
                                        oldRoadGeom.removeFromParent();
                                    }
                                });

                                generateRoad(terrain, chunk);
                                addChunk(terrain, chunk);
                                loadingRoads.remove(chunk);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            loadingRoads.remove(chunk);
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

                // Also remove road geometry and physics if it exists
                Geometry roadGeom = loadedRoads.remove(entry.getKey());
                if (roadGeom != null) {
                    RigidBodyControl roadPhysics = roadGeom.getControl(RigidBodyControl.class);
                    if (roadPhysics != null) {
                        bulletAppState.getPhysicsSpace().remove(roadPhysics);
                    }
                    roadGeom.removeFromParent();
                }

                return true;
            }
            return false;
        });
    }

    private void addChunk(float[][] terrain, ChunkCoord chunk) {
        Mesh mesh = generator.generateChunkMesh(terrain);
        Geometry chunkGeom = generator.createGeometry(chunk, mesh);

        loadedChunks.put(chunk, chunkGeom);
        loadingChunks.remove(chunk);

        main.enqueue(() -> {
            rootNode.attachChild(chunkGeom);
            bulletAppState.getPhysicsSpace().add(
                    chunkGeom.getControl(RigidBodyControl.class));

            // Add road geometry if it exists for this chunk
            List<jMonkeyEngine.Road.Node> roadPoints = generatedRoads.get(chunk);
            if (roadPoints != null && !roadPoints.isEmpty()) {
                Geometry roadGeom = generator.generateRoadGeometry(roadPoints, chunk, terrain);
                if (roadGeom != null) {
                    // Add physics to the road
                    RigidBodyControl roadPhysics = new RigidBodyControl(0f); // 0f = static (immovable)
                    roadGeom.addControl(roadPhysics);

                    loadedRoads.put(chunk, roadGeom);
                    rootNode.attachChild(roadGeom);
                    bulletAppState.getPhysicsSpace().add(roadPhysics);
                }
            }
        });
    }

    private void generateTerrain(ChunkCoord chunk) throws IOException {
        loadingHeightmaps.add(chunk);
        float[][] terrain = generator.generateHeightMap(chunk);

        generatedHeightmaps.put(chunk, terrain);
        loadingHeightmaps.remove(chunk);
    }

    private void generateRoad(float[][] terrain, ChunkCoord chunk) {
        Integer startX;
        Integer startZ;
        if (road.verticalExitUp) {
            startZ = 0;
            startX = road.lastXCoord;
        } else if (road.verticalExitDown) {
            startZ = CHUNK_SIZE - 1;
            startX = road.lastXCoord;
        } else {
            startZ = road.lastZCoord;
            startX = 0;
        }
        List<jMonkeyEngine.Road.Node> pathPoints = road.getRoadPointsInChunk(terrain, startX,
                                                                             startZ, CHUNK_SIZE - 1,
                                                                             CHUNK_SIZE / 2);

        generator.updateHeightMap(terrain, pathPoints);
        generatedRoads.put(chunk, pathPoints);
    }

    public float getHeight(int MAX_HEIGHT, int x, int z, ChunkCoord chunk) {
        float[][] heightMap = generatedHeightmaps.get(chunk);
        return (heightMap[x][z]) * MAX_HEIGHT;
    }

    public Vector3f getCamDirection(float height) {
        List<jMonkeyEngine.Road.Node> nodes = generatedRoads.get(new ChunkCoord(0, 0));
        jMonkeyEngine.Road.Node point = nodes.get(1);
        System.out.println(point.x * (SCALE / 16));
        System.out.println(point.y * (SCALE / 16));
        return new Vector3f(point.x * (SCALE / 16), height - 15,
                            point.y * (SCALE / 16));
    }

    public List<jMonkeyEngine.Road.Node> getRoadPoints(ChunkCoord chunk) {
        return generatedRoads.get(chunk);
    }

    public Geometry getChunk(ChunkCoord chunk) {
        return generatedChunks.get(chunk);
    }
}