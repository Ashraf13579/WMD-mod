package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class IonBlocksClient {

    private IonBlocksClient() {}

    static final float[][] SHIELD_HOUSING = {
            { -24F, 0F, -24F, 24F, 2F, 24F },
            { -24F, 2F, -21F, -21F, 29F, 21F }, { 21F, 2F, -21F, 24F, 29F, 21F },
            { -24F, 2F, -24F, -7F, 29F, -21F }, { 7F, 2F, -24F, 24F, 29F, -21F }, { -7F, 2F, -24F, 7F, 6F, -21F }, { -7F, 26F, -24F, 7F, 29F, -21F },
            { -24F, 2F, 21F, -6.6F, 29F, 24F }, { 6.6F, 2F, 21F, 24F, 29F, 24F }, { -6.6F, 14F, 21F, 6.6F, 29F, 24F },
            { -24F, 29F, -24F, 24F, 32F, -22.4F }, { -24F, 29F, 22.4F, 24F, 32F, 24F }, { -24F, 29F, -22.4F, -22.4F, 32F, 22.4F }, { 22.4F, 29F, -22.4F, 24F, 32F, 22.4F } };

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {

        MountedRenderer.register(event, IonBlocks.ION_BE.get(), context -> new AirEngineRenderer("ion_thruster", 2, 3));

        MountedRenderer.register(event, IonBlocks.SHIELD_BE.get(), context -> new ShieldRenderer());
    }

    static final class ShieldRenderer extends StagedUnitRenderer<CraftShieldBlockEntity> {

        private static final Set<String> ATTACH = Set.of("housing");
        private BlockState skin;

        ShieldRenderer() { super("craft_shield", 4, 2); }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && ATTACH.contains(bone); }

        @Override
        public void render(CraftShieldBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(CraftShieldBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin != null && poses.containsKey("housing"))
                AirEngineRenderer.CopycatSkin.drawBoxes(buffers, poses.get("housing"), normals.get("housing"), skin, be, SHIELD_HOUSING, light, overlay);
        }
    }
}
