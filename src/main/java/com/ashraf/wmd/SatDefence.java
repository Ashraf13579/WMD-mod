package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SatDefence {

    private SatDefence() {}

    public static final String TYPE = "interceptor";
    public static final int MODE_ZONE = 0, MODE_ANY = 1;

    public static final int KILL_DELAY_TICKS = 60;

    private record Pending(long flightId, long at, int satId, UUID owner, String ownerName, boolean kill, String dim) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static boolean charged(MinecraftServer server, OrbitMath.Sat s, long nowMs) {
        long shot = SatDefenceData.get(server).shotMs(s.id());
        return shot <= 0L || nowMs >= shot + OrbitMath.periodMs() - OrbitMath.windowMs();
    }

    public static long rechargeIn(MinecraftServer server, OrbitMath.Sat s, long nowMs) {
        long shot = SatDefenceData.get(server).shotMs(s.id());
        return shot <= 0L ? 0L : Math.max(0L, shot + OrbitMath.periodMs() - OrbitMath.windowMs() - nowMs);
    }

    public static int effectiveMode(MinecraftServer server, UUID owner) {
        if (owner != null && CcDefcon.level(owner) <= 3) return MODE_ANY;
        if (owner == null || !CommandCenter.hasModule(owner, CcModule.INT_SAT_CONSOLE)) return MODE_ZONE;
        return SatDefenceData.get(server).mode(owner);
    }

    public static boolean inZone(Level level, UUID owner, double x, double z) {
        double r2 = (double) Balance.INT_SAT_ZONE * Balance.INT_SAT_ZONE;
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) {
            if (b.getLevel() != level) continue;
            Vec3 bw = SableBridge.worldPos(level, b.getBlockPos());
            double dx = bw.x - x, dz = bw.z - z;
            if (dx * dx + dz * dz <= r2) return true;
        }
        return false;
    }

    static boolean hostile(ServerLevel level, OrbitMath.Sat s, Flight f) {
        if (f == null || f.build() == null || f.build().isInterceptor() || Satellites.isSatellite(f.build())) return false;
        ServerPlayer op = level.getServer().getPlayerList().getPlayer(s.owner());
        if (f.isOwnedBy(s.owner(), op != null ? op.getGameProfile().getName() : s.ownerName())) return false;
        if (effectiveMode(level.getServer(), s.owner()) == MODE_ANY) return true;
        return inZone(level, s.owner(), f.tx() + 0.5, f.tz() + 0.5);
    }

    static void onLaunch(ServerLevel level, Flight f) {
        if (level.dimension() != Level.OVERWORLD || f == null || f.build() == null || f.build().isInterceptor() || Satellites.isSatellite(f.build())) return;
        MinecraftServer server = level.getServer();
        long now = System.currentTimeMillis();
        for (OrbitMath.Sat s : OrbitData.get(server).sats()) {
            if (!TYPE.equals(s.type()) || now >= s.endMs() || !OrbitData.controlled(s)) continue;
            if (!OrbitMath.isOverhead(s, f.ox(), f.oz(), now) || !charged(server, s, now) || !hostile(level, s, f)) continue;
            engage(level, s, f, !Hypersonic.isHyper(f) && level.random.nextInt(100) < Balance.INT_SAT_KILL_PCT, now);
            return;
        }
    }

    static void engage(ServerLevel level, OrbitMath.Sat s, Flight f, boolean kill, long nowMs) {
        SatDefenceData.get(level.getServer()).shot(s.id(), nowMs, kill);
        PENDING.add(new Pending(f.id(), Math.max(level.getGameTime() + 1, f.start() + KILL_DELAY_TICKS), s.id(), s.owner(), s.ownerName(), kill,
                level.dimension().location().toString()));
        Wmd.LOGGER.info("Interceptor satellite #{} ({}) engages flight {} of {}: {}", s.id(), s.ownerName(), f.id(), f.launcher(), kill ? "KILL" : "miss");
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 1200 == 77) SatDefenceData.get(server).prune(server);
        if (PENDING.isEmpty()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(p.dim())) level = l;
            if (level == null) { it.remove(); continue; }
            if (level.getGameTime() < p.at()) continue;
            it.remove();
            resolve(level, p);
        }
    }

    private static void resolve(ServerLevel level, Pending p) {
        FlightManager mgr = FlightManager.get(level);
        Flight f = mgr.find(p.flightId());
        if (f == null) return;
        long now = level.getGameTime();
        Vec3 at = f.position(now);
        if (p.kill()) mgr.kill(level, f);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 6000, new InterceptFxPayload(at.x, at.y, at.z, p.kill(), 0));
        MinecraftServer server = level.getServer();
        CompoundTag e = new CompoundTag();
        e.putLong("ms", System.currentTimeMillis());
        e.putInt("sat", p.satId());
        e.putBoolean("hit", p.kill());
        e.putString("by", f.launcher());
        e.putInt("x", (int) Math.round(f.ox()));
        e.putInt("z", (int) Math.round(f.oz()));
        SatDefenceData.get(server).addLog(p.owner(), e);
        CcBattleLog.onSatEngage(server, p.owner(), p.satId(), f, p.kill());
        ServerPlayer owner = server.getPlayerList().getPlayer(p.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(p.kill() ? "message.wmd.intsat.kill" : "message.wmd.intsat.miss", p.satId(), f.launcher(),
                    (int) Math.round(f.ox()), (int) Math.round(f.oz())).withStyle(p.kill() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            owner.playNotifySound(p.kill() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.7F, p.kill() ? 1.6F : 0.7F);
        }
        ServerPlayer victim = flightOwner(server, f);
        if (victim != null && victim != owner) {
            victim.sendSystemMessage(Component.translatable(p.kill() ? "message.wmd.intsat.lost" : "message.wmd.intsat.dodged", f.id(), p.ownerName())
                    .withStyle(p.kill() ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
    }

    static ServerPlayer flightOwner(MinecraftServer server, Flight f) {
        try { if (!f.ownerId().isEmpty()) return server.getPlayerList().getPlayer(UUID.fromString(f.ownerId())); } catch (IllegalArgumentException ignored) {}
        return server.getPlayerList().getPlayerByName(f.launcher());
    }

    static Component testLaunch(ServerPlayer p) {
        MinecraftServer server = p.getServer();
        OrbitData od = OrbitData.get(server);
        OrbitMath.Sat sat = null;
        for (OrbitMath.Sat s : od.sats()) if (TYPE.equals(s.type()) && p.getUUID().equals(s.owner())) { sat = s; break; }
        if (sat == null) return Component.literal("You have no interceptor satellite: /wmd sat add interceptor");
        long now = System.currentTimeMillis();
        od.replace(server, sat.withPhase(OrbitMath.phaseForPass(sat, p.getX(), p.getZ(), now, 10.0)));
        SatDefenceData.get(server).recharge(sat.id());
        ServerLevel level = p.serverLevel();
        int tx = p.getBlockX(), tz = p.getBlockZ();
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(p.getUUID())) if (b.getLevel() == level) { Vec3 bw = SableBridge.worldPos(level, b.getBlockPos()); tx = (int) Math.floor(bw.x); tz = (int) Math.floor(bw.z); break; }
        Vec3 from = new Vec3(p.getX() + 1500.0, p.getY() + Flight.LAUNCH_HEIGHT, p.getZ());
        long id = FlightManager.get(level).launch(level, 1, from, tx, p.getBlockY(), tz, "Test enemy", UUID.randomUUID(), 0.0,
                new MissileBuild(1, 1, 1, 1, "decoy", false, false), false, 0, 0);
        boolean ctl = OrbitData.controlled(sat);
        return Component.literal("Fake enemy launch #" + id + " from " + Math.round(from.x) + " " + Math.round(from.z) + " at " + tx + " " + tz
                + " under interceptor satellite #" + sat.id() + " (" + (ctl ? "controlled" : "NOT controlled: needs a powered ground station + an active command center")
                + ", mode " + (effectiveMode(server, p.getUUID()) == MODE_ANY ? "ANY" : "OWN AREA") + "). Outcome in " + KILL_DELAY_TICKS / 20 + " s.");
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { PENDING.clear(); }
}
