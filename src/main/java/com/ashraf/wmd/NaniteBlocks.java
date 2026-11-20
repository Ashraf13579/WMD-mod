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

public final class NaniteBlocks {

    private NaniteBlocks() {}

    public static final DeferredBlock<NaniteForgeBlock> NANITE_FORGE = Wmd.BLOCKS.registerBlock("nanite_forge", NaniteForgeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> NANITE_FORGE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("nanite_forge", NANITE_FORGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NaniteForgeBlockEntity>> NANITE_FORGE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("nanite_forge", () -> BlockEntityType.Builder.of(NaniteForgeBlockEntity::new, NANITE_FORGE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NANITE_FORGE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(NANITE_FORGE_ITEM.get()); }
}
