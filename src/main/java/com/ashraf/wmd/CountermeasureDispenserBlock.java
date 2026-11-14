package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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

public class CountermeasureDispenserBlock extends UnitBlock {

    public static final MapCodec<CountermeasureDispenserBlock> CODEC = simpleCodec(CountermeasureDispenserBlock::new);

    public CountermeasureDispenserBlock(Properties properties) { super(properties, 0, 0, 0, 0, 0, 0); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public Direction[] placement(BlockPlaceContext ctx) {
        Direction mount = ctx.getClickedFace().getOpposite();
        return new Direction[] { mount, AirEngineBlock.thrustFacing(ctx, mount) };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CountermeasureDispenserBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, AirBlocks.DISPENSER_BE.get(), ProcessUnitBlockEntity::clientTick)
                : createTickerHelper(type, AirBlocks.DISPENSER_BE.get(), ProcessUnitBlockEntity::serverTick);
    }

    private static boolean is(ItemStack s, String id) { return s.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id))); }

    @Override
    public void interact(Level level, BlockPos controller, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controller) instanceof CountermeasureDispenserBlockEntity be) || !(level instanceof ServerLevel sl)) return;
        if (!gate(level, controller, player, be)) return;
        ItemStack held = player.getMainHandItem();
        int type = is(held, "flare_pack") ? AirMissiles.FLARE : is(held, "chaff_pod") ? AirMissiles.CHAFF : 0;
        if (type != 0) {
            Component why = be.reload(sl, type);
            if (why == null) { if (!player.getAbilities().instabuild) held.shrink(1); player.displayClientMessage(be.status(), true); }
            else player.displayClientMessage(why, true);
            return;
        }
        if (CopycatState.interact(level, player, hit.getBlockPos(), be)) return;
        player.displayClientMessage(be.status(), true);
    }

    @Override
    public void interact(Level level, BlockPos controller, Player player) {
        if (level.getBlockEntity(controller) instanceof CountermeasureDispenserBlockEntity be) player.displayClientMessage(be.status(), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.wmd.countermeasure_dispenser", Balance.AAM_DISPENSER_FLARES, Balance.AAM_DISPENSER_CHAFF).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.countermeasure_dispenser.use", Balance.DISP_PER_ITEM).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.copycat_skin").withStyle(ChatFormatting.GRAY));
        if (!SableBridge.loaded()) tip.add(Component.translatable("tooltip.wmd.needs_sable").withStyle(ChatFormatting.RED));
    }
}
