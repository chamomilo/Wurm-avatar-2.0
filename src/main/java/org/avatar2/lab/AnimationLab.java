package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;

/** Loopback commands never touch Wurm objects: only the animation worker does. */
public final class AnimationLab {
    private static final Logger LOG=Logger.getLogger("Avatar2.Lab");
    private static final ThreadLocal<Frame> FRAME=new ThreadLocal<Frame>();
    private static final Map<ColladaModel,Map<Joint,Matrix4f[]>> RESTORE=new ConcurrentHashMap<ColladaModel,Map<Joint,Matrix4f[]>>();
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static volatile Rig target;
    private static volatile LabCommand command=LabCommand.empty(0);
    private static volatile boolean enabled;
    private static volatile long lastContact;
    private static volatile Map<String,Object> telemetry=LabJson.map("status","waiting for visible local Avatar body");
    private static volatile String error="";
    private static LabServer server;
    private static boolean shutdownRegistered;

    private AnimationLab() { }
    public static synchronized String start() throws Exception {
        if(server!=null)return server.address();
        LabServer next=new LabServer(); next.start();server=next;lastContact=System.nanoTime();enabled=true;
        if(!shutdownRegistered){Runtime.getRuntime().addShutdownHook(new Thread(AnimationLab::stop,"Avatar2 Lab shutdown"));shutdownRegistered=true;}
        return next.address();
    }
    public static synchronized void stop(){
        enabled=false;command=LabCommand.empty(command.revision+1);
        if(server!=null){server.close();server=null;}LabCapture.clear();
    }
    public static synchronized void open() throws Exception {
        if(server==null)throw new IllegalStateException("Run /avatar2 lab on first");server.open();
    }
    public static synchronized String status(){return server==null?"Animation Lab off":server.address()+"; "+(target==null?"waiting for Avatar":target.model.getUrl());}
    /** Called for the local WORLD body only, never the paper doll or remote bodies. */
    public static void registerLocalModel(ColladaModel model){
        Rig current=target;if(current!=null&&current.model==model)return;
        target=model==null?null:new Rig(model);
        synchronized(AnimationLab.class){command=LabCommand.empty(command.revision+1);}
    }
    static void contact(){lastContact=System.nanoTime();}
    static synchronized Map<String,Object> submit(Map<String,Object> input){
        Rig rig=target;if(rig==null)throw new IllegalStateException("Local Avatar has not loaded");
        command=command.patch(input,rig.bones,rig.clips);contact();return command.json();
    }
    static Map<String,Object> state(){
        Rig rig=target;return LabJson.map("protocol",1,"enabled",enabled,"command",command.json(),"schema",rig==null?null:rig.schema,
                "telemetry",telemetry,"error",error,"capture",LabCapture.status());
    }
    /** Scope covers blending, FK and skinning, with a single immutable revision. */
    public static void beginFrame(ColladaModel model){
        Map<Joint,Matrix4f[]> saved=RESTORE.remove(model);
        if(saved!=null)restoreEngine(saved);
        Rig rig=target;
        if(rig==null||rig.model!=model||!enabled&&saved==null)return;
        if(enabled&&System.nanoTime()-lastContact>30_000_000_000L){
            synchronized(AnimationLab.class){if(System.nanoTime()-lastContact>30_000_000_000L)command=LabCommand.empty(command.revision+1);}
            lastContact=System.nanoTime();
        }
        FRAME.set(new Frame(rig,command,enabled));
    }
    public static boolean forceFrame(ColladaModel model){Frame f=FRAME.get();return f!=null&&f.rig.model==model;}

