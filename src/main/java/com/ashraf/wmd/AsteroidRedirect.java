package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AsteroidRedirect {

    private AsteroidRedirect() {}

    static final int A_ID = 0, A_START = 1, A_END = 2, A_SIZE = 3, A_SEED = 4, A_BY = 5, A_N = 6;

    static final int T_T0 = 0, T_PASS = 1, T_SIZE = 2, T_SEED = 3, T_HITS = 4, T_TX = 5, T_TZ = 6, T_AIM = 7, T_PIECES = 8, T_SPLIT = 9, T_N = 10;

    static long[] fitT(long[] a) { long[] o = new long[T_N]; System.arraycopy(a, 0, o, 0, Math.min(a.length, T_N)); return o; }
    static long[] fitPass(long[] a) { long[] o = new long[A_N]; System.arraycopy(a, 0, o, 0, Math.min(a.length, A_N)); return o; }

    public static Vec3 doors(BlockPos origin, Direction f) { return local(origin, f, 24.5, 6.5, 0.0); }

    public static Vec3 local(BlockPos origin, Direction f, double x, double y, double z) {
        Vec3 l = new Vec3(x, y, z);
        Vec3 w = switch (f) { case SOUTH -> new Vec3(-l.z, l.y, l.x); case WEST -> new Vec3(-l.x, l.y, -l.z); case NORTH -> new Vec3(l.z, l.y, -l.x); default -> l; };
        return new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5).add(w);
    }

    public static Vec3 axis(Direction f) { return new Vec3(f.getStepX(), 0.0, f.getStepZ()); }

    public static Vec3 parked(Vec3 doors, Direction f, double size) { return doors.add(axis(f).scale(Balance.ASTEROID_PARK_GAP + size / 2.0)); }

    public static Vec3 capturePoint(Vec3 doors, Direction f) { return doors.add(axis(f).scale(600.0)).add(0.0, 150.0, 0.0); }

    public static Vec3 pushStart(Vec3 doors, Direction f) { return doors.add(axis(f).scale(2_400.0)).add(0.0, 700.0, 0.0); }

    static double smooth(double x) { x = Math.max(0.0, Math.min(1.0, x)); return x * x * (3.0 - 2.0 * x); }

    public static Vec3 pushRock(Vec3 doors, Direction f, double size, double u) {
        Vec3 a = pushStart(doors, f), b = parked(doors, f, size);
        double k = smooth(u);
        return a.add(b.subtract(a).scale(k));
    }

    public static Vec3 launchTail(BlockPos origin, Direction f, double el) {
        double s = 0.5 * 0.001875 * Math.pow(Math.max(0.0, el - Balance.TUG_LEAVE_AT), 2.0);
        Vec3 sill = local(origin, f, 240.0 / 16.0 - 0.5, (38.0 + 34.0 * 0.66) / 16.0, 0.0);
        return sill.add(axis(f).scale(s)).add(0.0, Math.max(0.0, s - 20.0) * 0.25, 0.0);
    }

    public static Vec3 chaseTug(BlockPos origin, Direction f, double el) {
        Vec3 start = launchTail(origin, f, Balance.TUG_LAUNCH_TICKS);
        return start.add(axis(f).add(0.0, 0.25, 0.0).normalize().scale(2.5 * el));
    }

    static void second(ServerLevel orbit, Station.Data d) {
        MinecraftServer server = orbit.getServer();
        long now = orbit.getGameTime();
        boolean anySensor = false;
        for (int slot : d.slots.keySet()) if (SensorArray.working(server, slot)) { anySensor = true; break; }
        if (d.pass != null && now > d.pass[A_END] + Balance.TUG_CHASE_TICKS + Balance.TUG_LAUNCH_TICKS && d.pass[A_BY] == 0) { d.pass = null; d.setDirty(); }
        if (d.pass != null && d.pass[A_BY] != 0 && now > d.pass[A_END]) { d.pass = null; d.setDirty(); }
        if (d.asteroidIn <= 0) { d.asteroidIn = nextGap(orbit); d.setDirty(); }
        if (!anySensor || d.pass != null) return;
        d.asteroidIn -= 20;
        d.setDirty();
        if (d.asteroidIn <= 0) startPass(orbit, d);
    }

    private static long nextGap(ServerLevel orbit) {
        return Balance.ASTEROID_PASS_MIN + orbit.random.nextInt(Balance.ASTEROID_PASS_MAX - Balance.ASTEROID_PASS_MIN + 1);
    }

    static void startPass(ServerLevel orbit, Station.Data d) {
        long now = orbit.getGameTime();
        int size = Balance.ASTEROID_SIZE_MIN + orbit.random.nextInt(Balance.ASTEROID_SIZE_MAX - Balance.ASTEROID_SIZE_MIN + 1);
        d.pass = new long[] { now, now, now + Balance.ASTEROID_WINDOW, size, orbit.random.nextLong(), 0L };
        d.asteroidIn = nextGap(orbit);
        d.setDirty();
        Wmd.LOGGER.info("[asteroid] pass {} begins: {} blocks, window {} min", now, size, Balance.ASTEROID_WINDOW / 1200);
        MinecraftServer server = orbit.getServer();
        for (int slot : d.slots.keySet()) {
            if (!SensorArray.working(server, slot)) continue;
            Component sub = Component.translatable("title.wmd.abay.detected_sub", size, Balance.ASTEROID_WINDOW / 1200);
            for (ServerPlayer p : watchers(server, slot)) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 100, 20));
                p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.wmd.abay.detected").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                p.sendSystemMessage(Component.translatable("message.wmd.abay.detected", size, Balance.ASTEROID_WINDOW / 1200).withStyle(ChatFormatting.GOLD));
            }
            for (Station.Module m : Station.modules(server, slot))
                if (Station.SENSOR.equals(m.type()) && m.built()) orbit.playSound(null, Station.controller(m), WmdSounds.SA_CONTACT.get(), SoundSource.BLOCKS, 4.0F, 0.8F);
        }
    }

    static List<ServerPlayer> watchers(MinecraftServer server, int slot) {
        List<ServerPlayer> out = new ArrayList<>();
        UUID owner = Orbit.OrbitSlots.get(server).ownerOf(slot);
        ServerPlayer op = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (op != null) out.add(op);
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit != null) for (ServerPlayer p : orbit.players()) if (Orbit.slotAt(p.getX(), p.getZ()) == slot && !out.contains(p)) out.add(p);
        return out;
    }

    static int[] passStatus(MinecraftServer server, long now) {
        long[] p = Station.Data.get(server).pass;
        if (p == null || now > p[A_END]) return new int[] { 0, 0, 0 };
        return new int[] { p[A_BY] != 0 ? 2 : 1, (int) p[A_SIZE], (int) Math.max(0, (p[A_END] - now) / 20) };
    }

    static Component launch(ServerPlayer sp, int slot, Station.Module m, int[] t) {
        MinecraftServer server = sp.server;
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null) return null;
        Station.Data d = Station.Data.get(server);
        long now = orbit.getGameTime();
        if (t[AsteroidBay.P_STATE] != AsteroidBay.READY) return Component.translatable("message.wmd.abay.no_tug").withStyle(ChatFormatting.YELLOW);
        if (!SensorArray.working(server, slot)) return Component.translatable("message.wmd.abay.blind").withStyle(ChatFormatting.YELLOW);
        if (d.pass == null || now > d.pass[A_END] || d.pass[A_BY] != 0) return Component.translatable("message.wmd.abay.no_pass").withStyle(ChatFormatting.YELLOW);
        long[] f = d.bayT.computeIfAbsent(m.id(), k -> new long[T_N]);
        if (f[T_PASS] == d.pass[A_ID]) return Component.translatable("message.wmd.abay.launched").withStyle(ChatFormatting.YELLOW);
        f[T_T0] = now; f[T_PASS] = d.pass[A_ID]; f[T_SIZE] = d.pass[A_SIZE]; f[T_SEED] = d.pass[A_SEED]; f[T_HITS] = 0;
        t[AsteroidBay.P_STATE] = AsteroidBay.LAUNCH;
        d.setDirty();
        StationLife.tell(orbit, slot, Component.translatable("message.wmd.abay.launch", Balance.TUG_DOORS_AT / 20).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        StationLife.pa(orbit, slot, WmdSounds.PA_TUG.get());
        orbit.playSound(null, Station.controller(m), WmdSounds.ST_BREACH_KLAXON.get(), SoundSource.BLOCKS, 3.0F, 1.2F);
        Station.offerCamera(sp, m);
        sync(server, slot, m);
        return null;
    }

    static void phases(ServerLevel orbit, Station.Data d, int slot, Station.Module m, int[] t) {
        long[] f = d.bayT.get(m.id());
        int st = t[AsteroidBay.P_STATE];
        if (f == null || st < AsteroidBay.LAUNCH) return;
        MinecraftServer server = orbit.getServer();
        long now = orbit.getGameTime(), el = now - f[T_T0];
        switch (st) {
            case AsteroidBay.LAUNCH -> {
                if (Station.wrecked(server, m)) { lose(orbit, d, slot, m, t, f, "message.wmd.abay.lost_bay"); doors(orbit, m, true); return; }
                if (el >= Balance.TUG_DOORS_AT && el < Balance.TUG_LAUNCH_TICKS) doors(orbit, m, false);
                BlockPos dp = BlockPos.containing(doors(m.origin(), m.facing()));
                if (el >= Balance.TUG_DOORS_AT && el < Balance.TUG_DOORS_AT + 20) {
                    orbit.playSound(null, dp, WmdSounds.AB_DOORS.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                    orbit.playSound(null, Station.controller(m), WmdSounds.AB_VENT.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
                }
                if (el >= Balance.TUG_LEAVE_AT && el < Balance.TUG_LEAVE_AT + 20)
                    orbit.playSound(null, dp, WmdSounds.TUG_LAUNCH.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
                if (el >= Balance.TUG_LAUNCH_TICKS && doors(orbit, m, true)) {
                    orbit.playSound(null, dp, WmdSounds.AB_DOORS.get(), SoundSource.BLOCKS, 4.0F, 0.9F);
                    phase(orbit, d, slot, m, t, f, AsteroidBay.CHASE, now);
                }
            }
            case AsteroidBay.CHASE -> {
                if (el < Balance.TUG_CHASE_TICKS) return;
                long[] p = d.pass;
                if (p != null && p[A_ID] == f[T_PASS] && p[A_BY] == 0) {
                    p[A_BY] = slot + 1;
                    phase(orbit, d, slot, m, t, f, AsteroidBay.CAPTURE, now);
                    StationLife.tell(orbit, slot, Component.translatable("message.wmd.abay.capture").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                    cutscene(server, slot, m, f);
                } else {
                    phase(orbit, d, slot, m, t, f, AsteroidBay.RETURN, now);
                    for (ServerPlayer w : watchers(server, slot)) w.sendSystemMessage(Component.translatable("message.wmd.abay.beaten").withStyle(ChatFormatting.YELLOW));
                }
            }
            case AsteroidBay.CAPTURE -> {
                if (el < Balance.TUG_CAPTURE_TICKS) return;
                phase(orbit, d, slot, m, t, f, AsteroidBay.PUSH, now);
                for (ServerPlayer w : watchers(server, slot)) w.sendSystemMessage(Component.translatable("message.wmd.abay.push", Balance.TUG_PUSH_TICKS / 72_000).withStyle(ChatFormatting.AQUA));
                String who = ownerName(server, slot);
                for (int other : d.slots.keySet()) {
                    if (other == slot || !SensorArray.working(server, other)) continue;
                    for (ServerPlayer w : watchers(server, other))
                        w.sendSystemMessage(Component.translatable("message.wmd.abay.inbound", who, Balance.TUG_PUSH_TICKS / 72_000).withStyle(ChatFormatting.GOLD));
                }
            }
            case AsteroidBay.PUSH -> {
                if (el < Balance.TUG_PUSH_TICKS) return;
                phase(orbit, d, slot, m, t, f, AsteroidBay.PARKED, now);
                for (ServerPlayer w : watchers(server, slot)) w.sendSystemMessage(Component.translatable("message.wmd.abay.parked").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            case AsteroidBay.FALL -> AsteroidDrop.tick(orbit, d, slot, m, t, f);
            case AsteroidBay.RETURN -> {
                if (el < Balance.TUG_RETURN_TICKS) return;
                t[AsteroidBay.P_STATE] = AsteroidBay.READY;
                d.setDirty();
                StationLife.tell(orbit, slot, Component.translatable("message.wmd.abay.home").withStyle(ChatFormatting.AQUA));
                sync(server, slot, m);
            }
            default -> {}
        }
    }

    private static void phase(ServerLevel orbit, Station.Data d, int slot, Station.Module m, int[] t, long[] f, int st, long now) {
        t[AsteroidBay.P_STATE] = st;
        f[T_T0] = now;
        d.setDirty();
        sync(orbit.getServer(), slot, m);
    }

    private static void lose(ServerLevel orbit, Station.Data d, int slot, Station.Module m, int[] t, long[] f, String key) {
        t[AsteroidBay.P_STATE] = AsteroidBay.NONE;
        t[AsteroidBay.P_AM] = 0; t[AsteroidBay.P_DT] = 0; t[AsteroidBay.P_CORE] = 0; t[AsteroidBay.P_PROG] = 0;
        d.bayT.remove(m.id());
        d.setDirty();
        for (ServerPlayer w : watchers(orbit.getServer(), slot)) w.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        sync(orbit.getServer(), slot, m);
    }

    static boolean doors(ServerLevel orbit, Station.Module m, boolean shut) {
        boolean done = true;
        for (int y = 2; y <= 10; y++) for (int z = -4; z <= 4; z++) {
            BlockPos p = Station.world(m, Station.BAY_LEN - 1, y, z);
            if (!orbit.isLoaded(p)) continue;
            var st = orbit.getBlockState(p);
            if (!shut && st.is(Wmd.STATION_SOLID.get())) orbit.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            if (shut && st.isAir()) {
                if (orbit.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) orbit.setBlock(p, Wmd.STATION_SOLID.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                else done = false;
            }
        }
        return done;
    }

    static String ownerName(MinecraftServer server, int slot) {
        UUID o = Orbit.OrbitSlots.get(server).ownerOf(slot);
        return o == null ? "?" : CcIntel.name(server, o);
    }

    private static void cutscene(MinecraftServer server, int slot, Station.Module m, long[] f) {
        Vec3 d = doors(m.origin(), m.facing()), c = capturePoint(d, m.facing()), to = c.add(axis(m.facing()).scale(f[T_SIZE]));
        CutscenePayload p = new CutscenePayload(CutscenePayload.KIND_CAPTURE, (float) c.x, (float) c.y, (float) c.z, (float) to.x, (float) to.y, (float) to.z,
                0, Balance.TUG_CAPTURE_TICKS, "");
        for (ServerPlayer w : watchers(server, slot)) if (Orbit.isOrbit(w.level())) PacketDistributor.sendToPlayer(w, p);
    }

    static List<Integer> pushing(MinecraftServer server, int slot) {
        List<Integer> out = new ArrayList<>();
        Station.Data d = Station.Data.get(server);
        for (int s : d.slots.keySet()) {
            if (s == slot) continue;
            Station.Module m = AsteroidBay.module(server, s);
            int[] t = m == null ? null : d.bay.get(m.id());
            if (t != null && t[AsteroidBay.P_STATE] == AsteroidBay.PUSH) out.add(s);
        }
        return out;
    }

    static Vec3 pushingRock(MinecraftServer server, int slot, long now) {
        Station.Data d = Station.Data.get(server);
        Station.Module m = AsteroidBay.module(server, slot);
        int[] t = m == null ? null : d.bay.get(m.id());
        long[] f = m == null ? null : d.bayT.get(m.id());
        if (t == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.PUSH) return null;
        return pushRock(doors(m.origin(), m.facing()), m.facing(), f[T_SIZE], (now - f[T_T0]) / (double) Balance.TUG_PUSH_TICKS);
    }

    static int tugHealth(MinecraftServer server, int slot) {
        Station.Module m = AsteroidBay.module(server, slot);
        long[] f = m == null ? null : Station.Data.get(server).bayT.get(m.id());
        return f == null ? 0 : (int) Math.max(0, 100 * (Balance.TUG_HITS - f[T_HITS]) / Balance.TUG_HITS);
    }

    static boolean hitTug(MinecraftServer server, int slot, String by) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        Station.Data d = Station.Data.get(server);
        Station.Module m = AsteroidBay.module(server, slot);
        int[] t = m == null ? null : d.bay.get(m.id());
        long[] f = m == null ? null : d.bayT.get(m.id());
        if (orbit == null || t == null || f == null || t[AsteroidBay.P_STATE] != AsteroidBay.PUSH) return false;
        f[T_HITS]++;
        d.setDirty();
        if (f[T_HITS] >= Balance.TUG_HITS) lose(orbit, d, slot, m, t, f, "message.wmd.abay.lost_shot");
        else {
            for (ServerPlayer w : watchers(server, slot))
                w.sendSystemMessage(Component.translatable("message.wmd.abay.hit", by, f[T_HITS], Balance.TUG_HITS).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            sync(server, slot, m);
        }
        return true;
    }

    static void sync(MinecraftServer server, int slot, Station.Module m) {
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        if (orbit == null || m == null) return;
        PacketDistributor.sendToPlayersInDimension(orbit, tugPayload(server, slot, m));
    }

    static AsteroidBayPayloads.Tug tugPayload(MinecraftServer server, int slot, Station.Module m) {
        Station.Data d = Station.Data.get(server);
        int[] t = d.bay.get(m.id());
        long[] f = d.bayT.get(m.id());
        int st = t == null ? AsteroidBay.NONE : t[AsteroidBay.P_STATE];
        if (f == null) f = new long[T_N];
        return new AsteroidBayPayloads.Tug(slot, st, f[T_T0], (int) f[T_SIZE], f[T_SEED], (int) f[T_HITS], m.origin().getX(), m.origin().getY(), m.origin().getZ(), m.facing().get3DDataValue());
    }

    static void resync(ServerLevel orbit, Station.Data d) {
        MinecraftServer server = orbit.getServer();
        for (int slot : d.slots.keySet()) {
            Station.Module m = AsteroidBay.module(server, slot);
            int[] t = m == null ? null : d.bay.get(m.id());
            if (t != null && t[AsteroidBay.P_STATE] >= AsteroidBay.LAUNCH) PacketDistributor.sendToPlayersInDimension(orbit, tugPayload(server, slot, m));
        }
    }
}
