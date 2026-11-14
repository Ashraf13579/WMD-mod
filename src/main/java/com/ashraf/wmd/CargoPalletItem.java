package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

public class CargoPalletItem extends BlockItem {

    private final boolean heavy;

    public CargoPalletItem(Block block, Properties p, boolean heavy) {
        super(block, p.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        this.heavy = heavy;
    }

    static boolean fits(ItemStack s) { return !(s.getItem() instanceof CargoPalletItem) && s.getItem().canFitInsideContainerItems(); }

    static ItemStack packed(boolean heavy, List<ItemStack> items) {
        ItemStack st = new ItemStack(CargoBlocks.palletItem(heavy));
        st.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return st;
    }

    @Override
    public boolean canFitInsideContainerItems() { return false; }

    @Override
    public void appendHoverText(ItemStack s, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        long n = s.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().count();
        tip.add(Component.translatable("tooltip.wmd.cargo_pallet", n, Balance.PALLET_SLOTS[heavy ? 1 : 0]).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable(heavy ? "tooltip.wmd.cargo_pallet_heavy.how" : "tooltip.wmd.cargo_pallet.how").withStyle(ChatFormatting.GRAY));
    }
}
