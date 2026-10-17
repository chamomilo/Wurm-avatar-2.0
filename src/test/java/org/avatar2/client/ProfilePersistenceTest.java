package org.avatar2.client;

import org.avatar2.core.*;
import org.junit.Test;
import java.nio.file.*;
import java.util.Properties;
import static org.junit.Assert.*;

public class ProfilePersistenceTest {
    @Test public void bodyEditsPreviewImmediatelyAndSaveKeepsEverySliderBit() throws Exception {
        Path dir=Files.createTempDirectory("avatar2-live-profile-");
        Path file=dir.resolve("profile.properties");
        try {
            AvatarProfileRepository repo=new AvatarProfileRepository(file.toFile());
            repo.save(new AvatarProfile());byte[] original=Files.readAllBytes(file);
            AvatarStudioController c=new AvatarStudioController(null,repo);c.initialize();
            c.perform("bodyShape:mpfb-athletic");
            assertEquals("mpfb-athletic",AvatarRuntimeState.profile().shape);
            c.setValue("shapeMix",.37123457f);c.setValue("heightDirts",14.654321f);
            c.perform("bodySkin:middle");
            AvatarProfile preview=AvatarRuntimeState.profile();
            assertEquals(.37123457f,preview.shapeMix,0);
            assertEquals(14.654321f/10,preview.heightMetres,0);
            assertEquals("middle",preview.ageSkin);
            assertTrue(c.dirty());assertArrayEquals(original,Files.readAllBytes(file));
            c.perform("save");
            assertEquals(preview.shapeMix,repo.load().shapeMix,0);
            assertEquals(preview.heightMetres,repo.load().heightMetres,0);
            assertEquals(preview.toProperties(),AvatarRuntimeState.profile().toProperties());
            c.perform("nativeHeight");assertEquals(0,AvatarRuntimeState.profile().heightMetres,0);
            c.perform("reset");appearance(AvatarRuntimeState.profile(),"none",0,0,"original");
            c.perform("revert");assertEquals(preview.toProperties(),AvatarRuntimeState.profile().toProperties());
            assertFalse(c.dirty());
        } finally {
            AvatarRuntimeState.apply(new AvatarProfile());
            Files.deleteIfExists(dir.resolve("profile.properties.bak"));
            Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
    }

    @Test public void failedSaveKeepsTheLiveDraftAndLastSavedBody() throws Exception {
        Path dir=Files.createTempDirectory("avatar2-save-failure-");
        Path blocker=dir.resolve("blocker");Files.write(blocker,new byte[]{1});
        try {
            AvatarStudioController c=new AvatarStudioController(null,new AvatarProfileRepository(blocker.resolve("profile.properties").toFile()));
            c.initialize();c.perform("bodyShape:mpfb-older");c.setValue("shapeMix",.63f);
            AvatarProfile preview=AvatarRuntimeState.profile();c.perform("save");
            assertTrue(c.dirty());assertTrue(c.status().startsWith("Save failed"));
            assertEquals(preview.toProperties(),AvatarRuntimeState.profile().toProperties());
            c.perform("revert");appearance(AvatarRuntimeState.profile(),"none",0,0,"original");
        } finally {
            AvatarRuntimeState.apply(new AvatarProfile());
            Files.deleteIfExists(blocker);Files.deleteIfExists(dir);
        }
    }

    @Test public void savedAppearanceSurvivesReloadApplyAndRevertForBothSexes() throws Exception {
        Path dir=Files.createTempDirectory("avatar2-profile-v2-");
        Path file=dir.resolve("profile.properties");
        try {
            AvatarProfileRepository repo=new AvatarProfileRepository(file.toFile());
            for(AvatarProfile.Sex sex:AvatarProfile.Sex.values()) {
                AvatarStudioController c=new AvatarStudioController(null,repo);c.initialize();
                c.profile().sex=sex;
                c.perform("bodyShape:mpfb-older");c.setValue("shapeMix",.375f);
                c.setValue("heightDirts",13);c.perform("bodySkin:old");
                c.setValue("headSize",.73123455f);
                c.perform("save");assertFalse(c.dirty());
                Properties values=new Properties();try(java.io.InputStream in=Files.newInputStream(file)){values.load(in);}
                assertEquals("8",values.getProperty("schema"));
                AvatarStudioController restart=new AvatarStudioController(null,repo);restart.initialize();
                assertEquals(sex,restart.profile().sex);
                assertEquals(.73123455f,restart.profile().headSize,0);
                appearance(restart.profile(),"mpfb-older",.375f,1.3f,"old");
                appearance(AvatarRuntimeState.profile(),"mpfb-older",.375f,1.3f,"old");
                byte[] saved=Files.readAllBytes(file);
                restart.perform("bodyShape:mpfb-heavy");restart.setValue("heightDirts",20);
                restart.perform("bodySkin:middle");restart.perform("apply");
                restart.setValue("headSize",0);
                assertTrue(restart.dirty());assertArrayEquals(saved,Files.readAllBytes(file));
                appearance(AvatarRuntimeState.profile(),"mpfb-heavy",.375f,2,"middle");
                restart.perform("revert");assertFalse(restart.dirty());
                assertEquals(.73123455f,restart.profile().headSize,0);
                appearance(AvatarRuntimeState.profile(),"mpfb-older",.375f,1.3f,"old");
                restart.perform("reset");restart.perform("save");
                appearance(repo.load(),"none",0,0,"original");
            }
        } finally {
            AvatarRuntimeState.apply(new AvatarProfile());
            Files.deleteIfExists(dir.resolve("profile.properties.bak"));
            Files.deleteIfExists(file);Files.deleteIfExists(dir);
        }
    }
    @Test public void legacyMigrationPreservesNativeAppearanceAndNewInvalidValuesAreBounded() {
        Properties old=new Properties();old.setProperty("schema","1");old.setProperty("sex","FEMALE");
        old.setProperty("preset","ATHLETIC");old.setProperty("stature","1.04");
        old.setProperty("shape","mpfb-heavy");old.setProperty("heightMetres","2");
        AvatarProfile p=AvatarProfile.fromProperties(old);
        appearance(p,"none",0,0,"original");assertEquals(1.04f,p.stature,0);
        assertEquals(AvatarProfile.Preset.ATHLETIC,p.preset);
        old.setProperty("schema","2");old.setProperty("shape","bad");old.setProperty("shapeMix","NaN");
        old.setProperty("heightMetres","Infinity");old.setProperty("ageSkin","bad");
        appearance(AvatarProfile.fromProperties(old),"none",0,0,"original");
        old.setProperty("shape","mpfb-heavy");old.setProperty("shapeMix","5");old.setProperty("heightMetres","-7");
        appearance(AvatarProfile.fromProperties(old),"mpfb-heavy",1,1.3f,"original");
        old.setProperty("heightMetres","9");assertEquals(2,AvatarProfile.fromProperties(old).heightMetres,0);
        p=AvatarProfile.fromProperties(old);p.shapeMix=0;
        assertEquals("mpfb-heavy",AvatarProfile.fromProperties(p.toProperties()).shape);
    }
    private static void appearance(AvatarProfile p,String shape,float mix,float height,String skin) {
        assertEquals(shape,p.shape);assertEquals(mix,p.shapeMix,0);
        assertEquals(height,p.heightMetres,.00001f);assertEquals(skin,p.ageSkin);
    }
}
