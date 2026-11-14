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

public final class RfPowerBlocks {

    private RfPowerBlocks() {}

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3));
    }

    public static final DeferredBlock<RfLinkBlock> SENDER = Wmd.BLOCKS.registerBlock("rf_sender", p -> new RfLinkBlock(p, true), props());
    public static final DeferredItem<BlockItem> SENDER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("rf_sender", SENDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RfLinkBlockEntity>> SENDER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("rf_sender", () -> BlockEntityType.Builder.of(RfLinkBlockEntity::new, SENDER.get()).build(null));

    public static final DeferredBlock<RfLinkBlock> RECEIVER = Wmd.BLOCKS.registerBlock("rf_receiver", p -> new RfLinkBlock(p, false), props());
    public static final DeferredItem<BlockItem> RECEIVER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("rf_receiver", RECEIVER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RfLinkBlockEntity>> RECEIVER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("rf_receiver", () -> BlockEntityType.Builder.of(RfLinkBlockEntity::new, RECEIVER.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SENDER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RECEIVER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(SENDER_ITEM.get());
        output.accept(RECEIVER_ITEM.get());
    }
}
