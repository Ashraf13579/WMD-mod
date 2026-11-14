package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

public class JetCockpitBlock extends UnitBlock implements WrenchUse {

    public static final MapCodec<JetCockpitBlock> CODEC = simpleCodec(p -> new JetCockpitBlock(p, false));

    final boolean tandem;

    final boolean deck;

    public JetCockpitBlock(Properties properties, boolean tandem) { this(properties, tandem, false); }

    public JetCockpitBlock(Properties properties, boolean tandem, boolean deck) {
        super(properties, deck ? -1 : 0, deck ? 1 : 0, 0, 1, 0, deck ? 2 : tandem ? 4 : 2);
        this.tandem = tandem && !deck;
        this.deck = deck;
    }

    private static final net.minecraft.world.phys.shapes.VoxelShape AISLE_FLOOR = net.minecraft.world.level.block.Block.box(0, 0, 0, 16, 2, 16),
            AISLE_ROOF = net.minecraft.world.level.block.Block.box(0, 15, 0, 16, 16, 16);

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape machineCollision(BlockGetter level, BlockPos controller, BlockPos local, Direction facing) {
        if (deck && local.getX() == 0 && local.getZ() == 0) return local.getY() == 0 ? AISLE_FLOOR : AISLE_ROOF;
        return super.machineCollision(level, controller, local, facing);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override public boolean hullPlug(BlockPos local) { return !deck && local.getY() == 0; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new JetCockpitBlockEntity(deck ? AirBlocks.FLIGHT_DECK_BE.get() : tandem ? AirBlocks.JET_COCKPIT_TANDEM_BE.get() : AirBlocks.JET_COCKPIT_BE.get(), pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<JetCockpitBlockEntity> own = deck ? AirBlocks.FLIGHT_DECK_BE.get() : tandem ? AirBlocks.JET_COCKPIT_TANDEM_BE.get() : AirBlocks.JET_COCKPIT_BE.get();
        return level.isClientSide
                ? createTickerHelper(type, own, JetCockpitBlockEntity::clientTickCockpit)
                : createTickerHelper(type, own, ProcessUnitBlockEntity::serverTick);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof JetCockpitBlockEntity be)) return;
        if (!AirGate.on()) { player.displayClientMessage(Component.translatable("message.wmd.cockpit.gated"), true); return; }
        BlockPos local = Machines.toLocal(controller, level.getBlockState(controller).getValue(FACING), hit.getBlockPos());
        if (CopycatHull.interact(level, hit.getBlockPos(), player, be, deck || local.getY() == 0)) return;
        if (player.getMainHandItem().is(Wmd.WRENCH.get())) {
            int t = be.cycleTint();
            player.displayClientMessage(Component.translatable("message.wmd.cockpit.tint", Component.translatable("gui.wmd.cockpit.tint" + t)), true);
            level.playSound(null, hit.getBlockPos(), WmdSounds.COCKPIT_SWITCH.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_CLICK, 1.2F);
            return;
        }
        super.interact(level, controller, player, hit);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (!(level.getBlockEntity(controller) instanceof JetCockpitBlockEntity be) || !AirGate.on()) return;
        if (player.isShiftKeyDown()) { player.displayClientMessage(be.status(), true); return; }
        if (!(player instanceof ServerPlayer sp)) return;
        if (be.ejected()) { be.refit(sp); return; }
        be.sit(sp);
    }

    @Override protected boolean isSignalSource(BlockState state) { return true; }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!(level.getBlockEntity(pos) instanceof JetCockpitBlockEntity be)) return 0;

        Direction face = direction.getOpposite();
        Direction facing = state.getValue(FACING);
        Direction mount = Machines.mount(state);
        if (be.deck()) return face == Machines.dirToWorld(facing, mount, Direction.DOWN) ? be.signal(JetCockpitBlockEntity.AX_THROTTLE) : 0;
        if (face == Machines.dirToWorld(facing, mount, Direction.WEST)) return be.signal(JetCockpitBlockEntity.AX_PITCH);
        if (face == Machines.dirToWorld(facing, mount, Direction.EAST)) return be.signal(JetCockpitBlockEntity.AX_ROLL);
        if (face == Machines.dirToWorld(facing, mount, Direction.DOWN)) return be.signal(JetCockpitBlockEntity.AX_YAW);
        if (face == Machines.dirToWorld(facing, mount, Direction.NORTH)) return be.signal(JetCockpitBlockEntity.AX_THROTTLE);
        return 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(deck ? "tooltip.wmd.flight_deck" : tandem ? "tooltip.wmd.jet_cockpit_tandem" : "tooltip.wmd.jet_cockpit").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.jet_cockpit.keys").withStyle(ChatFormatting.DARK_GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
