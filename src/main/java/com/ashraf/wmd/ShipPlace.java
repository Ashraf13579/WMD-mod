package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class ShipPlace {

    private ShipPlace() {}

    static final double LEVEL_DEG = 30.0;

    public static Direction facing(BlockPlaceContext ctx) {
        Direction vanilla = ctx.getHorizontalDirection().getOpposite();
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!SableBridge.isOnShip(level, pos)) return vanilla;
        Vec3 c = Vec3.atCenterOf(pos), w0 = SableBridge.toWorld(level, c);
        if (w0 == null || w0 == c) return vanilla;
        double upY = SableBridge.toWorld(level, c.add(0.0, 1.0, 0.0)).subtract(w0).y;
        if (upY < Math.cos(Math.toRadians(LEVEL_DEG)) && ctx.getClickedFace() != Direction.UP) {
            Player p = ctx.getPlayer();
            if (p != null && !level.isClientSide) {
                int tilt = (int) Math.round(Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, upY)))));
                p.displayClientMessage(Component.translatable("message.wmd.ship_not_level", tilt), true);
            }
            return null;
        }
        Player p = ctx.getPlayer();
        if (p == null) return vanilla;
        Vec3 toPlayer = p.getEyePosition().subtract(w0);
        if (toPlayer.lengthSqr() < 1e-6) toPlayer = p.getLookAngle().reverse();
        Direction best = vanilla;
        double bestDot = -Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 wd = SableBridge.toWorld(level, c.add(d.getStepX(), 0.0, d.getStepZ())).subtract(w0);
            double dot = wd.dot(toPlayer);
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
    }

    public static Direction upFor(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!SableBridge.isOnShip(level, pos)) return Direction.UP;
        Vec3 c = Vec3.atCenterOf(pos), w0 = SableBridge.toWorld(level, c);
        if (w0 == null || w0 == c) return Direction.UP;
        Direction best = Direction.UP;
        double bestY = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            Vec3 w = SableBridge.toWorld(level, c.add(d.getStepX(), d.getStepY(), d.getStepZ()));
            if (w == null) continue;
            double y = w.subtract(w0).y;
            if (y > bestY + 1.0E-6) { bestY = y; best = d; }
        }
        return best;
    }

    public static Direction away(Level level, BlockPos pos, Direction mount, net.minecraft.world.entity.LivingEntity placer) {
        if (!SableBridge.isOnShip(level, pos)) return placer.getDirection();
        Vec3 c = Vec3.atCenterOf(pos), w0 = SableBridge.toWorld(level, c);
        if (w0 == null) w0 = c;
        Vec3 look = placer.getLookAngle();
        Direction best = placer.getDirection();
        double bestDot = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == mount.getAxis()) continue;
            Vec3 w = SableBridge.toWorld(level, c.add(d.getStepX(), d.getStepY(), d.getStepZ()));
            double dot = (w == null ? c.add(d.getStepX(), d.getStepY(), d.getStepZ()) : w).subtract(w0).dot(look);
            if (dot > bestDot) { bestDot = dot; best = d; }
        }
        return best;
    }

    public static Direction[] place(BlockPlaceContext ctx, boolean anyFace) { return place(ctx, anyFace, false); }

    public static Direction[] place(BlockPlaceContext ctx, boolean anyFace, boolean json) {
        Direction mount = anyFace ? mountFor(ctx) : upFor(ctx);
        Direction facing = !anyFace && mount == Direction.UP && !SableBridge.isOnShip(ctx.getLevel(), ctx.getClickedPos()) ? facing(ctx)
                : json && mount.getAxis() != Direction.Axis.Y ? mountFacing(ctx, mount, mount, mount.getOpposite()) : mountFacing(ctx, mount);
        return facing == null ? null : new Direction[] { mount, facing };
    }

    public static Direction mountFor(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        if (face.getAxis() == Direction.Axis.Y) return face;
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (SableBridge.isOnShip(level, pos)) {
            Vec3 c = Vec3.atCenterOf(pos), w0 = SableBridge.toWorld(level, c);
            if (w0 != null && w0 != c && SableBridge.toWorld(level, c.add(0.0, 1.0, 0.0)).subtract(w0).y < Math.cos(Math.toRadians(LEVEL_DEG))) return face;
        }
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) ? Direction.UP : face;
    }

    public static Direction mountFacing(BlockPlaceContext ctx, Direction mount) {
        return mountFacing(ctx, mount, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    }

    public static Direction mountFacing(BlockPlaceContext ctx, Direction mount, Direction... options) {
        Level level = ctx.getLevel();
        Player p = ctx.getPlayer();
        if (p == null) return ctx.getHorizontalDirection().getOpposite();
        Vec3 c = Vec3.atCenterOf(ctx.getClickedPos()), w0 = SableBridge.toWorld(level, c);
        if (w0 == null) w0 = c;
        Vec3 toPlayer = p.getEyePosition().subtract(w0);
        if (toPlayer.lengthSqr() < 1e-6) toPlayer = p.getLookAngle().reverse();
        Direction best = ctx.getHorizontalDirection().getOpposite();
        double bestDot = -Double.MAX_VALUE;
        if (options.length > 0) best = options[0];
        for (Direction f : options) {
            double[] v = Machines.mountTurn(mount, f.getStepX(), 0.0, f.getStepZ());
            Vec3 wv = SableBridge.toWorld(level, c.add(v[0], v[1], v[2]));
            Vec3 wd = (wv == null ? c.add(v[0], v[1], v[2]) : wv).subtract(w0);
            double dot = wd.dot(toPlayer);
            if (dot > bestDot) { bestDot = dot; best = f; }
        }
        return best;
    }
}
