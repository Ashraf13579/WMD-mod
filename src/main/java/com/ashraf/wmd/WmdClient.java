package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Wmd.MODID, dist = Dist.CLIENT)

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class WmdClient {
    public WmdClient(ModContainer container) {

        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    @SubscribeEvent
    static void registerModelLoaders(net.neoforged.neoforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
        event.register(PipeModel.LOADER_ID, PipeModel.Loader.INSTANCE);
        event.register(DuctModel.LOADER_ID, DuctModel.Loader.INSTANCE);
    }

    static int pipeTint(int argb) {
        if (argb == 0) return 0x626262;
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (Math.min(255, (int) (r * 1.3 + 25)) << 16) | (Math.min(255, (int) (g * 1.3 + 25)) << 8) | Math.min(255, (int) (b * 1.3 + 25));
    }

    @SubscribeEvent
    static void registerBlockColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (tint != 0) return -1;
            if (level != null && pos != null && level.getBlockEntity(pos) instanceof FluidPipeBlockEntity be) return pipeTint(be.fluidColour());
            return 0x626262;
        }, Wmd.FLUID_PIPE.get(), Wmd.ALUMINIUM_PIPE.get(), Wmd.TITANIUM_PIPE.get(), Wmd.FLUID_VALVE.get());
    }

    @SubscribeEvent
    static void registerItemColours(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0x626262 : -1, Wmd.FLUID_PIPE_ITEM.get(), Wmd.ALUMINIUM_PIPE_ITEM.get(), Wmd.TITANIUM_PIPE_ITEM.get(), Wmd.FLUID_VALVE_ITEM.get());
        event.register((stack, tint) -> tint == 1 ? FluidCanisterItem.bandColour(stack) : -1, Wmd.CANISTER.get());
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Wmd.MISSILE_ENTITY.get(),
                context -> new ThrownItemRenderer<>(context, 2.0F, true));

        MountedRenderer.register(event, Wmd.DIESEL_GENERATOR_BE.get(),
                context -> new DieselGeneratorRenderer());
        MountedRenderer.register(event, Wmd.RADAR_BE.get(),
                context -> new RadarRenderer());
        MountedRenderer.register(event, Wmd.ALARM_BE.get(),
                context -> new AlarmRenderer());
        MountedRenderer.register(event, Wmd.LAUNCHER_BE.get(),
                context -> new InterceptorLauncherRenderer());
        MountedRenderer.register(event, Wmd.SILO_BE.get(),
                context -> new InterceptorSiloRenderer());
        MountedRenderer.register(event, Wmd.SILO4_BE.get(),
                context -> new InterceptorSiloT4Renderer());
        MountedRenderer.register(event, Wmd.HEAVY_BATTERY_BE.get(),
                context -> new InterceptorBatteryRenderer());
        MountedRenderer.register(event, Wmd.CIWS_BE.get(),
                context -> new CiwsRenderer());
        MountedRenderer.register(event, Wmd.SALVAGE_TURRET_BE.get(),
                context -> new SalvageTurretRenderer());
        MountedRenderer.register(event, Wmd.LAB_BE.get(),
                context -> new LabModuleRenderer());
        MountedRenderer.register(event, Wmd.WARHEAD_REACTOR_BE.get(),
                context -> new WarheadReactorRenderer());
        MountedRenderer.register(event, Wmd.PUMPJACK_BE.get(),
                context -> new PumpjackRenderer());
        MountedRenderer.register(event, Wmd.CRUDE_TANK_BE.get(),
                context -> new CrudeTankRenderer());
        MountedRenderer.register(event, Wmd.STORAGE_TANK_BE.get(),
                context -> new StorageTankRenderer());
        MountedRenderer.register(event, Wmd.FEED_PUMP_BE.get(),
                context -> new RefineryUnitRenderer<FeedPumpBlockEntity>("feed_pump", 1));
        MountedRenderer.register(event, Wmd.GAS_TURBINE_BE.get(),
                context -> new RefineryUnitRenderer<GasTurbineBlockEntity>("gas_turbine", 3));
        MountedRenderer.register(event, Wmd.GRID_CONVERTER_BE.get(),
                context -> new RefineryUnitRenderer<GridConverterBlockEntity>("grid_converter", 2));
        MountedRenderer.register(event, Wmd.MAGNESIUM_CELL_BE.get(),
                context -> new RefineryUnitRenderer<MagnesiumCellBlockEntity>("magnesium_cell", 2));

        MountedRenderer.register(event, Wmd.RESEARCH_STATION_BE.get(),
                context -> new ResearchStationRenderer());

        MountedRenderer.register(event, Wmd.ROLLING_MILL_BE.get(),
                context -> new StagedUnitRenderer<RollingMillBlockEntity>("rolling_mill", 2));
        MountedRenderer.register(event, Wmd.WIRE_DRAWER_BE.get(),
                context -> new StagedUnitRenderer<WireDrawerBlockEntity>("wire_drawer", 2));

        MountedRenderer.register(event, Wmd.ROASTER_BE.get(),
                context -> new StagedUnitRenderer<RoasterBlockEntity>("roaster", 2));
        MountedRenderer.register(event, Wmd.COKE_OVEN_BE.get(),
                context -> new StagedUnitRenderer<CokeOvenBlockEntity>("coke_oven", 2));
        MountedRenderer.register(event, Wmd.STARTER_GENERATOR_BE.get(),
                context -> new StagedUnitRenderer<StarterGeneratorBlockEntity>("starter_generator", 2));
        MountedRenderer.register(event, Wmd.SOLAR_PANEL_BE.get(), context -> new StagedUnitRenderer<SolarPanelBlockEntity>("solar_panel", 2));
        MountedRenderer.register(event, Wmd.LAVA_GENERATOR_BE.get(), context -> new StagedUnitRenderer<LavaGeneratorBlockEntity>("lava_generator", 2));
        MountedRenderer.register(event, Wmd.STEAM_ENGINE_BE.get(), context -> new StagedUnitRenderer<SteamEngineBlockEntity>("steam_engine", 3));
        MountedRenderer.register(event, Wmd.GASOLINE_ENGINE_BE.get(), context -> new StagedUnitRenderer<GasolineEngineBlockEntity>("gasoline_engine", 3));
        MountedRenderer.register(event, Wmd.FREE_PISTON_GENERATOR_BE.get(), context -> new StagedUnitRenderer<FreePistonGeneratorBlockEntity>("free_piston_generator", 3));
        MountedRenderer.register(event, Wmd.WIND_TURBINE_BE.get(), context -> new StagedUnitRenderer<WindTurbineBlockEntity>("wind_turbine", 19, 4));
        MountedRenderer.register(event, Wmd.STAMP_MILL_BE.get(),
                context -> new StagedUnitRenderer<StampMillBlockEntity>("stamp_mill", 3));
        MountedRenderer.register(event, Wmd.POWDER_MILL_BE.get(),
                context -> new StagedUnitRenderer<PowderMillBlockEntity>("powder_mill", 2));
        MountedRenderer.register(event, Wmd.BATTERY_T1_BE.get(), context -> new StagedUnitRenderer<BatteryTierBlockEntity>("battery_t1", 1));
        MountedRenderer.register(event, Wmd.BATTERY_T2_BE.get(), context -> new StagedUnitRenderer<BatteryTierBlockEntity>("battery_t2_cabinet", 2));
        MountedRenderer.register(event, Wmd.BATTERY_T3_BE.get(), context -> new StagedUnitRenderer<BatteryTierBlockEntity>("battery_t3", 3));
        MountedRenderer.register(event, Wmd.BATTERY_T4_BE.get(), context -> new StagedUnitRenderer<BatteryTierBlockEntity>("battery_t4", 3));
        MountedRenderer.register(event, Wmd.SENTRY_TURRET_BE.get(), context -> new StagedUnitRenderer<SentryTurretBlockEntity>("sentry_turret", 2));
        MountedRenderer.register(event, Wmd.FENCE_ENERGIZER_BE.get(), context -> new StagedUnitRenderer<FenceEnergizerBlockEntity>("fence_energizer", 1));
        MountedRenderer.register(event, Wmd.MINE_BE.get(), context -> new MineRenderer());
        MountedRenderer.register(event, Wmd.CCTV_CAMERA_BE.get(), context -> new StagedUnitRenderer<CctvCameraBlockEntity>("cctv_camera", 1));
        MountedRenderer.register(event, Wmd.COVERT_CAMERA_BE.get(), context -> new StagedUnitRenderer<CctvCameraBlockEntity>("covert_camera", 1));
        MountedRenderer.register(event, Wmd.CAMERA_RELAY_BE.get(), context -> new StagedUnitRenderer<CameraRelayBlockEntity>("camera_relay", 3));
        MountedRenderer.register(event, Wmd.PERIMETER_SENSOR_BE.get(), context -> new CctvRenderers.Sensor());
        MountedRenderer.register(event, Wmd.FLOODLIGHT_BE.get(), context -> new CctvRenderers.Floodlight());
        MountedRenderer.register(event, Wmd.CC_SECURITY_MONITOR_BE.get(), context -> new CctvRenderers.Monitor());
        MountedRenderer.register(event, Wmd.C4_CHARGE_BE.get(), context -> new C4Renderer());
        MountedRenderer.register(event, Wmd.PORTABLE_JAMMER_BE.get(), context -> new StagedUnitRenderer<PortableJammerBlockEntity>("portable_jammer", 1));
        MountedRenderer.register(event, Wmd.SEISMIC_SCANNER_BE.get(), context -> new StagedUnitRenderer<SeismicScannerBlockEntity>("seismic_scanner", 2));
        MountedRenderer.register(event, Wmd.BLAST_FURNACE_BE.get(),
                context -> new StagedUnitRenderer<BlastFurnaceBlockEntity>("blast_furnace", 3));
        MountedRenderer.register(event, Wmd.HYDROGEN_FURNACE_BE.get(),
                context -> new StagedUnitRenderer<HydrogenFurnaceBlockEntity>("hydrogen_furnace", 3));
        MountedRenderer.register(event, Wmd.SINTER_PRESS_BE.get(),
                context -> new StagedUnitRenderer<SinterPressBlockEntity>("sinter_press", 2));

        MountedRenderer.register(event, Wmd.LEACH_TANK_BE.get(),
                context -> new StagedUnitRenderer<LeachTankBlockEntity>("leach_tank", 2));
        MountedRenderer.register(event, Wmd.CENTRIFUGE_BE.get(),
                context -> new StagedUnitRenderer<CentrifugeBlockEntity>("centrifuge", 3));

        MountedRenderer.register(event, Wmd.NUCLEAR_REACTOR_BE.get(),
                context -> new NuclearReactorRenderer());
        MountedRenderer.register(event, Wmd.COOLING_TOWER_BE.get(),
                context -> new StagedUnitRenderer<CoolingTowerBlockEntity>("cooling_tower_xl", 16, 12));
        MountedRenderer.register(event, Wmd.STEAM_TURBINE_BE.get(),
                context -> new StagedUnitRenderer<SteamTurbineBlockEntity>("steam_turbine", 4));
        MountedRenderer.register(event, Wmd.REPROCESSING_PLANT_BE.get(),
                context -> new StagedUnitRenderer<ReprocessingPlantBlockEntity>("reprocessing_plant", 3));

        MountedRenderer.register(event, Wmd.FUSION_REACTOR_BE.get(),
                context -> new StagedUnitRenderer<FusionReactorBlockEntity>("fusion_reactor", 13, 17));

        MountedRenderer.register(event, Wmd.FORCEFIELD_BE.get(),
                context -> new StagedUnitRenderer<ForcefieldBlockEntity>("forcefield_generator", 9, 4));

        MountedRenderer.register(event, Wmd.LASER_DEFENCE_BE.get(),
                context -> new StagedUnitRenderer<LaserDefenceBlockEntity>("laser_defence", 5, 2));

        MountedRenderer.register(event, Wmd.SWARM_LAUNCHER_BE.get(),
                context -> new StagedUnitRenderer<SwarmLauncherBlockEntity>("swarm_launcher", 3, 3));

        MountedRenderer.register(event, Wmd.DEADHAND_BE.get(),
                context -> new StagedUnitRenderer<DeadHandBlockEntity>("deadhand_bunker", 4, 3));

        MountedRenderer.register(event, Wmd.CREW_CAPSULE_BE.get(), context -> new CrewCapsuleRenderer());
        event.registerBlockEntityRenderer(Wmd.STATION_MODULE_BE.get(), context -> new StationModuleRenderer());
        event.registerBlockEntityRenderer(Wmd.STATION_FIXTURE_BE.get(), context -> new StationFixtureRenderer());
        event.registerBlockEntityRenderer(Wmd.CARGO_POD_BE.get(), context -> new CargoPodRenderer());
        MountedRenderer.register(event, Wmd.OXYGEN_STATION_BE.get(),
                context -> new RefineryUnitRenderer<OxygenStationBlockEntity>("oxygen_station", 2));
        MountedRenderer.register(event, Wmd.ANTIMATTER_COLLIDER_BE.get(),
                context -> new StagedUnitRenderer<AntimatterColliderBlockEntity>("antimatter_collider", 9, 33));
        MountedRenderer.register(event, Wmd.ANTIMATTER_STORE_BE.get(),
                context -> new StagedUnitRenderer<AntimatterStoreBlockEntity>("antimatter_store", 4));

        MountedRenderer.register(event, Wmd.RAILGUN_BE.get(),
                context -> new StagedUnitRenderer<RailgunBlockEntity>("railgun", 10, 30));

        MountedRenderer.register(event, Wmd.GROUND_STATION_BE.get(),
                context -> new GroundStationRenderer());

        MountedRenderer.register(event, Wmd.EARLY_WARNING_ARRAY_BE.get(), context -> new StagedUnitRenderer<EarlyWarningArrayBlockEntity>("early_warning_array", 18, 9));
        event.registerEntityRenderer(Wmd.RECON_DRONE_ENTITY.get(), ReconDroneRenderer::new);
        event.registerEntityRenderer(Wmd.STRIKE_DRONE_ENTITY.get(), StrikeDroneRenderer::new);
        event.registerEntityRenderer(Wmd.BIG_DRONE_ENTITY.get(), StrikeDroneRenderer::new);
        event.registerEntityRenderer(Wmd.PILOT_BODY.get(), PilotBodyRenderer::new);
        event.registerEntityRenderer(Wmd.TURRET_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(Wmd.CREW_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(Wmd.HAND_MISSILE.get(), HandMissileRenderer::new);
        event.registerEntityRenderer(Wmd.BOMB.get(), BombRenderer::new);
        event.registerEntityRenderer(Wmd.XL_TRANSPORTER.get(), XlTransporterRenderer::new);
        event.registerEntityRenderer(Wmd.PARTS_RUNNER.get(), PartsRunnerRenderer::new);
        event.registerEntityRenderer(Wmd.SENTRY_DOG.get(), SentryDogRenderer::new);
        event.registerEntityRenderer(Wmd.MISSILE_ROBOT.get(), MissileRobotRenderer::new);

        MountedRenderer.register(event, Wmd.SATELLITE_FACILITY_BE.get(),
                context -> new SatelliteFacilityRenderer());

        MountedRenderer.register(event, Wmd.MORTAR_BE.get(), context -> new StagedUnitRenderer<MortarBlockEntity>("mortar", 3, 2));
        MountedRenderer.register(event, Wmd.HOWITZER_BE.get(), context -> new StagedUnitRenderer<HowitzerBlockEntity>("howitzer", 6, 6));
        MountedRenderer.register(event, Wmd.SHELL_PRESS_BE.get(), context -> new StagedUnitRenderer<ShellPressBlockEntity>("shell_press", 4, 4));

        MountedRenderer.register(event, Wmd.ALKYLATION_UNIT_BE.get(), context -> new RefineryUnitRenderer<AlkylationUnitBlockEntity>("alkylation_unit", 10));
        MountedRenderer.register(event, Wmd.CRUISE_LAUNCHER_BE.get(), context -> new StagedUnitRenderer<CruiseLauncherBlockEntity>("cruise_launcher", 6, 7));

        MountedRenderer.register(event, Wmd.SUPER_GUN_BE.get(), context -> new StagedUnitRenderer<SuperGunBlockEntity>("super_gun", 17, 34));

        MountedRenderer.register(event, Wmd.CC_MAP_TABLE_BE.get(), context -> new CcRenderers.MapTable());
        MountedRenderer.register(event, Wmd.CC_WARNING_BOARD_BE.get(), context -> new CcRenderers.WarningBoard());
        MountedRenderer.register(event, Wmd.CC_RECON_WALL_BE.get(), context -> new CcRenderers.ReconWall());
        MountedRenderer.register(event, Wmd.CC_AIR_DEFENCE_BE.get(), context -> new StagedUnitRenderer<CcAirDefenceBlockEntity>("cc_air_defence", 2));
        MountedRenderer.register(event, Wmd.CC_BUILDING_BE.get(), context -> new CcBuildingRenderer());
        MountedRenderer.register(event, Wmd.CC_LAUNCH_CONSOLE_BE.get(), context -> new CcRenderersB.LaunchConsole());
        MountedRenderer.register(event, Wmd.CC_RETARGET_CONSOLE_BE.get(), context -> new CcRenderersB.RetargetConsole());
        MountedRenderer.register(event, Wmd.CC_STRIKE_PLANNER_BE.get(), context -> new CcRenderersB.StrikePlanner());
        MountedRenderer.register(event, Wmd.CC_MISSILE_CAM_BE.get(), context -> new CcRenderersB.MissileCam());
        MountedRenderer.register(event, Wmd.CC_INTERCEPTOR_CONSOLE_BE.get(), context -> new CcRenderersS5.InterceptorConsole());

        MountedRenderer.register(event, Wmd.COUNTER_BATTERY_RADAR_BE.get(), context -> new StagedUnitRenderer<CounterBatteryRadarBlockEntity>("counter_battery_radar", 7, 3));
        MountedRenderer.register(event, Wmd.CC_ARTILLERY_CONSOLE_BE.get(), context -> new CcRenderersS52.ArtilleryConsole());
        MountedRenderer.register(event, Wmd.CC_IRON_DOME_BE.get(), context -> new CcRenderersS52.IronDomeDesk());
        MountedRenderer.register(event, Wmd.CC_PLANNER_BE.get(), context -> new CcRenderersS52.PlannerDesk());
        MountedRenderer.register(event, Wmd.ROBOT_DOCK_BE.get(), context -> new RobotDockRenderer());
        MountedRenderer.register(event, Wmd.XL_DOCK_BE.get(), context -> new XlDockRenderer());
        MountedRenderer.register(event, Wmd.RUNNER_CHARGER_BE.get(), context -> new RunnerChargerRenderer());
        MountedRenderer.register(event, Wmd.LASER_TRIPOD_BE.get(), context -> new LaserTripodRenderer());
        MountedRenderer.register(event, Wmd.LASER_RECEIVER_BE.get(), context -> new LaserReceiverRenderer());
        MountedRenderer.register(event, Wmd.MISSILE_DEPOT_BE.get(), context -> new MissileDepotRenderer());
        MountedRenderer.register(event, Wmd.MISSILE_TELEPORTER_BE.get(), context -> new MissileTeleporterRenderer());

        MountedRenderer.register(event, Wmd.MLRS_BE.get(), context -> new StagedUnitRenderer<MlrsBlockEntity>("mlrs", 7, 9));
        MountedRenderer.register(event, Wmd.DECOY_LAUNCHER_BE.get(), context -> new StagedUnitRenderer<DecoyBlockEntity.Launcher>("decoy_launcher", 2, 4));
        MountedRenderer.register(event, Wmd.DECOY_RADAR_BE.get(), context -> new StagedUnitRenderer<DecoyBlockEntity.Radar>("decoy_radar", 4, 3));
        MountedRenderer.register(event, Wmd.CAMO_NET_BE.get(), context -> new CamoNetRenderer());
        MountedRenderer.register(event, Wmd.THERMAL_NET_BE.get(), context -> new CamoNetRenderer());
        MountedRenderer.register(event, Wmd.SMOKE_MACHINE_BE.get(), context -> new StagedUnitRenderer<SmokeMachineBlockEntity>("smoke_machine", 2));
        MountedRenderer.register(event, Wmd.BOMB_RACK_BE.get(), context -> new BombRackRenderer("bomb_rack", 1, 4));
        MountedRenderer.register(event, Wmd.BOMB_BAY_BE.get(), context -> new BombRackRenderer("bomb_bay", 4, 9));
        MountedRenderer.register(event, Wmd.BOMB_SIGHT_BE.get(), context -> new StagedUnitRenderer<BombSightBlockEntity>("bomb_sight", 1));
        MountedRenderer.register(event, Wmd.DRONE_BE.get(), context -> new DroneModuleRenderer());
        MountedRenderer.register(event, Wmd.BOMB_LINE_BE.get(), context -> new BombLineRenderer());
        MountedRenderer.register(event, Wmd.DRONE_HIVE_BE.get(), context -> new DroneHiveRenderer());
        MountedRenderer.register(event, Wmd.DRONE_CATAPULT_BE.get(), context -> new DroneCatapultRenderer());

        MountedRenderer.register(event, Wmd.CC_SAT_GLOBE_BE.get(), context -> new CcRenderersC.SatGlobe());
        MountedRenderer.register(event, Wmd.CC_EW_RACK_BE.get(), context -> new StagedUnitRenderer<CcEwRackBlockEntity>("cc_ew_rack", 3));
        MountedRenderer.register(event, Wmd.CC_GPS_RACK_BE.get(), context -> new CcRenderersC.GpsRack());
        MountedRenderer.register(event, Wmd.CC_ALARM_BOARD_BE.get(), context -> new CcRenderersC.AlarmBoard());
        MountedRenderer.register(event, Wmd.CC_MAP_PRINTER_BE.get(), context -> new StagedUnitRenderer<CcMapPrinterBlockEntity>("cc_map_printer", 2));
        MountedRenderer.register(event, Wmd.CC_COMMS_ARRAY_BE.get(), context -> new StagedUnitRenderer<CcCommsArrayBlockEntity>("cc_comms_array", 4));
        MountedRenderer.register(event, Wmd.WATER_INTAKE_BE.get(),
                context -> new RefineryUnitRenderer<WaterIntakeBlockEntity>("water_intake", 1));
        MountedRenderer.register(event, Wmd.CHLOR_ALKALI_BE.get(),
                context -> new RefineryUnitRenderer<ChlorAlkaliBlockEntity>("chlor_alkali_cell", 1));
        MountedRenderer.register(event, Wmd.CHLORINATOR_BE.get(),
                context -> new RefineryUnitRenderer<ChlorinatorBlockEntity>("chlorinator", 3));
        MountedRenderer.register(event, Wmd.KROLL_REACTOR_BE.get(),
                context -> new RefineryUnitRenderer<KrollReactorBlockEntity>("kroll_reactor", 3));
        MountedRenderer.register(event, Wmd.MINING_RIG_BE.get(),
                context -> new RefineryUnitRenderer<MiningRigBlockEntity>("mining_rig", 7));
        MountedRenderer.register(event, Wmd.DESALTER_BE.get(),
                context -> new RefineryUnitRenderer<DesalterBlockEntity>("desalter", 4));
        MountedRenderer.register(event, Wmd.FURNACE_BE.get(),
                context -> new RefineryUnitRenderer<FurnaceBlockEntity>("furnace", 10));
        MountedRenderer.register(event, Wmd.COAL_GENERATOR_BE.get(),
                context -> new RefineryUnitRenderer<CoalGeneratorBlockEntity>("coal_generator", 8));
        MountedRenderer.register(event, Wmd.FLARE_STACK_BE.get(),
                context -> new RefineryUnitRenderer<FlareStackBlockEntity>("flare_stack", 11));
        MountedRenderer.register(event, Wmd.ELECTROLYSER_BE.get(),
                context -> new RefineryUnitRenderer<ElectrolyserBlockEntity>("electrolyser", 3));
        MountedRenderer.register(event, Wmd.CRUSHER_BE.get(),
                context -> new RefineryUnitRenderer<CrusherBlockEntity>("crusher", 2));
        MountedRenderer.register(event, Wmd.ARC_FURNACE_BE.get(),
                context -> new RefineryUnitRenderer<ArcFurnaceBlockEntity>("arc_furnace", 3));
        MountedRenderer.register(event, Wmd.NITRATION_REACTOR_BE.get(),
                context -> new RefineryUnitRenderer<NitrationReactorBlockEntity>("nitration_reactor", 6));
        MountedRenderer.register(event, Wmd.COKER_BE.get(),
                context -> new RefineryUnitRenderer<CokerBlockEntity>("coker", 10));
        MountedRenderer.register(event, Wmd.HYDROTREATER_BE.get(),
                context -> new RefineryUnitRenderer<HydrotreaterBlockEntity>("hydrotreater", 8));
        MountedRenderer.register(event, Wmd.FCC_BE.get(),
                context -> new RefineryUnitRenderer<FccBlockEntity>("fcc", 10));
        MountedRenderer.register(event, Wmd.REFORMER_BE.get(),
                context -> new RefineryUnitRenderer<ReformerBlockEntity>("reformer", 8));
        MountedRenderer.register(event, Wmd.VACUUM_COLUMN_BE.get(),
                context -> new RefineryUnitRenderer<VacuumColumnBlockEntity>("vacuum_column", 10));
        MountedRenderer.register(event, Wmd.ATMOSPHERIC_COLUMN_BE.get(),
                context -> new RefineryUnitRenderer<AtmosphericColumnBlockEntity>("atmospheric_column", 12));

        MountedRenderer.register(event, Wmd.LAUNCH_TERMINAL_BE.get(),
                context -> new PadRenderer());
        for (var t : java.util.List.of(Wmd.BAY_CORE_BE, Wmd.BAY_WELD_BE, Wmd.BAY_CRANE_BE, Wmd.BAY_WARHEAD_BE, Wmd.BAY_CLEAN_BE, Wmd.BAY_PAINT_BE, Wmd.BAY_CRYO_BE, Wmd.BAY_EXTEND_BE, Wmd.BAY_RACK_BE, Wmd.BAY_RACK_R_BE))
            MountedRenderer.register(event, t.get(), context -> new BayRenderer());
    }

    @SubscribeEvent
    static void registerParticles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(WmdParticles.LAUNCH_SMOKE.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.BANK));
        event.registerSpriteSet(WmdParticles.TRAIL_SMOKE.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.TRAIL));
        event.registerSpriteSet(WmdParticles.ROCKET_FLAME.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.FLAME));
        event.registerSpriteSet(WmdParticles.VAPOUR.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.VAPOUR));
        event.registerSpriteSet(WmdParticles.GAS_CLOUD.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.GAS));
        event.registerSpriteSet(WmdParticles.COLLAPSE_DUST.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.DUST));
        event.registerSpriteSet(WmdParticles.VACUUM_SPARK.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.SPARK));
        event.registerSpriteSet(WmdParticles.WATER_SPRAY.get(), sprites -> new WmdSmokeParticle.Provider(sprites, WmdSmokeParticle.Kind.SPRAY));
    }

    @SubscribeEvent
    static void registerFluidLooks(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        for (WmdFluids.Entry f : WmdFluids.ALL) {
            final int color = f.color();
            event.registerFluidType(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                @Override
                public net.minecraft.resources.ResourceLocation getStillTexture() { return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_still"); }

                @Override
                public net.minecraft.resources.ResourceLocation getFlowingTexture() { return net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/water_flow"); }

                @Override
                public int getTintColor() { return color; }
            }, f.type().get());
        }

        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private MissileItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new MissileItemRenderer();
                return renderer;
            }
        }, Wmd.MISSILE_T1.get(), Wmd.MISSILE_T2.get(), Wmd.MISSILE_T3.get(), Wmd.INTERCEPTOR_T1.get(), Wmd.INTERCEPTOR_T2.get(), Wmd.INTERCEPTOR_T3.get(), Wmd.INTERCEPTOR_T4.get(),
                Wmd.STAGE_CORE.get(), Wmd.STAGE_UPPER.get(), Wmd.STAGE_BOOSTER.get());

        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private PayloadUnitItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new PayloadUnitItemRenderer();
                return renderer;
            }
        }, Wmd.PAYLOAD_UNIT.get());

        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private PartItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new PartItemRenderer();
                return renderer;
            }
        }, Wmd.partItems().stream().map(d -> (net.minecraft.world.item.Item) d.get()).toArray(net.minecraft.world.item.Item[]::new));
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Wmd.GENERATOR_MENU.get(), GeneratorScreen::new);
        event.register(Wmd.TERMINAL_MENU.get(), TerminalScreen::new);
        event.register(Wmd.RADAR_MENU.get(), RadarScreen::new);
        event.register(Wmd.ALARM_MENU.get(), AlarmScreen::new);
        event.register(Wmd.SORTER_MENU.get(), SorterScreen::new);

        event.<MachineMenu, MachineScreen>register(Wmd.MACHINE_MENU.get(), (menu, inv, title) -> menu.machine() instanceof ResearchStationBlockEntity
                ? new ResearchStationScreen(menu, inv, title)
                : menu.machine() instanceof SinterPressBlockEntity ? new SinterPressScreen(menu, inv, title)
                : menu.machine() instanceof NuclearReactorBlockEntity ? new NuclearReactorScreen(menu, inv, title)
                : menu.machine() instanceof RailgunBlockEntity ? new RailgunScreen(menu, inv, title)
                : menu.machine() instanceof FusionReactorBlockEntity ? new FusionReactorScreen(menu, inv, title)
                : menu.machine() instanceof ForcefieldBlockEntity ? new ForcefieldScreen(menu, inv, title)
                : menu.machine() instanceof SwarmLauncherBlockEntity ? new SwarmLauncherScreen(menu, inv, title)
                : menu.machine() instanceof DeadHandBlockEntity ? new DeadHandScreen(menu, inv, title)
                : menu.machine() instanceof SmokeMachineBlockEntity ? new SmokeMachineScreen(menu, inv, title)
                : menu.machine() instanceof BombRackBlockEntity ? new BombRackScreen(menu, inv, title)
                : menu.machine() instanceof BombSightBlockEntity ? new BombSightScreen(menu, inv, title)
                : menu.machine() instanceof EarlyWarningArrayBlockEntity ? new EarlyWarningScreen(menu, inv, title)
                : menu.machine() instanceof GroundStationBlockEntity ? new GroundStationScreen(menu, inv, title)
                : menu.machine() instanceof MlrsBlockEntity ? new MlrsScreen(menu, inv, title)
                : menu.machine() instanceof SuperGunBlockEntity ? new SuperGunScreen(menu, inv, title)
                : menu.machine() instanceof ArtilleryGunBlockEntity ? new ArtilleryGunScreen(menu, inv, title)
                : menu.machine() instanceof ShellPressBlockEntity ? new ShellPressScreen(menu, inv, title)
                : menu.machine() instanceof CruiseLauncherBlockEntity ? new CruiseLauncherScreen(menu, inv, title)
                : menu.machine() instanceof SatelliteFacilityBlockEntity ? new SatelliteFacilityScreen(menu, inv, title)
                : CcScreenC.handles(menu.machine()) ? new CcScreenC(menu, inv, title)
                : menu.machine() instanceof CcInterceptorConsoleBlockEntity ? new CcScreenS5(menu, inv, title)
                : menu.machine() instanceof CounterBatteryRadarBlockEntity ? new CounterBatteryScreen(menu, inv, title)
                : menu.machine() instanceof CcArtilleryConsoleBlockEntity ? new CcScreenArtillery(menu, inv, title)
                : menu.machine() instanceof CcIronDomeBlockEntity ? new CcScreenDome(menu, inv, title)
                : menu.machine() instanceof CcPlannerBlockEntity ? new CcScreenPlanner(menu, inv, title)
                : menu.machine() instanceof CcConsoleBlockEntity ? new CcScreenB(menu, inv, title)
                : menu.machine() instanceof CcModuleBlockEntity ? new CcScreen(menu, inv, title)
                : new MachineScreen(menu, inv, title));
        event.register(Wmd.BAY_MENU.get(), BayScreen::new);
        event.register(Wmd.LAB_MENU.get(), LabScreen::new);
        event.register(Wmd.DRONE_MENU.get(), DroneScreen::new);
        event.register(Wmd.BOMB_LINE_MENU.get(), BombLineScreen::new);
        event.register(Wmd.DRONE_HIVE_MENU.get(), DroneHiveScreen::new);
        event.register(Wmd.BIG_DRONE_MENU.get(), BigDroneScreen::new);
        event.register(Wmd.LAUNCHER_MENU.get(), LauncherScreen::new);
        event.register(Wmd.RACK_MENU.get(), RackScreen::new);
        event.register(Wmd.DEPOT_MENU.get(), DepotScreen::new);
        event.register(Wmd.TELEPORTER_MENU.get(), TeleporterScreen::new);
        event.register(Wmd.CC_BUILDING_MENU.get(), CcBuildingScreen::new);
        event.register(Wmd.CARGO_POD_MENU.get(), CargoPodScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {

        Wmd.LOGGER.info("HELLO FROM CLIENT SETUP");
        Wmd.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
