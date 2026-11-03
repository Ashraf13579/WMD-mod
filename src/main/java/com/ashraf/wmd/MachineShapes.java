package com.ashraf.wmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MachineShapes {
    private MachineShapes() {}

    private static final Map<String, JsonObject> FILES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<VoxelShape>> CACHE = new ConcurrentHashMap<>();

    private static final Map<String, String> MESH_OF = new HashMap<>();
    static {
        MESH_OF.put("interceptor_silo_t3", "silo_t3");
        MESH_OF.put("crew_capsule_hull", "crew_capsule_landed");
        MESH_OF.put("interceptor_silo_t4", "silo_t4");
        MESH_OF.put("interceptor_battery_t2", "battery_t2");
        MESH_OF.put("bay_rack_r", "bay_rack_r");
        MESH_OF.put("cooling_tower", "cooling_tower_xl");
    }

    private static JsonObject file(String mesh) {
        return FILES.computeIfAbsent(mesh, m -> {
            try (InputStream in = MachineShapes.class.getResourceAsStream("/data/wmd/machine_shapes/" + m + ".json")) {
                if (in == null) return new JsonObject();
                return JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            } catch (Exception e) {
                Wmd.LOGGER.error("Machine shapes {} could not be read", m, e);
                return new JsonObject();
            }
        });
    }

    public static String meshOf(String blockId) { return MESH_OF.getOrDefault(blockId, blockId); }

    public static java.util.List<BlockPos> cells(String mesh) {
        JsonObject f = file(mesh);
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (!f.has("cells")) return out;
        for (String k : f.getAsJsonObject("cells").keySet()) {
            String[] p = k.split(",");
            out.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        return out;
    }

    public static java.util.List<net.minecraft.world.phys.AABB> meshBoxes(String mesh) {
        JsonObject f = file(mesh);
        java.util.List<net.minecraft.world.phys.AABB> out = new java.util.ArrayList<>();
        if (!f.has("cells")) return out;
        JsonObject cells = f.getAsJsonObject("cells");
        for (String k : cells.keySet()) {
            String[] p = k.split(",");
            int cx = Integer.parseInt(p[0]), cy = Integer.parseInt(p[1]), cz = Integer.parseInt(p[2]);
            for (JsonElement be : cells.get(k).getAsJsonArray()) {
                JsonArray b = be.getAsJsonArray();
                out.add(new net.minecraft.world.phys.AABB(cx - 0.5 + b.get(0).getAsDouble() / 16.0, cy + b.get(1).getAsDouble() / 16.0, cz - 0.5 + b.get(2).getAsDouble() / 16.0,
                        cx - 0.5 + b.get(3).getAsDouble() / 16.0, cy + b.get(4).getAsDouble() / 16.0, cz - 0.5 + b.get(5).getAsDouble() / 16.0));
            }
        }
        return out;
    }

    public static VoxelShape get(String blockId, BlockPos local, Direction facing) {
        String key = blockId + "|" + local.getX() + "," + local.getY() + "," + local.getZ() + "|" + facing;
        return CACHE.computeIfAbsent(key, k -> Optional.ofNullable(build(meshOf(blockId), local, facing))).orElse(null);
    }

    private static VoxelShape build(String mesh, BlockPos local, Direction facing) {
        JsonObject f = file(mesh);
        if (!f.has("cells")) return null;
        JsonObject cells = f.getAsJsonObject("cells");
        JsonElement el = cells.get(local.getX() + "," + local.getY() + "," + local.getZ());
        if (el == null) return Shapes.empty();
        VoxelShape out = Shapes.empty();
        for (JsonElement be : el.getAsJsonArray()) {
            JsonArray b = be.getAsJsonArray();
            double x0 = b.get(0).getAsDouble(), y0 = b.get(1).getAsDouble(), z0 = b.get(2).getAsDouble(), x1 = b.get(3).getAsDouble(), y1 = b.get(4).getAsDouble(), z1 = b.get(5).getAsDouble();
            double ax, az, bx, bz;
            switch (facing) {
                case EAST -> { ax = 16 - z0; az = x0; bx = 16 - z1; bz = x1; }
                case SOUTH -> { ax = 16 - x0; az = 16 - z0; bx = 16 - x1; bz = 16 - z1; }
                case WEST -> { ax = z0; az = 16 - x0; bx = z1; bz = 16 - x1; }
                default -> { ax = x0; az = z0; bx = x1; bz = z1; }
            }
            out = Shapes.or(out, Block.box(Math.min(ax, bx), y0, Math.min(az, bz), Math.max(ax, bx), y1, Math.max(az, bz)));
        }
        return out.optimize();
    }
}
