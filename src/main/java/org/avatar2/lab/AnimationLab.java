package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.*;
import org.avatar2.client.AvatarGeometryPrototype;
import org.avatar2.client.AvatarHeightPrototype;
import org.avatar2.client.AvatarEquipmentSockets;
import org.avatar2.client.AvatarAgeSkin;
import org.avatar2.client.AvatarRuntimeState;
import org.avatar2.core.AvatarProfile;
import org.avatar2.core.GeometryProbe;
import org.avatar2.core.HeightLimits;

/** Loopback commands never touch Wurm objects: only the animation worker does. */
public final class AnimationLab {
    private static final Logger LOG=Logger.getLogger("Avatar2.Lab");
    private static final ThreadLocal<Frame> FRAME=new ThreadLocal<Frame>();
    // Values contain names/matrices only, never joints that could retain a weak model key.
    private static final Map<ColladaModel,Map<String,Matrix4f[]>> RESTORE=Collections.synchronizedMap(new WeakHashMap<ColladaModel,Map<String,Matrix4f[]>>());
    private static final LabCommand NO_TRIAL=LabCommand.empty(0);
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static volatile Rig target;
    private static volatile Rig paperTarget;
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
    public static boolean isEnabled(){return enabled;}
    /** Studio reads the same immutable command as HTTP, keeping only visible controls alive. */
    public static synchronized StudioControls studioControls(boolean keepAlive){
        Rig rig=target;if(enabled&&keepAlive)contact();
        LabCommand shown=rig==null?command:appearance(rig,command);
        return new StudioControls(enabled&&rig!=null,rig==null?17:rig.height.baselineMetres*shown.heightScale*10,
                shown.geometry,shown.clip,shown.ageSkin,
                rig==null?Collections.singleton("original"):AvatarAgeSkin.available(rig.model),
                rig==null?Collections.singleton("none"):AvatarGeometryPrototype.availableShapes(rig.model),
                rig!=null&&AvatarGeometryPrototype.breastSupported(rig.model));
    }
    public static synchronized void studioHeight(float dirts){
        requireStudio();submit(LabJson.map("heightDirts",dirts));
    }
    public static synchronized void studioShape(String shape){
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        if("blend".equals(shape)) { submit(LabJson.map("geometry",LabCommand.geometryJson(g.asBlend()))); return; }
        if("proportions".equals(shape)) {
            GeometryProbe next = new GeometryProbe(g.torsoWidth,g.torsoDepth,shape,1,0,0,0,g.buildMuscle,g.buildFat,g.buildBreast,g.buildShoulders,g.headSize,g.agePreset);
            submit(LabJson.map("geometry",LabCommand.geometryJson(next))); return;
        }
        GeometryProbe next=new GeometryProbe(g.torsoWidth,g.torsoDepth,shape,shape.equals("none")?0:g.shape.equals("none")?1:g.shapeMix).withHeadSize(g.headSize);
        submit(LabJson.map("geometry",LabCommand.geometryJson(next)));
    }
    public static synchronized void studioMix(float mix){
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        if(g.shape.equals("none"))throw new IllegalStateException("Select a shape first");
        submit(LabJson.map("geometry",LabCommand.geometryJson(new GeometryProbe(g.torsoWidth,g.torsoDepth,g.shape,mix,
                g.blendAthletic,g.blendHeavy,g.blendOlder,g.buildMuscle,g.buildFat,g.buildBreast,g.buildShoulders,g.headSize,g.agePreset))));
    }
    public static synchronized void studioContribution(String name,float value){
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        if(!"blend".equals(g.shape))throw new IllegalStateException("Select Blend first");
        if(!Arrays.asList("Athletic","Heavy","Older").contains(name))throw new IllegalArgumentException("Unknown shape contribution");
        GeometryProbe next=g.withContribution(Arrays.asList("Athletic","Heavy","Older").indexOf(name),value);
        submit(LabJson.map("geometry",LabCommand.geometryJson(next)));
    }
    public static synchronized void studioMotion(String clip){
        requireStudio();submit(LabJson.map("preview",LabJson.map("clip",clip,"paused",false,"phase",0,"speed",1)));
    }
    public static synchronized void studioBuild(String name,float value){
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        if(!"proportions".equals(g.shape))throw new IllegalStateException("Select Build first");
        if(!Arrays.asList("Muscle","Fat","Breast","Shoulders").contains(name))throw new IllegalArgumentException("Unknown build control");
        GeometryProbe next=new GeometryProbe(g.torsoWidth,g.torsoDepth,g.shape,g.shapeMix,0,0,0,
                "Muscle".equals(name)?value:g.buildMuscle,"Fat".equals(name)?value:g.buildFat,
                "Breast".equals(name)?value:g.buildBreast,"Shoulders".equals(name)?value:g.buildShoulders,g.headSize,g.agePreset);
        submit(LabJson.map("geometry",LabCommand.geometryJson(next)));
    }
    public static synchronized void studioSkin(String preset){
        requireStudio();submit(LabJson.map("ageSkin",preset));
    }
    public static synchronized void studioAge(String preset) {
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        if(!"proportions".equals(g.shape))throw new IllegalStateException("Select Build before body age");
        org.avatar2.core.BodyAge.validate(preset);
        submit(LabJson.map("geometry",LabCommand.geometryJson(g.withAgePreset(preset)),"ageSkin",preset));
    }
    public static synchronized void studioHeadSize(float value){
        requireStudio();GeometryProbe g=appearance(target,command).geometry;
        submit(LabJson.map("geometry",LabCommand.geometryJson(g.withHeadSize(value))));
    }
    /** Explicit Studio Save promotes only the selected appearance, never motion/pose tests. */
    public static synchronized AvatarProfile studioProfile(){
        requireStudio();expireLease();
        AvatarProfile profile=AvatarRuntimeState.profile();
        if(!profile.enabled)throw new IllegalStateException("Enable the Avatar body first");
        LabCommand shown=appearance(target,command,profile);
        if(shown.geometry.torsoWidth!=0 || shown.geometry.torsoDepth!=0)
            throw new IllegalStateException("Reset the temporary torso width/depth test first");
        profile.shape=shown.geometry.shape;profile.shapeMix=shown.geometry.shapeMix;
        profile.blendAthletic=shown.geometry.blendAthletic;profile.blendHeavy=shown.geometry.blendHeavy;
        profile.blendOlder=shown.geometry.blendOlder;
        profile.buildMuscle=shown.geometry.buildMuscle;profile.buildFat=shown.geometry.buildFat;
        profile.buildBreast=shown.geometry.buildBreast;
        profile.buildShoulders=shown.geometry.buildShoulders;
        profile.headSize=shown.geometry.headSize;
        profile.agePreset=shown.geometry.agePreset;
        // Preserve the native-height sentinel unless the user actually selected a trial height.
        if(command.heightOverride)profile.heightMetres=target.height.baselineMetres*shown.heightScale;
        profile.ageSkin=shown.ageSkin;profile.preset=AvatarProfile.Preset.CUSTOM;
        profile.normalize();return profile;
    }
    /** Render readers use immutable commands and never modify animation/mesh state. */
    public static String skinFor(ColladaModel model){
        Rig rig=rigFor(model);if(rig==null)return "original";
        LabCommand trial=rig==target&&enabled&&System.nanoTime()-lastContact<=30_000_000_000L?command:NO_TRIAL;
        return appearance(rig,trial).ageSkin;
    }
    public static synchronized void studioReset(){
        requireStudio();submit(LabJson.map("reset",true));
    }
    private static void requireStudio(){if(!enabled||target==null)throw new IllegalStateException("Enable Animation Lab and show the local body");}
    public static final class StudioControls {
        public final boolean ready,breastSupported;public final float heightDirts,shapeMix,blendAthletic,blendHeavy,blendOlder,buildMuscle,buildFat,buildBreast,buildShoulders,headSize;
        public final String shape,clip,ageSkin,agePreset;public final Set<String> shapes,skins;
        StudioControls(boolean ready,float height,GeometryProbe g,String clip,String skin,Set<String> skins,Set<String> shapes,boolean breastSupported){
            this.ready=ready;heightDirts=height;this.shape=g.shape;shapeMix=g.shapeMix;this.clip=clip;
            blendAthletic=g.blendAthletic;blendHeavy=g.blendHeavy;blendOlder=g.blendOlder;
            buildMuscle=g.buildMuscle;buildFat=g.buildFat;
            buildBreast=g.buildBreast;this.breastSupported=breastSupported;
            buildShoulders=g.buildShoulders;
            headSize=g.headSize;
            agePreset=g.agePreset;
            this.ageSkin=skin;this.skins=Collections.unmodifiableSet(new LinkedHashSet<>(skins));
            this.shapes=Collections.unmodifiableSet(new LinkedHashSet<>(shapes));
        }
    }
    public static synchronized void open() throws Exception {
        if(server==null)throw new IllegalStateException("Run /avatar2 lab on first");server.open();
    }
    public static synchronized String status(){return server==null?"Animation Lab off":server.address()+"; "+(target==null?"waiting for Avatar":target.model.getUrl());}
    /** Called for the local WORLD body only, never the paper doll or remote bodies. */
    public static void registerLocalModel(ColladaModel model){
        Rig current=target;if(current==null?model==null:current.model==model)return;
        if(current!=null)AvatarGeometryPrototype.request(current.model,GeometryProbe.NEUTRAL);
        target=model==null?null:new Rig(model);
        telemetry=LabJson.map("status","waiting for visible local Avatar body");
        synchronized(AnimationLab.class){command=LabCommand.empty(command.revision+1);}
        if(model==null)registerPaperModel(null);
    }
    /** Explicit local paper-doll scope; cached templates and remote copies stay neutral. */
    public static void registerPaperModel(ColladaModel model){
        Rig old=paperTarget;if(old==null?model==null:old.model==model)return;
        if(old!=null)AvatarGeometryPrototype.request(old.model,GeometryProbe.NEUTRAL);
        paperTarget=model==null?null:new Rig(model);
    }
    private static Rig rigFor(ColladaModel model){
        Rig world=target,paper=paperTarget;
        return world!=null&&world.model==model?world:paper!=null&&paper.model==model?paper:null;
    }
    private static LabCommand appearance(Rig rig,LabCommand trial){
        return appearance(rig,trial,AvatarRuntimeState.profile());
    }
    private static LabCommand appearance(Rig rig,LabCommand trial,AvatarProfile p){
        if(!p.enabled || rig==paperTarget&&!p.paperDollUsesAvatar)return trial;
        float scale=p.heightMetres==0?1:rig.height.fromMetres(Math.max(HeightLimits.MIN_METRES,Math.min(HeightLimits.MAX_METRES,p.heightMetres)));
        GeometryProbe geometry=p.geometry();
        // Profile sex can change before Wurm replaces the registered local model.
        // Keep the previous binding safe during that transition; explicit Lab
        // commands still go through submit's complete binding validation.
        if(geometry.buildBreast!=.5f&&!AvatarGeometryPrototype.breastSupported(rig.model))
            geometry=new GeometryProbe(geometry.torsoWidth,geometry.torsoDepth,geometry.shape,geometry.shapeMix,
                    geometry.blendAthletic,geometry.blendHeavy,geometry.blendOlder,geometry.buildMuscle,geometry.buildFat,.5f,geometry.buildShoulders,geometry.headSize,geometry.agePreset);
        if(geometry.buildShoulders!=.5f&&!AvatarGeometryPrototype.shouldersSupported(rig.model))
            geometry=new GeometryProbe(geometry.torsoWidth,geometry.torsoDepth,geometry.shape,geometry.shapeMix,
                    geometry.blendAthletic,geometry.blendHeavy,geometry.blendOlder,geometry.buildMuscle,geometry.buildFat,geometry.buildBreast,.5f,geometry.headSize,geometry.agePreset);
        if(!"young".equals(geometry.agePreset)&&!AvatarGeometryPrototype.ageSupported(rig.model,geometry))geometry=geometry.withAgePreset("young");
        return trial.appearance(geometry,scale,p.ageSkin);
    }
    private static void expireLease(){
        long now=System.nanoTime();
        if(enabled&&now-lastContact>30_000_000_000L){
            command=LabCommand.empty(command.revision+1);lastContact=now;
        }
    }
    static synchronized void contact(){expireLease();lastContact=System.nanoTime();}
    static synchronized Map<String,Object> submit(Map<String,Object> input){
        // A reconnect must not renew an already expired experiment. Expire before
        // patching, so a fresh explicit command remains valid after reconnect.
        expireLease();
        Rig rig=target;if(rig==null)throw new IllegalStateException("Local Avatar has not loaded");
        LabCommand next=command.patch(input,rig.bones,rig.clips,rig.height);
        AvatarGeometryPrototype.validate(rig.model,next.geometry);
        AvatarAgeSkin.validate(rig.model,next.ageSkin);
        command=next;contact();return appearance(rig,command).json();
    }
    static Map<String,Object> state(){
        Rig rig=target;return LabJson.map("protocol",1,"enabled",enabled,"command",(rig==null?command:appearance(rig,command)).json(),"schema",rig==null?null:rig.schema,
                "ageSkins",rig==null?Collections.singleton("original"):AvatarAgeSkin.available(rig.model),
                "telemetry",telemetry,"error",error,"capture",LabCapture.status());
    }
    /** Scope covers blending, FK and skinning, with a single immutable revision. */
    public static void beginFrame(ColladaModel model){
        Map<String,Matrix4f[]> saved=RESTORE.remove(model);
        if(saved!=null)for(Map.Entry<String,Matrix4f[]> entry:saved.entrySet()) {
            Joint joint=model.getJoint(entry.getKey());if(joint==null)continue;
            Matrix4f[] matrices=entry.getValue();joint.getBindMatrix().set(matrices[0]);
            joint.getForwardKinematicsMatrix().set(matrices[1]);joint.getForwardKinematicsMatrixTotal().set(matrices[2]);
        }
        AvatarHeightPrototype.prepare(model);
        Rig rig=rigFor(model);
        if(rig==null)return;
        if(enabled&&System.nanoTime()-lastContact>30_000_000_000L){
            synchronized(AnimationLab.class){if(System.nanoTime()-lastContact>30_000_000_000L)command=LabCommand.empty(command.revision+1);}
            lastContact=System.nanoTime();
        }
        boolean lab=rig==target&&enabled;
        LabCommand next=appearance(rig,lab?command:NO_TRIAL);
        boolean hadProbe=AvatarGeometryPrototype.hasProbe(model);
        AvatarGeometryPrototype.request(model,next.geometry);
        if(!lab&&saved==null&&!hadProbe&&next.geometry.isNeutral()&&next.heightScale==1)return;
        FRAME.set(new Frame(rig,next,lab||next.heightScale!=1,lab));
    }
    public static boolean forceFrame(ColladaModel model){Frame f=FRAME.get();return f!=null&&f.rig.model==model;}

