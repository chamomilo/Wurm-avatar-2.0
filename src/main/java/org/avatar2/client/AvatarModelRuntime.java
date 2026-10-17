package org.avatar2.client;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PlayerBodyRenderable;
import com.wurmonline.client.renderer.cell.CellRenderable;
import com.wurmonline.client.renderer.model.AbstractModelData;
import com.wurmonline.client.renderer.model.ModelResourceWrapper;
import com.wurmonline.client.renderer.model.collada.ColladaModelData;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import org.avatar2.core.AvatarProfile;

import java.util.logging.Logger;

/** Reconciles the editor profile with the local player's live body. */
final class AvatarModelRuntime {
    private static final Logger LOG = Logger.getLogger("Avatar2");
    private static PlayerBodyRenderable appliedBody;
    private static String appliedModel;
    private static AbstractModelData preparedModelData;
    private static AbstractModelData preparedPaperModelData;

    private AvatarModelRuntime() { }

    static synchronized void reconcile(PlayerBodyRenderable body) {
        if (body == null) return;
        World world = CellRenderable.getWorld();
        if (world == null || world.getPlayer() == null
                || world.getPlayer().getPlayerBody() != body) return;
        AvatarProfile profile = AvatarRuntimeState.profile();
        String model = modelFor(profile);
        int revision = AvatarRuntimeState.revision();
        // Appearance is read by the animation worker. Slider revisions must not
        // reload the same body, discard its private rig or restart Lab commands.
        if (body == appliedBody && model.equals(appliedModel)) {
            preparePlayerBaking(body, model);
            return;
        }
        if (profile.enabled && !AvatarResources.ensureLoaded()) return;
        body.setBodyModel(model);
        appliedBody = body;
        appliedModel = model;
        preparedModelData = null;
        preparedPaperModelData = null;
        preparePlayerBaking(body, model);
        LOG.info("Applied live body model " + model + " at revision " + revision);
    }

    /**
     * Avatar 2.0 v0.1 has native-rig body and eye meshes, not Wurm's collection
     * of separately named paper-doll pieces. Keep the normal player baking path;
     * ClientHooks substitutes these meshes and their own textures at the two
     * bake call sites.
     */
    private static void preparePlayerBaking(PlayerBodyRenderable body,
                                            String modelName) {
        if (!modelName.startsWith("model.avatar2.body.")) {
            org.avatar2.lab.AnimationLab.registerLocalModel(null);
            return;
        }
        ModelResourceWrapper wrapper = body.getModelWrapper();
        if (wrapper == null || !wrapper.isLoaded() || !wrapper.isCollada()) return;
        String path = wrapper.getResourceUrl().getFilePath().replace('\\', '/');
        if (!path.startsWith("avatar2/body/")) return;
        AbstractModelData data = wrapper.getModelData();
        if (data instanceof ColladaModelData) {
            ColladaModel liveModel = ((ColladaModelData)data).getModel();
            org.avatar2.lab.AnimationLab.registerLocalModel(liveModel);
            AvatarShieldWalk.reconcile(body, liveModel);
        }
        ModelResourceWrapper paper = body.getPaperDollModel();
        org.avatar2.lab.AnimationLab.registerPaperModel(paper != null && paper.isLoaded()
                && paper.isCollada() && paper.getModelData() instanceof ColladaModelData
                && paper.getResourceUrl().getFilePath().replace('\\', '/').startsWith("avatar2/body/")
                ? ((ColladaModelData)paper.getModelData()).getModel() : null);
        // The loader's independent paper-doll copy can complete after the world
        // body. Bake it once when ready, and retry while its textures are pending.
        if(paper!=null && paper.isLoaded() && paper.getModelData()!=preparedPaperModelData
                && Avatar2Mod.prepareAvatarPaperDoll(paper))preparedPaperModelData=paper.getModelData();
        if (data == null || data == preparedModelData) return;
        if (!(data instanceof ColladaModelData)) {
            LOG.warning("Avatar body resource is not COLLADA; direct rendering was not enabled");
            return;
        }
        ColladaModelData avatarData = (ColladaModelData) data;
        ColladaModel collada = avatarData.getModel();
        if (collada == null) return;
        collada.setCantBeBaked(false);
        preparedModelData = data;
        LOG.info("Enabled vanilla player-mesh baking for the Avatar 2.0 body");
    }

    static String modelFor(AvatarProfile profile) {
        String sex = profile.sex == AvatarProfile.Sex.FEMALE ? "female" : "male";
        if (!profile.enabled) {
            return "model.creature.humanoid.human.player." + sex;
        }
        // Shapes use one fixed binding per sex. Legacy preset/parameter values
        // must never silently select the retired Athletic topology.
        return "model.avatar2.body." + sex + ".bodybuilder";
    }
}
