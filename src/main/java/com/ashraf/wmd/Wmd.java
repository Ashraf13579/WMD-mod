package com.ashraf.wmd;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Wmd.MODID)
public class Wmd {

    public static final String MODID = "wmd";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MODID);

    public static final DeferredRegister<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, MODID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.placement.PlacementModifierType<?>, net.minecraft.world.level.levelgen.placement.PlacementModifierType<OreSwitch>> ORE_SWITCH =
            PLACEMENT_MODIFIERS.register("ore_switch", () -> () -> OreSwitch.CODEC);

    public static final DeferredBlock<Block> CRUDE_OIL_DEPOSIT = BLOCKS.registerSimpleBlock("crude_oil_deposit",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .sound(SoundType.DEEPSLATE));
    public static final DeferredItem<BlockItem> CRUDE_OIL_DEPOSIT_ITEM = ITEMS.registerSimpleBlockItem("crude_oil_deposit", CRUDE_OIL_DEPOSIT);

    public static final DeferredItem<OilDetectorItem> OIL_DETECTOR = ITEMS.registerItem("oil_detector", OilDetectorItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<Feature<?>, OilDepositFeature> OIL_DEPOSIT_FEATURE = FEATURES.register("oil_deposit",
            () -> new OilDepositFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, OreDepositFeature> ORE_DEPOSIT_FEATURE = FEATURES.register("ore_deposit",
            () -> new OreDepositFeature(OreDepositFeature.Config.CODEC));

    public static final DeferredHolder<Feature<?>, NoduleFieldFeature> NODULE_FIELD_FEATURE = FEATURES.register("nodule_field",
            () -> new NoduleFieldFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredItem<GroundScannerItem> GROUND_SCANNER = ITEMS.registerItem("ground_scanner", GroundScannerItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredBlock<GeneratorEngineBlock> GENERATOR_ENGINE = BLOCKS.registerBlock("generator_engine",
            GeneratorEngineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion());
    public static final DeferredItem<BlockItem> GENERATOR_ENGINE_ITEM = ITEMS.registerSimpleBlockItem("generator_engine", GENERATOR_ENGINE);

    public static final DeferredBlock<DieselGeneratorBlock> DIESEL_GENERATOR = BLOCKS.registerBlock("diesel_generator", DieselGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DIESEL_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("diesel_generator", DIESEL_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorEngineBlockEntity>> DIESEL_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("diesel_generator", () -> BlockEntityType.Builder.of(GeneratorEngineBlockEntity::new, DIESEL_GENERATOR.get()).build(null));

    public static final DeferredBlock<MultiblockPartBlock> MACHINE_CASING = BLOCKS.registerBlock("machine_casing",
            MultiblockPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> MACHINE_CASING_ITEM = ITEMS.registerSimpleBlockItem("machine_casing", MACHINE_CASING);
    public static final DeferredBlock<MultiblockPartBlock> POWER_PORT = BLOCKS.registerBlock("power_port",
            MultiblockPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 6.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> POWER_PORT_ITEM = ITEMS.registerSimpleBlockItem("power_port", POWER_PORT);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU = MENU_TYPES.register("generator",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new GeneratorMenu(id, inventory, buf)));

    public static final DeferredItem<Item> WRENCH = ITEMS.registerSimpleItem("wrench", new Item.Properties().stacksTo(1));

    public static final DeferredItem<Item> PIPE_CLEARER = ITEMS.register("pipe_clearer", () -> new PipeClearerItem(new Item.Properties().durability(128)));

    public static final DeferredItem<Item> BAY_SIZE_KIT = ITEMS.registerSimpleItem("bay_size_kit", new Item.Properties());

    public static final DeferredBlock<BatteryTierBlock.T1> BATTERY_T1 = BLOCKS.registerBlock("battery_t1", BatteryTierBlock.T1::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> BATTERY_T1_ITEM = ITEMS.registerSimpleBlockItem(BATTERY_T1);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryTierBlockEntity>> BATTERY_T1_BE = BLOCK_ENTITY_TYPES.register("battery_t1",
            () -> BlockEntityType.Builder.<BatteryTierBlockEntity>of((p, s) -> new BatteryTierBlockEntity(Wmd.BATTERY_T1_BE.get(), p, s, 1), BATTERY_T1.get()).build(null));
    public static final DeferredBlock<BatteryTierBlock.T2> BATTERY_T2 = BLOCKS.registerBlock("battery_t2_cabinet", BatteryTierBlock.T2::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> BATTERY_T2_ITEM = ITEMS.registerSimpleBlockItem(BATTERY_T2);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryTierBlockEntity>> BATTERY_T2_BE = BLOCK_ENTITY_TYPES.register("battery_t2",
            () -> BlockEntityType.Builder.<BatteryTierBlockEntity>of((p, s) -> new BatteryTierBlockEntity(Wmd.BATTERY_T2_BE.get(), p, s, 2), BATTERY_T2.get()).build(null));
    public static final DeferredBlock<BatteryTierBlock.T3> BATTERY_T3 = BLOCKS.registerBlock("battery_t3", BatteryTierBlock.T3::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> BATTERY_T3_ITEM = ITEMS.registerSimpleBlockItem(BATTERY_T3);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryTierBlockEntity>> BATTERY_T3_BE = BLOCK_ENTITY_TYPES.register("battery_t3",
            () -> BlockEntityType.Builder.<BatteryTierBlockEntity>of((p, s) -> new BatteryTierBlockEntity(Wmd.BATTERY_T3_BE.get(), p, s, 3), BATTERY_T3.get()).build(null));
    public static final DeferredBlock<BatteryTierBlock.T4> BATTERY_T4 = BLOCKS.registerBlock("battery_t4", BatteryTierBlock.T4::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> BATTERY_T4_ITEM = ITEMS.registerSimpleBlockItem(BATTERY_T4);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryTierBlockEntity>> BATTERY_T4_BE = BLOCK_ENTITY_TYPES.register("battery_t4",
            () -> BlockEntityType.Builder.<BatteryTierBlockEntity>of((p, s) -> new BatteryTierBlockEntity(Wmd.BATTERY_T4_BE.get(), p, s, 4), BATTERY_T4.get()).build(null));

    public static final DeferredBlock<CctvBlocks.Camera> CCTV_CAMERA = BLOCKS.registerBlock("cctv_camera", CctvBlocks.Camera::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CCTV_CAMERA_ITEM = ITEMS.registerSimpleBlockItem(CCTV_CAMERA);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CctvCameraBlockEntity>> CCTV_CAMERA_BE = BLOCK_ENTITY_TYPES.register("cctv_camera",
            () -> BlockEntityType.Builder.of(CctvCameraBlockEntity::new, CCTV_CAMERA.get()).build(null));
    public static final DeferredBlock<CctvBlocks.Covert> COVERT_CAMERA = BLOCKS.registerBlock("covert_camera", CctvBlocks.Covert::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 3.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> COVERT_CAMERA_ITEM = ITEMS.registerSimpleBlockItem(COVERT_CAMERA);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CctvCameraBlockEntity>> COVERT_CAMERA_BE = BLOCK_ENTITY_TYPES.register("covert_camera",
            () -> BlockEntityType.Builder.of(CctvCameraBlockEntity::new, COVERT_CAMERA.get()).build(null));
    public static final DeferredBlock<CctvBlocks.Relay> CAMERA_RELAY = BLOCKS.registerBlock("camera_relay", CctvBlocks.Relay::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> CAMERA_RELAY_ITEM = ITEMS.registerSimpleBlockItem(CAMERA_RELAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CameraRelayBlockEntity>> CAMERA_RELAY_BE = BLOCK_ENTITY_TYPES.register("camera_relay",
            () -> BlockEntityType.Builder.of(CameraRelayBlockEntity::new, CAMERA_RELAY.get()).build(null));
    public static final DeferredBlock<CctvBlocks.Sensor> PERIMETER_SENSOR = BLOCKS.registerBlock("perimeter_sensor", CctvBlocks.Sensor::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> PERIMETER_SENSOR_ITEM = ITEMS.registerSimpleBlockItem(PERIMETER_SENSOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PerimeterSensorBlockEntity>> PERIMETER_SENSOR_BE = BLOCK_ENTITY_TYPES.register("perimeter_sensor",
            () -> BlockEntityType.Builder.of(PerimeterSensorBlockEntity::new, PERIMETER_SENSOR.get()).build(null));
    public static final DeferredBlock<CctvBlocks.Floodlight> FLOODLIGHT = BLOCKS.registerBlock("floodlight", CctvBlocks.Floodlight::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> FLOODLIGHT_ITEM = ITEMS.registerSimpleBlockItem(FLOODLIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FloodlightBlockEntity>> FLOODLIGHT_BE = BLOCK_ENTITY_TYPES.register("floodlight",
            () -> BlockEntityType.Builder.of(FloodlightBlockEntity::new, FLOODLIGHT.get()).build(null));

    public static final DeferredBlock<C4ChargeBlock> C4_CHARGE = BLOCKS.registerBlock("c4_charge", C4ChargeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.0F, 0.5F).sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final DeferredItem<BlockItem> C4_CHARGE_ITEM = ITEMS.registerSimpleBlockItem(C4_CHARGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<C4ChargeBlockEntity>> C4_CHARGE_BE = BLOCK_ENTITY_TYPES.register("c4_charge",
            () -> BlockEntityType.Builder.of(C4ChargeBlockEntity::new, C4_CHARGE.get()).build(null));
    public static final DeferredItem<DetonatorItem> DETONATOR = ITEMS.registerItem("detonator", DetonatorItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<HackingDeviceItem> HACKING_DEVICE = ITEMS.registerItem("hacking_device", HackingDeviceItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredBlock<CctvBlocks.Jammer> PORTABLE_JAMMER = BLOCKS.registerBlock("portable_jammer", CctvBlocks.Jammer::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> PORTABLE_JAMMER_ITEM = ITEMS.registerSimpleBlockItem(PORTABLE_JAMMER, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortableJammerBlockEntity>> PORTABLE_JAMMER_BE = BLOCK_ENTITY_TYPES.register("portable_jammer",
            () -> BlockEntityType.Builder.of(PortableJammerBlockEntity::new, PORTABLE_JAMMER.get()).build(null));
    public static final DeferredBlock<CctvBlocks.Scanner> SEISMIC_SCANNER = BLOCKS.registerBlock("seismic_scanner", CctvBlocks.Scanner::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SEISMIC_SCANNER_ITEM = ITEMS.registerSimpleBlockItem(SEISMIC_SCANNER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeismicScannerBlockEntity>> SEISMIC_SCANNER_BE = BLOCK_ENTITY_TYPES.register("seismic_scanner",
            () -> BlockEntityType.Builder.of(SeismicScannerBlockEntity::new, SEISMIC_SCANNER.get()).build(null));

    public static final DeferredBlock<SentryTurretBlock> SENTRY_TURRET = BLOCKS.registerBlock("sentry_turret", SentryTurretBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 18.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> SENTRY_TURRET_ITEM = ITEMS.registerSimpleBlockItem(SENTRY_TURRET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SentryTurretBlockEntity>> SENTRY_TURRET_BE = BLOCK_ENTITY_TYPES.register("sentry_turret",
            () -> BlockEntityType.Builder.of(SentryTurretBlockEntity::new, SENTRY_TURRET.get()).build(null));
    public static final DeferredBlock<FenceEnergizerBlock> FENCE_ENERGIZER = BLOCKS.registerBlock("fence_energizer", FenceEnergizerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> FENCE_ENERGIZER_ITEM = ITEMS.registerSimpleBlockItem(FENCE_ENERGIZER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FenceEnergizerBlockEntity>> FENCE_ENERGIZER_BE = BLOCK_ENTITY_TYPES.register("fence_energizer",
            () -> BlockEntityType.Builder.of(FenceEnergizerBlockEntity::new, FENCE_ENERGIZER.get()).build(null));
    public static final DeferredBlock<ElectricFenceBlock> ELECTRIC_FENCE = BLOCKS.registerBlock("electric_fence", ElectricFenceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredItem<BlockItem> ELECTRIC_FENCE_ITEM = ITEMS.registerSimpleBlockItem(ELECTRIC_FENCE);
    public static final DeferredBlock<MineBlock.PressureMine> PRESSURE_MINE = BLOCKS.registerBlock("pressure_mine", MineBlock.PressureMine::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(2.0F, 1.0F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final DeferredItem<BlockItem> PRESSURE_MINE_ITEM = ITEMS.registerSimpleBlockItem(PRESSURE_MINE);
    public static final DeferredBlock<MineBlock.Claymore> CLAYMORE = BLOCKS.registerBlock("claymore", MineBlock.Claymore::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F, 1.0F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY).noOcclusion());
    public static final DeferredItem<BlockItem> CLAYMORE_ITEM = ITEMS.registerSimpleBlockItem(CLAYMORE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineBlockEntity>> MINE_BE = BLOCK_ENTITY_TYPES.register("mine",
            () -> BlockEntityType.Builder.of(MineBlockEntity::new, PRESSURE_MINE.get(), CLAYMORE.get()).build(null));

    public static final DeferredBlock<BatteryBlock> BATTERY = BLOCKS.registerBlock("battery",
            BatteryBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> BATTERY_ITEM = ITEMS.registerSimpleBlockItem("battery", BATTERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BatteryBlockEntity>> BATTERY_BE =
            BLOCK_ENTITY_TYPES.register("battery",
                    () -> BlockEntityType.Builder.of(BatteryBlockEntity::new, BATTERY.get()).build(null));

    public static final DeferredBlock<PadPlateBlock> PAD_PLATE = BLOCKS.registerBlock("launch_plate",
            PadPlateBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4.0F, 8.0F).sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<BlockItem> PAD_PLATE_ITEM = ITEMS.registerSimpleBlockItem("launch_plate", PAD_PLATE);

    public static final DeferredBlock<LaunchTerminalBlock> LAUNCH_TERMINAL_1 = BLOCKS.registerBlock("launch_terminal_1",
            p -> new LaunchTerminalBlock(p, 1),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4.0F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<LaunchTerminalBlock> LAUNCH_TERMINAL_2 = BLOCKS.registerBlock("launch_terminal_2",
            p -> new LaunchTerminalBlock(p, 2),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.0F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<LaunchTerminalBlock> LAUNCH_TERMINAL_3 = BLOCKS.registerBlock("launch_terminal_3",
            p -> new LaunchTerminalBlock(p, 3),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(4.0F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<LaunchTerminalBlock> LAUNCH_TERMINAL_4 = BLOCKS.registerBlock("launch_terminal_4",
            p -> new LaunchTerminalBlock(p, 4),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(4.0F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK));

    public static final DeferredBlock<PadFillerBlock> PAD_FILLER = BLOCKS.registerBlock("pad_filler",
            PadFillerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false));
    public static final DeferredItem<BlockItem> LAUNCH_TERMINAL_1_ITEM = ITEMS.registerSimpleBlockItem("launch_terminal_1", LAUNCH_TERMINAL_1);
    public static final DeferredItem<BlockItem> LAUNCH_TERMINAL_2_ITEM = ITEMS.registerSimpleBlockItem("launch_terminal_2", LAUNCH_TERMINAL_2);
    public static final DeferredItem<BlockItem> LAUNCH_TERMINAL_3_ITEM = ITEMS.registerSimpleBlockItem("launch_terminal_3", LAUNCH_TERMINAL_3);
    public static final DeferredItem<BlockItem> LAUNCH_TERMINAL_4_ITEM = ITEMS.registerSimpleBlockItem("launch_terminal_4", LAUNCH_TERMINAL_4);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchTerminalBlockEntity>> LAUNCH_TERMINAL_BE =
            BLOCK_ENTITY_TYPES.register("launch_terminal",
                    () -> BlockEntityType.Builder.of(LaunchTerminalBlockEntity::new,
                            LAUNCH_TERMINAL_1.get(), LAUNCH_TERMINAL_2.get(), LAUNCH_TERMINAL_3.get(), LAUNCH_TERMINAL_4.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL_MENU = MENU_TYPES.register("terminal",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new TerminalMenu(id, inventory, buf)));

    public static final DeferredItem<StageItem> STAGE_CORE = ITEMS.registerItem("stage_core", p -> new StageItem(p, StageItem.CORE), new Item.Properties().stacksTo(1));
    public static final DeferredItem<StageItem> STAGE_UPPER = ITEMS.registerItem("stage_upper", p -> new StageItem(p, StageItem.UPPER), new Item.Properties().stacksTo(1));
    public static final DeferredItem<StageItem> STAGE_BOOSTER = ITEMS.registerItem("stage_booster", p -> new StageItem(p, StageItem.BOOSTER), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LaunchMissileItem> MISSILE_T1 = ITEMS.registerItem("missile_t1", p -> new LaunchMissileItem(p, 1), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LaunchMissileItem> MISSILE_T2 = ITEMS.registerItem("missile_t2", p -> new LaunchMissileItem(p, 2), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LaunchMissileItem> MISSILE_T3 = ITEMS.registerItem("missile_t3", p -> new LaunchMissileItem(p, 3), new Item.Properties().stacksTo(1));

    public static final DeferredItem<NetworkCardItem> NETWORK_CARD = ITEMS.registerItem("network_card", NetworkCardItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredBlock<RadarStationBlock> RADAR_STATION = BLOCKS.registerBlock("radar_station",
            RadarStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> RADAR_STATION_ITEM = ITEMS.registerSimpleBlockItem("radar_station", RADAR_STATION);
    public static final DeferredBlock<RadarPartBlock> RADAR_PART = BLOCKS.registerBlock("radar_part",
            RadarPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadarBlockEntity>> RADAR_BE =
            BLOCK_ENTITY_TYPES.register("radar_station",
                    () -> BlockEntityType.Builder.of(RadarBlockEntity::new, RADAR_STATION.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<RadarMenu>> RADAR_MENU = MENU_TYPES.register("radar",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new RadarMenu(id, inventory, buf)));

    public static final DeferredBlock<BusbarBlock> BUSBAR = BLOCKS.registerBlock("busbar", BusbarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> BUSBAR_ITEM = ITEMS.registerSimpleBlockItem("busbar", BUSBAR);

    public static final DeferredBlock<HullPenetratorBlock> HULL_PENETRATOR = BLOCKS.registerBlock("hull_penetrator", HullPenetratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> HULL_PENETRATOR_ITEM = ITEMS.registerSimpleBlockItem("hull_penetrator", HULL_PENETRATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BusbarBlockEntity>> BUSBAR_BE =
            BLOCK_ENTITY_TYPES.register("busbar", () -> BlockEntityType.Builder.of(BusbarBlockEntity::new, BUSBAR.get(), HULL_PENETRATOR.get()).build(null));

    public static final DeferredBlock<AlarmBlock> ALARM = BLOCKS.registerBlock("alarm", AlarmBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> ALARM_ITEM = ITEMS.registerSimpleBlockItem("alarm", ALARM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlarmBlockEntity>> ALARM_BE =
            BLOCK_ENTITY_TYPES.register("alarm", () -> BlockEntityType.Builder.of(AlarmBlockEntity::new, ALARM.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<MachineMenu>> MACHINE_MENU = MENU_TYPES.register("machine",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new MachineMenu(id, inventory, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<SorterMenu>> SORTER_MENU = MENU_TYPES.register("sorter",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new SorterMenu(id, inventory, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<AlarmMenu>> ALARM_MENU = MENU_TYPES.register("alarm",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new AlarmMenu(id, inventory, buf)));

    public static final DeferredBlock<FluidPipeBlock> FLUID_PIPE = BLOCKS.registerBlock("fluid_pipe", FluidPipeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> FLUID_PIPE_ITEM = ITEMS.registerSimpleBlockItem("fluid_pipe", FLUID_PIPE);

    public static final DeferredBlock<FluidPipeBlock> ALUMINIUM_PIPE = BLOCKS.registerBlock("aluminium_pipe", p -> new FluidPipeBlock(p, 2),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> ALUMINIUM_PIPE_ITEM = ITEMS.registerSimpleBlockItem("aluminium_pipe", ALUMINIUM_PIPE);
    public static final DeferredBlock<FluidPipeBlock> TITANIUM_PIPE = BLOCKS.registerBlock("titanium_pipe", p -> new FluidPipeBlock(p, 3),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> TITANIUM_PIPE_ITEM = ITEMS.registerSimpleBlockItem("titanium_pipe", TITANIUM_PIPE);
    public static final DeferredBlock<FluidValveBlock> FLUID_VALVE = BLOCKS.registerBlock("fluid_valve", FluidValveBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> FLUID_VALVE_ITEM = ITEMS.registerSimpleBlockItem("fluid_valve", FLUID_VALVE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidPipeBlockEntity>> FLUID_PIPE_BE =
            BLOCK_ENTITY_TYPES.register("fluid_pipe", () -> BlockEntityType.Builder.of(FluidPipeBlockEntity::new, FLUID_PIPE.get(), ALUMINIUM_PIPE.get(), TITANIUM_PIPE.get(), FLUID_VALVE.get()).build(null));
    public static final DeferredBlock<FloodgateBlock> FLOODGATE = BLOCKS.registerBlock("floodgate", FloodgateBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> FLOODGATE_ITEM = ITEMS.registerSimpleBlockItem("floodgate", FLOODGATE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FloodgateBlockEntity>> FLOODGATE_BE =
            BLOCK_ENTITY_TYPES.register("floodgate", () -> BlockEntityType.Builder.of(FloodgateBlockEntity::new, FLOODGATE.get()).build(null));
    public static final DeferredItem<FluidCanisterItem> CANISTER = ITEMS.registerItem("canister", FluidCanisterItem::new, new Item.Properties());

    public static final DeferredBlock<ItemDuctBlock> ITEM_DUCT = BLOCKS.registerBlock("item_duct", ItemDuctBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> ITEM_DUCT_ITEM = ITEMS.registerSimpleBlockItem("item_duct", ITEM_DUCT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItemDuctBlockEntity>> ITEM_DUCT_BE =
            BLOCK_ENTITY_TYPES.register("item_duct", () -> BlockEntityType.Builder.of(ItemDuctBlockEntity::new, ITEM_DUCT.get()).build(null));
    public static final DeferredBlock<SorterBlock> SORTER = BLOCKS.registerBlock("sorter", SorterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> SORTER_ITEM = ITEMS.registerSimpleBlockItem("sorter", SORTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SorterBlockEntity>> SORTER_BE =
            BLOCK_ENTITY_TYPES.register("sorter", () -> BlockEntityType.Builder.of(SorterBlockEntity::new, SORTER.get()).build(null));
    public static final DeferredBlock<SupplyInterfaceBlock> SUPPLY_INTERFACE = BLOCKS.registerBlock("supply_interface", SupplyInterfaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> SUPPLY_INTERFACE_ITEM = ITEMS.registerSimpleBlockItem("supply_interface", SUPPLY_INTERFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupplyInterfaceBlockEntity>> SUPPLY_INTERFACE_BE =
            BLOCK_ENTITY_TYPES.register("supply_interface", () -> BlockEntityType.Builder.of(SupplyInterfaceBlockEntity::new, SUPPLY_INTERFACE.get()).build(null));

    public static final DeferredBlock<MachinePartBlock> MACHINE_PART = BLOCKS.registerBlock("machine_part", MachinePartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachinePartBlockEntity>> MACHINE_PART_BE =
            BLOCK_ENTITY_TYPES.register("machine_part", () -> BlockEntityType.Builder.of(MachinePartBlockEntity::new, MACHINE_PART.get()).build(null));
    public static final DeferredBlock<PumpjackBlock> PUMPJACK = BLOCKS.registerBlock("pumpjack", PumpjackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion());

    public static final DeferredBlock<WarheadReactorBlock> WARHEAD_REACTOR = BLOCKS.registerBlock("warhead_reactor", WarheadReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> WARHEAD_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("warhead_reactor", WARHEAD_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WarheadReactorBlockEntity>> WARHEAD_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("warhead_reactor", () -> BlockEntityType.Builder.of(WarheadReactorBlockEntity::new, WARHEAD_REACTOR.get()).build(null));

    public static final DeferredItem<InterceptorItem> INTERCEPTOR_T1 = ITEMS.registerItem("interceptor_t1", p -> new InterceptorItem(p, 1), new Item.Properties());
    public static final DeferredBlock<InterceptorLauncherBlock> INTERCEPTOR_LAUNCHER = BLOCKS.registerBlock("interceptor_launcher", InterceptorLauncherBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> INTERCEPTOR_LAUNCHER_ITEM = ITEMS.registerSimpleBlockItem("interceptor_launcher", INTERCEPTOR_LAUNCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptorLauncherBlockEntity>> LAUNCHER_BE =
            BLOCK_ENTITY_TYPES.register("interceptor_launcher", () -> BlockEntityType.Builder.of(InterceptorLauncherBlockEntity::new, INTERCEPTOR_LAUNCHER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<LauncherMenu>> LAUNCHER_MENU = MENU_TYPES.register("interceptor_launcher",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new LauncherMenu(id, inventory, buf)));

    public static final DeferredBlock<InterceptorSiloBlock> INTERCEPTOR_SILO_T3 = BLOCKS.registerBlock("interceptor_silo_t3", InterceptorSiloBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 1200.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> INTERCEPTOR_SILO_T3_ITEM = ITEMS.registerSimpleBlockItem("interceptor_silo_t3", INTERCEPTOR_SILO_T3);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptorSiloBlockEntity>> SILO_BE =
            BLOCK_ENTITY_TYPES.register("interceptor_silo_t3", () -> BlockEntityType.Builder.of(InterceptorSiloBlockEntity::new, INTERCEPTOR_SILO_T3.get()).build(null));

    public static final DeferredBlock<InterceptorSiloT4Block> INTERCEPTOR_SILO_T4 = BLOCKS.registerBlock("interceptor_silo_t4", InterceptorSiloT4Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(10.0F, 1200.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> INTERCEPTOR_SILO_T4_ITEM = ITEMS.registerSimpleBlockItem("interceptor_silo_t4", INTERCEPTOR_SILO_T4);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptorSiloT4BlockEntity>> SILO4_BE =
            BLOCK_ENTITY_TYPES.register("interceptor_silo_t4", () -> BlockEntityType.Builder.of(InterceptorSiloT4BlockEntity::new, INTERCEPTOR_SILO_T4.get()).build(null));

    public static final DeferredBlock<InterceptorBatteryBlock> INTERCEPTOR_BATTERY_T2 = BLOCKS.registerBlock("interceptor_battery_t2", InterceptorBatteryBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 600.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> INTERCEPTOR_BATTERY_T2_ITEM = ITEMS.registerSimpleBlockItem("interceptor_battery_t2", INTERCEPTOR_BATTERY_T2);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InterceptorBatteryBlockEntity>> HEAVY_BATTERY_BE =
            BLOCK_ENTITY_TYPES.register("interceptor_battery_t2", () -> BlockEntityType.Builder.of(InterceptorBatteryBlockEntity::new, INTERCEPTOR_BATTERY_T2.get()).build(null));

    public static final DeferredItem<CiwsAmmoItem> CIWS_AMMO = ITEMS.registerItem("ciws_ammo", CiwsAmmoItem::new, new Item.Properties());

    public static final DeferredItem<UpgradeItem> UPGRADE_SPEED = ITEMS.registerItem("upgrade_speed", p -> new UpgradeItem(UpgradeItem.Type.SPEED, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_EFFICIENCY = ITEMS.registerItem("upgrade_efficiency", p -> new UpgradeItem(UpgradeItem.Type.EFFICIENCY, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_THROUGHPUT = ITEMS.registerItem("upgrade_throughput", p -> new UpgradeItem(UpgradeItem.Type.THROUGHPUT, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_BUFFER = ITEMS.registerItem("upgrade_buffer", p -> new UpgradeItem(UpgradeItem.Type.BUFFER, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_MUFFLER = ITEMS.registerItem("upgrade_muffler", p -> new UpgradeItem(UpgradeItem.Type.MUFFLER, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_AUTO_EJECT = ITEMS.registerItem("upgrade_auto_eject", p -> new UpgradeItem(UpgradeItem.Type.AUTO_EJECT, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_CIWS_DRUM = ITEMS.registerItem("upgrade_ciws_drum", p -> new UpgradeItem(UpgradeItem.Type.CIWS_DRUM, p), new Item.Properties());
    public static final DeferredItem<UpgradeItem> UPGRADE_HEAVY_WATER = ITEMS.registerItem("upgrade_heavy_water", p -> new UpgradeItem(UpgradeItem.Type.HEAVY_WATER, p), new Item.Properties());
    public static final DeferredBlock<CiwsBlock> CIWS = BLOCKS.registerBlock("ciws", CiwsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 600.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CIWS_ITEM = ITEMS.registerSimpleBlockItem("ciws", CIWS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CiwsBlockEntity>> CIWS_BE =
            BLOCK_ENTITY_TYPES.register("ciws", () -> BlockEntityType.Builder.of(CiwsBlockEntity::new, CIWS.get()).build(null));

    public static final DeferredHolder<Feature<?>, SalvageSiteFeature> SALVAGE_SITE_FEATURE = FEATURES.register("salvage_site",
            () -> new SalvageSiteFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredBlock<SalvageTurretBlock> SALVAGE_TURRET = BLOCKS.registerBlock("salvage_turret", SalvageTurretBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(30.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SALVAGE_TURRET_ITEM = ITEMS.registerSimpleBlockItem("salvage_turret", SALVAGE_TURRET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SalvageTurretBlockEntity>> SALVAGE_TURRET_BE =
            BLOCK_ENTITY_TYPES.register("salvage_turret", () -> BlockEntityType.Builder.of(SalvageTurretBlockEntity::new, SALVAGE_TURRET.get()).build(null));
    public static final DeferredBlock<SalvageGeneratorBlock> SALVAGE_GENERATOR = BLOCKS.registerBlock("salvage_generator", SalvageGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> SALVAGE_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("salvage_generator", SALVAGE_GENERATOR);
    public static final DeferredItem<SalvageItem> RADAR_COMPONENT = ITEMS.registerItem("radar_component", p -> new SalvageItem(p, "radar_component"),
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    public static final DeferredItem<SalvageItem> GUIDANCE_CORE_T4 = ITEMS.registerItem("guidance_core_t4", p -> new SalvageItem(p, "guidance_core_t4"),
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<SalvageItem> SATELLITE_PART = ITEMS.registerItem("satellite_part", p -> new SalvageItem(p, "satellite_part"),
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));

    public static final DeferredBlock<SiloFillerBlock> SILO_FILLER = BLOCKS.registerBlock("silo_filler", SiloFillerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false));

    public static final DeferredBlock<HardenedBlock.Concrete> REINFORCED_CONCRETE = BLOCKS.registerBlock("reinforced_concrete", HardenedBlock.Concrete::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(8.0F, 1200.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredBlock<HardenedBlock.Glass> BLAST_GLASS = BLOCKS.registerBlock("blast_glass", HardenedBlock.Glass::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<HardenedBlock.Plate> ARMOUR_PLATE = BLOCKS.registerBlock("armour_plate", HardenedBlock.Plate::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(12.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> REINFORCED_CONCRETE_ITEM = ITEMS.registerSimpleBlockItem("reinforced_concrete", REINFORCED_CONCRETE);
    public static final DeferredItem<BlockItem> BLAST_GLASS_ITEM = ITEMS.registerSimpleBlockItem("blast_glass", BLAST_GLASS);
    public static final DeferredItem<BlockItem> ARMOUR_PLATE_ITEM = ITEMS.registerSimpleBlockItem("armour_plate", ARMOUR_PLATE);

    private static BlockBehaviour.Properties labProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion();
    }
    public static final DeferredBlock<LabModuleBlock.Core> LAB_CORE = BLOCKS.registerBlock("lab_core", LabModuleBlock.Core::new, labProps());
    public static final DeferredBlock<LabModuleBlock.Press> LAB_PRESS = BLOCKS.registerBlock("lab_press", LabModuleBlock.Press::new, labProps());
    public static final DeferredBlock<LabModuleBlock.Filler> LAB_FILLER = BLOCKS.registerBlock("lab_filler", LabModuleBlock.Filler::new, labProps());
    public static final DeferredBlock<LabModuleBlock.Oven> LAB_OVEN = BLOCKS.registerBlock("lab_oven", LabModuleBlock.Oven::new, labProps());
    public static final DeferredBlock<LabModuleBlock.Storage> LAB_STORAGE = BLOCKS.registerBlock("lab_storage", LabModuleBlock.Storage::new, labProps());
    public static final DeferredItem<LabModuleItem> LAB_CORE_ITEM = ITEMS.registerItem("lab_core", p -> new LabModuleItem(LAB_CORE.get(), p), new Item.Properties());
    public static final DeferredItem<LabModuleItem> LAB_PRESS_ITEM = ITEMS.registerItem("lab_press", p -> new LabModuleItem(LAB_PRESS.get(), p), new Item.Properties());
    public static final DeferredItem<LabModuleItem> LAB_FILLER_ITEM = ITEMS.registerItem("lab_filler", p -> new LabModuleItem(LAB_FILLER.get(), p), new Item.Properties());
    public static final DeferredItem<LabModuleItem> LAB_OVEN_ITEM = ITEMS.registerItem("lab_oven", p -> new LabModuleItem(LAB_OVEN.get(), p), new Item.Properties());
    public static final DeferredItem<LabModuleItem> LAB_STORAGE_ITEM = ITEMS.registerItem("lab_storage", p -> new LabModuleItem(LAB_STORAGE.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LabModuleBlockEntity>> LAB_BE =
            BLOCK_ENTITY_TYPES.register("lab_module", () -> BlockEntityType.Builder.of(LabModuleBlockEntity::new, LAB_CORE.get(), LAB_PRESS.get(), LAB_FILLER.get(), LAB_OVEN.get(), LAB_STORAGE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<LabMenu>> LAB_MENU = MENU_TYPES.register("lab",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new LabMenu(id, inventory, buf)));
    public static final DeferredItem<HardenedLidItem> HARDENED_LID = ITEMS.registerItem("hardened_lid", HardenedLidItem::new, new Item.Properties().stacksTo(4));
    public static final DeferredItem<ReactorRepairKitItem> REACTOR_REPAIR_KIT = ITEMS.registerItem("reactor_repair_kit", ReactorRepairKitItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<BlockItem> PUMPJACK_ITEM = ITEMS.registerSimpleBlockItem("pumpjack", PUMPJACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpjackBlockEntity>> PUMPJACK_BE =
            BLOCK_ENTITY_TYPES.register("pumpjack", () -> BlockEntityType.Builder.of(PumpjackBlockEntity::new, PUMPJACK.get()).build(null));

    public static final DeferredBlock<FeedPumpBlock> FEED_PUMP = BLOCKS.registerBlock("feed_pump", FeedPumpBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(8)));
    public static final DeferredItem<BlockItem> FEED_PUMP_ITEM = ITEMS.registerSimpleBlockItem("feed_pump", FEED_PUMP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedPumpBlockEntity>> FEED_PUMP_BE =
            BLOCK_ENTITY_TYPES.register("feed_pump", () -> BlockEntityType.Builder.of(FeedPumpBlockEntity::new, FEED_PUMP.get()).build(null));

    public static final DeferredBlock<WaterIntakeBlock> WATER_INTAKE = BLOCKS.registerBlock("water_intake", WaterIntakeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(8)));
    public static final DeferredItem<BlockItem> WATER_INTAKE_ITEM = ITEMS.registerSimpleBlockItem("water_intake", WATER_INTAKE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterIntakeBlockEntity>> WATER_INTAKE_BE =
            BLOCK_ENTITY_TYPES.register("water_intake", () -> BlockEntityType.Builder.of(WaterIntakeBlockEntity::new, WATER_INTAKE.get()).build(null));

    public static final DeferredBlock<GasTurbineBlock> GAS_TURBINE = BLOCKS.registerBlock("gas_turbine", GasTurbineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> GAS_TURBINE_ITEM = ITEMS.registerSimpleBlockItem("gas_turbine", GAS_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasTurbineBlockEntity>> GAS_TURBINE_BE =
            BLOCK_ENTITY_TYPES.register("gas_turbine", () -> BlockEntityType.Builder.of(GasTurbineBlockEntity::new, GAS_TURBINE.get()).build(null));
    public static final DeferredBlock<GridConverterBlock> GRID_CONVERTER = BLOCKS.registerBlock("grid_converter", GridConverterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> GRID_CONVERTER_ITEM = ITEMS.registerSimpleBlockItem("grid_converter", GRID_CONVERTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GridConverterBlockEntity>> GRID_CONVERTER_BE =
            BLOCK_ENTITY_TYPES.register("grid_converter", () -> BlockEntityType.Builder.of(GridConverterBlockEntity::new, GRID_CONVERTER.get()).build(null));

    public static final DeferredBlock<MagnesiumCellBlock> MAGNESIUM_CELL = BLOCKS.registerBlock("magnesium_cell", MagnesiumCellBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> MAGNESIUM_CELL_ITEM = ITEMS.registerSimpleBlockItem("magnesium_cell", MAGNESIUM_CELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagnesiumCellBlockEntity>> MAGNESIUM_CELL_BE =
            BLOCK_ENTITY_TYPES.register("magnesium_cell", () -> BlockEntityType.Builder.of(MagnesiumCellBlockEntity::new, MAGNESIUM_CELL.get()).build(null));

    public static final DeferredBlock<ResearchStationBlock> RESEARCH_STATION = BLOCKS.registerBlock("research_station", ResearchStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> RESEARCH_STATION_ITEM = ITEMS.registerSimpleBlockItem("research_station", RESEARCH_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchStationBlockEntity>> RESEARCH_STATION_BE =
            BLOCK_ENTITY_TYPES.register("research_station", () -> BlockEntityType.Builder.of(ResearchStationBlockEntity::new, RESEARCH_STATION.get()).build(null));

    public static final DeferredBlock<RollingMillBlock> ROLLING_MILL = BLOCKS.registerBlock("rolling_mill", RollingMillBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> ROLLING_MILL_ITEM = ITEMS.registerSimpleBlockItem("rolling_mill", ROLLING_MILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RollingMillBlockEntity>> ROLLING_MILL_BE =
            BLOCK_ENTITY_TYPES.register("rolling_mill", () -> BlockEntityType.Builder.of(RollingMillBlockEntity::new, ROLLING_MILL.get()).build(null));
    public static final DeferredBlock<WireDrawerBlock> WIRE_DRAWER = BLOCKS.registerBlock("wire_drawer", WireDrawerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> WIRE_DRAWER_ITEM = ITEMS.registerSimpleBlockItem("wire_drawer", WIRE_DRAWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WireDrawerBlockEntity>> WIRE_DRAWER_BE =
            BLOCK_ENTITY_TYPES.register("wire_drawer", () -> BlockEntityType.Builder.of(WireDrawerBlockEntity::new, WIRE_DRAWER.get()).build(null));

    public static final DeferredBlock<RoasterBlock> ROASTER = BLOCKS.registerBlock("roaster", RoasterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> ROASTER_ITEM = ITEMS.registerSimpleBlockItem("roaster", ROASTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoasterBlockEntity>> ROASTER_BE =
            BLOCK_ENTITY_TYPES.register("roaster", () -> BlockEntityType.Builder.of(RoasterBlockEntity::new, ROASTER.get()).build(null));

    public static final DeferredBlock<BlastFurnaceBlock> BLAST_FURNACE = BLOCKS.registerBlock("blast_furnace", BlastFurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> BLAST_FURNACE_ITEM = ITEMS.registerSimpleBlockItem("blast_furnace", BLAST_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlastFurnaceBlockEntity>> BLAST_FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("blast_furnace", () -> BlockEntityType.Builder.of(BlastFurnaceBlockEntity::new, BLAST_FURNACE.get()).build(null));

    public static final DeferredBlock<CokeOvenBlock> COKE_OVEN = BLOCKS.registerBlock("coke_oven", CokeOvenBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> COKE_OVEN_ITEM = ITEMS.registerSimpleBlockItem("coke_oven", COKE_OVEN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CokeOvenBlockEntity>> COKE_OVEN_BE =
            BLOCK_ENTITY_TYPES.register("coke_oven", () -> BlockEntityType.Builder.of(CokeOvenBlockEntity::new, COKE_OVEN.get()).build(null));
    public static final DeferredBlock<StarterGeneratorBlock> STARTER_GENERATOR = BLOCKS.registerBlock("starter_generator", StarterGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> STARTER_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("starter_generator", STARTER_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarterGeneratorBlockEntity>> STARTER_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("starter_generator", () -> BlockEntityType.Builder.of(StarterGeneratorBlockEntity::new, STARTER_GENERATOR.get()).build(null));

    public static final DeferredBlock<SolarPanelBlock> SOLAR_PANEL = BLOCKS.registerBlock("solar_panel", SolarPanelBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SOLAR_PANEL_ITEM = ITEMS.registerSimpleBlockItem("solar_panel", SOLAR_PANEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL_BE =
            BLOCK_ENTITY_TYPES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, SOLAR_PANEL.get()).build(null));

    public static final DeferredBlock<WindTurbineBlock> WIND_TURBINE = BLOCKS.registerBlock("wind_turbine", WindTurbineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> WIND_TURBINE_ITEM = ITEMS.registerSimpleBlockItem("wind_turbine", WIND_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindTurbineBlockEntity>> WIND_TURBINE_BE =
            BLOCK_ENTITY_TYPES.register("wind_turbine", () -> BlockEntityType.Builder.of(WindTurbineBlockEntity::new, WIND_TURBINE.get()).build(null));

    public static final DeferredBlock<SteamEngineBlock> STEAM_ENGINE = BLOCKS.registerBlock("steam_engine", SteamEngineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> STEAM_ENGINE_ITEM = ITEMS.registerSimpleBlockItem("steam_engine", STEAM_ENGINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamEngineBlockEntity>> STEAM_ENGINE_BE =
            BLOCK_ENTITY_TYPES.register("steam_engine", () -> BlockEntityType.Builder.of(SteamEngineBlockEntity::new, STEAM_ENGINE.get()).build(null));

    public static final DeferredBlock<GasolineEngineBlock> GASOLINE_ENGINE = BLOCKS.registerBlock("gasoline_engine", GasolineEngineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> GASOLINE_ENGINE_ITEM = ITEMS.registerSimpleBlockItem("gasoline_engine", GASOLINE_ENGINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GasolineEngineBlockEntity>> GASOLINE_ENGINE_BE =
            BLOCK_ENTITY_TYPES.register("gasoline_engine", () -> BlockEntityType.Builder.of(GasolineEngineBlockEntity::new, GASOLINE_ENGINE.get()).build(null));

    public static final DeferredBlock<FreePistonGeneratorBlock> FREE_PISTON_GENERATOR = BLOCKS.registerBlock("free_piston_generator", FreePistonGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> FREE_PISTON_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("free_piston_generator", FREE_PISTON_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FreePistonGeneratorBlockEntity>> FREE_PISTON_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("free_piston_generator", () -> BlockEntityType.Builder.of(FreePistonGeneratorBlockEntity::new, FREE_PISTON_GENERATOR.get()).build(null));

    public static final DeferredBlock<LavaGeneratorBlock> LAVA_GENERATOR = BLOCKS.registerBlock("lava_generator", LavaGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> LAVA_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("lava_generator", LAVA_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LavaGeneratorBlockEntity>> LAVA_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("lava_generator", () -> BlockEntityType.Builder.of(LavaGeneratorBlockEntity::new, LAVA_GENERATOR.get()).build(null));
    public static final DeferredBlock<StampMillBlock> STAMP_MILL = BLOCKS.registerBlock("stamp_mill", StampMillBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> STAMP_MILL_ITEM = ITEMS.registerSimpleBlockItem("stamp_mill", STAMP_MILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StampMillBlockEntity>> STAMP_MILL_BE =
            BLOCK_ENTITY_TYPES.register("stamp_mill", () -> BlockEntityType.Builder.of(StampMillBlockEntity::new, STAMP_MILL.get()).build(null));
    public static final DeferredBlock<PowderMillBlock> POWDER_MILL = BLOCKS.registerBlock("powder_mill", PowderMillBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> POWDER_MILL_ITEM = ITEMS.registerSimpleBlockItem("powder_mill", POWDER_MILL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowderMillBlockEntity>> POWDER_MILL_BE =
            BLOCK_ENTITY_TYPES.register("powder_mill", () -> BlockEntityType.Builder.of(PowderMillBlockEntity::new, POWDER_MILL.get()).build(null));
    public static final DeferredBlock<HydrogenFurnaceBlock> HYDROGEN_FURNACE = BLOCKS.registerBlock("hydrogen_furnace", HydrogenFurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> HYDROGEN_FURNACE_ITEM = ITEMS.registerSimpleBlockItem("hydrogen_furnace", HYDROGEN_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrogenFurnaceBlockEntity>> HYDROGEN_FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("hydrogen_furnace", () -> BlockEntityType.Builder.of(HydrogenFurnaceBlockEntity::new, HYDROGEN_FURNACE.get()).build(null));
    public static final DeferredBlock<SinterPressBlock> SINTER_PRESS = BLOCKS.registerBlock("sinter_press", SinterPressBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> SINTER_PRESS_ITEM = ITEMS.registerSimpleBlockItem("sinter_press", SINTER_PRESS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinterPressBlockEntity>> SINTER_PRESS_BE =
            BLOCK_ENTITY_TYPES.register("sinter_press", () -> BlockEntityType.Builder.of(SinterPressBlockEntity::new, SINTER_PRESS.get()).build(null));

    public static final DeferredBlock<LeachTankBlock> LEACH_TANK = BLOCKS.registerBlock("leach_tank", LeachTankBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> LEACH_TANK_ITEM = ITEMS.registerSimpleBlockItem("leach_tank", LEACH_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LeachTankBlockEntity>> LEACH_TANK_BE =
            BLOCK_ENTITY_TYPES.register("leach_tank", () -> BlockEntityType.Builder.of(LeachTankBlockEntity::new, LEACH_TANK.get()).build(null));
    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE = BLOCKS.registerBlock("centrifuge", CentrifugeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM = ITEMS.registerSimpleBlockItem("centrifuge", CENTRIFUGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE_BE =
            BLOCK_ENTITY_TYPES.register("centrifuge", () -> BlockEntityType.Builder.of(CentrifugeBlockEntity::new, CENTRIFUGE.get()).build(null));

    public static final DeferredBlock<NuclearReactorBlock> NUCLEAR_REACTOR = BLOCKS.registerBlock("nuclear_reactor", NuclearReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(10)));
    public static final DeferredItem<BlockItem> NUCLEAR_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("nuclear_reactor", NUCLEAR_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NuclearReactorBlockEntity>> NUCLEAR_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("nuclear_reactor", () -> BlockEntityType.Builder.of(NuclearReactorBlockEntity::new, NUCLEAR_REACTOR.get()).build(null));
    public static final DeferredBlock<ReactorPartBlock> REACTOR_PLATING = BLOCKS.registerBlock("reactor_plating", ReactorPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(8.0F, 30.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPartBlock> REACTOR_FRAME = BLOCKS.registerBlock("reactor_frame", ReactorPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPartBlock> REACTOR_CORE_SHIELD = BLOCKS.registerBlock("reactor_core_shield", ReactorPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(8.0F, 30.0F).sound(SoundType.GLASS).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(s -> 4));
    public static final DeferredBlock<ReactorPartBlock> CONTROL_ROD_DRIVE = BLOCKS.registerBlock("control_rod_drive", ReactorPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPartBlock> COOLANT_PUMP = BLOCKS.registerBlock("coolant_pump", ReactorPartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPortBlock> REACTOR_FUEL_PORT = BLOCKS.registerBlock("reactor_fuel_port", ReactorPortBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPortBlock> REACTOR_WATER_PORT = BLOCKS.registerBlock("reactor_water_port", ReactorPortBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredBlock<ReactorPortBlock> REACTOR_POWER_PORT = BLOCKS.registerBlock("reactor_power_port", ReactorPortBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> REACTOR_PLATING_ITEM = ITEMS.registerSimpleBlockItem("reactor_plating", REACTOR_PLATING);
    public static final DeferredItem<BlockItem> REACTOR_FRAME_ITEM = ITEMS.registerSimpleBlockItem("reactor_frame", REACTOR_FRAME);
    public static final DeferredItem<BlockItem> REACTOR_CORE_SHIELD_ITEM = ITEMS.registerSimpleBlockItem("reactor_core_shield", REACTOR_CORE_SHIELD);
    public static final DeferredItem<BlockItem> CONTROL_ROD_DRIVE_ITEM = ITEMS.registerSimpleBlockItem("control_rod_drive", CONTROL_ROD_DRIVE);
    public static final DeferredItem<BlockItem> COOLANT_PUMP_ITEM = ITEMS.registerSimpleBlockItem("coolant_pump", COOLANT_PUMP);
    public static final DeferredItem<BlockItem> REACTOR_FUEL_PORT_ITEM = ITEMS.registerSimpleBlockItem("reactor_fuel_port", REACTOR_FUEL_PORT);
    public static final DeferredItem<BlockItem> REACTOR_WATER_PORT_ITEM = ITEMS.registerSimpleBlockItem("reactor_water_port", REACTOR_WATER_PORT);
    public static final DeferredItem<BlockItem> REACTOR_POWER_PORT_ITEM = ITEMS.registerSimpleBlockItem("reactor_power_port", REACTOR_POWER_PORT);

    public static final DeferredBlock<ReactorPortBlock> REACTOR_STEAM_PORT = BLOCKS.registerBlock("reactor_steam_port", ReactorPortBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> REACTOR_STEAM_PORT_ITEM = ITEMS.registerSimpleBlockItem("reactor_steam_port", REACTOR_STEAM_PORT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReactorPortBlockEntity>> REACTOR_PORT_BE =
            BLOCK_ENTITY_TYPES.register("reactor_port", () -> BlockEntityType.Builder.of(ReactorPortBlockEntity::new, REACTOR_FUEL_PORT.get(), REACTOR_WATER_PORT.get(), REACTOR_POWER_PORT.get(), REACTOR_STEAM_PORT.get()).build(null));
    public static final DeferredBlock<CoolingTowerBlock> COOLING_TOWER = BLOCKS.registerBlock("cooling_tower", CoolingTowerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(6.0F, 20.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(5)));
    public static final DeferredItem<BlockItem> COOLING_TOWER_ITEM = ITEMS.registerSimpleBlockItem("cooling_tower", COOLING_TOWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoolingTowerBlockEntity>> COOLING_TOWER_BE =
            BLOCK_ENTITY_TYPES.register("cooling_tower", () -> BlockEntityType.Builder.of(CoolingTowerBlockEntity::new, COOLING_TOWER.get()).build(null));

    public static final DeferredBlock<SteamTurbineBlock> STEAM_TURBINE = BLOCKS.registerBlock("steam_turbine", SteamTurbineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> STEAM_TURBINE_ITEM = ITEMS.registerSimpleBlockItem("steam_turbine", STEAM_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteamTurbineBlockEntity>> STEAM_TURBINE_BE =
            BLOCK_ENTITY_TYPES.register("steam_turbine", () -> BlockEntityType.Builder.of(SteamTurbineBlockEntity::new, STEAM_TURBINE.get()).build(null));

    public static final DeferredBlock<FusionReactorBlock> FUSION_REACTOR = BLOCKS.registerBlock("fusion_reactor", FusionReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(12)));
    public static final DeferredItem<BlockItem> FUSION_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("fusion_reactor", FUSION_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FusionReactorBlockEntity>> FUSION_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("fusion_reactor", () -> BlockEntityType.Builder.of(FusionReactorBlockEntity::new, FUSION_REACTOR.get()).build(null));

    public static final DeferredBlock<ForcefieldBlock> FORCEFIELD = BLOCKS.registerBlock("forcefield_generator", ForcefieldBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(10)));
    public static final DeferredItem<BlockItem> FORCEFIELD_ITEM = ITEMS.registerSimpleBlockItem("forcefield_generator", FORCEFIELD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ForcefieldBlockEntity>> FORCEFIELD_BE =
            BLOCK_ENTITY_TYPES.register("forcefield_generator", () -> BlockEntityType.Builder.of(ForcefieldBlockEntity::new, FORCEFIELD.get()).build(null));

    public static final DeferredBlock<LaserDefenceBlock> LASER_DEFENCE = BLOCKS.registerBlock("laser_defence", LaserDefenceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(8)));
    public static final DeferredItem<BlockItem> LASER_DEFENCE_ITEM = ITEMS.registerSimpleBlockItem("laser_defence", LASER_DEFENCE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserDefenceBlockEntity>> LASER_DEFENCE_BE =
            BLOCK_ENTITY_TYPES.register("laser_defence", () -> BlockEntityType.Builder.of(LaserDefenceBlockEntity::new, LASER_DEFENCE.get()).build(null));

    public static final DeferredBlock<SwarmLauncherBlock> SWARM_LAUNCHER = BLOCKS.registerBlock("swarm_launcher", SwarmLauncherBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> SWARM_LAUNCHER_ITEM = ITEMS.registerSimpleBlockItem("swarm_launcher", SWARM_LAUNCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SwarmLauncherBlockEntity>> SWARM_LAUNCHER_BE =
            BLOCK_ENTITY_TYPES.register("swarm_launcher", () -> BlockEntityType.Builder.of(SwarmLauncherBlockEntity::new, SWARM_LAUNCHER.get()).build(null));
    public static final DeferredItem<SwarmDronePackItem> SWARM_DRONE_PACK = ITEMS.registerItem("swarm_drone_pack", SwarmDronePackItem::new, new Item.Properties());

    public static final DeferredBlock<DeadHandBlock> DEADHAND = BLOCKS.registerBlock("deadhand_bunker", DeadHandBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(30.0F, 1200.0F).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> DEADHAND_ITEM = ITEMS.registerSimpleBlockItem("deadhand_bunker", DEADHAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeadHandBlockEntity>> DEADHAND_BE =
            BLOCK_ENTITY_TYPES.register("deadhand_bunker", () -> BlockEntityType.Builder.of(DeadHandBlockEntity::new, DEADHAND.get()).build(null));
    public static final DeferredBlock<ReprocessingPlantBlock> REPROCESSING_PLANT = BLOCKS.registerBlock("reprocessing_plant", ReprocessingPlantBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> REPROCESSING_PLANT_ITEM = ITEMS.registerSimpleBlockItem("reprocessing_plant", REPROCESSING_PLANT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReprocessingPlantBlockEntity>> REPROCESSING_PLANT_BE =
            BLOCK_ENTITY_TYPES.register("reprocessing_plant", () -> BlockEntityType.Builder.of(ReprocessingPlantBlockEntity::new, REPROCESSING_PLANT.get()).build(null));

    public static final DeferredBlock<AntimatterColliderBlock> ANTIMATTER_COLLIDER = BLOCKS.registerBlock("antimatter_collider", AntimatterColliderBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(10)));
    public static final DeferredItem<BlockItem> ANTIMATTER_COLLIDER_ITEM = ITEMS.registerSimpleBlockItem("antimatter_collider", ANTIMATTER_COLLIDER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AntimatterColliderBlockEntity>> ANTIMATTER_COLLIDER_BE =
            BLOCK_ENTITY_TYPES.register("antimatter_collider", () -> BlockEntityType.Builder.of(AntimatterColliderBlockEntity::new, ANTIMATTER_COLLIDER.get()).build(null));
    public static final DeferredBlock<AntimatterStoreBlock> ANTIMATTER_STORE = BLOCKS.registerBlock("antimatter_store", AntimatterStoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(8)));
    public static final DeferredItem<BlockItem> ANTIMATTER_STORE_ITEM = ITEMS.registerSimpleBlockItem("antimatter_store", ANTIMATTER_STORE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AntimatterStoreBlockEntity>> ANTIMATTER_STORE_BE =
            BLOCK_ENTITY_TYPES.register("antimatter_store", () -> BlockEntityType.Builder.of(AntimatterStoreBlockEntity::new, ANTIMATTER_STORE.get()).build(null));
    public static final DeferredItem<Item> EMPTY_TRAP_CELL = ITEMS.registerSimpleItem("empty_trap_cell", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> ANTIMATTER_CELL = ITEMS.registerSimpleItem("antimatter_cell", new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredBlock<RailgunBlock> RAILGUN = BLOCKS.registerBlock("railgun", RailgunBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(10.0F, 60.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(8)));
    public static final DeferredItem<BlockItem> RAILGUN_ITEM = ITEMS.registerSimpleBlockItem("railgun", RAILGUN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RailgunBlockEntity>> RAILGUN_BE =
            BLOCK_ENTITY_TYPES.register("railgun", () -> BlockEntityType.Builder.of(RailgunBlockEntity::new, RAILGUN.get()).build(null));
    public static final DeferredItem<Item> RAILGUN_SLUG = ITEMS.registerSimpleItem("railgun_slug", new Item.Properties().stacksTo(4).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredBlock<GroundStationBlock> GROUND_STATION = BLOCKS.registerBlock("ground_station", GroundStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> GROUND_STATION_ITEM = ITEMS.registerSimpleBlockItem("ground_station", GROUND_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GroundStationBlockEntity>> GROUND_STATION_BE =
            BLOCK_ENTITY_TYPES.register("ground_station", () -> BlockEntityType.Builder.of(GroundStationBlockEntity::new, GROUND_STATION.get()).build(null));

    public static final DeferredBlock<EarlyWarningArrayBlock> EARLY_WARNING_ARRAY = BLOCKS.registerBlock("early_warning_array", EarlyWarningArrayBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> EARLY_WARNING_ARRAY_ITEM = ITEMS.registerSimpleBlockItem("early_warning_array", EARLY_WARNING_ARRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EarlyWarningArrayBlockEntity>> EARLY_WARNING_ARRAY_BE =
            BLOCK_ENTITY_TYPES.register("early_warning_array", () -> BlockEntityType.Builder.of(EarlyWarningArrayBlockEntity::new, EARLY_WARNING_ARRAY.get()).build(null));
    public static final DeferredItem<ReconDroneItem> RECON_DRONE = ITEMS.registerItem("recon_drone", ReconDroneItem::new, new Item.Properties().stacksTo(4));
    public static final DeferredItem<SpaceItem> RTG_CELL = ITEMS.registerItem("rtg_cell", p -> new SpaceItem(p, "rtg_cell"),
            new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    public static final DeferredBlock<SatelliteFacilityBlock> SATELLITE_FACILITY = BLOCKS.registerBlock("satellite_facility", SatelliteFacilityBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> SATELLITE_FACILITY_ITEM = ITEMS.registerSimpleBlockItem("satellite_facility", SATELLITE_FACILITY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SatelliteFacilityBlockEntity>> SATELLITE_FACILITY_BE =
            BLOCK_ENTITY_TYPES.register("satellite_facility", () -> BlockEntityType.Builder.of(SatelliteFacilityBlockEntity::new, SATELLITE_FACILITY.get()).build(null));
    public static final DeferredItem<PayloadUnitItem> PAYLOAD_UNIT = ITEMS.registerItem("payload_unit", PayloadUnitItem::new,
            new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE));

    public static final DeferredBlock<MortarBlock> MORTAR = BLOCKS.registerBlock("mortar", MortarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> MORTAR_ITEM = ITEMS.registerSimpleBlockItem("mortar", MORTAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MortarBlockEntity>> MORTAR_BE =
            BLOCK_ENTITY_TYPES.register("mortar", () -> BlockEntityType.Builder.of(MortarBlockEntity::new, MORTAR.get()).build(null));
    public static final DeferredBlock<HowitzerBlock> HOWITZER = BLOCKS.registerBlock("howitzer", HowitzerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> HOWITZER_ITEM = ITEMS.registerSimpleBlockItem("howitzer", HOWITZER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HowitzerBlockEntity>> HOWITZER_BE =
            BLOCK_ENTITY_TYPES.register("howitzer", () -> BlockEntityType.Builder.of(HowitzerBlockEntity::new, HOWITZER.get()).build(null));
    public static final DeferredBlock<ShellPressBlock> SHELL_PRESS = BLOCKS.registerBlock("shell_press", ShellPressBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> SHELL_PRESS_ITEM = ITEMS.registerSimpleBlockItem("shell_press", SHELL_PRESS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellPressBlockEntity>> SHELL_PRESS_BE =
            BLOCK_ENTITY_TYPES.register("shell_press", () -> BlockEntityType.Builder.of(ShellPressBlockEntity::new, SHELL_PRESS.get()).build(null));
    public static final DeferredItem<ArtShellItem> SHELL_MORTAR_HE = ITEMS.registerItem("shell_mortar_he", p -> new ArtShellItem(p, Artillery.MORTAR, Artillery.HE), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_MORTAR_SMOKE = ITEMS.registerItem("shell_mortar_smoke", p -> new ArtShellItem(p, Artillery.MORTAR, Artillery.SMOKE), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_MORTAR_ILLUM = ITEMS.registerItem("shell_mortar_illum", p -> new ArtShellItem(p, Artillery.MORTAR, Artillery.ILLUM), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_MORTAR_CLUSTER = ITEMS.registerItem("shell_mortar_cluster", p -> new ArtShellItem(p, Artillery.MORTAR, Artillery.CLUSTER), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_HE = ITEMS.registerItem("shell_howitzer_he", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.HE), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_SMOKE = ITEMS.registerItem("shell_howitzer_smoke", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.SMOKE), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_ILLUM = ITEMS.registerItem("shell_howitzer_illum", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.ILLUM), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_CLUSTER = ITEMS.registerItem("shell_howitzer_cluster", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.CLUSTER), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> PROPELLANT_CHARGE = ITEMS.registerItem("propellant_charge", p -> new ArtShellItem(p, -1, -1), new Item.Properties());

    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_LASER = ITEMS.registerItem("shell_howitzer_laser", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.LASER), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_CANISTER = ITEMS.registerItem("shell_howitzer_canister", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.CANISTER), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_SENSOR = ITEMS.registerItem("shell_howitzer_sensor", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.SENSOR), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_PIERCE = ITEMS.registerItem("shell_howitzer_pierce", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.PIERCE), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_INCENDIARY = ITEMS.registerItem("shell_howitzer_incendiary", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.INCENDIARY), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_FLAK = ITEMS.registerItem("shell_howitzer_flak", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.FLAK), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_CAMERA = ITEMS.registerItem("shell_howitzer_camera", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.CAMERA), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_JAMMING = ITEMS.registerItem("shell_howitzer_jamming", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.JAMMING), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_ROCKET = ITEMS.registerItem("shell_howitzer_rocket", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.ROCKET_ASSIST), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_LEAFLET = ITEMS.registerItem("shell_howitzer_leaflet", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.LEAFLET), new Item.Properties().stacksTo(8));
    public static final DeferredItem<ArtShellItem> SHELL_SUPER_NUKE = ITEMS.registerItem("shell_super_nuke", p -> new ArtShellItem(p, Artillery.SUPER_GUN, Artillery.SUPER_NUKE), new Item.Properties().stacksTo(1));

    public static final DeferredBlock<AlkylationUnitBlock> ALKYLATION_UNIT = BLOCKS.registerBlock("alkylation_unit", AlkylationUnitBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> ALKYLATION_UNIT_ITEM = ITEMS.registerSimpleBlockItem("alkylation_unit", ALKYLATION_UNIT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlkylationUnitBlockEntity>> ALKYLATION_UNIT_BE =
            BLOCK_ENTITY_TYPES.register("alkylation_unit", () -> BlockEntityType.Builder.of(AlkylationUnitBlockEntity::new, ALKYLATION_UNIT.get()).build(null));
    public static final DeferredBlock<CruiseLauncherBlock> CRUISE_LAUNCHER = BLOCKS.registerBlock("cruise_launcher", CruiseLauncherBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> CRUISE_LAUNCHER_ITEM = ITEMS.registerSimpleBlockItem("cruise_launcher", CRUISE_LAUNCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CruiseLauncherBlockEntity>> CRUISE_LAUNCHER_BE =
            BLOCK_ENTITY_TYPES.register("cruise_launcher", () -> BlockEntityType.Builder.of(CruiseLauncherBlockEntity::new, CRUISE_LAUNCHER.get()).build(null));
    public static final DeferredItem<MissilePartItem> JET_ENGINE = ITEMS.registerItem("jet_engine", p -> new MissilePartItem(p, 1, 2, MissilePartItem.JET), new Item.Properties());
    public static final DeferredItem<CruiseMissileItem> CRUISE_MISSILE = ITEMS.registerItem("cruise_missile", CruiseMissileItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<FuelCanisterItem> ACID_CATALYST = ITEMS.registerItem("acid_catalyst", p -> new FuelCanisterItem(p, "acid_catalyst"), new Item.Properties());
    public static final DeferredItem<FuelCanisterItem> FUEL_CANISTER = ITEMS.registerItem("fuel_canister", p -> new FuelCanisterItem(p, "fuel_canister"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<FuelCanisterItem> ALKYLATE_CANISTER = ITEMS.registerItem("alkylate_canister", p -> new FuelCanisterItem(p, "alkylate_canister"), new Item.Properties().stacksTo(16));

    public static final DeferredBlock<SuperGunBlock> SUPER_GUN = BLOCKS.registerBlock("super_gun", SuperGunBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> SUPER_GUN_ITEM = ITEMS.registerSimpleBlockItem("super_gun", SUPER_GUN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperGunBlockEntity>> SUPER_GUN_BE =
            BLOCK_ENTITY_TYPES.register("super_gun", () -> BlockEntityType.Builder.of(SuperGunBlockEntity::new, SUPER_GUN.get()).build(null));
    public static final DeferredItem<SuperGunItem> SHELL_SUPER_HE = ITEMS.registerItem("shell_super_he", p -> new SuperGunItem(p, false), new Item.Properties().stacksTo(8));
    public static final DeferredItem<SuperGunItem> SUPER_CHARGE = ITEMS.registerItem("super_charge", p -> new SuperGunItem(p, true), new Item.Properties());

    private static BlockBehaviour.Properties ccMachine(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(light));
    }
    public static final DeferredBlock<CcMapTableBlock> CC_MAP_TABLE = BLOCKS.registerBlock("cc_map_table", CcMapTableBlock::new, ccMachine(7));
    public static final DeferredBlock<CcModuleBlock.WarningBoard> CC_WARNING_BOARD = BLOCKS.registerBlock("cc_warning_board", CcModuleBlock.WarningBoard::new, ccMachine(5));
    public static final DeferredBlock<CcModuleBlock.ReconWall> CC_RECON_WALL = BLOCKS.registerBlock("cc_recon_wall", CcModuleBlock.ReconWall::new, ccMachine(5));
    public static final DeferredBlock<CcModuleBlock.AirDefence> CC_AIR_DEFENCE = BLOCKS.registerBlock("cc_air_defence", CcModuleBlock.AirDefence::new, ccMachine(4));
    public static final DeferredBlock<CcBuildingBlock> CC_BUILDING = BLOCKS.registerBlock("cc_building", CcBuildingBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(10.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> CC_BUILDING_ITEM = ITEMS.registerItem("cc_building", p -> new BlockItem(CC_BUILDING.get(), p),
            new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<BlockItem> CC_MAP_TABLE_ITEM = ITEMS.registerSimpleBlockItem("cc_map_table", CC_MAP_TABLE);
    public static final DeferredItem<BlockItem> CC_WARNING_BOARD_ITEM = ITEMS.registerSimpleBlockItem("cc_warning_board", CC_WARNING_BOARD);
    public static final DeferredItem<BlockItem> CC_RECON_WALL_ITEM = ITEMS.registerSimpleBlockItem("cc_recon_wall", CC_RECON_WALL);
    public static final DeferredItem<BlockItem> CC_AIR_DEFENCE_ITEM = ITEMS.registerSimpleBlockItem("cc_air_defence", CC_AIR_DEFENCE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcBuildingBlockEntity>> CC_BUILDING_BE =
            BLOCK_ENTITY_TYPES.register("cc_building", () -> BlockEntityType.Builder.of(CcBuildingBlockEntity::new, CC_BUILDING.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CcBuildingMenu>> CC_BUILDING_MENU = MENU_TYPES.register("cc_building",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new CcBuildingMenu(id, inventory, buf)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcMapTableBlockEntity>> CC_MAP_TABLE_BE =
            BLOCK_ENTITY_TYPES.register("cc_map_table", () -> BlockEntityType.Builder.of(CcMapTableBlockEntity::new, CC_MAP_TABLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcWarningBoardBlockEntity>> CC_WARNING_BOARD_BE =
            BLOCK_ENTITY_TYPES.register("cc_warning_board", () -> BlockEntityType.Builder.of(CcWarningBoardBlockEntity::new, CC_WARNING_BOARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcReconWallBlockEntity>> CC_RECON_WALL_BE =
            BLOCK_ENTITY_TYPES.register("cc_recon_wall", () -> BlockEntityType.Builder.of(CcReconWallBlockEntity::new, CC_RECON_WALL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcAirDefenceBlockEntity>> CC_AIR_DEFENCE_BE =
            BLOCK_ENTITY_TYPES.register("cc_air_defence", () -> BlockEntityType.Builder.of(CcAirDefenceBlockEntity::new, CC_AIR_DEFENCE.get()).build(null));

    public static final DeferredBlock<CcModuleBlocksC.SatGlobe> CC_SAT_GLOBE = BLOCKS.registerBlock("cc_sat_globe", CcModuleBlocksC.SatGlobe::new, ccMachine(6));
    public static final DeferredBlock<CcModuleBlocksC.EwRack> CC_EW_RACK = BLOCKS.registerBlock("cc_ew_rack", CcModuleBlocksC.EwRack::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksC.GpsRack> CC_GPS_RACK = BLOCKS.registerBlock("cc_gps_rack", CcModuleBlocksC.GpsRack::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksC.AlarmBoard> CC_ALARM_BOARD = BLOCKS.registerBlock("cc_alarm_board", CcModuleBlocksC.AlarmBoard::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksC.MapPrinter> CC_MAP_PRINTER = BLOCKS.registerBlock("cc_map_printer", CcModuleBlocksC.MapPrinter::new, ccMachine(3));
    public static final DeferredBlock<CcModuleBlocksC.CommsArray> CC_COMMS_ARRAY = BLOCKS.registerBlock("cc_comms_array", CcModuleBlocksC.CommsArray::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_SAT_GLOBE_ITEM = ITEMS.registerSimpleBlockItem("cc_sat_globe", CC_SAT_GLOBE);
    public static final DeferredItem<BlockItem> CC_EW_RACK_ITEM = ITEMS.registerSimpleBlockItem("cc_ew_rack", CC_EW_RACK);
    public static final DeferredItem<BlockItem> CC_GPS_RACK_ITEM = ITEMS.registerSimpleBlockItem("cc_gps_rack", CC_GPS_RACK);
    public static final DeferredItem<BlockItem> CC_ALARM_BOARD_ITEM = ITEMS.registerSimpleBlockItem("cc_alarm_board", CC_ALARM_BOARD);
    public static final DeferredItem<BlockItem> CC_MAP_PRINTER_ITEM = ITEMS.registerSimpleBlockItem("cc_map_printer", CC_MAP_PRINTER);
    public static final DeferredItem<BlockItem> CC_COMMS_ARRAY_ITEM = ITEMS.registerSimpleBlockItem("cc_comms_array", CC_COMMS_ARRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcSatGlobeBlockEntity>> CC_SAT_GLOBE_BE =
            BLOCK_ENTITY_TYPES.register("cc_sat_globe", () -> BlockEntityType.Builder.of(CcSatGlobeBlockEntity::new, CC_SAT_GLOBE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcEwRackBlockEntity>> CC_EW_RACK_BE =
            BLOCK_ENTITY_TYPES.register("cc_ew_rack", () -> BlockEntityType.Builder.of(CcEwRackBlockEntity::new, CC_EW_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcGpsRackBlockEntity>> CC_GPS_RACK_BE =
            BLOCK_ENTITY_TYPES.register("cc_gps_rack", () -> BlockEntityType.Builder.of(CcGpsRackBlockEntity::new, CC_GPS_RACK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcAlarmBoardBlockEntity>> CC_ALARM_BOARD_BE =
            BLOCK_ENTITY_TYPES.register("cc_alarm_board", () -> BlockEntityType.Builder.of(CcAlarmBoardBlockEntity::new, CC_ALARM_BOARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcMapPrinterBlockEntity>> CC_MAP_PRINTER_BE =
            BLOCK_ENTITY_TYPES.register("cc_map_printer", () -> BlockEntityType.Builder.of(CcMapPrinterBlockEntity::new, CC_MAP_PRINTER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcCommsArrayBlockEntity>> CC_COMMS_ARRAY_BE =
            BLOCK_ENTITY_TYPES.register("cc_comms_array", () -> BlockEntityType.Builder.of(CcCommsArrayBlockEntity::new, CC_COMMS_ARRAY.get()).build(null));
    public static final DeferredItem<SpaceItem> CUBESAT = ITEMS.registerItem("cubesat", p -> new SpaceItem(p, "cubesat"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<SpaceItem> UPLINK_CELL = ITEMS.registerItem("uplink_cell", p -> new SpaceItem(p, "uplink_cell"), new Item.Properties().stacksTo(16));

    public static final DeferredItem<SeekerModuleItem> LASER_SEEKER = ITEMS.registerItem("laser_seeker", p -> new SeekerModuleItem(p, MissileBuild.SEEK_LASER), new Item.Properties().stacksTo(16));
    public static final DeferredItem<SeekerModuleItem> HEAT_SEEKER = ITEMS.registerItem("heat_seeker", p -> new SeekerModuleItem(p, MissileBuild.SEEK_HEAT), new Item.Properties().stacksTo(16));
    public static final DeferredItem<SeekerModuleItem> ARM_SEEKER = ITEMS.registerItem("arm_seeker", p -> new SeekerModuleItem(p, MissileBuild.SEEK_ARM), new Item.Properties().stacksTo(16));

    public static final DeferredItem<HandLauncherItem> MANPADS_LAUNCHER = ITEMS.registerItem("manpads_launcher", p -> new HandLauncherItem(p, HandLock.MANPADS), new Item.Properties().stacksTo(1));
    public static final DeferredItem<HandLauncherItem> JAVELIN_LAUNCHER = ITEMS.registerItem("javelin_launcher", p -> new HandLauncherItem(p, HandLock.JAVELIN), new Item.Properties().stacksTo(1));
    public static final DeferredItem<LeafletItem> LEAFLET = ITEMS.registerItem("leaflet", LeafletItem::new, new Item.Properties().stacksTo(64));
    public static final DeferredItem<Item> MANPADS_ROUND = ITEMS.registerSimpleItem("manpads_round", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> JAVELIN_ROUND = ITEMS.registerSimpleItem("javelin_round", new Item.Properties().stacksTo(4));
    public static final DeferredItem<SeekerModuleItem> TV_MODULE = ITEMS.registerItem("tv_module", p -> new SeekerModuleItem(p, MissileBuild.SEEK_TV), new Item.Properties().stacksTo(16));

    public static final DeferredItem<SpaceItem> CAM_POD = ITEMS.registerItem("cam_pod", p -> new SpaceItem(p, "cam_pod"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<TargetDesignatorItem> TARGET_DESIGNATOR = ITEMS.registerItem("target_designator", TargetDesignatorItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredBlock<CcModuleBlocksB.LaunchConsole> CC_LAUNCH_CONSOLE = BLOCKS.registerBlock("cc_launch_console", CcModuleBlocksB.LaunchConsole::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksB.RetargetConsole> CC_RETARGET_CONSOLE = BLOCKS.registerBlock("cc_retarget_console", CcModuleBlocksB.RetargetConsole::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksB.StrikePlanner> CC_STRIKE_PLANNER = BLOCKS.registerBlock("cc_strike_planner", CcModuleBlocksB.StrikePlanner::new, ccMachine(4));
    public static final DeferredBlock<CcModuleBlocksB.MissileCam> CC_MISSILE_CAM = BLOCKS.registerBlock("cc_missile_cam", CcModuleBlocksB.MissileCam::new, ccMachine(5));
    public static final DeferredItem<BlockItem> CC_LAUNCH_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("cc_launch_console", CC_LAUNCH_CONSOLE);
    public static final DeferredItem<BlockItem> CC_RETARGET_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("cc_retarget_console", CC_RETARGET_CONSOLE);
    public static final DeferredItem<BlockItem> CC_STRIKE_PLANNER_ITEM = ITEMS.registerSimpleBlockItem("cc_strike_planner", CC_STRIKE_PLANNER);
    public static final DeferredItem<BlockItem> CC_MISSILE_CAM_ITEM = ITEMS.registerSimpleBlockItem("cc_missile_cam", CC_MISSILE_CAM);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcLaunchConsoleBlockEntity>> CC_LAUNCH_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("cc_launch_console", () -> BlockEntityType.Builder.of(CcLaunchConsoleBlockEntity::new, CC_LAUNCH_CONSOLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcRetargetConsoleBlockEntity>> CC_RETARGET_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("cc_retarget_console", () -> BlockEntityType.Builder.of(CcRetargetConsoleBlockEntity::new, CC_RETARGET_CONSOLE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcStrikePlannerBlockEntity>> CC_STRIKE_PLANNER_BE =
            BLOCK_ENTITY_TYPES.register("cc_strike_planner", () -> BlockEntityType.Builder.of(CcStrikePlannerBlockEntity::new, CC_STRIKE_PLANNER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcMissileCamBlockEntity>> CC_MISSILE_CAM_BE =
            BLOCK_ENTITY_TYPES.register("cc_missile_cam", () -> BlockEntityType.Builder.of(CcMissileCamBlockEntity::new, CC_MISSILE_CAM.get()).build(null));

    public static final DeferredItem<SpaceItem> LASER_DESIGNATOR_POD = ITEMS.registerItem("laser_designator_pod", p -> new SpaceItem(p, "laser_designator_pod"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<SpaceItem> KILL_VEHICLE_PACK = ITEMS.registerItem("kill_vehicle_pack", p -> new SpaceItem(p, "kill_vehicle_pack"), new Item.Properties().stacksTo(16));
    public static final DeferredBlock<CcModuleBlocksS5.InterceptorConsole> CC_INTERCEPTOR_CONSOLE = BLOCKS.registerBlock("cc_interceptor_console", CcModuleBlocksS5.InterceptorConsole::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_INTERCEPTOR_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("cc_interceptor_console", CC_INTERCEPTOR_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcInterceptorConsoleBlockEntity>> CC_INTERCEPTOR_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("cc_interceptor_console", () -> BlockEntityType.Builder.of(CcInterceptorConsoleBlockEntity::new, CC_INTERCEPTOR_CONSOLE.get()).build(null));

    public static final DeferredBlock<CcModuleBlocksS52.ArtilleryConsole> CC_ARTILLERY_CONSOLE = BLOCKS.registerBlock("cc_artillery_console", CcModuleBlocksS52.ArtilleryConsole::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_ARTILLERY_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("cc_artillery_console", CC_ARTILLERY_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcArtilleryConsoleBlockEntity>> CC_ARTILLERY_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("cc_artillery_console", () -> BlockEntityType.Builder.of(CcArtilleryConsoleBlockEntity::new, CC_ARTILLERY_CONSOLE.get()).build(null));

    public static final DeferredBlock<CcModuleBlocksS52.PlannerDesk> CC_PLANNER = BLOCKS.registerBlock("cc_planner", CcModuleBlocksS52.PlannerDesk::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_PLANNER_ITEM = ITEMS.registerSimpleBlockItem("cc_planner", CC_PLANNER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcPlannerBlockEntity>> CC_PLANNER_BE =
            BLOCK_ENTITY_TYPES.register("cc_planner", () -> BlockEntityType.Builder.of(CcPlannerBlockEntity::new, CC_PLANNER.get()).build(null));

    public static final DeferredBlock<CcModuleBlocksS9.SecurityMonitor> CC_SECURITY_MONITOR = BLOCKS.registerBlock("cc_security_monitor", CcModuleBlocksS9.SecurityMonitor::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_SECURITY_MONITOR_ITEM = ITEMS.registerSimpleBlockItem("cc_security_monitor", CC_SECURITY_MONITOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcSecurityMonitorBlockEntity>> CC_SECURITY_MONITOR_BE =
            BLOCK_ENTITY_TYPES.register("cc_security_monitor", () -> BlockEntityType.Builder.of(CcSecurityMonitorBlockEntity::new, CC_SECURITY_MONITOR.get()).build(null));
    public static final DeferredBlock<RobotDockBlock> ROBOT_DOCK = BLOCKS.registerBlock("robot_dock", RobotDockBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> ROBOT_DOCK_ITEM = ITEMS.registerSimpleBlockItem("robot_dock", ROBOT_DOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RobotDockBlockEntity>> ROBOT_DOCK_BE =
            BLOCK_ENTITY_TYPES.register("robot_dock", () -> BlockEntityType.Builder.of(RobotDockBlockEntity::new, ROBOT_DOCK.get()).build(null));
    public static final DeferredItem<MissileRobotItem> MISSILE_ROBOT_ITEM = ITEMS.registerItem("missile_robot", MissileRobotItem::new, new Item.Properties());

    public static final DeferredBlock<XlDockBlock> XL_DOCK = BLOCKS.registerBlock("xl_dock", XlDockBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> XL_DOCK_ITEM = ITEMS.registerSimpleBlockItem("xl_dock", XL_DOCK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XlDockBlockEntity>> XL_DOCK_BE =
            BLOCK_ENTITY_TYPES.register("xl_dock", () -> BlockEntityType.Builder.of(XlDockBlockEntity::new, XL_DOCK.get()).build(null));
    public static final DeferredItem<MissileRobotItem> XL_TRANSPORTER_ITEM = ITEMS.registerItem("xl_transporter", p -> new MissileRobotItem(p, MissileRobotEntity.KIND_XL), new Item.Properties());

    public static final DeferredItem<MissileRobotItem> PARTS_RUNNER_ITEM = ITEMS.registerItem("parts_runner", p -> new MissileRobotItem(p, MissileRobotEntity.KIND_RUNNER), new Item.Properties());

    public static final DeferredItem<Item> RUNNER_UPGRADE_KIT = ITEMS.registerSimpleItem("runner_upgrade_kit", new Item.Properties().stacksTo(16));
    public static final DeferredBlock<RunnerChargerBlock> RUNNER_CHARGER = BLOCKS.registerBlock("runner_charger", RunnerChargerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> RUNNER_CHARGER_ITEM = ITEMS.registerSimpleBlockItem("runner_charger", RUNNER_CHARGER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RunnerChargerBlockEntity>> RUNNER_CHARGER_BE =
            BLOCK_ENTITY_TYPES.register("runner_charger", () -> BlockEntityType.Builder.of(RunnerChargerBlockEntity::new, RUNNER_CHARGER.get()).build(null));

    public static final DeferredBlock<LaserTripodBlock> LASER_TRIPOD = BLOCKS.registerBlock("laser_tripod", LaserTripodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> LASER_TRIPOD_ITEM = ITEMS.registerSimpleBlockItem("laser_tripod", LASER_TRIPOD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserTripodBlockEntity>> LASER_TRIPOD_BE =
            BLOCK_ENTITY_TYPES.register("laser_tripod", () -> BlockEntityType.Builder.of(LaserTripodBlockEntity::new, LASER_TRIPOD.get()).build(null));
    public static final DeferredBlock<LaserReceiverBlock> LASER_RECEIVER = BLOCKS.registerBlock("laser_receiver", LaserReceiverBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> LASER_RECEIVER_ITEM = ITEMS.registerSimpleBlockItem("laser_receiver", LASER_RECEIVER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserReceiverBlockEntity>> LASER_RECEIVER_BE =
            BLOCK_ENTITY_TYPES.register("laser_receiver", () -> BlockEntityType.Builder.of(LaserReceiverBlockEntity::new, LASER_RECEIVER.get()).build(null));
    public static final DeferredItem<LaserTabletItem> LASER_TABLET = ITEMS.registerItem("laser_tablet", LaserTabletItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredBlock<ThermalNetBlock> THERMAL_NET = BLOCKS.registerBlock("thermal_net", ThermalNetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1.0F, 2.0F).sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> THERMAL_NET_ITEM = ITEMS.registerSimpleBlockItem("thermal_net", THERMAL_NET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThermalNetBlock.Entity>> THERMAL_NET_BE =
            BLOCK_ENTITY_TYPES.register("thermal_net", () -> BlockEntityType.Builder.of(ThermalNetBlock.Entity::new, THERMAL_NET.get()).build(null));
    public static final DeferredBlock<SmokeMachineBlock> SMOKE_MACHINE = BLOCKS.registerBlock("smoke_machine", SmokeMachineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> SMOKE_MACHINE_ITEM = ITEMS.registerSimpleBlockItem("smoke_machine", SMOKE_MACHINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmokeMachineBlockEntity>> SMOKE_MACHINE_BE =
            BLOCK_ENTITY_TYPES.register("smoke_machine", () -> BlockEntityType.Builder.of(SmokeMachineBlockEntity::new, SMOKE_MACHINE.get()).build(null));
    public static final DeferredItem<Item> SMOKE_CANISTER = ITEMS.registerSimpleItem("smoke_canister", new Item.Properties().stacksTo(16));

    public static final DeferredBlock<BombRackBlock> BOMB_RACK = BLOCKS.registerBlock("bomb_rack", BombRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> BOMB_RACK_ITEM = ITEMS.registerSimpleBlockItem("bomb_rack", BOMB_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BombRackBlockEntity>> BOMB_RACK_BE =
            BLOCK_ENTITY_TYPES.register("bomb_rack", () -> BlockEntityType.Builder.<BombRackBlockEntity>of((p, s) -> new BombRackBlockEntity(Wmd.BOMB_RACK_BE.get(), p, s, false), BOMB_RACK.get()).build(null));
    public static final DeferredBlock<BombBayBlock> BOMB_BAY = BLOCKS.registerBlock("bomb_bay", BombBayBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> BOMB_BAY_ITEM = ITEMS.registerSimpleBlockItem("bomb_bay", BOMB_BAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BombRackBlockEntity>> BOMB_BAY_BE =
            BLOCK_ENTITY_TYPES.register("bomb_bay", () -> BlockEntityType.Builder.<BombRackBlockEntity>of((p, s) -> new BombRackBlockEntity(Wmd.BOMB_BAY_BE.get(), p, s, true), BOMB_BAY.get()).build(null));
    public static final DeferredItem<BombItem> BOMB_IRON = ITEMS.register("bomb_iron", () -> new BombItem(BombType.IRON, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<BombItem> BOMB_LASER = ITEMS.register("bomb_laser", () -> new BombItem(BombType.LASER, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<BombItem> BOMB_GLIDE = ITEMS.register("bomb_glide", () -> new BombItem(BombType.GLIDE, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<BombItem> BOMB_CLUSTER = ITEMS.register("bomb_cluster", () -> new BombItem(BombType.CLUSTER, new Item.Properties().stacksTo(4)));

    public static final DeferredItem<BombItem> BOMB_DELAYED = ITEMS.register("bomb_delayed", () -> new BombItem(BombType.DELAYED, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<BombItem> BOMB_NAPALM = ITEMS.register("bomb_napalm", () -> new BombItem(BombType.NAPALM, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<BombItem> BOMB_PENETRATOR = ITEMS.register("bomb_penetrator", () -> new BombItem(BombType.PENETRATOR, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BombItem> BOMB_DAISY = ITEMS.register("bomb_daisy", () -> new BombItem(BombType.DAISY, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BombItem> BOMB_TALLBOY = ITEMS.register("bomb_tallboy", () -> new BombItem(BombType.TALLBOY, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BombItem> BOMB_TSAR = ITEMS.register("bomb_tsar", () -> new BombItem(BombType.TSAR, new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<BombItem> BOMB_BOUNCING = ITEMS.register("bomb_bouncing", () -> new BombItem(BombType.BOUNCING, new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<BombSightBlock> BOMB_SIGHT = BLOCKS.registerBlock("bomb_sight", BombSightBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> BOMB_SIGHT_ITEM = ITEMS.registerSimpleBlockItem("bomb_sight", BOMB_SIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BombSightBlockEntity>> BOMB_SIGHT_BE =
            BLOCK_ENTITY_TYPES.register("bomb_sight", () -> BlockEntityType.Builder.of(BombSightBlockEntity::new, BOMB_SIGHT.get()).build(null));
    public static final DeferredBlock<NapalmFireBlock> NAPALM_FIRE = BLOCKS.registerBlock("napalm_fire", NapalmFireBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).noCollission().instabreak().lightLevel(s -> 15).sound(SoundType.WOOL).noLootTable().pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<DroneModuleBlock.Core> DRONE_CORE = BLOCKS.registerBlock("drone_core", DroneModuleBlock.Core::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Frame> DRONE_FRAME = BLOCKS.registerBlock("drone_frame", DroneModuleBlock.Frame::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Avionics> DRONE_AVIONICS = BLOCKS.registerBlock("drone_avionics", DroneModuleBlock.Avionics::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Propulsion> DRONE_PROPULSION = BLOCKS.registerBlock("drone_propulsion", DroneModuleBlock.Propulsion::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Test> DRONE_TEST = BLOCKS.registerBlock("drone_test", DroneModuleBlock.Test::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Storage> DRONE_STORAGE = BLOCKS.registerBlock("drone_storage", DroneModuleBlock.Storage::new, labProps());
    public static final DeferredBlock<DroneModuleBlock.Hangar> DRONE_HANGAR = BLOCKS.registerBlock("drone_hangar", DroneModuleBlock.Hangar::new, labProps());
    public static final DeferredItem<DroneModuleItem> DRONE_CORE_ITEM = ITEMS.registerItem("drone_core", p -> new DroneModuleItem(DRONE_CORE.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_FRAME_ITEM = ITEMS.registerItem("drone_frame", p -> new DroneModuleItem(DRONE_FRAME.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_AVIONICS_ITEM = ITEMS.registerItem("drone_avionics", p -> new DroneModuleItem(DRONE_AVIONICS.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_PROPULSION_ITEM = ITEMS.registerItem("drone_propulsion", p -> new DroneModuleItem(DRONE_PROPULSION.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_TEST_ITEM = ITEMS.registerItem("drone_test", p -> new DroneModuleItem(DRONE_TEST.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_STORAGE_ITEM = ITEMS.registerItem("drone_storage", p -> new DroneModuleItem(DRONE_STORAGE.get(), p), new Item.Properties());
    public static final DeferredItem<DroneModuleItem> DRONE_HANGAR_ITEM = ITEMS.registerItem("drone_hangar", p -> new DroneModuleItem(DRONE_HANGAR.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneModuleBlockEntity>> DRONE_BE =
            BLOCK_ENTITY_TYPES.register("drone_module", () -> BlockEntityType.Builder.of(DroneModuleBlockEntity::new, DRONE_CORE.get(), DRONE_FRAME.get(), DRONE_AVIONICS.get(),
                    DRONE_PROPULSION.get(), DRONE_TEST.get(), DRONE_STORAGE.get(), DRONE_HANGAR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<DroneMenu>> DRONE_MENU = MENU_TYPES.register("drone_line",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new DroneMenu(id, inventory, buf)));
    public static final DeferredItem<DroneItem> DRONE_T1 = ITEMS.register("drone_t1", () -> new DroneItem(DroneType.T1, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<DroneItem> DRONE_T2 = ITEMS.register("drone_t2", () -> new DroneItem(DroneType.T2, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<DroneItem> DRONE_T3 = ITEMS.register("drone_t3", () -> new DroneItem(DroneType.T3, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DroneItem> DRONE_T4 = ITEMS.register("drone_t4", () -> new DroneItem(DroneType.T4, new Item.Properties().stacksTo(1)));

    public static final DeferredBlock<BombLineBlock.Core> BOMB_LINE_CORE = BLOCKS.registerBlock("bomb_line_core", BombLineBlock.Core::new, labProps());
    public static final DeferredBlock<BombLineBlock.Press> BOMB_LINE_PRESS = BLOCKS.registerBlock("bomb_line_press", BombLineBlock.Press::new, labProps());
    public static final DeferredBlock<BombLineBlock.Filling> BOMB_LINE_FILLING = BLOCKS.registerBlock("bomb_line_filling", BombLineBlock.Filling::new, labProps());
    public static final DeferredBlock<BombLineBlock.Fuze> BOMB_LINE_FUZE = BLOCKS.registerBlock("bomb_line_fuze", BombLineBlock.Fuze::new, labProps());
    public static final DeferredBlock<BombLineBlock.Storage> BOMB_LINE_STORAGE = BLOCKS.registerBlock("bomb_line_storage", BombLineBlock.Storage::new, labProps());
    public static final DeferredBlock<BombLineBlock.Cradle> BOMB_LINE_CRADLE = BLOCKS.registerBlock("bomb_line_cradle", BombLineBlock.Cradle::new, labProps());
    public static final DeferredItem<BombLineItem> BOMB_LINE_CORE_ITEM = ITEMS.registerItem("bomb_line_core", p -> new BombLineItem(BOMB_LINE_CORE.get(), p), new Item.Properties());
    public static final DeferredItem<BombLineItem> BOMB_LINE_PRESS_ITEM = ITEMS.registerItem("bomb_line_press", p -> new BombLineItem(BOMB_LINE_PRESS.get(), p), new Item.Properties());
    public static final DeferredItem<BombLineItem> BOMB_LINE_FILLING_ITEM = ITEMS.registerItem("bomb_line_filling", p -> new BombLineItem(BOMB_LINE_FILLING.get(), p), new Item.Properties());
    public static final DeferredItem<BombLineItem> BOMB_LINE_FUZE_ITEM = ITEMS.registerItem("bomb_line_fuze", p -> new BombLineItem(BOMB_LINE_FUZE.get(), p), new Item.Properties());
    public static final DeferredItem<BombLineItem> BOMB_LINE_STORAGE_ITEM = ITEMS.registerItem("bomb_line_storage", p -> new BombLineItem(BOMB_LINE_STORAGE.get(), p), new Item.Properties());
    public static final DeferredItem<BombLineItem> BOMB_LINE_CRADLE_ITEM = ITEMS.registerItem("bomb_line_cradle", p -> new BombLineItem(BOMB_LINE_CRADLE.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BombLineBlockEntity>> BOMB_LINE_BE =
            BLOCK_ENTITY_TYPES.register("bomb_line_module", () -> BlockEntityType.Builder.of(BombLineBlockEntity::new, BOMB_LINE_CORE.get(), BOMB_LINE_PRESS.get(), BOMB_LINE_FILLING.get(),
                    BOMB_LINE_FUZE.get(), BOMB_LINE_STORAGE.get(), BOMB_LINE_CRADLE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BombLineMenu>> BOMB_LINE_MENU = MENU_TYPES.register("bomb_line",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new BombLineMenu(id, inventory, buf)));

    public static final DeferredBlock<TorpedoLineBlock.Shop> TORPEDO_LINE_SHOP = BLOCKS.registerBlock("torpedo_line_shop", TorpedoLineBlock.Shop::new, labProps());
    public static final DeferredBlock<TorpedoLineBlock.Fitout> TORPEDO_LINE_FITOUT = BLOCKS.registerBlock("torpedo_line_fitout", TorpedoLineBlock.Fitout::new, labProps());
    public static final DeferredBlock<TorpedoLineBlock.Test> TORPEDO_LINE_TEST = BLOCKS.registerBlock("torpedo_line_test", TorpedoLineBlock.Test::new, labProps());
    public static final DeferredItem<TorpedoLineItem> TORPEDO_LINE_SHOP_ITEM = ITEMS.registerItem("torpedo_line_shop", p -> new TorpedoLineItem(TORPEDO_LINE_SHOP.get(), p), new Item.Properties());
    public static final DeferredItem<TorpedoLineItem> TORPEDO_LINE_FITOUT_ITEM = ITEMS.registerItem("torpedo_line_fitout", p -> new TorpedoLineItem(TORPEDO_LINE_FITOUT.get(), p), new Item.Properties());
    public static final DeferredItem<TorpedoLineItem> TORPEDO_LINE_TEST_ITEM = ITEMS.registerItem("torpedo_line_test", p -> new TorpedoLineItem(TORPEDO_LINE_TEST.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TorpedoLineBlockEntity>> TORPEDO_LINE_BE =
            BLOCK_ENTITY_TYPES.register("torpedo_line_module", () -> BlockEntityType.Builder.of(TorpedoLineBlockEntity::new, TORPEDO_LINE_SHOP.get(), TORPEDO_LINE_FITOUT.get(), TORPEDO_LINE_TEST.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<TorpedoLineMenu>> TORPEDO_LINE_MENU = MENU_TYPES.register("torpedo_line",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new TorpedoLineMenu(id, inventory, buf)));

    public static final DeferredBlock<DroneHiveBlock> DRONE_HIVE = BLOCKS.registerBlock("drone_hive", DroneHiveBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> DRONE_HIVE_ITEM = ITEMS.registerSimpleBlockItem("drone_hive", DRONE_HIVE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneHiveBlockEntity>> DRONE_HIVE_BE =
            BLOCK_ENTITY_TYPES.register("drone_hive", () -> BlockEntityType.Builder.of(DroneHiveBlockEntity::new, DRONE_HIVE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<DroneHiveMenu>> DRONE_HIVE_MENU = MENU_TYPES.register("drone_hive",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new DroneHiveMenu(id, inventory, buf)));

    public static final DeferredBlock<DroneCatapultBlock> DRONE_CATAPULT = BLOCKS.registerBlock("drone_catapult", DroneCatapultBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> DRONE_CATAPULT_ITEM = ITEMS.registerSimpleBlockItem("drone_catapult", DRONE_CATAPULT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroneCatapultBlockEntity>> DRONE_CATAPULT_BE =
            BLOCK_ENTITY_TYPES.register("drone_catapult", () -> BlockEntityType.Builder.of(DroneCatapultBlockEntity::new, DRONE_CATAPULT.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BigDroneMenu>> BIG_DRONE_MENU = MENU_TYPES.register("big_drone",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new BigDroneMenu(id, inventory, buf)));
    public static final DeferredBlock<RunwayBlocks.Tile> RUNWAY_TILE = BLOCKS.registerBlock("runway_tile", RunwayBlocks.Tile::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> RUNWAY_TILE_ITEM = ITEMS.registerSimpleBlockItem("runway_tile", RUNWAY_TILE);
    public static final DeferredBlock<RunwayBlocks.Line> RUNWAY_LINE = BLOCKS.registerBlock("runway_line", RunwayBlocks.Line::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.8F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> RUNWAY_LINE_ITEM = ITEMS.registerSimpleBlockItem("runway_line", RUNWAY_LINE);
    public static final DeferredBlock<RunwayBlocks.Light> RUNWAY_LIGHT = BLOCKS.registerBlock("runway_light", RunwayBlocks.Light::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F, 3.0F).sound(SoundType.METAL).noOcclusion().lightLevel(st -> 12));
    public static final DeferredItem<BlockItem> RUNWAY_LIGHT_ITEM = ITEMS.registerSimpleBlockItem("runway_light", RUNWAY_LIGHT);
    public static final DeferredBlock<MissileDepotBlock> MISSILE_DEPOT = BLOCKS.registerBlock("missile_depot", MissileDepotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 60.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> MISSILE_DEPOT_ITEM = ITEMS.registerSimpleBlockItem("missile_depot", MISSILE_DEPOT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileDepotBlockEntity>> MISSILE_DEPOT_BE =
            BLOCK_ENTITY_TYPES.register("missile_depot", () -> BlockEntityType.Builder.of(MissileDepotBlockEntity::new, MISSILE_DEPOT.get()).build(null));

    public static final DeferredBlock<MissileTeleporterBlock> MISSILE_TELEPORTER = BLOCKS.registerBlock("missile_teleporter", MissileTeleporterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 60.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> MISSILE_TELEPORTER_ITEM = ITEMS.registerSimpleBlockItem("missile_teleporter", MISSILE_TELEPORTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MissileTeleporterBlockEntity>> MISSILE_TELEPORTER_BE =
            BLOCK_ENTITY_TYPES.register("missile_teleporter", () -> BlockEntityType.Builder.of(MissileTeleporterBlockEntity::new, MISSILE_TELEPORTER.get()).build(null));
    public static final DeferredItem<LinkCardItem> LINK_CARD = ITEMS.registerItem("link_card", LinkCardItem::new, new Item.Properties());
    public static final DeferredBlock<CcModuleBlocksS52.IronDomeDesk> CC_IRON_DOME = BLOCKS.registerBlock("cc_iron_dome", CcModuleBlocksS52.IronDomeDesk::new, ccMachine(4));
    public static final DeferredItem<BlockItem> CC_IRON_DOME_ITEM = ITEMS.registerSimpleBlockItem("cc_iron_dome", CC_IRON_DOME);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CcIronDomeBlockEntity>> CC_IRON_DOME_BE =
            BLOCK_ENTITY_TYPES.register("cc_iron_dome", () -> BlockEntityType.Builder.of(CcIronDomeBlockEntity::new, CC_IRON_DOME.get()).build(null));
    public static final DeferredBlock<CounterBatteryRadarBlock> COUNTER_BATTERY_RADAR = BLOCKS.registerBlock("counter_battery_radar", CounterBatteryRadarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> COUNTER_BATTERY_RADAR_ITEM = ITEMS.registerSimpleBlockItem("counter_battery_radar", COUNTER_BATTERY_RADAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CounterBatteryRadarBlockEntity>> COUNTER_BATTERY_RADAR_BE =
            BLOCK_ENTITY_TYPES.register("counter_battery_radar", () -> BlockEntityType.Builder.of(CounterBatteryRadarBlockEntity::new, COUNTER_BATTERY_RADAR.get()).build(null));

    public static final DeferredBlock<SonarDomeBlock> SONAR_DOME = BLOCKS.registerBlock("sonar_dome", SonarDomeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> SONAR_DOME_ITEM = ITEMS.registerSimpleBlockItem("sonar_dome", SONAR_DOME);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SonarDomeBlockEntity>> SONAR_DOME_BE =
            BLOCK_ENTITY_TYPES.register("sonar_dome", () -> BlockEntityType.Builder.of(SonarDomeBlockEntity::new, SONAR_DOME.get()).build(null));
    public static final DeferredBlock<HydrophoneArrayBlock> HYDROPHONE_ARRAY = BLOCKS.registerBlock("hydrophone_array", HydrophoneArrayBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> HYDROPHONE_ARRAY_ITEM = ITEMS.registerSimpleBlockItem("hydrophone_array", HYDROPHONE_ARRAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrophoneArrayBlockEntity>> HYDROPHONE_ARRAY_BE =
            BLOCK_ENTITY_TYPES.register("hydrophone_array", () -> BlockEntityType.Builder.of(HydrophoneArrayBlockEntity::new, HYDROPHONE_ARRAY.get()).build(null));

    public static final DeferredBlock<TowedArrayWinchBlock> TOWED_ARRAY_WINCH = BLOCKS.registerBlock("towed_array_winch", TowedArrayWinchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> TOWED_ARRAY_WINCH_ITEM = ITEMS.registerSimpleBlockItem("towed_array_winch", TOWED_ARRAY_WINCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TowedArrayWinchBlockEntity>> TOWED_ARRAY_WINCH_BE =
            BLOCK_ENTITY_TYPES.register("towed_array_winch", () -> BlockEntityType.Builder.of(TowedArrayWinchBlockEntity::new, TOWED_ARRAY_WINCH.get()).build(null));
    public static final DeferredBlock<ListeningNodeBlock> LISTENING_NODE = BLOCKS.registerBlock("listening_node", ListeningNodeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> LISTENING_NODE_ITEM = ITEMS.registerSimpleBlockItem("listening_node", LISTENING_NODE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ListeningNodeBlockEntity>> LISTENING_NODE_BE =
            BLOCK_ENTITY_TYPES.register("listening_node", () -> BlockEntityType.Builder.of(ListeningNodeBlockEntity::new, LISTENING_NODE.get()).build(null));
    public static final DeferredBlock<CoastalBatteryBlock> COASTAL_BATTERY = BLOCKS.registerBlock("coastal_battery", CoastalBatteryBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> COASTAL_BATTERY_ITEM = ITEMS.registerSimpleBlockItem("coastal_battery", COASTAL_BATTERY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoastalBatteryBlockEntity>> COASTAL_BATTERY_BE =
            BLOCK_ENTITY_TYPES.register("coastal_battery", () -> BlockEntityType.Builder.of(CoastalBatteryBlockEntity::new, COASTAL_BATTERY.get()).build(null));
    public static final DeferredBlock<DockCraneBlock> DOCK_CRANE = BLOCKS.registerBlock("dock_crane", DockCraneBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> DOCK_CRANE_ITEM = ITEMS.registerSimpleBlockItem("dock_crane", DOCK_CRANE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DockCraneBlockEntity>> DOCK_CRANE_BE =
            BLOCK_ENTITY_TYPES.register("dock_crane", () -> BlockEntityType.Builder.of(DockCraneBlockEntity::new, DOCK_CRANE.get()).build(null));
    public static final DeferredBlock<SonarConsoleBlock> SONAR_CONSOLE = BLOCKS.registerBlock("sonar_console", SonarConsoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> SONAR_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("sonar_console", SONAR_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SonarConsoleBlockEntity>> SONAR_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("sonar_console", () -> BlockEntityType.Builder.of(SonarConsoleBlockEntity::new, SONAR_CONSOLE.get()).build(null));

    public static final DeferredBlock<TorpedoTubeBlock> TORPEDO_TUBE = BLOCKS.registerBlock("torpedo_tube", TorpedoTubeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> TORPEDO_TUBE_ITEM = ITEMS.registerSimpleBlockItem("torpedo_tube", TORPEDO_TUBE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TorpedoTubeBlockEntity>> TORPEDO_TUBE_BE =
            BLOCK_ENTITY_TYPES.register("torpedo_tube", () -> BlockEntityType.Builder.of(TorpedoTubeBlockEntity::new, TORPEDO_TUBE.get()).build(null));
    public static final DeferredBlock<TorpedoLauncherDeckBlock> TORPEDO_LAUNCHER_DECK = BLOCKS.registerBlock("torpedo_launcher_deck", TorpedoLauncherDeckBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> TORPEDO_LAUNCHER_DECK_ITEM = ITEMS.registerSimpleBlockItem("torpedo_launcher_deck", TORPEDO_LAUNCHER_DECK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TorpedoLauncherDeckBlockEntity>> TORPEDO_LAUNCHER_DECK_BE =
            BLOCK_ENTITY_TYPES.register("torpedo_launcher_deck", () -> BlockEntityType.Builder.of(TorpedoLauncherDeckBlockEntity::new, TORPEDO_LAUNCHER_DECK.get()).build(null));
    public static final DeferredBlock<TorpedoRackBlock> TORPEDO_RACK = BLOCKS.registerBlock("torpedo_rack", TorpedoRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> TORPEDO_RACK_ITEM = ITEMS.registerSimpleBlockItem("torpedo_rack", TORPEDO_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TorpedoRackBlockEntity>> TORPEDO_RACK_BE =
            BLOCK_ENTITY_TYPES.register("torpedo_rack", () -> BlockEntityType.Builder.of(TorpedoRackBlockEntity::new, TORPEDO_RACK.get()).build(null));

    public static final DeferredBlock<FireControlConsoleBlock> FIRE_CONTROL_CONSOLE = BLOCKS.registerBlock("fire_control_console", FireControlConsoleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> FIRE_CONTROL_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem("fire_control_console", FIRE_CONTROL_CONSOLE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FireControlConsoleBlockEntity>> FIRE_CONTROL_CONSOLE_BE =
            BLOCK_ENTITY_TYPES.register("fire_control_console", () -> BlockEntityType.Builder.of(FireControlConsoleBlockEntity::new, FIRE_CONTROL_CONSOLE.get()).build(null));
    public static final DeferredItem<TorpedoItem> TORPEDO_T1 = ITEMS.register("torpedo_t1", () -> new TorpedoItem(TorpedoKind.T1, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<TorpedoItem> TORPEDO_T2 = ITEMS.register("torpedo_t2", () -> new TorpedoItem(TorpedoKind.T2, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<TorpedoItem> TORPEDO_T3 = ITEMS.register("torpedo_t3", () -> new TorpedoItem(TorpedoKind.T3, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<TorpedoItem> TORPEDO_T4 = ITEMS.register("torpedo_t4", () -> new TorpedoItem(TorpedoKind.T4, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<TorpedoItem> UUV = ITEMS.register("uuv", () -> new TorpedoItem(TorpedoKind.UUV, new Item.Properties().stacksTo(4)));

    public static final DeferredBlock<NavalReactorBlock> NAVAL_REACTOR = BLOCKS.registerBlock("naval_reactor", NavalReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> NAVAL_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("naval_reactor", NAVAL_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavalReactorBlockEntity>> NAVAL_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("naval_reactor", () -> BlockEntityType.Builder.of(NavalReactorBlockEntity::new, NAVAL_REACTOR.get()).build(null));
    public static final DeferredBlock<PeriscopeBlock> PERISCOPE = BLOCKS.registerBlock("periscope", PeriscopeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> PERISCOPE_ITEM = ITEMS.registerSimpleBlockItem("periscope", PERISCOPE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PeriscopeBlockEntity>> PERISCOPE_BE =
            BLOCK_ENTITY_TYPES.register("periscope", () -> BlockEntityType.Builder.of(PeriscopeBlockEntity::new, PERISCOPE.get()).build(null));
    public static final DeferredBlock<CommsBuoyBlock> COMMS_BUOY_WINCH = BLOCKS.registerBlock("comms_buoy", CommsBuoyBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> COMMS_BUOY_WINCH_ITEM = ITEMS.registerSimpleBlockItem("comms_buoy", COMMS_BUOY_WINCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CommsBuoyBlockEntity>> COMMS_BUOY_BE =
            BLOCK_ENTITY_TYPES.register("comms_buoy", () -> BlockEntityType.Builder.of(CommsBuoyBlockEntity::new, COMMS_BUOY_WINCH.get()).build(null));
    public static final DeferredBlock<EscapePodBlock> ESCAPE_POD = BLOCKS.registerBlock("escape_pod", EscapePodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> ESCAPE_POD_ITEM = ITEMS.registerSimpleBlockItem("escape_pod", ESCAPE_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EscapePodBlockEntity>> ESCAPE_POD_BE =
            BLOCK_ENTITY_TYPES.register("escape_pod", () -> BlockEntityType.Builder.of(EscapePodBlockEntity::new, ESCAPE_POD.get()).build(null));
    public static final DeferredItem<TorpedoItem> TORPEDO_ATT = ITEMS.register("torpedo_att", () -> new TorpedoItem(TorpedoKind.ATT, new Item.Properties().stacksTo(4)));
    public static final DeferredItem<Item> ESCAPE_CAPSULE = ITEMS.register("escape_capsule", () -> new Item(new Item.Properties().stacksTo(4)));

    public static final DeferredBlock<ScrewPropellerBlock> SCREW_PROPELLER = BLOCKS.registerBlock("screw_propeller", ScrewPropellerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> SCREW_PROPELLER_ITEM = ITEMS.registerSimpleBlockItem("screw_propeller", SCREW_PROPELLER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScrewPropellerBlockEntity>> SCREW_PROPELLER_BE =
            BLOCK_ENTITY_TYPES.register("screw_propeller", () -> BlockEntityType.Builder.of(SableBridge::newScrew, SCREW_PROPELLER.get()).build(null));
    public static final DeferredBlock<EngineTelegraphBlock> ENGINE_TELEGRAPH = BLOCKS.registerBlock("engine_telegraph", EngineTelegraphBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> ENGINE_TELEGRAPH_ITEM = ITEMS.registerSimpleBlockItem("engine_telegraph", ENGINE_TELEGRAPH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineTelegraphBlockEntity>> ENGINE_TELEGRAPH_BE =
            BLOCK_ENTITY_TYPES.register("engine_telegraph", () -> BlockEntityType.Builder.of(EngineTelegraphBlockEntity::new, ENGINE_TELEGRAPH.get()).build(null));
    public static final DeferredBlock<HelmStationBlock> HELM_STATION = BLOCKS.registerBlock("helm_station", HelmStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> HELM_STATION_ITEM = ITEMS.registerSimpleBlockItem("helm_station", HELM_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HelmStationBlockEntity>> HELM_STATION_BE =
            BLOCK_ENTITY_TYPES.register("helm_station", () -> BlockEntityType.Builder.of(HelmStationBlockEntity::new, HELM_STATION.get()).build(null));

    public static final DeferredBlock<PumpJetBlock> PUMP_JET = BLOCKS.registerBlock("pump_jet", PumpJetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> PUMP_JET_ITEM = ITEMS.registerSimpleBlockItem("pump_jet", PUMP_JET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PumpJetBlockEntity>> PUMP_JET_BE =
            BLOCK_ENTITY_TYPES.register("pump_jet", () -> BlockEntityType.Builder.of(SableBridge::newPumpJet, PUMP_JET.get()).build(null));

    public static final DeferredBlock<DuctedFanBlock> DUCTED_FAN = BLOCKS.registerBlock("ducted_fan", DuctedFanBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<AirEngineItem> DUCTED_FAN_ITEM = ITEMS.registerItem("ducted_fan", p -> new AirEngineItem(DUCTED_FAN.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DuctedFanBlockEntity>> DUCTED_FAN_BE =
            BLOCK_ENTITY_TYPES.register("ducted_fan", () -> BlockEntityType.Builder.of(SableBridge::newDuctedFan, DUCTED_FAN.get()).build(null));
    public static final DeferredBlock<TurbofanBlock> TURBOFAN = BLOCKS.registerBlock("turbofan", TurbofanBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<AirEngineItem> TURBOFAN_ITEM = ITEMS.registerItem("turbofan", p -> new AirEngineItem(TURBOFAN.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbofanBlockEntity>> TURBOFAN_BE =
            BLOCK_ENTITY_TYPES.register("turbofan", () -> BlockEntityType.Builder.of(SableBridge::newTurbofan, TURBOFAN.get()).build(null));
    public static final DeferredBlock<JatoRackBlock> JATO_RACK = BLOCKS.registerBlock("jato_rack", JatoRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<AirEngineItem> JATO_RACK_ITEM = ITEMS.registerItem("jato_rack", p -> new AirEngineItem(JATO_RACK.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JatoRackBlockEntity>> JATO_RACK_BE =
            BLOCK_ENTITY_TYPES.register("jato_rack", () -> BlockEntityType.Builder.of(SableBridge::newJato, JATO_RACK.get()).build(null));

    public static final DeferredItem<Item> JATO_BOOSTER = ITEMS.registerSimpleItem("jato_booster", new Item.Properties().stacksTo(8));

    public static final DeferredItem<EngineRepairKitItem> ENGINE_REPAIR_KIT = ITEMS.registerItem("engine_repair_kit", EngineRepairKitItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredBlock<SubDieselBlock> SUB_DIESEL = BLOCKS.registerBlock("sub_diesel", SubDieselBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> SUB_DIESEL_ITEM = ITEMS.registerSimpleBlockItem("sub_diesel", SUB_DIESEL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SubDieselBlockEntity>> SUB_DIESEL_BE =
            BLOCK_ENTITY_TYPES.register("sub_diesel", () -> BlockEntityType.Builder.of(SubDieselBlockEntity::new, SUB_DIESEL.get()).build(null));
    public static final DeferredBlock<SnorkelMastBlock> SNORKEL_MAST = BLOCKS.registerBlock("snorkel_mast", SnorkelMastBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> SNORKEL_MAST_ITEM = ITEMS.registerSimpleBlockItem("snorkel_mast", SNORKEL_MAST);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SnorkelMastBlockEntity>> SNORKEL_MAST_BE =
            BLOCK_ENTITY_TYPES.register("snorkel_mast", () -> BlockEntityType.Builder.of(SnorkelMastBlockEntity::new, SNORKEL_MAST.get()).build(null));

    public static final DeferredItem<DepthChargeItem> DEPTH_CHARGE = ITEMS.register("depth_charge", () -> new DepthChargeItem(new Item.Properties().stacksTo(8)));
    public static final DeferredBlock<DepthChargeRackBlock> DEPTH_CHARGE_RACK = BLOCKS.registerBlock("depth_charge_rack", DepthChargeRackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> DEPTH_CHARGE_RACK_ITEM = ITEMS.registerSimpleBlockItem("depth_charge_rack", DEPTH_CHARGE_RACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepthChargeRackBlockEntity>> DEPTH_CHARGE_RACK_BE =
            BLOCK_ENTITY_TYPES.register("depth_charge_rack", () -> BlockEntityType.Builder.of(DepthChargeRackBlockEntity::new, DEPTH_CHARGE_RACK.get()).build(null));

    public static final DeferredBlock<NoduleFieldBlock> NODULE_FIELD = BLOCKS.registerBlock("nodule_field", NoduleFieldBlock::new, NoduleFieldBlock.props());
    public static final DeferredItem<BlockItem> NODULE_FIELD_ITEM = ITEMS.register("nodule_field", () -> new NoduleFieldBlock.BlockItemTip(NODULE_FIELD.get(), new Item.Properties()));
    public static final DeferredItem<NoduleItem> MANGANESE_NODULE = ITEMS.register("manganese_nodule", () -> new NoduleItem(new Item.Properties(), "manganese_nodule"));
    public static final DeferredItem<NoduleItem> MANGANESE_CONCENTRATE = ITEMS.register("manganese_concentrate", () -> new NoduleItem(new Item.Properties(), "manganese_concentrate"));
    public static final DeferredItem<NoduleItem> NICKEL_COBALT_CONCENTRATE = ITEMS.register("nickel_cobalt_concentrate", () -> new NoduleItem(new Item.Properties(), "nickel_cobalt_concentrate"));
    public static final DeferredItem<NoduleItem> MANGANESE_INGOT = ITEMS.register("manganese_ingot", () -> new NoduleItem(new Item.Properties(), "manganese_ingot"));
    public static final DeferredItem<NoduleItem> NICKEL_COBALT_INGOT = ITEMS.register("nickel_cobalt_ingot", () -> new NoduleItem(new Item.Properties(), "nickel_cobalt_ingot"));

    public static final DeferredBlock<DeepHull.Plate> MANGANESE_HULL_PLATE = BLOCKS.registerBlock("manganese_hull_plate", p -> new DeepHull.Plate(p, 0),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 9.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredBlock<DeepHull.Plate> SUPERALLOY_HULL = BLOCKS.registerBlock("superalloy_hull", p -> new DeepHull.Plate(p, 1),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(10.0F, 15.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredBlock<DeepHull.Viewport> DEEP_VIEWPORT = BLOCKS.registerBlock("deep_viewport", DeepHull.Viewport::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 9.0F).sound(SoundType.GLASS).noOcclusion().requiresCorrectToolForDrops()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final DeferredItem<BlockItem> MANGANESE_HULL_PLATE_ITEM = ITEMS.registerSimpleBlockItem("manganese_hull_plate", MANGANESE_HULL_PLATE);
    public static final DeferredItem<BlockItem> SUPERALLOY_HULL_ITEM = ITEMS.registerSimpleBlockItem("superalloy_hull", SUPERALLOY_HULL);
    public static final DeferredItem<BlockItem> DEEP_VIEWPORT_ITEM = ITEMS.registerSimpleBlockItem("deep_viewport", DEEP_VIEWPORT);

    public static final DeferredBlock<Anechoic.Tile> ANECHOIC_TILE = BLOCKS.registerBlock("anechoic_tile", Anechoic.Tile::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 4.0F).sound(SoundType.WOOL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> ANECHOIC_TILE_ITEM = ITEMS.registerSimpleBlockItem("anechoic_tile", ANECHOIC_TILE);

    public static final DeferredItem<UpgradeItem> DEEP_SONAR_UPGRADE = ITEMS.registerItem("deep_sonar_upgrade", p -> new UpgradeItem(UpgradeItem.Type.DEEP_SONAR, p), new Item.Properties());

    public static final DeferredBlock<TorpedoNetBlock> TORPEDO_NET = BLOCKS.registerBlock("torpedo_net", TorpedoNetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredItem<TorpedoNetItem> TORPEDO_NET_ITEM = ITEMS.registerItem("torpedo_net", TorpedoNetItem::new, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TorpedoNetBlockEntity>> TORPEDO_NET_BE =
            BLOCK_ENTITY_TYPES.register("torpedo_net", () -> BlockEntityType.Builder.of(TorpedoNetBlockEntity::new, TORPEDO_NET.get()).build(null));
    public static final DeferredItem<NoduleItem> SEABED_CRAWLER = ITEMS.register("seabed_crawler", () -> new NoduleItem(new Item.Properties().stacksTo(1), "seabed_crawler"));
    public static final DeferredBlock<DredgeWinchBlock> DREDGE_WINCH = BLOCKS.registerBlock("dredge_winch", DredgeWinchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> DREDGE_WINCH_ITEM = ITEMS.registerSimpleBlockItem("dredge_winch", DREDGE_WINCH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DredgeWinchBlockEntity>> DREDGE_WINCH_BE =
            BLOCK_ENTITY_TYPES.register("dredge_winch", () -> BlockEntityType.Builder.of(DredgeWinchBlockEntity::new, DREDGE_WINCH.get()).build(null));
    public static final DeferredBlock<CollectorRigBlock> COLLECTOR_RIG = BLOCKS.registerBlock("collector_rig", CollectorRigBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> COLLECTOR_RIG_ITEM = ITEMS.registerSimpleBlockItem("collector_rig", COLLECTOR_RIG);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CollectorRigBlockEntity>> COLLECTOR_RIG_BE =
            BLOCK_ENTITY_TYPES.register("collector_rig", () -> BlockEntityType.Builder.of(CollectorRigBlockEntity::new, COLLECTOR_RIG.get()).build(null));

    public static final DeferredBlock<DepthChargeThrowerBlock> DEPTH_CHARGE_THROWER = BLOCKS.registerBlock("depth_charge_thrower", DepthChargeThrowerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> DEPTH_CHARGE_THROWER_ITEM = ITEMS.registerSimpleBlockItem("depth_charge_thrower", DEPTH_CHARGE_THROWER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepthChargeThrowerBlockEntity>> DEPTH_CHARGE_THROWER_BE =
            BLOCK_ENTITY_TYPES.register("depth_charge_thrower", () -> BlockEntityType.Builder.of(DepthChargeThrowerBlockEntity::new, DEPTH_CHARGE_THROWER.get()).build(null));

    public static final DeferredBlock<NoisemakerLauncherBlock> NOISEMAKER_LAUNCHER = BLOCKS.registerBlock("noisemaker_launcher", NoisemakerLauncherBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> NOISEMAKER_LAUNCHER_ITEM = ITEMS.registerSimpleBlockItem("noisemaker_launcher", NOISEMAKER_LAUNCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NoisemakerLauncherBlockEntity>> NOISEMAKER_LAUNCHER_BE =
            BLOCK_ENTITY_TYPES.register("noisemaker_launcher", () -> BlockEntityType.Builder.of(NoisemakerLauncherBlockEntity::new, NOISEMAKER_LAUNCHER.get()).build(null));
    public static final DeferredBlock<SeaMineBlock.Influence> MINE_INFLUENCE = BLOCKS.registerBlock("mine_influence", SeaMineBlock.Influence::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> MINE_INFLUENCE_ITEM = ITEMS.register("mine_influence", () -> new SeaMineItem(MINE_INFLUENCE.get(), new Item.Properties()));
    public static final DeferredBlock<SeaMineBlock.Bottom> MINE_BOTTOM = BLOCKS.registerBlock("mine_bottom", SeaMineBlock.Bottom::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> MINE_BOTTOM_ITEM = ITEMS.register("mine_bottom", () -> new SeaMineItem(MINE_BOTTOM.get(), new Item.Properties()));
    public static final DeferredBlock<SeaMineBlock.Captor> MINE_CAPTOR = BLOCKS.registerBlock("mine_captor", SeaMineBlock.Captor::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> MINE_CAPTOR_ITEM = ITEMS.register("mine_captor", () -> new SeaMineItem(MINE_CAPTOR.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeaMineBlockEntity>> SEA_MINE_BE = BLOCK_ENTITY_TYPES.register("sea_mine",
            () -> BlockEntityType.Builder.of(SeaMineBlockEntity::new, MINE_INFLUENCE.get(), MINE_BOTTOM.get(), MINE_CAPTOR.get()).build(null));

    public static final DeferredItem<AsrocItem> ASROC = ITEMS.register("asroc", () -> new AsrocItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<SlbmItem> SLBM = ITEMS.register("slbm", () -> new SlbmItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CruiseCapsuleItem> CRUISE_CAPSULE = ITEMS.register("cruise_capsule", () -> new CruiseCapsuleItem(new Item.Properties().stacksTo(4)));
    public static final DeferredBlock<LaunchCellBlock> LAUNCH_CELL = BLOCKS.registerBlock("launch_cell", LaunchCellBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> LAUNCH_CELL_ITEM = ITEMS.registerSimpleBlockItem("launch_cell", LAUNCH_CELL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaunchCellBlockEntity>> LAUNCH_CELL_BE =
            BLOCK_ENTITY_TYPES.register("launch_cell", () -> BlockEntityType.Builder.of(LaunchCellBlockEntity::new, LAUNCH_CELL.get()).build(null));
    public static final DeferredItem<SonobuoyItem> SONOBUOY = ITEMS.register("sonobuoy", () -> new SonobuoyItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<NoisemakerItem> NOISEMAKER = ITEMS.register("noisemaker", () -> new NoisemakerItem(new Item.Properties().stacksTo(16)));

    public static final DeferredBlock<MlrsBlock> MLRS = BLOCKS.registerBlock("mlrs", MlrsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> MLRS_ITEM = ITEMS.registerSimpleBlockItem("mlrs", MLRS);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MlrsBlockEntity>> MLRS_BE =
            BLOCK_ENTITY_TYPES.register("mlrs", () -> BlockEntityType.Builder.of(MlrsBlockEntity::new, MLRS.get()).build(null));
    public static final DeferredBlock<CamoNetBlock> CAMO_NET = BLOCKS.registerBlock("camo_net", CamoNetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(1.0F, 2.0F).sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CAMO_NET_ITEM = ITEMS.registerSimpleBlockItem("camo_net", CAMO_NET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CamoNetBlockEntity>> CAMO_NET_BE =
            BLOCK_ENTITY_TYPES.register("camo_net", () -> BlockEntityType.Builder.of(CamoNetBlockEntity::new, CAMO_NET.get()).build(null));
    public static final DeferredBlock<DecoyBlock.Launcher> DECOY_LAUNCHER = BLOCKS.registerBlock("decoy_launcher", DecoyBlock.Launcher::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.8F, 1.0F).sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DECOY_LAUNCHER_ITEM = ITEMS.registerSimpleBlockItem("decoy_launcher", DECOY_LAUNCHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecoyBlockEntity.Launcher>> DECOY_LAUNCHER_BE =
            BLOCK_ENTITY_TYPES.register("decoy_launcher", () -> BlockEntityType.Builder.of(DecoyBlockEntity.Launcher::new, DECOY_LAUNCHER.get()).build(null));
    public static final DeferredBlock<DecoyBlock.Radar> DECOY_RADAR = BLOCKS.registerBlock("decoy_radar", DecoyBlock.Radar::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.8F, 1.0F).sound(SoundType.WOOL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DECOY_RADAR_ITEM = ITEMS.registerSimpleBlockItem("decoy_radar", DECOY_RADAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecoyBlockEntity.Radar>> DECOY_RADAR_BE =
            BLOCK_ENTITY_TYPES.register("decoy_radar", () -> BlockEntityType.Builder.of(DecoyBlockEntity.Radar::new, DECOY_RADAR.get()).build(null));
    public static final DeferredItem<MlrsPodItem> MLRS_POD = ITEMS.registerItem("mlrs_pod", p -> new MlrsPodItem(p, Artillery.HE), new Item.Properties().stacksTo(4));
    public static final DeferredItem<MlrsPodItem> MLRS_POD_GUIDED = ITEMS.registerItem("mlrs_pod_guided", p -> new MlrsPodItem(p, Artillery.GUIDED), new Item.Properties().stacksTo(4));
    public static final DeferredItem<ArtShellItem> SHELL_MORTAR_GUIDED = ITEMS.registerItem("shell_mortar_guided", p -> new ArtShellItem(p, Artillery.MORTAR, Artillery.GUIDED), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ArtShellItem> SHELL_HOWITZER_GUIDED = ITEMS.registerItem("shell_howitzer_guided", p -> new ArtShellItem(p, Artillery.HOWITZER, Artillery.GUIDED), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_BLANK =ITEMS.registerItem("chip_blank", p -> new ChipItem(p, null), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_METALLURGY_2 = ITEMS.registerItem("chip_metallurgy_2", p -> new ChipItem(p, "metallurgy_2"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_MINING_1 = ITEMS.registerItem("chip_mining_1", p -> new ChipItem(p, "mining_1"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_POWER_2 = ITEMS.registerItem("chip_power_2", p -> new ChipItem(p, "power_2"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_ROCKETRY_2 = ITEMS.registerItem("chip_rocketry_2", p -> new ChipItem(p, "rocketry_2"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_DEFENCE_2 = ITEMS.registerItem("chip_defence_2", p -> new ChipItem(p, "defence_2"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_WARHEADS_2 = ITEMS.registerItem("chip_warheads_2", p -> new ChipItem(p, "warheads_2"), new Item.Properties().stacksTo(16));

    public static final DeferredItem<ChipItem> CHIP_METALLURGY_3 = ITEMS.registerItem("chip_metallurgy_3", p -> new ChipItem(p, "metallurgy_3"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_NUCLEAR_1 = ITEMS.registerItem("chip_nuclear_1", p -> new ChipItem(p, "nuclear_1"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_FUSION_1 = ITEMS.registerItem("chip_fusion_1", p -> new ChipItem(p, "fusion_1"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_ANTIMATTER_1 = ITEMS.registerItem("chip_antimatter_1", p -> new ChipItem(p, "antimatter_1"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_RAILGUN_1 = ITEMS.registerItem("chip_railgun_1", p -> new ChipItem(p, "railgun_1"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_SPACE_1 = ITEMS.registerItem("chip_space_1", p -> new ChipItem(p, "space_1"), new Item.Properties().stacksTo(16));

    public static final DeferredItem<FieldManualItem> FIELD_MANUAL = ITEMS.registerItem("field_manual", FieldManualItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredBlock<ChlorAlkaliBlock> CHLOR_ALKALI = BLOCKS.registerBlock("chlor_alkali_cell", ChlorAlkaliBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> CHLOR_ALKALI_ITEM = ITEMS.registerSimpleBlockItem("chlor_alkali_cell", CHLOR_ALKALI);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChlorAlkaliBlockEntity>> CHLOR_ALKALI_BE =
            BLOCK_ENTITY_TYPES.register("chlor_alkali_cell", () -> BlockEntityType.Builder.of(ChlorAlkaliBlockEntity::new, CHLOR_ALKALI.get()).build(null));
    public static final DeferredBlock<ChlorinatorBlock> CHLORINATOR = BLOCKS.registerBlock("chlorinator", ChlorinatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> CHLORINATOR_ITEM = ITEMS.registerSimpleBlockItem("chlorinator", CHLORINATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChlorinatorBlockEntity>> CHLORINATOR_BE =
            BLOCK_ENTITY_TYPES.register("chlorinator", () -> BlockEntityType.Builder.of(ChlorinatorBlockEntity::new, CHLORINATOR.get()).build(null));
    public static final DeferredBlock<KrollReactorBlock> KROLL_REACTOR = BLOCKS.registerBlock("kroll_reactor", KrollReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> KROLL_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("kroll_reactor", KROLL_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KrollReactorBlockEntity>> KROLL_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("kroll_reactor", () -> BlockEntityType.Builder.of(KrollReactorBlockEntity::new, KROLL_REACTOR.get()).build(null));

    public static final DeferredBlock<MiningRigBlock> MINING_RIG = BLOCKS.registerBlock("mining_rig", MiningRigBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 14.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> MINING_RIG_ITEM = ITEMS.registerSimpleBlockItem("mining_rig", MINING_RIG);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MiningRigBlockEntity>> MINING_RIG_BE =
            BLOCK_ENTITY_TYPES.register("mining_rig", () -> BlockEntityType.Builder.of(MiningRigBlockEntity::new, MINING_RIG.get()).build(null));
    public static final DeferredBlock<DesalterBlock> DESALTER = BLOCKS.registerBlock("desalter", DesalterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> DESALTER_ITEM = ITEMS.registerSimpleBlockItem("desalter", DESALTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DesalterBlockEntity>> DESALTER_BE =
            BLOCK_ENTITY_TYPES.register("desalter", () -> BlockEntityType.Builder.of(DesalterBlockEntity::new, DESALTER.get()).build(null));
    public static final DeferredBlock<FurnaceBlock> FURNACE = BLOCKS.registerBlock("furnace", FurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(13)));
    public static final DeferredItem<BlockItem> FURNACE_ITEM = ITEMS.registerSimpleBlockItem("furnace", FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnaceBlockEntity>> FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("furnace", () -> BlockEntityType.Builder.of(FurnaceBlockEntity::new, FURNACE.get()).build(null));

    public static final DeferredBlock<Block> CREATIVE_BATTERY = BLOCKS.registerSimpleBlock("creative_battery",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 8));
    public static final DeferredItem<BlockItem> CREATIVE_BATTERY_ITEM = ITEMS.registerSimpleBlockItem("creative_battery", CREATIVE_BATTERY);

    public static final DeferredBlock<CreativeFluidSourceBlock> CREATIVE_FLUID_SOURCE = BLOCKS.registerBlock("creative_fluid_source", CreativeFluidSourceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 8));
    public static final DeferredItem<BlockItem> CREATIVE_FLUID_SOURCE_ITEM = ITEMS.registerSimpleBlockItem("creative_fluid_source", CREATIVE_FLUID_SOURCE);

    public static final DeferredBlock<StorageTankBlock> STORAGE_TANK = BLOCKS.registerBlock("storage_tank", StorageTankBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> STORAGE_TANK_ITEM = ITEMS.registerSimpleBlockItem("storage_tank", STORAGE_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageTankBlockEntity>> STORAGE_TANK_BE =
            BLOCK_ENTITY_TYPES.register("storage_tank", () -> BlockEntityType.Builder.of(StorageTankBlockEntity::new, STORAGE_TANK.get()).build(null));

    static { aliasCrudeTank(); }
    static void aliasCrudeTank() {
        net.minecraft.resources.ResourceLocation from = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "crude_tank"),
                to = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "storage_tank");
        BLOCKS.addAlias(from, to);
        ITEMS.addAlias(from, to);
        BLOCK_ENTITY_TYPES.addAlias(from, to);
    }

    public static final DeferredBlock<HydrotreaterBlock> HYDROTREATER = BLOCKS.registerBlock("hydrotreater", HydrotreaterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> HYDROTREATER_ITEM = ITEMS.registerSimpleBlockItem("hydrotreater", HYDROTREATER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HydrotreaterBlockEntity>> HYDROTREATER_BE =
            BLOCK_ENTITY_TYPES.register("hydrotreater", () -> BlockEntityType.Builder.of(HydrotreaterBlockEntity::new, HYDROTREATER.get()).build(null));

    public static final DeferredBlock<FccBlock> FCC = BLOCKS.registerBlock("fcc", FccBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(12)));
    public static final DeferredItem<BlockItem> FCC_ITEM = ITEMS.registerSimpleBlockItem("fcc", FCC);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FccBlockEntity>> FCC_BE =
            BLOCK_ENTITY_TYPES.register("fcc", () -> BlockEntityType.Builder.of(FccBlockEntity::new, FCC.get()).build(null));

    public static final DeferredBlock<ReformerBlock> REFORMER = BLOCKS.registerBlock("reformer", ReformerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> REFORMER_ITEM = ITEMS.registerSimpleBlockItem("reformer", REFORMER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReformerBlockEntity>> REFORMER_BE =
            BLOCK_ENTITY_TYPES.register("reformer", () -> BlockEntityType.Builder.of(ReformerBlockEntity::new, REFORMER.get()).build(null));

    public static final DeferredBlock<VacuumColumnBlock> VACUUM_COLUMN = BLOCKS.registerBlock("vacuum_column", VacuumColumnBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(10)));
    public static final DeferredItem<BlockItem> VACUUM_COLUMN_ITEM = ITEMS.registerSimpleBlockItem("vacuum_column", VACUUM_COLUMN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VacuumColumnBlockEntity>> VACUUM_COLUMN_BE =
            BLOCK_ENTITY_TYPES.register("vacuum_column", () -> BlockEntityType.Builder.of(VacuumColumnBlockEntity::new, VACUUM_COLUMN.get()).build(null));

    public static final DeferredBlock<AtmosphericColumnBlock> ATMOSPHERIC_COLUMN = BLOCKS.registerBlock("atmospheric_column", AtmosphericColumnBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(10)));
    public static final DeferredItem<BlockItem> ATMOSPHERIC_COLUMN_ITEM = ITEMS.registerSimpleBlockItem("atmospheric_column", ATMOSPHERIC_COLUMN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtmosphericColumnBlockEntity>> ATMOSPHERIC_COLUMN_BE =
            BLOCK_ENTITY_TYPES.register("atmospheric_column", () -> BlockEntityType.Builder.of(AtmosphericColumnBlockEntity::new, ATMOSPHERIC_COLUMN.get()).build(null));

    public static final DeferredBlock<CoalGeneratorBlock> COAL_GENERATOR = BLOCKS.registerBlock("coal_generator", CoalGeneratorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> COAL_GENERATOR_ITEM = ITEMS.registerSimpleBlockItem("coal_generator", COAL_GENERATOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoalGeneratorBlockEntity>> COAL_GENERATOR_BE =
            BLOCK_ENTITY_TYPES.register("coal_generator", () -> BlockEntityType.Builder.of(CoalGeneratorBlockEntity::new, COAL_GENERATOR.get()).build(null));

    private static BlockBehaviour.Properties bayProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion();
    }
    public static final DeferredBlock<BayModuleBlock.Core> BAY_CORE = BLOCKS.registerBlock("bay_core", BayModuleBlock.Core::new, bayProps());
    public static final DeferredBlock<BayModuleBlock.Weld> BAY_WELD = BLOCKS.registerBlock("bay_weld", BayModuleBlock.Weld::new, bayProps());
    public static final DeferredBlock<BayModuleBlock.Crane> BAY_CRANE = BLOCKS.registerBlock("bay_crane", BayModuleBlock.Crane::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_CORE_ITEM = ITEMS.registerItem("bay_core", p -> new BayModuleItem(BAY_CORE.get(), p), new Item.Properties());
    public static final DeferredItem<BayModuleItem> BAY_WELD_ITEM = ITEMS.registerItem("bay_weld", p -> new BayModuleItem(BAY_WELD.get(), p), new Item.Properties());
    public static final DeferredItem<BayModuleItem> BAY_CRANE_ITEM = ITEMS.registerItem("bay_crane", p -> new BayModuleItem(BAY_CRANE.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_CORE_BE =
            BLOCK_ENTITY_TYPES.register("bay_core", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_CORE_BE.get(), pos, st, BayKind.CORE), BAY_CORE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_WELD_BE =
            BLOCK_ENTITY_TYPES.register("bay_weld", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_WELD_BE.get(), pos, st, BayKind.WELD), BAY_WELD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_CRANE_BE =
            BLOCK_ENTITY_TYPES.register("bay_crane", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_CRANE_BE.get(), pos, st, BayKind.CRANE), BAY_CRANE.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Warhead> BAY_WARHEAD = BLOCKS.registerBlock("bay_warhead", BayModuleBlock.Warhead::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_WARHEAD_ITEM = ITEMS.registerItem("bay_warhead", p -> new BayModuleItem(BAY_WARHEAD.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_WARHEAD_BE =
            BLOCK_ENTITY_TYPES.register("bay_warhead", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_WARHEAD_BE.get(), pos, st, BayKind.WARHEAD), BAY_WARHEAD.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Clean> BAY_CLEAN = BLOCKS.registerBlock("bay_clean", BayModuleBlock.Clean::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_CLEAN_ITEM = ITEMS.registerItem("bay_clean", p -> new BayModuleItem(BAY_CLEAN.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_CLEAN_BE =
            BLOCK_ENTITY_TYPES.register("bay_clean", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_CLEAN_BE.get(), pos, st, BayKind.CLEAN), BAY_CLEAN.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Paint> BAY_PAINT = BLOCKS.registerBlock("bay_paint", BayModuleBlock.Paint::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_PAINT_ITEM = ITEMS.registerItem("bay_paint", p -> new BayModuleItem(BAY_PAINT.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_PAINT_BE =
            BLOCK_ENTITY_TYPES.register("bay_paint", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_PAINT_BE.get(), pos, st, BayKind.PAINT), BAY_PAINT.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Extend> BAY_EXTEND = BLOCKS.registerBlock("bay_extend", BayModuleBlock.Extend::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_EXTEND_ITEM = ITEMS.registerItem("bay_extend", p -> new BayModuleItem(BAY_EXTEND.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_EXTEND_BE =
            BLOCK_ENTITY_TYPES.register("bay_extend", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_EXTEND_BE.get(), pos, st, BayKind.EXTEND), BAY_EXTEND.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Rack> BAY_RACK = BLOCKS.registerBlock("bay_rack", BayModuleBlock.Rack::new, bayProps());

    public static final DeferredBlock<BayModuleBlock.RackR> BAY_RACK_R = BLOCKS.registerBlock("bay_rack_r", BayModuleBlock.RackR::new, bayProps());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_RACK_R_BE =
            BLOCK_ENTITY_TYPES.register("bay_rack_r", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_RACK_R_BE.get(), pos, st, BayKind.RACK_R), BAY_RACK_R.get()).build(null));
    public static final DeferredItem<BayModuleItem> BAY_RACK_ITEM = ITEMS.registerItem("bay_rack", p -> new BayModuleItem(BAY_RACK.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_RACK_BE =
            BLOCK_ENTITY_TYPES.register("bay_rack", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_RACK_BE.get(), pos, st, BayKind.RACK), BAY_RACK.get()).build(null));
    public static final DeferredBlock<BayModuleBlock.Cryo> BAY_CRYO = BLOCKS.registerBlock("bay_cryo", BayModuleBlock.Cryo::new, bayProps());
    public static final DeferredItem<BayModuleItem> BAY_CRYO_ITEM = ITEMS.registerItem("bay_cryo", p -> new BayModuleItem(BAY_CRYO.get(), p), new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BayModuleBlockEntity>> BAY_CRYO_BE =
            BLOCK_ENTITY_TYPES.register("bay_cryo", () -> BlockEntityType.Builder.of((pos, st) -> new BayModuleBlockEntity(Wmd.BAY_CRYO_BE.get(), pos, st, BayKind.CRYO), BAY_CRYO.get()).build(null));
    public static final DeferredItem<MissilePartItem> AIRFRAME_T2 = ITEMS.registerItem("airframe_t2", p -> new MissilePartItem(p, 0, 2), new Item.Properties());
    public static final DeferredItem<MissilePartItem> AIRFRAME_T3 = ITEMS.registerItem("airframe_t3", p -> new MissilePartItem(p, 0, 3), new Item.Properties());
    public static final DeferredItem<MissilePartItem> AIRFRAME_T4 = ITEMS.registerItem("airframe_t4", p -> new MissilePartItem(p, 0, 4), new Item.Properties());
    public static final DeferredItem<MissilePartItem> MOTOR_T2 = ITEMS.registerItem("motor_t2", p -> new MissilePartItem(p, 1, 2), new Item.Properties());
    public static final DeferredItem<MissilePartItem> MOTOR_T3 = ITEMS.registerItem("motor_t3", p -> new MissilePartItem(p, 1, 3), new Item.Properties());
    public static final DeferredItem<MissilePartItem> MOTOR_T4 = ITEMS.registerItem("motor_t4", p -> new MissilePartItem(p, 1, 4), new Item.Properties());
    public static final DeferredItem<MissilePartItem> GUIDANCE_T2 = ITEMS.registerItem("guidance_t2", p -> new MissilePartItem(p, 2, 2), new Item.Properties());
    public static final DeferredItem<MissilePartItem> GUIDANCE_T3 = ITEMS.registerItem("guidance_t3", p -> new MissilePartItem(p, 2, 3), new Item.Properties());
    public static final DeferredItem<MissilePartItem> GUIDANCE_T4 = ITEMS.registerItem("guidance_t4", p -> new MissilePartItem(p, 2, 4), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_INCENDIARY = ITEMS.registerItem("warhead_incendiary", p -> new MissilePartItem(p, 3, 1, "incendiary"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_INTERCEPTOR = ITEMS.registerItem("warhead_interceptor", p -> new MissilePartItem(p, 3, 1, "interceptor"), new Item.Properties());
    public static final DeferredItem<InterceptorItem> INTERCEPTOR_T2 = ITEMS.registerItem("interceptor_t2", p -> new InterceptorItem(p, 2), new Item.Properties());
    public static final DeferredItem<InterceptorItem> INTERCEPTOR_T3 = ITEMS.registerItem("interceptor_t3", p -> new InterceptorItem(p, 3), new Item.Properties());
    public static final DeferredItem<InterceptorItem> INTERCEPTOR_T4 = ITEMS.registerItem("interceptor_t4", p -> new InterceptorItem(p, 4), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_FRAG = ITEMS.registerItem("warhead_frag", p -> new MissilePartItem(p, 3, 1, "frag"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_DECOY = ITEMS.registerItem("warhead_decoy", p -> new MissilePartItem(p, 3, 1, "decoy"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_THERMOBARIC = ITEMS.registerItem("warhead_thermobaric", p -> new MissilePartItem(p, 3, 2, "thermobaric"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_CLUSTER = ITEMS.registerItem("warhead_cluster", p -> new MissilePartItem(p, 3, 2, "cluster"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_BREACHER = ITEMS.registerItem("warhead_breacher", p -> new MissilePartItem(p, 3, 2, "breacher"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_CHAFF = ITEMS.registerItem("warhead_chaff", p -> new MissilePartItem(p, 3, 2, "chaff"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_BUNKER = ITEMS.registerItem("warhead_bunker", p -> new MissilePartItem(p, 3, 3, "bunker"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_EMP = ITEMS.registerItem("warhead_emp", p -> new MissilePartItem(p, 3, 3, "emp"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> MIRV_BUS = ITEMS.registerItem("mirv_bus", p -> (MissilePartItem) new MirvBusItem(p), new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<MissilePartItem> WARHEAD_THERMO = ITEMS.registerItem("warhead_thermo", p -> new MissilePartItem(p, 3, 4, "thermo"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_ROD = ITEMS.registerItem("warhead_rod", p -> new MissilePartItem(p, 3, 3, "rod"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_HEMP = ITEMS.registerItem("warhead_hemp", p -> new MissilePartItem(p, 3, 4, "hemp"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_SEISMIC = ITEMS.registerItem("warhead_seismic", p -> new MissilePartItem(p, 3, 4, "seismic"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_NUKE = ITEMS.registerItem("warhead_nuke", p -> new MissilePartItem(p, 3, 3, "nuke"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_NEUTRON = ITEMS.registerItem("warhead_neutron", p -> new MissilePartItem(p, 3, 3, "neutron"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_ANTIMATTER = ITEMS.registerItem("warhead_antimatter", p -> new MissilePartItem(p, 3, 4, "antimatter"), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<MissilePartItem> WARHEAD_FLASH = ITEMS.registerItem("warhead_flash", p -> new MissilePartItem(p, 3, 2, "flash"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_CONCUSSION = ITEMS.registerItem("warhead_concussion", p -> new MissilePartItem(p, 3, 2, "concussion"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_CHEMICAL = ITEMS.registerItem("warhead_chemical", p -> new MissilePartItem(p, 3, 2, "chemical"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_NANITE = ITEMS.registerItem("warhead_nanite", p -> new MissilePartItem(p, 3, 3, "nanite"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_GRAVITY = ITEMS.registerItem("warhead_gravity", p -> new MissilePartItem(p, 3, 3, "gravity"), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_CRYO = ITEMS.registerItem("warhead_cryo", p -> new MissilePartItem(p, 3, 3, "cryo"), new Item.Properties());
    public static final DeferredItem<GasMaskItem> GAS_MASK = ITEMS.registerItem("gas_mask", p -> new GasMaskItem(GasMaskItem.MATERIAL, p), new Item.Properties());

    public static final DeferredItem<EvaSuitItem> EVA_HELMET = ITEMS.registerItem("eva_helmet", p -> new EvaSuitItem(EvaSuitItem.MATERIAL, net.minecraft.world.item.ArmorItem.Type.HELMET, p), new Item.Properties());
    public static final DeferredItem<EvaSuitItem> EVA_TORSO = ITEMS.registerItem("eva_torso", p -> new EvaSuitItem(EvaSuitItem.MATERIAL, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, p), new Item.Properties());
    public static final DeferredItem<EvaSuitItem> EVA_LEGS = ITEMS.registerItem("eva_legs", p -> new EvaSuitItem(EvaSuitItem.MATERIAL, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, p), new Item.Properties());
    public static final DeferredItem<EvaSuitItem> EVA_BOOTS = ITEMS.registerItem("eva_boots", p -> new EvaSuitItem(EvaSuitItem.MATERIAL, net.minecraft.world.item.ArmorItem.Type.BOOTS, p), new Item.Properties());
    public static final DeferredItem<OxygenTankItem> OXYGEN_TANK = ITEMS.registerItem("oxygen_tank", OxygenTankItem::new, new Item.Properties());

    public static final DeferredBlock<OxygenStationBlock> OXYGEN_STATION = BLOCKS.registerBlock("oxygen_station", OxygenStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(6)));
    public static final DeferredItem<BlockItem> OXYGEN_STATION_ITEM = ITEMS.registerSimpleBlockItem("oxygen_station", OXYGEN_STATION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OxygenStationBlockEntity>> OXYGEN_STATION_BE =
            BLOCK_ENTITY_TYPES.register("oxygen_station", () -> BlockEntityType.Builder.of(OxygenStationBlockEntity::new, OXYGEN_STATION.get()).build(null));
    public static final DeferredItem<ChipItem> CHIP_SPACE_2 = ITEMS.registerItem("chip_space_2", p -> new ChipItem(p, "space_2"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<ChipItem> CHIP_SPACE_3 = ITEMS.registerItem("chip_space_3", p -> new ChipItem(p, "space_3"), new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SOLAR_CELL = ITEMS.registerSimpleItem("solar_cell", new Item.Properties());

    public static final DeferredItem<CrewCapsuleItem> CREW_CAPSULE = ITEMS.registerItem("crew_capsule", CrewCapsuleItem::new, new Item.Properties());
    public static final DeferredItem<Item> USED_CREW_CAPSULE = ITEMS.registerItem("used_crew_capsule", Item::new, new Item.Properties().stacksTo(1));
    public static final DeferredBlock<CrewCapsuleBlock> CREW_CAPSULE_BLOCK = BLOCKS.registerBlock("crew_capsule_hull", CrewCapsuleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrewCapsuleBlockEntity>> CREW_CAPSULE_BE =
            BLOCK_ENTITY_TYPES.register("crew_capsule_hull", () -> BlockEntityType.Builder.of(CrewCapsuleBlockEntity::new, CREW_CAPSULE_BLOCK.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<TurretSeatEntity>> TURRET_SEAT = ENTITY_TYPES.register("turret_seat",
            () -> EntityType.Builder.<TurretSeatEntity>of(TurretSeatEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).build("turret_seat"));
    public static final DeferredHolder<EntityType<?>, EntityType<HelmSeatEntity>> HELM_SEAT = ENTITY_TYPES.register("helm_seat",
            () -> EntityType.Builder.<HelmSeatEntity>of(HelmSeatEntity::new, MobCategory.MISC).sized(0.5F, 0.3F).clientTrackingRange(10).build("helm_seat"));
    public static final DeferredHolder<EntityType<?>, EntityType<JatoBottleEntity>> JATO_BOTTLE = ENTITY_TYPES.register("jato_bottle",
            () -> EntityType.Builder.<JatoBottleEntity>of(JatoBottleEntity::new, MobCategory.MISC).sized(0.7F, 0.7F).clientTrackingRange(10).updateInterval(2).build("jato_bottle"));
    public static final DeferredHolder<EntityType<?>, EntityType<CrewSeatEntity>> CREW_SEAT = ENTITY_TYPES.register("crew_seat",
            () -> EntityType.Builder.<CrewSeatEntity>of(CrewSeatEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).build("crew_seat"));

    public static final DeferredBlock<Block> STATION_HULL = BLOCKS.registerSimpleBlock("station_hull", stationHull());
    public static final DeferredBlock<Block> STATION_HULL_SEAM = BLOCKS.registerSimpleBlock("station_hull_seam", stationHull());
    public static final DeferredBlock<Block> STATION_BAND = BLOCKS.registerSimpleBlock("station_band", stationHull());
    public static final DeferredBlock<Block> STATION_DECK = BLOCKS.registerSimpleBlock("station_deck", stationHull());
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> STATION_WINDOW = BLOCKS.registerBlock("station_window", net.minecraft.world.level.block.TransparentBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(50.0F, 1200.0F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<StationHullBlock.Light> STATION_LIGHT = BLOCKS.registerBlock("station_light", StationHullBlock.Light::new,
            stationHull().lightLevel(st -> st.getValue(StationHullBlock.Light.NIGHT) ? 7 : 15));
    public static final DeferredBlock<StationHullBlock.Frame> STATION_HATCH_FRAME = BLOCKS.registerBlock("station_hatch_frame", StationHullBlock.Frame::new, stationHull());
    public static final DeferredItem<BlockItem> STATION_HULL_ITEM = ITEMS.registerSimpleBlockItem("station_hull", STATION_HULL);
    public static final DeferredItem<BlockItem> STATION_HULL_SEAM_ITEM = ITEMS.registerSimpleBlockItem("station_hull_seam", STATION_HULL_SEAM);
    public static final DeferredItem<BlockItem> STATION_BAND_ITEM = ITEMS.registerSimpleBlockItem("station_band", STATION_BAND);
    public static final DeferredItem<BlockItem> STATION_DECK_ITEM = ITEMS.registerSimpleBlockItem("station_deck", STATION_DECK);
    public static final DeferredItem<BlockItem> STATION_WINDOW_ITEM = ITEMS.registerSimpleBlockItem("station_window", STATION_WINDOW);
    public static final DeferredItem<BlockItem> STATION_LIGHT_ITEM = ITEMS.registerSimpleBlockItem("station_light", STATION_LIGHT);
    public static final DeferredItem<BlockItem> STATION_HATCH_FRAME_ITEM = ITEMS.registerSimpleBlockItem("station_hatch_frame", STATION_HATCH_FRAME);
    public static final DeferredBlock<StationModuleBlock> STATION_MODULE = BLOCKS.registerBlock("station_module", StationModuleBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationModuleBlockEntity>> STATION_MODULE_BE =
            BLOCK_ENTITY_TYPES.register("station_module", () -> BlockEntityType.Builder.of(StationModuleBlockEntity::new, STATION_MODULE.get()).build(null));
    public static final DeferredBlock<Block> STATION_SOLID = BLOCKS.registerSimpleBlock("station_solid",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false));

    public static final DeferredBlock<StationHullBlock.Pole> STATION_POLE_LADDER = BLOCKS.registerBlock("station_pole_ladder", StationHullBlock.Pole::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false));
    public static final DeferredBlock<Turret.Hatch> STATION_TURRET_HATCH = BLOCKS.registerBlock("station_turret_hatch", Turret.Hatch::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredBlock<StationHullBlock.Ladder> STATION_LADDER = BLOCKS.registerBlock("station_ladder", StationHullBlock.Ladder::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false));

    public static final DeferredBlock<StationSealBlock> STATION_SEAL = BLOCKS.registerBlock("station_seal", StationSealBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3_600_000.0F).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable()
                    .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false));

    public static final DeferredItem<StationKitItem> STATION_KIT_CORE = ITEMS.registerItem("station_kit_core", p -> new StationKitItem(p, Station.CORE), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_HABITAT = ITEMS.registerItem("station_kit_habitat", p -> new StationKitItem(p, Station.HABITAT), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_WING = ITEMS.registerItem("station_kit_wing", p -> new StationKitItem(p, Station.WING), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_SENSOR = ITEMS.registerItem("station_kit_sensor", p -> new StationKitItem(p, Station.SENSOR), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_LASER = ITEMS.registerItem("station_kit_laser", p -> new StationKitItem(p, Station.LASER), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_ROD = ITEMS.registerItem("station_kit_rod", p -> new StationKitItem(p, Station.ROD), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<StationKitItem> STATION_KIT_MIRROR = ITEMS.registerItem("station_kit_mirror", p -> new StationKitItem(p, Station.MIRROR), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_TURRET = ITEMS.registerItem("station_kit_turret", p -> new StationKitItem(p, Station.TURRET), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));
    public static final DeferredItem<StationKitItem> STATION_KIT_BAY = ITEMS.registerItem("station_kit_bay", p -> new StationKitItem(p, Station.BAY), new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<Item> DT_FUEL_CANISTER = ITEMS.registerSimpleItem("dt_fuel_canister", new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<Item> TUG_CORE = ITEMS.registerSimpleItem("tug_core", new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredItem<Item> ORBITAL_FRAME = ITEMS.registerSimpleItem("orbital_frame", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> LIFE_SUPPORT_UNIT = ITEMS.registerSimpleItem("life_support_unit", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> POWER_CORE = ITEMS.registerSimpleItem("power_core", new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<Item> WEAPON_CORE = ITEMS.registerSimpleItem("weapon_core", new Item.Properties().stacksTo(16).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<Item> ORBITAL_ROD = ITEMS.registerSimpleItem("orbital_rod", new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.EPIC));

    public static final DeferredBlock<StationFixtureBlock.Console> STATION_CONSOLE = BLOCKS.registerBlock("station_console", StationFixtureBlock.Console::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.Bunk> STATION_BUNK = BLOCKS.registerBlock("station_bunk", StationFixtureBlock.Bunk::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.Rack> STATION_OXYGEN_RACK = BLOCKS.registerBlock("station_oxygen_rack", StationFixtureBlock.Rack::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.RodRack> STATION_ROD_RACK = BLOCKS.registerBlock("station_rod_rack", StationFixtureBlock.RodRack::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.RodConsole> STATION_ROD_CONSOLE = BLOCKS.registerBlock("station_rod_console", StationFixtureBlock.RodConsole::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.MirrorDesk> STATION_MIRROR_DESK = BLOCKS.registerBlock("station_mirror_desk", StationFixtureBlock.MirrorDesk::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.BayConsole> STATION_BAY_CONSOLE = BLOCKS.registerBlock("station_bay_console", StationFixtureBlock.BayConsole::new, stationFixture());
    public static final DeferredBlock<StationFixtureBlock.Battery> STATION_BATTERY = BLOCKS.registerBlock("station_battery", StationFixtureBlock.Battery::new, stationFixture());
    public static final DeferredItem<StationBatteryItem> STATION_BATTERY_ITEM = ITEMS.registerItem("station_battery_rack", StationBatteryItem::new, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationFixtureBlockEntity>> STATION_FIXTURE_BE = BLOCK_ENTITY_TYPES.register("station_fixture",
            () -> BlockEntityType.Builder.of(StationFixtureBlockEntity::new, STATION_CONSOLE.get(), STATION_BUNK.get(), STATION_OXYGEN_RACK.get(), STATION_ROD_RACK.get(), STATION_ROD_CONSOLE.get(), STATION_MIRROR_DESK.get(), STATION_BAY_CONSOLE.get(), STATION_BATTERY.get()).build(null));

    public static final DeferredBlock<net.minecraft.world.level.block.DoorBlock> STATION_HATCH = BLOCKS.registerBlock("station_hatch",
            p -> new net.minecraft.world.level.block.DoorBlock(net.minecraft.world.level.block.state.properties.BlockSetType.COPPER, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(50.0F, 1200.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY).requiresCorrectToolForDrops());
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> STATION_HATCH_ITEM = ITEMS.registerItem("station_hatch",
            p -> new net.minecraft.world.item.DoubleHighBlockItem(STATION_HATCH.get(), p), new Item.Properties());
    public static final DeferredItem<StationRepairKitItem> STATION_REPAIR_KIT = ITEMS.registerItem("station_repair_kit", StationRepairKitItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredItem<CargoPodItem> CARGO_POD = ITEMS.registerItem("cargo_pod", CargoPodItem::new, new Item.Properties());
    public static final DeferredItem<Item> STATION_FUEL_CELL = ITEMS.registerSimpleItem("station_fuel_cell", new Item.Properties().stacksTo(16));
    public static final DeferredBlock<CargoPodBlock> CARGO_POD_BLOCK = BLOCKS.registerBlock("cargo_pod_hull", CargoPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoPodBlockEntity>> CARGO_POD_BE =
            BLOCK_ENTITY_TYPES.register("cargo_pod_hull", () -> BlockEntityType.Builder.of(CargoPodBlockEntity::new, CARGO_POD_BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<CargoPodMenu>> CARGO_POD_MENU = MENU_TYPES.register("cargo_pod",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new CargoPodMenu(id, inventory, buf)));

    private static BlockBehaviour.Properties stationFixture() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noLootTable();
    }

    private static BlockBehaviour.Properties stationHull() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(50.0F, 1200.0F).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final DeferredItem<MissilePartItem> WARHEAD_ASAT = ITEMS.registerItem("warhead_asat", p -> new MissilePartItem(p, 3, 4, "asat"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> WARHEAD_CUBESAT = ITEMS.registerItem("warhead_cubesat", p -> new MissilePartItem(p, 3, 3, "cubesat"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> MOTOR_DIVERT = ITEMS.registerItem("motor_divert", p -> new MissilePartItem(p, 1, 3, "divert"), new Item.Properties());

    public static final DeferredItem<MissilePartItem> MOTOR_HYPERSONIC = ITEMS.registerItem("motor_hypersonic", p -> new MissilePartItem(p, 1, 3, "hypersonic"), new Item.Properties());

    public static java.util.List<DeferredItem<MissilePartItem>> partItems() {
        return java.util.List.of(AIRFRAME_T1, AIRFRAME_T2, AIRFRAME_T3, AIRFRAME_T4, MOTOR_T1, MOTOR_T2, MOTOR_T3, MOTOR_T4, MOTOR_DIVERT, MOTOR_HYPERSONIC,
                GUIDANCE_T1, GUIDANCE_T2, GUIDANCE_T3, GUIDANCE_T4, WARHEAD_HE_T1, WARHEAD_INTERCEPTOR, WARHEAD_INCENDIARY, WARHEAD_FRAG, WARHEAD_DECOY, WARHEAD_THERMOBARIC, WARHEAD_CLUSTER, WARHEAD_BREACHER, WARHEAD_CHAFF, WARHEAD_BUNKER, WARHEAD_EMP, WARHEAD_NUKE, WARHEAD_THERMO, WARHEAD_ROD, WARHEAD_HEMP, WARHEAD_SEISMIC, WARHEAD_ASAT, WARHEAD_CUBESAT,
                WARHEAD_NEUTRON, WARHEAD_FLASH, WARHEAD_CONCUSSION, WARHEAD_CHEMICAL,
                WARHEAD_ANTIMATTER, MIRV_BUS, WARHEAD_NANITE, WARHEAD_GRAVITY, WARHEAD_CRYO);
    }
    public static final DeferredHolder<MenuType<?>, MenuType<BayMenu>> BAY_MENU = MENU_TYPES.register("bay",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new BayMenu(id, inventory, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<RackMenu>> RACK_MENU = MENU_TYPES.register("rack",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new RackMenu(id, inventory, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<DepotMenu>> DEPOT_MENU = MENU_TYPES.register("missile_depot",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new DepotMenu(id, inventory, buf)));
    public static final DeferredHolder<MenuType<?>, MenuType<TeleporterMenu>> TELEPORTER_MENU = MENU_TYPES.register("missile_teleporter",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> new TeleporterMenu(id, inventory, buf)));
    public static final DeferredItem<MissilePartItem> AIRFRAME_T1 = ITEMS.registerItem("airframe_t1", p -> new MissilePartItem(p, 0, 1), new Item.Properties());
    public static final DeferredItem<MissilePartItem> MOTOR_T1 = ITEMS.registerItem("motor_t1", p -> new MissilePartItem(p, 1, 1), new Item.Properties());
    public static final DeferredItem<MissilePartItem> GUIDANCE_T1 = ITEMS.registerItem("guidance_t1", p -> new MissilePartItem(p, 2, 1), new Item.Properties());
    public static final DeferredItem<MissilePartItem> WARHEAD_HE_T1 = ITEMS.registerItem("warhead_he_t1", p -> new MissilePartItem(p, 3, 1), new Item.Properties());

    public static final DeferredBlock<FlareStackBlock> FLARE_STACK = BLOCKS.registerBlock("flare_stack", FlareStackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(13)));
    public static final DeferredItem<BlockItem> FLARE_STACK_ITEM = ITEMS.registerSimpleBlockItem("flare_stack", FLARE_STACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlareStackBlockEntity>> FLARE_STACK_BE =
            BLOCK_ENTITY_TYPES.register("flare_stack", () -> BlockEntityType.Builder.of(FlareStackBlockEntity::new, FLARE_STACK.get()).build(null));
    public static final DeferredBlock<ElectrolyserBlock> ELECTROLYSER = BLOCKS.registerBlock("electrolyser", ElectrolyserBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> ELECTROLYSER_ITEM = ITEMS.registerSimpleBlockItem("electrolyser", ELECTROLYSER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ElectrolyserBlockEntity>> ELECTROLYSER_BE =
            BLOCK_ENTITY_TYPES.register("electrolyser", () -> BlockEntityType.Builder.of(ElectrolyserBlockEntity::new, ELECTROLYSER.get()).build(null));

    public static final DeferredBlock<CrusherBlock> CRUSHER = BLOCKS.registerBlock("crusher", CrusherBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(7)));
    public static final DeferredItem<BlockItem> CRUSHER_ITEM = ITEMS.registerSimpleBlockItem("crusher", CRUSHER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrusherBlockEntity>> CRUSHER_BE =
            BLOCK_ENTITY_TYPES.register("crusher", () -> BlockEntityType.Builder.of(CrusherBlockEntity::new, CRUSHER.get()).build(null));
    public static final DeferredBlock<ArcFurnaceBlock> ARC_FURNACE = BLOCKS.registerBlock("arc_furnace", ArcFurnaceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(13)));
    public static final DeferredItem<BlockItem> ARC_FURNACE_ITEM = ITEMS.registerSimpleBlockItem("arc_furnace", ARC_FURNACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcFurnaceBlockEntity>> ARC_FURNACE_BE =
            BLOCK_ENTITY_TYPES.register("arc_furnace", () -> BlockEntityType.Builder.of(ArcFurnaceBlockEntity::new, ARC_FURNACE.get()).build(null));
    public static final DeferredBlock<NitrationReactorBlock> NITRATION_REACTOR = BLOCKS.registerBlock("nitration_reactor", NitrationReactorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(9)));
    public static final DeferredItem<BlockItem> NITRATION_REACTOR_ITEM = ITEMS.registerSimpleBlockItem("nitration_reactor", NITRATION_REACTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NitrationReactorBlockEntity>> NITRATION_REACTOR_BE =
            BLOCK_ENTITY_TYPES.register("nitration_reactor", () -> BlockEntityType.Builder.of(NitrationReactorBlockEntity::new, NITRATION_REACTOR.get()).build(null));
    public static final DeferredBlock<CokerBlock> COKER = BLOCKS.registerBlock("coker", CokerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(11)));
    public static final DeferredItem<BlockItem> COKER_ITEM = ITEMS.registerSimpleBlockItem("coker", COKER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CokerBlockEntity>> COKER_BE =
            BLOCK_ENTITY_TYPES.register("coker", () -> BlockEntityType.Builder.of(CokerBlockEntity::new, COKER.get()).build(null));

    public static final DeferredItem<MissileItem> MISSILE = ITEMS.registerItem("missile", MissileItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE_ENTITY = ENTITY_TYPES.register("missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("missile"));

    public static final DeferredHolder<EntityType<?>, EntityType<MissileRobotEntity>> MISSILE_ROBOT = ENTITY_TYPES.register("missile_robot",
            () -> EntityType.Builder.<MissileRobotEntity>of(MissileRobotEntity::new, MobCategory.MISC)
                    .sized(2.9F, 2.6F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("missile_robot"));

    public static final DeferredHolder<EntityType<?>, EntityType<SentryDogEntity>> SENTRY_DOG = ENTITY_TYPES.register("sentry_dog",
            () -> EntityType.Builder.<SentryDogEntity>of(SentryDogEntity::new, MobCategory.MISC)
                    .sized(0.7F, 1.4F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("sentry_dog"));
    public static final DeferredItem<SentryDogItem> SENTRY_DOG_ITEM = ITEMS.registerItem("sentry_dog", SentryDogItem::new, new Item.Properties());

    public static final DeferredHolder<EntityType<?>, EntityType<PartsRunnerEntity>> PARTS_RUNNER = ENTITY_TYPES.register("parts_runner",
            () -> EntityType.Builder.<PartsRunnerEntity>of(PartsRunnerEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("parts_runner"));

    public static final DeferredHolder<EntityType<?>, EntityType<XlTransporterEntity>> XL_TRANSPORTER = ENTITY_TYPES.register("xl_transporter",
            () -> EntityType.Builder.<XlTransporterEntity>of(XlTransporterEntity::new, MobCategory.MISC)
                    .sized(4.9F, 3.6F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("xl_transporter"));

    public static final DeferredHolder<EntityType<?>, EntityType<HandMissileEntity>> HAND_MISSILE = ENTITY_TYPES.register("hand_missile",
            () -> EntityType.Builder.<HandMissileEntity>of(HandMissileEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(40)
                    .updateInterval(1)
                    .build("hand_missile"));

    public static final DeferredHolder<EntityType<?>, EntityType<NoisemakerEntity>> NOISEMAKER_ENTITY = ENTITY_TYPES.register("noisemaker",
            () -> EntityType.Builder.<NoisemakerEntity>of(NoisemakerEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .build("noisemaker"));

    public static final DeferredHolder<EntityType<?>, EntityType<DepthChargeEntity>> DEPTH_CHARGE_ENTITY = ENTITY_TYPES.register("depth_charge",
            () -> EntityType.Builder.<DepthChargeEntity>of(DepthChargeEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("depth_charge"));

    public static final DeferredHolder<EntityType<?>, EntityType<KGunArborEntity>> KGUN_ARBOR_ENTITY = ENTITY_TYPES.register("kgun_arbor",
            () -> EntityType.Builder.<KGunArborEntity>of(KGunArborEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("kgun_arbor"));

    public static final DeferredHolder<EntityType<?>, EntityType<DredgeBucketEntity>> DREDGE_BUCKET_ENTITY = ENTITY_TYPES.register("dredge_bucket",
            () -> EntityType.Builder.<DredgeBucketEntity>of(DredgeBucketEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("dredge_bucket"));
    public static final DeferredHolder<EntityType<?>, EntityType<SeabedCrawlerEntity>> SEABED_CRAWLER_ENTITY = ENTITY_TYPES.register("seabed_crawler",
            () -> EntityType.Builder.<SeabedCrawlerEntity>of(SeabedCrawlerEntity::new, MobCategory.MISC)
                    .sized(3.6F, 1.8F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("seabed_crawler"));

    public static final DeferredHolder<EntityType<?>, EntityType<RisingMineEntity>> RISING_MINE = ENTITY_TYPES.register("rising_mine",
            () -> EntityType.Builder.<RisingMineEntity>of(RisingMineEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("rising_mine"));

    public static final DeferredHolder<EntityType<?>, EntityType<LaidMineEntity>> LAID_MINE = ENTITY_TYPES.register("laid_mine",
            () -> EntityType.Builder.<LaidMineEntity>of(LaidMineEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("laid_mine"));

    public static final DeferredHolder<EntityType<?>, EntityType<SonobuoyEntity>> SONOBUOY_ENTITY = ENTITY_TYPES.register("sonobuoy",
            () -> EntityType.Builder.<SonobuoyEntity>of(SonobuoyEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.3F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("sonobuoy"));

    public static final DeferredHolder<EntityType<?>, EntityType<AsrocEntity>> ASROC_ENTITY = ENTITY_TYPES.register("asroc",
            () -> EntityType.Builder.<AsrocEntity>of(AsrocEntity::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .build("asroc"));

    public static final DeferredHolder<EntityType<?>, EntityType<SlbmEntity>> SLBM_ENTITY = ENTITY_TYPES.register("slbm",
            () -> EntityType.Builder.<SlbmEntity>of(SlbmEntity::new, MobCategory.MISC)
                    .sized(0.9F, 6.0F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build("slbm"));
    public static final DeferredHolder<EntityType<?>, EntityType<CapsuleEntity>> CAPSULE_ENTITY = ENTITY_TYPES.register("cruise_capsule",
            () -> EntityType.Builder.<CapsuleEntity>of(CapsuleEntity::new, MobCategory.MISC)
                    .sized(0.8F, 6.25F)
                    .clientTrackingRange(24)
                    .updateInterval(1)
                    .build("cruise_capsule"));

    public static final DeferredHolder<EntityType<?>, EntityType<CommsBuoyEntity>> COMMS_BUOY = ENTITY_TYPES.register("comms_buoy",
            () -> EntityType.Builder.<CommsBuoyEntity>of(CommsBuoyEntity::new, MobCategory.MISC)
                    .sized(1.2F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("comms_buoy"));
    public static final DeferredHolder<EntityType<?>, EntityType<EscapePodEntity>> ESCAPE_POD_ENTITY = ENTITY_TYPES.register("escape_pod",
            () -> EntityType.Builder.<EscapePodEntity>of(EscapePodEntity::new, MobCategory.MISC)
                    .sized(1.5F, 2.8F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("escape_pod"));
    public static final DeferredHolder<EntityType<?>, EntityType<TorpedoEntity>> TORPEDO = ENTITY_TYPES.register("torpedo",
            () -> EntityType.Builder.<TorpedoEntity>of(TorpedoEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("torpedo"));
    public static final DeferredHolder<EntityType<?>, EntityType<BombEntity>> BOMB = ENTITY_TYPES.register("bomb",
            () -> EntityType.Builder.<BombEntity>of(BombEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(40)
                    .updateInterval(1)
                    .build("bomb"));

    public static final DeferredHolder<EntityType<?>, EntityType<PilotBodyEntity>> PILOT_BODY = ENTITY_TYPES.register("pilot_body",
            () -> EntityType.Builder.<PilotBodyEntity>of(PilotBodyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .build("pilot_body"));
    public static final DeferredHolder<EntityType<?>, EntityType<StrikeDroneEntity>> STRIKE_DRONE_ENTITY = ENTITY_TYPES.register("strike_drone",
            () -> EntityType.Builder.<StrikeDroneEntity>of(StrikeDroneEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.45F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("strike_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<StrikeDroneEntity>> BIG_DRONE_ENTITY = ENTITY_TYPES.register("big_drone",
            () -> EntityType.Builder.<StrikeDroneEntity>of(StrikeDroneEntity::new, MobCategory.MISC)
                    .sized(4.0F, 1.2F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("big_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<ReconDroneEntity>> RECON_DRONE_ENTITY = ENTITY_TYPES.register("recon_drone",
            () -> EntityType.Builder.<ReconDroneEntity>of(ReconDroneEntity::new, MobCategory.MISC)
                    .sized(1.4F, 0.4F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("recon_drone"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB = CREATIVE_MODE_TABS.register("wmd_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "steel_ingot")).getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(CHIP_BLANK.get());
                output.accept(CHIP_METALLURGY_2.get());
                output.accept(CHIP_MINING_1.get());
                output.accept(CHIP_POWER_2.get());
                output.accept(CHIP_ROCKETRY_2.get());
                output.accept(CHIP_DEFENCE_2.get());
                output.accept(CHIP_WARHEADS_2.get());
                output.accept(CHIP_METALLURGY_3.get());
                output.accept(CHIP_NUCLEAR_1.get());
                output.accept(CHIP_SPACE_1.get());
                output.accept(CHIP_FUSION_1.get());
                output.accept(CHIP_ANTIMATTER_1.get());
                output.accept(CHIP_RAILGUN_1.get());
                output.accept(CHIP_SPACE_2.get());
                output.accept(CHIP_SPACE_3.get());
                WmdMaterials.addToTab(output);
            }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_INDUSTRY = CREATIVE_MODE_TABS.register("wmd_industry", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.industry"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_tab"))
            .icon(() -> COAL_GENERATOR_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(OIL_DETECTOR.get());
                output.accept(GROUND_SCANNER.get());
                output.accept(WRENCH.get());
                output.accept(DIESEL_GENERATOR_ITEM.get());
                output.accept(GENERATOR_ENGINE_ITEM.get());
                output.accept(MACHINE_CASING_ITEM.get());
                output.accept(POWER_PORT_ITEM.get());
                output.accept(BATTERY_T1_ITEM.get());
                output.accept(BATTERY_T2_ITEM.get());
                output.accept(BATTERY_T3_ITEM.get());
                output.accept(BATTERY_T4_ITEM.get());
                output.accept(BUSBAR_ITEM.get());
                output.accept(FLUID_PIPE_ITEM.get());
                output.accept(ALUMINIUM_PIPE_ITEM.get());
                output.accept(TITANIUM_PIPE_ITEM.get());
                output.accept(FLUID_VALVE_ITEM.get());
                output.accept(FLOODGATE_ITEM.get());
                output.accept(CANISTER.get());
                output.accept(ITEM_DUCT_ITEM.get());
                output.accept(SORTER_ITEM.get());
                output.accept(SUPPLY_INTERFACE_ITEM.get());
                output.accept(PIPE_CLEARER.get());
                output.accept(PUMPJACK_ITEM.get());
                output.accept(UPGRADE_HEAVY_WATER.get());
                output.accept(UPGRADE_SPEED.get());
                output.accept(UPGRADE_EFFICIENCY.get());
                output.accept(UPGRADE_THROUGHPUT.get());
                output.accept(UPGRADE_BUFFER.get());
                output.accept(UPGRADE_MUFFLER.get());
                output.accept(UPGRADE_AUTO_EJECT.get());
                output.accept(FEED_PUMP_ITEM.get());
                output.accept(WATER_INTAKE_ITEM.get());
                output.accept(MINING_RIG_ITEM.get());
                output.accept(CHLOR_ALKALI_ITEM.get());
                output.accept(MAGNESIUM_CELL_ITEM.get());
                output.accept(RESEARCH_STATION_ITEM.get());
                output.accept(FIELD_MANUAL.get());
                output.accept(CHLORINATOR_ITEM.get());
                output.accept(KROLL_REACTOR_ITEM.get());
                output.accept(GAS_TURBINE_ITEM.get());
                output.accept(GRID_CONVERTER_ITEM.get());
                output.accept(DESALTER_ITEM.get());
                output.accept(FURNACE_ITEM.get());
                output.accept(ATMOSPHERIC_COLUMN_ITEM.get());
                output.accept(VACUUM_COLUMN_ITEM.get());
                output.accept(REFORMER_ITEM.get());
                output.accept(FCC_ITEM.get());
                output.accept(HYDROTREATER_ITEM.get());
                output.accept(COAL_GENERATOR_ITEM.get());
                output.accept(FLARE_STACK_ITEM.get());
                output.accept(ELECTROLYSER_ITEM.get());
                output.accept(BLAST_FURNACE_ITEM.get());
                output.accept(COKE_OVEN_ITEM.get());
                output.accept(STARTER_GENERATOR_ITEM.get());
                output.accept(SOLAR_PANEL_ITEM.get());
                output.accept(WIND_TURBINE_ITEM.get());
                output.accept(STEAM_ENGINE_ITEM.get());
                output.accept(LAVA_GENERATOR_ITEM.get());
                output.accept(GASOLINE_ENGINE_ITEM.get());
                output.accept(FREE_PISTON_GENERATOR_ITEM.get());
                output.accept(STAMP_MILL_ITEM.get());
                output.accept(POWDER_MILL_ITEM.get());
                output.accept(CRUSHER_ITEM.get());
                output.accept(ROLLING_MILL_ITEM.get());
                output.accept(WIRE_DRAWER_ITEM.get());
                output.accept(ROASTER_ITEM.get());
                output.accept(HYDROGEN_FURNACE_ITEM.get());
                output.accept(SINTER_PRESS_ITEM.get());
                output.accept(LEACH_TANK_ITEM.get());
                output.accept(CENTRIFUGE_ITEM.get());
                output.accept(NUCLEAR_REACTOR_ITEM.get());
                output.accept(REACTOR_PLATING_ITEM.get());
                output.accept(REACTOR_FRAME_ITEM.get());
                output.accept(REACTOR_CORE_SHIELD_ITEM.get());
                output.accept(CONTROL_ROD_DRIVE_ITEM.get());
                output.accept(COOLANT_PUMP_ITEM.get());
                output.accept(REACTOR_FUEL_PORT_ITEM.get());
                output.accept(REACTOR_WATER_PORT_ITEM.get());
                output.accept(REACTOR_POWER_PORT_ITEM.get());
                output.accept(REACTOR_STEAM_PORT_ITEM.get());
                output.accept(COOLING_TOWER_ITEM.get());
                output.accept(STEAM_TURBINE_ITEM.get());
                output.accept(REPROCESSING_PLANT_ITEM.get());
                output.accept(FUSION_REACTOR_ITEM.get());
                output.accept(ANTIMATTER_COLLIDER_ITEM.get());
                output.accept(ANTIMATTER_STORE_ITEM.get());
                output.accept(EMPTY_TRAP_CELL.get());
                output.accept(ANTIMATTER_CELL.get());
                output.accept(ALKYLATION_UNIT_ITEM.get());
                output.accept(ARC_FURNACE_ITEM.get());
                output.accept(NITRATION_REACTOR_ITEM.get());
                output.accept(COKER_ITEM.get());
                output.accept(STORAGE_TANK_ITEM.get());
                output.accept(ACID_CATALYST.get());
                output.accept(FUEL_CANISTER.get());
                output.accept(ALKYLATE_CANISTER.get());
                output.accept(CRUDE_OIL_DEPOSIT_ITEM.get());
                output.accept(CREATIVE_BATTERY_ITEM.get());
                output.accept(CREATIVE_FLUID_SOURCE_ITEM.get());
            }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_MISSILES = CREATIVE_MODE_TABS.register("wmd_missiles", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.missiles"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_industry"))
            .icon(() -> MISSILE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(BAY_SIZE_KIT.get());
                output.accept(PAD_PLATE_ITEM.get());
                output.accept(LAUNCH_TERMINAL_1_ITEM.get());
                output.accept(LAUNCH_TERMINAL_2_ITEM.get());
                output.accept(LAUNCH_TERMINAL_3_ITEM.get());
                output.accept(LAUNCH_TERMINAL_4_ITEM.get());
                output.accept(NETWORK_CARD.get());
                output.accept(GUIDANCE_CORE_T4.get());
                output.accept(LAB_CORE_ITEM.get());
                output.accept(LAB_PRESS_ITEM.get());
                output.accept(LAB_FILLER_ITEM.get());
                output.accept(WARHEAD_REACTOR_ITEM.get());
                output.accept(LAB_OVEN_ITEM.get());
                output.accept(LAB_STORAGE_ITEM.get());
                output.accept(REACTOR_REPAIR_KIT.get());
                output.accept(CRUISE_LAUNCHER_ITEM.get());
                output.accept(ROBOT_DOCK_ITEM.get());
                output.accept(MISSILE_ROBOT_ITEM.get());
                output.accept(XL_DOCK_ITEM.get());
                output.accept(XL_TRANSPORTER_ITEM.get());
                output.accept(RUNNER_CHARGER_ITEM.get());
                output.accept(PARTS_RUNNER_ITEM.get());
                output.accept(RUNNER_UPGRADE_KIT.get());
                output.accept(MISSILE_DEPOT_ITEM.get());
                output.accept(MISSILE_TELEPORTER_ITEM.get());
                output.accept(LINK_CARD.get());
                output.accept(MISSILE_T1.get());
                output.accept(STAGE_CORE.get());
                output.accept(STAGE_UPPER.get());
                output.accept(STAGE_BOOSTER.get());
                output.accept(CAM_POD.get());
                output.accept(LASER_SEEKER.get());
                output.accept(HEAT_SEEKER.get());
                output.accept(ARM_SEEKER.get());
                output.accept(TV_MODULE.get());
                output.accept(CRUISE_MISSILE.get());
                output.accept(JET_ENGINE.get());
                output.accept(MISSILE_T2.get());
                output.accept(MISSILE_T3.get());
                output.accept(BAY_CORE_ITEM.get());
                output.accept(BAY_WELD_ITEM.get());
                output.accept(BAY_CRANE_ITEM.get());
                output.accept(BAY_WARHEAD_ITEM.get());
                output.accept(BAY_CLEAN_ITEM.get());
                output.accept(BAY_PAINT_ITEM.get());
                output.accept(BAY_CRYO_ITEM.get());
                output.accept(BAY_EXTEND_ITEM.get());
                output.accept(BAY_RACK_ITEM.get());
                for (DeferredItem<MissilePartItem> part : partItems()) output.accept(part.get());
            }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_ARTILLERY = CREATIVE_MODE_TABS.register("wmd_artillery", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.artillery"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_missiles"))
            .icon(() -> HOWITZER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SWARM_LAUNCHER_ITEM.get());
                output.accept(SWARM_DRONE_PACK.get());
                output.accept(RAILGUN_ITEM.get());
                output.accept(RAILGUN_SLUG.get());
                output.accept(MORTAR_ITEM.get());
                output.accept(HOWITZER_ITEM.get());
                output.accept(SHELL_PRESS_ITEM.get());
                output.accept(SUPER_GUN_ITEM.get());
                output.accept(COUNTER_BATTERY_RADAR_ITEM.get());
                output.accept(MLRS_ITEM.get());
                output.accept(TARGET_DESIGNATOR.get());
                output.accept(RECON_DRONE.get());
                for (DeferredItem<ArtShellItem> artShell : java.util.List.of(SHELL_MORTAR_HE, SHELL_MORTAR_SMOKE, SHELL_MORTAR_ILLUM, SHELL_MORTAR_CLUSTER, SHELL_HOWITZER_HE,
                        SHELL_HOWITZER_SMOKE, SHELL_HOWITZER_ILLUM, SHELL_HOWITZER_CLUSTER, PROPELLANT_CHARGE)) output.accept(artShell.get());
                output.accept(SHELL_MORTAR_GUIDED.get());
                output.accept(SHELL_HOWITZER_GUIDED.get());
                for (DeferredItem<ArtShellItem> it : java.util.List.of(SHELL_HOWITZER_LASER, SHELL_HOWITZER_CANISTER, SHELL_HOWITZER_SENSOR, SHELL_HOWITZER_PIERCE, SHELL_HOWITZER_INCENDIARY,
                        SHELL_HOWITZER_FLAK, SHELL_HOWITZER_CAMERA, SHELL_HOWITZER_JAMMING, SHELL_HOWITZER_ROCKET, SHELL_HOWITZER_LEAFLET, SHELL_SUPER_NUKE)) output.accept(it.get());
                output.accept(MLRS_POD.get());
                output.accept(MLRS_POD_GUIDED.get());
                output.accept(SHELL_SUPER_HE.get());
                output.accept(SUPER_CHARGE.get());
                output.accept(MANPADS_LAUNCHER.get()); output.accept(MANPADS_ROUND.get());
                output.accept(LEAFLET.get());
                output.accept(JAVELIN_LAUNCHER.get()); output.accept(JAVELIN_ROUND.get());
                output.accept(LASER_TRIPOD_ITEM.get());
                output.accept(LASER_RECEIVER_ITEM.get());
                output.accept(LASER_TABLET.get());
                output.accept(BOMB_RACK_ITEM.get()); output.accept(BOMB_BAY_ITEM.get());
                output.accept(BOMB_IRON.get()); output.accept(BOMB_LASER.get()); output.accept(BOMB_GLIDE.get()); output.accept(BOMB_CLUSTER.get());
                output.accept(BOMB_DELAYED.get()); output.accept(BOMB_NAPALM.get()); output.accept(BOMB_PENETRATOR.get()); output.accept(BOMB_DAISY.get());
                output.accept(BOMB_TALLBOY.get()); output.accept(BOMB_BOUNCING.get()); output.accept(BOMB_TSAR.get()); output.accept(BOMB_SIGHT_ITEM.get());
                output.accept(DRONE_CORE_ITEM.get()); output.accept(DRONE_FRAME_ITEM.get()); output.accept(DRONE_AVIONICS_ITEM.get()); output.accept(DRONE_PROPULSION_ITEM.get());
                output.accept(DRONE_TEST_ITEM.get()); output.accept(DRONE_STORAGE_ITEM.get()); output.accept(DRONE_HANGAR_ITEM.get());
                output.accept(DRONE_T1.get()); output.accept(DRONE_T2.get()); output.accept(DRONE_T3.get()); output.accept(DRONE_T4.get());
                output.accept(DRONE_HIVE_ITEM.get());
                output.accept(DRONE_CATAPULT_ITEM.get()); output.accept(RUNWAY_TILE_ITEM.get()); output.accept(RUNWAY_LINE_ITEM.get()); output.accept(RUNWAY_LIGHT_ITEM.get());
                output.accept(BOMB_LINE_CORE_ITEM.get()); output.accept(BOMB_LINE_PRESS_ITEM.get()); output.accept(BOMB_LINE_FILLING_ITEM.get());
                output.accept(BOMB_LINE_FUZE_ITEM.get()); output.accept(BOMB_LINE_STORAGE_ITEM.get()); output.accept(BOMB_LINE_CRADLE_ITEM.get());
            }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_DEFENCE = CREATIVE_MODE_TABS.register("wmd_defence", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.defence"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_artillery"))
            .icon(() -> CIWS_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(RADAR_STATION_ITEM.get());
                output.accept(INTERCEPTOR_LAUNCHER_ITEM.get());
                output.accept(INTERCEPTOR_BATTERY_T2_ITEM.get());
                output.accept(CIWS_ITEM.get());
                output.accept(SALVAGE_TURRET_ITEM.get());
                output.accept(SALVAGE_GENERATOR_ITEM.get());
                output.accept(RADAR_COMPONENT.get());
                output.accept(CIWS_AMMO.get());
                output.accept(UPGRADE_CIWS_DRUM.get());
                output.accept(INTERCEPTOR_SILO_T3_ITEM.get());
                output.accept(INTERCEPTOR_SILO_T4_ITEM.get());
                output.accept(INTERCEPTOR_T1.get());
                output.accept(INTERCEPTOR_T2.get());
                output.accept(INTERCEPTOR_T3.get());
                output.accept(INTERCEPTOR_T4.get());
                output.accept(REINFORCED_CONCRETE_ITEM.get());
                output.accept(BLAST_GLASS_ITEM.get());
                output.accept(ARMOUR_PLATE_ITEM.get());
                output.accept(HARDENED_LID.get());
                output.accept(FORCEFIELD_ITEM.get());
                output.accept(LASER_DEFENCE_ITEM.get());
                output.accept(DEADHAND_ITEM.get());
                output.accept(EARLY_WARNING_ARRAY_ITEM.get());
                output.accept(CAMO_NET_ITEM.get());
                output.accept(DECOY_LAUNCHER_ITEM.get());
                output.accept(DECOY_RADAR_ITEM.get());
                output.accept(SENTRY_DOG_ITEM.get());
                output.accept(SENTRY_TURRET_ITEM.get());
                output.accept(CCTV_CAMERA_ITEM.get());
                output.accept(C4_CHARGE_ITEM.get());
                output.accept(DETONATOR.get());
                output.accept(HACKING_DEVICE.get());
                output.accept(PORTABLE_JAMMER_ITEM.get());
                output.accept(SEISMIC_SCANNER_ITEM.get());
                output.accept(COVERT_CAMERA_ITEM.get());
                output.accept(CAMERA_RELAY_ITEM.get());
                output.accept(PERIMETER_SENSOR_ITEM.get());
                output.accept(FLOODLIGHT_ITEM.get());
                output.accept(FENCE_ENERGIZER_ITEM.get());
                output.accept(ELECTRIC_FENCE_ITEM.get());
                output.accept(PRESSURE_MINE_ITEM.get());
                output.accept(CLAYMORE_ITEM.get());
                output.accept(ALARM_ITEM.get());
                output.accept(THERMAL_NET_ITEM.get());
                output.accept(SMOKE_MACHINE_ITEM.get());
                output.accept(SMOKE_CANISTER.get());
                output.accept(KILL_VEHICLE_PACK.get());
                output.accept(GAS_MASK.get());
            }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_SPACE = CREATIVE_MODE_TABS.register("wmd_space", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.space"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_defence"))
            .icon(() -> CC_BUILDING_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SATELLITE_PART.get());
                output.accept(GROUND_STATION_ITEM.get());
                output.accept(SATELLITE_FACILITY_ITEM.get());
                output.accept(CC_BUILDING_ITEM.get());
                output.accept(CC_MAP_TABLE_ITEM.get());
                output.accept(CC_WARNING_BOARD_ITEM.get());
                output.accept(CC_RECON_WALL_ITEM.get());
                output.accept(CC_AIR_DEFENCE_ITEM.get());
                output.accept(CC_LAUNCH_CONSOLE_ITEM.get());
                output.accept(CC_RETARGET_CONSOLE_ITEM.get());
                output.accept(CC_STRIKE_PLANNER_ITEM.get());
                output.accept(CC_MISSILE_CAM_ITEM.get());
                output.accept(CC_SAT_GLOBE_ITEM.get());
                output.accept(CC_EW_RACK_ITEM.get());
                output.accept(CC_GPS_RACK_ITEM.get());
                output.accept(CC_ALARM_BOARD_ITEM.get());
                output.accept(CC_MAP_PRINTER_ITEM.get());
                output.accept(CC_COMMS_ARRAY_ITEM.get());
                output.accept(CC_INTERCEPTOR_CONSOLE_ITEM.get());
                output.accept(CC_ARTILLERY_CONSOLE_ITEM.get());
                output.accept(CC_IRON_DOME_ITEM.get());
                output.accept(CC_PLANNER_ITEM.get());
                output.accept(CC_SECURITY_MONITOR_ITEM.get());
                output.accept(RTG_CELL.get());
                for (String t : Satellites.TYPES) output.accept(PayloadUnitItem.of(t));
                output.accept(CUBESAT.get());
                output.accept(UPLINK_CELL.get());
                output.accept(LASER_DESIGNATOR_POD.get());
                output.accept(EVA_HELMET.get());
                { net.minecraft.world.item.ItemStack t = new net.minecraft.world.item.ItemStack(EVA_TORSO.get()); EvaSuitItem.setOxygen(t, Balance.O2_FULL); output.accept(t); }
                output.accept(EVA_LEGS.get());
                output.accept(EVA_BOOTS.get());
                output.accept(OxygenTankItem.full(OXYGEN_TANK.get()));
                output.accept(OXYGEN_STATION_ITEM.get());
                output.accept(SOLAR_CELL.get());
                output.accept(CREW_CAPSULE.get());
                output.accept(USED_CREW_CAPSULE.get());
                output.accept(STATION_HULL_ITEM.get());
                output.accept(STATION_HULL_SEAM_ITEM.get());
                output.accept(STATION_BAND_ITEM.get());
                output.accept(STATION_DECK_ITEM.get());
                output.accept(STATION_WINDOW_ITEM.get());
                output.accept(STATION_LIGHT_ITEM.get());
                output.accept(STATION_HATCH_FRAME_ITEM.get());
                output.accept(STATION_KIT_CORE.get());
                output.accept(STATION_KIT_HABITAT.get());
                output.accept(STATION_KIT_WING.get());
                output.accept(STATION_KIT_SENSOR.get());
                output.accept(STATION_KIT_LASER.get());
                output.accept(STATION_KIT_ROD.get());
                output.accept(ORBITAL_ROD.get());
                output.accept(STATION_KIT_MIRROR.get());
                output.accept(STATION_KIT_BAY.get());
                output.accept(STATION_KIT_TURRET.get());
                output.accept(DT_FUEL_CANISTER.get());
                output.accept(TUG_CORE.get());
                output.accept(ORBITAL_FRAME.get());
                output.accept(LIFE_SUPPORT_UNIT.get());
                output.accept(POWER_CORE.get());
                output.accept(WEAPON_CORE.get());
                output.accept(STATION_HATCH_ITEM.get());
                output.accept(STATION_REPAIR_KIT.get());
                output.accept(STATION_BATTERY_ITEM.get());
                output.accept(CARGO_POD.get());
                output.accept(STATION_FUEL_CELL.get());
            }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_DEEP_SEA = CREATIVE_MODE_TABS.register("wmd_deep_sea", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.deep_sea"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_space"))
            .icon(() -> TORPEDO_T2.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SONAR_DOME_ITEM.get());
                output.accept(HYDROPHONE_ARRAY_ITEM.get());
                output.accept(SONAR_CONSOLE_ITEM.get());
                output.accept(TOWED_ARRAY_WINCH_ITEM.get());
                output.accept(LISTENING_NODE_ITEM.get());
                output.accept(COASTAL_BATTERY_ITEM.get());
                output.accept(DOCK_CRANE_ITEM.get());
                output.accept(TORPEDO_TUBE_ITEM.get());
                output.accept(TORPEDO_LAUNCHER_DECK_ITEM.get());
                output.accept(TORPEDO_RACK_ITEM.get());
                output.accept(FIRE_CONTROL_CONSOLE_ITEM.get());
                output.accept(TORPEDO_T1.get());
                output.accept(TORPEDO_T2.get());
                output.accept(TORPEDO_T3.get());
                output.accept(TORPEDO_T4.get());
                output.accept(UUV.get());
                output.accept(TORPEDO_ATT.get());
                output.accept(NAVAL_REACTOR_ITEM.get());
                output.accept(PERISCOPE_ITEM.get());
                output.accept(COMMS_BUOY_WINCH_ITEM.get());
                output.accept(ESCAPE_POD_ITEM.get());
                output.accept(ESCAPE_CAPSULE.get());
                output.accept(NOISEMAKER.get());
                output.accept(NOISEMAKER_LAUNCHER_ITEM.get());
                output.accept(DEPTH_CHARGE.get());
                output.accept(SONOBUOY.get());
                output.accept(LAUNCH_CELL_ITEM.get());
                output.accept(ASROC.get());
                output.accept(SLBM.get());
                output.accept(CRUISE_CAPSULE.get());
                output.accept(DEPTH_CHARGE_RACK_ITEM.get());
                output.accept(DREDGE_WINCH_ITEM.get()); output.accept(COLLECTOR_RIG_ITEM.get()); output.accept(SEABED_CRAWLER.get());
                output.accept(MANGANESE_NODULE.get()); output.accept(MANGANESE_CONCENTRATE.get()); output.accept(NICKEL_COBALT_CONCENTRATE.get());
                output.accept(MANGANESE_INGOT.get()); output.accept(NICKEL_COBALT_INGOT.get()); output.accept(NODULE_FIELD_ITEM.get());
                output.accept(MANGANESE_HULL_PLATE_ITEM.get()); output.accept(SUPERALLOY_HULL_ITEM.get()); output.accept(DEEP_VIEWPORT_ITEM.get());
                output.accept(ANECHOIC_TILE_ITEM.get()); output.accept(DEEP_SONAR_UPGRADE.get());
                output.accept(TORPEDO_NET_ITEM.get());
                output.accept(DEPTH_CHARGE_THROWER_ITEM.get());
                output.accept(MINE_INFLUENCE_ITEM.get()); output.accept(MINE_BOTTOM_ITEM.get()); output.accept(MINE_CAPTOR_ITEM.get());
                output.accept(TORPEDO_LINE_SHOP_ITEM.get()); output.accept(TORPEDO_LINE_FITOUT_ITEM.get()); output.accept(TORPEDO_LINE_TEST_ITEM.get());
                output.accept(HULL_PENETRATOR_ITEM.get());

                output.accept(SCREW_PROPELLER_ITEM.get()); output.accept(ENGINE_TELEGRAPH_ITEM.get()); output.accept(HELM_STATION_ITEM.get());
                output.accept(PUMP_JET_ITEM.get()); output.accept(SUB_DIESEL_ITEM.get()); output.accept(SNORKEL_MAST_ITEM.get());
                if (AirGate.AVAILABLE) AirBlocks.navyTab(output);
             }).build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> WMD_TAB_AIR = CREATIVE_MODE_TABS.register("wmd_air_battles", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wmd.air_battles"))
            .withTabsBefore(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "wmd_deep_sea"))
            .icon(() -> TURBOFAN_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(DUCTED_FAN_ITEM.get());
                output.accept(TURBOFAN_ITEM.get());
                output.accept(JATO_RACK_ITEM.get());
                output.accept(JATO_BOOSTER.get());
                output.accept(ENGINE_REPAIR_KIT.get());
                if (AirGate.AVAILABLE) AirBlocks.tab(output);
             }).build());

    public Wmd(IEventBus modEventBus, ModContainer modContainer) {
        WmdMaterials.init();
        WmdGameRules.init();

        modEventBus.addListener(this::commonSetup);

        if (AirGate.AVAILABLE) AirBlocks.init();

        BLOCKS.register(modEventBus);
        NoduleRetrogen.register(modEventBus);

        if (net.neoforged.fml.ModList.get().isLoaded("create")) {
            try {
                Class.forName("com.ashraf.wmd.CreateCompat").getMethod("init", IEventBus.class).invoke(null, modEventBus);
            } catch (ClassNotFoundException e) {
                LOGGER.info("[create] this WMD build has no Create drive coupling");
            } catch (ReflectiveOperationException | LinkageError e) {
                LOGGER.error("[create] the Create drive coupling failed to start (another Create version?)", e);
            }
        }

        ITEMS.register(modEventBus);
        GasMaskItem.MATERIALS.register(modEventBus);
        EvaSuitItem.MATERIALS.register(modEventBus);
        WmdComponents.COMPONENTS.register(modEventBus);

        CREATIVE_MODE_TABS.register(modEventBus);

        ENTITY_TYPES.register(modEventBus);

        FEATURES.register(modEventBus);
        PLACEMENT_MODIFIERS.register(modEventBus);

        BLOCK_ENTITY_TYPES.register(modEventBus);

        MENU_TYPES.register(modEventBus);

        WmdParticles.TYPES.register(modEventBus);
        WmdSounds.SOUNDS.register(modEventBus);

        WmdFluids.TYPES.register(modEventBus);
        WmdFluids.FLUIDS.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);

        modEventBus.addListener(this::registerPayloads);

        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> e.put(SENTRY_DOG.get(), SentryDogEntity.createAttributes().build()));

        NeoForge.EVENT_BUS.register(this);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.Server.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DIESEL_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DIESEL_GENERATOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> Grid.gridOnly(BatteryBlock.energyAt(level, pos, state)), BATTERY.get());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RADAR_BE.get(), (be, side) -> Grid.gridOnly(be.getEnergy()));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BUSBAR_BE.get(), (be, side) -> Grid.gridOnly(be.getEnergy()));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MACHINE_PART_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.item(side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COAL_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COAL_GENERATOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAUNCHER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAUNCHER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HEAVY_BATTERY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HEAVY_BATTERY_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CIWS_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CIWS_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SILO_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SILO_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SILO4_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SILO4_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAB_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAB_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BAY_CORE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BAY_CORE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BAY_WELD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BAY_WELD_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BAY_CRANE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BAY_WARHEAD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BAY_CRANE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BAY_WARHEAD_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLARE_STACK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FLARE_STACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ELECTROLYSER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CRUSHER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CRUSHER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ARC_FURNACE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ARC_FURNACE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ELECTROLYSER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, NITRATION_REACTOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NITRATION_REACTOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NITRATION_REACTOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COKER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COKER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COKER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLUID_PIPE_BE.get(), (be, side) -> be.getHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FLOODGATE_BE.get(), (be, side) -> be.port(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ROBOT_DOCK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, XL_DOCK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RUNNER_CHARGER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LASER_TRIPOD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LASER_RECEIVER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SMOKE_MACHINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BOMB_RACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BOMB_BAY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BOMB_SIGHT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DRONE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DRONE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BOMB_LINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TORPEDO_LINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TORPEDO_LINE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BOMB_LINE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BOMB_LINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DRONE_HIVE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DRONE_CATAPULT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MISSILE_DEPOT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MISSILE_DEPOT_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MISSILE_TELEPORTER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MISSILE_TELEPORTER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) { try { CcTweakedCompat.register(event); } catch (Throwable t) { LOGGER.warn("CC: Tweaked compat failed to register (audit 1 Nov, M18): {}", t.toString()); } }
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SUPPLY_INTERFACE_BE.get(), (be, side) -> be.items());

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MACHINE_PART_BE.get(), (be, side) -> be.fluid(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MACHINE_PART_BE.get(), (be, side) -> Grid.gridOnly(be.energy(side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PUMPJACK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PUMPJACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CHLOR_ALKALI_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHLOR_ALKALI_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHLOR_ALKALI_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, CHLORINATOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CHLORINATOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CHLORINATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, KROLL_REACTOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, KROLL_REACTOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, KROLL_REACTOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MINING_RIG_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MINING_RIG_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MINING_RIG_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GAS_TURBINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GAS_TURBINE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GAS_TURBINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GRID_CONVERTER_BE.get(), (be, side) -> be.energyPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MAGNESIUM_CELL_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MAGNESIUM_CELL_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MAGNESIUM_CELL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ROLLING_MILL_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ROLLING_MILL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WIRE_DRAWER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WIRE_DRAWER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROASTER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ROASTER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ROASTER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HYDROGEN_FURNACE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COKE_OVEN_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COKE_OVEN_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STARTER_GENERATOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STARTER_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STARTER_GENERATOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SOLAR_PANEL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WIND_TURBINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STEAM_ENGINE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STEAM_ENGINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STEAM_ENGINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAVA_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAVA_GENERATOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GASOLINE_ENGINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, GASOLINE_ENGINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FREE_PISTON_GENERATOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FREE_PISTON_GENERATOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STAMP_MILL_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STAMP_MILL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, POWDER_MILL_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, POWDER_MILL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY_T1_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY_T2_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY_T3_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BATTERY_T4_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CAMERA_RELAY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SENTRY_TURRET_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FENCE_ENERGIZER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLAST_FURNACE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BLAST_FURNACE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HYDROGEN_FURNACE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HYDROGEN_FURNACE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SINTER_PRESS_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SINTER_PRESS_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LEACH_TANK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LEACH_TANK_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LEACH_TANK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CENTRIFUGE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CENTRIFUGE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REACTOR_PORT_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.item(side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, REACTOR_PORT_BE.get(), (be, side) -> be.fluid(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, REACTOR_PORT_BE.get(), (be, side) -> Grid.gridOnly(be.energy(side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COOLING_TOWER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, COOLING_TOWER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STEAM_TURBINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, STEAM_TURBINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FUSION_REACTOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FUSION_REACTOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FORCEFIELD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LASER_DEFENCE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SWARM_LAUNCHER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, REPROCESSING_PLANT_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, REPROCESSING_PLANT_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, REPROCESSING_PLANT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ANTIMATTER_COLLIDER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, OXYGEN_STATION_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, OXYGEN_STATION_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ANTIMATTER_COLLIDER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ANTIMATTER_STORE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RAILGUN_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RAILGUN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GROUND_STATION_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EARLY_WARNING_ARRAY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SATELLITE_FACILITY_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SATELLITE_FACILITY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MORTAR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, HOWITZER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HOWITZER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SUPER_GUN_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SUPER_GUN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SHELL_PRESS_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SHELL_PRESS_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ALKYLATION_UNIT_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ALKYLATION_UNIT_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ALKYLATION_UNIT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CRUISE_LAUNCHER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CRUISE_LAUNCHER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COUNTER_BATTERY_RADAR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SONAR_DOME_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HYDROPHONE_ARRAY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TOWED_ARRAY_WINCH_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COASTAL_BATTERY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DOCK_CRANE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SONAR_CONSOLE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TORPEDO_TUBE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TORPEDO_LAUNCHER_DECK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TORPEDO_RACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAUNCH_CELL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NAVAL_REACTOR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NAVAL_REACTOR_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, NAVAL_REACTOR_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PERISCOPE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SCREW_PROPELLER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, PUMP_JET_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DUCTED_FAN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TURBOFAN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        if (AirGate.AVAILABLE) AirBlocks.capabilities(event);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TURBOFAN_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SUB_DIESEL_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SUB_DIESEL_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SNORKEL_MAST_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HELM_STATION_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COMMS_BUOY_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ESCAPE_POD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DEPTH_CHARGE_RACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DEPTH_CHARGE_THROWER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NOISEMAKER_LAUNCHER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FIRE_CONTROL_CONSOLE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MLRS_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, MLRS_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CC_MAP_PRINTER_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, RESEARCH_STATION_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DREDGE_WINCH_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, DREDGE_WINCH_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COLLECTOR_RIG_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, COLLECTOR_RIG_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RESEARCH_STATION_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, RESEARCH_STATION_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, WATER_INTAKE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WATER_INTAKE_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, WATER_INTAKE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FEED_PUMP_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FEED_PUMP_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DESALTER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DESALTER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FURNACE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FURNACE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, be, side) -> Grid.gridOnly(CreativeBattery.STORAGE), CREATIVE_BATTERY.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, be, side) -> CreativeFluidSourceBlock.handler(state), CREATIVE_FLUID_SOURCE.get());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, STORAGE_TANK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HYDROTREATER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HYDROTREATER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FCC_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FCC_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, REFORMER_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, REFORMER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VACUUM_COLUMN_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, VACUUM_COLUMN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ATMOSPHERIC_COLUMN_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ATMOSPHERIC_COLUMN_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, LAUNCH_TERMINAL_BE.get(), (be, side) -> Grid.gridOnly(be.getEnergy()));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, LAUNCH_TERMINAL_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.automationPort()));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LAUNCH_TERMINAL_BE.get(), (be, side) -> be.getFuelInput());
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> Grid.gridOnly(RadarStructure.energyAt(level, pos, side)), RADAR_PART.get());

        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> Grid.gridOnly(state.getValue(PadPlateBlock.FORMED) ? PadStructure.energyAt(level, pos) : null),
                PAD_PLATE.get());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetTargetPayload.TYPE, SetTargetPayload.STREAM_CODEC, SetTargetPayload::handle);
        registrar.playToServer(SetDecoyPayload.TYPE, SetDecoyPayload.STREAM_CODEC, SetDecoyPayload::handle);
        registrar.playToServer(BombTargetPayload.TYPE, BombTargetPayload.STREAM_CODEC, BombTargetPayload::handle);
        registrar.playToServer(BombSightKeyPayload.TYPE, BombSightKeyPayload.STREAM_CODEC, BombSightKeyPayload::handle);
        registrar.playToServer(EvaThrustPayload.TYPE, EvaThrustPayload.STREAM_CODEC, EvaThrustPayload::handle);
        registrar.playToServer(BombSightPayload.TYPE, BombSightPayload.STREAM_CODEC, BombSightPayload::handle);
        registrar.playToServer(SetRadarBearingPayload.TYPE, SetRadarBearingPayload.STREAM_CODEC, SetRadarBearingPayload::handle);
        registrar.playToServer(SetRadarConfigPayload.TYPE, SetRadarConfigPayload.STREAM_CODEC, SetRadarConfigPayload::handle);
        registrar.playToServer(SetAlarmPayload.TYPE, SetAlarmPayload.STREAM_CODEC, SetAlarmPayload::handle);
        registrar.playToServer(SetResearchPayload.TYPE, SetResearchPayload.STREAM_CODEC, SetResearchPayload::handle);
        registrar.playToServer(ArtGunPayload.TYPE, ArtGunPayload.STREAM_CODEC, ArtGunPayload::handle);
        registrar.playToServer(CruisePayload.TYPE, CruisePayload.STREAM_CODEC, CruisePayload::handle);
        registrar.playToClient(UnderwaterBlastPayload.TYPE, UnderwaterBlastPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> UnderwaterBlastView.onReceive(p)));
        registrar.playToClient(PeriscopeAirPayload.TYPE, PeriscopeAirPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientPeriscope.airMarks(p)));
        registrar.playToClient(PeriscopePayload.TYPE, PeriscopePayload.STREAM_CODEC, PeriscopePayload::handle);
        registrar.playToServer(HelmInputPayload.TYPE, HelmInputPayload.STREAM_CODEC, HelmInputPayload::handle);
        registrar.playToClient(HelmHudPayload.TYPE, HelmHudPayload.STREAM_CODEC, HelmHudPayload::handle);
        registrar.playToServer(CockpitInputPayload.TYPE, CockpitInputPayload.STREAM_CODEC, CockpitInputPayload::handle);
        registrar.playToClient(CockpitHudPayload.TYPE, CockpitHudPayload.STREAM_CODEC, CockpitHudPayload::handle);
        registrar.playToClient(ApHudPayload.TYPE, ApHudPayload.STREAM_CODEC, ApHudPayload::handle);
        registrar.playToServer(ApPayloads.Ask.TYPE, ApPayloads.Ask.STREAM_CODEC, ApPayloads::handleAsk);
        registrar.playToClient(ApPayloads.State.TYPE, ApPayloads.State.STREAM_CODEC, ApPayloads::handleState);
        registrar.playToClient(FlightCheckPayload.Show.TYPE, FlightCheckPayload.Show.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> FlightCheckScreen.show(p.tag())));
        registrar.playToServer(FlightCheckPayload.Ask.TYPE, FlightCheckPayload.Ask.STREAM_CODEC, FlightCheckPayload.Ask::handle);
        registrar.playToClient(AirframePayload.Show.TYPE, AirframePayload.Show.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> AirframeScreen.show(p.tag())));
        registrar.playToServer(AirframePayload.Ask.TYPE, AirframePayload.Ask.STREAM_CODEC, AirframePayload.Ask::handle);
        registrar.playToClient(LoadoutPayload.Show.TYPE, LoadoutPayload.Show.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> LoadoutScreen.show(p.tag())));
        registrar.playToServer(LoadoutPayload.Act.TYPE, LoadoutPayload.Act.STREAM_CODEC, LoadoutPayload.Act::handle);
        registrar.playToClient(RwrPayload.TYPE, RwrPayload.STREAM_CODEC, RwrPayload::handle);
        registrar.playToClient(SmokePayload.TYPE, SmokePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> SmokePayload.onReceive(p)));
        registrar.playToClient(DomeMarkersPayload.TYPE, DomeMarkersPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> DomeMarkersPayload.onReceive(p)));
        registrar.playToClient(ArtShellsPayload.TYPE, ArtShellsPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ArtilleryView.onShells(p)));
        registrar.playToClient(ArtImpactPayload.TYPE, ArtImpactPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ArtilleryView.onImpact(p)));
        registrar.playToServer(SetMachineModePayload.TYPE, SetMachineModePayload.STREAM_CODEC, SetMachineModePayload::handle);
        registrar.playToServer(BulkLoadPayload.TYPE, BulkLoadPayload.STREAM_CODEC, BulkLoadPayload::handle);
        registrar.playToServer(SorterConfigPayload.TYPE, SorterConfigPayload.STREAM_CODEC, SorterConfigPayload::handle);
        registrar.playToClient(DuctTransitPayload.TYPE, DuctTransitPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientDucts.onTransit(p)));
        registrar.playToServer(UpgradeRemovePayload.TYPE, UpgradeRemovePayload.STREAM_CODEC, UpgradeRemovePayload::handle);
        registrar.playToServer(SetLauncherFrequencyPayload.TYPE, SetLauncherFrequencyPayload.STREAM_CODEC, SetLauncherFrequencyPayload::handle);
        registrar.playToServer(SetMachineFrequencyPayload.TYPE, SetMachineFrequencyPayload.STREAM_CODEC, SetMachineFrequencyPayload::handle);
        registrar.playToClient(SeismicResultPayload.TYPE, SeismicResultPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientSeismic.onResult(p)));
        registrar.playToClient(AftermathPayload.TYPE, AftermathPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAftermath.add(p)));
        registrar.playToClient(ShipFirePayload.TYPE, ShipFirePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientShipFire.add(p)));
        registrar.playToClient(WreckPayload.TYPE, WreckPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientWreckage.add(p)));
        registrar.playToClient(DeadHandIntelPayload.TYPE, DeadHandIntelPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> DeadHandBlockEntity.onIntel(ctx.player(), p)));

        registrar.playToClient(CctvPayloads.Open.TYPE, CctvPayloads.Open.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCctv.onOpen(p)));
        registrar.playToClient(CctvPayloads.Feed.TYPE, CctvPayloads.Feed.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCctv.onFeed(p)));
        registrar.playToServer(CctvPayloads.FeedRequest.TYPE, CctvPayloads.FeedRequest.STREAM_CODEC, CctvPayloads::handleFeed);
        registrar.playToServer(CctvPayloads.View.TYPE, CctvPayloads.View.STREAM_CODEC, CctvPayloads::handleView);
        registrar.playToServer(CctvPayloads.MarkTarget.TYPE, CctvPayloads.MarkTarget.STREAM_CODEC, CctvPayloads::handleMark);
        registrar.playToServer(SecurityActionPayload.TYPE, SecurityActionPayload.STREAM_CODEC, SecurityActionPayload::handle);
        registrar.playToServer(UplinkPayload.TYPE, UplinkPayload.STREAM_CODEC, UplinkPayload::handle);
        registrar.playToServer(CcBPayload.TYPE, CcBPayload.STREAM_CODEC, CcBPayload::handle);
        registrar.playToServer(TripodAimPayload.TYPE, TripodAimPayload.STREAM_CODEC, TripodAimPayload::handle);
        registrar.playToServer(DesignatePayload.TYPE, DesignatePayload.STREAM_CODEC, DesignatePayload::handle);
        registrar.playToServer(TvSteerPayload.TYPE, TvSteerPayload.STREAM_CODEC, TvSteerPayload::handle);
        registrar.playToServer(CamTerrainRequestPayload.TYPE, CamTerrainRequestPayload.STREAM_CODEC, CamTerrainRequestPayload::handle);
        registrar.playToClient(CamChunkPayload.TYPE, CamChunkPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCamTerrain.onChunk(p)));
        registrar.playToClient(TvWindowPayload.TYPE, TvWindowPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientTv.onWindow(p)));
        registrar.playToClient(HyperIncomingPayload.TYPE, HyperIncomingPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHypersonic.incoming(p)));
        registrar.playToClient(HandLockPayload.TYPE, HandLockPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientHandLock.onState(p)));
        registrar.playToServer(AreaViewRequestPayload.TYPE, AreaViewRequestPayload.STREAM_CODEC, AreaViewRequestPayload::handle);
        registrar.playToClient(DesigStatusPayload.TYPE, DesigStatusPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> DesigStatusPayload.onReceive(p)));
        registrar.playToClient(SatBeamPayload.TYPE, SatBeamPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> SatBeamPayload.onReceive(p)));
        registrar.playToClient(AreaViewPayload.TYPE, AreaViewPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> AreaViewClient.onView(p)));
        registrar.playToClient(AreaMarkersPayload.TYPE, AreaMarkersPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> AreaViewClient.onMarkers(p)));
        registrar.playToServer(TabletFirePayload.TYPE, TabletFirePayload.STREAM_CODEC, TabletFirePayload::handle);
        registrar.playToServer(DroneControlPayload.TYPE, DroneControlPayload.STREAM_CODEC, DroneControlPayload::handle);
        registrar.playToServer(DroneHiveTabletPayload.TYPE, DroneHiveTabletPayload.STREAM_CODEC, DroneHiveTabletPayload::handle);
        registrar.playToClient(DroneFlyPayload.TYPE, DroneFlyPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientDroneFly.onFly(p)));
        registrar.playToServer(BigDronePayload.TYPE, BigDronePayload.STREAM_CODEC, BigDronePayload::handle);
        registrar.playToServer(DroneLasePayload.TYPE, DroneLasePayload.STREAM_CODEC, DroneLasePayload::handle);
        registrar.playToServer(CcRoutePayload.TYPE, CcRoutePayload.STREAM_CODEC, CcRoutePayload::handle);
        registrar.playToServer(ShellPressTextPayload.TYPE, ShellPressTextPayload.STREAM_CODEC, ShellPressTextPayload::handle);
        registrar.playToClient(CameraShellPayload.TYPE, CameraShellPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCamera.open(p)));
        registrar.playToClient(CcDronesPayload.TYPE, CcDronesPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CcDronesView.set(p)));
        registrar.playToClient(TripodScopePayload.TYPE, TripodScopePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientTripod.enter(p.pos())));
        registrar.playToClient(TabletInfoPayload.TYPE, TabletInfoPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> LaserTabletScreen.show(p)));
        registrar.playToClient(CcCamPayload.TYPE, CcCamPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientMissileCam.start(p.flight())));
        registrar.playToClient(CcCamPadPayload.TYPE, CcCamPadPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientMissileCam.startPad(p)));
        registrar.playToClient(SecurityStatePayload.TYPE, SecurityStatePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> SecurityClientState.set(p)));
        registrar.playToServer(LaunchPayload.TYPE, LaunchPayload.STREAM_CODEC, LaunchPayload::handle);

        registrar.playToClient(FlightsPayload.TYPE, FlightsPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientPacketHandlers.flights(p)));
        registrar.playToClient(ImpactPayload.TYPE, ImpactPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientPacketHandlers.impact(p)));
        registrar.playToClient(InterceptPayload.TYPE, InterceptPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientIntercepts.add(p)));
        registrar.playToClient(CiwsFirePayload.TYPE, CiwsFirePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientCiws.fire(p)));
        registrar.playToClient(LaunchSoundPayload.TYPE, LaunchSoundPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> LaunchSounds.play(p)));
        registrar.playToClient(EvadePayload.TYPE, EvadePayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientIntercepts.evade(p)));
        registrar.playToClient(CounterPayload.TYPE, CounterPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientIntercepts.counter(p)));
        registrar.playToClient(AamPayload.TYPE, AamPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAam.update(p)));
        registrar.playToClient(AamDecoyPayload.TYPE, AamDecoyPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAam.decoy(p)));
        registrar.playToClient(AamBurstPayload.TYPE, AamBurstPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAam.burst(p)));
        registrar.playToClient(AirRocketPayload.TYPE, AirRocketPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientAirRockets.update(p)));
        registrar.playToClient(PalletFarPayload.TYPE, PalletFarPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientPallets.update(p)));
        registrar.playToClient(InterceptSteerPayload.TYPE, InterceptSteerPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientIntercepts.steer(p)));
        registrar.playToClient(InterceptFxPayload.TYPE, InterceptFxPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientIntercepts.fx(p)));
        registrar.playToClient(OrbitPayload.TYPE, OrbitPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> OrbitView.set(p)));
        registrar.playToServer(ReconPayload.TYPE, ReconPayload.STREAM_CODEC, ReconPayload::handle);
        registrar.playToClient(ReconMapPayload.TYPE, ReconMapPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> ReconView.set(p)));
        registrar.playToServer(CcActionPayload.TYPE, CcActionPayload.STREAM_CODEC, CcActionPayload::handle);
        registrar.playToClient(CcAlarmLogPayload.TYPE, CcAlarmLogPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CcAlarmLogView.set(p)));
        registrar.playToClient(GpsPayload.TYPE, GpsPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> GpsView.set(p)));
        registrar.playToClient(CcAlertPayload.TYPE, CcAlertPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CcAlertView.set(p)));
        registrar.playToClient(CcBattleLogPayload.TYPE, CcBattleLogPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> CcBattleLogView.set(p)));
        registrar.playToClient(BdaPayload.TYPE, BdaPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> BdaView.set(p)));
        registrar.playToServer(CcDesignatePayload.TYPE, CcDesignatePayload.STREAM_CODEC, CcDesignatePayload::handle);
        registrar.playToServer(S5Payload.TYPE, S5Payload.STREAM_CODEC, S5Payload::handle);
        registrar.playToServer(SonarPayload.TYPE, SonarPayload.STREAM_CODEC, SonarPayload::handle);
        registrar.playToServer(ShorePayload.TYPE, ShorePayload.STREAM_CODEC, ShorePayload::handle);
        registrar.playToServer(LaunchCellPayload.TYPE, LaunchCellPayload.STREAM_CODEC, LaunchCellPayload::handle);
        registrar.playToServer(DepthChargePayload.TYPE, DepthChargePayload.STREAM_CODEC, DepthChargePayload::handle);
        registrar.playToServer(ThrowerPayload.TYPE, ThrowerPayload.STREAM_CODEC, ThrowerPayload::handle);
        registrar.playToServer(NoisemakerLauncherPayload.TYPE, NoisemakerLauncherPayload.STREAM_CODEC, NoisemakerLauncherPayload::handle);
        registrar.playToServer(SeaMinePayloads.Act.TYPE, SeaMinePayloads.Act.STREAM_CODEC, SeaMinePayloads.Act::handle);
        registrar.playToClient(SeaMinePayloads.Open.TYPE, SeaMinePayloads.Open.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> SeaMineScreen.open(p.pos())));
        registrar.playToServer(FireControlPayload.TYPE, FireControlPayload.STREAM_CODEC, FireControlPayload::handle);
        registrar.playToServer(TorpedoPayload.TYPE, TorpedoPayload.STREAM_CODEC, TorpedoPayload::handle);
        registrar.playToClient(DronesPayload.TYPE, DronesPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> DroneView.set(p)));
        registrar.playToServer(NodulePayload.TYPE, NodulePayload.STREAM_CODEC, NodulePayload::handle);
        registrar.playToClient(BombsPayload.TYPE, BombsPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> BombView.set(p)));
        registrar.playToClient(AsatFxPayload.TYPE, AsatFxPayload.STREAM_CODEC, (p, ctx) -> ctx.enqueueWork(() -> { AsatFxView.add(p); if (p.hit()) LaunchSounds.asat(); }));
    }

    private void commonSetup(FMLCommonSetupEvent event) {

        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        LOGGER.info("HELLO from server starting");
    }
}
