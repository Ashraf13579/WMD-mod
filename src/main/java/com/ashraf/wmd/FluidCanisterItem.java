package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class FluidCanisterItem extends Item {

    public FluidCanisterItem(Properties properties) { super(properties.stacksTo(16)); }

    public static FluidStack content(ItemStack s) {
        SimpleFluidContent c = s.get(WmdComponents.CANISTER_FLUID.get());
        return c == null ? FluidStack.EMPTY : c.copy();
    }

    static ItemStack with(ItemStack one, FluidStack f) {
        if (f.isEmpty()) one.remove(WmdComponents.CANISTER_FLUID.get());
        else one.set(WmdComponents.CANISTER_FLUID.get(), SimpleFluidContent.copyOf(f));
        return one;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, ctx.getClickedPos(), ctx.getClickedFace());
        if (h == null || player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Owned o = Access.ownerAt(level, ctx.getClickedPos());
        if (o != null && !Access.mayUse(o, player)) { Access.deny(o, player); return InteractionResult.FAIL; }
        ItemStack held = ctx.getItemInHand();
        FluidStack in = content(held);
        FluidStack after;
        if (in.isEmpty()) {
            FluidStack got = h.drain(Balance.CANISTER_MB, IFluidHandler.FluidAction.EXECUTE);
            if (got.isEmpty()) { player.displayClientMessage(Component.translatable("message.wmd.canister.nothing"), true); return InteractionResult.FAIL; }
            after = got;
        } else {
            int n = h.fill(in.copy(), IFluidHandler.FluidAction.EXECUTE);
            if (n <= 0) { player.displayClientMessage(Component.translatable("message.wmd.canister.refused", in.getHoverName()), true); return InteractionResult.FAIL; }
            after = in.copyWithAmount(in.getAmount() - n);
        }
        ItemStack changed = with(held.copyWithCount(1), after);
        if (!player.getAbilities().instabuild) held.shrink(1);
        if (held.isEmpty()) player.setItemInHand(ctx.getHand(), changed);
        else if (!player.getInventory().add(changed)) player.drop(changed, false);
        level.playSound(null, ctx.getClickedPos(), in.isEmpty() ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7F, 1.1F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        FluidStack f = content(stack);
        return f.isEmpty() ? Component.translatable("item.wmd.canister.empty") : Component.translatable("item.wmd.canister.full", f.getHoverName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidStack f = content(stack);
        tooltip.add(Component.translatable("tooltip.wmd.canister.amount", String.format("%,d", f.getAmount()), String.format("%,d", Balance.CANISTER_MB)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wmd.canister.how").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static int bandColour(ItemStack stack) {
        FluidStack f = content(stack);
        if (f.isEmpty()) return 0xFFB8C0C8;
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == f.getFluid()) return e.color() | 0xFF000000;
        return f.getFluid() == net.minecraft.world.level.material.Fluids.LAVA ? 0xFFE8641E : 0xFF3F76E4;
    }
}
