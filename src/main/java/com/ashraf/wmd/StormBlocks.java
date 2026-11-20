package com.ashraf.wmd;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
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

public final class StormBlocks {

    private StormBlocks() {}

    public static final DeferredBlock<StormGeneratorBlock> STORM_GENERATOR = Wmd.BLOCKS.registerBlock("storm_generator", StormGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> STORM_GENERATOR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("storm_generator", STORM_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StormGeneratorBlockEntity>> STORM_GENERATOR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("storm_generator", () -> BlockEntityType.Builder.of(StormGeneratorBlockEntity::new, STORM_GENERATOR.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STORM_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(STORM_GENERATOR_ITEM.get()); }
}
