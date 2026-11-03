package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public interface MachinePorts {

    default IFluidHandler fluidPort(BlockPos partPos, Direction side) { return null; }

    default IEnergyStorage energyPort(BlockPos partPos, Direction side) { return null; }

    default IItemHandler itemPort(BlockPos partPos, Direction side) { return null; }
}
