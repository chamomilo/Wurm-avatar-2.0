package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.client.util.BufferUtil;
import com.wurmonline.math.Vector3f;
import java.nio.*;
import java.util.*;

/** A textured, anatomically shaped hairline on Avatar's own cranial surface. */
final class AvatarScalp {
    final float[] vertices,normals,coordinates;
    final int[] indices;
    private final int fingerprint;
    AvatarScalp(TriangleMesh body,int[] selected,float[] rest,AvatarHeadShape head) {
        float[] current=body.getOriginalVertexBuffer(),normal=body.getOriginalNormalBuffer();
        float z0=Float.POSITIVE_INFINITY,z1=-z0,x0=z0,x1=-z0;
        for(int i:selected){z0=Math.min(z0,rest[i*3+2]);z1=Math.max(z1,rest[i*3+2]);x0=Math.min(x0,rest[i*3]);x1=Math.max(x1,rest[i*3]);}
        Builder out=new Builder(current,normal,rest,head,x0,x1,z0,z1);
        for(int t=0;t<selected.length;t+=3) {
            int[] source={selected[t],selected[t+1],selected[t+2]};
            // Four patches per source triangle give a finer contour without changing the rig.
            Point a=new Point(source,1,0,0),b=new Point(source,0,1,0),c=new Point(source,0,0,1);
            Point ab=a.mix(b,.5f),bc=b.mix(c,.5f),ca=c.mix(a,.5f);
            out.triangle(a,ab,ca);out.triangle(ab,b,bc);out.triangle(ca,bc,c);out.triangle(ab,bc,ca);
        }
        vertices=out.array(out.vertices);normals=out.array(out.normals);coordinates=out.array(out.uv);
        indices=new int[vertices.length/3];for(int i=0;i<indices.length;i++)indices[i]=i;
        fingerprint=((Arrays.hashCode(vertices)*31+Arrays.hashCode(normals))*31+Arrays.hashCode(coordinates))*31+Arrays.hashCode(indices);
    }

    /** Equivalent world/portrait snapshots can retain the same uploaded scalp. */
    boolean sameGeometry(AvatarScalp other) {
        return other==this || other!=null&&fingerprint==other.fingerprint&&Arrays.equals(vertices,other.vertices)
                &&Arrays.equals(normals,other.normals)&&Arrays.equals(coordinates,other.coordinates)&&Arrays.equals(indices,other.indices);
    }

    static int[] select(TriangleMesh body,AvatarHeadShape head,int headIndex) {
        float[] v=body.getOriginalVertexBuffer();boolean[] inside=new boolean[v.length/3];
        for(int i=0;i<inside.length;i++) {
            float weight=0;
            for(int slot=0;slot<4;slot++)if(body.getBoneBuffer().get(i*4+slot)==headIndex)weight+=body.getWeightBuffer().get(i*4+slot);
            inside[i]=v[i*3+1]>head.upper&&weight>.999f;
        }
        List<Integer> selected=new ArrayList<>();IntBuffer full=body.getIndexBuffer();
        for(int i=0;i<body.getIndexCount();i+=3)if(inside[full.get(i)]&&inside[full.get(i+1)]&&inside[full.get(i+2)])
            for(int j=0;j<3;j++)selected.add(full.get(i+j));
        int[] result=new int[selected.size()];for(int i=0;i<result.length;i++)result[i]=selected.get(i);
        return result;
    }

    void apply(TriangleMesh mesh,Matrix4f fit,float[] region) {
        Matrix4f inverse=fit.invert();FloatBuffer points=BufferUtil.newFloatBuffer(vertices.length),normal=BufferUtil.newFloatBuffer(normals.length),uv=BufferUtil.newFloatBuffer(coordinates.length);
        for(int i=0;i<vertices.length;i+=3) {
            Vector3f p=inverse.mult(new Vector3f(vertices[i],vertices[i+1],vertices[i+2]));points.put(p.x).put(p.y).put(p.z);
            Vector3f n=new Vector3f(normals[i]*fit.m00,normals[i+1]*fit.m11,normals[i+2]*fit.m22).normalize();normal.put(n.x).put(n.y).put(n.z);
            int k=i/3*2;uv.put(region[0]+coordinates[k]*(region[2]-region[0])).put(region[1]+coordinates[k+1]*(region[3]-region[1]));
        }
        points.flip();normal.flip();uv.flip();IntBuffer triangles=BufferUtil.newIntBuffer(indices.length);triangles.put(indices).flip();
        mesh.freeVBO();mesh.setVertexBuffer(points);mesh.setNormalBuffer(normal);mesh.setIndexBuffer(triangles);mesh.setTextureCoordBuffer(uv,0,2);
        mesh.setHasNormalBuffer(true);mesh.shouldRender=true;
    }

