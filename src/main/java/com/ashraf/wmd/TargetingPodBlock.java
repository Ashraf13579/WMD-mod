package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

public class TargetingPodBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<TargetingPodBlock> CODEC = simpleCodec(TargetingPodBlock::new);

    public TargetingPodBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 1); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TargetingPodBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AirBlocks.TARGETING_POD_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, AirBlocks.TARGETING_POD_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        boolean flush = level != null && controller != null && level.getBlockEntity(controller) instanceof TargetingPodBlockEntity be && be.flush();
        VoxelShape g = MachineShapes.get(flush ? "targeting_flush" : "targeting_pod", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("targeting_pod", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof TargetingPodBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (held.is(Wmd.WRENCH.get()) && !player.isShiftKeyDown()) {
            be.toggleLook();
            player.displayClientMessage(Component.translatable(be.flush() ? "message.wmd.tpod.to_flush" : "message.wmd.tpod.to_pod"), true);
            level.playSound(null, hit.getBlockPos(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof TargetingPodBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.targeting_pod").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.targeting_pod.use", (int) Balance.TPOD_RANGE).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
