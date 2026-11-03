package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CrudeTankBlockEntity extends BlockEntity implements GeoBlockEntity, MachinePorts, GuiMachine {

    private final FluidTank tank = new FluidTank(Balance.CRUDE_TANK_MB, fs -> fs.getFluid() == WmdFluids.CRUDE_OIL.source().get());
    private int syncedMb = -1;

    Object soundHandle;
    private long fillingUntil;
    private boolean loadedOnce;

    private float shownLevel = -1;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private final IFluidHandler input = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return tank.isFluidValid(s); }
        @Override public int fill(FluidStack s, FluidAction a) { return tank.fill(s, a); }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
    };

    private final IFluidHandler output = new IFluidHandler() {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int t) { return tank.getFluid(); }
        @Override public int getTankCapacity(int t) { return tank.getCapacity(); }
        @Override public boolean isFluidValid(int t, FluidStack s) { return false; }
        @Override public int fill(FluidStack s, FluidAction a) { return 0; }
        @Override public FluidStack drain(FluidStack s, FluidAction a) { return tank.drain(s, a); }
        @Override public FluidStack drain(int max, FluidAction a) { return tank.drain(max, a); }
    };

    public CrudeTankBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.CRUDE_TANK_BE.get(), pos, state);
    }

    public int getStoredMb() { return tank.getFluidAmount(); }

    @Override public Component guiTitle() { return Component.translatable("block.wmd.crude_tank"); }
    @Override public List<FluidTank> guiTanks() { return List.of(tank); }
    @Override public List<Fluid> guiTankFluids() { return List.of(WmdFluids.CRUDE_OIL.source().get()); }
    @Override public List<Component> guiTankLabels() { return List.of(Component.translatable("gui.wmd.label.crude")); }
    @Override public boolean guiRunning() { return false; }
    @Override public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.capacity", Balance.CRUDE_TANK_MB / 1000));
    }

    public float fill() { return (float) tank.getFluidAmount() / tank.getCapacity(); }

    @Override
    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        Direction facing = getBlockState().getValue(MachineBlock.FACING);
        BlockPos l = Machines.toLocal(worldPosition, facing, Machines.mount(getBlockState()), part);
        if (l.getY() != 0 || l.getZ() != 2) return null;
        if (l.getX() == -2 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.WEST)) return input;
        if (l.getX() == 2 && side == Machines.dirToWorld(facing, Machines.mount(getBlockState()), Direction.EAST)) return output;
        return null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrudeTankBlockEntity be) {
        int now = be.tank.getFluidAmount();
        if (now != be.syncedMb && level.getGameTime() % 10 == 0) {
            be.syncedMb = now;
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CrudeTankBlockEntity be) {
        float target = be.fill();
        if (be.shownLevel < 0) be.shownLevel = target;
        be.shownLevel += (target - be.shownLevel) * 0.1F;
        if (be.isFilling()) CrudeTankClient.ensureSound(be);
    }

    public BlockPos centre() {
        return Machines.toWorld(worldPosition, getBlockState().getValue(MachineBlock.FACING), Machines.mount(getBlockState()), 0, 3, 2);
    }

    public boolean isFilling() { return level != null && level.getGameTime() < fillingUntil; }

    public float shownLevel(float partialTick) { return shownLevel < 0 ? 0 : shownLevel; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int before = tank.getFluidAmount();
        tank.readFromNBT(registries, tag);

        if (level != null && level.isClientSide) {
            if (loadedOnce && tank.getFluidAmount() > before) fillingUntil = level.getGameTime() + 40;
            loadedOnce = true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tank.writeToNBT(registries, tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
