package com.ashraf.wmd;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public enum DroneType {
    T1(1, "drone_t1", () -> Wmd.DRONE_T1.get(), false, 1.0F, new float[][] { { -3.4F, 2.95F, -3.4F }, { 3.4F, 2.95F, -3.4F }, { -3.4F, 2.95F, 3.4F }, { 3.4F, 2.95F, 3.4F } }, null),
    T2(2, "drone_t2", () -> Wmd.DRONE_T2.get(), false, 1.0F, new float[][] { { -5.2F, 6.1F, -5.2F }, { 5.2F, 6.1F, -5.2F }, { -5.2F, 6.1F, 5.2F }, { 5.2F, 6.1F, 5.2F } }, null),
    T3(3, "drone_t3", () -> Wmd.DRONE_T3.get(), true, 1.0F, null, new float[] { 0.0F, 6.0F, 26.2F }),
    T4(4, "drone_t4", () -> Wmd.DRONE_T4.get(), true, 0.55F, null, new float[] { 0.0F, 15.0F, 49.8F });

    public final int tier;
    public final String mesh;
    private final Supplier<Item> item;

    public final boolean big;
    public final float lineScale;

    public final float[][] rotors;

    public final float[] prop;

    DroneType(int tier, String mesh, Supplier<Item> item, boolean big, float lineScale, float[][] rotors, float[] prop) {
        this.tier = tier; this.mesh = mesh; this.item = item; this.big = big; this.lineScale = lineScale; this.rotors = rotors; this.prop = prop;
    }

    public String id() { return name().toLowerCase(); }
    public int runTicks() { return Balance.DRONE_RUN_TICKS[tier - 1]; }
    public int runFe() { return Balance.DRONE_RUN_FE[tier - 1]; }
    public ItemStack stack() { return new ItemStack(item.get()); }

    public static DroneType of(int i) { return values()[Math.floorMod(i, values().length)]; }

    public static DroneType of(ItemStack s) {
        if (s.isEmpty()) return null;
        for (DroneType t : values()) if (s.is(t.item.get())) return t;
        return null;
    }
}
