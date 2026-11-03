package com.ashraf.wmd;

import java.util.EnumMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class GasMaskItem extends ArmorItem {

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Wmd.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register("gas_mask", () -> new ArmorMaterial(
            net.minecraft.Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                for (ArmorItem.Type t : ArmorItem.Type.values()) m.put(t, t == ArmorItem.Type.HELMET ? 1 : 0);
            }),
            5, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.CHARCOAL),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gas_mask"))), 0.0F, 0.0F));

    public GasMaskItem(Holder<ArmorMaterial> material, Item.Properties p) { super(material, ArmorItem.Type.HELMET, p.durability(Balance.GAS_MASK_FILTER)); }

    public static boolean protects(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.HEAD);
        return s.getItem() instanceof GasMaskItem && s.getDamageValue() < s.getMaxDamage();
    }

    public static void breathe(LivingEntity e) {
        ItemStack s = e.getItemBySlot(EquipmentSlot.HEAD);
        if (s.getItem() instanceof GasMaskItem && !(e instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().instabuild))
            s.setDamageValue(Math.min(s.getMaxDamage(), s.getDamageValue() + 1));
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, java.util.function.Consumer<Item> onBroken) { return 0; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.wmd.gas_mask.tip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.wmd.gas_mask.filter", (stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
