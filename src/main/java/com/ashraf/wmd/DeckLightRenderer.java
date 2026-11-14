package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class DeckLightRenderer implements BlockEntityRenderer<DeckLightBlockEntity> {

    private static final String MESH = "deck_light";

    @Override
    public void render(DeckLightBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mm = MeshModel.named(MESH);
        BlockState st = be.getBlockState();
        Direction f = st.hasProperty(MachineBlock.FACING) ? st.getValue(MachineBlock.FACING) : Direction.NORTH;
        float[] c = DeckLightBlockEntity.RGB[Math.max(0, Math.min(DeckLightBlockEntity.RGB.length - 1, be.mode()))];
        boolean on = be.lit() && be.getLevel() != null && !ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));
        if (mm.has("base")) mm.draw("base", ps.last(), buffers, light, overlay);
        if (mm.has("housing")) {
            BlockState skin = be.copycat().shown();
            if (skin != null && be.getLevel() != null) CopycatSkin.draw(mm, "housing", ps.last().pose(), ps.last().normal(), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
            else mm.draw("housing", ps.last(), buffers, light, overlay);
        }

        {
            int k = on ? 255 : 70;
            lens(buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(WHITE)), ps.last(), on ? 0xF000F0 : light, overlay,
                    Math.round(c[0] * k), Math.round(c[1] * k), Math.round(c[2] * k));
        }
        ps.popPose();
        if (on) {

            net.minecraft.world.phys.Vec3 w = ShipFrame.drawnWorld(be.getLevel(), be.getBlockPos(), net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos()).add(0.0, 0.55, 0.0), partialTick);
            if (w != null) VeilBridge.machineGlow(0x4D00_0000_0000_0000L ^ be.getBlockPos().asLong(), w.x, w.y, w.z, c[0], c[1], c[2],
                    be.mode() == DeckLightBlockEntity.BEACON ? 6.0F : 3.0F, be.mode() == DeckLightBlockEntity.BEACON ? 1.6F : 1.0F, be.getLevel().getGameTime());
        }
    }

    private static final net.minecraft.resources.ResourceLocation WHITE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    private static void lens(com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack.Pose pose, int light, int overlay, int r, int g, int b) {
        final int n = 16;
        final float rad = 0.17F, y = 1.0F + 0.002F;
        for (int i = 0; i < n; i += 2) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n, a2 = Math.PI * 2 * (i + 2) / n;
            float[][] p = { { 0.0F, 0.0F }, { (float) (Math.cos(a0) * rad), (float) (Math.sin(a0) * rad) }, { (float) (Math.cos(a1) * rad), (float) (Math.sin(a1) * rad) },
                    { (float) (Math.cos(a2) * rad), (float) (Math.sin(a2) * rad) } };
            for (int k = 3; k >= 0; k--)
                vc.addVertex(pose, p[k][0], y, p[k][1]).setColor(r, g, b, 255).setUv(0.5F, 0.5F).setOverlay(overlay).setLight(light).setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    @Override public AABB getRenderBoundingBox(DeckLightBlockEntity be) { return new AABB(be.getBlockPos()).inflate(0.5); }
    @Override public boolean shouldRenderOffScreen(DeckLightBlockEntity be) { return false; }
    @Override public int getViewDistance() { return 192; }
}
