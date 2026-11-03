package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class InterceptorSiloBlock extends MachineBlock {

    public static final MapCodec<InterceptorSiloBlock> CODEC = simpleCodec(InterceptorSiloBlock::new);
    public static final int DEPTH = 20;
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 4; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public InterceptorSiloBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) { VoxelShape g = generated(local, facing); if (g != null) return g; return local.getY() >= 1 ? Block.box(0, 0, 0, 16, 8, 16) : Shapes.block(); }

    static boolean hollow(int lx, int lz) { return Math.abs(lx) <= 1 && lz >= 1 && lz <= 3; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false);
        if (mf == null) return null;
        Direction mount = mf[0], facing = mf[1];
        net.minecraft.network.chat.Component why = SiloPlacement.blocker(context.getLevel(), context.getClickedPos(), facing, mount, 2, 5, DEPTH, 1);
        if (why != null) { SiloPlacement.tell(context.getPlayer(), why); return null; }
        BlockPos o = context.getClickedPos();
        if (!ClaimGuard.mayPlace(context.getLevel(), context.getPlayer(), java.util.List.of(Machines.toWorld(o, facing, mount, -2, -1, 0), Machines.toWorld(o, facing, mount, 2, -1, 0),
                Machines.toWorld(o, facing, mount, -2, -1, 4), Machines.toWorld(o, facing, mount, 2, -1, 4)), o)) return null;
        return defaultBlockState().setValue(FACING, facing).setValue(MOUNT, mount);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        Direction facing = state.getValue(FACING), mount = Machines.mount(state);
        for (int x = -2; x <= 2; x++) for (int z = 0; z <= 4; z++) for (int y = -DEPTH; y <= -1; y++) {
            BlockPos p = Machines.toWorld(pos, facing, mount, x, y, z);
            level.setBlock(p, Wmd.SILO_FILLER.get().defaultBlockState().setValue(SiloFillerBlock.HOLLOW, hollow(x, z)), 2);
        }
        if (placer instanceof Player pl && level.getBlockEntity(pos) instanceof InterceptorSiloBlockEntity be) be.claim(pl);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this)) {
            if (level.getBlockEntity(pos) instanceof InterceptorSiloBlockEntity be) be.dropContents();
            Direction facing = state.getValue(FACING), mount = Machines.mount(state);
            for (int x = -2; x <= 2; x++) for (int z = 0; z <= 4; z++) for (int y = -DEPTH; y <= -1; y++) {
                BlockPos p = Machines.toWorld(pos, facing, mount, x, y, z);
                if (level.getBlockState(p).is(Wmd.SILO_FILLER.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new InterceptorSiloBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.SILO_BE.get(), InterceptorSiloBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.SILO_BE.get(), InterceptorSiloBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof InterceptorSiloBlockEntity be)) return;
        if (be.getOwner() == null) be.claim(player);
        LauncherMenu.open(player, be, "block.wmd.interceptor_silo_t3");
    }
}
