package com.ashraf.wmd;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class PlaceGuard {
    private PlaceGuard() {}

    private static boolean blocks(Entity e) { return e.isAlive() && e.blocksBuilding && !e.isSpectator(); }

    public static Entity blocker(Level level, BlockPos p) {
        List<Entity> l = level.getEntities((Entity) null, new AABB(p), PlaceGuard::blocks);
        return l.isEmpty() ? null : l.get(0);
    }

    public static Entity blocker(Level level, Iterable<BlockPos> cells) {
        AABB all = null;
        for (BlockPos p : cells) all = all == null ? new AABB(p) : all.minmax(new AABB(p));
        if (all == null) return null;
        List<Entity> l = level.getEntities((Entity) null, all, PlaceGuard::blocks);
        if (l.isEmpty()) return null;
        for (Entity e : l) for (BlockPos p : cells) if (e.getBoundingBox().intersects(new AABB(p))) return e;
        return null;
    }

    public static Entity blocker(Level level, BlockPos a, BlockPos b) {
        AABB box = new AABB(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        List<Entity> l = level.getEntities((Entity) null, box, PlaceGuard::blocks);
        return l.isEmpty() ? null : l.get(0);
    }

    public static Component message(Entity e) {
        BlockPos p = e.blockPosition();
        return Component.translatable("message.wmd.place.entity_in_way", e.getDisplayName(), p.getX(), p.getY(), p.getZ());
    }

    public static void tell(Level level, Player player, Entity e) {
        if (player != null && !level.isClientSide) player.displayClientMessage(message(e), true);
    }
}
