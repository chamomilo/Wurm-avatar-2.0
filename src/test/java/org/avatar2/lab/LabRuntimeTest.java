package org.avatar2.lab;

import com.wurmonline.client.renderer.model.collada.animation.*;
import com.wurmonline.client.renderer.model.collada.importer.*;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;
import org.avatar2.client.AvatarAnimationBridge;
import org.avatar2.client.AvatarGeometryPrototype;
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
    @Test public void repeatedAbsentBodyRegistrationPreservesRevisionAndClearsOldTelemetry() throws Exception {
        try {
            AnimationLab.start();
            File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/male-bodybuilder.dae");
            ColladaModel model;
            try(InputStream input=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),input);}
            AvatarAnimationBridge.ensureRoot(model);
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AnimationLab.registerLocalModel(model);frame(model,new ForwardKinematics());
            assertNotNull(LabJson.object(AnimationLab.state().get("telemetry")).get("model"));
            AnimationLab.registerLocalModel(null);
            Map<String,Object> command=LabJson.object(AnimationLab.state().get("command"));
            assertNull(AnimationLab.state().get("schema"));
            assertNull(LabJson.object(AnimationLab.state().get("telemetry")).get("model"));
            for(int tick=0;tick<100;tick++)AnimationLab.registerLocalModel(null);
            assertEquals(command,LabJson.object(AnimationLab.state().get("command")));
        } finally {AnimationLab.stop();AnimationLab.registerLocalModel(null);}
    }
    private void frame(ColladaModel m,ForwardKinematics fk){
        AnimationLab.beginFrame(m);assertTrue(AnimationLab.forceFrame(m));
        fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,17,0);
        if(AnimationLab.beforeForwardKinematics(m))fk.updateBoneTransformations(m,m.getJoint("Root"),null,false,17,0);
        AnimationLab.afterForwardKinematics(m);AvatarGeometryPrototype.beforeSkins(m);AnimationLab.afterSkinning(m);AnimationLab.endFrame();
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
                for(int dirt:new int[]{13,20}){
                    AnimationLab.submit(LabJson.map("heightDirts",dirt));frame(m,fk);
                    Map<String,Object> height=LabJson.object(LabJson.object(AnimationLab.state().get("telemetry")).get("height"));
                    assertEquals(dirt/10d,((Number)height.get("heightMetres")).doubleValue(),.000002);
                    Map<String,Object> before=LabJson.object(AnimationLab.state().get("command"));
                    try{AnimationLab.submit(LabJson.map("heightDirts",dirt==13?12.999:20.001));fail();}
                    catch(IllegalArgumentException expected){}
                    assertEquals(before,LabJson.object(AnimationLab.state().get("command")));
                }
                AnimationLab.submit(LabJson.map("reset",true));frame(m,fk);
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
            assertEquals(200,request(url+"/",null,null));
            assertEquals(401,request(url+"/state",null,null));assertEquals(403,request(url+"/state",token,"https://example.org"));assertEquals(200,request(url+"/state",token,null));
            assertFalse(LabJson.write(AnimationLab.state()).contains(token));
            server.close();assertFalse(Files.exists(path));
        }finally{server.close();}
    }
    @Test public void geometryRestoresOnResetLabOffTimeoutAndModelChange() throws Exception {
        try {
            AnimationLab.start();
            File file=new File(System.getProperty("avatar2ProjectDir"),"assets/runtime/body/male-bodybuilder.dae");
            ColladaModel model;
            try(InputStream input=new FileInputStream(file)){model=ColladaImporter.loadModel(file.toURI().toString(),input);}
            AvatarAnimationBridge.ensureRoot(model);
            ColladaPropertiesXml.load(model,new FakeResourceUrl(file.toURI().toURL()),false);
            AnimationLab.registerLocalModel(model);ForwardKinematics fk=new ForwardKinematics();
            TriangleMesh mesh=null;
            for(ColladaGeometry geometry:model.getGeometryArray())for(TriangleMesh candidate:geometry.getTriangleMeshArray())
                if(candidate.getName().startsWith("Avatar2BodyMesh"))mesh=candidate;
            assertNotNull(mesh);float[] baseline=mesh.getOriginalVertexBuffer().clone();
            Map<String,Object> probe=LabJson.map("geometry",LabJson.map("torsoWidth",1,"torsoDepth",-.75));
            AnimationLab.submit(probe);frame(model,fk);assertFalse(Arrays.equals(baseline,mesh.getOriginalVertexBuffer()));
            AnimationLab.submit(LabJson.map("reset",true));frame(model,fk);assertArrayEquals(baseline,mesh.getOriginalVertexBuffer(),0);
            AnimationLab.submit(probe);frame(model,fk);AnimationLab.stop();frame(model,fk);
            assertArrayEquals("Lab off with game pose must force a geometry rollback frame",baseline,mesh.getOriginalVertexBuffer(),0);
            AnimationLab.start();AnimationLab.submit(probe);frame(model,fk);
            java.lang.reflect.Field contact=AnimationLab.class.getDeclaredField("lastContact");contact.setAccessible(true);
            contact.setLong(null,System.nanoTime()-31_000_000_000L);
            AnimationLab.contact(); // Reopening a controller clears the expired probe before renewing its lease.
            frame(model,fk);
            assertArrayEquals("Lease expiry restores geometry",baseline,mesh.getOriginalVertexBuffer(),0);
            AnimationLab.submit(probe);frame(model,fk);AnimationLab.registerLocalModel(null);
            AvatarGeometryPrototype.beforeSkins(model);
            assertArrayEquals("Replacing the local model clears the old experiment",baseline,mesh.getOriginalVertexBuffer(),0);
        }finally{AnimationLab.stop();AnimationLab.registerLocalModel(null);}
    }
    private int request(String url,String token,String origin) throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2000);c.setReadTimeout(2000);
        if(token!=null)c.setRequestProperty("Authorization","Bearer "+token);if(origin!=null)c.setRequestProperty("Origin",origin);
        try{return c.getResponseCode();}finally{c.disconnect();}
    }
}
