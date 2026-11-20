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

public final class PlasmaMortarBlocks {

    private PlasmaMortarBlocks() {}

    public static final DeferredBlock<PlasmaMortarBlock> PLASMA_MORTAR = Wmd.BLOCKS.registerBlock("plasma_mortar", PlasmaMortarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> PLASMA_MORTAR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("plasma_mortar", PLASMA_MORTAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlasmaMortarBlockEntity>> PLASMA_MORTAR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("plasma_mortar", () -> BlockEntityType.Builder.of(PlasmaMortarBlockEntity::new, PLASMA_MORTAR.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PLASMA_MORTAR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PLASMA_MORTAR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(PLASMA_MORTAR_ITEM.get()); }
}
