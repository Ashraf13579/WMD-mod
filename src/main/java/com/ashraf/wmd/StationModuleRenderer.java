package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

public class StationModuleRenderer implements BlockEntityRenderer<StationModuleBlockEntity> {

    private static final int[][] LED = { { 74, 163, 255 }, { 235, 245, 255 }, { 255, 60, 50 } };
    private static final float PX = 1.0F / 16.0F;

    private static final java.util.Map<Long, float[]> DOORS = new java.util.HashMap<>();
    private static final float DOOR_TICKS = 20.0F;

    private static float door(StationModuleBlockEntity be, int k, boolean open, float t) {
        long key = be.getBlockPos().asLong() * 31L + k;
        float[] d = DOORS.get(key);
        float target = open ? 1.0F : 0.0F;
        if (d == null || t - d[1] > 10.0F || t < d[1]) { d = new float[] { target, t }; DOORS.put(key, d); }
        float step = (t - d[1]) / DOOR_TICKS;
        d[1] = t;
        d[0] = open ? Math.min(1.0F, d[0] + step) : Math.max(0.0F, d[0] - step);
        return CargoPodRenderer.smooth(d[0]);
    }
    private static final List<String> CORE_BONES = new ArrayList<>(), HAB_BONES = new ArrayList<>();

    static {
        CORE_BONES.addAll(List.of("core", "dock", "mast", "mast_hi"));
        for (int k = 0; k < 12; k++) CORE_BONES.add("dock_latch" + k);
        for (int k = 0; k < 3; k++) CORE_BONES.add("dock_petal" + k);
        HAB_BONES.addAll(List.of("hab", "rad_l", "rad_r"));
    }

