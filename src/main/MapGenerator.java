package main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MapGenerator {

    public static void main(String[] args) {
        int width = 100;
        int height = 100;
        int[][] map = new int[height][width];

        // 1. Fill entire map with solid Trees (4)
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                map[r][c] = 4;
            }
        }

        // 2. Sand Shore (5)
        for (int r = 20; r <= 36; r++) {
            for (int c = 24; c <= 76; c++) {
                map[r][c] = 5;
            }
        }

        // 3. Water (2)
        for (int r = 22; r <= 34; r++) {
            for (int c = 27; c <= 73; c++) {
                map[r][c] = 2;
            }
        }

        // 4. Main Road (south to north)
        for (int r = 55; r <= 94; r++) {
            map[r][49] = 3;
            map[r][50] = 3;
        }

        // 5. 3-Way Intersection
        for (int c = 15; c <= 85; c++) {
            map[54][c] = 3;
            map[55][c] = 3;
        }

        // 6. Center Branch
        for (int r = 37; r <= 54; r++) {
            map[r][49] = 3;
            map[r][50] = 3;
        }

        // 7. Right Branch
        for (int r = 18; r <= 54; r++) {
            map[r][84] = 3;
            map[r][85] = 3;
        }

        // 8. Left Branch Road (leads straight up to the house)
        for (int r = 21; r <= 54; r++) {
            map[r][15] = 3;
            map[r][16] = 3;
        }

        // 9. Clear 3x3 footprint directly under the house (Rows 18-20, Cols 14-16)
        for (int r = 18; r <= 20; r++) {
            for (int c = 14; c <= 16; c++) {
                map[r][c] = 0; // Grass tile base
            }
        }

        // Write directly to res/map/world01.txt
        try {
            File file = new File("res/map/world01.txt");
            file.getParentFile().mkdirs();
            FileWriter writer = new FileWriter(file);

            for (int r = 0; r < height; r++) {
                StringBuilder line = new StringBuilder();
                for (int c = 0; c < width; c++) {
                    line.append(map[r][c]).append(c == width - 1 ? "" : " ");
                }
                writer.write(line.toString() + "\n");
            }
            writer.close();
            System.out.println("Map generated successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}