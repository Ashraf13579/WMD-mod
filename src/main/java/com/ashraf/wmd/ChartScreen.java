package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

public class ChartScreen extends Screen {

    private static final int W = 420, H = 232, MAP_W = 248, MAP_H = 150, PRO_H = 44, SIDE_W = 154, SEND_GAP = 10, TILE_W = 128, TILE_H = 84;
    private static final String P = "gui.wmd.sp.";
    private static final int[] ZOOMS = { 16, 8, 4 };
    private final BlockPos src;
    private final boolean fromHelm;
    private int left, top, tab = SeaPilot.ROUTE, zoomIdx = 1, sel = -1, dragWp = -1, ticks, lastSent = -100, lastTile = -100, legScroll;
    private boolean dirty, panning, panMoved, viewSet;
    private double vx, vz, panMx, panMy;

    private float hc;
    private int hs = 1, hd = 20, end, sr = Balance.SP_STATION_R_DEF, sd = 20, mar = -1;
    private double sx, sz;
    private final List<double[]> wps = new ArrayList<>();

    private double bx, bz, hdg, v, dep, keel, pfL;
    private boolean sub, craft, helm, power, capped;
    private int crush = -1, mode, why, marEff = Balance.SP_MARGIN_SHIP;
    private int[] lv = new int[0], trk = new int[0], pf = new int[0], pfB = new int[0];
    private final List<int[]> con = new ArrayList<>(), fld = new ArrayList<>();
    private Component msg = Component.empty();
    private int msgAt = -1000;

    private ResourceLocation tex;
    private DynamicTexture dyn;
    private int tx0, tz0, tCell, tw, th, askX0, askZ0, askCell = -1;

    public ChartScreen(BlockPos src, boolean fromHelm) {
        super(Component.translatable(fromHelm ? P + "title_helm" : P + "title"));
        this.src = src;
        this.fromHelm = fromHelm;
        CompoundTag t = ClientSeaPilotBridge.state(src);
        if (t != null) { read(t, true); if (mode >= SeaPilot.HOLD) tab = mode; }
    }

    BlockPos src() { return src; }

    private void read(CompoundTag t, boolean settings) {
        bx = t.getDouble("x"); bz = t.getDouble("z"); hdg = t.getDouble("hdg"); v = t.getDouble("v"); dep = t.getDouble("dep"); sub = t.getBoolean("sub");
        crush = t.contains("crush") ? t.getInt("crush") : -1; keel = t.getDouble("keel"); power = t.getBoolean("pow"); craft = t.getBoolean("craft");
        helm = t.getBoolean("helm"); capped = t.getBoolean("cap"); mode = t.getInt("mode"); why = t.getInt("why");
        if (t.contains("marEff")) marEff = t.getInt("marEff");
        lv = t.getIntArray("lv"); trk = t.getIntArray("trk"); pf = t.getIntArray("pf"); pfB = t.getIntArray("pfB"); pfL = t.getDouble("pfL");
        con.clear();
        for (Tag e : t.getList("con", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) e; con.add(new int[] { c.getInt("x"), c.getInt("z"), c.getInt("k") }); }
        fld.clear();
        for (Tag e : t.getList("fld", Tag.TAG_COMPOUND)) { CompoundTag c = (CompoundTag) e; fld.add(new int[] { c.getInt("x"), c.getInt("z"), c.getInt("r"), c.getInt("n") }); }
        String m = t.getString("msg");
        if (!m.isEmpty()) {
            int bar = m.indexOf('|');
            msg = bar < 0 ? Component.translatable(m) : Component.translatable(m.substring(0, bar), m.substring(bar + 1));
            msgAt = ticks;
        }
        if (!viewSet) { vx = bx; vz = bz; viewSet = true; }
        if (!settings || !helm) return;
        hc = t.getFloat("hc"); hs = t.getInt("hs"); hd = t.getInt("hd");
        wps.clear();
        ListTag l = t.getList("rt", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) { CompoundTag w = l.getCompound(i); wps.add(new double[] { w.getDouble("x"), w.getDouble("z"), w.getInt("s"), w.getInt("d") }); }
        end = t.getInt("end"); sx = t.getDouble("sx"); sz = t.getDouble("sz"); sr = t.getInt("sr"); sd = t.getInt("sd"); mar = t.getInt("mar");
        if (sx == 0.0 && sz == 0.0) { sx = bx; sz = bz; }
        if (sel >= wps.size()) sel = wps.size() - 1;
    }

