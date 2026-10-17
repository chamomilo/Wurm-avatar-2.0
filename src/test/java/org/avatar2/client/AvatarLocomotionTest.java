package org.avatar2.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class AvatarLocomotionTest {
    @Test public void roadCadenceIsBoundedAndTallBodiesHaveSlowerSteps() {
        double small=cycles(.743f),tall=cycles(1.143f);
        assertTrue("road cadence cap",small<2.10&&tall<1.70);
        assertTrue("height changes stride timing",small>tall+.25);
    }
    private static double cycles(float height) {
        AvatarLocomotion.State s=new AvatarLocomotion.State();long now=1_000_000_000L;
        float before=s.phase(0,.56f,height,now);double cycles=0;
        for(int frame=1;frame<=600;frame++) {
            float phase=s.phase(frame*.08f,.56f,height,now+frame*16_666_667L);
            cycles+=(phase-before+1)%1;before=phase;
        }
        return cycles/10;
    }
    @Test public void firstAccelerationFrameMovesAndStoppingFreezesThePhase() {
        AvatarLocomotion.State s=new AvatarLocomotion.State();long now=1_000_000_000L;
        assertTrue(s.phase(0,.001f,1,now)>0);
        float a=s.phase(.0001f,.001f,1,now+50_000_000L);
        assertTrue(a>.08f);
        assertEquals(a,s.phase(0,0,1,now+100_000_000L),0);
        assertEquals(a,s.phase(0,0,1,now+2_000_000_000L),0);
        assertTrue(s.phase(0,.001f,1,now+2_100_000_000L)>a);
    }
    @Test public void reverseWrapAndLongFrameDoNotMakeAnAnimationLeap() {
        AvatarLocomotion.State s=new AvatarLocomotion.State();long now=1_000_000_000L;
        float before=s.phase(100,-.56f,1,now);
        for(int i=1;i<=240;i++) {
            float next=s.phase(100-i*.08f,-.56f,1,now+i*16_666_667L);
            assertTrue((before-next+1)%1<=.030001f);before=next;
        }
        float next=s.phase(-100,-.56f,1,now+9_000_000_000L);
        assertTrue((before-next+1)%1<=.180001f);
    }
    @Test public void bodyTurnsByShortestArcAtTheSameSpeedAcrossFrameRates() {
        for(int fps:new int[]{23,30,60,144}) {
            AvatarLocomotion.State s=new AvatarLocomotion.State();long now=1_000_000_000L;
            s.heading(0,now);float prev=0;
            for(int i=1;i<=fps;i++) {
                float next=s.heading((float)Math.PI/2,now+(long)(i*1e9/fps));
                assertTrue(next-prev<=Math.toRadians(220.01)/fps);prev=next;
            }
            assertEquals(Math.PI/2,prev,1e-6);assertEquals(0,s.lag,1e-6);
            assertFalse(s.turning(now+2_000_000_000L));
        }
        AvatarLocomotion.State s=new AvatarLocomotion.State();long now=1_000_000_000L;
        s.heading((float)Math.toRadians(179),now);
        assertEquals(Math.toRadians(-179),s.heading((float)Math.toRadians(-179),now+16_666_667L),1e-6);
        assertEquals(.731f,AvatarLocomotion.phase(null,.731f),0);
        assertEquals(.731f,AvatarLocomotion.yaw(null,.731f,true),0);
    }
}
