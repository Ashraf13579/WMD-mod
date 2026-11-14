package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AmmoDepotBlock extends UnitBlock {

    public static final MapCodec<AmmoDepotBlock> CODEC = simpleCodec(AmmoDepotBlock::new);

    public AmmoDepotBlock(Properties properties) {
        super(properties, AmmoDepotBlockEntity.X0, AmmoDepotBlockEntity.X1, AmmoDepotBlockEntity.Y0, AmmoDepotBlockEntity.Y1, AmmoDepotBlockEntity.Z0, AmmoDepotBlockEntity.Z1);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AmmoDepotBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AirBlocks.AMMO_DEPOT_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, AirBlocks.AMMO_DEPOT_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public VoxelShape machineShape(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("ammo_depot", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public VoxelShape machineShape(BlockPos local, Direction facing) {
        VoxelShape g = MachineShapes.get("ammo_depot", local, facing);
        return g != null ? g : machineShape();
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof AmmoDepotBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        if (Ammo.isAmmo(held)) {
            int n = be.insertHeld(player, held);
            player.displayClientMessage(n > 0 ? Component.translatable("message.wmd.ammo_depot.stored", n, held.getHoverName())
                    : Component.translatable("message.wmd.ammo_depot.full"), true);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.wmd.ammo_depot.order", be.order()), true);
            return;
        }
        interact(level, controller, player);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(controller) instanceof AmmoDepotBlockEntity be) || !(player instanceof ServerPlayer sp)) return;
        Loadouts.open(sp, be);
    }

    static void openStore(ServerPlayer sp, AmmoDepotBlockEntity be) {
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new AmmoDepotMenu(id, inv, be.store(), be.getBlockPos()), Component.translatable("block.wmd.ammo_depot")),
                buf -> buf.writeBlockPos(be.getBlockPos()));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.ammo_depot", Balance.AD_SLOTS, (int) Balance.AD_RANGE).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
    }
}
