package jMonkeyEngine.Terrain;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class HeightMapGenerator {

    public float[][] generateHeightmap(int width, int height, long seed, double scale, int chunkX, int chunkZ)
            throws IOException {
        float[][] heightmap = new float[width][height];

        //BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Convert local (x, y) in the chunk to world coordinates
                double worldX = (chunkX * (width - 1) + x) / scale;
                double worldY = (chunkZ * (height - 1) + y) / scale;

                float e = 20f * OpenSimplex2.noise2(seed, 0.1f * worldX, 0.1f * worldY) +
                        4f  * OpenSimplex2.noise2(seed, 0.25f * worldX, 0.25f * worldY) +
                        0.6f * OpenSimplex2.noise2(seed, 0.75f * worldX, 0.75f * worldY);

                e = e / (20f + 4f + 0.6f);

                heightmap[x][y] = e;

                // Visualization
//                double noiseValue = heightmap[x][y]; // range [-1,1]
//                int grayscale = (int) ((noiseValue + 1) / 2 * 255);
//                grayscale = Math.max(0, Math.min(255, grayscale));
//
//                int rgb = (grayscale << 16) | (grayscale << 8) | grayscale;
//                image.setRGB(x, y, rgb);
            }
        }

//        String folderPath = "generated_noise";
//        File directory = new File(folderPath);
//        if (!directory.exists()) {
//            directory.mkdirs();
//        }
//
//        File outputFile = new File(directory, "noise_chunk_" + chunkX + "_" + chunkZ + ".png");
//        ImageIO.write(image, "png", outputFile);
//        System.out.println("Noise image saved to: " + outputFile.getAbsolutePath());

        //System.out.println("Noise image saved as noise_chunk_" + chunkX + "_" + chunkZ + ".png");

        return heightmap;
    }

    public static void main(String[] args) throws IOException {
        HeightMapGenerator generator = new HeightMapGenerator();

        long seed = 1234L;
        float[][] heightmap;

        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                heightmap = generator.generateHeightmap(200, 200, seed, 100, x, z);
            }
        }
    }
}