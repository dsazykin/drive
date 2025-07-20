package jMonkeyEngine.Terrain;

import com.jme3.math.FastMath;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class TerrainGenerator {

    public float[][] generateHeightmap(int width, int height, long seed, double scale)
            throws IOException {
        float[][] heightmap = new float[width][height];

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double nx = x / scale;
                double ny = y / scale;

                float e = 20f * OpenSimplex2.noise2(seed, 0.1f * nx, 0.1f * ny) +
                        4f * OpenSimplex2.noise2(seed, 0.25f * nx, 0.25f * ny) +
                        0.6f * OpenSimplex2.noise2(seed, 0.75f * nx, 0.75f * ny);

                e = e / (20f + 4f + 0.6f);

                heightmap[x][y] = (float) Math.pow(e, 1f);

                double noiseValue = heightmap[x][y]; // range [-1,1]

                // Normalize to [0,255]
                int grayscale = (int) ((noiseValue + 1) / 2 * 255);
                grayscale = Math.max(0, Math.min(255, grayscale));

                int rgb = (grayscale << 16) | (grayscale << 8) | grayscale;
                image.setRGB(x, y, rgb);
            }
        }

        ImageIO.write(image, "png", new File("noise.png"));
        System.out.println("Noise image saved as noise.png");

        return heightmap;
    }

    public static void main(String[] args) throws IOException {
        TerrainGenerator generator = new TerrainGenerator();

        long seed = 1234L;
        float[][] heightmap = generator.generateHeightmap(512, 512, seed, 50.0);

        // Example: print some values
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                System.out.printf("%.2f ", heightmap[x][y]);
            }
            System.out.println();
        }
    }
}