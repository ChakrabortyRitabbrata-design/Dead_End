package main;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.imageio.ImageIO;

public class GenerateCleanEquipmentAssets {

    public static BufferedImage removeBackgroundFloodFill(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        
        int bgR = 0, bgG = 0, bgB = 0, count = 0;
        int[] borderX = { 0, 1, w-1, w-2 };
        int[] borderY = { 0, 1, h-1, h-2 };
        for (int x : borderX) {
            for (int y : borderY) {
                int rgb = src.getRGB(x, y);
                bgR += (rgb >> 16) & 0xFF;
                bgG += (rgb >> 8) & 0xFF;
                bgB += rgb & 0xFF;
                count++;
            }
        }
        bgR /= count; bgG /= count; bgB /= count;
        
        boolean[][] visited = new boolean[w][h];
        boolean[][] isBackground = new boolean[w][h];
        Queue<int[]> queue = new ArrayDeque<>();
        
        for (int x = 0; x < w; x++) {
            queue.add(new int[]{x, 0});
            queue.add(new int[]{x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            queue.add(new int[]{0, y});
            queue.add(new int[]{w - 1, y});
        }
        
        int[] dx = { 0, 0, 1, -1 };
        int[] dy = { 1, -1, 0, 0 };
        
        while (!queue.isEmpty()) {
            int[] pt = queue.poll();
            int x = pt[0], y = pt[1];
            if (x < 0 || x >= w || y < 0 || y >= h) continue;
            if (visited[x][y]) continue;
            visited[x][y] = true;
            
            int rgb = src.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            
            double dist = Math.hypot(Math.hypot(r - bgR, g - bgG), b - bgB);
            int brightness = (r + g + b) / 3;
            boolean matchesBg = (dist < 42 && brightness > 130) ||
                                (r > 165 && g > 155 && b > 170 && Math.abs(r - g) < 28 && Math.abs(r - b) < 28) ||
                                (r > 200 && g > 200 && b > 200);
            
            if (matchesBg) {
                isBackground[x][y] = true;
                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int ny = y + dy[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[nx][ny]) {
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }
        
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isBackground[x][y]) {
                    res.setRGB(x, y, 0x00000000);
                } else {
                    res.setRGB(x, y, 0xFF000000 | (src.getRGB(x, y) & 0xFFFFFF));
                }
            }
        }

        // Post-filter: keep only the main central component (removes any stray border artifacts from neighboring boxes)
        boolean[][] kept = new boolean[w][h];
        Queue<int[]> compQueue = new ArrayDeque<>();
        int midX = w / 2, midY = h / 2;
        // Find a solid pixel near the center
        boolean foundSeed = false;
        for (int r = 0; r <= 8 && !foundSeed; r++) {
            for (int dy2 = -r; dy2 <= r && !foundSeed; dy2++) {
                for (int dx2 = -r; dx2 <= r && !foundSeed; dx2++) {
                    int sx = midX + dx2, sy = midY + dy2;
                    if (sx >= 0 && sx < w && sy >= 0 && sy < h && !isBackground[sx][sy]) {
                        compQueue.add(new int[]{sx, sy});
                        kept[sx][sy] = true;
                        foundSeed = true;
                    }
                }
            }
        }
        while (!compQueue.isEmpty()) {
            int[] pt = compQueue.poll();
            int px = pt[0], py = pt[1];
            for (int i = 0; i < 4; i++) {
                int nx = px + dx[i], ny = py + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !isBackground[nx][ny] && !kept[nx][ny]) {
                    kept[nx][ny] = true;
                    compQueue.add(new int[]{nx, ny});
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!kept[x][y]) {
                    res.setRGB(x, y, 0x00000000);
                }
            }
        }

        return res;
    }

