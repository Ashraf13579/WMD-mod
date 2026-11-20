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

public final class PulseEmpBlocks {

    private PulseEmpBlocks() {}

    public static final DeferredBlock<PulseEmpBlock> PULSE_EMP = Wmd.BLOCKS.registerBlock("pulse_emp", PulseEmpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> PULSE_EMP_ITEM = Wmd.ITEMS.registerSimpleBlockItem("pulse_emp", PULSE_EMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PulseEmpBlockEntity>> PULSE_EMP_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("pulse_emp", () -> BlockEntityType.Builder.of(PulseEmpBlockEntity::new, PULSE_EMP.get()).build(null));

    static void init() {}

    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PULSE_EMP_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
    }

    static void tab(CreativeModeTab.Output output) { output.accept(PULSE_EMP_ITEM.get()); }
}
