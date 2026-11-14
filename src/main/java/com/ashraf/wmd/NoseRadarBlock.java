package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

public class NoseRadarBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<NoseRadarBlock> CODEC = simpleCodec(NoseRadarBlock::new);

    public NoseRadarBlock(Properties properties) { super(properties, 0, 0, 0, 0, -1, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction f = ctx.getClickedFace();
        return new Direction[] { Direction.UP, f.getAxis().isHorizontal() ? f : ctx.getHorizontalDirection() };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NoseRadarBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, AirBlocks.NOSE_RADAR_BE.get(), NoseRadarBlockEntity::clientTickNose)
                : createTickerHelper(type, AirBlocks.NOSE_RADAR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        boolean flush = level != null && controller != null && level.getBlockEntity(controller) instanceof NoseRadarBlockEntity be && be.flush();
        VoxelShape g = MachineShapes.get(flush ? "nose_radar_flush" : "nose_radar_ogive", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("nose_radar_ogive", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof NoseRadarBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown()) {
            be.toggleFront();
            player.displayClientMessage(Component.translatable(be.flush() ? "message.wmd.nose_radar.to_flush" : "message.wmd.nose_radar.to_ogive"), true);
            level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        if (held.isEmpty() && player.isShiftKeyDown() && level instanceof ServerLevel sl) {
            Component why = be.toggleRadome(sl);
            if (why != null) player.displayClientMessage(why, true);
            return;
        }
        interact(level, controller, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.nose_radar").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.nose_radar.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
