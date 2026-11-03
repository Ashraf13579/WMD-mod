package com.ashraf.wmd;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

public class RouteChartScreen extends Screen {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/route_chart_recon");

    private static final ResourceLocation TERRAIN = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/route_chart_terrain");
    private DynamicTexture terrainTex;
    private int terrainShown = -1;
    private int[] asked;
    private long askedAt;
    private static final int[] GRID = { 100, 250, 500, 1000, 2500 };

    private static final int[] ASSET_COL = { 0xFFFF9030, 0xFF40E060, 0xFFFFE040, 0xFF40D0FF, 0xFF5090FF, 0xFF70F0FF, 0xFFFF3030, 0xFFFF40FF, 0xFFC8E8FF, 0xFFB060FF };
    private static final int PICK = 5;

    private final Screen parent;
    private final CcMapTableBlockEntity table;
    private final CcDronesPayload.Site site;
    private final List<int[]> wps = new ArrayList<>();
    private int[] target;
    private boolean modeTarget, placed;
    private double bpp = -1, viewX, viewZ;
    private int x0, y0, size;

    private int drag = -4;
    private double panMx, panMy, panX, panZ;
    private DynamicTexture texture;
    private int shownVersion = -1;
    private boolean texReady;
    private Component note = Component.empty();
    private WmdUi.KitButton wpButton, tgtButton;

    public RouteChartScreen(Screen parent, CcMapTableBlockEntity table, CcDronesPayload.Site site) {
        super(Component.translatable("gui.wmd.chart.title"));
        this.parent = parent;
        this.table = table;
        this.site = site;
        int[] m = site.mission();
        if (m.length >= 3) {
            int n = m[0];
            for (int i = 0; i < n; i++) wps.add(new int[] { m[1 + 2 * i], m[2 + 2 * i] });
            target = new int[] { m[1 + 2 * n], m[2 + 2 * n] };
        }
        modeTarget = target == null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        size = Math.max(120, Math.min(width - 40, height - 74));
        x0 = (width - size) / 2;
        y0 = 26;
        if (bpp < 0) {
            viewX = site.at().getX() + 0.5;
            viewZ = site.at().getZ() + 0.5;
            bpp = site.range() * 2.3 / size;
        }
        int by = y0 + size + 6, w = (size - 16) / 5;
        wpButton = addRenderableWidget(new WmdUi.KitButton(x0, by, w, 14, Component.translatable("gui.wmd.chart.waypoint"), b -> { modeTarget = false; modes(); }));
        tgtButton = addRenderableWidget(new WmdUi.KitButton(x0 + (w + 4), by, w, 14, Component.translatable("gui.wmd.chart.target"), b -> { modeTarget = true; modes(); }));
        addRenderableWidget(new WmdUi.KitButton(x0 + 2 * (w + 4), by, w, 14, Component.translatable("gui.wmd.chart.clear"), b -> { wps.clear(); target = null; modeTarget = true; modes(); note = Component.empty(); }));
        addRenderableWidget(new WmdUi.KitButton(x0 + 3 * (w + 4), by, w, 14, Component.translatable("gui.wmd.chart.save"), b -> save()));
        addRenderableWidget(new WmdUi.KitButton(x0 + 4 * (w + 4), by, size - 4 * (w + 4), 14, Component.translatable("gui.wmd.chart.back"), b -> onClose()));
        modes();
    }

