package org.avatar2.client;

import javassist.*;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;

/** Native window, layout and pointer input; replace only GL/fonts and unrelated HUD startup. */
public final class StudioPreviewProbe {
    public static void main(String[] args) throws Exception {
        ClassPool pool=new ClassPool(true);String gui="com.wurmonline.client.renderer.gui.";
        String probe=gui+"StudioPreviewHarness";
        CtClass fonts=pool.get(gui+"text.TextFont");fonts.getClassInitializer().setBody("{}");
        fonts.getDeclaredMethod("getText",new CtClass[]{pool.get("java.lang.String")})
                .setBody("{ return new "+probe+".ProbeFont($1); }");
        CtClass component=pool.get(gui+"WurmComponent");component.getClassInitializer().setBody("{}");
        component.getDeclaredMethod("fillRect").setBody("{ "+probe+".rect($2,$3,$4,$5,$6,$7,$8,$9); }");
        CtClass hud=pool.get(gui+"HeadsUpDisplay");hud.getClassInitializer()
                .setBody("{ scissor=new com.wurmonline.client.renderer.backend.ScissorControl(); }");
        // SDK show/owner binding stays real; omit unrelated full-game HUD side effects.
        hud.getDeclaredMethod("addComponent").setBody("{ return components.add($1); }");
        hud.getDeclaredMethod("showComponent").setBody("{}");
        hud.getDeclaredMethod("hideComponent").setBody("{}");
        hud.getDeclaredMethod("setActiveWindow").setBody("{}");
        CtClass scissor=pool.get("com.wurmonline.client.renderer.backend.ScissorControl");
        scissor.getDeclaredMethod("pushClip").setBody("{ return "+probe+".clip($1,$2,$3,$4); }");
        scissor.getDeclaredMethod("popClip").setBody("{ "+probe+".unclip(); }");
        CtClass canvas=pool.get(gui+"ChamomiloUiV1Canvas");
        canvas.getDeclaredMethod("fill").setBody("{ "+probe+".rect($1.red,$1.green,$1.blue,$2,$3,$4,$5,$6); }");
        canvas.getDeclaredMethod("texture").setBody("{ return "+probe+".texture($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11); }");
        pool.get(gui+"text.ChamomiloUiV1Fonts").getDeclaredMethod("caption",new CtClass[]{
                CtClass.intType,CtClass.booleanType,pool.get("org.chamomilo.wurm.ui.v1.UiDensity")})
                .setBody("{ return "+probe+".captionFont($1,$2,$3); }");
        CtClass renderer=pool.get(gui+"Renderer");
        if(renderer.getClassInitializer()!=null) renderer.getClassInitializer().setBody("{}");
        for(CtMethod method:renderer.getDeclaredMethods("texturedQuadAlphaBlend"))
            if(method.getParameterTypes().length==14) method.setBody("{}");
        pool.get(gui+"WButton").getDeclaredMethod("renderComponent").setBody("{}");
        ClientHooks.installAvatarWeights(pool);ClientHooks.installGeometryPrototype(pool);
        ClientHooks.installHeightPrototype(pool);ClientHooks.installEquipmentSockets(pool);
        ClientHooks.installAgeSkin(pool);
        DefrostingClassLoader loader=new DefrostingClassLoader(pool);
        loader.loadClass(probe).getMethod("run",String.class).invoke(null,args[0]);
    }
}