    private CompoundTag settings() {
        CompoundTag t = new CompoundTag();
        t.putFloat("hc", hc); t.putInt("hs", hs); t.putInt("hd", hd);
        ListTag l = new ListTag();
        for (double[] w : wps) { CompoundTag c = new CompoundTag(); c.putDouble("x", w[0]); c.putDouble("z", w[1]); c.putInt("s", (int) w[2]); c.putInt("d", (int) w[3]); l.add(c); }
        t.put("rt", l);
        t.putInt("end", end); t.putDouble("sx", sx); t.putDouble("sz", sz); t.putInt("sr", sr); t.putInt("sd", sd); t.putInt("mar", mar);
        return t;
    }

    private void edit() { dirty = true; }

    private void send(int op, int m) {
        CompoundTag t = op == SeaPilotPayloads.SET || op == SeaPilotPayloads.ENGAGE ? settings() : new CompoundTag();
        if (op == SeaPilotPayloads.ENGAGE) t.putInt("m", m);
        PacketDistributor.sendToServer(new SeaPilotPayloads.Ask(src, op, t));
        if (op == SeaPilotPayloads.SET || op == SeaPilotPayloads.ENGAGE) dirty = false;
        lastSent = ticks;
    }

    void onState() {
        CompoundTag t = ClientSeaPilotBridge.state(src);
        if (t == null) return;
        int was = mode;
        read(t, !dirty && dragWp < 0);
        if (was != mode || msgAt == ticks) rebuildWidgets();
    }

    @Override
    protected void init() {
        left = (width - W) / 2; top = (height - H) / 2;
        ClientSeaPilotBridge.onTileHook = () -> Minecraft.getInstance().execute(this::onTile);
        int sx0 = sideX(), y = top + 24;
        String[] tabs = { "hold", "route", "station" };
        int tw3 = (SIDE_W - 4) / 3;
        for (int i = 0; i < 3; i++) {
            final int m = SeaPilot.HOLD + i;
            addRenderableWidget(new WmdUi.KitButton(sx0 + i * (tw3 + 2), y, tw3, 14, Component.translatable(P + "tab_" + tabs[i]), b -> { tab = m; WmdUi.tab(); rebuildWidgets(); }));
        }
        y = top + 42;
        switch (tab) {
            case SeaPilot.HOLD -> {
                row(y + 4, () -> hc = (hc + 355) % 360, () -> hc = (hc + 5) % 360);
                row(y + 20, () -> hs = Math.max(0, hs - 1), () -> hs = Math.min(SeaPilot.SPEEDS - 1, hs + 1));
                if (sub) row(y + 36, () -> hd = Math.max(0, hd - 4), () -> hd = Math.min(depthMax(), hd + 4));
                addRenderableWidget(new WmdUi.KitButton(sx0, y + 56, SIDE_W, 14, Component.translatable(P + "now"), b -> { hc = (float) Math.round(hdg); hd = (int) Math.round(Math.max(0, dep)); edit(); }));
            }
            case SeaPilot.ROUTE -> {
                if (sel >= 0 && sel < wps.size()) {
                    final double[] w = wps.get(sel);
                    row(y + 68, () -> w[2] = Math.max(0, w[2] - 1), () -> w[2] = Math.min(SeaPilot.SPEEDS - 1, w[2] + 1));
                    if (sub) row(y + 84, () -> w[3] = Math.max(0, w[3] - 4), () -> w[3] = Math.min(depthMax(), w[3] + 4));
                }
                int bw = (SIDE_W - 4) / 3;
                addRenderableWidget(new WmdUi.KitButton(sx0, y + 102, bw, 14, Component.translatable(P + "end" + end), b -> { end = (end + 1) % 3; edit(); rebuildWidgets(); }));
                addRenderableWidget(new WmdUi.KitButton(sx0 + bw + 2, y + 102, bw, 14, Component.translatable(P + "reverse"), b -> { java.util.Collections.reverse(wps); sel = -1; edit(); rebuildWidgets(); }));
                addRenderableWidget(new WmdUi.KitButton(sx0 + 2 * (bw + 2), y + 102, bw, 14, Component.translatable(P + "clear"), b -> { wps.clear(); sel = -1; edit(); rebuildWidgets(); }));
            }
            default -> {
                row(y + 20, () -> sr = Math.max(Balance.SP_STATION_R_MIN, sr - 2), () -> sr = Math.min(Balance.SP_STATION_R_MAX, sr + 2));
                if (sub) row(y + 36, () -> sd = Math.max(0, sd - 4), () -> sd = Math.min(depthMax(), sd + 4));
                addRenderableWidget(new WmdUi.KitButton(sx0, y + 56, SIDE_W, 14, Component.translatable(P + "here"), b -> { sx = bx; sz = bz; edit(); }));
            }
        }
        row(top + 164, () -> mar = mar < 0 ? Math.max(Balance.SP_MARGIN_MIN, marEff - 1) : Math.max(Balance.SP_MARGIN_MIN, mar - 1),
                () -> mar = mar < 0 ? Math.min(Balance.SP_MARGIN_MAX, marEff + 1) : Math.min(Balance.SP_MARGIN_MAX, mar + 1));
        boolean on = mode == tab;
        if (!helm) return;
        if (on) addRenderableWidget(new WmdUi.KitButton(sx0, top + H - 24, SIDE_W, 18, Component.translatable(P + "disengage"), b -> { send(SeaPilotPayloads.OFF, 0); WmdUi.click(); }));
        else addRenderableWidget(new WmdUi.KitButton(sx0, top + H - 24, SIDE_W, 18, Component.translatable(P + "engage", Component.translatable(P + "tab_" + tabs[tab - 1])),
                b -> { send(SeaPilotPayloads.ENGAGE, tab); WmdUi.confirm(); }, true));
    }

