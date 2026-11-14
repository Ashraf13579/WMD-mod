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

public final class HangarDoorBlocks {

    private HangarDoorBlocks() {}

    public static final DeferredBlock<HangarDoorBlock> HANGAR_DOOR = Wmd.BLOCKS.registerBlock("hangar_door", HangarDoorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HANGAR_DOOR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("hangar_door", HANGAR_DOOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HangarDoorBlockEntity>> HANGAR_DOOR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("hangar_door", () -> BlockEntityType.Builder.of(HangarDoorBlockEntity::new, HANGAR_DOOR.get()).build(null));

    public static final DeferredBlock<HangarCurtainBlock> HANGAR_CURTAIN = Wmd.BLOCKS.registerBlock("hangar_curtain", HangarCurtainBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)
                    .noOcclusion().noLootTable().isValidSpawn((s, l, p, e) -> false));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HANGAR_DOOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) {
        output.accept(HANGAR_DOOR_ITEM.get());
    }
}
