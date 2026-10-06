package org.avatar2.client;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

/** Minimal lifecycle hooks; geometry/material hooks remain feature-gated. */
final class ClientHooks {
    private ClientHooks() { }

    static void install() throws Exception {
        ClassPool pool = HookManager.getInstance().getClassPool();
        CtClass hud = pool.get("com.wurmonline.client.renderer.gui.HeadsUpDisplay");
        hud.getDeclaredMethod("init", new CtClass[]{CtClass.intType, CtClass.intType})
                .insertAfter("{ org.avatar2.client.Avatar2Mod.afterHudInitialized(this); }");
        hud.getDeclaredMethod("gameTick").insertAfter(
                "{ org.avatar2.client.Avatar2Mod.drainDeferredUi(); }");

        CtClass body = pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable");
        body.getDeclaredMethod("gameTick").insertBefore(
                "{ org.avatar2.client.Avatar2Mod.beforePlayerBodyTick(this); }");
        // Keep the outer refresh method intact: besides rebuilding the legacy
        // body atlas it also reconciles equipped/unequipped armour meshes.
        // Only the incompatible segmented-atlas builder itself is bypassed.
        body.getDeclaredMethod("buildPlayerTexture").insertBefore(
                "{ if (org.avatar2.client.Avatar2Mod.usesAvatar(this)) return; }");
        body.getDeclaredMethod("buildPlayerMesh").insertBefore(
                "{ if (org.avatar2.client.Avatar2Mod.rebuildAvatarMeshes(this)) return; }");
        // Vanilla hair is attached to NullHelmet. The native Avatar rig does
        // not expose that compatibility socket yet, so Wurm otherwise renders
        // the hairstyle at the model root between the player's feet.
        body.getDeclaredMethod("renderHair").insertBefore(
                "{ if (org.avatar2.client.Avatar2Mod.usesAvatar(this)) return; }");

        CtClass loader = pool.get("com.wurmonline.client.renderer.model.collada.ColladaDataLoader");
        loader.getDeclaredMethod("loadInternal").insertBefore(
                "{ org.avatar2.client.AvatarAnimationBridge.beginImport($1); }");
        loader.getDeclaredMethod("loadInternal").insertAfter(
                "{ org.avatar2.client.AvatarAnimationBridge.endImport(); }", true);

        // Wurm's implementation writes through slot influenceCount instead of
        // the loop variable. For a four-weight vertex that means slot zero of
        // the next vertex is irreversibly changed before loadInternal returns.
        CtClass triangleMesh = pool.get(
                "com.wurmonline.client.renderer.model.collada.importer.TriangleMesh");
        triangleMesh.getDeclaredMethod("normalizeBoneWeights").insertBefore(
                "{ if (org.avatar2.client.AvatarAnimationBridge.normalizeDuringAvatarImport(this)) return; }");

        loader.getDeclaredMethod("loadInternal").instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if ("com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml"
                        .equals(call.getClassName()) && "load".equals(call.getMethodName())) {
                    call.replace("{ if (!org.avatar2.client.AvatarAnimationBridge.loadIfAvatar($1, $2)) { $proceed($$); } }");
                }
            }
        });
        installAnimationLab(pool);
    }

    /** Signature and call-site counts are verified against the pinned client. */
    static void installAnimationLab(ClassPool pool) throws Exception {
        CtMethod apply=pool.get("com.wurmonline.client.renderer.model.collada.ColladaAnimationJob")
                .getDeclaredMethod("applyAnimations");
        apply.insertBefore("{ org.avatar2.lab.AnimationLab.beginFrame($2); }");
        final int[] counts=new int[3];
        apply.instrument(new ExprEditor(){
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if(call.getClassName().endsWith(".ColladaModel")&&call.getMethodName().equals("setUpdateSkinning")){
                    counts[0]++;call.replace("{ $proceed($1 || org.avatar2.lab.AnimationLab.forceFrame($0)); }");
                }else if(call.getClassName().endsWith(".ForwardKinematics")&&call.getMethodName().equals("updateBoneTransformations")){
                    counts[1]++;call.replace("{ $proceed($$); if (org.avatar2.lab.AnimationLab.beforeForwardKinematics($1)) { $proceed($$); } org.avatar2.lab.AnimationLab.afterForwardKinematics($1); }");
                }else if(call.getClassName().endsWith(".ColladaModelData")&&call.getMethodName().equals("calculateSkins")){
                    counts[2]++;call.replace("{ $proceed($$); org.avatar2.lab.AnimationLab.afterSkinning($0.getModel()); }");
                }
            }
        });
        apply.insertAfter("{ org.avatar2.lab.AnimationLab.endFrame(); }",true);
        if(counts[0]<1||counts[1]!=2||counts[2]!=1)throw new IllegalStateException("Unexpected Wurm animation pipeline: "+java.util.Arrays.toString(counts));
        CtMethod render=pool.get("com.wurmonline.client.WurmClientBase").getDeclaredMethod("renderFrame");
        final int[] swaps={0};
        render.instrument(new ExprEditor(){
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if(call.getClassName().equals("com.wurmonline.client.LwjglClient")&&call.getMethodName().equals("update")){
                    swaps[0]++;call.replace("{ org.avatar2.lab.AnimationLab.captureFrame(); $proceed($$); }");
                }
            }
        });
        if(swaps[0]!=1)throw new IllegalStateException("Could not locate Wurm framebuffer swap");
    }
}
