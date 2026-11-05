package main;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Capture the current Level 1 compositions through the game's real paint path. */
public final class LocationPreview {
    public static void main(String[] args) throws Exception {
        Path folder=Path.of("out/location-previews");Files.createDirectories(folder);
        GamePanel gp=new GamePanel();gp.setSize(gp.screenWidth,gp.screenHeight);gp.setupGame();
        for(var location:gp.tileM.currentMap.landmarks) {
            if(!java.util.Set.of("garden","cottage","farmhouse").contains(location.kind())) continue;
            gp.player.worldX=location.col()*48;gp.player.worldY=location.row()*48+24;
            gp.fogOfWar.update(location.col(),location.row());gp.levelM.notifyPlayer(location.name());
            BufferedImage image=new BufferedImage(gp.screenWidth,gp.screenHeight,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();gp.paint(g);g.dispose();
            ImageIO.write(image,"png",folder.resolve(location.kind()+".png").toFile());
            if(gp.tileM.collisionLayer[location.col()][location.row()]!=0) throw new AssertionError("Entrance blocked");
        }
        System.out.println("Location render captures: "+folder.toAbsolutePath());
    }
}
