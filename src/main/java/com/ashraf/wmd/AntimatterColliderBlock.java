package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AntimatterColliderBlock extends UnitBlock {

    public static final MapCodec<AntimatterColliderBlock> CODEC = simpleCodec(AntimatterColliderBlock::new);
    public static final int X0 = -16, X1 = 16, Y1 = 8, Z1 = 32;

    private static List<BlockPos> cells;

    public AntimatterColliderBlock(Properties properties) {
        super(properties, 0, 0, 0, 0, 0, 0);
    }

    static boolean gatePassage(int x, int y, int z) { return x >= 13 && x <= 16 && z >= 14 && z <= 18 && y <= 1; }

    static boolean plugCell(int x, int y, int z) { return x == 5 && y == 1 && z == 5; }

    @Override
    protected List<BlockPos> footprint() {
        if (cells == null) {
            List<BlockPos> l = new ArrayList<>();
            l.add(BlockPos.ZERO);
            for (BlockPos p : MachineShapes.cells("antimatter_collider")) {
                if (p.equals(BlockPos.ZERO) || p.getX() < X0 || p.getX() > X1 || p.getY() < 0 || p.getY() > Y1 || p.getZ() < 0 || p.getZ() > Z1) continue;
                if (gatePassage(p.getX(), p.getY(), p.getZ()) || plugCell(p.getX(), p.getY(), p.getZ())) continue;
                l.add(p);
            }
            if (l.size() < 100) Wmd.LOGGER.error("[antimatter] collider footprint has only {} cells: data/wmd/machine_shapes/antimatter_collider.json missing?", l.size());
            cells = List.copyOf(l);
        }
        return cells;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean tear = !ShipRelink.shipMove(level, movedByPiston) && !newState.is(this) && !level.isClientSide;
        if (tear && level.getBlockEntity(pos) instanceof AntimatterColliderBlockEntity be) be.removed(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (tear) clearStrays(level, pos, state.getValue(FACING), Machines.mount(state), true);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.player.Player player) {
        if (level.getBlockEntity(pos) instanceof AntimatterColliderBlockEntity be) be.markMined(level);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AntimatterColliderBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.ANTIMATTER_COLLIDER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.ANTIMATTER_COLLIDER_BE.get(), (lv, p, st, be) -> { be.healParts(); ProcessUnitBlockEntity.serverTick(lv, p, st, be); });
    }

    static int clearStrays(Level level, BlockPos ctrl, Direction facing, Direction mount, boolean all) {
        java.util.Set<BlockPos> keep = all ? java.util.Set.of() : new java.util.HashSet<>(((AntimatterColliderBlock) Wmd.ANTIMATTER_COLLIDER.get()).footprint());
        int n = 0;
        boolean was = MachineBlock.removing;
        MachineBlock.removing = true;
        try {
            for (int x = X0; x <= X1; x++)
                for (int y = 0; y <= Y1; y++)
                    for (int z = -1; z <= Z1; z++) {
                        if ((x | y | z) == 0 || keep.contains(new BlockPos(x, y, z))) continue;
                        BlockPos p = Machines.toWorld(ctrl, facing, mount, x, y, z);
                        if (!level.hasChunkAt(p) || !level.getBlockState(p).is(Wmd.MACHINE_PART.get())) continue;
                        if (level.getBlockEntity(p) instanceof MachinePartBlockEntity pe && ctrl.equals(pe.getController())) { level.removeBlock(p, false); n++; }
                    }
        } finally {
            MachineBlock.removing = was;
        }
        if (n > 0) Wmd.LOGGER.info("[antimatter] collider at {}: removed {} stray part block(s){}", ctrl.toShortString(), n, all ? " (torn down)" : " (old footprint)");
        return n;
    }

}
