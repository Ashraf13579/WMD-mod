package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class MineBlock extends MachineBlock {

    private static final List<BlockPos> CELL = List.of(BlockPos.ZERO);
    public final boolean claymore;

    protected MineBlock(Properties properties, boolean claymore) {
        super(properties);
        this.claymore = claymore;
    }

    @Override
    protected List<BlockPos> footprint() { return CELL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MineBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.MINE_BE.get(), MineBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.MINE_BE.get(), MineBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof MineBlockEntity be)) return;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            level.removeBlock(controller, false);
            ItemStack s = new ItemStack(asItem());
            if (!player.addItem(s)) player.drop(s, false);
            return;
        }
        player.displayClientMessage(Component.translatable(be.armed() ? "message.wmd.mine.armed" : "message.wmd.mine.arming"), true);
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Explosion explosion) { return false; }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        super.onBlockExploded(state, level, pos, explosion);
        if (level instanceof ServerLevel sl) {
            boolean c = claymore;
            sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + 2, () -> MineBlockEntity.blast(sl, pos, state, c)));
        }
    }

    public static class PressureMine extends MineBlock {
        public static final MapCodec<PressureMine> CODEC = simpleCodec(PressureMine::new);
        public PressureMine(Properties p) { super(p, false); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return Shapes.empty(); }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (level instanceof ServerLevel && entity instanceof LivingEntity le && level.getBlockEntity(pos) instanceof MineBlockEntity be) be.steppedOn(le);
        }
    }

    public static class Claymore extends MineBlock {
        public static final MapCodec<Claymore> CODEC = simpleCodec(Claymore::new);
        public Claymore(Properties p) { super(p, true); }
        @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

        @Override public boolean anyFace() { return true; }
    }
}