    /** Explicit pose/clip experiments keep their requested native sampling speed. */
    public static boolean gameMotion(ColladaModel model) {
        LabCommand c=command;
        return !(enabled && target!=null && target.model==model && System.nanoTime()-lastContact<=30_000_000_000L
                && (!"game".equals(c.clip) || !c.corrections.isEmpty() || !c.pose.isEmpty()));
    }

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
            AvatarHeightPrototype.apply(model,f.command.heightScale);
            if(f.command.heightScale!=1)f.modified=true;
            if(!f.lab)return;
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
        Frame f=FRAME.get();if(f!=null&&f.snapshot!=null&&f.rig.model==model){
            f.snapshot.put("geometry",AvatarGeometryPrototype.describe(model));
            f.snapshot.put("height",AvatarHeightPrototype.describe(model));
            f.snapshot.put("equipmentSockets",AvatarEquipmentSockets.describe(model));
            telemetry=f.snapshot;error="";}
    }
    public static void endFrame(){
        Frame f=FRAME.get();try{
            if(f!=null&&f.modified){restoreAnimation(f);Map<String,Matrix4f[]> matrices=new LinkedHashMap<>();
                for(Map.Entry<Joint,Matrix4f[]> entry:f.baseline.entrySet())matrices.put(entry.getKey().getName(),entry.getValue());
                RESTORE.put(f.rig.model,matrices);}
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
        final Rig rig;final LabCommand command;final boolean active,lab;
        boolean modified;double phase;
        Map<Joint,Matrix4f[]> baseline;Map<Joint,Matrix4f> mixed,predicted;Map<String,Object> snapshot;
        Frame(Rig rig,LabCommand command,boolean active,boolean lab){this.rig=rig;this.command=command;this.active=active;this.lab=lab;}
    }
    private static final class Rig {
        final ColladaModel model;final String id=UUID.randomUUID().toString();
        final List<Joint> joints;final Set<String> bones,clips;
        final Map<Joint,Matrix4f> rest=new LinkedHashMap<Joint,Matrix4f>();
        final Map<String,ColladaAnimation> previews=new HashMap<String,ColladaAnimation>();
        final ForwardKinematics fk=new ForwardKinematics();final Map<String,Object> schema;
        final HeightLimits height;
        Rig(ColladaModel model){
            AvatarGeometryPrototype.register(model);
            AvatarHeightPrototype.register(model);
            height=AvatarHeightPrototype.limits(model);
            this.model=model;joints=new ArrayList<Joint>(model.getJoints().values());
            Collections.sort(joints,new Comparator<Joint>(){public int compare(Joint a,Joint b){return a.getName().compareTo(b.getName());}});
            bones=Collections.unmodifiableSet(new LinkedHashSet<String>(model.getJoints().keySet()));
            clips=Collections.unmodifiableSet(model.getColladaAnimationList()==null?Collections.<String>emptySet():new TreeSet<String>(model.getColladaAnimationList().keySet()));
            List<Object> rows=new ArrayList<Object>();
            for(Joint j:joints){Matrix4f local=j.getParent()==null?new Matrix4f(j.getOriginalBindMatrix()):j.getParent().getOriginalBindMatrix().invert().mult(j.getOriginalBindMatrix());
                rest.put(j,local);rows.add(LabJson.map("name",j.getName(),"parent",j.getParent()==null?null:j.getParent().getName(),"index",j.getBoneIndex(),"restLocal",LabPose.rows(local),"restGlobal",LabPose.rows(j.getOriginalBindMatrix())));
            }
            schema=LabJson.map("id",id,"coordinates","Wurm imported model space; Y up; metres; right-handed rotations; row-major 4x4; column vectors",
                    "rotationOrder","Rx * Ry * Rz; degrees; pivot at joint origin","bones",rows,"clips",clips,"root",model.getRootJointName(),
                    "geometryShapes",AvatarGeometryPrototype.availableShapes(model),"buildBreastSupported",AvatarGeometryPrototype.breastSupported(model),"heightScaleRange",height.scaleRange(),
                    "heightMetresRange",new double[]{HeightLimits.MIN_METRES,HeightLimits.MAX_METRES},
                    "heightDirtsRange",new double[]{13,20},"dirtsPerMetre",HeightLimits.DIRTS_PER_METRE);
        }
        ColladaAnimation preview(String name){ColladaAnimation result=previews.get(name);if(result==null){result=new ColladaAnimation(model.getColladaAnimation(name),model);previews.put(name,result);}return result;}
    }
}
