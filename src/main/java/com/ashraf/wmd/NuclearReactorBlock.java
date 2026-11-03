package com.ashraf.wmd;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class NuclearReactorBlock extends BaseEntityBlock {

    public static final MapCodec<NuclearReactorBlock> CODEC = simpleCodec(NuclearReactorBlock::new);
    public static final DirectionProperty FACING = MachineBlock.FACING;
    public static final BooleanProperty FORMED = MultiblockPartBlock.FORMED;

    public NuclearReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(BlockStateProperties.LIT, false).setValue(MachineBlock.MOUNT, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, FORMED, BlockStateProperties.LIT, MachineBlock.MOUNT); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] mf = ShipPlace.place(context, false, true);
        return mf == null ? null : defaultBlockState().setValue(FACING, mf[1]).setValue(MachineBlock.MOUNT, mf[0]);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new NuclearReactorBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {

        return level.isClientSide
                ? createTickerHelper(type, Wmd.NUCLEAR_REACTOR_BE.get(), NuclearReactorBlockEntity::clientTickReactor)
                : createTickerHelper(type, Wmd.NUCLEAR_REACTOR_BE.get(), NuclearReactorBlockEntity::serverTickReactor);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof NuclearReactorBlockEntity be) be.claim(p);
        if (!level.isClientSide) ReactorStructure.tryFormNear(level, pos);
    }

    public void form(Level level, BlockPos ctrl, Player player) {
        BlockState s = level.getBlockState(ctrl);
        Direction f = s.getValue(FACING), m = Machines.mount(s);
        Player actor = player;
        if (actor == null && level instanceof net.minecraft.server.level.ServerLevel sl
                && level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be && be.getOwner() != null)
            actor = sl.getServer().getPlayerList().getPlayer(be.getOwner());
        if (actor != null && !ClaimGuard.mayPlaceBox(level, actor, ReactorStructure.world(ctrl, f, m, ReactorStructure.X0, ReactorStructure.Y0, ReactorStructure.Z0),
                ReactorStructure.world(ctrl, f, m, ReactorStructure.X1, ReactorStructure.Y0, ReactorStructure.Z1), null)) return;
        ReactorStructure.form(level, ctrl, f, m);
        level.setBlock(ctrl, s.setValue(FORMED, true), 3);
        if (level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be) be.formedChanged(true);
        if (player != null) player.displayClientMessage(Component.translatable("message.wmd.reactor_form.ok").withStyle(ChatFormatting.GREEN), true);
    }

    public void unform(Level level, BlockPos ctrl) {
        BlockState s = level.getBlockState(ctrl);
        if (!(s.getBlock() instanceof NuclearReactorBlock) || !s.getValue(FORMED)) return;
        ReactorStructure.unform(level, ctrl, s.getValue(FACING), Machines.mount(s));
        level.setBlock(ctrl, s.setValue(FORMED, false).setValue(BlockStateProperties.LIT, false), 3);
        if (level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be) be.formedChanged(false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (state.getValue(FORMED)) { interactFormed(level, pos, player); return InteractionResult.CONSUME; }
        ReactorStructure.Check c = ReactorStructure.check(level, pos, state.getValue(FACING), Machines.mount(state));
        if (c.complete()) form(level, pos, player);
        else player.displayClientMessage(Component.translatable("message.wmd.reactor_form.missing", c.wrong(), c.first().getX(), c.first().getY(), c.first().getZ(), c.expected())
                .withStyle(ChatFormatting.YELLOW), false);
        return InteractionResult.CONSUME;
    }

    public void interactFormed(Level level, BlockPos ctrl, Player player) {
        if (!(level.getBlockEntity(ctrl) instanceof NuclearReactorBlockEntity be)) return;
        if (!Access.mayUse(be, player)) { Access.deny(be, player); return; }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof ReactorRepairKitItem && be.isDamaged()) {
            if (be.repair(player) && !player.getAbilities().instabuild) held.shrink(1);
            return;
        }
        if (!player.isShiftKeyDown() && be.tryInsert(player)) return;
        if (player.isShiftKeyDown()) { player.displayClientMessage(be.status(), true); return; }
        MachineMenu.open(player, be);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (ShipRelink.shipMove(level, movedByPiston)) { super.onRemove(state, level, pos, newState, movedByPiston); return; }
        if (!level.isClientSide && !newState.is(this)) {
            if (state.getValue(FORMED) && !ReactorStructure.busy()) ReactorStructure.unform(level, pos, state.getValue(FACING), Machines.mount(state));
            if (level.getBlockEntity(pos) instanceof NuclearReactorBlockEntity be)
                for (ItemStack s : be.takeAll()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
