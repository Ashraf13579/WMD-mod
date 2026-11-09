package com.ashraf.wmd;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CreateCompat {

    private CreateCompat() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Wmd.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Wmd.MODID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Wmd.MODID);

    public static final DeferredBlock<DriveCouplingBlock> DRIVE_COUPLING = BLOCKS.registerBlock("drive_coupling", DriveCouplingBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> DRIVE_COUPLING_ITEM = ITEMS.registerSimpleBlockItem("drive_coupling", DRIVE_COUPLING);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DriveCouplingBlockEntity>> DRIVE_COUPLING_BE = BLOCK_ENTITY_TYPES.register("drive_coupling",
            () -> BlockEntityType.Builder.of(DriveCouplingBlockEntity::new, DRIVE_COUPLING.get()).build(null));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(CreateCompat::capabilities);
        modBus.addListener(CreateCompat::tab);
        if (FMLEnvironment.dist == Dist.CLIENT) CreateCompatClient.init(modBus);
        Wmd.LOGGER.info("[create] Create found: the WMD drive coupling is registered");
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DRIVE_COUPLING_BE.get(), (be, side) -> side == be.facing() ? null : Grid.gridOnly(be.energy()));
    }

    private static void tab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == Wmd.WMD_TAB_INDUSTRY.getKey()) event.accept(DRIVE_COUPLING_ITEM.get());
    }
}
