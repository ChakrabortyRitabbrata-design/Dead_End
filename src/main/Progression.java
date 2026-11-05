package main;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Score-based progression; stable enemy IDs prevent duplicate rewards. */
public final class Progression {
    private final Properties config = new Properties();
    private long score;
    private final Set<String> rewarded = new HashSet<>();
    private final Set<Integer> unlocked = new HashSet<>();
    public Progression() {
        try (var in = Files.newInputStream(Path.of("res/world/progression.properties"))) { config.load(in); }
        catch (IOException e) { throw new IllegalStateException("Missing progression configuration",e); }
        for(String key:config.stringPropertyNames()) value(key);
    }
    private long value(String key) {
        long v=Long.parseLong(Objects.requireNonNull(config.getProperty(key),key));
        if(v<0) throw new IllegalArgumentException("Negative value: "+key);
        return v;
    }
    public long score() {return score;}
    /** Score threshold needed to unlock the given gate. */
    public long requirement(int gate) {return value("gate."+gate+".score");}
    public boolean unlocked(int gate) {return unlocked.contains(gate);}
    public boolean rewarded(String id) {return rewarded.contains(id);}
    public boolean award(String id,String enemyClass) {
        long s=value(enemyClass+".score");
        if(!rewarded.add(id)) return false;
        score=Math.addExact(score,s); return true;
    }
    public boolean unlock(int gate) {
        if(score<requirement(gate)) return false;
        return unlocked.add(gate);
    }
    public void write(Properties p) {
        p.setProperty("score",Long.toString(score));
        p.setProperty("rewarded",String.join(",",new TreeSet<>(rewarded)));
        for(int i=1;i<=2;i++) p.setProperty("gate."+i,Boolean.toString(unlocked(i)));
    }
    public void read(Properties p) {
        long s=Long.parseLong(p.getProperty("score","0"));
        if(s<0) throw new IllegalArgumentException("Invalid score total");
        score=s; rewarded.clear(); unlocked.clear();
        for(String id:p.getProperty("rewarded","").split(",")) if(!id.isEmpty()) rewarded.add(id);
        for(int i=1;i<=2;i++) if(Boolean.parseBoolean(p.getProperty("gate."+i))) unlocked.add(i);
    }
}
