package org.avatar2.client;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.cell.AttachedCellRenderable;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import java.util.Map;
import java.util.WeakHashMap;

/** Fits the installed Wurm hairstyle to a completed Avatar head pose. */
public final class AvatarHair {
    private static final Map<ColladaModel, State> MODELS = new WeakHashMap<>();
    private static final Map<PlayerBodyRenderable, String> BODY_SEX = new WeakHashMap<>();
    private static final Map<AttachedCellRenderable, HairCopy> COPIES = new WeakHashMap<>();
    private static final ThreadLocal<Draw> DRAW = new ThreadLocal<>();
    private AvatarHair() { }

    public static void register(ColladaModel model, boolean male) {
        State next = new State(male);
        synchronized (AvatarHair.class) { if (!MODELS.containsKey(model)) MODELS.put(model, next); }
    }

    /** Independent copies start at the neutral fit, never at a previous Lab trial. */
    public static synchronized void copied(ColladaModel source, ColladaModel copy) {
        State old = MODELS.get(source);
        if (old != null) MODELS.put(copy, new State(old.male));
    }

    /** Called only after successful native skinning; render readers see one whole frame. */
    public static void publish(ColladaModel model) {
        State state;
        synchronized (AvatarHair.class) { state = MODELS.get(model); }
        if (state == null) return;
        Joint head = model.getJoint("head");
        if (head == null) return;
        Matrix4f fit = AvatarGeometryPrototype.headHairFit(model, state.male);
        if (fit == null) fit = state.fit;
        Matrix4f frame = head.getBindMatrix().mult(head.getOriginalInvertBindMatrix())
                .mult(fit);
        AvatarScalp scalp=AvatarGeometryPrototype.scalp(model);
        synchronized (AvatarHair.class) { state.completed = frame;state.fitCompleted=fit;state.scalp=scalp; }
    }

    static synchronized Matrix4f frame(ColladaModel model) {
        State state = MODELS.get(model);
        return state == null ? null : new Matrix4f(state.completed);
    }

    /** Keep the native hairstyle/colour and item offset path, with a fitted parent. */
    public static Matrix parentMatrix(ModelResourceWrapper wrapper, Matrix parent) {
        ColladaModel model = model(wrapper);
        Matrix4f frame;
        synchronized (AvatarHair.class) {
            State state=MODELS.get(model);
            frame=state==null?null:state.completed;
            if(state==null)DRAW.remove();else DRAW.set(new Draw(model,state.fitCompleted,state.scalp));
        }
        return frame == null || parent == null ? parent : Matrix.mult(parent,
                new Matrix().fromColladaMatrix(frame), new Matrix());
    }

    public static void endDraw() { DRAW.remove(); }

    /** Called by native attachment preparation, only inside its scoped hairstyle draw. */
    public static ModelResourceWrapper prepareCell(AttachedCellRenderable cell,ModelResourceWrapper parent,ModelResourceWrapper source) {
        Draw draw=DRAW.get();
        if(draw==null || draw.model!=model(parent) || draw.scalp==null || source==null || !source.isLoaded())return source;
        String path=source.getResourceUrl()==null?"":source.getResourceUrl().getFilePath().replace('\\','/');
        if(!(path.startsWith("player/hair/male_hair0")||path.startsWith("player/hair/femaleHair0"))||!path.endsWith(".wom"))return source;
        HairCopy own;
        synchronized (AvatarHair.class) { own=COPIES.get(cell); }
        if(own==null) {
            ModelResourceWrapper copy=source.clone();
            ColladaModel hair=model(copy);
            if(hair==null)return source;
            for(ColladaGeometry geometry:hair.getGeometryArray()) {
                TriangleMesh[] oldMeshes=geometry.getTriangleMeshArray(),detail=new TriangleMesh[oldMeshes.length];
                for(int i=0;i<detail.length;i++) {
                    detail[i]=AvatarHairDetail.refine(oldMeshes[i]);
                    if(detail[i]!=oldMeshes[i]){detail[i].setDefaultMaterial();oldMeshes[i].destroy();}
                }
                geometry.setTriangleMeshArray(detail);
            }
            TriangleMesh reference=hair.getGeometryArray()[0].getTriangleMeshArray()[0];
            TriangleMesh scalp=new TriangleMesh("Avatar2HairScalp");
            scalp.setColladaMaterial(reference.getColladaMaterial());scalp.setHasMaterial(true);
            ColladaGeometry addition=new ColladaGeometry("Avatar2HairScalp");addition.setTriangleMeshArray(new TriangleMesh[]{scalp});
            ColladaGeometry[] old=hair.getGeometryArray(),next=java.util.Arrays.copyOf(old,old.length+1);next[old.length]=addition;hair.setGeometryArray(next);
            own=new HairCopy(copy,scalp,path);
            synchronized (AvatarHair.class) { COPIES.put(cell,own); }
        }
        if(!sameFit(own.appliedFit,draw.fit)||!draw.scalp.sameGeometry(own.applied)) {
            boolean first=own.applied==null;
            draw.scalp.apply(own.scalp,draw.fit,own.region);own.appliedFit=draw.fit;
            if(first)own.scalp.setDefaultMaterial();
        }
        own.applied=draw.scalp;
        return own.wrapper;
    }

