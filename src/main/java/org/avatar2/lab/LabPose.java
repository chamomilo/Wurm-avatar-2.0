package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector3f;
import java.util.Map;

final class LabPose {
    private LabPose() { }
    static float[] rows(Matrix4f m){return new float[]{m.m00,m.m01,m.m02,m.m03,m.m10,m.m11,m.m12,m.m13,m.m20,m.m21,m.m22,m.m23,m.m30,m.m31,m.m32,m.m33};}
    static Matrix4f matrix(double[] m){return new Matrix4f((float)m[0],(float)m[1],(float)m[2],(float)m[3],(float)m[4],(float)m[5],(float)m[6],(float)m[7],(float)m[8],(float)m[9],(float)m[10],(float)m[11],(float)m[12],(float)m[13],(float)m[14],(float)m[15]);}
    static void validateRigid(double[] m){
        if(Math.abs(m[12])+Math.abs(m[13])+Math.abs(m[14])+Math.abs(m[15]-1)>0.0001)throw new IllegalArgumentException("Pose must be affine row-major");
        for(int a=0;a<3;a++)for(int b=a;b<3;b++){
            double dot=0;for(int r=0;r<3;r++)dot+=m[r*4+a]*m[r*4+b];
            if(Math.abs(dot-(a==b?1:0))>0.002)throw new IllegalArgumentException("Pose may not scale/shear");
        }
        if(matrix(m).determinant()<0.99f)throw new IllegalArgumentException("Pose may not reflect");
    }
    static Matrix4f correct(Matrix4f local,Matrix4f parent,Map<String,Object> correction){
        String space=(String)correction.get("space");double[] rotation=(double[])correction.get("rotation"),translation=(double[])correction.get("translation");
        Matrix4f delta=new Matrix4f();
        for(int i=0;i<3;i++){
            Matrix4f axis=new Matrix4f();axis.fromAngleAxis((float)Math.toRadians(rotation[i]),new Vector3f(i==0?1:0,i==1?1:0,i==2?1:0));delta=delta.mult(axis);
        }
        Matrix4f base=space.equals("model")?parent.mult(local):local;
        Matrix4f result=space.equals("local")?base.mult(delta):delta.mult(base);
        Vector3f offset=new Vector3f((float)translation[0],(float)translation[1],(float)translation[2]);
        if(space.equals("local")){
            Matrix4f orientation=new Matrix4f(base);orientation.setTranslation(0,0,0);offset=orientation.mult(offset);
        }
        // Rotation is always about the joint origin, never the model/parent origin.
        result.setTranslation(base.m03+offset.x,base.m13+offset.y,base.m23+offset.z);
        return space.equals("model")?parent.invert().mult(result):result;
    }
}
