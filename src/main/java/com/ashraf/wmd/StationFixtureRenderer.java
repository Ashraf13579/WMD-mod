package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class StationFixtureRenderer implements BlockEntityRenderer<StationFixtureBlockEntity> {

    @Override
    public void render(StationFixtureBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState st = be.getBlockState();
        if (st.getBlock() instanceof StationFixtureBlock f && f.meshDrawn()) return;
        Direction f = st.hasProperty(MachineBlock.FACING) ? st.getValue(MachineBlock.FACING) : Direction.NORTH;
        String name = st.getBlock() instanceof StationFixtureBlock.Console ? "station_console" : st.getBlock() instanceof StationFixtureBlock.Bunk ? "station_bunk"
                : st.getBlock() instanceof StationFixtureBlock.Battery ? "station_battery" : "oxygen_station";
        String[] bones = switch (name) {
            case "station_console" -> new String[] { "console", "screen", "glow" };
            case "station_bunk" -> new String[] { "bunk", "door", "lamp" };
            case "station_battery" -> new String[] { "base", "lamp" };
            default -> new String[] { "base", "cell", "tank", "needle", "lamp" };
        };
        MeshModel mesh = MeshModel.named(name);
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        if (mesh.has(bones[0])) {
            boolean status = name.equals("station_console") && near(be);
            for (String b : bones) {
                if (!mesh.has(b)) continue;
                if (status && b.equals("screen")) mesh.drawColoured(b, ps.last(), buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 40, 60, 95);
                else mesh.draw(b, ps.last(), buffers, light, overlay);
            }
            if (status) statusText(be, ps, buffers);
            if (name.equals("station_battery")) {
                long tick = be.getLevel() == null ? 0L : be.getLevel().getGameTime();
                for (int i = 0; i < 12; i++) {
                    boolean on = i < be.charge() && !(be.draining() && i == be.charge() - 1 && (tick / 10) % 2 == 0);
                    if (on && mesh.has("cell" + i)) mesh.drawColoured("cell" + i, ps.last(), buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 90, 170, 255);
                }
            }
        } else {
            int depth = name.equals("station_bunk") ? 2 : 1;
            BlockState hull = Wmd.STATION_HULL.get().defaultBlockState();
            for (int y = 0; y < 2; y++) for (int z = 0; z < depth; z++) {
                ps.pushPose();
                ps.translate(-0.5, y, z - 0.5);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(hull, ps, buffers, light, overlay);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    @Override public AABB getRenderBoundingBox(StationFixtureBlockEntity be) { return new AABB(be.getBlockPos()).inflate(2.0); }

    private static final double STATUS_R = 6.0;
    private static long scannedAt = Long.MIN_VALUE;
    private static Component[] lines = new Component[0];

    private static boolean near(StationFixtureBlockEntity be) {
        var p = Minecraft.getInstance().player;
        return p != null && p.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.0, be.getBlockPos().getZ() + 0.5) < STATUS_R * STATUS_R;
    }

    private static Component[] scan(StationFixtureBlockEntity be) {
        var level = be.getLevel();
        if (level == null) return lines;
        long now = level.getGameTime();
        if (now - scannedAt < 20 && now >= scannedAt) return lines;
        scannedAt = now;
        int slot = Orbit.slotAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5), mods = 0, breached = 0, wings = 0;
        String rod = null, mirror = null, bay = null;
        net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(be.getBlockPos());
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            if (!level.hasChunk(c.x + dx, c.z + dz)) continue;
            for (var e : level.getChunk(c.x + dx, c.z + dz).getBlockEntities().values()) {
                if (!(e instanceof StationModuleBlockEntity m) || Orbit.slotAt(e.getBlockPos().getX() + 0.5, e.getBlockPos().getZ() + 0.5) != slot) continue;
                mods++;
                if (m.breach() != 0) breached++;
                if (Station.WING.equals(m.type())) wings++;
                if (Station.ROD.equals(m.type())) rod = m.rdState() >= RodOfDeath.ARM && m.rdState() <= RodOfDeath.FALL ? "armed" : "safe";
                if (Station.MIRROR.equals(m.type())) mirror = m.rdState() == MirrorFocus.BURNING ? "burning" : "idle";
                if (Station.BAY.equals(m.type())) bay = m.bayState() == AsteroidBay.LAUNCH ? "launch" : "idle";
            }
        }
        java.util.List<Component> l = new java.util.ArrayList<>();
        l.add(Component.translatable("gui.wmd.station.scr.modules", mods));
        l.add(Component.translatable(breached == 0 ? "gui.wmd.station.scr.air_ok" : "gui.wmd.station.scr.air_bad", breached));
        l.add(Component.translatable("gui.wmd.station.scr.wings", wings));
        if (rod != null) l.add(Component.translatable("gui.wmd.station.scr.rod_" + rod));
        if (mirror != null) l.add(Component.translatable("gui.wmd.station.scr.mirror_" + mirror));
        if (bay != null) l.add(Component.translatable("gui.wmd.station.scr.bay_" + bay));
        lines = l.toArray(new Component[0]);
        return lines;
    }

    private static void statusText(StationFixtureBlockEntity be, PoseStack ps, MultiBufferSource buffers) {
        Component[] ls = scan(be);
        var font = Minecraft.getInstance().font;
        float s = 1.2F / 16.0F / 9.0F;
        ps.pushPose();
        ps.translate(4.3F / 16.0F, 24.3F / 16.0F, -3.9F / 16.0F);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.scale(s, -s, s);
        for (int i = 0; i < ls.length && i < 6; i++) {
            String key = ls[i].getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "";
            int col = key.endsWith("air_bad") || key.endsWith("rod_armed") || key.endsWith("mirror_burning") || key.endsWith("bay_launch") ? 0xFFFF6A5A : 0xFF9FD8FF;
            font.drawInBatch(ls[i], 0.0F, i * 10.0F, col, false, ps.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0,
                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        }
        ps.popPose();
    }
}
