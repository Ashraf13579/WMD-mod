package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class ListeningNodeBlockEntity extends SonarSensorBlockEntity implements CcAnimated {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 2, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1);

    static final double[] ROTOR = { 18.6 / 16.0, 22.0 / 16.0, 4.8 / 16.0 };

    private long battery = Balance.NODE_BATTERY;
    private boolean asleep;

    private int turbine;

    public ListeningNodeBlockEntity(BlockPos pos, BlockState state) { super(Wmd.LISTENING_NODE_BE.get(), pos, state, Balance.NODE_FE); }

    @Override protected double range() { return Balance.NODE_RANGE; }
    @Override protected int kind() { return SonarNet.NODE; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }
    @Override public String modeKey() { return "gui.wmd.sonar.node_mode"; }

    public net.neoforged.neoforge.energy.IEnergyStorage energyPort(BlockPos part, Direction side) { return null; }

    public long battery() { return battery; }
    public int batteryPct() { return (int) Math.round(100.0 * battery / Balance.NODE_BATTERY); }
    public boolean asleep() { return asleep; }

    private int turbineNow(ServerLevel sl) {
        double[] r = worldPoint(ROTOR[0], ROTOR[1], ROTOR[2]);
        BlockPos rp = BlockPos.containing(r[0], r[1], r[2]);
        if (!sl.getFluidState(rp).is(FluidTags.WATER)) return 0;
        for (Direction d : Direction.values()) {
            if (d == Direction.DOWN) continue;
            FluidState n = sl.getFluidState(rp.relative(d));
            if (n.is(FluidTags.WATER) && !n.isSource()) return Balance.NODE_TURBINE_FLOW;
        }
        return Balance.NODE_TURBINE_STILL;
    }

    private void power(ServerLevel sl) {
        int t = turbineNow(sl);
        battery = Math.min(Balance.NODE_BATTERY, battery + t);
        boolean was = asleep;
        if (battery < Balance.NODE_BATTERY * Balance.NODE_LOW) asleep = true;
        else if (battery >= Balance.NODE_BATTERY * Balance.NODE_WAKE) asleep = false;
        if (!asleep) {
            net.neoforged.neoforge.energy.IEnergyStorage e = energy();
            int want = Math.max(0, e.getMaxEnergyStored() - e.getEnergyStored());
            int give = (int) Math.min(want, battery);
            if (give > 0) { int took = e.receiveEnergy(give, false); battery -= took; }
        }
        if (was != asleep || t != turbine || sl.getGameTime() % 100 == 0) { turbine = t; setChanged(); sync(); }
    }

    @Override
    protected boolean work() {
        if (level instanceof ServerLevel sl) power(sl);
        return super.work();
    }

    @Override
    protected void idle() {
        super.idle();
        if (level instanceof ServerLevel sl) power(sl);
    }

    @Override protected boolean deaf(ServerLevel sl) { return asleep; }

    @Override public String deafKey() { return asleep ? "gui.wmd.sonar.deaf_battery" : null; }

    @Override
    public Component extraLine() {
        return Component.translatable(turbine > 0 ? "gui.wmd.sonar.battery_turbine" : "gui.wmd.sonar.battery", batteryPct(), turbine);
    }

    @Override
    public net.neoforged.neoforge.energy.IEnergyStorage guiEnergy() { return null; }

    @Override
    protected String detail() { return super.detail() + ", battery " + batteryPct() + "%" + (asleep ? " (asleep)" : "") + ", turbine " + turbine + " FE/t"; }

    public boolean hearing() { return isRunning() && wet && !asleep && !heard.isEmpty(); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final boolean h = hearing();
        final float spin = turbine > 0 ? (float) (tt * 0.05 * turbine / Balance.NODE_TURBINE_STILL) : 0.0F;
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("rotor").ifPresent(b -> { b.setRotY(0.0F); b.setRotZ(spin); });
        model.getBone("glow").ifPresent(b -> b.setHidden(!h || ((long) tt) % 30 > 20 || ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5)));
    }

    @Override
    protected void writeExtra(CompoundTag tag) {
        tag.putLong("lnBat", battery);
        tag.putBoolean("lnSleep", asleep);
        tag.putInt("lnTurb", turbine);
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        if (tag.contains("lnBat")) battery = Math.max(0L, Math.min(Balance.NODE_BATTERY, tag.getLong("lnBat")));
        asleep = tag.getBoolean("lnSleep");
        turbine = tag.getInt("lnTurb");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeExtra(tag);
    }
}