    private static boolean sameFit(Matrix4f a,Matrix4f b) {
        return a!=null&&a.m00==b.m00&&a.m11==b.m11&&a.m22==b.m22&&a.m03==b.m03&&a.m13==b.m13&&a.m23==b.m23;
    }

    public static boolean isMale(PlayerBodyRenderable body) {
        ColladaModel model = model(body.getModelWrapper());
        synchronized (AvatarHair.class) {
            State state = MODELS.get(model);
            if (state != null) return state.male;
        }
        return body.isMale();
    }

    /** Native hair is cached separately from the body; invalidate it on a sex switch. */
    public static boolean refresh(PlayerBodyRenderable body) {
        String sex = (Avatar2Mod.usesAvatar(body) ? "avatar:" : "native:") + isMale(body);
        synchronized (AvatarHair.class) { return !sex.equals(BODY_SEX.put(body, sex)); }
    }

    private static ColladaModel model(ModelResourceWrapper wrapper) {
        return wrapper != null && wrapper.getModelData() instanceof ColladaModelData
                ? ((ColladaModelData) wrapper.getModelData()).getModel() : null;
    }

    private static final class State {
        final boolean male;
        final Matrix4f fit = new Matrix4f();
        Matrix4f completed;
        Matrix4f fitCompleted;
        AvatarScalp scalp;
        State(boolean male) {
            this.male = male;
            // Calibrated cranial landmarks from the installed Male/female_1_04
            // and the two released Avatar bindings. No Wurm art is embedded.
            fit.m00 = male ? 1.173286f : 1.150135f;
            fit.m11 = male ? 1.051838f : .989861f;
            fit.m22 = male ? 1.055789f : .993178f;
            fit.m03 = male ? 0 : -.000002875f;
            fit.m13 = male ? 1.690934f : 1.556153f;
            fit.m23 = male ? .03443125f : .02693526f;
            completed = new Matrix4f(fit);
            fitCompleted = new Matrix4f(fit);
        }
    }

    private static final class Draw {
        final ColladaModel model;final Matrix4f fit;final AvatarScalp scalp;
        Draw(ColladaModel model,Matrix4f fit,AvatarScalp scalp){this.model=model;this.fit=fit;this.scalp=scalp;}
    }
    private static final class HairCopy {
        final ModelResourceWrapper wrapper;final TriangleMesh scalp;final float[] region;
        AvatarScalp applied;
        Matrix4f appliedFit;
        HairCopy(ModelResourceWrapper wrapper,TriangleMesh scalp,String path) {
            this.wrapper=wrapper;this.scalp=scalp;
            // Opaque strand regions in both DDS row orientations, with an 8px margin.
            float[][] uv=path.contains("femaleHair")?new float[][]{
                    {.1923828125f,.3896484375f,.7255859375f,.6103515625f},
                    {.6181640625f,.3974609375f,.7587890625f,.6025390625f},
                    {.5673828125f,.2841796875f,.6943359375f,.7158203125f},
                    {.5732421875f,.3486328125f,.8505859375f,.6513671875f},
                    {.4755859375f,.2509765625f,.6005859375f,.7490234375f},
                    {.2392578125f,.3642578125f,.6728515625f,.6357421875f},
                    {.3447265625f,.3818359375f,.6513671875f,.6181640625f}}:
                    new float[][]{
                    {.1826171875f,.2568359375f,.4013671875f,.7431640625f},
                    {.6025390625f,.3486328125f,.7607421875f,.6513671875f},
                    {.1982421875f,.2314453125f,.4619140625f,.7685546875f},
                    {.1845703125f,.2177734375f,.4755859375f,.7822265625f},
                    {.4052734375f,.3564453125f,.6220703125f,.6435546875f}};
            int style=1;for(int n=1;n<=uv.length;n++)if(path.contains("0"+n+".wom"))style=n;
            region=uv[style-1];
        }
    }
}
