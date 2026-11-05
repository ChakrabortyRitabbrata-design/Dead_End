package main;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import entities.*;
import tile.*;

/** Physical gate routing and persistent region sessions. */
public class LevelManager {
    private final GamePanel gp;
    public int currentLevel=1, fadeAlpha;
    public Progression progression=new Progression();
    private final Map<Integer,RegionState> regions=new HashMap<>();
    private record RegionState(FogOfWar fog,List<Monster> monsters,Dragon dragon) {}
    private int ticks, target, arrivalCol, arrivalRow;
    private boolean transitioning;
    private String message="Explore Willow Rest • M: world map";
    private int messageTicks=360;
    public LevelManager(GamePanel gp) {this.gp=gp;}
    public void notifyPlayer(String text) {message=text;messageTicks=240;}
    public String notification() {return messageTicks>0?message:"";}
    public void checkProgression() {
        if(messageTicks>0) messageTicks--;
        boolean interact=gp.keyH.consumeInteract();
        for(var gate:gp.tileM.currentMap.gates) {
            if(Math.hypot(gp.player.worldX/gp.tileSize-gate.col(),gp.player.worldY/gp.tileSize-gate.row())>3) continue;
            if(progression.unlock(gate.id())) notifyPlayer("Ward awakened — region revealed. Press F to travel.");
            if(interact) {
                if(progression.unlocked(gate.id())) {
                    arrivalCol=gate.arrivalCol();arrivalRow=gate.arrivalRow();beginTransition(gate.destination());
                } else notifyPlayer("Locked: "+progression.power()+" / "+progression.requirement(gate.id())+" Power");
            }
        }
    }
    public void onDragonDefeated() {
        if(currentLevel!=3) return;
        progression.award("3-dragon","dragon");
        if(gp.dragon!=null) gp.dragon.isDead=true;
        gp.gameState=GamePanel.GameState.GAME_COMPLETE;
    }
    public void beginTransition(int destination) {
        if(transitioning||destination<1||destination>3||Math.abs(destination-currentLevel)!=1) return;
        if(!progression.unlocked(Math.min(currentLevel,destination))) return;
        var route=gp.tileM.currentMap.gates.stream().filter(g->g.destination()==destination).findFirst();
        if(route.isEmpty()) return;
        arrivalCol=route.get().arrivalCol();arrivalRow=route.get().arrivalRow();
        try {MapLoader.loadLevel(destination);} catch(IOException e) {notifyPlayer("Cannot load destination: "+e.getMessage());return;}
        target=destination;ticks=0;transitioning=true;
        gp.keyH.clearAllInputs();gp.gameState=GamePanel.GameState.LEVEL_TRANSITION;
    }
    public void updateTransition() {
        ticks++;
        if(ticks<=30) fadeAlpha=ticks*255/30;
        else if(ticks==31) {
            try {enter(target,arrivalCol,arrivalRow);} catch(IOException e) {notifyPlayer("Travel failed: "+e.getMessage());}
        } else fadeAlpha=Math.max(0,255-(ticks-31)*255/30);
        if(ticks>=61) {transitioning=false;fadeAlpha=0;gp.gameState=GamePanel.GameState.PLAYING;}
    }
    private void snapshot() {regions.put(currentLevel,new RegionState(gp.fogOfWar,new ArrayList<>(gp.monsters),gp.dragon));}
    public void enter(int id,int col,int row) throws IOException {
        MapLoader.loadLevel(id);
        snapshot();gp.fogOfWar=new FogOfWar(1,1);
        gp.tileM.loadLevel(id);currentLevel=id;
        gp.player.worldX=col*gp.tileSize;gp.player.worldY=row*gp.tileSize;
        gp.player.resetActionState();
        gp.aSetter.setObject();RegionState saved=regions.get(id);
        if(saved!=null) {
            gp.fogOfWar=saved.fog;gp.monsters.clear();gp.monsters.addAll(saved.monsters);gp.dragon=saved.dragon;
        } else {
            gp.spawnMonstersForLevel();gp.dragon=null;
            if(gp.tileM.currentMap.dragonSpawn!=null) {
                gp.dragon=new Dragon(gp);int[] d=gp.tileM.currentMap.dragonSpawn;gp.dragon.activate(d[0],d[1]);
            }
        }
        gp.dragonActive=gp.dragon!=null&&!gp.dragon.isDead;
        gp.fogOfWar.update(col,row);notifyPlayer(gp.tileM.currentMap.levelName);
    }
    public FogOfWar fog(int region) {return region==currentLevel?gp.fogOfWar:regions.containsKey(region)?regions.get(region).fog:null;}
    public boolean visited(int region) {return region==currentLevel||regions.containsKey(region);}
    public void save(Path path) throws IOException {
        snapshot();Properties p=new Properties();progression.write(p);
        p.setProperty("version","1");p.setProperty("region",Integer.toString(currentLevel));
        p.setProperty("x",Integer.toString(gp.player.worldX));p.setProperty("y",Integer.toString(gp.player.worldY));
        p.setProperty("health",Integer.toString(gp.player.getHealth()));
        for(var entry:regions.entrySet()) {
            String prefix="region."+entry.getKey()+".";RegionState s=entry.getValue();
            p.setProperty(prefix+"fog",s.fog.encode());List<String> enemies=new ArrayList<>();
            for(Monster m:s.monsters) if(m.health>0) enemies.add(m.persistentId+":"+m.worldX+":"+m.worldY+":"+m.health);
            p.setProperty(prefix+"enemies",String.join(";",enemies));
        }
        Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());
        Path temp=Files.createTempFile(absolute.getParent(),"dead-end-",".tmp");
        try {
            try(var out=Files.newOutputStream(temp)) {p.store(out,"DEAD_END save v1");}
            try {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e) {Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
        notifyPlayer("World saved");
    }
    public void load(Path path) throws IOException {
        Properties p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}
        try {
            if(!"1".equals(p.getProperty("version"))) throw new IllegalArgumentException("Unsupported save version");
            Progression next=new Progression();next.read(p);
            int id=Integer.parseInt(p.getProperty("region"));
            if(id<1||id>3) throw new IllegalArgumentException("Invalid region");
            int x=Integer.parseInt(p.getProperty("x")),y=Integer.parseInt(p.getProperty("y"));
            int health=Integer.parseInt(p.getProperty("health"));
            if(health<0||health>100) throw new IllegalArgumentException("Invalid health");
            Map<Integer,RegionState> restored=new HashMap<>();
            for(int i=1;i<=3;i++) {
                String prefix="region."+i+".";if(!p.containsKey(prefix+"fog")) continue;
                MapData m=MapLoader.loadLevel(i);FogOfWar fog=new FogOfWar(m.mapWidth,m.mapHeight);
                fog.decode(p.getProperty(prefix+"fog"));List<Monster> enemies=new ArrayList<>();
                for(String item:p.getProperty(prefix+"enemies","").split(";")) {
                    if(item.isEmpty()) continue;String[] a=item.split(":");
                    int mx=Integer.parseInt(a[1]),my=Integer.parseInt(a[2]),hp=Integer.parseInt(a[3]);
                    if(mx<0||my<0||mx>=m.mapWidth*48||my>=m.mapHeight*48||hp<1||hp>100) throw new IllegalArgumentException("Invalid enemy");
                    if(next.rewarded(a[0])) continue;
                    Monster enemy=new Monster(gp,mx,my);enemy.persistentId=a[0];enemy.health=hp;enemies.add(enemy);
                }
                Dragon dragon=null;
                if(m.dragonSpawn!=null&&!next.rewarded("3-dragon")) {dragon=new Dragon(gp);dragon.activate(m.dragonSpawn[0],m.dragonSpawn[1]);}
                restored.put(i,new RegionState(fog,enemies,dragon));
            }
            MapData m=MapLoader.loadLevel(id);
            if(!restored.containsKey(id)||x<0||y<0||x>=m.mapWidth*48||y>=m.mapHeight*48||m.collisionLayer[x/48][y/48]!=0)
                throw new IllegalArgumentException("Invalid saved position");
            gp.fogOfWar=new FogOfWar(1,1);gp.tileM.loadLevel(id);currentLevel=id;progression=next;
            regions.clear();regions.putAll(restored);RegionState s=regions.get(id);
            gp.fogOfWar=s.fog;gp.monsters.clear();gp.monsters.addAll(s.monsters);gp.dragon=s.dragon;gp.dragonActive=s.dragon!=null;
            gp.player.worldX=x;gp.player.worldY=y;gp.player.restoreHealth(health);gp.aSetter.setObject();
            gp.keyH.clearAllInputs();gp.player.resetActionState();
            gp.fogOfWar.update(x/48,y/48);fadeAlpha=0;transitioning=false;
            gp.gameState=next.rewarded("3-dragon")?GamePanel.GameState.GAME_COMPLETE:GamePanel.GameState.PLAYING;
            notifyPlayer("World restored");
        } catch(RuntimeException e) {throw new IOException("Invalid save: "+e.getMessage(),e);}
    }
}
