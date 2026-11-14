package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class HangarDoorRenderer implements BlockEntityRenderer<HangarDoorBlockEntity> {

    static final float PX = 1.0F / 16.0F;

    static final double AX_Y = 8.0, AX_Z = 0.6, RMAX = 5.2, RMIN = 1.9, COIL_X = 4.8, SLOT_Y = 2.4, SLOT_Z = 0.0, RLEAD = 3.6, SLAT = 4.0,
            SLAT_END = 5.8, BAR_END = 4.4, SLAT_AUTH = -4.0, BAR_AUTH = -6.6, BAR_PX = 2.6;

    static final double END_PLATE = 4.8, END_CAP = 6.2, END_NONE = 8.0;

    static double[] leadGeom(double r) {
        double cu = AX_Z - SLOT_Z, cv = AX_Y - SLOT_Y, d = Math.hypot(cu, cv);
        double th = Math.atan2(cv, cu) + Math.asin(Math.min(0.999, r / d));
        return new double[] { Math.toDegrees(th), Math.sqrt(Math.max(0.0, d * d - r * r)) };
    }

    static double coilR(double wound) {
        double w = Math.max(0.0, Math.min(16.0, wound));
        return Math.sqrt(RMIN * RMIN + (RMAX * RMAX - RMIN * RMIN) * w / 16.0);
    }

    static double end(int kind) { return kind == HangarDoorBlockEntity.K_PLATE ? END_PLATE : kind == HangarDoorBlockEntity.K_CAP ? END_CAP : END_NONE; }

    @Override
    public void render(HangarDoorBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MeshModel mm = MeshModel.named("hangar_door");
        if (!mm.has("base")) return;
        BlockState st = be.getBlockState();
        Direction f = st.getValue(MachineBlock.FACING);
        BlockPos at = be.getBlockPos();
        HangarDoorBlockEntity head = be.inRun() && level.getBlockEntity(be.headPosClient()) instanceof HangarDoorBlockEntity h ? h : null;
        HangarDoorBlockEntity skins = head == null ? be : head;
        boolean run = head != null;
        int col = run ? be.column() : 0, n = run ? head.columns() : 1, drop = run ? be.columnDrop() : 0;
        int wk = run ? be.westKind() : HangarDoorBlockEntity.K_PLATE, ek = run ? be.eastKind() : HangarDoorBlockEntity.K_PLATE;
        double t = run ? head.travelAt(level.getGameTime() + pt) : 0.0;
        double e = drop > 0 ? HangarDoorBlockEntity.extension(t, drop) : 0.0;
        BlockState hoodSkin = skins.copycat().shown(), slatSkin = skins.slats().shown();
        int state = run ? head.doorState() : HangarDoorBlockEntity.S_NO_RUN;

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - f.toYRot()));
        draw(mm, "base", ps, buffers, light, overlay);
        skinned(mm, "housing", hoodSkin, ps, buffers, light, overlay, level, at);
        draw(mm, be.head() ? "head" : "fascia", ps, buffers, light, overlay);

        double r = coilR(drop - e);
        float turn = (float) -Math.toDegrees(e * 16.0 / Math.max(RMIN, r));
        double x0 = -end(wk), x1 = end(ek), xm = (x0 + x1) / 2.0, sx = (x1 - x0) / (2.0 * COIL_X);
        ps.pushPose();
        ps.translate(xm * PX, AX_Y * PX, AX_Z * PX);
        ps.mulPose(Axis.XP.rotationDegrees(turn));
        ps.scale((float) sx, (float) (r / RMAX), (float) (r / RMAX));
        ps.translate(0.0, -AX_Y * PX, -AX_Z * PX);
        skinned(mm, "housing_coil", slatSkin, ps, buffers, light, overlay, level, at);
        ps.popPose();

        double[] lg = leadGeom(r), l0 = leadGeom(RLEAD);
        ps.pushPose();
        ps.translate(xm * PX, SLOT_Y * PX, SLOT_Z * PX);
        ps.mulPose(Axis.XP.rotationDegrees((float) (90.0 - lg[0])));
        ps.scale((float) sx, (float) (lg[1] / l0[1]), 1.0F);
        ps.mulPose(Axis.XP.rotationDegrees((float) (l0[0] - 90.0)));
        ps.translate(0.0, -SLOT_Y * PX, -SLOT_Z * PX);
        skinned(mm, "housing_lead", slatSkin, ps, buffers, light, overlay, level, at);
        ps.popPose();
        skinned(mm, "housing_slot", slatSkin, ps, buffers, light, overlay, level, at);

        int lamp = lampColour(state, level.getGameTime());
        for (String side : new String[] { "w", "e" }) {
            int kind = side.equals("w") ? wk : ek;
            if (kind == HangarDoorBlockEntity.K_PLATE) {
                draw(mm, "plate_" + side, ps, buffers, light, overlay);
                ps.pushPose();
                pivotX(ps, AX_Y, AX_Z, turn);
                draw(mm, "gear_" + side, ps, buffers, light, overlay);
                ps.popPose();
                if (mm.has("lamp_" + side)) mm.drawColoured("lamp_" + side, ps.last(), buffers, lamp == 0x1E3A5A ? light : 0xF000F0, overlay, (lamp >> 16) & 255, (lamp >> 8) & 255, lamp & 255);
            } else if (kind == HangarDoorBlockEntity.K_CAP) draw(mm, "cap_" + side, ps, buffers, light, overlay);
        }

        for (int k = 1; k <= drop; k++) {
            int lk = LevelRenderer.getLightColor(level, at.below(k));
            ps.pushPose();
            ps.translate(0.0, -(k - 1), 0.0);
            if (run && col == 0) draw(mm, "rail_w", ps, buffers, lk, overlay);
            if (run && col == n - 1) draw(mm, "rail_e", ps, buffers, lk, overlay);
            ps.popPose();
        }

        boolean staticCurtain = run && state == HangarDoorBlockEntity.S_CLOSED && slatSkin == null;
        if (run && drop > 0 && !staticCurtain) {
            double yb = -e * 16.0;
            double lo = col == 0 ? -SLAT_END : -8.0, hi = col == n - 1 ? SLAT_END : 8.0;
            for (double y = yb + BAR_PX; y < 0.0; y += SLAT) {
                int lk = LevelRenderer.getLightColor(level, at.below(1 + (int) Math.floor(-y / 16.0)));
                ps.pushPose();
                ps.translate((lo + hi) / 2.0 * PX, (y - SLAT_AUTH) * PX, 0.0);
                ps.scale((float) ((hi - lo) / 16.0), 1.0F, 1.0F);
                skinned(mm, "housing_slat", slatSkin, ps, buffers, lk, overlay, level, at);
                ps.popPose();
            }
            double blo = col == 0 ? -BAR_END : -8.0, bhi = col == n - 1 ? BAR_END : 8.0;
            int lb = LevelRenderer.getLightColor(level, at.below(Math.max(1, (int) Math.ceil(e))));
            ps.pushPose();
            ps.translate((blo + bhi) / 2.0 * PX, (yb - BAR_AUTH) * PX, 0.0);
            ps.scale((float) ((bhi - blo) / 16.0), 1.0F, 1.0F);
            draw(mm, "bar", ps, buffers, lb, overlay);
            ps.popPose();
        }
        ps.popPose();
    }

    private static int lampColour(int state, long time) {
        if (state == HangarDoorBlockEntity.S_CLOSING || state == HangarDoorBlockEntity.S_OPENING) return (time / 5) % 2 == 0 ? 0x4AA3FF : 0x1E3A5A;
        if (state == HangarDoorBlockEntity.S_BLOCKED || state == HangarDoorBlockEntity.S_NO_POWER) return (time / 2) % 2 == 0 ? 0x4AA3FF : 0x1E3A5A;
        return 0x1E3A5A;
    }

    static void pivotX(PoseStack ps, double y, double z, double deg) {
        ps.translate(0.0, y * PX, z * PX);
        ps.mulPose(Axis.XP.rotationDegrees((float) deg));
        ps.translate(0.0, -y * PX, -z * PX);
    }

    private static void skinned(MeshModel mm, String bone, BlockState skin, PoseStack ps, MultiBufferSource buffers, int light, int overlay, Level level, BlockPos at) {
        if (!mm.has(bone)) return;
        if (skin != null) CopycatSkin.draw(mm, bone, ps.last().pose(), ps.last().normal(), skin, level, at, buffers, light, overlay);
        else mm.draw(bone, ps.last(), buffers, light, overlay);
    }

    private static void draw(MeshModel mm, String bone, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (mm.has(bone)) mm.draw(bone, ps.last(), buffers, light, overlay);
    }

    @Override
    public AABB getRenderBoundingBox(HangarDoorBlockEntity be) {
        BlockPos o = be.getBlockPos();
        return new AABB(o.getX() - 1, o.getY() - Balance.HDOOR_DROP_MAX - 1, o.getZ() - 1, o.getX() + 2, o.getY() + 2, o.getZ() + 2);
    }

    @Override public boolean shouldRenderOffScreen(HangarDoorBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }
}
