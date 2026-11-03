package com.ashraf.wmd;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

public final class Alkylate {

    private Alkylate() {}

    public static Fluid fluid() { return WmdFluids.ALKYLATE.source().get(); }

    public static boolean is(FluidStack s) { return s != null && !s.isEmpty() && s.getFluid() == fluid(); }

    public static boolean isCanister(ItemStack s) { return s.is(Wmd.ALKYLATE_CANISTER.get()); }

    public static boolean isEmptyCanister(ItemStack s) { return s.is(Wmd.FUEL_CANISTER.get()); }

    public static boolean isAcid(ItemStack s) { return s.is(Wmd.ACID_CATALYST.get()); }

    public static int t4Range(MissileBuild b, int range, boolean alkylate) {
        return alkylate && b != null && b.staged() ? (int) (Math.round(range * Balance.ALKYLATE_T4_RANGE_MULT / 10.0) * 10) : range;
    }

    public static int superChargeCost(int charges) { return Math.max(0, charges) * Balance.SUPER_CHARGE_ALKYLATE_MB; }

    public static int superChargeCanisters(int charges) {
        int mb = superChargeCost(charges);
        return (mb + Balance.ALKYLATE_CANISTER_MB - 1) / Balance.ALKYLATE_CANISTER_MB;
    }

    public static boolean takeForSuperCharges(IItemHandler h, int charges, boolean simulate) {
        int need = superChargeCanisters(charges);
        if (need <= 0) return true;
        int have = 0;
        for (int i = 0; i < h.getSlots(); i++) if (isCanister(h.getStackInSlot(i))) have += h.getStackInSlot(i).getCount();
        if (have < need) return false;
        if (simulate) return true;
        for (int i = 0; i < h.getSlots() && need > 0; i++) {
            if (!isCanister(h.getStackInSlot(i))) continue;
            need -= h.extractItem(i, need, false).getCount();
        }
        return need <= 0;
    }
}
