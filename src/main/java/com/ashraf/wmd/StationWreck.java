package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class StationWreck {

    private StationWreck() {}

    public static final int BLAST = 0, FALL = 1;

    public record Fx(int kind, double x, double y, double z, float ox, float oz, float size, BlockPos ctrl, int state, CompoundTag tag, long t0)
            implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_wreck"));
        public static final StreamCodec<FriendlyByteBuf, Fx> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarInt(p.kind()); b.writeDouble(p.x()); b.writeDouble(p.y()); b.writeDouble(p.z()); b.writeFloat(p.ox()); b.writeFloat(p.oz()); b.writeFloat(p.size());
            b.writeBlockPos(p.ctrl()); b.writeVarInt(p.state()); b.writeNbt(p.tag()); b.writeLong(p.t0());
        }, b -> new Fx(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBlockPos(), b.readVarInt(),
                b.readNbt(), b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static final java.util.concurrent.ConcurrentLinkedQueue<Fx> PENDING = Statics.client(new java.util.concurrent.ConcurrentLinkedQueue<>());

    private record Seq(int slot, int module, long t0, Vec3 mid) {}

    private static final List<Seq> SEQ = new ArrayList<>();

    private static final java.util.Map<Integer, Set<UUID>> LEFT = new java.util.HashMap<>();

    static void clearStatics() { SEQ.clear(); LEFT.clear(); }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { clearStatics(); }

    private static float[] outward(Station.Module core, Station.Module m) {
        if (core == null || core.id() == m.id()) return new float[] { 1.0F, 0.0F };
        BlockPos a = Station.middle(core), b = Station.middle(m);
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ(), l = Math.hypot(dx, dz);
        if (l < 0.5) { var n = m.facing().getNormal(); return new float[] { n.getX(), n.getZ() }; }
        return new float[] { (float) (dx / l), (float) (dz / l) };
    }

    private static float size(String type) {
        return switch (type) { case Station.CORE -> 5.0F; case Station.HABITAT, Station.BAY, Station.ROD -> 6.0F; case Station.TURRET -> 2.0F; default -> 3.5F; };
    }

    private static void send(ServerLevel level, int slot, Vec3 at, Fx fx) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, Balance.STATION_WRECK_FX_RANGE, fx);

        for (int v : Rendezvous.viewersOf(slot)) {
            Vec3 m = Rendezvous.inFrame(level.getServer(), v, slot, at);
            double dx = m.x - at.x, dz = m.z - at.z;
            BlockPos ctrl = fx.ctrl().offset((int) Math.round(dx), 0, (int) Math.round(dz));
            Fx moved = new Fx(fx.kind(), fx.x() + dx, fx.y(), fx.z() + dz, fx.ox(), fx.oz(), fx.size(), ctrl, fx.state(), fx.tag(), fx.t0());
            for (ServerPlayer p : level.players())
                if (Rendezvous.frameOf(p.getX()) == v && p.position().distanceTo(m) < Balance.STATION_WRECK_FX_RANGE) PacketDistributor.sendToPlayer(p, moved);
        }
    }

    static void start(ServerLevel level, int slot, Station.Module m) {
        BlockPos mp = Station.middle(m);
        Vec3 mid = new Vec3(mp.getX() + 0.5, mp.getY() + 0.5, mp.getZ() + 0.5);
        long now = level.getGameTime();
        float[] o = outward(Station.core(level.getServer(), slot), m);
        SEQ.add(new Seq(slot, m.id(), now, mid));
        send(level, slot, mid, new Fx(BLAST, mid.x, mid.y, mid.z, o[0], o[1], size(m.type()), Station.controller(m), 0, new CompoundTag(), now));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !Orbit.isOrbit(level)) return;
        long now = level.getGameTime();
        if (!SEQ.isEmpty()) wrecks(level, now);
        if (!LEFT.isEmpty() || !Station.Data.get(level.getServer()).reboost.isEmpty()) spacewalk(level, now);
    }

    private static void wrecks(ServerLevel level, long now) {
        for (Iterator<Seq> it = SEQ.iterator(); it.hasNext(); ) {
            Seq s = it.next();
            long el = now - s.t0();
            for (int k = 0; k < Balance.STATION_WRECK_BURSTS; k++)
                if (el == (long) k * Balance.STATION_WRECK_TICKS / Balance.STATION_WRECK_BURSTS)
                    level.playSound(null, s.mid().x, s.mid().y, s.mid().z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 6.0F, 0.7F + 0.15F * k);
            if (el < Balance.STATION_WRECK_TICKS) continue;
            it.remove();
            Station.Module m = null;
            for (Station.Module x : Station.modules(level.getServer(), s.slot())) if (x.id() == s.module()) m = x;
            if (m == null || !Station.breaksOff(m.type()) || !Station.wrecked(level.getServer(), m)) continue;
            BlockPos c = Station.controller(m);
            CompoundTag tag = level.getBlockEntity(c) instanceof StationModuleBlockEntity be ? be.getUpdateTag(level.registryAccess()) : new CompoundTag();
            int state = Block.getId(level.getBlockState(c));
            float[] o = outward(Station.core(level.getServer(), s.slot()), m);
            Station.detach(level, s.slot(), m);
            send(level, s.slot(), s.mid(), new Fx(FALL, s.mid().x, s.mid().y, s.mid().z, o[0], o[1], size(m.type()), c, state, tag, now));
            level.playSound(null, s.mid().x, s.mid().y, s.mid().z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F, 0.5F);
            StationLife.tell(level, s.slot(), Component.translatable("message.wmd.station.broke_off", StationLife.name(m)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
    }

    static boolean leftBehind(int slot, UUID id) { Set<UUID> s = LEFT.get(slot); return s != null && s.contains(id); }

    private static void spacewalk(ServerLevel level, long now) {
        Station.Data d = Station.Data.get(level.getServer());
        LEFT.keySet().removeIf(slot -> !d.reboost.containsKey(slot));
        for (var e : d.reboost.entrySet()) {
            long[] r = e.getValue();
            if (now < r[4] || now >= r[5]) continue;
            int slot = e.getKey();
            Station.Module core = Station.core(level.getServer(), slot);
            if (core == null) continue;
            double bx = r[2] - r[0], bz = r[3] - r[1], bl = Math.hypot(bx, bz);
            if (bl < 1.0) continue;
            bx /= bl; bz /= bl;
            BlockPos cm = Station.middle(core);
            for (ServerPlayer p : level.players()) {
                if (Orbit.slotAt(p.getX(), p.getZ()) != slot || p.isSpectator() || p.isCreative() || !p.isAlive() || p.isPassenger()) continue;
                Set<UUID> left = LEFT.computeIfAbsent(slot, k -> new HashSet<>());
                if (!left.contains(p.getUUID())) {
                    if (StationLife.inside(p) != null) continue;
                    left.add(p.getUUID());
                    p.sendSystemMessage(Component.translatable("message.wmd.reboost.left_behind").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }

                Vec3 v = p.getDeltaMovement().add(-bx * Balance.STATION_EVA_DRIFT, 0.0, -bz * Balance.STATION_EVA_DRIFT);
                if (v.horizontalDistance() > Balance.STATION_EVA_DRIFT_MAX) { double k = Balance.STATION_EVA_DRIFT_MAX / v.horizontalDistance(); v = new Vec3(v.x * k, v.y, v.z * k); }
                p.setDeltaMovement(v);
                p.hurtMarked = true;
                if (Math.hypot(p.getX() - (cm.getX() + 0.5), p.getZ() - (cm.getZ() + 0.5)) > Balance.STATION_EVA_LOST) {
                    p.sendSystemMessage(Component.translatable("message.wmd.reboost.lost").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                    p.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(OrbitSurvival.VACUUM)), Float.MAX_VALUE);
                    left.remove(p.getUUID());
                    Wmd.LOGGER.info("[station] {} was left behind by the burn of slot {} and lost in space", p.getGameProfile().getName(), slot);
                }
            }
        }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Fx.TYPE, Fx.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PENDING.add(p)));
        }
    }
}
