package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientDucts {

    private ClientDucts() {}

    private static final int MAX = 256;

    private record Batch(Vec3[] pts, double[] cum, ItemStack item, long start, int ticks) {}

    private static final List<Batch> BATCHES = new ArrayList<>();

    static void onTransit(DuctTransitPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || p.path().length == 0 || p.ticks() <= 0) return;
        Vec3[] pts = new Vec3[p.path().length + 2];
        for (int i = 0; i < p.path().length; i++) pts[i + 1] = Vec3.atCenterOf(BlockPos.of(p.path()[i]));
        Direction from = Direction.from3DDataValue(p.fromSide()), to = Direction.from3DDataValue(p.toSide());
        pts[0] = pts[1].add(from.getStepX() * 0.5, from.getStepY() * 0.5, from.getStepZ() * 0.5);
        pts[pts.length - 1] = pts[pts.length - 2].add(to.getStepX() * 0.5, to.getStepY() * 0.5, to.getStepZ() * 0.5);
        double[] cum = new double[pts.length];
        for (int i = 1; i < pts.length; i++) cum[i] = cum[i - 1] + pts[i].distanceTo(pts[i - 1]);
        if (BATCHES.size() >= MAX) BATCHES.remove(0);
        BATCHES.add(new Batch(pts, cum, p.item(), mc.level.getGameTime(), p.ticks()));
    }

    private static Vec3 at(Batch b, double t) {
        double d = Math.max(0, Math.min(1, t)) * b.cum()[b.cum().length - 1];
        for (int i = 1; i < b.pts().length; i++) {
            if (d <= b.cum()[i] || i == b.pts().length - 1) {
                double seg = b.cum()[i] - b.cum()[i - 1], f = seg <= 1e-6 ? 1 : (d - b.cum()[i - 1]) / seg;
                return b.pts()[i - 1].lerp(b.pts()[i], Math.max(0, Math.min(1, f)));
            }
        }
        return b.pts()[0];
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS || BATCHES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { BATCHES.clear(); return; }
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Iterator<Batch> it = BATCHES.iterator();
        while (it.hasNext()) {
            Batch b = it.next();
            double t = (now - b.start()) / b.ticks();
            if (t >= 1.0 || t < -0.5) { it.remove(); continue; }
            Vec3 p = at(b, t);
            if (p.distanceToSqr(cam) > 64 * 64) continue;
            ps.pushPose();
            ps.translate(p.x - cam.x, p.y - 0.12 - cam.y, p.z - cam.z);
            ps.scale(0.55F, 0.55F, 0.55F);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) ((now * 4) % 360)));
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p));
            mc.getItemRenderer().renderStatic(b.item(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
            ps.popPose();
        }
        buf.endBatch();
    }
}
