package com.ashraf.wmd;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ExoSuitItem extends ArmorItem {

    public ExoSuitItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties p) { super(material, type, p.durability(type.getDurability(Balance.EXO_DUR)).fireResistant()); }

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    public static boolean fullSet(LivingEntity e) {
        for (EquipmentSlot s : SLOTS) if (!(e.getItemBySlot(s).getItem() instanceof ExoSuitItem)) return false;
        return true;
    }

    public static boolean anyPiece(LivingEntity e) {
        for (EquipmentSlot s : SLOTS) if (e.getItemBySlot(s).getItem() instanceof ExoSuitItem) return true;
        return false;
    }

    public static boolean wears(LivingEntity e, EquipmentSlot s) { return e.getItemBySlot(s).getItem() instanceof ExoSuitItem; }

    public static ItemStack chest(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.CHEST);
        return s.getItem() instanceof ExoSuitItem ? s : ItemStack.EMPTY;
    }

    public static int fe(ItemStack s) { return s.isEmpty() ? 0 : s.getOrDefault(ExoSuitBlocks.EXO_FE.get(), 0); }

    public static void setFe(ItemStack s, int fe) { if (!s.isEmpty()) s.set(ExoSuitBlocks.EXO_FE.get(), Mth.clamp(fe, 0, Balance.EXO_FE_CAP)); }

    public static float charge(LivingEntity e) { return fe(chest(e)) / (float) Balance.EXO_FE_CAP; }

    public static boolean powered(LivingEntity e) { return fullSet(e) && fe(chest(e)) > 0; }

    @Override
    public boolean canElytraFly(ItemStack s, LivingEntity e) { return getType() == ArmorItem.Type.CHESTPLATE && ExoMods.wingsFly(e); }

    @Override
    public boolean elytraFlightTick(ItemStack s, LivingEntity e, int ticks) {
        if (getType() != ArmorItem.Type.CHESTPLATE || !ExoMods.wingsFly(e)) return false;
        if (!e.level().isClientSide) ExoMods.flightTick(e);
        return true;
    }

    @Override public boolean isBarVisible(ItemStack s) { return getType() == ArmorItem.Type.CHESTPLATE || super.isBarVisible(s); }

    @Override
    public int getBarWidth(ItemStack s) {
        return getType() == ArmorItem.Type.CHESTPLATE ? Math.round(13.0F * fe(s) / Balance.EXO_FE_CAP) : super.getBarWidth(s);
    }

    @Override
    public int getBarColor(ItemStack s) {
        if (getType() != ArmorItem.Type.CHESTPLATE) return super.getBarColor(s);
        float f = fe(s) / (float) Balance.EXO_FE_CAP;
        return f < 0.05F ? 0xFF3030 : f < Balance.EXO_LOW ? 0xFF9A3C : 0x4AD7FF;
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (getType() == ArmorItem.Type.CHESTPLATE)
            tip.add(Component.translatable("tooltip.wmd.exo_suit.charge", String.format("%,d", fe(s)), String.format("%,d", Balance.EXO_FE_CAP)).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.wmd.exo_suit.set").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.wmd.exo_suit.jets").withStyle(ChatFormatting.DARK_GRAY));
    }
}
