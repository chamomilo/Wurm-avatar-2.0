package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.text.TextFont;
import com.wurmonline.client.renderer.gui.text.ChamomiloUiV1Fonts;
import org.avatar2.client.Avatar2Mod;
import org.avatar2.client.AvatarStudioController;
import org.avatar2.core.AvatarProfile;
import org.avatar2.lab.AnimationLab;
import org.chamomilo.wurm.ui.v1.*;
import java.awt.Rectangle;
import java.util.*;

/** Persistent appearance and separate reversible Lab trials. */
public final class AvatarStudioWindow extends ChamomiloUiV1Window {
    public AvatarStudioWindow(AvatarStudioController controller) {
        super("avatar2.studio","Avatar Studio 2.0","en",UiBackground.LEATHER,5);
        setContent(new AvatarStudioPanel(controller));
        setInitialSize(650,868,false);
        onClose(Avatar2Mod::hideStudioWindow);
    }
    @Override void setSize(int width,int height) {
        super.setSize(Math.max(650,width),Math.max(868,height));
    }
    @Override protected void componentResized() {
        // Wurm restores saved dimensions through final setLocation(), bypassing setSize().
        if (!minimized && (width<650 || height<868)) {
            setSize(width,height);
            return;
        }
        super.componentResized();
    }
}

