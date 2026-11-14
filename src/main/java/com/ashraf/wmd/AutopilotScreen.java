package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

public class AutopilotScreen extends Screen {

    private static final int W = 420, H = 240, MAP_W = 240, MAP_H = 190, SIDE_W = 156, MAP_R = 512, MAP_STEP = 4, SEND_GAP = 10;
    private static final String P = "gui.wmd.ap.";
    private static final int[] ZOOMS = { 16, 8, 4, 2, 1 };
    private final BlockPos cockpit;
    private int left, top, tab = Autopilot.ROUTE, zoomIdx = 2, sel = -1, dragWp = -1, ticks, lastSent = -100, legScroll;
    private boolean dirty, panning, panMoved;
    private double vx, vz, panMx, panMy;

    private float hh, ha = 120, hs = 55, or = 120, oa = 140, os = 50;
    private double ox, oz;
    private int end, side, floor = Balance.AP_FLOOR, land = Autopilot.LAND_FULL;
    private final List<double[]> wps = new ArrayList<>();

    private double cx, cy, cz, chdg, cv, stall = -1, min = 35, cap = 90, hx = Double.NaN, hy, hz, hhd;
    private float fuel = -1;
    private int mode;
    private boolean hasHome;

    private double hlen = 60.0, markX = Double.NaN, markZ = Double.NaN, markEx, markEz;
    private boolean hBeacon;
    private ResourceLocation tex;
    private DynamicTexture dyn;
    private int texX0, texZ0;

    public AutopilotScreen(BlockPos cockpit) {
        super(Component.translatable(P + "title"));
        this.cockpit = cockpit;
        CompoundTag t = ClientAutopilotBridge.state(cockpit);
        if (t != null) { read(t, true); vx = cx; vz = cz; if (mode >= Autopilot.HOLD && mode <= Autopilot.HOME) tab = mode; }
    }

    private void read(CompoundTag t, boolean settings) {
        cx = t.getDouble("x"); cy = t.getDouble("y"); cz = t.getDouble("z"); chdg = t.getDouble("hdg"); cv = t.getDouble("v");
        stall = t.getDouble("stall"); min = t.getDouble("min"); cap = t.getDouble("cap"); fuel = t.getFloat("fuel"); mode = t.getInt("mode");
        hasHome = t.contains("hx");
        if (hasHome) { hx = t.getDouble("hx"); hy = t.getDouble("hy"); hz = t.getDouble("hz"); hhd = t.getDouble("hh"); hlen = t.contains("hl") ? t.getDouble("hl") : 60.0; hBeacon = t.getBoolean("hb"); }
        if (!settings) return;
        hh = t.getFloat("hh"); ha = t.getFloat("ha"); hs = t.getFloat("hs");
        wps.clear();
        ListTag l = t.getList("rt", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) { CompoundTag w = l.getCompound(i); wps.add(new double[] { w.getDouble("x"), w.getDouble("z"), w.getFloat("a"), w.getFloat("s") }); }
        end = t.getInt("end"); ox = t.getDouble("ox"); oz = t.getDouble("oz"); or = t.getFloat("or"); oa = t.getFloat("oa"); os = t.getFloat("os");
        side = t.getInt("side"); floor = t.getInt("floor"); land = t.getInt("land");
        if (ox == 0.0 && oz == 0.0) { ox = cx; oz = cz; }
    }

    private CompoundTag settings() {
        CompoundTag t = new CompoundTag();
        t.putFloat("hh", hh); t.putFloat("ha", ha); t.putFloat("hs", hs);
        ListTag l = new ListTag();
        for (double[] w : wps) { CompoundTag c = new CompoundTag(); c.putDouble("x", w[0]); c.putDouble("z", w[1]); c.putFloat("a", (float) w[2]); c.putFloat("s", (float) w[3]); l.add(c); }
        t.put("rt", l);
        t.putInt("end", end); t.putDouble("ox", ox); t.putDouble("oz", oz); t.putFloat("or", or); t.putFloat("oa", oa); t.putFloat("os", os);
        t.putInt("side", side); t.putInt("floor", floor); t.putInt("land", land);
        return t;
    }

    private void edit() { dirty = true; }

    private void home(int op, CompoundTag data) {
        PacketDistributor.sendToServer(new ApPayloads.Ask(cockpit, op, 0, data));
        lastSent = ticks;
    }

