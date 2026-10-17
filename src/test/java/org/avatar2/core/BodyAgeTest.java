package org.avatar2.core;

import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public final class BodyAgeTest {
    @Test public void previousSchemasKeepTheirExactBodyAndIndependentSkin() {
        AvatarProfile p=new AvatarProfile();p.selectShape("proportions");p.buildMuscle=.27123455f;p.buildFat=.73123455f;p.ageSkin="old";
        GeometryProbe original=p.geometry();
        for(int schema=4;schema<=7;schema++) {
            Properties values=p.toProperties();values.setProperty("schema",Integer.toString(schema));values.setProperty("agePreset","old");
            AvatarProfile migrated=AvatarProfile.fromProperties(values);
            assertEquals(original,migrated.geometry());assertEquals("old",migrated.ageSkin);assertEquals("young",migrated.agePreset);
        }
        p.agePreset="middle";assertEquals(p.geometry(),AvatarProfile.fromProperties(p.toProperties()).geometry());
        assertEquals(p.geometry(),p.copy().geometry());
    }
    @Test public void ageWeightsRetainFullMuscleFatBreastInteractionAndAmount() {
        for(float muscle:new float[]{0,.25f,.5f,1})for(float fat:new float[]{0,.5f,1})for(float breast:new float[]{0,.25f,.5f,.75f,1}) {
            GeometryProbe g=new GeometryProbe(0,0,"proportions",.73f,0,0,0,muscle,fat,breast,.5f,.5f,"old");
            float sum=0;for(float value:g.ageWeights()){assertTrue(value>=0);sum+=value;}
            assertEquals(.73f,sum,.000001f);
            float[] middle=g.withAgePreset("middle").ageWeights(),old=g.ageWeights();
            for(int i=0;i<old.length;i++)assertEquals(old[i]*.5f,middle[i],0);
            assertEquals("old",g.withHeadSize(0).agePreset);
        }
        try{GeometryProbe.NEUTRAL.withAgePreset("old");fail();}catch(IllegalArgumentException expected){}
        for(String bad:new String[]{null,"child","18", "OLD"})try{BodyAge.validate(bad);fail();}catch(IllegalArgumentException expected){}
    }
}
