package main;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import tile.*;

/** Original code-native environment art. World geometry lives in region recipes. */
public final class WorldRenderer {
    private final GamePanel gp;
    private final Map<Integer,MapData> atlas=new HashMap<>();
    private final Map<Integer,BufferedImage[]> textures=new HashMap<>();
    public WorldRenderer(GamePanel gp) {
        this.gp=gp;
        try {for(int i=1;i<=3;i++) atlas.put(i,MapLoader.loadLevel(i));}
        catch(java.io.IOException e) {throw new IllegalStateException(e);}
        for(int i=1;i<=3;i++) {
            BufferedImage[] set=new BufferedImage[4];
            for(int j=0;j<4;j++) {
                set[j]=new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB);Graphics2D g=set[j].createGraphics();
                Color base=color(i,j==3?17:j);g.setColor(base);g.fillRect(0,0,48,48);
                Random rng=new Random(i*101+j);
                for(int k=0;k<90;k++) {
                    int d=rng.nextInt(17)-8;
                    g.setColor(new Color(clamp(base.getRed()+d),clamp(base.getGreen()+d),clamp(base.getBlue()+d)));
                    g.fillRect(rng.nextInt(24)*2,rng.nextInt(24)*2,j==2?8:2,2);
                }
                g.dispose();
            }textures.put(i,set);
        }
    }
    private static int clamp(int c){return Math.max(0,Math.min(255,c));}
    public static Color color(int region,int terrain) {
        if(region==3&&terrain==18) return new Color(0x365c70);
        int[][] palette={{0x42684b,0xb19b72,0x356f83,0x254d3f,0x9a7954},
            {0x334f50,0x797563,0x4c3f68,0x243d43,0x877054},
            {0x48474b,0x80736a,0xbc5937,0x32343d,0x8b7463}};
        return new Color(palette[region-1][terrain==1?1:terrain==2||terrain==18?2:terrain==8?3:terrain==17?4:0]);
    }
    private int sx(int c){return c*48-gp.player.worldX+gp.player.screenX;}
    private int sy(int r){return r*48-gp.player.worldY+gp.player.screenY;}
    private static boolean waterOrBridge(int t) {return t==2||t==18||t==17;}
    public void drawGround(Graphics2D g) {
        MapData m=gp.tileM.currentMap;int id=m.levelId;
        int left=Math.max(0,(gp.player.worldX-gp.player.screenX)/48),top=Math.max(0,(gp.player.worldY-gp.player.screenY)/48);
        for(int c=left;c<Math.min(m.mapWidth,left+18);c++) for(int r=top;r<Math.min(m.mapHeight,top+14);r++) {
            int t=m.groundLayer[c][r],x=sx(c),y=sy(r);
            int texture=t==1?1:t==2||t==18?2:t==17?3:0;
            g.drawImage(textures.get(id)[texture],x,y,null);
            if(t==1) {
                // Rounded path shoulders; connected neighbours extend the road to tile edges.
                g.drawImage(textures.get(id)[0],x,y,null);
                Shape oldClip=g.getClip();java.awt.geom.Area road=new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Float(x-1,y-1,50,50));
                if(c>0&&(m.groundLayer[c-1][r]==1||m.groundLayer[c-1][r]==17)) road.add(new java.awt.geom.Area(new Rectangle(x,y,25,48)));
                if(c+1<m.mapWidth&&(m.groundLayer[c+1][r]==1||m.groundLayer[c+1][r]==17)) road.add(new java.awt.geom.Area(new Rectangle(x+24,y,24,48)));
                if(r>0&&(m.groundLayer[c][r-1]==1||m.groundLayer[c][r-1]==17)) road.add(new java.awt.geom.Area(new Rectangle(x,y,48,25)));
                if(r+1<m.mapHeight&&(m.groundLayer[c][r+1]==1||m.groundLayer[c][r+1]==17)) road.add(new java.awt.geom.Area(new Rectangle(x,y+24,48,24)));
                g.clip(road);g.drawImage(textures.get(id)[1],x,y,null);g.setClip(oldClip);
            }
            if(t==18) {g.setColor(color(id,t));g.fillRect(x,y,48,48);}
            if(t==2||t==18) {
                // Shore strips are selected from neighbouring terrain, not random images.
                g.setColor(id==3?new Color(0x392f36):new Color(0x8b9a7e));
                if(c>0&&!waterOrBridge(m.groundLayer[c-1][r])) g.fillRect(x,y,5,48);
                if(c+1<m.mapWidth&&!waterOrBridge(m.groundLayer[c+1][r])) g.fillRect(x+43,y,5,48);
                if(r>0&&!waterOrBridge(m.groundLayer[c][r-1])) g.fillRect(x,y,48,5);
                if(r+1<m.mapHeight&&!waterOrBridge(m.groundLayer[c][r+1])) g.fillRect(x,y+43,48,5);
                g.setColor(new Color(255,255,255,35));int shift=(c*13+r*7)%23;
                g.drawLine(x+shift,y+16,x+shift+12,y+16);g.drawLine(x+12,y+34,x+30,y+34);
            } else if(t==17) {
                g.setColor(new Color(0x4a4035));for(int p=0;p<48;p+=8) g.fillRect(x,y+p,48,2);
                g.setColor(new Color(0xc1a178));
                if(c==0||m.groundLayer[c-1][r]!=17) g.fillRect(x+3,y,3,48);
                if(c==m.mapWidth-1||m.groundLayer[c+1][r]!=17) g.fillRect(x+42,y,3,48);
            } else if(t==0&&Math.floorMod(c*71+r*29,7)==0) {
                g.setColor(id==1?new Color(0x8ca666):id==2?new Color(0x74b5a2):new Color(0x79747b));
                g.drawLine(x+17,y+25,x+14,y+19);g.drawLine(x+18,y+25,x+20,y+17);
                if(id==1) {g.setColor(new Color(0xd6ce9b));g.fillRect(x+31,y+30,3,3);}
            }
            if(t==0&&Math.floorMod(c*37+r*53,11)==0) {
                g.setColor(new Color(0,0,0,30));g.fillOval(x+8,y+28,29,10);
                g.setColor(id==1?new Color(0x34593e):id==2?new Color(0x547768):new Color(0x66626a));
                g.fillOval(x+8,y+20,16,14);g.fillOval(x+17,y+16,18,18);
                g.setColor(id==1?new Color(0x91a96b):id==2?new Color(0x93b4a0):new Color(0x8e8182));
                g.fillRect(x+19,y+18,5,3);
            }
        }
    }
    public void drawObjects(Graphics2D g,boolean foreground) {
        MapData m=gp.tileM.currentMap;
        int feet=gp.player.worldY+48;
        for(int r=0;r<m.mapHeight;r++) for(int c=0;c<m.mapWidth;c++) {
            if(m.groundLayer[c][r]!=8||m.collisionLayer[c][r]!=1) continue;
            if(((r+1)*48>feet)!=foreground) continue;
            int x=sx(c),y=sy(r);if(x < -100||x>gp.screenWidth+100||y < -20||y>gp.screenHeight+130) continue;
            tree(g,x,y,m.levelId,c*17+r*13);
        }
        for(var landmark:m.landmarks) {
            if(((landmark.row())*48>feet)!=foreground) continue;
            int x=sx(landmark.col()),y=sy(landmark.row());
            if(x < -180||x>gp.screenWidth+180||y < -100||y>gp.screenHeight+180) continue;
            landmark(g,x,y,landmark.kind(),m.levelId);
        }
        if(!foreground) return;
        for(var gate:m.gates) {
            int x=sx(gate.col()),y=sy(gate.row());
            boolean open=gp.levelM.progression.unlocked(gate.id());
            g.setColor(new Color(8,15,22,90));g.fillOval(x-60,y+18,140,30);
            Color glow=open?new Color(92,230,229):new Color(82,123,169);
            for(int i=4;i>=0;i--) {g.setColor(new Color(glow.getRed(),glow.getGreen(),glow.getBlue(),15+i*10));g.fillOval(x-26-i*5,y-90-i*5,65+i*10,120+i*7);}
            g.setColor(new Color(0x343d49));g.fillRect(x-47,y-98,26,130);g.fillRect(x+40,y-98,26,130);
            g.setColor(new Color(0x89958d));
            for(int n=0;n<5;n++) {g.fillRect(x-45,y-96+n*25,22,21);g.fillRect(x+42,y-96+n*25,22,21);}
            g.fillPolygon(new int[]{x-49,x-25,x+43,x+68,x+58,x-40},new int[]{y-98,y-126,y-126,y-98,y-78,y-78},6);
            g.setColor(glow);g.drawOval(x-16,y-76,46,94);
            int phase=(int)(System.currentTimeMillis()/70%75);
            for(int n=0;n<6;n++) {int yy=y-((phase+n*13)%100);g.fillRect(x+(n*19%45)-12,yy,3,5);}
            if(Math.hypot(gp.player.worldX/48-gate.col(),gp.player.worldY/48-gate.row())<=4) {
                g.setFont(new Font("SansSerif",Font.BOLD,12));g.setColor(Color.WHITE);
                g.drawString(open?"F • Travel through ward":"F • "+gp.levelM.progression.score()+" / "+gp.levelM.progression.requirement(gate.id())+" Score",x-80,y+55);
            }
        }
    }
    private void tree(Graphics2D g,int x,int y,int region,int seed) {
        x+=Math.floorMod(seed,13)-6;y+=Math.floorMod(seed*7,9)-4;
        g.setColor(new Color(0,0,0,55));g.fillOval(x-12,y+30,72,22);
        if(region==3) {
            g.setColor(new Color(0x292d37));g.fillPolygon(new int[]{x-12,x+8,x+32,x+62,x+52,x},new int[]{y+40,y-33,y-64,y+6,y+45,y+48},6);
            g.setColor(new Color(0x60616a));g.fillPolygon(new int[]{x+8,x+32,x+28,x-1},new int[]{y-33,y-64,y+18,y+31},4);
            g.setColor(new Color(0x85808a));g.drawLine(x+32,y-64,x+46,y-20);return;
        }
        g.setColor(new Color(0x3a3030));g.fillRect(x+17,y-26,15,69);
        g.setColor(new Color(0x786047));g.fillRect(x+20,y-23,5,64);
        if(region==2) {
            g.setStroke(new BasicStroke(7));g.setColor(new Color(0x4b5556));
            g.drawLine(x+23,y-4,x-9,y-36);g.drawLine(x+24,y-22,x+48,y-52);g.setStroke(new BasicStroke(1));
            g.setColor(new Color(0x244044));g.fillOval(x-17,y-66,76,45);
            g.setColor(new Color(0x39615c));g.fillOval(x-9,y-70,58,31);
            g.setColor(new Color(0x88bcb0));g.fillOval(x+39,y+22,11,6);return;
        }
        g.setColor(new Color(0x213f3b));g.fillOval(x-24,y-62,94,83);
        g.setColor(new Color(0x2e5945));g.fillOval(x-26,y-68,74,64);g.fillOval(x+2,y-80,65,65);
        g.setColor(new Color(0x48744f));g.fillOval(x-19,y-70,58,43);g.fillOval(x+8,y-79,45,38);
        g.setColor(new Color(0x64865a));g.fillOval(x-9,y-69,24,13);g.fillOval(x+22,y-75,19,11);
        g.setColor(new Color(0x355c43));g.fillOval(x+14,y-32,43,29);
    }
    private void landmark(Graphics2D g,int x,int y,String kind,int region) {
        if(region==1&&(kind.equals("garden")||kind.equals("cottage")||kind.equals("farmhouse"))) {
            LocationArt.draw(g,x,y,kind);return;
        }
        g.setColor(new Color(0,0,0,65));g.fillOval(x-70,y-15,150,45);
        if(kind.equals("village")||kind.equals("laboratory")) {
            g.setColor(new Color(0x8d8975));g.fillRect(x-53,y-82,104,80);
            g.setColor(new Color(0x42463f));for(int n=0;n<4;n++) g.fillRect(x-53+n*34,y-82,5,80);
            g.setColor(new Color(region==1?0x78564b:0x435759));
            g.fillPolygon(new int[]{x-70,x-45,x+46,x+68},new int[]{y-78,y-130,y-130,y-78},4);
            g.setColor(new Color(0xb79875));for(int n=0;n<5;n++) g.drawLine(x-44-n*4,y-128+n*10,x+46+n*4,y-128+n*10);
            g.setColor(new Color(0x292f32));g.fillRect(x-12,y-45,23,45);g.fillRect(x-42,y-60,18,22);g.fillRect(x+24,y-60,18,22);
        } else if(kind.equals("cemetery")) {
            for(int a=-1;a<=1;a++) for(int b=0;b<2;b++) {
                g.setColor(new Color(0x8e9991));g.fillRoundRect(x+a*40,y-b*42-25,21,30,8,8);
                g.setColor(new Color(0x555e60));g.drawLine(x+a*40+10,y-b*42-19,x+a*40+10,y-b*42-3);
            }
        } else {
            g.setColor(new Color(0x737e7d));
            for(int a=-1;a<=1;a+=2) {g.fillRect(x+a*56-12,y-80,25,80);g.fillRect(x+a*56-17,y-85,35,13);}
            g.setColor(new Color(0xa7aba0));g.fillOval(x-32,y-20,64,25);
            g.setColor(new Color(region==1?0xa3d9d0:region==2?0x91c4b0:0xd9a27b));
            g.fillPolygon(new int[]{x,x+13,x,x-13},new int[]{y-65,y-39,y-20,y-39},4);
        }
    }
    public void drawHud(Graphics2D g) {
        g.setColor(new Color(14,22,29,225));g.fillRoundRect(16,16,365,88,14,14);
        g.setColor(new Color(0xd9d0b3));g.setFont(new Font("Serif",Font.BOLD,19));g.drawString(gp.tileM.currentMap.levelName,29,42);
        g.setFont(new Font("SansSerif",Font.PLAIN,13));
        Progression p=gp.levelM.progression;g.drawString("Score  "+p.score()+"    HP  "+gp.player.getHealth(),29,66);
        int gate=!p.unlocked(1)?1:!p.unlocked(2)?2:0;
        g.setColor(new Color(0x91b9b6));g.drawString(gate==0?"Both wards open • Seek the dragon":"Next ward  "+p.requirement(gate)+"  •  "+Math.max(0,p.requirement(gate)-p.score())+" Score needed",29,88);
        g.setColor(new Color(14,22,29,220));g.fillRoundRect(16,gp.screenHeight-55,gp.screenWidth-32,41,10,10);
        g.setColor(new Color(0xd9d0b3));g.drawString(gp.levelM.notification().isEmpty()?"WASD move · Mouse / E fight · Q dodge · F gate · M map · F5 save · F9 load":gp.levelM.notification(),29,gp.screenHeight-29);
    }
    public void drawAtlas(Graphics2D g,float zoom,float panX,float panY) {
        g.setColor(new Color(0x111e28));g.fillRect(0,0,gp.screenWidth,gp.screenHeight);
        g.setFont(new Font("Serif",Font.BOLD,27));g.setColor(new Color(0xe4d8b6));g.drawString("DEAD_END  /  The broken wards",30,42);
        g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(0x98b0b4));g.drawString("WESTERN VALLEY                         POISONED BASIN                         CROWN MOUNTAINS",30,69);
        float scale=3.15f*zoom,ox=35+panX,oy=152+panY;
        Shape clip=g.getClip();g.clipRect(10,85,gp.screenWidth-20,gp.screenHeight-160);
        int offset=0;
        for(int i=1;i<=3;i++) {
            MapData m=atlas.get(i);boolean revealed=i==1||gp.levelM.progression.unlocked(i-1);
            float x0=ox+offset*scale,y0=oy+(3-i)*8*scale;
            FogOfWar fog=gp.levelM.fog(i);
            for(int c=0;c<m.mapWidth;c++) for(int r=0;r<m.mapHeight;r++) {
                Color base=revealed?color(i,m.groundLayer[c][r]):new Color(0x26333e);
                if(revealed&&fog!=null&&fog.getState(c,r)>0) base=base.brighter();
                g.setColor(base);g.fillRect((int)(x0+c*scale),(int)(y0+r*scale),(int)Math.ceil(scale),(int)Math.ceil(scale));
            }
            g.setColor(new Color(0x99aaa5));g.drawRect((int)x0,(int)y0,(int)(m.mapWidth*scale),(int)(m.mapHeight*scale));
            g.setFont(new Font("Serif",Font.BOLD,14));g.setColor(new Color(0xe4d8b6));g.drawString(m.levelName,(int)x0,(int)y0-12);
            if(revealed) {
                for(var gate:m.gates) {g.setColor(gp.levelM.progression.unlocked(gate.id())?Color.CYAN:new Color(0xdab67c));g.fillRect((int)(x0+gate.col()*scale)-3,(int)(y0+gate.row()*scale)-3,7,7);}
                if(zoom>=1.5) for(var l:m.landmarks) {
                    g.setColor(new Color(0xe8d8b1));g.setFont(new Font("SansSerif",Font.PLAIN,10));
                    g.drawString(l.name(),(int)(x0+l.col()*scale),(int)(y0+l.row()*scale));
                }
            } else {g.setColor(new Color(0x93a0aa));g.drawString("Beyond the sealed ward",(int)x0+15,(int)(y0+m.mapHeight*scale/2));}
            if(gp.levelM.currentLevel==i) {g.setColor(Color.WHITE);g.fillOval((int)(x0+gp.player.worldX/48.0*scale)-4,(int)(y0+gp.player.worldY/48.0*scale)-4,8,8);}
            if(i<3) {g.setColor(new Color(0x91b9b6));g.drawLine((int)(x0+m.mapWidth*scale),(int)(y0+24*scale),(int)(x0+(m.mapWidth+5)*scale),(int)(y0+24*scale));}
            offset+=m.mapWidth+5;
        }
        g.setClip(clip);g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(0xc2c9bb));
        g.drawString("White: you   ·   Cyan: open ward   ·   Gold: sealed ward   ·   Bright terrain: explored",30,gp.screenHeight-52);
        g.drawString("Scroll: zoom / reveal labels   ·   Drag: pan   ·   Home: fit all   ·   M / Esc: return",30,gp.screenHeight-29);
    }
}
