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

public final class DeckConsoleBlocks {

    private DeckConsoleBlocks() {}

    public static final DeferredBlock<DeckConsoleBlock> DECK_CONSOLE = Wmd.BLOCKS.registerBlock("deck_console", DeckConsoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> DECK_CONSOLE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("deck_console", DECK_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckConsoleBlockEntity>> DECK_CONSOLE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("deck_console", () -> BlockEntityType.Builder.of(DeckConsoleBlockEntity::new, DECK_CONSOLE.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DECK_CONSOLE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(DECK_CONSOLE_ITEM.get());
    }
}
