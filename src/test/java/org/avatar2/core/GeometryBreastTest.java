package org.avatar2.core;

import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public class GeometryBreastTest {
    @Test public void centerPreservesTheReleasedGridAndExtremesUseTheirOwnCorners() {
        GeometryProbe center=build(.25f,.75f,.5f);
        float[] weights=center.buildGridWeights();
        assertArrayEquals(center.buildWeights(),java.util.Arrays.copyOf(weights,3),0);
        for(int i=3;i<weights.length;i++)assertEquals(0,weights[i],0);
        assertTrue(build(1,0,.5f).isNeutral());assertFalse(build(1,0,0).isNeutral());
        for(float breast:new float[]{0,.25f,.5f,.75f,1})for(float muscle:new float[]{0,.5f,1})for(float fat:new float[]{0,.5f,1}) {
            float sum=0;for(float value:build(muscle,fat,breast).buildGridWeights()){assertTrue(value>=0);sum+=value;}
            assertTrue(sum<=1.000001f);
        }
        assertEquals(1,build(1,0,0).buildGridWeights()[3],0);
        assertEquals(1,build(0,1,1).buildGridWeights()[10],0);
    }
    @Test public void femaleValueRoundTripsWithoutChangingMaleOrLegacyAppearance() {
        AvatarProfile p=new AvatarProfile();p.sex=AvatarProfile.Sex.FEMALE;p.selectShape("proportions");
        p.buildBreast=.73123455f;p.buildMuscle=.25f;p.buildFat=.75f;
        assertEquals(p.geometry(),AvatarProfile.fromProperties(p.toProperties()).geometry());
        assertEquals(p.geometry(),p.copy().geometry());
        p.sex=AvatarProfile.Sex.MALE;assertEquals(.5f,p.geometry().buildBreast,0);
        p.sex=AvatarProfile.Sex.FEMALE;assertEquals(.73123455f,p.geometry().buildBreast,0);
        Properties old=p.toProperties();old.setProperty("schema","4");
        assertEquals(.5f,AvatarProfile.fromProperties(old).buildBreast,0);
    }
    @Test public void nonFiniteAndCrossModeBreastCommandsAreRejected() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f}) {
            try{build(1,0,bad);fail();}catch(IllegalArgumentException expected){ }
        }
        try{new GeometryProbe(0,0,"blend",1,0,0,0,1,0,.75f);fail();}catch(IllegalArgumentException expected){ }
    }
    private static GeometryProbe build(float muscle,float fat,float breast) {
        return new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat,breast);
    }
}
