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

public final class TeslaBlocks {

    private TeslaBlocks() {}

    public static final DeferredBlock<TeslaTowerBlock> TESLA_TOWER = Wmd.BLOCKS.registerBlock("tesla_tower", TeslaTowerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> TESLA_TOWER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("tesla_tower", TESLA_TOWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TeslaTowerBlockEntity>> TESLA_TOWER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("tesla_tower", () -> BlockEntityType.Builder.of(TeslaTowerBlockEntity::new, TESLA_TOWER.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TESLA_TOWER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(TESLA_TOWER_ITEM.get()); }
}
