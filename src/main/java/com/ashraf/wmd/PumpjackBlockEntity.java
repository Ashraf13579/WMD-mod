package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class PumpjackBlockEntity extends BlockEntity implements GeoBlockEntity, MachinePorts, GuiMachine, EmpVulnerable {

    private static class Buffer extends EnergyStorage {
        Buffer() { super(200_000, 20_000, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    private final Buffer store = new Buffer();

    @Override
    public void empDrain() { store.set(0); setChanged(); }
    private final FluidTank tank = new FluidTank(Balance.PUMPJACK_TANK_MB, fs -> fs.getFluid() == WmdFluids.CRUDE_OIL.source().get());
    private double progress;
    private boolean running;
    private List<BlockPos> cluster;
    private long nextScan;

    private float crank, crankSpeed;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private final IFluidHandler output = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
        @Override public int fill(FluidStack s, FluidAction a) { return 0; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
        @Override public FluidStack drain(int max, FluidAction a) { return tank.drain(max, a); }
    };

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.PUMPJACK_BE.get(), pos, state);
    }

    public int getStoredMb() { return tank.getFluidAmount(); }
    public boolean hasPower() { return store.getEnergyStored() >= Balance.PUMPJACK_FE_PER_TICK; }
    public boolean isRunning() { return running; }

    @Override public Component guiTitle() { return Component.translatable("block.wmd.pumpjack"); }
    @Override public List<FluidTank> guiTanks() { return List.of(tank); }
    @Override public List<Fluid> guiTankFluids() { return List.of(WmdFluids.CRUDE_OIL.source().get()); }
    @Override public List<Component> guiTankLabels() { return List.of(Component.translatable("gui.wmd.label.crude")); }
    @Override public IEnergyStorage guiEnergy() { return store; }
    @Override public int guiFeUse() { return Balance.PUMPJACK_FE_PER_TICK; }
    @Override public boolean guiRunning() { return running; }
    @Override public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.PUMPJACK_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.pumpjack", Balance.PUMPJACK_MB_PER_30S / 1000, 30));
    }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, facing, Machines.mount(getBlockState()), part);
        return l.getX() == -1 && l.getY() == 0 && l.getZ() == 0 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.WEST) ? output : null;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, facing, Machines.mount(getBlockState()), part);
        return l.getX() == 0 && l.getY() == 0 && l.getZ() == 7 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.SOUTH) ? store : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) {
            if (be.running) { be.running = false; be.setChanged(); level.sendBlockUpdated(pos, state, state, 3); }
            return;
        }
        ServerLevel sl = (ServerLevel) level;
        if (be.cluster == null || level.getGameTime() >= be.nextScan) {
            be.cluster = OilFields.clusterBelow(sl, pos);
            be.nextScan = level.getGameTime() + 100;
        }
        boolean now = be.hasPower() && !be.cluster.isEmpty() && be.tank.getFluidAmount() < be.tank.getCapacity();
        if (now) {
            be.store.use(Balance.PUMPJACK_FE_PER_TICK);
            be.progress += Balance.PUMPJACK_MB_PER_30S / 600.0;
            int want = (int) be.progress;
            if (want > 0) {
                OilFields fields = OilFields.get(sl);
                for (BlockPos p : be.cluster) {
                    int got = fields.take(sl, p, Math.min(want, be.tank.getCapacity() - be.tank.getFluidAmount()));
                    if (got > 0) {
                        be.tank.fill(new FluidStack(WmdFluids.CRUDE_OIL.source().get(), got), IFluidHandler.FluidAction.EXECUTE);
                        be.progress -= got;
                        if (level.getBlockState(p).is(Wmd.CRUDE_OIL_DEPOSIT.get()) == false) be.nextScan = 0;
                        break;
                    }
                }
            }
        }
        if (now != be.running) {
            be.running = now;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
        if (level.getGameTime() % 40 == 0) be.setChanged();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity be) {
        if (be.running) MachineLoop.ensure(be, WmdSounds.PUMPJACK_RUN.get(), be::isRunning, 1.0, 0.9F, false);
        be.crank += be.crankSpeed;
        float target = be.running ? 0.0785F : 0.0F;
        be.crankSpeed += (target - be.crankSpeed) * 0.04F;
        if (!be.running && be.crankSpeed < 0.0005F) be.crankSpeed = 0.0F;
        if (be.crank > Mth.TWO_PI * 50) be.crank -= Mth.TWO_PI * 50;
    }

    public float crankAngle(float partialTick) { return crank + crankSpeed * partialTick; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putInt("energy", store.getEnergyStored());
        tag.putDouble("progress", progress);
        tag.putBoolean("running", running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        store.set(tag.getInt("energy"));
        progress = tag.getDouble("progress");
        running = tag.getBoolean("running");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("running", running);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 8).fluid(-1, 0, 0, net.minecraft.core.Direction.WEST, "crude_oil", true).power(0, 0, 7, net.minecraft.core.Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }
}
