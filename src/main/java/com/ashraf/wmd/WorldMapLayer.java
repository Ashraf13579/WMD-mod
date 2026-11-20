package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;

public final class WorldMapLayer {

    private final ResourceLocation rl;
    private final int w, h;
    private DynamicTexture tex;
    private double lcx = Double.NaN, lcz, lbpp = -1;
    private int lver = -1;
    private float lmul = -1;
    private boolean any;
    private final int[] lut = new int[256];

    public WorldMapLayer(String name, int w, int h) {
        this.rl = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/wm_" + name);
        this.w = w; this.h = h;
    }

    public boolean update(double cx, double cz, double bpp, float mul) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        String dim = mc.level.dimension().location().toString();
        int lvl = Mth.clamp((int) Math.floor(Math.log(Math.max(bpp, 1.0E-6) / WorldMap.BASE) / Math.log(2.0) + 1.0E-9), 0, WorldMap.MAX_LEVEL);

        double span = (double) WorldMap.TILE * (WorldMap.BASE << lvl);
        int tx0 = (int) Math.floor((cx - w / 2.0 * bpp) / span), tx1 = (int) Math.floor((cx + w / 2.0 * bpp) / span);
        int tz0 = (int) Math.floor((cz - h / 2.0 * bpp) / span), tz1 = (int) Math.floor((cz + h / 2.0 * bpp) / span);
        List<int[]> want = new ArrayList<>();
        for (int tz = tz0; tz <= tz1 && want.size() < 120; tz++) for (int tx = tx0; tx <= tx1 && want.size() < 120; tx++) want.add(new int[] { tx, tz });
        want.sort((a, b) -> Double.compare(d2(a, span, cx, cz), d2(b, span, cx, cz)));
        WorldMapView.want(dim, mc.level.getGameTime(), lvl, want);
        if (!WorldMapView.available()) return false;
        boolean moved = Double.isNaN(lcx) || Math.abs(cx - lcx) >= bpp * 0.5 || Math.abs(cz - lcz) >= bpp * 0.5 || bpp != lbpp || mul != lmul || WorldMapView.version != lver;
        if (!moved) return any;
        compose(cx, cz, bpp, lvl, mul);
        lcx = cx; lcz = cz; lbpp = bpp; lmul = mul; lver = WorldMapView.version;
        return any;
    }

    private static double d2(int[] t, double span, double cx, double cz) {
        double dx = (t[0] + 0.5) * span - cx, dz = (t[1] + 0.5) * span - cz;
        return dx * dx + dz * dz;
    }

    private void compose(double cx, double cz, double bpp, int lvl, float mul) {
        if (tex == null) {
            tex = new DynamicTexture(new NativeImage(w, h, false));
            Minecraft.getInstance().getTextureManager().register(rl, tex);
        }
        for (int id = 4; id < 256; id++) {
            int c = MapColor.getColorFromPackedId(id);
            int r = (int) ((c & 255) * mul), g = (int) (((c >> 8) & 255) * mul), b = (int) (((c >> 16) & 255) * mul);
            lut[id] = 0xFF000000 | b << 16 | g << 8 | r;
        }
        NativeImage img = tex.getPixels();
        if (img == null) return;
        WorldMapView.Entry[] last = new WorldMapView.Entry[WorldMap.MAX_LEVEL + 1];
        int[] lastTx = new int[WorldMap.MAX_LEVEL + 1], lastTz = new int[WorldMap.MAX_LEVEL + 1];
        boolean[] have = new boolean[WorldMap.MAX_LEVEL + 1];
        boolean some = false;
        for (int py = 0; py < h; py++) {
            int iz = (int) Math.floor(cz + (py + 0.5 - h / 2.0) * bpp);
            java.util.Arrays.fill(have, false);
            for (int px = 0; px < w; px++) {
                int ix = (int) Math.floor(cx + (px + 0.5 - w / 2.0) * bpp), argb = 0;
                for (int l = lvl; l <= WorldMap.MAX_LEVEL && argb == 0; l++) {
                    int bl = WorldMap.BASE << l, u = Math.floorDiv(ix, bl), v = Math.floorDiv(iz, bl);
                    int tx = Math.floorDiv(u, WorldMap.TILE), tz = Math.floorDiv(v, WorldMap.TILE);
                    if (!have[l] || lastTx[l] != tx || lastTz[l] != tz) { last[l] = WorldMapView.tile(l, tx, tz); lastTx[l] = tx; lastTz[l] = tz; have[l] = true; }
                    WorldMapView.Entry e = last[l];
                    if (e == null) continue;
                    int id = e.ids[Math.floorMod(v, WorldMap.TILE) * WorldMap.TILE + Math.floorMod(u, WorldMap.TILE)] & 0xFF;
                    if (id >= 4) argb = lut[id];
                }
                if (argb != 0) some = true;
                img.setPixelRGBA(px, py, argb);
            }
        }
        any = some;
        tex.upload();
    }

    public void draw(GuiGraphics g, int x, int y) {
        if (tex != null && any) g.blit(rl, x, y, 0.0F, 0.0F, w, h, w, h);
    }

    public void drawStatus(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y) {
        if (!WorldMapView.available() || WorldMapView.progress() >= 100) return;
        g.drawString(font, net.minecraft.network.chat.Component.translatable("gui.wmd.worldmap.building", WorldMapView.progress()), x, y, 0xFFA8B4C4, true);
    }

    public void close() {
        if (tex != null) { tex.close(); Minecraft.getInstance().getTextureManager().release(rl); tex = null; }
        lcx = Double.NaN; any = false;
    }
}