    private void modes() {
        if (wpButton == null) return;
        wpButton.active = modeTarget;
        tgtButton.active = !modeTarget;
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void removed() {
        super.removed();
        if (texture != null) { minecraft.getTextureManager().release(TEX); texture = null; }
        texReady = false;
        if (terrainTex != null) { minecraft.getTextureManager().release(TERRAIN); terrainTex = null; }
    }

    private void askTerrain() {
        double span = size * bpp;
        int cell = TerrainAtlas.MIN_CELL;
        while (span / cell > 112) cell *= 2;
        int x0c = (int) Math.floor(wx(x0) / cell) * cell - cell, z0c = (int) Math.floor(wz(y0) / cell) * cell - cell;
        int w = Math.min(TerrainAtlas.MAX_CELLS, (int) Math.ceil(span / cell) + 3), h = w;
        int[] want = { x0c, z0c, cell, w, h };
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0;
        ChartTerrainPayload t = ChartTerrainView.tile;
        boolean again = t != null && t.pending() && java.util.Arrays.equals(want, asked) && now - askedAt >= 20;
        if ((java.util.Arrays.equals(want, asked) && !again) || now - askedAt < 8) return;
        asked = want;
        askedAt = now;
        PacketDistributor.sendToServer(new ChartTerrainPayload.Request(x0c, z0c, cell, w, h));
    }

    private final java.util.Map<Integer, Integer> biomeCol = new java.util.HashMap<>();

    private int biomeColour(int id) {
        return biomeCol.computeIfAbsent(id, k -> {
            var reg = minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var h = reg.getHolder(k);
            if (h.isEmpty()) return MapColor.STONE.col;
            var b = h.get();
            String path = b.unwrapKey().map(r -> r.location().getPath()).orElse("");
            if (b.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || b.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN) || b.is(net.minecraft.tags.BiomeTags.IS_RIVER)) return MapColor.WATER.col;
            if (b.is(net.minecraft.tags.BiomeTags.IS_BEACH) || path.contains("desert")) return MapColor.SAND.col;
            if (b.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) return MapColor.COLOR_ORANGE.col;
            if (path.contains("snow") || path.contains("frozen") || path.contains("ice") || path.contains("peaks") && !path.contains("stony")) return MapColor.SNOW.col;
            if (path.contains("stony") || path.contains("gravelly") || path.contains("windswept")) return MapColor.STONE.col;
            if (path.contains("swamp") || path.contains("mangrove")) return 0x4C6B2E;
            if (b.is(net.minecraft.tags.BiomeTags.IS_FOREST) || b.is(net.minecraft.tags.BiomeTags.IS_JUNGLE) || b.is(net.minecraft.tags.BiomeTags.IS_TAIGA) || path.contains("grove")) return MapColor.PLANT.col;
            if (b.is(net.minecraft.tags.BiomeTags.IS_SAVANNA)) return 0xA8A050;
            return MapColor.GRASS.col;
        });
    }

