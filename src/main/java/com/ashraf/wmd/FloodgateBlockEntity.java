package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class FloodgateBlockEntity extends BlockEntity implements Owned {

    private final SecurityData security = new SecurityData();
    private UUID owner;
    private String ownerName = "";
    private int placed;
    private final FluidTank tank = new FluidTank(Balance.FLOODGATE_TANK_MB, fs -> fs.getFluid() == Fluids.WATER || fs.getFluid() == Fluids.LAVA) {
        @Override protected void onContentsChanged() { setChanged(); }
    };

    public FloodgateBlockEntity(BlockPos pos, BlockState state) { super(Wmd.FLOODGATE_BE.get(), pos, state); }

    @Override public SecurityData security() { return security; }
    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }

    @Override
    public void claim(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    public IFluidHandler port(Direction side) {
        if (side == getBlockState().getValue(FloodgateBlock.FACING)) return null;
        return new IFluidHandler() {
            @Override public int getTanks() { return 1; }
            @Override public FluidStack getFluidInTank(int i) { return tank.getFluid(); }
            @Override public int getTankCapacity(int i) { return tank.getCapacity(); }
            @Override public boolean isFluidValid(int i, FluidStack s) { return tank.isFluidValid(s); }
            @Override public int fill(FluidStack s, FluidAction a) { return tank.fill(s, a); }
            @Override public FluidStack drain(FluidStack s, FluidAction a) { return FluidStack.EMPTY; }
            @Override public FluidStack drain(int max, FluidAction a) { return FluidStack.EMPTY; }
        };
    }

    public Component info() {
        return Component.translatable("message.wmd.floodgate.info", tank.isEmpty() ? Component.translatable("gui.wmd.floodgate.empty") : tank.getFluid().getHoverName(),
                String.format("%,d", tank.getFluidAmount()), placed, Balance.FLOODGATE_MAX);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FloodgateBlockEntity be) {
        if (!(level instanceof ServerLevel sl) || level.getGameTime() % Balance.FLOODGATE_INTERVAL != 0) return;
        boolean pouring = be.pour(sl, pos, state);
        if (pouring != state.getValue(FloodgateBlock.POURING)) level.setBlock(pos, state.setValue(FloodgateBlock.POURING, pouring), 3);
    }

    private boolean pour(ServerLevel level, BlockPos pos, BlockState state) {
        if (placed >= Balance.FLOODGATE_MAX || tank.getFluidAmount() < 1000 || level.hasNeighborSignal(pos)) return false;
        Fluid f = tank.getFluid().getFluid();
        if (f == Fluids.WATER && level.dimensionType().ultraWarm()) return false;
        ServerPlayer op = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (op == null) return false;
        BlockPos target = find(level, pos.relative(state.getValue(FloodgateBlock.FACING)), f);
        if (target == null) return false;
        if (!ClaimGuard.mayPlaceQuiet(level, op, target)) return false;
        level.setBlock(target, f.defaultFluidState().createLegacyBlock(), 3);
        tank.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        placed++;
        setChanged();
        level.playSound(null, target, f == Fluids.LAVA ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.6F, 1.0F);
        return true;
    }

    private static BlockPos find(Level level, BlockPos start, Fluid f) {
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        todo.add(start);
        seen.add(start);
        BlockPos best = null;
        int r2 = Balance.FLOODGATE_RADIUS * Balance.FLOODGATE_RADIUS;
        while (!todo.isEmpty() && seen.size() < 2048) {
            BlockPos p = todo.poll();
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            boolean own = s.getFluidState().isSource() && s.getFluidState().getType() == f;
            boolean free = !own && (s.isAir() || (s.canBeReplaced() && !s.getFluidState().isSource()));
            if (!own && !free) continue;
            if (free && (best == null || p.getY() < best.getY() || (p.getY() == best.getY() && p.distSqr(start) < best.distSqr(start)))) best = p;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getY() > start.getY()) continue;
                int dx = n.getX() - start.getX(), dz = n.getZ() - start.getZ();
                if (dx * dx + dz * dz > r2 || start.getY() - n.getY() > Balance.FLOODGATE_RADIUS || !seen.add(n)) continue;
                todo.add(n);
            }
        }
        return best;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        security.write(tag);
        tank.writeToNBT(registries, tag);
        tag.putInt("placed", placed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        security.read(tag);
        tank.readFromNBT(registries, tag);
        placed = tag.getInt("placed");
    }
}
