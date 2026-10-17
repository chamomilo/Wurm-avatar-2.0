package org.avatar2.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeometryBlendTest {
    @Test public void contributionsStayBoundedAndAmountScalesTheWholeBlend() {
        assertArrayEquals(new float[]{.2f,.3f,.1f},blend(1,.2f,.3f,.1f).weights(),0);
        assertArrayEquals(new float[]{.25f,.25f,0},blend(.5f,1,1,0).weights(),0);
        float sum=0;for(float weight:blend(1,1,1,1).weights())sum+=weight;
        assertEquals(1,sum,1e-7f);
        assertTrue(blend(1,0,0,0).isNeutral());
        assertTrue(blend(0,1,1,1).isNeutral());
        assertNotEquals(blend(1,.2f,.3f,0),blend(1,.3f,.2f,0));
    }
    @Test public void convertingAnEndpointPreservesItsEffectiveWeights() {
        for(String shape:new String[]{"none","mpfb-athletic","mpfb-heavy","mpfb-older"}) {
            AvatarProfile p=new AvatarProfile();p.shape=shape;p.shapeMix=shape.equals("none")?0:.37123457f;
            float[] before=p.geometry().weights();p.selectShape("blend");
            assertEquals(1,p.shapeMix,0);
            assertArrayEquals(before,p.geometry().weights(),0);
            p.selectShape("blend");assertArrayEquals(before,p.geometry().weights(),0);
        }
    }
    @Test public void editedShareReachesTheFullEndpointAndRetainsOtherRatios() {
        GeometryProbe original=blend(.37123457f,1,1,1),editable=original.asBlend();
        assertArrayEquals(original.weights(),editable.weights(),1e-7f);assertEquals(1,editable.shapeMix,0);
        for(int component=0;component<3;component++) {
            float[] full=new float[3];full[component]=1;
            assertArrayEquals(full,editable.withContribution(component,1).weights(),0);
        }
        GeometryProbe changed=blend(1,.2f,.6f,.2f).withContribution(0,.6f);
        assertArrayEquals(new float[]{.6f,.3f,.1f},changed.weights(),1e-7f);
        assertArrayEquals(new float[]{0,1,0},changed.withContribution(1,1).weights(),0);
        assertTrue(changed.withContribution(1,1).withContribution(1,0).isNeutral());
        assertArrayEquals(new float[]{0,.5f,0},blend(.5f,.5f,.5f,0).withContribution(1,1).weights(),0);
    }
    @Test public void profileSchemaThreePreservesExactWeightsAndSchemaTwoAppearance() {
        AvatarProfile p=new AvatarProfile();p.selectShape("blend");
        p.blendAthletic=.12345678f;p.blendHeavy=.8765432f;p.blendOlder=.23456789f;
        AvatarProfile loaded=AvatarProfile.fromProperties(p.toProperties());
        assertEquals(p.geometry(),loaded.geometry());assertEquals(p.geometry(),p.copy().geometry());
        java.util.Properties old=p.toProperties();old.setProperty("schema","2");
        old.setProperty("shape","mpfb-heavy");old.setProperty("shapeMix","0.42");
        assertEquals(new GeometryProbe(0,0,"mpfb-heavy",.42f),AvatarProfile.fromProperties(old).geometry());
    }
    @Test public void invalidOrHiddenContributionsAreRejected() {
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f}) {
            try {blend(1,value,0,0);fail();}catch(IllegalArgumentException expected) { }
        }
        try {new GeometryProbe(0,0,"mpfb-heavy",1,.1f,0,0);fail();}catch(IllegalArgumentException expected) { }
    }
    private static GeometryProbe blend(float amount,float athletic,float heavy,float older) {
        return new GeometryProbe(0,0,"blend",amount,athletic,heavy,older);
    }
}
