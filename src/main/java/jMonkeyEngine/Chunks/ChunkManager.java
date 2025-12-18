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

    // Lock specifically for synchronizing road state operations
    private final Object roadLock = new Object();

    private final int CHUNK_SIZE;
    private final float SCALE;
    private final int RENDER_DISTANCE;

    // Loading sets
    Set<ChunkCoord> loadingChunks = ConcurrentHashMap.newKeySet();
    Set<ChunkCoord> loadingHeightmaps = ConcurrentHashMap.newKeySet();
    Set<ChunkCoord> loadingRoads = ConcurrentHashMap.newKeySet();

    // Asset storage
    private final ConcurrentHashMap<ChunkCoord, Geometry> loadedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, Geometry> loadedRoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, Geometry> generatedChunks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, float[][]> generatedHeightmaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, List<jMonkeyEngine.Road.Node>> generatedRoads = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, ChunkCoord> nextRoadChunkMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ChunkCoord, ChunkCoord> prevRoadChunkMap = new ConcurrentHashMap<>();

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

                // --- CASE 1: Standard Chunk Loading ---
                if (!loadedChunks.containsKey(chunk) && !loadingChunks.contains(chunk)) {
                    loadingChunks.add(chunk);
                    executor.submit(() -> {
                        try {
                            // Generate or fetch terrain
                            float[][] terrain = generateTerrain(chunk);

                            // Synchronize road generation to ensure linear path consistency
                            synchronized (roadLock) {
                                if (chunk.x == road.currentXChunk && chunk.z == road.currentZChunk) {
                                    System.out.println("Generating road for chunk: " + chunk);
                                    generateRoadData(terrain, chunk);
                                }
                            }

                            buildAndAttachChunk(terrain, chunk);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            loadingChunks.remove(chunk);
                        }
                    });
                }
                // --- CASE 2: Catch-Up (Road data arrived after chunk loaded) ---
                else if (loadedChunks.containsKey(chunk)) {
                    // Check if we have road points data BUT no road geometry visual
                    boolean hasPoints = generatedRoads.containsKey(chunk);
                    boolean hasGeometry = loadedRoads.containsKey(chunk);

                    // Also check if this is the "Active Head" of the road that needs recalculating
                    boolean isActiveHead;
                    synchronized (roadLock) {
                        isActiveHead = (chunk.x == road.currentXChunk && chunk.z == road.currentZChunk);
                    }

                    if ((hasPoints && !hasGeometry) || (isActiveHead && !loadingRoads.contains(chunk))) {
                        // Avoid duplicates
                        if (loadingRoads.contains(chunk)) continue;

                        loadingRoads.add(chunk);
                        executor.submit(() -> {
                            try {
                                float[][] terrain = generatedHeightmaps.get(chunk);
                                if (terrain == null) terrain = generateTerrain(chunk);

                                synchronized (roadLock) {
                                    // Verify active head status again inside lock
                                    if (chunk.x == road.currentXChunk && chunk.z == road.currentZChunk) {
                                        cleanupOldRoad(chunk);
                                        generateRoadData(terrain, chunk);
                                    }
                                }

                                // Build visual only (Terrain mesh is already there)
                                buildAndAttachRoadOnly(terrain, chunk);

                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                loadingRoads.remove(chunk);
                            }
                        });
                    }
                }
            }
        }

        unloadUnusedChunks(neededChunks);
    }

    // --- Helpers ---

    public float[][] generateTerrain(ChunkCoord chunk) {
        return generatedHeightmaps.computeIfAbsent(chunk, generator::generateHeightMap);
    }

    private void generateRoadData(float[][] terrain, ChunkCoord chunk) {
        Integer startX;
        Integer startZ;

        // Read shared state safely
        if (road.verticalExitUp) {
            startZ = CHUNK_SIZE - 1;
            startX = road.lastXCoord;
        } else if (road.verticalExitDown) {
            startZ = 0;
            startX = road.lastXCoord;
        } else {
            startZ = road.lastZCoord;
            startX = 0;
        }

        List<jMonkeyEngine.Road.Node> existingPoints = null;
        if (generatedRoads.containsKey(chunk)) {
            existingPoints = generatedRoads.get(chunk);
        }

        HashMap<ChunkCoord, List<jMonkeyEngine.Road.Node>> roadPointsInChunk =
                road.getRoadPointsInChunk(terrain, existingPoints, startX, startZ, 300, chunk);

        if (!roadPointsInChunk.containsKey(chunk)) {
            System.out.println("WARNING: Pathfinder returned no key for current chunk " + chunk + ". Creating empty list.");
            roadPointsInChunk.put(chunk, new ArrayList<>());
        } else {
            System.out.println("Generated " + roadPointsInChunk.get(chunk).size() + " road points for chunk " + chunk);
        }

        // SAVE ALL DATA: Current Chunk AND Neighbors
        for (Map.Entry<ChunkCoord, List<jMonkeyEngine.Road.Node>> entry : roadPointsInChunk.entrySet()) {
            ChunkCoord coord = entry.getKey();
            List<jMonkeyEngine.Road.Node> points = entry.getValue();

            // Store points so neighbor chunks can find them later
            generatedRoads.put(coord, points);

            if (!coord.equals(chunk)) {
                nextRoadChunkMap.put(chunk, coord);
                prevRoadChunkMap.put(coord, chunk);
            }

            // If this is the active chunk, update the terrain heightmap immediately
            if (coord.equals(chunk)) {
                generator.updateHeightMap(terrain, points);
            }
        }

        System.out.println("next road chunk: (" + road.currentXChunk + ", " + road.currentZChunk + ")");
    }

    private void buildAndAttachChunk(float[][] terrain, ChunkCoord chunk) {
        Mesh mesh = generator.generateChunkMesh(terrain);
        Geometry chunkGeom = generator.createGeometry(chunk, mesh);
        generatedChunks.put(chunk, chunkGeom);

        main.enqueue(() -> {
            if (!loadingChunks.contains(chunk) && !loadedChunks.containsKey(chunk)) {
                 rootNode.attachChild(chunkGeom);
                 bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

                loadedChunks.put(chunk, chunkGeom);

                // Attach Road Visuals if points exist
                attachRoadVisuals(chunk, terrain);
            }
        });
    }

    private void buildAndAttachRoadOnly(float[][] terrain, ChunkCoord chunk) {
        main.enqueue(() -> {
            // Only attach if the parent chunk is still loaded
            if (loadedChunks.containsKey(chunk)) {
                attachRoadVisuals(chunk, terrain);
            }
        });
    }

    // Must be run on Main Thread
    private void attachRoadVisuals(ChunkCoord chunk, float[][] terrain) {
        if (loadedRoads.containsKey(chunk)) {
            return; // Already has a road
        }

        List<jMonkeyEngine.Road.Node> roadPoints = generatedRoads.get(chunk);

        if (roadPoints == null) {
            System.out.println("Skipping road visual for " + chunk + ": Points list is NULL.");
            return;
        }
        if (roadPoints.isEmpty()) {
            System.out.println("Skipping road visual for " + chunk + ": Points list is EMPTY.");
            return;
        }

        // --- STEP 1: FIND GHOST NODES ---
        jMonkeyEngine.Road.Node prevGhost = null;
        jMonkeyEngine.Road.Node nextGhost = null;

        // A. Find Previous Ghost
        // Look up who connects TO us
        ChunkCoord prevChunkCoord = prevRoadChunkMap.get(chunk);
        if (prevChunkCoord != null) {
            List<jMonkeyEngine.Road.Node> prevPoints = generatedRoads.get(prevChunkCoord);
            if (prevPoints != null && !prevPoints.isEmpty()) {
                // The ghost is the LAST node of the PREVIOUS chunk
                prevGhost = prevPoints.get(prevPoints.size() - 1);
            }
        }

        // B. Find Next Ghost
        // Look up who we connect TO
        ChunkCoord nextChunkCoord = nextRoadChunkMap.get(chunk);
        if (nextChunkCoord != null) {
            List<jMonkeyEngine.Road.Node> nextPoints = generatedRoads.get(nextChunkCoord);
            if (nextPoints != null && !nextPoints.isEmpty()) {
                // The ghost is the FIRST node of the NEXT chunk
                nextGhost = nextPoints.get(0);
            }
        }

        Geometry roadGeom;
        synchronized (roadLock) {
            roadGeom = generator.generateRoadGeometry(roadPoints, chunk, terrain, prevGhost, nextGhost);

            // Only update state if generation was successful
            if (roadGeom != null) {
                road.lastRoadNode = roadPoints.get(roadPoints.size() - 1);
                road.lastChunkCoord = chunk;
                road.lastHeightmap = terrain;

                System.out.println("generated road mesh for chunk " + chunk); // SUCCESS LOG
            } else {
                System.out.println("Failed to generate road mesh for " + chunk + " (Generator returned null)");
            }
        }

        if (roadGeom != null) {
            RigidBodyControl roadPhysics = new RigidBodyControl(0f);
            roadGeom.addControl(roadPhysics);

            rootNode.attachChild(roadGeom);
            bulletAppState.getPhysicsSpace().add(roadPhysics);

            loadedRoads.put(chunk, roadGeom);
        }
    }

    private void cleanupOldRoad(ChunkCoord chunk) {
        main.enqueue(() -> {
            Geometry oldRoad = loadedRoads.remove(chunk);
            if (oldRoad != null) {
                RigidBodyControl phy = oldRoad.getControl(RigidBodyControl.class);
                if (phy != null) bulletAppState.getPhysicsSpace().remove(phy);
                oldRoad.removeFromParent();
            }
        });
    }

    private void unloadUnusedChunks(Set<ChunkCoord> neededChunks) {
        loadedChunks.entrySet().removeIf(entry -> {
            if (!neededChunks.contains(entry.getKey())) {
                ChunkCoord coord = entry.getKey();
                Geometry chunkGeom = entry.getValue();

                main.enqueue(() -> {
                    chunkGeom.removeFromParent();
                    bulletAppState.getPhysicsSpace().remove(chunkGeom);

                    Geometry roadGeom = loadedRoads.remove(coord);
                    if (roadGeom != null) {
                        RigidBodyControl roadPhy = roadGeom.getControl(RigidBodyControl.class);
                        if (roadPhy != null) bulletAppState.getPhysicsSpace().remove(roadPhy);
                        roadGeom.removeFromParent();
                    }
                });
                return true;
            }
            return false;
        });
    }

    // Accessors
    public float getHeight(int MAX_HEIGHT, int x, int z, ChunkCoord chunk) {
        float[][] heightMap = generatedHeightmaps.get(chunk);
        return (heightMap != null) ? (heightMap[x][z]) * MAX_HEIGHT : 0;
    }

    public Vector3f getCamDirection(float height) {
        List<jMonkeyEngine.Road.Node> nodes = generatedRoads.get(new ChunkCoord(0, 0));
        if (nodes == null || nodes.size() < 2) return new Vector3f(0, height, 0);

        jMonkeyEngine.Road.Node point = nodes.get(1);
        return new Vector3f(point.x * (SCALE / 16), height - 15, point.y * (SCALE / 16));
    }

    public List<jMonkeyEngine.Road.Node> getRoadPoints(ChunkCoord chunk) {
        return generatedRoads.get(chunk);
    }

    public Geometry getChunk(ChunkCoord chunk) {
        return generatedChunks.get(chunk);
    }

    public float[][] getHeightmap(ChunkCoord chunk) {
        return generatedHeightmaps.get(chunk);
    }

    public void setPrevRoadChunk(ChunkCoord from, ChunkCoord to) {
        prevRoadChunkMap.put(from, to);
    }

    public void setNextRoadChunk(ChunkCoord from, ChunkCoord to) {
        nextRoadChunkMap.put(from, to);
    }
}