package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.util.BufferUtil;
import com.wurmonline.math.Vector3f;
import java.nio.*;

/** Private curved patches retain the installed hairstyle's UVs and silhouette. */
final class AvatarHairDetail {
    private static final int STEPS=3;
    private AvatarHairDetail() { }

    static TriangleMesh refine(TriangleMesh source) {
        FloatBuffer p=source.getVertexBuffer(),n=source.getNormalBuffer(),uv=source.getTextureCoordBuffer(0);
        if(p==null||n==null||uv==null)return source;
        int triangles=source.getIndexCount()/3,perPatch=(STEPS+1)*(STEPS+2)/2;
        FloatBuffer points=BufferUtil.newFloatBuffer(triangles*perPatch*3),normals=BufferUtil.newFloatBuffer(points.capacity()),tex=BufferUtil.newFloatBuffer(triangles*perPatch*2);
        IntBuffer indices=BufferUtil.newIntBuffer(source.getIndexCount()*STEPS*STEPS);
        for(int t=0;t<triangles;t++) {
            int[] ids=new int[3];Vector3f[] v=new Vector3f[3],normal=new Vector3f[3];
            for(int j=0;j<3;j++){ids[j]=source.getIndexBuffer().get(t*3+j);v[j]=vector(p,ids[j]);normal[j]=vector(n,ids[j]).normalize();}
            Vector3f face=v[1].subtract(v[0]).cross(v[2].subtract(v[0])).normalize();
            Vector3f[] edges={edge(v[0],v[1],normal[0]),edge(v[1],v[0],normal[1]),edge(v[1],v[2],normal[1]),edge(v[2],v[1],normal[2]),edge(v[2],v[0],normal[2]),edge(v[0],v[2],normal[0])};
            Vector3f centre=new Vector3f();for(Vector3f e:edges)centre.addLocal(e);centre.multLocal(.25f);
            for(Vector3f vertex:v)centre.subtractLocal(vertex.mult(1f/6));
            int[][] grid=new int[STEPS+1][STEPS+1];int base=t*perPatch,count=0;
            for(int row=0;row<=STEPS;row++)for(int col=0;col<=STEPS-row;col++) {
                float b=(float)row/STEPS,c=(float)col/STEPS,a=1-b-c;
                Vector3f linear=mix(v,a,b,c),curved=new Vector3f();
                add(curved,v[0],a*a*a);add(curved,v[1],b*b*b);add(curved,v[2],c*c*c);
                add(curved,edges[0],3*a*a*b);add(curved,edges[1],3*a*b*b);add(curved,edges[2],3*b*b*c);
                add(curved,edges[3],3*b*c*c);add(curved,edges[4],3*c*c*a);add(curved,edges[5],3*c*a*a);add(curved,centre,6*a*b*c);
                // Retain the original triangle's projection/winding, including thin cards.
                float offset=Math.max(-.004f,Math.min(.004f,curved.subtract(linear).dot(face)));
                // Adjacent native patches may have different normals. Keep their shared
                // edges exact so refinement cannot open cracks between hair cards.
                if(a<.00001f||b<.00001f||c<.00001f)offset=0;
                curved=linear.add(face.mult(offset));
                Vector3f norm=mix(normal,a,b,c).normalize();
                if(norm.lengthSquared()<.000001f)norm=face;
                points.put(curved.x).put(curved.y).put(curved.z);normals.put(norm.x).put(norm.y).put(norm.z);
                tex.put(a*uv.get(ids[0]*2)+b*uv.get(ids[1]*2)+c*uv.get(ids[2]*2));
                tex.put(a*uv.get(ids[0]*2+1)+b*uv.get(ids[1]*2+1)+c*uv.get(ids[2]*2+1));grid[row][col]=base+count++;
            }
            for(int row=0;row<STEPS;row++)for(int col=0;col<STEPS-row;col++) {
                indices.put(grid[row][col]).put(grid[row+1][col]).put(grid[row][col+1]);
                if(col<STEPS-row-1)indices.put(grid[row+1][col]).put(grid[row+1][col+1]).put(grid[row][col+1]);
            }
        }
        points.flip();normals.flip();tex.flip();indices.flip();
        TriangleMesh own=new TriangleMesh(source.getName()+"Avatar2Detail");
        own.setColladaMaterial(source.getColladaMaterial());own.setHasMaterial(true);
        own.setVertexBuffer(points);own.setNormalBuffer(normals);own.setTextureCoordBuffer(tex,0,2);own.setIndexBuffer(indices);
        own.setHasNormalBuffer(true);own.setAllowSlim(false);own.shouldRender=source.shouldRender;
        return own;
    }
    private static Vector3f vector(FloatBuffer values,int i){return new Vector3f(values.get(i*3),values.get(i*3+1),values.get(i*3+2));}
    private static Vector3f edge(Vector3f a,Vector3f b,Vector3f normal){return a.mult(2).add(b).subtract(normal.mult(b.subtract(a).dot(normal))).mult(1f/3);}
    private static Vector3f mix(Vector3f[] v,float a,float b,float c){return v[0].mult(a).add(v[1].mult(b)).add(v[2].mult(c));}
    private static void add(Vector3f target,Vector3f p,float weight){target.x+=p.x*weight;target.y+=p.y*weight;target.z+=p.z*weight;}
}