    /** Called AFTER the original FK has established the unmodified engine baseline. */
    public static boolean beforeForwardKinematics(ColladaModel model){
        Frame f=FRAME.get();if(f==null||!f.active||f.rig.model!=model)return false;
        try{
            f.baseline=new LinkedHashMap<Joint,Matrix4f[]>();
            f.mixed=new LinkedHashMap<Joint,Matrix4f>();
            for(Joint j:f.rig.joints){
                f.baseline.put(j,new Matrix4f[]{new Matrix4f(j.getBindMatrix()),new Matrix4f(j.getForwardKinematicsMatrix()),new Matrix4f(j.getForwardKinematicsMatrixTotal())});
                f.mixed.put(j,new Matrix4f(j.getAnimationMatrix()));
            }
            for(ColladaAnimation clip:model.getLegsAnimation())if(clip!=null){f.phase=clip.getCurrentFraction();break;}
            boolean override=!f.command.clip.equals("game")||!f.command.corrections.isEmpty()||!f.command.pose.isEmpty();
            if(!override)return false;
            // Resolve Wurm's identity-as-missing sentinel from the baseline FK.
            for(Joint j:f.rig.joints){
                Matrix4f local=j.getParent()==null?f.baseline.get(j)[2]:f.baseline.get(j)[1];
                j.getAnimationMatrix().set(local);
            }
            f.modified=true;
            if(!f.command.clip.equals("game")){
                for(Joint j:f.rig.joints)j.getAnimationMatrix().set(f.rig.rest.get(j));
                if(!f.command.clip.equals("bind")){
                    ColladaAnimation clip=f.rig.preview(f.command.clip);
                    double phase=f.command.phase;
                    if(!f.command.paused)phase=(phase+(System.nanoTime()-f.command.epoch)*1e-9*f.command.speed/Math.max(0.001,clip.getLength()))%1;
                    f.phase=phase;f.rig.fk.calculateForwardKinematics((float)phase,clip,false,false,false);
                }else f.phase=f.command.phase;
            }else{
                ColladaAnimation[] clips=model.getLegsAnimation();
                for(ColladaAnimation clip:clips)if(clip!=null){f.phase=clip.getCurrentFraction();break;}
            }
            for(Map.Entry<String,Object> e:f.command.pose.entrySet())model.getJoint(e.getKey()).getAnimationMatrix().set(LabPose.matrix((double[])e.getValue()));
            f.predicted=new LinkedHashMap<Joint,Matrix4f>();
            correctTree(model.getJoint(model.getRootJointName()),new Matrix4f(),f);
            return true;
        }catch(Throwable failure){
            fail(failure);if(f.mixed!=null)restoreAnimation(f);if(f.baseline!=null)restoreEngine(f.baseline);f.modified=false;return false;
        }
    }
    private static void correctTree(Joint joint,Matrix4f parent,Frame f){
        if(joint==null)return;Matrix4f local=joint.getAnimationMatrix();
        Object correction=f.command.corrections.get(joint.getName());
        if(correction!=null)local.set(LabPose.correct(local,parent,LabJson.object(correction)));
        Matrix4f global=parent.mult(local);f.predicted.put(joint,global);
        for(Joint child:joint.getChildList())correctTree(child,global,f);
    }
    /** Copy real matrices BEFORE skinning. Publication waits for successful skinning. */
    public static void afterForwardKinematics(ColladaModel model){
        Frame f=FRAME.get();if(f==null||!f.active||f.baseline==null)return;
        try{
            Map<String,Object> bones=new LinkedHashMap<String,Object>();
            for(Joint j:f.rig.joints){
                Matrix4f[] base=f.baseline.get(j);Matrix4f skin=j.getBindMatrix().mult(j.getOriginalInvertBindMatrix());
                bones.put(j.getName(),LabJson.map("mixedLocal",LabPose.rows(f.mixed.get(j)),"baselineLocal",LabPose.rows(base[1]),
                        "baselineGlobal",LabPose.rows(base[2]),"baselineBind",LabPose.rows(base[0]),"correctedLocal",LabPose.rows(j.getAnimationMatrix()),
                        "predictedGlobal",f.predicted==null?null:LabPose.rows(f.predicted.get(j)),
                        "engineLocal",LabPose.rows(j.getForwardKinematicsMatrix()),"engineGlobal",LabPose.rows(j.getForwardKinematicsMatrixTotal()),
                        "engineBind",LabPose.rows(j.getBindMatrix()),"skinMatrix",LabPose.rows(skin)));
            }
            f.snapshot=LabJson.map("sequence",SEQUENCE.incrementAndGet(),"revision",f.command.revision,"timeMillis",System.currentTimeMillis(),
                    "schemaId",f.rig.id,"model",model.getUrl(),"preview",f.command.clip,"phase",f.phase,
                    "bodyBlend",model.getBodyBlendingFraction(),"legsBlend",model.getLegsBlendingFraction(),
                    "bodyClips",clipStates(model.getBodyAnimation()),"legsClips",clipStates(model.getLegsAnimation()),
                    "moveSpeedModifier",model.getMoveAnimationSpeedModifier(),"bones",bones);
        }catch(Throwable failure){fail(failure);}
    }
    public static void afterSkinning(ColladaModel model){
        Frame f=FRAME.get();if(f!=null&&f.snapshot!=null&&f.rig.model==model){telemetry=f.snapshot;error="";}
    }
    public static void endFrame(){
        Frame f=FRAME.get();try{
            if(f!=null&&f.modified){restoreAnimation(f);RESTORE.put(f.rig.model,f.baseline);}
        }finally{FRAME.remove();}
    }
    private static List<Object> clipStates(ColladaAnimation[] clips){
        List<Object> result=new ArrayList<Object>();for(ColladaAnimation c:clips)if(c!=null)result.add(LabJson.map("name",c.getName(),"phase",c.getCurrentFraction(),"length",c.getLength(),"blend",c.getBlendFraction()));return result;
    }
    private static void restoreAnimation(Frame f){for(Map.Entry<Joint,Matrix4f> e:f.mixed.entrySet())e.getKey().getAnimationMatrix().set(e.getValue());}
    private static void restoreEngine(Map<Joint,Matrix4f[]> saved){for(Map.Entry<Joint,Matrix4f[]> e:saved.entrySet()){
        e.getKey().getBindMatrix().set(e.getValue()[0]);e.getKey().getForwardKinematicsMatrix().set(e.getValue()[1]);e.getKey().getForwardKinematicsMatrixTotal().set(e.getValue()[2]);
    }}
    private static void fail(Throwable failure){error=failure.toString();LOG.log(Level.WARNING,"Animation Lab disabled corrections after error",failure);synchronized(AnimationLab.class){command=LabCommand.empty(command.revision+1);}}
    public static void captureFrame(){if(enabled)LabCapture.onRender(telemetry);}

