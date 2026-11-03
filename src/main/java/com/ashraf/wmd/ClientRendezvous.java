package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientRendezvous {

    private ClientRendezvous() {}

    private static final double FAR = 400.0;
    private static final Map<Integer, StationModuleBlockEntity> FAKE = new HashMap<>();
    private static final Map<Integer, CompoundTag> LOOK = new HashMap<>();
    private static Rendezvous.Ghosts pruned;

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        Rendezvous.Ghosts g = Rendezvous.latest;
        if (g == null) return;
        if (mc.level == null || mc.player == null || !Orbit.isOrbit(mc.level) || System.currentTimeMillis() - Rendezvous.latestAt > 3_000L) {
            Rendezvous.latest = null; FAKE.clear(); LOOK.clear(); pruned = null;
            return;
        }
        if (Rendezvous.frameOf(mc.player.getX()) != g.frame()) return;
        if (pruned != g) {
            Set<Integer> keep = new HashSet<>();
            for (Rendezvous.Ghost h : g.list()) for (Rendezvous.GMod m : h.mods()) keep.add(m.id());
            FAKE.keySet().retainAll(keep); LOOK.keySet().retainAll(keep);
            pruned = g;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double el = mc.level.getGameTime() + pt - g.at();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockPos me = Orbit.slotCentre(g.frame());
        for (Rendezvous.Ghost h : g.list()) {
            BlockPos it = Orbit.slotCentre(h.slot());
            double ox = me.getX() - it.getX() + h.dx() + h.vx() * el, oz = me.getZ() - it.getZ() + h.dz() + h.vz() * el;
            double far = new Vec3(it.getX() + ox - cam.x, it.getY() - cam.y, it.getZ() + oz - cam.z).length(), k = far > FAR ? FAR / far : 1.0;
            for (Rendezvous.GMod m : h.mods()) {
                StationModuleBlockEntity be = fake(mc, m);
                BlockEntityRenderer<StationModuleBlockEntity> r = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                if (r == null) continue;
                BlockPos p = be.getBlockPos();
                ps.pushPose();
                ps.translate((p.getX() + ox - cam.x) * k, (p.getY() - cam.y) * k, (p.getZ() + oz - cam.z) * k);
                if (k < 1.0) ps.scale((float) k, (float) k, (float) k);
                r.render(be, pt, ps, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
        }
        buffers.endBatch();
    }

    private static StationModuleBlockEntity fake(Minecraft mc, Rendezvous.GMod m) {
        BlockPos pos = BlockPos.of(m.pos());
        StationModuleBlockEntity be = FAKE.get(m.id());
        if (be == null || !be.getBlockPos().equals(pos)) {
            be = new StationModuleBlockEntity(pos, Wmd.STATION_MODULE.get().defaultBlockState());
            be.setLevel(mc.level);
            FAKE.put(m.id(), be);
            LOOK.remove(m.id());
        }
        if (!m.tag().equals(LOOK.get(m.id()))) {
            be.loadWithComponents(m.tag(), mc.level.registryAccess());
            LOOK.put(m.id(), m.tag());
        }
        return be;
    }
}
