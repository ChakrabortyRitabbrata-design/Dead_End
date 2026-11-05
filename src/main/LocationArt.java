package main;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;

/** Cached original pixel-scale location compositions; transparent outside the artwork. */
public final class LocationArt {
    private static final Map<String,BufferedImage> CACHE=new HashMap<>();
    public static void draw(Graphics2D g,int x,int y,String kind) {
        g.drawImage(CACHE.computeIfAbsent(kind,LocationArt::build),x-168,y-192,null);
    }
    private static void rect(Graphics2D g,int color,int x,int y,int w,int h) {
        g.setColor(new Color(color));g.fillRect(x,y,w,h);
    }
    private static BufferedImage build(String kind) {
        BufferedImage image=new BufferedImage(384,240,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.translate(192,192);
        if(kind.equals("garden")) garden(g);else house(g,kind.equals("farmhouse"));
        g.dispose();return image;
    }
    private static void flower(Graphics2D g,int x,int y,int color) {
        rect(g,0x466145,x,y,2,8);rect(g,0x74865a,x-3,y+4,4,2);
        rect(g,color,x-4,y-2,10,4);rect(g,color,x-1,y-5,4,10);
        rect(g,0xe3c777,x,y-1,2,2);
    }
    private static void bed(Graphics2D g,int x,int y,int w,int h,boolean vegetables,int seed) {
        rect(g,0x465141,x-3,y-3,w+6,h+6);rect(g,0x928c6b,x-2,y-2,w+4,3);
        rect(g,0x675345,x,y,w,h);Random random=new Random(seed);
        for(int i=0;i<100;i++) rect(g,random.nextBoolean()?0x78614c:0x564a40,x+random.nextInt(w),y+random.nextInt(h),2,2);
        int[] colors={0xd9b4b4,0xc7c698,0xaf9cba,0xc8a276};
        for(int yy=y+8;yy<y+h;yy+=14) for(int xx=x+7;xx<x+w;xx+=13) {
            if(vegetables) {
                g.setColor(new Color(0x496444));g.fillOval(xx-4,yy-4,10,7);
                rect(g,0x91a56d,xx-2,yy-4,2,5);rect(g,0x718c59,xx,yy-2,4,2);
            } else flower(g,xx,yy,colors[random.nextInt(colors.length)]);
        }
    }
    private static void fence(Graphics2D g,int x,int y,int width) {
        rect(g,0x51483b,x,y+9,width,4);rect(g,0x9a8967,x,y+8,width,3);
        rect(g,0x51483b,x,y+23,width,4);rect(g,0x9a8967,x,y+22,width,3);
        for(int xx=x;xx<=x+width;xx+=16) {
            rect(g,0x51483b,xx+1,y+1,7,33);rect(g,0xb7a17b,xx,y,5,31);
            rect(g,0xd1ba8c,xx,y,5,3);
        }
    }
    private static void stones(Graphics2D g,int x,int y,int rows) {
        for(int i=0;i<rows;i++) {
            int xx=x+(i%2)*5, yy=y+i*12;
            g.setColor(new Color(0x525849));g.fillRoundRect(xx-12,yy,26,10,5,5);
            g.setColor(new Color(0x9c9e86));g.fillRoundRect(xx-11,yy,23,7,4,4);
            rect(g,0xb4b29b,xx-8,yy+1,10,2);
        }
    }
    private static void garden(Graphics2D g) {
        g.setColor(new Color(0,0,0,30));g.fillOval(-114,-122,232,140);
        // Fence occupies the same outer tiles as the collision perimeter; entrance stays open.
        fence(g,-118,-166,230);
        for(int yy=-126;yy<-20;yy+=24) {fence(g,-118,yy,12);fence(g,102,yy,12);}
        bed(g,-91,-115,58,45,false,72);bed(g,34,-115,57,45,false,41);
        bed(g,-91,-52,58,40,false,57);bed(g,34,-52,57,40,true,82);
        stones(g,0,-103,10);
        // Trellis over the rear seat, with climbing roses.
        rect(g,0x6d5a45,-28,-142,5,39);rect(g,0x6d5a45,26,-142,5,39);
        rect(g,0xb19e73,-33,-143,69,5);
        rect(g,0x463e33,-24,-112,5,10);rect(g,0x463e33,22,-112,5,10);
        rect(g,0x9a7951,-28,-119,59,6);rect(g,0xb5996d,-28,-128,59,6);
        for(int i=0;i<9;i++) flower(g,-32+i*8,-144+(i%3)*3,0xc8a1ad);
        for(int i=0;i<7;i++) flower(g,-112+i*32,12+(i%2)*7,0xe0c78d);
    }
    private static void window(Graphics2D g,int x,int y) {
        rect(g,0x423d36,x-3,y-3,28,30);rect(g,0xb5a183,x-1,y-1,24,26);
        rect(g,0x35474b,x+2,y+2,18,20);rect(g,0x6d8990,x+3,y+3,7,7);
        rect(g,0xb5a183,x+10,y+1,2,23);rect(g,0xb5a183,x,y+11,22,2);
        rect(g,0x806744,x-9,y,6,25);rect(g,0x806744,x+25,y,6,25);
        rect(g,0xc0ad85,x-5,y+26,33,3);
        bed(g,x-5,y+28,32,9,false,x+142);
    }
    private static void barrel(Graphics2D g,int x,int y) {
        g.setColor(new Color(0x453c32));g.fillOval(x-2,y+18,27,9);
        rect(g,0x95714f,x,y,22,23);
        for(int a=3;a<22;a+=6) rect(g,0x634b39,x+a,y,2,23);
        rect(g,0x4d5650,x,y+4,22,3);rect(g,0x4d5650,x,y+17,22,3);
        g.setColor(new Color(0xb39165));g.fillOval(x,y-4,22,8);
        g.setColor(new Color(0x6e543d));g.drawOval(x,y-4,21,7);
    }
    private static void house(Graphics2D g,boolean farm) {
        g.setColor(new Color(0,0,0,55));g.fillOval(-85,-13,173,31);
        rect(g,0x444d45,-72,-10,144,12);rect(g,0xa6a18a,-69,-12,138,5);
        rect(g,farm?0x988065:0xa1a18f,-69,-91,138,80);
        // Individual stone courses or timber boards with varied, restrained colours.
        Random rng=new Random(farm?17:29);
        for(int yy=-87;yy<-15;yy+=10) for(int xx=-67;xx<65;xx+=19) {
            int d=rng.nextInt(18);g.setColor(farm?new Color(135+d,110+d,80+d):new Color(140+d,145+d,128+d));
            g.fillRect(xx,yy,17,8);
        }
        rect(g,0x554b3c,-71,-94,7,83);rect(g,0x554b3c,64,-94,7,83);
        rect(g,0x554b3c,-69,-93,138,6);rect(g,0x554b3c,-3,-91,6,32);
        // Chimney behind tiled roof.
        rect(g,0x4f5751,38,-165,18,47);
        for(int yy=-162;yy<-122;yy+=8) {rect(g,0x939588,39,yy,15,6);rect(g,0x666e65,46,yy,2,6);}
        rect(g,0xb0ae98,35,-168,24,6);
        Polygon roof=new Polygon(new int[]{-88,-57,57,88},new int[]{-86,-151,-151,-86},4);
        g.setColor(new Color(0x373e3b));g.fillPolygon(new int[]{-92,-59,59,92},new int[]{-82,-155,-155,-82},4);
        Shape clip=g.getClip();g.clip(roof);rect(g,farm?0x795449:0x50666a,-90,-154,180,73);
        for(int row=0;row<8;row++) for(int col=-6;col<=6;col++) {
            int x=col*18+(row%2)*9, y=-150+row*9, v=rng.nextInt(18);
            g.setColor(farm?new Color(125+v,84+v,65+v):new Color(75+v,103+v,108+v));g.fillRect(x,y,16,7);
            rect(g,farm?0xb58b6a:0x8fa3a1,x,y,15,1);
        }
        g.setClip(clip);rect(g,0xc0a27b,-57,-153,114,4);rect(g,0x9d8c6e,-89,-88,178,5);
        // Recessed front door, lintel, individual planks and threshold.
        rect(g,0x3b3c33,-19,-57,38,48);rect(g,0xb6a486,-22,-60,44,5);
        rect(g,0x68503d,-14,-52,28,42);
        for(int x=-12;x<14;x+=7) rect(g,0x957b53,x,-50,2,39);
        rect(g,0xd0b26a,8,-30,3,3);rect(g,0xc0b59a,-22,-9,44,6);
        window(g,-53,-61);window(g,31,-61);stones(g,0,0,3);
        barrel(g,-101,-26);
        // Lantern and a vine soften the facade.
        rect(g,0x3d4540,23,-52,2,18);rect(g,0x4b4a3c,20,-43,9,13);rect(g,0xe4c87e,22,-41,5,8);
        for(int i=0;i<9;i++) {
            rect(g,0x456047,-66+(i%3)*3,-18-i*7,3,9);
            g.setColor(new Color(0x6e8554));g.fillOval(-70+(i%3)*4,-20-i*7,10,5);
        }
        bed(g,92,-61,58,47,farm,farm?131:31);
        if(farm) {bed(g,92,2,58,25,true,122);barrel(g,-128,-23);}
        else for(int i=0;i<5;i++) flower(g,79+i*16,7+(i%2)*5,0xc7adc2);
    }
}
