package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class AsteroidDrop {

    private AsteroidDrop() {}

    public static Vec3 bearing(long seed) {
        double a = (seed >>> 8 & 0xFFFF) / 65536.0 * Math.PI * 2.0;
        return new Vec3(Math.cos(a), 0.0, Math.sin(a));
    }

    public static Vec3 fallPos(Vec3 t, long seed, double left) {
        double u = Math.max(0.0, Math.min(1.0, 1.0 - left / Balance.ASTEROID_FALL_TICKS));
        double d = Balance.ASTEROID_ENTRY_ALT * Math.pow(1.0 - u, 1.4);
        return t.add(bearing(seed).scale(d)).add(0.0, d, 0.0);
    }

    public static Vec3 scatter(long seed, int i, int n) {
        if (n <= 1) return Vec3.ZERO;
        long h = seed * 31L + i * 0x9E3779B97F4A7C15L;
        double a = (h >>> 16 & 0xFFFF) / 65536.0 * 0.6 + i * Math.PI * 2.0 / n, f = (h >>> 32 & 0xFFFF) / 65536.0;
        double d = Balance.ASTEROID_SCATTER_MIN + (Balance.ASTEROID_SCATTER_MAX - Balance.ASTEROID_SCATTER_MIN) * f;
        return new Vec3(Math.cos(a) * d, 0.0, Math.sin(a) * d);
    }

    public static Vec3 piecePos(Vec3 t, long seed, double left, int i, int n, double splitLeft) {
        Vec3 p = fallPos(t, seed, left);
        if (n <= 1 || splitLeft <= 0) return p;
        double k = AsteroidRedirect.smooth(1.0 - left / splitLeft);
        return p.add(scatter(seed, i, n).scale(k));
    }

    static int tier(int pieces) { return pieces <= 1 ? 4 : pieces == 2 ? 3 : pieces <= 4 ? 2 : 1; }

    static Component target(ServerPlayer sp, int slot, Station.Module m, int[] t, int x, int z) {
        Station.Data d = Station.Data.get(sp.server);
        if (t[AsteroidBay.P_STATE] != AsteroidBay.PUSH && t[AsteroidBay.P_STATE] != AsteroidBay.PARKED) return Component.translatable("message.wmd.abay.no_rock").withStyle(ChatFormatting.YELLOW);
        Orbit.Ground g = Orbit.ground(sp.server, slot);
        if (Math.abs(x - g.x()) > Balance.ASTEROID_TARGET_R || Math.abs(z - g.z()) > Balance.ASTEROID_TARGET_R)
            return Component.translatable("message.wmd.rod.out_of_view", Balance.ASTEROID_TARGET_R).withStyle(ChatFormatting.YELLOW);
        long[] f = d.bayT.computeIfAbsent(m.id(), k -> new long[AsteroidRedirect.T_N]);
        f[AsteroidRedirect.T_TX] = x; f[AsteroidRedirect.T_TZ] = z; f[AsteroidRedirect.T_AIM] = 1;
        d.setDirty();
        return Component.translatable("message.wmd.abay.target", x, z).withStyle(ChatFormatting.AQUA);
    }

    static Component drop(ServerPlayer sp, int slot, Station.Module m, int[] t) {
        MinecraftServer server = sp.server;
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Data d = Station.Data.get(server);
        long[] f = d.bayT.get(m.id());
        if (orbit == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.PARKED) return Component.translatable("message.wmd.abay.not_parked").withStyle(ChatFormatting.YELLOW);
        if (f[AsteroidRedirect.T_AIM] != 1) return Component.translatable("message.wmd.abay.no_target").withStyle(ChatFormatting.YELLOW);
        Orbit.Ground g = Orbit.ground(server, slot);
        int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ];
        if (Math.abs(tx - g.x()) > Balance.ASTEROID_TARGET_R || Math.abs(tz - g.z()) > Balance.ASTEROID_TARGET_R)
            return Component.translatable("message.wmd.rod.out_of_view", Balance.ASTEROID_TARGET_R).withStyle(ChatFormatting.YELLOW);
        long now = orbit.getGameTime();
        t[AsteroidBay.P_STATE] = AsteroidBay.FALL;
        forget(slot);
        f[AsteroidRedirect.T_T0] = now; f[AsteroidRedirect.T_PIECES] = 1; f[AsteroidRedirect.T_SPLIT] = 0;
        d.setDirty();
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        if (owner != null) {
            for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.setDefcon(1);
            CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_OUT, "log.wmd.abay.dropped", Integer.toString(tx), Integer.toString(tz));
        }
        for (ServerPlayer w : AsteroidRedirect.watchers(server, slot))
            w.sendSystemMessage(Component.translatable("message.wmd.abay.dropped", tx, tz, Balance.ASTEROID_FALL_TICKS / 1200).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        AsteroidRedirect.sync(server, slot, m);

        net.minecraft.core.BlockPos bc = Station.middle(m), ctl = Station.controller(m);
        for (ServerPlayer op : orbit.players())
            if (op.blockPosition().distSqr(bc) < (double) Balance.ROD_RELEASE_CAM_R * Balance.ROD_RELEASE_CAM_R)
                PacketDistributor.sendToPlayer(op, new CutscenePayload(CutscenePayload.KIND_CAM, 0.0F, 0.0F, 0.0F,
                        ctl.getX() + 0.5F, ctl.getY() + 0.5F, ctl.getZ() + 0.5F, 0, Balance.ASTEROID_DROP_CAM_TICKS, ""));
        Wmd.LOGGER.info("[asteroid] slot {} drops its rock ({} blocks) on {} {}", slot, f[AsteroidRedirect.T_SIZE], tx, tz);
        return null;
    }

    private static final Set<Integer> STREAMING = new HashSet<>();

    private static final Map<Integer, long[]> RAMP = new HashMap<>();

    private static final Map<Integer, double[]> RAMP_R = new HashMap<>();

    private static final Set<Integer> WARN = new HashSet<>();

    private static final Set<Integer> SOON = new HashSet<>();

    private static final Map<Long, Long> SITE_SHOT = new HashMap<>();

    private static final List<Object[]> PENDING = new ArrayList<>();

    static long key(int slot) { return 0x41535444L << 32 | (slot & 0xFFFFFFFFL); }

    static void clearStatics() { STREAMING.clear(); RAMP.clear(); RAMP_R.clear(); WARN.clear(); SOON.clear(); SITE_SHOT.clear(); PENDING.clear(); WAVES.clear(); WAVE_DIMS.clear(); }

    static void tick(ServerLevel orbit, Station.Data d, int slot, Station.Module m, int[] t, long[] f) {
        MinecraftServer server = orbit.getServer();
        ServerLevel sl = server.getLevel(Orbit.ground(server, slot).dim());
        long now = orbit.getGameTime(), el = now - f[AsteroidRedirect.T_T0], left = Balance.ASTEROID_FALL_TICKS - el;
        if (sl == null) return;
        int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ];
        if (left <= 0) { impact(orbit, sl, d, slot, m, t, f); return; }
        if (!ForcefieldBlockEntity.LOADED.isEmpty() && sl.hasChunk(tx >> 4, tz >> 4)) {
            Vec3 tgt = new Vec3(tx + 0.5, sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz), tz + 0.5);

            int pieces = (int) Math.max(1, f[AsteroidRedirect.T_PIECES]);

            double splitLeft = f[AsteroidRedirect.T_SPLIT] == 0 ? 0.0 : Balance.ASTEROID_FALL_TICKS - (f[AsteroidRedirect.T_SPLIT] - f[AsteroidRedirect.T_T0]);
            double was = Math.min(Balance.ASTEROID_FALL_TICKS, left + 20);
            java.util.Set<ForcefieldBlockEntity> hitDomes = new java.util.HashSet<>();
            for (int i = 0; i < pieces && tier(pieces) > 1; i++) {
                Forcefield.Crossing c = Forcefield.crossing(sl, piecePos(tgt, f[AsteroidRedirect.T_SEED], was, i, pieces, splitLeft), piecePos(tgt, f[AsteroidRedirect.T_SEED], left, i, pieces, splitLeft));
                if (c != null && hitDomes.add(c.be())) Forcefield.shellHit(sl, c, tier(pieces), WarheadType.ASTEROID);
            }
        }
        long gnow = sl.getGameTime(), impactG = gnow + left;
        if (el < 20 || WARN.remove(slot)) {
            warnAll(server, sl, slot, tx, tz, gnow, impactG, left);
            MusicTriggersHook.fire(sl, "wmd:asteroid_incoming", new BlockPos(tx, groundY(sl, tx, tz), tz));
        }
        if (left <= MusicTriggersHook.IMPACT_SOON_TICKS && SOON.add(slot)) MusicTriggersHook.fire(sl, "wmd:asteroid_impact_soon", new BlockPos(tx, groundY(sl, tx, tz), tz));
        if (el % 100 < 20) sendFall(sl, slot, f, 1, impactG, false);
        if (left <= Balance.ASTEROID_INT_WINDOW && left % 400 < 20) for (ServerPlayer p : sl.players()) AlarmNet.raiseAll(sl, p.getUUID());

        if ((STREAMING.add(slot) || el % 200 < 20) && !TargetSnapshot.has(key(slot)))
            TargetSnapshot.start(sl, key(slot), tx, tz, null, Balance.ASTEROID_SNAP_R, Balance.ASTEROID_SNAP_FAR_R);
        double full = WarheadFx.holdRadius(4, WarheadType.ASTEROID) + Balance.ASTEROID_SCATTER_MAX * 0.5;

        long[] r0 = RAMP.computeIfAbsent(slot, k -> new long[] { el, left });
        double step = Math.max(Balance.RG_HOLD_RAMP_STEP, (full - Balance.RG_HOLD_RAMP_START) / Math.max(1.0, (r0[1] - 1_200) / (double) Balance.RG_HOLD_RAMP_TICKS));
        if (el % Balance.RG_HOLD_RAMP_TICKS < 20) {
            double[] cur = RAMP_R.computeIfAbsent(slot, k -> new double[] { Balance.RG_HOLD_RAMP_START });
            double mspt = server.getAverageTickTimeNanos() / 1.0E6;
            if (cur[0] < full && mspt < Balance.RAMP_MSPT && loaded(sl, tx, tz, cur[0] * 0.92) >= Balance.RAMP_LOADED) {
                double cap = Math.sqrt(cur[0] * cur[0] + Balance.RAMP_STEP_CHUNKS * 256.0 / Math.PI) - cur[0];
                cur[0] = Math.min(full, cur[0] + Math.min(step, cap));
            }
            FlightManager.get(sl).holdForTest(sl, tx, tz, cur[0]);
        }
        if (left <= Balance.ASTEROID_CUT_LEAD && left > Balance.ASTEROID_CUT_LEAD - 20) {
            int ty = groundY(sl, tx, tz);
            Vec3 p = fallPos(new Vec3(tx + 0.5, ty, tz + 0.5), f[AsteroidRedirect.T_SEED], left);
            PacketDistributor.sendToPlayersInDimension(sl, new CutscenePayload(CutscenePayload.KIND_DROP, (float) p.x, (float) p.y, (float) p.z, tx + 0.5F, ty, tz + 0.5F,
                    (int) left, (int) left + Balance.ASTEROID_CUT_AFTER, ""));
        }
        if (left <= Balance.ASTEROID_INT_WINDOW) interceptors(sl, slot, f, left, gnow);
    }

    private static double loaded(ServerLevel sl, int x, int z, double r) {
        int n = 0;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            if (sl.hasChunk((int) Math.floor(x + Math.cos(a) * r) >> 4, (int) Math.floor(z + Math.sin(a) * r) >> 4)) n++;
        }
        return n / 24.0;
    }

    private static int groundY(ServerLevel sl, int x, int z) {
        return sl.hasChunk(x >> 4, z >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : 64;
    }

    private static void warnAll(MinecraftServer server, ServerLevel sl, int slot, int tx, int tz, long gnow, long impactG, long left) {
        String who = AsteroidRedirect.ownerName(server, slot);
        long secs = (left + 19) / 20; String mmss = String.format("%d:%02d", secs / 60, secs % 60);
        for (ServerPlayer p : sl.players()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.wmd.abay.imminent").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.wmd.abay.imminent_sub", tx, tz, mmss).withStyle(ChatFormatting.GOLD)));
            p.sendSystemMessage(Component.translatable("message.wmd.abay.imminent", who, tx, tz, mmss).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            CcBattleLog.add(server, p.getUUID(), CcBattleLog.LAUNCH_IN, "log.wmd.abay.imminent", Integer.toString(tx), Integer.toString(tz));
            CommandCenter.addWarning(server, p.getUUID(), new CommandCenter.Warning(key(slot) ^ p.getUUID().getLeastSignificantBits(), tx, tz, tx, tz, gnow, impactG, false));
            AlarmNet.raiseAll(sl, p.getUUID());
            p.playNotifySound(WmdSounds.AST_ALARM.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    private static void sendFall(ServerLevel sl, int slot, long[] f, int stage, long impactG, boolean sea) {
        int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ];
        long now = sl.getGameTime(), orbitNow = sl.getServer().getLevel(Orbit.ORBIT) == null ? now : sl.getServer().getLevel(Orbit.ORBIT).getGameTime();
        long splitG = f[AsteroidRedirect.T_SPLIT] == 0 ? 0L : now - (orbitNow - f[AsteroidRedirect.T_SPLIT]);
        PacketDistributor.sendToPlayersInDimension(sl, new AsteroidBayPayloads.Fall(key(slot), stage, tx, groundY(sl, tx, tz), tz, impactG, (int) f[AsteroidRedirect.T_SIZE],
                (int) Math.max(1, f[AsteroidRedirect.T_PIECES]), splitG, f[AsteroidRedirect.T_SEED], sea, AsteroidRedirect.ownerName(sl.getServer(), slot)));
    }

    private static void interceptors(ServerLevel sl, int slot, long[] f, long left, long gnow) {
        if (f[AsteroidRedirect.T_PIECES] >= 1 << Balance.ASTEROID_MAX_SPLITS) return;
        UUID dropper = Orbit.OrbitSlots.get(sl.getServer()).ownerOf(slot);
        int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ];
        Vec3 target = new Vec3(tx + 0.5, groundY(sl, tx, tz), tz + 0.5);
        for (InterceptorSite s : CommandCenter.allSites()) {
            if (!(s instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != sl || EmpField.jammed(sl, be.getBlockPos())) continue;
            UUID o = s.getOwner();
            int[] k = Drones.siteKind(s);
            if (k == null || o == null || o.equals(dropper) || s.loaded() <= 0 || s.cooldownTicks() > 0 || s.energy() < Balance.INT_SHOT_FE) continue;
            BlockPos bp = be.getBlockPos();
            if (Math.hypot(bp.getX() - tx, bp.getZ() - tz) > Balance.ASTEROID_INT_R) continue;
            Long last = SITE_SHOT.get(bp.asLong());
            if (last != null && gnow - last < Balance.ASTEROID_INT_EVERY) continue;

            if (s instanceof InterceptorSiloBlockEntity s3) { s3.wakeForShip(gnow); if (s3.lid() < 0.99F) continue; }
            if (s instanceof InterceptorSiloT4BlockEntity s4) { s4.wakeForShip(gnow); if (s4.lid() < 0.99F) continue; }
            if (!Drones.takeOne(s.magazineHandler())) continue;
            s.shipShot(Balance.INT_SHOT_FE, Balance.INT_RELOAD_TICKS);
            SITE_SHOT.put(bp.asLong(), gnow);
            Vec3 from = s instanceof InterceptorSiloBlockEntity s3m ? s3m.mouth() : s instanceof InterceptorSiloT4BlockEntity s4m ? s4m.mouth() : Vec3.atCenterOf(bp).add(0.0, 2.0, 0.0);
            int rise = s instanceof InterceptorSiloBlockEntity ? Balance.SILO3_RISE_TICKS : s instanceof InterceptorSiloT4BlockEntity ? Balance.SILO4_RISE_TICKS : 0;
            Vec3 aim = fallPos(target, f[AsteroidRedirect.T_SEED], Math.max(0.0, left - rise - 200.0));
            int dur = (int) Math.max(40, Math.min(400, Math.min(left - rise - 20, from.distanceTo(aim) / Balance.INT_SPEED[k[0] - 1])));
            Vec3 to = fallPos(target, f[AsteroidRedirect.T_SEED], Math.max(0.0, left - rise - dur));
            PacketDistributor.sendToPlayersNear(sl, null, to.x, to.y, to.z, 8_000, new InterceptPayload(FlightManager.get(sl).nextShotId(), from.x, from.y, from.z, to.x, to.y, to.z, gnow + rise, dur, k[1]));
            PENDING.add(new Object[] { slot, gnow + rise + dur, s instanceof Owned ow ? ow.getOwnerName() : CcIntel.name(sl.getServer(), o) });
            Wmd.LOGGER.info("[asteroid] interceptor (tier {}) of {} fired at the rock of slot {}", k[0], o, slot);
        }
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!PENDING.isEmpty()) for (Iterator<Object[]> it = PENDING.iterator(); it.hasNext(); ) {
            Object[] p = it.next();
            int slot = (int) p[0];
            if (sl.getServer().getLevel(Orbit.ground(sl.getServer(), slot).dim()) != sl || sl.getGameTime() < (long) p[1]) continue;
            it.remove();
            if (split(sl.getServer(), slot, (String) p[2])) Wmd.LOGGER.info("[asteroid] an interceptor broke the rock of slot {}", slot);
        }
        if (!WAVES.isEmpty() && sl.getGameTime() % 5 == 0) waves(sl);
    }

    static int[] fallingOver(MinecraftServer server, int defender) {
        Station.Data d = Station.Data.get(server);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null) return null;
        Orbit.Ground g = Orbit.ground(server, defender);
        for (int s : d.slots.keySet()) {
            if (s == defender) continue;
            Station.Module m = AsteroidBay.module(server, s);
            int[] t = m == null ? null : d.bay.get(m.id());
            long[] f = m == null ? null : d.bayT.get(m.id());
            if (t == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.FALL || f[AsteroidRedirect.T_PIECES] >= 1 << Balance.ASTEROID_MAX_SPLITS) continue;
            if (Balance.ASTEROID_FALL_TICKS - (orbit.getGameTime() - f[AsteroidRedirect.T_T0]) > Balance.ASTEROID_INT_WINDOW) continue;
            if (Orbit.ground(server, s).dim() != g.dim()) continue;
            int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ];
            if (Math.abs(tx - g.x()) > Balance.SENSOR_GROUND_R || Math.abs(tz - g.z()) > Balance.SENSOR_GROUND_R) continue;
            return new int[] { s, tx, tz };
        }
        return null;
    }

    static boolean split(MinecraftServer server, int slot, String by) {
        Station.Data d = Station.Data.get(server);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module m = AsteroidBay.module(server, slot);
        int[] t = m == null ? null : d.bay.get(m.id());
        long[] f = m == null ? null : d.bayT.get(m.id());
        if (orbit == null || t == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.FALL || f[AsteroidRedirect.T_PIECES] >= 1 << Balance.ASTEROID_MAX_SPLITS) return false;
        f[AsteroidRedirect.T_PIECES] = Math.max(1, f[AsteroidRedirect.T_PIECES]) * 2;
        f[AsteroidRedirect.T_SPLIT] = orbit.getGameTime();
        d.setDirty();
        int n = (int) f[AsteroidRedirect.T_PIECES];
        ServerLevel sl = server.getLevel(Orbit.ground(server, slot).dim());
        if (sl != null) MusicTriggersHook.fire(sl, "wmd:asteroid_split", new BlockPos((int) f[AsteroidRedirect.T_TX], groundY(sl, (int) f[AsteroidRedirect.T_TX], (int) f[AsteroidRedirect.T_TZ]), (int) f[AsteroidRedirect.T_TZ]));
        if (sl != null) {
            long left = Balance.ASTEROID_FALL_TICKS - (orbit.getGameTime() - f[AsteroidRedirect.T_T0]);
            sendFall(sl, slot, f, 1, sl.getGameTime() + left, false);
            for (ServerPlayer p : sl.players()) {
                p.sendSystemMessage(Component.translatable("message.wmd.abay.split", by, n).withStyle(ChatFormatting.GOLD));
                p.playNotifySound(WmdSounds.AST_SPLIT_FAR.get(), net.minecraft.sounds.SoundSource.MASTER, 0.9F, 1.0F);
            }
        }
        for (ServerPlayer w : AsteroidRedirect.watchers(server, slot)) w.sendSystemMessage(Component.translatable("message.wmd.abay.split_owner", by, n).withStyle(ChatFormatting.RED));
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        if (o != null) CcBattleLog.add(server, o, CcBattleLog.LOST, "log.wmd.abay.split", by, Integer.toString(n));
        return true;
    }

    private static void impact(ServerLevel orbit, ServerLevel sl, Station.Data d, int slot, Station.Module m, int[] t, long[] f) {
        MinecraftServer server = orbit.getServer();
        int tx = (int) f[AsteroidRedirect.T_TX], tz = (int) f[AsteroidRedirect.T_TZ], n = (int) Math.max(1, f[AsteroidRedirect.T_PIECES]);

        int[] pts = new int[2 * n + 2];
        pts[0] = tx; pts[1] = tz;
        for (int i = 0; i < n; i++) { Vec3 o = scatter(f[AsteroidRedirect.T_SEED], i, n); pts[2 + 2 * i] = tx + (int) Math.round(o.x); pts[3 + 2 * i] = tz + (int) Math.round(o.z); }
        long late = orbit.getGameTime() - f[AsteroidRedirect.T_T0] - Balance.ASTEROID_FALL_TICKS;
        if (!WarheadFx.impactReady(sl, FlightManager.get(sl), pts) && late < WarheadFx.IMPACT_WAIT_TICKS) return;
        STREAMING.remove(slot); RAMP.remove(slot); RAMP_R.remove(slot); SOON.remove(slot);
        TargetSnapshot.stopIn(key(slot), sl, Balance.ASTEROID_CUT_AFTER + 200);
        int top = sl.getHeight(Heightmap.Types.WORLD_SURFACE, tx, tz), floor = sl.getHeight(Heightmap.Types.OCEAN_FLOOR, tx, tz);
        boolean sea = top - floor >= 4 && sl.getFluidState(new BlockPos(tx, top - 1, tz)).is(net.minecraft.tags.FluidTags.WATER);
        FlightManager mgr = FlightManager.get(sl);
        int tier = tier(n);
        for (int i = 0; i < n; i++) {
            Vec3 o = scatter(f[AsteroidRedirect.T_SEED], i, n);
            int px = tx + (int) Math.round(o.x), pz = tz + (int) Math.round(o.z);
            int py = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            mgr.holdForTest(sl, px, pz, WarheadFx.holdRadius(tier, WarheadType.ASTEROID));
            WarheadFx.detonate(sl, mgr, px, py, pz, tier, WarheadType.ASTEROID, 0.0, 1.0);
        }
        Wmd.LOGGER.info("[asteroid] the rock of slot {} strikes {} {} ({} piece(s), tier {}, sea {})", slot, tx, tz, n, tier, sea);
        double rr = Missiles.craterRadius(tier) * WarheadType.ASTEROID.crater;
        StrikeLog.report(sl, Orbit.OrbitSlots.get(server).ownerOf(slot), n > 1 ? "log.wmd.strike.asteroid_split" : "log.wmd.strike.asteroid", tx, tz, rr, rr * WarheadType.ASTEROID.depth);
        MusicTriggersHook.fire(sl, "wmd:asteroid_impact", new BlockPos(tx, top, tz));
        sendFall(sl, slot, f, 3, sl.getGameTime(), sea);
        if (sea) { WAVES.add(new double[] { tx + 0.5, top, tz + 0.5, sl.getGameTime() }); WAVE_DIMS.add(sl.dimension()); }

        t[AsteroidBay.P_STATE] = AsteroidBay.NONE;
        t[AsteroidBay.P_AM] = 0; t[AsteroidBay.P_DT] = 0; t[AsteroidBay.P_CORE] = 0; t[AsteroidBay.P_PROG] = 0;
        d.bayT.remove(m.id());
        d.setDirty();
        for (ServerPlayer w : AsteroidRedirect.watchers(server, slot)) w.sendSystemMessage(Component.translatable("message.wmd.abay.impact", tx, tz, n).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        AsteroidRedirect.sync(server, slot, m);
    }

    private static final List<double[]> WAVES = new ArrayList<>();
    private static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> WAVE_DIMS = new ArrayList<>();

    private static void waves(ServerLevel sl) {
        Iterator<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> dims = WAVE_DIMS.iterator();
        for (Iterator<double[]> it = WAVES.iterator(); it.hasNext(); ) {
            double[] w = it.next();
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = dims.next();
            if (dim != sl.dimension()) continue;
            double r = (sl.getGameTime() - w[3]) * Balance.ASTEROID_SEA_WAVE_SPEED;
            if (r > Balance.ASTEROID_SEA_WAVE_R) { it.remove(); dims.remove(); continue; }
            AABB box = new AABB(w[0] - r - 8, w[1] - 6, w[2] - r - 8, w[0] + r + 8, w[1] + 12, w[2] + r + 8);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box)) {
                double dx = e.getX() - w[0], dz = e.getZ() - w[2], dd = Math.sqrt(dx * dx + dz * dz);
                if (dd < r - 8 || dd > r + 8 || dd < 1e-3) continue;
                double k = 1.6 * (1.0 - r / Balance.ASTEROID_SEA_WAVE_R) + 0.3;
                e.setDeltaMovement(e.getDeltaMovement().add(dx / dd * k, 0.5 * k, dz / dd * k));
                e.hurtMarked = true;
            }
        }
    }

    static void forget(int slot) {
        STREAMING.remove(slot); RAMP.remove(slot); RAMP_R.remove(slot); WARN.remove(slot); SOON.remove(slot);
        TargetSnapshot.stop(key(slot));
    }

    static void aborted(ServerLevel orbit, int slot, int[] t, long[] f) {
        if (t == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.FALL) return;
        MinecraftServer server = orbit.getServer();
        forget(slot);
        ServerLevel sl = server.getLevel(Orbit.ground(server, slot).dim());
        if (sl != null) sendFall(sl, slot, f, 4, sl.getGameTime(), false);
        long k = key(slot);
        CommandCenter.removeWarnings((owner, w) -> w.id() == (k ^ owner.getLeastSignificantBits()));
        Wmd.LOGGER.info("[asteroid] slot {}: the bay broke off during the fall: no impact", slot);
    }

    static String testDrop(MinecraftServer server, int slot, int x, int z, int seconds) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module m = AsteroidBay.module(server, slot);
        if (orbit == null || m == null) return "Your station has no asteroid bay (in orbit: /wmd station spawn bay)";
        Station.Data d = Station.Data.get(server);
        int[] t = d.bay.computeIfAbsent(m.id(), k -> new int[AsteroidBay.P_N]);
        if (t[AsteroidBay.P_STATE] == AsteroidBay.FALL) return "A rock is falling already";
        long[] f = d.bayT.computeIfAbsent(m.id(), k -> new long[AsteroidRedirect.T_N]);
        if (f[AsteroidRedirect.T_SIZE] <= 0) f[AsteroidRedirect.T_SIZE] = (Balance.ASTEROID_SIZE_MIN + Balance.ASTEROID_SIZE_MAX) / 2;
        if (f[AsteroidRedirect.T_SEED] == 0) f[AsteroidRedirect.T_SEED] = orbit.getRandom().nextLong();
        f[AsteroidRedirect.T_TX] = x; f[AsteroidRedirect.T_TZ] = z; f[AsteroidRedirect.T_AIM] = 1;
        f[AsteroidRedirect.T_PIECES] = 1; f[AsteroidRedirect.T_SPLIT] = 0; f[AsteroidRedirect.T_HITS] = 0;
        f[AsteroidRedirect.T_T0] = orbit.getGameTime() - (Balance.ASTEROID_FALL_TICKS - seconds * 20L);
        t[AsteroidBay.P_STATE] = AsteroidBay.FALL;
        forget(slot);
        TargetSnapshot.stop(key(slot));
        WARN.add(slot);
        d.setDirty();
        AsteroidRedirect.sync(server, slot, m);
        Wmd.LOGGER.info("[asteroid] TEST drop: slot {} on {} {} in {} s", slot, x, z, seconds);
        return null;
    }
}
