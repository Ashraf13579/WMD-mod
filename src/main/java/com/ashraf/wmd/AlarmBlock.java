package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AlarmBlock extends BaseEntityBlock {

    public static final MapCodec<AlarmBlock> CODEC = simpleCodec(AlarmBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape NS = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape EW = NS;
    private static final VoxelShape COL_NS = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape COL_EW = COL_NS;

    public AlarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, MachineBlock.MOUNT); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false);
        return mf == null ? null : defaultBlockState().setValue(FACING, mf[1]).setValue(MachineBlock.MOUNT, mf[0]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Machines.mountShape(state.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EW, Machines.mount(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Machines.mountShape(state.getValue(FACING).getAxis() == Direction.Axis.Z ? COL_NS : COL_EW, Machines.mount(state));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AlarmBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.ALARM_BE.get(), AlarmBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.ALARM_BE.get(), AlarmBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) be.claim(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AlarmBlockEntity be) {
            if (be.getOwner() == null) be.claim(player);
            if (!Access.mayUse(be, player)) {
                player.displayClientMessage(Component.translatable("message.wmd.alarm.not_yours", be.getOwnerName()), true);
            } else {
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AlarmMenu(id, inv, be),
                        Component.translatable("block.wmd.alarm")), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
