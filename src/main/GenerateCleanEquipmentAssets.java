package main;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import javax.imageio.ImageIO;

public class GenerateCleanEquipmentAssets {

    public static BufferedImage cleanSprite(BufferedImage raw) {
        int w = raw.getWidth();
        int h = raw.getHeight();
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        // Get true background color from the 4 corners
        int[] cx = { 0, w - 1, 0, w - 1 };
        int[] cy = { 0, 0, h - 1, h - 1 };
        int bgR = 0, bgG = 0, bgB = 0;
        for (int i = 0; i < 4; i++) {
            int rgb = raw.getRGB(cx[i], cy[i]);
            bgR += (rgb >> 16) & 0xFF;
            bgG += (rgb >> 8) & 0xFF;
            bgB += rgb & 0xFF;
        }
        bgR /= 4; bgG /= 4; bgB /= 4;

        boolean[][] isOuterBg = new boolean[w][h];
        boolean[][] visited = new boolean[w][h];
        Queue<int[]> q = new ArrayDeque<>();

        // Seed strictly the outer border of the box
        for (int x = 0; x < w; x++) {
            q.add(new int[]{x, 0});
            q.add(new int[]{x, h - 1});
        }
        for (int y = 0; y < h; y++) {
            q.add(new int[]{0, y});
            q.add(new int[]{w - 1, y});
        }

        int[] dx = { 0, 0, 1, -1 };
        int[] dy = { 1, -1, 0, 0 };

        while (!q.isEmpty()) {
            int[] pt = q.poll();
            int x = pt[0], y = pt[1];
            if (x < 0 || x >= w || y < 0 || y >= h) continue;
            if (visited[x][y]) continue;
            visited[x][y] = true;

            int rgb = raw.getRGB(x, y);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            double dist = Math.hypot(Math.hypot(r - bgR, g - bgG), b - bgB);
            // Must be close to the grey-purple panel background and NOT dark outline or steel
            boolean isDarkOutline = (r < 100 && g < 100 && b < 115);
            boolean isBgColor = (dist < 28) && !isDarkOutline;

            if (isBgColor) {
                isOuterBg[x][y] = true;
                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i], ny = y + dy[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && !visited[nx][ny]) {
                        q.add(new int[]{nx, ny});
                    }
                }
            }
        }

        // Fill result: any pixel that is NOT outer background is part of the solid sprite!
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isOuterBg[x][y]) {
                    res.setRGB(x, y, 0x00000000);
                } else {
                    res.setRGB(x, y, 0xFF000000 | (raw.getRGB(x, y) & 0xFFFFFF));
                }
            }
        }

        // Remove any disconnected border noise from neighboring box dividers (keep only main connected component)
        boolean[][] kept = new boolean[w][h];
        Queue<int[]> compQ = new ArrayDeque<>();
        int midX = w / 2, midY = h / 2;
        boolean foundSeed = false;
        for (int r = 0; r <= 10 && !foundSeed; r++) {
            for (int dy2 = -r; dy2 <= r && !foundSeed; dy2++) {
                for (int dx2 = -r; dx2 <= r && !foundSeed; dx2++) {
                    int sx = midX + dx2, sy = midY + dy2;
                    if (sx >= 0 && sx < w && sy >= 0 && sy < h && !isOuterBg[sx][sy]) {
                        compQ.add(new int[]{sx, sy});
                        kept[sx][sy] = true;
                        foundSeed = true;
                    }
                }
            }
        }
        int[] d8x = { 0, 0, 1, -1, 1, 1, -1, -1 };
        int[] d8y = { 1, -1, 0, 0, 1, -1, 1, -1 };
        while (!compQ.isEmpty()) {
            int[] pt = compQ.poll();
            int px = pt[0], py = pt[1];
            for (int i = 0; i < 8; i++) {
                int nx = px + d8x[i], ny = py + d8y[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && !isOuterBg[nx][ny] && !kept[nx][ny]) {
                    kept[nx][ny] = true;
                    compQ.add(new int[]{nx, ny});
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
        
        int[] colsX = { 68, 177, 293, 403 };
        int[] rowsY = { 51, 86, 120, 155 };
        
        int[] shieldColsX = { 94, 94, 94, 94 };
        int[] shieldRowsY = { 226, 260, 294, 323 };
        
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
                BufferedImage clean = cleanSprite(raw);
                
                // Mirror LEFT to RIGHT for helmet and shield
                if (d == 2 && (item == 0 || item == 4)) {
                    int leftX = (item == 4) ? shieldColsX[1] : colsX[item];
                    int leftY = (item == 4) ? shieldRowsY[1] : rowsY[1];
                    int lsx = leftX - boxW/2;
                    int lsy = leftY - boxH/2;
                    BufferedImage leftRaw = panel.getSubimage(lsx, lsy, boxW, boxH);
                    clean = flipHorizontal(cleanSprite(leftRaw));
                }
                
                g.drawImage(clean, d * boxW, 0, null);
            }
            g.dispose();
            
            File resOut = new File(resDir, fileNames[item]);
            File binOut = new File(binDir, fileNames[item]);
            ImageIO.write(sheet, "png", resOut);
            ImageIO.write(sheet, "png", binOut);
            System.out.println("Generated clean authentic equipment: " + resOut.getName() + " (" + sheet.getWidth() + "x" + sheet.getHeight() + ")");
        }
    }
}
