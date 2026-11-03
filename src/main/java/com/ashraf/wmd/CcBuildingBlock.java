package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CcBuildingBlock extends UnitBlock {

    public static final MapCodec<CcBuildingBlock> CODEC = simpleCodec(CcBuildingBlock::new);
    private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape STEP = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape THRESHOLD = Block.box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape PLANE_X = Block.box(0, 0, 7, 16, 16, 9), PLANE_Z = Block.box(7, 0, 0, 9, 16, 16);

    public CcBuildingBlock(Properties properties) {
        super(properties, CcBuildingSpec.X0, CcBuildingSpec.X1, CcBuildingSpec.Y0, CcBuildingSpec.Y1, CcBuildingSpec.Z0, CcBuildingSpec.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CcBuildingBlockEntity(pos, state); }

    @Override
    protected List<BlockPos> footprint() { return CcBuildingSpec.footprint(); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false);
        if (mf == null) return null;
        Direction mount = mf[0], facing = mf[1];
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int x = CcBuildingSpec.X0; x <= CcBuildingSpec.X1; x++) for (int y = CcBuildingSpec.Y0; y <= CcBuildingSpec.Y1; y++)
            for (int z = CcBuildingSpec.Z0; z <= CcBuildingSpec.Z1; z++) {
                BlockPos p = Machines.toWorld(origin, facing, mount, x, y, z);
                if (p.equals(origin)) continue;
                if (!level.isInWorldBounds(p) || !level.getBlockState(p).canBeReplaced()) {
                    if (!level.isClientSide && context.getPlayer() != null)
                        context.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.ccb.no_space", p.getX(), p.getY(), p.getZ()), true);
                    return null;
                }
            }

        net.minecraft.world.entity.Entity in = PlaceGuard.blocker(level, Machines.toWorld(origin, facing, mount, CcBuildingSpec.X0, CcBuildingSpec.Y0, CcBuildingSpec.Z0),
                Machines.toWorld(origin, facing, mount, CcBuildingSpec.X1, CcBuildingSpec.Y1, CcBuildingSpec.Z1));
        if (in != null) { PlaceGuard.tell(level, context.getPlayer(), in); return null; }
        if (!ClaimGuard.mayPlaceBox(level, context.getPlayer(), Machines.toWorld(origin, facing, mount, CcBuildingSpec.X0, CcBuildingSpec.Y0, CcBuildingSpec.Z0),
                Machines.toWorld(origin, facing, mount, CcBuildingSpec.X1, CcBuildingSpec.Y0, CcBuildingSpec.Z1), origin)) return null;

        return defaultBlockState().setValue(FACING, facing).setValue(MOUNT, mount);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof CcBuildingBlockEntity b) CcBuildingMenu.open(player, b);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof CcBuildingBlockEntity b)) return;
        if (!gate(level, controller, player, b)) return;
        Direction f = level.getBlockState(controller).getValue(FACING), mount = Machines.mount(level.getBlockState(controller));
        BlockPos local = Machines.toLocal(controller, f, mount, hit.getBlockPos());
        if (!player.isShiftKeyDown() && CcBuildingSpec.isDoorClick(local)) { b.doorClick(player); return; }
        CcBuildingMenu.open(player, b);
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        if (g != null) return g;
        if (CcBuildingSpec.isDoorway(local)) return Shapes.empty();
        if (local.getZ() == 0 && local.getY() == 0 && local.getX() >= -1 && local.getX() <= 1) return STEP;
        return FULL;
    }

    private static boolean open(BlockGetter level, BlockPos controller) {
        return level.getBlockEntity(controller) instanceof CcBuildingBlockEntity b && b.isDoorOpen();
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        if (!CcBuildingSpec.isDoorCell(local)) return machineShape(local, facing);
        if (!open(level, controller)) {
            VoxelShape g = machineShape(local, facing);
            return g.isEmpty() ? FULL : g;
        }
        VoxelShape plane = facing.getAxis() == Direction.Axis.Z ? PLANE_X : PLANE_Z;
        return Shapes.or(plane, openCollision(local, facing));
    }

    @Override
    public VoxelShape machineCollision(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        if (CcBuildingSpec.isDoorCell(local) && open(level, controller)) return openCollision(local, facing);
        return machineShape(level, controller, local, facing);
    }

    private VoxelShape openCollision(BlockPos local, Direction facing) {
        if (local.getY() != 1) return Shapes.empty();
        VoxelShape g = generated(local, facing);
        return g == null ? Shapes.empty() : Shapes.join(g, THRESHOLD, BooleanOp.AND);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof CcBuildingBlockEntity b) b.markMinedBy(player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof CcBuildingBlockEntity b) {
            if (!b.minedByOwner() && level instanceof net.minecraft.server.level.ServerLevel sl) DeadHand.ccLost(sl, b.getOwner(), pos);
            b.dismantle();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.CC_BUILDING_BE.get(), CcBuildingBlockEntity::clientTickBuilding)
                : createTickerHelper(type, Wmd.CC_BUILDING_BE.get(), CcBuildingBlockEntity::serverTickBuilding);
    }
}
