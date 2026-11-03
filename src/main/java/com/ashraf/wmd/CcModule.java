package com.ashraf.wmd;

public enum CcModule {
    MAP_TABLE("map_table", true),
    WARNING_BOARD("warning_board", true),
    RECON_WALL("recon_wall", true),
    MAP_PRINTER("map_printer", true),
    SAT_CONTROL("sat_control", true),
    AIR_DEFENCE("air_defence", true),
    EW_CONSOLE("ew_console", true),
    ALARM_BOARD("alarm_board", true),
    LAUNCH_CONSOLE("launch_console", true),
    RETARGET_CONSOLE("retarget_console", true),
    STRIKE_PLANNER("strike_planner", true),
    MISSILE_CAM("missile_cam", true),
    COMMS_ARRAY("comms_array", true),
    GPS_CONSOLE("gps_console", true),
    INT_SAT_CONSOLE("int_sat_console", true),
    ARTILLERY_CONSOLE("artillery_console", true),
    IRON_DOME("iron_dome", true),
    PLANNER("planner", true),
    SECURITY_MONITOR("security_monitor", true);

    public final String id;
    public final boolean built;

    CcModule(String id, boolean built) { this.id = id; this.built = built; }

    public int fe() {
        return switch (this) {
            case MAP_TABLE -> Balance.CC_TABLE_FE_PER_TICK;
            case WARNING_BOARD -> Balance.CC_WARNING_FE;
            case RECON_WALL -> Balance.CC_RECON_WALL_FE;
            case AIR_DEFENCE -> Balance.CC_AIR_DEFENCE_FE;
            case LAUNCH_CONSOLE -> Balance.CC_LAUNCH_FE;
            case RETARGET_CONSOLE -> Balance.CC_RETARGET_FE;
            case STRIKE_PLANNER -> Balance.CC_STRIKE_FE;
            case MISSILE_CAM -> Balance.CC_CAM_FE;
            case SAT_CONTROL -> Balance.CC_GLOBE_FE;
            case EW_CONSOLE -> Balance.CC_EW_FE;
            case GPS_CONSOLE -> Balance.CC_GPS_FE;
            case ALARM_BOARD -> Balance.CC_ALARM_FE;
            case MAP_PRINTER -> Balance.CC_PRINTER_FE;
            case COMMS_ARRAY -> Balance.CC_COMMS_FE;
            case INT_SAT_CONSOLE -> Balance.CC_INTERCEPTOR_FE;
            case ARTILLERY_CONSOLE -> Balance.ART_CONSOLE_FE;
            case IRON_DOME -> Balance.DOME_FE;
            case PLANNER -> Balance.PLANNER_FE;
            case SECURITY_MONITOR -> Balance.CC_SECURITY_FE;
            default -> 0;
        };
    }

    public String langKey() { return "gui.wmd.cc.module." + id; }
}
