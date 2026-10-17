package org.avatar2.client;

import com.wurmonline.client.renderer.gui.AvatarStudioWindow;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.ColladaGeometry;
import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import com.wurmonline.client.resources.textures.ResourceTexture;
import com.wurmonline.client.resources.textures.Texture;
import org.avatar2.core.AvatarProfileRepository;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;
import org.gotti.wurmunlimited.modloader.interfaces.ModListener;
import org.gotti.wurmunlimited.modloader.interfaces.ModEntry;
import org.chamomilo.wurm.update.SharedUpdateCoordinator;
import org.chamomilo.wurm.update.SharedUpdateHooks;
import org.gotti.wurmunlimited.modsupport.console.ConsoleListener;
import org.gotti.wurmunlimited.modsupport.console.ModConsole;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Avatar2Mod implements WurmClientMod, Configurable,
        PreInitable, Initable, ConsoleListener, ModListener, org.chamomilo.wurm.update.SharedLanguageCoordinator.Participant {
    public static final String VERSION = "0.15.2";
    private static volatile String studioLanguage = "en";
    public String[] supportedLanguageCodes() { return new String[]{"en","pt-BR","de","ru"}; }
    public void setUserLanguage(String code) {
        if(java.util.Arrays.asList(supportedLanguageCodes()).contains(code))studioLanguage=code;
    }
    public static String studioLanguage() { return studioLanguage; }
    private static final Logger LOG = Logger.getLogger("Avatar2");
    private static final Queue<Runnable> DEFERRED_UI =
            new ConcurrentLinkedQueue<Runnable>();
    private static volatile Avatar2Mod instance;

    private HeadsUpDisplay hud;
    private AvatarStudioController controller;
    private AvatarStudioWindow window;
    private HudBridge bridge;

    public Avatar2Mod() { instance = this; }

    @Override public String getVersion() { return VERSION; }

    @Override
    public void configure(Properties properties) {
        AvatarStudioSettings.configure(org.chamomilo.wurm.update.SharedModSettings.configure(
                "avatar2", properties, AvatarStudioSettings.specs(), (key, values) -> {
                    if (key.equals("panelVisible")) setStudioVisible(Boolean.parseBoolean(values.getProperty(key)));
                    AvatarStudioSettings.configure(values);
                }));
    }

    private void setStudioVisible(boolean visible) {
        if (hud == null || window == null || bridge == null) return;
        try { bridge.setVisible(hud, window, visible); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Could not change Avatar Studio visibility", error); }
    }

    @Override
    public void preInit() {
        try {
            SharedUpdateHooks.install();
            ClientHooks.install();
            LOG.info("Avatar 2.0 client hooks installed");
        } catch (Exception error) {
            throw new RuntimeException("Could not install Avatar 2.0 hooks", error);
        }
    }

    @Override
    public void init() {
        ModConsole.addConsoleListener(this);
        SharedUpdateHooks.registerHost("avatar2");
    }

    @Override public void modInitialized(ModEntry<?> entry) {
        SharedUpdateCoordinator.modInitialized(entry);
    }

    public static void afterHudInitialized(HeadsUpDisplay hud) {
        Avatar2Mod mod = instance;
        if (mod != null) {
            mod.attach(hud);
        }
    }

    private synchronized void attach(HeadsUpDisplay next) {
        if (next == null || hud == next && window != null) return;
        try {
            if (bridge == null) bridge = new HudBridge();
            if (hud != null && window != null) bridge.remove(hud, window);
            hud = next;
            controller = new AvatarStudioController(hud,
                    new AvatarProfileRepository(AvatarStudioSettings.profileFile()));
            controller.initialize();
            window = new AvatarStudioWindow(controller);
            window.setPosition(Math.max(16, hud.getWidth() - 704), 80);
            bridge.register(hud, window, AvatarStudioSettings.panelVisible());
            LOG.info("Avatar Studio registered in HUD settings");
        } catch (Throwable error) {
            LOG.log(Level.SEVERE, "Could not register Avatar Studio", error);
        }
    }

    public static void deferUi(Runnable operation) {
        if (operation != null) DEFERRED_UI.offer(operation);
    }

    public static void drainDeferredUi() {
        for (int count = 0; count < 64; count++) {
            Runnable operation = DEFERRED_UI.poll();
            if (operation == null) return;
            try { operation.run(); }
            catch (Throwable error) { LOG.log(Level.WARNING, "Avatar UI task failed", error); }
        }
    }

    public static void beforePlayerBodyTick(PlayerBodyRenderable body) {
        AvatarModelRuntime.reconcile(body);
    }

    /** True only after the local body has resolved to an Avatar resource. */
    public static boolean usesAvatar(PlayerBodyRenderable body) {
        return body != null && isAvatarWrapper(body.getModelWrapper());
    }

    /**
     * Builds the continuous Avatar body through Wurm's baked-mesh renderer.
     * The vanilla method assumes segmented body and face meshes and dereferences
     * a missing face on our one-piece topology, so both world and paper-doll
     * models are handled atomically here.
     */
    public static boolean rebuildAvatarMeshes(PlayerBodyRenderable body) {
        if (body == null || !isAvatarWrapper(body.getModelWrapper())) return false;
        try {
            boolean world = bakeAvatarWrapper(body.getModelWrapper(), "world");
            ModelResourceWrapper paperDoll = body.getPaperDollModel();
            if (isAvatarWrapper(paperDoll)) org.avatar2.lab.AnimationLab.registerPaperModel(
                    ((ColladaModelData)paperDoll.getModelData()).getModel());
            boolean paper = paperDoll == null || !isAvatarWrapper(paperDoll)
                    || bakeAvatarWrapper(paperDoll, "paper doll");
            if (!world || !paper) {
                LOG.severe("Avatar 2.0 baked-mesh rebuild was incomplete");
            }
        } catch (Throwable error) {
            LOG.log(Level.SEVERE, "Could not rebuild Avatar 2.0 player meshes", error);
        }
        // Never fall through to the segmented vanilla face baker for this model.
        return true;
    }

    static boolean prepareAvatarPaperDoll(ModelResourceWrapper wrapper) {
        if(!isAvatarWrapper(wrapper))return false;
        ColladaModelData data=(ColladaModelData)wrapper.getModelData();
        // An asynchronous copy can be loaded before both diffuse textures arrive.
        // Wait without clearing its existing baked output or logging every tick.
        for(TriangleMesh mesh:avatarSourceMeshes(data))if(mesh.getColladaMaterial()==null
                || data.getLoadedTextures().get(mesh.getColladaMaterial())==null&&mesh.getOverridedTexture()==null)return false;
        return bakeAvatarWrapper(wrapper,"paper doll");
    }

    private static boolean bakeAvatarWrapper(ModelResourceWrapper wrapper,
                                             String role) {
        if (wrapper == null || !wrapper.isLoaded() || !wrapper.isCollada()
                || !(wrapper.getModelData() instanceof ColladaModelData)) return false;
        ColladaModelData data = (ColladaModelData) wrapper.getModelData();
        List<TriangleMesh> sources = avatarSourceMeshes(data);
        if (sources.isEmpty()) return false;
        data.setSpecificMeshesToDraw(null);
        data.destroyBakedMeshes();
        for (TriangleMesh source : sources) {
            Texture texture = avatarTexture(data, source);
            if (texture == null) return false;
            TriangleMesh baked = data.bakeDefaultMesh(source);
            if (baked == null) {
                LOG.severe("Avatar 2.0 baker returned no " + role
                        + " mesh for " + source.getName());
                return false;
            }
            data.addBakedMeshToList(baked, texture);
            baked.calculateSkin();
        }
        data.setShouldBeRendered(true);
        LOG.info("Built Avatar 2.0 " + role + " from " + sources.size()
                + " skinned meshes");
        return true;
    }

    /** Preserves each Avatar mesh's UV texture instead of Wurm's body atlas. */
    private static Texture avatarTexture(ColladaModelData data,
                                         TriangleMesh mesh) {
        if (mesh == null || mesh.getColladaMaterial() == null) return null;
        ResourceTexture texture = data.getLoadedTextures().get(mesh.getColladaMaterial());
        if (texture == null) texture = mesh.getOverridedTexture();
        if (texture == null) {
            LOG.severe("Avatar 2.0 skin texture was not loaded");
            return null;
        }
        return texture;
    }

    private static boolean isAvatarWrapper(ModelResourceWrapper wrapper) {
        return wrapper != null && wrapper.isLoaded() && wrapper.isCollada()
                && wrapper.getModelData() instanceof ColladaModelData
                && isAvatarData((ColladaModelData) wrapper.getModelData());
    }

    private static boolean isAvatarData(ColladaModelData data) {
        if (data == null || data.getUrl() == null) return false;
        return data.getUrl().getFilePath().replace('\\', '/')
                .startsWith("avatar2/body/");
    }

    private static List<TriangleMesh> avatarSourceMeshes(ColladaModelData data) {
        List<TriangleMesh> result = new ArrayList<TriangleMesh>();
        if (data == null || data.getModel() == null
                || data.getModel().getGeometryArray() == null) return result;
        for (ColladaGeometry geometry : data.getModel().getGeometryArray()) {
            if (geometry == null || geometry.getTriangleMeshArray() == null) continue;
            for (TriangleMesh mesh : geometry.getTriangleMeshArray()) {
                if (mesh != null && mesh.getName() != null
                        && (mesh.getName().startsWith("Avatar2BodyMesh")
                        || mesh.getName().startsWith("Avatar2EyesMesh"))) {
                    result.add(mesh);
                }
            }
        }
        return result;
    }

    public static void hideStudioWindow() {
        deferUi(new Runnable() {
            @Override public void run() {
                Avatar2Mod mod = instance;
                if (mod == null || mod.bridge == null) return;
                try { mod.bridge.hide(mod.hud, mod.window); }
                catch (ReflectiveOperationException error) {
                    LOG.log(Level.WARNING, "Could not hide Avatar Studio", error);
                }
            }
        });
    }

    private synchronized void toggle() {
        try {
            if (bridge != null) bridge.toggle(hud, window);
        } catch (ReflectiveOperationException error) {
            LOG.log(Level.WARNING, "Could not toggle Avatar Studio", error);
        }
    }

    @Override
    public boolean handleInput(String input, Boolean silent) {
        if (input == null) return false;
        String command = input.trim();
        if (command.startsWith("/")) command = command.substring(1);
        String lower = command.toLowerCase(Locale.ROOT);
        if (!lower.equals("avatar2") && !lower.startsWith("avatar2 ")) return false;
        if (lower.equals("avatar2")) {
            deferUi(new Runnable() { @Override public void run() { toggle(); } });
        } else if (controller != null && lower.equals("avatar2 save")) {
            controller.perform("save");
            output(controller.status());
        } else if (controller != null && lower.equals("avatar2 reset")) {
            controller.perform("reset");
            output(controller.status());
        } else if (lower.startsWith("avatar2 lab ")) {
            final String action=lower.substring("avatar2 lab ".length());
            deferUi(new Runnable(){@Override public void run(){
                try {
                    if(action.equals("on")) output(org.avatar2.lab.AnimationLab.start());
                    else if(action.equals("off")){org.avatar2.lab.AnimationLab.stop();output("Animation Lab off; original motion restored on next visible animation frame");}
                    else if(action.equals("open"))org.avatar2.lab.AnimationLab.open();
                    else if(action.equals("status"))output(org.avatar2.lab.AnimationLab.status());
                    else output("/avatar2 lab on | open | off | status");
                }catch(Exception error){output("Animation Lab: "+error.getMessage());}
            }});
        } else {
            output("Usage: /avatar2 | /avatar2 save | /avatar2 reset | /avatar2 lab on|open|off|status");
        }
        return true;
    }

    private void output(String message) {
        try {
            if (hud != null) hud.consoleOutput("[Avatar2] " + message);
            else LOG.info(message);
        } catch (Throwable unavailable) { LOG.info(message); }
    }
}
