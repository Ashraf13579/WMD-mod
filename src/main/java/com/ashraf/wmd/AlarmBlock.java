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

public class AlarmBlock extends BaseEntityBlock implements net.minecraft.world.level.block.SimpleWaterloggedBlock {

    public static final MapCodec<AlarmBlock> CODEC = simpleCodec(AlarmBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final java.util.Map<Direction, VoxelShape> OUTLINE = new java.util.EnumMap<>(Direction.class);
    private static final java.util.Map<Direction, VoxelShape> COLLIDE = new java.util.EnumMap<>(Direction.class);

    private static VoxelShape meshShape(Direction facing, boolean collide) {
        java.util.Map<Direction, VoxelShape> cache = collide ? COLLIDE : OUTLINE;
        VoxelShape got = cache.get(facing);
        if (got != null) return got;
        VoxelShape out = net.minecraft.world.phys.shapes.Shapes.empty();
        for (int cx = -1; cx <= 1; cx++) for (int cy = 0; cy <= 1; cy++) for (int cz = -1; cz <= 1; cz++) {
            VoxelShape c = MachineShapes.get("alarm", new BlockPos(cx, cy, cz), facing);
            if (c == null) { out = null; break; }
            if (c.isEmpty()) continue;
            int ox, oz;
            switch (facing) {
                case EAST -> { ox = -cz; oz = cx; }
                case SOUTH -> { ox = -cx; oz = -cz; }
                case WEST -> { ox = cz; oz = -cx; }
                default -> { ox = cx; oz = cz; }
            }
            out = net.minecraft.world.phys.shapes.Shapes.or(out, c.move(ox, cy, oz));
        }
        if (out == null || out.isEmpty()) out = Block.box(0, 0, 0, 16, 16, 16);
        if (collide) out = net.minecraft.world.phys.shapes.Shapes.join(out, Block.box(0, 0, 0, 16, 24, 16), net.minecraft.world.phys.shapes.BooleanOp.AND);
        out = out.optimize();
        cache.put(facing, out);
        return out;
    }

    public AlarmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MachineBlock.MOUNT, Direction.UP).setValue(Waterlog.W, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, MachineBlock.MOUNT, Waterlog.W); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false);
        return mf == null ? null : Waterlog.placed(defaultBlockState(), context.getLevel(), context.getClickedPos()).setValue(FACING, mf[1]).setValue(MachineBlock.MOUNT, mf[0]);
    }

    @Override
    protected net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        net.minecraft.world.level.material.FluidState f = Waterlog.fluid(state);
        return f != null ? f : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Waterlog.tick(state, level, pos);
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Machines.mountShape(meshShape(state.getValue(FACING), false), Machines.mount(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Machines.mountShape(meshShape(state.getValue(FACING), true), Machines.mount(state));
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
