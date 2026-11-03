package com.ashraf.wmd;

import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

record CargoFilter(String warhead, boolean uplink, boolean refined) {

    static final CargoFilter ANY = new CargoFilter("", false, false);

    static final String[] WARHEADS = warheads();

    private static String[] warheads() {
        java.util.List<String> l = new java.util.ArrayList<>();
        l.add("");
        for (WarheadType t : WarheadType.values()) if (t != WarheadType.INTERCEPTOR) l.add(t.id);
        return l.toArray(new String[0]);
    }

    boolean any() { return warhead.isEmpty() && !uplink && !refined; }

    static boolean appliesTo(Item item) { return item instanceof LaunchMissileItem || item == Wmd.STAGE_UPPER.get(); }

    boolean test(ItemStack s, Item item) {
        if (!s.is(item)) return false;
        if (any() || !appliesTo(item)) return true;
        MissileBuild b = MissileBuild.of(s);
        if (!warhead.isEmpty() && !warhead.equals(b.warheadType())) return false;
        if (uplink && !b.uplink()) return false;
        return !refined || b.refined();
    }

    Predicate<ItemStack> of(Item item) { return s -> test(s, item); }

    CargoFilter cycleWarhead(int d) {
        int i = 0;
        for (int k = 0; k < WARHEADS.length; k++) if (WARHEADS[k].equals(warhead)) i = k;
        return new CargoFilter(WARHEADS[Math.floorMod(i + (d < 0 ? -1 : 1), WARHEADS.length)], uplink, refined);
    }

    CargoFilter withUplink(boolean u) { return new CargoFilter(warhead, u, refined); }
    CargoFilter withRefined(boolean r) { return new CargoFilter(warhead, uplink, r); }

    void write(CompoundTag t) {
        if (any()) return;
        t.putString("fw", warhead);
        t.putBoolean("fu", uplink);
        t.putBoolean("fr", refined);
    }

    static CargoFilter read(CompoundTag t) {
        if (!t.contains("fw")) return ANY;
        return new CargoFilter(t.getString("fw"), t.getBoolean("fu"), t.getBoolean("fr"));
    }
}
