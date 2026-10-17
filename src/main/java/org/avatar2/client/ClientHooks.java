package org.avatar2.client;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.FieldAccess;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Opcode;
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
        installHair(pool);

        CtClass loader = pool.get("com.wurmonline.client.renderer.model.collada.ColladaDataLoader");
        loader.getDeclaredMethod("loadInternal").insertBefore(
                "{ org.avatar2.client.AvatarAnimationBridge.beginImport($1); }");
        loader.getDeclaredMethod("loadInternal").insertAfter(
                "{ org.avatar2.client.AvatarAnimationBridge.endImport(); }", true);

        // Wurm's implementation writes through slot influenceCount instead of
        // the loop variable. For a four-weight vertex that means slot zero of
        // the next vertex is irreversibly changed before loadInternal returns.
        installAvatarWeights(pool);

        loader.getDeclaredMethod("loadInternal").instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if ("com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml"
                        .equals(call.getClassName()) && "load".equals(call.getMethodName())) {
                    call.replace("{ if (!org.avatar2.client.AvatarAnimationBridge.loadIfAvatar($1, $2)) { $proceed($$); } }");
                }
            }
        });
        installWalkMotion(pool);
        installPlayerRenderPosture(pool);
        installGeometryPrototype(pool);
        installHeightPrototype(pool);
        installPortraitHeight(pool);
        installEquipmentSockets(pool);
        installFirstPerson(pool);
        installAgeSkin(pool);
        installAnimationLab(pool);
    }

    static void installHair(ClassPool pool) throws Exception {
        pool.get("com.wurmonline.client.renderer.cell.AttachedCellRenderable").getDeclaredMethod("prepareRender").insertBefore(
                "{ this.model=org.avatar2.client.AvatarHair.prepareCell(this,this.parentModel,this.model); }");
        pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel")
                .getDeclaredConstructor(new CtClass[]{pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel")})
                .insertAfter("{ org.avatar2.client.AvatarHair.copied($1,this); }");
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData")
                .getDeclaredMethod("calculateSkins").insertAfter(
                "{ org.avatar2.client.AvatarHair.publish(this.getModel()); }");
        CtMethod hair = pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable").getDeclaredMethod("renderHair");
        final int[] calls = new int[3];
        hair.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if (call.getMethodName().equals("isMale")) {
                    calls[0]++; call.replace("{ $_=org.avatar2.client.AvatarHair.isMale($0); }");
                } else if (call.getMethodName().equals("renderNullEquipment")) {
                    calls[1]++; call.replace("{ com.wurmonline.client.renderer.Matrix fitted=org.avatar2.client.AvatarHair.parentMatrix($3,$2);"
                            + " try { $proceed($1,fitted,$3,$4,$5,$6,$7,$8); } finally { org.avatar2.client.AvatarHair.endDraw(); } }");
                }
            }
            @Override public void edit(FieldAccess field) throws javassist.CannotCompileException {
                if (field.isReader() && field.getFieldName().equals("isMale")) {
                    calls[2]++; field.replace("{ $_=org.avatar2.client.AvatarHair.isMale($0); }");
                }
            }
        });
        hair.insertBefore("{ if(org.avatar2.client.AvatarFirstPerson.hideHair($3)) return;"
                + " if(org.avatar2.client.AvatarHair.refresh(this)) this.hairModel=null; }");
        if (calls[0]!=1 || calls[1]!=1 || calls[2]!=1)
            throw new IllegalStateException("Unexpected Wurm hairstyle path: "+java.util.Arrays.toString(calls));
    }

    static void installAgeSkin(ClassPool pool) throws Exception {
        CtClass mesh = pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh");
        mesh.getDeclaredConstructor(new CtClass[]{mesh}).insertAfter(
                "{ org.avatar2.client.AvatarAgeSkin.copiedMesh($1, this); }");
        CtClass model = pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel");
        model.getDeclaredConstructor(new CtClass[]{model}).insertAfter(
                "{ org.avatar2.client.AvatarAgeSkin.copiedModel($1, this); }");
        CtClass render = pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelRender");
        for (String method : new String[]{"renderTriangleList", "renderGPUSkinningTriangleList"}) {
            CtMethod draw = render.getDeclaredMethod(method);
            if (draw.getParameterTypes().length != 12
                    || !draw.getParameterTypes()[9].getName().equals("com.wurmonline.client.resources.textures.Texture"))
                throw new IllegalStateException("Unexpected Wurm age-skin draw signature");
            // The CPU entry dispatches GPU meshes to the second entry. Resolve once,
            // so a command change cannot treat an already substituted texture as native.
            String guard = method.equals("renderTriangleList") ? "if (!$9.hasGPUSkinning()) " : "";
            draw.insertBefore("{ " + guard + "$10 = org.avatar2.client.AvatarAgeSkin.texture($8, $9, $10); }");
        }
    }

    static void installAvatarWeights(ClassPool pool) throws Exception {
        pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh")
                .getDeclaredMethod("normalizeBoneWeights").insertBefore(
                "{ if (org.avatar2.client.AvatarAnimationBridge.normalizeDuringAvatarImport(this)) return; }");
    }

    /** Keep copy ownership explicit: Wurm setters mutate shared wrappers in place. */
    static void installGeometryPrototype(ClassPool pool) throws Exception {
        CtClass mesh = pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh");
        // Wurm enables CPU normal calculation only when it first detaches a
        // shared normalBuffer. Private Avatar buffers must calculate normals
        // every frame too. Replace the pinned method's unique flag initializer,
        // preserving the real skinning implementation and avoiding buffer churn.
        CtMethod skin = mesh.getDeclaredMethod("calculateSkin");
        CodeIterator code = skin.getMethodInfo().getCodeAttribute().iterator();
        int normalFlag = -1, flagCount = 0;
        while (code.hasNext()) {
            int position = code.next();
            if (code.byteAt(position) == Opcode.ICONST_0 && code.byteAt(position + 1) == Opcode.ISTORE_1) {
                normalFlag = position; flagCount++;
            }
        }
        if (flagCount != 1) throw new IllegalStateException("Unexpected Wurm CPU normal flag: " + flagCount);
        ConstPool constants = skin.getMethodInfo().getConstPool();
        int managed = constants.addMethodrefInfo(constants.addClassInfo("org.avatar2.client.AvatarGeometryPrototype"),
                "isManaged", "(Lcom/wurmonline/client/renderer/model/collada/importer/TriangleMesh;)Z");
        code.writeByte(Opcode.ALOAD_0, normalFlag);
        code.insert(normalFlag + 1, new byte[]{(byte)Opcode.INVOKESTATIC, (byte)(managed >> 8), (byte)managed});
        mesh.getDeclaredConstructor(new CtClass[]{mesh}).insertAfter(
                "{ if (org.avatar2.client.AvatarGeometryPrototype.isManaged($1)) {"
                + " if (this.originalVertBuffer != null) this.originalVertBuffer = (float[])this.originalVertBuffer.clone();"
                + " if (this.originalNormalBuffer != null) this.originalNormalBuffer = (float[])this.originalNormalBuffer.clone();"
                + " if (this.vertBuffer != null && this.vertBuffer.buffer != null) {"
                + " com.wurmonline.client.util.SharedFloatBuffer old = this.vertBuffer;"
                + " this.vertBuffer = new com.wurmonline.client.util.SharedFloatBuffer(com.wurmonline.client.util.BufferUtil.clone(old.buffer)); old.release(); }"
                + " if (this.normalBuffer != null && this.normalBuffer.buffer != null) {"
                + " com.wurmonline.client.util.SharedFloatBuffer old = this.normalBuffer;"
                + " this.normalBuffer = new com.wurmonline.client.util.SharedFloatBuffer(com.wurmonline.client.util.BufferUtil.clone(old.buffer)); old.release(); }"
                + " this.freeVBO(); this.allowSlim = false;"
                + " org.avatar2.client.AvatarGeometryPrototype.copiedMesh($1, this); } }");
        mesh.getDeclaredMethod("optimize").insertBefore(
                "{ if (org.avatar2.client.AvatarGeometryPrototype.isManaged(this)) return; }");
        mesh.getDeclaredMethod("slim").insertBefore(
                "{ if (org.avatar2.client.AvatarGeometryPrototype.isManaged(this)) return; }");
        skin.insertBefore(
                "{ org.avatar2.client.AvatarGeometryPrototype.beforeSkin(this); }");
        CtClass model = pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel");
        for (String boundsMethod : new String[]{"calculateBoundingBox", "calculatePickingBox"})
            model.getDeclaredMethod(boundsMethod).instrument(new ExprEditor() {
                @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                    if (call.getClassName().equals("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh")
                            && call.getMethodName().equals("getVertexBuffer"))
                        call.replace("{ $_ = org.avatar2.client.AvatarGeometryPrototype.boundsBuffer($0, $proceed($$)); }");
                }
            });
        model.getDeclaredConstructor(new CtClass[]{model}).insertAfter(
                "{ org.avatar2.client.AvatarGeometryPrototype.copiedModel($1, this); }");
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData")
                .getDeclaredMethod("calculateSkins").insertBefore(
                "{ boolean geometryChanged = org.avatar2.client.AvatarGeometryPrototype.beforeSkins(this.getModel());"
                + " boolean heightChanged = org.avatar2.client.AvatarHeightPrototype.boundsChanged(this.getModel());"
                + " if (geometryChanged || heightChanged) {"
                + " this.bounds = org.avatar2.client.AvatarHeightPrototype.bounds(this.getModel(), this.getModel().calculateBoundingBox());"
                + " this.pickingBound = org.avatar2.client.AvatarHeightPrototype.bounds(this.getModel(), this.getModel().calculatePickingBox());"
                + " if (this.pickingBound == null) this.pickingBound = new com.wurmonline.client.renderer.model.ModelDataBounds(this.bounds);"
                + " this.originalPickingBound = new com.wurmonline.client.renderer.model.ModelDataBounds(this.pickingBound); } }");
    }

    static void installHeightPrototype(ClassPool pool) throws Exception {
        CtClass mesh = pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh");
        final int[] gpuCalls = new int[2];
        mesh.getDeclaredMethod("updateBoneBuffers").instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if (!call.getClassName().equals("com.wurmonline.client.renderer.model.collada.animation.Joint")) return;
                boolean inverse = call.getMethodName().equals("getOriginalInvertBindMatrix");
                if (inverse || call.getMethodName().equals("getBindMatrix")) {
                    gpuCalls[inverse ? 1 : 0]++;
                    call.replace("{ $_ = org.avatar2.client.AvatarHeightPrototype.gpuBind(this, $proceed($$), " + inverse + "); }");
                }
            }
        });
        if (gpuCalls[0] != 1 || gpuCalls[1] != 1)
            throw new IllegalStateException("Unexpected Wurm DQ bind calls: " + java.util.Arrays.toString(gpuCalls));
        CtClass model = pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel");
        model.getDeclaredConstructor(new CtClass[]{model}).insertAfter(
                "{ org.avatar2.client.AvatarHeightPrototype.copied($1, this); }");
        for (String method : new String[]{"getNullTransformation", "getNullOriginalTransformation"})
            for (CtMethod getter : model.getDeclaredMethods(method)) {
                boolean original = method.equals("getNullOriginalTransformation");
                String key = getter.getParameterTypes()[0] == CtClass.intType ? "Integer.valueOf($1)" : "$1";
                getter.insertAfter("{ $_ = org.avatar2.client.AvatarHeightPrototype.attachment(this, $_, "
                        + key + ", " + original + "); }");
            }
        model.getDeclaredMethod("getOriginalNullAttachmentOffset").insertAfter(
                "{ $_ = org.avatar2.client.AvatarHeightPrototype.originalOffset(this, $_, $1); }");
        for(CtMethod getter:model.getDeclaredMethods("getNullAttachmentOffset")) {
            String key=getter.getParameterTypes()[0]==CtClass.intType?"Integer.valueOf($1)":"$1";
            getter.insertAfter("{ $_ = org.avatar2.client.AvatarHeightPrototype.cameraOffset(this, $_, "+key+"); }");
        }
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData")
                .getDeclaredMethod("calculateSkins").insertAfter(
                "{ org.avatar2.client.AvatarHeightPrototype.publishCamera(this.getModel()); }");
        // getNullScale uses cached unscaled FK metadata; preserve it.
        model.getDeclaredMethod("getNullRotation").insertAfter(
                "{ $_ = org.avatar2.client.AvatarHeightPrototype.rotation(this, $_, $1); }");
    }

    /** Complete Avatar's height integration with Wurm's native portrait camera. */
    static void installPortraitHeight(ClassPool pool) throws Exception {
        // Wurm normally shares the cached paper-doll model. Applied Avatar
        // appearance needs a private copy, just like the local world body.
        pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable$PaperDoll")
                .getDeclaredMethod("wantNewModelCopy").insertBefore(
                "{ if (paperDollModel != null && paperDollModel.getResourceUrl() != null"
                + " && paperDollModel.getResourceUrl().getFilePath().replace('\\\\','/').startsWith(\"avatar2/body/\")) return true; }");
        CtMethod render=pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable").getDeclaredMethod("renderPaperDoll");
        final int[] count={0};
        render.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if(call.getClassName().equals("com.wurmonline.client.renderer.Matrix")&&call.getMethodName().equals("fromTranslationRotation")) {
                    count[0]++;
                    call.replace("{ $_=org.avatar2.client.AvatarHeightPrototype.portraitMatrix(this.getPaperDollModel(),$proceed($$)); }");
                }
            }
        });
        if(count[0]!=2)throw new IllegalStateException("Unexpected native paper-doll camera transforms: "+count[0]);
    }

    /** Remove Wurm's two decorative -4 degree pitches for the Avatar body. */
    static void installPlayerRenderPosture(ClassPool pool) throws Exception {
        CtMethod render = pool.get("com.wurmonline.client.renderer.WorldRender")
                .getDeclaredMethod("renderPlayerModel");
        final int[] counts = new int[2];
        render.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if (call.getClassName().equals("com.wurmonline.math.Quaternion")
                        && call.getMethodName().equals("fromAngles")) {
                    counts[0]++;
                    call.replace("{ $_ = $proceed(org.avatar2.client.AvatarRenderPosture.pitch(world.getPlayer().getPlayerBody(), $1), org.avatar2.client.AvatarLocomotion.yaw(world.getPlayer().getPlayerBody(), $2, !world.getPlayer().isFlying() && world.getPlayer().getCarrierCreature() == null), $3); }");
                }
            }
            @Override public void edit(FieldAccess field) throws javassist.CannotCompileException {
                if (field.isReader() && field.getFieldName().equals("playerModelViewRotate")) {
                    counts[1]++;
                    field.replace("{ $_ = org.avatar2.client.AvatarRenderPosture.viewRotation(world.getPlayer().getPlayerBody(), $proceed()); }");
                }
            }
        });
        if (counts[0] != 1 || counts[1] != 1)
            throw new IllegalStateException("Unexpected Wurm player render rotations: " + java.util.Arrays.toString(counts));
    }

    static void installFirstPerson(ClassPool pool) throws Exception {
        CtMethod playerRender=pool.get("com.wurmonline.client.renderer.WorldRender").getDeclaredMethod("renderPlayerModel");
        playerRender.insertBefore("{ org.avatar2.client.AvatarFirstPerson.begin(world.getPlayer().getPlayerBody(),"
                + "org.avatar2.client.AvatarFirstPerson.nearEyes(freeCamera,$4,cameraX-xPlayer-cameraOffset.getX(),"
                + "cameraY-hPlayer-cameraOffset.getY(),cameraZ-yPlayer-cameraOffset.getZ())); }");
        playerRender.insertAfter("{ org.avatar2.client.AvatarFirstPerson.end(); }",true);
        CtClass mesh=pool.get("com.wurmonline.client.renderer.model.collada.importer.TriangleMesh");
        mesh.getDeclaredConstructor(new CtClass[]{mesh}).insertAfter("{ org.avatar2.client.AvatarFirstPerson.copied($1,this); }");
        mesh.getDeclaredMethod("destroy").insertBefore("{ org.avatar2.client.AvatarFirstPerson.destroyed(this); }");
        CtClass render=pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelRender");
        final int[] counts=new int[2];
        for(String name:new String[]{"renderTriangleList","renderGPUSkinningTriangleList"}) {
            CtMethod method=render.getDeclaredMethod(name);
            method.insertBefore("{ if(org.avatar2.client.AvatarFirstPerson.hidden($8,$9)) return; }");
            method.instrument(new ExprEditor() {
                @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                    if(!call.getClassName().endsWith(".TriangleMesh")) return;
                    if(call.getMethodName().equals("getIBO")) {
                        counts[0]++;call.replace("{ $_=org.avatar2.client.AvatarFirstPerson.indexBuffer($0);if($_==null) $_=$proceed($$); }");
                    } else if(call.getMethodName().equals("getIndexCount")) {
                        counts[1]++;call.replace("{ $_=org.avatar2.client.AvatarFirstPerson.indexCount($0,$proceed($$)); }");
                    }
                }
            });
        }
        if(counts[0]!=3 || counts[1]!=3) throw new IllegalStateException("Unexpected Wurm first-person render indices: "+java.util.Arrays.toString(counts));
    }

    static void installWalkMotion(ClassPool pool) throws Exception {
        CtClass model = pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel");
        model.getDeclaredConstructor(new CtClass[]{model}).insertAfter(
                "{ com.wurmonline.client.renderer.model.collada.animation.AvatarWalkMotion.copied($1, this); }");
        pool.get("com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics")
                .getDeclaredMethod("calculateForwardKinematics")
                .insertAfter("{ org.avatar2.client.AvatarLocomotion.sampled($2,$3,$4); com.wurmonline.client.renderer.model.collada.animation.AvatarWalkMotion.sampled($1, $2, $3, $4);"
                        + " org.avatar2.client.AvatarShieldWalk.sampled($2, $3, $4); }");
        pool.get("com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics")
                .getDeclaredMethod("blendAnimation", new CtClass[]{
                        pool.get("com.wurmonline.client.renderer.model.collada.animation.ColladaAnimation"),
                        pool.get("com.wurmonline.client.renderer.model.collada.animation.ColladaAnimation"), model,
                        CtClass.floatType, CtClass.booleanType, CtClass.booleanType})
                .insertAfter("{ org.avatar2.client.AvatarShieldWalk.blended($1,$2,$3,$4,$5,$6); }");
        pool.get("com.wurmonline.client.renderer.model.collada.ColladaModelData")
                .getDeclaredMethod("calculateSkins").insertAfter(
                "{ org.avatar2.client.AvatarShieldWalk.publishFrame(this.getModel()); }");
        CtMethod move=pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable").getDeclaredMethod("applyMoveAnimation");
        move.insertBefore("{ org.avatar2.client.AvatarLocomotion.begin(this); }");
        pool.get("com.wurmonline.client.renderer.PlayerBodyRenderable").getDeclaredMethod("blendBetweenIdleToWalk")
                .insertBefore("{ if (!$2 && !player.isFlying() && player.getCarrierCreature()==null"
                        + " && org.avatar2.client.AvatarLocomotion.startsWithoutIdleWait(this,$1)) {"
                        + " blendToStart=false; this.setIsWalking(true); return true; } }");
        final int[] counts=new int[3];
        move.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if(call.getClassName().equals("com.wurmonline.client.timing.IFloat") && call.getMethodName().equals("getValue")) {
                    counts[0]++;call.replace("{ $_ = org.avatar2.client.AvatarLocomotion.phase(this,$proceed($$)); }");
                } else if(call.getClassName().equals("com.wurmonline.client.renderer.cell.MovementData") && call.getMethodName().equals("getWalkSpeed")) {
                    counts[1]++;call.replace("{ $_ = org.avatar2.client.AvatarLocomotion.animationSpeed(this,$proceed($$)); }");
                }
            }
        });
        CtMethod transition=pool.get("com.wurmonline.client.renderer.model.collada.animation.ForwardKinematics")
                .getDeclaredMethod("blendBetweenAnimation");
        CtClass clipClass=pool.get("com.wurmonline.client.renderer.model.collada.animation.ColladaAnimation");
        transition.addLocalVariable("avatarFrom",clipClass);transition.addLocalVariable("avatarTo",clipClass);
        transition.insertBefore("{ avatarFrom=$1; avatarTo=$2; }");
        transition.instrument(new ExprEditor() {
            @Override public void edit(MethodCall call) throws javassist.CannotCompileException {
                if(call.getClassName().equals("com.wurmonline.client.game.World") && call.getMethodName().equals("getFrameDelta")) {
                    counts[2]++;call.replace("{ $_ = org.avatar2.client.AvatarLocomotion.transitionDelta(avatarFrom,avatarTo,$proceed($$)); }");
                }
            }
        });
        if(counts[0]!=1 || counts[1]!=5 || counts[2]!=1)
            throw new IllegalStateException("Unexpected Wurm local locomotion hooks: "+java.util.Arrays.toString(counts));
    }

    static void installEquipmentSockets(ClassPool pool) throws Exception {
        CtClass model = pool.get("com.wurmonline.client.renderer.model.collada.importer.ColladaModel");
        String adapter = "org.avatar2.client.AvatarEquipmentSockets";
        model.getDeclaredConstructor(new CtClass[]{model}).insertAfter(
                "{ " + adapter + ".copied($1, this); }");
        model.getDeclaredMethod("hasNull").insertBefore(
                "{ if (" + adapter + ".id(this,$1) >= 0) return true; }");
        model.getDeclaredMethod("getNullId").insertBefore(
                "{ int virtualId = " + adapter + ".id(this,$1); if (virtualId >= 0) return virtualId; }");
        for (String name : new String[]{"getNullTransformation", "getNullOriginalTransformation"})
            for (CtMethod method : model.getDeclaredMethods(name)) {
                CtClass[] parameters = method.getParameterTypes();
                String key = parameters[0] == CtClass.intType ? "Integer.valueOf($1)" : "$1";
                String destination = parameters.length == 1 ? "null" : "$2";
                method.insertBefore("{ com.wurmonline.client.renderer.model.collada.math.Matrix4f virtualFrame = "
                        + adapter + ".transform(this," + key + "," + destination + ","
                        + name.equals("getNullOriginalTransformation") + "); if (virtualFrame != null) return virtualFrame; }");
            }
        for (String name : new String[]{"getNullAttachmentOffset", "getOriginalNullAttachmentOffset"})
            for (CtMethod method : model.getDeclaredMethods(name)) {
                String key = method.getParameterTypes()[0] == CtClass.intType ? "Integer.valueOf($1)" : "$1";
                method.insertBefore("{ com.wurmonline.math.Vector virtualOffset = " + adapter
                        + ".offset(this," + key + ",$2," + name.equals("getOriginalNullAttachmentOffset")
                        + "); if (virtualOffset != null) return virtualOffset; }");
            }
        for (String name : new String[]{"getNullRotation", "getNullScale"})
            model.getDeclaredMethod(name).insertBefore("{ com.wurmonline.math.Vector virtualValue = " + adapter
                    + (name.equals("getNullScale") ? ".scale" : ".rotation")
                    + "(this,$1); if (virtualValue != null) return virtualValue; }");
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