    private void rebuildTerrain() {
        terrainShown = ChartTerrainView.version;
        if (terrainTex != null) { minecraft.getTextureManager().release(TERRAIN); terrainTex = null; }
        ChartTerrainPayload t = ChartTerrainView.tile;
        if (t == null || minecraft.level == null) return;
        NativeImage img = new NativeImage(t.w(), t.h(), false);
        for (int j = 0; j < t.h(); j++) for (int i = 0; i < t.w(); i++) {
            int c = t.argb()[j * t.w() + i];
            if (c == 0) { img.setPixelRGBA(i, j, 0); continue; }
            if ((c >>> 24) == 0x01) {
                int rgb = biomeColour((c >>> 2) & 0x3FFFFF), sh = c & 3;
                float k = (sh == 2 ? 1.0F : sh == 0 ? 0.71F : 0.86F) * 0.9F;
                c = 0xFF000000 | (int) (((rgb >> 16) & 0xFF) * k) << 16 | (int) (((rgb >> 8) & 0xFF) * k) << 8 | (int) ((rgb & 0xFF) * k);
            }
            img.setPixelRGBA(i, j, (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF);
        }
        terrainTex = new DynamicTexture(img);
        minecraft.getTextureManager().register(TERRAIN, terrainTex);
    }

    private double sx(double wx) { return x0 + size / 2.0 + (wx - viewX) / bpp; }
    private double sy(double wz) { return y0 + size / 2.0 + (wz - viewZ) / bpp; }
    private double wx(double sx) { return viewX + (sx - x0 - size / 2.0) * bpp; }
    private double wz(double sy) { return viewZ + (sy - y0 - size / 2.0) * bpp; }
    private boolean inChart(double mx, double my) { return mx >= x0 && mx < x0 + size && my >= y0 && my < y0 + size; }

    private int pointAt(double mx, double my) {
        if (target != null && Math.abs(sx(target[0] + 0.5) - mx) <= PICK && Math.abs(sy(target[1] + 0.5) - my) <= PICK) return -1;
        for (int i = wps.size() - 1; i >= 0; i--)
            if (Math.abs(sx(wps.get(i)[0] + 0.5) - mx) <= PICK && Math.abs(sy(wps.get(i)[1] + 0.5) - my) <= PICK) return i;
        return -4;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!inChart(mx, my)) return super.mouseClicked(mx, my, button);
        int p = pointAt(mx, my);
        if (button == 1) {
            if (p == -1) target = null;
            else if (p >= 0) wps.remove(p);
            else { drag = -3; panMx = mx; panMy = my; panX = viewX; panZ = viewZ; }
            if (p != -4) WmdUi.click();
            return true;
        }
        if (button != 0) return true;
        if (p != -4) { drag = p; return true; }
        int[] at = { (int) Math.floor(wx(mx)), (int) Math.floor(wz(my)) };
        if (modeTarget || target == null) { target = at; modeTarget = false; modes(); }
        else if (wps.size() < DroneMission.WAYPOINTS) wps.add(at);
        else { note = Component.translatable("gui.wmd.chart.max_wp", DroneMission.WAYPOINTS); return true; }
        note = Component.empty();
        WmdUi.click();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == -3) { viewX = panX - (mx - panMx) * bpp; viewZ = panZ - (my - panMy) * bpp; return true; }
        if (drag == -1 || drag >= 0) {
            double cmx = Math.max(x0, Math.min(x0 + size - 1, mx)), cmy = Math.max(y0, Math.min(y0 + size - 1, my));
            int[] at = { (int) Math.floor(wx(cmx)), (int) Math.floor(wz(cmy)) };
            if (drag == -1) target = at; else if (drag < wps.size()) wps.set(drag, at);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        drag = -4;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!inChart(mx, my) || sy == 0) return super.mouseScrolled(mx, my, sx, sy);
        double wxm = wx(mx), wzm = wz(my);
        double min = 500.0 / size, max = site.range() * 3.0 / size;
        bpp = Math.max(min, Math.min(max, bpp * (sy > 0 ? 0.8 : 1.25)));
        viewX = wxm - (mx - x0 - size / 2.0) * bpp;
        viewZ = wzm - (my - y0 - size / 2.0) * bpp;
        return true;
    }

    private void save() {
        if (target == null) { note = Component.translatable("gui.wmd.big_drone.need_target"); return; }
        int n = wps.size();
        int[] d = new int[3 + 2 * n];
        d[0] = n;
        for (int i = 0; i < n; i++) { d[1 + 2 * i] = wps.get(i)[0]; d[2 + 2 * i] = wps.get(i)[1]; }
        d[1 + 2 * n] = target[0]; d[2 + 2 * n] = target[1];
        PacketDistributor.sendToServer(new CcRoutePayload(table.getBlockPos(), site.pos(), d));
        note = Component.translatable("gui.wmd.chart.saved");
        WmdUi.click();
    }

    private void rebuildTexture() {
        shownVersion = ReconView.version;
        if (texture != null) { minecraft.getTextureManager().release(TEX); texture = null; }
        texReady = false;
        ReconMapPayload m = ReconView.map;
        if (m == null || !m.hasMap() || m.n() <= 0) return;
        int n = m.n();
        NativeImage img = new NativeImage(n, n, false);
        byte[] col = m.col();
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            int v = col[z * n + x] & 0xFF;
            img.setPixelRGBA(x, z, v < 4 ? 0x00000000 : MapColor.getColorFromPackedId(v));
        }
        texture = new DynamicTexture(img);
        minecraft.getTextureManager().register(TEX, texture);
        texReady = true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        if (ReconView.version != shownVersion) rebuildTexture();
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.chart.head", Component.translatable(site.hangar() ? "gui.wmd.cc.drones.hangar" : "gui.wmd.cc.drones.catapult"),
                site.at().getX(), site.at().getZ(), site.tier() == 0 ? "-" : "T" + site.tier()), x0, 10, size, WmdUi.TEXT);
        WmdHelp.button(g, x0 + size - 10, 7);
        g.enableScissor(x0, y0, x0 + size, y0 + size);

        askTerrain();
        if (ChartTerrainView.version != terrainShown) rebuildTerrain();
        ChartTerrainPayload tt = ChartTerrainView.tile;
        if (terrainTex != null && tt != null) {
            int cs = tt.cell();
            int ax = (int) Math.round(sx(tt.x0())), az = (int) Math.round(sy(tt.z0()));
            int pw = (int) Math.round(tt.w() * cs / bpp), ph = (int) Math.round(tt.h() * cs / bpp);
            if (pw > 0 && ph > 0) g.blit(TERRAIN, ax, az, pw, ph, 0.0F, 0.0F, tt.w(), tt.h(), tt.w(), tt.h());
            g.fill(x0, y0, x0 + size, y0 + size, 0x38000814);
        }

        ReconMapPayload rm = ReconView.map;
        if (texReady && rm != null) {
            int ax = (int) Math.round(sx(rm.x0())), az = (int) Math.round(sy(rm.z0())), w = (int) Math.round(rm.n() * rm.step() / bpp);
            if (w > 0) g.blit(TEX, ax, az, w, w, 0.0F, 0.0F, rm.n(), rm.n(), rm.n(), rm.n());
        }

        int step = GRID[GRID.length - 1];
        for (int s : GRID) if (s / bpp >= 28) { step = s; break; }
        double wl = wx(x0), wr = wx(x0 + size), wt = wz(y0), wb = wz(y0 + size);
        for (long k = (long) Math.floor(wl / step); k * step <= wr; k++) { int x = (int) Math.round(sx(k * step)); g.fill(x, y0, x + 1, y0 + size, 0x283C8CFF); }
        for (long k = (long) Math.floor(wt / step); k * step <= wb; k++) { int y = (int) Math.round(sy(k * step)); g.fill(x0, y, x0 + size, y + 1, 0x283C8CFF); }

        double scx = sx(site.at().getX() + 0.5), scy = sy(site.at().getZ() + 0.5), rr = site.range() / bpp;
        int dots = (int) Math.max(60, Math.min(720, rr * 2));
        for (int i = 0; i < dots; i++) {
            double a = Math.PI * 2.0 * i / dots;
            int x = (int) Math.round(scx + Math.cos(a) * rr), y = (int) Math.round(scy + Math.sin(a) * rr);
            if (i % 2 == 0) g.fill(x, y, x + 1, y + 1, 0xC06FA8FF);
        }

        int[] as = table.assets();
        for (int i = 0; i + 2 < as.length; i += 3) {
            int x = (int) Math.round(sx(as[i] + 0.5)), y = (int) Math.round(sy(as[i + 1] + 0.5));
            g.fill(x - 1, y - 1, x + 2, y + 2, ASSET_COL[Math.max(0, Math.min(ASSET_COL.length - 1, as[i + 2]))]);
        }
        CcTarget des = table.designated();
        if (des != null) cross(g, (int) Math.round(sx(des.x() + 0.5)), (int) Math.round(sy(des.z() + 0.5)), 4, 0xFFFF3030);

        double px = scx, py = scy;
        for (int[] w : wps) { double qx = sx(w[0] + 0.5), qy = sy(w[1] + 0.5); dotted(g, px, py, qx, qy, 0xFFE0E8F0); px = qx; py = qy; }
        if (target != null) dotted(g, px, py, sx(target[0] + 0.5), sy(target[1] + 0.5), 0xFFFF9040);

        int ix = (int) Math.round(scx), iy = (int) Math.round(scy);
        g.fill(ix - 3, iy - 3, ix + 4, iy + 4, 0xFF0A0D11);
        g.fill(ix - 2, iy - 2, ix + 3, iy + 3, WmdUi.BLUE_LIGHT);
        g.drawString(font, site.hangar() ? "H" : "C", ix + 5, iy - 4, WmdUi.BLUE_LIGHT, true);
        int hover = inChart(mouseX, mouseY) ? pointAt(mouseX, mouseY) : -4;
        for (int i = 0; i < wps.size(); i++) {
            int x = (int) Math.round(sx(wps.get(i)[0] + 0.5)), y = (int) Math.round(sy(wps.get(i)[1] + 0.5));
            g.fill(x - 3, y - 3, x + 4, y + 4, hover == i || drag == i ? 0xFFFFFFFF : 0xFF0A0D11);
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFFE0E8F0);
            g.drawString(font, Integer.toString(i + 1), x + 5, y - 4, 0xFFE0E8F0, true);
        }
        if (target != null) {
            int x = (int) Math.round(sx(target[0] + 0.5)), y = (int) Math.round(sy(target[1] + 0.5));
            cross(g, x, y, hover == -1 || drag == -1 ? 6 : 5, 0xFFFF9040);
            g.drawString(font, "TGT", x + 6, y - 4, 0xFFFF9040, true);
        }
        g.disableScissor();

        if (inChart(mouseX, mouseY)) {
            String at = (int) Math.floor(wx(mouseX)) + " " + (int) Math.floor(wz(mouseY));
            g.drawString(font, at, x0 + size - 4 - font.width(at), y0 + 4, WmdUi.TEXT, true);
        }
        g.drawString(font, Component.translatable("gui.wmd.chart.grid", step), x0 + 4, y0 + size - 12, WmdUi.TEXT_MUTED, true);

        Component foot;
        int col = WmdUi.TEXT;
        if (target == null) { foot = Component.translatable("gui.wmd.big_drone.no_route"); col = WmdUi.TEXT_MUTED; }
        else {
            double l = 0.0, lx = site.at().getX() + 0.5, lz = site.at().getZ() + 0.5;
            for (int[] w : wps) { l += Math.hypot(w[0] + 0.5 - lx, w[1] + 0.5 - lz); lx = w[0] + 0.5; lz = w[1] + 0.5; }
            l += Math.hypot(target[0] + 0.5 - lx, target[1] + 0.5 - lz);
            int direct = (int) Math.round(Math.hypot(target[0] - site.at().getX(), target[1] - site.at().getZ()));
            foot = Component.translatable("gui.wmd.big_drone.route", String.format("%,d", Math.round(l)), String.format("%,d", direct), String.format("%,d", site.range()));
            if (direct > site.range()) col = WmdUi.BAD;
        }
        int fy = y0 + size + 24;
        WmdUi.drawFit(g, font, foot, x0, fy, size, col);
        WmdUi.drawFit(g, font, note.getString().isEmpty() ? Component.translatable(modeTarget ? "gui.wmd.chart.hint_target" : "gui.wmd.chart.hint") : note, x0, fy + 11, size,
                note.getString().isEmpty() ? WmdUi.TEXT_MUTED : WmdUi.WARN);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.renderBackground(g, mouseX, mouseY, pt);
        WmdUi.panel(g, x0 - 8, 4, size + 16, size + 70);
        WmdUi.divider(g, x0, 20, size);
        WmdUi.well(g, x0, y0, size, size);
    }

    private static void cross(GuiGraphics g, int x, int y, int r, int col) {
        for (int k = -r; k <= r; k++) { g.fill(x + k, y + k, x + k + 1, y + k + 1, col); g.fill(x + k, y - k, x + k + 1, y - k + 1, col); }
    }

    private static void dotted(GuiGraphics g, double ax, double ay, double bx, double by, int col) {
        double len = Math.hypot(bx - ax, by - ay);
        int n = (int) Math.min(4000, len / 3.0);
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(ax + (bx - ax) * t), y = (int) Math.round(ay + (by - ay) * t);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
