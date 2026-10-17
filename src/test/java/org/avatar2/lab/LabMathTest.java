package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import com.wurmonline.math.Vector3f;
import org.junit.Test;
import org.avatar2.core.HeightLimits;
import java.util.*;
import static org.junit.Assert.*;

public class LabMathTest {
    @Test public void adultPresetValidatesBeforePublishingAndRoundTripsWithSkin() {
        LabCommand initial=LabCommand.empty(0);
        LabCommand changed=initial.patch(LabJson.map("geometry",LabJson.map("shape","proportions","shapeMix",1,"agePreset","old","buildMuscle",.25,"buildFat",.75),"ageSkin","old"),Collections.emptySet(),Collections.emptySet());
        assertEquals("old",changed.geometry.agePreset);assertEquals("old",changed.ageSkin);
        assertEquals(changed.geometry,initial.patch(LabJson.map("geometry",changed.json().get("geometry")),Collections.emptySet(),Collections.emptySet()).geometry);
        for(Object bad:Arrays.asList(null,"child",18,"OLD")) {
            try {changed.patch(LabJson.map("geometry",LabJson.map("shape","proportions","shapeMix",1,"agePreset",bad)),Collections.emptySet(),Collections.emptySet());fail();}catch(IllegalArgumentException expected){}
            assertEquals("old",changed.geometry.agePreset);assertEquals("old",changed.ageSkin);
        }
        assertEquals("young",changed.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).geometry.agePreset);
    }
    @Test public void headSizeUsesValidatedIndependentGeometryAndResets() {
        LabCommand initial=LabCommand.empty(0);
        LabCommand changed=initial.patch(LabJson.map("geometry",LabJson.map("headSize",.75)),Collections.emptySet(),Collections.emptySet());
        assertEquals(.75,changed.geometry.headSize,0);assertEquals("none",changed.geometry.shape);
        assertEquals(.75,changed.patch(LabJson.map("ageSkin","old"),Collections.emptySet(),Collections.emptySet()).geometry.headSize,0);
        assertEquals(.5,changed.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).geometry.headSize,0);
        for(Object bad:Arrays.asList(Double.NaN,Double.POSITIVE_INFINITY,-.01,1.01,"large")) {
            try {changed.patch(LabJson.map("geometry",LabJson.map("headSize",bad)),Collections.emptySet(),Collections.emptySet());fail();}
            catch(IllegalArgumentException expected) { }
            assertEquals(.75,changed.geometry.headSize,0);
        }
    }
    @Test public void blendCommandsValidateAtomicallyAndRoundTripAllContributions() {
        LabCommand initial=LabCommand.empty(7);
        Map<String,Object> values=LabJson.map("shape","blend","shapeMix",.7,"blendAthletic",.25,"blendHeavy",1,"blendOlder",.5);
        LabCommand next=initial.patch(LabJson.map("geometry",values),Collections.emptySet(),Collections.emptySet());
        LabCommand roundTrip=initial.patch(LabJson.map("geometry",next.json().get("geometry")),Collections.emptySet(),Collections.emptySet());
        assertEquals(next.geometry,roundTrip.geometry);
        assertEquals(next.geometry,next.patch(LabJson.map("ageSkin","old"),Collections.emptySet(),Collections.emptySet()).geometry);
        for(Object bad:Arrays.asList(-.1,1.1,Double.NaN,"1")) {
            values.put("blendHeavy",bad);
            try {initial.patch(LabJson.map("geometry",values),Collections.emptySet(),Collections.emptySet());fail();}
            catch(IllegalArgumentException expected) { }
            assertEquals(7,initial.revision);assertTrue(initial.geometry.isNeutral());
        }
        assertTrue(next.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).geometry.isNeutral());
    }
    @Test public void explicitNeutralTrialOverridesSavedAppearanceUntilReset() {
        org.avatar2.core.GeometryProbe saved=new org.avatar2.core.GeometryProbe(0,0,"mpfb-older",.5f);
        LabCommand empty=LabCommand.empty(0);
        assertEquals(saved,empty.appearance(saved,.8f,"old").geometry);
        LabCommand trial=empty.patch(LabJson.map("geometry",LabJson.map("shape","none","shapeMix",0),
                "heightScale",1,"ageSkin","original"),Collections.emptySet(),Collections.emptySet());
        LabCommand shown=trial.appearance(saved,.8f,"old");
        assertTrue(shown.geometry.isNeutral());assertEquals(1,shown.heightScale,0);assertEquals("original",shown.ageSkin);
        shown=trial.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).appearance(saved,.8f,"old");
        assertEquals(saved,shown.geometry);assertEquals(.8f,shown.heightScale,0);assertEquals("old",shown.ageSkin);
    }
    @Test public void ageSkinCommandsAreStrictAtomicRetainedAndReset(){
        LabCommand initial=LabCommand.empty(4);
        for(String skin:Arrays.asList("original","young","middle","old")) {
            LabCommand next=initial.patch(LabJson.map("ageSkin",skin),Collections.emptySet(),Collections.emptySet());
            assertEquals(skin,next.ageSkin);assertEquals("original",initial.ageSkin);
            assertEquals(skin,next.patch(LabJson.map("preview",LabJson.map("clip","game")),Collections.emptySet(),Collections.emptySet()).ageSkin);
            assertEquals("original",next.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).ageSkin);
        }
        for(Object bad:Arrays.asList("child","OLD","../old.png",1,null)) {
            try{initial.patch(LabJson.map("ageSkin",bad),Collections.emptySet(),Collections.emptySet());fail();}
            catch(IllegalArgumentException expected){}
            assertEquals(4,initial.revision);assertEquals("original",initial.ageSkin);
        }
    }
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
    @Test public void geometryCommandsAreBoundedAndResetWithTheWholeCommand(){
        LabCommand initial=LabCommand.empty(1);
        LabCommand changed=initial.patch(LabJson.map("geometry",LabJson.map("torsoWidth",1,"torsoDepth",-.5)),Collections.emptySet(),Collections.emptySet());
        assertEquals(1,changed.geometry.torsoWidth,0);assertEquals(-.5,changed.geometry.torsoDepth,0);
        assertTrue(initial.geometry.isNeutral());
        LabCommand retained=changed.patch(LabJson.map("corrections",LabJson.map()),Collections.emptySet(),Collections.emptySet());
        assertEquals(changed.geometry,retained.geometry);
        assertTrue(retained.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).geometry.isNeutral());
        LabCommand authored=initial.patch(LabJson.map("geometry",LabJson.map("shape","mpfb-athletic","shapeMix",.5)),Collections.emptySet(),Collections.emptySet());
        assertEquals("mpfb-athletic",authored.geometry.shape);assertEquals(.5,authored.geometry.shapeMix,0);
        assertFalse(authored.geometry.isNeutral());
        for(Map<String,Object> bad:Arrays.asList(LabJson.map("shape","unknown"),LabJson.map("shape","none","shapeMix",1),LabJson.map("shapeMix",Double.NaN))){
            try{initial.patch(LabJson.map("geometry",bad),Collections.emptySet(),Collections.emptySet());fail();}catch(IllegalArgumentException expected){}
        }
        for(Object bad:Arrays.asList(1.01,Double.NaN,"1")){
            try{initial.patch(LabJson.map("geometry",LabJson.map("torsoWidth",bad)),Collections.emptySet(),Collections.emptySet());fail();}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void heightCommandsAreBoundedRetainedAndReset(){
        for(float baseline:new float[]{1.729108f,1.591230f}){
            HeightLimits limits=new HeightLimits(baseline);LabCommand initial=LabCommand.empty(1);
            LabCommand changed=initial.patch(LabJson.map("heightDirts",13),Collections.emptySet(),Collections.emptySet(),limits);
            assertEquals(1.3,changed.heightScale*baseline,.000002);assertEquals(1,initial.heightScale,0);
            assertEquals(changed.heightScale,changed.patch(LabJson.map("geometry",LabJson.map()),Collections.emptySet(),Collections.emptySet(),limits).heightScale,0);
            assertEquals(1,changed.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet(),limits).heightScale,0);
            assertEquals(2,initial.patch(LabJson.map("heightMetres",2),Collections.emptySet(),Collections.emptySet(),limits).heightScale*baseline,.000002);
            for(Map<String,Object> bad:Arrays.asList(LabJson.map("heightScale",.5),LabJson.map("heightScale",1.5),
                    LabJson.map("heightDirts",12.999),LabJson.map("heightDirts",20.001),LabJson.map("heightMetres",Double.NaN),
                    LabJson.map("heightScale",Double.POSITIVE_INFINITY),LabJson.map("heightScale","1"),
                    LabJson.map("heightDirts",13,"heightMetres",1.3),LabJson.map("heightScale",1,"heightDirts",17))){
                try{initial.patch(bad,Collections.emptySet(),Collections.emptySet(),limits);fail();}
                catch(IllegalArgumentException expected){}
                assertEquals(1,initial.revision);assertEquals(1,initial.heightScale,0);
            }
        }
    }
    @Test public void buildUsesWholeCommandValidationAndReset(){
        LabCommand initial=LabCommand.empty(1);
        Map<String,Object> values=LabJson.map("shape","proportions","shapeMix",1,"buildMuscle",.25,"buildFat",.75,"buildBreast",.75,"buildShoulders",.25);
        LabCommand changed=initial.patch(LabJson.map("geometry",values),Collections.emptySet(),Collections.emptySet());
        assertEquals(.25,changed.geometry.buildMuscle,0);assertEquals(.75,changed.geometry.buildFat,0);
        assertEquals(.75,changed.geometry.buildBreast,0);
        assertEquals(.25,changed.geometry.buildShoulders,0);
        assertTrue(initial.geometry.isNeutral());
        assertEquals(changed.geometry,changed.patch(LabJson.map("ageSkin","old"),Collections.emptySet(),Collections.emptySet()).geometry);
        assertTrue(changed.patch(LabJson.map("reset",true),Collections.emptySet(),Collections.emptySet()).geometry.isNeutral());
        for(Map<String,Object> bad:Arrays.asList(LabJson.map("shape","proportions","buildFat",1.01),
                LabJson.map("shape","proportions","buildMuscle",Double.NaN),LabJson.map("shape","blend","buildFat",.5),
                LabJson.map("shape","proportions","blendHeavy",.5),LabJson.map("shape","proportions","unknown",1),
                LabJson.map("shape","proportions","buildBreast",Double.NaN),LabJson.map("shape","proportions","buildBreast",1.01),
                LabJson.map("shape","blend","buildBreast",.75),LabJson.map("shape","proportions","buildShoulders",Double.NaN),
                LabJson.map("shape","proportions","buildShoulders",1.01),LabJson.map("shape","blend","buildShoulders",.25))) {
            try{changed.patch(LabJson.map("geometry",bad,"ageSkin","middle"),Collections.emptySet(),Collections.emptySet());fail();}
            catch(IllegalArgumentException expected) { }
            assertEquals(.75,changed.geometry.buildFat,0);assertEquals("original",changed.ageSkin);
        }
    }
}
