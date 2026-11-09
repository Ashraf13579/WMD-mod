package com.ashraf.wmd;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DriveCouplingBlock extends HorizontalKineticBlock implements IBE<DriveCouplingBlockEntity> {

    private static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 15.0, 15.5);

    public DriveCouplingBlock(Properties props) { super(props); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction f = clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection();
        return defaultBlockState().setValue(HORIZONTAL_FACING, f);
    }

    @Override public Direction.Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }

    @Override public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) { return face == state.getValue(HORIZONTAL_FACING); }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DriveCouplingBlockEntity be)) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) be.toggleReverse(); else be.nextRpm();
        player.displayClientMessage(Component.translatable("message.wmd.drive_coupling.set", be.rpm(),
                Component.translatable(be.reversed() ? "message.wmd.drive_coupling.reverse" : "message.wmd.drive_coupling.forward")), true);
        return InteractionResult.SUCCESS;
    }

    @Override public Class<DriveCouplingBlockEntity> getBlockEntityClass() { return DriveCouplingBlockEntity.class; }

    @Override public BlockEntityType<? extends DriveCouplingBlockEntity> getBlockEntityType() { return CreateCompat.DRIVE_COUPLING_BE.get(); }
}
