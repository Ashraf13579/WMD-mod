package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class SonobuoyItem extends Item {

    public SonobuoyItem(Properties properties) { super(properties); }

    static int freq(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        int f = d == null ? 1 : d.copyTag().getInt("sonFreq");
        return Math.max(1, f);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos p = ctx.getClickedPos();
        BlockEntity be = level.getBlockEntity(p);
        if (be instanceof MachinePartBlockEntity part) { BlockPos c = part.validController(level); be = c == null ? null : level.getBlockEntity(c); }
        int f;
        if (be instanceof SonarConsoleBlockEntity c) f = c.frequency();
        else if (be instanceof SonarSensorBlockEntity s) f = s.frequency();
        else return InteractionResult.PASS;
        Player pl = ctx.getPlayer();
        if (!level.isClientSide && pl != null && be instanceof Owned o && !Access.mayUse(o, pl)) return InteractionResult.FAIL;
        if (!level.isClientSide) {
            ItemStack s = ctx.getItemInHand();
            CompoundTag t = new CompoundTag();
            t.putInt("sonFreq", f);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            if (pl != null) pl.displayClientMessage(Component.translatable("message.wmd.sonobuoy.linked", f), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(s);
        Vec3 look = player.getLookAngle();
        SonobuoyEntity.toss(sl, player.getEyePosition().add(look.scale(0.8)), look.scale(0.7).add(0.0, 0.2, 0.0), player.getUUID(), freq(s));
        player.displayClientMessage(Component.translatable("message.wmd.sonobuoy.out", freq(s), Balance.SONOBUOY_TICKS / 1200), true);
        if (!player.getAbilities().instabuild) s.shrink(1);
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.wmd.sonobuoy.desc", Balance.SONOBUOY_TICKS / 1200, (int) Balance.SONOBUOY_RANGE).withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(Component.translatable("item.wmd.sonobuoy.freq", freq(stack)).withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
    }
}
