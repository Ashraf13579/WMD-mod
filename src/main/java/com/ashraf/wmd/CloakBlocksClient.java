package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class CloakBlocksClient {

    private CloakBlocksClient() {}

    static final float[][] CLOAK_HOUSING = {
            { -8F, 0F, 6F, 8F, 1.6F, 8F }, { -8F, 1.6F, 6F, -6.4F, 14.4F, 8F }, { 6.4F, 1.6F, 6F, 8F, 14.4F, 8F }, { -8F, 14.4F, 6F, 8F, 21.6F, 8F },
            { -8F, 21.6F, 6F, -1.4F, 26.4F, 8F }, { 1.4F, 21.6F, 6F, 8F, 26.4F, 8F }, { -8F, 26.4F, 6F, 8F, 31F, 8F },
            { -8F, 0F, -8F, 8F, 31F, -6.8F }, { -8F, 0F, -6.8F, -6.8F, 31F, 6F }, { 6.8F, 0F, -6.8F, 8F, 31F, 6F },
            { -8F, 31F, -8F, 8F, 32F, -5.5F }, { -8F, 31F, 5.5F, 8F, 32F, 8F }, { -8F, 31F, -5.5F, -5.5F, 32F, 5.5F }, { 5.5F, 31F, -5.5F, 8F, 32F, 5.5F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, CloakBlocks.CLOAK_BE.get(), context -> new CloakRenderer());

        MountedRenderer.register(event, CloakBlocks.QRADAR_BE.get(), context -> new StagedUnitRenderer<QuantumRadarBlockEntity>("quantum_radar", 9, 5));
    }

    static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {}

    static final class CloakRenderer extends StagedUnitRenderer<ActiveCloakBlockEntity> {

        private static final Set<String> ATTACH = Set.of("housing", "housing_pe", "housing_pw");
        private BlockState skin;

        CloakRenderer() { super("active_cloak", 4, -1); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(ActiveCloakBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(ActiveCloakBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            if (poses.containsKey("housing")) AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get("housing"), normals.get("housing"), skin, be, CLOAK_HOUSING, light, overlay);
            for (String b : new String[] { "housing_pe", "housing_pw" }) {
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named("active_cloak"), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
            }
        }
    }
}
