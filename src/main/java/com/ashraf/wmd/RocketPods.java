package com.ashraf.wmd;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

public final class RocketPods {

    private RocketPods() {}

    public static final DeferredBlock<RocketPodBlock> ROCKET_POD = Wmd.BLOCKS.registerBlock("rocket_pod", RocketPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> ROCKET_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("rocket_pod", ROCKET_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RocketPodBlockEntity>> ROCKET_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("rocket_pod", () -> BlockEntityType.Builder.of(RocketPodBlockEntity::new, ROCKET_POD.get()).build(null));

    public static final DeferredItem<AirRocketItem> AIR_ROCKET = Wmd.ITEMS.register("air_rocket", () -> new AirRocketItem(new Item.Properties().stacksTo(16)));

    static void init() {}

    static void tab(CreativeModeTab.Output output) {
        output.accept(ROCKET_POD_ITEM.get());
        output.accept(AIR_ROCKET.get());
    }
}
