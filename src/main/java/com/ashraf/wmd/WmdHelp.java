package com.ashraf.wmd;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class WmdHelp {

    private WmdHelp() {}

    public static final int SIZE = 11, ROOM = SIZE + 2;

    private static final Map<String, String> BLOCK_PAGE = Map.ofEntries(

            Map.entry("pumpjack", "oil"), Map.entry("feed_pump", "oil"), Map.entry("desalter", "oil"), Map.entry("crude_tank", "oil"), Map.entry("storage_tank", "oil"),
            Map.entry("oil_detector", "oil"),
            Map.entry("furnace", "refinery"), Map.entry("atmospheric_column", "refinery"), Map.entry("vacuum_column", "refinery"), Map.entry("coker", "refinery"),
            Map.entry("reformer", "oil_upgrades"), Map.entry("fcc", "oil_upgrades"), Map.entry("hydrotreater", "oil_upgrades"), Map.entry("flare_stack", "oil_upgrades"),
            Map.entry("coal_generator", "power"), Map.entry("diesel_generator", "power"), Map.entry("generator", "power"), Map.entry("generator_engine", "power"),
            Map.entry("sentry_turret", "base_defence"), Map.entry("cc_security_monitor", "cctv"), Map.entry("c4_charge", "raid_kit"), Map.entry("portable_jammer", "raid_kit"), Map.entry("seismic_scanner", "raid_kit"), Map.entry("cctv_camera", "cctv"), Map.entry("covert_camera", "cctv"), Map.entry("camera_relay", "cctv"), Map.entry("perimeter_sensor", "cctv"), Map.entry("floodlight", "cctv"), Map.entry("fence_energizer", "base_defence"), Map.entry("electric_fence", "base_defence"), Map.entry("pressure_mine", "mines"), Map.entry("claymore", "mines"), Map.entry("battery", "power"), Map.entry("battery_t1", "power"), Map.entry("battery_t2_cabinet", "power"), Map.entry("battery_t3", "power"), Map.entry("battery_t4", "power"), Map.entry("creative_battery", "power"),
            Map.entry("gas_turbine", "turbine"), Map.entry("grid_converter", "turbine"),
            Map.entry("electrolyser", "aluminium_he"), Map.entry("nitration_reactor", "aluminium_he"),
            Map.entry("rolling_mill", "plates_wires"), Map.entry("wire_drawer", "plates_wires"),
            Map.entry("ground_scanner", "deposits"), Map.entry("research_station", "research"), Map.entry("water_intake", "water"), Map.entry("item_duct", "ducts"), Map.entry("floodgate", "fluids"), Map.entry("robot_dock", "robots"), Map.entry("cc_planner", "robots"), Map.entry("xl_dock", "heavy"), Map.entry("missile_depot", "heavy"), Map.entry("missile_teleporter", "teleporter"), Map.entry("runner_charger", "runner"), Map.entry("laser_tripod", "laser"), Map.entry("laser_receiver", "laser"), Map.entry("thermal_net", "counters"), Map.entry("smoke_machine", "counters"), Map.entry("bomb_rack", "bombs"), Map.entry("bomb_bay", "heavy_bombs"), Map.entry("bomb_sight", "bomb_sight"), Map.entry("drone_core", "drone_line"), Map.entry("drone_hive", "drone_hive"), Map.entry("drone_catapult", "big_drone"), Map.entry("runway_tile", "big_drone"), Map.entry("runway_line", "big_drone"), Map.entry("runway_light", "big_drone"), Map.entry("bomb_line_cradle", "bomb_line"), Map.entry("bomb_line_filling", "bomb_line"), Map.entry("bomb_line_core", "bomb_line"), Map.entry("bomb_line_press", "bomb_line"), Map.entry("bomb_line_fuze", "bomb_line"), Map.entry("bomb_line_storage", "bomb_line"), Map.entry("drone_frame", "drone_line"), Map.entry("drone_avionics", "drone_line"), Map.entry("drone_propulsion", "drone_line"), Map.entry("drone_test", "drone_line"), Map.entry("drone_hangar", "drone_line"), Map.entry("drone_storage", "drone_line"), Map.entry("sorter", "ducts"), Map.entry("supply_interface", "ducts"), Map.entry("mining_rig", "rig"),
            Map.entry("blast_furnace", "blast_furnace"), Map.entry("coke_oven", "early_machines"), Map.entry("starter_generator", "early_machines"), Map.entry("solar_panel", "renewables"), Map.entry("wind_turbine", "renewables"), Map.entry("steam_engine", "power"), Map.entry("lava_generator", "lava_power"), Map.entry("gasoline_engine", "gasoline_engine"), Map.entry("free_piston_generator", "free_piston"), Map.entry("stamp_mill", "early_machines"), Map.entry("powder_mill", "early_machines"), Map.entry("crusher", "crusher"), Map.entry("chlor_alkali_cell", "chlorine"), Map.entry("chlorinator", "chlorinator"), Map.entry("magnesium_cell", "magnesium"),
            Map.entry("kroll_reactor", "kroll"), Map.entry("arc_furnace", "arc"),
            Map.entry("roaster", "tungsten_chain"), Map.entry("hydrogen_furnace", "tungsten_chain"), Map.entry("sinter_press", "tungsten_chain"),
            Map.entry("leach_tank", "uranium"), Map.entry("centrifuge", "centrifuge"), Map.entry("nuclear_reactor", "reactor_run"),
            Map.entry("cooling_tower", "steam_loop"), Map.entry("steam_turbine", "steam_loop"), Map.entry("fusion_reactor", "fusion"), Map.entry("forcefield_generator", "forcefield"), Map.entry("laser_defence", "laser_defence"), Map.entry("swarm_launcher", "swarm"), Map.entry("deadhand_bunker", "deadhand"), Map.entry("antimatter_collider", "antimatter"), Map.entry("antimatter_store", "antimatter"), Map.entry("railgun", "railgun"), Map.entry("breeder_rod", "fusion_fuel"), Map.entry("reprocessing_plant", "plutonium"),

            Map.entry("launch_terminal", "launch"), Map.entry("launch_terminal_1", "launch"), Map.entry("launch_terminal_2", "launch"),
            Map.entry("launch_terminal_3", "launch"), Map.entry("launch_terminal_4", "t4_rocket"), Map.entry("terminal", "launch"),
            Map.entry("radar", "radar"), Map.entry("radar_station", "radar"), Map.entry("alarm", "alarm"),
            Map.entry("interceptor_launcher", "interceptors"), Map.entry("interceptor_battery_t2", "interceptors"), Map.entry("interceptor_silo_t3", "interceptors"),
            Map.entry("interceptor_silo_t4", "interceptors"), Map.entry("ciws", "ciws"),
            Map.entry("bay_rack", "rack"), Map.entry("bay_rack_r", "rack"),

            Map.entry("ground_station", "ground_station"), Map.entry("oxygen_station", "orbit_oxygen"), Map.entry("satellite_facility", "sat_facility"), Map.entry("early_warning_array", "early_warning"),
            Map.entry("cc_building", "cc_building"), Map.entry("cc_map_table", "cc_map_table"), Map.entry("cc_warning_board", "cc_warning_board"),
            Map.entry("cc_recon_wall", "cc_recon_wall"), Map.entry("cc_air_defence", "cc_air_defence"), Map.entry("cc_launch_console", "cc_launch"),
            Map.entry("cc_retarget_console", "cc_retarget"), Map.entry("cc_strike_planner", "cc_strike"), Map.entry("cc_missile_cam", "cc_cam"),
            Map.entry("cc_sat_globe", "cc_globe"), Map.entry("cc_ew_rack", "cc_ew_gps"), Map.entry("cc_gps_rack", "cc_ew_gps"), Map.entry("cc_alarm_board", "cc_alarm"),
            Map.entry("cc_map_printer", "cc_printer"), Map.entry("cc_comms_array", "cc_comms"), Map.entry("cc_interceptor_console", "interceptor_sat"),

            Map.entry("mortar", "mortar"), Map.entry("howitzer", "howitzer"), Map.entry("shell_press", "shell_press"), Map.entry("counter_battery_radar", "counter_battery"),
            Map.entry("cc_artillery_console", "art_console"), Map.entry("cc_iron_dome", "iron_dome"), Map.entry("mlrs", "mlrs"), Map.entry("super_gun", "super_gun"),
            Map.entry("camo_net", "camo"), Map.entry("decoy_launcher", "decoys"), Map.entry("decoy_radar", "decoys"),
            Map.entry("alkylation_unit", "alkylation"), Map.entry("cruise_launcher", "cruise_launcher"),

            Map.entry("sonar_dome", "sonar_dome"), Map.entry("hydrophone_array", "hydrophone_array"), Map.entry("sonar_console", "sonar_console"),
            Map.entry("dredge_winch", "dredge_winch"), Map.entry("collector_rig", "collector_rig"), Map.entry("nodule_field", "dredge_winch"),
            Map.entry("manganese_hull_plate", "deep_hull"), Map.entry("superalloy_hull", "deep_hull"), Map.entry("deep_viewport", "deep_hull"),
            Map.entry("hull_penetrator", "hull_penetrator"), Map.entry("torpedo_net", "torpedo_net"),
            Map.entry("anechoic_tile", "anechoic"), Map.entry("deep_sonar_upgrade", "deep_sonar"),
            Map.entry("towed_array_winch", "towed_array_winch"), Map.entry("listening_node", "listening_node"), Map.entry("coastal_battery", "coastal_battery"), Map.entry("dock_crane", "dock_crane"),
            Map.entry("torpedo_tube", "torpedo_tube"), Map.entry("torpedo_line_shop", "torpedo_line"), Map.entry("torpedo_line_fitout", "torpedo_line"), Map.entry("torpedo_line_test", "torpedo_line"), Map.entry("torpedo_launcher_deck", "torpedo_launcher_deck"), Map.entry("torpedo_rack", "torpedo_rack"), Map.entry("fire_control_console", "fire_control_console"), Map.entry("depth_charge_rack", "depth_charge"), Map.entry("launch_cell", "launch_cell"),
            Map.entry("naval_reactor", "naval_reactor"), Map.entry("periscope", "periscope"), Map.entry("comms_buoy", "comms_buoy"), Map.entry("escape_pod", "escape_pod"), Map.entry("noisemaker_launcher", "noisemaker_launcher"),
            Map.entry("depth_charge_thrower", "kgun"), Map.entry("mine_influence", "mine_influence"), Map.entry("mine_bottom", "mine_bottom"), Map.entry("mine_captor", "mine_captor"));

    static String pageForBlock(Block b) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (!Wmd.MODID.equals(id.getNamespace())) return null;
        String p = id.getPath(), page = BLOCK_PAGE.get(p);
        if (page != null) return page;
        if (p.startsWith("bay")) return "bay";
        if (p.startsWith("lab") || p.equals("warhead_reactor")) return "lab";
        return null;
    }

    private static String pageAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null) return null;
        return pageForBlock(mc.level.getBlockState(pos).getBlock());
    }

    public static String pageFor(Screen s) {
        if (s == null || s instanceof FieldManualScreen) return null;
        String page = null;
        if (s instanceof MachineScreen ms) {
            if (ms.getMenu().machine() instanceof BlockEntity be) page = pageForBlock(be.getBlockState().getBlock());
            if (s instanceof SonarScreen && SonarScreen.hullView()) page = "sub_hull";
        } else if (s instanceof LauncherScreen ls) page = pageAt(ls.getMenu().getPos());
        else if (s instanceof TerminalScreen ts) page = ts.getMenu().getTier() >= 4 ? "t4_rocket" : "launch";
        else if (s instanceof BayScreen) page = "bay";
        else if (s instanceof LabScreen) page = "lab";
        else if (s instanceof DroneScreen) page = "drone_line";
        else if (s instanceof BombLineScreen) page = "bomb_line";
        else if (s instanceof TorpedoLineScreen) page = "torpedo_line";
        else if (s instanceof DroneHiveScreen) page = "drone_hive";
        else if (s instanceof BigDroneScreen) page = "big_drone";
        else if (s instanceof RouteChartScreen) page = "cc_drones";
        else if (s instanceof RackScreen) page = "rack";
        else if (s instanceof RadarScreen) page = "radar";
        else if (s instanceof AlarmScreen) page = "alarm";
        else if (s instanceof SorterScreen) page = "ducts";
        else if (s instanceof GeneratorScreen) page = "power";
        else if (s instanceof CcBuildingScreen) page = "cc_building";
        else if (s instanceof FrequencyScreen) page = "alarm";
        else if (s instanceof CrewCapsuleScreen) page = "orbit_capsule";
        else if (s instanceof StationScreen) page = "station";
        else if (s instanceof CargoPodScreen) page = "cargo";
        else if (s instanceof RodScreen) page = "rod";
        else if (s instanceof MirrorScreen) page = "mirror";
        else if (s instanceof AsteroidBayScreen) page = "abay";
        else if (s instanceof SecurityScreen) page = "lock";
        else if (s instanceof UplinkScreen) page = "uplink";
        else if (s instanceof ReconMapScreen) page = "recon_map";
        else if (s instanceof LaserTabletScreen) page = "laser";
        else if (s instanceof AreaViewScreen) page = "designator";
        else if (s instanceof SecurityMonitorScreen) page = "cctv";
        else if (s instanceof SeaMineScreen sm) page = sm.page();
        else return null;
        return page != null ? page : "contents";
    }

    private static Screen drawnFor;
    private static int bx, by;

    static boolean wantsHeaderButton(int x, int y) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null || drawnFor == s || pageFor(s) == null) return false;
        if (s instanceof AbstractContainerScreen<?> cs) return cs.getGuiLeft() == x && cs.getGuiTop() == y;
        return true;
    }

    public static void button(GuiGraphics g, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s == null || pageFor(s) == null) return;
        drawnFor = s;
        bx = x;
        by = y;
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
        boolean hover = WmdUi.over(mx, my, x, y, SIZE, SIZE);
        WmdUi.button(g, mc.font, x, y, SIZE, SIZE, Component.literal("?"), WmdUi.B_NORMAL, hover);
        if (hover) s.setTooltipForNextRenderPass(Component.translatable("gui.wmd.help.tip"));
    }

    public static boolean hovered(double mx, double my) {
        return drawnFor != null && drawnFor == Minecraft.getInstance().screen && WmdUi.over(mx, my, bx, by, SIZE, SIZE);
    }

    @SubscribeEvent
    static void onRenderPre(ScreenEvent.Render.Pre event) {
        if (drawnFor == event.getScreen()) drawnFor = null;
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen s = event.getScreen();
        if (event.getButton() != 0 || s != drawnFor || !WmdUi.over(event.getMouseX(), event.getMouseY(), bx, by, SIZE, SIZE)) return;
        if (s instanceof AbstractContainerScreen<?> cs && !cs.getMenu().getCarried().isEmpty()) return;
        String page = pageFor(s);
        if (page == null) return;
        WmdUi.click();
        FieldManualScreen.open(s, page);
        event.setCanceled(true);
    }
}
