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

public final class AirBlocks {

    private AirBlocks() {}

    public static final DeferredBlock<AirSearchRadarBlock> AIR_SEARCH_RADAR = Wmd.BLOCKS.registerBlock("air_search_radar", AirSearchRadarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(4)));
    public static final DeferredItem<BlockItem> AIR_SEARCH_RADAR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("air_search_radar", AIR_SEARCH_RADAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirSearchRadarBlockEntity>> AIR_SEARCH_RADAR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("air_search_radar", () -> BlockEntityType.Builder.of(AirSearchRadarBlockEntity::new, AIR_SEARCH_RADAR.get()).build(null));

    public static final DeferredBlock<PowerCoreBlock> POWER_CORE = Wmd.BLOCKS.registerBlock("jet_power_core", PowerCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> POWER_CORE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("jet_power_core", POWER_CORE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PowerCoreBlockEntity>> POWER_CORE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("jet_power_core", () -> BlockEntityType.Builder.of(PowerCoreBlockEntity::new, POWER_CORE.get()).build(null));

    public static final DeferredBlock<JetCockpitBlock> JET_COCKPIT = Wmd.BLOCKS.registerBlock("jet_cockpit", p -> new JetCockpitBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> JET_COCKPIT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("jet_cockpit", JET_COCKPIT);
    public static final DeferredBlock<JetCockpitBlock> JET_COCKPIT_TANDEM = Wmd.BLOCKS.registerBlock("jet_cockpit_tandem", p -> new JetCockpitBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> JET_COCKPIT_TANDEM_ITEM = Wmd.ITEMS.registerSimpleBlockItem("jet_cockpit_tandem", JET_COCKPIT_TANDEM);

    public static final DeferredBlock<JetCockpitBlock> FLIGHT_DECK = Wmd.BLOCKS.registerBlock("flight_deck", p -> new JetCockpitBlock(p, false, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 15.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> FLIGHT_DECK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("flight_deck", FLIGHT_DECK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JetCockpitBlockEntity>> JET_COCKPIT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("jet_cockpit", () -> BlockEntityType.Builder.<JetCockpitBlockEntity>of((pos, st) -> new JetCockpitBlockEntity(AirBlocks.JET_COCKPIT_BE.get(), pos, st), JET_COCKPIT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JetCockpitBlockEntity>> JET_COCKPIT_TANDEM_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("jet_cockpit_tandem", () -> BlockEntityType.Builder.<JetCockpitBlockEntity>of((pos, st) -> new JetCockpitBlockEntity(AirBlocks.JET_COCKPIT_TANDEM_BE.get(), pos, st), JET_COCKPIT_TANDEM.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JetCockpitBlockEntity>> FLIGHT_DECK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("flight_deck", () -> BlockEntityType.Builder.<JetCockpitBlockEntity>of((pos, st) -> new JetCockpitBlockEntity(AirBlocks.FLIGHT_DECK_BE.get(), pos, st), FLIGHT_DECK.get()).build(null));

    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<CockpitSeatEntity>> COCKPIT_SEAT = Wmd.ENTITY_TYPES.register("cockpit_seat",
            () -> net.minecraft.world.entity.EntityType.Builder.<CockpitSeatEntity>of(CockpitSeatEntity::new, net.minecraft.world.entity.MobCategory.MISC).sized(0.5F, 0.3F).clientTrackingRange(10).build("cockpit_seat"));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<EjectionSeatEntity>> EJECTION_SEAT = Wmd.ENTITY_TYPES.register("ejection_seat",
            () -> net.minecraft.world.entity.EntityType.Builder.<EjectionSeatEntity>of(EjectionSeatEntity::new, net.minecraft.world.entity.MobCategory.MISC).sized(0.7F, 0.9F).clientTrackingRange(16).updateInterval(1).build("ejection_seat"));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<EjectedCanopyEntity>> EJECTED_CANOPY = Wmd.ENTITY_TYPES.register("ejected_canopy",
            () -> net.minecraft.world.entity.EntityType.Builder.<EjectedCanopyEntity>of(EjectedCanopyEntity::new, net.minecraft.world.entity.MobCategory.MISC).sized(0.9F, 0.6F).clientTrackingRange(16).updateInterval(2).build("ejected_canopy"));

    public static final DeferredBlock<AirStationBlock> PYLON = Wmd.BLOCKS.registerBlock("pylon", p -> new AirStationBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> PYLON_ITEM = Wmd.ITEMS.registerSimpleBlockItem("pylon", PYLON);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirStationBlockEntity>> PYLON_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("pylon", () -> BlockEntityType.Builder.<AirStationBlockEntity>of((pos, st) -> new AirStationBlockEntity(AirBlocks.PYLON_BE.get(), pos, st, false), PYLON.get()).build(null));
    public static final DeferredBlock<AirStationBlock> LAUNCH_RAIL = Wmd.BLOCKS.registerBlock("launch_rail", p -> new AirStationBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LAUNCH_RAIL_ITEM = Wmd.ITEMS.registerSimpleBlockItem("launch_rail", LAUNCH_RAIL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirStationBlockEntity>> LAUNCH_RAIL_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("launch_rail", () -> BlockEntityType.Builder.<AirStationBlockEntity>of((pos, st) -> new AirStationBlockEntity(AirBlocks.LAUNCH_RAIL_BE.get(), pos, st, true), LAUNCH_RAIL.get()).build(null));
    public static final DeferredItem<AamItem> AAM_HEAT = Wmd.ITEMS.register("aam_heat", () -> new AamItem(new net.minecraft.world.item.Item.Properties().stacksTo(4), AirMissiles.HEAT));
    public static final DeferredItem<AamItem> AAM_RADAR = Wmd.ITEMS.register("aam_radar", () -> new AamItem(new net.minecraft.world.item.Item.Properties().stacksTo(4), AirMissiles.RADAR));

    public static final DeferredBlock<NoseRadarBlock> NOSE_RADAR = Wmd.BLOCKS.registerBlock("nose_radar", NoseRadarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> NOSE_RADAR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("nose_radar", NOSE_RADAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NoseRadarBlockEntity>> NOSE_RADAR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("nose_radar", () -> BlockEntityType.Builder.of(NoseRadarBlockEntity::new, NOSE_RADAR.get()).build(null));

    public static final DeferredBlock<CountermeasureDispenserBlock> DISPENSER = Wmd.BLOCKS.registerBlock("countermeasure_dispenser", CountermeasureDispenserBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DISPENSER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("countermeasure_dispenser", DISPENSER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CountermeasureDispenserBlockEntity>> DISPENSER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("countermeasure_dispenser", () -> BlockEntityType.Builder.of(CountermeasureDispenserBlockEntity::new, DISPENSER.get()).build(null));

    public static final DeferredBlock<WingSectionBlock> WING_SECTION = Wmd.BLOCKS.registerBlock("wing_section", SableBridge::newWingSection,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> WING_SECTION_ITEM = Wmd.ITEMS.registerSimpleBlockItem("wing_section", WING_SECTION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WingSectionBlock.BE>> WING_SECTION_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("wing_section", () -> BlockEntityType.Builder.of(WingSectionBlock.BE::new, WING_SECTION.get()).build(null));
    public static final DeferredBlock<ControlSurfaceBlock> CONTROL_SURFACE = Wmd.BLOCKS.registerBlock("control_surface", SableBridge::newControlSurface,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> CONTROL_SURFACE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("control_surface", CONTROL_SURFACE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControlSurfaceBlock.BE>> CONTROL_SURFACE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("control_surface", () -> BlockEntityType.Builder.of(ControlSurfaceBlock.BE::new, CONTROL_SURFACE.get()).build(null));

    public static final DeferredBlock<WeaponsBayBlock> WEAPONS_BAY = Wmd.BLOCKS.registerBlock("weapons_bay", WeaponsBayBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> WEAPONS_BAY_ITEM = Wmd.ITEMS.registerSimpleBlockItem("weapons_bay", WEAPONS_BAY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WeaponsBayBlockEntity>> WEAPONS_BAY_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("weapons_bay", () -> BlockEntityType.Builder.of(WeaponsBayBlockEntity::new, WEAPONS_BAY.get()).build(null));
    public static final DeferredItem<AamItem> AAM_ARM = Wmd.ITEMS.register("aam_arm", () -> new AamItem(new net.minecraft.world.item.Item.Properties().stacksTo(4), AirMissiles.ARM));

    public static final DeferredItem<AamItem> AAM_AGM = Wmd.ITEMS.register("aam_agm", () -> new AamItem(new net.minecraft.world.item.Item.Properties().stacksTo(4), AirMissiles.AGM));

    public static final DeferredItem<SeekerModuleItem> RADAR_SEEKER = Wmd.ITEMS.register("radar_seeker", () -> new SeekerModuleItem(new net.minecraft.world.item.Item.Properties().stacksTo(16), MissileBuild.SEEK_RADAR));

    public static final DeferredBlock<SamSiteBlock> SAM_SITE = Wmd.BLOCKS.registerBlock("sam_site", SamSiteBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> SAM_SITE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("sam_site", SAM_SITE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SamSiteBlockEntity>> SAM_SITE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("sam_site", () -> BlockEntityType.Builder.of(SamSiteBlockEntity::new, SAM_SITE.get()).build(null));

    public static final DeferredBlock<JammerPodBlock> JAMMER_POD = Wmd.BLOCKS.registerBlock("jammer_pod", JammerPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> JAMMER_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("jammer_pod", JAMMER_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JammerPodBlockEntity>> JAMMER_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("jammer_pod", () -> BlockEntityType.Builder.of(JammerPodBlockEntity::new, JAMMER_POD.get()).build(null));

    public static final DeferredBlock<GunPodBlock> GUN_POD = Wmd.BLOCKS.registerBlock("gun_pod", GunPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(3)));
    public static final DeferredItem<BlockItem> GUN_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gun_pod", GUN_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GunPodBlockEntity>> GUN_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gun_pod", () -> BlockEntityType.Builder.of(GunPodBlockEntity::new, GUN_POD.get()).build(null));

    public static final DeferredBlock<TargetingPodBlock> TARGETING_POD = Wmd.BLOCKS.registerBlock("targeting_pod", TargetingPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(UnitBlock.light(2)));
    public static final DeferredItem<BlockItem> TARGETING_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("targeting_pod", TARGETING_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TargetingPodBlockEntity>> TARGETING_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("targeting_pod", () -> BlockEntityType.Builder.of(TargetingPodBlockEntity::new, TARGETING_POD.get()).build(null));

    public static final DeferredBlock<LandingGearBlock> LANDING_GEAR = Wmd.BLOCKS.registerBlock("landing_gear", LandingGearBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LANDING_GEAR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("landing_gear", LANDING_GEAR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LandingGearBlockEntity>> LANDING_GEAR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("landing_gear", () -> BlockEntityType.Builder.of(SableBridge::newLandingGear, LANDING_GEAR.get()).build(null));
    public static final DeferredBlock<LandingGearBlock> LANDING_GEAR_LARGE = Wmd.BLOCKS.registerBlock("landing_gear_large", p -> new LandingGearBlock(p, LandingGearBlockEntity.LARGE),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LANDING_GEAR_LARGE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("landing_gear_large", LANDING_GEAR_LARGE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LandingGearBlockEntity>> LANDING_GEAR_LARGE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("landing_gear_large", () -> BlockEntityType.Builder.of(SableBridge::newLandingGear, LANDING_GEAR_LARGE.get()).build(null));
    public static final DeferredBlock<LandingGearBlock> LANDING_GEAR_BOGIE = Wmd.BLOCKS.registerBlock("landing_gear_bogie", p -> new LandingGearBlock(p, LandingGearBlockEntity.BOGIE),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LANDING_GEAR_BOGIE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("landing_gear_bogie", LANDING_GEAR_BOGIE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LandingGearBlockEntity>> LANDING_GEAR_BOGIE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("landing_gear_bogie", () -> BlockEntityType.Builder.of(SableBridge::newLandingGear, LANDING_GEAR_BOGIE.get()).build(null));

    public static final DeferredItem<TowBarItem> TOW_BAR = Wmd.ITEMS.register("tow_bar", () -> new TowBarItem(new net.minecraft.world.item.Item.Properties().stacksTo(1)));

    public static final DeferredBlock<TailhookBlock> TAILHOOK = Wmd.BLOCKS.registerBlock("tailhook", TailhookBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TAILHOOK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("tailhook", TAILHOOK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TailhookBlockEntity>> TAILHOOK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("tailhook", () -> BlockEntityType.Builder.of(TailhookBlockEntity::new, TAILHOOK.get()).build(null));

    public static final DeferredBlock<ArrestingWireBlock> ARRESTING_WIRE = Wmd.BLOCKS.registerBlock("arresting_wire", ArrestingWireBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> ARRESTING_WIRE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("arresting_wire", ARRESTING_WIRE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArrestingWireBlockEntity>> ARRESTING_WIRE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("arresting_wire", () -> BlockEntityType.Builder.of(ArrestingWireBlockEntity::new, ARRESTING_WIRE.get()).build(null));

    public static final DeferredBlock<CatapultTrackBlock> CATAPULT_TRACK = Wmd.BLOCKS.registerBlock("catapult_track", CatapultTrackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CATAPULT_TRACK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("catapult_track", CATAPULT_TRACK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatapultTrackBlockEntity>> CATAPULT_TRACK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("catapult_track", () -> BlockEntityType.Builder.of(CatapultTrackBlockEntity::new, CATAPULT_TRACK.get()).build(null));

    public static final DeferredBlock<DeckLightBlock> DECK_LIGHT = Wmd.BLOCKS.registerBlock("deck_light", DeckLightBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().lightLevel(s -> 10));
    public static final DeferredItem<BlockItem> DECK_LIGHT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("deck_light", DECK_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckLightBlockEntity>> DECK_LIGHT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("deck_light", () -> BlockEntityType.Builder.of(DeckLightBlockEntity::new, DECK_LIGHT.get()).build(null));

    public static final DeferredBlock<DeckLiftBlock> DECK_LIFT = Wmd.BLOCKS.registerBlock("deck_lift", DeckLiftBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DECK_LIFT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("deck_lift", DECK_LIFT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckLiftBlockEntity>> DECK_LIFT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("deck_lift", () -> BlockEntityType.Builder.of(DeckLiftBlockEntity::new, DECK_LIFT.get()).build(null));

    public static final DeferredBlock<LiftFanBlock> LIFT_FAN = Wmd.BLOCKS.registerBlock("lift_fan", LiftFanBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LIFT_FAN_ITEM = Wmd.ITEMS.registerSimpleBlockItem("lift_fan", LIFT_FAN);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftFanBlockEntity>> LIFT_FAN_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("lift_fan", () -> BlockEntityType.Builder.of(SableBridge::newLiftFan, LIFT_FAN.get()).build(null));

    public static final DeferredBlock<DragChuteBlock> DRAG_CHUTE = Wmd.BLOCKS.registerBlock("drag_chute", DragChuteBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DRAG_CHUTE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("drag_chute", DRAG_CHUTE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DragChuteBlockEntity>> DRAG_CHUTE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("drag_chute", () -> BlockEntityType.Builder.of(DragChuteBlockEntity::new, DRAG_CHUTE.get()).build(null));

    public static final DeferredBlock<SurveyScannerBlock> SURVEY_SCANNER = Wmd.BLOCKS.registerBlock("survey_scanner", SurveyScannerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SURVEY_SCANNER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("survey_scanner", SURVEY_SCANNER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SurveyScannerBlockEntity>> SURVEY_SCANNER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("survey_scanner", () -> BlockEntityType.Builder.of(SurveyScannerBlockEntity::new, SURVEY_SCANNER.get()).build(null));

    public static final DeferredBlock<GantryRailBlock> GANTRY_RAIL = Wmd.BLOCKS.registerBlock("gantry_rail", GantryRailBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 10.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> GANTRY_RAIL_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gantry_rail", GANTRY_RAIL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GantryRailBlockEntity>> GANTRY_RAIL_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gantry_rail", () -> BlockEntityType.Builder.of(GantryRailBlockEntity::new, GANTRY_RAIL.get()).build(null));
    public static final DeferredBlock<GantryCabinetBlock> GANTRY_CABINET = Wmd.BLOCKS.registerBlock("gantry_cabinet", GantryCabinetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> GANTRY_CABINET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("gantry_cabinet", GANTRY_CABINET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GantryCabinetBlockEntity>> GANTRY_CABINET_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("gantry_cabinet", () -> BlockEntityType.Builder.of(GantryCabinetBlockEntity::new, GANTRY_CABINET.get()).build(null));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<GantryMenu>> GANTRY_MENU =
            Wmd.MENU_TYPES.register("gantry_cabinet", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(GantryMenu::new));

    public static final DeferredBlock<HeliRotorBlock> HELI_ROTOR = Wmd.BLOCKS.registerBlock("heli_rotor", HeliRotorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HELI_ROTOR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("heli_rotor", HELI_ROTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeliRotorBlockEntity>> HELI_ROTOR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("heli_rotor", () -> BlockEntityType.Builder.of(SableBridge::newHeliRotor, HELI_ROTOR.get()).build(null));
    public static final DeferredBlock<HeliTailBlock> HELI_TAIL = Wmd.BLOCKS.registerBlock("heli_tail", HeliTailBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HELI_TAIL_ITEM = Wmd.ITEMS.registerSimpleBlockItem("heli_tail", HELI_TAIL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeliTailBlockEntity>> HELI_TAIL_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("heli_tail", () -> BlockEntityType.Builder.of(SableBridge::newHeliTail, HELI_TAIL.get()).build(null));
    public static final DeferredBlock<HeliTurboshaftBlock> HELI_TURBOSHAFT = Wmd.BLOCKS.registerBlock("heli_turboshaft", HeliTurboshaftBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HELI_TURBOSHAFT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("heli_turboshaft", HELI_TURBOSHAFT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeliTurboshaftBlockEntity>> HELI_TURBOSHAFT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("heli_turboshaft", () -> BlockEntityType.Builder.of(SableBridge::newHeliTurboshaft, HELI_TURBOSHAFT.get()).build(null));

    public static final DeferredBlock<JetEngineBlock> SINGLE_JET = Wmd.BLOCKS.registerBlock("single_jet", p -> new JetEngineBlock(p, JetEngineBlock.Kind.SINGLE),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SINGLE_JET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("single_jet", SINGLE_JET);
    public static final DeferredBlock<JetEngineBlock> TWIN_JET = Wmd.BLOCKS.registerBlock("twin_jet", p -> new JetEngineBlock(p, JetEngineBlock.Kind.TWIN),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TWIN_JET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("twin_jet", TWIN_JET);
    public static final DeferredBlock<JetEngineBlock> STEALTH_JET = Wmd.BLOCKS.registerBlock("stealth_jet", p -> new JetEngineBlock(p, JetEngineBlock.Kind.STEALTH),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> STEALTH_JET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("stealth_jet", STEALTH_JET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JetEngineBlockEntity>> JET_ENGINE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("buried_jet", () -> BlockEntityType.Builder.of(SableBridge::newJetEngine, SINGLE_JET.get(), TWIN_JET.get(), STEALTH_JET.get()).build(null));

    public static final DeferredBlock<TurbopropBlock> TURBOPROP = Wmd.BLOCKS.registerBlock("turboprop", TurbopropBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TURBOPROP_ITEM = Wmd.ITEMS.registerSimpleBlockItem("turboprop", TURBOPROP);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TurbopropBlockEntity>> TURBOPROP_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("turboprop", () -> BlockEntityType.Builder.of(SableBridge::newTurboprop, TURBOPROP.get()).build(null));
    public static final DeferredBlock<TiltrotorBlock> TILTROTOR = Wmd.BLOCKS.registerBlock("tiltrotor", TiltrotorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> TILTROTOR_ITEM = Wmd.ITEMS.registerSimpleBlockItem("tiltrotor", TILTROTOR);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TiltrotorBlockEntity>> TILTROTOR_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("tiltrotor", () -> BlockEntityType.Builder.of(SableBridge::newTiltrotor, TILTROTOR.get()).build(null));
    public static final DeferredBlock<ShipTurbineBlock> SHIP_TURBINE = Wmd.BLOCKS.registerBlock("ship_turbine", ShipTurbineBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> SHIP_TURBINE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("ship_turbine", SHIP_TURBINE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipTurbineBlockEntity>> SHIP_TURBINE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("ship_turbine", () -> BlockEntityType.Builder.of(ShipTurbineBlockEntity::new, SHIP_TURBINE.get()).build(null));
    public static final DeferredBlock<WaterjetBlock> WATERJET = Wmd.BLOCKS.registerBlock("waterjet", WaterjetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> WATERJET_ITEM = Wmd.ITEMS.registerSimpleBlockItem("waterjet", WATERJET);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterjetBlockEntity>> WATERJET_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("waterjet", () -> BlockEntityType.Builder.of(SableBridge::newWaterjet, WATERJET.get()).build(null));
    public static final DeferredBlock<AzimuthPodBlock> AZIMUTH_POD = Wmd.BLOCKS.registerBlock("azimuth_pod", AzimuthPodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> AZIMUTH_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("azimuth_pod", AZIMUTH_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AzimuthPodBlockEntity>> AZIMUTH_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("azimuth_pod", () -> BlockEntityType.Builder.of(SableBridge::newAzimuthPod, AZIMUTH_POD.get()).build(null));
    public static final DeferredBlock<BowThrusterBlock> BOW_THRUSTER = Wmd.BLOCKS.registerBlock("bow_thruster", BowThrusterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> BOW_THRUSTER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("bow_thruster", BOW_THRUSTER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BowThrusterBlockEntity>> BOW_THRUSTER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("bow_thruster", () -> BlockEntityType.Builder.of(SableBridge::newBowThruster, BOW_THRUSTER.get()).build(null));

    public static final DeferredBlock<CargoHookBlock> CARGO_HOOK_LIGHT = Wmd.BLOCKS.registerBlock("cargo_hook_light", p -> new CargoHookBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CARGO_HOOK_LIGHT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("cargo_hook_light", CARGO_HOOK_LIGHT);
    public static final DeferredBlock<CargoHookBlock> CARGO_HOOK_HEAVY = Wmd.BLOCKS.registerBlock("cargo_hook_heavy", p -> new CargoHookBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> CARGO_HOOK_HEAVY_ITEM = Wmd.ITEMS.registerSimpleBlockItem("cargo_hook_heavy", CARGO_HOOK_HEAVY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoHookBlockEntity>> CARGO_HOOK_LIGHT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("cargo_hook_light", () -> BlockEntityType.Builder.of(SableBridge::newCargoHook, CARGO_HOOK_LIGHT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoHookBlockEntity>> CARGO_HOOK_HEAVY_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("cargo_hook_heavy", () -> BlockEntityType.Builder.of(SableBridge::newCargoHook, CARGO_HOOK_HEAVY.get()).build(null));
    public static final DeferredBlock<DroguePodBlock> DROGUE_POD = Wmd.BLOCKS.registerBlock("drogue_pod", DroguePodBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DROGUE_POD_ITEM = Wmd.ITEMS.registerSimpleBlockItem("drogue_pod", DROGUE_POD);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DroguePodBlockEntity>> DROGUE_POD_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("drogue_pod", () -> BlockEntityType.Builder.of(DroguePodBlockEntity::new, DROGUE_POD.get()).build(null));
    public static final DeferredBlock<RefuelProbeBlock> REFUEL_PROBE = Wmd.BLOCKS.registerBlock("refuel_probe", RefuelProbeBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> REFUEL_PROBE_ITEM = Wmd.ITEMS.registerSimpleBlockItem("refuel_probe", REFUEL_PROBE);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RefuelProbeBlockEntity>> REFUEL_PROBE_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("refuel_probe", () -> BlockEntityType.Builder.of(RefuelProbeBlockEntity::new, REFUEL_PROBE.get()).build(null));

    public static final DeferredBlock<NavLightBlock> NAV_LIGHT = Wmd.BLOCKS.registerBlock("nav_light", NavLightBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion().noCollission()
                    .lightLevel(NavLightBlock::lightOf));
    public static final DeferredItem<BlockItem> NAV_LIGHT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("nav_light", NAV_LIGHT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavLightBlockEntity>> NAV_LIGHT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("nav_light", () -> BlockEntityType.Builder.of(NavLightBlockEntity::new, NAV_LIGHT.get()).build(null));

    public static final DeferredBlock<FuelTankBlock> FUEL_TANK = Wmd.BLOCKS.registerBlock("fuel_tank", p -> new FuelTankBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> FUEL_TANK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("fuel_tank", FUEL_TANK);
    public static final DeferredBlock<FuelTankBlock> DROP_TANK = Wmd.BLOCKS.registerBlock("drop_tank", p -> new FuelTankBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> DROP_TANK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("drop_tank", DROP_TANK);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelTankBlockEntity>> FUEL_TANK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("fuel_tank", () -> BlockEntityType.Builder.of(FuelTankBlockEntity::new, FUEL_TANK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FuelTankBlockEntity>> DROP_TANK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("drop_tank", () -> BlockEntityType.Builder.of(FuelTankBlockEntity::new, DROP_TANK.get()).build(null));

    public static final DeferredBlock<AmmoDepotBlock> AMMO_DEPOT = Wmd.BLOCKS.registerBlock("ammo_depot", AmmoDepotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 40.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> AMMO_DEPOT_ITEM = Wmd.ITEMS.registerSimpleBlockItem("ammo_depot", AMMO_DEPOT);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AmmoDepotBlockEntity>> AMMO_DEPOT_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("ammo_depot", () -> BlockEntityType.Builder.of(AmmoDepotBlockEntity::new, AMMO_DEPOT.get()).build(null));
    public static final DeferredBlock<OrdnanceLoaderBlock> ORDNANCE_LOADER = Wmd.BLOCKS.registerBlock("ordnance_loader", p -> new OrdnanceLoaderBlock(p, false),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 20.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> ORDNANCE_LOADER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("ordnance_loader", ORDNANCE_LOADER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrdnanceLoaderBlockEntity>> ORDNANCE_LOADER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("ordnance_loader", () -> BlockEntityType.Builder.of(OrdnanceLoaderBlockEntity::new, ORDNANCE_LOADER.get()).build(null));
    public static final DeferredBlock<OrdnanceLoaderBlock> HEAVY_LOADER = Wmd.BLOCKS.registerBlock("heavy_loader", p -> new OrdnanceLoaderBlock(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(8.0F, 30.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> HEAVY_LOADER_ITEM = Wmd.ITEMS.registerSimpleBlockItem("heavy_loader", HEAVY_LOADER);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OrdnanceLoaderBlockEntity>> HEAVY_LOADER_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("heavy_loader", () -> BlockEntityType.Builder.of(OrdnanceLoaderBlockEntity::new, HEAVY_LOADER.get()).build(null));
    public static final DeferredBlock<LoaderTrackBlock> LOADER_TRACK = Wmd.BLOCKS.registerBlock("loader_track", LoaderTrackBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 12.0F).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK).noOcclusion());
    public static final DeferredItem<BlockItem> LOADER_TRACK_ITEM = Wmd.ITEMS.registerSimpleBlockItem("loader_track", LOADER_TRACK);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<AmmoDepotMenu>> AMMO_DEPOT_MENU =
            Wmd.MENU_TYPES.register("ammo_depot", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(AmmoDepotMenu::new));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoaderTrackBlockEntity>> LOADER_TRACK_BE =
            Wmd.BLOCK_ENTITY_TYPES.register("loader_track", () -> BlockEntityType.Builder.of(LoaderTrackBlockEntity::new, LOADER_TRACK.get()).build(null));

    public static final DeferredItem<FlightCheckItem> FLIGHT_CHECK_TABLET = Wmd.ITEMS.register("flight_check_tablet", () -> new FlightCheckItem(new net.minecraft.world.item.Item.Properties()));

    static void init() {
        if (!AirGate.AVAILABLE) throw new IllegalStateException("1.2 blocks registered with the air gate unavailable");
        ServiceBlocks.init();
        GunnerBlocks.init();
        RocketPods.init();
        AntiShipItems.init();
        CargoBlocks.init();
        HangarDoorBlocks.init();
        DeckConsoleBlocks.init();
        TurntableBlocks.init();
        NavalGuns.init();
        AnchorBlocks.init();
        ChartTableBlocks.init();
        RfPowerBlocks.init();
    }

    static void capabilities(RegisterCapabilitiesEvent event) {
        ServiceBlocks.capabilities(event);
        CargoBlocks.capabilities(event);
        HangarDoorBlocks.capabilities(event);
        DeckConsoleBlocks.capabilities(event);
        TurntableBlocks.capabilities(event);
        AnchorBlocks.capabilities(event);
        ChartTableBlocks.capabilities(event);
        RfPowerBlocks.capabilities(event);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AIR_SEARCH_RADAR_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SAM_SITE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, POWER_CORE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ARRESTING_WIRE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, CATAPULT_TRACK_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, DECK_LIFT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, POWER_CORE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SHIP_TURBINE_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SHIP_TURBINE_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AZIMUTH_POD_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BOW_THRUSTER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, FUEL_TANK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, DROP_TANK_BE.get(), (be, side) -> be.fluidPort(be.getBlockPos(), side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, AMMO_DEPOT_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, AMMO_DEPOT_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ORDNANCE_LOADER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, HEAVY_LOADER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SURVEY_SCANNER_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GANTRY_CABINET_BE.get(), (be, side) -> Grid.gridOnly(be.energyPort(be.getBlockPos(), side)));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GANTRY_CABINET_BE.get(), (be, side) -> ItemPorts.wmdOnly(be.itemPort(be.getBlockPos(), side)));
    }

    static void navyTab(CreativeModeTab.Output output) {
        output.accept(SHIP_TURBINE_ITEM.get());
        output.accept(WATERJET_ITEM.get());
        output.accept(AZIMUTH_POD_ITEM.get());
        output.accept(BOW_THRUSTER_ITEM.get());
        NavalGuns.navyTab(output);
        AnchorBlocks.navyTab(output);
        ChartTableBlocks.navyTab(output);
    }

    static void tab(CreativeModeTab.Output output) {
        ServiceBlocks.tab(output);
        GunnerBlocks.tab(output);
        RocketPods.tab(output);
        AntiShipItems.tab(output);
        CargoBlocks.tab(output);
        HangarDoorBlocks.tab(output);
        DeckConsoleBlocks.tab(output);
        TurntableBlocks.tab(output);
        RfPowerBlocks.tab(output);
        output.accept(AIR_SEARCH_RADAR_ITEM.get());
        output.accept(SAM_SITE_ITEM.get());
        output.accept(AMMO_DEPOT_ITEM.get());
        output.accept(ORDNANCE_LOADER_ITEM.get());
        output.accept(HEAVY_LOADER_ITEM.get());
        output.accept(LOADER_TRACK_ITEM.get());
        output.accept(POWER_CORE_ITEM.get());
        output.accept(JET_COCKPIT_ITEM.get());
        output.accept(JET_COCKPIT_TANDEM_ITEM.get());
        output.accept(FLIGHT_DECK_ITEM.get());
        output.accept(PYLON_ITEM.get());
        output.accept(LAUNCH_RAIL_ITEM.get());
        output.accept(AAM_HEAT.get());
        output.accept(AAM_RADAR.get());
        output.accept(AAM_ARM.get());
        output.accept(AAM_AGM.get());
        output.accept(RADAR_SEEKER.get());
        output.accept(WEAPONS_BAY_ITEM.get());
        output.accept(JAMMER_POD_ITEM.get());
        output.accept(GUN_POD_ITEM.get());
        output.accept(TARGETING_POD_ITEM.get());
        output.accept(LANDING_GEAR_ITEM.get());
        output.accept(LANDING_GEAR_LARGE_ITEM.get());
        output.accept(LANDING_GEAR_BOGIE_ITEM.get());
        output.accept(TOW_BAR.get());
        output.accept(TAILHOOK_ITEM.get());
        output.accept(ARRESTING_WIRE_ITEM.get());
        output.accept(CATAPULT_TRACK_ITEM.get());
        output.accept(DECK_LIGHT_ITEM.get());
        output.accept(DECK_LIFT_ITEM.get());
        output.accept(NOSE_RADAR_ITEM.get());
        output.accept(DISPENSER_ITEM.get());
        output.accept(WING_SECTION_ITEM.get());
        output.accept(CONTROL_SURFACE_ITEM.get());
        output.accept(FLIGHT_CHECK_TABLET.get());
        output.accept(LIFT_FAN_ITEM.get());
        output.accept(DRAG_CHUTE_ITEM.get());
        output.accept(SURVEY_SCANNER_ITEM.get());
        output.accept(GANTRY_RAIL_ITEM.get());
        output.accept(GANTRY_CABINET_ITEM.get());
        output.accept(HELI_ROTOR_ITEM.get());
        output.accept(HELI_TAIL_ITEM.get());
        output.accept(HELI_TURBOSHAFT_ITEM.get());
        output.accept(TURBOPROP_ITEM.get());
        output.accept(TILTROTOR_ITEM.get());
        output.accept(CARGO_HOOK_LIGHT_ITEM.get());
        output.accept(CARGO_HOOK_HEAVY_ITEM.get());
        output.accept(DROGUE_POD_ITEM.get());
        output.accept(REFUEL_PROBE_ITEM.get());
        output.accept(NAV_LIGHT_ITEM.get());
        output.accept(FUEL_TANK_ITEM.get());
        output.accept(DROP_TANK_ITEM.get());
        output.accept(SINGLE_JET_ITEM.get());
        output.accept(TWIN_JET_ITEM.get());
        output.accept(STEALTH_JET_ITEM.get());
    }
}
