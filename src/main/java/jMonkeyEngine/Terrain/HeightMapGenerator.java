package jMonkeyEngine.Terrain;

import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import jMonkeyEngine.Road.RoadGenerator;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class HeightMapGenerator {

    static long seed = 54325432798L;

    public float[][] generateHeightmap(int width, int height, long seed, double scale, int chunkX, int chunkZ, List<Vector2f> roadPath)
            throws IOException {
        float[][] heightmap = new float[width][height];
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // === Trace the road path once globally ===
        float roadWidth = 0.25f;
        float flattenStrength = 0.0000000001f;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double worldX = (chunkX * (width - 1) + x) / scale;
                double worldY = (chunkZ * (height - 1) + y) / scale;

                // === Terrain noise ===
                float e = 40f * OpenSimplex2.noise2(seed, 0.05f * worldX, 0.05f * worldY) +
                        6f * OpenSimplex2.noise2(seed, 0.25f * worldX, 0.25f * worldY) +
                        0.9f * OpenSimplex2.noise2(seed, 0.5f * worldX, 0.5f * worldY) +
                        0.6f * OpenSimplex2.noise2(seed, 0.75f * worldX, 0.75f * worldY);
                e = e / (40f + 6f + 0.9f + 0.6f);
                e = (e + 1f) / 2f;
                e = FastMath.pow(e, 0.75f);
                float terrainHeight = e;

                // === Road flattening ===
                float closestDistance = Float.MAX_VALUE;
                float roadHeightSampleZ = (float)worldY;

                for (Vector2f roadPoint : roadPath) {
                    float dx = (float)worldX - roadPoint.x;
                    float dz = (float)worldY - roadPoint.y;
                    float distSq = dx * dx + dz * dz;
                    if (distSq < closestDistance) {
                        closestDistance = distSq;
                        roadHeightSampleZ = roadPoint.y; // Use this for road elevation sample
                    }
                }

                float distance = (float)Math.sqrt(closestDistance);
                float falloff = Math.max(0f, 1f - (distance / (roadWidth / 2f)));
                falloff = falloff * falloff * (3f - 2f * falloff); // smoothstep

                float roadHeight = 100f * OpenSimplex2.noise2(seed, 0.07f * worldX, 0.07f * roadHeightSampleZ) +
                        4f * OpenSimplex2.noise2(seed, 0.25f * worldX, 0.25f * roadHeightSampleZ) +
                        0.6f * OpenSimplex2.noise2(seed, 0.75f * worldX, 0.75f * roadHeightSampleZ);
                roadHeight = roadHeight / (100f + 4f + 0.6f);

                float finalHeight = FastMath.interpolateLinear(falloff * flattenStrength, terrainHeight, roadHeight);

                if (falloff > 0f) {
                    finalHeight += 2f; // Or exactly 1f if that's enough
                }

                heightmap[x][y] = finalHeight;

                // === Visualization ===
                double noiseValue = heightmap[x][y];
                int rgb;
                if (noiseValue < 0.1) rgb = new Color(0, 0, 255).getRGB();
                else if (noiseValue < 0.2) rgb = new Color(211, 169, 108).getRGB();
                else if (noiseValue < 0.3) rgb = new Color(34, 175, 34).getRGB();
                else if (noiseValue < 0.4) rgb = new Color(34, 125, 34).getRGB();
                else if (noiseValue < 0.5) rgb = new Color(34, 100, 25).getRGB();
                else if (noiseValue < 0.6) rgb = new Color(75, 80, 30).getRGB();
                else if (noiseValue < 0.7) rgb = new Color(90, 75, 20).getRGB();
                else if (noiseValue < 0.8) rgb = new Color(110, 70, 20).getRGB();
                else if (noiseValue < 0.9) rgb = new Color(139, 69, 19).getRGB();
                else rgb = new Color(255, 255, 255).getRGB();

                if (falloff > 0.01f) {
                    rgb = new Color(120, 120, 120).getRGB(); // road
                }

                image.setRGB(x, y, rgb);
            }
        }

        //generateImage(chunkX, chunkZ, image);

        return heightmap;
    }

    private static void generateImage(int chunkX, int chunkZ, BufferedImage image) throws IOException {
        File directory = new File("generated_noise");
        if (!directory.exists()) directory.mkdirs();
        File outputFile = new File(directory, "noise_chunk_" + chunkX + "_" + chunkZ + ".png");
        ImageIO.write(image, "png", outputFile);
        System.out.println("Noise image saved to: " + outputFile.getAbsolutePath());
    }

    public static void main(String[] args) throws IOException {
        HeightMapGenerator generator = new HeightMapGenerator();
        RoadGenerator road = new RoadGenerator(1000, 40, seed);

        //List<Vector2f> pathPoints = road.getRoadPointsInChunk(0, 0);
        float[][] heightmap = generator.generateHeightmap(1000, 1000, seed, 40, 0, 0, new ArrayList<>());

//        for (int x = -1; x < 2; x++) {
//            for (int z = -1; z < 2; z++) {
//                List<Vector2f> pathPoints = road.getRoadPointsInChunk(x, z);
//                heightmap = generator.generateHeightmap(500, 500, seed, 40, x, z, pathPoints);
//            }
//        }
    }
}