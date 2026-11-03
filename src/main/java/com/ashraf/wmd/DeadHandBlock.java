package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DeadHandBlock extends UnitBlock {

    public static final MapCodec<DeadHandBlock> CODEC = simpleCodec(DeadHandBlock::new);

    private static final VoxelShape SILL = Block.box(0, 0, 0, 16, 12, 16), LINTEL = Block.box(0, 12, 0, 16, 16, 16);

    public DeadHandBlock(Properties properties) {
        super(properties, DeadHandBlockEntity.X0, DeadHandBlockEntity.X1, DeadHandBlockEntity.Y0, DeadHandBlockEntity.Y1, DeadHandBlockEntity.Z0, DeadHandBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DeadHandBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.DEADHAND_BE.get(), DeadHandBlockEntity::clientTickDh)
                : createTickerHelper(type, Wmd.DEADHAND_BE.get(), DeadHandBlockEntity::serverTickDh);
    }

    static boolean doorCell(BlockPos local) { return local.getX() == 0 && local.getZ() == 2 && local.getY() >= 0 && local.getY() <= 2; }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof DeadHandBlockEntity be)) return;
        Direction f = level.getBlockState(controller).getValue(FACING), mount = Machines.mount(level.getBlockState(controller));
        if (!player.isShiftKeyDown() && doorCell(Machines.toLocal(controller, f, mount, hit.getBlockPos()))) { be.doorClick(player); return; }
        super.interact(level, controller, player, hit);
    }

    @Override
    public VoxelShape machineCollision(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        if (doorCell(local) && level.getBlockEntity(controller) instanceof DeadHandBlockEntity be && be.doorOpen())
            return local.getY() == 0 ? SILL : local.getY() == 2 ? LINTEL : Shapes.empty();
        return super.machineCollision(level, controller, local, facing);
    }

    static boolean mayMine(BlockGetter level, BlockPos controller, Player player) {
        return !(level.getBlockEntity(controller) instanceof DeadHandBlockEntity be) || be.getOwner() == null || Access.mayUse(be, player);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return mayMine(level, pos, player) ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level instanceof ServerLevel sl) DeadHand.remove(sl, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
