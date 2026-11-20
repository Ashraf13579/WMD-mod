package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ExoSuitBlocksClient {

    private ExoSuitBlocksClient() {}

    @net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
    static final class Screens {
        @net.neoforged.bus.api.SubscribeEvent
        static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) { event.register(ExoSuitBlocks.SUIT_RACK_MENU.get(), SuitRackScreen::new); }
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, ExoSuitBlocks.SUIT_RACK_BE.get(), context -> new SuitRackRenderer());
    }

    static final class SuitRackRenderer extends StagedUnitRenderer<SuitRackBlockEntity> {

        private static final Set<String> ATTACH = Set.of("rack");
        private static final float PX = 1.0F / 16.0F, SUIT_Y = 1.2F, SUIT_Z = -3.0F;
        private static final String[][] BONES = {
                { "helmet" }, { "torso", "arm_l", "arm_r", "shoulder_l", "shoulder_r", "jet_l", "jet_r" }, { "hips", "leg_l", "leg_r", "shin_l", "shin_r" }, { "boot_l", "boot_r" } };
        private static final String[][] LIGHTS = {
                { "visor", "lt_helmet" }, { "lt_torso", "coreglow", "ltsh_l", "ltsh_r", "ltarm_l", "ltarm_r" }, {}, { "ltboot_l", "ltboot_r" } };

        private static final java.util.Map<ExoModule, String[]> MOD_BONES = java.util.Map.of(
                ExoModule.THERMAL, new String[] { "m_thermal" }, ExoModule.REBREATHER, new String[] { "m_rebreather" },
                ExoModule.CANNON, new String[] { "m_cannon", "m_barrel" }, ExoModule.MISSILES, new String[] { "m_pod", "m_podlid" },
                ExoModule.WINGS, new String[] { "m_wingroot", "m_wing_l", "m_wing_r", "m_wpod_l", "m_wpod_r" }, ExoModule.CLOAK, new String[] { "m_cloak" },
                ExoModule.RF, new String[] { "m_rf", "m_rfmast" }, ExoModule.BARRIER, new String[] { "m_barrier_l", "m_barrier_r", "m_barrier_h" },
                ExoModule.MAGGRIP, new String[] { "m_mag_l", "m_mag_r" });

        private static final float[] HOLSTER = { 9.0F, 15.0F, 1.0F };

        SuitRackRenderer() { super("suit_rack", 2); }

        @Override protected Set<String> attachBones() { return ATTACH; }

        @Override
        protected void drawAttached(SuitRackBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            Matrix4f m4 = poses.get("rack");
            if (m4 == null) return;
            if (!be.tool().isEmpty()) {
                PoseStack tp = new PoseStack();
                tp.last().pose().set(m4);
                tp.last().normal().set(normals.get("rack"));
                tp.translate(HOLSTER[0] * PX, HOLSTER[1] * PX, HOLSTER[2] * PX);
                tp.mulPose(Axis.XP.rotationDegrees(-90.0F));
                HandToolItemRenderer.drawRest(be.tool(), tp, buffers, light, OverlayTexture.NO_OVERLAY);
            }
            if (!be.holdsSuit()) return;
            MeshModel m = MeshModel.named(ClientExoSuitLayer.MESH);
            if (!m.has("torso")) return;
            PoseStack ps = new PoseStack();
            ps.last().pose().set(m4);
            ps.last().normal().set(normals.get("rack"));
            ps.translate(0.0F, SUIT_Y * PX, SUIT_Z * PX);
            ItemStack chest = be.piece(1);
            float charge = ExoSuitItem.fe(chest) / (float) Balance.EXO_FE_CAP;
            int b = chest.isEmpty() || charge <= 0.0F ? 0 : charge < Balance.EXO_LOW ? 90 : 255;
            int ov = OverlayTexture.NO_OVERLAY;
            for (int i = 0; i < 4; i++) {
                if (be.piece(i).isEmpty()) continue;
                for (String bone : BONES[i]) m.draw(bone, ps.last(), buffers, light, ov);
                if (b > 0) for (String bone : LIGHTS[i]) m.drawColoured(bone, ps.last(), buffers, light, ov, b, b, b);
            }
            for (int i = 0; i < 4; i++) {
                ItemStack pc = be.piece(i);
                if (pc.isEmpty()) continue;
                for (ExoModule mod : ExoModule.ALL) {
                    if (mod.piece() != i || !mod.on(pc)) continue;
                    for (String bone : MOD_BONES.get(mod)) m.draw(bone, ps.last(), buffers, light, ov);
                }
            }
            if (!chest.isEmpty()) {
                for (int k = 0; k < 4; k++) if (charge > k * 0.25F + (k == 0 ? 0.0F : 0.001F)) m.drawColoured("gauge" + k, ps.last(), buffers, light, ov, 255, 255, 255);
                float spin = be.charging() && be.getLevel() != null ? (be.getLevel().getGameTime() % 360L) * Balance.EXO_CORE_LOW : 0.0F;
                ps.pushPose();
                ps.translate(0.0F, 18.8F * PX, 7.1F * PX);
                ps.mulPose(Axis.ZP.rotationDegrees(ClientExoSuitLayer.CORE_SIGN * Mth.wrapDegrees(spin)));
                ps.translate(0.0F, -18.8F * PX, -7.1F * PX);
                m.draw("core", ps.last(), buffers, light, ov);
                ps.popPose();
            }
        }
    }
}
