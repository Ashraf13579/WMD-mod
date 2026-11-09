package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SonarScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 210, WF_X = 6, WF_Y = 51, WF_W = 160, WF_H = 82;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, BLUE = WmdUi.BLUE_LIGHT, CYAN = WmdUi.CYAN, WARN = WmdUi.WARN;
    private static final String P = "gui.wmd.sonar.";

    public SonarScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private net.minecraft.world.level.block.entity.BlockEntity be() {
        return menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity b ? b : null;
    }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int value) {
        net.minecraft.world.level.block.entity.BlockEntity b = be();
        if (b != null) PacketDistributor.sendToServer(new SonarPayload(b.getBlockPos(), action, value));
    }

    @Override
    protected void init() {
        super.init();
        net.minecraft.world.level.block.entity.BlockEntity b = be();
        if (b == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        java.util.function.IntSupplier freq = b instanceof SonarSensorBlockEntity s ? s::frequency : b instanceof SonarConsoleBlockEntity c ? c::frequency : () -> 1;
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"),
                () -> minecraft.setScreen(new FrequencyScreen(this, freq, f -> send(SonarPayload.FREQ, f))));
        if (b instanceof TowedArrayWinchBlockEntity tw)
            reelButton = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + 61, PW - 12, 14, reelLabel(tw), x -> {
                if (be() instanceof TowedArrayWinchBlockEntity w) send(SonarPayload.REEL, w.stowed() ? 0 : 1);
            }, true));
        if (b instanceof SonarDomeBlockEntity)
            addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + 49, PW - 12, 14, Component.translatable(P + "ping"), x -> send(SonarPayload.PING, 0), true));
        if (b instanceof SonarConsoleBlockEntity con) {
            pingAllButton = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + WF_Y + WF_H + 4, 98, 14, Component.translatable(P + "ping_all"), x -> send(SonarPayload.PING, 0), true));
            surveyButton = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + WF_Y + WF_H + 4, PW - 12, 14, Component.translatable(P + "survey"), x -> send(SonarPayload.SURVEY, 0), true));
            huntButton = addRenderableWidget(new WmdUi.KitButton(px() + 106, py() + WF_Y + WF_H + 4, PW - 112, 14, huntLabel(con), x -> {
                SonarConsoleBlockEntity c = be() instanceof SonarConsoleBlockEntity k ? k : null;
                if (c != null) send(SonarPayload.HUNT, c.hunting() ? 0 : 1);
            }));
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        net.minecraft.world.level.block.entity.BlockEntity b = be();
        if (b == null) return;
        if (huntButton != null && b instanceof SonarConsoleBlockEntity con) huntButton.setMessage(huntLabel(con));
        if (reelButton != null && b instanceof TowedArrayWinchBlockEntity tw) reelButton.setMessage(reelLabel(tw));
        if (surveyButton != null) {
            boolean sea = view == V_SEABED, hullView = view == V_HULL;
            surveyButton.visible = sea;
            if (pingAllButton != null) pingAllButton.visible = !sea && !hullView;
            if (huntButton != null) huntButton.visible = !sea && !hullView;
        }
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        ItemStack icon = new ItemStack(b.getBlockState().getBlock().asItem());
        Component title = Component.translatable(b.getBlockState().getBlock().getDescriptionId());
        if (b instanceof SonarSensorBlockEntity s) sensor(g, s, x, y, now, icon, title);
        else if (b instanceof SonarConsoleBlockEntity c) console(g, c, x, y, icon, title);
    }

    private static String kind(int k) {
        NoiseNet.Kind[] v = NoiseNet.Kind.values();
        return "gui.wmd.sonar.kind." + (k >= 0 && k < v.length ? v[k].name().toLowerCase(java.util.Locale.ROOT) : "engine");
    }

    private void sensor(GuiGraphics g, SonarSensorBlockEntity s, int x, int y, long now, ItemStack icon, Component title) {
        int cx = x + PW / 2;
        boolean on = s.isRunning(), wet = s.wet();
        List<CompoundTag> rows = s.heardRows();
        String deaf = on && wet ? s.deafKey() : null;
        Component chip = Component.translatable(!on ? P + "offline" : !wet ? P + "dry" : deaf != null ? deaf : rows.isEmpty() ? P + "listening" : P + "contact");
        WmdUi.header(g, font, x, y, PW, icon, title, chip, !on ? WmdUi.K_BAD : !wet || deaf != null ? WmdUi.K_WARN : rows.isEmpty() ? WmdUi.K_GOOD : WmdUi.K_INFO, on && wet && deaf == null && !rows.isEmpty());
        fit(g, Component.translatable(P + "range", String.format("%,d", (int) s.listenRange()), s.frequency()), cx, y + 26, GREY);
        if (s.deep()) {
            WmdUi.led(g, x + 7, y + 38, WmdUi.K_INFO, false);
            fit(g, Component.translatable(s.modeKey()), cx + 6, y + 37, CYAN, PW - 26);
        } else fit(g, Component.translatable(s.modeKey()), cx, y + 37, GREY);
        int top = y + 49;
        Component extra = s.extraLine();
        if (extra != null && !(s instanceof SonarDomeBlockEntity)) { fit(g, extra, cx, y + 49, deaf != null ? WARN : GREY); top = y + 62; }
        if (s instanceof TowedArrayWinchBlockEntity) top = y + 79;
        if (s instanceof SonarDomeBlockEntity d) {
            int cd = d.cooldown(now);
            Component hint = cd > 0 ? Component.translatable(P + "cooldown", (cd + 19) / 20)
                    : d.lastPing() > 0 && now - d.lastPing() < Balance.SONAR_PING_SHOW ? Component.translatable(P + "last_ping", d.lastPingShips(), (Balance.SONAR_PING_SHOW - (now - d.lastPing())) / 20)
                    : Component.translatable(P + "ping_hint", String.format("%,d", (int) Balance.SONAR_PING_RANGE), (int) Balance.SONAR_PING_DB);
            fit(g, hint, cx, y + 66, cd > 0 ? WARN : GREY);
            top = y + 79;
        }
        WmdUi.divider(g, x + 5, top, PW - 10);
        if (rows.isEmpty()) { fit(g, Component.translatable(!on ? P + "none_off" : !wet ? P + "none_dry" : P + "none"), cx, top + 8, GREY); return; }
        for (int i = 0; i < rows.size() && top + 5 + i * 11 < y + PH - 10; i++) {
            CompoundTag c = rows.get(i);
            int ry = top + 5 + i * 11;
            WmdUi.led(g, x + 7, ry + 1, i == 0 ? WmdUi.K_INFO : WmdUi.K_IDLE, false);
            drawLeft(g, Component.translatable(P + "r_heard", Component.translatable(kind(c.getInt("k"))), compass(c.getInt("b") / 10.0), String.format("%.0f", c.getInt("b") / 10.0), loud(c.getInt("l"))), x + 15, ry, PW - 20, i == 0 ? TEXT : GREY);
        }
    }

    static final int V_MAP = 0, V_HISTORY = 1, V_SEABED = 2, V_HULL = 3, VIEWS = 4;
    private static int view = V_MAP;

    static boolean hullView() { return view == V_HULL; }
    private WmdUi.KitButton huntButton, pingAllButton, surveyButton, reelButton;

    private static Component reelLabel(TowedArrayWinchBlockEntity w) {
        if (w.reeling() && !w.stowed()) return Component.translatable(P + "towed_redstone");
        return Component.translatable(w.stowed() ? P + "towed_stream" : P + "towed_reel");
    }

    private static Component huntLabel(SonarConsoleBlockEntity c) { return Component.translatable(c.hunting() ? P + "hunt_btn_on" : P + "hunt_btn_off"); }

    private final List<Object[]> hits = new java.util.ArrayList<>();
    static final int TG_Y = 37, TG_H = 11;

    private void console(GuiGraphics g, SonarConsoleBlockEntity c, int x, int y, ItemStack icon, Component title) {
        if (view == V_SEABED) { seabed(g, c, x, y, icon, title); return; }
        if (view == V_HULL) { hull(g, c, x, y, icon, title); return; }
        int cx = x + PW / 2;
        boolean on = c.isRunning();
        List<CompoundTag> rows = c.contactRows(), fixes = threats(c.fixRows());
        boolean none = c.domes() + c.arrays() == 0, quiet = rows.isEmpty() && fixes.isEmpty();
        Component chip = !on ? Component.translatable(P + "offline") : none ? Component.translatable(P + "no_sensors") : Component.translatable(P + "contacts", rows.size() + fixes.size());
        WmdUi.header(g, font, x, y, PW, icon, title, chip, !on ? WmdUi.K_BAD : none ? WmdUi.K_WARN : quiet ? WmdUi.K_GOOD : WmdUi.K_INFO, on && !quiet);
        fit(g, Component.translatable(P + "net", c.frequency(), c.domes(), c.arrays()), cx, y + 26, GREY);
        toggle(g, x, y);
        int wx = x + WF_X, wy = y + WF_Y;
        WmdUi.well(g, wx, wy, WF_W, WF_H);
        if (view == V_MAP) map(g, c, rows, fixes, wx, wy);
        else history(g, c, wx, wy);

        int top = y + WF_Y + WF_H + 22;
        WmdUi.divider(g, x + 5, top, PW - 10);
        int ry = top + 4, max = y + PH - 9;
        if (quiet) { fit(g, Component.translatable(!on ? P + "none_off" : none ? P + "none_sensors" : P + "none"), cx, ry + 4, GREY); return; }
        for (int i = 0; i < rows.size(); i++) {
            if (ry > max) return;
            CompoundTag t = rows.get(i);
            boolean fix = t.getBoolean("f");
            String id = String.valueOf(i + 1);
            mark(g, x + 7, ry, id, fix ? CYAN : DIM_CYAN);
            Component what = Component.translatable(kind(t.getInt("k")));
            Component line = fix
                    ? Component.translatable(P + "s_fix", what, dist(c, t.getInt("fx"), t.getInt("fz")), dir(c, t.getInt("fx"), t.getInt("fz")))
                    : Component.translatable(P + "s_brg", what, compass(t.getInt("b") / 10.0));
            drawLeft(g, line, x + 18, ry, PW - 23, fix ? TEXT : GREY);
            hits.add(new Object[] { x + 5, ry - 1, PW - 10, 10, contactTip(t, id) });
            ry += 10;
        }
        for (int i = 0; i < fixes.size(); i++) {
            if (ry > max) return;
            CompoundTag f = fixes.get(i);
            boolean sub = f.getBoolean("s");
            String id = String.valueOf((char) ('A' + Math.min(i, 25)));
            mark(g, x + 7, ry, id, sub ? WARN : BLUE);
            int fx = f.getInt("x"), fz = f.getInt("z");
            String[] lab = f.getString("n").split(":");
            Component line = lab[0].equals("mine") ? Component.translatable(P + "s_mine", f.getInt("d"), dist(c, fx, fz), dir(c, fx, fz))
                    : sub
                    ? Component.translatable(P + "s_sub", f.getInt("d"), dist(c, fx, fz), dir(c, fx, fz))
                    : Component.translatable(P + "s_ship", dist(c, fx, fz), dir(c, fx, fz));
            drawLeft(g, line, x + 18, ry, PW - 23, TEXT);
            hits.add(new Object[] { x + 5, ry - 1, PW - 10, 10, pingTip(f, id) });
            ry += 10;
        }
    }

    private static final int DIM_CYAN = 0xFF5FCFDE, WRECK_GREY = 0xFF8A949E;

    private static boolean wreck(CompoundTag f) { return f.getString("n").startsWith("wreck:"); }

    private static List<CompoundTag> threats(List<CompoundTag> all) { List<CompoundTag> l = new java.util.ArrayList<>(); for (CompoundTag f : all) if (!wreck(f)) l.add(f); return l; }

    private static List<CompoundTag> wrecks(List<CompoundTag> all) { List<CompoundTag> l = new java.util.ArrayList<>(); for (CompoundTag f : all) if (wreck(f)) l.add(f); return l; }

    private final List<int[]> placed = new java.util.ArrayList<>();

    private void label(GuiGraphics g, String s, int mx, int my, int hs, int col) {
        int w = (int) Math.ceil(font.width(s) * 0.6F), h = 5;
        int[][] tries = { { mx + hs + 2, my - 2 }, { mx - hs - 2 - w, my - 2 }, { mx - w / 2, my - hs - 2 - h }, { mx - w / 2, my + hs + 2 } };
        for (int[] t : tries) {
            boolean clear = true;
            for (int[] r : placed) if (t[0] < r[0] + r[2] + 1 && t[0] + w + 1 > r[0] && t[1] < r[1] + r[3] + 1 && t[1] + h + 1 > r[1]) { clear = false; break; }
            if (!clear) continue;
            placed.add(new int[] { t[0], t[1], w, h });
            small(g, s, t[0], t[1], col);
            return;
        }
    }

    private void taken(int x, int y, int hs) { placed.add(new int[] { x - hs, y - hs, hs * 2 + 1, hs * 2 + 1 }); }

    static final int TG_W = (PW - 12 - 2 * (VIEWS - 1)) / VIEWS;

    private void toggle(GuiGraphics g, int x, int y) {
        int w = TG_W;
        for (int k = 0; k < VIEWS; k++) {
            int tx = x + 6 + k * (w + 2), ty = y + TG_Y;
            boolean sel = k == view;
            g.fill(tx, ty, tx + w, ty + TG_H, sel ? WmdUi.CARD : WmdUi.WELL);
            if (sel) g.fill(tx, ty + TG_H - 1, tx + w, ty + TG_H, WmdUi.BLUE);
            Component t = Component.translatable(P + (k == V_MAP ? "tab_map" : k == V_HISTORY ? "tab_history" : k == V_SEABED ? "tab_seabed" : "tab_hull"));
            int tw = font.width(t);
            if (tw <= w - 2) g.drawString(font, t, tx + (w - tw) / 2, ty + 2, sel ? TEXT : GREY, false);
            else {
                float s = (w - 2) / (float) tw;
                g.pose().pushPose();
                g.pose().translate(tx + 1.0F, ty + 2.0F + (1.0F - s) * 4.0F, 0.0F);
                g.pose().scale(s, s, 1.0F);
                g.drawString(font, t, 0, 0, sel ? TEXT : GREY, false);
                g.pose().popPose();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && be() instanceof SonarConsoleBlockEntity) {
            int x = px(), y = py(), w = TG_W;
            for (int k = 0; k < VIEWS; k++)
                if (WmdUi.over(mx, my, x + 6 + k * (w + 2), y + TG_Y, w, TG_H)) {
                    if (view != k) { view = k; WmdUi.tab(); }
                    return true;
                }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void map(GuiGraphics g, SonarConsoleBlockEntity c, List<CompoundTag> rows, List<CompoundTag> fixes, int wx, int wy) {
        int mx = wx + WF_W / 2, my = wy + WF_H / 2, cxw = c.mapX(), czw = c.mapZ();
        List<CompoundTag> sens = c.sensorRows();
        double ext = 40.0;
        for (CompoundTag t : sens) ext = Math.max(ext, Math.hypot(t.getInt("x") - cxw, t.getInt("z") - czw));
        for (CompoundTag t : rows) if (t.getBoolean("f")) ext = Math.max(ext, Math.hypot(t.getInt("fx") - cxw, t.getInt("fz") - czw));
        for (CompoundTag t : fixes) ext = Math.max(ext, Math.hypot(t.getInt("x") - cxw, t.getInt("z") - czw));
        ext *= 1.1;
        double scale = (WF_H / 2.0 - 5.0) / ext;
        int step = 10;
        for (int n : new int[] { 10, 25, 50, 100, 250, 500, 1000, 2500, 5000 }) { step = n; if (n >= ext / 2.0) break; }
        g.enableScissor(wx, wy, wx + WF_W, wy + WF_H);
        placed.clear();
        taken(mx, my, 2);
        for (CompoundTag t : sens) taken(mx + (int) Math.round((t.getInt("x") - cxw) * scale), my + (int) Math.round((t.getInt("z") - czw) * scale), 2);
        for (CompoundTag f : fixes) taken(mx + (int) Math.round((f.getInt("x") - cxw) * scale), my + (int) Math.round((f.getInt("z") - czw) * scale), 2);

        for (int r = step; r * scale < WF_W; r += step) {
            double rp = r * scale;
            int dots = Math.max(24, (int) (rp * 1.2));
            for (int i = 0; i < dots; i++) {
                double a = i * Math.PI * 2.0 / dots;
                int px = mx + (int) Math.round(Math.cos(a) * rp), py = my + (int) Math.round(Math.sin(a) * rp);
                g.fill(px, py, px + 1, py + 1, 0x30FFFFFF);
            }
            small(g, String.valueOf(r), mx + (int) rp + 2, my - 3, WmdUi.TEXT_DIM);
        }
        small(g, "N", mx - 1, wy + 1, GREY);

        for (int i = 0; i < rows.size(); i++) {
            CompoundTag t = rows.get(i);
            if (!t.contains("ex")) continue;
            int sx = mx + (int) Math.round((t.getInt("ex") - cxw) * scale), sy = my + (int) Math.round((t.getInt("ez") - czw) * scale);
            String id = String.valueOf(i + 1);
            if (t.getBoolean("f")) {
                int fx = mx + (int) Math.round((t.getInt("fx") - cxw) * scale), fy = my + (int) Math.round((t.getInt("fz") - czw) * scale);
                line(g, sx, sy, fx, fy, 0x803AC2D5, 0);
                g.fill(fx - 2, fy - 2, fx + 3, fy + 3, CYAN);
                taken(fx, fy, 2);
                label(g, id, fx, fy, 2, CYAN);
                hits.add(new Object[] { fx - 3, fy - 3, 7, 7, contactTip(t, id) });
            } else {
                double b = Math.toRadians(t.getInt("b") / 10.0);
                int ex = sx + (int) Math.round(Math.sin(b) * WF_W * 2), ey = sy - (int) Math.round(Math.cos(b) * WF_W * 2);
                line(g, sx, sy, ex, ey, 0x902C8F9E, 3);
                int lx = sx + (int) Math.round(Math.sin(b) * 18), ly = sy - (int) Math.round(Math.cos(b) * 18);
                label(g, id, lx, ly, 0, DIM_CYAN);
            }
        }

        for (int i = 0; i < fixes.size(); i++) {
            CompoundTag f = fixes.get(i);
            int fx = mx + (int) Math.round((f.getInt("x") - cxw) * scale), fy = my + (int) Math.round((f.getInt("z") - czw) * scale);
            int col = f.getBoolean("s") ? WARN : BLUE;
            g.fill(fx - 2, fy - 2, fx + 2, fy + 2, col);
            String id = String.valueOf((char) ('A' + Math.min(i, 25)));
            label(g, id, fx, fy, 2, col);
            hits.add(new Object[] { fx - 3, fy - 3, 6, 6, pingTip(f, id) });
        }

        for (CompoundTag t : sens) {
            int sx = mx + (int) Math.round((t.getInt("x") - cxw) * scale), sy = my + (int) Math.round((t.getInt("z") - czw) * scale);
            boolean dome = t.getInt("k") == SonarNet.DOME;
            if (dome) { g.fill(sx - 1, sy - 2, sx + 2, sy + 3, BLUE); g.fill(sx - 2, sy - 1, sx + 3, sy + 2, BLUE); }
            else g.fill(sx - 2, sy - 2, sx + 2, sy + 2, BLUE);
            hits.add(new Object[] { sx - 3, sy - 3, 6, 6, List.<Component>of(Component.translatable(P + (dome ? "map_dome" : t.getInt("k") == SonarNet.BUOY ? "map_buoy" : t.getInt("k") == SonarNet.UUV ? "map_uuv" : t.getInt("k") == SonarNet.TOWED ? "map_towed" : t.getInt("k") == SonarNet.NODE ? "map_node" : "map_array"), t.getInt("x"), t.getInt("z"))) });
        }
        g.fill(mx - 1, my - 1, mx + 2, my + 2, TEXT);
        hits.add(new Object[] { mx - 2, my - 2, 5, 5, List.<Component>of(Component.translatable(P + "map_console", cxw, czw)) });
        g.disableScissor();
        small(g, "?", wx + WF_W - 5, wy + 1, WmdUi.TEXT_DIM);
        hits.add(new Object[] { wx + WF_W - 8, wy, 8, 8, List.<Component>of(Component.translatable(P + "map_help1"), Component.translatable(P + "map_help2"), Component.translatable(P + "map_help3")) });
    }

    private static final int SAND = 0xFFD9B26F, SAND_DIM = 0x50D9B26F;

    private void seabed(GuiGraphics g, SonarConsoleBlockEntity c, int x, int y, ItemStack icon, Component title) {
        int cx = x + PW / 2;
        boolean on = c.isRunning();
        List<CompoundTag> fields = c.surveyRows(), wrecks = wrecks(c.fixRows());
        boolean none = c.domes() == 0;
        Component chip = !on ? Component.translatable(P + "offline") : none ? Component.translatable(P + "no_domes") : Component.translatable(P + "fields", fields.size());
        WmdUi.header(g, font, x, y, PW, icon, title, chip, !on ? WmdUi.K_BAD : none ? WmdUi.K_WARN : fields.isEmpty() ? WmdUi.K_GOOD : WmdUi.K_INFO, false);
        fit(g, Component.translatable(P + "net", c.frequency(), c.domes(), c.arrays()), cx, y + 26, GREY);
        toggle(g, x, y);
        int wx = x + WF_X, wy = y + WF_Y;
        WmdUi.well(g, wx, wy, WF_W, WF_H);
        seabedMap(g, c, fields, wrecks, wx, wy);
        int top = y + WF_Y + WF_H + 22;
        WmdUi.divider(g, x + 5, top, PW - 10);
        int ry = top + 4, max = y + PH - 9;
        if (fields.isEmpty() && wrecks.isEmpty()) {
            fit(g, Component.translatable(!on ? P + "none_off" : none ? P + "none_domes" : P + "none_survey1"), cx, ry + 4, GREY);
            if (on && !none) fit(g, Component.translatable(P + "none_survey2", Balance.SURVEY_RANGE), cx, ry + 15, GREY);
            return;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (ry > max) return;
            CompoundTag t = fields.get(i);
            String id = String.valueOf(i + 1);
            mark(g, x + 7, ry, id, SAND);
            int fx = t.getInt("x"), fz = t.getInt("z");
            drawLeft(g, Component.translatable(P + "r_field", t.getInt("n"), dist(c, fx, fz), dir(c, fx, fz), t.getInt("d")), x + 18, ry, PW - 23, TEXT);
            hits.add(new Object[] { x + 5, ry - 1, PW - 10, 10, fieldTip(t, id) });
            ry += 10;
        }
        for (int i = 0; i < wrecks.size(); i++) {
            if (ry > max) return;
            CompoundTag f = wrecks.get(i);
            String id = String.valueOf((char) ('A' + Math.min(i, 25)));
            mark(g, x + 7, ry, id, WRECK_GREY);
            int fx = f.getInt("x"), fz = f.getInt("z");
            String[] lab = f.getString("n").split(":");
            drawLeft(g, Component.translatable(P + "s_wreck", lab.length >= 2 ? lab[1] : "?", f.getInt("d"), dist(c, fx, fz), dir(c, fx, fz)), x + 18, ry, PW - 23, TEXT);
            hits.add(new Object[] { x + 5, ry - 1, PW - 10, 10, pingTip(f, id) });
            ry += 10;
        }
    }

    private void seabedMap(GuiGraphics g, SonarConsoleBlockEntity c, List<CompoundTag> fields, List<CompoundTag> wrecks, int wx, int wy) {
        int mx = wx + WF_W / 2, my = wy + WF_H / 2, cxw = c.mapX(), czw = c.mapZ();
        List<CompoundTag> sens = c.sensorRows();
        double ext = 40.0;
        for (CompoundTag t : fields) ext = Math.max(ext, Math.hypot(t.getInt("x") - cxw, t.getInt("z") - czw) + t.getInt("r"));
        for (CompoundTag t : wrecks) ext = Math.max(ext, Math.hypot(t.getInt("x") - cxw, t.getInt("z") - czw));
        for (CompoundTag t : sens) if (t.getInt("k") == SonarNet.DOME) ext = Math.max(ext, Math.hypot(t.getInt("x") - cxw, t.getInt("z") - czw));
        ext *= 1.1;
        double scale = (WF_H / 2.0 - 5.0) / ext;
        int step = 10;
        for (int n : new int[] { 10, 25, 50, 100, 250, 500, 1000 }) { step = n; if (n >= ext / 2.0) break; }
        g.enableScissor(wx, wy, wx + WF_W, wy + WF_H);
        placed.clear();
        taken(mx, my, 2);
        for (CompoundTag t : sens) if (t.getInt("k") == SonarNet.DOME) taken(mx + (int) Math.round((t.getInt("x") - cxw) * scale), my + (int) Math.round((t.getInt("z") - czw) * scale), 2);
        for (int r = step; r * scale < WF_W; r += step) {
            ring(g, mx, my, r * scale, 0x30FFFFFF, 1);
            small(g, String.valueOf(r), mx + (int) (r * scale) + 2, my - 3, WmdUi.TEXT_DIM);
        }
        small(g, "N", mx - 1, wy + 1, GREY);

        for (CompoundTag t : sens) {
            if (t.getInt("k") != SonarNet.DOME) continue;
            int sx = mx + (int) Math.round((t.getInt("x") - cxw) * scale), sy = my + (int) Math.round((t.getInt("z") - czw) * scale);
            ring(g, sx, sy, Balance.SURVEY_RANGE * scale, 0x603AC2D5, 3);
        }

        for (int i = 0; i < fields.size(); i++) {
            CompoundTag f = fields.get(i);
            int fx = mx + (int) Math.round((f.getInt("x") - cxw) * scale), fy = my + (int) Math.round((f.getInt("z") - czw) * scale);
            int rp = Math.max(2, (int) Math.round(f.getInt("r") * scale));
            for (int dy = -rp; dy <= rp; dy++) {
                int hw = (int) Math.round(Math.sqrt(Math.max(0, rp * rp - dy * dy)));
                g.fill(fx - hw, fy + dy, fx + hw + 1, fy + dy + 1, SAND_DIM);
            }
            g.fill(fx - 1, fy - 1, fx + 2, fy + 2, SAND);
            String id = String.valueOf(i + 1);
            label(g, id, fx, fy, rp, SAND);
            hits.add(new Object[] { fx - rp - 1, fy - rp - 1, rp * 2 + 3, rp * 2 + 3, fieldTip(f, id) });
        }
        for (int i = 0; i < wrecks.size(); i++) {
            CompoundTag f = wrecks.get(i);
            int fx = mx + (int) Math.round((f.getInt("x") - cxw) * scale), fy = my + (int) Math.round((f.getInt("z") - czw) * scale);
            g.fill(fx - 2, fy - 2, fx + 2, fy + 2, WRECK_GREY);
            taken(fx, fy, 2);
            String id = String.valueOf((char) ('A' + Math.min(i, 25)));
            label(g, id, fx, fy, 2, WRECK_GREY);
            hits.add(new Object[] { fx - 3, fy - 3, 6, 6, pingTip(f, id) });
        }
        for (CompoundTag t : sens) {
            if (t.getInt("k") != SonarNet.DOME) continue;
            int sx = mx + (int) Math.round((t.getInt("x") - cxw) * scale), sy = my + (int) Math.round((t.getInt("z") - czw) * scale);
            g.fill(sx - 1, sy - 2, sx + 2, sy + 3, BLUE);
            g.fill(sx - 2, sy - 1, sx + 3, sy + 2, BLUE);
            hits.add(new Object[] { sx - 3, sy - 3, 6, 6, List.<Component>of(Component.translatable(P + "map_dome", t.getInt("x"), t.getInt("z"))) });
        }
        g.fill(mx - 1, my - 1, mx + 2, my + 2, TEXT);
        hits.add(new Object[] { mx - 2, my - 2, 5, 5, List.<Component>of(Component.translatable(P + "map_console", cxw, czw)) });
        g.disableScissor();
        small(g, "?", wx + WF_W - 5, wy + 1, WmdUi.TEXT_DIM);
        hits.add(new Object[] { wx + WF_W - 8, wy, 8, 8, List.<Component>of(Component.translatable(P + "sea_help1"), Component.translatable(P + "sea_help2", Balance.SURVEY_RANGE),
                Component.translatable(P + "sea_help3"), Component.translatable(P + "sea_help4")) });
    }

    private static void ring(GuiGraphics g, int cx, int cy, double rp, int col, int dash) {
        int dots = Math.max(24, (int) (rp * 1.2));
        for (int i = 0; i < dots; i++) {
            if (dash > 1 && (i / dash) % 2 == 1) continue;
            double a = i * Math.PI * 2.0 / dots;
            int px = cx + (int) Math.round(Math.cos(a) * rp), py = cy + (int) Math.round(Math.sin(a) * rp);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }

    private static List<Component> fieldTip(CompoundTag t, String id) {
        int age = t.getInt("a");
        return List.of(Component.translatable(P + "tip_field", id),
                Component.translatable(P + "tip_field_at", t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("r") * 2 + 1),
                Component.translatable(P + "tip_field_left", t.getInt("n"), age < 60 ? Component.translatable(P + "age_s", age) : Component.translatable(P + "age_m", age / 60)),
                Component.translatable(P + "tip_field_depth", t.getInt("d")));
    }

    private void history(GuiGraphics g, SonarConsoleBlockEntity c, int wx, int wy) {
        hits.add(new Object[] { wx, wy, WF_W, WF_H, List.<Component>of(Component.translatable(P + "hist_help1"), Component.translatable(P + "hist_help2")) });
        for (int k = 1; k < 4; k++) g.fill(wx + WF_W * k / 4, wy, wx + WF_W * k / 4 + 1, wy + WF_H - 7, 0x22FFFFFF);
        List<int[]> fall = c.waterfall();
        int rowH = Math.max(1, (WF_H - 8) / Math.max(1, Balance.SONAR_WATERFALL_ROWS));
        for (int r = 0; r < fall.size(); r++) {
            int ry = wy + r * rowH;
            if (ry + rowH > wy + WF_H - 7) break;
            for (int v : fall.get(r)) {
                int b10 = v & 0xFFF, lv = (v >>> 12) & 0xFF;
                int px = wx + Math.min(WF_W - 2, b10 * WF_W / 3600);
                float t = Math.max(0.0F, Math.min(1.0F, (lv - (float) Balance.NOISE_FLOOR_DB) / 60.0F));
                int a = 0x60 + (int) (t * 0x9F);
                int col = (a << 24) | (t > 0.7F ? 0xE8FBFF : 0x3AC2D5);
                g.fill(px, ry, px + 2, ry + rowH, col);
            }
        }
        String[] d = { "N", "E", "S", "W", "N" };
        for (int k = 0; k < 5; k++) small(g, d[k], wx + Math.min(WF_W - 4, Math.max(1, WF_W * k / 4 - 1)), wy + WF_H - 6, GREY);
        String now = Component.translatable(P + "hist_now").getString();
        small(g, now, wx + WF_W - 2 - (int) (font.width(now) * 0.6F), wy + 1, WmdUi.TEXT_DIM);
    }

    private static final String[] DIRS = { "n", "ne", "e", "se", "s", "sw", "w", "nw" };

    static Component compass(double deg) {
        int i = (int) Math.floor((((deg % 360.0) + 360.0) % 360.0 + 22.5) / 45.0) % 8;
        return Component.translatable(P + "dir." + DIRS[i]);
    }

    private static double bearingTo(SonarConsoleBlockEntity c, int x, int z) {
        return (Math.toDegrees(Math.atan2(x - c.mapX(), -(z - c.mapZ()))) + 360.0) % 360.0;
    }

    private static Component dir(SonarConsoleBlockEntity c, int x, int z) { return compass(bearingTo(c, x, z)); }

    private static String dist(SonarConsoleBlockEntity c, int x, int z) { return String.format("%,d", (int) Math.round(Math.hypot(x - c.mapX(), z - c.mapZ()))); }

    static Component loud(int db) {
        int over = db - (int) Balance.NOISE_FLOOR_DB;
        return Component.translatable(P + (over < 8 ? "loud.faint" : over < 20 ? "loud.clear" : "loud.loud"));
    }

    private static List<Component> contactTip(CompoundTag t, String id) {
        List<Component> l = new java.util.ArrayList<>();
        l.add(Component.translatable(P + "tip_contact", id, Component.translatable(kind(t.getInt("k")))));
        l.add(Component.translatable(P + "tip_heard", compass(t.getInt("b") / 10.0), String.format("%.0f", t.getInt("b") / 10.0), loud(t.getInt("l")), t.getInt("l")));
        l.add(t.getBoolean("f") ? Component.translatable(P + "tip_fix", t.getInt("fx"), t.getInt("fz"), t.getInt("fe")) : Component.translatable(P + "tip_nofix"));
        return l;
    }

    private static List<Component> pingTip(CompoundTag f, String id) {
        if (f.getString("n").startsWith("mine:"))
            return List.of(Component.translatable(P + "tip_ping", id, Component.translatable(P + "mine")),
                    Component.translatable(P + "tip_ping_at", f.getInt("x"), f.getInt("y"), f.getInt("z"), f.getInt("d")));
        return List.of(Component.translatable(P + "tip_ping", id, Component.translatable(P + (f.getString("n").startsWith("mine:") ? "mine" : f.getString("n").startsWith("wreck:") ? "wreck" : f.getBoolean("s") ? "sub" : "surface"))),
                Component.translatable(P + "tip_ping_at", f.getInt("x"), f.getInt("y"), f.getInt("z"), f.getInt("d")),
                Component.translatable(P + "tip_ping_size", f.getString("n").substring(f.getString("n").lastIndexOf(':') + 1)));
    }

    private void hull(GuiGraphics g, SonarConsoleBlockEntity c, int x, int y, ItemStack icon, Component title) {
        int cx = x + PW / 2;
        boolean on = c.isRunning();
        CompoundTag h = c.hullInfo();
        int kind = on ? HullPage.chipKind(h) : WmdUi.K_BAD;
        Component chip = Component.translatable(on ? P + "hull_chip_" + HullPage.chip(h) : P + "offline");
        WmdUi.header(g, font, x, y, PW, icon, title, chip, kind, on && kind == WmdUi.K_BAD);
        boolean sub = h.getBoolean("on");
        Component weak;
        if (!sub) weak = Component.translatable(P + "hull_weak_none");
        else if (h.contains("w")) {
            net.minecraft.world.level.block.Block blk = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(h.getString("w")));
            weak = h.getInt("c") > 0 ? Component.translatable(P + "hull_weak", Component.translatable(blk.getDescriptionId()), h.getInt("c"))
                    : Component.translatable(P + "hull_weak_nc", Component.translatable(blk.getDescriptionId()));
        } else weak = Component.translatable(P + "hull_weak_unknown");
        fit(g, weak, cx, y + 26, GREY);
        toggle(g, x, y);
        int wx = x + WF_X, wy = y + WF_Y;
        WmdUi.well(g, wx, wy, WF_W, WF_H);
        if (on) HullPage.paint(new LiveScreenRenderer.GuiCanvas(g, font).box(wx, wy, WF_W, WF_H), h, false);
        else fit(g, Component.translatable(P + "none_off"), cx, wy + WF_H / 2 - 4, GREY);
        hits.add(new Object[] { wx + WF_W - 8, wy, 8, 8, List.<Component>of(Component.translatable(P + "hull_help1"), Component.translatable(P + "hull_help2", (int) Math.round(Balance.SONAR_HULL_WARN * 100)),
                Component.translatable(P + "hull_help3")) });
        g.drawString(font, "?", wx + WF_W - 6, wy + 1, GREY, false);

        if (on && sub) {
            Component state = Component.translatable(P + (h.getBoolean("x") ? "hull_st_breached" : h.getBoolean("o") ? "hull_st_holed" : h.getBoolean("s") ? "hull_st_sealed" : "hull_st_open"), h.getInt("k"));
            if (h.getInt("b") > 0) state = Component.translatable(P + "hull_st_bonus", state, h.getInt("b"));
            fit(g, state, cx, y + WF_Y + WF_H + 7, h.getBoolean("x") ? WARN : GREY);
        }
        int top = y + WF_Y + WF_H + 22;
        WmdUi.divider(g, x + 5, top, PW - 10);
        int ry = top + 4, max = y + PH - 9;
        if (!on || !sub) return;
        List<CompoundTag> leaks = HullPage.leaks(h);
        if (leaks.isEmpty()) { fit(g, Component.translatable(P + "hull_noleaks"), cx, ry + 4, GREY); return; }
        int total = h.getInt("ln");
        for (int i = 0; i < leaks.size(); i++) {
            if (ry > max) return;
            CompoundTag l = leaks.get(i);
            boolean breach = l.getBoolean("t") || l.getBoolean("o");
            mark(g, x + 7, ry, String.valueOf(i + 1), breach ? 0xFFE05050 : WARN);
            drawLeft(g, HullPage.leakLine(l), x + 18, ry, PW - 23, breach ? TEXT : GREY);
            ry += 10;
        }
        if (total > leaks.size() && ry <= max) fit(g, Component.translatable(P + "hull_more", total - leaks.size()), cx, ry, GREY);
    }

    private void mark(GuiGraphics g, int x, int y, String id, int col) {
        g.fill(x, y - 1, x + 9, y + 8, (col & 0x00FFFFFF) | 0x40000000);
        g.fill(x, y - 1, x + 1, y + 8, col);
        g.drawString(font, id, x + 3, y, col, false);
    }

    private void small(GuiGraphics g, String s, int x, int y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(0.6F, 0.6F, 1.0F);
        g.drawString(font, s, 0, 0, col, false);
        g.pose().popPose();
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int col, int dash) {
        int n = Math.min(600, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) continue;
            int x = x0 + (n == 0 ? 0 : (x1 - x0) * i / n), y = y0 + (n == 0 ? 0 : (y1 - y0) * i / n);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        super.render(g, mouseX, mouseY, partialTick);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] h = hits.get(i);
            if (WmdUi.over(mouseX, mouseY, (int) h[0], (int) h[1], (int) h[2], (int) h[3])) {
                @SuppressWarnings("unchecked") List<Component> tip = (List<Component>) h[4];
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) { fit(g, text, cx, y, colour, PW - 10); }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour, int maxW) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
