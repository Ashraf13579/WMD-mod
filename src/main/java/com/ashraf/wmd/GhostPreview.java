package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class GhostPreview {

    private GhostPreview() {}

    private static final int RECHECK = 10, MAX_RED = 1500;
    private static final int STEEL = 0xC8D4E0, RED = 0xF0402E;

    private static final class Plan {
        Block block; BlockPos origin; Direction facing, mount = Direction.UP; long checkedAt;
        boolean ok;
        int minX, minY, minZ, maxX, maxY, maxZ;
        final List<BlockPos> red = new ArrayList<>();
    }

    private static Plan plan;
    private static final Map<Block, PortMap> PORTS = new HashMap<>();

    public static boolean enabled() { return !ClientConfig.SPEC.isLoaded() || ClientConfig.GHOST_PREVIEW.get(); }

    static MachineBlock machineOf(ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof MachineBlock mb)) return null;
        if (mb instanceof CcModuleBlock) return null;
        if (mb instanceof BayModuleBlock b && b.kind != BayKind.CORE) return null;
        if (mb instanceof LabModuleBlock l && l.kind != LabKind.CORE) return null;
        return mb.footprint().size() > 1 ? mb : null;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.options.hideGui || !enabled()) { plan = null; return; }
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Block mb = ghostOf(player.getMainHandItem());
        if (mb == null) { hand = InteractionHand.OFF_HAND; mb = ghostOf(player.getOffhandItem()); }
        if (mb == null) { plan = null; return; }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) { plan = null; return; }
        ItemStack stack = player.getItemInHand(hand);
        BlockItem bi = (BlockItem) stack.getItem();
        BlockPlaceContext ctx = new BlockPlaceContext(player, hand, stack, hit);
        if (!ctx.canPlace()) { plan = null; return; }
        ctx = bi.updatePlacementContext(ctx);
        if (ctx == null) { plan = null; return; }
        BlockPos origin = ctx.getClickedPos();
        Direction[] mf = ShipPlace.place(ctx, mb instanceof MachineBlock m && m.anyFace());
        Direction mount = mf == null ? Direction.UP : mf[0], facing = mf == null ? null : mf[1];
        if (facing == null) facing = ctx.getHorizontalDirection().getOpposite();
        long now = level.getGameTime();
        Plan p = plan;
        if (p == null || p.block != mb || !p.origin.equals(origin) || p.facing != facing || p.mount != mount || now - p.checkedAt >= RECHECK || now < p.checkedAt) {
            p = check(level, player, mb, ctx, origin, facing, now);
            p.mount = mount;
            plan = p;
        }
        draw(event, level, p);
    }

    static Block ghostOf(ItemStack s) {
        MachineBlock mb = machineOf(s);
        if (mb != null) return mb;
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof RadarStationBlock rb ? rb : null;
    }

    private static Plan check(ClientLevel level, LocalPlayer player, Block mb, BlockPlaceContext ctx, BlockPos origin, Direction facing, long now) {
        Plan p = new Plan();
        p.block = mb; p.origin = origin; p.facing = facing; p.checkedAt = now;
        BlockState st;
        try { st = mb.getStateForPlacement(ctx); } catch (RuntimeException e) { st = null; }
        p.ok = st != null && st.canSurvive(level, origin) && level.isUnobstructed(st, origin, CollisionContext.of(player));
        p.mount = st != null ? Machines.mount(st) : Direction.UP;
        p.minX = p.minY = p.minZ = Integer.MAX_VALUE;
        p.maxX = p.maxY = p.maxZ = Integer.MIN_VALUE;
        if (mb instanceof RadarStationBlock) radarCells(level, p);
        else if (mb instanceof InterceptorSiloT4Block) siloCells(level, p, InterceptorSiloT4Block.HALF, InterceptorSiloT4Block.SIZE, InterceptorSiloT4Block.DEPTH);
        else if (mb instanceof InterceptorSiloBlock) siloCells(level, p, 2, 5, InterceptorSiloBlock.DEPTH);
        else if (mb instanceof CcBuildingBlock) {
            for (int x = CcBuildingSpec.X0; x <= CcBuildingSpec.X1; x++) for (int y = CcBuildingSpec.Y0; y <= CcBuildingSpec.Y1; y++)
                for (int z = CcBuildingSpec.Z0; z <= CcBuildingSpec.Z1; z++) {
                    BlockPos w = Machines.toWorld(origin, facing, p.mount, x, y, z);
                    grow(p, w);
                    if (!w.equals(origin) && (!level.isInWorldBounds(w) || !level.getBlockState(w).canBeReplaced())) addRed(p, w);
                }
        } else if (mb instanceof MachineBlock mach) {
            Direction mount = mach.anyFace() ? ShipPlace.mountFor(ctx) : ShipPlace.upFor(ctx);
            for (BlockPos l : mach.footprint()) {
                BlockPos w = Machines.toWorld(origin, facing, mount, l.getX(), l.getY(), l.getZ());
                grow(p, w);
                if (!w.equals(origin) && (level.isOutsideBuildHeight(w) || !level.getBlockState(w).canBeReplaced())) addRed(p, w);
            }
        }
        return p;
    }

    private static void radarCells(ClientLevel level, Plan p) {
        int h = RadarStructure.HALF;
        for (int x = -h; x <= h; x++) for (int z = 0; z <= 2 * h; z++) for (int y = 0; y < RadarStructure.HEIGHT; y++) {
            BlockPos w = Machines.toWorld(p.origin, p.facing, p.mount, x, y, z);
            grow(p, w);
            if (w.equals(p.origin)) continue;
            if (level.isOutsideBuildHeight(w)) { addRed(p, w); continue; }
            BlockState b = level.getBlockState(w);
            if (!b.isAir() && !b.canBeReplaced()) addRed(p, w);
        }
        net.minecraft.world.entity.Entity in = PlaceGuard.blocker(level, Machines.toWorld(p.origin, p.facing, p.mount, -h, 0, 0), Machines.toWorld(p.origin, p.facing, p.mount, h, RadarStructure.HEIGHT - 1, 2 * h));
        if (in != null) { p.ok = false; addRed(p, in.blockPosition()); }
    }

    private static void siloCells(ClientLevel level, Plan p, int half, int size, int depth) {
        for (int x = -half; x <= half; x++) for (int z = 0; z < size; z++) for (int y = -depth; y <= 1; y++) {
            BlockPos w = Machines.toWorld(p.origin, p.facing, p.mount, x, y, z);
            grow(p, w);
            if (w.equals(p.origin)) continue;
            if (level.isOutsideBuildHeight(w)) { addRed(p, w); continue; }
            BlockState b = level.getBlockState(w);
            if (b.isAir()) continue;
            if (level.getBlockEntity(w) != null || (b.getDestroySpeed(level, w) < 0 && !b.canBeReplaced())) addRed(p, w);
        }
    }

    private static void grow(Plan p, BlockPos w) {
        p.minX = Math.min(p.minX, w.getX()); p.minY = Math.min(p.minY, w.getY()); p.minZ = Math.min(p.minZ, w.getZ());
        p.maxX = Math.max(p.maxX, w.getX()); p.maxY = Math.max(p.maxY, w.getY()); p.maxZ = Math.max(p.maxZ, w.getZ());
    }

    private static void addRed(Plan p, BlockPos w) { if (p.red.size() < MAX_RED) p.red.add(w.immutable()); }

    private static void draw(RenderLevelStageEvent event, ClientLevel level, Plan p) {
        Vec3 cam = event.getCamera().getPosition();
        boolean ok = p.ok && p.red.isEmpty();
        float pulse = 0.85F + 0.15F * (float) Math.sin((level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.25);
        final float e = 0.006F;
        float x0 = (float) (p.minX - cam.x) - e, y0 = (float) (p.minY - cam.y) - e, z0 = (float) (p.minZ - cam.z) - e;
        float x1 = (float) (p.maxX + 1 - cam.x) + e, y1 = (float) (p.maxY + 1 - cam.y) + e, z1 = (float) (p.maxZ + 1 - cam.z) + e;
        float gy = (float) (p.origin.getY() - cam.y) + 0.03F;

        BufferBuilder bb = WorldOverlayGeo.quads();
        WorldOverlayGeo.boxFaces(bb, x0, y0, z0, x1, y1, z1, WorldOverlayGeo.argb(ok ? WmdUi.BLUE : RED, ok ? 0.06F : 0.08F));
        for (BlockPos r : p.red) {
            float rx = (float) (r.getX() - cam.x), ry = (float) (r.getY() - cam.y), rz = (float) (r.getZ() - cam.z);
            WorldOverlayGeo.boxFaces(bb, rx - 0.012F, ry - 0.012F, rz - 0.012F, rx + 1.012F, ry + 1.012F, rz + 1.012F, WorldOverlayGeo.argb(RED, 0.38F * pulse));
        }
        float[][] front = frontEdge(p.facing, x0, z0, x1, z1);
        frontFace(bb, front, y0, y1, WorldOverlayGeo.argb(WmdUi.BLUE_LIGHT, 0.10F));
        arrow(bb, front, p.facing, gy, WorldOverlayGeo.argb(WmdUi.BLUE_LIGHT, 0.85F));
        PortMap pm = portsOf(p.block);
        if (pm != null) PortOverlay.addPorts(bb, cam, p.origin, p.facing, p.mount, pm, 0.85F, null, null);
        WorldOverlayGeo.draw(event, bb, 0);

        BufferBuilder lb = WorldOverlayGeo.lines();
        WorldOverlayGeo.boxLines(lb, x0, y0, z0, x1, y1, z1, WorldOverlayGeo.argb(ok ? STEEL : RED, 0.9F));
        int blue = WorldOverlayGeo.argb(WmdUi.BLUE_LIGHT, 1.0F);
        WorldOverlayGeo.line(lb, front[0][0], gy, front[0][1], front[1][0], gy, front[1][1], blue);
        WorldOverlayGeo.line(lb, front[0][0], y1, front[0][1], front[1][0], y1, front[1][1], blue);
        if (p.red.size() <= 300) for (BlockPos r : p.red) {
            float rx = (float) (r.getX() - cam.x), ry = (float) (r.getY() - cam.y), rz = (float) (r.getZ() - cam.z);
            WorldOverlayGeo.boxLines(lb, rx - 0.014F, ry - 0.014F, rz - 0.014F, rx + 1.014F, ry + 1.014F, rz + 1.014F, WorldOverlayGeo.argb(RED, 0.9F));
        }
        WorldOverlayGeo.draw(event, lb, 2.0F);
    }

    private static float[][] frontEdge(Direction facing, float x0, float z0, float x1, float z1) {
        return switch (facing) {
            case SOUTH -> new float[][] { { x0, z1 }, { x1, z1 } };
            case EAST -> new float[][] { { x1, z0 }, { x1, z1 } };
            case WEST -> new float[][] { { x0, z0 }, { x0, z1 } };
            default -> new float[][] { { x0, z0 }, { x1, z0 } };
        };
    }

    private static void frontFace(BufferBuilder bb, float[][] f, float y0, float y1, int argb) {
        WorldOverlayGeo.quad(bb, WorldOverlayGeo.v(f[0][0], y0, f[0][1]), WorldOverlayGeo.v(f[1][0], y0, f[1][1]), WorldOverlayGeo.v(f[1][0], y1, f[1][1]), WorldOverlayGeo.v(f[0][0], y1, f[0][1]), argb);
    }

    private static void arrow(BufferBuilder bb, float[][] f, Direction facing, float y, int argb) {
        float cx = (f[0][0] + f[1][0]) / 2, cz = (f[0][1] + f[1][1]) / 2;
        float dx = facing.getStepX(), dz = facing.getStepZ();
        float sx = -dz, sz = dx;
        float s0 = 0.25F, s1 = 1.15F, h1 = 2.15F, sw = 0.22F, hw = 0.6F;
        WorldOverlayGeo.quad(bb,
                WorldOverlayGeo.v(cx + dx * s0 - sx * sw, y, cz + dz * s0 - sz * sw), WorldOverlayGeo.v(cx + dx * s0 + sx * sw, y, cz + dz * s0 + sz * sw),
                WorldOverlayGeo.v(cx + dx * s1 + sx * sw, y, cz + dz * s1 + sz * sw), WorldOverlayGeo.v(cx + dx * s1 - sx * sw, y, cz + dz * s1 - sz * sw), argb);
        WorldOverlayGeo.quad(bb,
                WorldOverlayGeo.v(cx + dx * s1 - sx * hw, y, cz + dz * s1 - sz * hw), WorldOverlayGeo.v(cx + dx * s1 + sx * hw, y, cz + dz * s1 + sz * hw),
                WorldOverlayGeo.v(cx + dx * h1, y, cz + dz * h1), WorldOverlayGeo.v(cx + dx * h1, y, cz + dz * h1), argb);
    }

    private static PortMap portsOf(Block block) {
        if (PORTS.containsKey(block)) return PORTS.get(block);
        PortMap pm = null;
        try {
            if (block instanceof net.minecraft.world.level.block.EntityBlock eb) {
                BlockEntity be = eb.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
                if (be instanceof GuiMachine gm) pm = gm.guiPortMap();
            }
        } catch (Throwable t) {
            pm = null;
        }
        PORTS.put(block, pm);
        return pm;
    }
}
