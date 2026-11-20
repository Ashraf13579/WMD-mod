package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SuitRackBlock extends UnitBlock {

    public static final MapCodec<SuitRackBlock> CODEC = simpleCodec(SuitRackBlock::new);

    public SuitRackBlock(Properties properties) {
        super(properties, 0, 0, 0, 1, 0, 0);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SuitRackBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ExoSuitBlocks.SUIT_RACK_BE.get(), SuitRackBlockEntity::clientTickRack)
                : createTickerHelper(type, ExoSuitBlocks.SUIT_RACK_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof SuitRackBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        if (be.holster(player)) return;
        if (!be.standsIn(player)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) sp.openMenu(be.menu(), buf -> buf.writeBlockPos(controller));
            return;
        }
        be.use(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.suit_rack", String.format("%,d", Balance.RACK_CHARGE_FE)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.suit_rack.use").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return walkIn(0, state.getValue(FACING));
    }

    @Override
    public VoxelShape machineCollision(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) { return walkIn(local.getY(), facing); }

    static VoxelShape walkIn(int y, Direction facing) {
        VoxelShape back = turned(0, 0, 12, 16, 16, 16, facing);
        return y == 0 ? Shapes.or(back, Block.box(0, 0, 0, 16, 1, 16)) : back;
    }

    private static VoxelShape turned(double x0, double y0, double z0, double x1, double y1, double z1, Direction facing) {
        double ax, az, bx, bz;
        switch (facing) {
            case EAST -> { ax = 16 - z0; az = x0; bx = 16 - z1; bz = x1; }
            case SOUTH -> { ax = 16 - x0; az = 16 - z0; bx = 16 - x1; bz = 16 - z1; }
            case WEST -> { ax = z0; az = 16 - x0; bx = z1; bz = 16 - x1; }
            default -> { ax = x0; az = z0; bx = x1; bz = z1; }
        }
        return Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz));
    }
}
