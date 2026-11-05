package tile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Small authored recipe format. Geometry is expanded once, never in the renderer. */
public final class WorldDefinition {
    public record Gate(int id, int col, int row, int destination, int arrivalCol, int arrivalRow) {}
    public record Landmark(String kind, int col, int row, String name) {}

    public static MapData load(int id) throws IOException {
        List<String> lines = Files.readAllLines(Path.of("res/world/region" + id + ".txt"));
        String[] header = lines.get(0).split(" ", 5);
        MapData m = new MapData(id, header[4], Integer.parseInt(header[2]), Integer.parseInt(header[3]), 48);
        m.authoredWorld = true;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] t = line.split(" ");
            int x = Integer.parseInt(t[1].matches("\\d+") ? t[1] : t[2]);
            switch (t[0]) {
                case "SPAWN" -> m.playerSpawn = new int[]{x, n(t,2)};
                case "WATER", "FOREST" -> {
                    int y=n(t,2), rx=n(t,3), ry=n(t,4);
                    for(int c=1;c<m.mapWidth-1;c++) for(int r=1;r<m.mapHeight-1;r++) {
                        double dx=(c-x)/(double)rx, dy=(r-y)/(double)ry;
                        double edge=1+0.12*Math.sin(c*.8+r*.6);
                        if(dx*dx+dy*dy<edge) {
                            m.groundLayer[c][r]=t[0].equals("WATER")?2:8;
                            m.collisionLayer[c][r]=1;
                        }
                    }
                }
                case "ROAD" -> {
                    int y=n(t,2), xx=n(t,3), yy=n(t,4), width=n(t,5);
                    int steps=Math.max(Math.abs(xx-x),Math.abs(yy-y))*3;
                    for(int s=0;s<=steps;s++) {
                        double f=s/(double)Math.max(1,steps);
                        clear(m,(int)Math.round(x+(xx-x)*f),(int)Math.round(y+(yy-y)*f),width/2, true);
                    }
                }
                case "CLEAR" -> clear(m,x,n(t,2),n(t,3),false);
                case "LANDMARK" -> {
                    int y=n(t,3); String name=String.join(" ",Arrays.copyOfRange(t,4,t.length));
                    m.landmarks.add(new Landmark(t[1],x,y,name));
                    m.locations.add(new MapData.LocationRecord(name,x-2,y-2,5,5));
                    clear(m,x,y,4,false);
                }
                case "GATE" -> {
                    m.gates.add(new Gate(x,n(t,2),n(t,3),n(t,4),n(t,5),n(t,6)));
                    clear(m,n(t,2),n(t,3),3,false);
                }
                case "BOSS" -> m.dragonSpawn=new int[]{x,n(t,2)};
                case "ENEMY" -> m.enemyRegions.add(new MapData.EnemyRegion(x,n(t,2),n(t,3),n(t,4),0));
                default -> throw new IOException("Unknown world directive: "+t[0]);
            }
        }
        for(int c=0;c<m.mapWidth;c++) for(int r=0;r<m.mapHeight;r++)
            if(c==0||r==0||c==m.mapWidth-1||r==m.mapHeight-1) {
                m.groundLayer[c][r]=8; m.collisionLayer[c][r]=1;
            }
        // Solid wall footprints stop at the landmark's approach tile.
        for(var l:m.landmarks) if(l.kind().equals("village")||l.kind().equals("laboratory")||l.kind().equals("cottage")||l.kind().equals("farmhouse"))
            for(int c=l.col()-1;c<=l.col()+1;c++) for(int r=l.row()-2;r<l.row();r++)
                m.collisionLayer[c][r]=1;
        for(var l:m.landmarks) if(l.kind().equals("garden")) {
            for(int c=l.col()-2;c<=l.col()+2;c++) m.collisionLayer[c][l.row()-3]=1;
            for(int r=l.row()-2;r<l.row();r++) {
                m.collisionLayer[l.col()-2][r]=1;m.collisionLayer[l.col()+2][r]=1;
            }
        }
        if(id==3) for(int c=29;c<50;c++) for(int r=14;r<30;r++)
            if(m.groundLayer[c][r]==2) m.groundLayer[c][r]=18;
        return m;
    }
    private static int n(String[] t,int i) {return Integer.parseInt(t[i]);}
    private static void clear(MapData m,int x,int y,int radius,boolean road) {
        for(int c=x-radius;c<=x+radius;c++) for(int r=y-radius;r<=y+radius;r++) {
            if(c<1||r<1||c>=m.mapWidth-1||r>=m.mapHeight-1) continue;
            if((c-x)*(c-x)+(r-y)*(r-y)>radius*radius+1) continue;
            int old=m.groundLayer[c][r];
            m.groundLayer[c][r]=road?(old==2||old==17?17:1):(old==1?1:0);
            m.collisionLayer[c][r]=0;
        }
    }
}
