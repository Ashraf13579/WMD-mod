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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public class AnchorWindlassBlock extends UnitBlock {

    public static final MapCodec<AnchorWindlassBlock> CODEC = simpleCodec(AnchorWindlassBlock::new);

    public AnchorWindlassBlock(Properties properties) { super(properties, 0, 1, 0, 0, 0, 1); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean faceAway() { return true; }

    @Override public boolean hullPlug(BlockPos local) { return local.equals(BlockPos.ZERO); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AnchorWindlassBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AnchorBlocks.ANCHOR_WINDLASS_BE.get(), AnchorWindlassBlockEntity::clientTick)
                : createTickerHelper(type, AnchorBlocks.ANCHOR_WINDLASS_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("anchor_windlass", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof AnchorWindlassBlockEntity be)) return;
        if (!AirGate.on()) { player.displayClientMessage(Component.translatable("message.wmd.anchor.gated"), true); return; }
        if (!gate(level, controller, player, be)) return;
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        interact(level, controller, player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof AnchorWindlassBlockEntity be) || !AirGate.on()) return;
        if (!Access.mayUse(be, player)) { Access.deny(be, player); return; }
        if (player.isShiftKeyDown()) { player.displayClientMessage(be.status(), true); return; }
        if (player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new AnchorPayloads.Open(controller));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.anchor_windlass", Balance.ANCHOR_WATER_MAX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.anchor_windlass.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
