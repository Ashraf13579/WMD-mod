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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class DroneModuleBlock extends MachineBlock {

    @Override public boolean anyFace() { return true; }

    public final DroneKind kind;
    private static final List<BlockPos> CELLS = new ArrayList<>(), HANGAR_CELLS = new ArrayList<>();

    static {
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 3; z++) CELLS.add(new BlockPos(x, y, z));
        for (int x = -5; x <= 5; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 7; z++) HANGAR_CELLS.add(new BlockPos(x, y, z));
    }

    protected DroneModuleBlock(Properties properties, DroneKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected List<BlockPos> footprint() { return kind == DroneKind.HANGAR ? HANGAR_CELLS : CELLS; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        if (g != null) return g;
        if (kind == DroneKind.HANGAR) return local.getY() == 0 ? Block.box(0, 0, 0, 16, 2, 16) : Shapes.empty();
        VoxelShape c = LabModuleBlock.corridor(local);
        return c != null ? c : Shapes.block();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof DroneModuleBlockEntity m)) return;
        if (m.kind() == DroneKind.HANGAR) { BigDroneMenu.open(player, m); return; }
        DroneModuleBlockEntity core = DroneLine.coreOf(m);
        if (core == null) { player.displayClientMessage(Component.translatable("message.wmd.drone_line.no_core"), true); return; }
        if (core != m && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
        DroneMenu.open(player, core);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DroneModuleBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.DRONE_BE.get(), (l, p, s, be) -> LineAudio.drone(l, be))
                : createTickerHelper(type, Wmd.DRONE_BE.get(), DroneModuleBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof DroneModuleBlockEntity m) m.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static class Core extends DroneModuleBlock {
        public static final MapCodec<Core> CODEC = simpleCodec(Core::new);
        public Core(Properties p) { super(p, DroneKind.CORE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Frame extends DroneModuleBlock {
        public static final MapCodec<Frame> CODEC = simpleCodec(Frame::new);
        public Frame(Properties p) { super(p, DroneKind.FRAME); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Avionics extends DroneModuleBlock {
        public static final MapCodec<Avionics> CODEC = simpleCodec(Avionics::new);
        public Avionics(Properties p) { super(p, DroneKind.AVIONICS); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Propulsion extends DroneModuleBlock {
        public static final MapCodec<Propulsion> CODEC = simpleCodec(Propulsion::new);
        public Propulsion(Properties p) { super(p, DroneKind.PROPULSION); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Test extends DroneModuleBlock {
        public static final MapCodec<Test> CODEC = simpleCodec(Test::new);
        public Test(Properties p) { super(p, DroneKind.TEST); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Storage extends DroneModuleBlock {
        public static final MapCodec<Storage> CODEC = simpleCodec(Storage::new);
        public Storage(Properties p) { super(p, DroneKind.STORAGE); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Hangar extends DroneModuleBlock {
        public static final MapCodec<Hangar> CODEC = simpleCodec(Hangar::new);
        public Hangar(Properties p) { super(p, DroneKind.HANGAR); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
