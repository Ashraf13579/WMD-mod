package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CcLaunch {

    private CcLaunch() {}

    public static final int MAX_ROWS = 8;

    private static final Set<LaunchTerminalBlockEntity> TERMINALS = Collections.newSetFromMap(new WeakHashMap<>());

    public static void terminalSeen(LaunchTerminalBlockEntity t) {
        if (!TERMINALS.contains(t)) TERMINALS.add(t);
    }

    public static boolean loaded(LaunchTerminalBlockEntity t) {
        Level l = t.getLevel();
        return l != null && !t.isRemoved() && l.isLoaded(t.getBlockPos()) && l.getBlockEntity(t.getBlockPos()) == t;
    }

    public static List<LaunchTerminalBlockEntity> terminalsOf(UUID owner) {
        List<LaunchTerminalBlockEntity> l = new ArrayList<>();
        if (owner == null) return l;
        for (LaunchTerminalBlockEntity t : TERMINALS) if (owner.equals(t.getOwner()) && loaded(t)) l.add(t);
        l.sort((a, b) -> a.isFormed() != b.isFormed() ? (a.isFormed() ? -1 : 1) : Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        return l.size() > MAX_ROWS ? new ArrayList<>(l.subList(0, MAX_ROWS)) : l;
    }

    public static List<LaunchTerminalBlockEntity> terminalsIn(Level level, UUID owner, int max) {
        List<LaunchTerminalBlockEntity> l = new ArrayList<>();
        if (owner == null) return l;
        for (LaunchTerminalBlockEntity t : TERMINALS) {
            if (l.size() >= max) break;
            if (t.getLevel() == level && owner.equals(t.getOwner()) && loaded(t)) l.add(t);
        }
        return l;
    }

    public static LaunchTerminalBlockEntity terminalAt(Level level, BlockPos pos, UUID owner) {
        if (level == null || owner == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof LaunchTerminalBlockEntity t && owner.equals(t.getOwner()) ? t : null;
    }

    public static ListTag rows(Level consoleLevel, UUID owner) {
        ListTag out = new ListTag();
        for (LaunchTerminalBlockEntity t : terminalsOf(owner)) {
            CompoundTag c = new CompoundTag();
            BlockPos p = t.getBlockPos();
            c.putLong("p", p.asLong());
            c.putBoolean("dim", t.getLevel() != consoleLevel);
            c.putInt("t", t.getTier());
            c.putInt("s", t.getStage().ordinal());
            c.putInt("h", t.hint());
            c.putBoolean("ts", t.isTargetSet());
            c.putInt("tx", t.getTargetX());
            c.putInt("tz", t.getTargetZ());
            c.putInt("r", t.getRange());
            MissileBuild b = t.shownBuild();
            c.putString("w", b == null ? "" : b.warheadType());
            c.putInt("m", b == null ? 0 : b.calibre());
            c.putBoolean("sat", b != null && Satellites.toOrbit(b));
            if (Asat.isAsat(b)) { c.putBoolean("asat", true); c.putInt("as", t.asatSat()); }
            c.putBoolean("in", CcReach.reach(t.getLevel(), owner, p));
            c.putInt("eta", t.isTargetSet() ? t.predictTicks(t.getTargetX(), t.getTargetZ()) : -1);
            out.add(c);
        }
        return out;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        TERMINALS.clear();
    }
}
