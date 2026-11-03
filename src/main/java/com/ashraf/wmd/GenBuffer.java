package com.ashraf.wmd;

import net.neoforged.neoforge.energy.IEnergyStorage;

final class GenBuffer implements IEnergyStorage {

    int stored;
    private final int capacity, maxOut;
    private final Runnable changed;

    GenBuffer(int capacity, int maxOut, Runnable changed) { this.capacity = capacity; this.maxOut = maxOut; this.changed = changed; }

    boolean hasRoom(int n) { return stored + n <= capacity; }

    @Override public int receiveEnergy(int n, boolean sim) { return 0; }

    @Override
    public int extractEnergy(int n, boolean sim) {
        int t = Math.min(Math.min(n, stored), maxOut);
        if (t > 0 && !sim) { stored -= t; changed.run(); }
        return t;
    }

    @Override public int getEnergyStored() { return stored; }
    @Override public int getMaxEnergyStored() { return capacity; }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return false; }
}
