package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import org.avatar2.client.*;
import org.avatar2.core.*;
import org.avatar2.lab.AnimationLab;
import org.chamomilo.wurm.ui.v1.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Exercises production Studio controls against imported bodies and the actual Lab command. */
public final class StudioPreviewHarness {
    private static final BufferedImage canvas=new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB);
    private static final Graphics2D graphics=canvas.createGraphics();
    private static final Map<UiAsset,BufferedImage> assets=new EnumMap<>(UiAsset.class);
    private static final Map<String,BufferedImage> tinted=new HashMap<>();
    private static final Deque<Shape> clips=new ArrayDeque<>();
    private static final List<Label> labels=new ArrayList<>();
    private static int fontSize=12;
    public static void run(String root) throws Exception {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        File output=new File(root,"build/reports/avatar2/studio");output.mkdirs();
        File profileFile=new File(output,"avatar-profile.properties");
        AvatarProfileRepository repository=new AvatarProfileRepository(profileFile);
        repository.save(AvatarProfile.bodybuilder(AvatarProfile.Sex.MALE));
        byte[] saved=java.nio.file.Files.readAllBytes(profileFile.toPath());
        AvatarStudioController controller=new AvatarStudioController(null,repository);controller.initialize();
        WurmComponent.SCREEN_WIDTH=1920;WurmComponent.SCREEN_HEIGHT=1080;
        try {
            AvatarStudioWindow window=new AvatarStudioWindow(controller);window.gameTick();window.setPosition(20,20);
            window.show(fixtureHud());int normalWidth=window.width,normalHeight=window.height;
            window.toggleMaximized();check(window.width==1900&&window.height==1060,"SDK binds maximize to HUD");
            window.toggleMaximized();check(window.width==normalWidth&&window.height==normalHeight,"SDK restores window size");
            AvatarStudioPanel panel=panel(window.getComponent());check(panel!=null,"Actual native content exists");
            window.restorePositionHints(new com.wurmonline.client.settings.WindowPosition(1268,194,650,540,0));
            check(window.height>=740,"Restored legacy height preserves Blend and skin controls");
            snapshot(window,new File(output,"lab-off.png"));
            for(Object control:(List<?>)field(AvatarStudioPanel.class,"hits").get(panel)) {
                Object action=field(control.getClass(),"action").get(control);
                check(!action.equals("athletic")&&!action.equals("bodybuilder"),"Retired topology selectors are absent");
            }
            check(!controller.testControls(false).ready,"Lab off disables tests");
            click(panel,"bodyShape:mpfb-heavy");snapshot(window,null);
            drag(panel,"shapeMix",145);drag(panel,"heightDirts",999);click(panel,"bodySkin:middle");
            check(controller.dirty()&&controller.profile().shapeMix==.5f&&controller.profile().heightMetres==2
                    &&controller.profile().ageSkin.equals("middle"),"Body controls edit the persistent draft without Lab");
            check(AvatarRuntimeState.profile().toProperties().equals(controller.profile().toProperties()),
                    "Body controls preview immediately without Apply");
            check(Arrays.equals(saved,java.nio.file.Files.readAllBytes(profileFile.toPath())),"Live preview does not write the profile");
            click(panel,"save");
            AvatarStudioController restart=new AvatarStudioController(null,repository);restart.initialize();
            check(restart.profile().shape.equals("mpfb-heavy")&&restart.profile().shapeMix==.5f
                    &&restart.profile().heightMetres==2&&restart.profile().ageSkin.equals("middle"),"Body controls survive restart");
            snapshot(window,new File(output,"saved-body.png"));
            click(panel,"bodyShape:blend");snapshot(window,null);
            check(controller.profile().blendHeavy==.5f&&controller.profile().shapeMix==1,"Blend conversion preserves Heavy and restores full slider range");
            drag(panel,"blendAthletic",145);drag(panel,"blendOlder",72);click(panel,"save");
            AvatarProfile blendSaved=repository.load();
            check(blendSaved.geometry().equals(controller.profile().geometry()),"Body Save retains all blend shares");
            snapshot(window,new File(output,"saved-blend.png"));
            click(panel,"bodyShape:proportions");snapshot(window,null);
            drag(panel,"buildMuscle",72);drag(panel,"buildFat",217);drag(panel,"buildShoulders",217);drag(panel,"headSize",217);click(panel,"bodyAge:old");click(panel,"save");
            AvatarProfile buildSaved=repository.load();
            check(buildSaved.shape.equals("proportions")&&buildSaved.buildMuscle==72/290f
                    &&buildSaved.buildFat==217/290f&&buildSaved.buildShoulders==217/290f,"Native Build sliders save independent values");
            check(buildSaved.headSize==217/290f,"Native head slider saves exact size");
            check(buildSaved.agePreset.equals("old")&&buildSaved.ageSkin.equals("old"),"Native body age saves body and skin");
            snapshot(window,new File(output,"saved-build.png"));
            drag(panel,"buildMuscle",999);click(panel,"revert");
            check(controller.profile().geometry().equals(buildSaved.geometry()),"Revert restores saved Build values");
            click(panel,"sex");snapshot(window,null);drag(panel,"buildBreast",217);click(panel,"save");
            check(repository.load().sex==AvatarProfile.Sex.FEMALE&&repository.load().buildBreast==217/290f,"Body breast slider saves female volume");
            snapshot(window,new File(output,"saved-breast.png"));
            drag(panel,"buildBreast",72);click(panel,"revert");
            check(controller.profile().buildBreast==217/290f,"Body Revert restores breast volume");
            click(panel,"sex");snapshot(window,null);
            check(controller.profile().geometry().buildBreast==.5f,"Male body ignores the stored female volume");
            click(panel,"sex");snapshot(window,null);check(controller.profile().buildBreast==217/290f,"Female choice survives sex switch");
            click(panel,"sex");snapshot(window,null);
            click(panel,"reset");click(panel,"save");saved=java.nio.file.Files.readAllBytes(profileFile.toPath());
            click(panel,"lab");snapshot(window,null);
            click(panel,"motion:run");check(controller.testControls(false).clip.equals("game"),"Disabled motion has no effect");
            click(panel,"labToggle");check(AnimationLab.isEnabled(),"Lab enables on native release");
            for(String sex:new String[]{"male","female"}) {
                controller.profile().sex=AvatarProfile.Sex.valueOf(sex.toUpperCase(Locale.ROOT));controller.perform("save");
                saved=java.nio.file.Files.readAllBytes(profileFile.toPath());
                ColladaModel body=GeometryRuntimeHarness.load(new File(root,"assets/runtime/body/"+sex+"-bodybuilder.dae"));
                AvatarAnimationBridge.normalizeSkinWeights(body);
                try(InputStream in=new FileInputStream(new File(root,"assets/runtime/morph/"+sex+"-bodybuilder.avm"))) {
                    AvatarGeometryPrototype.registerWithMorphs(body,MorphBank.read(in));
                }
                AvatarAgeSkin.register(body,new com.wurmonline.client.resources.AgeSkinFixtureUrl(new File(root),"avatar2/body/"+sex+"-bodybuilder.dae"));
                AnimationLab.registerLocalModel(body);snapshot(window,null);
                AnimationLab.StudioControls controls=controller.testControls(false);
                float importedHeight=controls.heightDirts;
                check(controls.ready&&controls.shapes.size()==6,"All authored shapes, Blend and Build for "+sex);
                drag(panel,"testHeight",-50);check(Math.abs(controller.value("testHeight")-13)<.0001,"Height lower clamp");
                drag(panel,"testHeight",999);check(Math.abs(controller.value("testHeight")-20)<.0001,"Height upper clamp");
                click(panel,"shape:mpfb-heavy");snapshot(window,null);
                check(controller.testControls(false).shapeMix==1,"First authored shape selection applies visibly");
                drag(panel,"testMix",999);controls=controller.testControls(false);
                check(controls.shape.equals("mpfb-heavy")&&controls.shapeMix==1,"Shape amount upper endpoint");
                drag(panel,"testMix",-50);check(controller.value("testMix")==0,"Shape amount lower endpoint");
                drag(panel,"testMix",145);check(controller.value("testMix")==.5f,"Shape interpolation");
                click(panel,"shape:mpfb-older");
                check(controller.testControls(false).shapeMix==.5f,"Switching authored shapes preserves the chosen blend");
                click(panel,"shape:none");
                check(controller.testControls(false).shapeMix==0,"Original clears the authored blend");
                click(panel,"shape:mpfb-heavy");
                check(controller.testControls(false).shapeMix==1,"Selecting a shape again starts at full amount");
                click(panel,"motion:run");check(controller.testControls(false).clip.equals("run"),"Run preview");
                Object run=hit(panel,"motion:idle");int[] box=box(run);
                panel.leftPressed(box[0]+10,box[1]+10,0);
                check(controller.testControls(false).clip.equals("run"),"Button does not activate on press");
                panel.mouseDragged(-1,-1);panel.leftReleased(box[0]+10,box[1]+10);
                check(controller.testControls(false).clip.equals("run"),"Dragging away cancels button");
                click(panel,"skin:old");check(controller.testControls(false).ageSkin.equals("old"),"Native age skin uses the Lab command");
                AnimationLab.studioHeight(13);snapshot(window,new File(output,sex+"-bodybuilder.png"));
                click(panel,"testReset");controls=controller.testControls(false);
                check(controls.shape.equals("none")&&controls.clip.equals("game")&&controls.shapeMix==0,"Reset clears preview and geometry");
                check(controls.ageSkin.equals("original"),"Reset restores original skin");
                check(Math.abs(controls.heightDirts-importedHeight)<.0001,"Reset restores imported height");
                check(!controller.dirty()&&Arrays.equals(saved,java.nio.file.Files.readAllBytes(profileFile.toPath())),
                        "Tests leave persistent profile untouched");
                click(panel,"shape:mpfb-older");snapshot(window,null);drag(panel,"testMix",107);
                drag(panel,"testHeight",91);click(panel,"skin:middle");click(panel,"motion:run");
                AnimationLab.StudioControls selected=controller.testControls(false);
                click(panel,"save");controls=controller.testControls(false);
                check(repository.load().shape.equals(selected.shape)&&repository.load().shapeMix==selected.shapeMix
                        &&repository.load().ageSkin.equals(selected.ageSkin),"Lab Save keeps the selected appearance");
                check(Math.abs(repository.load().heightMetres*10-selected.heightDirts)<.00001,
                        "Lab Save keeps the selected physical height");
                check(controls.shape.equals(selected.shape)&&controls.shapeMix==selected.shapeMix
                        &&controls.heightDirts==selected.heightDirts&&controls.ageSkin.equals(selected.ageSkin)
                        &&controls.clip.equals("run"),"Lab Save does not reset sliders or motion");
                click(panel,"testReset");controls=controller.testControls(false);
                check(controls.shape.equals(selected.shape)&&controls.shapeMix==selected.shapeMix
                        &&Math.abs(controls.heightDirts-selected.heightDirts)<.00001&&controls.ageSkin.equals(selected.ageSkin),
                        "Reset test returns to the explicitly saved Lab appearance");
                click(panel,"shape:blend");snapshot(window,null);
                check(controller.testControls(false).blendOlder==selected.shapeMix&&controller.testControls(false).shapeMix==1,"Lab Blend conversion preserves Older and restores full slider range");
                drag(panel,"testAthletic",145);drag(panel,"testHeavy",999);click(panel,"save");
                controls=controller.testControls(false);
                check(repository.load().geometry().equals(AnimationLab.studioProfile().geometry()),"Lab Save retains the mixed body");
                check(controls.blendAthletic==0&&controls.blendHeavy==1&&controls.blendOlder==0&&controls.shapeMix==1,"Native Lab slider reaches the complete Heavy endpoint");
                snapshot(window,new File(output,sex+"-blend-lab.png"));
                click(panel,"shape:proportions");snapshot(window,null);
                drag(panel,"testMuscle",72);drag(panel,"testFat",217);drag(panel,"testHeadSize",72);click(panel,"age:middle");click(panel,"save");
                controls=controller.testControls(false);
                check(controls.buildMuscle==72/290f&&controls.buildFat==217/290f,"Lab Build sliders stay independent");
                check(controls.headSize==72/290f && repository.load().headSize==72/290f,"Lab head Save persists exact size");
                check(controls.agePreset.equals("middle")&&repository.load().agePreset.equals("middle"),"Native Lab Save retains adult body preset");
                check(repository.load().geometry().equals(AnimationLab.studioProfile().geometry()),"Lab Build Save retains the tested body");
                snapshot(window,new File(output,sex+"-build-lab.png"));
                Avatar2Mod languageHost=new Avatar2Mod();
                for(String language:new String[]{"en","pt-BR","de","ru"}) {
                    languageHost.setUserLanguage(language);snapshot(window,new File(output,sex+"-age-"+language+".png"));
                    check(controller.testControls(false).agePreset.equals("middle"),"Language switch changed adult draft");
                }
                languageHost.setUserLanguage("en");
                int[] skinBounds=box(hit(panel,"skin:old")),saveBounds=box(hit(panel,"save"));
                check(saveBounds[1]>=skinBounds[1]+skinBounds[3]+16,"Age controls retain a gap above Save/Revert");
                check(controls.breastSupported==sex.equals("female"),"Breast control availability matches the actual binding");
                if(controls.breastSupported) {
                    drag(panel,"testBreast",217);click(panel,"save");
                    check(repository.load().buildBreast==217/290f&&controller.testControls(false).buildBreast==217/290f,"Lab Save retains female breast volume");
                    snapshot(window,new File(output,"female-breast-lab.png"));
                    drag(panel,"testBreast",0);click(panel,"testReset");
                    check(controller.testControls(false).buildBreast==217/290f,"Lab Reset restores saved breast volume");
                }
                drag(panel,"testFat",0);click(panel,"testReset");
                check(controller.testControls(false).buildFat==217/290f,"Lab Reset restores saved Build values");
                click(panel,"body");snapshot(window,null);click(panel,"reset");click(panel,"save");
                saved=java.nio.file.Files.readAllBytes(profileFile.toPath());click(panel,"lab");snapshot(window,null);
            }
            for(int size:new int[]{10,18}) {
                fontSize=size;AvatarStudioWindow fonts=new AvatarStudioWindow(controller);fonts.gameTick();fonts.setPosition(20,20);
                snapshot(fonts,new File(output,"font-"+size+".png"));
                AvatarStudioPanel content=panel(fonts.getComponent());click(content,"lab");snapshot(fonts,new File(output,"lab-font-"+size+".png"));
                click(content,"info");
                snapshot(fonts,new File(output,"info-"+size+".png"));
                fonts.setSize(240,120);check(fonts.width>=650&&fonts.height>=740,"Minimum preserves controls");
                fonts.toggleCollapsed();check(fonts.minimized,"Native collapse works");
                fonts.toggleCollapsed();check(!fonts.minimized,"Native restore works");
            }
            fontSize=12;window.mouseExited();snapshot(window,null);BufferedImage a=copy(window);
            labels.clear();window.render(null,.1f);check(same(a,copy(window)),"HUD transition alpha leaves the window opaque");
            click(panel,"labToggle");check(!AnimationLab.isEnabled(),"Lab off clears controls");
            System.out.println("AVATAR2_STUDIO_OK immediate Body preview, exact Body save/restart, explicit Lab appearance save without slider/motion reset, separate unsaved trials, native window/input, release cancellation, height clamps, both shape banks, preview/reset, profile isolation, font sizes 10/18, minimum size, collapse and fixed alpha; previews="+output);
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);graphics.dispose();}
    }
    private static AvatarStudioPanel panel(WurmComponent node) throws Exception {
        if(node instanceof AvatarStudioPanel)return (AvatarStudioPanel)node;
        if(node instanceof WurmBorderPanel)for(FlexComponent child:(FlexComponent[])field(WurmBorderPanel.class,"components").get(node))
            if(child!=null){AvatarStudioPanel result=panel(child);if(result!=null)return result;}
        return null;
    }
    private static Object hit(AvatarStudioPanel panel,String action) throws Exception {
        for(Object hit:(List<?>)field(AvatarStudioPanel.class,"hits").get(panel))
            if(field(hit.getClass(),"action").get(hit).equals(action))return hit;
        throw new AssertionError("Missing hit: "+action);
    }
    private static int[] box(Object hit) throws Exception {int[] result=new int[4];String[] names={"x","y","width","height"};
        for(int i=0;i<4;i++)result[i]=field(hit.getClass(),names[i]).getInt(hit);return result;}
    private static void click(AvatarStudioPanel panel,String action) throws Exception {
        Object target=hit(panel,action);int[] b=box(target);panel.leftPressed(b[0]+b[2]/2,b[1]+b[3]/2,0);panel.leftReleased(b[0]+b[2]/2,b[1]+b[3]/2);
    }
    private static void drag(AvatarStudioPanel panel,String action,int offset) throws Exception {
        int[] b=box(hit(panel,action));panel.leftPressed(b[0]+b[2]/2,b[1]+10,0);panel.mouseDragged(b[0]+offset,b[1]+10);panel.leftReleased(b[0]+offset,b[1]+10);
    }
    private static Field field(Class<?> type,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static HeadsUpDisplay fixtureHud() throws Exception {
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        HeadsUpDisplay hud=(HeadsUpDisplay)unsafe.allocateInstance(HeadsUpDisplay.class);
        field(HeadsUpDisplay.class,"width").setInt(hud,1920);field(HeadsUpDisplay.class,"height").setInt(hud,1080);
        field(HeadsUpDisplay.class,"components").set(hud,new ArrayList<WurmComponent>());return hud;
    }
    private static void snapshot(AvatarStudioWindow window,File output) throws Exception {
        check(window.x>=0&&window.y>=0&&window.x+window.width<=canvas.getWidth()
                &&window.y+window.height<=canvas.getHeight(),"Preview canvas contains the restored window");
        labels.clear();rect(.025f,.023f,.018f,1f,0,0,canvas.getWidth(),canvas.getHeight());
        AvatarStudioPanel panel=panel(window.getComponent());
        if(panel!=null)for(UiButtonMotion motion:((Map<String,UiButtonMotion>)field(AvatarStudioPanel.class,"motion").get(panel)).values())motion.setAnimationsEnabled(false);
        window.render(null,1);
        check(!labels.isEmpty(),"Preview contains actual native captions");
        for(Label label:labels)check(label.x>=window.x&&label.x+label.width<=window.x+window.width,
                "Text stays inside window: "+label.value);
        if(output!=null)ImageIO.write(copy(window),"png",output);
    }
    private static BufferedImage copy(WurmComponent window) {
        BufferedImage image=new BufferedImage(window.width,window.height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.drawImage(canvas,0,0,window.width,window.height,window.x,window.y,window.x+window.width,window.y+window.height,null);g.dispose();return image;
    }
    private static boolean same(BufferedImage a,BufferedImage b) {
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;return true;
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    public static boolean clip(int x,int y,int w,int h) {clips.push(graphics.getClip()==null?new Rectangle(0,0,canvas.getWidth(),canvas.getHeight()):graphics.getClip());graphics.clipRect(x,y,w,h);return w>0&&h>0;}
    public static void unclip() {graphics.setClip(clips.pop());}
    public static void rect(float r,float g,float b,float a,int x,int y,int w,int h) {graphics.setColor(new Color(r,g,b,a));graphics.fillRect(x,y,w,h);}
    public static boolean texture(UiAsset asset,float tint,float alpha,int x,int y,int w,int h,float u0,float v0,float u1,float v1) throws Exception {
        check(alpha==1,"Fixed texture alpha");BufferedImage source=assets.get(asset);
        if(source==null){try(InputStream in=UiResources.open(asset)){source=ImageIO.read(in);}assets.put(asset,source);}
        String key=asset.name()+":"+Float.floatToIntBits(tint);BufferedImage image=tinted.get(key);
        if(image==null){image=new RescaleOp(new float[]{tint,tint,tint,1},new float[4],null).filter(source,null);tinted.put(key,image);}
        graphics.drawImage(image,x,y,x+w,y+h,Math.round(u0*source.getWidth()),Math.round(v0*source.getHeight()),Math.round(u1*source.getWidth()),Math.round(v1*source.getHeight()),null);return true;
    }
    public static TextFont captionFont(int size,boolean bold,UiDensity density) {return new ProbeFont(UiTypography.font(size,bold,density),true);}
    private static final class Label {final String value;final int x,y,width;Label(String value,int x,int y,int width){this.value=value;this.x=x;this.y=y;this.width=width;}}
    public static final class ProbeFont extends TextFont {
        private final Font font;private final boolean advances;private int x,y;
        public ProbeFont(String name){this(new Font("Verdana",name.equals("bold")?Font.BOLD:Font.PLAIN,fontSize),false);}
        ProbeFont(Font font,boolean advances){this.font=font;this.advances=advances;}
        public void moveTo(int x,int y){this.x=x;this.y=y;}
        public int paint(Queue queue,String value,float r,float g,float b,float a){check(a==1,"Fixed text alpha");labels.add(new Label(value,x,y,getWidth(value)));
            graphics.setFont(font);graphics.setColor(new Color(r,g,b,a));
            if(advances){int px=x;for(char c:value.toCharArray()){graphics.drawString(String.valueOf(c),px,y);px+=metrics().charWidth(c);}}
            else graphics.drawString(value,x,y);return getWidth(value);}
        private FontMetrics metrics(){return graphics.getFontMetrics(font);}
        public int getWidth(String value){if(!advances)return metrics().stringWidth(value);int sum=0;for(char c:value.toCharArray())sum+=metrics().charWidth(c);return sum;}
        public int getWidth(char[] value,int start,int length){return getWidth(new String(value,start,length));}
        public int getHeight(){return metrics().getHeight();}public int getAscent(){return metrics().getAscent();}
        public int getDescent(){return metrics().getDescent();}public int getLeading(){return metrics().getLeading();}
    }
}
