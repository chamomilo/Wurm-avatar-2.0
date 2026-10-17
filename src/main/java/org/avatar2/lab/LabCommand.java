package org.avatar2.lab;

import java.util.*;
import org.avatar2.core.GeometryProbe;
import org.avatar2.core.HeightLimits;
import org.avatar2.core.AgeSkin;

/** Validated immutable whole-frame command. Corrections/pose are replaced atomically. */
final class LabCommand {
    final long revision, epoch;
    final String clip;
    final boolean paused;
    final double phase, speed;
    final Map<String,Object> corrections, pose;
    final GeometryProbe geometry;
    final float heightScale;
    final String ageSkin;
    final boolean geometryOverride, heightOverride, skinOverride;
    LabCommand(long rev,String clip,boolean paused,double phase,double speed,Map<String,Object> corrections,Map<String,Object> pose){
        this(rev,clip,paused,phase,speed,corrections,pose,GeometryProbe.NEUTRAL,1,"original",System.nanoTime(),false,false,false);
    }
    private LabCommand(long rev,String clip,boolean paused,double phase,double speed,Map<String,Object> corrections,Map<String,Object> pose,GeometryProbe geometry,float heightScale,String ageSkin,long epoch,boolean geometryOverride,boolean heightOverride,boolean skinOverride){
        revision=rev;this.epoch=epoch;this.clip=clip;this.paused=paused;this.phase=phase;this.speed=speed;
        this.corrections=Collections.unmodifiableMap(corrections);this.pose=Collections.unmodifiableMap(pose);
        this.geometry=geometry;
        this.heightScale=heightScale;
        this.ageSkin=ageSkin;
        this.geometryOverride=geometryOverride;this.heightOverride=heightOverride;this.skinOverride=skinOverride;
    }
    LabCommand appearance(GeometryProbe base,float scale,String skin) {
        return new LabCommand(revision,clip,paused,phase,speed,corrections,pose,
                geometryOverride?geometry:base,heightOverride?heightScale:scale,skinOverride?ageSkin:skin,epoch,
                geometryOverride,heightOverride,skinOverride);
    }
    static LabCommand empty(long rev){return new LabCommand(rev,"game",true,0,1,new LinkedHashMap<String,Object>(),new LinkedHashMap<String,Object>());}
    LabCommand patch(Map<String,Object> input,Set<String> bones,Set<String> clips){
        return patch(input,bones,clips,null);
    }
    LabCommand patch(Map<String,Object> input,Set<String> bones,Set<String> clips,HeightLimits height){
        keys(input,"expectedRevision","preview","corrections","pose","geometry","heightScale","heightMetres","heightDirts","ageSkin","reset");
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
        GeometryProbe geometry=base.geometry;
        String ageSkin=input.containsKey("ageSkin")?AgeSkin.validate(text(input.get("ageSkin"))):base.ageSkin;
        int heightInputs=(input.containsKey("heightScale")?1:0)+(input.containsKey("heightMetres")?1:0)+(input.containsKey("heightDirts")?1:0);
        if(heightInputs>1)throw new IllegalArgumentException("Choose one height unit: heightScale, heightMetres or heightDirts");
        float heightScale=input.containsKey("heightScale")?(float)number(input.get("heightScale"),.1,10):base.heightScale;
        if(input.containsKey("heightMetres")||input.containsKey("heightDirts")){
            if(height==null)throw new IllegalArgumentException("Height units require a measured body");
            heightScale=input.containsKey("heightMetres")?height.fromMetres(number(input.get("heightMetres"),1.3,2)):
                    height.fromDirts(number(input.get("heightDirts"),13,20));
        }
        if(height!=null)height.validateScale(heightScale);
        if(input.containsKey("geometry")){
            Map<String,Object> values=LabJson.object(input.get("geometry"));keys(values,"torsoWidth","torsoDepth","shape","shapeMix","blendAthletic","blendHeavy","blendOlder","buildMuscle","buildFat","buildBreast","buildShoulders","headSize","agePreset");
            geometry=new GeometryProbe(values.containsKey("torsoWidth")?(float)number(values.get("torsoWidth"),-1,1):0,
                    values.containsKey("torsoDepth")?(float)number(values.get("torsoDepth"),-1,1):0,
                    values.containsKey("shape")?text(values.get("shape")):"none",
                    values.containsKey("shapeMix")?(float)number(values.get("shapeMix"),0,1):0,
                    values.containsKey("blendAthletic")?(float)number(values.get("blendAthletic"),0,1):0,
                    values.containsKey("blendHeavy")?(float)number(values.get("blendHeavy"),0,1):0,
                    values.containsKey("blendOlder")?(float)number(values.get("blendOlder"),0,1):0,
                    values.containsKey("buildMuscle")?(float)number(values.get("buildMuscle"),0,1):1,
                    values.containsKey("buildFat")?(float)number(values.get("buildFat"),0,1):0,
                    values.containsKey("buildBreast")?(float)number(values.get("buildBreast"),0,1):.5f,
                    values.containsKey("buildShoulders")?(float)number(values.get("buildShoulders"),0,1):.5f,
                    values.containsKey("headSize")?(float)number(values.get("headSize"),0,1):.5f,
                    values.containsKey("agePreset")?text(values.get("agePreset")):"young");
        }
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
        return new LabCommand(revision+1,c,p,f,s,changes,poses,geometry,heightScale,ageSkin,input.containsKey("preview")||Boolean.TRUE.equals(input.get("reset"))?System.nanoTime():base.epoch,
                base.geometryOverride||input.containsKey("geometry"),base.heightOverride||heightInputs>0,base.skinOverride||input.containsKey("ageSkin"));
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
    static Map<String,Object> geometryJson(GeometryProbe g) {
        return LabJson.map("torsoWidth",g.torsoWidth,"torsoDepth",g.torsoDepth,"shape",g.shape,"shapeMix",g.shapeMix,
                "blendAthletic",g.blendAthletic,"blendHeavy",g.blendHeavy,"blendOlder",g.blendOlder,
                "buildMuscle",g.buildMuscle,"buildFat",g.buildFat,"buildBreast",g.buildBreast,"buildShoulders",g.buildShoulders,"headSize",g.headSize,"agePreset",g.agePreset);
    }
    Map<String,Object> json(){return LabJson.map("revision",revision,"preview",LabJson.map("clip",clip,"paused",paused,"phase",phase,"speed",speed),"corrections",corrections,"pose",pose,
            "geometry",geometryJson(geometry),"heightScale",heightScale,"ageSkin",ageSkin);}
}
