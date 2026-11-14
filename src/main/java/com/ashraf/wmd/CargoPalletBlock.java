package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CargoPalletBlock extends UnitBlock {

    public static final MapCodec<CargoPalletBlock> CODEC = simpleCodec(p -> new CargoPalletBlock(p, false));
    private final boolean heavy;

    public CargoPalletBlock(Properties properties, boolean heavy) {
        super(properties, 0, heavy ? 1 : 0, 0, 0, 0, heavy ? 1 : 0);
        this.heavy = heavy;
    }

    public boolean heavy() { return heavy; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CargoPalletBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return null; }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return getShape(state, level, pos, context); }

    static BlockPos controllerAt(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.getBlock() instanceof CargoPalletBlock) return p;
        if (s.is(Wmd.MACHINE_PART.get()) && level.getBlockEntity(p) instanceof MachinePartBlockEntity part && part.getController() != null
                && level.getBlockState(part.getController()).getBlock() instanceof CargoPalletBlock) return part.getController();
        return null;
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) { interact(level, controller, player); }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof CargoPalletBlockEntity be)) return;
        if (player.isShiftKeyDown() && player.getMainHandItem().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.wmd.pallet.contents", Cargo.stacks(be.items()), be.store().getContainerSize()), true);
            return;
        }
        if (player instanceof ServerPlayer sp) be.open(sp, 0);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && !ShipRelink.shipMove(level, movedByPiston) && level.getBlockEntity(pos) instanceof CargoPalletBlockEntity be) {
            if (!be.released()) {
                ItemStack packed = CargoPalletItem.packed(heavy, be.items());
                be.store().clearContent();
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packed);
            } else Containers.dropContents(level, pos, be.store());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {}
}
