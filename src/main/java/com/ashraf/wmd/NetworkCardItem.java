package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class NetworkCardItem extends Item {

    public NetworkCardItem(Properties properties) {
        super(properties);
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    public static boolean isLinked(ItemStack stack) {
        return tag(stack).contains("radarDim");
    }

    public static BlockPos linkedPos(ItemStack stack) {
        CompoundTag t = tag(stack);
        return new BlockPos(t.getInt("radarX"), t.getInt("radarY"), t.getInt("radarZ"));
    }

    public static RadarBlockEntity resolve(ItemStack stack, Level level) {
        if (!stack.is(Wmd.NETWORK_CARD.get()) || !isLinked(stack)) return null;
        if (!tag(stack).getString("radarDim").equals(level.dimension().location().toString())) return null;
        BlockPos p = linkedPos(stack);
        RadarBlockEntity r = radarAt(level, p);
        if (r != null) return r;

        BlockPos q = ShipRelink.forward(level, p);
        if (q.equals(p)) return null;
        r = radarAt(level, q);
        if (r != null) CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> { t.putInt("radarX", q.getX()); t.putInt("radarY", q.getY()); t.putInt("radarZ", q.getZ()); });
        return r;
    }

    private static RadarBlockEntity radarAt(Level level, BlockPos p) {
        if (!level.isLoaded(p)) return null;
        return level.getBlockEntity(p) instanceof RadarBlockEntity r && r.isFormed() ? r : null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (context.getPlayer() != null && EmpField.handheldDead(level, context.getPlayer())) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        boolean station = state.getBlock() instanceof RadarStationBlock;
        if (!station && !(state.getBlock() instanceof RadarPartBlock)) return InteractionResult.PASS;

        if (!level.isClientSide) {
            BlockPos controller = station ? (state.getValue(RadarStationBlock.FORMED) ? pos : null)
                    : RadarStructure.findController(level, pos);
            if (controller == null) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("item.wmd.network_card.not_formed"), true);
                }
            } else if (context.getPlayer() != null && level.getBlockEntity(controller) instanceof Owned o && !Access.mayUse(o, context.getPlayer())) {
                Access.deny(o, context.getPlayer());
            } else {
                ItemStack stack = context.getItemInHand();
                String dim = level.dimension().location().toString();
                CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
                    t.putInt("radarX", controller.getX());
                    t.putInt("radarY", controller.getY());
                    t.putInt("radarZ", controller.getZ());
                    t.putString("radarDim", dim);
                });
                level.playSound(null, pos, WmdSounds.TOOL_CARD.get(), SoundSource.BLOCKS, 0.75F, 1.0F);
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("item.wmd.network_card.linked",
                            controller.getX(), controller.getY(), controller.getZ()), true);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isLinked(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isLinked(stack)) {
            BlockPos p = linkedPos(stack);
            tooltip.add(Component.translatable("item.wmd.network_card.tooltip_linked", p.getX(), p.getY(), p.getZ()));
        } else {
            tooltip.add(Component.translatable("item.wmd.network_card.tooltip_unlinked"));
        }
    }
}
