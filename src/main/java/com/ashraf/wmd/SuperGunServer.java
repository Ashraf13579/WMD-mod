package com.ashraf.wmd;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SuperGunServer {

    private SuperGunServer() {}

    private static final int HOLD_TICKS = 100;

    private static final Set<Long> HELD = new HashSet<>();

    static void onFired(ServerLevel level, SuperGunBlockEntity gun) { onFiredBy(level, gun.getBlockPos(), gun.getOwner(), gun.getOwnerName()); }

    static void onFiredBy(ServerLevel level, BlockPos gunPos, UUID shooter, String shooterName) {
        ArtShell s = newest(level, gunPos);
        if (s == null) return;
        MinecraftServer server = level.getServer();
        double r2 = (double) Balance.SUPER_GUN_WARN_RANGE * Balance.SUPER_GUN_WARN_RANGE;
        Set<UUID> owners = new LinkedHashSet<>();
        for (CcModuleBlockEntity m : CommandCenter.modules()) {
            if (!(m instanceof CcWarningBoardBlockEntity b) || b.isRemoved() || b.getLevel() != level) continue;
            UUID o = b.getOwner();
            if (o == null || o.equals(shooter)) continue;
            net.minecraft.world.phys.Vec3 bw = SableBridge.worldPos(level, b.getBlockPos());
            double bx = bw.x, bz = bw.z;
            double dg = sq(bx - s.ox()) + sq(bz - s.oz()), di = sq(bx - s.tx()) + sq(bz - s.tz());
            if (Math.min(dg, di) <= r2) owners.add(o);
        }
        net.minecraft.world.phys.Vec3 gw = SableBridge.worldPos(level, gunPos);
        int gx = (int) Math.floor(gw.x), gz = (int) Math.floor(gw.z);
        int secs = Math.max(1, s.dur() / 20);
        for (UUID o : owners) {
            long[] imp = Recon.blurred((int) Math.floor(s.tx()), (int) Math.floor(s.tz()), level.random);
            CommandCenter.addWarning(server, o, new CommandCenter.Warning(SuperGun.warnId(s.id()), gx, gz, (int) imp[0], (int) imp[1], s.start(), s.endTime(), false));
            ServerPlayer p = server.getPlayerList().getPlayer(o);
            if (p != null) {
                p.sendSystemMessage(Component.translatable("message.wmd.super_gun.warn", gx, gz, imp[0], imp[1], secs).withStyle(ChatFormatting.RED));
                p.displayClientMessage(Component.translatable("message.wmd.super_gun.bar", secs).withStyle(ChatFormatting.RED), true);
            }
        }
        Wmd.LOGGER.info("Super gun of {} at {} fired shell #{} at {} {} ({} s); warned {} owner(s)", shooterName, gunPos.toShortString(), s.id(),
                Math.round(s.tx()), Math.round(s.tz()), secs, owners.size());
    }

    private static double sq(double v) { return v * v; }

    private static ArtShell newest(ServerLevel level, BlockPos gunPos) {
        List<ArtShell> l = ArtilleryManager.get(level).shells();
        long now = level.getGameTime(), gp = gunPos.asLong();
        for (int i = l.size() - 1; i >= 0; i--) {
            ArtShell s = l.get(i);
            if (s.gun() == Artillery.SUPER_GUN && s.gunPos() == gp && s.start() == now) return s;
        }
        return null;
    }

    static double holdRadius() { return WarheadFx.holdRadius(Balance.SUPER_GUN_BLAST_TIER, WarheadType.HE); }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 10 != 0) return;
        ArtilleryManager m = level.getDataStorage().get(new SavedData.Factory<>(ArtilleryManager::new, ArtilleryManager::load, null), "wmd_artillery");
        if (m == null || m.shells().isEmpty()) return;
        long now = level.getGameTime();
        for (ArtShell s : m.shells()) {
            if (s.gun() != Artillery.SUPER_GUN || now < s.endTime() - HOLD_TICKS || HELD.contains(s.id())) continue;
            HELD.add(s.id());
            FlightManager.get(level).holdForTest(level, (int) Math.floor(s.tx()), (int) Math.floor(s.tz()), holdRadius());
        }
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { HELD.clear(); }

    static void impact(ServerLevel level, ArtShell s, long now) {
        HELD.remove(s.id());
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        FlightManager fm = FlightManager.get(level);
        fm.holdForTest(level, gx, gz, holdRadius());

        int gy = ArtilleryManager.ground(level, gx, gz, s.ty(), true);
        Vec3 v = s.velocity(s.endTime());
        UUID prev = ShipStrike.begin(realOwner(s));
        try { detonate(level, fm, s, gx, gy, gz, v); }
        finally { ShipStrike.end(prev); }
        fm.setDirty();
        PacketDistributor.sendToPlayersNear(level, null, gx + 0.5, gy, gz + 0.5, ArtilleryManager.NEAR,
                new ArtImpactPayload(s.id(), s.gun(), s.type(), gx + 0.5, gy, gz + 0.5, ArtImpactPayload.IMPACT));
        UUID o = s.owner();
        if (o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L))
            CcBattleLog.add(level.getServer(), o, CcBattleLog.HIT, "log.wmd.super_gun_hit", Integer.toString(gx), Integer.toString(gz));
        Wmd.LOGGER.info("Super gun shell #{} of {} landed at {} {} {}", s.id(), s.ownerName(), gx, gy, gz);
    }

    private static void detonate(ServerLevel level, FlightManager fm, ArtShell s, int gx, int gy, int gz, Vec3 v) {
        if (s.type() == Artillery.SUPER_NUKE) {
            fm.holdForTest(level, gx, gz, WarheadFx.holdRadius(WarheadType.NUKE.tier, WarheadType.NUKE));
            WarheadFx.detonate(level, fm, gx, gy, gz, WarheadType.NUKE.tier, WarheadType.NUKE, v.x, v.z);
            Wmd.LOGGER.info("Super gun NUCLEAR shell #{} of {} went off at {} {} {}", s.id(), s.ownerName(), gx, gy, gz);
        } else WarheadFx.detonate(level, fm, gx, gy, gz, Balance.SUPER_GUN_BLAST_TIER, WarheadType.HE, v.x, v.z);
    }

    private static UUID realOwner(ArtShell s) {
        UUID o = s.owner();
        return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L) ? o : null;
    }

    static void endedEarly(ServerLevel level, ArtShell s, Vec3 p, long now) {
        FlightManager fm = FlightManager.get(level);
        if (HELD.remove(s.id())) fm.release(level, (int) Math.floor(s.tx()), (int) Math.floor(s.tz()), holdRadius(), now + 100);
        int gx = (int) Math.floor(p.x), gy = (int) Math.floor(p.y), gz = (int) Math.floor(p.z);
        Vec3 v = s.velocity(now);
        UUID prev = ShipStrike.begin(realOwner(s));
        try { detonate(level, fm, s, gx, gy, gz, v); }
        finally { ShipStrike.end(prev); }
        fm.setDirty();
        PacketDistributor.sendToPlayersNear(level, null, p.x, p.y, p.z, ArtilleryManager.NEAR,
                new ArtImpactPayload(s.id(), s.gun(), s.type(), p.x, p.y, p.z, ArtImpactPayload.IMPACT));
        UUID o = realOwner(s);
        if (o != null) CcBattleLog.add(level.getServer(), o, CcBattleLog.HIT, "log.wmd.super_gun_hit", Integer.toString(gx), Integer.toString(gz));
        Wmd.LOGGER.info("Super gun shell #{} of {} struck a ship in flight at {} {} {}", s.id(), s.ownerName(), gx, gy, gz);
    }
}
