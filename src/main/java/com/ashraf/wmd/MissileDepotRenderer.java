package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public class MissileDepotRenderer implements BlockEntityRenderer<MissileDepotBlockEntity> {

    private static final String NAME = "missile_depot";
    private static final float CX = -8.0F, CZ = 104.0F;
    private static final float[] DZ = { -78.0F, -26.0F, 26.0F, 78.0F };
    private static final float[] CRANE = { CX, 416.0F, 24.0F }, GLOW = { CX, 216.0F, CZ };

    @Override
    public void render(MissileDepotBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(NAME);
        if (!mesh.has("base")) return;
        float out = be.craneOut(pt);
        int open = be.openCell(pt);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        mesh.draw("base", ps.last(), buffers, light, overlay);
        for (int i = 0; i < Balance.DEPOT_CELLS; i++) {
            String b = "hatch" + i;
            if (!mesh.has(b)) continue;
            boolean west = i < 4;
            float[] pv = { west ? CX - 36.0F - 22.0F : CX + 36.0F + 22.0F, 414.0F, CZ + DZ[i % 4] };
            float rz = i == open ? (west ? -90.0F : 90.0F) * out : 0.0F;
            SatelliteMesh.drawBone(mesh, NAME, b, pv, 0.0F, 0.0F, rz, ps, buffers, light, overlay);
        }
        if (mesh.has("crane")) SatelliteMesh.drawBone(mesh, NAME, "crane", CRANE, 0.0F, 180.0F * out, 0.0F, ps, buffers, light, overlay);
        drawLoad(be, pt, out, ps, buffers, light);
        if (mesh.has("glow")) SatelliteMesh.drawBone(mesh, NAME, "glow", GLOW, 0.0F, 0.0F, 0.0F, ps, buffers, LightTexture.FULL_BRIGHT, overlay);
        ps.popPose();
    }

    private static void drawLoad(MissileDepotBlockEntity be, float pt, float out, PoseStack ps, MultiBufferSource buffers, int light) {
        float t = be.craneT(pt);
        ItemStack s = be.craneItem();
        if (t < 0.0F || s.isEmpty()) return;
        boolean in = be.craneIn();
        float lower;
        if (in) {
            if (t < 0.375F) return;
            lower = t < 0.75F ? 1.0F - WmdUi.easeOut((t - 0.375F) / 0.375F) : 0.0F;
        } else {
            if (t >= 0.625F) return;
            lower = t < 0.25F ? 0.0F : WmdUi.easeOut((t - 0.25F) / 0.375F);
        }
        MissileBuild b = MissileBuild.of(s);
        float len = MissileParts.lengthOf(b), hook = HOOK_Y / 16.0F;
        float tail = Mth.lerp(lower, hook - len, 0.2F);
        float[] pv = SatelliteMesh.pivot(NAME, "crane", CRANE);
        ps.pushPose();
        ps.translate(pv[0] / 16.0F, pv[1] / 16.0F, pv[2] / 16.0F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F * out));
        ps.translate(-pv[0] / 16.0F, -pv[1] / 16.0F, -pv[2] / 16.0F);
        ps.translate(pv[0] / 16.0F, tail, (pv[2] + HOOK_REACH) / 16.0F);
        MissileParts.render(b, ps, buffers, light, pt);
        ps.popPose();
    }

    private static final float HOOK_Y = 413.0F, HOOK_REACH = 150.0F;

    @Override
    public AABB getRenderBoundingBox(MissileDepotBlockEntity be) { return new AABB(be.getBlockPos()).inflate(24.0, 1.0, 24.0).expandTowards(0.0, 30.0, 0.0); }

    @Override
    public boolean shouldRenderOffScreen(MissileDepotBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 256; }
}
