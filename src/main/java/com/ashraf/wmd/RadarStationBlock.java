package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class RadarStationBlock extends BaseEntityBlock {

    public static final MapCodec<RadarStationBlock> CODEC = simpleCodec(RadarStationBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public RadarStationBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(MachineBlock.MOUNT, net.minecraft.core.Direction.UP)
                .setValue(MachineBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, MachineBlock.MOUNT, MachineBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {

        net.minecraft.core.Direction[] mf = ShipPlace.place(context, false);
        if (mf == null) return defaultBlockState().setValue(MachineBlock.MOUNT, ShipPlace.upFor(context));
        return defaultBlockState().setValue(MachineBlock.MOUNT, mf[0]).setValue(MachineBlock.FACING, mf[1]);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.RADAR_BE.get(), RadarBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.RADAR_BE.get(), RadarBlockEntity::serverTick);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        net.minecraft.core.Direction mount = Machines.mount(state);
        BlockPos ctrl = placer != null ? pos.relative(ShipPlace.away(level, pos, mount, placer), RadarStructure.HALF) : pos;
        boolean moved = !ctrl.equals(pos);
        if (moved) level.removeBlock(pos, false);
        BlockState atCtrl = level.getBlockState(ctrl);
        boolean ctrlFree = !moved || (!level.isOutsideBuildHeight(ctrl) && (atCtrl.isAir() || atCtrl.canBeReplaced()));
        net.minecraft.world.entity.Entity in = RadarStructure.entityInside(level, ctrl, mount);

        boolean claimOk = !(placer instanceof Player cp) || ClaimGuard.mayPlaceBox(level, cp, RadarStructure.at(ctrl, mount, -RadarStructure.HALF, 0, -RadarStructure.HALF), RadarStructure.at(ctrl, mount, RadarStructure.HALF, 0, RadarStructure.HALF), pos);
        RadarStructure.Check check = ctrlFree ? RadarStructure.check(level, ctrl, mount) : new RadarStructure.Check(1, ctrl);
        if (in == null && claimOk && check.complete()) {
            if (moved) level.setBlock(ctrl, state, Block.UPDATE_ALL);
            RadarStructure.form(level, ctrl);
            if (placer != null && level.getBlockEntity(ctrl) instanceof RadarBlockEntity rbe0 && mount == net.minecraft.core.Direction.UP
                    && !SableBridge.isOnShip(level, ctrl))
                rbe0.startBearing((placer.getDirection().toYRot() + 180.0F) % 360.0F);
            if (placer instanceof Player player) {
                if (level.getBlockEntity(ctrl) instanceof RadarBlockEntity rbe && rbe.getOwner() == null) rbe.claim(player);
                player.displayClientMessage(Component.translatable("block.wmd.radar_station.formed"), true);
            }
            level.playSound(null, ctrl, WmdSounds.MB_FORMED.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        } else {
            BlockPos p = check.first() != null ? check.first() : ctrl;
            if (placer instanceof Player player) {
                if (claimOk)
                player.displayClientMessage(in != null ? PlaceGuard.message(in) : Component.translatable(moved ? "block.wmd.radar_station.incomplete_front" : "block.wmd.radar_station.incomplete",
                        check.wrong(), p.getX(), p.getY(), p.getZ()), true);
                if (!player.getAbilities().instabuild) {
                    ItemStack back = new ItemStack(this);
                    if (!player.getInventory().add(back)) player.drop(back, false);
                }
            }
            level.removeBlock(pos, false);
            level.playSound(null, pos, WmdSounds.MB_FAIL.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Wmd.NETWORK_CARD.get())) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!stack.is(Wmd.WRENCH.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (!level.isClientSide) {
            Owned o = Access.ownerAt(level, pos);
            if (o != null && !Access.mayMine(o, player)) { Access.deny(o, player); return ItemInteractionResult.SUCCESS; }
            if (state.getValue(FORMED)) {
                RadarStructure.unform(level, pos);
                player.displayClientMessage(Component.translatable("block.wmd.radar_station.unformed"), true);
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.2F);
            } else {
                net.minecraft.core.Direction mount = Machines.mount(state);
                RadarStructure.Check check = RadarStructure.check(level, pos, mount);
                if (check.complete()) {
                    if (!ClaimGuard.mayPlaceBox(level, player, RadarStructure.at(pos, mount, -RadarStructure.HALF, 0, -RadarStructure.HALF), RadarStructure.at(pos, mount, RadarStructure.HALF, 0, RadarStructure.HALF), null))
                        return ItemInteractionResult.FAIL;
                    RadarStructure.form(level, pos);
                    if (level.getBlockEntity(pos) instanceof RadarBlockEntity rbe && rbe.getOwner() == null) rbe.claim(player);
                    player.displayClientMessage(Component.translatable("block.wmd.radar_station.formed"), true);
                    level.playSound(null, pos, WmdSounds.MB_FORMED.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                } else {
                    BlockPos p = check.first();
                    player.displayClientMessage(Component.translatable("block.wmd.radar_station.incomplete",
                            check.wrong(), p.getX(), p.getY(), p.getZ()), true);
                    level.playSound(null, pos, WmdSounds.MB_FAIL.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) RadarStructure.openScreen(player, level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this) && state.getValue(FORMED)) {
            RadarStructure.removeParts(level, pos, Machines.mount(state));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
