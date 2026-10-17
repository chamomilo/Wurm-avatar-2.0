package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class AvatarStabilityHarness {
    private static final CountDownLatch RENDEZVOUS=new CountDownLatch(2);
    private static final ThreadLocal<Boolean> MET=new ThreadLocal<>();
    public static void rendezvous() {
        if(!Thread.currentThread().getName().startsWith("Avatar stability")||Boolean.TRUE.equals(MET.get())) return;
        MET.set(true);RENDEZVOUS.countDown();
        try { if(!RENDEZVOUS.await(3,TimeUnit.SECONDS))throw new AssertionError("Interleaving did not meet"); }
        catch(InterruptedException e) {throw new AssertionError(e);}
    }
    public static void run(String root,boolean expectDeadlock)throws Exception {
        ColladaModel model=GeometryRuntimeHarness.load(new File(root,"assets/runtime/body/male-bodybuilder.dae"));
        AvatarGeometryPrototype.register(model);AvatarHeightPrototype.register(model);AvatarEquipmentSockets.register(model);
        AvatarHeightPrototype.apply(model,AvatarHeightPrototype.limits(model).fromDirts(13));
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread telemetry=new Thread(()-> {
            try {for(int i=0;i<20000;i++)AvatarHeightPrototype.describe(model);} catch(Throwable e){failure.set(e);}
        },"Avatar stability telemetry");
        Thread renderer=new Thread(()-> {
            try {for(int i=0;i<20000;i++)model.getNullTransformation("NullRightHand");} catch(Throwable e){failure.set(e);}
        },"Avatar stability renderer");
        telemetry.setDaemon(true);renderer.setDaemon(true);telemetry.start();renderer.start();
        telemetry.join(expectDeadlock?1500:15000);renderer.join(expectDeadlock?100:15000);
        long[] deadlocked=ManagementFactory.getThreadMXBean().findMonitorDeadlockedThreads();
        if(expectDeadlock) {
            if(deadlocked==null||deadlocked.length<2)throw new AssertionError("Old release did not reproduce deadlock",failure.get());
            System.out.println("AVATAR2_060_DEADLOCK_REPRODUCED height telemetry -> sockets / renderer sockets -> height");
        } else {
            if(telemetry.isAlive()||renderer.isAlive()||deadlocked!=null||failure.get()!=null)
                throw new AssertionError("Current renderer/telemetry stalled",failure.get());
            System.out.println("AVATAR2_STABILITY_OK deterministic interleaving + 40000 concurrent native attachment/height telemetry calls");
        }
    }
}
