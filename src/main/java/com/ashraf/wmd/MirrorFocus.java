package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class MirrorFocus {

    private MirrorFocus() {}

    public static final int FOLDED = 0, DEPLOYING = 1, READY = 2, BURNING = 3, COOLING = 4, FOLDING = 5;
    public static final int CMD_TARGET = 0, CMD_DEPLOY = 1, CMD_FOCUS = 2, CMD_OFF = 3, CMD_FOLD = 4;

    private static final class St {
        int state; long t0; boolean goal; int gx, gz; double sx, sz; boolean spot;
        long burned; long coolLen; String lost = "";
        final Set<UUID> warned = new HashSet<>();
    }

    private static final Map<Integer, St> ST = new HashMap<>();

    static void clearStatics() { ST.clear(); }

    static boolean busy(int slot) { St s = ST.get(slot); return s != null && s.state != FOLDED && s.state != READY; }

    static Station.Module module(MinecraftServer server, int slot) {
        for (Station.Module m : Station.modules(server, slot)) if (Station.MIRROR.equals(m.type()) && m.built()) return m;
        return null;
    }

    private static St st(MinecraftServer server, int slot) {
        return ST.computeIfAbsent(slot, k -> {
            St s = new St();
            if (Station.Data.get(server).mirrorUp.containsKey(slot)) s.state = READY;
            return s;
        });
    }

    static boolean burning(int slot) { St s = ST.get(slot); return s != null && s.state == BURNING; }

    static int[] status(MinecraftServer server, int slot, long now) {
        Station.Module m = module(server, slot);
        if (m == null) return new int[] { -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        if (Station.wrecked(server, m)) return new int[] { -2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        St s = st(server, slot);
        long el = now - s.t0;
        int len = s.state == DEPLOYING ? Balance.MIRROR_DEPLOY_TICKS : s.state == FOLDING ? Balance.MIRROR_FOLD_TICKS : s.state == COOLING ? (int) s.coolLen
                : s.state == BURNING ? Balance.MIRROR_BURN_TICKS : 1;
        int pct = s.state == BURNING ? (int) Math.min(100, 100 * (s.burned + el) / Balance.MIRROR_BURN_TICKS) : (int) Math.max(0, Math.min(100, 100 * el / Math.max(1, len)));
        int secs = s.state == BURNING ? (int) Math.max(0, (Balance.MIRROR_BURN_TICKS - s.burned - el) / 20) : s.state == COOLING ? (int) Math.max(0, (s.coolLen - el) / 20) : 0;
        ServerLevel ground = server.getLevel(Orbit.ground(server, slot).dim());
        return new int[] { s.state, pct, secs, s.goal ? 1 : 0, s.gx, s.gz, s.spot ? 1 : 0, (int) Math.floor(s.sx), (int) Math.floor(s.sz),
                StationLife.sunlit(now) ? 1 : 0, ground != null && ground.isDay() ? 1 : 0, ground != null && !ground.isRaining() && !ground.isThundering() ? 1 : 0 };
    }

    static Component command(ServerPlayer p, int slot, int cmd, int x, int z) {
        MinecraftServer server = p.server;
        if (!p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) return Component.translatable("message.wmd.mirror.not_owner");
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module m = module(server, slot);
        if (m == null || orbit == null) return Component.translatable("message.wmd.mirror.none");
        if (Station.wrecked(server, m)) return Component.translatable("message.wmd.mirror.wrecked");
        St s = st(server, slot);
        long now = orbit.getGameTime();
        switch (cmd) {
            case CMD_TARGET -> {
                Orbit.Ground g = Orbit.ground(server, slot);
                if (Math.abs(x - g.x()) > Balance.MIRROR_TARGET_R || Math.abs(z - g.z()) > Balance.MIRROR_TARGET_R)
                    return Component.translatable("message.wmd.rod.out_of_view", Balance.MIRROR_TARGET_R);
                s.goal = true; s.gx = x; s.gz = z;
                if (!s.spot) { s.spot = true; s.sx = x + 0.5; s.sz = z + 0.5; }
                return Component.translatable("message.wmd.mirror.goal", x, z).withStyle(ChatFormatting.GOLD);
            }
            case CMD_DEPLOY -> {
                if (s.state != FOLDED) return Component.translatable("message.wmd.mirror.busy");
                if (blocked(server, slot, m)) return Component.translatable("message.wmd.mirror.blocked");
                set(orbit, m, s, DEPLOYING, now);
                orbit.playSound(null, Station.middle(m), WmdSounds.MR_DEPLOY.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                return Component.translatable("message.wmd.mirror.deploying");
            }
            case CMD_FOCUS -> {
                if (s.state != READY) return Component.translatable(s.state == COOLING ? "message.wmd.mirror.cooling" : "message.wmd.mirror.not_ready");
                if (!s.goal) return Component.translatable("message.wmd.mirror.no_goal");
                String why = why(server, slot, now);
                if (why != null) return Component.translatable(why);
                s.warned.clear();
                set(orbit, m, s, BURNING, now);
                StationLife.pa(orbit, slot, WmdSounds.PA_MIRROR.get());
                orbit.playSound(null, Station.middle(m), WmdSounds.MR_FOCUS_ON.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                UUID owner = p.getUUID();
                CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_OUT, "log.wmd.mirror.burn", Integer.toString(s.gx), Integer.toString(s.gz));
                Wmd.LOGGER.info("[mirror] {} focuses the solar mirror of slot {} on {} {}", p.getGameProfile().getName(), slot, s.gx, s.gz);
                Station.offerCamera(p, m);
                return Component.translatable("message.wmd.mirror.burning", s.gx, s.gz).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            }
            case CMD_OFF -> {
                if (s.state != BURNING) return Component.translatable("message.wmd.mirror.not_burning");
                stop(orbit, m, s, now, false);
                return Component.translatable("message.wmd.mirror.off");
            }
            case CMD_FOLD -> {
                if (s.state != READY && s.state != COOLING) return Component.translatable(s.state == BURNING ? "message.wmd.mirror.off_first" : "message.wmd.mirror.busy");
                set(orbit, m, s, FOLDING, now);
                orbit.playSound(null, Station.middle(m), WmdSounds.MR_DEPLOY.get(), SoundSource.BLOCKS, 3.0F, 0.85F);
                return Component.translatable("message.wmd.mirror.folding");
            }
            default -> { return null; }
        }
    }

    private static String why(MinecraftServer server, int slot, long now) {
        if (!StationLife.sunlit(now)) return "message.wmd.mirror.no_sun";
        ServerLevel ground = server.getLevel(Orbit.ground(server, slot).dim());
        if (ground == null || !ground.isDay()) return "message.wmd.mirror.night";
        if (ground.isRaining() || ground.isThundering()) return "message.wmd.mirror.clouds";
        if (!StationLife.powered(slot)) return "message.wmd.mirror.no_power";
        return null;
    }

    private static void stop(ServerLevel orbit, Station.Module m, St s, long now, boolean overheat) {
        s.burned += now - s.t0;
        s.coolLen = overheat ? Balance.MIRROR_COOL_TICKS : Math.max(Balance.MIRROR_MIN_COOL, Math.min(Balance.MIRROR_COOL_TICKS, s.burned));
        s.burned = 0;
        set(orbit, m, s, COOLING, now);
        orbit.playSound(null, Station.middle(m), WmdSounds.MR_FOCUS_OFF.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    private static void set(ServerLevel orbit, Station.Module m, St s, int state, long t0) {
        s.state = state; s.t0 = t0;
        Station.Data d = Station.Data.get(orbit.getServer());
        int slot = Station.slotOf(orbit.getServer(), m);
        if (state == FOLDED || state == DEPLOYING) d.mirrorUp.remove(slot); else d.mirrorUp.put(slot, 1);
        d.setDirty();
        if (orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setRod(state, t0, 0, s.burned, s.coolLen);
    }

    static boolean blocked(MinecraftServer server, int slot, Station.Module m) {
        boolean[] hit = { false };
        for (Station.Module o : Station.modules(server, slot)) {
            if (o.id() == m.id()) continue;
            Station.cells(o, (p, st) -> {
                if (hit[0]) return;
                BlockPos l = Station.local(m, p);
                if (l.getX() >= 13 && l.getX() <= 48 && l.getY() >= 6 && l.getY() <= 20 && Math.abs(l.getZ()) <= 17) hit[0] = true;
            });
        }
        return hit[0];
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit) || ST.isEmpty()) return;
        long now = orbit.getGameTime();
        MinecraftServer server = orbit.getServer();
        if (now % 20 == 0) MirrorBurn.second(server);
        for (Map.Entry<Integer, St> e : ST.entrySet()) {
            int slot = e.getKey();
            St s = e.getValue();
            Station.Module m = module(server, slot);
            if (m == null || Station.wrecked(server, m)) {
                if (s.state == BURNING) { s.state = READY; tellOwner(server, slot, Component.translatable("message.wmd.mirror.lost_wreck").withStyle(ChatFormatting.RED)); }
                s.spot = false; continue;
            }
            long el = now - s.t0;

            if (s.goal && s.spot && (s.state == READY || s.state == BURNING)) {
                double dx = s.gx + 0.5 - s.sx, dz = s.gz + 0.5 - s.sz, d = Math.hypot(dx, dz);
                if (d > 1.0E-3) { double k = Math.min(1.0, Balance.MIRROR_SPEED / d); s.sx += dx * k; s.sz += dz * k; }
                if (d > 2.0 && now % 80 == 0) orbit.playSound(null, Station.middle(m), WmdSounds.MR_STEER_LOOP.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
            }
            switch (s.state) {
                case DEPLOYING -> { if (el >= Balance.MIRROR_DEPLOY_TICKS) set(orbit, m, s, READY, now); }
                case FOLDING -> { if (el >= Balance.MIRROR_FOLD_TICKS) set(orbit, m, s, FOLDED, now); }
                case COOLING -> { if (el >= s.coolLen) set(orbit, m, s, READY, now); }
                case BURNING -> {
                    if (el % 100 == 0) orbit.playSound(null, Station.middle(m), WmdSounds.MR_BEAM_LOOP.get(), SoundSource.BLOCKS, 2.5F, 1.0F);
                    if (el % 20 == 0) {
                        String why = why(server, slot, now);
                        if (why != null) {
                            s.burned += el;
                            set(orbit, m, s, READY, now);
                            tellOwner(server, slot, Component.translatable("message.wmd.mirror.lost", Component.translatable(why)).withStyle(ChatFormatting.GOLD));
                        } else if (s.burned + el >= Balance.MIRROR_BURN_TICKS) {
                            stop(orbit, m, s, now, true);
                            tellOwner(server, slot, Component.translatable("message.wmd.mirror.overheat", Balance.MIRROR_COOL_TICKS / 1200).withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
                default -> {}
            }
            if (now % 20 == 0) {
                aim(orbit, slot, m, s);
                if ((s.state == READY || s.state == BURNING) && s.goal && s.spot) warn(server, slot, s);
            }
        }
    }

    private static void aim(ServerLevel orbit, int slot, Station.Module m, St s) {
        if (!(orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be)) return;
        float tx = 0.0F, tz = 0.0F;
        if (s.spot && (s.state == READY || s.state == BURNING || s.state == COOLING)) {
            Orbit.Ground g = Orbit.ground(orbit.getServer(), slot);
            Vec3 l = StationLaser.toLocal(m.facing(), new Vec3(s.sx - g.x(), 0.0, s.sz - g.z()));
            tx = (float) Math.toDegrees(Math.atan2(l.x, Balance.PLANET_ALT));
            tz = (float) Math.toDegrees(Math.atan2(l.z, Balance.PLANET_ALT));
        }
        be.setAim(tx, tz);
    }

    private static void warn(MinecraftServer server, int slot, St s) {
        ServerLevel ground = server.getLevel(Orbit.ground(server, slot).dim());
        if (ground == null) return;
        long r2 = (long) Balance.MIRROR_WARN_R * Balance.MIRROR_WARN_R;
        boolean burn = s.state == BURNING;
        Component bar = Component.translatable(burn ? "message.wmd.mirror.bar_burn" : "message.wmd.mirror.bar_pilot", (int) s.sx, (int) s.sz)
                .withStyle(burn ? ChatFormatting.RED : ChatFormatting.GOLD);
        for (ServerPlayer p : ground.players()) {
            double dx = p.getX() - s.sx, dz = p.getZ() - s.sz;
            if (dx * dx + dz * dz > r2) continue;
            p.displayClientMessage(bar, true);
            if (burn && s.warned.add(p.getUUID())) {
                p.sendSystemMessage(Component.translatable("message.wmd.mirror.warn", (int) s.sx, (int) s.sz).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                CcBattleLog.add(server, p.getUUID(), CcBattleLog.LAUNCH_IN, "log.wmd.mirror.warn", Integer.toString((int) s.sx), Integer.toString((int) s.sz));
            }
        }
    }

    static double[] burningSpot(int slot) {
        St s = ST.get(slot);
        return s != null && s.state == BURNING && s.spot ? new double[] { s.sx, s.sz } : null;
    }

    static List<double[]> spots() {
        List<double[]> out = new java.util.ArrayList<>();
        for (Map.Entry<Integer, St> e : ST.entrySet()) {
            St s = e.getValue();
            if (s.spot && s.goal && (s.state == READY || s.state == BURNING)) out.add(new double[] { e.getKey(), s.state, s.sx, s.sz, s.t0 });
        }
        return out;
    }

    private static void tellOwner(MinecraftServer server, int slot, Component c) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        ServerPlayer p = o == null ? null : server.getPlayerList().getPlayer(o);
        if (p != null) p.sendSystemMessage(c);
    }
}
