package com.ashraf.wmd;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;

public final class HardenedBlock {

    private HardenedBlock() {}

    static void tip(List<Component> tooltip, int rating) {
        tooltip.add(Component.translatable("tooltip.wmd.hardened", rating, (int) Math.round(Math.pow(Balance.HARDENED_FALLOFF, rating) * 100)));
    }

    public static class Concrete extends Block implements Hardened {
        public static final MapCodec<Concrete> CODEC = simpleCodec(Concrete::new);
        public Concrete(Properties p) { super(p); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override public int rating() { return 1; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) { tip(tooltip, rating()); }
    }

    public static class Plate extends Block implements Hardened {
        public static final MapCodec<Plate> CODEC = simpleCodec(Plate::new);
        public Plate(Properties p) { super(p); }
        @Override protected MapCodec<? extends Block> codec() { return CODEC; }
        @Override public int rating() { return 3; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) { tip(tooltip, rating()); }
    }

    public static class Glass extends TransparentBlock implements Hardened {
        public static final MapCodec<Glass> CODEC = simpleCodec(Glass::new);
        public Glass(Properties p) { super(p); }
        @Override public MapCodec<? extends TransparentBlock> codec() { return CODEC; }
        @Override public int rating() { return 1; }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) { tip(tooltip, rating()); }
    }
}
