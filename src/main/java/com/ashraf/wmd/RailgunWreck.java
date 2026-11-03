package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class RailgunWreck {

    private RailgunWreck() {}

    record Part(BlockState state, float w, float y0, float y1, boolean glow) {}

    static final List<Part> PARTS = List.of(
            new Part(Blocks.NETHERITE_BLOCK.defaultBlockState(), 4.0F, -6.0F, -2.0F, false),
            new Part(Blocks.NETHERITE_BLOCK.defaultBlockState(), 4.0F, -2.0F, 2.0F, false),
            new Part(Blocks.NETHERITE_BLOCK.defaultBlockState(), 4.0F, 2.0F, 6.0F, false),
            new Part(Blocks.NETHERITE_BLOCK.defaultBlockState(), 4.0F, 6.0F, 10.0F, false),
            new Part(Blocks.POLISHED_DEEPSLATE.defaultBlockState(), 3.2F, 10.0F, 11.2F, false),
            new Part(Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 4.4F, 8.6F, 9.3F, false),
            new Part(Blocks.MAGMA_BLOCK.defaultBlockState(), 4.5F, 0.4F, 1.4F, true),
            new Part(Blocks.MAGMA_BLOCK.defaultBlockState(), 4.4F, 3.0F, 3.7F, true),
            new Part(Blocks.MAGMA_BLOCK.defaultBlockState(), 4.3F, 5.6F, 6.1F, true));

    static Quaternionf orient(Vec3 dir) {
        Vector3f up = new Vector3f((float) -dir.x, (float) -dir.y, (float) -dir.z).normalize();
        if (up.y < 0.35F) { up.y = 0.35F; up.normalize(); }
        return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), up);
    }

    static double floor(double x, double z, double fallback, java.util.function.IntBinaryOperator h) {
        double sum = 0.0;
        int n = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int v = h.applyAsInt((int) Math.floor(x + Math.cos(a) * 9.0), (int) Math.floor(z + Math.sin(a) * 9.0));
            if (v != Integer.MIN_VALUE) { sum += v; n++; }
        }
        return n == 0 ? fallback : sum / n;
    }

    private record Pending(ServerLevel level, int x, int z, Vec3 dir, long due) {}
    private record Placed(ServerLevel level, UUID id, long until) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Placed> PLACED = new ArrayList<>();

    static void clearStatics() { PENDING.clear(); PLACED.clear(); }
    private static final String TAG = "wmd_rg_wreck", UNTIL = "wmd_rg_until:";

    static void schedule(ServerLevel sl, int x, int z, Vec3 dir) { PENDING.add(new Pending(sl, x, z, dir, sl.getGameTime() + Balance.RG_WRECK_DELAY)); }

    private static void place(Pending p) {
        ServerLevel sl = p.level();
        if (!sl.hasChunk(p.x() >> 4, p.z() >> 4)) return;
        double fy = floor(p.x() + 0.5, p.z() + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.x(), p.z()),
                (bx, bz) -> sl.hasChunk(bx >> 4, bz >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) : Integer.MIN_VALUE);
        Quaternionf q = orient(p.dir());
        long until = sl.getGameTime() + Balance.RG_WRECK_TICKS;
        for (Part part : PARTS) {
            Display.BlockDisplay e = EntityType.BLOCK_DISPLAY.create(sl);
            if (e == null) continue;
            Vector3f off = q.transform(new Vector3f(-part.w() / 2.0F, part.y0(), -part.w() / 2.0F), new Vector3f());
            Transformation tr = new Transformation(off, new Quaternionf(q), new Vector3f(part.w(), part.y1() - part.y0(), part.w()), new Quaternionf());
            CompoundTag t = new CompoundTag();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(p.x() + 0.5)); pos.add(DoubleTag.valueOf(fy)); pos.add(DoubleTag.valueOf(p.z() + 0.5));
            t.put("Pos", pos);
            ListTag rot = new ListTag();
            rot.add(FloatTag.valueOf(0.0F)); rot.add(FloatTag.valueOf(0.0F));
            t.put("Rotation", rot);
            t.put("block_state", NbtUtils.writeBlockState(part.state()));
            t.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, tr).getOrThrow());
            t.putFloat("view_range", 4.0F);
            if (part.glow()) { CompoundTag b = new CompoundTag(); b.putInt("block", 15); b.putInt("sky", 15); t.put("brightness", b); }
            e.load(t);
            e.addTag(TAG);
            e.addTag(UNTIL + until);
            if (sl.addFreshEntity(e)) PLACED.add(new Placed(sl, e.getUUID(), until));
        }
        Wmd.LOGGER.info("[railgun] the spent slug stands in the crater at {} {} {} for {} s", p.x(), Math.round(fy), p.z(), Balance.RG_WRECK_TICKS / 20);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || PENDING.isEmpty() && PLACED.isEmpty()) return;
        long now = sl.getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.level() == sl && now >= p.due()) { it.remove(); place(p); }
        }
        if (now % 20 != 0) return;
        for (Iterator<Placed> it = PLACED.iterator(); it.hasNext(); ) {
            Placed p = it.next();
            if (p.level() != sl || now < p.until()) continue;
            it.remove();
            Entity e = sl.getEntity(p.id());
            if (e != null) e.discard();
        }
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl) || !event.getEntity().getTags().contains(TAG)) return;
        for (String s : event.getEntity().getTags())
            if (s.startsWith(UNTIL)) {
                long until;
                try { until = Long.parseLong(s.substring(UNTIL.length())); } catch (NumberFormatException ignored) { continue; }
                if (sl.getGameTime() >= until) event.setCanceled(true);
                else if (PLACED.stream().noneMatch(p -> p.id().equals(event.getEntity().getUUID()))) PLACED.add(new Placed(sl, event.getEntity().getUUID(), until));
            }
    }
}
