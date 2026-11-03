package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TsarEvent {

    private TsarEvent() {}

    private static final class Ev {
        UUID owner; String ownerName = "?"; int tx, tz, ty; long t0; boolean cut, soon, warned; double ramp = Balance.RG_HOLD_RAMP_START; int inAir;
    }

    private record Shot(UUID bomb, UUID owner, String name, Vec3 to, long arrive) {}

    private static final Map<UUID, Ev> EVENTS = new HashMap<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final Map<String, Long> READY = new HashMap<>();

    static void clearStatics() { EVENTS.clear(); SHOTS.clear(); READY.clear(); }

    static long key(BombEntity b) { return 0x54534152L << 32 | (b.getId() & 0xFFFFFFFFL); }

    static void track(ServerLevel sl, BombEntity b, Vec3 pos, double ground, double h) {
        MinecraftServer server = sl.getServer();
        long now = sl.getGameTime();
        Ev e = EVENTS.get(b.getUUID());
        if (e == null) {
            e = new Ev();
            e.owner = b.owner(); e.tx = (int) Math.floor(pos.x); e.tz = (int) Math.floor(pos.z); e.ty = (int) ground; e.t0 = now;
            ServerPlayer op = e.owner == null ? null : server.getPlayerList().getPlayer(e.owner);
            e.ownerName = op != null ? op.getGameProfile().getName() : e.owner == null ? "?" : CcIntel.name(server, e.owner);
            EVENTS.put(b.getUUID(), e);
            Wmd.LOGGER.info("[tsar] chutes open over {} {} ({} blocks up), {} s to the burst", e.tx, e.tz, Math.round(h), Math.round((h - Balance.TSAR_BURST_H) / Balance.TSAR_CHUTE_SPEED / 20.0));
        }
        long left = (long) Math.max(0.0, (h - Balance.TSAR_BURST_H) / Balance.TSAR_CHUTE_SPEED);
        if (!e.warned) {
            e.warned = true;
            Component line = Component.translatable("message.wmd.tsar.incoming", e.tx, e.tz, Math.max(1, left / 20)).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            for (ServerPlayer p : sl.players()) if (Math.hypot(p.getX() - e.tx, p.getZ() - e.tz) < Balance.TSAR_SEEN_R) p.sendSystemMessage(line);
            MusicTriggersHook.fire(sl, "wmd:tsar_incoming", new BlockPos(e.tx, e.ty, e.tz));
        }
        if (!e.soon && left <= MusicTriggersHook.IMPACT_SOON_TICKS) { e.soon = true; MusicTriggersHook.fire(sl, "wmd:tsar_burst_soon", new BlockPos(e.tx, e.ty, e.tz)); }
        if (!TargetSnapshot.has(key(b)) && (now - e.t0) % 200 == 0)
            TargetSnapshot.start(sl, key(b), e.tx, e.tz, null, Balance.TSAR_SNAP_R, Balance.TSAR_SNAP_FAR_R);
        if ((now - e.t0) % Balance.RG_HOLD_RAMP_TICKS == 0) {
            double full = WarheadFx.holdRadius(WarheadType.TSAR.tier, WarheadType.TSAR);
            double step = Math.max(Balance.RG_HOLD_RAMP_STEP, (full - e.ramp) / Math.max(1.0, left / (double) Balance.RG_HOLD_RAMP_TICKS));
            double mspt = server.getAverageTickTimeNanos() / 1.0E6;
            if (e.ramp < full && mspt < Balance.RAMP_MSPT && loaded(sl, e.tx, e.tz, e.ramp * 0.92) >= Balance.RAMP_LOADED) {
                double cap = Math.sqrt(e.ramp * e.ramp + Balance.RAMP_STEP_CHUNKS * 256.0 / Math.PI) - e.ramp;
                e.ramp = Math.min(full, e.ramp + Math.min(step, cap));
            }
            FlightManager.get(sl).holdForTest(sl, e.tx, e.tz, e.ramp);
        }
        if (!e.cut && left <= Balance.TSAR_CUT_LEAD) {
            e.cut = true;
            PacketDistributor.sendToPlayersInDimension(sl, new CutscenePayload(CutscenePayload.KIND_TSAR, (float) pos.x, (float) pos.y, (float) pos.z, e.tx + 0.5F, e.ty, e.tz + 0.5F,
                    (int) left, (int) left + Balance.TSAR_CUT_AFTER, ""));
        }
        if ((now - e.t0) % Balance.TSAR_INT_EVERY == 0) defences(sl, b, e, pos, now);
    }

    private static double loaded(ServerLevel sl, int x, int z, double r) {
        int n = 0;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            if (sl.hasChunk((int) Math.floor(x + Math.cos(a) * r) >> 4, (int) Math.floor(z + Math.sin(a) * r) >> 4)) n++;
        }
        return n / 24.0;
    }

    private static int tierOf(InterceptorSite s) {
        if (s instanceof InterceptorLauncherBlockEntity) return 1;
        if (s instanceof InterceptorBatteryBlockEntity) return 2;
        if (s instanceof InterceptorSiloBlockEntity) return 3;
        if (s instanceof InterceptorSiloT4BlockEntity) return 4;
        return 0;
    }

    private static void defences(ServerLevel sl, BombEntity b, Ev e, Vec3 pos, long now) {
        int air = 0;
        for (Shot s : SHOTS) if (s.bomb().equals(b.getUUID())) air++;
        for (InterceptorSite site : CommandCenter.allSites()) {
            if (air >= 2) return;
            if (!(site instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != sl || EmpField.jammed(sl, be.getBlockPos())) continue;
            UUID owner = site.getOwner();
            int tier = tierOf(site);
            if (owner == null || tier == 0 || owner.equals(e.owner) || site.cooldownTicks() > 0 || site.energy() < Balance.INT_SHOT_FE) continue;
            String key = sl.dimension().location() + "|" + be.getBlockPos().asLong();
            Long ready = READY.get(key);
            if (ready != null && now < ready) continue;
            IItemHandler mag = site.magazineHandler();
            int slot = -1;
            for (int i = 0; i < site.slotCount() && i < mag.getSlots(); i++) if (!mag.getStackInSlot(i).isEmpty()) { slot = i; break; }
            if (slot < 0) continue;
            Vec3 from = site instanceof InterceptorSiloBlockEntity s3m ? s3m.mouth() : site instanceof InterceptorSiloT4BlockEntity s4m ? s4m.mouth() : Vec3.atCenterOf(be.getBlockPos()).add(0.0, 3.0, 0.0);
            if (from.distanceTo(pos) > Interceptors.rangeOf(tier, owner)) continue;
            if (site instanceof InterceptorSiloBlockEntity s3) { s3.wakeForShip(now); if (s3.lid() < 0.99F) continue; }
            if (site instanceof InterceptorSiloT4BlockEntity s4) { s4.wakeForShip(now); if (s4.lid() < 0.99F || Math.abs(LauncherGeometry.wrap(slot * 90.0 - s4.drum())) >= 1.0) continue; }
            ItemStack out = mag.extractItem(slot, 1, false);
            if (out.isEmpty()) continue;
            site.shipShot(Balance.INT_SHOT_FE, Balance.INT_RELOAD_TICKS);
            READY.put(key, now + Balance.INT_RELOAD_TICKS);
            int rise = tier == 3 ? Balance.SILO3_RISE_TICKS : tier == 4 ? Balance.SILO4_RISE_TICKS : 0;
            double speed = Balance.INT_SPEED[tier - 1];
            int duration = Math.max(12, (int) Math.ceil(from.distanceTo(pos) / speed));
            Vec3 to = pos.add(0.0, -Balance.TSAR_CHUTE_SPEED * (rise + duration), 0.0);
            long id = FlightManager.get(sl).nextShotId();
            ServerPlayer op = sl.getServer().getPlayerList().getPlayer(owner);
            SHOTS.add(new Shot(b.getUUID(), owner, op != null ? op.getGameProfile().getName() : CcIntel.name(sl.getServer(), owner), to, now + rise + duration));
            PacketDistributor.sendToPlayersNear(sl, null, from.x, from.y, from.z, 4000, new InterceptPayload(id, from.x, from.y, from.z, to.x, to.y, to.z, now + rise, duration, tier == 1 ? 0 : tier == 2 ? 3 : tier == 3 ? 1 : 2));
            Wmd.LOGGER.info("[tsar] tier {} interceptor from {} at the Tsar ({} blocks)", tier, be.getBlockPos().toShortString(), (int) from.distanceTo(to));
            air++;
        }
    }

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Tick {
        private Tick() {}

        @SubscribeEvent
        static void onTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
            if (SHOTS.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
            long now = sl.getGameTime();
            for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
                Shot s = it.next();
                if (now < s.arrive()) continue;
                if (!(sl.getEntity(s.bomb()) instanceof BombEntity b) || b.isRemoved()) { it.remove(); continue; }
                it.remove();
                boolean kill = b.position().distanceTo(s.to()) < 12.0 && sl.random.nextInt(100) < Balance.TSAR_INT_HIT_PCT;
                Vec3 at = kill ? b.position() : s.to();
                PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 6000, new InterceptFxPayload(at.x, at.y, at.z, kill, 0));
                if (kill) b.tsarBreak(sl, s.owner(), s.name());
            }
        }
    }

    static void burst(ServerLevel sl, BombEntity b, Vec3 ground, double h) {
        Ev e = EVENTS.remove(b.getUUID());
        TargetSnapshot.stopIn(key(b), sl, Balance.TSAR_CUT_AFTER + 200);
        Flash f = new Flash(ground.x, ground.y + h, ground.z, sl.getGameTime());
        for (ServerPlayer p : sl.players()) PacketDistributor.sendToPlayer(p, f);
        double r = Missiles.craterRadius(WarheadType.TSAR.tier) * WarheadType.TSAR.crater;
        StrikeLog.report(sl, e != null ? e.owner : b.owner(), "log.wmd.strike.tsar", (int) ground.x, (int) ground.z, r, r * WarheadType.TSAR.depth * Balance.AIRBURST_DEPTH);
        MusicTriggersHook.fire(sl, "wmd:tsar_burst", BlockPos.containing(ground));
        Component line = Component.translatable("message.wmd.tsar.seen", (int) ground.x, (int) ground.z).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer p : sl.players()) if (Math.hypot(p.getX() - ground.x, p.getZ() - ground.z) < Balance.TSAR_SEEN_R) p.sendSystemMessage(line);
    }

    static void broken(ServerLevel sl, BombEntity b) {
        if (EVENTS.remove(b.getUUID()) != null) TargetSnapshot.stop(key(b));
        SHOTS.removeIf(s -> s.bomb().equals(b.getUUID()));
    }

    static void gone(ServerLevel sl, BombEntity b) {
        if (EVENTS.remove(b.getUUID()) != null) TargetSnapshot.stop(key(b));
        SHOTS.removeIf(s -> s.bomb().equals(b.getUUID()));
    }

    public record Flash(double x, double y, double z, long t) implements CustomPacketPayload {
        public static final Type<Flash> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tsar_flash"));
        public static final StreamCodec<FriendlyByteBuf, Flash> CODEC = StreamCodec.of((buf, p) -> { buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeLong(p.t()); },
                buf -> new Flash(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static volatile Flash lastFlash;

    @EventBusSubscriber(modid = Wmd.MODID)
    public static final class Reg {
        private Reg() {}

        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(Flash.TYPE, Flash.CODEC, (p, ctx) -> ctx.enqueueWork(() -> lastFlash = p));
        }
    }
}
