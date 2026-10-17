package org.avatar2.core;

import com.wurmonline.mesh.Tiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeightLimitsTest {
    @Test public void dirtEndpointsMatchNativeWurmHeightDecoder(){
        for(float baseline:new float[]{1.729108f,1.591230f}){
            HeightLimits limits=new HeightLimits(baseline);
            for(int dirt=13;dirt<=20;dirt++){
                float scale=limits.fromDirts(dirt);limits.validateScale(scale);
                assertEquals(Tiles.decodeHeightAsFloat(dirt),baseline*scale,.000002);
                assertEquals(scale,limits.fromMetres(dirt/10d),0);
            }
            limits.validateScale(1);
            assertEquals(limits.fromDirts(13),limits.scaleRange()[0],0);
            assertEquals(limits.fromDirts(20),limits.scaleRange()[1],0);
        }
    }
}
