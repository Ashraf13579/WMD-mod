package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ServiceReceptacleBlockEntity extends P6DriveBlockEntity {

    static final int DOOR_HOLD = 60;

    static final double[] FACE = { 0.0, 0.5, -0.5 }, NORMAL = { 0.0, 0.0, -1.0 };

    private long doorUntil = Long.MIN_VALUE / 2;
    private boolean coupled, sentOpen;
    private List<BlockPos> drivesCache = new ArrayList<>();
    private long drivesAt = Long.MIN_VALUE / 2;

    private boolean tOpen;
    private float cDoor;

    public ServiceReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(ServiceBlocks.SERVICE_RECEPTACLE_BE.get(), pos, state, 0, 1, 0);
    }

    void armSignal(ServerLevel sl, boolean coupledNow) {
        doorUntil = sl.getGameTime() + DOOR_HOLD;
        coupled = coupledNow;
        if (!sentOpen) { sentOpen = true; syncNow(); }
    }

    Vec3[] facePoint() {
        Vec3 p = SableBridge.toWorld(level, plotPoint(FACE[0], FACE[1], FACE[2]));
        Vec3 q = SableBridge.toWorld(level, plotPoint(FACE[0] + NORMAL[0], FACE[1] + NORMAL[1], FACE[2] + NORMAL[2]));
        if (p == null || q == null) return null;
        return new Vec3[] { p, q.subtract(p).normalize() };
    }

    boolean exposed() {
        if (level == null) return false;
        BlockPos f = worldPosition.relative(front());
        BlockState st = level.getBlockState(f);
        return st.isAir() || st.getCollisionShape(level, f).isEmpty();
    }

    UUID craft() { return level instanceof ServerLevel sl ? SableBridge.shipIdAt(sl, worldPosition) : null; }

    int acceptFuel(ServerLevel sl, Fluid fluid, int mb, boolean simulate) {
        if (!AirGate.on() || mb <= 0 || fluid == null) return 0;
        UUID s = craft();
        if (s == null) return 0;
        int put = 0;
        if (fluid == WmdFluids.JET_FUEL.source().get()) {
            int[] pool = PowerCoreBlockEntity.fuelOf(sl, s);
            int room = Math.max(0, pool[1] - pool[0]);
            put = simulate ? Math.min(mb, room) : PowerCoreBlockEntity.moveFuel(sl, s, Math.min(mb, room), false);
        }
        if (put < mb) put += fillDrives(sl, s, new FluidStack(fluid, mb - put), simulate);
        return put;
    }

    int acceptPower(ServerLevel sl, int fe) {
        UUID s = craft();
        return s == null ? 0 : PowerCoreBlockEntity.chargeFrom(sl, s, fe);
    }

    private int fillDrives(ServerLevel sl, UUID s, FluidStack fs, boolean simulate) {
        long now = sl.getGameTime();
        if (now - drivesAt > 40) {
            drivesAt = now;
            drivesCache = new ArrayList<>();
            for (BlockPos p : SableBridge.blockEntitiesOn(sl, s)) {
                BlockEntity be = sl.getBlockEntity(p);
                if (be instanceof ShipTurbineBlockEntity || be instanceof SubDieselBlockEntity) drivesCache.add(p);
            }
        }
        int put = 0;
        for (BlockPos p : drivesCache) {
            if (put >= fs.getAmount()) break;
            BlockEntity be = sl.getBlockEntity(p);
            IFluidHandler h = drivePort(be);
            if (h == null) continue;
            int n = h.fill(new FluidStack(fs.getFluid(), fs.getAmount() - put), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (n > 0) put += n;
        }
        return put;
    }

    static IFluidHandler drivePort(BlockEntity be) {
        if (be == null || be.isRemoved()) return null;
        BlockState st = be.getBlockState();
        Direction f = Machines.facing(st), m = Machines.mount(st);
        if (be instanceof ShipTurbineBlockEntity t) return t.fluidPort(Machines.toWorld(be.getBlockPos(), f, m, 1, 1, 1), Machines.dirToWorld(f, m, Direction.UP));
        if (be instanceof SubDieselBlockEntity d) return d.fluidPort(Machines.toWorld(be.getBlockPos(), f, m, 1, 0, 0), Machines.dirToWorld(f, m, Direction.NORTH));
        return null;
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }
    @Override protected boolean damageable() { return false; }
    @Override protected int spoolUp() { return 1; }
    @Override protected int spoolDown() { return 1; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        boolean open = AirGate.on() && sl.getGameTime() < doorUntil;
        if (!open) coupled = false;
        if (open != sentOpen) { sentOpen = open; syncNow(); }
        force = null;
        return false;
    }

    @Override
    public Component status() {
        return Component.translatable("block.wmd.service_receptacle").append(": ")
                .append(Component.translatable(coupled ? "message.wmd.service_receptacle.coupled" : sentOpen ? "message.wmd.service_receptacle.open" : "message.wmd.service_receptacle.closed"));
    }

    @Override protected String detail() { return (coupled ? "coupled" : sentOpen ? "door open" : "door closed") + (ship != null ? ", ship " + ship : ", not on a ship"); }

    @Override
    protected void clientExtra(Level lv) { cDoor = Mth.approach(cDoor, tOpen ? 1.0F : 0.0F, 1.0F / 16.0F); }

    @Override protected boolean runningClient() { return false; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float k = cDoor * cDoor * (3.0F - 2.0F * cDoor);
        model.getBone("door").ifPresent(b -> b.setRotX(105.0F * k * Mth.DEG_TO_RAD * RX));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!tOpen));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.SA_DOOR.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected boolean soundAlive() { return false; }

    @Override
    protected void putExtra(CompoundTag tag) { tag.putBoolean("srOpen", sentOpen); }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        boolean o = tag.getBoolean("srOpen");
        if (client) {
            if (o != tOpen && level != null) level.playLocalSound(worldPosition, WmdSounds.SA_DOOR.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, o ? 1.05F : 0.9F, false);
            tOpen = o;
        }
    }
}
