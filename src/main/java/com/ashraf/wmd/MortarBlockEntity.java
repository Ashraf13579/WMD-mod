package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class MortarBlockEntity extends ArtilleryGunBlockEntity {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 1;

    public static final float REST_EL = 45.0F;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1)
            .item(0, 0, 0, Direction.NORTH, false).item(1, 0, 0, Direction.NORTH, false).item(0, 0, 0, Direction.WEST, false).item(0, 0, 1, Direction.WEST, false)
            .item(1, 0, 0, Direction.EAST, false).item(1, 0, 1, Direction.EAST, false).item(0, 0, 1, Direction.SOUTH, false).item(1, 0, 1, Direction.SOUTH, false);

    private final Slot[] slots = shellSlots;
    private final List<FluidTank> tanks = gauges(slots);
    private final IItemHandler hatch = inHandler(shellSlots);

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.MORTAR_BE.get(), pos, state, 0);
    }

    @Override public int gun() { return Artillery.MORTAR; }
    @Override protected int magazine() { return Balance.MORTAR_MAGAZINE; }
    @Override protected int layTicks() { return Balance.MORTAR_LAY_TICKS; }

    @Override protected double[] pivotLocal() { return new double[] { 0.5, 0.259, 0.5 }; }
    @Override protected double trunnionHeight() { return 0.0; }
    @Override protected double barrelLength() { return 1.406; }
    @Override protected float restElevation() { return REST_EL; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override public IEnergyStorage guiEnergy() { return null; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) {
        if (side == null || side.getAxis() == Direction.Axis.Y) return null;
        BlockPos l = Machines.toLocal(worldPosition, facing(), Machines.mount(getBlockState()), part);
        if (l.getY() != 0) return null;
        for (Direction ld : Direction.Plane.HORIZONTAL) {
            if (Machines.dirToWorld(facing(), Machines.mount(getBlockState()), ld) != side) continue;
            boolean outer = (ld == Direction.WEST && l.getX() == X0) || (ld == Direction.EAST && l.getX() == X1)
                    || (ld == Direction.NORTH && l.getZ() == Z0) || (ld == Direction.SOUTH && l.getZ() == Z1);
            return outer ? hatch : null;
        }
        return null;
    }
}
