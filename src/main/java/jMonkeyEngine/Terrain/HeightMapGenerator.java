package jMonkeyEngine.Terrain;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class HeightMapGenerator {

    public float[][] generateHeightmap(int width, int height, long seed, double scale, int chunkX, int chunkZ)
            throws IOException {
        float[][] heightmap = new float[width][height];

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Convert local (x, y) in the chunk to world coordinates
                double worldX = (chunkX * (width - 1) + x) / scale;
                double worldY = (chunkZ * (height - 1) + y) / scale;

                float e = 100f * OpenSimplex2.noise2(seed, 0.07f * worldX, 0.07f * worldY) +
                        4f  * OpenSimplex2.noise2(seed, 0.25f * worldX, 0.25f * worldY) +
                        0.6f * OpenSimplex2.noise2(seed, 0.75f * worldX, 0.75f * worldY);

                e = e / (100f + 4f + 0.6f);

                // Shift to [0, 1]
                e = (e + 1f) / 2f;

                heightmap[x][y] = e;

                // Visualization
                double noiseValue = heightmap[x][y]; // range [0,1]
                int rgb;
                if (noiseValue < 0.1) {
                    // Water (blue)
                    rgb = new Color(0, 0, 255).getRGB();
                } else if (noiseValue < 0.2) {
                    // Beach (yellow)
                    rgb = new Color(211, 169, 108).getRGB(); // sand yellow
                } else if (noiseValue < 0.3) {
                    // Grassland (green)
                    rgb = new Color(34, 175, 34).getRGB(); // forest green
                } else if (noiseValue < 0.4) {
                    // Grassland (green)
                    rgb = new Color(34, 125, 34).getRGB(); // forest green
                } else if (noiseValue < 0.5) {
                    // Grassland (green)
                    rgb = new Color(34, 100, 25).getRGB(); // forest green
                } else if (noiseValue < 0.6) {
                    // Grassland (green)
                    rgb = new Color(75, 80, 30).getRGB(); // forest green
                } else if (noiseValue < 0.7) {
                    // Grassland (green)
                    rgb = new Color(90, 75, 20).getRGB(); // forest green
                } else if (noiseValue < 0.8) {
                    // Grassland (green)
                    rgb = new Color(110, 70, 20).getRGB(); // forest green
                } else if (noiseValue < 0.9) {
                    // Mountain (brown)
                    rgb = new Color(139, 69, 19).getRGB(); // saddle brown
                } else {
                    // Snow (white)
                    rgb = new Color(255, 255, 255).getRGB();
                }

                image.setRGB(x, y, rgb);
            }
        }

        String folderPath = "generated_noise";
        File directory = new File(folderPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

//        File outputFile = new File(directory, "noise_chunk_" + chunkX + "_" + chunkZ + ".png");
//        ImageIO.write(image, "png", outputFile);
//        System.out.println("Noise image saved to: " + outputFile.getAbsolutePath());
//
//        System.out.println("Noise image saved as noise_chunk_" + chunkX + "_" + chunkZ + ".png");

        return heightmap;
    }

    public static void main(String[] args) throws IOException {
        HeightMapGenerator generator = new HeightMapGenerator();

        long seed = 1234L;
        float[][] heightmap;

        heightmap = generator.generateHeightmap(1500, 1500, seed, 40, 0, 0);
    }
}