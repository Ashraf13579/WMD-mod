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

public final class TurntableBlocks {

    private TurntableBlocks() {}

    public static final DeferredBlock<TurntableBlock> TURNTABLE = Wmd.BLOCKS.registerBlock("turntable", TurntableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TURNTABLE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("turntable", TURNTABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurntableBlockEntity>> TURNTABLE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("turntable", () -> BlockEntityType.Builder.of(TurntableBlockEntity::new, TURNTABLE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURNTABLE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(TURNTABLE_ITEM.get());
    }
}
