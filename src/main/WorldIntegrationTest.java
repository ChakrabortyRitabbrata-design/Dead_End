package main;

import entities.Monster;
import tile.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Headless integration checks using actual game entities and Swing painting. */
public final class WorldIntegrationTest {
    private static void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);}
    public static void main(String[] args) throws Exception {
        Path output=Path.of("out/world-checks");Files.createDirectories(output);
        for(int id=1;id<=3;id++) {
            final int regionId=id;
            MapData m=MapLoader.loadLevel(id);boolean[][] seen=new boolean[m.mapWidth][m.mapHeight];
            ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(m.playerSpawn);seen[m.playerSpawn[0]][m.playerSpawn[1]]=true;
            while(!queue.isEmpty()) {
                int[] p=queue.remove();
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x=p[0]+d[0],y=p[1]+d[1];
                    if(x<0||y<0||x>=m.mapWidth||y>=m.mapHeight||seen[x][y]||m.collisionLayer[x][y]!=0) continue;
                    seen[x][y]=true;queue.add(new int[]{x,y});
                }
            }
            for(var g:m.gates) {
                check(seen[g.col()][g.row()],"Unreachable gate "+id+":"+g.id());
                MapData destination=MapLoader.loadLevel(g.destination());
                check(destination.collisionLayer[g.arrivalCol()][g.arrivalRow()]==0,"Blocked arrival");
                check(destination.gates.stream().anyMatch(back->back.id()==g.id()&&back.destination()==regionId),"Missing return gate");
            }
            for(var l:m.landmarks) check(seen[l.col()][l.row()],"Unreachable landmark "+l.name());
            System.out.println("Region "+id+": all landmarks, gates and arrivals reachable");
        }
        Progression p=new Progression();
        check(!p.unlock(1),"Locked gate accepted insufficient Power");
        check(p.award("first","undead"),"First award failed");check(!p.award("first","undead"),"Duplicate award");
        for(int i=0;i<9;i++) p.award("test"+i,"undead");
        check(p.score()==1000&&p.power()==2000,"Score/Power not independent");
        check(p.unlock(1)&&p.power()==2000,"Unlock consumed Power");check(!p.unlock(1),"Unlock repeated");
        FogOfWar fog=new FogOfWar(64,52);fog.update(10,10);fog.update(50,40);
        check(fog.getState(10,10)==FogOfWar.EXPLORED,"Teleport left stale VISIBLE fog");
        FogOfWar copy=new FogOfWar(64,52);copy.decode(fog.encode());check(copy.getState(10,10)==FogOfWar.EXPLORED,"Fog roundtrip");
        GamePanel gp=new GamePanel();gp.setSize(gp.screenWidth,gp.screenHeight);gp.setupGame();
        gp.fogOfWar.update(10,40);render(gp,output.resolve("level1-start.png"));
        int originalX=gp.player.worldX;gp.keyH.rightPressed=true;
        for(int i=0;i<5;i++) gp.update();gp.keyH.clearAllInputs();
        check(gp.player.worldX>originalX,"WASD movement integration");
        gp.player.worldX=10*48;gp.player.worldY=40*48;
        gp.gameState=GamePanel.GameState.FULL_MAP;render(gp,output.resolve("atlas-locked.png"));gp.gameState=GamePanel.GameState.PLAYING;
        int total=gp.monsters.size();check(total==14,"Level 1 spawn budget: "+total);
        Monster first=gp.monsters.get(0);String defeatedId=first.persistentId;first.takeDamage(1000);first.takeDamage(1000);
        check(gp.levelM.progression.power()==200,"Actual death duplicate rewards");
        for(int i=1;i<10;i++) gp.monsters.get(i).takeDamage(1000);
        gp.player.worldX=57*48;gp.player.worldY=7*48;gp.levelM.checkProgression();
        gp.fogOfWar.update(57,7);
        check(gp.levelM.progression.unlocked(1),"Physical gate did not unlock");render(gp,output.resolve("level1-gate.png"));
        gp.levelM.enter(2,8,44);gp.fogOfWar.update(35,24);
        gp.player.worldX=35*48;gp.player.worldY=28*48;render(gp,output.resolve("level2-temple.png"));
        gp.gameState=GamePanel.GameState.FULL_MAP;render(gp,output.resolve("atlas-second-region.png"));gp.gameState=GamePanel.GameState.PLAYING;
        check(gp.monsters.size()==17,"Level 2 spawn budget");
        for(int i=0;i<10;i++) gp.monsters.get(i).takeDamage(1000);
        gp.player.worldX=63*48;gp.player.worldY=7*48;gp.levelM.checkProgression();check(gp.levelM.progression.unlocked(2),"Second ward");
        gp.levelM.enter(3,8,48);gp.player.worldX=39*48;gp.player.worldY=34*48;
        gp.player.worldX=40*48;gp.player.worldY=12*48;gp.fogOfWar.update(40,12);render(gp,output.resolve("level3-mountains.png"));
        gp.player.worldX=39*48;gp.player.worldY=31*48;
        gp.fogOfWar.update(39,31);for(int i=0;i<120;i++) gp.dragon.update();render(gp,output.resolve("level3-arena.png"));
        gp.gameState=GamePanel.GameState.FULL_MAP;render(gp,output.resolve("atlas-unlocked.png"));gp.gameState=GamePanel.GameState.PLAYING;
        gp.levelM.enter(1,55,9);check(gp.fogOfWar.getState(10,40)==FogOfWar.EXPLORED,"Return lost fog");
        check(gp.monsters.stream().anyMatch(m->m.persistentId.equals(defeatedId)&&m.health==0),"Return revived defeated enemy");
        long power=gp.levelM.progression.power();Path save=output.resolve("test-save.properties");gp.levelM.save(save);
        GamePanel restored=new GamePanel();restored.setupGame();restored.levelM.load(save);
        check(restored.levelM.progression.power()==power&&restored.levelM.progression.unlocked(2),"Save lost progression");
        check(restored.monsters.stream().noneMatch(m->m.persistentId.equals(defeatedId)),"Save revived defeated enemy");
        check(restored.fogOfWar.getState(10,40)==FogOfWar.EXPLORED,"Save lost exploration");
        restored.levelM.enter(2,8,44);check(restored.fogOfWar.getState(35,24)==FogOfWar.EXPLORED,"Other-region fog lost");
        restored.levelM.beginTransition(3);for(int i=0;i<61;i++) restored.levelM.updateTransition();
        check(restored.levelM.currentLevel==3&&restored.gameState==GamePanel.GameState.PLAYING,"Fade transition failed");
        check(restored.tileM.collisionLayer[restored.player.worldX/48][restored.player.worldY/48]==0,"Transition arrival blocked");
        Path malformed=output.resolve("invalid-save.properties");Files.writeString(malformed,"version=999\n");
        try {restored.levelM.load(malformed);throw new AssertionError("Invalid save accepted");} catch(java.io.IOException expected) {}
        check(restored.levelM.currentLevel==3,"Failed load mutated world");
        check(restored.gameState!=GamePanel.GameState.GAME_COMPLETE,"Power triggered victory");
        restored.levelM.onDragonDefeated();check(restored.gameState==GamePanel.GameState.GAME_COMPLETE,"Boss callback failed");
        System.out.println("PASS: rewards, gate thresholds, fog, return travel, save/load, fades and boss callback");
        System.out.println("Swing render captures: "+output.toAbsolutePath());
    }
    private static void render(GamePanel gp,Path path) throws Exception {
        BufferedImage image=new BufferedImage(gp.screenWidth,gp.screenHeight,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();gp.paint(g);g.dispose();ImageIO.write(image,"png",path.toFile());
    }
}
