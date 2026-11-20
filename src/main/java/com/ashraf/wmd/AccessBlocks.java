package com.ashraf.wmd;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class AccessBlocks {

    private AccessBlocks() {}

    public static final DeferredBlock<AccessTerminalBlock> TERMINAL = Wmd.BLOCKS.registerBlock("access_terminal", AccessTerminalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TERMINAL_ITEM = Wmd.ITEMS.registerSimpleBlockItem("access_terminal", TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AccessTerminalBlockEntity>> TERMINAL_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("access_terminal", () -> BlockEntityType.Builder.of(AccessTerminalBlockEntity::new, TERMINAL.get()).build(null));

    static void init() {}

    static void tab(CreativeModeTab.Output output) { output.accept(TERMINAL_ITEM.get()); }
}
