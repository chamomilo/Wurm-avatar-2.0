package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import org.avatar2.client.AvatarAnimationBridge;
import com.wurmonline.client.renderer.model.collada.ColladaPropertiesXml;
import com.wurmonline.client.resources.FakeResourceUrl;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class LabRuntimeTest {
    private void frame(ColladaModel m,ForwardKinematics fk){
        AnimationLab.beginFrame(m);assertTrue(AnimationLab.forceFrame(m));
        fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,17,0);
        if(AnimationLab.beforeForwardKinematics(m))fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,17,0);
        AnimationLab.afterForwardKinematics(m);AnimationLab.afterSkinning(m);AnimationLab.endFrame();
    }
    @Test public void actualImportedBodiesDoNotAccumulateCorrectionsAndResetRestoresEngineState() throws Exception {
        try{
            AnimationLab.start();
            for(String name:new String[]{"male-athletic","male-bodybuilder","female-athletic","female-bodybuilder"}){
                File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/"+name+".dae");ColladaModel m;
                try(InputStream in=new FileInputStream(file)){m=ColladaImporter.loadModel(file.toURI().toString(),in);}AvatarAnimationBridge.ensureRoot(m);
                ColladaPropertiesXml.load(m,new FakeResourceUrl(file.toURI().toURL()),false);
                AnimationLab.registerLocalModel(m);ForwardKinematics fk=new ForwardKinematics();
                frame(m,fk);Map<String,Object> first=AnimationLab.state();
                Map<String,Object> bones=LabJson.object(LabJson.object(first.get("telemetry")).get("bones"));
                float[] baseline=(float[])LabJson.object(bones.get("head")).get("engineBind");
                Matrix4f oldAnimation=new Matrix4f(m.getJoint("spine_01").getAnimationMatrix());
                AnimationLab.submit(LabJson.object(LabJson.parse("{\"corrections\":{\"spine_01\":{\"space\":\"local\",\"rotation\":[3,0,0]}}}")));
                frame(m,fk);float[] changed=LabPose.rows(m.getJoint("head").getBindMatrix());
                assertFalse(Arrays.equals(baseline,changed));assertArrayEquals(LabPose.rows(oldAnimation),LabPose.rows(m.getJoint("spine_01").getAnimationMatrix()),0);
                for(int n=0;n<8;n++){frame(m,fk);assertArrayEquals(name,changed,LabPose.rows(m.getJoint("head").getBindMatrix()),0.00002f);}
                AnimationLab.submit(LabJson.map("reset",true));frame(m,fk);
                assertArrayEquals(name,baseline,LabPose.rows(m.getJoint("head").getBindMatrix()),0.00002f);
                // Exercise actual registered clips and independent preview caches.
                for(String clip:new String[]{"idle","walk","run"}){
                    for(double phase:new double[]{0,.25,.5,.75}){
                        AnimationLab.submit(LabJson.map("preview",LabJson.map("clip",clip,"paused",true,"phase",phase)));
                        frame(m,fk);float[] preview=LabPose.rows(m.getJoint("head").getBindMatrix());
                        frame(m,fk);assertArrayEquals(clip,preview,LabPose.rows(m.getJoint("head").getBindMatrix()),0.00002f);
                    }
                }
                AnimationLab.submit(LabJson.object(LabJson.parse("{\"preview\":{\"clip\":\"bind\"},\"corrections\":{\"clavicle_l\":{\"space\":\"model\",\"rotation\":[0,2,0]}}}")));frame(m,fk);
                AnimationLab.stop();frame(m,fk); // final rollback frame still forces skinning
                assertArrayEquals(name,baseline,LabPose.rows(m.getJoint("head").getBindMatrix()),0.00002f);
                AnimationLab.start();
            }
        }finally{AnimationLab.stop();AnimationLab.registerLocalModel(null);}
    }
    @Test public void serverRequiresTokenRejectsForeignOriginAndHasNoTokenInState() throws Exception {
        LabServer server=new LabServer();try{
            server.start();String address=server.address();int start=address.indexOf("session: ")+9;
            Path path=Paths.get(address.substring(start,address.length()-1));Map<String,Object> session=LabJson.object(LabJson.parse(new String(Files.readAllBytes(path),StandardCharsets.UTF_8)));
            String url=(String)session.get("url"),token=(String)session.get("token");
            assertEquals(401,request(url+"/state",null,null));assertEquals(403,request(url+"/state",token,"https://example.org"));assertEquals(200,request(url+"/state",token,null));
            assertFalse(LabJson.write(AnimationLab.state()).contains(token));
            server.close();assertFalse(Files.exists(path));
        }finally{server.close();}
    }
    private int request(String url,String token,String origin) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(2000);
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(origin!=null)c.setRequestProperty("Origin",origin);
        try{return c.getResponseCode();}finally{c.disconnect();}
    }
}
