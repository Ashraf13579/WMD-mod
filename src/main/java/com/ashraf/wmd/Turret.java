package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Turret {

    private Turret() {}

    private static final class Gun {
        TurretSeatEntity seat; BlockPos controller; int slot; boolean trigger; long last; int barrel; float syncYaw = 999.0F, syncEl; long syncAt;
    }

    private static final Map<UUID, Gun> GUNS = new HashMap<>();
    private record Bolt(long id, int slot, UUID shooter, Vec3[] pos, Vec3 dir, long t0) {}
    private static final List<Bolt> BOLTS = new ArrayList<>();

    private static final Map<Long, Integer> INTERCEPT = new HashMap<>();

    private static long interceptKey(int slot, long end) { return ((long) slot << 42) ^ (end & ((1L << 42) - 1)); }
    private static long nextBolt = 1;

    static void clearStatics() { GUNS.clear(); BOLTS.clear(); INTERCEPT.clear(); nextBolt = 1; }

    static Station.Module module(MinecraftServer server, int slot) {
        for (Station.Module m : Station.modules(server, slot)) if (Station.TURRET.equals(m.type()) && m.built()) return m;
        return null;
    }

    static Vec3 centre(Station.Module turret) {
        BlockPos c = Station.world(turret, 0, 6, 0);
        return new Vec3(c.getX() + 0.5, c.getY() - 64.0 / 16.0, c.getZ() + 0.5);
    }

    static float[] aim(Vec3 look) {
        float yaw = (float) Math.toDegrees(Math.atan2(-look.z, look.x));
        float el = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, look.y))));
        return new float[] { yaw, Math.max(-Balance.TURRET_PITCH_DOWN, Math.min(Balance.TURRET_PITCH_UP, el)) };
    }

    static Vec3 dir(float yaw, float el) {
        double y = Math.toRadians(yaw), e = Math.toRadians(el);
        return new Vec3(Math.cos(e) * Math.cos(y), Math.sin(e), -Math.cos(e) * Math.sin(y));
    }

    static void man(ServerPlayer p, BlockPos hatch) {
        if (!(p.level() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;
        int slot = Orbit.slotAt(hatch.getX() + 0.5, hatch.getZ() + 0.5);
        Station.Module t = module(p.server, slot);
        if (t == null) { p.displayClientMessage(Component.translatable("message.wmd.turret.none"), true); return; }
        if (Station.wrecked(p.server, t)) { p.displayClientMessage(Component.translatable("message.wmd.turret.wrecked").withStyle(ChatFormatting.RED), true); return; }
        for (Gun g : GUNS.values()) if (g.slot == slot && g.seat != null && g.seat.isVehicle()) { p.displayClientMessage(Component.translatable("message.wmd.turret.taken"), true); return; }
        Vec3 c = centre(t);
        TurretSeatEntity s = new TurretSeatEntity(Wmd.TURRET_SEAT.get(), orbit);
        s.setUp(Station.controller(t), hatch);
        s.setPos(c.x, c.y - 1.52, c.z);
        orbit.addFreshEntity(s);
        p.startRiding(s, true);
        Gun g = new Gun();
        g.seat = s; g.controller = Station.controller(t); g.slot = slot;
        GUNS.put(p.getUUID(), g);
        orbit.playSound(null, hatch, WmdSounds.TU_HATCH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        p.displayClientMessage(Component.translatable("message.wmd.turret.manned").withStyle(ChatFormatting.AQUA), true);
    }

    static void trigger(ServerPlayer p, boolean down) { Gun g = GUNS.get(p.getUUID()); if (g != null) g.trigger = down; }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Tick {
        private Tick() {}

        @SubscribeEvent
        static void onTick(LevelTickEvent.Post event) {
            if (!(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;
            MinecraftServer server = orbit.getServer();
            long now = orbit.getGameTime();
            if (now % 20 == 0) for (ServerPlayer o : orbit.players())
                if (o.getVehicle() instanceof TurretSeatEntity s && !GUNS.containsKey(o.getUUID())) {
                    Gun g = new Gun();
                    g.seat = s; g.controller = s.controller(); g.slot = Orbit.slotAt(s.controller().getX() + 0.5, s.controller().getZ() + 0.5);
                    GUNS.put(o.getUUID(), g);
                }
            if (GUNS.isEmpty() && BOLTS.isEmpty()) return;
            for (Iterator<Map.Entry<UUID, Gun>> it = GUNS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Gun> e = it.next();
                Gun g = e.getValue();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null || g.seat == null || g.seat.isRemoved() || p.getVehicle() != g.seat) { it.remove(); continue; }
                Station.Module t = module(server, g.slot);
                if (t == null || Station.wrecked(server, t)) { p.stopRiding(); it.remove(); continue; }
                float[] a = aim(p.getLookAngle());
                if (now - g.syncAt >= Balance.TURRET_SYNC_TICKS && (Math.abs(a[0] - g.syncYaw) > 0.5F || Math.abs(a[1] - g.syncEl) > 0.5F)) {
                    g.syncYaw = a[0]; g.syncEl = a[1]; g.syncAt = now;
                    if (orbit.getBlockEntity(g.controller) instanceof StationModuleBlockEntity be) be.setAim(a[0], a[1]);
                }
                if (now % 20 == 0) hud(p, server, g.slot);
                if (g.trigger && now - g.last >= Balance.TURRET_SHOT_EVERY) fire(orbit, p, g, t, a, now);
            }
            bolts(orbit, now);
        }
    }

    private static void hud(ServerPlayer p, MinecraftServer server, int slot) {
        long buf = Station.Data.get(server).energy.getOrDefault(slot, Balance.STATION_BUFFER / 2);
        p.displayClientMessage(Component.translatable("message.wmd.turret.hud", String.format("%.1f", buf / 1_000_000.0), Balance.TURRET_SHOT_FE / 1000,
                (int) (buf / Balance.TURRET_SHOT_FE)).withStyle(buf < Balance.TURRET_SHOT_FE * 40L ? ChatFormatting.RED : ChatFormatting.AQUA), true);
    }

    private static void fire(ServerLevel orbit, ServerPlayer p, Gun g, Station.Module t, float[] a, long now) {
        MinecraftServer server = orbit.getServer();
        Station.Data d = Station.Data.get(server);
        long buf = d.energy.getOrDefault(g.slot, Balance.STATION_BUFFER / 2);
        if (buf < Balance.TURRET_SHOT_FE || !StationLife.powered(g.slot)) {
            if (now - g.last >= 40) { p.displayClientMessage(Component.translatable("message.wmd.turret.no_power").withStyle(ChatFormatting.RED), true); g.last = now; }
            return;
        }
        d.energy.put(g.slot, buf - Balance.TURRET_SHOT_FE);
        d.setDirty();
        g.last = now;
        Vec3 dir = dir(a[0], a[1]), side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : side.normalize();
        g.barrel ^= 1;
        Vec3 from = centre(t).add(dir.scale(Balance.TURRET_MUZZLE)).add(side.scale((g.barrel == 0 ? 1.0 : -1.0) * 19.0 / 16.0));
        Bolt b = new Bolt(nextBolt++, g.slot, p.getUUID(), new Vec3[] { from }, dir, now);
        BOLTS.add(b);
        orbit.playSound(null, BlockPos.containing(from), WmdSounds.TU_SHOT.get(), SoundSource.BLOCKS, 1.6F, 0.95F + orbit.random.nextFloat() * 0.1F);
        show(orbit, b, from);
        if (a[1] <= -Balance.TURRET_DOWN_DEG)
            for (SensorArray.Threat th : SensorArray.threats(g.slot, now))
                if ("asat".equals(th.kind()) && th.end() >= now && th.end() - now <= Balance.TURRET_INTERCEPT_WINDOW) { INTERCEPT.merge(interceptKey(g.slot, th.end()), 1, Integer::sum); break; }
    }

    private static void show(ServerLevel orbit, Bolt b, Vec3 from) {
        MinecraftServer server = orbit.getServer();
        for (ServerPlayer o : orbit.players()) {
            int f = Rendezvous.frameOf(o.getX());
            Vec3 at = f == b.slot() ? from : Rendezvous.close(server, f, b.slot()) ? Rendezvous.inFrame(server, f, b.slot(), from) : null;
            if (at == null || o.position().distanceToSqr(at) > 600.0 * 600.0) continue;
            PacketDistributor.sendToPlayer(o, new Shot(b.id(), (float) at.x, (float) at.y, (float) at.z, (float) b.dir().x, (float) b.dir().y, (float) b.dir().z));
        }
    }

    private static void bolts(ServerLevel orbit, long now) {
        MinecraftServer server = orbit.getServer();
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            Vec3 p0 = b.pos()[0], p1 = p0.add(b.dir().scale(Balance.TURRET_BOLT_SPEED));
            b.pos()[0] = p1;
            boolean gone = (now - b.t0()) * Balance.TURRET_BOLT_SPEED > Balance.TURRET_RANGE;
            Vec3 best = null; double bd = Double.MAX_VALUE;
            Station.Module hitM = null; int hitSlot = -1; ServerPlayer hitP = null;
            for (int s : Rendezvous.closeSlots(server, b.slot())) {
                Vec3 q0 = Rendezvous.fromFrame(server, b.slot(), s, p0), q1 = Rendezvous.fromFrame(server, b.slot(), s, p1);
                for (Station.Module m : Station.modules(server, s)) {
                    if (!m.built() || Station.wrecked(server, m)) continue;
                    var c = box(m).clip(q0, q1);
                    if (c.isPresent() && c.get().distanceToSqr(q0) < bd) { bd = c.get().distanceToSqr(q0); best = c.get(); hitM = m; hitSlot = s; hitP = null; }
                }
                for (ServerPlayer o : orbit.players())
                    if (Rendezvous.frameOf(o.getX()) == s && !o.isSpectator()) {
                        var c = o.getBoundingBox().inflate(0.3).clip(q0, q1);
                        if (c.isPresent() && c.get().distanceToSqr(q0) < bd) { bd = c.get().distanceToSqr(q0); best = c.get(); hitP = o; hitM = null; hitSlot = s; }
                    }
            }
            for (ServerPlayer o : orbit.players())
                if (Rendezvous.frameOf(o.getX()) == b.slot() && !o.isSpectator() && !o.getUUID().equals(b.shooter()) && !(o.getVehicle() instanceof TurretSeatEntity)) {
                    var c = o.getBoundingBox().inflate(0.3).clip(p0, p1);
                    if (c.isPresent() && c.get().distanceToSqr(p0) < bd) { bd = c.get().distanceToSqr(p0); best = c.get(); hitP = o; hitM = null; hitSlot = b.slot(); }
                }
            if (best == null) { if (gone) it.remove(); continue; }
            it.remove();
            ServerPlayer shooter = server.getPlayerList().getPlayer(b.shooter());
            Vec3 here = hitSlot == b.slot() ? best : Rendezvous.inFrame(server, b.slot(), hitSlot, best);
            if (hitM != null) {
                int after = StationLife.damage(orbit, hitSlot, hitM, Balance.TURRET_DMG);
                if (shooter != null && after <= 0) shooter.sendSystemMessage(Component.translatable("message.wmd.turret.killed",
                        Component.translatable("station.wmd.module." + hitM.type()), Boarding.ownerName(server, hitSlot)).withStyle(ChatFormatting.GREEN));
            } else if (hitP != null) {
                hitP.hurt(orbit.damageSources().indirectMagic(shooter, shooter), Balance.TURRET_PLAYER_DMG);
            }
            VacuumBurst.send(orbit, best, 1.2);
            if (hitSlot != b.slot()) VacuumBurst.send(orbit, here, 1.2);
            orbit.playSound(null, BlockPos.containing(best), WmdSounds.TU_HIT.get(), SoundSource.BLOCKS, 1.4F, 1.0F);
            Hit h = new Hit(b.id());
            for (ServerPlayer o : orbit.players()) if (o.position().distanceToSqr(here) < 600.0 * 600.0 || o.position().distanceToSqr(best) < 600.0 * 600.0) PacketDistributor.sendToPlayer(o, h);
        }
    }

    static AABB box(Station.Module m) {
        BlockPos a, b;
        if (Station.CORE.equals(m.type())) { a = Station.world(m, -4, 0, -4); b = Station.world(m, 4, 9, 4); }
        else if (Station.tube(m.type())) { int h = Station.half(m.type()); a = Station.world(m, 0, 0, -h); b = Station.world(m, Station.len(m.type()), 2 * h + 1, h); }
        else if (Station.TURRET.equals(m.type())) { a = Station.world(m, -2, 0, -2); b = Station.world(m, 2, 5, 2); }
        else { int[] q = Station.boomBox(m.type()); a = Station.world(m, q[0], q[2], q[4]); b = Station.world(m, q[1] + 1, q[3] + 1, q[5]); }
        return new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()) + 1);
    }

    static boolean intercepted(MinecraftServer server, int slot) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit == null ? 0L : orbit.getGameTime();
        int n = 0;
        for (java.util.Iterator<Map.Entry<Long, Integer>> it = INTERCEPT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Integer> e = it.next();
            long end = e.getKey() & ((1L << 42) - 1);
            boolean mine = (int) (e.getKey() >>> 42) == slot && Math.abs(end - (now & ((1L << 42) - 1))) <= 40;
            if (mine) n = Math.max(n, e.getValue());
            if (mine || end < (now & ((1L << 42) - 1)) - 1200) it.remove();
        }
        return n >= Balance.TURRET_INTERCEPT_HITS;
    }

    public static class Hatch extends Block {
        public static final MapCodec<Hatch> CODEC = simpleCodec(Hatch::new);
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1.5, 15);

        public Hatch(Properties p) { super(p); registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)); }

        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
        @Override protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

        @Override
        protected InteractionResult useWithoutItem(BlockState s, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) man(sp, pos);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    public record Trigger(boolean down) implements CustomPacketPayload {
        public static final Type<Trigger> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turret_trigger"));
        public static final StreamCodec<FriendlyByteBuf, Trigger> CODEC = StreamCodec.of((b, p) -> b.writeBoolean(p.down()), b -> new Trigger(b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Shot(long id, float x, float y, float z, float dx, float dy, float dz) implements CustomPacketPayload {
        public static final Type<Shot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turret_shot"));
        public static final StreamCodec<FriendlyByteBuf, Shot> CODEC = StreamCodec.of((b, p) -> {
            b.writeLong(p.id()); b.writeFloat(p.x()); b.writeFloat(p.y()); b.writeFloat(p.z()); b.writeFloat(p.dx()); b.writeFloat(p.dy()); b.writeFloat(p.dz());
        }, b -> new Shot(b.readLong(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Hit(long id) implements CustomPacketPayload {
        public static final Type<Hit> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turret_hit"));
        public static final StreamCodec<FriendlyByteBuf, Hit> CODEC = StreamCodec.of((b, p) -> b.writeLong(p.id()), b -> new Hit(b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<CustomPacketPayload> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("1");
            r.playToServer(Trigger.TYPE, Trigger.CODEC, (p, ctx) -> ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) trigger(sp, p.down()); }));
            r.playToClient(Shot.TYPE, Shot.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
            r.playToClient(Hit.TYPE, Hit.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
