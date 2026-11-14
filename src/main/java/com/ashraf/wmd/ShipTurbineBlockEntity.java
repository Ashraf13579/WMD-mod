package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ShipTurbineBlockEntity extends P6DriveBlockEntity {

    private static final Map<UUID, double[]> DRIVE = Statics.server(new HashMap<>());

    private final FluidTank fuel = new FluidTank(Balance.GT_TANK, fs -> fs.getFluid() == WmdFluids.DIESEL.source().get() || fs.getFluid() == WmdFluids.JET_FUEL.source().get());
    private final IFluidHandler fuelIn = fillOnly(fuel);
    private int startTicks;
    private boolean lit;
    private double burnAcc;

    public ShipTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.SHIP_TURBINE_BE.get(), pos, state, 0, Balance.GT_START_FE * Balance.GT_START_TICKS, Balance.GT_START_FE * 2);
    }

    static double drive(ServerLevel sl, UUID ship) {
        double[] d = ship == null ? null : DRIVE.get(ship);
        return d == null || sl.getGameTime() - (long) d[1] > 20 ? 0.0 : d[0];
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.5, 1.0, -0.4 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.5, 0.9, 1.5 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected double hitSize() { return 2.5; }
    @Override protected int spoolUp() { return Balance.GT_SPOOL_UP; }
    @Override protected int spoolDown() { return Balance.GT_SPOOL_DOWN; }
    @Override protected double spinRps() { return 6.0; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        orders(sl);
        if (beat(sl)) {
            airBeat(sl);
            report(sl, 0.0, lit, 0.0, 0.0);
        }
        boolean want = Math.abs(throttle) > 0.02 && damageStage() < 3 && ship != null;
        if (!want) { lit = false; startTicks = 0; }
        else if (!lit) {
            if (fuel.getFluidAmount() > 0 && useExtraEnergy(Balance.GT_START_FE)) { if (++startTicks >= Balance.GT_START_TICKS) lit = true; }
            else startTicks = Math.max(0, startTicks - 2);
        }
        if (lit) {
            burnAcc += Balance.GT_FUEL_IDLE + (Balance.GT_FUEL_FULL - Balance.GT_FUEL_IDLE) * Math.min(1.0, Math.abs(throttle));
            int mb = (int) burnAcc;
            if (mb > 0) {
                burnAcc -= mb;
                if (fuel.drain(mb, IFluidHandler.FluidAction.EXECUTE).getAmount() < mb) { lit = false; startTicks = 0; }
            }
        }
        float target = lit ? 1.0F : startTicks > 0 ? 0.2F * startTicks / Balance.GT_START_TICKS : 0.0F;
        spoolTo(target);
        if (ship != null) {
            double[] d = DRIVE.get(ship);
            long now = sl.getGameTime();
            double give = lit ? spool : 0.0;
            if (d == null || now - (long) d[1] > 20 || give >= d[0]) DRIVE.put(ship, new double[] { give, now });
        }
        force = null;
        syncSpool(sl.getGameTime());
        return spool > 1.0E-3F || lit;
    }

    @Override
    public Component status() {
        String k = lit ? "message.wmd.ship_turbine.on" : startTicks > 0 ? "message.wmd.ship_turbine.starting"
                : fuel.getFluidAmount() <= 0 ? "message.wmd.ship_turbine.no_fuel" : "message.wmd.ship_turbine.off";
        return Component.translatable("block.wmd.ship_turbine").append(": ").append(Component.translatable(k, Math.round(spool * 100.0F), fuel.getFluidAmount()));
    }

    @Override protected String detail() { return "lit " + lit + ", start " + startTicks + ", spool " + Math.round(spool * 100) + "%, fuel " + fuel.getFluidAmount() + " mB"; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 1, 0, Direction.UP) ? energy() : null; }

    public IFluidHandler fluidPort(BlockPos part, Direction side) { return at(part, side, 1, 1, 1, Direction.UP) ? fuelIn : null; }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }
    @Override protected List<Fluid> tankFluids() { return List.of(fuel.isEmpty() ? WmdFluids.DIESEL.source().get() : fuel.getFluid().getFluid()); }
    @Override protected List<String> labelKeys() { return List.of("fuel"); }
    @Override protected int rateMbPerTick() { return 1; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float a = spinAt(pt) * RZ;
        model.getBone("shaft").ifPresent(b -> b.setRotZ(a));
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cSpool < 0.25F || lv.random.nextFloat() > cSpool * 0.6F) return;
        double[] w = worldPoint(0.5, 2.05, 2.5);
        lv.addParticle(ParticleTypes.SMOKE, w[0] + (lv.random.nextDouble() - 0.5) * 0.5, w[1], w[2] + (lv.random.nextDouble() - 0.5) * 0.5, 0.0, 0.12, 0.0);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.GAS_TURBINE_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_GIANT * (0.25F + 0.75F * cSpool); }
    @Override protected float soundPitch() { return 0.8F + 0.2F * cSpool; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 2); }

    @Override
    protected void putExtra(CompoundTag tag) {
        tag.putBoolean("gtLit", lit);
        tag.putInt("gtStart", startTicks);
    }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        if (client) return;
        lit = tag.getBoolean("gtLit");
        startTicks = tag.getInt("gtStart");
    }

}
