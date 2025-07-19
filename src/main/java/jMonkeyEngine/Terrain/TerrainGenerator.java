package jMonkeyEngine.Terrain;

public class TerrainGenerator {

    public static float[][] generateHeightmap(int width, int height, long seed, double scale) {
        float[][] heightmap = new float[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                double nx = x / scale;
                double ny = y / scale;

                heightmap[x][y] = OpenSimplex2.noise2(seed, nx, ny);
            }
        }

        return heightmap;
    }

    public static void main(String[] args) {
        long seed = 1234L;
        float[][] heightmap = generateHeightmap(512, 512, seed, 100.0);

        // Example: print some values
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                System.out.printf("%.2f ", heightmap[x][y]);
            }
            System.out.println();
        }
    }
}