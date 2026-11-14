package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.Vec3;

public class EngineTelegraphBlock extends UnitBlock {

    public static final MapCodec<EngineTelegraphBlock> CODEC = simpleCodec(EngineTelegraphBlock::new);

    public EngineTelegraphBlock(Properties properties) { super(properties, 0, 0, 0, 1, 0, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EngineTelegraphBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.ENGINE_TELEGRAPH_BE.get(), EngineTelegraphBlockEntity::clientTickTelegraph)
                : createTickerHelper(type, Wmd.ENGINE_TELEGRAPH_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof EngineTelegraphBlockEntity be)) return;
        BlockState st = level.getBlockState(controller);
        Direction facing = st.getValue(FACING);
        BlockPos local = Machines.toLocal(controller, facing, hit.getBlockPos());
        boolean right = viewerRight(level, hit, facing);
        if (local.getY() >= 1) {
            if (player.isShiftKeyDown()) { player.displayClientMessage(be.status(), true); return; }
            be.stepLever(player, right ? 1 : -1);
        } else {
            be.turnWheel(player, player.isShiftKeyDown() ? 0 : right ? 1 : -1);
        }
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof EngineTelegraphBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    static boolean viewerRight(Level level, BlockHitResult hit, Direction facing) {
        BlockPos b = hit.getBlockPos();
        Vec3 at = hit.getLocation();
        Vec3 rel = at.subtract(b.getX(), b.getY(), b.getZ());
        if (Math.abs(rel.x - 0.5) > 0.6 || Math.abs(rel.y - 0.5) > 0.6 || Math.abs(rel.z - 0.5) > 0.6) {
            UUID ship = SableBridge.shipIdAt(level, b);
            Vec3 p = ship == null ? null : SableBridge.toShip(level, ship, at);
            if (p != null) rel = p.subtract(b.getX(), b.getY(), b.getZ());
        }
        double lx = switch (facing) {
            case SOUTH -> 1.0 - rel.x;
            case EAST -> rel.z;
            case WEST -> 1.0 - rel.z;
            default -> rel.x;
        };
        return lx < 0.5;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.engine_telegraph").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