    private void send(int op, int arg) {
        PacketDistributor.sendToServer(new ApPayloads.Ask(cockpit, op, arg, op == ApPayloads.SET || op == ApPayloads.ENGAGE ? settings() : new CompoundTag()));
        if (op == ApPayloads.SET || op == ApPayloads.ENGAGE) dirty = false;
        lastSent = ticks;
    }

    private void onState() {
        CompoundTag t = ClientAutopilotBridge.state(cockpit);
        if (t != null) { read(t, false); rebuildWidgets(); }
    }

    @Override
    protected void init() {
        left = (width - W) / 2; top = (height - H) / 2;
        ClientAutopilotBridge.onStateHook = () -> Minecraft.getInstance().execute(this::onState);
        if (tex == null) sample();
        int sx = left + W - SIDE_W - 6, y = top + 24;
        String[] tabs = { "hold", "route", "orbit", "home" };
        int tw = (SIDE_W - 6) / 4;
        for (int i = 0; i < 4; i++) {
            final int m = Autopilot.HOLD + i;
            addRenderableWidget(new WmdUi.KitButton(sx + i * (tw + 2), y, tw, 14, Component.translatable(P + "tab_" + tabs[i]), b -> { tab = m; WmdUi.tab(); rebuildWidgets(); }));
        }
        y += 22;
        switch (tab) {
            case Autopilot.HOLD -> {
                row(sx, y, () -> hh = (hh + 355) % 360, () -> hh = (hh + 5) % 360); y += 16;
                row(sx, y, () -> ha = Math.max(-32, ha - 5), () -> ha = Math.min(600, ha + 5)); y += 16;
                row(sx, y, () -> hs = Math.max(10, hs - 5), () -> hs = Math.min((float) cap, hs + 5)); y += 16;
                addRenderableWidget(new WmdUi.KitButton(sx, y, SIDE_W, 14, Component.translatable(P + "set_now"), b -> { hh = (float) chdg; ha = (float) Math.round(cy); hs = (float) Math.round(cv); edit(); }));
                y += 18;
            }
            case Autopilot.ROUTE -> {
                y += 70;
                if (sel >= 0 && sel < wps.size()) {
                    final double[] w = wps.get(sel);
                    row(sx, y, () -> w[2] = Math.max(-32, w[2] - 5), () -> w[2] = Math.min(600, w[2] + 5)); y += 16;
                    row(sx, y, () -> w[3] = Math.max(10, w[3] - 5), () -> w[3] = Math.min(cap, w[3] + 5)); y += 16;
                } else y += 32;
                int bw = (SIDE_W - 4) / 3;
                addRenderableWidget(new WmdUi.KitButton(sx, y, bw, 14, Component.translatable(P + "end" + end), b -> { end = (end + 1) % 3; edit(); rebuildWidgets(); }));
                addRenderableWidget(new WmdUi.KitButton(sx + bw + 2, y, bw, 14, Component.translatable(P + "reverse"), b -> { java.util.Collections.reverse(wps); sel = -1; edit(); rebuildWidgets(); }));
                addRenderableWidget(new WmdUi.KitButton(sx + 2 * (bw + 2), y, bw, 14, Component.translatable(P + "clear"), b -> { wps.clear(); sel = -1; edit(); rebuildWidgets(); }));
                y += 18;
            }
            case Autopilot.ORBIT -> {
                y += 12;
                row(sx, y, () -> or = (float) Math.max(Balance.AP_ORBIT_R_MIN, or - 10), () -> or = (float) Math.min(Balance.AP_ORBIT_R_MAX, or + 10)); y += 16;
                row(sx, y, () -> oa = Math.max(-32, oa - 5), () -> oa = Math.min(600, oa + 5)); y += 16;
                row(sx, y, () -> os = Math.max(10, os - 5), () -> os = Math.min((float) cap, os + 5)); y += 16;
                addRenderableWidget(new WmdUi.KitButton(sx, y, SIDE_W, 14, Component.translatable(P + "side" + side), b -> { side = (side + 1) % 3; edit(); rebuildWidgets(); }));
                y += 18;
            }
            default -> {
                y += 34;
                addRenderableWidget(new WmdUi.KitButton(sx, y, SIDE_W, 14, Component.translatable(P + "land" + land), b -> { land = (land + 1) % 3; edit(); rebuildWidgets(); }));
                y += 18;
                int bw = (SIDE_W - 2) / 2;
                addRenderableWidget(new WmdUi.KitButton(sx, y, bw, 14, Component.translatable(P + "home_here"), b -> { home(ApPayloads.HOME_HERE, new CompoundTag()); WmdUi.click(); }));
                addRenderableWidget(new WmdUi.KitButton(sx + bw + 2, y, bw, 14, Component.translatable(P + "home_clear"), b -> { home(ApPayloads.HOME_CLEAR, new CompoundTag()); WmdUi.click(); }));
                y += 18;
            }
        }
        row(sx, top + H - 74, () -> floor = Math.max(Balance.AP_FLOOR_MIN, floor - 4), () -> floor = Math.min(Balance.AP_FLOOR_MAX, floor + 4));
        boolean flyingThis = mode == tab || (tab == Autopilot.HOME && mode == Autopilot.PARKED);
        if (flyingThis) addRenderableWidget(new WmdUi.KitButton(sx, top + H - 26, SIDE_W, 18, Component.translatable(P + "disengage"), b -> { send(ApPayloads.OFF, 0); WmdUi.click(); }));
        else addRenderableWidget(new WmdUi.KitButton(sx, top + H - 26, SIDE_W, 18, Component.translatable(P + "engage"), b -> { send(ApPayloads.ENGAGE, tab); WmdUi.confirm(); }, true));
    }

