package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class TorpedoNetRenderer implements BlockEntityRenderer<TorpedoNetBlockEntity> {

    static final String MODEL = "torpedo_net";
    private static final float PX = 1.0F / 16.0F, BOB = 0.9F * PX, BOB_PERIOD = 3.2F;

    static final float HOME_HALF_DY = 2.0F, HOME_FAR_DX = 3.0F;

    @Override
    public void render(TorpedoNetBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MeshModel mm = MeshModel.named(MODEL);
        if (!mm.has("tile")) return;
        be.refresh(level);
        int L = be.len(), D = be.depth();
        boolean surf = be.surfaced();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        AABB box = be.netBox();
        double dx = Math.max(Math.max(box.minX - cam.x, 0.0), cam.x - box.maxX), dy = Math.max(Math.max(box.minY - cam.y, 0.0), cam.y - box.maxY),
                dz = Math.max(Math.max(box.minZ - cam.z, 0.0), cam.z - box.maxZ);
        boolean far = Math.sqrt(dx * dx + dy * dy + dz * dz) > Balance.NET_FAR;
        float ropeDy = surf ? -0.5F : 0.0F;
        float t = (level.getGameTime() + partialTick) / 20.0F;

        ps.pushPose();
        if (be.axis() == Direction.Axis.X) ps.translate(0.0, 0.0, 0.5);
        else { ps.translate(0.5, 0.0, 0.0); ps.mulPose(Axis.YP.rotationDegrees(-90.0F)); }
        for (int j = 0; j < D; j++) {
            for (int i = 0; i < L; i++) {
                if (!be.intact(i, j)) continue;
                int lt = LevelRenderer.getLightColor(level, be.cell(i, j));
                boolean top = j == D - 1;

                if (far) piece(mm, ps, buffers, "far", i - HOME_FAR_DX, j, lt, overlay);
                else if (top && surf) piece(mm, ps, buffers, "half", i, j - HOME_HALF_DY, lt, overlay);
                else piece(mm, ps, buffers, "tile", i, j, lt, overlay);
                if (top) piece(mm, ps, buffers, "rope", i, j + ropeDy, lt, overlay);
                if (j == 0) {
                    piece(mm, ps, buffers, "chain", i, 0, lt, overlay);
                    if (i % 4 == 1 || (L < 2 && i == 0)) piece(mm, ps, buffers, "sinker", i, 0, lt, overlay);
                }
                if (far) continue;
                if (i + 1 < L && !be.intact(i + 1, j)) fray(mm, ps, buffers, i, j, 0.0F, lt, overlay);
                if (i > 0 && !be.intact(i - 1, j)) fray(mm, ps, buffers, i, j, 180.0F, lt, overlay);
                if (j + 1 < D && !be.intact(i, j + 1)) fray(mm, ps, buffers, i, j, 90.0F, lt, overlay);
                if (j > 0 && !be.intact(i, j - 1)) fray(mm, ps, buffers, i, j, -90.0F, lt, overlay);
            }
        }

        for (int i = L < 2 ? 0 : 1; i < Math.max(L, 1); i += 2) {
            if (!be.intact(i, D - 1) && !be.intact(i - 1, D - 1)) continue;
            float x = L < 2 ? 0.0F : i - 0.5F;
            int lt = LevelRenderer.getLightColor(level, be.cell(i, D - 1));
            if (surf) piece(mm, ps, buffers, "buoy", x, D - 1 + ropeDy + (float) Math.sin(t * Math.PI * 2.0 / BOB_PERIOD + i * 0.9) * BOB, lt, overlay);
            else piece(mm, ps, buffers, "float", x, D - 1, lt, overlay);
        }

        for (int e = 0; e < 2; e++) {
            int i = e == 0 ? 0 : L - 1;
            int bed = be.bed(e == 1);
            ps.pushPose();
            if (e == 1) { ps.translate(i + 0.5, 0.0, 0.0); ps.mulPose(Axis.YP.rotationDegrees(180.0F)); ps.translate(-i - 0.5, 0.0, 0.0); }
            for (int j = 0; j < D; j++) piece(mm, ps, buffers, "post", i, j, LevelRenderer.getLightColor(level, be.cell(i, j)), overlay);
            int lt = LevelRenderer.getLightColor(level, be.cell(i, D - 1));
            piece(mm, ps, buffers, "cap", i, D - 1, LevelRenderer.getLightColor(level, be.cell(i, D)), overlay);
            piece(mm, ps, buffers, "ropeend", i, D - 1 + ropeDy, lt, overlay);
            for (int k = 0; k < bed; k++) piece(mm, ps, buffers, "achain", i, -k, LevelRenderer.getLightColor(level, be.cell(i, -k - 1)), overlay);
            if (bed > 0) piece(mm, ps, buffers, "anchor", i, -bed + 2, LevelRenderer.getLightColor(level, be.cell(i, -bed)), overlay);
            ps.popPose();
        }
        ps.popPose();
    }

    private static void piece(MeshModel mm, PoseStack ps, MultiBufferSource buffers, String bone, float x, float y, int light, int overlay) {
        ps.pushPose();
        ps.translate(x, y, 0.0F);
        mm.draw(bone, ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    private static void fray(MeshModel mm, PoseStack ps, MultiBufferSource buffers, int i, int j, float deg, int light, int overlay) {
        ps.pushPose();
        ps.translate(i + 0.5F, j + 0.5F, 0.0F);
        ps.mulPose(Axis.ZP.rotationDegrees(deg));
        ps.translate(-0.5F, -0.5F, 0.0F);
        mm.draw("fray", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(TorpedoNetBlockEntity be) {
        AABB b = be.netBox();
        return new AABB(b.minX - 1.0, b.minY - 66.0, b.minZ - 1.0, b.maxX + 1.0, b.maxY + 1.0, b.maxZ + 1.0);
    }

    @Override
    public int getViewDistance() { return 160; }

    @Override
    public boolean shouldRenderOffScreen(TorpedoNetBlockEntity be) { return true; }
}