    private int sideX() { return left + W - SIDE_W - 6; }

    private int depthMax() { return crush > 0 ? (int) Math.floor(Balance.SP_CRUSH_CAP * crush) : Balance.SP_DEPTH_MAX; }

    private void row(int y, Runnable minus, Runnable plus) {
        int x = sideX();
        addRenderableWidget(new WmdUi.KitButton(x + SIDE_W - 30, y, 14, 14, Component.literal("-"), b -> { minus.run(); edit(); }));
        addRenderableWidget(new WmdUi.KitButton(x + SIDE_W - 14, y, 14, 14, Component.literal("+"), b -> { plus.run(); edit(); }));
    }

    @Override
    public void tick() {
        ticks++;
        if (dirty && ticks - lastSent >= SEND_GAP) send(SeaPilotPayloads.SET, 0);
        else if (ticks % 20 == 0 && ticks - lastSent >= 5) { PacketDistributor.sendToServer(new SeaPilotPayloads.Ask(src, SeaPilotPayloads.OPEN, new CompoundTag())); lastSent = ticks; }
        askTile();
        Minecraft mc = Minecraft.getInstance();
        if (fromHelm ? !src.equals(ClientHelm.seatedHelm()) : mc.player == null || !Reach.usable(mc.player, src, Balance.CHART_REACH + 1.0)) onClose();
    }