    private static final class Point {
        final int[] source;final float[] weight;
        Point(int[] source,float a,float b,float c){this.source=source;weight=new float[]{a,b,c};}
        Point mix(Point other,float t){return new Point(source,weight[0]*(1-t)+other.weight[0]*t,weight[1]*(1-t)+other.weight[1]*t,weight[2]*(1-t)+other.weight[2]*t);}
        float component(float[] values,int axis){float result=0;for(int i=0;i<3;i++)result+=values[source[i]*3+axis]*weight[i];return result;}
    }
    private static final class Builder {
        final float[] current,normal,rest;final AvatarHeadShape head;final float x0,x1,z0,z1;
        final List<Float> vertices=new ArrayList<>(),normals=new ArrayList<>(),uv=new ArrayList<>();
        Builder(float[] current,float[] normal,float[] rest,AvatarHeadShape head,float x0,float x1,float z0,float z1){this.current=current;this.normal=normal;this.rest=rest;this.head=head;this.x0=x0;this.x1=x1;this.z0=z0;this.z1=z1;}
        float distance(Point p) {
            double front=Math.max(0,Math.min(1,(p.component(rest,2)-z0)/(z1-z0)));
            double x=(p.component(rest,0)-(x0+x1)/2)*2/(x1-x0),range=head.y-head.upper;
            double line=head.y-range*(.95-.60*front*front);
            // A shallow central peak, recessed temples and fine edge variation.
            line-=range*.055*Math.exp(-x*x/.08)*Math.pow(front,6);
            line+=range*.055*Math.exp(-Math.pow((Math.abs(x)-.58)/.18,2))*Math.pow(front,3);
            line+=range*.009*Math.sin(x*87+front*19)*Math.pow(front,3);
            return (float)(p.component(rest,1)-line);
        }
        void triangle(Point a,Point b,Point c) {
            List<Point> clipped=new ArrayList<>(),edge=new ArrayList<>();Point[] points={a,b,c};
            for(int i=0;i<3;i++) {
                Point p=points[i],q=points[(i+1)%3];float dp=distance(p),dq=distance(q);
                if(dp>=0)clipped.add(p);
                if((dp>=0)!=(dq>=0)){Point crossing=p.mix(q,dp/(dp-dq));clipped.add(crossing);edge.add(crossing);}
            }
            for(int i=1;i+1<clipped.size();i++){add(clipped.get(0));add(clipped.get(i));add(clipped.get(i+1));}
            if(edge.size()==2&&edge.get(0).component(rest,2)>z0+(z1-z0)*.35f) {
                // Sparse tapered roots soften the forehead/temple boundary at the skin.
                Point p=edge.get(0).mix(edge.get(1),.18f),q=edge.get(0).mix(edge.get(1),.82f),tip=p.mix(q,.5f);
                float length=.0022f+.0008f*(float)Math.sin(tip.component(rest,0)*931+tip.component(rest,2)*577);
                Vector3f pp=position(p,0),qp=position(q,0),tp=position(tip,length),n=normal(tip);
                if(qp.subtract(pp).cross(tp.subtract(pp)).dot(n)<0){Point swap=p;p=q;q=swap;}
                add(p);add(q);add(tip,length);
            }
        }
        Vector3f normal(Point p){return new Vector3f(p.component(normal,0),p.component(normal,1),p.component(normal,2)).normalize();}
        Vector3f position(Point p,float extension) {
            Vector3f n=normal(p),down=new Vector3f(n.x*n.y,n.y*n.y-1,n.z*n.y).normalize().mult(extension);
            return new Vector3f(p.component(current,0)+n.x*.0008f+down.x,p.component(current,1)+n.y*.0008f+down.y,p.component(current,2)+n.z*.0008f+down.z);
        }
        void add(Point p){add(p,0);}
        void add(Point p,float extension) {
            Vector3f n=normal(p),position=position(p,extension);
            vertices.add(position.x);vertices.add(position.y);vertices.add(position.z);
            normals.add(n.x);normals.add(n.y);normals.add(n.z);
            double x=p.component(rest,0)-(x0+x1)/2,z=p.component(rest,2)-(z0+z1)/2;
            float u=(float)(.5+Math.atan2(x,z)/(2*Math.PI)),v=Math.max(0,Math.min(1,(p.component(rest,1)-head.upper-extension)/(head.y-head.upper)));
            // Longitudinal strand mapping follows the head, with a mild swept flow.
            uv.add(Math.max(0,Math.min(1,u+.04f*(1-v)*(float)Math.sin(u*Math.PI*2))));uv.add(v);
        }
        float[] array(List<Float> values){float[] result=new float[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;}
    }
}