    private static final class Frame {
        final Rig rig;final LabCommand command;final boolean active;
        boolean modified;double phase;
        Map<Joint,Matrix4f[]> baseline;Map<Joint,Matrix4f> mixed,predicted;Map<String,Object> snapshot;
        Frame(Rig rig,LabCommand command,boolean active){this.rig=rig;this.command=command;this.active=active;}
    }
    private static final class Rig {
        final ColladaModel model;final String id=UUID.randomUUID().toString();
        final List<Joint> joints;final Set<String> bones,clips;
        final Map<Joint,Matrix4f> rest=new LinkedHashMap<Joint,Matrix4f>();
        final Map<String,ColladaAnimation> previews=new HashMap<String,ColladaAnimation>();
        final ForwardKinematics fk=new ForwardKinematics();final Map<String,Object> schema;
        Rig(ColladaModel model){
            this.model=model;joints=new ArrayList<Joint>(model.getJoints().values());
            Collections.sort(joints,new Comparator<Joint>(){public int compare(Joint a,Joint b){return a.getName().compareTo(b.getName());}});
            bones=Collections.unmodifiableSet(new LinkedHashSet<String>(model.getJoints().keySet()));
            clips=Collections.unmodifiableSet(model.getColladaAnimationList()==null?Collections.<String>emptySet():new TreeSet<String>(model.getColladaAnimationList().keySet()));
            List<Object> rows=new ArrayList<Object>();
            for(Joint j:joints){Matrix4f local=j.getParent()==null?new Matrix4f(j.getOriginalBindMatrix()):j.getParent().getOriginalBindMatrix().invert().mult(j.getOriginalBindMatrix());
                rest.put(j,local);rows.add(LabJson.map("name",j.getName(),"parent",j.getParent()==null?null:j.getParent().getName(),"index",j.getBoneIndex(),"restLocal",LabPose.rows(local),"restGlobal",LabPose.rows(j.getOriginalBindMatrix())));
            }
            schema=LabJson.map("id",id,"coordinates","Wurm imported model space; Y up; metres; right-handed rotations; row-major 4x4; column vectors",
                    "rotationOrder","Rx * Ry * Rz; degrees; pivot at joint origin","bones",rows,"clips",clips,"root",model.getRootJointName());
        }
        ColladaAnimation preview(String name){ColladaAnimation result=previews.get(name);if(result==null){result=new ColladaAnimation(model.getColladaAnimation(name),model);previews.put(name,result);}return result;}
    }
}
