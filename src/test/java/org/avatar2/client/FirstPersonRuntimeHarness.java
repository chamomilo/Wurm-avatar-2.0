package org.avatar2.client;

import com.wurmonline.client.renderer.ModelRenderMode;
import com.wurmonline.client.renderer.backend.IndexBuffer;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.resources.FakeResourceUrl;
import org.avatar2.core.FirstPersonCut;
import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FirstPersonRuntimeHarness {
    public static void run(String root)throws Exception {
        List<String> report=new ArrayList<>();report.add("body,full_triangles,first_person_triangles,removed_triangles,cap_triangles,validation");
        for(String name:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}) {
            File file=new File(root,"assets/runtime/body/"+name+".dae");
            ColladaModel model=GeometryRuntimeHarness.load(file);AvatarGeometryPrototype.register(model);AvatarFirstPerson.register(model);
            for(ColladaGeometry g:model.getGeometryArray())for(TriangleMesh source:g.getTriangleMeshArray()) {
                int[] full=indices(source);int[] cut=AvatarFirstPerson.indices(source);
                require(cut!=null,"Missing mask");
                ColladaModel clone=new ColladaModel(model);TriangleMesh child=null;
                for(ColladaGeometry cg:clone.getGeometryArray())for(TriangleMesh m:cg.getTriangleMeshArray())if(m.getName().equals(source.getName()))child=m;
                require(Arrays.equals(cut,AvatarFirstPerson.indices(child)),"Model copy lost mask");
                ColladaModelData data=new ColladaModelData(new FakeResourceUrl(file.toURI().toURL()),clone);
                TriangleMesh baked=data.bakeDefaultMesh(child);
                require(Arrays.equals(cut,AvatarFirstPerson.indices(baked)),"Baker lost mask");
                require(Arrays.equals(full,indices(source))&&Arrays.equals(full,indices(baked)),"Full body indices changed");
                java.lang.reflect.Field view=AvatarFirstPerson.class.getDeclaredField("VIEW");view.setAccessible(true);
                ThreadLocal<ColladaModel> scope=(ThreadLocal<ColladaModel>)view.get(null);
                scope.set(clone);
                try {
                    require(AvatarFirstPerson.indexCount(baked,full.length)==cut.length,"Baked first-person count mismatch");
                    require(AvatarFirstPerson.indexCount(source,full.length)==full.length,"Another model lost its head");
                    require(AvatarFirstPerson.hidden(data,baked)==(cut.length==0),"Eyes/body draw selection mismatch");
                    if(cut.length>0) {
                        IndexBuffer ibo=AvatarFirstPerson.indexBuffer(baked);
                        require(ibo!=null&&ibo.getNumIndex()==cut.length,"Native first-person IBO missing");
                        require(ibo==AvatarFirstPerson.indexBuffer(baked),"IBO rebuilt every draw");
                        IntBuffer buffer=ibo.getSystemBuffer();
                        for(int i=0;i<cut.length;i++)require(buffer.get(i)==cut[i],"Native IBO differs from mask");
                    }
                } finally {AvatarFirstPerson.end();}
                require(AvatarFirstPerson.indexCount(baked,full.length)==full.length&&AvatarFirstPerson.indexBuffer(baked)==null,
                        "Full-body rendering did not resume after first-person draw");
                if(source.getName().startsWith("Avatar2EyesMesh")) {require(cut.length==0,"Eyes not hidden");continue;}
                float cutY=model.getJoint("neck_01").getOriginalBindMatrix().m13;float[] v=source.getOriginalVertexBuffer();
                FirstPersonCut.Result result=FirstPersonCut.build(v,full,cutY);
                require(Arrays.equals(cut,result.indices),"Non-deterministic mask");
                require(result.removedTriangles>1000&&result.capTriangles>4,"Invalid head/neck cut");
                for(int index:cut)require(v[index*3+1]<=cutY,"Head remained in first-person indices");
                // Every newly cut boundary must now be closed. UV seams weld only for this topology check.
                Map<Long,Integer> before=edges(v,full),after=edges(v,cut);
                for(Map.Entry<Long,Integer> edge:after.entrySet()) {
                    if(before.getOrDefault(edge.getKey(),0)==2 || !before.containsKey(edge.getKey()))
                        require(edge.getValue()==2,"Neck cap is open or non-manifold");
                }
                int firstCap=cut.length-result.capTriangles*3;
                for(int i=firstCap;i<cut.length;i+=3) {
                    int a=cut[i]*3,b=cut[i+1]*3,c=cut[i+2]*3;
                    float ax=v[b]-v[a],az=v[b+2]-v[a+2],bx=v[c]-v[a],bz=v[c+2]-v[a+2];
                    require(Math.abs(ax*bz-az*bx)>1e-9,"Degenerate neck cap triangle");
                }
                report.add(name+","+full.length/3+","+cut.length/3+","+result.removedTriangles+","+result.capTriangles+",closed_cut_source_copy_native_baker_full_body_unchanged");
                System.out.println("AVATAR2_FIRST_PERSON_OK "+name+" removed="+result.removedTriangles+" cap="+result.capTriangles);
                // Stable diagnostic for visual QA of the actual retained/capped topology.
                File obj=new File(root,"build/reports/avatar2/"+name+"-first-person.obj");
                obj.getParentFile().mkdirs();try(PrintWriter writer=new PrintWriter(obj,"UTF-8")) {
                    for(int i=0;i<v.length;i+=3)writer.println("v "+v[i]+" "+v[i+1]+" "+v[i+2]);
                    for(int i=0;i<cut.length;i+=3)writer.println("f "+(cut[i]+1)+" "+(cut[i+1]+1)+" "+(cut[i+2]+1));
                }
                baked.destroy();require(AvatarFirstPerson.indices(baked)==null,"Destroyed mesh retained its mask/IBO holder");
            }
        }
        require(AvatarFirstPerson.nearEyes(false,ModelRenderMode.RENDER_NORMAL,0,0,0),"First person not detected");
        require(AvatarFirstPerson.nearEyes(true,ModelRenderMode.RENDER_NORMAL,0,0,0),"Free camera exposes head interior");
        for(boolean free:new boolean[]{false,true}) {
            require(AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_NORMAL,.35f,0,0),"Camera mask boundary missing");
            require(AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_NORMAL,0,-.2f,.1f),"Camera below eyes exposes head interior");
            require(!AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_NORMAL,.351f,0,0),"Exterior camera lost head");
            require(!AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_NORMAL,0,Float.NaN,0),"Invalid camera selected mask");
            require(!AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_NORMAL,Float.POSITIVE_INFINITY,0,0),"Infinite camera selected mask");
            require(!AvatarFirstPerson.nearEyes(free,ModelRenderMode.RENDER_SHADOW,0,0,0),"Shadow lost head");
        }
        require(!AvatarFirstPerson.nearEyes(false,ModelRenderMode.RENDER_SHADOW,0,0,0),"Shadow lost head");
        require(!AvatarFirstPerson.nearEyes(false,ModelRenderMode.RENDER_NORMAL,1,0,0),"Third person lost head");
        Files.write(new File(root,"build/reports/avatar2/first-person-runtime.csv").toPath(),report,StandardCharsets.UTF_8);
    }
    private static int[] indices(TriangleMesh m) {int[] out=new int[m.getIndexCount()];for(int i=0;i<out.length;i++)out[i]=m.getIndexBuffer().get(i);return out;}
    private static Map<Long,Integer> edges(float[] v,int[] indices) {
        Map<String,Integer> weld=new HashMap<>();int[] ids=new int[v.length/3];
        for(int i=0;i<ids.length;i++) {String p=Math.round(v[i*3]*100000)+":"+Math.round(v[i*3+1]*100000)+":"+Math.round(v[i*3+2]*100000);ids[i]=weld.computeIfAbsent(p,k->weld.size());}
        Map<Long,Integer> edges=new HashMap<>();for(int i=0;i<indices.length;i+=3)for(int j=0;j<3;j++) {
            int a=ids[indices[i+j]],b=ids[indices[i+(j+1)%3]];if(a==b)continue;
            long key=((long)Math.min(a,b)<<32)|(Math.max(a,b)&0xffffffffL);edges.put(key,edges.getOrDefault(key,0)+1);
        }return edges;
    }
    private static void require(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
