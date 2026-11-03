package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.BufferBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class PortOverlay {

    private PortOverlay() {}

    static final double RANGE = 16.0;
    private static final int SCAN_TICKS = 10;
    private static final float R_OUT = 0.24F, R_RIM = 0.17F, R_IN = 0.13F, LIFT = 0.03F;

    private record Machine(BlockPos pos, Direction facing, Direction mount, PortMap map, List<Laid> lay) {}

    private record Laid(PortMap.Port port, double wx, double wy, double wz, Direction n, float scale, float[][] t, int rim, int inner) {}

    record Marker(double x, double y, double z, Direction n, float r, PortMap.Port port) {}

    private static final List<Machine> FOUND = new ArrayList<>();
    private static Marker hovered;
    private static int scanTimer;

    public static boolean enabled() { return ClientConfig.SPEC.isLoaded() && ClientConfig.PORT_OVERLAY.get(); }

    static boolean active(LocalPlayer p) {
        return p != null && enabled() && (isTool(p.getMainHandItem()) || isTool(p.getOffhandItem()));
    }

    static boolean isTool(ItemStack s) {
        return !s.isEmpty() && (s.is(Wmd.FLUID_PIPE_ITEM.get()) || s.is(Wmd.BUSBAR_ITEM.get()) || s.is(Wmd.WRENCH.get()));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !active(mc.player)) {
            if (!FOUND.isEmpty()) FOUND.clear();
            hovered = null;
            scanTimer = 0;
            return;
        }
        if (scanTimer-- > 0) return;
        scanTimer = SCAN_TICKS;
        scan(mc.level, mc.player.blockPosition());
    }

    private static void scan(ClientLevel level, BlockPos at) {
        FOUND.clear();
        int r = (int) Math.ceil((RANGE + 24) / 16.0);
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        double max = (RANGE + 24) * (RANGE + 24);
        for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) {
            LevelChunk ch = level.getChunkSource().getChunk(cx + i, cz + j, false);
            if (ch == null) continue;
            for (BlockEntity be : ch.getBlockEntities().values()) {
                if (!(be instanceof GuiMachine gm) || be.isRemoved()) continue;
                if (be.getBlockPos().distSqr(at) > max) continue;
                PortMap pm;
                try { pm = gm.guiPortMap(); } catch (RuntimeException e) { continue; }
                if (pm == null || pm.ports.isEmpty()) continue;
                Direction fc = facingOf(be.getBlockState()), mt = Machines.mount(be.getBlockState());
                FOUND.add(new Machine(be.getBlockPos(), fc, mt, pm, layout(be.getBlockPos(), fc, mt, pm)));
            }
        }
    }

    static Direction facingOf(BlockState s) {
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (FOUND.isEmpty() || !active(mc.player)) { hovered = null; return; }
        Vec3 cam = event.getCamera().getPosition();
        Vec3 eye = mc.player.getEyePosition();
        List<Marker> markers = new ArrayList<>();
        BufferBuilder bb = WorldOverlayGeo.quads();
        boolean any = false;
        for (Machine m : FOUND) any |= addPorts(bb, cam, m.lay(), 1.0F, eye, markers);
        if (any) WorldOverlayGeo.draw(event, bb, 0);
        else { var md = bb.build(); if (md != null) md.close(); }
        hovered = pick(mc, markers);
    }

    static boolean addPorts(BufferBuilder bb, Vec3 cam, BlockPos origin, Direction facing, Direction mount, PortMap pm, float alpha, Vec3 eye, List<Marker> out) {
        return addPorts(bb, cam, layout(origin, facing, mount, pm), alpha, eye, out);
    }

    private static List<Laid> layout(BlockPos origin, Direction facing, Direction mount, PortMap pm) {
        Map<String, Integer> total = new HashMap<>(), seen = new HashMap<>();
        for (PortMap.Port p : pm.ports) total.merge(key(origin, facing, mount, p), 1, Integer::sum);
        List<Laid> out = new ArrayList<>(pm.ports.size());
        for (PortMap.Port p : pm.ports) {
            BlockPos cell = Machines.toWorld(origin, facing, mount, p.x(), p.y(), p.z());
            Direction n = Machines.dirToWorld(facing, mount, p.side());
            String k = key(origin, facing, mount, p);
            int cnt = total.get(k), idx = seen.merge(k, 1, Integer::sum) - 1;
            float scale = cnt > 2 ? 0.7F : 1.0F;
            float off = (idx - (cnt - 1) / 2.0F) * (cnt > 2 ? 0.34F : 0.5F);
            double wx = cell.getX() + 0.5 + n.getStepX() * (0.5 + LIFT), wy = cell.getY() + 0.5 + n.getStepY() * (0.5 + LIFT), wz = cell.getZ() + 0.5 + n.getStepZ() * (0.5 + LIFT);
            float[][] t = WorldOverlayGeo.tangents(n);
            wx += t[0][0] * off; wy += t[0][1] * off; wz += t[0][2] * off;
            int rim = p.kind() == PortMap.POWER ? WmdUi.CYAN : p.out() ? WmdUi.ORANGE : WmdUi.BLUE;
            int inner = switch (p.kind()) { case PortMap.FLUID -> fluidColour(p.fluidId()); case PortMap.ITEM -> 0xFFEFEFEF; default -> WmdUi.CYAN; };
            out.add(new Laid(p, wx, wy, wz, n, scale, t, rim, inner));
        }
        return out;
    }

    private static boolean addPorts(BufferBuilder bb, Vec3 cam, List<Laid> lay, float alpha, Vec3 eye, List<Marker> out) {
        boolean any = false;
        for (Laid l : lay) {
            PortMap.Port p = l.port();
            double wx = l.wx(), wy = l.wy(), wz = l.wz();
            Direction n = l.n();
            float[][] t = l.t();
            if (eye != null && eye.distanceToSqr(wx, wy, wz) > RANGE * RANGE) continue;
            float[] c = { (float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z) };
            Marker hv = hovered;
            boolean hot = out != null && hv != null && hv.port() == p && Math.abs(hv.x() - wx) < 1e-3 && Math.abs(hv.y() - wy) < 1e-3 && Math.abs(hv.z() - wz) < 1e-3;
            float s = l.scale() * (hot ? 1.25F : 1.0F);
            WorldOverlayGeo.ring(bb, c, t[0], t[1], 0F, R_OUT * s, WorldOverlayGeo.argb(0x05070A, 0.75F * alpha));
            WorldOverlayGeo.ring(bb, c, t[0], t[1], R_RIM * s, R_OUT * s - 0.025F * s, WorldOverlayGeo.argb(hot ? 0xFFFFFF : l.rim(), 0.95F * alpha));
            float[] c2 = { c[0] + n.getStepX() * 0.004F, c[1] + n.getStepY() * 0.004F, c[2] + n.getStepZ() * 0.004F };
            WorldOverlayGeo.ring(bb, c2, t[0], t[1], 0F, R_IN * s, WorldOverlayGeo.argb(l.inner(), 0.95F * alpha));
            if (out != null) out.add(new Marker(wx, wy, wz, n, R_OUT * s, p));
            any = true;
        }
        return any;
    }

    private static String key(BlockPos origin, Direction facing, Direction mount, PortMap.Port p) {
        return Machines.toWorld(origin, facing, mount, p.x(), p.y(), p.z()).asLong() + "/" + p.side();
    }

    private static Marker pick(Minecraft mc, List<Marker> markers) {
        if (markers.isEmpty()) return null;
        Vec3 eye = mc.player.getEyePosition(), dir = mc.player.getViewVector(1.0F);
        double limit = RANGE + 1;
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() == HitResult.Type.BLOCK) limit = Math.min(limit, hr.getLocation().distanceTo(eye) + 0.6);
        Marker best = null;
        double bestT = limit;
        for (Marker m : markers) {
            double nx = m.n().getStepX(), ny = m.n().getStepY(), nz = m.n().getStepZ();
            double den = dir.x * nx + dir.y * ny + dir.z * nz;
            if (Math.abs(den) < 1e-4) continue;
            double tt = ((m.x() - eye.x) * nx + (m.y() - eye.y) * ny + (m.z() - eye.z) * nz) / den;
            if (tt <= 0 || tt >= bestT) continue;
            double hx = eye.x + dir.x * tt - m.x(), hy = eye.y + dir.y * tt - m.y(), hz = eye.z + dir.z * tt - m.z();
            if (hx * hx + hy * hy + hz * hz > m.r() * m.r() * 1.3) continue;
            bestT = tt;
            best = m;
        }
        return best;
    }

    @SubscribeEvent
    static void onGui(RenderGuiEvent.Post event) {
        Marker hv = hovered;
        PortMap.Port p = hv == null ? null : hv.port();
        Minecraft mc = Minecraft.getInstance();
        if (p == null || mc.screen != null || mc.options.hideGui || !active(mc.player)) return;
        GuiGraphics g = event.getGuiGraphics();
        Component what = switch (p.kind()) {
            case PortMap.FLUID -> fluidName(p.fluidId());
            case PortMap.ITEM -> Component.translatable("gui.wmd.ports.items");
            default -> Component.translatable("gui.wmd.ports.power");
        };
        MutableComponent dirText = p.kind() == PortMap.POWER ? Component.translatable("gui.wmd.kit.power").withColor(WmdUi.CYAN)
                : p.out() ? Component.translatable("gui.wmd.kit.out").withColor(WmdUi.ORANGE_LIGHT) : Component.translatable("gui.wmd.kit.in").withColor(WmdUi.BLUE_LIGHT);
        MutableComponent line = Component.empty().append(what.copy().withColor(WmdUi.TEXT)).append(Component.literal("  ")).append(dirText);
        int w = mc.font.width(line), x = g.guiWidth() / 2 - w / 2, y = g.guiHeight() / 2 + 12;
        g.fill(x - 4, y - 3, x + w + 4, y + 11, 0xC0101418);
        g.fill(x - 4, y - 3, x - 2, y + 11, p.kind() == PortMap.POWER ? WmdUi.CYAN : p.out() ? WmdUi.ORANGE : WmdUi.BLUE);
        g.drawString(mc.font, line, x, y, WmdUi.TEXT, false);
    }

    static int fluidColour(String id) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.id().equals(id)) return e.color() | 0xFF000000;
        return 0xFF808080;
    }

    static Component fluidName(String id) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.id().equals(id)) return e.type().get().getDescription();
        return Component.literal(id);
    }
}
