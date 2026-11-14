package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class PlaceTurn {

    private PlaceTurn() {}

    record Way(Direction mount, Direction facing, Direction support) {}

    private static final Map<UUID, Integer> TURN = Statics.server(new HashMap<>());

    static int clientTurn;

    static int of(Player p) {
        if (p == null) return 0;
        if (p.level().isClientSide) return clientTurn;
        Integer t = TURN.get(p.getUUID());
        return t == null ? 0 : t;
    }

    static void set(Player p, int turn) {
        if (p == null || p.level().isClientSide) return;
        if (turn == 0) TURN.remove(p.getUUID());
        else TURN.put(p.getUUID(), Math.max(-64, Math.min(64, turn)));
    }

    static List<Way> ways(MachineBlock mb, BlockPlaceContext ctx, Direction[] plain) {
        Level level = ctx.getLevel();
        BlockPos origin = ctx.getClickedPos();
        List<Way> out = new ArrayList<>();
        Direction clickedSupport = ctx.getClickedFace().getOpposite();
        if (plain != null && fits(mb, level, origin, plain[0], plain[1])) out.add(new Way(plain[0], plain[1], clickedSupport));
        for (Direction d : Direction.values()) {
            BlockPos sup = origin.relative(d);
            if (!level.isInWorldBounds(sup) || level.getBlockState(sup).canBeReplaced()) continue;
            Direction face = d.getOpposite();
            Vec3 hv = Vec3.atCenterOf(sup).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            Direction[] mf;
            try {
                BlockPlaceContext c2 = new BlockPlaceContext(level, ctx.getPlayer(), ctx.getHand(), ctx.getItemInHand(), new BlockHitResult(hv, face, sup, false));
                if (!c2.getClickedPos().equals(origin)) continue;
                mf = mb.placement(c2);
            } catch (RuntimeException e) { mf = null; }
            if (mf == null) continue;
            Direction mount = mf[0];
            List<Direction> facings = new ArrayList<>();
            facings.add(mf[1]);
            for (Direction f : Direction.values()) if (f.getAxis() != mount.getAxis() && !facings.contains(f) && MachineBlock.FACING.getPossibleValues().contains(f)) facings.add(f);
            for (Direction f : facings) {
                if (f.getAxis() == mount.getAxis()) continue;
                if (contains(out, mount, f) || !fits(mb, level, origin, mount, f) || !open(mb, level, origin, mount, f, d)) continue;
                out.add(new Way(mount, f, d));
            }
        }
        return out;
    }

    static Direction[] choose(MachineBlock mb, BlockPlaceContext ctx, Direction[] plain) {
        int t = of(ctx.getPlayer());
        if (t == 0) {

            if (plain == null || !faceMounted(mb) || open(mb, ctx.getLevel(), ctx.getClickedPos(), plain[0], plain[1], ctx.getClickedFace().getOpposite())) return plain;
            for (Way w : ways(mb, ctx, plain)) if (open(mb, ctx.getLevel(), ctx.getClickedPos(), w.mount(), w.facing(), w.support())) return new Direction[] { w.mount(), w.facing() };
            return plain;
        }
        List<Way> w = ways(mb, ctx, plain);
        if (w.isEmpty()) return plain;
        Way p = w.get(Math.floorMod(t, w.size()));
        return new Direction[] { p.mount(), p.facing() };
    }

    private static boolean contains(List<Way> l, Direction mount, Direction facing) {
        for (Way w : l) if (w.mount() == mount && w.facing() == facing) return true;
        return false;
    }

    private static final java.util.Map<Class<?>, Boolean> FACE_MOUNTED = new java.util.concurrent.ConcurrentHashMap<>();

    static boolean faceMounted(MachineBlock mb) {
        return mb.anyFace() || FACE_MOUNTED.computeIfAbsent(mb.getClass(), c -> {
            try { return c.getMethod("placement", BlockPlaceContext.class).getDeclaringClass() != MachineBlock.class; }
            catch (NoSuchMethodException e) { return false; }
        });
    }

    static boolean open(MachineBlock mb, Level level, BlockPos origin, Direction mount, Direction facing, Direction support) {
        BlockPos o = origin.relative(support.getOpposite());
        for (BlockPos l : mb.footprint()) if (Machines.toWorld(origin, facing, mount, l.getX(), l.getY(), l.getZ()).equals(o)) return true;
        return level.isInWorldBounds(o) && level.getBlockState(o).canBeReplaced();
    }

    static boolean fits(MachineBlock mb, Level level, BlockPos origin, Direction mount, Direction facing) {
        if (facing.getAxis() == mount.getAxis() || !MachineBlock.FACING.getPossibleValues().contains(facing)) return false;
        for (BlockPos l : mb.footprint()) {
            BlockPos p = Machines.toWorld(origin, facing, mount, l.getX(), l.getY(), l.getZ());
            if (!level.isInWorldBounds(p)) return false;
            if (!p.equals(origin) && !level.getBlockState(p).canBeReplaced()) return false;
        }
        return true;
    }
}
