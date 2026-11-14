package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class AirStationBlock extends UnitBlock {

    public static final MapCodec<AirStationBlock> CODEC = simpleCodec(p -> new AirStationBlock(p, false));
    private final boolean rail;

    public AirStationBlock(Properties properties, boolean rail) {
        super(properties, 0, 0, 0, 0, 0, rail ? 1 : 0);
        this.rail = rail;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirStationBlockEntity(rail ? AirBlocks.LAUNCH_RAIL_BE.get() : AirBlocks.PYLON_BE.get(), pos, state, rail);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        BlockEntityType<AirStationBlockEntity> t = rail ? AirBlocks.LAUNCH_RAIL_BE.get() : AirBlocks.PYLON_BE.get();
        return level.isClientSide ? createTickerHelper(type, t, ProcessUnitBlockEntity::clientTick) : createTickerHelper(type, t, ProcessUnitBlockEntity::serverTick);
    }

    static String refusal(AirStationBlockEntity be, int kind) {
        BombType b = AirStores.bomb(kind);
        if (b == null || be.rail()) return "message.wmd.station.wrong";
        if (b == BombType.PENETRATOR) return "message.wmd.station.weapons_bay";
        if (b.heavy && !AirStores.pylonHeavy(b)) return "message.wmd.station.heavy_bay";
        if (b.heavy) return "message.wmd.station.heavy_room";
        return "message.wmd.station.blocked";
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof AirStationBlockEntity be)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        int sk = AirStores.kindOf(held);
        if (sk >= 0) {
            if (be.load(sk, AirStores.freqOf(held))) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                level.playSound(null, hit.getBlockPos(), WmdSounds.STATION_LOAD.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
            } else player.displayClientMessage(Component.translatable(be.accepts(sk) ? "message.wmd.station.full" : refusal(be, sk)), true);
            return;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            ItemStack back = be.unload();
            if (!back.isEmpty()) player.getInventory().placeItemBackInInventory(back);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof AirStationBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(rail ? "tooltip.wmd.launch_rail" : "tooltip.wmd.pylon").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.station.use").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
