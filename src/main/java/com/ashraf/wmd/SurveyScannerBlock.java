package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SurveyScannerBlock extends UnitBlock {

    public static final MapCodec<SurveyScannerBlock> CODEC = simpleCodec(SurveyScannerBlock::new);

    public SurveyScannerBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 0); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SurveyScannerBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AirBlocks.SURVEY_SCANNER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, AirBlocks.SURVEY_SCANNER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        if (ctx.getClickedFace() == Direction.UP) return null;
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("survey_scanner", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("survey_scanner", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof SurveyScannerBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        if (player.getMainHandItem().isEmpty() && !player.isShiftKeyDown() && player instanceof ServerPlayer sp) { be.use(sp); return; }
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof SurveyScannerBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SurveyScannerBlockEntity be ? be.comparator() : 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.survey_scanner").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.survey_scanner.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
