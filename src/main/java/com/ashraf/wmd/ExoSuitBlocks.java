package com.ashraf.wmd;

import java.util.EnumMap;
import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ExoSuitBlocks {

    private ExoSuitBlocks() {}

    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, Wmd.MODID);
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Wmd.MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<SuitRackMenu>> SUIT_RACK_MENU = MENUS.register("suit_rack",
            () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(SuitRackMenu::new));
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Wmd.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXO_FE =
            COMPONENTS.registerComponentType("exo_fe", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXO_MODS =
            COMPONENTS.registerComponentType("exo_mods", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXO_AMMO =
            COMPONENTS.registerComponentType("exo_ammo", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> EXO_RF =
            COMPONENTS.registerComponentType("exo_rf", b -> b.persistent(com.mojang.serialization.Codec.INT).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register("exo_suit", () -> new ArmorMaterial(
            net.minecraft.Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.HELMET, Balance.EXO_ARMOR[0]); m.put(ArmorItem.Type.CHESTPLATE, Balance.EXO_ARMOR[1]);
                m.put(ArmorItem.Type.LEGGINGS, Balance.EXO_ARMOR[2]); m.put(ArmorItem.Type.BOOTS, Balance.EXO_ARMOR[3]); m.put(ArmorItem.Type.BODY, Balance.EXO_ARMOR[1]);
            }),
            Balance.EXO_ENCHANT, SoundEvents.ARMOR_EQUIP_NETHERITE,
            () -> Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "armour_plate"))),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "exo_suit"))), Balance.EXO_TOUGH, Balance.EXO_KB));

    private static Item.Properties suit() { return new Item.Properties().rarity(Rarity.EPIC); }

    public static final DeferredItem<ExoSuitItem> EXO_HELMET = Wmd.ITEMS.registerItem("exo_helmet", p -> new ExoSuitItem(MATERIAL, ArmorItem.Type.HELMET, p), suit());
    public static final DeferredItem<ExoSuitItem> EXO_CHEST = Wmd.ITEMS.registerItem("exo_chest", p -> new ExoSuitItem(MATERIAL, ArmorItem.Type.CHESTPLATE, p), suit());
    public static final DeferredItem<ExoSuitItem> EXO_LEGS = Wmd.ITEMS.registerItem("exo_legs", p -> new ExoSuitItem(MATERIAL, ArmorItem.Type.LEGGINGS, p), suit());
    public static final DeferredItem<ExoSuitItem> EXO_BOOTS = Wmd.ITEMS.registerItem("exo_boots", p -> new ExoSuitItem(MATERIAL, ArmorItem.Type.BOOTS, p), suit());

    public static final java.util.Map<ExoModule, DeferredItem<ExoModuleItem>> MODULES = new java.util.EnumMap<>(ExoModule.class);
    static {
        for (ExoModule m : ExoModule.ALL)
            MODULES.put(m, Wmd.ITEMS.registerItem("exo_mod_" + m.id, p -> new ExoModuleItem(m, p), new Item.Properties().rarity(Rarity.RARE)));
    }
    public static final DeferredItem<ExoMissilePackItem> MICRO_MISSILES = Wmd.ITEMS.registerItem("exo_micro_missiles", ExoMissilePackItem::new, new Item.Properties());

    public static final DeferredBlock<SuitRackBlock> SUIT_RACK = Wmd.BLOCKS.registerBlock("suit_rack", SuitRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> SUIT_RACK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("suit_rack", SUIT_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuitRackBlockEntity>> SUIT_RACK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("suit_rack", () -> BlockEntityType.Builder.of(SuitRackBlockEntity::new, SUIT_RACK.get()).build(null));

    static void register(IEventBus bus) {
        MATERIALS.register(bus);
        COMPONENTS.register(bus);
        MENUS.register(bus);
    }

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SUIT_RACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> new ComponentEnergyStorage(stack, EXO_FE.get(), Balance.EXO_FE_CAP, Balance.RACK_CHARGE_FE, 0),
                EXO_CHEST.get());
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(EXO_HELMET.get());
        ItemStack c = new ItemStack(EXO_CHEST.get());
        ExoSuitItem.setFe(c, Balance.EXO_FE_CAP);
        output.accept(c);
        output.accept(EXO_LEGS.get());
        output.accept(EXO_BOOTS.get());
        output.accept(SUIT_RACK_ITEM.get());
        for (ExoModule m : ExoModule.ALL) output.accept(MODULES.get(m).get());
        output.accept(MICRO_MISSILES.get());
    }
}
