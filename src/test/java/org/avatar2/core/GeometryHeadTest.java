package org.avatar2.core;

import org.junit.Test;
import java.util.Properties;
import static org.junit.Assert.*;

public class GeometryHeadTest {
    @Test public void migrationIgnoresLegacyPlaceholderAndSavesExactIndependentSize() {
        AvatarProfile p=new AvatarProfile();p.headSize=.73123455f;
        for(int schema=1;schema<=6;schema++) {
            Properties old=p.toProperties();old.setProperty("schema",String.valueOf(schema));old.setProperty("head","1");
            assertEquals(.5f,AvatarProfile.fromProperties(old).headSize,0);
        }
        AvatarProfile restored=AvatarProfile.fromProperties(p.toProperties());
        assertEquals(p.headSize,restored.headSize,0);assertEquals(p.headSize,p.copy().headSize,0);
        for(String shape:GeometryProbe.SHAPES) {p.selectShape(shape);assertEquals(p.headSize,p.geometry().headSize,0);}
        assertEquals(p.headSize,p.geometry().asBlend().headSize,0);
        assertFalse(GeometryProbe.NEUTRAL.withHeadSize(1).isNeutral());
    }
    @Test public void invalidHeadCommandsAreRejectedBeforeUse() {
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-.01f,1.01f}) {
            try {GeometryProbe.NEUTRAL.withHeadSize(value);fail("Invalid head accepted");}catch(IllegalArgumentException expected){}
        }
    }
}
