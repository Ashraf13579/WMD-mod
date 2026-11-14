package com.ashraf.wmd;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class CargoBlocks {

    private CargoBlocks() {}

    public static final DeferredBlock<CargoRampBlock> CARGO_RAMP = Wmd.BLOCKS.registerBlock("cargo_ramp", CargoRampBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CARGO_RAMP_ITEM = Wmd.ITEMS.registerSimpleBlockItem("cargo_ramp", CARGO_RAMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoRampBlockEntity>> CARGO_RAMP_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("cargo_ramp", () -> BlockEntityType.Builder.of(CargoRampBlockEntity::new, CARGO_RAMP.get()).build(null));

    public static final DeferredBlock<CargoPalletBlock> CARGO_PALLET = Wmd.BLOCKS.registerBlock("cargo_pallet", p -> new CargoPalletBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.5F, 6.0F).sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<CargoPalletBlock> CARGO_PALLET_HEAVY = Wmd.BLOCKS.registerBlock("cargo_pallet_heavy", p -> new CargoPalletBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<CargoPalletItem> CARGO_PALLET_ITEM = Wmd.ITEMS.registerItem("cargo_pallet", p -> new CargoPalletItem(CARGO_PALLET.get(), p, false));
    public static final DeferredItem<CargoPalletItem> CARGO_PALLET_HEAVY_ITEM = Wmd.ITEMS.registerItem("cargo_pallet_heavy", p -> new CargoPalletItem(CARGO_PALLET_HEAVY.get(), p, true));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoPalletBlockEntity>> CARGO_PALLET_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("cargo_pallet", () -> BlockEntityType.Builder.of(CargoPalletBlockEntity::new, CARGO_PALLET.get(), CARGO_PALLET_HEAVY.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CargoPalletMenu>> CARGO_PALLET_MENU =
            Wmd.MENU_TYPES.register("cargo_pallet", () -> IMenuTypeExtension.create(CargoPalletMenu::new));

    public static final DeferredHolder<EntityType<?>, EntityType<PalletEntity>> PALLET = Wmd.ENTITY_TYPES.register("drop_pallet",
            () -> EntityType.Builder.<PalletEntity>of(PalletEntity::new, MobCategory.MISC).sized(0.98F, 1.0F).clientTrackingRange(16).updateInterval(1).build("drop_pallet"));
    public static final DeferredHolder<EntityType<?>, EntityType<ParachuteEntity>> PARACHUTE = Wmd.ENTITY_TYPES.register("para_chute",
            () -> EntityType.Builder.<ParachuteEntity>of(ParachuteEntity::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(16).updateInterval(1).build("para_chute"));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CARGO_RAMP_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(CARGO_RAMP_ITEM.get());
        output.accept(CARGO_PALLET_ITEM.get());
        output.accept(CARGO_PALLET_HEAVY_ITEM.get());
    }

    static Item palletItem(boolean heavy) { return heavy ? CARGO_PALLET_HEAVY_ITEM.get() : CARGO_PALLET_ITEM.get(); }
}
