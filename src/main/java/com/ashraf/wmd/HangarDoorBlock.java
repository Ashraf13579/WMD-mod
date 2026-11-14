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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HangarDoorBlock extends UnitBlock {

    public static final MapCodec<HangarDoorBlock> CODEC = simpleCodec(HangarDoorBlock::new);

    public HangarDoorBlock(Properties properties) {
        super(properties, 0, 0, 0, 0, 0, 0);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public boolean faceAway() { return true; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState s = super.getStateForPlacement(context);
        if (s == null || Machines.mount(s) != Direction.UP) return null;
        return s;
    }

    static boolean same(BlockGetter level, BlockPos p, Direction facing) {
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof HangarDoorBlock && s.getValue(FACING) == facing;
    }

    static BlockPos neighbour(BlockGetter level, BlockPos from, Direction facing, int side) {
        for (int o = 0; o <= Balance.HDOOR_STEP_MAX; o++) {
            BlockPos a = Machines.toWorld(from, facing, side, o, 0);
            if (same(level, a, facing)) return a;
            if (o == 0) continue;
            BlockPos b = Machines.toWorld(from, facing, side, -o, 0);
            if (same(level, b, facing)) return b;
        }
        return null;
    }

    static HangarDoorBlockEntity headOf(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        BlockPos p = pos;
        if (st.getBlock() instanceof HangarCurtainBlock) {
            Direction f = st.getValue(HangarCurtainBlock.FACING);
            p = null;
            BlockPos q = pos;
            for (int i = 0; i <= Balance.HDOOR_DROP_MAX && q != null; i++) {
                q = q.above();
                BlockState a = level.getBlockState(q);
                if (a.getBlock() instanceof HangarDoorBlock && a.getValue(FACING) == f) { p = q; break; }
                if (!(a.getBlock() instanceof HangarCurtainBlock) || a.getValue(HangarCurtainBlock.FACING) != f) q = null;
            }
            if (p == null) return null;
            st = level.getBlockState(p);
        }
        if (!(st.getBlock() instanceof HangarDoorBlock)) return null;
        Direction f = st.getValue(FACING);
        for (int i = 0; i < Balance.HDOOR_W_MAX; i++) {
            BlockPos l = neighbour(level, p, f, -1);
            if (l == null) break;
            p = l;
        }
        return level.getBlockEntity(p) instanceof HangarDoorBlockEntity h ? h : null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HangarDoorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, HangarDoorBlocks.HANGAR_DOOR_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, HangarDoorBlocks.HANGAR_DOOR_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        HangarDoorBlockEntity h = headOf(level, pos);
        return h == null ? 0 : h.comparatorOutput();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        HangarDoorBlockEntity.click(level, controller, player, hit, false);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        HangarDoorBlockEntity h = headOf(level, controller);
        player.displayClientMessage(h == null ? Component.translatable("message.wmd.hdoor.no_head") : h.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.hangar_door", Balance.HDOOR_W_MIN, Balance.HDOOR_W_MAX, Balance.HDOOR_DROP_MAX).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.hangar_door.use").withStyle(ChatFormatting.GRAY));
    }
}