    public static BufferedImage flipHorizontal(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = flipped.createGraphics();
        g.drawImage(img, w, 0, -w, h, null);
        g.dispose();
        return flipped;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage panel = ImageIO.read(new File("equipment_area_full.png"));
        int boxW = 28;
        int boxH = 28;
        
        // Exact horizontal centers for each item:
        int[] colsX = { 68, 177, 293, 403 };
        int[] rowsY = { 51, 86, 120, 155 };
        
        // Exact centers for shield (using clean column 2):
        int[] shieldColsX = { 88, 91, 91, 88 };
        int[] shieldRowsY = { 225, 259, 293, 328 };
        
        String[] fileNames = {
            "equipment_helmet_clean.png",
            "equipment_chestplate_clean.png",
            "equipment_leggings_clean.png",
            "equipment_boots_clean.png",
            "equipment_shield_clean.png"
        };
        
        File resDir = new File("res/player");
        File binDir = new File("bin/player");
        if (!resDir.exists()) resDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();
        
        for (int item = 0; item < 5; item++) {
            BufferedImage sheet = new BufferedImage(boxW * 4, boxH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            
            for (int d = 0; d < 4; d++) {
                int cx = (item == 4) ? shieldColsX[d] : colsX[item];
                int cy = (item == 4) ? shieldRowsY[d] : rowsY[d];
                
                int sx = cx - boxW/2;
                int sy = cy - boxH/2;
                if (sx < 0) sx = 0;
                if (sy < 0) sy = 0;
                if (sx + boxW > panel.getWidth()) sx = panel.getWidth() - boxW;
                if (sy + boxH > panel.getHeight()) sy = panel.getHeight() - boxH;
                
                BufferedImage raw = panel.getSubimage(sx, sy, boxW, boxH);
                BufferedImage clean = removeBackgroundFloodFill(raw);
                
                // Mirror LEFT to RIGHT for helmet and shield
                if (d == 2 && (item == 0 || item == 4)) {
                    int leftX = (item == 4) ? shieldColsX[1] : colsX[item];
                    int leftY = (item == 4) ? shieldRowsY[1] : rowsY[1];
                    int lsx = leftX - boxW/2;
                    int lsy = leftY - boxH/2;
                    BufferedImage leftRaw = panel.getSubimage(lsx, lsy, boxW, boxH);
                    clean = flipHorizontal(removeBackgroundFloodFill(leftRaw));
                }
                
                // For Helmet DOWN: open face/eyes so hero face shows through
                if (item == 0 && d == 0) {
                    BufferedImage openHelm = new BufferedImage(boxW, boxH, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < boxH; y++) {
                        for (int x = 0; x < boxW; x++) {
                            int rgb = clean.getRGB(x, y);
                            // Visor cutout: center is at x=13..14
                            if (y >= 13 && y <= 22 && x >= 8 && x <= 19) {
                                if (x != 13 && x != 14) {
                                    openHelm.setRGB(x, y, 0x00000000);
                                    continue;
                                }
                            }
                            openHelm.setRGB(x, y, rgb);
                        }
                    }
                    clean = openHelm;
                }
                
                // For Helmet LEFT & RIGHT: clear interior white patch so cheek/neck shows through
                if (item == 0 && (d == 1 || d == 2)) {
                    for (int y = 14; y < boxH; y++) {
                        for (int x = 0; x < boxW; x++) {
                            int rgb = clean.getRGB(x, y);
                            int r = (rgb >> 16) & 0xFF;
                            int gr = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            if (r > 190 && gr > 190 && b > 190) {
                                clean.setRGB(x, y, 0x00000000);
                            }
                        }
                    }
                }
                
                g.drawImage(clean, d * boxW, 0, null);
            }
            g.dispose();
            
            File resOut = new File(resDir, fileNames[item]);
            File binOut = new File(binDir, fileNames[item]);
            ImageIO.write(sheet, "png", resOut);
            ImageIO.write(sheet, "png", binOut);
            System.out.println("Generated: " + resOut.getName() + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");
        }
    }
}
