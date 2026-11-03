package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class Grid {
    private Grid() {}

    static boolean pushing;

    public static IEnergyStorage gridOnly(IEnergyStorage s) {
        return s == null ? null : new IEnergyStorage() {
            @Override public int receiveEnergy(int n, boolean simulate) { return pushing ? s.receiveEnergy(n, simulate) : 0; }
            @Override public int extractEnergy(int n, boolean simulate) { return s.extractEnergy(n, simulate); }
            @Override public int getEnergyStored() { return s.getEnergyStored(); }
            @Override public int getMaxEnergyStored() { return s.getMaxEnergyStored(); }
            @Override public boolean canExtract() { return s.canExtract(); }
            @Override public boolean canReceive() { return pushing && s.canReceive(); }
        };
    }

    static boolean isWmd(Level level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace().equals(Wmd.MODID);
    }
}
