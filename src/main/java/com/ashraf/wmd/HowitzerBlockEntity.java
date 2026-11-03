package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class HowitzerBlockEntity extends ArtilleryGunBlockEntity {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 2, Z0 = 0, Z1 = 4;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).item(1, 0, 2, Direction.EAST, false).power(0, 0, 4, Direction.SOUTH, false);

    static final float RECOIL_PX = 10.0F, RECOIL_SIGN = 1.0F, RAM_DEG = -120.26F;

    private final Slot charges = new Slot(Balance.HOWITZER_CHARGE_SLOT, false, s -> s.is(wmd("propellant_charge")));

    private final Slot[] rack = new Slot[Balance.RACK_SLOTS];
    {
        for (int k = 0; k < rack.length; k++) rack[k] = new Slot(Balance.RACK_MAGAZINE, false, s -> s.getItem() instanceof ArtShellItem a && Artillery.isSpecial(a.type()));
    }
    private final Slot[] slots = { shellSlots[0], shellSlots[1], shellSlots[2], shellSlots[3], shellSlots[4], rack[0], rack[1], rack[2], charges };
    private final List<FluidTank> tanks = gauges(slots);
    private final IItemHandler hatch = inHandler(slots);

    public HowitzerBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.HOWITZER_BE.get(), pos, state, Balance.HOWITZER_FE_PER_TICK);
    }

    @Override public int gun() { return Artillery.HOWITZER; }
    @Override protected int magazine() { return Balance.HOWITZER_MAGAZINE; }
    @Override protected int layTicks() { return Balance.HOWITZER_LAY_TICKS; }

    @Override protected double[] pivotLocal() { return new double[] { 0.0, 0.0, 2.0 }; }
    @Override protected double trunnionHeight() { return 1.3125; }
    @Override protected double barrelLength() { return 2.475; }
    @Override protected float restElevation() { return 0.0F; }
    @Override protected Slot chargeSlot() { return charges; }
    @Override protected Slot rackSlot(int k) { return k >= 0 && k < rack.length ? rack[k] : null; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float soundVolume() { return 0.25F; }

    public int propellant() { return charges.count(); }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 1, 0, 2, Direction.EAST) ? hatch : null; }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 4, Direction.SOUTH) ? energy() : null; }

    @Override
    protected void animateExtra(CcBones model, double tt, float pt, double since) {
        float recoil = 0.0F;
        if (since >= 0.0 && since < 2.0) recoil = (float) (since / 2.0);
        else if (since >= 2.0 && since < 20.0) recoil = (float) (1.0 - (since - 2.0) / 18.0);
        final float r = RECOIL_SIGN * RECOIL_PX * recoil * recoil * (3.0F - 2.0F * recoil);
        model.getBone("barrel").ifPresent(b -> b.setPosZ(r));
        double a = 30.0, e = Math.max(a + 20.0, Balance.HOWITZER_RELOAD_TICKS - 20.0);
        float ram = since >= a && since <= e ? (float) Math.sin(Math.PI * (since - a) / (e - a)) : 0.0F;
        final float rr = (float) Math.toRadians(RAM_DEG * ram);
        model.getBone("rammer").ifPresent(b -> b.setRotZ(rr));
    }
}