    private void row(int sx, int y, Runnable minus, Runnable plus) {
        addRenderableWidget(new WmdUi.KitButton(sx + SIDE_W - 30, y, 14, 14, Component.literal("-"), b -> { minus.run(); edit(); }));
        addRenderableWidget(new WmdUi.KitButton(sx + SIDE_W - 14, y, 14, 14, Component.literal("+"), b -> { plus.run(); edit(); }));
    }

    @Override
    public void tick() {
        ticks++;
        if (dirty && ticks - lastSent >= SEND_GAP) send(ApPayloads.SET, 0);
        else if (ticks % 20 == 0 && ticks - lastSent >= 5) { PacketDistributor.sendToServer(new ApPayloads.Ask(cockpit, ApPayloads.OPEN, 0, new CompoundTag())); lastSent = ticks; }
        if (ClientCockpit.seated() == null) onClose();
    }

    @Override
    public void onClose() {
        if (dirty) send(ApPayloads.SET, 0);
        ClientAutopilotBridge.onStateHook = null;
        if (dyn != null) { dyn.close(); dyn = null; }
        ClientAutopilot.back();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    private void sample() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        int n = 2 * MAP_R / MAP_STEP;
        NativeImage img = new NativeImage(n, n, false);
        texX0 = (int) Math.floor(cx) - MAP_R; texZ0 = (int) Math.floor(cz) - MAP_R;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            int prevH = Integer.MIN_VALUE;
            for (int j = 0; j < n; j++) {
                int x = texX0 + i * MAP_STEP, z = texZ0 + j * MAP_STEP, argb;
                if (!level.hasChunk(x >> 4, z >> 4)) argb = 0xFF101418;
                else {
                    int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    mp.set(x, h, z);
                    BlockState st = level.getBlockState(mp);
                    MapColor mc = st.getMapColor(level, mp);
                    int rgb = mc == null ? 0x303030 : mc.col;
                    double k = prevH == Integer.MIN_VALUE || h == prevH ? 0.86 : h > prevH ? 1.0 : 0.70;
                    if (!st.getFluidState().isEmpty()) k = 0.80;
                    prevH = h;
                    int r = (int) (((rgb >> 16) & 255) * k * 0.8), g = (int) (((rgb >> 8) & 255) * k * 0.8), b = (int) ((rgb & 255) * k * 0.8);
                    argb = 0xFF000000 | r << 16 | g << 8 | b;
                }
                img.setPixelRGBA(i, j, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF);
            }
        }
        dyn = new DynamicTexture(img);
        tex = Minecraft.getInstance().getTextureManager().register("wmd_ap_map", dyn);
    }

    private int mapX() { return left + 6; }
    private int mapY() { return top + 24; }
    private double bpp() { return ZOOMS[zoomIdx]; }
    private int sx(double x) { return (int) Math.round(mapX() + MAP_W / 2.0 + (x - vx) / bpp()); }
    private int sy(double z) { return (int) Math.round(mapY() + MAP_H / 2.0 + (z - vz) / bpp()); }
    private double wx(double mx) { return vx + (mx - mapX() - MAP_W / 2.0) * bpp(); }
    private double wz(double my) { return vz + (my - mapY() - MAP_H / 2.0) * bpp(); }
    private boolean inMap(double mx, double my) { return WmdUi.over(mx, my, mapX(), mapY(), MAP_W, MAP_H); }

    private int nearestWp(double mx, double my) {
        int best = -1; double bd = 36;
        for (int i = 0; i < wps.size(); i++) { double dx = sx(wps.get(i)[0]) - mx, dy = sy(wps.get(i)[1]) - my, d = dx * dx + dy * dy; if (d < bd) { bd = d; best = i; } }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == Autopilot.ROUTE && WmdUi.over(mx, my, left + W - SIDE_W - 6, top + 46, SIDE_W, 66)) {
            int i = legScroll + (int) ((my - top - 58) / 10);
            if (my >= top + 58 && i >= 0 && i < wps.size()) { sel = i; WmdUi.click(); rebuildWidgets(); }
            return true;
        }
        if (!inMap(mx, my)) return false;
        if (button == 1) { panning = true; panMoved = false; panMx = mx; panMy = my; return true; }
        if (button != 0) return false;
        if (tab == Autopilot.ROUTE) {
            int w = nearestWp(mx, my);
            if (w >= 0) { sel = w; dragWp = w; }
            else if (wps.size() < Balance.AP_WP_MAX) {
                double[] last = sel >= 0 && sel < wps.size() ? wps.get(sel) : wps.isEmpty() ? null : wps.get(wps.size() - 1);
                wps.add(new double[] { wx(mx), wz(my), last == null ? Math.round(Math.max(cy, 80)) : last[2], last == null ? Math.round(Math.max(cv, min * 1.2)) : last[3] });
                sel = wps.size() - 1; dragWp = sel; edit();
            } else WmdUi.deny();
            WmdUi.click(); rebuildWidgets();
            return true;
        }
        if (tab == Autopilot.ORBIT) { ox = wx(mx); oz = wz(my); edit(); WmdUi.click(); return true; }
        if (tab == Autopilot.HOME) { markX = wx(mx); markZ = wz(my); markEx = markX; markEz = markZ; return true; }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && button == 1) { vx -= dx * bpp(); vz -= dy * bpp(); if (Math.abs(mx - panMx) + Math.abs(my - panMy) > 3) panMoved = true; return true; }
        if (dragWp >= 0 && dragWp < wps.size() && button == 0 && inMap(mx, my)) { wps.get(dragWp)[0] = wx(mx); wps.get(dragWp)[1] = wz(my); edit(); return true; }
        if (!Double.isNaN(markX) && button == 0) { markEx = wx(mx); markEz = wz(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1 && panning) {
            panning = false;
            if (!panMoved && tab == Autopilot.ROUTE) { int w = nearestWp(mx, my); if (w >= 0) { wps.remove(w); sel = -1; edit(); WmdUi.click(); rebuildWidgets(); } }
            return true;
        }
        dragWp = -1;
        if (!Double.isNaN(markX) && button == 0) {
            double dx = markEx - markX, dz = markEz - markZ, len = Math.hypot(dx, dz);
            if (len >= Balance.AL_STRIP_MIN) {
                CompoundTag d = new CompoundTag();
                d.putDouble("x", markX); d.putDouble("z", markZ); d.putDouble("hdg", (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0); d.putDouble("len", len);
                home(ApPayloads.HOME_MAP, d);
                WmdUi.confirm();
            } else WmdUi.deny();
            markX = Double.NaN;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxv, double syv) {
        if (inMap(mx, my)) { zoomIdx = Mth.clamp(zoomIdx + (syv > 0 ? 1 : -1), 0, ZOOMS.length - 1); return true; }
        if (tab == Autopilot.ROUTE) { legScroll = Mth.clamp(legScroll - (int) Math.signum(syv), 0, Math.max(0, wps.size() - 5)); return true; }
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
            g.fill(x + (int) Math.round(Math.cos(t) * r), y + (int) Math.round(Math.sin(t) * r), x + (int) Math.round(Math.cos(t) * r) + 1, y + (int) Math.round(Math.sin(t) * r) + 1, col);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        WmdUi.panel(g, left, top, W, H);
        boolean flying = mode != Autopilot.OFF && mode != Autopilot.PARKED;
        String[] modes = { "off", "hold", "route", "orbit", "home", "parked" };
        WmdUi.header(g, font, left, top, W, ItemStackHolder.icon(), title, Component.translatable(P + "chip_" + modes[Mth.clamp(mode, 0, 5)]),
                flying ? WmdUi.K_GOOD : mode == Autopilot.PARKED ? WmdUi.K_INFO : WmdUi.K_IDLE, false);

        int x0 = mapX(), y0 = mapY();
        WmdUi.well(g, x0, y0, MAP_W, MAP_H);
        g.enableScissor(x0, y0, x0 + MAP_W, y0 + MAP_H);
        if (tex != null) {
            int span = 2 * MAP_R, px = sx(texX0), py = sy(texZ0), ps = (int) Math.round(span / bpp()), n = span / MAP_STEP;
            g.blit(tex, px, py, ps, ps, 0.0F, 0.0F, n, n, n, n);
        }
        if (!Double.isNaN(markX)) {
            int ax = sx(markX), ay = sy(markZ), bx = sx(markEx), by = sy(markEz);
            double ml = Math.hypot(markEx - markX, markEz - markZ);
            line(g, ax, ay, bx, by, ml >= Balance.AL_STRIP_MIN ? WmdUi.ORANGE : WmdUi.BAD, 0);
            g.fill(ax - 2, ay - 2, ax + 3, ay + 3, WmdUi.ORANGE);
            g.drawString(font, Math.round(ml) + "", bx + 4, by - 4, ml >= Balance.AL_STRIP_MIN ? WmdUi.ORANGE : WmdUi.BAD, false);
        }
        if (hasHome) {
            double r = Math.toRadians(hhd), hl = hBeacon ? 60.0 : hlen;
            int hx0 = sx(hx), hy0 = sy(hz), hx1 = sx(hx + Math.sin(r) * hl), hy1 = sy(hz - Math.cos(r) * hl);
            line(g, hx0, hy0, hx1, hy1, 0xFF8FE3A8, 0);
            g.fill(hx0 - 2, hy0 - 2, hx0 + 3, hy0 + 3, 0xFF8FE3A8);
        }
        if (tab == Autopilot.ROUTE) {
            int px = sx(cx), py = sy(cz);
            for (int i = 0; i < wps.size(); i++) { int qx = sx(wps.get(i)[0]), qy = sy(wps.get(i)[1]); line(g, px, py, qx, qy, WmdUi.BLUE_LIGHT, 0); px = qx; py = qy; }
            if (!wps.isEmpty() && end == Autopilot.END_ORBIT) { double[] l = wps.get(wps.size() - 1); circle(g, sx(l[0]), sy(l[1]), or / bpp(), WmdUi.ORANGE, true); }
            for (int i = 0; i < wps.size(); i++) {
                int qx = sx(wps.get(i)[0]), qy = sy(wps.get(i)[1]);
                g.fill(qx - 4, qy - 4, qx + 5, qy + 5, 0xFF0F1318); g.fill(qx - 3, qy - 3, qx + 4, qy + 4, i == sel ? WmdUi.ORANGE : WmdUi.BLUE);
                g.drawString(font, Integer.toString(i + 1), qx - 2, qy - 13, WmdUi.TEXT, true);
            }
        } else if (tab == Autopilot.ORBIT) {
            circle(g, sx(ox), sy(oz), or / bpp(), WmdUi.ORANGE, false);
            g.fill(sx(ox) - 3, sy(oz), sx(ox) + 4, sy(oz) + 1, WmdUi.ORANGE); g.fill(sx(ox), sy(oz) - 3, sx(ox) + 1, sy(oz) + 4, WmdUi.ORANGE);
        } else if (tab == Autopilot.HOLD) {
            double r = Math.toRadians(hh);
            line(g, sx(cx), sy(cz), sx(cx + Math.sin(r) * 2000), sy(cz - Math.cos(r) * 2000), WmdUi.BLUE_LIGHT, 3);
        }

        double hr = Math.toRadians(chdg);
        int ax = sx(cx), ay = sy(cz);
        line(g, ax, ay, ax + (int) Math.round(Math.sin(hr) * 8), ay - (int) Math.round(Math.cos(hr) * 8), 0xFFFFFFFF, 0);
        g.fill(ax - 2, ay - 2, ax + 3, ay + 3, 0xFFFFFFFF);
        g.disableScissor();
        g.drawString(font, Component.translatable(P + (tab == Autopilot.ROUTE ? "hint_route" : tab == Autopilot.ORBIT ? "hint_orbit" : "hint_map"), ZOOMS[zoomIdx] * MAP_W),
                x0, y0 + MAP_H + 4, WmdUi.TEXT_DIM, false);

        int sx = left + W - SIDE_W - 6, y = top + 46;
        switch (tab) {
            case Autopilot.HOLD -> {
                value(g, sx, y, "heading", Math.round(hh) + "°"); y += 16;
                value(g, sx, y, "altitude", Math.round(ha) + " Y"); y += 16;
                value(g, sx, y, "speed", Math.round(hs) + " b/s"); y += 16;
            }
            case Autopilot.ROUTE -> {
                WmdUi.well(g, sx, y, SIDE_W, 64);
                g.drawString(font, "WP   ALT   SPD   LEG", sx + 3, y + 2, WmdUi.TEXT_DIM, false);
                if (wps.isEmpty()) g.drawString(font, Component.translatable(P + "no_wps"), sx + 3, y + 14, WmdUi.TEXT_DIM, false);
                double px = cx, pz = cz;
                for (int i = 0; i < wps.size(); i++) {
                    double[] w = wps.get(i);
                    double d = Math.hypot(w[0] - px, w[1] - pz); px = w[0]; pz = w[1];
                    int r = i - legScroll;
                    if (r < 0 || r >= 5) continue;
                    int ry = y + 12 + r * 10;
                    if (i == sel) { g.fill(sx + 1, ry - 1, sx + SIDE_W - 1, ry + 9, 0xFF2D3A4A); g.fill(sx + 1, ry - 1, sx + 2, ry + 9, WmdUi.BLUE); }
                    g.drawString(font, String.format("%-4d %-5d %-5d %s", i + 1, Math.round(w[2]), Math.round(w[3]), d >= 1000 ? String.format("%.1fk", d / 1000) : Long.toString(Math.round(d))), sx + 3, ry, WmdUi.TEXT, false);
                }
                y += 70;
                if (sel >= 0 && sel < wps.size()) { value(g, sx, y, "wp_alt", Math.round(wps.get(sel)[2]) + " Y"); value(g, sx, y + 16, "wp_spd", Math.round(wps.get(sel)[3]) + " b/s"); }
            }
            case Autopilot.ORBIT -> {
                g.drawString(font, Component.translatable(P + "centre", Math.round(ox), Math.round(oz)), sx, y + 2, WmdUi.TEXT, false); y += 12;
                value(g, sx, y, "radius", Math.round(or) + ""); y += 16;
                value(g, sx, y, "altitude", Math.round(oa) + " Y"); y += 16;
                value(g, sx, y, "speed", Math.round(os) + " b/s"); y += 34;
                double slant = Math.hypot(or, oa - Math.max(cy - 120, 63));
                g.drawString(font, Component.translatable(P + "slant", Math.round(slant), Math.round(Balance.GTUR_RANGE)), sx, y, slant <= Balance.GTUR_RANGE ? WmdUi.GOOD : WmdUi.WARN, false);
            }
            default -> {
                WmdUi.drawFit(g, font, hasHome ? Component.translatable(P + "home_at", Math.round(hx), Math.round(hy), Math.round(hz), Math.round(hhd)) : Component.translatable(P + "no_home"), sx, y + 2, SIDE_W, hasHome ? WmdUi.GOOD : WmdUi.WARN);
                WmdUi.drawFit(g, font, Component.translatable(P + "home_how"), sx, y + 14, SIDE_W, WmdUi.TEXT_DIM);
            }
        }
        value(g, sx, top + H - 74, "floor", floor + "");
        WmdUi.divider(g, sx, top + H - 56, SIDE_W);
        WmdUi.drawFit(g, font, Component.translatable(P + "check", stall > 0 ? Long.toString(Math.round(stall)) : "?", Math.round(min), Math.round(cap), fuel < 0 ? "-" : Math.round(fuel * 100) + "%"), sx, top + H - 50, SIDE_W, WmdUi.TEXT_DIM);
        WmdUi.drawFit(g, font, Component.translatable(P + "letgo"), sx, top + H - 40, SIDE_W, WmdUi.TEXT_DIM);
        g.drawString(font, Component.translatable(P + "esc"), left + 6, top + H - 10, WmdUi.TEXT_DIM, false);
        super.render(g, mx, my, pt);
    }

    private void value(GuiGraphics g, int sx, int y, String key, String v) {
        g.drawString(font, Component.translatable(P + key), sx, y + 3, WmdUi.TEXT_MUTED, false);
        g.drawString(font, v, sx + SIDE_W - 34 - font.width(v), y + 3, WmdUi.TEXT, false);
    }

    private static final class ItemStackHolder {
        static net.minecraft.world.item.ItemStack icon() {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_cockpit"));
            return new net.minecraft.world.item.ItemStack(item);
        }
    }
}
