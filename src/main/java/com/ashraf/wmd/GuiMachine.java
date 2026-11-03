package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public interface GuiMachine {

    Component guiTitle();

    List<FluidTank> guiTanks();

    List<Fluid> guiTankFluids();

    List<Component> guiTankLabels();

    default IEnergyStorage guiEnergy() { return null; }

    default boolean guiHidesPower() { return false; }

    default int guiFeUse() { return 0; }

    default String guiTankUnit(int tank) { return "mB"; }

    default List<net.minecraft.world.item.ItemStack> guiSlotIcons() { return List.of(); }

    default boolean guiSlotOutput(int slot) { return false; }

    default boolean guiTankColoured() { return false; }

    default boolean guiTankOutput(int tank) { return false; }

    default int guiProgress() { return -1; }

    default int guiStatus() { return guiRunning() ? 1 : 0; }

    int LOW_POWER = 100;
    static boolean lowPower(int status) { return status > LOW_POWER && status < LOW_POWER + 100; }
    static int lowPowerPct(int status) { return status - LOW_POWER; }

    default PortMap guiPortMap() { return null; }

    default boolean guiUpgrades() { return false; }

    default int guiUpgradeCode(int i) { return 0; }

    default net.minecraft.world.item.ItemStack guiPreview(List<net.minecraft.world.item.ItemStack> inputs) { return net.minecraft.world.item.ItemStack.EMPTY; }

    boolean guiRunning();

    List<Component> guiInfo();
}
