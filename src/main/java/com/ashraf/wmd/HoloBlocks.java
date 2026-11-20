package com.ashraf.wmd;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class HoloBlocks {

    private HoloBlocks() {}

    public static final DeferredBlock<HoloTableBlock> HOLO_TABLE = Wmd.BLOCKS.registerBlock("holo_table", HoloTableBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> HOLO_TABLE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("holo_table", HOLO_TABLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HoloTableBlockEntity>> HOLO_TABLE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("holo_table", () -> BlockEntityType.Builder.of(HoloTableBlockEntity::new, HOLO_TABLE.get()).build(null));

    static void init() {}

    static boolean isHolo(ItemStack s) { return !s.isEmpty() && s.is(HOLO_TABLE_ITEM.get()); }

    static void tab(CreativeModeTab.Output output) { output.accept(HOLO_TABLE_ITEM.get()); }
}
