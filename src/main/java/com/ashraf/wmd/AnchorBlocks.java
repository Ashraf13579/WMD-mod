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

public final class AnchorBlocks {

    private AnchorBlocks() {}

    public static final DeferredBlock<AnchorWindlassBlock> ANCHOR_WINDLASS = Wmd.BLOCKS.registerBlock("anchor_windlass", AnchorWindlassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(0)));
    public static final DeferredItem<BlockItem> ANCHOR_WINDLASS_ITEM = Wmd.ITEMS.registerSimpleBlockItem("anchor_windlass", ANCHOR_WINDLASS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnchorWindlassBlockEntity>> ANCHOR_WINDLASS_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("anchor_windlass", () -> BlockEntityType.Builder.of(AnchorWindlassBlockEntity::new, ANCHOR_WINDLASS.get()).build(null));

    public static final DeferredBlock<HawsePipeBlock> HAWSE_PIPE = Wmd.BLOCKS.registerBlock("hawse_pipe", HawsePipeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HAWSE_PIPE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("hawse_pipe", HAWSE_PIPE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HawsePipeBlockEntity>> HAWSE_PIPE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("hawse_pipe", () -> BlockEntityType.Builder.of(HawsePipeBlockEntity::new, HAWSE_PIPE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ANCHOR_WINDLASS_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void navyTab(CreativeModeTab.Output output) {
        output.accept(ANCHOR_WINDLASS_ITEM.get());
        output.accept(HAWSE_PIPE_ITEM.get());
    }
}
