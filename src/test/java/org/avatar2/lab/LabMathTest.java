package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector3f;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LabMathTest {
    @Test public void rotationsKeepPivotAndSeparateBoneAndParentAxes(){
        Matrix4f base=new Matrix4f();base.fromAngleAxis((float)(Math.PI/2),new Vector3f(0,0,1));base.setTranslation(1,2,3);
        Matrix4f local=LabPose.correct(base,new Matrix4f(),LabJson.map("space","local","rotation",new double[]{30,0,0},"translation",new double[]{0,0,0}));
        Matrix4f parent=LabPose.correct(base,new Matrix4f(),LabJson.map("space","parent","rotation",new double[]{30,0,0},"translation",new double[]{0,0,0}));
        assertEquals(1,local.m03,0);assertEquals(2,local.m13,0);assertEquals(3,local.m23,0);
        assertEquals(1,parent.m03,0);assertEquals(2,parent.m13,0);assertEquals(3,parent.m23,0);
        assertTrue(Math.abs(local.m20-parent.m20)>.4);
    }
    @Test public void modelAxesAreConvertedThroughParent(){
        Matrix4f parent=new Matrix4f();parent.fromAngleAxis((float)(Math.PI/2),new Vector3f(0,1,0));parent.setTranslation(2,0,0);
        Matrix4f base=new Matrix4f();base.setTranslation(0,1,0);
        Matrix4f local=LabPose.correct(base,parent,LabJson.map("space","model","rotation",new double[]{0,0,20},"translation",new double[]{0,0,0}));
        Matrix4f actual=parent.mult(local),wanted=LabPose.correct(parent.mult(base),new Matrix4f(),LabJson.map("space","parent","rotation",new double[]{0,0,20},"translation",new double[]{0,0,0}));
        assertArrayEquals(LabPose.rows(wanted),LabPose.rows(actual),0.00001f);
    }
    @Test public void jsonRejectsDuplicateKeysNonFiniteAndTrailingData(){
        for(String value:Arrays.asList("{\"x\":1,\"x\":2}","[1e999]","[NaN]","{}x","[01]","[+1]","{\"x\":\"a\n\"}")){
            try{LabJson.parse(value);fail(value);}catch(IllegalArgumentException expected){}
        }
        assertEquals("{\"name\":\"X\\u000aY\",\"value\":[1.0,true,null]}",LabJson.write(LabJson.parse("{\"name\":\"X\\nY\",\"value\":[1,true,null]}")));
    }
    @Test public void validationAndRevisionAreAtomic(){
        LabCommand initial=LabCommand.empty(9);
        Set<String> bones=new HashSet<String>(Arrays.asList("spine_01","NullCamera0"));Set<String> clips=Collections.singleton("walk");
        LabCommand next=initial.patch(LabJson.object(LabJson.parse("{\"expectedRevision\":9,\"corrections\":{\"spine_01\":{\"rotation\":[1,2,3]}}}")),bones,clips);
        assertEquals(10,next.revision);assertTrue(initial.corrections.isEmpty());assertEquals(9,initial.revision);
        for(String bad:Arrays.asList("{\"expectedRevision\":8}","{\"preview\":{\"clip\":\"missing\"}}","{\"reset\":\"true\"}","{\"corrections\":{\"NullCamera0\":{}}}","{\"corrections\":{\"spine_01\":{\"rotation\":[90,0,0]}}}")){
            try{initial.patch(LabJson.object(LabJson.parse(bad)),bones,clips);fail(bad);}catch(IllegalArgumentException|ConcurrentModificationException expected){}
        }
    }
    @Test public void fullPoseRejectsScaleShearReflection(){
        double[] identity={1,0,0,0,0,1,0,1,0,0,1,0,0,0,0,1};LabPose.validateRigid(identity);
        identity[0]=2;try{LabPose.validateRigid(identity);fail();}catch(IllegalArgumentException expected){}
        identity[0]=-1;try{LabPose.validateRigid(identity);fail();}catch(IllegalArgumentException expected){}
    }
}
