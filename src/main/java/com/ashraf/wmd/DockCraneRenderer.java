package com.ashraf.wmd;

import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class DockCraneRenderer extends StagedUnitRenderer<DockCraneBlockEntity> {

    private static final java.util.Set<String> BONES = java.util.Set.of("slew");
    private static final int ROPE = 0xFF20262C;

    public DockCraneRenderer() { super("dock_crane", 6, 7); }

    @Override protected java.util.Set<String> attachBones() { return BONES; }

    @Override
    protected void drawAttached(DockCraneBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        Matrix4f m = poses.get("slew");
        if (m == null || be.getLevel() == null) return;
        float[] p = be.pose(be.getLevel().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        float luff = p[1], drop = p[2];
        float hy = DockCraneBlockEntity.headY(luff), hz = DockCraneBlockEntity.headZ(luff);
        float ry = DockCraneBlockEntity.headY(DockCraneBlockEntity.LUFF_REST), rz = DockCraneBlockEntity.headZ(DockCraneBlockEntity.LUFF_REST);
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get("slew"));

        CcRenderers.line(buffers.getBuffer(RenderType.lines()), ps.last(), 0.0F, hy / 16.0F, hz / 16.0F, 0.0F, (hy - DockCraneBlockEntity.HOOK_TOP) / 16.0F - drop, hz / 16.0F, ROPE);

        MeshModel mesh = MeshModel.named("dock_crane");
        ps.pushPose();
        ps.translate(0.0F, (hy - ry) / 16.0F - drop, (hz - rz) / 16.0F);
        if (mesh.has("hook")) mesh.draw("hook", ps.last(), buffers, light, overlay);
        ps.popPose();

        ItemStack load = be.carried();
        if (load.isEmpty()) return;
        ps.pushPose();
        ps.translate(0.0F, (hy - DockCraneBlockEntity.LOAD_AXIS) / 16.0F - drop, hz / 16.0F);
        if (load.getItem() instanceof TorpedoItem) {
            TorpedoKind kind = TorpedoKind.of(load);
            MeshModel tm = MeshModel.named(kind.mesh());
            if (tm.has("part")) {
                ps.translate(0.0F, -kind.axisPx() / 16.0F, 0.0F);
                TorpedoRenderer.draw(tm, kind, ps.last(), buffers, light);
            }
        } else {
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
            ps.scale(2.5F, 2.5F, 2.5F);
            Minecraft.getInstance().getItemRenderer().renderStatic(load, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
        }
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(DockCraneBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(7.0, 0.0, 7.0).expandTowards(0.0, 7.0, 0.0).expandTowards(0.0, -Balance.CRANE_DROP - 2.0, 0.0);
    }
}
