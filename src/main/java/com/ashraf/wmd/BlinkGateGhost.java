package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class BlinkGateGhost {

    private BlinkGateGhost() {}

    private static final int SHOW_TICKS = 200, RANGE = 64;
    private record Box(AABB box, boolean ok, List<BlockPos> blocked) {}
    private static final Map<Long, Long> SHOWN = Statics.client(new HashMap<>());
    private static final Map<Long, List<Box>> CACHE = Statics.client(new HashMap<>());
    private static final Map<Long, Long> CACHED_AT = Statics.client(new HashMap<>());
    private static final Map<Long, Long> UNBUILT = Statics.client(new HashMap<>());

    static void show(BlockPos pos) {
        Level level = Minecraft.getInstance().level;
        if (level != null) { SHOWN.put(pos.asLong(), level.getGameTime()); CACHED_AT.remove(pos.asLong()); }
    }

    static void unbuilt(BlockPos pos, long now) {
        if (UNBUILT.size() > 64) UNBUILT.clear();
        UNBUILT.put(pos.asLong(), now);
    }

    @SubscribeEvent
    static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null || UNBUILT.isEmpty()) return;
        long now = level.getGameTime();
        boolean holding = p.getMainHandItem().is(BlinkGateBlocks.CORNER_ITEM.get()) || p.getMainHandItem().is(BlinkGateBlocks.SEGMENT_ITEM.get())
                || p.getOffhandItem().is(BlinkGateBlocks.CORNER_ITEM.get()) || p.getOffhandItem().is(BlinkGateBlocks.SEGMENT_ITEM.get());
        Vec3 c = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        UNBUILT.values().removeIf(t -> now - t > 5);
        for (long key : UNBUILT.keySet()) {
            BlockPos pos = BlockPos.of(key);
            Long shown = SHOWN.get(key);
            if (!holding && (shown == null || now - shown > SHOW_TICKS)) continue;
            if (pos.distSqr(p.blockPosition()) > RANGE * RANGE || !(level.getBlockEntity(pos) instanceof BlinkGateCoreBlockEntity core) || core.formed()) continue;
            List<Box> boxes = CACHE.get(key);
            Long at = CACHED_AT.get(key);
            if (boxes == null || at == null || now - at >= 10) {
                boxes = slots(level, core);
                CACHE.put(key, boxes);
                CACHED_AT.put(key, now);
            }
            if (vc == null) vc = buf.getBuffer(RenderType.lines());
            for (Box b : boxes) {
                float r = b.ok() ? 0.37F : 1.0F, g = b.ok() ? 0.69F : 0.3F, bl = b.ok() ? 1.0F : 0.25F;
                LevelRenderer.renderLineBox(ps, vc, b.box().inflate(0.01).move(-c.x, -c.y, -c.z), r, g, bl, 0.9F);
                for (BlockPos q : b.blocked()) LevelRenderer.renderLineBox(ps, vc, new AABB(q).inflate(0.02).move(-c.x, -c.y, -c.z), 1.0F, 0.2F, 0.15F, 1.0F);
            }
        }
        if (vc != null) buf.endBatch(RenderType.lines());
    }

    private static List<Box> slots(Level level, BlinkGateCoreBlockEntity core) {
        List<Box> out = new ArrayList<>();
        for (BlinkGateCoreBlockEntity.Slot s : BlinkGateCoreBlockEntity.slots(core.size())) {
            BlinkGateCoreBlockEntity.Place pl = core.place(s);
            if (pl == null || pl.cells().isEmpty()) continue;
            BlockPos lo = pl.cells().get(0), hi = lo;
            List<BlockPos> blocked = new ArrayList<>();
            for (BlockPos q : pl.cells()) {
                lo = new BlockPos(Math.min(lo.getX(), q.getX()), Math.min(lo.getY(), q.getY()), Math.min(lo.getZ(), q.getZ()));
                hi = new BlockPos(Math.max(hi.getX(), q.getX()), Math.max(hi.getY(), q.getY()), Math.max(hi.getZ(), q.getZ()));
                if (!level.getBlockState(q).canBeReplaced() && !(level.getBlockEntity(q) instanceof BlinkGatePieceBlockEntity)
                        && !(level.getBlockEntity(q) instanceof MachinePartBlockEntity mp && mp.getController() != null
                             && level.getBlockEntity(mp.getController()) instanceof BlinkGatePieceBlockEntity)) blocked.add(q);
            }
            out.add(new Box(AABB.encapsulatingFullBlocks(lo, hi), blocked.isEmpty(), blocked));
        }
        return out;
    }
}
