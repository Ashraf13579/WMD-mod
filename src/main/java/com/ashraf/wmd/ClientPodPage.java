package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

final class ClientPodPage {

    private ClientPodPage() {}

    private static final Map<String, float[]> GEO = new HashMap<>();

    static Vec3 screenWorld, screenToPilot;
    static long screenAt = Long.MIN_VALUE;

    static boolean pageOn(JetCockpitBlockEntity be) {
        CockpitSeatEntity s = ClientCockpit.seated();
        if (s == null || s.cockpit() == null || !s.cockpit().equals(be.getBlockPos())) return false;
        CockpitHudPayload h = ClientCockpitBridge.hud();
        return h != null && h.pod() != CockpitHudPayload.NONE && h.has(CockpitHudPayload.F_POWER) && (h.lasing() || ClientPodCam.active());
    }

    private static float[] geo(String model) {
        return GEO.computeIfAbsent(model, m -> {
            MeshModel mm = MeshModel.named(m);
            float[] b = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
            mm.forEachQuad("mfd_r", (v, i) -> { for (int k = 0; k < 4; k++) { int o = i + k * 8; for (int a = 0; a < 3; a++) { b[a] = Math.min(b[a], v[o + a]); b[a + 3] = Math.max(b[a + 3], v[o + a]); } } });
            if (b[0] > b[3]) return null;
            double[] seatZ = { 0.0, 0.0 };
            mm.forEachQuad("seat", (v, i) -> { for (int k = 0; k < 4; k++) { seatZ[0] += v[i + k * 8 + 2]; seatZ[1]++; } });
            float cx = (b[0] + b[3]) * 0.5F, cy = (b[1] + b[4]) * 0.5F, cz = (b[2] + b[5]) * 0.5F;
            float unit = (b[3] - b[0]) / 4.6F;
            float side = seatZ[1] > 0 ? Math.signum((float) (seatZ[0] / seatZ[1]) - cz) : -1.0F;
            float pageZ = (side > 0 ? b[5] : b[2]) + side * 0.15F * unit;
            return new float[] { cx, cy - 0.2F * unit, cz, 2.2F * unit, 2.6F * unit, side == 0 ? -1.0F : side, pageZ };
        });
    }

    static void draw(JetCockpitBlockEntity be, String model, Matrix4f housing, MultiBufferSource buffers) {
        float[] g = geo(model);
        if (g == null) return;
        Minecraft mc = Minecraft.getInstance();
        CockpitHudPayload h = ClientCockpitBridge.hud();
        if (h == null) return;

        Vector3f view = new Vector3f(0.0F, 0.0F, -g[5]);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f right = new Vector3f(view).cross(up);
        float w = g[3] * 2.0F, s = w / 100.0F;
        float hgt = g[4] * 2.0F / s;
        Matrix4f m = new Matrix4f(housing)
                .translate(g[0] - right.x * g[3], g[1] + g[4], g[6])
                .mul(new Matrix4f().set(new float[] { right.x * s, right.y * s, right.z * s, 0, 0, -s, 0, 0, view.x * s, view.y * s, view.z * s, 0, 0, 0, 0, 1 }));

        Vector4f c = housing.transform(new Vector4f(g[0], g[1], g[6], 1.0F));
        Vector4f toP = housing.transform(new Vector4f(g[0], g[1], g[6] + g[5] * 4.0F * (g[3] / 2.2F), 1.0F));
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        screenWorld = new Vec3(cam.x + c.x, cam.y + c.y, cam.z + c.z);
        Vec3 tp = new Vec3(cam.x + toP.x, cam.y + toP.y, cam.z + toP.z).subtract(screenWorld);
        screenToPilot = tp.lengthSqr() > 1.0E-9 ? tp.normalize() : null;
        screenAt = mc.level == null ? 0 : mc.level.getGameTime();

        int light = LightTexture.FULL_BRIGHT;
        VertexConsumer bg = buffers.getBuffer(RenderType.textBackground());
        boolean lasing = h.lasing();
        long t = mc.level == null ? 0 : mc.level.getGameTime();
        fill(bg, m, 0, 0, 100, hgt, 0xF0061209, light);
        int lineC = 0xFF6FE69A, dimC = 0xA06FE69A, orange = (t / 6) % 2 == 0 ? 0xFFFF8A3A : 0xC0FF8A3A;
        float cx = 50, cy = hgt * 0.5F + 4;
        int box = lasing ? orange : lineC;
        float r = 13;
        fill(bg, m, cx - r, cy - r, cx + r, cy - r + 1.6F, box, light); fill(bg, m, cx - r, cy + r - 1.6F, cx + r, cy + r, box, light);
        fill(bg, m, cx - r, cy - r, cx - r + 1.6F, cy + r, box, light); fill(bg, m, cx + r - 1.6F, cy - r, cx + r, cy + r, box, light);
        fill(bg, m, 8, cy - 0.6F, cx - r - 4, cy + 0.6F, dimC, light); fill(bg, m, cx + r + 4, cy - 0.6F, 92, cy + 0.6F, dimC, light);
        fill(bg, m, cx - 0.6F, 22, cx + 0.6F, cy - r - 4, dimC, light); fill(bg, m, cx - 0.6F, cy + r + 4, cx + 0.6F, hgt - 22, dimC, light);
        fill(bg, m, 2, 2, 98, 3, dimC, light); fill(bg, m, 2, hgt - 3, 98, hgt - 2, dimC, light);
        Font font = mc.font;
        text(font, m, buffers, "TGP", 5, 6, 1.2F, lineC);
        String st = lasing ? "LASE" : "STBY";
        text(font, m, buffers, st, 95 - font.width(st) * 1.2F, 6, 1.2F, lasing ? 0xFFFF8A3A : dimC);
        String rng = "RNG --";
        if (lasing && mc.player != null) rng = "RNG " + Math.round(new Vec3(h.px(), h.py(), h.pz()).distanceTo(mc.player.getEyePosition()));
        text(font, m, buffers, rng, 5, hgt - 16, 1.2F, lineC);
        String hint = ClientPodCam.active() ? "" : "K: VIEW";
        if (!hint.isEmpty()) text(font, m, buffers, hint, 95 - font.width(hint) * 1.0F, hgt - 15, 1.0F, dimC);
    }

    private static void fill(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb, int light) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vc.addVertex(m, x0, y0, 0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m, x0, y1, 0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m, x1, y1, 0).setColor(r, g, b, a).setLight(light);
        vc.addVertex(m, x1, y0, 0).setColor(r, g, b, a).setLight(light);
    }

    private static void text(Font font, Matrix4f m, MultiBufferSource buffers, String s, float x, float y, float scale, int argb) {
        Matrix4f t = new Matrix4f(m).translate(x, y, -0.4F).scale(scale, scale, 1.0F);
        font.drawInBatch(s, 0, 0, argb, false, t, buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
    }

    static void clearStatics() { screenWorld = null; screenToPilot = null; }
}
