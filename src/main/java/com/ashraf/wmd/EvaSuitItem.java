package com.ashraf.wmd;

import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EvaSuitItem extends ArmorItem {

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Wmd.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register("eva_suit", () -> new ArmorMaterial(
            net.minecraft.Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.HELMET, 2); m.put(ArmorItem.Type.CHESTPLATE, 6); m.put(ArmorItem.Type.LEGGINGS, 5); m.put(ArmorItem.Type.BOOTS, 2); m.put(ArmorItem.Type.BODY, 5);
            }),
            9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "titanium_plate"))),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "eva_suit"))), 0.0F, 0.0F));

    public EvaSuitItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties p) { super(material, type, p.durability(type.getDurability(20))); }

    public static boolean fullSuit(LivingEntity e) {
        for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET })
            if (!(e.getItemBySlot(s).getItem() instanceof EvaSuitItem)) return false;
        return true;
    }

    public static boolean suitBoots(LivingEntity e) { return e.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof EvaSuitItem; }

    public static ItemStack torso(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.CHEST);
        return s.getItem() instanceof EvaSuitItem ? s : ItemStack.EMPTY;
    }

    public static int oxygen(ItemStack s) { return s.isEmpty() ? 0 : s.getOrDefault(WmdComponents.OXYGEN.get(), 0); }

    public static void setOxygen(ItemStack s, int ticks) { if (!s.isEmpty()) s.set(WmdComponents.OXYGEN.get(), Mth.clamp(ticks, 0, Balance.O2_FULL)); }

    static String clock(int ticks) {
        int sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    @Override public boolean isBarVisible(ItemStack s) { return getType() == ArmorItem.Type.CHESTPLATE || super.isBarVisible(s); }

    @Override
    public int getBarWidth(ItemStack s) {
        return getType() == ArmorItem.Type.CHESTPLATE ? Math.round(13.0F * oxygen(s) / Balance.O2_FULL) : super.getBarWidth(s);
    }

    @Override
    public int getBarColor(ItemStack s) {
        if (getType() != ArmorItem.Type.CHESTPLATE) return super.getBarColor(s);
        int o = oxygen(s);
        return o < 1_200 ? 0xFF3030 : o < 6_000 ? 0xFF9A3C : 0x4AA3FF;
    }

    @Override
    public void appendHoverText(ItemStack s, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        if (getType() == ArmorItem.Type.CHESTPLATE)
            tip.add(Component.translatable("tooltip.wmd.eva_suit.oxygen", clock(oxygen(s)), clock(Balance.O2_FULL)).withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("tooltip.wmd.eva_suit.set").withStyle(ChatFormatting.GRAY));
    }
}
