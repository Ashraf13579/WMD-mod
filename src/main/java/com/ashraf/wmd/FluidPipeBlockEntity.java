package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FluidPipeBlockEntity extends BlockEntity {

    private final FluidTank tank;

    private int cutMask;

    private final int rate;
    private net.minecraft.world.level.material.Fluid synced = net.minecraft.world.level.material.Fluids.EMPTY;
    private int shownColour;

    private net.minecraft.world.level.material.Fluid memory = net.minecraft.world.level.material.Fluids.EMPTY;

    private net.minecraft.world.level.material.Fluid lineChecked;

    public int fluidColour() {
        if (tank.isEmpty()) return 0;
        net.minecraft.world.level.material.Fluid f = tank.getFluid().getFluid();
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == f) return e.color() | 0xFF000000;
        return f == net.minecraft.world.level.material.Fluids.LAVA ? 0xFFE8641E : 0xFF3F76E4;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public FluidPipeBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FLUID_PIPE_BE.get(), pos, state);
        int tier = state.getBlock() instanceof FluidPipeBlock p ? p.tier : 1;
        this.tank = new FluidTank(Balance.PIPE_CAPACITY_TIER[tier - 1], f -> sameKind(memory, f.getFluid()));
        this.rate = Balance.PIPE_RATE_TIER[tier - 1];
    }

    public boolean isCut(Direction d) { return (cutMask & (1 << d.ordinal())) != 0; }

    void setCut(Direction d, boolean c) { cutMask = c ? cutMask | (1 << d.ordinal()) : cutMask & ~(1 << d.ordinal()); setChanged(); }

    static boolean passes(BlockState fs, Direction d) {
        if (!(fs.getBlock() instanceof FluidValveBlock)) return true;
        return fs.getValue(FluidValveBlock.OPEN) && (!fs.getValue(FluidValveBlock.ONE_WAY) || d == fs.getValue(FluidValveBlock.FACING));
    }

    public IFluidHandler getHandler() { return tank; }

    public net.minecraft.world.level.material.Fluid kind() { return tank.isEmpty() ? memory : tank.getFluid().getFluid(); }

    static boolean sameKind(net.minecraft.world.level.material.Fluid a, net.minecraft.world.level.material.Fluid b) {
        return a == net.minecraft.world.level.material.Fluids.EMPTY || b == net.minecraft.world.level.material.Fluids.EMPTY || a.isSame(b);
    }

    static boolean sameLine(net.minecraft.world.level.LevelReader level, BlockPos a, BlockPos b) {
        return !(level.getBlockEntity(a) instanceof FluidPipeBlockEntity pa) || !(level.getBlockEntity(b) instanceof FluidPipeBlockEntity pb) || sameKind(pa.kind(), pb.kind());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FluidPipeBlockEntity be) {
        FluidTank tank = be.tank;
        int pulledFrom = 0;

        for (Direction d : Direction.values()) {
            if (FluidPipeBlock.connOf(state, d) != FluidPipeBlock.Conn.PORT || !passes(state, d.getOpposite())) continue;
            int space = tank.getCapacity() - tank.getFluidAmount();
            if (space <= 0) break;
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(d), d.getOpposite());
            if (h == null) continue;
            int want = Math.min(be.rate, space);
            net.minecraft.world.level.material.Fluid k = be.kind();
            FluidStack got = k == net.minecraft.world.level.material.Fluids.EMPTY ? h.drain(want, IFluidHandler.FluidAction.EXECUTE)
                    : h.drain(new FluidStack(k, want), IFluidHandler.FluidAction.EXECUTE);
            if (!got.isEmpty()) {
                tank.fill(got, IFluidHandler.FluidAction.EXECUTE);
                pulledFrom |= 1 << d.ordinal();
            }
        }

        for (Direction d : Direction.values()) {
            if (tank.isEmpty()) break;
            if (FluidPipeBlock.connOf(state, d) != FluidPipeBlock.Conn.PORT || (pulledFrom & (1 << d.ordinal())) != 0 || !passes(state, d)) continue;
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(d), d.getOpposite());
            if (h == null) continue;
            int sent = h.fill(new FluidStack(tank.getFluid().getFluid(), Math.min(be.rate, tank.getFluidAmount())), IFluidHandler.FluidAction.EXECUTE);
            if (sent > 0) tank.drain(sent, IFluidHandler.FluidAction.EXECUTE);
        }

        for (Direction d : Direction.values()) {
            if (tank.isEmpty()) break;
            if (FluidPipeBlock.connOf(state, d) != FluidPipeBlock.Conn.PIPE) continue;
            if (!(level.getBlockEntity(pos.relative(d)) instanceof FluidPipeBlockEntity other)) continue;
            if (!passes(state, d) || !passes(other.getBlockState(), d)) continue;
            if (!sameKind(other.kind(), tank.getFluid().getFluid())) continue;

            int diff = tank.getFluidAmount() * other.tank.getCapacity() - other.tank.getFluidAmount() * tank.getCapacity();
            if (diff <= other.tank.getCapacity()) continue;
            int move = Math.min(Math.min(be.rate, other.rate), Math.max(1, (int) ((long) diff / (tank.getCapacity() + other.tank.getCapacity()))));
            int taken = other.tank.fill(new FluidStack(tank.getFluid().getFluid(), move), IFluidHandler.FluidAction.EXECUTE);
            if (taken > 0) tank.drain(taken, IFluidHandler.FluidAction.EXECUTE);
        }

        net.minecraft.world.level.material.Fluid now = tank.isEmpty() ? net.minecraft.world.level.material.Fluids.EMPTY : tank.getFluid().getFluid();
        if (now != be.synced) { be.synced = now; level.sendBlockUpdated(pos, state, state, 3); }

        if (now != net.minecraft.world.level.material.Fluids.EMPTY && now != be.memory) { be.memory = now; be.setChanged(); }
        net.minecraft.world.level.material.Fluid line = be.kind();
        if (line != be.lineChecked) { be.lineChecked = line; FluidPipeBlock.refreshAround(level, pos); }
        if (level.getGameTime() % 20 == 0) be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putInt("cut", cutMask);
        if (memory != net.minecraft.world.level.material.Fluids.EMPTY) tag.putString("mem", net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(memory).toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        cutMask = tag.getInt("cut");
        net.minecraft.resources.ResourceLocation mem = tag.contains("mem") ? net.minecraft.resources.ResourceLocation.tryParse(tag.getString("mem")) : null;
        memory = mem == null ? net.minecraft.world.level.material.Fluids.EMPTY : net.minecraft.core.registries.BuiltInRegistries.FLUID.get(mem);
        if (level != null && level.isClientSide) {
            int c = fluidColour();
            if (c != shownColour) { shownColour = c; level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8); }
        }
    }

    public void clear() { tank.setFluid(FluidStack.EMPTY); memory = net.minecraft.world.level.material.Fluids.EMPTY; setChanged(); }
}
