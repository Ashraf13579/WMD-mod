package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class Camo {

    private Camo() {}

    private static final Map<ResourceKey<Level>, Set<CamoNetBlockEntity>> NETS = new HashMap<>();

    static void join(CamoNetBlockEntity n) {
        if (n.getLevel() == null || n.getLevel().isClientSide) return;
        NETS.computeIfAbsent(n.getLevel().dimension(), k -> Collections.newSetFromMap(new WeakHashMap<>())).add(n);
    }

    static void leave(CamoNetBlockEntity n) {
        for (Set<CamoNetBlockEntity> s : NETS.values()) s.remove(n);
    }

    static List<CamoNetBlockEntity> nets(Level level) {
        Set<CamoNetBlockEntity> s = level == null ? null : NETS.get(level.dimension());
        List<CamoNetBlockEntity> l = new ArrayList<>();
        if (s != null) for (CamoNetBlockEntity n : s) if (!n.isRemoved() && n.getLevel() == level) l.add(n);
        return l;
    }

    public static boolean hiddenFromRecon(BlockEntity be) {
        if (be instanceof DecoyBlockEntity d && d.popped()) return true;
        if (be instanceof DeadHandBlockEntity) return true;
        Level level = be.getLevel();
        if (level == null) return false;
        Set<CamoNetBlockEntity> s = NETS.get(level.dimension());
        if (s == null || s.isEmpty()) return false;
        BlockPos p = be.getBlockPos();
        for (CamoNetBlockEntity n : s) if (!n.isRemoved() && n.covers(p)) return true;
        return false;
    }

    public static long sample(ServerLevel level, int x, int z) {
        Set<CamoNetBlockEntity> s = NETS.get(level.dimension());
        if (s == null || s.isEmpty()) return Long.MIN_VALUE;
        for (CamoNetBlockEntity n : s)
            if (!n.isRemoved() && n.coversColumn(x, z)) return ((long) MapColor.PLANT.id << 32) | ((long) (int) Math.floor(n.roofY()) & 0xFFFFFFFFL);
        return Long.MIN_VALUE;
    }

    public static boolean thermalHides(Level level, BlockPos p) {
        Set<CamoNetBlockEntity> s = level == null ? null : NETS.get(level.dimension());
        if (s == null || s.isEmpty()) return false;
        for (CamoNetBlockEntity n : s) if (!n.isRemoved() && n.thermal() && n.covers(p)) return true;
        return false;
    }

    public static boolean thermalHidesColumn(Level level, int x, int z) {
        Set<CamoNetBlockEntity> s = level == null ? null : NETS.get(level.dimension());
        if (s == null || s.isEmpty()) return false;
        for (CamoNetBlockEntity n : s) if (!n.isRemoved() && n.thermal() && n.coversColumn(x, z)) return true;
        return false;
    }

    public static void blast(ServerLevel level, Vec3 pos, double reach) {
        if (level == null || pos == null || reach <= 0.0) return;
        for (CamoNetBlockEntity n : nets(level)) if (n.hitBy(pos, reach)) n.tear();
        DecoyBlockEntity.blast(level, pos, reach);
    }

    static BlockPos controllerAt(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        Block b = s.getBlock();
        if (b instanceof CamoNetBlock) return null;
        if (b instanceof MachineBlock) return p;
        if (level.getBlockEntity(p) instanceof MachinePartBlockEntity part && part.getController() != null) {
            BlockPos c = part.getController();
            Block cb = level.getBlockState(c).getBlock();
            return cb instanceof MachineBlock && !(cb instanceof CamoNetBlock) ? c : null;
        }
        return null;
    }

    static int[] boxOf(Level level, BlockPos c) {
        BlockState s = level.getBlockState(c);
        if (!(s.getBlock() instanceof MachineBlock mb) || !s.hasProperty(MachineBlock.FACING)) return null;
        Direction f = s.getValue(MachineBlock.FACING);
        int[] b = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (BlockPos l : mb.footprint()) {
            BlockPos w = Machines.toWorld(c, f, l.getX(), l.getY(), l.getZ());
            b[0] = Math.min(b[0], w.getX()); b[1] = Math.min(b[1], w.getY()); b[2] = Math.min(b[2], w.getZ());
            b[3] = Math.max(b[3], w.getX()); b[4] = Math.max(b[4], w.getY()); b[5] = Math.max(b[5], w.getZ());
        }
        return b[0] == Integer.MAX_VALUE ? null : b;
    }

    static int[] machineFor(Level level, BlockPos net, Direction facing, int span, boolean[] tooBig) {
        for (Direction d : new Direction[] { facing.getOpposite(), facing.getClockWise(), facing.getCounterClockWise(), facing }) {
            BlockPos c = controllerAt(level, net.relative(d));
            if (c == null) continue;
            int[] b = boxOf(level, c);
            if (b == null) continue;
            if (b[3] - b[0] + 1 > span || b[5] - b[2] + 1 > span) { if (tooBig != null) tooBig[0] = true; continue; }
            return new int[] { c.getX(), c.getY(), c.getZ(), b[0], b[1], b[2], b[3], b[4], b[5] };
        }
        return null;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) {
        NETS.clear();
        DecoyBlockEntity.LOADED.clear();
    }
}
