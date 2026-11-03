package com.ashraf.wmd;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class LaunchTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<LaunchTerminalBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            propertiesCodec(), Codec.INT.fieldOf("tier").forGetter(LaunchTerminalBlock::getTier)
    ).apply(i, LaunchTerminalBlock::new));
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private final int tier;

    public LaunchTerminalBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    public int getTier() { return tier; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED, MachineBlock.MOUNT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, Wmd.LAUNCH_TERMINAL_BE.get(), LaunchTerminalBlockEntity::clientTick)
                : createTickerHelper(type, Wmd.LAUNCH_TERMINAL_BE.get(), LaunchTerminalBlockEntity::serverTick);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false, true);
        return mf == null ? null : defaultBlockState().setValue(FACING, mf[1]).setValue(MachineBlock.MOUNT, mf[0]);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchTerminalBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof Owned o) o.claim(p);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Wmd.WRENCH.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.getBlockEntity(pos) instanceof Owned o && !Access.mayUse(o, player)) { if (!level.isClientSide) Access.deny(o, player); return ItemInteractionResult.FAIL; }
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING), mount = Machines.mount(state);
            if (state.getValue(FORMED)) {
                PadStructure.unform(level, pos, facing, mount, tier);
                level.setBlock(pos, state.setValue(FORMED, false), 3);
                player.displayClientMessage(Component.translatable("block.wmd.launch_terminal.unformed"), true);
                level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.4F);
            } else {
                PadStructure.Check check = PadStructure.check(level, pos, facing, mount, tier);
                if (check.complete()) {
                    int n = PadStructure.size(tier);
                    if (!ClaimGuard.mayPlaceBox(level, player, PadStructure.cell(pos, facing, mount, tier, 0, 0), PadStructure.cell(pos, facing, mount, tier, n - 1, n - 1), null))
                        return ItemInteractionResult.FAIL;
                    PadStructure.form(level, pos, facing, mount, tier);
                    level.setBlock(pos, state.setValue(FORMED, true), 3);
                    player.displayClientMessage(Component.translatable("block.wmd.launch_terminal.formed"), true);
                    level.playSound(null, pos, WmdSounds.MB_FORMED.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                } else {
                    int n = PadStructure.size(tier);
                    BlockPos p = check.firstWrong();
                    player.displayClientMessage(Component.translatable("block.wmd.launch_terminal.incomplete",
                            n, n, check.wrong(), p.getX(), p.getY(), p.getZ()), true);
                    level.playSound(null, pos, WmdSounds.MB_FAIL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LaunchTerminalBlockEntity be) {
            if (be.getOwner() == null) be.claim(player);
            else if (!Access.mayUse(be, player)) { Access.deny(be, player); return InteractionResult.FAIL; }
            player.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new TerminalMenu(id, inventory, be),
                            Component.translatable(getDescriptionId())),
                    buf -> {
                        buf.writeBlockPos(pos);
                        buf.writeVarInt(tier);
                        buf.writeVarInt(be.getTargetX());
                        buf.writeVarInt(be.getTargetY());
                        buf.writeVarInt(be.getTargetZ());
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this)) {
            if (state.getValue(FORMED)) PadStructure.unform(level, pos, state.getValue(FACING), Machines.mount(state), tier);
            if (level.getBlockEntity(pos) instanceof LaunchTerminalBlockEntity be) be.dropContents(level);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
