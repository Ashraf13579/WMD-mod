package com.ashraf.wmd;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class AirBlocksClient {

    private AirBlocksClient() {}

    static final float[][] TRACK_HOUSING = { { -8F, 14.6F, -8F, -1.6F, 16F, 8F }, { 1.6F, 14.6F, -8F, 8F, 16F, 8F } };

    static final float[][] SAM_HOUSING = {
            { -24F, 0F, -8F, 24F, 8F, 37.8F }, { -24F, 0F, 37.8F, -6.4F, 8F, 40F }, { 6.4F, 0F, 37.8F, 24F, 8F, 40F }, { -6.4F, 0F, 37.8F, 6.4F, 1.6F, 40F } };

    static final float[][] ASR_HOUSING = {
            { -24F, 0F, -8F, 24F, 14F, 37.8F }, { -24F, 0F, 37.8F, -6.4F, 14F, 40F }, { 6.4F, 0F, 37.8F, 24F, 14F, 40F }, { -6.4F, 0F, 37.8F, 6.4F, 1.6F, 40F } };

    static final float[][] CORE_HOUSING = {
            { -8F, 14.6F, -8F, 8F, 16F, 8F }, { -8F, 0F, -8F, 8F, 1.4F, 8F },
            { -8F, 1.4F, -8F, -6.6F, 14.6F, -6.2F }, { -8F, 1.4F, 6.2F, -6.6F, 14.6F, 8F }, { -8F, 1.4F, -6.2F, -6.6F, 1.8F, 6.2F }, { -8F, 14.2F, -6.2F, -6.6F, 14.6F, 6.2F },
            { 6.6F, 1.4F, -8F, 8F, 14.6F, -6.2F }, { 6.6F, 1.4F, 6.2F, 8F, 14.6F, 8F }, { 6.6F, 1.4F, -6.2F, 8F, 1.8F, 6.2F }, { 6.6F, 14.2F, -6.2F, 8F, 14.6F, 6.2F },
            { -6.6F, 1.4F, -8F, -5.4F, 14.6F, -6.6F }, { 5.4F, 1.4F, -8F, 6.6F, 14.6F, -6.6F }, { -5.4F, 1.4F, -8F, 5.4F, 2.6F, -6.6F }, { -5.4F, 13.4F, -8F, 5.4F, 14.6F, -6.6F },
            { -6.6F, 1.4F, 6.6F, -6.0F, 14.6F, 8F }, { 6.0F, 1.4F, 6.6F, 6.6F, 14.6F, 8F } };

    static final float[][] DISP_HOUSING = {
            { -8F, 14.6F, -8F, 8F, 16F, 8F }, { -8F, 1.4F, -8F, -6.6F, 14.6F, 8F }, { 6.6F, 1.4F, -8F, 8F, 14.6F, 8F },
            { -6.6F, 1.4F, -8F, 6.6F, 14.6F, -6.6F }, { -6.6F, 1.4F, 6.6F, 6.6F, 14.6F, 8F },
            { -8F, 0F, -8F, 8F, 1.4F, -3F }, { -8F, 0F, 3F, 8F, 1.4F, 8F }, { -8F, 0F, -3F, -7F, 1.4F, 3F }, { 7F, 0F, -3F, 8F, 1.4F, 3F }, { -1F, 0.3F, -3F, 1F, 1.4F, 3F } };

    static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(AirBlocks.AMMO_DEPOT_MENU.get(), AmmoDepotScreen::new);
        event.register(AirBlocks.GANTRY_MENU.get(), GantryScreen::new);
        CargoBlocksClient.screens(event);
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        ServiceBlocksClient.renderers(event);
        GunnerRenderers.register(event);
        RocketPodsClient.renderers(event);
        CargoBlocksClient.renderers(event);
        HangarDoorBlocksClient.renderers(event);
        DeckConsoleBlocksClient.renderers(event);
        TurntableBlocksClient.renderers(event);
        NavalGunsClient.renderers(event);
        AnchorBlocksClient.renderers(event);
        ChartTableBlocksClient.renderers(event);
        RfPowerBlocksClient.renderers(event);

        MountedRenderer.register(event, AirBlocks.SAM_SITE_BE.get(), context -> new CopycatUnitRenderer<SamSiteBlockEntity>("sam_site", 4, 2, SAM_HOUSING));

        MountedRenderer.register(event, AirBlocks.AMMO_DEPOT_BE.get(), context -> new CopycatUnitRenderer<AmmoDepotBlockEntity>("ammo_depot", 2, 2, null));

        MountedRenderer.register(event, AirBlocks.ORDNANCE_LOADER_BE.get(), context -> new OrdnanceLoaderRenderer("ordnance_loader", 11, 12));
        MountedRenderer.register(event, AirBlocks.HEAVY_LOADER_BE.get(), context -> new OrdnanceLoaderRenderer("heavy_loader", 14, 16));

        MountedRenderer.register(event, AirBlocks.LOADER_TRACK_BE.get(), context -> new CopycatUnitRenderer<LoaderTrackBlockEntity>("loader_track", 1, -1, TRACK_HOUSING));

        MountedRenderer.register(event, AirBlocks.AIR_SEARCH_RADAR_BE.get(), context -> new CopycatUnitRenderer<AirSearchRadarBlockEntity>("air_search_radar", 4, 2, ASR_HOUSING));

        MountedRenderer.register(event, AirBlocks.POWER_CORE_BE.get(), context -> new CopycatUnitRenderer<PowerCoreBlockEntity>("power_core", 1, -1, CORE_HOUSING));

        MountedRenderer.register(event, AirBlocks.JET_COCKPIT_BE.get(), context -> new JetCockpitRenderer("jet_cockpit", 3));
        MountedRenderer.register(event, AirBlocks.JET_COCKPIT_TANDEM_BE.get(), context -> new JetCockpitRenderer("jet_cockpit_tandem", 5));
        MountedRenderer.register(event, AirBlocks.FLIGHT_DECK_BE.get(), context -> new JetCockpitRenderer("flight_deck", 3));

        MountedRenderer.register(event, AirBlocks.PYLON_BE.get(), context -> new AirStationRenderer("pylon", 1, false));
        MountedRenderer.register(event, AirBlocks.LAUNCH_RAIL_BE.get(), context -> new AirStationRenderer("launch_rail", 1, true));

        event.registerBlockEntityRenderer(AirBlocks.CONTROL_SURFACE_BE.get(), ControlSurfaceRenderer::new);
        MountedRenderer.register(event, AirBlocks.DISPENSER_BE.get(), context -> new CopycatUnitRenderer<CountermeasureDispenserBlockEntity>("countermeasure_dispenser", 1, -1, DISP_HOUSING));

        MountedRenderer.register(event, AirBlocks.NOSE_RADAR_BE.get(), context -> new NoseRadarRenderer());

        MountedRenderer.register(event, AirBlocks.WEAPONS_BAY_BE.get(), context -> new WeaponsBayRenderer());

        MountedRenderer.register(event, AirBlocks.JAMMER_POD_BE.get(), context -> new JammerPodRenderer());

        MountedRenderer.register(event, AirBlocks.GUN_POD_BE.get(), context -> new GunPodRenderer());
        MountedRenderer.register(event, AirBlocks.TARGETING_POD_BE.get(), context -> new TargetingPodRenderer());
        MountedRenderer.register(event, AirBlocks.LANDING_GEAR_BE.get(), context -> new LandingGearRenderer());
        MountedRenderer.register(event, AirBlocks.LANDING_GEAR_LARGE_BE.get(), context -> new LandingGearRenderer());
        MountedRenderer.register(event, AirBlocks.LANDING_GEAR_BOGIE_BE.get(), context -> new LandingGearRenderer());
        MountedRenderer.register(event, AirBlocks.TAILHOOK_BE.get(), context -> new TailhookRenderer());
        MountedRenderer.register(event, AirBlocks.ARRESTING_WIRE_BE.get(), context -> new ArrestingWireRenderer());
        MountedRenderer.register(event, AirBlocks.LIFT_FAN_BE.get(), context -> new CopycatUnitRenderer<LiftFanBlockEntity>("lift_fan", 1, 2, null));
        MountedRenderer.register(event, AirBlocks.DRAG_CHUTE_BE.get(), context -> new DragChuteRenderer());
        MountedRenderer.register(event, AirBlocks.SURVEY_SCANNER_BE.get(), context -> new SurveyScannerRenderer());
        MountedRenderer.register(event, AirBlocks.GANTRY_RAIL_BE.get(), context -> new CopycatUnitRenderer<GantryRailBlockEntity>("gantry_rail", 1, 1, null));
        MountedRenderer.register(event, AirBlocks.GANTRY_CABINET_BE.get(), context -> new GantryCabinetRenderer());
        MountedRenderer.register(event, AirBlocks.HELI_ROTOR_BE.get(), context -> new HeliRotorRenderer());
        MountedRenderer.register(event, AirBlocks.HELI_TAIL_BE.get(), context -> new CopycatUnitRenderer<HeliTailBlockEntity>("heli_tail", 1, 1, null));
        MountedRenderer.register(event, AirBlocks.HELI_TURBOSHAFT_BE.get(), context -> new CopycatUnitRenderer<HeliTurboshaftBlockEntity>("heli_turboshaft", 1, 2, null));
        MountedRenderer.register(event, AirBlocks.JET_ENGINE_BE.get(), context -> new JetEngineRenderer());

        MountedRenderer.register(event, AirBlocks.TURBOPROP_BE.get(), context -> new P6StyledRenderer<TurbopropBlockEntity>(new P6StyledRenderer.Prop("turboprop"), new P6StyledRenderer.Prop("turboprop_bare")));
        MountedRenderer.register(event, AirBlocks.TILTROTOR_BE.get(), context -> new CopycatUnitRenderer<TiltrotorBlockEntity>("tiltrotor", 2, 3, null));
        MountedRenderer.register(event, AirBlocks.SHIP_TURBINE_BE.get(), context -> new CopycatUnitRenderer<ShipTurbineBlockEntity>("ship_turbine", 2, 4, null));
        MountedRenderer.register(event, AirBlocks.WATERJET_BE.get(), context -> P6StyledRenderer.<WaterjetBlockEntity>of("waterjet", 1, 3));
        MountedRenderer.register(event, AirBlocks.AZIMUTH_POD_BE.get(), context -> new CopycatUnitRenderer<AzimuthPodBlockEntity>("azimuth_pod", 1, 3, null) {
            @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(AzimuthPodBlockEntity be) { return super.getRenderBoundingBox(be).expandTowards(0, -4.5, 0); }
        });
        MountedRenderer.register(event, AirBlocks.BOW_THRUSTER_BE.get(), context -> new CopycatUnitRenderer<BowThrusterBlockEntity>("bow_thruster", 1, 1, null));

        MountedRenderer.register(event, AirBlocks.CARGO_HOOK_LIGHT_BE.get(), context -> new P7PartsRenderer<CargoHookBlockEntity>("cargo_hook_light", 1, 1, Balance.HOOK_LEN[0] + 2.0, 2.0));
        MountedRenderer.register(event, AirBlocks.CARGO_HOOK_HEAVY_BE.get(), context -> new P7PartsRenderer<CargoHookBlockEntity>("cargo_hook_heavy", 1, 2, Balance.HOOK_LEN[1] + 3.0, 3.0));
        MountedRenderer.register(event, AirBlocks.DROGUE_POD_BE.get(), context -> new P7PartsRenderer<DroguePodBlockEntity>("drogue_pod", 1, 2, Balance.HOSE_LEN * Balance.HOSE_SAG + 3.0, Balance.HOSE_LEN + 3.0));
        MountedRenderer.register(event, AirBlocks.REFUEL_PROBE_BE.get(), context -> new P7PartsRenderer<RefuelProbeBlockEntity>("refuel_probe", 1, 1, 0.0, 1.5));
        MountedRenderer.register(event, AirBlocks.NAV_LIGHT_BE.get(), context -> new NavLightRenderer());
        MountedRenderer.register(event, AirBlocks.FUEL_TANK_BE.get(), context -> new P7PartsRenderer<FuelTankBlockEntity>("fuel_tank", 1, 1, 0.0, 0.0));
        MountedRenderer.register(event, AirBlocks.DROP_TANK_BE.get(), context -> new P7PartsRenderer<FuelTankBlockEntity>("drop_tank", 1, 3, 0.0, 0.0));
        MountedRenderer.register(event, AirBlocks.CATAPULT_TRACK_BE.get(), context -> new CatapultTrackRenderer());
        MountedRenderer.register(event, AirBlocks.DECK_LIGHT_BE.get(), context -> new DeckLightRenderer());
        MountedRenderer.register(event, AirBlocks.DECK_LIFT_BE.get(), context -> new DeckLiftRenderer());
        event.registerEntityRenderer(AirBlocks.COCKPIT_SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(AirBlocks.EJECTION_SEAT.get(), EjectionSeatRenderer::new);
        event.registerEntityRenderer(AirBlocks.EJECTED_CANOPY.get(), EjectedCanopyRenderer::new);
    }
}
