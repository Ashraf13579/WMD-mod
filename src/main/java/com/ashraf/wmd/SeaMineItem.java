package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class SeaMineItem extends BlockItem {

    static final String DEPTH_TAG = "smDepth";

    public SeaMineItem(Block block, Properties properties) { super(block, properties); }

    int kind() { return getBlock() instanceof SeaMineBlock m ? m.kind : SeaMineBlockEntity.K_INFLUENCE; }

    static int depth(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return -1;
        CompoundTag t = d.copyTag();
        return t.contains(DEPTH_TAG) ? t.getInt(DEPTH_TAG) : -1;
    }

    static int layDepth(ItemStack s, int kind) {
        int d = depth(s);
        return d < 0 ? SeaMineBlockEntity.defaultDepth(kind) : d;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos p = new BlockPlaceContext(ctx).getClickedPos();
        if (!level.getFluidState(p).is(FluidTags.WATER) || SableBridge.isOnShip(level, p) || SableBridge.isOnShip(level, ctx.getClickedPos())) return InteractionResult.PASS;
        return super.useOn(ctx);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                if (kind() == SeaMineBlockEntity.K_BOTTOM) player.displayClientMessage(Component.translatable("message.wmd.seamine.lay_nodepth"), true);
                else {
                    int now = layDepth(s, kind()), next = Balance.MINE_LAY_DEPTHS[0];
                    for (int d : Balance.MINE_LAY_DEPTHS) if (d > now) { next = d; break; }
                    final int set = next;
                    CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putInt(DEPTH_TAG, set));
                    player.displayClientMessage(Component.translatable("message.wmd.seamine.lay_depth", set), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(s);
        if (!LaidMineEntity.layFrom(sp, hand, kind())) return InteractionResultHolder.fail(s);
        player.getCooldowns().addCooldown(this, Balance.MINE_LAY_COOLDOWN);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        int k = kind();
        if (k != SeaMineBlockEntity.K_BOTTOM) lines.add(Component.translatable("item.wmd.sea_mine.depth", layDepth(stack, k)).withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("item.wmd.sea_mine.lay").withStyle(ChatFormatting.GRAY));
        if (k != SeaMineBlockEntity.K_BOTTOM) lines.add(Component.translatable("item.wmd.sea_mine.lay_depth").withStyle(ChatFormatting.GRAY));
        if (k != SeaMineBlockEntity.K_INFLUENCE)
            lines.add(Component.translatable("item.wmd.sea_mine.lay_load", Component.translatable(k == SeaMineBlockEntity.K_BOTTOM ? "item.wmd.torpedo_t1" : "item.wmd.torpedo_t2")).withStyle(ChatFormatting.GRAY));
    }
}
