package org.avatar2.lab;

import java.util.*;

/** Validated immutable whole-frame command. Corrections/pose are replaced atomically. */
final class LabCommand {
    final long revision, epoch;
    final String clip;
    final boolean paused;
    final double phase, speed;
    final Map<String,Object> corrections, pose;
    LabCommand(long rev,String clip,boolean paused,double phase,double speed,Map<String,Object> corrections,Map<String,Object> pose){
        this(rev,clip,paused,phase,speed,corrections,pose,System.nanoTime());
    }
    private LabCommand(long rev,String clip,boolean paused,double phase,double speed,Map<String,Object> corrections,Map<String,Object> pose,long epoch){
        revision=rev;this.epoch=epoch;this.clip=clip;this.paused=paused;this.phase=phase;this.speed=speed;
        this.corrections=Collections.unmodifiableMap(corrections);this.pose=Collections.unmodifiableMap(pose);
    }
    static LabCommand empty(long rev){return new LabCommand(rev,"game",true,0,1,new LinkedHashMap<String,Object>(),new LinkedHashMap<String,Object>());}
    LabCommand patch(Map<String,Object> input,Set<String> bones,Set<String> clips){
        keys(input,"expectedRevision","preview","corrections","pose","reset");
        if(input.containsKey("reset")&&!(input.get("reset") instanceof Boolean))throw new IllegalArgumentException("reset must be boolean");
        if(input.containsKey("expectedRevision")&&number(input.get("expectedRevision"),0,Long.MAX_VALUE)!=revision)
            throw new ConcurrentModificationException("Command revision changed");
        LabCommand base=Boolean.TRUE.equals(input.get("reset"))?empty(revision):this;
        String c=base.clip;boolean p=base.paused;double f=base.phase,s=base.speed;
        if(input.containsKey("preview")){
            Map<String,Object> v=LabJson.object(input.get("preview"));keys(v,"clip","paused","phase","speed");
            if(v.containsKey("clip")){c=text(v.get("clip"));if(!c.equals("game")&&!c.equals("bind")&&!clips.contains(c))throw new IllegalArgumentException("Unknown clip: "+c);}
            if(v.containsKey("paused")){if(!(v.get("paused") instanceof Boolean))throw new IllegalArgumentException("paused must be boolean");p=(Boolean)v.get("paused");}
            if(v.containsKey("phase"))f=number(v.get("phase"),0,1);
            if(v.containsKey("speed"))s=number(v.get("speed"),0.05,3);
        }
        Map<String,Object> changes=new LinkedHashMap<String,Object>(base.corrections),poses=new LinkedHashMap<String,Object>(base.pose);
        if(input.containsKey("corrections")){
            changes=new LinkedHashMap<String,Object>();
            for(Map.Entry<String,Object> e:LabJson.object(input.get("corrections")).entrySet()){
                bone(e.getKey(),bones);Map<String,Object> v=LabJson.object(e.getValue());keys(v,"space","rotation","translation");
                String space=v.containsKey("space")?text(v.get("space")):"local";
                if(!Arrays.asList("local","parent","model").contains(space))throw new IllegalArgumentException("Unknown correction space");
                changes.put(e.getKey(),LabJson.map("space",space,"rotation",vector(v.get("rotation"),3,45),"translation",vector(v.get("translation"),3,0.15)));
            }
        }
        if(input.containsKey("pose")){
            poses=new LinkedHashMap<String,Object>();
            for(Map.Entry<String,Object> e:LabJson.object(input.get("pose")).entrySet()){
                bone(e.getKey(),bones);double[] m=vector(e.getValue(),16,10);LabPose.validateRigid(m);poses.put(e.getKey(),m);
            }
        }
        return new LabCommand(revision+1,c,p,f,s,changes,poses,input.containsKey("preview")||Boolean.TRUE.equals(input.get("reset"))?System.nanoTime():base.epoch);
    }
    private static void bone(String name,Set<String> bones){if(!bones.contains(name)||name.equals("NullCamera0"))throw new IllegalArgumentException("Unknown/non-deforming bone: "+name);}
    static void keys(Map<String,Object> in,String...allowed){Set<String> a=new HashSet<String>(Arrays.asList(allowed));for(String key:in.keySet())if(!a.contains(key))throw new IllegalArgumentException("Unknown field: "+key);}
    static String text(Object v){if(!(v instanceof String))throw new IllegalArgumentException("Expected string");return (String)v;}
    static double number(Object v,double min,double max){if(!(v instanceof Number))throw new IllegalArgumentException("Expected number");double n=((Number)v).doubleValue();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException("Number outside "+min+".."+max);return n;}
    static double[] vector(Object v,int count,double bound){
        double[] r=new double[count];if(v==null&&count==3)return r;
        if(!(v instanceof List)||((List<?>)v).size()!=count)throw new IllegalArgumentException("Expected "+count+" numbers");
        for(int i=0;i<count;i++)r[i]=number(((List<?>)v).get(i),-bound,bound);return r;
    }
    Map<String,Object> json(){return LabJson.map("revision",revision,"preview",LabJson.map("clip",clip,"paused",paused,"phase",phase,"speed",speed),"corrections",corrections,"pose",pose);}
}
