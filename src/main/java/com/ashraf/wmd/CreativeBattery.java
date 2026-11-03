package com.ashraf.wmd;

import net.neoforged.neoforge.energy.IEnergyStorage;

public final class CreativeBattery {

    private CreativeBattery() {}

    public static final IEnergyStorage STORAGE = new IEnergyStorage() {
        @Override public int receiveEnergy(int toReceive, boolean simulate) { return 0; }
        @Override public int extractEnergy(int toExtract, boolean simulate) { return Math.max(0, toExtract); }
        @Override public int getEnergyStored() { return 1_000_000_000; }
        @Override public int getMaxEnergyStored() { return 1_000_000_000; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };
}
