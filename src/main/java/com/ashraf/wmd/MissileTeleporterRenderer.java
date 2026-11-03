package com.ashraf.wmd;

import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class MissileTeleporterRenderer implements BlockEntityRenderer<MissileTeleporterBlockEntity> {

    private static final String NAME = "missile_teleporter";
    private static final float AXIS_Z = 48.0F, HOOK_Y = 358.0F, BASE_Y = 24.0F;
    private static final float[] RINGS = { 60.0F, 110.0F, 160.0F, 210.0F, 260.0F, 310.0F };

    private record Clip(VertexConsumer d, float cut) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { d.addVertex(x, Math.min(y, cut), z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { d.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { d.setNormal(x, y, z); return this; }
    }

    private static float ease(float t) { t = Mth.clamp(t, 0.0F, 1.0F); return t * t * (3.0F - 2.0F * t); }

    @Override
    public void render(MissileTeleporterBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        int phase = be.phase();
        float t = be.progress(pt);

        float boomZ = 0.0F, trolleyZ = 0.0F, missileBase = BASE_Y, missileZ = AXIS_Z;
        boolean drawMissile = !be.missile().isEmpty() && phase != MissileTeleporterBlockEntity.EMPTY;
        ItemStack m = be.missile();
        MissileBuild build = Logistics.buildOf(m);
        if (build == null && m.getItem() instanceof LaunchMissileItem lm) build = MissileBuild.preset(lm.getTier());
        float len = build == null ? 0.0F : (float) (160.0 * BayCalibre.len(build.calibre()));
        if (phase == MissileTeleporterBlockEntity.LOADING) {
            float out = t < 0.2F ? ease(t / 0.2F) : t < 0.3F ? 1.0F : 1.0F - ease((t - 0.3F) / 0.35F);
            boomZ = -48.0F * out;
            trolleyZ = -88.0F * out;
            missileZ = AXIS_Z + trolleyZ;
            missileBase = t < 0.65F ? HOOK_Y - len : Mth.lerp(ease((t - 0.65F) / 0.35F), HOOK_Y - len, BASE_Y);
            drawMissile = drawMissile && t >= 0.2F;
        }
        int lit = phase == MissileTeleporterBlockEntity.CHARGING ? Mth.ceil(t * 6) : phase == MissileTeleporterBlockEntity.FADING ? 6 : 0;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        if (mesh.has("base")) {
            mesh.draw("base", ps.last(), buffers, light, overlay);
            for (int i = 0; i < lit; i++) if (mesh.has("band" + i)) mesh.draw("band" + i, ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
            ps.pushPose();
            ps.translate(0.0F, 0.0F, boomZ / 16.0F);
            if (mesh.has("boom")) mesh.draw("boom", ps.last(), buffers, light, overlay);
            ps.popPose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, trolleyZ / 16.0F);
            if (mesh.has("trolley")) mesh.draw("trolley", ps.last(), buffers, light, overlay);
            ps.popPose();
            if (mesh.has("glow")) mesh.draw("glow", ps.last(), buffers, LightTexture.FULL_BRIGHT, overlay);
        }
        if (drawMissile && build != null) {
            MultiBufferSource src = buffers;
            if (phase == MissileTeleporterBlockEntity.FADING) {
                float cutPx = BASE_Y + len * (1.0F - t);
                Vector4f v = ps.last().pose().transform(new Vector4f(0.0F, cutPx / 16.0F, 0.0F, 1.0F));
                final float cut = v.y();
                src = rt -> new Clip(buffers.getBuffer(rt), cut);
            }
            ps.pushPose();
            ps.translate(0.0F, missileBase / 16.0F, missileZ / 16.0F);
            MissileParts.render(build, ps, src, phase == MissileTeleporterBlockEntity.FADING || phase == MissileTeleporterBlockEntity.CHARGING ? LightTexture.FULL_BRIGHT : light, pt);
            ps.popPose();
        }
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(MissileTeleporterBlockEntity be) { return new AABB(be.getBlockPos()).inflate(12.0, 1.0, 12.0).expandTowards(0.0, 26.0, 0.0); }

    @Override
    public boolean shouldRenderOffScreen(MissileTeleporterBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 256; }
}
