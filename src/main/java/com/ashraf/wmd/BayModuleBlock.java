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
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BayModuleBlock extends MachineBlock {

    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 8, 16);
    public final BayKind kind;
    private final List<BlockPos> footprint = new ArrayList<>();

    protected BayModuleBlock(Properties properties, BayKind kind) {
        super(properties);
        this.kind = kind;
        for (int x = -4; x <= 4; x++) for (int z = 0; z < kind.length; z++) footprint.add(new BlockPos(x, 0, z));
    }

    @Override
    protected List<BlockPos> footprint() { return footprint; }

    @Override
    public VoxelShape machineShape() { return FLOOR; }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof BayModuleBlockEntity m)) return;
        BayModuleBlockEntity core = BayLine.coreOf(m);
        if (core == null) { player.displayClientMessage(Component.translatable("message.wmd.bay.no_core"), true); return; }
        if (core != m && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
        var held = player.getMainHandItem();
        if (held.is(Wmd.BAY_SIZE_KIT.get())) {
            if (core.upgradeSize()) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, controller, net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.3F);
                player.displayClientMessage(Component.translatable("message.wmd.bay.size_upgraded", core.sizeLevel()), true);
            } else player.displayClientMessage(Component.translatable("message.wmd.bay.size_max"), true);
            return;
        }
        BayMenu.open(player, core);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BayModuleBlockEntity(switch (kind) {
            case CORE -> Wmd.BAY_CORE_BE.get();
            case WELD -> Wmd.BAY_WELD_BE.get();
            case CRANE -> Wmd.BAY_CRANE_BE.get();
            case WARHEAD -> Wmd.BAY_WARHEAD_BE.get();
            case CLEAN -> Wmd.BAY_CLEAN_BE.get();
            case PAINT -> Wmd.BAY_PAINT_BE.get();
            case CRYO -> Wmd.BAY_CRYO_BE.get();
            case EXTEND -> Wmd.BAY_EXTEND_BE.get();
            case RACK -> Wmd.BAY_RACK_BE.get();
            case RACK_R -> Wmd.BAY_RACK_R_BE.get();
        }, pos, state, kind);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<BayModuleBlockEntity> mine = switch (kind) {
            case CORE -> Wmd.BAY_CORE_BE.get();
            case WELD -> Wmd.BAY_WELD_BE.get();
            case CRANE -> Wmd.BAY_CRANE_BE.get();
            case WARHEAD -> Wmd.BAY_WARHEAD_BE.get();
            case CLEAN -> Wmd.BAY_CLEAN_BE.get();
            case PAINT -> Wmd.BAY_PAINT_BE.get();
            case CRYO -> Wmd.BAY_CRYO_BE.get();
            case EXTEND -> Wmd.BAY_EXTEND_BE.get();
            case RACK -> Wmd.BAY_RACK_BE.get();
            case RACK_R -> Wmd.BAY_RACK_R_BE.get();
        };
        return level.isClientSide ? createTickerHelper(type, mine, BayModuleBlockEntity::clientTick)
                : createTickerHelper(type, mine, BayModuleBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof BayModuleBlockEntity m) m.dropContents(level, pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static class Core extends BayModuleBlock {
        public static final MapCodec<Core> CODEC = simpleCodec(Core::new);
        public Core(Properties p) { super(p, BayKind.CORE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Weld extends BayModuleBlock {
        public static final MapCodec<Weld> CODEC = simpleCodec(Weld::new);
        public Weld(Properties p) { super(p, BayKind.WELD); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Crane extends BayModuleBlock {
        public static final MapCodec<Crane> CODEC = simpleCodec(Crane::new);
        public Crane(Properties p) { super(p, BayKind.CRANE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Warhead extends BayModuleBlock {
        public static final MapCodec<Warhead> CODEC = simpleCodec(Warhead::new);
        public Warhead(Properties p) { super(p, BayKind.WARHEAD); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Clean extends BayModuleBlock {
        public static final MapCodec<Clean> CODEC = simpleCodec(Clean::new);
        public Clean(Properties p) { super(p, BayKind.CLEAN); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Paint extends BayModuleBlock {
        public static final MapCodec<Paint> CODEC = simpleCodec(Paint::new);
        public Paint(Properties p) { super(p, BayKind.PAINT); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Cryo extends BayModuleBlock {
        public static final MapCodec<Cryo> CODEC = simpleCodec(Cryo::new);
        public Cryo(Properties p) { super(p, BayKind.CRYO); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Rack extends BayModuleBlock {
        public static final MapCodec<Rack> CODEC = simpleCodec(Rack::new);
        private static final List<BlockPos> CELLS = new ArrayList<>();
        static { for (int x = 0; x <= 7; x++) for (int z = 0; z < BayKind.RACK.length; z++) CELLS.add(new BlockPos(x, 0, z)); }
        public Rack(Properties p) { super(p, BayKind.RACK); }
        protected Rack(Properties p, BayKind kind) { super(p, kind); }
        @Override protected List<BlockPos> footprint() { return CELLS; }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override
        public void interact(Level level, BlockPos controller, Player player) {
            if (!(level.getBlockEntity(controller) instanceof BayModuleBlockEntity m)) return;
            BayModuleBlockEntity core = m.core();
            if (core != null && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
            RackMenu.open(player, m);
        }
    }

    public static class RackR extends Rack {
        public static final MapCodec<RackR> CODEC = simpleCodec(RackR::new);
        private static final List<BlockPos> CELLS_R = new ArrayList<>();
        static { for (int x = -7; x <= 0; x++) for (int z = 0; z < BayKind.RACK_R.length; z++) CELLS_R.add(new BlockPos(x, 0, z)); }
        public RackR(Properties p) { super(p, BayKind.RACK_R); }
        @Override protected List<BlockPos> footprint() { return CELLS_R; }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Extend extends BayModuleBlock {
        public static final MapCodec<Extend> CODEC = simpleCodec(Extend::new);
        public Extend(Properties p) { super(p, BayKind.EXTEND); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
