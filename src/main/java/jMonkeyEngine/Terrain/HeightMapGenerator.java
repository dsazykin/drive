package jMonkeyEngine.Terrain;

import com.jme3.math.FastMath;
import jMonkeyEngine.Chunks.ChunkCoord;
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
    private final float ROAD_WIDTH;

    float prevHeight = Float.MAX_VALUE;

    public HeightMapGenerator(long seed, int chunkSize, double scale, float roadWidth) {
        SEED = seed;
        CHUNK_SIZE = chunkSize;
        SCALE = scale;
        ROAD_WIDTH = roadWidth;
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

//        try {
//            generateImage(chunkX, chunkZ, heightmap);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        return heightmap;
    }

    public void applyRoadFlattening(float[][] heightmap, List<Node> roadPath) {
        if (roadPath.size() < 2) return;

        // 1. PRE-CALCULATE AND SMOOTH NODE HEIGHTS
        // We treat the road as a 3D ribbon. We determine the Y (height) for every Node first.
        float[] nodeHeights = new float[roadPath.size()];

        // Step A: Sample raw terrain height at every node
        for (int i = 0; i < roadPath.size(); i++) {
            Node n = roadPath.get(i);
            nodeHeights[i] = sampleHeight(heightmap, n.x, n.y);
        }

        // Step B: Smooth the vertical profile (Iterative averaging)
        // This removes sudden spikes/dips, making the road gradient gradual.
        // Run this pass 5-10 times for a smoother road.
        int smoothingPasses = 10;
        for (int p = 0; p < smoothingPasses; p++) {
            float[] smoothed = nodeHeights.clone();
            for (int i = 1; i < roadPath.size() - 1; i++) {
                // Average previous, current, and next height
                smoothed[i] = (nodeHeights[i - 1] + nodeHeights[i] + nodeHeights[i + 1]) / 3f;
            }
            nodeHeights = smoothed;
        }

        // 2. RASTERIZE THE ROAD
        // We use a "Brush" approach. For every step on the spline, we affect the surrounding pixels.

        // How wide the flat asphalt is
        float roadRadius = ROAD_WIDTH / 2f;
        // How wide the blended shoulder is (the slope connecting road to terrain)
        float blendDistance = 6.0f;
        float totalEffectRadius = roadRadius + blendDistance;

        int subdivisions = 10; // Higher subdivisions = fewer gaps in the rasterization

        for (int i = 0; i < roadPath.size() - 1; i++) {
            // Catmull-Rom Control Points
            Node p0 = (i > 0) ? roadPath.get(i - 1) : roadPath.get(i);
            Node p1 = roadPath.get(i);
            Node p2 = roadPath.get(i + 1);
            Node p3 = (i < roadPath.size() - 2) ? roadPath.get(i + 2) : roadPath.get(i + 1);

            // Height Control Points
            float h0 = (i > 0) ? nodeHeights[i - 1] : nodeHeights[i];
            float h1 = nodeHeights[i];
            float h2 = nodeHeights[i + 1];
            float h3 = (i < roadPath.size() - 2) ? nodeHeights[i + 2] : nodeHeights[i + 1];

            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;

                // Pre-calculate powers of t
                float t2 = t * t;
                float t3 = t2 * t;

                // Catmull-Rom coefficients
                float coef0 = -0.5f * t3 + t2 - 0.5f * t;
                float coef1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
                float coef2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
                float coef3 = 0.5f * t3 - 0.5f * t2;

                // INTERPOLATE 3D POSITION (X, Z, and Height)
                // Note: Your Node.y is actually the Z coordinate in 3D space
                float cx = coef0 * p0.x + coef1 * p1.x + coef2 * p2.x + coef3 * p3.x;
                float cz = coef0 * p0.y + coef1 * p1.y + coef2 * p2.y + coef3 * p3.y;
                float cy = coef0 * h0 + coef1 * h1 + coef2 * h2 + coef3 * h3; // Calculated Road Height

                // Apply to terrain within radius
                int minX = (int) Math.floor(cx - totalEffectRadius);
                int maxX = (int) Math.ceil(cx + totalEffectRadius);
                int minZ = (int) Math.floor(cz - totalEffectRadius);
                int maxZ = (int) Math.ceil(cz + totalEffectRadius);

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        // Bounds check
                        if (x < 0 || z < 0 || x >= heightmap.length || z >= heightmap[0].length) continue;

                        float dx = x - cx;
                        float dz = z - cz;
                        float distSq = dx * dx + dz * dz;

                        // Optimization: Skip if outside total radius
                        if (distSq > totalEffectRadius * totalEffectRadius) continue;

                        float dist = (float) Math.sqrt(distSq);

                        // 3. APPLY HEIGHT BLENDING
                        if (dist <= roadRadius) {
                            // We are on the asphalt: Hard set to road height
                            heightmap[x][z] = cy;
                        } else if (dist < totalEffectRadius) {
                            // We are on the shoulder: Blend from road height to terrain height
                            // Calculate blend factor (0.0 at road edge, 1.0 at outer edge)
                            float blendFactor = (dist - roadRadius) / blendDistance;

                            // Use smoothstep for nicer transition curves
                            blendFactor = blendFactor * blendFactor * (3 - 2 * blendFactor);

                            float originalHeight = heightmap[x][z];
                            // Linear Interpolation (Lerp)
                            heightmap[x][z] = cy * (1 - blendFactor) + originalHeight * blendFactor;
                        }
                    }
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
        int chunkSize = 200;
        float scale = 40;
        HeightMapGenerator generator = new HeightMapGenerator(seed, chunkSize, scale, 6f);
        RoadGenerator road = new RoadGenerator();

//        float[][] heightmap = generator.generateHeightmap(0, 0);
//        List<Node> path = road.getRoadPointsInChunk(heightmap, 0, chunkSize / 2, chunkSize - 1, chunkSize / 2);
//        generator.applyRoadFlattening(heightmap, path);
//        generator.generateImage(0, 0, heightmap);

//        for (int i = 0; i < heightmap.length; i++) {
//            for (int j = 0; j < heightmap[i].length; j++) {
//                System.out.print(heightmap[i][j] + " ");
//            }
//            System.out.println();
//        }

//        for (int x = -1; x < 2; x++) {
//            for (int z = -1; z < 2; z++) {
//                ChunkCoord chunk = new ChunkCoord(x, z);
//                float[][] heightmap = generator.generateHeightmap(chunk.x, -chunk.z);
//                List<Node> path = null;
//                if (x == 0 && z == 0) {
//                    path = road.getRoadPointsInChunk(heightmap, 0, chunkSize / 2, chunkSize - 1, chunkSize / 2);
//                } else {
//                    Integer startX;
//                    Integer startZ;
//                    if (road.currentXChunk == chunk.x && road.currentZChunk == chunk.z) {
//                        if (road.verticalExitUp) {
//                            startZ = 0;
//                            startX = road.lastXCoord;
//                        } else if (road.verticalExitDown) {
//                            startZ = chunkSize - 1;
//                            startX = road.lastXCoord;
//                        } else {
//                            startZ = road.lastZCoord;
//                            startX = 0;
//                        }
//                        System.out.println(startX);
//                        System.out.println(startZ);
//                        path = road.getRoadPointsInChunk(heightmap, startX,
//                                                         startZ, chunkSize - 1,
//                                                         chunkSize / 2);
//                    }
//                }
//                if (path != null) {
//                    generator.applyRoadFlattening(heightmap, path, chunk);
//                }
//                generator.generateImage(chunk.x, chunk.z, heightmap);
//            }
//        }

        int count = 0;
        int x = 0;
        int z = 0;
        while (count < 1) {
            ChunkCoord chunk = new ChunkCoord(x, z);
            System.out.println("next chunk: " + chunk.x + ", " + chunk.z);
            float[][] heightmap = generator.generateHeightmap(chunk.x, chunk.z);
            List<Node> path = null;
            if (x == 0 && z == 0) {
                path = road.getRoadPointsInChunk(heightmap, 0, chunkSize / 2, chunkSize - 1, chunkSize / 2);
            } else {
                Integer startX;
                Integer startZ;
                if (road.currentXChunk == chunk.x && road.currentZChunk == chunk.z) {
                    if (road.verticalExitUp) {
                        System.out.println("upward exit");
                        startZ = 0;
                        startX = road.lastXCoord;
                    } else if (road.verticalExitDown) {
                        System.out.println("downward exit");
                        startZ = chunkSize - 1;
                        startX = road.lastXCoord;
                    } else {
                        System.out.println("normal exit");
                        startZ = road.lastZCoord;
                        startX = 0;
                    }
                    System.out.println(startX);
                    System.out.println(startZ);
                    path = road.getRoadPointsInChunk(heightmap, startX,
                                                     startZ, chunkSize - 1,
                                                     chunkSize / 2);
                    System.out.println(path);
                }
            }
            if (path != null) {
                System.out.println("applying road to chunk: " + chunk.x + ", " + chunk.z);
                generator.applyRoadFlattening(heightmap, path);
                x = road.currentXChunk;
                z = road.currentZChunk;
                count++;
            }
            generator.generateImage(chunk.x, chunk.z, heightmap);
        }
    }
}