package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class RodOfDeath {

    private RodOfDeath() {}

    public static final int IDLE = 0, ARM = 1, LOAD = 2, SWING = 3, SPIN = 4, FINAL = 5, FALL = 6, COOL = 7, BACK = 8;
    public static final int CMD_TARGET = 0, CMD_ARM = 1, CMD_STAND_DOWN = 2;

    private static final class St {
        int state; long t0; int tx, tz, ty; boolean target, burned;
        long waitFrom = -1;
        int[] pieces;
        int from; long fromEl; long coolLen = Balance.ROD_COOL_TICKS;
        final Set<UUID> warned = new HashSet<>();
    }

    private static final Map<Integer, St> ST = new HashMap<>();

    static void clearStatics() { ST.clear(); }

    static boolean busy(int slot) { St s = ST.get(slot); return s != null && s.state != IDLE; }

    public static int len(int state) {
        return switch (state) {
            case ARM -> Balance.ROD_ARM_TICKS; case LOAD -> Balance.ROD_LOAD_TICKS; case SWING -> Balance.ROD_SWING_TICKS; case SPIN -> Balance.ROD_SPIN_TICKS;
            case FINAL -> Balance.ROD_FINAL_TICKS; case FALL -> Balance.ROD_FALL_TICKS; case COOL -> Balance.ROD_COOL_TICKS; case BACK -> Balance.ROD_BACK_TICKS;
            default -> 1;
        };
    }

    static long toImpact(int state) {
        long t = 0;
        for (int s = state; s >= ARM && s <= FALL; s++) t += len(s);
        return t;
    }

    static Station.Module module(MinecraftServer server, int slot) {
        for (Station.Module m : Station.modules(server, slot)) if (Station.ROD.equals(m.type()) && m.built()) return m;
        return null;
    }

    static int[] status(MinecraftServer server, int slot, long now) {
        Station.Module m = module(server, slot);
        St s = ST.get(slot);
        int tx = s == null ? 0 : s.tx, tz = s == null ? 0 : s.tz, set = s != null && s.target ? 1 : 0;
        if (m == null) return new int[] { -1, 0, -1, set, tx, tz };
        if (Station.wrecked(server, m)) return new int[] { -2, 0, -1, set, tx, tz };
        if (s == null) return new int[] { IDLE, 0, -1, 0, 0, 0 };
        long el = now - s.t0;
        int pct = (int) Math.max(0, Math.min(100, 100 * el / Math.max(1, s.state == COOL ? s.coolLen : len(s.state))));
        int secs = s.state >= ARM && s.state <= FALL ? (int) Math.max(0, (toImpact(s.state) - el) / 20) : -1;
        return new int[] { s.state, pct, secs, set, tx, tz };
    }

    static Component command(ServerPlayer p, int slot, int cmd, int x, int z) {
        MinecraftServer server = p.server;
        if (!p.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) return Component.translatable("message.wmd.rod.not_owner");
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Module m = module(server, slot);
        if (m == null || orbit == null) return Component.translatable("message.wmd.rod.none");
        if (Station.wrecked(server, m)) return Component.translatable("message.wmd.rod.wrecked");
        St s = ST.computeIfAbsent(slot, k -> new St());
        long now = orbit.getGameTime();
        Station.Data d = Station.Data.get(server);
        switch (cmd) {
            case CMD_TARGET -> {
                if (s.state != IDLE && s.state != COOL) return Component.translatable("message.wmd.rod.busy");
                Orbit.Ground g = Orbit.ground(server, slot);
                if (Math.abs(x - g.x()) > Balance.ROD_TARGET_R || Math.abs(z - g.z()) > Balance.ROD_TARGET_R)
                    return Component.translatable("message.wmd.rod.out_of_view", Balance.ROD_TARGET_R);
                s.tx = x; s.tz = z; s.target = true;
                return Component.translatable("message.wmd.rod.target", x, z).withStyle(ChatFormatting.AQUA);
            }
            case CMD_ARM -> {
                if (s.state != IDLE) return Component.translatable(s.state == COOL ? "message.wmd.rod.cooling" : "message.wmd.rod.busy");
                if (!s.target) return Component.translatable("message.wmd.rod.no_target");
                int rods = d.rods.getOrDefault(m.id(), 0);
                if (rods <= 0) return Component.translatable("message.wmd.rod.no_rod");
                if (!StationLife.powered(slot)) return Component.translatable("message.wmd.rod.no_power_now");
                long buf = d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2);
                if (buf < Balance.ROD_SHOT_FE) return Component.translatable("message.wmd.rod.no_power", String.format("%,d", Balance.ROD_SHOT_FE / 1000));
                Orbit.Ground g = Orbit.ground(server, slot);
                if (Math.abs(s.tx - g.x()) > Balance.ROD_TARGET_R || Math.abs(s.tz - g.z()) > Balance.ROD_TARGET_R)
                    return Component.translatable("message.wmd.rod.out_of_view", Balance.ROD_TARGET_R);
                d.energy.put(slot, buf - Balance.ROD_SHOT_FE);
                d.rods.put(m.id(), rods - 1);
                d.rodArmed.put(slot, m.id());
                d.setDirty();
                s.warned.clear();
                s.burned = false;
                set(orbit, m, s, ARM, now);
                ground(server, s, RailgunEventPayload.CHARGING, now);
                orbit.playSound(null, Station.middle(m), WmdSounds.RD_ARM.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                StationLife.pa(orbit, slot, WmdSounds.PA_ROD_ARMED.get());
                UUID owner = p.getUUID();
                CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_OUT, "log.wmd.rod.armed", Integer.toString(s.tx), Integer.toString(s.tz));
                Wmd.LOGGER.info("[rod] {} arms the rod of death of slot {} at {} {}", p.getGameProfile().getName(), slot, s.tx, s.tz);
                Station.offerCamera(p, m);
                return Component.translatable("message.wmd.rod.armed", s.tx, s.tz).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            }
            case CMD_STAND_DOWN -> {
                if (s.state < ARM || s.state > FINAL) return Component.translatable("message.wmd.rod.not_armed");
                standDown(orbit, slot, m, s, now);
                return Component.translatable("message.wmd.rod.stood_down");
            }
            default -> { return null; }
        }
    }

    private static void standDown(ServerLevel orbit, int slot, Station.Module m, St s, long now) {
        Station.Data d = Station.Data.get(orbit.getServer());
        d.rods.put(m.id(), Math.min(Balance.ROD_RACK_MAX, d.rods.getOrDefault(m.id(), 0) + 1));
        d.rodArmed.remove(slot);
        d.setDirty();
        if (s.state >= ARM && s.state <= FINAL) ground(orbit.getServer(), s, RailgunEventPayload.ABORTED, now);
        if (s.state == FINAL) TargetSnapshot.stop(rodKey(slot));
        s.from = s.state; s.fromEl = now - s.t0;
        set(orbit, m, s, BACK, now);
        orbit.playSound(null, Station.middle(m), WmdSounds.RD_ABORT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
    }

    private static void set(ServerLevel orbit, Station.Module m, St s, int state, long t0) {
        s.state = state; s.t0 = t0;
        if (orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setRod(state, t0, s.from, s.fromEl, s.coolLen);
    }

    private static void restore(ServerLevel orbit) {
        MinecraftServer server = orbit.getServer();
        Station.Data d = Station.Data.get(server);
        if (d.rodArmed.isEmpty()) return;
        for (var it = d.rodArmed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            St s = ST.get(e.getKey());
            if (s != null && s.state >= ARM && s.state <= FINAL) continue;
            it.remove();
            d.rods.put(e.getValue(), Math.min(Balance.ROD_RACK_MAX, d.rods.getOrDefault(e.getValue(), 0) + 1));
            Station.Module m = Station.byId(server, e.getValue());
            if (m != null && orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setRod(IDLE, orbit.getGameTime(), 0, 0L, Balance.ROD_COOL_TICKS);
            Wmd.LOGGER.info("[rod] slot {}: a rod was being prepared when the server stopped: back in the rack", e.getKey());
        }
        d.setDirty();
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel orbit) || !Orbit.isOrbit(orbit)) return;
        long now = orbit.getGameTime();
        if (now % 100 == 0) restore(orbit);
        if (ST.isEmpty()) return;
        MinecraftServer server = orbit.getServer();
        for (Map.Entry<Integer, St> e : ST.entrySet()) {
            int slot = e.getKey();
            St s = e.getValue();
            if (s.state == IDLE) continue;
            Station.Module m = module(server, slot);
            boolean prep = s.state >= ARM && s.state <= FINAL;
            if (m == null || Station.wrecked(server, m)) {
                if (s.state == FALL) {
                    if (now - s.t0 >= Balance.ROD_FALL_TICKS && impact(server, slot, s)) s.state = IDLE;
                    continue;
                }
                if (prep || s.state == BACK) tellOwner(server, slot, Component.translatable("message.wmd.rod.lost").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                if (prep) ground(server, s, RailgunEventPayload.ABORTED, now);
                if (s.state == FINAL) TargetSnapshot.stop(rodKey(slot));
                if (prep) { Station.Data.get(server).rodArmed.remove(slot); Station.Data.get(server).setDirty(); }
                s.state = IDLE;
                if (m != null && orbit.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be) be.setRod(IDLE, now, 0, 0L, s.coolLen);
                continue;
            }
            if (prep && !StationLife.powered(slot)) {
                tellOwner(server, slot, Component.translatable("message.wmd.rod.power_lost").withStyle(ChatFormatting.GOLD));
                standDown(orbit, slot, m, s, now);
                continue;
            }
            long el = now - s.t0;
            sounds(orbit, m, s, el);
            if ((s.state == FINAL || s.state == FALL) && el % Balance.RG_HOLD_RAMP_TICKS == 0) holdRamp(server, slot, s, s.state == FINAL ? el : Balance.ROD_FINAL_TICKS);
            if (s.state >= ARM && s.state <= SPIN && el % 100 == 0) ground(server, s, RailgunEventPayload.CHARGING, now);
            if (s.state == FINAL && el % 100 == 0) ground(server, s, RailgunEventPayload.FINAL, now);
            if (s.state == FALL && el % 100 == 0) ground(server, s, s.burned ? RailgunEventPayload.FIRED : RailgunEventPayload.FINAL, now);
            if (s.state == FALL && el > 0 && !s.burned && !ForcefieldBlockEntity.LOADED.isEmpty()) {
                ServerLevel gl = server.getLevel(Orbit.ground(server, slot).dim());
                if (gl != null) Forcefield.verticalHit(gl, s.tx + 0.5, s.tz + 0.5, Balance.ROD_FALL_ALT * (1.0 - (el - 1) / (double) Balance.ROD_FALL_TICKS),
                        Balance.ROD_FALL_ALT * (1.0 - el / (double) Balance.ROD_FALL_TICKS), 4, WarheadType.ORBITAL_ROD);
            }
            long need = s.state == COOL ? s.coolLen : len(s.state);
            if (el < need) continue;
            switch (s.state) {
                case ARM -> set(orbit, m, s, LOAD, now);
                case LOAD -> set(orbit, m, s, SWING, now);
                case SWING -> set(orbit, m, s, SPIN, now);
                case SPIN -> {
                    set(orbit, m, s, FINAL, now);
                    ground(server, s, RailgunEventPayload.FINAL, now);
                    ServerLevel gl = server.getLevel(Orbit.ground(server, slot).dim());
                    if (gl != null) TargetSnapshot.start(gl, rodKey(slot), s.tx, s.tz, null, Balance.ROD_SNAP_R, Balance.ROD_SNAP_FAR_R);
                }
                case FINAL -> release(orbit, slot, m, s, now);
                case FALL -> { if (impact(server, slot, s)) { s.coolLen = Balance.ROD_COOL_TICKS; set(orbit, m, s, COOL, now); } }
                case BACK -> { s.coolLen = Balance.ROD_ABORT_COOL_TICKS; set(orbit, m, s, COOL, now); }
                case COOL -> set(orbit, m, s, IDLE, now);
                default -> {}
            }
        }
    }

    private static void sounds(ServerLevel orbit, Station.Module m, St s, long el) {
        BlockPos c = Station.middle(m);
        int ld = Balance.ROD_LOAD_TICKS, sw = Balance.ROD_SWING_TICKS;
        switch (s.state) {
            case ARM -> { if (el % 60 == 0) orbit.playSound(null, c, WmdSounds.RD_ARM.get(), SoundSource.BLOCKS, 2.5F, 1.0F); }
            case LOAD -> {
                if (el == ld / 10) orbit.playSound(null, c, WmdSounds.RD_DRUM.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                else if (el > ld * 15 / 100 && el < ld * 80 / 100 && el % 80 == 0) orbit.playSound(null, c, WmdSounds.RD_HOIST_LOOP.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
                else if (el == ld * 85 / 100) orbit.playSound(null, c, WmdSounds.RD_CLAMP.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                else if (el == ld * 97 / 100) orbit.playSound(null, c, WmdSounds.RD_LOCK.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
            }
            case SWING -> {
                if (el == 20) orbit.playSound(null, c, WmdSounds.RD_DOOR.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                else if (el == sw / 5 || el == sw / 2) orbit.playSound(null, c, WmdSounds.RD_SWING.get(), SoundSource.BLOCKS, 3.0F, el == sw / 5 ? 0.95F : 1.05F);
                else if (el == sw * 9 / 10) orbit.playSound(null, c, WmdSounds.RD_LOCK.get(), SoundSource.BLOCKS, 3.0F, 0.9F);
            }
            case SPIN -> { if (el % 100 == 0) orbit.playSound(null, c, WmdSounds.RD_SPIN_LOOP.get(), SoundSource.BLOCKS, 3.0F, 0.7F + 0.7F * el / Balance.ROD_SPIN_TICKS); }
            case FINAL -> { if (el % 80 == 0 && el < Balance.ROD_FINAL_TICKS - Balance.ROD_SILENCE_TICKS) orbit.playSound(null, c, WmdSounds.RD_FINAL.get(), SoundSource.BLOCKS, 3.0F, 1.0F); }
            case COOL -> { if (el == 20 || el == 200) orbit.playSound(null, c, WmdSounds.RD_COOL.get(), SoundSource.BLOCKS, 2.5F, el == 20 ? 1.0F : 0.9F); }
            default -> {}
        }
    }

    private static void release(ServerLevel orbit, int slot, Station.Module m, St s, long now) {
        MinecraftServer server = orbit.getServer();
        Station.Data.get(server).rodArmed.remove(slot);
        Station.Data.get(server).setDirty();
        set(orbit, m, s, FALL, now);
        orbit.playSound(null, Station.middle(m), WmdSounds.RD_RELEASE.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        if (owner != null) {
            for (CcBuildingBlockEntity b : CommandCenter.activeBuildings(owner)) b.setDefcon(1);
            CcBattleLog.add(server, owner, CcBattleLog.LAUNCH_OUT, "log.wmd.rod.released", Integer.toString(s.tx), Integer.toString(s.tz));
        }
        tellOwner(server, slot, Component.translatable("message.wmd.rod.released", s.tx, s.tz, Balance.ROD_FALL_TICKS / 20).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        StationLife.tell(orbit, slot, Component.translatable("message.wmd.rod.pa_away", Balance.ROD_FALL_TICKS / 20).withStyle(ChatFormatting.GOLD));
        StationLife.pa(orbit, slot, WmdSounds.PA_ROD_AWAY.get());
        net.minecraft.core.BlockPos mc = Station.middle(m), ctl = Station.controller(m);
        for (net.minecraft.server.level.ServerPlayer op : orbit.players())
            if (op.blockPosition().distSqr(mc) < (double) Balance.ROD_RELEASE_CAM_R * Balance.ROD_RELEASE_CAM_R)
                PacketDistributor.sendToPlayer(op, new CutscenePayload(CutscenePayload.KIND_CAM, 0.0F, 0.0F, 0.0F,
                        ctl.getX() + 0.5F, ctl.getY() + 0.5F, ctl.getZ() + 0.5F, 0, Balance.ROD_RELEASE_CAM_TICKS, ""));
        ground(server, s, RailgunEventPayload.FINAL, now);

        ServerLevel sl = server.getLevel(Orbit.ground(server, slot).dim());
        if (sl == null) return;
        int ty = s.ty == 0 ? 64 : s.ty;
        MusicTriggersHook.fire(sl, "wmd:rod_release", new net.minecraft.core.BlockPos(s.tx, ty, s.tz));
        PacketDistributor.sendToPlayersInDimension(sl, new CutscenePayload(CutscenePayload.KIND_ROD, s.tx + 0.5F, ty + Balance.ROD_FALL_ALT, s.tz + 0.5F,
                s.tx + 0.5F, ty, s.tz + 0.5F, Balance.ROD_FALL_TICKS, Balance.ROD_FALL_TICKS + Balance.ROD_CUT_AFTER, ""));
        long gt = sl.getGameTime();
        for (ServerPlayer p : sl.players()) {
            CommandCenter.addWarning(server, p.getUUID(), new CommandCenter.Warning(rodKey(slot) ^ p.getUUID().getLeastSignificantBits(), s.tx, s.tz, s.tx, s.tz, gt, gt + Balance.ROD_FALL_TICKS, false));
        }
    }

    private static void holdRamp(MinecraftServer server, int slot, St s, long el) {
        ServerLevel sl = server.getLevel(Orbit.ground(server, slot).dim());
        if (sl == null) return;
        double full = WarheadFx.holdRadius(4, WarheadType.ORBITAL_ROD);
        double r = Math.min(full, Balance.RG_HOLD_RAMP_START + (double) Balance.RG_HOLD_RAMP_STEP * (el / Balance.RG_HOLD_RAMP_TICKS));
        FlightManager.get(sl).holdForTest(sl, s.tx, s.tz, r);
    }

    static boolean burn(MinecraftServer server, int slot, String by) {
        St s = ST.get(slot);
        if (s == null || s.state != FALL || s.burned) return false;
        s.burned = true;
        tellOwner(server, slot, Component.translatable("message.wmd.rod.burned", by).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        if (o != null) CcBattleLog.add(server, o, CcBattleLog.LOST, "log.wmd.rod.burned", by);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        ground(server, s, RailgunEventPayload.FIRED, orbit == null ? 0L : orbit.getGameTime());
        Wmd.LOGGER.info("[rod] the rod of slot {} was burned up in its fall by {}", slot, by);
        return true;
    }

    static int[] fallingOver(MinecraftServer server, int defender) {
        Orbit.Ground g = Orbit.ground(server, defender);
        for (Map.Entry<Integer, St> e : ST.entrySet()) {
            St s = e.getValue();
            if (e.getKey() == defender || s.state != FALL || s.burned) continue;
            if (Orbit.ground(server, e.getKey()).dim() != g.dim()) continue;
            if (Math.abs(s.tx - g.x()) > Balance.SENSOR_GROUND_R || Math.abs(s.tz - g.z()) > Balance.SENSOR_GROUND_R) continue;
            return new int[] { e.getKey(), s.tx, s.tz };
        }
        return null;
    }

    private static boolean impact(MinecraftServer server, int slot, St s) {
        Orbit.Ground g = Orbit.ground(server, slot);
        ServerLevel sl = server.getLevel(g.dim());
        if (sl != null) {
            java.util.Random pr = new java.util.Random(s.t0 * 31L + slot);
            int[] pts = new int[s.burned ? 2 * Balance.ROD_BREAK_PIECES : 2];
            if (s.burned) for (int i = 0; i < Balance.ROD_BREAK_PIECES; i++) {
                double a = pr.nextDouble() * Math.PI * 2.0, d = 20.0 + pr.nextDouble() * (Balance.ROD_BREAK_SPREAD - 20.0);
                pts[2 * i] = s.tx + (int) Math.round(Math.cos(a) * d); pts[2 * i + 1] = s.tz + (int) Math.round(Math.sin(a) * d);
            }
            else { pts[0] = s.tx; pts[1] = s.tz; }
            long now = sl.getGameTime();
            if (s.waitFrom < 0) s.waitFrom = now;
            if (!WarheadFx.impactReady(sl, FlightManager.get(sl), pts) && now - s.waitFrom < WarheadFx.IMPACT_WAIT_TICKS) return false;
            s.waitFrom = -1;
            s.pieces = pts;
        }
        ground(server, s, RailgunEventPayload.ENDED, 0L);
        if (sl == null) return true;
        TargetSnapshot.stopIn(rodKey(slot), sl, Balance.ROD_CUT_AFTER + 200);
        FlightManager mgr = FlightManager.get(sl);
        if (s.burned) {
            for (int i = 0; i < Balance.ROD_BREAK_PIECES; i++) {
                int px = s.pieces[2 * i], pz = s.pieces[2 * i + 1];
                int py = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
                mgr.holdForTest(sl, px, pz, WarheadFx.holdRadius(Balance.ROD_BREAK_TIER, WarheadType.HE));
                WarheadFx.detonate(sl, mgr, px, py, pz, Balance.ROD_BREAK_TIER, WarheadType.HE, 0.0, 1.0);
            }
            Wmd.LOGGER.info("[rod] the burned rod of slot {} comes down in {} pieces round {} {}", slot, Balance.ROD_BREAK_PIECES, s.tx, s.tz);
            return true;
        }
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.tx, s.tz);
        Wmd.LOGGER.info("[rod] the rod of slot {} strikes {} {} {}", slot, s.tx, y, s.tz);
        mgr.holdForTest(sl, s.tx, s.tz, WarheadFx.holdRadius(4, WarheadType.ORBITAL_ROD));
        WarheadFx.detonate(sl, mgr, s.tx, y, s.tz, 4, WarheadType.ORBITAL_ROD, 0.0, 1.0);
        double rr = Missiles.craterRadius(4) * WarheadType.ORBITAL_ROD.crater;
        StrikeLog.report(sl, Orbit.OrbitSlots.get(server).ownerOf(slot), "log.wmd.strike.rod", s.tx, s.tz, rr, rr * WarheadType.ORBITAL_ROD.depth);
        MusicTriggersHook.fire(sl, "wmd:rod_impact", new net.minecraft.core.BlockPos(s.tx, y, s.tz));
        return true;
    }

    private static void ground(MinecraftServer server, St s, int stage, long now) {
        int slot = -1;
        for (Map.Entry<Integer, St> e : ST.entrySet()) if (e.getValue() == s) slot = e.getKey();
        if (slot < 0) return;
        Orbit.Ground g = Orbit.ground(server, slot);
        ServerLevel sl = server.getLevel(g.dim());
        if (sl == null) return;
        long impact = s.t0 + toImpact(s.state);
        long impactGround = sl.getGameTime() + Math.max(0L, impact - now);
        if (s.ty == 0 && sl.hasChunkAt(new BlockPos(s.tx, 64, s.tz))) s.ty = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.tx, s.tz);
        int ty = s.ty == 0 ? 64 : s.ty;
        RailgunEventPayload ev = new RailgunEventPayload(rodKey(slot), stage, s.tx, ty, s.tz, impactGround, 0.0F, 0.0F, 0.0F, "", RailgunEventPayload.KIND_ROD);

        for (ServerPlayer p : sl.players()) {
            PacketDistributor.sendToPlayer(p, ev);
            if (stage != RailgunEventPayload.FINAL) continue;
            AlarmNet.raiseAll(sl, p.getUUID());
            if (!s.warned.add(p.getUUID())) continue;
            long secs = Math.max(0L, (impact - now) / 20);
            String mmss = String.format("%d:%02d", secs / 60, secs % 60);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.wmd.rod.strike").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.wmd.rod.strike_sub", s.tx, s.tz, mmss).withStyle(ChatFormatting.GOLD)));
            p.sendSystemMessage(Component.translatable("message.wmd.rod.incoming", s.tx, s.tz, mmss).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            CcBattleLog.add(server, p.getUUID(), CcBattleLog.LAUNCH_IN, "log.wmd.rod.incoming", Integer.toString(s.tx), Integer.toString(s.tz));
        }
    }

    static long rodKey(int slot) { return 0x524F44L << 32 | (slot & 0xFFFFFFFFL); }

    private static void tellOwner(MinecraftServer server, int slot, Component c) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        ServerPlayer p = o == null ? null : server.getPlayerList().getPlayer(o);
        if (p != null) p.sendSystemMessage(c);
    }
}
