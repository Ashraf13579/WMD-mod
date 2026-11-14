package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class GunnerRenderers {

    private GunnerRenderers() {}

    static class Skinned<T extends ProcessUnitBlockEntity & CopycatState.Host & CcAnimated> implements BlockEntityRenderer<T> {
        private final Look<T> look;
        private final double grow;

        Skinned(String mesh, int up, int reach, Set<String> attach, double grow) { this.look = new Look<>(mesh, up, reach, attach); this.grow = grow; }

        @Override
        public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            look.render(be, partialTick, poseStack, buffers, light, overlay);
        }

        @Override public AABB getRenderBoundingBox(T be) { return look.getRenderBoundingBox(be).inflate(grow); }
        @Override public boolean shouldRenderOffScreen(T be) { return true; }
        @Override public int getViewDistance() { return 192; }
    }

    static final class Look<T extends ProcessUnitBlockEntity & CopycatState.Host & CcAnimated> extends StagedUnitRenderer<T> {
        private final String name;
        private final Set<String> attach;
        private BlockState skin;

        Look(String name, int up, int reach, Set<String> attach) {
            super(name, up, reach);
            this.name = name;
            this.attach = attach;
        }

        @Override protected Set<String> attachBones() { return attach; }
        @Override protected boolean drawsItself(String bone) { return skin != null && attach.contains(bone); }

        @Override
        public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copycat().shown();
            try {
                super.render(be, partialTick, poseStack, buffers, light, overlay);
            } finally {
                skin = null;
            }
        }

        @Override
        protected void drawAttached(T be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null) return;
            for (String b : attach)
                if (poses.containsKey(b)) CopycatSkin.draw(MeshModel.named(name), b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        }
    }

    static void register(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        MountedRenderer.register(event, GunnerBlocks.GUN_TURRET_BE.get(), context -> turret());
        MountedRenderer.register(event, GunnerBlocks.GUNNER_STATION_BE.get(), context -> station());
        MountedRenderer.register(event, GunnerBlocks.HEAVY_GUN_BE.get(), context -> new Skinned<HeavyGunBlockEntity>("heavy_gun", 2, 3, Set.of("housing", "housing_door_l", "housing_door_r"), 2.0));
        event.registerEntityRenderer(GunnerBlocks.GUNNER_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    static BlockEntityRenderer<GunTurretBlockEntity> turret() {
        return new Skinned<>("gun_turret", 1, 2, Set.of("housing", "housing_door_w", "housing_door_e"), 2.5);
    }

    static BlockEntityRenderer<GunnerStationBlockEntity> station() {
        return new Skinned<>("gunner_station", 2, 2, Set.of("housing"), 0.5);
    }
}
