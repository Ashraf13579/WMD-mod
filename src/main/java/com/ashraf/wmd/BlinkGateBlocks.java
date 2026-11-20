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

public final class BlinkGateBlocks {

    private BlinkGateBlocks() {}

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion();
    }

    public static final DeferredBlock<BlinkGateCoreBlock> BLINK_CORE = Wmd.BLOCKS.registerBlock("blink_gate_core", BlinkGateCoreBlock::new,
            props().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> BLINK_CORE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("blink_gate_core", BLINK_CORE);
    public static final DeferredBlock<BlinkGateCornerBlock> BLINK_CORNER = Wmd.BLOCKS.registerBlock("blink_gate_corner", BlinkGateCornerBlock::new, props());
    public static final DeferredBlock<BlinkGateSegmentBlock> BLINK_SEGMENT = Wmd.BLOCKS.registerBlock("blink_gate_segment", BlinkGateSegmentBlock::new, props());
    public static final DeferredItem<Item> CORNER_ITEM = Wmd.ITEMS.registerSimpleItem("blink_gate_corner", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> SEGMENT_ITEM = Wmd.ITEMS.registerSimpleItem("blink_gate_segment", new Item.Properties().stacksTo(64));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlinkGateCoreBlockEntity>> BLINK_CORE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("blink_gate_core", () -> BlockEntityType.Builder.of(BlinkGateCoreBlockEntity::new, BLINK_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlinkGatePieceBlockEntity>> BLINK_CORNER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("blink_gate_corner", () -> BlockEntityType.Builder.of(BlinkGatePieceBlockEntity::corner, BLINK_CORNER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlinkGatePieceBlockEntity>> BLINK_SEGMENT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("blink_gate_segment", () -> BlockEntityType.Builder.of(BlinkGatePieceBlockEntity::segment, BLINK_SEGMENT.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BLINK_CORE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(BLINK_CORE_ITEM.get());
        output.accept(CORNER_ITEM.get());
        output.accept(SEGMENT_ITEM.get());
    }
}