final class AvatarStudioPanel extends FlexComponent {
    private static final UiScale SCALE=UiScale.BASE;
    private static final int BUTTON_HEIGHT=SCALE.pixels(32);
    private final ChamomiloUiV1Canvas canvas=new ChamomiloUiV1Canvas(this);
    private final AvatarStudioController controller;
    private final List<Hit> hits=new ArrayList<>();
    private final Map<String,UiButtonMotion> motion=new HashMap<>();
    private final Map<String,Group> groups=new HashMap<>();
    private String tab="body";
    private Hit slider,pressed;
    private int mouseX=-1,mouseY=-1;
    AvatarStudioPanel(AvatarStudioController controller) {
        super("Avatar Studio controls",0,0,620,550);this.controller=controller;
        group("tabs",new int[]{190,190,190},"Body","Lab","Info");
        group("body",new int[]{280,280,280,280},"Avatar on","Vanilla body","Sex: MALE","Sex: FEMALE");
        group("lab",new int[]{210,210,144,210},"Lab on","Lab off","Open Lab","Reset test");
        group("shapes",new int[]{92,92,92,92,92,92},"Original","Athletic","Heavy","Older","Blend","Build");
        group("motion",new int[]{140,140,140,140},"Game motion","Idle","Walk","Run");
        group("skins",new int[]{140,140,140,140},"Original skin","Young skin","Middle skin","Old skin");
        // Stable all-language union: new preset peers retain one size/baseline when switched.
        group("ages",new int[]{188,188,188,188,188,188,188,188,188,188,188,188},
                "Young","Middle","Old","Jovem","Maduro","Idoso","Jung","Mittel","Alt","Молодой","Зрелый","Пожилой");
        group("nativeHeight",new int[]{210},"Original height");
        group("footer",new int[]{140,140,140,140},"Apply body","Save body","Revert body","Reset body");
    }
    private void group(String id,int[] widths,String... captions) { groups.put(id,new Group(widths,captions)); }
    @Override protected void renderComponent(Queue queue,float ignoredAlpha) {
        hits.clear();canvas.begin(queue);
        UiPainter.background(canvas,UiBackground.LEATHER,1f,x,y,width,height);
        button(queue,"tabs",x+12,y+8,190,"Body","body",tab.equals("body"),true);
        button(queue,"tabs",x+208,y+8,190,"Lab","lab",tab.equals("lab"),true);
        button(queue,"tabs",x+404,y+8,190,"Info","info",tab.equals("info"),true);
        if(tab.equals("info")) renderInfo(queue);else if(tab.equals("lab")) renderLab(queue);else renderBody(queue);
        renderFooter(queue);
    }
    private void renderBody(Queue queue) {
        AvatarProfile p=controller.profile();int left=x+12,top=y+52;
        button(queue,"body",left,top,280,p.enabled?"Avatar on":"Vanilla body","enabled",p.enabled,true);
        button(queue,"body",left+292,top,280,"Sex: "+p.sex,"sex",false,true);
        paint(queue,"Changes preview immediately. Save keeps them after restart.",left,top+53);
        renderSlider(queue,left,top+70,"Height (dirt)","heightDirts",13,20,true,
                String.format(Locale.ROOT,"%.1f / %.2f m",p.standingHeightMetres()*10,p.standingHeightMetres()));
        button(queue,"nativeHeight",left,top+116,210,"Original height","nativeHeight",p.heightMetres==0,true);
        String[] shapes={"none","mpfb-athletic","mpfb-heavy","mpfb-older","blend","proportions"};
        String[] captions={"Original","Athletic","Heavy","Older","Blend","Build"};
        paint(queue,"Body shape",left,top+180);
        for(int i=0;i<shapes.length;i++) button(queue,"shapes",left+i*98,top+192,92,captions[i],"bodyShape:"+shapes[i],p.shape.equals(shapes[i]),true);
        renderSlider(queue,left,top+234,"Shape amount","shapeMix",0,1,!p.shape.equals("none"),
                String.format(Locale.ROOT,"%.0f%%",p.shapeMix*100));
        if(p.shape.equals("proportions")) {
            renderSlider(queue,left,top+276,"Muscle","buildMuscle",0,1,true,percent(p.buildMuscle));
            renderSlider(queue,left,top+314,"Fullness","buildFat",0,1,true,percent(p.buildFat));
            renderSlider(queue,left,top+352,"Breast volume","buildBreast",0,1,p.sex==AvatarProfile.Sex.FEMALE,
                    p.sex==AvatarProfile.Sex.FEMALE?percent(p.buildBreast):"Female only");
            renderSlider(queue,left,top+390,"Shoulder width","buildShoulders",0,1,true,percent(p.buildShoulders));
        } else {
        boolean blend=p.shape.equals("blend");
        renderSlider(queue,left,top+276,"Athletic share","blendAthletic",0,1,blend,percent(p.blendAthletic));
        renderSlider(queue,left,top+314,"Heavy share","blendHeavy",0,1,blend,percent(p.blendHeavy));
        renderSlider(queue,left,top+352,"Older share","blendOlder",0,1,blend,percent(p.blendOlder));
        paint(queue,"Blend: 100% gives the full shape. Other shares shrink to fit.",left,top+411);
        }
        ageRow(queue,left,top+430,"bodyAge:",p.agePreset,p.shape.equals("proportions"));
        renderSlider(queue,left,top+492,"Head size","headSize",0,1,true,percent(p.headSize));
        paint(queue,"Skin can be changed independently after choosing body age.",left,top+542);
        String[] skins={"original","young","middle","old"};
        String[] skinCaptions={"Original skin","Young skin","Middle skin","Old skin"};
        for(int i=0;i<4;i++) button(queue,"skins",left+i*146,top+554,140,skinCaptions[i],"bodySkin:"+skins[i],p.ageSkin.equals(skins[i]),true);
        paint(queue,"Revert restores your last saved body. Lab has separate temporary tests.",left,top+616);
    }
    private void renderLab(Queue queue) {
        AvatarProfile p=controller.profile();int left=x+12,top=y+52;
        button(queue,"body",left,top,280,p.enabled?"Avatar on":"Vanilla body","enabled",p.enabled,true);
        button(queue,"body",left+292,top,280,"Sex: "+p.sex,"sex",false,true);
        paint(queue,"Temporary tests override the applied Body profile in the world.",left,top+53);
        paint(queue,"Save body keeps the selected height, shape and skin. Motion stays temporary.",left,top+76);
        AnimationLab.StudioControls state=controller.testControls(true);
        int labY=top+93;
        button(queue,"lab",left,labY,210,controller.labEnabled()?"Lab on":"Lab off","labToggle",controller.labEnabled(),true);
        button(queue,"lab",left+216,labY,144,"Open Lab","labOpen",false,true);
        button(queue,"lab",left+366,labY,210,"Reset test","testReset",false,state.ready);
        paint(queue,"Lab off / Reset test returns to the applied Body profile.",left,labY+53);
        renderSlider(queue,left,labY+64,"Height (dirt)","testHeight",13,20,state.ready,
                String.format(Locale.ROOT,"%.1f / %.2f m",state.heightDirts,state.heightDirts/10));
        String[] names={"Original","Athletic","Heavy","Older","Blend","Build"};
        String[] shapes={"none","mpfb-athletic","mpfb-heavy","mpfb-older","blend","proportions"};
        for(int i=0;i<shapes.length;i++) button(queue,"shapes",left+i*98,labY+106,92,names[i],"shape:"+shapes[i],
                state.shape.equals(shapes[i]),state.ready&&state.shapes.contains(shapes[i]));
        renderSlider(queue,left,labY+146,"Shape amount","testMix",0,1,state.ready&&!state.shape.equals("none"),
                String.format(Locale.ROOT,"%.0f%%",state.shapeMix*100));
        if(state.shape.equals("proportions")) {
            renderSlider(queue,left,labY+190,"Muscle","testMuscle",0,1,state.ready,percent(state.buildMuscle));
            renderSlider(queue,left,labY+228,"Fullness","testFat",0,1,state.ready,percent(state.buildFat));
            renderSlider(queue,left,labY+266,"Breast volume","testBreast",0,1,state.ready&&state.breastSupported,
                    state.breastSupported?percent(state.buildBreast):"Female only");
            renderSlider(queue,left,labY+304,"Shoulder width","testShoulders",0,1,state.ready,percent(state.buildShoulders));
        } else {
        boolean blend=state.ready&&state.shape.equals("blend");
        renderSlider(queue,left,labY+190,"Athletic share","testAthletic",0,1,blend,percent(state.blendAthletic));
        renderSlider(queue,left,labY+228,"Heavy share","testHeavy",0,1,blend,percent(state.blendHeavy));
        renderSlider(queue,left,labY+266,"Older share","testOlder",0,1,blend,percent(state.blendOlder));
        paint(queue,state.ready?"100% gives the full shape; others shrink. Reset restores the profile.":
                "Enable Lab and show the local Avatar body to test these controls.",left,labY+318);
        }
        ageRow(queue,left,labY+342,"age:",state.agePreset,state.ready&&state.shape.equals("proportions"));
        renderSlider(queue,left,labY+404,"Head size","testHeadSize",0,1,state.ready,percent(state.headSize));
        String[] clips={"game","idle","walk","run"};String[] captions={"Game motion","Idle","Walk","Run"};
        for(int i=0;i<4;i++) button(queue,"motion",left+i*146,labY+466,140,captions[i],"motion:"+clips[i],
                state.clip.equals(clips[i]),state.ready);
        paint(queue,"Skin test can override the selected adult body preset.",left,labY+520);
        String[] skins={"original","young","middle","old"};
        String[] skinCaptions={"Original skin","Young skin","Middle skin","Old skin"};
        for(int i=0;i<4;i++) button(queue,"skins",left+i*146,labY+532,140,skinCaptions[i],"skin:"+skins[i],
                state.ageSkin.equals(skins[i]),state.ready&&state.skins.contains(skins[i]));
    }
    private void ageRow(Queue queue,int left,int top,String action,String selected,boolean enabled) {
        String language=Avatar2Mod.studioLanguage();
        String[] captions=language.equals("ru")?new String[]{"Молодой","Зрелый","Пожилой"}:
                language.equals("de")?new String[]{"Jung","Mittel","Alt"}:
                language.equals("pt-BR")?new String[]{"Jovem","Maduro","Idoso"}:new String[]{"Young","Middle","Old"};
        String title=language.equals("ru")?"Возраст тела (Build)":language.equals("de")?"Körperalter (Build)":
                language.equals("pt-BR")?"Idade corporal (Build)":"Body age (Build)";
        paint(queue,title,left,top+17);
        String[] presets={"young","middle","old"};
        for(int i=0;i<3;i++)button(queue,"ages",left+i*194,top+24,188,captions[i],action+presets[i],selected.equals(presets[i]),enabled);
    }
    private void renderInfo(Queue queue) {
        int left=x+16,top=y+72;
        String[] lines={"Avatar 2.0 "+Avatar2Mod.VERSION,"Native body rig with idle, walk and run.",
                "Height: 13..20 dirt (1.3..2.0 metres), proportional on all axes.",
                "Authored shape banks: Athletic, heavier and older adult.",
                "One body per sex; Athletic, heavier and older are body shapes.",
                "Blend combines Athletic, Heavy and Older with bounded shares.",
                "Build controls muscle and fullness with a separate authored grid.",
                "Female Build has independent breast volume; 50% retains the build.",
                "Build shoulder width: 0% narrow / 50% original / 100% wide.",
                "Head size: 0% smaller / 50% original / 100% larger (92..108%).",
                "Build has Young, Middle and Old adult body presets with skin.",
                "Body tab saves the complete appearance in profile schema 8.",
                "Body edits preview immediately. Save keeps the selected appearance.",
                "Lab Save keeps height, shape and skin; motion/pose tests stay temporary.",
                "Reset test, Lab off and timeout return to the applied profile.",
                "First-person view hides head/eyes and closes the lower neck.",
                "Weapons and shields keep their own size across body heights.",
                hairInfo()};
        for(String line:lines) {paint(queue,line,left,top);top+=28;}
    }
    private String hairInfo() {
        String language=Avatar2Mod.studioLanguage();
        return language.equals("ru")?"Волосы Wurm следуют голове. Броня и борода — в разработке.":
                language.equals("de")?"Wurmhaare folgen dem Kopf. Rüstung und Bart folgen später.":
                language.equals("pt-BR")?"Cabelo Wurm acompanha a cabeça. Armadura e barba: em desenvolvimento.":
                "Native hair follows the head. Armour and beard fitting: pending.";
    }
    private void renderFooter(Queue queue) {
        int left=x+12,top=y+height-65;
        String[] captions={"Apply body","Save body","Revert body","Reset body"};
        String[] actions={"apply","save","revert","reset"};
        for(int i=0;i<4;i++) button(queue,"footer",left+i*146,top,140,captions[i],actions[i],false,true);
        paint(queue,(controller.dirty()?"* ":"")+controller.status(),left,y+height-10);
    }
    private void renderSlider(Queue queue,int left,int top,String label,String control,float min,float max,boolean enabled,String value) {
        int barX=left+145,barWidth=290;float fraction=Math.max(0,Math.min(1,(controller.value(control)-min)/(max-min)));
        paint(queue,label,left,top+23,137);
        UiRect bar=new UiRect(barX,top+13,barWidth,12);
        UiHudPainter.back(canvas,UiBackground.WALNUT,1f,bar);
        UiHudPainter.value(canvas,UiAxis.HORIZONTAL,fraction,UiColor.rgb(enabled?0x9b8044:0x514a39),1f,bar);
        UiPainter.frame(canvas,3,1f,barX,top+13,barWidth,12);
        canvas.fill(UiColor.rgb(enabled?0xfff09a:0x777466),1f,barX+Math.round(fraction*barWidth)-3,top+9,7,20);
        paint(queue,value,barX+barWidth+10,top+23);
        if(enabled) hits.add(new Hit(control,barX,top,barWidth,38,min,max));
    }
    private static String percent(float value) {return String.format(Locale.ROOT,"%.0f%%",value*100);}
    private void button(Queue queue,String group,int bx,int by,int bw,String caption,String action,boolean selected,boolean enabled) {
        Hit hit=new Hit(action,bx,by,bw,BUTTON_HEIGHT);hit.available=enabled;hits.add(hit);
        UiButtonMotion animation=motion.get(action);if(animation==null) {animation=new UiButtonMotion();motion.put(action,animation);}
        boolean hover=hit.contains(mouseX,mouseY);
        animation.update(enabled,hover,pressed!=null&&pressed.action.equals(action)&&hover,System.nanoTime());
        UiPainter.button(canvas,animation.brightness(),animation.depth(),animation.hover(),SCALE,1f,bx,by,bw,BUTTON_HEIGHT);
        Group g=groups.get(group);boolean bold=enabled&&hover;
        TextFont font=ChamomiloUiV1Fonts.caption(g.size,bold,UiDensity.HIGH);
        Rectangle ink=UiTypography.ink(caption,g.size,bold,UiDensity.HIGH);
        int left=Math.min(0,ink.x),right=Math.max(UiTypography.width(caption,g.size,bold,UiDensity.HIGH),ink.x+ink.width);
        int offset=Math.round(animation.depth()*SCALE.factor);
        UiColor color=UiPainter.buttonCaptionColor(enabled,animation.hover());
        font.moveTo(bx+(bw-(right-left))/2-left+offset,by+g.baseline+offset);
        font.paint(queue,caption,color.red,color.green,color.blue,1f);
        if(selected) canvas.fill(UiColor.EDGE,1f,bx+5,by+BUTTON_HEIGHT-3,bw-10,2);
    }
    private void paint(Queue queue,String value,int px,int baseline) {
        paint(queue,value,px,baseline,Math.max(0,x+width-12-px));
    }
    private void paint(Queue queue,String value,int px,int baseline,int available) {
        if(text==null) return;
        int end=value.length();
        while(end>0&&text.getWidth(value.substring(0,end)+(end<value.length()?"…":""))>available)
            end=value.offsetByCodePoints(end,-1);
        value=end==0?"":value.substring(0,end)+(end<value.length()?"…":"");
        text.moveTo(px,baseline);text.paint(queue,value,.90f,.86f,.75f,1f);
    }
    @Override protected void leftPressed(int px,int py,int buttons) {
        mouseX=px;mouseY=py;slider=null;pressed=null;
        for(Hit hit:hits) if(hit.available&&hit.contains(px,py)) {
            if(hit.slider) {slider=hit;updateSlider(px);} else {pressed=hit;motion.get(hit.action).pointerPressed(System.nanoTime());}
            break;
        }
    }
    @Override protected void mouseDragged(int px,int py) {
        mouseX=px;mouseY=py;
        if(slider!=null) updateSlider(px);
        if(pressed!=null&&!pressed.contains(px,py)) pressed=null;
    }
    @Override void mouseMoved(int px,int py) {mouseX=px;mouseY=py;}
    @Override void mouseExited() {mouseX=-1;mouseY=-1;pressed=null;}
    @Override protected void leftReleased(int px,int py) {
        Hit hit=pressed;pressed=null;slider=null;
        if(hit!=null&&hit.contains(px,py)&&hit.available) {
            if(hit.action.equals("body")||hit.action.equals("lab")||hit.action.equals("info")) tab=hit.action;
            else controller.perform(hit.action.equals("save")&&tab.equals("lab")?"saveTestedBody":hit.action);
        }
    }
    private void updateSlider(int px) {
        float f=Math.max(0,Math.min(1,(px-slider.x)/(float)slider.width));
        controller.setValue(slider.action,slider.min+(slider.max-slider.min)*f);
    }
    private static final class Group {
        final int size,baseline;
        Group(int[] widths,String[] captions) {
            int size=128;for(int i=0;i<captions.length;i++)
                size=Math.min(size,UiButtonLayout.fit(new String[]{captions[i]},widths[i],BUTTON_HEIGHT,UiDensity.HIGH,SCALE,true).fontPixels);
            int top=0,bottom=0;
            for(String caption:captions) for(boolean bold:new boolean[]{false,true}) {
                Rectangle ink=UiTypography.ink(caption,size,bold,UiDensity.HIGH);top=Math.min(top,ink.y);bottom=Math.max(bottom,ink.y+ink.height);
            }
            this.size=size;baseline=(BUTTON_HEIGHT-(bottom-top))/2-top;
        }
    }
    private static final class Hit {
        final String action;final int x,y,width,height;final float min,max;final boolean slider;boolean available=true;
        Hit(String action,int x,int y,int width,int height) {this(action,x,y,width,height,0,0);}
        Hit(String action,int x,int y,int width,int height,float min,float max) {
            this.action=action;this.x=x;this.y=y;this.width=width;this.height=height;this.min=min;this.max=max;slider=max>min;
        }
        boolean contains(int px,int py) {return px>=x&&py>=y&&px<x+width&&py<y+height;}
    }
}
