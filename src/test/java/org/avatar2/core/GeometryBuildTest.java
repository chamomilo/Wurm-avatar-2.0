package org.avatar2.core;

import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public class GeometryBuildTest {
    @Test public void originalAndFullCombinationReachTheirOwnGridCorners() {
        assertTrue(build(1,0).isNeutral());
        assertArrayEquals(new float[]{1,0,0},build(0,0).buildWeights(),0);
        assertArrayEquals(new float[]{0,1,0},build(1,1).buildWeights(),0);
        assertArrayEquals(new float[]{0,0,1},build(0,1).buildWeights(),0);
        for(float muscle:new float[]{0,.25f,.5f,.75f,1}) for(float fat:new float[]{0,.25f,.5f,.75f,1}) {
            float[] weights=build(muscle,fat).buildWeights();
            assertEquals(1-muscle,weights[0]+weights[2],.000001);
            assertEquals(fat,weights[1]+weights[2],.000001);
            assertTrue(weights[0]+weights[1]+weights[2]<=1);
        }
    }
    @Test public void exactParametersRoundTripAndLegacyPlaceholdersKeepTheirAppearance() {
        AvatarProfile p=new AvatarProfile();p.selectShape("proportions");
        p.buildMuscle=.27123457f;p.buildFat=.6312345f;p.shapeMix=.73123455f;
        AvatarProfile restored=AvatarProfile.fromProperties(p.toProperties());
        assertEquals(p.geometry(),restored.geometry());assertEquals(p.geometry(),p.copy().geometry());
        for(String schema:new String[]{"1","2","3"}) {
            Properties old=new Properties();old.setProperty("schema",schema);
            old.setProperty("muscle","0.01");old.setProperty("mass","1");
            old.setProperty("buildMuscle","0");old.setProperty("buildFat","1");
            assertTrue(AvatarProfile.fromProperties(old).geometry().isNeutral());
        }
    }
    @Test public void invalidOrCrossModeControlsCannotBecomeGeometry() {
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f}) {
            try {build(bad,0);fail();}catch(IllegalArgumentException expected) { }
            try {build(1,bad);fail();}catch(IllegalArgumentException expected) { }
        }
        try {new GeometryProbe(0,0,"blend",1,0,0,0,.5f,1);fail();}catch(IllegalArgumentException expected) { }
        try {new GeometryProbe(0,0,"proportions",1,.1f,0,0,.5f,1);fail();}catch(IllegalArgumentException expected) { }
    }
    private static GeometryProbe build(float muscle,float fat) {
        return new GeometryProbe(0,0,"proportions",1,0,0,0,muscle,fat);
    }
}
