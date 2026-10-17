package org.avatar2.core;
import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public class GeometryShouldersTest {
    @Test public void bothSexesSaveShouldersWithoutReinterpretingLegacyPlaceholder() {
        for(AvatarProfile.Sex sex:AvatarProfile.Sex.values()) {
            AvatarProfile p=new AvatarProfile();p.sex=sex;p.selectShape("proportions");p.buildShoulders=.73123455f;
            assertEquals(p.geometry(),p.copy().geometry());
            assertEquals(p.geometry(),AvatarProfile.fromProperties(p.toProperties()).geometry());
            Properties old=p.toProperties();old.setProperty("schema","5");old.setProperty("shoulders",".86");
            assertEquals(.5f,AvatarProfile.fromProperties(old).buildShoulders,0);
        }
    }
    @Test public void centerIsExactAndAmountScalesTheLocalResidual() {
        assertTrue(build(.5f,1).isNeutral());
        assertFalse(build(0,1).isNeutral());assertFalse(build(1,1).isNeutral());
        assertTrue(build(0,0).isNeutral());
        assertEquals(.3f,build(.2f,.5f).buildGridWeights()[11],1e-7);
        assertEquals(.3f,build(.8f,.5f).buildGridWeights()[12],1e-7);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f}) {
            try{build(bad,1);fail();}catch(IllegalArgumentException expected){ }
        }
        try{new GeometryProbe(0,0,"blend",1,0,0,0,1,0,.5f,0);fail();}catch(IllegalArgumentException expected){ }
    }
    private static GeometryProbe build(float shoulders,float amount) {
        return new GeometryProbe(0,0,"proportions",amount,0,0,0,1,0,.5f,shoulders);
    }
}
