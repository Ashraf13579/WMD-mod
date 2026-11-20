package com.ashraf.wmd;

import java.util.List;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public enum ExoModule {
    THERMAL("thermal", EquipmentSlot.HEAD, "m_thermal"),
    REBREATHER("rebreather", EquipmentSlot.HEAD, "m_rebreather"),
    CANNON("cannon", EquipmentSlot.CHEST, "m_cannon"),
    MISSILES("missiles", EquipmentSlot.CHEST, "m_pod"),
    WINGS("wings", EquipmentSlot.CHEST, "m_wingroot"),
    CLOAK("cloak", EquipmentSlot.CHEST, "m_cloak"),
    RF("rf", EquipmentSlot.CHEST, "m_rf"),
    BARRIER("barrier", EquipmentSlot.LEGS, "m_barrier_h"),
    MAGGRIP("maggrip", EquipmentSlot.FEET, "m_mag_l");

    public final String id;
    public final EquipmentSlot slot;
    public final String bone;

    ExoModule(String id, EquipmentSlot slot, String bone) { this.id = id; this.slot = slot; this.bone = bone; }

    public static final List<ExoModule> ALL = List.of(values());

    public int bit() { return 1 << ordinal(); }

    public int piece() { return slot == EquipmentSlot.HEAD ? 0 : slot == EquipmentSlot.CHEST ? 1 : slot == EquipmentSlot.LEGS ? 2 : 3; }

    public static int mask(ItemStack piece) { return piece.isEmpty() ? 0 : piece.getOrDefault(ExoSuitBlocks.EXO_MODS.get(), 0); }

    public boolean on(ItemStack piece) { return (mask(piece) & bit()) != 0; }

    public static void set(ItemStack piece, ExoModule m, boolean fitted) {
        if (piece.isEmpty()) return;
        int v = mask(piece);
        v = fitted ? v | m.bit() : v & ~m.bit();
        if (v == 0) piece.remove(ExoSuitBlocks.EXO_MODS.get());
        else piece.set(ExoSuitBlocks.EXO_MODS.get(), v);
    }

    public boolean fitted(LivingEntity e) {
        ItemStack s = e.getItemBySlot(slot);
        return s.getItem() instanceof ExoSuitItem && on(s);
    }

    public boolean live(LivingEntity e) { return fitted(e) && ExoSuitItem.powered(e); }
}
