package com.ashraf.wmd;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class AsteroidBay {

    private AsteroidBay() {}

    static final int P_AM = 0, P_DT = 1, P_CORE = 2, P_PROG = 3, P_STATE = 4, P_N = 5;

    public static final int NONE = 0, BUILDING = 1, READY = 2, LAUNCH = 3, CHASE = 4, CAPTURE = 5, PUSH = 6, PARKED = 7, RETURN = 8, FALL = 9;

    public static final int CMD_BUILD = 1, CMD_LAUNCH = 2, CMD_TARGET = 3, CMD_DROP = 4;

    static int[] fit(int[] a) { int[] o = new int[P_N]; System.arraycopy(a, 0, o, 0, Math.min(a.length, P_N)); return o; }

    static int need(int part) { return part == P_AM ? Balance.TUG_ANTIMATTER : part == P_DT ? Balance.TUG_DT : Balance.TUG_CORES; }

    static Station.Module module(MinecraftServer server, int slot) {
        if (slot < 0) return null;
        for (Station.Module m : Station.modules(server, slot)) if (Station.BAY.equals(m.type()) && m.built()) return m;
        return null;
    }

    static boolean drawing(Station.Data d, Station.Module m, long buf) {
        int[] t = d.bay.get(m.id());
        return t != null && t[P_STATE] == BUILDING && buf >= Balance.BAY_BUILD_MIN_BUFFER;
    }

    static void tick(ServerLevel level, Station.Data d, int slot, List<Station.Module> mods, long buf, boolean powered) {
        for (Station.Module m : mods) {
            if (!Station.BAY.equals(m.type())) continue;
            int[] t = d.bay.get(m.id());
            boolean building = t != null && t[P_STATE] == BUILDING;
            boolean run = building && powered && buf >= Balance.BAY_BUILD_MIN_BUFFER && !Station.wrecked(level.getServer(), m);
            if (run) {
                t[P_PROG] = Math.min(Balance.TUG_BUILD_TICKS, t[P_PROG] + 20);
                if (t[P_PROG] >= Balance.TUG_BUILD_TICKS) {
                    t[P_STATE] = READY;
                    StationLife.tell(level, slot, Component.translatable("message.wmd.abay.ready").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                    level.playSound(null, Station.controller(m), WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 3.0F, 0.8F);
                }
                d.setDirty();
            }
            if (t != null) AsteroidRedirect.phases(level, d, slot, m, t);
            long[] f = d.bayT.get(m.id());
            if (level.getBlockEntity(Station.controller(m)) instanceof StationModuleBlockEntity be)
                be.setBay(t == null ? NONE : t[P_STATE], t == null ? 0 : permille(t), building && !run, f == null ? 0L : f[AsteroidRedirect.T_T0]);
        }
    }

    static int permille(int[] t) { return (int) Math.min(1000L, (long) t[P_PROG] * 1000L / Balance.TUG_BUILD_TICKS); }

    static void console(ServerPlayer sp, ServerLevel level, int slot, Station.Module m) {
        ItemStack held = sp.getMainHandItem();
        int part = held.is(Wmd.ANTIMATTER_CELL.get()) ? P_AM : held.is(Wmd.DT_FUEL_CANISTER.get()) ? P_DT : held.is(Wmd.TUG_CORE.get()) ? P_CORE : -1;
        if (part < 0) { AsteroidBayPayloads.sendInfo(sp, slot); return; }
        Station.Data d = Station.Data.get(level.getServer());
        int[] t = d.bay.computeIfAbsent(m.id(), k -> new int[P_N]);
        if (t[P_STATE] != NONE) { sp.displayClientMessage(Component.translatable("message.wmd.abay.has_tug").withStyle(ChatFormatting.YELLOW), true); return; }
        int want = need(part) - t[part];
        if (want <= 0) { sp.displayClientMessage(Component.translatable("message.wmd.abay.part_full", held.getHoverName()).withStyle(ChatFormatting.YELLOW), true); return; }
        int n = Math.min(want, held.getCount());
        Component name = held.getHoverName();
        if (!sp.getAbilities().instabuild) held.shrink(n);
        t[part] += n;
        d.setDirty();
        sp.displayClientMessage(Component.translatable("message.wmd.abay.loaded", name, t[part], need(part)).withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, sp.blockPosition(), WmdSounds.ST_DOCK_CLAMP.get(), SoundSource.BLOCKS, 0.8F, 1.3F);
    }

    static Component command(ServerPlayer sp, int slot, int cmd, int x, int z) {
        MinecraftServer server = sp.server;
        if (slot < 0) slot = Orbit.slotOf(server, sp.getUUID());
        Station.Module m = module(server, slot);
        if (m == null) return Component.translatable("message.wmd.abay.none").withStyle(ChatFormatting.YELLOW);
        if (!sp.getUUID().equals(Orbit.OrbitSlots.get(server).ownerOf(slot))) return Component.translatable("message.wmd.abay.not_owner").withStyle(ChatFormatting.YELLOW);
        if (Station.wrecked(server, m)) return Component.translatable("message.wmd.abay.wrecked").withStyle(ChatFormatting.RED);
        Station.Data d = Station.Data.get(server);
        int[] t = d.bay.computeIfAbsent(m.id(), k -> new int[P_N]);
        if (cmd == CMD_BUILD) {
            if (t[P_STATE] != NONE) return Component.translatable("message.wmd.abay.has_tug").withStyle(ChatFormatting.YELLOW);
            for (int part : new int[] { P_AM, P_DT, P_CORE }) if (t[part] < need(part)) return Component.translatable("message.wmd.abay.missing").withStyle(ChatFormatting.YELLOW);
            t[P_STATE] = BUILDING;
            t[P_PROG] = 0;
            d.setDirty();
            ServerLevel orbit = server.getLevel(Orbit.ORBIT);
            if (orbit != null) StationLife.tell(orbit, slot, Component.translatable("message.wmd.abay.building", Balance.TUG_BUILD_TICKS / 1200).withStyle(ChatFormatting.AQUA));
            return null;
        }
        if (cmd == CMD_LAUNCH) return AsteroidRedirect.launch(sp, slot, m, t);
        if (cmd == CMD_TARGET) return AsteroidDrop.target(sp, slot, m, t, x, z);
        if (cmd == CMD_DROP) return AsteroidDrop.drop(sp, slot, m, t);
        return null;
    }

    static int[] status(MinecraftServer server, int slot) {
        Station.Module m = module(server, slot);
        if (m == null) return new int[] { -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        if (Station.wrecked(server, m)) return new int[] { -2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        Station.Data d = Station.Data.get(server);
        int[] t = d.bay.getOrDefault(m.id(), new int[P_N]);
        boolean paused = t[P_STATE] == BUILDING && d.energy.getOrDefault(slot, Balance.STATION_BUFFER / 2) < Balance.BAY_BUILD_MIN_BUFFER;
        long[] f = d.bayT.get(m.id());
        ServerLevel orbit = server.getLevel(Orbit.ORBIT);
        long now = orbit == null ? 0L : orbit.getGameTime();
        int len = switch (t[P_STATE]) { case LAUNCH -> Balance.TUG_LAUNCH_TICKS; case CHASE -> Balance.TUG_CHASE_TICKS; case CAPTURE -> Balance.TUG_CAPTURE_TICKS;
            case PUSH -> Balance.TUG_PUSH_TICKS; case RETURN -> Balance.TUG_RETURN_TICKS; case FALL -> Balance.ASTEROID_FALL_TICKS; default -> 0; };
        int phase = f == null || len == 0 ? 0 : (int) Math.max(0L, (f[AsteroidRedirect.T_T0] + len - now) / 20);
        boolean launched = f != null && d.pass != null && f[AsteroidRedirect.T_PASS] == d.pass[AsteroidRedirect.A_ID];
        return new int[] { t[P_STATE], t[P_AM], t[P_DT], t[P_CORE], permille(t), (Balance.TUG_BUILD_TICKS - t[P_PROG]) / 20, paused ? 1 : 0, phase, launched ? 1 : 0,
                f == null ? 0 : (int) f[AsteroidRedirect.T_SIZE], f == null ? 0 : (int) f[AsteroidRedirect.T_AIM], f == null ? 0 : (int) f[AsteroidRedirect.T_TX],
                f == null ? 0 : (int) f[AsteroidRedirect.T_TZ], f == null ? 1 : (int) Math.max(1, f[AsteroidRedirect.T_PIECES]) };
    }
}
