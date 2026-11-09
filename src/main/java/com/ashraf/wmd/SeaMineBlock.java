package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

public abstract class SeaMineBlock extends MachineBlock {

    private static final List<BlockPos> CELL = List.of(BlockPos.ZERO);
    private static final List<BlockPos> BOTTOM_CELLS = new ArrayList<>();
    static {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) BOTTOM_CELLS.add(new BlockPos(x, 0, z));
        BOTTOM_CELLS.add(new BlockPos(0, 1, 0)); BOTTOM_CELLS.add(new BlockPos(1, 1, 0)); BOTTOM_CELLS.add(new BlockPos(-1, 1, 0));
        BOTTOM_CELLS.add(new BlockPos(0, 1, 1)); BOTTOM_CELLS.add(new BlockPos(0, 1, -1));
    }

    public final int kind;

    protected SeaMineBlock(Properties properties, int kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected List<BlockPos> footprint() { return kind == SeaMineBlockEntity.K_BOTTOM ? BOTTOM_CELLS : CELL; }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        if (kind != SeaMineBlockEntity.K_BOTTOM) {
            VoxelShape g = MachineShapes.get("mine_anchor", local, facing);
            if (g != null) return g;
        }
        return super.machineShape(local, facing);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        if (!level.getFluidState(p).is(FluidTags.WATER) || SableBridge.isOnShip(level, p)) {
            if (!level.isClientSide && ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.wmd.seamine.water"), true);
            return null;
        }
        return super.getStateForPlacement(ctx);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SeaMineBlockEntity be) be.depthFromItem(SeaMineItem.depth(stack));
    }

    static String layAt(ServerLevel sl, BlockPos cell, Direction facing, ServerPlayer layer, ItemStack stack, boolean loaded) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi) || !(bi.getBlock() instanceof SeaMineBlock block)) return "lost";
        if (layer == null) return "offline";
        if (!sl.isInWorldBounds(cell) || !Waterlog.water(sl, cell) || !sl.getBlockState(cell).canBeReplaced() || SableBridge.isOnShip(sl, cell)) return "land";
        int top = NavalLayer.waterTop(sl, cell.getX(), cell.getZ());
        if (top == NavalLayer.NO_WATER || top - cell.getY() + 1 < Balance.MINE_LAY_MIN_WATER) return "shallow";
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos l : block.footprint()) {
            BlockPos p = Machines.toWorld(cell, facing, Direction.UP, l.getX(), l.getY(), l.getZ());
            if (!sl.isInWorldBounds(p) || !Waterlog.water(sl, p) || !sl.getBlockState(p).canBeReplaced()) return "room";
            cells.add(p);
        }
        if (PlaceGuard.blocker(sl, cells) != null) return "room";
        for (BlockPos p : cells) if (!sl.getWorldBorder().isWithinBounds(p) || sl.getServer().isUnderSpawnProtection(sl, p, layer)) return "claim";
        if (!ClaimGuard.mayPlace(sl, layer, cells, null)) return "claim";
        BlockState st = Waterlog.placed(block.defaultBlockState().setValue(FACING, facing), sl, cell);
        if (!sl.setBlock(cell, st, 3)) return "room";
        block.setPlacedBy(sl, cell, st, layer, stack);
        if (sl.getBlockEntity(cell) instanceof SeaMineBlockEntity be) be.laid(loaded);
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SeaMineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, Wmd.SEA_MINE_BE.get(), SeaMineBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof SeaMineBlockEntity be)) return;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            ItemStack w = be.takeWarhead();
            level.removeBlock(controller, false);
            for (ItemStack s : new ItemStack[] { new ItemStack(asItem()), w }) if (!s.isEmpty() && !player.addItem(s)) player.drop(s, false);
            return;
        }
        if (be.tryLoad(player)) { player.displayClientMessage(Component.translatable("message.wmd.seamine.loaded"), true); return; }
        if (player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, new SeaMinePayloads.Open(controller));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!ShipRelink.shipMove(level, movedByPiston) && !level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof SeaMineBlockEntity be) {
            ItemStack w = be.takeWarhead();
            if (!w.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, w);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Explosion explosion) { return false; }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof SeaMineBlockEntity be) {
            be.sympathetic(sl);
            if (level.getBlockState(pos).is(this)) super.onBlockExploded(state, level, pos, explosion);
            return;
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    public static class Influence extends SeaMineBlock {
        public static final MapCodec<Influence> CODEC = simpleCodec(Influence::new);
        public Influence(Properties p) { super(p, SeaMineBlockEntity.K_INFLUENCE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Bottom extends SeaMineBlock {
        public static final MapCodec<Bottom> CODEC = simpleCodec(Bottom::new);
        public Bottom(Properties p) { super(p, SeaMineBlockEntity.K_BOTTOM); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Captor extends SeaMineBlock {
        public static final MapCodec<Captor> CODEC = simpleCodec(Captor::new);
        public Captor(Properties p) { super(p, SeaMineBlockEntity.K_CAPTOR); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
