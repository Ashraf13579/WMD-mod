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

public final class CloakBlocks {

    private CloakBlocks() {}

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3));
    }

    public static final DeferredBlock<ActiveCloakBlock> CLOAK = Wmd.BLOCKS.registerBlock("active_cloak", ActiveCloakBlock::new, props());
    public static final DeferredItem<BlockItem> CLOAK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("active_cloak", CLOAK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ActiveCloakBlockEntity>> CLOAK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("active_cloak", () -> BlockEntityType.Builder.of(ActiveCloakBlockEntity::new, CLOAK.get()).build(null));

    public static final DeferredBlock<QuantumRadarBlock> QRADAR = Wmd.BLOCKS.registerBlock("quantum_radar", QuantumRadarBlock::new, props());
    public static final DeferredItem<BlockItem> QRADAR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("quantum_radar", QRADAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<QuantumRadarBlockEntity>> QRADAR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("quantum_radar", () -> BlockEntityType.Builder.of(QuantumRadarBlockEntity::new, QRADAR.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CLOAK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, QRADAR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(CLOAK_ITEM.get());
        output.accept(QRADAR_ITEM.get());
    }
}
