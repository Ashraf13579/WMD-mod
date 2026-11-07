package com.ashraf.wmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class CcBuildingSpec {

    private CcBuildingSpec() {}

    public static final String NAME = "cc_building";
    public static final int X0 = -9, X1 = 9, Y0 = 0, Y1 = 9, Z0 = 0, Z1 = 18;
    public static final int HALL_X0 = -7, HALL_X1 = 7, HALL_Y0 = 1, HALL_Y1 = 7, HALL_Z0 = 2, HALL_Z1 = 16;

    public static final int SOCKET_X = 9, SOCKET_Y = 0, SOCKET_Z = 9;

    public static final int CENTRE_X = 0, CENTRE_Y = 1, CENTRE_Z = 9;

    public static final float DOOR_TRAVEL_PX = 11.0F;

    public static final float LEAF_L_DIR = -1.0F;

    public static final int DOOR_TICKS = 30;

    public static final float XS = -1.0F;

    public static boolean isDoorCell(BlockPos l) {
        return l.getZ() == 1 && l.getX() >= -1 && l.getX() <= 1 && l.getY() >= 1 && l.getY() <= 3;
    }

    public static boolean isDoorClick(BlockPos l) {
        return (l.getZ() == 0 || l.getZ() == 1) && l.getX() >= -1 && l.getX() <= 1 && l.getY() >= 1 && l.getY() <= 3;
    }

    public static boolean isDoorway(BlockPos l) {
        return l.getZ() == 0 && l.getX() >= -1 && l.getX() <= 1 && l.getY() >= 1 && l.getY() <= 3;
    }

    public static boolean inHall(int x, int y, int z) {
        return x >= HALL_X0 && x <= HALL_X1 && y >= HALL_Y0 && y <= HALL_Y1 && z >= HALL_Z0 && z <= HALL_Z1;
    }

    public static boolean hallPart(int x, int y, int z) {
        if (y == 4 && z >= 2 && z <= 5) return true;
        if (y == 5 && z == 5) return true;
        return x == 7 && y >= 1 && y <= 3 && z >= 6 && z <= 8;
    }

    private static final List<BlockPos> FOOTPRINT;
    static {
        List<BlockPos> l = new ArrayList<>();
        for (int x = X0; x <= X1; x++) for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++)
            if (!inHall(x, y, z) || hallPart(x, y, z)) l.add(new BlockPos(x, y, z));
        FOOTPRINT = Collections.unmodifiableList(l);
    }

    public static List<BlockPos> footprint() { return FOOTPRINT; }

    public record Slot(CcModule kind, String bone, int x, int y, int z, Direction facing) {}

    private static final Slot[] SLOTS = {
            new Slot(CcModule.MAP_TABLE, "map_table", 0, 1, 10, Direction.NORTH),
            new Slot(CcModule.WARNING_BOARD, "warning_board", -4, 4, 16, Direction.NORTH),
            new Slot(CcModule.RECON_WALL, "recon_wall", 4, 4, 16, Direction.NORTH),
            new Slot(CcModule.MAP_PRINTER, "map_printer", -2, 1, 2, Direction.SOUTH),
            new Slot(CcModule.SAT_CONTROL, "sat_globe", 5, 1, 11, Direction.WEST),
            new Slot(CcModule.AIR_DEFENCE, "air_defence", 4, 1, 15, Direction.NORTH),
            new Slot(CcModule.EW_CONSOLE, "ew_rack", 7, 1, 2, Direction.WEST),
            new Slot(CcModule.ALARM_BOARD, "alarm_board", -7, 3, 11, Direction.EAST),

            new Slot(CcModule.LAUNCH_CONSOLE, "launch_console", -4, 1, 15, Direction.NORTH),
            new Slot(CcModule.RETARGET_CONSOLE, "retarget_console", 7, 1, 15, Direction.NORTH),
            new Slot(CcModule.STRIKE_PLANNER, "strike_planner", 4, 1, 3, Direction.SOUTH),
            new Slot(CcModule.MISSILE_CAM, "missile_cam", -7, 1, 3, Direction.EAST),
            new Slot(CcModule.COMMS_ARRAY, "comms_array", 0, 10, 8, Direction.NORTH),
            new Slot(CcModule.GPS_CONSOLE, "gps_rack", -7, 1, 6, Direction.EAST),
            new Slot(CcModule.INT_SAT_CONSOLE, "interceptor_console", -6, 1, 10, Direction.EAST),
            new Slot(CcModule.ARTILLERY_CONSOLE, "artillery_console", 0, 1, 15, Direction.NORTH),
            new Slot(CcModule.IRON_DOME, "iron_dome", -6, 1, 13, Direction.EAST),
            new Slot(CcModule.PLANNER, "planner", -6, 5, 3, Direction.SOUTH),

            new Slot(CcModule.SECURITY_MONITOR, "security_monitor", -7, 1, 7, Direction.EAST),

    };

    public static Slot slot(CcModule k) { return SLOTS[k.ordinal()]; }

    public static BlockPos anchor(CcModule k) {
        Slot s = slot(k);
        BlockPos g = geoAnchors().get("slot_" + s.bone());
        return g != null ? g : new BlockPos(s.x(), s.y(), s.z());
    }

    public static Block block(CcModule k) {
        return switch (k) {
            case MAP_TABLE -> Wmd.CC_MAP_TABLE.get();
            case WARNING_BOARD -> Wmd.CC_WARNING_BOARD.get();
            case RECON_WALL -> Wmd.CC_RECON_WALL.get();
            case MAP_PRINTER -> Wmd.CC_MAP_PRINTER.get();
            case SAT_CONTROL -> Wmd.CC_SAT_GLOBE.get();
            case AIR_DEFENCE -> Wmd.CC_AIR_DEFENCE.get();
            case EW_CONSOLE -> Wmd.CC_EW_RACK.get();
            case ALARM_BOARD -> Wmd.CC_ALARM_BOARD.get();
            case LAUNCH_CONSOLE -> Wmd.CC_LAUNCH_CONSOLE.get();
            case RETARGET_CONSOLE -> Wmd.CC_RETARGET_CONSOLE.get();
            case STRIKE_PLANNER -> Wmd.CC_STRIKE_PLANNER.get();
            case MISSILE_CAM -> Wmd.CC_MISSILE_CAM.get();
            case COMMS_ARRAY -> Wmd.CC_COMMS_ARRAY.get();
            case GPS_CONSOLE -> Wmd.CC_GPS_RACK.get();
            case INT_SAT_CONSOLE -> Wmd.CC_INTERCEPTOR_CONSOLE.get();
            case ARTILLERY_CONSOLE -> Wmd.CC_ARTILLERY_CONSOLE.get();
            case IRON_DOME -> Wmd.CC_IRON_DOME.get();
            case PLANNER -> Wmd.CC_PLANNER.get();
            case SECURITY_MONITOR -> Wmd.CC_SECURITY_MONITOR.get();
        };
    }

    public static Item item(CcModule k) {
        Block b = block(k);
        return b == null ? Items.AIR : b.asItem();
    }

    public static CcModule kindOf(Item item) {
        if (item == null || item == Items.AIR) return null;
        for (CcModule k : CcModule.values()) if (item(k) == item) return k;
        return null;
    }

    private static volatile Map<String, BlockPos> anchors;

    private static Map<String, BlockPos> geoAnchors() {
        Map<String, BlockPos> a = anchors;
        if (a != null) return a;
        Map<String, BlockPos> m = new HashMap<>();
        try (InputStream in = CcBuildingSpec.class.getResourceAsStream("/assets/wmd/geo/block/" + NAME + ".geo.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement ge : root.getAsJsonArray("minecraft:geometry")) {
                    JsonArray bones = ge.getAsJsonObject().getAsJsonArray("bones");
                    if (bones == null) continue;
                    for (JsonElement be : bones) {
                        JsonObject b = be.getAsJsonObject();
                        String name = b.get("name").getAsString();
                        if (!name.startsWith("slot_") || !b.has("pivot")) continue;
                        JsonArray p = b.getAsJsonArray("pivot");

                        m.put(name, new BlockPos(Math.round(-p.get(0).getAsFloat() / 16.0F), Math.round(p.get(1).getAsFloat() / 16.0F),
                                Math.round(p.get(2).getAsFloat() / 16.0F)));
                    }
                }
            }
        } catch (Exception e) {
            Wmd.LOGGER.error("Command center slot anchors could not be read from the geo", e);
        }
        anchors = m;
        return m;
    }
}
