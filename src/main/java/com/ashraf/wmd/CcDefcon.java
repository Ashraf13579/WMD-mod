package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CcDefcon {

    private CcDefcon() {}

    public static final int MIN = 1, MAX = 5, PEACE = 5;

    public static final int LOCKDOWN_TICKS = 600;

    public static final int IMPACT_GRACE_TICKS = 100;

    public static final int COUNTER_ARM_TICKS = 1200;

    public static final int THREAT_RADIUS = 2_000;

    public static boolean threat(CcBuildingBlockEntity b, CommandCenter.Warning w) {
        if (w == null || w.sat() || b.getLevel() == null || b.getLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) return false;
        net.minecraft.world.phys.Vec3 c = SableBridge.worldPos(b.getLevel(), b.hallCentre());
        double dx = w.ix() - (c.x - 0.5), dz = w.iz() - (c.z - 0.5);
        return dx * dx + dz * dz <= (double) THREAT_RADIUS * THREAT_RADIUS;
    }

    public static final int B_INCOMING = 0, B_LOCKDOWN = 1, B_DEFCON = 2, B_COUNTER = 3, B_BDA = 4;

    public static int clamp(int d) { return Math.max(MIN, Math.min(MAX, d)); }

    public static int colour(int d) {
        return switch (clamp(d)) { case 1 -> 0xFFFF2828; case 2 -> 0xFFFF7A1E; case 3 -> 0xFFF0C030; case 4 -> 0xFF40C8E0; default -> 0xFF40D060; };
    }

    public static int level(UUID owner) {
        int d = PEACE;
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) d = Math.min(d, b.defcon());
        return d;
    }

    static List<ServerPlayer> team(MinecraftServer server, UUID owner) {
        Map<UUID, ServerPlayer> m = new LinkedHashMap<>();
        if (server == null || owner == null) return new ArrayList<>();
        ServerPlayer o = server.getPlayerList().getPlayer(owner);
        if (o != null) m.put(owner, o);
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner))
            for (UUID u : b.security().trusted().keySet()) {
                if (m.containsKey(u)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(u);
                if (p != null) m.put(u, p);
            }
        return new ArrayList<>(m.values());
    }

    static void banner(MinecraftServer server, UUID owner, int kind, boolean chat, String key, String... args) {
        CcAlertPayload p = new CcAlertPayload(kind, key, List.of(args));
        Component text = CcBattleLog.text(key, List.of(args));
        for (ServerPlayer sp : team(server, owner)) {
            PacketDistributor.sendToPlayer(sp, p);
            if (chat && !(kind == B_INCOMING && sp.getUUID().equals(owner)))
                sp.sendSystemMessage(text.copy().withStyle(kind == B_COUNTER ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
    }

    static void onWarning(MinecraftServer server, UUID owner, CommandCenter.Warning w) {
        if (server == null || owner == null || w == null || w.sat()) return;
        int d = level(owner);
        if (d > 4) return;
        if (DroneWarnings.isWarning(w.id())) {
            banner(server, owner, B_INCOMING, true, DroneWarnings.isDrone(w.id()) ? "hud.wmd.cc.alert.drone" : "hud.wmd.cc.alert.bomb", Integer.toString(w.ox()), Integer.toString(w.oz()));
            if (d <= 2) for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.sealCheckNow();
            return;
        }
        if (AirshipWatch.isWarning(w.id())) {
            banner(server, owner, B_INCOMING, true, "hud.wmd.cc.alert.airship", Integer.toString(w.ox()), Integer.toString(w.oz()));
            if (d <= 2) for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.sealCheckNow();
            return;
        }
        long now = server.overworld().getGameTime();
        String eta = OrbitMath.mmss(Math.max(0L, w.impactGt() - now) * 50L);
        banner(server, owner, B_INCOMING, true, "hud.wmd.cc.alert.incoming", Integer.toString(w.ox()), Integer.toString(w.oz()),
                Integer.toString(w.ix()), Integer.toString(w.iz()), eta);
        if (d <= 2) for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.sealCheckNow();
        if (d <= 1) {
            CcTarget t = new CcTarget(w.ox(), w.oz(), "gui.wmd.cc.des.launch_site", CcTarget.SRC_COUNTER, "DEFCON 1", System.currentTimeMillis());
            boolean armed = false;
            for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) {
                if (b.defcon() > 1 || !threat(b, w)) continue;
                b.designate(t);
                b.armCounter(now + COUNTER_ARM_TICKS);
                if (b.getLevel() instanceof ServerLevel sl) MusicTriggersHook.fire(sl, "wmd:counter_strike_armed", b.getBlockPos());
                armed = true;
            }
            if (!armed) return;
            banner(server, owner, B_COUNTER, true, "hud.wmd.cc.alert.counter", Integer.toString(w.ox()), Integer.toString(w.oz()));
            CcBattleLog.add(server, owner, CcBattleLog.COUNTER, "log.wmd.counter_armed", Integer.toString(w.ox()), Integer.toString(w.oz()));
        }
    }

    static void onAlarm(MinecraftServer server, UUID owner, CcAlarms.Event e) {
        if (server == null || owner == null || e == null) return;
        long now = server.overworld().getGameTime();
        boolean any = false;
        for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) {
            if (b.defcon() > 3 && !b.lockdownToggle()) continue;
            b.lockdown(now + LOCKDOWN_TICKS);
            if (b.getLevel() instanceof ServerLevel sl) MusicTriggersHook.fire(sl, "wmd:lockdown", b.getBlockPos());
            any = true;
        }
        if (!any) return;
        String who = e.actor().isEmpty() ? "#message.wmd.cc.alarm.someone" : e.actor();
        CcAlertPayload p = new CcAlertPayload(B_LOCKDOWN, "hud.wmd.cc.alert.lockdown", List.of(who, "#" + e.machine()));
        Component chat = CcBattleLog.text("hud.wmd.cc.alert.lockdown", List.of(who, "#" + e.machine()));
        for (ServerPlayer sp : team(server, owner)) {
            PacketDistributor.sendToPlayer(sp, p);
            sp.sendSystemMessage(chat.copy().withStyle(ChatFormatting.RED));
            sp.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.7F);
        }
        CcBattleLog.add(server, owner, CcBattleLog.ALARM, "log.wmd.lockdown", who, "#" + e.machine());
    }

    static void changed(ServerPlayer by, CcBuildingBlockEntity b, int old, int now) {
        MinecraftServer server = by.server;
        Wmd.LOGGER.info("Command center: {} set DEFCON {} -> {} at {}", by.getGameProfile().getName(), old, now, b.getBlockPos().toShortString());
        CcBattleLog.add(server, b.getOwner(), CcBattleLog.DEFCON, "log.wmd.defcon", Integer.toString(old), Integer.toString(now), by.getGameProfile().getName());
        banner(server, b.getOwner(), B_DEFCON, false, "hud.wmd.cc.alert.defcon." + clamp(now), Integer.toString(clamp(now)));
        for (CcBuildingBlockEntity x : CommandCenter.activeBuildings(b.getOwner())) x.sealCheckNow();

        if (b.getLevel() instanceof ServerLevel sl) MusicTriggersHook.fire(sl, "wmd:defcon_" + clamp(now), b.getBlockPos());
    }
}
