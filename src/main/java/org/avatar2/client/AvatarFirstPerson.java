package org.avatar2.client;

import com.wurmonline.client.renderer.ModelRenderMode;
import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.backend.IndexBuffer;
import com.wurmonline.client.renderer.model.collada.AbstractColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.util.BufferUtil;
import org.avatar2.core.FirstPersonCut;
import java.nio.IntBuffer;
import java.util.*;

/** Per-draw index selection; never changes the rig, skin buffers or full-body indices. */
public final class AvatarFirstPerson {
    private static final Map<TriangleMesh,Cut> CUTS=new WeakHashMap<>();
    private static final ThreadLocal<ColladaModel> VIEW=new ThreadLocal<>();
    private AvatarFirstPerson() { }

    public static synchronized void register(ColladaModel model) {
        float cutY=model.getJoint("neck_01").getOriginalBindMatrix().m13;
        for(ColladaGeometry geometry:model.getGeometryArray()) for(TriangleMesh mesh:geometry.getTriangleMeshArray()) {
            if(CUTS.containsKey(mesh)) continue;
            if(mesh.getName().startsWith("Avatar2BodyMesh")) {
                IntBuffer indices=mesh.getIndexBuffer();int[] full=new int[mesh.getIndexCount()];
                for(int i=0;i<full.length;i++) full[i]=indices.get(i);
                FirstPersonCut.Result result=FirstPersonCut.build(mesh.getOriginalVertexBuffer(),full,cutY);
                CUTS.put(mesh,new Cut(result.indices));
            } else if(mesh.getName().startsWith("Avatar2EyesMesh")) CUTS.put(mesh,new Cut(new int[0]));
        }
    }
    public static synchronized void copied(TriangleMesh source,TriangleMesh copy) {
        Cut old=CUTS.get(source);if(old!=null) CUTS.put(copy,new Cut(old.indices));
    }
    /** Actual camera displacement includes collision fallback, mounts and free-look.
     * Free-look can enter the same head volume as the ordinary first-person camera.
     */
    public static boolean nearEyes(boolean free,ModelRenderMode mode,float dx,float dy,float dz) {
        return mode==ModelRenderMode.RENDER_NORMAL && Float.isFinite(dx) && Float.isFinite(dy)
                && Float.isFinite(dz) && dx*dx+dy*dy+dz*dz<=.35f*.35f;
    }
    public static void begin(PlayerBodyRenderable body,boolean near) {
        VIEW.remove();
        if(near && Avatar2Mod.usesAvatar(body) && body.getModelWrapper().getModelData() instanceof AbstractColladaModelData)
            VIEW.set(((AbstractColladaModelData)body.getModelWrapper().getModelData()).getModel());
    }
    public static void end() { VIEW.remove(); }
    public static boolean hideHair(com.wurmonline.client.renderer.model.ModelResourceWrapper wrapper) {
        return VIEW.get()!=null && wrapper!=null && wrapper.getModelData() instanceof AbstractColladaModelData
                && ((AbstractColladaModelData)wrapper.getModelData()).getModel()==VIEW.get();
    }
    private static Cut selected(AbstractColladaModelData data,TriangleMesh mesh) {
        return VIEW.get()!=null && data.getModel()==VIEW.get()?CUTS.get(mesh):null;
    }
    public static synchronized boolean hidden(AbstractColladaModelData data,TriangleMesh mesh) {
        Cut cut=selected(data,mesh);return cut!=null && cut.indices.length==0;
    }
    private static Cut selected(TriangleMesh mesh) {
        return VIEW.get()!=null && AvatarGeometryPrototype.owner(mesh)==VIEW.get()?CUTS.get(mesh):null;
    }
    public static synchronized int indexCount(TriangleMesh mesh,int nativeCount) {
        Cut cut=selected(mesh);return cut==null?nativeCount:cut.indices.length;
    }
    public static synchronized IndexBuffer indexBuffer(TriangleMesh mesh) {
        Cut cut=selected(mesh);if(cut==null) return null;
        if(cut.holder==null) {
            cut.holder=new TriangleMesh("Avatar2FirstPersonIndices");
            IntBuffer buffer=BufferUtil.newIntBuffer(cut.indices.length);buffer.put(cut.indices).flip();
            cut.holder.setIndexBuffer(buffer);
        }
        return cut.holder.getIBO();
    }
    /** Copies for native-runtime validation; callers cannot edit the render mask. */
    public static synchronized int[] indices(TriangleMesh mesh) {
        Cut cut=CUTS.get(mesh);return cut==null?null:cut.indices.clone();
    }
    public static synchronized void destroyed(TriangleMesh mesh) {
        Cut cut=CUTS.remove(mesh);if(cut!=null && cut.holder!=null) cut.holder.destroy();
    }
    private static final class Cut {
        final int[] indices;TriangleMesh holder;
        Cut(int[] indices) { this.indices=indices; }
    }
}
