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
        BlockState state;
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
        return mb;
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
        Direction[] mf = mb instanceof MachineBlock m ? PlaceTurn.choose(m, ctx, m.placement(ctx)) : ShipPlace.place(ctx, false);
        Direction mount = mf == null ? Direction.UP : mf[0], facing = mf == null ? null : mf[1];
        if (facing == null) facing = ctx.getHorizontalDirection().getOpposite();
        long now = level.getGameTime();
        Plan p = plan;
        if (p == null || p.block != mb || !p.origin.equals(origin) || p.facing != facing || p.mount != mount || now - p.checkedAt >= RECHECK || now < p.checkedAt) {
            p = check(level, player, mb, ctx, origin, facing, mount, now);
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

    private static Plan check(ClientLevel level, LocalPlayer player, Block mb, BlockPlaceContext ctx, BlockPos origin, Direction facing, Direction chosenMount, long now) {
        Plan p = new Plan();
        p.block = mb; p.origin = origin; p.facing = facing; p.checkedAt = now;
        BlockState st;
        try { st = mb.getStateForPlacement(ctx); } catch (RuntimeException e) { st = null; }
        p.ok = st != null && st.canSurvive(level, origin) && level.isUnobstructed(st, origin, CollisionContext.of(player));
        p.mount = st != null ? Machines.mount(st) : Direction.UP;
        p.state = st;
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
            Direction mount = chosenMount;
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
        org.joml.Matrix4f pre = null;
        if (SableBridge.isOnShip(level, p.origin)) {
            Vec3 o = Vec3.atLowerCornerOf(p.origin);
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 w0 = ShipFrame.drawnWorld(level, p.origin, o, pt);
            if (w0 != null && w0.distanceToSqr(o) > 1.0) {
                Vec3 ex = ShipFrame.drawnWorld(level, p.origin, o.add(1, 0, 0), pt).subtract(w0), ey = ShipFrame.drawnWorld(level, p.origin, o.add(0, 1, 0), pt).subtract(w0),
                        ez = ShipFrame.drawnWorld(level, p.origin, o.add(0, 0, 1), pt).subtract(w0), t = w0.subtract(cam);
                pre = new org.joml.Matrix4f((float) ex.x, (float) ex.y, (float) ex.z, 0.0F, (float) ey.x, (float) ey.y, (float) ey.z, 0.0F,
                        (float) ez.x, (float) ez.y, (float) ez.z, 0.0F, (float) t.x, (float) t.y, (float) t.z, 1.0F);
                cam = o;
            }
        }
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
        WorldOverlayGeo.draw(event, bb, 0, pre);

        BufferBuilder lb = WorldOverlayGeo.lines();
        WorldOverlayGeo.boxLines(lb, x0, y0, z0, x1, y1, z1, WorldOverlayGeo.argb(ok ? STEEL : RED, 0.9F));
        int blue = WorldOverlayGeo.argb(WmdUi.BLUE_LIGHT, 1.0F);
        WorldOverlayGeo.line(lb, front[0][0], gy, front[0][1], front[1][0], gy, front[1][1], blue);
        WorldOverlayGeo.line(lb, front[0][0], y1, front[0][1], front[1][0], y1, front[1][1], blue);
        if (p.red.size() <= 300) for (BlockPos r : p.red) {
            float rx = (float) (r.getX() - cam.x), ry = (float) (r.getY() - cam.y), rz = (float) (r.getZ() - cam.z);
            WorldOverlayGeo.boxLines(lb, rx - 0.014F, ry - 0.014F, rz - 0.014F, rx + 1.014F, ry + 1.014F, rz + 1.014F, WorldOverlayGeo.argb(RED, 0.9F));
        }
        WorldOverlayGeo.draw(event, lb, 2.0F, pre);
        model(event, level, p, cam, pre, ok);
    }

    private static final Map<BlockState, BlockEntity> DUMMY = Statics.client(new HashMap<>());
    private static final java.util.Set<Block> NO_MODEL = Statics.client(new java.util.HashSet<>());

    private static final java.util.Set<Block> LOGGED_OFF = Statics.client(new java.util.HashSet<>());

    private static final class Bounds implements com.mojang.blaze3d.vertex.VertexConsumer {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        int n;
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z); x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z); n++;
            return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
        net.minecraft.world.phys.AABB box() { return new net.minecraft.world.phys.AABB(x0, y0, z0, x1, y1, z1); }
    }

    private static final com.mojang.blaze3d.vertex.VertexConsumer NOOP = new com.mojang.blaze3d.vertex.VertexConsumer() {
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    private static void model(RenderLevelStageEvent event, ClientLevel level, Plan p, Vec3 ref, org.joml.Matrix4f pre, boolean ok) {
        if (p.state == null || NO_MODEL.contains(p.block) || !(p.block instanceof net.minecraft.world.level.block.EntityBlock eb)) return;
        Minecraft mc = Minecraft.getInstance();

        Vec3 camPlot = AnchorClient.toPlot(level, p.origin, event.getCamera().getPosition(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (new net.minecraft.world.phys.AABB(p.minX, p.minY, p.minZ, p.maxX + 1, p.maxY + 1, p.maxZ + 1).inflate(Balance.GHOST_MODEL_CLEAR).contains(camPlot)) return;
        BlockEntity be = DUMMY.get(p.state);
        if (be == null || be.getLevel() != level || !be.getBlockPos().equals(p.origin)) {
            try { be = eb.newBlockEntity(p.origin, p.state); } catch (RuntimeException e) { be = null; }
            if (be == null) { NO_MODEL.add(p.block); return; }
            be.setLevel(level);
            DUMMY.clear();
            DUMMY.put(p.state, be);
        }
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<BlockEntity> r = mc.getBlockEntityRenderDispatcher().getRenderer(be);
        if (r == null) { NO_MODEL.add(p.block); return; }
        net.minecraft.client.renderer.MultiBufferSource.BufferSource real = mc.renderBuffers().bufferSource();
        real.endBatch();
        net.minecraft.client.renderer.MultiBufferSource only = rt -> MeshModel.GHOST_TYPES.contains(rt) ? real.getBuffer(rt) : NOOP;

        Bounds bnd = new Bounds();
        {
            com.mojang.blaze3d.vertex.PoseStack dry = new com.mojang.blaze3d.vertex.PoseStack();
            dry.translate(p.origin.getX() - ref.x, p.origin.getY() - ref.y, p.origin.getZ() - ref.z);
            MeshModel.ghostAlpha = Balance.GHOST_MODEL_ALPHA;
            try {
                r.render(be, event.getPartialTick().getGameTimeDeltaPartialTick(false), dry, rt -> MeshModel.GHOST_TYPES.contains(rt) ? bnd : NOOP,
                        net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            } catch (Throwable t) {
                NO_MODEL.add(p.block);
                return;
            } finally {
                MeshModel.ghostAlpha = 0;
            }
        }
        if (bnd.n == 0) return;
        Vec3 camRel = pre == null ? Vec3.ZERO : camPlot.subtract(ref);
        if (bnd.box().inflate(Balance.GHOST_MODEL_CLEAR).contains(camRel)) return;
        net.minecraft.world.phys.AABB own = new net.minecraft.world.phys.AABB(p.minX - ref.x, p.minY - ref.y, p.minZ - ref.z, p.maxX + 1 - ref.x, p.maxY + 1 - ref.y, p.maxZ + 1 - ref.z);
        if (!own.inflate(3.0).contains(bnd.box().getCenter()) && LOGGED_OFF.add(p.block))
            Wmd.LOGGER.info("[ghost] {} model drawn off its box: model {} vs box {} (mount {}, facing {})", p.block, bnd.box(), own, p.mount, p.facing);
        org.joml.Matrix4fStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(event.getModelViewMatrix());
        if (pre != null) mv.mul(pre);
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.vertex.PoseStack ps = new com.mojang.blaze3d.vertex.PoseStack();
        ps.translate(p.origin.getX() - ref.x, p.origin.getY() - ref.y, p.origin.getZ() - ref.z);
        MeshModel.ghostAlpha = Balance.GHOST_MODEL_ALPHA;
        MeshModel.ghostRgb = ok ? 0xA0C8FF : 0xFF8080;
        try {
            r.render(be, event.getPartialTick().getGameTimeDeltaPartialTick(false), ps, only, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        } catch (Throwable t) {
            NO_MODEL.add(p.block);
            Wmd.LOGGER.warn("Placement ghost: the renderer of {} cannot draw a ghost, outline only", p.block, t);
        } finally {
            MeshModel.ghostAlpha = 0;
        }
        for (net.minecraft.client.renderer.RenderType t : MeshModel.GHOST_TYPES) real.endBatch(t);
        mv.popMatrix();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
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
