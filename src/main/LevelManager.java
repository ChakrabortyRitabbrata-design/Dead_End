package main;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import entities.*;
import inventory.*;
import tile.*;

/** Physical gate routing and persistent region sessions. */
public class LevelManager {
    private final GamePanel gp;
    public int currentLevel=1, fadeAlpha;
    public Progression progression=new Progression();
    private final Map<Integer,RegionState> regions=new HashMap<>();
    private record RegionState(FogOfWar fog,List<Monster> monsters,Dragon dragon,Dragon dragon2) {}
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
                } else notifyPlayer("Locked: "+progression.score()+" / "+progression.requirement(gate.id())+" Score needed");
            }
        }
    }
    public void onDragonDefeated() {
        if(currentLevel!=3) return;
        if(gp.dragon!=null) gp.dragon.isDead=true;
        if(gp.dragon2!=null) gp.dragon2.isDead=true;
        progression.award("3-dragon","dragon");
        gp.gameState=GamePanel.GameState.GAME_COMPLETE;
    }
    public void onDragonDefeated(Dragon defeated) {
        if(currentLevel!=3) return;
        defeated.isDead=true;
        if((gp.dragon==null||gp.dragon.isDead)
                &&(gp.dragon2==null||gp.dragon2.isDead)) {
            progression.award("3-dragon","dragon");
            gp.gameState=GamePanel.GameState.GAME_COMPLETE;
        }
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
    private void snapshot() {regions.put(currentLevel,new RegionState(gp.fogOfWar,new ArrayList<>(gp.monsters),gp.dragon,gp.dragon2));}
    public void enter(int id,int col,int row) throws IOException {
        MapLoader.loadLevel(id);
        snapshot();gp.fogOfWar=new FogOfWar(1,1);
        gp.tileM.loadLevel(id);currentLevel=id;
        gp.player.worldX=col*gp.tileSize;gp.player.worldY=row*gp.tileSize;
        gp.player.resetActionState();
        gp.aSetter.setObject();RegionState saved=regions.get(id);
        if(saved!=null) {
            gp.fogOfWar=saved.fog;gp.monsters.clear();gp.monsters.addAll(saved.monsters);gp.dragon=saved.dragon;gp.dragon2=saved.dragon2;
        } else {
            gp.spawnMonstersForLevel();gp.dragon=null;gp.dragon2=null;
            if(id==3&&gp.tileM.currentMap.dragonSpawn!=null) {
                gp.dragon=new Dragon(gp);int[] d=gp.tileM.currentMap.dragonSpawn;gp.dragon.activate(d[0],d[1]);
                gp.dragon2=new Dragon(gp);gp.dragon2.activate(d[0],d[1],gp.dragon.getWaterRegionId());
            }
        }
        gp.dragonActive=id==3&&((gp.dragon!=null&&!gp.dragon.isDead)
                ||(gp.dragon2!=null&&!gp.dragon2.isDead));
        gp.fogOfWar.update(col,row);notifyPlayer(gp.tileM.currentMap.levelName);
    }
    public FogOfWar fog(int region) {return region==currentLevel?gp.fogOfWar:regions.containsKey(region)?regions.get(region).fog:null;}
    public boolean visited(int region) {return region==currentLevel||regions.containsKey(region);}
    public void save(Path path) throws IOException {
        snapshot();Properties p=new Properties();progression.write(p);
        p.setProperty("version","1");p.setProperty("region",Integer.toString(currentLevel));
        p.setProperty("x",Integer.toString(gp.player.worldX));p.setProperty("y",Integer.toString(gp.player.worldY));
        p.setProperty("health",Integer.toString(gp.player.getHealth()));
        p.setProperty("executionerSword.unlocked", Boolean.toString(gp.executionerSwordUnlocked));
        boolean isEquipped = gp.player.isExecutionerSwordEquipped();
        int slotPos = -1;
        if (!isEquipped && gp.inventory != null) {
            for (int i = 0; i < Inventory.TOTAL_SLOTS; i++) {
                Item it = gp.inventory.getSlot(i);
                if (it != null && ("executioner_sword".equalsIgnoreCase(it.id) || "EXECUTIONER SWORD".equalsIgnoreCase(it.name))) {
                    slotPos = i;
                    break;
                }
            }
        }
        boolean owned = isEquipped || (slotPos >= 0);
        p.setProperty("executionerSword.owned", Boolean.toString(owned));
        p.setProperty("executionerSword.equipped", Boolean.toString(isEquipped));
        p.setProperty("executionerSword.slot", Integer.toString(slotPos));
        for(var entry:regions.entrySet()) {
            String prefix="region."+entry.getKey()+".";RegionState s=entry.getValue();
            p.setProperty(prefix+"fog",s.fog.encode());List<String> enemies=new ArrayList<>();
            for(Monster m:s.monsters) if(m.health>0) enemies.add(m.persistentId+":"+m.worldX+":"+m.worldY+":"+m.health);
            p.setProperty(prefix+"enemies",String.join(";",enemies));
            if(s.dragon!=null) {
                p.setProperty(prefix+"dragonDead",Boolean.toString(s.dragon.isDead));
                if(!s.dragon.isDead) {
                    p.setProperty(prefix+"dragonHealth",Integer.toString(s.dragon.health));
                    p.setProperty(prefix+"dragonX",Integer.toString(s.dragon.worldX));
                    p.setProperty(prefix+"dragonY",Integer.toString(s.dragon.worldY));
                    p.setProperty(prefix+"dragonWaterRegion",Integer.toString(s.dragon.getWaterRegionId()));
                }
            }
            if(s.dragon2!=null) {
                p.setProperty(prefix+"dragon2Dead",Boolean.toString(s.dragon2.isDead));
                if(!s.dragon2.isDead) {
                    p.setProperty(prefix+"dragon2Health",Integer.toString(s.dragon2.health));
                    p.setProperty(prefix+"dragon2X",Integer.toString(s.dragon2.worldX));
                    p.setProperty(prefix+"dragon2Y",Integer.toString(s.dragon2.worldY));
                    p.setProperty(prefix+"dragon2WaterRegion",Integer.toString(s.dragon2.getWaterRegionId()));
                }
            }
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
                Dragon dragon=null,dragon2=null;
                if(i==3&&m.dragonSpawn!=null&&!next.rewarded("3-dragon")) {
                    dragon=new Dragon(gp,m);dragon.activate(m.dragonSpawn[0],m.dragonSpawn[1]);
                    dragon2=new Dragon(gp,m);
                    dragon2.activate(m.dragonSpawn[0],m.dragonSpawn[1],dragon.getWaterRegionId());
                    if(Boolean.parseBoolean(p.getProperty(prefix+"dragonDead","false"))) {
                        dragon.restoreDefeated();
                    } else if(p.containsKey(prefix+"dragonHealth")) {
                        int bossHealth=Integer.parseInt(p.getProperty(prefix+"dragonHealth"));
                        int bossX=Integer.parseInt(p.getProperty(prefix+"dragonX"));
                        int bossY=Integer.parseInt(p.getProperty(prefix+"dragonY"));
                        if(bossHealth<0||bossHealth>300) throw new IllegalArgumentException("Invalid dragon health");
                        if(p.containsKey(prefix+"dragonWaterRegion")
                                &&!dragon.restoreWaterRegion(Integer.parseInt(
                                        p.getProperty(prefix+"dragonWaterRegion"))))
                            throw new IllegalArgumentException("Invalid dragon water region");
                        dragon.health=bossHealth;
                        if(bossX<0||bossY<0||bossX>=m.mapWidth*48||bossY>=m.mapHeight*48)
                            throw new IllegalArgumentException("Invalid dragon position");
                        dragon.worldX=bossX;dragon.worldY=bossY;
                        if(!dragon.isOnValidTerrain()) throw new IllegalArgumentException("Dragon is outside traversable terrain");
                    }
                    if(Boolean.parseBoolean(p.getProperty(prefix+"dragon2Dead","false"))) {
                        dragon2.restoreDefeated();
                    } else if(p.containsKey(prefix+"dragon2Health")) {
                        int bossHealth=Integer.parseInt(p.getProperty(prefix+"dragon2Health"));
                        int bossX=Integer.parseInt(p.getProperty(prefix+"dragon2X"));
                        int bossY=Integer.parseInt(p.getProperty(prefix+"dragon2Y"));
                        if(bossHealth<0||bossHealth>300) throw new IllegalArgumentException("Invalid second dragon health");
                        if(p.containsKey(prefix+"dragon2WaterRegion")
                                &&!dragon2.restoreWaterRegion(Integer.parseInt(
                                        p.getProperty(prefix+"dragon2WaterRegion"))))
                            throw new IllegalArgumentException("Invalid second dragon water region");
                        dragon2.health=bossHealth;
                        if(bossX<0||bossY<0||bossX>=m.mapWidth*48||bossY>=m.mapHeight*48)
                            throw new IllegalArgumentException("Invalid second dragon position");
                        dragon2.worldX=bossX;dragon2.worldY=bossY;
                        if(!dragon2.isOnValidTerrain()) throw new IllegalArgumentException("Second dragon is outside traversable terrain");
                    }
                }
                restored.put(i,new RegionState(fog,enemies,dragon,dragon2));
            }
            MapData m=MapLoader.loadLevel(id);
            boolean inBounds=x>=0&&y>=0&&x<m.mapWidth*48&&y<m.mapHeight*48;
            boolean waterPosition=inBounds&&id==3
                    &&(m.groundLayer[x/48][y/48]==2||m.groundLayer[x/48][y/48]==18);
            if(!restored.containsKey(id)||!inBounds
                    ||(m.collisionLayer[x/48][y/48]!=0&&!waterPosition))
                throw new IllegalArgumentException("Invalid saved position");
            gp.fogOfWar=new FogOfWar(1,1);gp.tileM.loadLevel(id);currentLevel=id;progression=next;
            regions.clear();regions.putAll(restored);RegionState s=regions.get(id);
            gp.fogOfWar=s.fog;gp.monsters.clear();gp.monsters.addAll(s.monsters);gp.dragon=s.dragon;gp.dragon2=s.dragon2;
            gp.dragonActive=id==3&&((s.dragon!=null&&!s.dragon.isDead)
                    ||(s.dragon2!=null&&!s.dragon2.isDead));
            gp.player.worldX=x;gp.player.worldY=y;gp.player.restoreHealth(health);gp.aSetter.setObject();
            gp.keyH.clearAllInputs();gp.player.resetActionState();
            gp.fogOfWar.update(x/48,y/48);fadeAlpha=0;transitioning=false;
            gp.gameState=next.rewarded("3-dragon")?GamePanel.GameState.GAME_COMPLETE:GamePanel.GameState.PLAYING;

            boolean esUnlocked = Boolean.parseBoolean(p.getProperty("executionerSword.unlocked", "false"));
            boolean esOwned = Boolean.parseBoolean(p.getProperty("executionerSword.owned", "false"));
            boolean esEquipped = Boolean.parseBoolean(p.getProperty("executionerSword.equipped", "false"));
            int esSlot = Integer.parseInt(p.getProperty("executionerSword.slot", "-1"));

            gp.executionerSwordUnlocked = esUnlocked;
            gp.removeExecutionerSword();

            if (esOwned) {
                ExecutionerSword sword = new ExecutionerSword();
                if (esEquipped && gp.equipmentManager != null) {
                    gp.equipmentManager.equip(EquipmentSlot.MAIN_HAND, sword);
                } else if (gp.inventory != null) {
                    if (esSlot >= 0 && esSlot < Inventory.TOTAL_SLOTS && gp.inventory.getSlot(esSlot) == null) {
                        gp.inventory.setSlot(esSlot, sword);
                    } else {
                        gp.inventory.addItem(sword);
                    }
                }
            }

            notifyPlayer("World restored");
        } catch(RuntimeException e) {throw new IOException("Invalid save: "+e.getMessage(),e);}
    }
}