    @Override
    public void render(StationModuleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named(Station.SENSOR.equals(be.type()) ? "sensor_array" : Station.ROD.equals(be.type()) ? "rod_module" : Station.MIRROR.equals(be.type()) ? "mirror_module" : Station.BAY.equals(be.type()) ? "asteroid_bay" : "station_" + be.type());
        String root = switch (be.type()) { case Station.HABITAT -> "hab"; case Station.WING -> "wing"; case Station.SENSOR -> "sensor"; case Station.LASER -> "laser"; case Station.ROD -> "rod"; case Station.MIRROR -> "mirror"; case Station.BAY -> "bay"; case Station.TURRET -> "tur_mount"; default -> "core"; };
        if (!mesh.has(root) || be.getLevel() == null) return;
        float t = be.getLevel().getGameTime() + pt;
        int orbitT = OrbitClock.phase(be.getLevel().getGameTime());
        boolean sun = orbitT < Balance.STATION_SUN_TICKS;
        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (be.facing()) { case SOUTH -> -90.0F; case WEST -> 180.0F; case NORTH -> 90.0F; default -> 0.0F; }));
        if (be.arrive() > 0L) arrival(be, ps, t);
        switch (be.type()) {
            case Station.HABITAT -> { ps.translate(-0.5, 0.0, 0.0); habitat(be, mesh, ps, buffers, light, overlay, t, orbitT); }
            case Station.WING -> { ps.translate(-0.5, -1.0, 0.0); wing(be, mesh, ps, buffers, light, overlay, orbitT, t); }
            case Station.SENSOR -> { ps.translate(-0.5, -1.0, 0.0); sensor(be, mesh, ps, buffers, light, overlay, t); }
            case Station.LASER -> { ps.translate(-0.5, -1.0, 0.0); laser(be, mesh, ps, buffers, light, overlay, t); }
            case Station.ROD -> { ps.translate(-0.5, 0.0, 0.0); rod(be, mesh, ps, buffers, light, overlay, t); }
            case Station.MIRROR -> { ps.translate(-0.5, 0.0, 0.0); mirror(be, mesh, ps, buffers, light, overlay, t); }
            case Station.BAY -> { ps.translate(-0.5, 0.0, 0.0); bay(be, mesh, ps, buffers, light, overlay, t); }
            case Station.TURRET -> { ps.translate(0.0, 2.0, 0.0); turret(be, mesh, ps, buffers, light, overlay, t, pt); }
            default -> core(be, mesh, ps, buffers, light, overlay, t, sun);
        }
        ps.popPose();
    }

    private static final java.util.Map<Integer, Long> ALERT = new java.util.HashMap<>();

    private static int slotOf(StationModuleBlockEntity be) { return Orbit.slotAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5); }

    private static void alert(StationModuleBlockEntity be) { if (be.getLevel() != null) ALERT.put(slotOf(be), be.getLevel().getGameTime() + 40L); }

    private static void lamps(StationModuleBlockEntity be, PoseStack ps, MultiBufferSource buffers, float t, float[][] at) {
        Long until = ALERT.get(slotOf(be));
        if (until == null || be.getLevel() == null || until < be.getLevel().getGameTime()) return;
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
        org.joml.Matrix4f m = ps.last().pose();
        for (float[] p : at) {
            float x = p[0] * PX, y = p[1] * PX, z = p[2] * PX, s = 2.0F * PX;
            quad(vc, m, x - s, y - s, z, x + s, y - s, z, x + s, y + s, z, x - s, y + s, z, 255, 40, 30, 230, 230);
            quad(vc, m, x, y - s, z - s, x, y - s, z + s, x, y + s, z + s, x, y + s, z - s, 255, 40, 30, 230, 230);
            for (int k = 0; k < 2; k++) {
                double a = t * 0.3 + k * Math.PI;
                float dx = (float) Math.cos(a), dz = (float) Math.sin(a), L = 48.0F * PX, w = 10.0F * PX, ex = x + dx * L, ez = z + dz * L;
                quad(vc, m, x - dz * PX, y, z + dx * PX, x + dz * PX, y, z - dx * PX, ex + dz * w, y - 10.0F * PX, ez - dx * w, ex - dz * w, y - 10.0F * PX, ez + dx * w, 255, 50, 40, 120, 0);
                quad(vc, m, x, y + PX, z, x, y - PX, z, ex, y - 10.0F * PX - w, ez, ex, y - 10.0F * PX + w, ez, 255, 50, 40, 120, 0);
            }
        }
        var bp = be.getBlockPos();
        VeilBridge.glow(0x414C5254L ^ bp.asLong(), bp.getX() + 0.5, bp.getY() + 5.0, bp.getZ() + 0.5, 1.0F, 0.12F, 0.08F, 14.0F,
                0.6F + 0.4F * (float) Math.sin(t * 0.3), 0.2F, be.getLevel().getGameTime());
    }

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a0, int a1) {
        vc.addVertex(m, ax, ay, az).setColor(r, g, b, a0); vc.addVertex(m, bx, by, bz).setColor(r, g, b, a0);
        vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a1); vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a1);
        vc.addVertex(m, dx, dy, dz).setColor(r, g, b, a1); vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a1);
        vc.addVertex(m, bx, by, bz).setColor(r, g, b, a0); vc.addVertex(m, ax, ay, az).setColor(r, g, b, a0);
    }

    private static void arrival(StationModuleBlockEntity be, PoseStack ps, float t) {
        float u = Math.max(0.0F, Math.min(1.0F, (t - be.arrive()) / Balance.STATION_ARRIVE_TICKS)), e = (1.0F - u) * (1.0F - u);
        boolean core = Station.CORE.equals(be.type());
        if (core) ps.translate(0.0, -48.0F * e, 0.0); else ps.translate(40.0F * e, 0.0, 0.0);
        var level = be.getLevel();
        if (level != null && u < 1.0F && level.random.nextFloat() < 0.35F) {
            var n = be.facing().getNormal();
            double ox = core ? 0.0 : n.getX() * 40.0 * e, oy = core ? -48.0 * e : 0.0, oz = core ? 0.0 : n.getZ() * 40.0 * e;
            for (int i = 0; i < 2; i++)
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, be.getBlockPos().getX() + 0.5 + ox + (level.random.nextFloat() - 0.5) * 9.0,
                        be.getBlockPos().getY() + 4.0 + oy + (level.random.nextFloat() - 0.5) * 7.0, be.getBlockPos().getZ() + 0.5 + oz + (level.random.nextFloat() - 0.5) * 9.0,
                        (level.random.nextFloat() - 0.5) * 0.3, (level.random.nextFloat() - 0.5) * 0.3, (level.random.nextFloat() - 0.5) * 0.3);
        }
    }

    private void core(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t, boolean sun) {
        for (String b : CORE_BONES) if (!b.startsWith("dock_latch")) draw(mesh, b, ps, buffers, light, overlay);
        for (int k = 0; k < 12; k++) {
            float lock = be.dock() > 0L ? Math.max(0.0F, Math.min(1.0F, (t - be.dock() - k * (Balance.STATION_DOCK_TICKS / 12.0F)) / 5.0F)) : 0.0F;
            double a = Math.toRadians(-(30.0 * k + 15.0));
            ps.pushPose();
            ps.translate(-Math.cos(a) * 2.5F * PX * lock, 0.0, -Math.sin(a) * 2.5F * PX * lock);
            draw(mesh, "dock_latch" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
        for (int k = 0; k < 4; k++) port(mesh, "port" + k, be.port(k), door(be, k, be.port(k) == StationLife.OPEN, t), ps, buffers, light, overlay);
        float hatch = door(be, 4, be.port(4) == StationLife.OPEN, t);
        ps.pushPose(); ps.translate(24.0F * PX * hatch, 0.0, 0.0); draw(mesh, "hatch_a", ps, buffers, light, overlay); ps.popPose();
        ps.pushPose(); ps.translate(-24.0F * PX * hatch, 0.0, 0.0); draw(mesh, "hatch_b", ps, buffers, light, overlay); ps.popPose();
        for (int k = 0; k < 8; k++) if ((be.breach() >> k & 1) != 0) draw(mesh, "core_breach" + k, ps, buffers, light, overlay);
        if (be.wrecked()) ClientStationWreck.burn(be, false);
        lamps(be, ps, buffers, t, new float[][] { { 36, 118, 36 }, { -36, 118, -36 } });
        int blink = (int) (t / 8.0F) % 4;
        if (blink == 0) draw(mesh, "strobe_g", ps, buffers, light, overlay);
        if (blink == 2) draw(mesh, "strobe_r", ps, buffers, light, overlay);
        if (!sun) draw(mesh, "flood", ps, buffers, light, overlay);

        float mx = 36.77F * PX, mz = 36.77F * PX;
        ps.pushPose();
        ps.translate(mx, 0.0, mz); ps.mulPose(Axis.YP.rotationDegrees(t * 0.6F)); ps.translate(-mx, 0.0, -mz);
        draw(mesh, "dish", ps, buffers, light, overlay);
        ps.popPose();
    }

    private void habitat(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t, int orbitT) {
        for (String b : HAB_BONES) draw(mesh, b, ps, buffers, light, overlay);
        port(mesh, "port", be.port(1), door(be, 1, be.port(1) == StationLife.OPEN, t), ps, buffers, light, overlay);
        for (int k = 0; k < 8; k++) if ((be.breach() >> k & 1) != 0) draw(mesh, "hab_breach" + k, ps, buffers, light, overlay);
        if (be.wrecked()) ClientStationWreck.burn(be, true);
        lamps(be, ps, buffers, t, new float[][] { { 40, 120, 20 }, { Station.HAB_LEN * 16 - 40, 120, -20 } });
        ps.pushPose();
        rotX(ps, 72.0F * PX, 0.0F, t * 0.45F);
        draw(mesh, "spin", ps, buffers, light, overlay);
        ps.popPose();

        for (int n = 0; n < 4; n++) port(mesh, "sport" + n, be.port(2 + n), door(be, 2 + n, be.port(2 + n) == StationLife.OPEN, t), ps, buffers, light, overlay);
    }

    private void wing(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, int orbitT, float t) {
        draw(mesh, "wing", ps, buffers, light, overlay);

        float d = be.arrive() > 0L ? 0.0F : be.deploy() > 0L ? Math.max(0.0F, Math.min(1.0F, (t - be.deploy()) / Balance.STATION_DEPLOY_TICKS)) : 1.0F;
        float lids = Math.min(1.0F, d / 0.15F), e = Math.max(0.0F, Math.min(1.0F, (d - 0.15F) / 0.85F)), f = 1.0F - e;

        float day = Balance.STATION_SUN_TICKS, night = Balance.STATION_ORBIT_TICKS - Balance.STATION_SUN_TICKS;
        float ang = d < 1.0F || be.wrecked() ? 0.0F : orbitT < day ? -100.0F + 200.0F * orbitT / day : 100.0F - 200.0F * (orbitT - day) / night;
        ps.pushPose();
        rotX(ps, 40.0F * PX, 0.0F, ang);
        draw(mesh, "joint", ps, buffers, light, overlay);
        for (String a : new String[] { "p", "s" }) {
            float zs = a.equals("p") ? 1.0F : -1.0F;
            ps.pushPose();
            outArray(ps, a);
            draw(mesh, "box_" + a, ps, buffers, light, overlay);
            ps.pushPose(); rotX(ps, 46.5F * PX, zs * 40.0F * PX, -100.0F * zs * lids); draw(mesh, "lid_" + a + "_a", ps, buffers, light, overlay); ps.popPose();
            ps.pushPose(); rotX(ps, 33.5F * PX, zs * 40.0F * PX, 100.0F * zs * lids); draw(mesh, "lid_" + a + "_b", ps, buffers, light, overlay); ps.popPose();
            if (d < 0.15F) { ps.popPose(); continue; }
            for (int n = 0; n < 3; n++) {
                ps.pushPose(); ps.translate(0.0, 0.0, -zs * 110.0F * n * f * PX); draw(mesh, "mast_" + a + n, ps, buffers, light, overlay); ps.popPose();
            }
            ps.pushPose(); ps.translate(0.0, 0.0, -zs * 330.0F * f * PX); draw(mesh, "tip_" + a, ps, buffers, light, overlay); ps.popPose();
            if (be.wrecked()) draw(mesh, "torn_" + a, ps, buffers, light, overlay);
            for (String side : new String[] { "A", "B" }) for (int n = 0; n < 7; n++) {
                if (be.wrecked() && side.equals("A") && n >= 5) continue;
                ps.pushPose();
                ps.translate(0.0, 0.0, -zs * 46.0F * n * f * PX);
                rotX(ps, 40.0F * PX, zs * (40.0F + 48.0F * n) * PX, (n % 2 == 0 ? 80.0F : -80.0F) * zs * f);
                draw(mesh, "blank_" + a + side + n, ps, buffers, light, overlay);
                ps.popPose();
            }
            ps.popPose();
        }
        ps.popPose();
    }

    private static void outArray(PoseStack ps, String a) {
        float xm = 76.0F * PX;
        boolean p = a.equals("p");
        ps.translate(xm + (p ? -18.0F : 363.5F - 18.0F) * PX, 0.0, 0.0);
        ps.mulPose(Axis.YP.rotationDegrees(p ? 90.0F : -90.0F));
        ps.translate(-xm, 0.0, 0.0);
    }

    private static void port(MeshModel mesh, String p, int state, float open, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        draw(mesh, p, ps, buffers, light, overlay);
        ps.pushPose(); ps.translate(0.0, 50.0F * PX * open, 0.0); draw(mesh, p + "_a", ps, buffers, light, overlay); ps.popPose();
        ps.pushPose(); ps.translate(0.0, 25.0F * PX * open, 0.0); draw(mesh, p + "_b", ps, buffers, light, overlay); ps.popPose();
        int[] c = LED[Math.max(0, Math.min(2, state))];
        if (open > 0.01F && open < 0.99F)
            c = new int[] { (int) (255 + (80 - 255) * open), (int) (60 + (230 - 60) * open), (int) (50 + (120 - 50) * open) };
        if (mesh.has(p + "_led")) mesh.drawColoured(p + "_led", ps.last(), buffers, open > 0.01F && open < 0.99F ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light, overlay, c[0], c[1], c[2]);
    }

    private void sensor(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t) {
        draw(mesh, "sensor", ps, buffers, light, overlay);
        float d = be.arrive() > 0L ? 0.0F : be.deploy() <= 0L ? 1.0F : Math.max(0.0F, Math.min(1.0F, (t - be.deploy()) / Balance.SENSOR_DEPLOY_TICKS));
        float m1 = CargoPodRenderer.smooth(d / 0.25F), m2 = CargoPodRenderer.smooth((d - 0.15F) / 0.25F);
        float open = CargoPodRenderer.smooth((d - 0.3F) / 0.45F), swing = CargoPodRenderer.smooth((d - 0.6F) / 0.4F);
        int mode = be.mode();
        boolean on = mode > 0 && d >= 1.0F;
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        for (int c = 0; c < 6; c++) {
            if (!mesh.has("sa_tile" + c)) continue;
            if (!on) { mesh.drawColoured("sa_tile" + c, ps.last(), buffers, light, overlay, 40, 50, 62); continue; }
            float k = 0.25F + 0.75F * Math.max(0.0F, (float) Math.cos(t * (mode == 2 ? 0.5F : 0.22F) - c * 0.9F));
            mesh.drawColoured("sa_tile" + c, ps.last(), buffers, full, overlay, (int) (40 + 50 * k), (int) (90 + 90 * k), (int) (150 + 105 * k));
        }
        ps.pushPose();
        if (on) CargoPodRenderer.about(ps, 26.0F, 66.0F, 0.0F, Axis.YP, t * (mode == 2 ? 24.0F : 10.0F));
        if (mesh.has("sa_beacon")) {
            if (!on) mesh.drawColoured("sa_beacon", ps.last(), buffers, light, overlay, 50, 55, 62);
            else if (mode == 2) mesh.drawColoured("sa_beacon", ps.last(), buffers, full, overlay, 255, 60, 50);
            else mesh.drawColoured("sa_beacon", ps.last(), buffers, full, overlay, 80, 160, 255);
        }
        ps.popPose();
        float az, el;
        if (mode == 2) { az = -20.0F + 3.0F * (float) Math.sin(t * 0.3F); el = -15.0F + 2.0F * (float) Math.sin(t * 0.37F); }
        else if (on) { az = 40.0F * (float) Math.sin(t * Math.PI * 2.0 / 400.0); el = 25.0F + 15.0F * (float) Math.sin(t * Math.PI * 2.0 / 260.0); }
        else { az = 0.0F; el = 20.0F; }
        az *= swing;
        el = 90.0F + (el - 90.0F) * swing;
        ps.pushPose();
        ps.translate(0.0F, -28.0F * PX * (1.0F - m1), 0.0F);
        draw(mesh, "sa_mast1", ps, buffers, light, overlay);
        ps.translate(0.0F, -28.0F * PX * (1.0F - m2), 0.0F);
        draw(mesh, "sa_mast2", ps, buffers, light, overlay);
        CargoPodRenderer.about(ps, 68.0F, 0.0F, 0.0F, Axis.YP, az);
        draw(mesh, "sa_az", ps, buffers, light, overlay);
        CargoPodRenderer.about(ps, 68.0F, 146.0F, 0.0F, Axis.ZP, el);
        draw(mesh, "sa_el", ps, buffers, light, overlay);
        float fold = 72.0F * (1.0F - open), lift = 144.0F / (4.0F * (72.0F * 72.0F / 56.0F));
        for (int k = 0; k < 12; k++) {
            double ph = Math.toRadians(30.0 * k);
            float py = 146.0F + 12.0F * (float) Math.cos(ph), pz = 12.0F * (float) Math.sin(ph);
            ps.pushPose();
            if (fold > 0.01F) {
                ps.translate((80.0F + lift) * PX, py * PX, pz * PX);
                ps.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(-fold), 0.0F, (float) -Math.sin(ph), (float) Math.cos(ph)));
                ps.translate(-(80.0F + lift) * PX, -py * PX, -pz * PX);
            }
            draw(mesh, "sa_petal" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
        ps.popPose();
    }

    private static final java.util.Map<Long, float[]> LZ = new java.util.HashMap<>();

    private static float clamp01(float x) { return Math.max(0.0F, Math.min(1.0F, x)); }

    private static final java.util.Map<Long, float[]> TUR = new java.util.HashMap<>();

    private void turret(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t, float pt) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean mine = ClientTurret.gunnerOf(be.getBlockPos());
        float yaw = be.aimRoll(), el = be.aimPitch();
        if (mine) { float[] a = Turret.aim(mc.player.getViewVector(pt)); yaw = a[0]; el = a[1]; }
        float[] cs = TUR.computeIfAbsent(be.getBlockPos().asLong(), k -> new float[] { 0.0F, -60.0F, t });
        float dt = Math.max(0.0F, Math.min(5.0F, t - cs[2]));
        cs[2] = t;
        float k = mine ? 1.0F : 1.0F - (float) Math.pow(0.8, dt);
        cs[0] += (((yaw - cs[0]) % 360.0F + 540.0F) % 360.0F - 180.0F) * k;
        cs[1] += (el - cs[1]) * k;
        draw(mesh, "tur_mount", ps, buffers, light, overlay);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(cs[0]));
        draw(mesh, "tur_yaw", ps, buffers, light, overlay);
        CargoPodRenderer.about(ps, 0.0F, -64.0F, 0.0F, Axis.ZP, cs[1]);
        boolean inside = mine && mc.options.getCameraType().isFirstPerson();
        if (!inside) { draw(mesh, "tur_ball", ps, buffers, light, overlay); draw(mesh, "tur_window", ps, buffers, light, overlay); }
        draw(mesh, "tur_guns", ps, buffers, light, overlay);
        if (mesh.has("tur_coils")) mesh.drawColoured("tur_coils", ps.last(), buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 120, 200, 255);
        ps.popPose();
    }

    private void laser(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t) {
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int st = be.lzState();
        float el = t - be.lzT0();
        float u = switch (st) {
            case StationLaser.CHARGING -> clamp01(el / Balance.LASER_CHARGE_TICKS);
            case StationLaser.LOCK -> clamp01(el / Balance.LASER_LOCK_TICKS);
            case StationLaser.FIRE -> clamp01(el / Balance.LASER_FIRE_TICKS);
            case StationLaser.COOL -> clamp01(el / Balance.LASER_COOL_TICKS);
            default -> 0.0F;
        };
        boolean hot = st == StationLaser.ARMED || st == StationLaser.LOCK || st == StationLaser.FIRE;
        float d = switch (st) { case StationLaser.CHARGING -> CargoPodRenderer.smooth((u - 0.3F) / 0.4F); case StationLaser.COOL -> 1.0F - CargoPodRenderer.smooth((u - 0.85F) / 0.15F);
            case StationLaser.IDLE -> 0.0F; default -> 1.0F; };
        float rad = st == StationLaser.CHARGING ? CargoPodRenderer.smooth(u / 0.3F) : d;
        float fin = switch (st) { case StationLaser.CHARGING -> 35.0F * CargoPodRenderer.smooth((u - 0.8F) / 0.2F); case StationLaser.FIRE -> 35.0F + 35.0F * CargoPodRenderer.smooth(el / 6.0F);
            case StationLaser.COOL -> 70.0F * (1.0F - CargoPodRenderer.smooth((u - 0.8F) / 0.2F)); case StationLaser.ARMED, StationLaser.LOCK -> 35.0F; default -> 0.0F; };
        float heat = st == StationLaser.FIRE ? CargoPodRenderer.smooth((el - Balance.LASER_HIT_AT) / 6.0F) : st == StationLaser.COOL ? 1.0F - u : 0.0F;
        float ringSpeed = switch (st) { case StationLaser.CHARGING -> 2.0F + 20.0F * u; case StationLaser.ARMED -> 22.0F; case StationLaser.LOCK, StationLaser.FIRE -> 32.0F;
            case StationLaser.COOL -> 22.0F * (1.0F - u); default -> 0.0F; };
        float ringGlow = switch (st) { case StationLaser.CHARGING -> 0.25F + 0.75F * u; case StationLaser.ARMED, StationLaser.LOCK -> 0.85F + 0.15F * (float) Math.sin(t * 0.4F);
            case StationLaser.FIRE -> 1.0F; case StationLaser.COOL -> 0.6F * (1.0F - u); default -> 0.0F; };
        int lit = switch (st) { case StationLaser.CHARGING -> (int) (u * 12.0F); case StationLaser.ARMED, StationLaser.LOCK -> 12;
            case StationLaser.FIRE -> Math.max(0, 12 - (int) Math.max(0.0F, (el - Balance.LASER_HIT_AT) * 12.0F / 20.0F)); default -> 0; };
        float pump = switch (st) { case StationLaser.CHARGING -> 0.5F; case StationLaser.ARMED, StationLaser.LOCK -> 0.25F; case StationLaser.FIRE -> 0.8F; case StationLaser.COOL -> 0.15F * (1.0F - u); default -> 0.0F; };

        long key = be.getBlockPos().asLong();
        float[] cs = LZ.computeIfAbsent(key, k -> new float[] { 0.0F, 0.0F, 0.0F, t });
        float dt = Math.max(0.0F, Math.min(5.0F, t - cs[3]));
        cs[3] = t;
        cs[0] = (cs[0] + ringSpeed * dt) % 360.0F;
        float k = 1.0F - (float) Math.pow(0.92, dt);
        float dr = ((be.aimRoll() - cs[1]) % 360.0F + 540.0F) % 360.0F - 180.0F;
        cs[1] += dr * k;
        cs[2] += (be.aimPitch() - cs[2]) * k;

        draw(mesh, "laser", ps, buffers, light, overlay);
        for (int c = 0; c < 4; c++) draw(mesh, "lz_clamp" + c, ps, buffers, light, overlay);
        for (int c = 0; c < 12; c++) {
            boolean on = c < lit;
            if (mesh.has("lz_capled" + c)) {
                if (on) mesh.drawColoured("lz_capled" + c, ps.last(), buffers, full, overlay, 110, 190, 255);
                else mesh.drawColoured("lz_capled" + c, ps.last(), buffers, light, overlay, 45, 55, 66);
            }
            ps.pushPose();
            if (on) ps.translate(0.0F, 2.0F * PX, 0.0F);
            draw(mesh, "lz_lid" + c, ps, buffers, light, overlay);
            ps.popPose();
        }
        for (int c = 0; c < 6; c++) {
            if (!mesh.has("lz_cond" + c)) continue;
            boolean on = st != StationLaser.IDLE && st != StationLaser.COOL && Math.floorMod((int) (t * 0.4F) - c, 6) < 2;
            if (on) mesh.drawColoured("lz_cond" + c, ps.last(), buffers, full, overlay, 120, 200, 255);
            else mesh.drawColoured("lz_cond" + c, ps.last(), buffers, light, overlay, 50, 60, 70);
        }
        for (int c = 0; c < 2; c++) {
            ps.pushPose();
            ps.translate(-5.0F * PX * (0.5F + 0.5F * (float) Math.sin(t * pump + c * 1.6F)) * (pump > 0 ? 1.0F : 0.0F), 0.0F, 0.0F);
            draw(mesh, "lz_pump" + c, ps, buffers, light, overlay);
            ps.popPose();
        }
        for (int c = 0; c < 4; c++) {
            double a = Math.toRadians(new double[] { 45, -45, 135, -135 }[c]);
            float hy = (float) (40.0 + 56.0 * Math.cos(a) - 18.0 * Math.sin(a)), hz = (float) (56.0 * Math.sin(a) + 18.0 * Math.cos(a));
            ps.pushPose();
            CargoPodRenderer.about(ps, 0.0F, hy, hz, Axis.XP, 60.0F * rad);
            hotDraw(mesh, "lz_rad" + c, ps, buffers, light, overlay, heat);
            ps.popPose();
        }
        float vent = st == StationLaser.FIRE ? CargoPodRenderer.smooth((el - Balance.LASER_HIT_AT) / 4.0F) : st == StationLaser.COOL ? 1.0F - CargoPodRenderer.smooth(u / 0.1F) : 0.0F;
        for (int c = 0; c < 2; c++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, 145.0F, 52.0F, c == 0 ? 57.0F : -57.0F, Axis.XP, (c == 0 ? -70.0F : 70.0F) * vent);
            draw(mesh, "lz_vent" + c, ps, buffers, light, overlay);
            ps.popPose();
        }
        if (vent > 0.5F && st == StationLaser.FIRE && be.getLevel() != null && be.getLevel().random.nextFloat() < 0.6F) {
            var lv = be.getLevel();
            var p = be.getBlockPos();
            lv.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, p.getX() + 0.5 + (lv.random.nextFloat() - 0.5) * 6.0, p.getY() + 1.0, p.getZ() + 0.5 + (lv.random.nextFloat() - 0.5) * 6.0, 0.0, 0.1, 0.0);
        }

        ps.pushPose();
        CargoPodRenderer.about(ps, 0.0F, 40.0F, 0.0F, Axis.XP, cs[1]);
        draw(mesh, "lz_roll", ps, buffers, light, overlay);
        for (String side : new String[] { "p", "s" }) {
            float zs = side.equals("p") ? 1.0F : -1.0F;
            for (int c = 0; c < 5; c++) {
                ps.pushPose();
                CargoPodRenderer.about(ps, 206.6F + 13.0F * c, 40.0F, zs * 41.0F, Axis.YP, -fin * zs);
                hotDraw(mesh, "lz_fin_" + side + c, ps, buffers, light, overlay, heat);
                ps.popPose();
            }
            ps.pushPose();
            float yaw = st == StationLaser.LOCK || st == StationLaser.FIRE ? 0.0F : 30.0F * (float) Math.sin(t * 0.03F + zs);
            float tp = st == StationLaser.LOCK || st == StationLaser.FIRE ? Math.min(60.0F, cs[2]) : 10.0F * (float) Math.sin(t * 0.05F);
            CargoPodRenderer.about(ps, 238.0F, 74.0F, zs * 32.0F, Axis.YP, yaw);
            draw(mesh, "lz_trk_" + side, ps, buffers, light, overlay);
            CargoPodRenderer.about(ps, 238.0F, 84.0F, zs * 32.0F, Axis.ZP, tp);
            draw(mesh, "lz_trkp_" + side, ps, buffers, light, overlay);
            ps.popPose();
        }
        for (int c = 0; c < 2; c++) {
            ps.pushPose();
            if (hot) CargoPodRenderer.about(ps, 266.0F, 76.0F, c == 0 ? 32.0F : -32.0F, Axis.YP, t * 18.0F);
            if (mesh.has("lz_strobe" + c)) {
                if (hot) mesh.drawColoured("lz_strobe" + c, ps.last(), buffers, full, overlay, 255, 50, 40);
                else mesh.drawColoured("lz_strobe" + c, ps.last(), buffers, light, overlay, 70, 70, 75);
            }
            ps.popPose();
        }

        CargoPodRenderer.about(ps, 248.0F, 40.0F, 0.0F, Axis.ZP, cs[2]);
        draw(mesh, "lz_pitch", ps, buffers, light, overlay);
        int gr = (int) (60 + 195 * ringGlow), gg = (int) (70 + 185 * ringGlow), gb = (int) (90 + 165 * Math.min(1.0F, ringGlow * 1.2F));
        for (int c = 0; c < 8; c++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, 0.0F, 40.0F, 0.0F, Axis.XP, (c % 2 == 0 ? 1.0F : -1.0F) * cs[0]);
            draw(mesh, "lz_ring" + c, ps, buffers, light, overlay);
            if (mesh.has("lz_ringled" + c)) mesh.drawColoured("lz_ringled" + c, ps.last(), buffers, ringGlow > 0.05F ? full : light, overlay, gr, gg, gb);
            ps.popPose();
        }
        float t2 = 32.0F * CargoPodRenderer.smooth(d / 0.5F), t3 = 32.0F * CargoPodRenderer.smooth((d - 0.3F) / 0.5F);
        float cap = CargoPodRenderer.smooth((d - 0.6F) / 0.4F);
        float iris = st == StationLaser.FIRE ? CargoPodRenderer.smooth(el / 5.0F) : st == StationLaser.COOL ? 1.0F - CargoPodRenderer.smooth(u * Balance.LASER_COOL_TICKS / 40.0F) : 0.0F;
        float lens = st == StationLaser.FIRE ? CargoPodRenderer.smooth((el - 5.0F) / 10.0F) : 0.0F;
        ps.translate(t2 * PX, 0.0F, 0.0F);
        draw(mesh, "lz_tube2", ps, buffers, light, overlay);
        ps.translate(t3 * PX, 0.0F, 0.0F);
        draw(mesh, "lz_tube3", ps, buffers, light, overlay);
        for (String side : new String[] { "p", "s" }) {
            float zs = side.equals("p") ? 1.0F : -1.0F;
            ps.pushPose();
            CargoPodRenderer.about(ps, 526.0F, 40.0F, zs * 32.0F, Axis.YP, 110.0F * zs * cap);
            draw(mesh, "lz_cap_" + side, ps, buffers, light, overlay);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(4.0F * PX * lens, 0.0F, 0.0F);
        if (mesh.has("lz_lens")) {
            float lg = st == StationLaser.FIRE ? 1.0F : st == StationLaser.CHARGING ? u : st == StationLaser.ARMED || st == StationLaser.LOCK ? 0.8F + 0.2F * (float) Math.sin(t * 0.5F) : 0.1F;
            mesh.drawColoured("lz_lens", ps.last(), buffers, lg > 0.2F ? full : light, overlay, (int) (40 + 215 * lg), (int) (60 + 195 * lg), (int) (90 + 165 * lg));
        }
        ps.popPose();
        for (int c = 0; c < 10; c++) {
            double a = Math.toRadians(36.0 * c);
            ps.pushPose();
            ps.translate(0.0F, 15.0F * PX * iris * (float) Math.cos(a), 15.0F * PX * iris * (float) Math.sin(a));
            draw(mesh, "lz_iris" + c, ps, buffers, light, overlay);
            ps.popPose();
        }
        ps.popPose();
    }

    private static final float[] MAG_Z = { -36.0F, 0.0F, 36.0F };
    private static final int[] MAG_FILL = { 0, 2, 1 };

    private static float magGrab(int st, float el, float drum) {
        if (st != RodOfDeath.LOAD) return drum;
        float u = clamp01(el / RodOfDeath.len(RodOfDeath.LOAD));
        float lift = CargoPodRenderer.smooth((u - 0.08F) / 0.07F);
        return u < 0.88F ? lift - 152.0F * CargoPodRenderer.smooth((u - 0.55F) / 0.3F) : -151.0F + 151.0F * CargoPodRenderer.smooth((u - 0.88F) / 0.12F);
    }

    private static final java.util.Map<Long, float[]> RD = new java.util.HashMap<>();

    private static float[] rodPose(int st, float el, long cool) {
        float[] p = new float[18];
        if (st == RodOfDeath.IDLE || st == RodOfDeath.COOL && cool <= Balance.ROD_ABORT_COOL_TICKS) return p;
        float u = clamp01(el / Math.max(1, st == RodOfDeath.COOL ? cool : RodOfDeath.len(st)));
        switch (st) {
            case RodOfDeath.ARM -> { p[0] = -120.0F * (1.0F - CargoPodRenderer.smooth(u / 0.8F)); p[13] = clamp01(el / 40.0F); }
            case RodOfDeath.LOAD -> {
                p[1] = CargoPodRenderer.smooth(u / 0.08F) * (1.0F - CargoPodRenderer.smooth((u - 0.86F) / 0.05F));
                float lift = 8.0F * CargoPodRenderer.smooth((u - 0.08F) / 0.07F);
                p[2] = u < 0.88F ? lift - 146.0F * CargoPodRenderer.smooth((u - 0.55F) / 0.3F) : -138.0F + 138.0F * CargoPodRenderer.smooth((u - 0.88F) / 0.12F);
                p[3] = 154.0F * CargoPodRenderer.smooth((u - 0.25F) / 0.28F);
                p[4] = CargoPodRenderer.smooth((u - 0.15F) / 0.1F) * (1.0F - CargoPodRenderer.smooth((u - 0.9F) / 0.08F));
                p[5] = CargoPodRenderer.smooth((u - 0.18F) / 0.1F) * (1.0F - CargoPodRenderer.smooth((u - 0.9F) / 0.08F));
                p[13] = 1.0F; p[16] = u < 0.87F ? 1 : 0; p[17] = u < 0.87F ? 0 : 1;
            }
            case RodOfDeath.SWING -> {
                p[3] = 154.0F * (1.0F - CargoPodRenderer.smooth(u / 0.25F));
                p[6] = CargoPodRenderer.smooth(u / 0.15F);
                p[7] = 90.0F * CargoPodRenderer.smooth((u - 0.15F) / 0.6F) + (u > 0.75F ? 2.0F * (float) Math.sin(el * 0.2F) * (1.0F - u) * 4.0F : 0.0F);
                p[8] = CargoPodRenderer.smooth((u - 0.6F) / 0.2F);
                p[13] = 1.0F; p[17] = 1;
            }
            case RodOfDeath.SPIN -> { p[6] = 1; p[7] = 90; p[8] = 1; p[9] = 2.0F + 28.0F * u; p[10] = u; p[13] = 1; p[17] = 1; }
            case RodOfDeath.FINAL -> {
                boolean silent = el > Balance.ROD_FINAL_TICKS - Balance.ROD_SILENCE_TICKS;
                p[6] = 1; p[7] = 90; p[8] = 1; p[9] = 30; p[10] = silent ? 1.3F : 1.0F; p[11] = CargoPodRenderer.smooth(u / 0.1F); p[13] = 1; p[17] = 1;
            }
            case RodOfDeath.FALL -> {
                p[6] = 1; p[7] = 90.0F + 3.0F * (float) Math.sin(el * 1.3F) * Math.max(0.0F, 1.0F - el / 30.0F); p[8] = 1; p[9] = 30; p[10] = 1.0F - u; p[11] = 1;

                p[12] = -230.0F * CargoPodRenderer.smooth(el / 40.0F) - (el > 40 ? 0.12F * (el - 40) * (el - 40) : 0.0F); p[13] = 1; p[14] = CargoPodRenderer.smooth(el / 20.0F); p[15] = el < 80 ? 1.0F : Math.max(0.0F, 1.0F - (el - 80) / 60.0F);
                p[17] = el < Balance.ROD_AWAY_TICKS ? 1 : 0;
            }
            case RodOfDeath.COOL -> {
                p[11] = 1.0F - CargoPodRenderer.smooth(el / 100.0F); p[9] = 30.0F * (1.0F - CargoPodRenderer.smooth(el / 600.0F));
                p[14] = 1.0F - CargoPodRenderer.smooth(el / 3600.0F);
                p[7] = 90.0F * (1.0F - CargoPodRenderer.smooth((el - 600.0F) / 1200.0F));
                p[6] = 1.0F - CargoPodRenderer.smooth((el - 1800.0F) / 300.0F); p[8] = p[6];
                p[13] = 1.0F - CargoPodRenderer.smooth(el / 100.0F);
            }
            default -> {}
        }
        return p;
    }

    private void rod(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t) {
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int st = be.rdState();
        float el = Math.max(0.0F, t - be.rdT0());
        float[] p;
        if (st == RodOfDeath.BACK) {
            float[] a = rodPose(be.rdFrom(), be.rdFromEl(), be.rdCool()), r = new float[18];
            float k = CargoPodRenderer.smooth(el / Balance.ROD_BACK_TICKS);
            p = new float[18];
            for (int i = 0; i < 16; i++) p[i] = a[i] + (r[i] - a[i]) * k;
            p[17] = a[17] > 0 && k < 0.5F ? 1 : 0;
        } else p = rodPose(st, el, be.rdCool());
        boolean live = st >= RodOfDeath.ARM && st <= RodOfDeath.FALL;
        long key = be.getBlockPos().asLong();
        float[] cs = RD.computeIfAbsent(key, k -> new float[] { 0.0F, t });
        float dt = Math.max(0.0F, Math.min(5.0F, t - cs[1]));
        cs[1] = t;
        cs[0] = (cs[0] + p[9] * dt) % 360.0F;

        draw(mesh, "rod", ps, buffers, light, overlay);
        float led = p[13], pulse = st == RodOfDeath.FINAL ? 0.7F + 0.3F * (float) Math.sin(t * 0.5F) : 1.0F;
        if (mesh.has("rd_led")) mesh.drawColoured("rd_led", ps.last(), buffers, full, overlay, (int) ((74 + 181 * led) * pulse), (int) ((163 - 103 * led) * pulse), (int) ((255 - 205 * led) * pulse));
        for (String leaf : new String[] { "rd_hatch_a", "rd_hatch_b" }) {
            ps.pushPose();
            ps.translate(0.0F, 0.0F, (leaf.endsWith("a") ? 26.0F : -26.0F) * PX * p[4]);
            draw(mesh, leaf, ps, buffers, light, overlay);
            ps.popPose();
        }
        float[][] pods = { { 60.0F, 64.3F, 15.0F }, { 60.0F, -64.3F, -15.0F }, { 276.0F, 64.3F, 15.0F }, { 276.0F, -64.3F, -15.0F } };
        for (int i = 0; i < 4; i++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, pods[i][0], 7.7F, pods[i][1], Axis.XP, pods[i][2] * p[8]);
            hotDraw(mesh, "rd_pod" + i, ps, buffers, light, overlay, p[15]);
            ps.popPose();
        }
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, i == 0 ? 56.0F : 144.0F, -11.0F, 40.0F, Axis.XP, -80.0F * p[6]);
            draw(mesh, "rd_arm" + i, ps, buffers, light, overlay);
            ps.popPose();
        }

        ps.pushPose();
        CargoPodRenderer.about(ps, 320.0F, -52.0F, 0.0F, Axis.ZP, p[7]);
        draw(mesh, "rd_tube", ps, buffers, light, overlay);
        ps.pushPose();
        CargoPodRenderer.about(ps, 0.0F, -23.5F, 22.0F, Axis.XP, 110.0F * p[5]);
        draw(mesh, "rd_tdoor", ps, buffers, light, overlay);
        ps.popPose();
        for (int k = 0; k < 6; k++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, 0.0F, -52.0F, 0.0F, Axis.XP, (k % 2 == 0 ? 1.0F : -1.0F) * cs[0]);
            draw(mesh, "rd_col" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
        if (mesh.has("rd_coil")) {
            float c = Math.min(1.0F, p[10]), w = Math.max(0.0F, p[10] - 1.0F) / 0.3F;
            if (c <= 0.02F) mesh.drawColoured("rd_coil", ps.last(), buffers, light, overlay, 40, 55, 70);
            else mesh.drawColoured("rd_coil", ps.last(), buffers, full, overlay, (int) (40 + 70 * c + 145 * w), (int) (55 + 135 * c + 65 * w), (int) (70 + 185 * c));
        }
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45.0 + 90.0 * k);
            ps.pushPose();
            ps.translate(0.0F, 30.0F * PX * p[11] * (float) Math.cos(a), 30.0F * PX * p[11] * (float) Math.sin(a));
            draw(mesh, "rd_petal" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
        hotDraw(mesh, "rd_shroud", ps, buffers, light, overlay, p[14]);
        if (p[17] > 0) {
            ps.pushPose();
            ps.translate(p[12] * PX, 0.0F, 0.0F);
            CargoPodRenderer.about(ps, 0.0F, -52.0F, 0.0F, Axis.XP, cs[0] * 0.5F);
            draw(mesh, "rd_rodt", ps, buffers, light, overlay);
            ps.popPose();
        }
        ps.popPose();

        int racked = Math.min(3, be.rods());
        boolean mag = mesh.has("rd_cross");
        float cross = 0.0F;
        ps.pushPose();
        if (mag) {
            draw(mesh, "rd_rack", ps, buffers, light, overlay);
            boolean taking = live || st == RodOfDeath.BACK && be.rdFrom() >= RodOfDeath.LOAD;
            int shown = st == RodOfDeath.ARM ? Math.min(3, racked + 1) : racked;
            for (int k = 0; k < shown; k++) draw(mesh, "rd_rod" + MAG_FILL[k], ps, buffers, light, overlay);
            for (int k = 0; k < 3; k++) {
                boolean on = false;
                for (int j = 0; j < shown; j++) on |= MAG_FILL[j] == k;
                if (mesh.has("rd_mag" + k)) mesh.drawColoured("rd_mag" + k, ps.last(), buffers, on ? full : light, overlay, on ? 74 : 40, on ? 163 : 50, on ? 255 : 62);
            }
            float slotZ = MAG_Z[MAG_FILL[Math.min(2, racked)]];
            float uArm = clamp01(el / RodOfDeath.len(RodOfDeath.ARM)), uLoad = clamp01(el / RodOfDeath.len(RodOfDeath.LOAD));
            if (st == RodOfDeath.ARM) cross = slotZ * CargoPodRenderer.smooth(uArm / 0.6F);
            else if (st == RodOfDeath.LOAD) cross = slotZ * (1.0F - CargoPodRenderer.smooth((uLoad - 0.1F) / 0.12F));
            else if (st == RodOfDeath.BACK && be.rdFrom() == RodOfDeath.ARM) cross = 0.0F;
            if (!taking && st != RodOfDeath.ARM) cross = 0.0F;
        } else {
            CargoPodRenderer.about(ps, 0.0F, 64.0F, 0.0F, Axis.XP, p[0]);
            draw(mesh, "rd_rack", ps, buffers, light, overlay);
            if (st == RodOfDeath.ARM) for (int k = 0; k <= racked && k < 3; k++) draw(mesh, "rd_rod" + k, ps, buffers, light, overlay);
            else if (live || st == RodOfDeath.BACK && be.rdFrom() >= RodOfDeath.LOAD) for (int k = 1; k <= racked && k < 3; k++) draw(mesh, "rd_rod" + k, ps, buffers, light, overlay);
            else for (int k = 0; k < racked; k++) draw(mesh, "rd_rod" + k, ps, buffers, light, overlay);
        }
        ps.popPose();
        ps.pushPose();
        ps.translate(p[3] * PX, 0.0F, 0.0F);
        draw(mesh, "rd_trolley", ps, buffers, light, overlay);
        if (mag) {
            ps.translate(0.0F, 0.0F, cross * PX);
            draw(mesh, "rd_cross", ps, buffers, light, overlay);
        }

        ps.translate(0.0F, (mag ? magGrab(st, el, p[2]) : p[2]) * PX, 0.0F);
        draw(mesh, "rd_grab", ps, buffers, light, overlay);
        for (int k = 0; k < 4; k++) {
            ps.pushPose();
            ps.translate(0.0F, 0.0F, (k % 2 == 0 ? -3.0F : 3.0F) * PX * p[1]);
            draw(mesh, "rd_claw" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
        if (p[16] > 0) draw(mesh, "rd_rodh", ps, buffers, light, overlay);
        ps.popPose();
        float[][] beacons = { { 114.0F, 116.0F }, { 234.0F, 116.0F } };
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            if (live) CargoPodRenderer.about(ps, beacons[k][0], beacons[k][1], 0.0F, Axis.YP, t * 14.0F);
            if (mesh.has("rd_beacon" + k)) {
                if (live) mesh.drawColoured("rd_beacon" + k, ps.last(), buffers, full, overlay, 255, 50, 40);
                else mesh.drawColoured("rd_beacon" + k, ps.last(), buffers, light, overlay, 70, 70, 75);
            }
            ps.popPose();
        }
        ps.pushPose();
        CargoPodRenderer.about(ps, 168.0F, 0.0F, -46.0F, Axis.YP, t * (live ? 4.0F : 1.5F));
        if (mesh.has("rd_holo")) mesh.drawColoured("rd_holo", ps.last(), buffers, full, overlay, live ? 255 : 90, live ? 90 : 170, live ? 80 : 255);
        ps.popPose();
        if (live) alert(be);
        MeshModel dk = desk(mesh, "rd_holo", "station_rod_console");
        if (dk != null) {
            ps.pushPose();
            deskAt(ps, 176.0F, 32.0F, -48.0F, 180.0F);
            dk.draw("console", ps.last(), buffers, light, overlay);
            if (dk.has("glow")) dk.draw("glow", ps.last(), buffers, full, overlay);
            if (dk.has("screen")) dk.drawColoured("screen", ps.last(), buffers, full, overlay, live ? 255 : 90, live ? 90 : 170, live ? 80 : 255);
            boolean blink = live && (int) (t / 10.0F) % 2 == 0;
            if (dk.has("arm")) dk.drawColoured("arm", ps.last(), buffers, live ? full : light, overlay, live ? 255 : 90, blink ? 60 : live ? 30 : 70, live ? 40 : 75);
            ps.popPose();
        }
    }

    private static final float MR_HUBF = 346.0F, MR_R0 = 44.0F, MR_RM = 146.0F, MR_R1 = 248.0F, MR_SAG = 40.0F, MR_AX = 72.0F;

    private static float mrDishX(float r) { float u = (r - MR_R0) / (MR_R1 - MR_R0); return MR_HUBF + MR_SAG * u * u; }

    private static final java.util.Map<Long, float[]> MR = new java.util.HashMap<>();

    private static float[] mirrorPose(int st, float el, long cool) {
        float[] p = { 0, 0, 0, 80, 176, -60, 0, 0, 0, 0, 0 };
        if (st == MirrorFocus.DEPLOYING || st == MirrorFocus.FOLDING) {
            float u = clamp01(el / (st == MirrorFocus.DEPLOYING ? Balance.MIRROR_DEPLOY_TICKS : Balance.MIRROR_FOLD_TICKS));
            if (st == MirrorFocus.FOLDING) u = 1.0F - u;
            float a = CargoPodRenderer.smooth(u / 0.25F);
            p[0] = 22.0F * a; p[1] = 160.0F * a; p[2] = -112.0F * CargoPodRenderer.smooth((u - 0.2F) / 0.25F);
            p[3] = 80.0F * (1.0F - CargoPodRenderer.smooth((u - 0.45F) / 0.3F)); p[4] = 176.0F * (1.0F - CargoPodRenderer.smooth((u - 0.65F) / 0.3F));
            p[5] = -60.0F + 124.0F * CargoPodRenderer.smooth((u - 0.8F) / 0.2F); p[6] = CargoPodRenderer.smooth((u - 0.3F) / 0.3F);
            return p;
        }
        if (st == MirrorFocus.FOLDED || st < 0) return p;
        p[0] = 22; p[1] = 160; p[2] = -112; p[3] = 0; p[4] = 0; p[5] = 64; p[6] = 1;
        if (st == MirrorFocus.BURNING) { p[7] = CargoPodRenderer.smooth(el / 40.0F); p[8] = 1; p[9] = 1; p[10] = CargoPodRenderer.smooth(el / 20.0F); }
        if (st == MirrorFocus.COOLING) { float u = clamp01(el / Math.max(1, cool)); p[7] = 1.0F - CargoPodRenderer.smooth(el / 40.0F); p[10] = -(1.0F - u); }
        return p;
    }

    private void mirror(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t) {
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int st = be.rdState();
        float el = Math.max(0.0F, t - be.rdT0());
        float[] p = mirrorPose(st, el, be.rdCool());
        boolean burn = st == MirrorFocus.BURNING, up = st == MirrorFocus.READY || burn || st == MirrorFocus.COOLING;
        long key = be.getBlockPos().asLong();
        float[] cs = MR.computeIfAbsent(key, k -> new float[] { 0.0F, 0.0F, t });
        float dt = Math.max(0.0F, Math.min(5.0F, t - cs[2]));
        cs[2] = t;
        float k = 1.0F - (float) Math.pow(0.95, dt);
        cs[0] += ((up ? be.aimRoll() : 0.0F) - cs[0]) * k;
        cs[1] += ((up ? be.aimPitch() : 0.0F) - cs[1]) * k;
        float gold = p[9];
        int lr = (int) (74 + 181 * gold), lg = (int) (163 + 47 * gold), lb = (int) (255 - 175 * gold);

        draw(mesh, "mirror", ps, buffers, light, overlay);
        draw(mesh, "mr_room", ps, buffers, light, overlay);
        if (mesh.has("mr_led")) mesh.drawColoured("mr_led", ps.last(), buffers, full, overlay, lr, lg, lb);
        if (mesh.has("mr_screen")) mesh.drawColoured("mr_screen", ps.last(), buffers, full, overlay, burn ? 255 : 70, burn ? 200 : 130, burn ? 120 : 200);
        if (mesh.has("mr_lamps")) {
            boolean lit = st == MirrorFocus.READY || burn;
            mesh.drawColoured("mr_lamps", ps.last(), buffers, lit ? full : light, overlay, lit ? 255 : 120, lit ? 180 : 100, lit ? 60 : 60);
        }
        ps.pushPose();
        CargoPodRenderer.about(ps, 110.0F, 42.0F, -10.0F, Axis.XP, burn ? 8.0F * (float) Math.sin(t * 0.07F) : 0.0F);
        draw(mesh, "mr_stick", ps, buffers, light, overlay);
        ps.popPose();
        ps.pushPose();
        CargoPodRenderer.about(ps, 110.0F, 40.0F, 12.0F, Axis.ZP, -40.0F * p[8]);
        draw(mesh, "mr_lever", ps, buffers, light, overlay);
        ps.popPose();
        for (String side : new String[] { "p", "s" }) {
            float zs = side.equals("p") ? 1.0F : -1.0F;
            ps.pushPose();
            CargoPodRenderer.about(ps, 88.0F, 112.0F, zs * 84.0F, Axis.XP, -zs * 100.0F * p[7]);
            draw(mesh, "mr_shut_" + side, ps, buffers, light, overlay);
            ps.popPose();
        }
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            if (burn) CargoPodRenderer.about(ps, i == 0 ? 44.0F : 132.0F, 118.0F, 0.0F, Axis.YP, t * 14.0F);
            if (mesh.has("mr_beacon" + i)) {
                if (burn) mesh.drawColoured("mr_beacon" + i, ps.last(), buffers, full, overlay, 255, 160, 40);
                else mesh.drawColoured("mr_beacon" + i, ps.last(), buffers, light, overlay, 70, 70, 75);
            }
            ps.popPose();
        }
        ps.pushPose();
        CargoPodRenderer.about(ps, 100.0F, 0.0F, 0.0F, Axis.YP, t * (burn ? 4.0F : 1.5F));
        if (mesh.has("mr_holo")) mesh.drawColoured("mr_holo", ps.last(), buffers, full, overlay, burn ? 255 : 90, burn ? 200 : 170, burn ? 90 : 255);
        ps.popPose();
        if (burn) alert(be);
        MeshModel dk = desk(mesh, "mr_stick", "station_mirror_desk");
        if (dk != null) {
            ps.pushPose();
            deskAt(ps, 104.0F, 16.0F, 0.0F, 90.0F);
            dk.draw("console", ps.last(), buffers, light, overlay);
            if (dk.has("glow")) dk.draw("glow", ps.last(), buffers, full, overlay);
            if (dk.has("screen")) dk.drawColoured("screen", ps.last(), buffers, full, overlay, burn ? 255 : 70, burn ? 200 : 150, burn ? 120 : 220);
            boolean lit = st == MirrorFocus.READY || burn;
            if (dk.has("lamps")) dk.drawColoured("lamps", ps.last(), buffers, lit ? full : light, overlay, lit ? 255 : 120, lit ? 180 : 100, 60);
            ps.pushPose();
            CargoPodRenderer.about(ps, -13.0F, 15.2F, -1.0F, Axis.XP, burn ? 8.0F * (float) Math.sin(t * 0.07F) : 0.0F);
            draw(dk, "stick", ps, buffers, light, overlay);
            ps.popPose();
            ps.pushPose();
            CargoPodRenderer.about(ps, 13.0F, 15.2F, -1.0F, Axis.ZP, -40.0F * p[8]);
            draw(dk, "lever", ps, buffers, light, overlay);
            ps.popPose();
            ps.pushPose();
            CargoPodRenderer.about(ps, 0.0F, 0.0F, -3.0F, Axis.YP, t * (burn ? 4.0F : 1.5F));
            if (dk.has("holo")) dk.drawColoured("holo", ps.last(), buffers, full, overlay, burn ? 255 : 90, burn ? 200 : 170, burn ? 90 : 255);
            ps.popPose();
            ps.popPose();
        }

        ps.pushPose();
        CargoPodRenderer.about(ps, 182.0F, 72.0F, 0.0F, Axis.ZP, p[0]);
        draw(mesh, "mr_arm", ps, buffers, light, overlay);
        ps.pushPose();
        CargoPodRenderer.about(ps, 236.0F, 100.0F, 0.0F, Axis.YP, 40.0F * p[6] + (up ? 6.0F * (float) Math.sin(t * 0.01F) : 0.0F));
        draw(mesh, "mr_sun", ps, buffers, light, overlay);
        ps.popPose();
        float[][] rads = { { 96.0F, 25.0F, 80.0F }, { 48.0F, 25.0F, -80.0F }, { 96.0F, -25.0F, -80.0F }, { 48.0F, -25.0F, 80.0F } };
        for (int i = 0; i < 4; i++) {
            ps.pushPose();
            CargoPodRenderer.about(ps, 246.0F, rads[i][0], rads[i][1], Axis.XP, rads[i][2] * p[6]);
            hotDraw(mesh, "mr_rad" + i, ps, buffers, light, overlay, burn ? 0.25F * p[10] : 0.0F);
            ps.popPose();
        }
        ps.translate(p[1] * PX, 0.0F, 0.0F);
        draw(mesh, "mr_arm2", ps, buffers, light, overlay);
        CargoPodRenderer.about(ps, 326.0F, 72.0F, 0.0F, Axis.ZP, p[2]);
        float aimK = clamp01(p[2] / -112.0F);
        CargoPodRenderer.about(ps, 326.0F, 72.0F, 0.0F, Axis.ZP, cs[0] * aimK);
        CargoPodRenderer.about(ps, 326.0F, 72.0F, 0.0F, Axis.YP, -cs[1] * aimK);
        draw(mesh, "mr_bud", ps, buffers, light, overlay);
        if (mesh.has("mr_hubled")) mesh.drawColoured("mr_hubled", ps.last(), buffers, full, overlay, lr, lg, lb);
        mirrorPetals(mesh, ps, buffers, light, overlay, p[3], p[4]);
        ps.translate(p[5] * PX, 0.0F, 0.0F);
        draw(mesh, "mr_mast", ps, buffers, light, overlay);
        if (burn && p[10] > 0.02F) {
            var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
            org.joml.Matrix4f mm = ps.last().pose();
            float x0 = (MR_HUBF + 150.0F) * PX, x1 = x0 + 60.0F, yc = MR_AX * PX, heat = p[10];
            float flick = 0.85F + 0.15F * (float) Math.sin(t * 0.9F);
            for (float[] w : new float[][] { { 10.0F * PX, 0.35F }, { 3.5F * PX, 0.9F } }) {
                int a = (int) (255 * w[1] * heat * flick), g2 = w[1] > 0.5F ? 245 : 200, b2 = w[1] > 0.5F ? 210 : 110;
                CcRenderers.quad(vc, mm, x0, yc - w[0], 0.0F, x1, yc - w[0], 0.0F, x1, yc + w[0], 0.0F, x0, yc + w[0], 0.0F, (a << 24) | 0xFF0000 | g2 << 8 | b2);
                CcRenderers.quad(vc, mm, x0, yc, -w[0], x1, yc, -w[0], x1, yc, w[0], x0, yc, w[0], (a << 24) | 0xFF0000 | g2 << 8 | b2);
            }
        }
        if (mesh.has("mr_head")) {
            float h = p[10];
            if (h > 0.02F) mesh.drawColoured("mr_head", ps.last(), buffers, full, overlay, 255, (int) (200 + 50 * h), (int) (120 + 100 * h));
            else if (h < -0.02F) mesh.drawColoured("mr_head", ps.last(), buffers, full, overlay, 255, (int) (110 - 60 * h), (int) (40 - 30 * h));
            else mesh.draw("mr_head", ps.last(), buffers, light, overlay);
        }
        ps.popPose();
    }

    private static final float BY_TUG_X = 144.0F, BY_TUG_Y = 38.0F + 34.0F * 0.66F;

    private static final float[] BY_GAN = { 192.0F, 256.0F };

    private void bay(StationModuleBlockEntity be, MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float t) {
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        int st = be.bayState();
        boolean building = st == AsteroidBay.BUILDING, launch = st == AsteroidBay.LAUNCH, work = building && !be.bayPaused() || launch;
        float el = launch ? Math.max(0.0F, t - be.bayT0()) : 0.0F;
        float door = !launch ? 0.0F : el < Balance.TUG_LAUNCH_TICKS - 60 ? CargoPodRenderer.smooth((el - Balance.TUG_DOORS_AT) / 60.0F)
                : 1.0F - CargoPodRenderer.smooth((el - (Balance.TUG_LAUNCH_TICKS - 60)) / 60.0F);
        float slide = !launch ? 0.0F : el < 600.0F ? 96.0F * CargoPodRenderer.smooth((el - 260.0F) / 140.0F) : 96.0F * (1.0F - CargoPodRenderer.smooth((el - 600.0F) / 200.0F));
        for (String b : new String[] { "bay", "bay_end", "by_room", "by_rad_p", "by_rad_s" }) draw(mesh, b, ps, buffers, light, overlay);
        ps.pushPose();
        CargoPodRenderer.about(ps, 411.5F, 0.0F, 78.0F, Axis.YP, -95.0F * door);
        draw(mesh, "by_door_a", ps, buffers, light, overlay);
        ps.popPose();
        ps.pushPose();
        CargoPodRenderer.about(ps, 411.5F, 0.0F, -78.0F, Axis.YP, 95.0F * door);
        draw(mesh, "by_door_b", ps, buffers, light, overlay);
        ps.popPose();
        ps.pushPose();
        ps.translate(slide * PX, 0.0F, 0.0F);
        draw(mesh, "by_cradle", ps, buffers, light, overlay);
        ps.popPose();
        boolean moving = launch && door > 0.01F && door < 0.99F;
        if (mesh.has("by_led")) {
            if (moving || launch && el < Balance.TUG_DOORS_AT) {
                float k = 0.6F + 0.4F * (float) Math.sin(t * 0.4F);
                mesh.drawColoured("by_led", ps.last(), buffers, full, overlay, (int) (255 * k), (int) (170 * k), (int) (60 * k));
            } else mesh.drawColoured("by_led", ps.last(), buffers, full, overlay, 74, 163, 255);
        }
        if (mesh.has("by_screen")) mesh.drawColoured("by_screen", ps.last(), buffers, full, overlay, work ? 120 : 70, work ? 190 : 130, work ? 255 : 200);
        ps.pushPose();
        CargoPodRenderer.about(ps, 56.0F, 0.0F, -62.0F, Axis.YP, t * 1.5F);
        if (mesh.has("by_holo")) mesh.drawColoured("by_holo", ps.last(), buffers, full, overlay, 255, 170, 80);
        ps.popPose();
        if (launch) alert(be);
        MeshModel dk = desk(mesh, "by_holo", "station_bay_desk");
        if (dk != null) {
            ps.pushPose();
            deskAt(ps, 56.0F, 96.0F, -64.0F, 0.0F);
            dk.draw("console", ps.last(), buffers, light, overlay);
            if (dk.has("glow")) dk.draw("glow", ps.last(), buffers, full, overlay);
            if (dk.has("screen")) dk.drawColoured("screen", ps.last(), buffers, full, overlay, work ? 120 : 70, work ? 190 : 130, work ? 255 : 200);
            CargoPodRenderer.about(ps, 0.0F, 0.0F, 5.6F, Axis.YP, t * 1.5F);
            if (dk.has("holo")) dk.drawColoured("holo", ps.last(), buffers, full, overlay, 255, 170, 80);
            ps.popPose();
        }
        if (mesh.has("by_topbeacon")) {
            boolean on = (int) (t / 20.0F) % 2 == 0;
            mesh.drawColoured("by_topbeacon", ps.last(), buffers, on ? full : light, overlay, on ? 255 : 90, on ? 50 : 40, on ? 40 : 40);
        }
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            if (work) CargoPodRenderer.about(ps, 368.0F, 182.0F, i == 0 ? 32.0F : -32.0F, Axis.YP, t * 14.0F);
            if (mesh.has("by_beacon" + i)) {
                if (work) mesh.drawColoured("by_beacon" + i, ps.last(), buffers, full, overlay, 255, 160, 40);
                else if (building) mesh.drawColoured("by_beacon" + i, ps.last(), buffers, full, overlay, 150, 30, 25);
                else mesh.drawColoured("by_beacon" + i, ps.last(), buffers, light, overlay, 70, 70, 75);
            }
            ps.popPose();
        }
        for (int i = 0; i < 8; i++) {
            if (!mesh.has("by_fl" + i)) continue;
            if (launch) {
                boolean on = Math.floorMod((int) (t / 3.0F) - i, 8) < 2;
                mesh.drawColoured("by_fl" + i, ps.last(), buffers, full, overlay, on ? 255 : 90, on ? 180 : 60, on ? 60 : 30);
            } else if (work) {
                float k = 0.6F + 0.4F * (float) Math.sin(t * 0.08F + i * 0.6F);
                mesh.drawColoured("by_fl" + i, ps.last(), buffers, full, overlay, (int) (255 * k), (int) (190 * k), (int) (90 * k));
            } else mesh.drawColoured("by_fl" + i, ps.last(), buffers, full, overlay, 120, 150, 190);
        }

        var level = be.getLevel();
        for (int i = 0; i < 2; i++) {
            boolean weld = work && !launch;
            float shift = weld ? 34.0F * (float) Math.sin(t * 0.012F + i * Math.PI) : 0.0F, swing = weld ? 22.0F * (float) Math.sin(t * 0.05F + i * 1.7F) : 0.0F;
            ps.pushPose();
            ps.translate(shift * PX, 0.0F, 0.0F);
            draw(mesh, "by_gan" + i, ps, buffers, light, overlay);
            CargoPodRenderer.about(ps, BY_GAN[i], 168.0F, 0.0F, Axis.XP, swing);
            draw(mesh, "by_arm" + i, ps, buffers, light, overlay);
            if (mesh.has("by_torch" + i)) {
                if (weld) {
                    float f = 0.55F + 0.45F * (level == null ? 1.0F : level.random.nextFloat());
                    mesh.drawColoured("by_torch" + i, ps.last(), buffers, full, overlay, 255, (int) (200 * f), (int) (90 * f));
                } else mesh.draw("by_torch" + i, ps.last(), buffers, light, overlay);
            }
            ps.popPose();
            if (weld && level != null && level.random.nextFloat() < 0.5F) {
                var w = bayWorld(be, BY_GAN[i] + shift + 16.0F, 95.0F, 0.0F);
                for (int n = 0; n < 2; n++)
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z,
                            (level.random.nextFloat() - 0.5F) * 0.25F, -level.random.nextFloat() * 0.15F, (level.random.nextFloat() - 0.5F) * 0.25F);
                if (level.random.nextFloat() < 0.2F) level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, w.x, w.y, w.z, 0.0, 0.01, 0.0);
            }
        }

        if (st == AsteroidBay.BUILDING || st == AsteroidBay.READY || launch && el < Balance.TUG_LEAVE_AT) {
            MeshModel tug = MeshModel.named("asteroid_tug");
            if (tug.has("tug")) {
                float pm = st == AsteroidBay.BUILDING ? be.bayPm() / 1000.0F : 1.0F;
                ps.pushPose();
                ps.translate((BY_TUG_X + slide) * PX, BY_TUG_Y * PX, 0.0F);
                draw(tug, "tug", ps, buffers, light, overlay);
                boolean ready = st != AsteroidBay.BUILDING;
                if (tug.has("tg_led")) tug.drawColoured("tg_led", ps.last(), buffers, ready ? full : light, overlay, ready ? 74 : 90, ready ? 163 : 95, ready ? 255 : 100);
                for (int k = 0; k < 4; k++) {
                    if (pm >= 0.2F) draw(tug, "tg_eng" + k, ps, buffers, light, overlay);
                    if (pm >= 0.2F && tug.has("tg_glow" + k)) tug.drawColoured("tg_glow" + k, ps.last(), buffers, ready ? full : light, overlay, ready ? 60 : 40, ready ? 110 : 50, ready ? 170 : 60);
                    if (pm >= 0.4F) draw(tug, "tg_claw" + k, ps, buffers, light, overlay);
                }
                if (pm >= 0.6F) draw(tug, "tg_net", ps, buffers, light, overlay);
                if (pm >= 0.8F) for (String w : new String[] { "tg_wing_p", "tg_wing_s", "tg_wing2_p", "tg_wing2_s" }) draw(tug, w, ps, buffers, light, overlay);
                ps.popPose();
            }
        }
    }

    private static net.minecraft.world.phys.Vec3 bayWorld(StationModuleBlockEntity be, float x, float y, float z) {
        double lx = x / 16.0 - 0.5, lz = z / 16.0;
        double wx, wz;
        switch (be.facing()) {
            case SOUTH -> { wx = -lz; wz = lx; }
            case WEST -> { wx = -lx; wz = -lz; }
            case NORTH -> { wx = lz; wz = -lx; }
            default -> { wx = lx; wz = lz; }
        }
        return new net.minecraft.world.phys.Vec3(be.getBlockPos().getX() + 0.5 + wx, be.getBlockPos().getY() + y / 16.0, be.getBlockPos().getZ() + 0.5 + wz);
    }

    private static void mirrorPetals(MeshModel mesh, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float fold, float tip) {
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(15.0 + 30.0 * k);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            ps.pushPose();
            aboutAxis(ps, MR_HUBF, MR_AX + MR_R0 * c, MR_R0 * s, 0.0F, -s, c, -fold);
            draw(mesh, "mr_pet" + k, ps, buffers, light, overlay);
            aboutAxis(ps, mrDishX(MR_RM), MR_AX + MR_RM * c, MR_RM * s, 0.0F, -s, c, tip);
            draw(mesh, "mr_tip" + k, ps, buffers, light, overlay);
            ps.popPose();
        }
    }

    private static void aboutAxis(PoseStack ps, float x, float y, float z, float ax, float ay, float az, float deg) {
        if (Math.abs(deg) < 0.01F) return;
        ps.translate(x * PX, y * PX, z * PX);
        ps.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(deg), ax, ay, az));
        ps.translate(-x * PX, -y * PX, -z * PX);
    }

    private static void hotDraw(MeshModel mesh, String b, PoseStack ps, MultiBufferSource buffers, int light, int overlay, float heat) {
        if (!mesh.has(b)) return;
        if (heat <= 0.02F) { mesh.draw(b, ps.last(), buffers, light, overlay); return; }
        mesh.drawColoured(b, ps.last(), buffers, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, overlay, 255, (int) (255 - 120 * heat), (int) (255 - 200 * heat));
    }

    private static void rotX(PoseStack ps, float y, float z, float deg) {
        ps.translate(0.0, y, z); ps.mulPose(Axis.XP.rotationDegrees(deg)); ps.translate(0.0, -y, -z);
    }

    private static void draw(MeshModel mesh, String b, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (mesh.has(b)) mesh.draw(b, ps.last(), buffers, light, overlay);
    }

    private static MeshModel desk(MeshModel module, String oldBone, String name) {
        MeshModel d = MeshModel.named(name);
        return d.has("console") && !module.has(oldBone) ? d : null;
    }

    private static void deskAt(PoseStack ps, float x, float y, float z, float deg) {
        ps.translate(x * PX, y * PX, z * PX);
        if (deg != 0.0F) ps.mulPose(Axis.YP.rotationDegrees(deg));
    }

    @Override public boolean shouldRenderOffScreen(StationModuleBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(StationModuleBlockEntity be) { return new AABB(be.getBlockPos()).inflate(40.0, 30.0, 40.0); }
    @Override public int getViewDistance() { return 256; }
}
