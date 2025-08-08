package jMonkeyEngine.Terrain;

import com.jme3.math.FastMath;
import jMonkeyEngine.Road.Node;
import jMonkeyEngine.Road.RoadGenerator;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

public class HeightMapGenerator {

    private final long SEED;
    private final int CHUNK_SIZE;
    private final double SCALE;

    public HeightMapGenerator(long seed, int chunkSize, double scale) {
        SEED = seed;
        CHUNK_SIZE = chunkSize;
        SCALE = scale;
    }

    public float[][] generateHeightmap(int chunkX, int chunkZ) {
        float[][] heightmap = new float[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                double worldX = (chunkX * (CHUNK_SIZE - 1) + x) / SCALE;
                double worldY = (chunkZ * (CHUNK_SIZE - 1) + y) / SCALE;

                // === Terrain noise ===
                float e = 40f * OpenSimplex2.noise2(SEED, 0.05f * worldX, 0.05f * worldY) +
                        6f * OpenSimplex2.noise2(SEED, 0.25f * worldX, 0.25f * worldY) +
                        0.9f * OpenSimplex2.noise2(SEED, 0.5f * worldX, 0.5f * worldY) +
                        0.6f * OpenSimplex2.noise2(SEED, 0.75f * worldX, 0.75f * worldY);
                e = e / (40f + 6f + 0.9f + 0.6f);
                e = (e + 1f) / 2f;
                e = FastMath.pow(e, 0.8f);
                float terrainHeight = e;

                heightmap[x][y] = terrainHeight;

            }
        }

        return heightmap;
    }

    public void applyRoadFlattening(float[][] heightmap, List<Node> roadPath) {
        float roadWidth = 6f;
        float halfWidth = roadWidth / 2f;

        float[][] targetHeights = new float[heightmap.length][heightmap[0].length];
        boolean[][] hasTarget = new boolean[heightmap.length][heightmap[0].length];

        float prevHeight = Float.MAX_VALUE;

        for (int i = 0; i < roadPath.size() - 1; i++) {
            Node a = roadPath.get(i);
            Node b = roadPath.get(i + 1);

            float dx = b.x - a.x;
            float dz = b.y - a.y;
            float segLength = (float) Math.sqrt(dx * dx + dz * dz);
            dx /= segLength;
            dz /= segLength;

            float px = -dz;
            float pz = dx;

            for (float t = 0; t <= segLength; t += 0.5f) {
                float cx = a.x + dx * t;
                float cz = a.y + dz * t;

                float lx = cx + px * halfWidth;
                float lz = cz + pz * halfWidth;
                float rx = cx - px * halfWidth;
                float rz = cz - pz * halfWidth;

                float count = 0;
                float leftH = sampleHeight(heightmap, lx, lz);
                if (leftH != 0) count++;
                float rightH = sampleHeight(heightmap, rx, rz);
                if (rightH != 0) count++;
                float currHeight = (leftH + rightH) / count + 2;

                if (i > 0 || t > 0) {
                    currHeight = prevHeight * 0.7f + currHeight * 0.3f;
                }

                prevHeight = currHeight;
                float targetHeight = currHeight;

                for (float offset = -halfWidth; offset <= halfWidth; offset += 1f) {
                    float ix = Math.round(cx + px * offset);
                    float iz = Math.round(cz + pz * offset);

                    int x = Math.round(ix);
                    int z = Math.round(iz);
                    if (x < 0 || z < 0 || x >= heightmap.length || z >= heightmap[0].length) continue;
                    if (hasTarget[x][z]) continue;

                    targetHeights[x][z] = targetHeight;
                    hasTarget[x][z] = true;
                }
            }
        }

        for (int x = 0; x < heightmap.length; x++) {
            for (int z = 0; z < heightmap[0].length; z++) {
                if (hasTarget[x][z]) {
                    heightmap[x][z] = targetHeights[x][z];
                }
            }
        }
    }

    private float sampleHeight(float[][] map, float x, float z) {
        int ix = Math.round(x);
        int iz = Math.round(z);
        if (ix < 0 || iz < 0 || ix >= map.length || iz >= map[0].length) return 0;
        return map[ix][iz];
    }

    private void setHeight(float[][] map, float x, float z, float height) {
        int ix = Math.round(x);
        int iz = Math.round(z);
        if (ix < 0 || iz < 0 || ix >= map.length || iz >= map[0].length) return;
        map[ix][iz] = height;
    }

    public void generateImage(int chunkX, int chunkZ, float[][] heightmap) throws IOException {
        BufferedImage image = new BufferedImage(CHUNK_SIZE, CHUNK_SIZE, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_SIZE; y++) {
                double noiseValue = heightmap[x][y];
                int rgb;
                if (noiseValue < 0.1)
                    rgb = new Color(0, 0, 255).getRGB();
                else if (noiseValue < 0.2)
                    rgb = new Color(211, 169, 108).getRGB();
                else if (noiseValue < 0.3)
                    rgb = new Color(34, 175, 34).getRGB();
                else if (noiseValue < 0.4)
                    rgb = new Color(34, 125, 34).getRGB();
                else if (noiseValue < 0.5)
                    rgb = new Color(34, 100, 25).getRGB();
                else if (noiseValue < 0.6)
                    rgb = new Color(75, 80, 30).getRGB();
                else if (noiseValue < 0.7)
                    rgb = new Color(90, 75, 20).getRGB();
                else if (noiseValue < 0.8)
                    rgb = new Color(110, 70, 20).getRGB();
                else if (noiseValue < 0.9)
                    rgb = new Color(139, 69, 19).getRGB();
                else
                    rgb = new Color(255, 255, 255).getRGB();

                if (noiseValue > 2f) {
                    rgb = new Color(120, 120, 120).getRGB(); // road
                }

                image.setRGB(x, y, rgb);
            }
        }

        File directory = new File("generated_noise");
        if (!directory.exists()) directory.mkdirs();
        File outputFile = new File(directory, "noise_chunk_" + chunkX + "_" + chunkZ + ".png");
        ImageIO.write(image, "png", outputFile);
        System.out.println("Noise image saved to: " + outputFile.getAbsolutePath());
    }

    public static void main(String[] args) throws IOException {
        Long seed = 946496062586794636L;
        int chunkSize = 500;
        float scale = 40;
        HeightMapGenerator generator = new HeightMapGenerator(seed, chunkSize, scale);
        RoadGenerator road = new RoadGenerator();

        float[][] heightmap = generator.generateHeightmap(0, 0);
        List<Node> path = road.getRoadPointsInChunk(heightmap, 0, chunkSize / 2, chunkSize - 1, chunkSize / 2);
        generator.applyRoadFlattening(heightmap, path);
        generator.generateImage(0, 0, heightmap);

//        for (int i = 0; i < heightmap.length; i++) {
//            for (int j = 0; j < heightmap[i].length; j++) {
//                System.out.print(heightmap[i][j] + " ");
//            }
//            System.out.println();
//        }

//        for (int x = -1; x < 2; x++) {
//            for (int z = -1; z < 2; z++) {
//                float[][] heightmap = generator.generateHeightmap(500, 500, seed, 40, x, z);
//                List<Vector2f> roadPoints = road.getRoadPointsInChunk(x, z, heightmap);
//                generator.applyRoadFlattening(heightmap, 500, 500, x, z, roadPoints);
//                generator.generateImage(500, 500, x, z, heightmap);
//            }
//        }
    }
}