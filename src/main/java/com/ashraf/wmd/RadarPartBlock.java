package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RadarPartBlock extends Block {

    public RadarPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MachineBlock.MOUNT, net.minecraft.core.Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachineBlock.MOUNT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && !RadarStructure.isUnforming()) {
            BlockPos c = RadarStructure.findController(level, pos, Machines.mount(state));
            if (c != null) RadarStructure.unform(level, c);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!level.isClientSide && !RadarStructure.isUnforming()) {
            BlockPos c = RadarStructure.findController(level, pos, Machines.mount(state));
            if (c != null) {
                net.minecraft.world.item.Item item = level.getBlockState(c).getBlock().asItem();
                level.destroyBlock(c, false);
                net.minecraft.world.phys.Vec3 w = SableBridge.worldPos(level, c);
                net.minecraft.world.entity.item.ItemEntity it = new net.minecraft.world.entity.item.ItemEntity(level, w.x, w.y + 0.5, w.z, new ItemStack(item));
                it.setDefaultPickUpDelay();
                level.addFreshEntity(it);
            }
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {

        if (stack.is(Wmd.NETWORK_CARD.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockPos c = RadarStructure.findController(level, pos);
            if (c != null) RadarStructure.openScreen(player, level, c);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
