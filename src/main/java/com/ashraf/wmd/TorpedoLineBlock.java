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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class TorpedoLineBlock extends MachineBlock {

    public final TorpedoLineKind kind;
    private static final List<BlockPos> CELLS = new ArrayList<>();

    static {
        for (int x = -1; x <= 2; x++) for (int y = 0; y <= 2; y++) for (int z = 0; z <= 5; z++) CELLS.add(new BlockPos(x, y, z));
    }

    protected TorpedoLineBlock(Properties properties, TorpedoLineKind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected List<BlockPos> footprint() { return CELLS; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = generated(local, facing);
        return g != null ? g : Shapes.block();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof TorpedoLineBlockEntity m)) return;
        TorpedoLineBlockEntity core = TorpedoLine.coreOf(m);
        if (core == null) { player.displayClientMessage(Component.translatable("message.wmd.torpedo_line.no_core"), true); return; }
        if (core != m && !Access.mayUse(core, player)) { Access.deny(core, player); return; }
        TorpedoLineMenu.open(player, core);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TorpedoLineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.TORPEDO_LINE_BE.get(), TorpedoLineBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.TORPEDO_LINE_BE.get(), TorpedoLineBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof TorpedoLineBlockEntity m) m.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    public static class Shop extends TorpedoLineBlock {
        public static final MapCodec<Shop> CODEC = simpleCodec(Shop::new);
        public Shop(Properties p) { super(p, TorpedoLineKind.SHOP); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Fitout extends TorpedoLineBlock {
        public static final MapCodec<Fitout> CODEC = simpleCodec(Fitout::new);
        public Fitout(Properties p) { super(p, TorpedoLineKind.FITOUT); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }

    public static class Test extends TorpedoLineBlock {
        public static final MapCodec<Test> CODEC = simpleCodec(Test::new);
        public Test(Properties p) { super(p, TorpedoLineKind.TEST); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    }
}
