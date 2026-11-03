package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class CruiseLaunchers {

    private CruiseLaunchers() {}

    public static final int MAX_ROWS = 8;

    private static final Set<CruiseLauncherBlockEntity> ALL = Collections.newSetFromMap(new WeakHashMap<>());

    static void seen(CruiseLauncherBlockEntity l) { if (!ALL.contains(l)) ALL.add(l); }

    public static boolean loaded(CruiseLauncherBlockEntity l) {
        Level lv = l.getLevel();
        return lv != null && !l.isRemoved() && lv.isLoaded(l.getBlockPos()) && lv.getBlockEntity(l.getBlockPos()) == l;
    }

    public static List<CruiseLauncherBlockEntity> launchersOf(UUID owner) {
        List<CruiseLauncherBlockEntity> l = new ArrayList<>();
        if (owner == null) return l;
        for (CruiseLauncherBlockEntity c : ALL) if (owner.equals(c.getOwner()) && loaded(c)) l.add(c);
        l.sort((a, b) -> Long.compare(a.getBlockPos().asLong(), b.getBlockPos().asLong()));
        return l.size() > MAX_ROWS ? new ArrayList<>(l.subList(0, MAX_ROWS)) : l;
    }

    public static CruiseLauncherBlockEntity launcherAt(Level level, BlockPos pos, UUID owner) {
        if (level == null || owner == null || !level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof CruiseLauncherBlockEntity c && owner.equals(c.getOwner()) ? c : null;
    }

    public static CompoundTag row(Level consoleLevel, UUID owner, CruiseLauncherBlockEntity l) {
        CompoundTag c = new CompoundTag();
        BlockPos p = l.getBlockPos();
        c.putBoolean("cruise", true);
        c.putLong("p", p.asLong());
        c.putInt("n", l.loaded());
        c.putInt("f", l.fuelCanisters());
        c.putInt("st", l.launcherStatus());
        c.putInt("ph", l.phase());
        c.putBoolean("ts", l.hasTarget());
        c.putInt("tx", l.targetX());
        c.putInt("tz", l.targetZ());
        c.putInt("rl", l.routeLength());
        c.putInt("rg", l.nextRange());
        c.putInt("wp", l.waypoints());
        c.putInt("eta", l.flightTicks());
        boolean dim = l.getLevel() != consoleLevel;
        c.putBoolean("dim", dim);
        c.putBoolean("in", !dim && l.getLevel() != null && CcReach.reach(l.getLevel(), owner, p));
        return c;
    }

    static List<CruiseLauncherBlockEntity> all() {
        List<CruiseLauncherBlockEntity> l = new ArrayList<>();
        for (CruiseLauncherBlockEntity c : ALL) if (loaded(c)) l.add(c);
        return l;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { ALL.clear(); }
}