    @Override
    public void onClose() {
        if (dirty && helm) send(SeaPilotPayloads.SET, 0);
        ClientSeaPilotBridge.onTileHook = null;
        if (dyn != null) { dyn.close(); dyn = null; tex = null; }
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    private double bpp() { return ZOOMS[zoomIdx]; }

    private void askTile() {
        int cell = (int) bpp() * 2;
        double halfW = MAP_W / 2.0 * bpp(), halfH = MAP_H / 2.0 * bpp();
        boolean covered = askCell == cell && vx - halfW >= askX0 && vz - halfH >= askZ0 && vx + halfW <= askX0 + TILE_W * cell && vz + halfH <= askZ0 + TILE_H * cell;
        if (covered || ticks - lastTile < 10) return;
        askCell = cell;
        askX0 = Mth.floor((vx - TILE_W * cell / 2.0) / cell) * cell;
        askZ0 = Mth.floor((vz - TILE_H * cell / 2.0) / cell) * cell;
        CompoundTag t = new CompoundTag();
        t.putInt("x0", askX0); t.putInt("z0", askZ0); t.putInt("cell", cell); t.putInt("w", TILE_W); t.putInt("h", TILE_H);
        PacketDistributor.sendToServer(new SeaPilotPayloads.Ask(src, SeaPilotPayloads.TILE, t));
        lastTile = ticks;
    }

    private void onTile() {
        SeaPilotPayloads.Tile p = ClientSeaPilotBridge.tile(src);
        if (p == null || p.cells().length != p.w() * p.h()) return;
        NativeImage img = new NativeImage(p.w(), p.h(), false);
        int[] c = p.cells();
        for (int y = 0; y < p.h(); y++) for (int x = 0; x < p.w(); x++) {
            int val = c[y * p.w() + x], argb = ChartGlass.colour(val, x, y, 1.0F);
            if ((x > 0 && ChartGlass.edge(val, c[y * p.w() + x - 1])) || (y > 0 && ChartGlass.edge(val, c[(y - 1) * p.w() + x]))) argb = WmdUi.scale(argb, 0.72F);
            img.setPixelRGBA(x, y, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF);
        }
        if (dyn != null) dyn.close();
        dyn = new DynamicTexture(img);
        tex = Minecraft.getInstance().getTextureManager().register("wmd_sea_chart", dyn);
        tx0 = p.x0(); tz0 = p.z0(); tCell = p.cell(); tw = p.w(); th = p.h();
    }

    private int mapX() { return left + 6; }
    private int mapY() { return top + 24; }
    private int sxOf(double x) { return (int) Math.round(mapX() + MAP_W / 2.0 + (x - vx) / bpp()); }
    private int syOf(double z) { return (int) Math.round(mapY() + MAP_H / 2.0 + (z - vz) / bpp()); }
    private double wx(double mx) { return vx + (mx - mapX() - MAP_W / 2.0) * bpp(); }
    private double wz(double my) { return vz + (my - mapY() - MAP_H / 2.0) * bpp(); }
    private boolean inMap(double mx, double my) { return WmdUi.over(mx, my, mapX(), mapY(), MAP_W, MAP_H); }

    private int nearestWp(double mx, double my) {
        int best = -1; double bd = 36;
        for (int i = 0; i < wps.size(); i++) { double dx = sxOf(wps.get(i)[0]) - mx, dy = syOf(wps.get(i)[1]) - my, d = dx * dx + dy * dy; if (d < bd) { bd = d; best = i; } }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == SeaPilot.ROUTE && WmdUi.over(mx, my, sideX(), top + 42, SIDE_W, 64)) {
            int i = legScroll + (int) ((my - top - 54) / 10);
            if (my >= top + 54 && i >= 0 && i < wps.size()) { sel = i; WmdUi.click(); rebuildWidgets(); }
            return true;
        }
        if (!inMap(mx, my)) return false;
        if (button == 1) { panning = true; panMoved = false; panMx = mx; panMy = my; return true; }
        if (button != 0 || !helm) return button == 0;
        if (tab == SeaPilot.ROUTE) {
            int w = nearestWp(mx, my);
            if (w >= 0) { sel = w; dragWp = w; }
            else if (wps.size() < Balance.SP_WP_MAX) {
                double[] last = sel >= 0 && sel < wps.size() ? wps.get(sel) : wps.isEmpty() ? null : wps.get(wps.size() - 1);
                wps.add(new double[] { wx(mx), wz(my), last == null ? 1 : last[2], last == null ? Math.round(Math.max(0, sub ? dep : 0)) : last[3] });
                sel = wps.size() - 1; dragWp = sel; edit();
            } else WmdUi.deny();
            WmdUi.click(); rebuildWidgets();
            return true;
        }
        if (tab == SeaPilot.STATION) { sx = wx(mx); sz = wz(my); edit(); WmdUi.click(); return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && button == 1) { vx -= dx * bpp(); vz -= dy * bpp(); if (Math.abs(mx - panMx) + Math.abs(my - panMy) > 3) panMoved = true; return true; }
        if (dragWp >= 0 && dragWp < wps.size() && button == 0 && inMap(mx, my)) { wps.get(dragWp)[0] = wx(mx); wps.get(dragWp)[1] = wz(my); edit(); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && panning) {
            panning = false;
            if (!panMoved && tab == SeaPilot.ROUTE && helm) { int w = nearestWp(mx, my); if (w >= 0) { wps.remove(w); sel = -1; edit(); WmdUi.click(); rebuildWidgets(); } }
            return true;
        }
        dragWp = -1;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxv, double syv) {
        if (inMap(mx, my)) { zoomIdx = Mth.clamp(zoomIdx + (syv > 0 ? 1 : -1), 0, ZOOMS.length - 1); return true; }
        if (tab == SeaPilot.ROUTE) { legScroll = Mth.clamp(legScroll - (int) Math.signum(syv), 0, Math.max(0, wps.size() - 5)); return true; }
        return super.mouseScrolled(mx, my, sxv, syv);
    }

