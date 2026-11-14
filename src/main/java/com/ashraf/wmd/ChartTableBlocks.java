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

public final class ChartTableBlocks {

    private ChartTableBlocks() {}

    public static final DeferredBlock<ChartTableBlock> CHART_TABLE = Wmd.BLOCKS.registerBlock("chart_table", ChartTableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> CHART_TABLE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("chart_table", CHART_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChartTableBlockEntity>> CHART_TABLE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("chart_table", () -> BlockEntityType.Builder.of(ChartTableBlockEntity::new, CHART_TABLE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHART_TABLE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void navyTab(CreativeModeTab.Output output) {
        output.accept(CHART_TABLE_ITEM.get());
    }
}
