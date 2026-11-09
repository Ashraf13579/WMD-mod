package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class TorpedoTubeBlockEntity extends TorpedoLauncherBlockEntity {

    public static final int X0 = 0, X1 = 0, Y0 = 0, Y1 = 0, Z0 = 0, Z1 = 3;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.EAST, false);

    public TorpedoTubeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.TORPEDO_TUBE_BE.get(), pos, state, Balance.TUBE_FE, 1, 0); }

    @Override protected int chargeTicks() { return Balance.TUBE_CHARGE_TICKS; }

    @Override protected double[][] muzzle() { return new double[][] { { 0.5, 0.5, 0.0 }, { 0.0, 0.0, -1.0 } }; }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.EAST) ? energy() : null; }

    public boolean doorOpen() { return reloadLeft > 0 || fireStatus() == S_RELOADING; }

    public boolean ready() { return fireStatus() == S_READY; }
}
