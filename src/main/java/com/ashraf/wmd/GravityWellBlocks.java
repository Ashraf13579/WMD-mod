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

public final class GravityWellBlocks {

    private GravityWellBlocks() {}

    public static final DeferredBlock<GravityWellBlock> GRAVITY_WELL = Wmd.BLOCKS.registerBlock("gravity_well", GravityWellBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> GRAVITY_WELL_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gravity_well", GRAVITY_WELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravityWellBlockEntity>> GRAVITY_WELL_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gravity_well", () -> BlockEntityType.Builder.of(GravityWellBlockEntity::new, GRAVITY_WELL.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GRAVITY_WELL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(GRAVITY_WELL_ITEM.get()); }
}
