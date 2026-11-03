package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class LabModuleBlock extends MachineBlock {

    public final LabKind kind;
    private static final List<BlockPos> CELLS = new ArrayList<>();

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 3; z++) CELLS.add(new BlockPos(x, y, z));
    }

    protected LabModuleBlock(Properties properties, LabKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected List<BlockPos> footprint() { return CELLS; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) { VoxelShape g = generated(local, facing); if (g != null) return g; return corridor(local) != null ? corridor(local) : Shapes.block(); }

    static VoxelShape corridor(BlockPos local) {
        if (local.getZ() != 3) return null;
        return local.getY() == 0 ? Block.box(0, 0, 0, 16, 6, 16) : Shapes.empty();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof LabModuleBlockEntity m)) return;
        LabModuleBlockEntity core = m.kind() == LabKind.CORE ? m : LabLine.coreOf(m);
        if (core == null) { player.displayClientMessage(Component.translatable("message.wmd.lab.no_core"), true); return; }
        if (core != m && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
        LabMenu.open(player, core);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LabModuleBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.LAB_BE.get(), LabModuleBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.LAB_BE.get(), LabModuleBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof LabModuleBlockEntity m) m.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static class Core extends LabModuleBlock {
        public static final MapCodec<Core> CODEC = simpleCodec(Core::new);
        public Core(Properties p) { super(p, LabKind.CORE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Press extends LabModuleBlock {
        public static final MapCodec<Press> CODEC = simpleCodec(Press::new);
        public Press(Properties p) { super(p, LabKind.PRESS); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Filler extends LabModuleBlock {
        public static final MapCodec<Filler> CODEC = simpleCodec(Filler::new);
        public Filler(Properties p) { super(p, LabKind.FILLER); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Oven extends LabModuleBlock {
        public static final MapCodec<Oven> CODEC = simpleCodec(Oven::new);
        public Oven(Properties p) { super(p, LabKind.OVEN); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    private static final List<BlockPos> TALL = new ArrayList<>();
    static { for (int x = -2; x <= 2; x++) for (int y = 0; y <= 5; y++) for (int z = 0; z <= 3; z++) TALL.add(new BlockPos(x, y, z)); }

    public static class Storage extends LabModuleBlock {
        @Override protected List<BlockPos> footprint() { return TALL; }
        public static final MapCodec<Storage> CODEC = simpleCodec(Storage::new);
        public Storage(Properties p) { super(p, LabKind.STORAGE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
