package com.ashraf.wmd;

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
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class IonBlocks {

    private IonBlocks() {}

    public static final DeferredBlock<IonThrusterBlock> ION = Wmd.BLOCKS.registerBlock("ion_thruster", IonThrusterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 18.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<AirEngineItem> ION_ITEM = Wmd.ITEMS.registerItem("ion_thruster", p -> new AirEngineItem(ION.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IonThrusterBlockEntity>> ION_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("ion_thruster", () -> BlockEntityType.Builder.of(SableBridge::newIonThruster, ION.get()).build(null));

    public static final DeferredBlock<CraftShieldBlock> SHIELD = Wmd.BLOCKS.registerBlock("craft_shield", CraftShieldBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 18.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> SHIELD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("craft_shield", SHIELD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftShieldBlockEntity>> SHIELD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("craft_shield", () -> BlockEntityType.Builder.of(CraftShieldBlockEntity::new, SHIELD.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ION_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SHIELD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(ION_ITEM.get());
        output.accept(SHIELD_ITEM.get());
    }
}
