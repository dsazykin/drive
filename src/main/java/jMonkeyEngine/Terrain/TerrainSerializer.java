package jMonkeyEngine.Terrain;

import jMonkeyEngine.Chunks.ChunkCoord;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TerrainSerializer {

    private static final String TERRAIN_DIR = "saved_terrain/";
    private static final String HEIGHTMAP_FILE = "heightmap.dat";
    private static final String METADATA_FILE = "metadata.dat";

    public static class TerrainData implements Serializable {
        private static final long serialVersionUID = 1L;

        public float[][] heightMap;
        public Map<ChunkCoord, ChunkData> chunks;
        public List<jMonkeyEngine.Road.Node> pathPoints;
        public int parentSize;
        public int chunkSize;
        public float scale;
        public long seed;

        public static class ChunkData implements Serializable {
            private static final long serialVersionUID = 1L;
            public int x;
            public int z;
            // Store mesh data if needed
        }
    }

    /**
     * Save generated terrain to disk
     */
    public static boolean saveTerrain(String name, float[][] heightMap,
                                     Map<ChunkCoord, ?> chunks,
                                     List<jMonkeyEngine.Road.Node> pathPoints,
                                     int parentSize, int chunkSize, float scale, long seed) {
        File dir = new File(TERRAIN_DIR + name);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            TerrainData data = new TerrainData();
            data.heightMap = heightMap;
            data.chunks = new HashMap<>();
            data.pathPoints = pathPoints;
            data.parentSize = parentSize;
            data.chunkSize = chunkSize;
            data.scale = scale;
            data.seed = seed;

            // Save chunk metadata
            for (Map.Entry<ChunkCoord, ?> entry : chunks.entrySet()) {
                TerrainData.ChunkData chunkData = new TerrainData.ChunkData();
                chunkData.x = entry.getKey().x;
                chunkData.z = entry.getKey().z;
                data.chunks.put(entry.getKey(), chunkData);
            }

            // Write to file
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(new File(dir, HEIGHTMAP_FILE)))) {
                oos.writeObject(data);
            }

            System.out.println("Terrain saved successfully to: " + dir.getAbsolutePath());
            return true;

        } catch (IOException e) {
            System.err.println("Failed to save terrain: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load terrain from disk
     */
    public static TerrainData loadTerrain(String name) {
        File file = new File(TERRAIN_DIR + name + "/" + HEIGHTMAP_FILE);

        if (!file.exists()) {
            System.out.println("No saved terrain found: " + file.getAbsolutePath());
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            TerrainData data = (TerrainData) ois.readObject();
            System.out.println("Terrain loaded successfully from: " + file.getAbsolutePath());
            return data;

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load terrain: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check if saved terrain exists
     */
    public static boolean terrainExists(String name) {
        File file = new File(TERRAIN_DIR + name + "/" + HEIGHTMAP_FILE);
        return file.exists();
    }

    /**
     * Delete saved terrain
     */
    public static boolean deleteTerrain(String name) {
        File dir = new File(TERRAIN_DIR + name);
        if (dir.exists()) {
            deleteDirectory(dir);
            return true;
        }
        return false;
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}

