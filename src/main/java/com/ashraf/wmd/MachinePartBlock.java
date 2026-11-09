package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MachinePartBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<MachinePartBlock> CODEC = simpleCodec(MachinePartBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    public MachinePartBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(Waterlog.W, false)); }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) { builder.add(Waterlog.W); }

    @Override
    protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        net.minecraft.world.level.material.FluidState f = Waterlog.fluid(state);
        return f != null ? f : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.core.Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos vc = level.getBlockEntity(pos) instanceof MachinePartBlockEntity be ? be.validController(level) : null;
        if (vc != null && level.getBlockState(vc).getBlock() instanceof MachineBlock mb) {
            BlockState cs = level.getBlockState(vc);
            net.minecraft.core.Direction facing = cs.getValue(MachineBlock.FACING), mount = Machines.mount(cs);
            return Machines.mountShape(mb.machineShape(level, vc, Machines.toLocal(vc, facing, mount, pos), facing), mount);
        }
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockPos vc = level.getBlockEntity(pos) instanceof MachinePartBlockEntity be ? be.validController(level) : null;
        if (vc != null && level.getBlockState(vc).getBlock() instanceof MachineBlock mb) {
            BlockState cs = level.getBlockState(vc);
            net.minecraft.core.Direction facing = cs.getValue(MachineBlock.FACING), mount = Machines.mount(cs);
            return Machines.mountShape(mb.machineCollision(level, vc, Machines.toLocal(vc, facing, mount, pos), facing), mount);
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachinePartBlockEntity(pos, state); }

    private static BlockPos controllerOf(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachinePartBlockEntity be ? be.validController(level) : null;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockPos c = level.getBlockEntity(pos) instanceof MachinePartBlockEntity be ? be.validController(level) : null;
        if (c != null && level.getBlockState(c).getDestroySpeed(level, c) < 0.0F) return 0.0F;
        if (c != null && level.getBlockState(c).getBlock() instanceof DeadHandBlock && !DeadHandBlock.mayMine(level, c, player)) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos c = controllerOf(level, pos);
        if (c != null && level.getBlockEntity(c) instanceof FusionReactorBlockEntity be) be.markMined(level);
        if (c != null && level.getBlockEntity(c) instanceof AntimatterStoreBlockEntity st) st.markMined(level);
        if (c != null && level.getBlockEntity(c) instanceof AntimatterColliderBlockEntity co) co.markMined(level);
        if (c != null && level.getBlockEntity(c) instanceof CcBuildingBlockEntity cc) cc.markMinedBy(player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && !MachineBlock.removing) {
            BlockPos c = controllerOf(level, pos);
            if (c != null && level.getBlockState(c).getBlock() instanceof MachineBlock) level.destroyBlock(c, true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos c = controllerOf(level, pos);
        if (c != null && level.getBlockState(c).getBlock() instanceof MachineBlock mb) {
            if (!level.isClientSide) mb.interact(level, c, player, hit);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
