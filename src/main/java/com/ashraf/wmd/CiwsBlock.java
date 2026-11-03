package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CiwsBlock extends MachineBlock {

    public static final MapCodec<CiwsBlock> CODEC = simpleCodec(CiwsBlock::new);
    private static final List<BlockPos> FOOTPRINT = new ArrayList<>();

    static {
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 4; y++) for (int z = 0; z <= 2; z++) FOOTPRINT.add(new BlockPos(x, y, z));
    }

    public CiwsBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean anyFace() { return true; }

    @Override
    protected List<BlockPos> footprint() { return FOOTPRINT; }

    @Override
    public VoxelShape machineShape() { return Shapes.block(); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CiwsBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, Wmd.CIWS_BE.get(), CiwsBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.CIWS_BE.get(), CiwsBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof CiwsBlockEntity be) be.claim(p);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof CiwsBlockEntity be)) return;
        if (be.getOwner() == null) be.claim(player);
        if (Access.mayUse(be, player) && be.fitDrum(player)) return;
        LauncherMenu.open(player, be, "block.wmd.ciws");
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof CiwsBlockEntity be) be.dropContents();
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
