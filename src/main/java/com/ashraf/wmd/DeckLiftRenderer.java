package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DeckLiftRenderer implements BlockEntityRenderer<DeckLiftBlockEntity> {

    static final int RAIL_TICKS = 10;

    static final float POST_INSET = 0.12F;

    @Override
    public void render(DeckLiftBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        MeshModel mm = MeshModel.named("deck_lift");
        if (!mm.has("plate")) return;

        if (!be.getBlockState().getValue(DeckLiftBlock.HEAD) || be.sx() <= 0) {
            if (!coveredByHead(level, be)) lone(be, mm, ps, buffers, light, overlay);
            if (!be.getBlockState().getValue(DeckLiftBlock.HEAD)) return;
        }
        BlockPos o = be.getBlockPos();
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        if (mm.has("head")) mm.draw("head", ps.last(), buffers, light, overlay);
        ps.popPose();
        int sx = be.sx(), sz = be.sz(), drop = be.drop();
        if (sx <= 0 || sz <= 0) return;
        float yoff = (float) (be.topY(pt) - (o.getY() + 1.0));
        if (be.liftState() != DeckLiftBlockEntity.UP) DeckLiftWalk.seen(o);
        BlockState skin = be.copycat().shown();
        int ly = (int) Math.floor(o.getY() + 1.0 + yoff);
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            BlockPos lp = new BlockPos(o.getX() + x, ly, o.getZ() + z);
            int l = LevelRenderer.getLightColor(level, lp);
            ps.pushPose();
            ps.translate(x + 0.5, yoff, z + 0.5);
            if (mm.has("under")) mm.draw("under", ps.last(), buffers, l, overlay);
            if (skin != null) CopycatSkin.draw(mm, "plate", ps.last().pose(), ps.last().normal(), skin, level, lp, buffers, l, overlay);
            else mm.draw("plate", ps.last(), buffers, l, overlay);
            ps.popPose();
        }

        if (drop > 0 && mm.has("post")) {
            for (int[] p : posts(sx, sz)) {
                float px = p[0] == 0 ? POST_INSET : p[0] == sx ? sx - POST_INSET : p[0], pz = p[1] == 0 ? POST_INSET : p[1] == sz ? sz - POST_INSET : p[1];
                for (int i = 0; i < drop; i++) {
                    BlockPos lp = new BlockPos(o.getX() + Math.min(sx - 1, (int) px), o.getY() + 1 - drop + i, o.getZ() + Math.min(sz - 1, (int) pz));
                    ps.pushPose();
                    ps.translate(px, 1 - drop + i, pz);
                    mm.draw("post", ps.last(), buffers, LevelRenderer.getLightColor(level, lp), overlay);
                    ps.popPose();
                }
                if (mm.has("nut")) {
                    ps.pushPose();
                    ps.translate(px, yoff, pz);
                    mm.draw("nut", ps.last(), buffers, LevelRenderer.getLightColor(level, new BlockPos(o.getX() + Math.min(sx - 1, (int) px), ly, o.getZ() + Math.min(sz - 1, (int) pz))), overlay);
                    ps.popPose();
                }
            }
        }

        int st = be.liftState();
        if (st == DeckLiftBlockEntity.UP || !mm.has("rail")) return;
        float k = railRaise(be, level.getGameTime() + pt);
        if (k <= 0.01F) return;
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            if (x != 0 && z != 0 && x != sx - 1 && z != sz - 1) continue;
            int l = LevelRenderer.getLightColor(level, new BlockPos(o.getX() + x, ly, o.getZ() + z));
            if (z == 0) rail(mm, ps, buffers, l, overlay, x, z, yoff, 0.0F, k);
            if (z == sz - 1) rail(mm, ps, buffers, l, overlay, x, z, yoff, 180.0F, k);
            if (x == 0) rail(mm, ps, buffers, l, overlay, x, z, yoff, 90.0F, k);
            if (x == sx - 1) rail(mm, ps, buffers, l, overlay, x, z, yoff, -90.0F, k);
        }
        boolean blink = (level.getGameTime() / 6) % 2 == 0;
        for (int[] c : new int[][] { { 0, 0 }, { sx, 0 }, { 0, sz }, { sx, sz } }) {
            float cx = c[0] == 0 ? 0.3F : c[0] - 0.3F, cz = c[1] == 0 ? 0.3F : c[1] - 0.3F;
            if (mm.has("beacon")) {
                ps.pushPose();
                ps.translate(cx, yoff, cz);
                mm.draw("beacon", ps.last(), buffers, 0xF000F0, overlay);
                ps.popPose();
            }
            if (blink && k > 0.9F) {
                Vec3 w = ShipFrame.drawnWorld(level, o, new Vec3(o.getX() + cx, o.getY() + 1.0 + yoff + 0.9, o.getZ() + cz), Float.NaN);
                if (w != null) VeilBridge.machineGlow(0x4E00_0000_0000_0000L ^ o.asLong() ^ ((long) c[0] << 20) ^ c[1], w.x, w.y, w.z, 0.25F, 0.55F, 1.0F, 4.0F, 1.0F, level.getGameTime());
            }
        }
    }

    private static boolean coveredByHead(Level level, DeckLiftBlockEntity be) {
        long now = level.getGameTime();

        if (be.coverAt != Long.MIN_VALUE && now >= be.coverAt && now - be.coverAt < 20) return be.covered;
        be.coverAt = now;
        BlockPos p = be.getBlockPos();
        int n = 0;
        while (n++ < Balance.LIFT_MAX && level.getBlockState(p.west()).getBlock() instanceof DeckLiftBlock) p = p.west();
        n = 0;
        while (n++ < Balance.LIFT_MAX && level.getBlockState(p.north()).getBlock() instanceof DeckLiftBlock) p = p.north();
        BlockPos o = be.getBlockPos();
        boolean c = level.getBlockEntity(p) instanceof DeckLiftBlockEntity h && h.getBlockState().getValue(DeckLiftBlock.HEAD) && h.sx() > 0
                && o.getX() >= p.getX() && o.getX() < p.getX() + h.sx() && o.getZ() >= p.getZ() && o.getZ() < p.getZ() + h.sz();
        be.covered = c;
        return c;
    }

    private static void lone(DeckLiftBlockEntity be, MeshModel mm, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        if (mm.has("under")) mm.draw("under", ps.last(), buffers, light, overlay);
        BlockState skin = be.copycat().shown();
        if (skin != null && be.getLevel() != null) CopycatSkin.draw(mm, "plate", ps.last().pose(), ps.last().normal(), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        else mm.draw("plate", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    private static void rail(MeshModel mm, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int x, int z, float yoff, float yaw, float k) {
        ps.pushPose();
        ps.translate(x + 0.5, yoff + 1.0, z + 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0.0, 0.0, -7.4 / 16.0);
        ps.mulPose(Axis.XP.rotationDegrees(-(1.0F - k) * 90.0F));
        ps.translate(0.0, -1.0, 7.4 / 16.0);
        mm.draw("rail", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    static float railRaise(DeckLiftBlockEntity be, double now) {
        int st = be.liftState();
        double t = now - be.getStateAtClient();
        if (st == DeckLiftBlockEntity.LOWERING) return (float) Math.min(1.0, t / RAIL_TICKS);
        if (st == DeckLiftBlockEntity.RAISING) return (float) Math.min(1.0, Math.max(0.0, (be.getTripTicksClient() - t) / RAIL_TICKS));
        return st == DeckLiftBlockEntity.DOWN ? 1.0F : 0.0F;
    }

    static java.util.List<int[]> posts(int sx, int sz) {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        for (int x = 0; x <= sx; x += Balance.LIFT_POST_EVERY) { l.add(new int[] { x, 0 }); l.add(new int[] { x, sz }); }
        if (sx % Balance.LIFT_POST_EVERY != 0) { l.add(new int[] { sx, 0 }); l.add(new int[] { sx, sz }); }
        for (int z = Balance.LIFT_POST_EVERY; z < sz; z += Balance.LIFT_POST_EVERY) { l.add(new int[] { 0, z }); l.add(new int[] { sx, z }); }
        return l;
    }

    @Override
    public AABB getRenderBoundingBox(DeckLiftBlockEntity be) {
        BlockPos o = be.getBlockPos();
        int sx = Math.max(1, be.sx()), sz = Math.max(1, be.sz());
        return new AABB(o.getX() - 1, o.getY() - Math.max(1, be.drop()) - 1, o.getZ() - 1, o.getX() + sx + 1, o.getY() + 3, o.getZ() + sz + 1);
    }

    @Override public boolean shouldRenderOffScreen(DeckLiftBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 256; }
}
