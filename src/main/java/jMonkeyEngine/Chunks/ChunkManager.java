package jMonkeyEngine.Chunks;

import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.math.Vector2f;
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

    private final int chunkSize;
    private final float scale;
    private final int renderDistance;
    private final int MAX_HEIGHT;

    Set<ChunkCoord> loadingChunks = ConcurrentHashMap.newKeySet();
    private final Map<ChunkCoord, Geometry> loadedChunks = new HashMap<>();
    private final HashMap<ChunkCoord, List<Geometry>> loadedRoads = new HashMap<>();

    public ChunkManager(BulletAppState bulletAppState, Node rootNode, RoadGenerator road,
                        TerrainGenerator generator, SimpleApplication main, ExecutorService executor,
                        int chunkSize,
                        float scale, int renderDistance, int maxHeight) {
        this.rootNode = rootNode;
        this.bulletAppState = bulletAppState;
        this.generator = generator;
        this.road = road;
        this.main = main;
        this.executor = executor;
        this.chunkSize = chunkSize;
        this.scale = scale;
        this.renderDistance = renderDistance;
        MAX_HEIGHT = maxHeight;
    }

    public void addChunk(ChunkCoord thisChunk, Geometry chunk) {
        loadedChunks.put(thisChunk, chunk);
    }

    public void addRoad(ChunkCoord chunk, List<Geometry> roads) {
        loadedRoads.put(chunk, roads);
    }

    public void updateChunks(Vector3f playerPos) {
        int playerChunkX = (int) Math.floor(playerPos.x / ((chunkSize - 1) * (scale / 8)));
        int playerChunkZ = (int) Math.floor(playerPos.z / ((chunkSize - 1) * (scale / 8)));

        Set<ChunkCoord> neededChunks = new HashSet<>();

        for (int dz = -renderDistance; dz <= renderDistance; dz++) {
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                final ChunkCoord chunk = new ChunkCoord(chunkX, chunkZ);

                neededChunks.add(chunk);

                if (!loadedChunks.containsKey(chunk) && !loadingChunks.contains(chunk)) {
                    loadingChunks.add(chunk);
                    executor.submit(() -> {
                        try {
                            float[][] terrain = generator.generateHeightMap(chunkSize, scale, chunk);

                            List<Geometry> roads = road.buildRoad(chunk, terrain);

                            Mesh mesh = generator.generateChunkMesh(terrain, chunkSize, scale);
                            Geometry chunkGeom = generator.createGeometry(chunk, mesh);

//                            Geometry r;
//                            if (chunkX == 0) {
//                                int zOffSet = (int) (chunkZ * ((chunkSize - 1) * (scale / 4)));
//                                r = road.generateStraightRoad(50, 10f, 10f, terrain, zOffSet);
//                                System.out.println("generated road");
//                            } else {
//                                r = null;
//                            }

                            main.enqueue(() -> {
                                loadedChunks.put(chunk, chunkGeom);
                                loadingChunks.remove(chunk);

                                rootNode.attachChild(chunkGeom);
                                bulletAppState.getPhysicsSpace().add(chunkGeom.getControl(RigidBodyControl.class));

                                if (roads != null) {
                                    for (Geometry r : roads) {
                                        rootNode.attachChild(r);
                                        bulletAppState.getPhysicsSpace()
                                                .add(r.getControl(RigidBodyControl.class));
                                    }
                                    loadedRoads.put(chunk, roads);
                                }

//                                if (chunkX == 0) {
//                                    rootNode.attachChild(r);
//                                    bulletAppState.getPhysicsSpace()
//                                            .add(r.getControl(RigidBodyControl.class));
//                                    System.out.println("Attaching road at Z = " + (chunkZ * chunkSize * (scale / 4f)));
//                                }
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

                List<Geometry> roads = loadedRoads.remove(entry.getKey());
                if (roads != null) {
                    for (Geometry road : roads) {
                        road.removeFromParent();
                        bulletAppState.getPhysicsSpace().remove(road);
                    }
                }
                return true;
            }
            return false;
        });
    }

}

//    public void updateChunks(Vector3f playerPos) {
//        int playerChunkX = (int) Math.floor(playerPos.x / ((chunkSize - 1) * (scale / 4)));
//        int playerChunkZ = (int) Math.floor(playerPos.z / ((chunkSize - 1) * (scale / 4)));
//
//        Set<ChunkCoord> neededChunks = new HashSet<>();
//
//        for (int dz = -renderDistance; dz <= renderDistance; dz++) {
//            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
//                int chunkX = playerChunkX + dx;
//                int chunkZ = playerChunkZ + dz;
//                final ChunkCoord chunk = new ChunkCoord(chunkX, chunkZ);
//
//                neededChunks.add(chunk);
//            }
//        }
//
//        // Unload chunks that are no longer needed
//        try {
//            main.enqueue(() -> {
//                try {
//                    loadedChunks.entrySet().removeIf(entry -> {
//                        ChunkCoord chunkCoord = entry.getKey();
//                        if (!neededChunks.contains(chunkCoord)) {
//                            Geometry chunk = entry.getValue();
//                            chunk.removeFromParent();
//                            bulletAppState.getPhysicsSpace().remove(chunk);
//
//                            System.out.println(neededChunks);
//                            System.out.println(loadedChunks);
//                            System.out.println("Unloading chunk " + chunkCoord);
//                            System.out.println("Still in rootNode? " + rootNode.hasChild(chunk));
//                            System.out.println("Chunk parent: " + chunk.getParent());
//                            System.out.println("Physics has control? " + bulletAppState.getPhysicsSpace().getRigidBodyList().contains(chunk.getControl(RigidBodyControl.class)));
//
//                            return true;
//                        }
//                        return false;
//                    });
//                    currentNeededChunks.clear();
//                    currentNeededChunks.addAll(neededChunks);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//        for (ChunkCoord chunk : neededChunks) {
//            if (!loadedChunks.containsKey(chunk) && !loadingChunks.contains(chunk)) {
//                loadingChunks.add(chunk);
//                System.out.println("Submitting load for chunk " + chunk);
//                executor.submit(() -> {
//                    try {
//                        float[][] terrain = generator.generateHeightMap(chunkSize, scale, chunk);
//
//                        System.out.println("Successfully generated terrain for chunk: " + chunk);
//
//                        Mesh mesh = generator.generateChunkMesh(terrain, chunkSize, scale);
//                        System.out.println("Successfully generated mesh for chunk: " + chunk);
//
//                        Geometry chunkGeom = generator.createGeometry(chunk, mesh);
//                        System.out.println("Successfully generated geometry for chunk: " + chunk);
//
//                        main.enqueue(() -> {
//                            try {
//                                List<Geometry> roads = road.buildRoad(chunk, terrain);
//                                ;
//                                System.out.println(
//                                        "Successfully generated roads for chunk: " + chunk);
//
//
//                                if (!currentNeededChunks.contains(chunk)) {
//                                    System.out.println("Chunk " + chunk + " is no longer needed. Skipping load.");
//                                    return;
//                                }
//
//                                System.out.println("Loading chunk " + chunk);
//
//                                loadedChunks.put(chunk, chunkGeom);
//                                rootNode.attachChild(chunkGeom);
//                                bulletAppState.getPhysicsSpace()
//                                        .add(chunkGeom.getControl(RigidBodyControl.class));
//
//                                if (roads != null) {
//                                    for (Geometry r : roads) {
//                                        rootNode.attachChild(r);
//                                        bulletAppState.getPhysicsSpace()
//                                                .add(r.getControl(RigidBodyControl.class));
//                                    }
//                                }
//                            } catch (Exception e) {
//                                e.printStackTrace();
//                            }
//                        });
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    } finally {
//                        loadingChunks.remove(chunk); // ← moved here so it's ALWAYS cleared
//                    }
//                });
//            } //else {
//            //                if (loadingChunks.contains(chunk)) {
//            //                    System.out.println("Chunk " + chunk + " already marked as loading");
//            //                }
//            //                if (loadedChunks.containsKey(chunk)) {
//            //                    System.out.println("Chunk " + chunk + " already loaded");
//            //                }
//            //            }
//
//        }
//    }
//
//}