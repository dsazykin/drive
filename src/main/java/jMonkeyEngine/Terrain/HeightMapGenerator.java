package jMonkeyEngine.Terrain;

import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import jMonkeyEngine.Road.RoadGenerator;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

public class HeightMapGenerator {

    static long seed = 600092672;

    public float[][] generateHeightmap(int width, int height, long seed, double scale, int chunkX, int chunkZ)
            throws IOException {
        float[][] heightmap = new float[width][height];

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

                heightmap[x][y] = terrainHeight;

            }
        }

        return heightmap;
    }

//    public void applyRoadFlattening(float[][] heightmap, int width, int height, float scale,
//                                    int chunkX, int chunkZ, long seed, List<Vector2f> roadPath
//    ) {
//        float roadWidth = 0.25f;
//        float flattenStrength = 0.0000000001f;
//
//        for (int x = 0; x < width; x++) {
//            for (int y = 0; y < height; y++) {
//                double worldX = (chunkX * (width - 1) + x) / scale;
//                double worldY = (chunkZ * (height - 1) + y) / scale;
//
//                float terrainHeight = heightmap[x][y];
//
//                // Compute closest road point
//                float closestDistance = Float.MAX_VALUE;
//                float roadHeightSampleZ = (float) worldY;
//
//                for (Vector2f roadPoint : roadPath) {
//                    float dx = (float) worldX - roadPoint.x;
//                    float dz = (float) worldY - roadPoint.y;
//                    float distSq = dx * dx + dz * dz;
//                    if (distSq < closestDistance) {
//                        closestDistance = distSq;
//                        roadHeightSampleZ = roadPoint.y;
//                    }
//                }
//
//                float distance = (float) Math.sqrt(closestDistance);
//                float falloff = Math.max(0f, 1f - (distance / (roadWidth / 2f)));
//                falloff = falloff * falloff * (3f - 2f * falloff); // smoothstep
//
//                float roadHeight = 100f * OpenSimplex2.noise2(seed, 0.07f * worldX, 0.07f * roadHeightSampleZ) +
//                        4f * OpenSimplex2.noise2(seed, 0.25f * worldX, 0.25f * roadHeightSampleZ) +
//                        0.6f * OpenSimplex2.noise2(seed, 0.75f * worldX, 0.75f * roadHeightSampleZ);
//                roadHeight /= (100f + 4f + 0.6f);
//
//                float finalHeight = FastMath.interpolateLinear(falloff * flattenStrength, terrainHeight, roadHeight);
//                if (falloff > 0f) finalHeight += 2f;
//
//                heightmap[x][y] = finalHeight;
//            }
//        }
//    }

    public void applyRoadFlattening(float[][] heightmap, int width, int height, int chunkX,
                                    int chunkZ, List<Vector2f> roadPath) {
        //System.out.println(roadPath);
        for (Vector2f roadPoint : roadPath) {
            int x = Math.round(roadPoint.x);
            int z = Math.round(roadPoint.y);

            int xStart = chunkX * (width - 1);
            int zStart = chunkZ * (height - 1);

            int localX = (int) FastMath.clamp(x - xStart, 0, 499);
            int localZ = (int) FastMath.clamp(z - zStart, 0, 499);

            //System.out.println("x: " + localX + " z: " + localZ);

            float roadHeight = heightmap[localX][localZ];
            //System.out.println("road height: " + roadHeight);

            for (int i = -3; i <= 3; i++) {
                for (int j = -3; j <= 3; j++) {
                    if (localX + i < 0 || localZ + j < 0 || localX + i >= heightmap.length || localZ + j >= heightmap[0].length) continue;
                    heightmap[localX + i][localZ + j] = roadHeight + 2;
                }
            }

            //System.out.println(heightmap[localX][localZ]);
        }
    }

    private void generateImage(int width, int height, int chunkX, int chunkZ, float[][] heightmap) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
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
        HeightMapGenerator generator = new HeightMapGenerator();
        RoadGenerator road = new RoadGenerator(500, 40, seed);

//        float[][] heightmap = generator.generateHeightmap(500, 500, seed, 40, 0, 0);
//        List<Vector2f> roadPoints = road.getRoadPointsInChunk(0, 0, heightmap);
//        generator.applyRoadFlattening(heightmap, 500, 500, 0, 0, roadPoints);
//        generator.generateImage(500, 500, 0, 0, heightmap);

//        for (int i = 0; i < heightmap.length; i++) {
//            for (int j = 0; j < heightmap[i].length; j++) {
//                System.out.print(heightmap[i][j] + " ");
//            }
//            System.out.println();
//        }

        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                float[][] heightmap = generator.generateHeightmap(500, 500, seed, 40, x, z);
                List<Vector2f> roadPoints = road.getRoadPointsInChunk(x, z, heightmap);
                generator.applyRoadFlattening(heightmap, 500, 500, x, z, roadPoints);
                generator.generateImage(500, 500, x, z, heightmap);
            }
        }
    }
}