    private void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col, int dash) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int k = 0; k <= n; k++) {
            if (dash > 0 && (k / dash) % 2 == 1) continue;
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * k / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * k / n);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private void circle(GuiGraphics g, int x, int y, double r, int col, boolean dashed) {
        int n = Math.max(16, (int) (r * 0.8));
        for (int k = 0; k < n; k++) {
            if (dashed && k % 2 == 1) continue;
            double t = k * Math.PI * 2.0 / n;
            int px = x + (int) Math.round(Math.cos(t) * r), py = y + (int) Math.round(Math.sin(t) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }

    private static int legColour(int verdict) { return verdict == SeaChart.V_BAD ? WmdUi.BAD : verdict == SeaChart.V_UNKNOWN ? WmdUi.WARN : WmdUi.GOOD; }

    private int verdict(int i) { return 2 * i < lv.length ? lv[2 * i] : SeaChart.V_UNKNOWN; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        WmdUi.panel(g, left, top, W, H);
        boolean on = mode != SeaPilot.OFF;
        Component chip = !helm ? Component.translatable(P + "chip_chart") : on ? Component.translatable(P + "chip_on", Component.translatable(P + "tab_" + SeaPilot.MODES[mode]))
                : why != SeaPilot.R_NONE ? Component.translatable(P + "chip_off", Component.translatable("hud.wmd.sp.why_" + SeaPilot.REASONS[Mth.clamp(why, 0, SeaPilot.REASONS.length - 1)]))
                : Component.translatable(P + "chip_ready");
        WmdUi.header(g, font, left, top, W, ItemStackHolder.icon(), title, chip, !helm ? WmdUi.K_INFO : on ? WmdUi.K_GOOD : why != SeaPilot.R_NONE ? WmdUi.K_WARN : WmdUi.K_IDLE, false);
        map(g);
        profile(g);
        side(g);
        super.render(g, mx, my, pt);
    }

    private void map(GuiGraphics g) {
        int x0 = mapX(), y0 = mapY();
        WmdUi.well(g, x0, y0, MAP_W, MAP_H);
        g.enableScissor(x0, y0, x0 + MAP_W, y0 + MAP_H);
        g.fill(x0, y0, x0 + MAP_W, y0 + MAP_H, ChartGlass.UNKNOWN);
        if (tex != null) {
            int px = sxOf(tx0), py = syOf(tz0), pw = (int) Math.round(tw * tCell / bpp()), ph = (int) Math.round(th * tCell / bpp());
            g.blit(tex, px, py, pw, ph, 0.0F, 0.0F, tw, th, tw, th);
        }
        int step = (int) bpp() * 25;
        int gx = Mth.floor(wx(x0) / step) * step;
        for (double x = gx; sxOf(x) < x0 + MAP_W; x += step) g.fill(sxOf(x), y0, sxOf(x) + 1, y0 + MAP_H, 0x1ADCE3EA);
        int gz = Mth.floor(wz(y0) / step) * step;
        for (double z = gz; syOf(z) < y0 + MAP_H; z += step) g.fill(x0, syOf(z), x0 + MAP_W, syOf(z) + 1, 0x1ADCE3EA);
        for (int[] f : fld) {
            double r = Math.max(3, f[2] / bpp());
            circle(g, sxOf(f[0]), syOf(f[1]), r, 0xFFF2D68C, true);
            g.fill(sxOf(f[0]) - 1, syOf(f[1]) - 1, sxOf(f[0]) + 1, syOf(f[1]) + 1, 0xFFF2D68C);
        }
        for (int i = 0; i + 1 < trk.length; i += 2) g.fill(sxOf(trk[i]), syOf(trk[i + 1]), sxOf(trk[i]) + 1, syOf(trk[i + 1]) + 1, 0xE0FFFFFF);
        if (tab == SeaPilot.ROUTE) {
            int px = sxOf(bx), py = syOf(bz);
            for (int i = 0; i < wps.size(); i++) {
                int qx = sxOf(wps.get(i)[0]), qy = syOf(wps.get(i)[1]), vd = verdict(i);
                line(g, px, py, qx, qy, legColour(vd), vd == SeaChart.V_UNKNOWN ? 3 : 0);
                px = qx; py = qy;
            }
            if (!wps.isEmpty() && end == SeaPilot.END_STATION) { double[] l = wps.get(wps.size() - 1); circle(g, sxOf(l[0]), syOf(l[1]), Math.max(4, sr / bpp()), WmdUi.ORANGE, true); }
            for (int i = 0; i < wps.size(); i++) {
                int qx = sxOf(wps.get(i)[0]), qy = syOf(wps.get(i)[1]);
                g.fill(qx - 4, qy - 4, qx + 5, qy + 5, 0xFF0F1318); g.fill(qx - 3, qy - 3, qx + 4, qy + 4, i == sel ? WmdUi.ORANGE : WmdUi.BLUE);
                g.drawString(font, Integer.toString(i + 1), qx - 2, qy - 13, WmdUi.TEXT, true);
            }
        } else if (tab == SeaPilot.STATION) {
            int cx = sxOf(sx), cy = syOf(sz);
            circle(g, cx, cy, Math.max(4, sr / bpp()), WmdUi.ORANGE, false);
            g.fill(cx - 3, cy, cx + 4, cy + 1, WmdUi.ORANGE); g.fill(cx, cy - 3, cx + 1, cy + 4, WmdUi.ORANGE);
        } else {
            double r = Math.toRadians(hc);
            line(g, sxOf(bx), syOf(bz), sxOf(bx + Math.sin(r) * 4000), syOf(bz - Math.cos(r) * 4000), WmdUi.BLUE_LIGHT, 3);
        }
        for (int[] c : con) {
            int cx = sxOf(c[0]), cy = syOf(c[1]), col = c[2] == 0 ? WmdUi.ORANGE : WmdUi.BAD;
            for (int k = 0; k < 4; k++) g.fill(cx - 3 + k, cy - k, cx + 4 - k, cy + 1 + k, col);
            for (int k = 0; k < 3; k++) g.fill(cx - 2 + k, cy + 1 + k, cx + 3 - k, cy + 2 + k, col);
        }
        double hr = Math.toRadians(hdg);
        int ax = sxOf(bx), ay = syOf(bz);
        line(g, ax, ay, ax + (int) Math.round(Math.sin(hr) * 8), ay - (int) Math.round(Math.cos(hr) * 8), 0xFFFFFFFF, 0);
        g.fill(ax - 2, ay - 2, ax + 3, ay + 3, 0xFFFFFFFF);
        g.disableScissor();
        g.drawString(font, Component.translatable(P + "scale", step), x0 + 3, y0 + MAP_H - 10, WmdUi.TEXT_DIM, true);
    }

    private void profile(GuiGraphics g) {
        int x0 = mapX(), y0 = mapY() + MAP_H + 6, w = MAP_W, h = PRO_H;
        WmdUi.well(g, x0, y0, w, h);
        if (pf.length < 2 || pfL < 1.0) { g.drawString(font, Component.translatable(P + "no_profile"), x0 + 4, y0 + h / 2 - 4, WmdUi.TEXT_DIM, false); return; }
        double maxD = 20;
        for (int d : pf) maxD = Math.max(maxD, d);
        for (double[] wp : wps) maxD = Math.max(maxD, wp[3] + keel + marEff);
        maxD = Math.min(maxD * 1.08, 300);
        int top = y0 + 8, bot = y0 + h - 2, n = pf.length;
        g.fill(x0, top, x0 + w, bot, 0xFF13304F);
        g.fill(x0, top, x0 + w, top + 1, 0xFF9FC3E6);
        for (int k = 0; k < n; k++) {
            int xa = x0 + k * w / n, xb = x0 + (k + 1) * w / n, d = pf[k];
            if (d < 0) { g.fill(xa, top, xb, bot, (k % 2 == 0) ? 0xFF1C222A : 0xFF2C343E); continue; }
            int ys = d == 0 ? top : top + (int) Math.round(Math.min(1.0, d / maxD) * (bot - top));
            g.fill(xa, ys, xb, bot, 0xFF6B5A3C);
            g.fill(xa, ys, xb, ys + 1, 0xFFC9A86A);
        }
        double prev = 0;
        for (int i = 0; i < pfB.length; i++) {
            int xa = x0 + (int) Math.round(prev / pfL * w), xb = x0 + (int) Math.round(pfB[i] / pfL * w);
            double d = tab == SeaPilot.ROUTE && i < wps.size() ? (sub ? wps.get(i)[3] : 0) : sub ? (tab == SeaPilot.STATION ? sd : hd) : 0;
            int col = tab == SeaPilot.ROUTE ? legColour(verdict(i)) : WmdUi.BLUE_LIGHT;
            int yd = top + (int) Math.round(Math.min(1.0, (d + (sub ? 0 : keel)) / maxD) * (bot - top));
            g.fill(xa, yd, xb, yd + 1, col);
            int yn = top + (int) Math.round(Math.min(1.0, (d + keel + marEff) / maxD) * (bot - top));
            for (int x = xa; x < xb; x += 4) g.fill(x, yn, Math.min(xb, x + 2), yn + 1, 0xC0DCE3EA);
            if (tab == SeaPilot.ROUTE) g.fill(xb - 1, top, xb, bot, 0x60DCE3EA);
            prev = pfB[i];
        }
        if (sub && crush > 0 && Balance.SP_CRUSH_CAP * crush < maxD) {
            int yc = top + (int) Math.round(Balance.SP_CRUSH_CAP * crush / maxD * (bot - top));
            for (int x = x0; x < x0 + w; x += 5) g.fill(x, yc, Math.min(x0 + w, x + 3), yc + 1, WmdUi.BAD);
        }
        g.fill(x0 + 1, top + (int) Math.round(Math.min(1.0, Math.max(0, dep) / maxD) * (bot - top)) - 1, x0 + 6, top + (int) Math.round(Math.min(1.0, Math.max(0, dep) / maxD) * (bot - top)) + 2, 0xFFFFFFFF);
        g.drawString(font, Component.translatable(tab == SeaPilot.ROUTE ? P + "pro_route" : tab == SeaPilot.STATION ? P + "pro_station" : P + "pro_hold"), x0 + 3, y0 + 1, WmdUi.TEXT_DIM, false);
        String deepest = Integer.toString((int) Math.round(maxD));
        g.drawString(font, deepest, x0 + w - 3 - font.width(deepest), bot - 9, WmdUi.TEXT_DIM, false);
    }

    private void side(GuiGraphics g) {
        int x = sideX(), y = top + 42;
        String[] speeds = { "speed0", "speed1", "speed2", "speed3" };
        if (!helm) {
            WmdUi.drawFit(g, font, Component.translatable(craft ? P + "no_helm" : P + "ashore"), x, y + 2, SIDE_W, WmdUi.WARN);
            WmdUi.drawFit(g, font, Component.translatable(P + "chart_only"), x, y + 14, SIDE_W, WmdUi.TEXT_DIM);
        } else switch (tab) {
            case SeaPilot.HOLD -> {
                value(g, x, y + 4, "course", String.format("%03d°", Math.round(hc) % 360));
                value(g, x, y + 20, "speed", Component.translatable(P + speeds[Mth.clamp(hs, 0, 3)]).getString());
                if (sub) value(g, x, y + 36, "depth", Integer.toString(hd));
            }
            case SeaPilot.ROUTE -> {
                WmdUi.well(g, x, y, SIDE_W, 64);
                g.drawString(font, Component.translatable(P + "leg_head", sub ? "DEPTH" : ""), x + 3, y + 2, WmdUi.TEXT_DIM, false);
                if (wps.isEmpty()) g.drawString(font, Component.translatable(P + "no_wps"), x + 3, y + 14, WmdUi.TEXT_DIM, false);
                double px = bx, pz = bz;
                for (int i = 0; i < wps.size(); i++) {
                    double[] w = wps.get(i);
                    double d = Math.hypot(w[0] - px, w[1] - pz); px = w[0]; pz = w[1];
                    int r = i - legScroll;
                    if (r < 0 || r >= 5) continue;
                    int ry = y + 12 + r * 10;
                    if (i == sel) { g.fill(x + 1, ry - 1, x + SIDE_W - 1, ry + 9, 0xFF2D3A4A); g.fill(x + 1, ry - 1, x + 2, ry + 9, WmdUi.BLUE); }
                    String sp = Component.translatable(P + speeds[Mth.clamp((int) w[2], 0, 3)]).getString();
                    g.drawString(font, String.format("%-2d %5s %-5s %s", i + 1, ClientSeaPilot.dist((float) d), sp, sub ? Integer.toString((int) w[3]) : ""), x + 3, ry, WmdUi.TEXT, false);
                    int vd = verdict(i);
                    String c = vd == SeaChart.V_BAD ? "!" : vd == SeaChart.V_UNKNOWN ? "?" : "ok";
                    g.drawString(font, c, x + SIDE_W - 4 - font.width(c), ry, legColour(vd), false);
                }
                if (sel >= 0 && sel < wps.size()) {
                    value(g, x, y + 68, "leg_speed", Component.translatable(P + speeds[Mth.clamp((int) wps.get(sel)[2], 0, 3)]).getString());
                    if (sub) value(g, x, y + 84, "leg_depth", Integer.toString((int) wps.get(sel)[3]));
                }
            }
            default -> {
                WmdUi.drawFit(g, font, Component.translatable(P + "station_how"), x, y + 4, SIDE_W, WmdUi.TEXT_DIM);
                value(g, x, y + 20, "radius", Integer.toString(sr));
                if (sub) value(g, x, y + 36, "depth", Integer.toString(sd));
            }
        }
        value(g, x, top + 164, "margin", mar < 0 ? Component.translatable(P + "auto", marEff).getString() : Integer.toString(mar));
        WmdUi.divider(g, x, top + 181, SIDE_W);
        Component check;
        int col;
        int bad = -1, unknown = -1;
        for (int i = 0; i < wps.size(); i++) { int vd = verdict(i); if (vd == SeaChart.V_BAD && bad < 0) bad = i; if (vd == SeaChart.V_UNKNOWN && unknown < 0) unknown = i; }
        if (!helm) { check = Component.translatable(P + "check_chart"); col = WmdUi.TEXT_DIM; }
        else if (!power) { check = Component.translatable(P + "check_power"); col = WmdUi.BAD; }
        else if (tab == SeaPilot.ROUTE && bad >= 0) { check = Component.translatable(P + "check_bad", bad + 1, 2 * bad + 1 < lv.length && lv[2 * bad + 1] >= 0 ? lv[2 * bad + 1] : 0); col = WmdUi.BAD; }
        else if (tab == SeaPilot.ROUTE && unknown >= 0) { check = Component.translatable(P + "check_unknown", unknown + 1); col = WmdUi.WARN; }
        else { check = sub && crush > 0 ? Component.translatable(P + "check_ok_sub", marEff, (int) Math.floor(Balance.SP_CRUSH_CAP * crush)) : Component.translatable(P + "check_ok", marEff); col = WmdUi.GOOD; }
        WmdUi.drawFit(g, font, check, x, top + 186, SIDE_W, col);
        if (ticks - msgAt < 160) WmdUi.drawFit(g, font, msg, x, top + 196, SIDE_W, WmdUi.TEXT_MUTED);
        else WmdUi.drawFit(g, font, Component.translatable(P + "letgo"), x, top + 196, SIDE_W, WmdUi.TEXT_DIM);
    }

    private void value(GuiGraphics g, int sx0, int y, String key, String v) {
        g.drawString(font, Component.translatable(P + key), sx0, y + 3, WmdUi.TEXT_MUTED, false);
        g.drawString(font, v, sx0 + SIDE_W - 34 - font.width(v), y + 3, WmdUi.TEXT, false);
    }

    private static final class ItemStackHolder {
        static net.minecraft.world.item.ItemStack icon() {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chart_table"));
            return new net.minecraft.world.item.ItemStack(item);
        }
    }
}
