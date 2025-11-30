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

    float prevHeight = Float.MAX_VALUE;

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

//        try {
//            generateImage(chunkX, chunkZ, heightmap);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        return heightmap;
    }

    public void applyRoadFlattening(float[][] heightmap, List<Node> roadPath, ChunkCoord chunk) {
        float roadWidth = 6f;
        float halfWidth = roadWidth / 2f;

        float[][] targetHeights = new float[heightmap.length][heightmap[0].length];
        boolean[][] hasTarget = new boolean[heightmap.length][heightmap[0].length];

        // Interpolate points for smooth curves
        int subdivisions = 8; // Points between each control point

        for (int i = 0; i < roadPath.size() - 1; i++) {
            // Get control points for Catmull-Rom spline
            Node p0 = (i > 0) ? roadPath.get(i - 1) : roadPath.get(i);
            Node p1 = roadPath.get(i);
            Node p2 = roadPath.get(i + 1);
            Node p3 = (i < roadPath.size() - 2) ? roadPath.get(i + 2) : roadPath.get(i + 1);

            // Interpolate between p1 and p2
            for (int j = 0; j < subdivisions; j++) {
                float t = (float) j / subdivisions;

                // Catmull-Rom interpolation
                float t2 = t * t;
                float t3 = t2 * t;

                float coef0 = -0.5f * t3 + t2 - 0.5f * t;
                float coef1 = 1.5f * t3 - 2.5f * t2 + 1.0f;
                float coef2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t;
                float coef3 = 0.5f * t3 - 0.5f * t2;

                float cx = coef0 * p0.x + coef1 * p1.x + coef2 * p2.x + coef3 * p3.x;
                float cz = coef0 * p0.y + coef1 * p1.y + coef2 * p2.y + coef3 * p3.y;

                // Calculate tangent direction for perpendicular
                float dx = 0, dz = 0;
                if (j == 0 && i > 0) {
                    dx = p2.x - p1.x;
                    dz = p2.y - p1.y;
                } else if (j < subdivisions - 1) {
                    // Next point on curve
                    float tNext = (float)(j + 1) / subdivisions;
                    float t2Next = tNext * tNext;
                    float t3Next = t2Next * tNext;

                    float coef0Next = -0.5f * t3Next + t2Next - 0.5f * tNext;
                    float coef1Next = 1.5f * t3Next - 2.5f * t2Next + 1.0f;
                    float coef2Next = -1.5f * t3Next + 2.0f * t2Next + 0.5f * tNext;
                    float coef3Next = 0.5f * t3Next - 0.5f * t2Next;

                    float cxNext = coef0Next * p0.x + coef1Next * p1.x + coef2Next * p2.x + coef3Next * p3.x;
                    float czNext = coef0Next * p0.y + coef1Next * p1.y + coef2Next * p2.y + coef3Next * p3.y;

                    dx = cxNext - cx;
                    dz = czNext - cz;
                } else {
                    dx = p2.x - p1.x;
                    dz = p2.y - p1.y;
                }

                float segLength = (float) Math.sqrt(dx * dx + dz * dz);
                if (segLength > 0.01f) {
                    dx /= segLength;
                    dz /= segLength;
                }

                float px = -dz;
                float pz = dx;


                float lx = cx + px * halfWidth;
                float lz = cz + pz * halfWidth;
                float rx = cx - px * halfWidth;
                float rz = cz - pz * halfWidth;

                float count = 0;
                float leftH = sampleHeight(heightmap, lx, lz);
                if (leftH != 0) count++;
                float rightH = sampleHeight(heightmap, rx, rz);
                if (rightH != 0) count++;
                float currHeight = (leftH + rightH) / count;

                if (prevHeight != Float.MAX_VALUE) {
                    currHeight = prevHeight * 0.98f + currHeight * 0.02f;
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

        smoothRoad(heightmap, hasTarget, targetHeights);

        blendTerrain(heightmap, hasTarget, targetHeights);

//        try {
//            generateImage(chunk.x, chunk.z, heightmap);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }

    private static void smoothRoad(float[][] heightmap, boolean[][] hasTarget,
                                  float[][] targetHeights) {
        for (int x = 1; x < heightmap.length - 1; x++) {
            for (int z = 1; z < heightmap[0].length - 1; z++) {
                if (hasTarget[x][z]) {
                    float sum = targetHeights[x][z];
                    int count = 1;

                    if (hasTarget[x-1][z]) { sum += targetHeights[x-1][z]; count++; }
                    if (hasTarget[x+1][z]) { sum += targetHeights[x+1][z]; count++; }
                    if (hasTarget[x][z-1]) { sum += targetHeights[x][z-1]; count++; }
                    if (hasTarget[x][z+1]) { sum += targetHeights[x][z+1]; count++; }

                    heightmap[x][z] = sum / count;
                }
            }
        }
    }

    private static void blendTerrain(float[][] heightmap, boolean[][] hasTarget,
                                  float[][] targetHeights) {
        int featherRadius = 4;

        for (int x = 0; x < heightmap.length; x++) {
            for (int z = 0; z < heightmap[0].length; z++) {
                if (hasTarget[x][z]) {
                    float roadH = targetHeights[x][z];

                    for (int dx = -featherRadius; dx <= featherRadius; dx++) {
                        for (int dz = -featherRadius; dz <= featherRadius; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;
                            if (nx < 0 || nz < 0 || nx >= heightmap.length || nz >= heightmap[0].length) continue;
                            if (hasTarget[nx][nz]) continue;

                            float dist = (float)Math.sqrt(dx*dx + dz*dz);
                            if (dist > featherRadius) continue;

                            float t = dist / featherRadius;
                            float originalH = heightmap[nx][nz];
                            float blendedH = roadH * (1 - t) + originalH * t;

                            heightmap[nx][nz] = blendedH;
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
        HeightMapGenerator generator = new HeightMapGenerator(seed, chunkSize, scale);
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
        while (count < 5) {
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
                generator.applyRoadFlattening(heightmap, path, chunk);
                x = road.currentXChunk;
                z = road.currentZChunk;
                count++;
            }
            generator.generateImage(chunk.x, chunk.z, heightmap);
        }
    }
}