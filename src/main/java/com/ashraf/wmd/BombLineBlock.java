package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BombLineBlock extends MachineBlock {

    public final BombLineKind kind;
    private static final List<BlockPos> CELLS = new ArrayList<>(), CRADLE_CELLS = new ArrayList<>();

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 3; z++) CELLS.add(new BlockPos(x, y, z));
        for (int x = -3; x <= 4; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 3; z++) CRADLE_CELLS.add(new BlockPos(x, y, z));
    }

    protected BombLineBlock(Properties properties, BombLineKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected List<BlockPos> footprint() { return kind == BombLineKind.CRADLE ? CRADLE_CELLS : CELLS; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        if (g != null) return g;
        VoxelShape c = LabModuleBlock.corridor(local);
        return c != null ? c : Shapes.block();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof BombLineBlockEntity m)) return;
        BombLineBlockEntity core = BombLine.coreOf(m);
        if (core == null) { player.displayClientMessage(Component.translatable("message.wmd.bomb_line.no_core"), true); return; }
        if (core != m && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
        BombLineMenu.open(player, core);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BombLineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.BOMB_LINE_BE.get(), (l, p, s, be) -> LineAudio.bomb(l, be))
                : createTickerHelper(type, Wmd.BOMB_LINE_BE.get(), BombLineBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof BombLineBlockEntity m) m.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static class Core extends BombLineBlock {
        public static final MapCodec<Core> CODEC = simpleCodec(Core::new);
        public Core(Properties p) { super(p, BombLineKind.CORE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Press extends BombLineBlock {
        public static final MapCodec<Press> CODEC = simpleCodec(Press::new);
        public Press(Properties p) { super(p, BombLineKind.PRESS); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Filling extends BombLineBlock {
        public static final MapCodec<Filling> CODEC = simpleCodec(Filling::new);
        public Filling(Properties p) { super(p, BombLineKind.FILLING); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Fuze extends BombLineBlock {
        public static final MapCodec<Fuze> CODEC = simpleCodec(Fuze::new);
        public Fuze(Properties p) { super(p, BombLineKind.FUZE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Storage extends BombLineBlock {
        public static final MapCodec<Storage> CODEC = simpleCodec(Storage::new);
        public Storage(Properties p) { super(p, BombLineKind.STORAGE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Cradle extends BombLineBlock {
        public static final MapCodec<Cradle> CODEC = simpleCodec(Cradle::new);
        public Cradle(Properties p) { super(p, BombLineKind.CRADLE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
