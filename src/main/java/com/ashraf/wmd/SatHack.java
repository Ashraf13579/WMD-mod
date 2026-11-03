package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class SatHack {

    private SatHack() {}

    private static final class Hack {
        final ResourceKey<Level> dim;
        final BlockPos station;
        final UUID hacker;
        final String hackerName;
        final int satId;
        int ticks;
        Hack(ResourceKey<Level> dim, BlockPos station, UUID hacker, String hackerName, int satId) {
            this.dim = dim; this.station = station.immutable(); this.hacker = hacker; this.hackerName = hackerName; this.satId = satId;
        }
    }

    private static final List<Hack> HACKS = new ArrayList<>();

    private static final Map<String, Long> TRIED = new HashMap<>();

    public static int totalTicks() { return Math.max(1, Balance.SAT_HACK_SECONDS * 20); }

    private static String key(UUID hacker, int satId) { return hacker + "|" + satId; }

    public static boolean tried(UUID hacker, int satId, long nowMs) {
        Long until = TRIED.get(key(hacker, satId));
        return until != null && nowMs < until;
    }

    static int[] triedIds(UUID hacker, long nowMs) {
        List<Integer> l = new ArrayList<>();
        String p = hacker + "|";
        for (Map.Entry<String, Long> e : TRIED.entrySet())
            if (nowMs < e.getValue() && e.getKey().startsWith(p)) {
                try { l.add(Integer.parseInt(e.getKey().substring(p.length()))); } catch (NumberFormatException ignored) {}
            }
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int[] hacked(MinecraftServer server, UUID owner) {
        if (owner == null || HACKS.isEmpty()) return new int[0];
        OrbitData od = OrbitData.get(server);
        List<Integer> l = new ArrayList<>();
        for (Hack h : HACKS) {
            OrbitMath.Sat s = od.byId(h.satId);
            if (s != null && owner.equals(s.owner()) && !l.contains(h.satId)) l.add(h.satId);
        }
        return l.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Hack runningAt(ResourceKey<Level> dim, BlockPos pos) {
        for (Hack h : HACKS) if (h.dim == dim && h.station.equals(pos)) return h;
        return null;
    }

    static Component start(ServerPlayer player, GroundStationBlockEntity st, int satId, boolean instant) {
        MinecraftServer server = player.getServer();
        UUID me = player.getUUID();
        if (st == null || st.getLevel() == null || st.isRemoved()) return Component.translatable("message.wmd.hack.no_station");
        if (!me.equals(st.getOwner())) return Component.translatable("message.wmd.hack.owner_only");
        OrbitMath.Sat s = OrbitData.get(server).byId(satId);
        if (s == null) return Component.translatable("message.wmd.hack.no_sat", satId);
        if (me.equals(s.owner())) return Component.translatable("message.wmd.hack.own", satId);
        if (instant) {
            succeed(server, s, me, player.getGameProfile().getName());
            return null;
        }
        long now = System.currentTimeMillis();
        net.minecraft.world.phys.Vec3 sw = SableBridge.worldPos(st.getLevel(), st.getBlockPos());
        double x = sw.x, z = sw.z;
        if (!st.isRunning()) return Component.translatable("message.wmd.hack.unpowered");
        if (!CommandCenter.hasModule(me, CcModule.EW_CONSOLE)) return Component.translatable("message.wmd.hack.no_ew");
        if (!OrbitMath.isOverhead(s, x, z, now)) return Component.translatable("message.wmd.hack.not_overhead", satId, OrbitMath.mmss(OrbitMath.nextPass(s, x, z, now)));
        if (tried(me, satId, now)) return Component.translatable("message.wmd.hack.tried", satId);
        if (runningAt(st.getLevel().dimension(), st.getBlockPos()) != null) return Component.translatable("message.wmd.hack.busy");
        long left = OrbitMath.overheadLeft(s, x, z, now);
        if (left < totalTicks() * 50L) return Component.translatable("message.wmd.hack.too_late", satId, OrbitMath.mmss(left), Balance.SAT_HACK_SECONDS);
        TRIED.put(key(me, satId), now + left);
        HACKS.add(new Hack(st.getLevel().dimension(), st.getBlockPos(), me, player.getGameProfile().getName(), satId));
        push(server, st, me);

        ServerPlayer o = server.getPlayerList().getPlayer(s.owner());
        if (o != null) {
            Component m = Component.translatable("message.wmd.hack.warning", OrbitData.typeName(s.type()), s.id()).withStyle(ChatFormatting.RED);
            o.sendSystemMessage(m);
            o.displayClientMessage(m, true);
            o.playNotifySound(WmdSounds.CC_ALERT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        Wmd.LOGGER.info("{} started hacking satellite #{} ({}, owner {}) from the ground station at {}", player.getGameProfile().getName(), s.id(), s.type(),
                s.ownerName(), st.getBlockPos().toShortString());
        return Component.translatable("message.wmd.hack.started", satId, Balance.SAT_HACK_SECONDS, OrbitMath.mmss(left));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (HACKS.isEmpty()) {
            if (!TRIED.isEmpty() && event.getServer().getTickCount() % 1200 == 33) {
                long now = System.currentTimeMillis();
                TRIED.values().removeIf(v -> v <= now);
            }
            return;
        }
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        boolean second = server.getTickCount() % 20 == 0;
        OrbitData od = OrbitData.get(server);
        List<Object[]> done = new ArrayList<>();
        for (Iterator<Hack> it = HACKS.iterator(); it.hasNext(); ) {
            Hack h = it.next();
            ServerLevel level = server.getLevel(h.dim);
            GroundStationBlockEntity st = level != null && level.isLoaded(h.station) && level.getBlockEntity(h.station) instanceof GroundStationBlockEntity g && !g.isRemoved() ? g : null;
            OrbitMath.Sat s = od.byId(h.satId);
            String why = null;
            if (st == null) why = "message.wmd.hack.cancel.station";
            else if (!h.hacker.equals(st.getOwner())) why = "message.wmd.hack.cancel.station";
            else if (!st.isRunning()) why = "message.wmd.hack.cancel.unpowered";
            else if (s == null) why = "message.wmd.hack.cancel.gone";
            else if (h.hacker.equals(s.owner())) why = "message.wmd.hack.cancel.gone";
            else if (!CommandCenter.hasModule(h.hacker, CcModule.EW_CONSOLE)) why = "message.wmd.hack.cancel.ew";
            else if (!OrbitMath.isOverhead(s, SableBridge.worldPos(level, h.station).x, SableBridge.worldPos(level, h.station).z, now)) why = "message.wmd.hack.cancel.pass";
            if (why != null) {
                it.remove();
                note(server, h.hacker, Component.translatable(why, h.satId).withStyle(ChatFormatting.YELLOW));
                if (st != null) push(server, st, h.hacker);
                continue;
            }
            if (st.takeHack(Balance.SAT_HACK_FE_PER_TICK)) h.ticks++;
            if (h.ticks >= totalTicks()) {
                it.remove();
                done.add(new Object[] { h, st, s });
                continue;
            }
            if (second) push(server, st, h.hacker);
        }
        for (Object[] d : done) {
            Hack h = (Hack) d[0];
            OrbitMath.Sat s = od.byId(h.satId);
            if (s != null && !h.hacker.equals(s.owner())) succeed(server, s, h.hacker, h.hackerName);
            push(server, (GroundStationBlockEntity) d[1], h.hacker);
        }
    }

    private static void push(MinecraftServer server, GroundStationBlockEntity st, UUID hacker) {
        if (st.getLevel() == null) return;
        Hack h = runningAt(st.getLevel().dimension(), st.getBlockPos());
        st.setHackView(h == null ? 0 : h.satId, h == null ? 0 : h.ticks, triedIds(hacker, System.currentTimeMillis()));
    }

    static void succeed(MinecraftServer server, OrbitMath.Sat s, UUID hacker, String hackerName) {
        UUID oldOwner = s.owner();
        String oldName = s.ownerName();
        if (!OrbitData.get(server).transfer(server, s.id(), hacker, hackerName)) return;
        CcBattleLog.onHack(server, s, hacker, hackerName);
        cancelAll(server, s.id(), null);
        Wmd.LOGGER.info("{} HACKED satellite #{} ({}) from {}", hackerName, s.id(), s.type(), oldName);
        ServerPlayer hp = server.getPlayerList().getPlayer(hacker);
        if (hp != null) {
            hp.sendSystemMessage(Component.translatable("message.wmd.hack.success", OrbitData.typeName(s.type()), s.id(), oldName).withStyle(ChatFormatting.GREEN));
            hp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        ServerPlayer op = server.getPlayerList().getPlayer(oldOwner);
        if (op != null) op.sendSystemMessage(Component.translatable("message.wmd.hack.stolen", OrbitData.typeName(s.type()), s.id(), hackerName).withStyle(ChatFormatting.RED));
    }

    static void cancelAll(MinecraftServer server, int satId, String whyKey) {
        for (Iterator<Hack> it = HACKS.iterator(); it.hasNext(); ) {
            Hack h = it.next();
            if (h.satId != satId) continue;
            it.remove();
            if (whyKey != null) note(server, h.hacker, Component.translatable(whyKey, satId).withStyle(ChatFormatting.YELLOW));
            ServerLevel level = server.getLevel(h.dim);
            if (level != null && level.isLoaded(h.station) && level.getBlockEntity(h.station) instanceof GroundStationBlockEntity g) push(server, g, h.hacker);
        }
    }

    static Component rekey(MinecraftServer server, UUID by, int satId, boolean ignoreCooldown) {
        OrbitMath.Sat s = OrbitData.get(server).byId(satId);
        if (s == null) return Component.translatable("message.wmd.hack.no_sat", satId);
        if (by != null && !by.equals(s.owner())) return Component.translatable("message.wmd.rekey.not_yours", satId);
        long now = System.currentTimeMillis();
        SatDefenceData d = SatDefenceData.get(server);
        long ready = d.rekeyMs(satId) + Balance.SAT_REKEY_COOLDOWN_S * 1000L;
        if (!ignoreCooldown && d.rekeyMs(satId) > 0L && now < ready) return Component.translatable("message.wmd.rekey.cooldown", satId, OrbitMath.mmss(ready - now));
        d.rekeyed(satId, now);
        boolean any = false;
        for (Hack h : HACKS) if (h.satId == satId) any = true;
        cancelAll(server, satId, "message.wmd.hack.cancel.rekey");
        Wmd.LOGGER.info("Satellite #{} re-keyed by its owner {}{}", satId, s.ownerName(), any ? " (a hack was cut off)" : "");
        return Component.translatable(any ? "message.wmd.rekey.done_cut" : "message.wmd.rekey.done", satId).withStyle(ChatFormatting.GREEN);
    }

    public static long rekeyIn(MinecraftServer server, int satId) {
        long t = SatDefenceData.get(server).rekeyMs(satId);
        return t <= 0L ? 0L : Math.max(0L, t + Balance.SAT_REKEY_COOLDOWN_S * 1000L - System.currentTimeMillis());
    }

    private static void note(MinecraftServer server, UUID who, Component c) {
        ServerPlayer p = server.getPlayerList().getPlayer(who);
        if (p != null) p.sendSystemMessage(c);
    }

    static List<String> describe(MinecraftServer server) {
        List<String> l = new ArrayList<>();
        for (Hack h : HACKS) l.add("#" + h.satId + " by " + h.hackerName + " from " + h.station.toShortString() + " " + (h.ticks * 100 / totalTicks()) + " %");
        return l;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        HACKS.clear();
        TRIED.clear();
    }
}
