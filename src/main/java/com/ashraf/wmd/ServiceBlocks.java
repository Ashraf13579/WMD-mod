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

public final class ServiceBlocks {

    private ServiceBlocks() {}

    public static final DeferredBlock<ServiceArmBlock> SERVICE_ARM = Wmd.BLOCKS.registerBlock("service_arm", ServiceArmBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> SERVICE_ARM_ITEM = Wmd.ITEMS.registerSimpleBlockItem("service_arm", SERVICE_ARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceArmBlockEntity>> SERVICE_ARM_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("service_arm", () -> BlockEntityType.Builder.of(ServiceArmBlockEntity::new, SERVICE_ARM.get()).build(null));

    public static final DeferredBlock<ServiceReceptacleBlock> SERVICE_RECEPTACLE = Wmd.BLOCKS.registerBlock("service_receptacle", ServiceReceptacleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SERVICE_RECEPTACLE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("service_receptacle", SERVICE_RECEPTACLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ServiceReceptacleBlockEntity>> SERVICE_RECEPTACLE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("service_receptacle", () -> BlockEntityType.Builder.of(ServiceReceptacleBlockEntity::new, SERVICE_RECEPTACLE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SERVICE_ARM_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SERVICE_ARM_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(SERVICE_ARM_ITEM.get());
        output.accept(SERVICE_RECEPTACLE_ITEM.get());
    }
}
