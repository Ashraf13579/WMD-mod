package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaserTabletScreen extends Screen {

    static final int W = 264, H = 224, ROW_P = 36, ROW_F = 26, BTN_W = 52, BTN_H = 14, FOOT_Y = 186;

    static final int SRC_Y = 48, SRC_W = 59, BOX_Y = 68, BOX_W = 60, OPT_Y = 86, OPT_W = 40, OPT_X = 120, DRN_Y = 106, WIDE_W = 100, SET_Y = 150, SET_W = 60;
    private static final int A_SRC = 100, A_OPT = 110, A_SAVE = 120, A_BACK = 121;
    private static LaserTabletScreen open;
    private TabletInfoPayload info;
    private int tab, ticks, armedAction = -1, armedIndex = -1;
    private long armedFlight, armedAt = -1, msgAt;
    private String msg = "";

    private int edit = -1, eSrc, eRounds, eTier;
    private String eX = "", eZ = "";
    private EditBox xBox, zBox;

    private LaserTabletScreen(TabletInfoPayload info) {
        super(Component.translatable("gui.wmd.tablet.title"));
        this.info = info;
        take(info);
    }

    public static void show(TabletInfoPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == open && open != null) { open.info = p; open.take(p); return; }
        open = new LaserTabletScreen(p);
        mc.setScreen(open);
    }

    private void take(TabletInfoPayload p) {
        String m = p.data().getString("msg");
        if (!m.isEmpty()) { msg = m; msgAt = ticks; }
        if (edit >= rows().size()) leaveSetup();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() { if (open == this) open = null; }

    @Override
    protected void init() {
        int x = x0(), y = y0();
        if (xBox != null) { eX = xBox.getValue(); eZ = zBox.getValue(); }
        xBox = box(x + 20, y + BOX_Y, eX);
        zBox = box(x + 20 + BOX_W + 26, y + BOX_Y, eZ);
        showBoxes();
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, BOX_W, 12, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        b.setResponder(s -> { if (edit >= 0 && !s.isEmpty() && !s.equals("-")) eSrc = LaserTabletItem.SRC_XZ; });
        addRenderableWidget(b);
        return b;
    }

    private void showBoxes() {
        boolean on = tab == 0 && edit >= 0;
        xBox.visible = on; zBox.visible = on;
        if (!on) { xBox.setFocused(false); zBox.setFocused(false); }
    }

    private void send(int action, int index, long flight) { PacketDistributor.sendToServer(new TabletFirePayload(info.mainHand(), action, index, flight)); }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) send(LaserTabletItem.A_REFRESH, 0, 0L);
        if (armedAt >= 0 && ticks - armedAt > 60) armedAt = -1;
    }

    private int x0() { return (width - W) / 2 + 12; }
    private int y0() { return (height - H) / 2; }

    private ListTag rows() { return info.data().getList("rows", Tag.TAG_COMPOUND); }
    private ListTag flights() { return info.data().getList("flights", Tag.TAG_COMPOUND); }

    private boolean armed(int action, int index, long flight) {
        return armedAt >= 0 && armedAction == action && armedIndex == index && armedFlight == flight;
    }

    private static Component line(CompoundTag t) {
        String k = t.getString("k");
        if (k.isEmpty()) return Component.empty();
        ListTag a = t.getList("a", Tag.TAG_STRING);
        Object[] args = new Object[a.size()];
        for (int i = 0; i < a.size(); i++) {
            String s = a.getString(i);
            args[i] = s.startsWith("#") ? Component.translatable(s.substring(1)) : s;
        }
        return Component.translatable(k, args);
    }

    private static boolean enabled(CompoundTag r) { return r.getCompound("why").getString("k").isEmpty(); }

    private int bx(int x) { return x + W - 8 - BTN_W; }
    private int byP(int ry, int k) { return ry + 2 + k * (BTN_H + 3); }
    private int byF(int ry, int k) { return ry + 5; }
    private int bxF(int x, int k) { return x + W - 8 - BTN_W * 2 - 3 + k * (BTN_W + 3); }

    private int srcX(int x, int k) { return x + 8 + k * (SRC_W + 3); }
    private int optX(int x, int k) { return x + OPT_X + k * (OPT_W + 3); }
    private int setX(int x, int k) { return k == 0 ? x + 8 : x + W - 8 - SET_W * (3 - k) - 4 * (2 - k); }

    private static Component warhead(String wh) { return wh == null || wh.isEmpty() ? Component.literal("?") : Component.translatable("warhead.wmd." + wh); }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = x0(), y = y0();
        for (int i = 0; i < 2; i++) if (i != tab) WmdUi.tab(g, x - 23, y + 22 + i * 24, i == 0 ? "icon_process" : "icon_uplink", false, WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22));
        WmdUi.panel(g, x, y, W, H);
        WmdUi.tab(g, x - 23, y + 22 + tab * 24, tab == 0 ? "icon_process" : "icon_uplink", true, false);
        int n = tab == 0 ? rows().size() : flights().size();
        Component chip = tab == 0 ? Component.translatable("gui.wmd.tablet.chip_slots", n, Balance.TABLET_SLOTS) : Component.translatable("gui.wmd.tablet.chip_flights", n);
        WmdUi.header(g, font, x, y, W, new ItemStack(Wmd.LASER_TABLET.get()), title, chip, n > 0 ? WmdUi.K_GOOD : WmdUi.K_IDLE, false);
        if (tab == 1) drawFlights(g, x, y, mx, my);
        else if (edit >= 0) drawSetup(g, x, y, mx, my);
        else drawRows(g, x, y, mx, my);
        if (tab == 0) {
            CompoundTag d = info.data();
            Component tl = !d.contains("trx") ? Component.translatable("gui.wmd.tablet.no_tripod")
                    : d.getBoolean("lit") ? Component.translatable("gui.wmd.tablet.spot", d.getInt("sx"), d.getInt("sy"), d.getInt("sz"))
                    : Component.translatable("gui.wmd.tablet.tripod_dark", d.getInt("trx"), d.getInt("trz"));
            WmdUi.drawFit(g, font, tl, x + 8, y + FOOT_Y - 15, W - 16, WmdUi.TEXT_MUTED);
        }
        WmdUi.divider(g, x + 8, y + FOOT_Y - 4, W - 16);
        boolean fresh = !msg.isEmpty() && ticks - msgAt < 160;
        boolean good = fresh && msg.startsWith("gui.wmd.tablet.ans.") && !msg.endsWith("refused") && !msg.endsWith("no_flight") && !msg.endsWith("none_out");
        String hint = tab == 1 ? "gui.wmd.tablet.hint_flights" : edit >= 0 ? "gui.wmd.tablet.hint_setup" : "gui.wmd.tablet.hint_slots";
        WmdUi.drawFit(g, font, fresh ? Component.translatable(msg) : Component.translatable(hint), x + 8, y + FOOT_Y, W - 16, fresh ? (good ? WmdUi.GOOD : WmdUi.WARN) : WmdUi.TEXT_MUTED);
        int by = y + H - 22;
        WmdUi.button(g, font, x + W - 78, by, 70, BTN_H, Component.translatable("gui.wmd.tablet.refresh"), WmdUi.B_NORMAL, WmdUi.over(mx, my, x + W - 78, by, 70, BTN_H));
    }

    private void drawRows(GuiGraphics g, int x, int y, int mx, int my) {
        ListTag rs = rows();
        if (rs.isEmpty()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.tablet.no_slots"), x + 8, y + 28, W - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < rs.size(); i++) {
            CompoundTag r = rs.getCompound(i);
            int ry = y + 24 + i * ROW_P, tw = W - 24 - BTN_W;
            if (i > 0) g.fill(x + 8, ry - 2, x + W - 8, ry - 1, 0xFF222932);
            boolean ok = r.getBoolean("ok");
            WmdUi.drawFit(g, font, line(r.getCompound("l1")), x + 8, ry + 1, tw, ok ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
            WmdUi.drawFit(g, font, line(r.getCompound("l2")), x + 8, ry + 11, tw, ok ? WmdUi.TEXT_MUTED : WmdUi.WARN);
            WmdUi.drawFit(g, font, line(r.getCompound("l3")), x + 8, ry + 21, tw, r.getBoolean("good") ? WmdUi.GOOD : WmdUi.WARN);
            int aa = r.getInt("aa");
            button(g, mx, my, bx(x), byP(ry, 0), aa, i, 0L, r.getString("act"), enabled(r), BTN_W);
            WmdUi.button(g, font, bx(x), byP(ry, 1), BTN_W, BTN_H, Component.translatable("gui.wmd.tablet.setup"), WmdUi.B_NORMAL, WmdUi.over(mx, my, bx(x), byP(ry, 1), BTN_W, BTN_H));
        }
    }

    private CompoundTag editRow() { return edit >= 0 && edit < rows().size() ? rows().getCompound(edit) : new CompoundTag(); }

    private static final String[] SRC_KEYS = { null, "gui.wmd.tablet.src.xz", "gui.wmd.tablet.src.desig", "gui.wmd.tablet.src.laser" };

    private void drawSetup(GuiGraphics g, int x, int y, int mx, int my) {
        CompoundTag r = editRow();
        int kind = r.getInt("k");
        WmdUi.drawFit(g, font, line(r.getCompound("l1")), x + 8, y + 25, W - 16, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.tablet.target_from"), x + 8, y + SRC_Y - 10, W - 16, WmdUi.TEXT_MUTED);
        for (int k = 0; k < 4; k++) {
            int bx = srcX(x, k), by = y + SRC_Y;
            Component t = Component.translatable(k == 0 ? r.getString("own") : SRC_KEYS[k]);
            WmdUi.button(g, font, bx, by, SRC_W, BTN_H, t, WmdUi.B_NORMAL, WmdUi.over(mx, my, bx, by, SRC_W, BTN_H));
            if (k == eSrc) g.renderOutline(bx - 1, by - 1, SRC_W + 2, BTN_H + 2, WmdUi.BLUE_LIGHT);
        }
        g.drawString(font, "X", x + 10, y + BOX_Y + 2, WmdUi.TEXT_MUTED, false);
        g.drawString(font, "Z", x + 20 + BOX_W + 16, y + BOX_Y + 2, WmdUi.TEXT_MUTED, false);
        boolean gun = kind == LaserTabletItem.K_GUN;
        if (gun || isHive(r)) {
            WmdUi.drawFit(g, font, Component.translatable(gun ? "gui.wmd.tablet.rounds_per" : "gui.wmd.tablet.tier_pick"), x + 8, y + OPT_Y + 3, OPT_X - 12, WmdUi.TEXT_MUTED);
            int n = gun ? 3 : 2, sel = gun ? eRounds : eTier - 1;
            for (int k = 0; k < n; k++) {
                int bx = optX(x, k), by = y + OPT_Y;
                Component t = Component.translatable(gun ? "gui.wmd.tablet.rounds." + k : "gui.wmd.tablet.tier." + (k + 1));
                WmdUi.button(g, font, bx, by, OPT_W, BTN_H, t, WmdUi.B_NORMAL, WmdUi.over(mx, my, bx, by, OPT_W, BTN_H));
                if (k == sel) g.renderOutline(bx - 1, by - 1, OPT_W + 2, BTN_H + 2, WmdUi.BLUE_LIGHT);
            }
        }
        if (r.getBoolean("open")) WmdUi.button(g, font, x + 8, y + DRN_Y, WIDE_W, BTN_H, Component.translatable("gui.wmd.tablet.open"), WmdUi.B_NORMAL, WmdUi.over(mx, my, x + 8, y + DRN_Y, WIDE_W, BTN_H));
        if (r.getBoolean("recall")) WmdUi.button(g, font, x + 12 + WIDE_W, y + DRN_Y, WIDE_W, BTN_H, Component.translatable("gui.wmd.tablet.recall_all"), WmdUi.B_NORMAL,
                WmdUi.over(mx, my, x + 12 + WIDE_W, y + DRN_Y, WIDE_W, BTN_H));
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.tablet.src_hint." + eSrc), x + 8, y + SET_Y - 12, W - 16, WmdUi.TEXT_MUTED);
        button(g, mx, my, setX(x, 0), y + SET_Y, LaserTabletItem.A_UNLINK, edit, 0L, "gui.wmd.tablet.unlink", true, SET_W);
        WmdUi.button(g, font, setX(x, 1), y + SET_Y, SET_W, BTN_H, Component.translatable("gui.wmd.tablet.save"), WmdUi.B_NORMAL, WmdUi.over(mx, my, setX(x, 1), y + SET_Y, SET_W, BTN_H));
        WmdUi.button(g, font, setX(x, 2), y + SET_Y, SET_W, BTN_H, Component.translatable("gui.wmd.tablet.back"), WmdUi.B_NORMAL, WmdUi.over(mx, my, setX(x, 2), y + SET_Y, SET_W, BTN_H));
    }

    private static boolean isHive(CompoundTag r) { return "gui.wmd.tablet.src.own2".equals(r.getString("own")); }

    private void drawFlights(GuiGraphics g, int x, int y, int mx, int my) {
        ListTag fs = flights();
        if (fs.isEmpty()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.tablet.no_flights"), x + 8, y + 28, W - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < fs.size(); i++) {
            CompoundTag r = fs.getCompound(i);
            int ry = y + 24 + i * ROW_F, tw = W - 30 - BTN_W * 2;
            if (i > 0) g.fill(x + 8, ry - 2, x + W - 8, ry - 1, 0xFF222932);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.tablet.flight", r.getInt("tier"), warhead(r.getString("wh")), r.getInt("tx"), r.getInt("tz")), x + 8, ry + 1, tw, WmdUi.TEXT);
            boolean dive = r.getBoolean("dive");
            WmdUi.drawFit(g, font, Component.translatable(dive ? "gui.wmd.tablet.flight_dive" : "gui.wmd.tablet.flight_cruise", r.getInt("left")), x + 8, ry + 11, tw,
                    dive ? WmdUi.WARN : WmdUi.TEXT_MUTED);
            long id = r.getLong("id");
            button(g, mx, my, bxF(x, 0), byF(ry, 0), LaserTabletItem.A_BURST, i, id, "gui.wmd.tablet.burst", r.getString("bwhy").isEmpty(), BTN_W);
            button(g, mx, my, bxF(x, 1), byF(ry, 0), LaserTabletItem.A_ABORT, i, id, "gui.wmd.tablet.abort", true, BTN_W);
        }
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, int action, int index, long flight, String key, boolean on, int w) {
        boolean a = armed(action, index, flight);
        WmdUi.button(g, font, bx, by, w, BTN_H, Component.translatable(a ? "gui.wmd.tablet.confirm" : key), !on ? WmdUi.B_DISABLED : a ? WmdUi.B_DANGER : WmdUi.B_NORMAL,
                on && WmdUi.over(mx, my, bx, by, w, BTN_H));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = x0(), y = y0();
        for (int i = 0; i < 2; i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { g.renderTooltip(font, Component.translatable(i == 0 ? "gui.wmd.tablet.tab_slots" : "gui.wmd.tablet.tab_flights"), mx, my); return; }
        Component why = null;
        if (tab == 0 && edit < 0) {
            ListTag rs = rows();
            for (int i = 0; i < rs.size(); i++)
                if (WmdUi.over(mx, my, bx(x), byP(y + 24 + i * ROW_P, 0), BTN_W, BTN_H)) why = line(rs.getCompound(i).getCompound("why"));
        } else if (tab == 1) {
            ListTag fs = flights();
            for (int i = 0; i < fs.size(); i++)
                if (WmdUi.over(mx, my, bxF(x, 0), byF(y + 24 + i * ROW_F, 0), BTN_W, BTN_H) && !fs.getCompound(i).getString("bwhy").isEmpty())
                    why = Component.translatable(fs.getCompound(i).getString("bwhy"));
        }
        if (why != null && !why.getString().isEmpty()) g.renderTooltip(font, why, mx, my);
    }

    private boolean press(int action, int index, long flight, boolean on) {
        if (!on) { WmdUi.deny(); return true; }
        if (!armed(action, index, flight)) { armedAction = action; armedIndex = index; armedFlight = flight; armedAt = ticks; WmdUi.click(); return true; }
        armedAt = -1;
        WmdUi.confirm();
        send(action, index, flight);
        if (action == LaserTabletItem.A_UNLINK) leaveSetup();
        return true;
    }

    private void enterSetup(int i) {
        CompoundTag r = rows().getCompound(i);
        edit = i;
        eSrc = r.getInt("src");
        eRounds = Math.floorMod(r.getInt("r"), 3);
        eTier = r.getInt("t") == 2 ? 2 : 1;
        xBox.setValue(r.getInt("src") == LaserTabletItem.SRC_XZ || r.getInt("x") != 0 || r.getInt("z") != 0 ? Integer.toString(r.getInt("x")) : "");
        zBox.setValue(r.getInt("src") == LaserTabletItem.SRC_XZ || r.getInt("x") != 0 || r.getInt("z") != 0 ? Integer.toString(r.getInt("z")) : "");
        eSrc = r.getInt("src");
        armedAt = -1;
        showBoxes();
    }

    private void leaveSetup() { edit = -1; armedAt = -1; if (xBox != null) showBoxes(); }

    private static Integer parse(EditBox b) {
        try { return b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private void save() {
        Integer tx = parse(xBox), tz = parse(zBox);
        if (eSrc == LaserTabletItem.SRC_XZ && (tx == null || tz == null)) { WmdUi.deny(); msg = "gui.wmd.tablet.ans.need_xz"; msgAt = ticks; return; }
        WmdUi.confirm();
        PacketDistributor.sendToServer(new TabletFirePayload(info.mainHand(), LaserTabletItem.A_SETUP, edit, 0L, tx == null ? 0 : tx, tz == null ? 0 : tz, eSrc | eRounds << 4 | eTier << 8));
        leaveSetup();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = x0(), y = y0(), by = y + H - 22;
        for (int i = 0; i < 2; i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { if (tab != i) { tab = i; armedAt = -1; showBoxes(); WmdUi.tab(); } return true; }
        if (WmdUi.over(mx, my, x + W - 78, by, 70, BTN_H)) { WmdUi.click(); send(LaserTabletItem.A_REFRESH, 0, 0L); return true; }
        if (tab == 0 && edit >= 0) {
            CompoundTag r = editRow();
            for (int k = 0; k < 4; k++) if (WmdUi.over(mx, my, srcX(x, k), y + SRC_Y, SRC_W, BTN_H)) { eSrc = k; WmdUi.click(); return true; }
            boolean gun = r.getInt("k") == LaserTabletItem.K_GUN, hive = isHive(r);
            if (gun || hive)
                for (int k = 0; k < (gun ? 3 : 2); k++)
                    if (WmdUi.over(mx, my, optX(x, k), y + OPT_Y, OPT_W, BTN_H)) { if (gun) eRounds = k; else eTier = k + 1; WmdUi.click(); return true; }
            if (r.getBoolean("open") && WmdUi.over(mx, my, x + 8, y + DRN_Y, WIDE_W, BTN_H)) { WmdUi.click(); send(LaserTabletItem.A_OPEN, edit, 0L); return true; }
            if (r.getBoolean("recall") && WmdUi.over(mx, my, x + 12 + WIDE_W, y + DRN_Y, WIDE_W, BTN_H)) { WmdUi.click(); send(LaserTabletItem.A_CEASE, edit, 0L); return true; }
            if (WmdUi.over(mx, my, setX(x, 0), y + SET_Y, SET_W, BTN_H)) return press(LaserTabletItem.A_UNLINK, edit, 0L, true);
            if (WmdUi.over(mx, my, setX(x, 1), y + SET_Y, SET_W, BTN_H)) { save(); return true; }
            if (WmdUi.over(mx, my, setX(x, 2), y + SET_Y, SET_W, BTN_H)) { WmdUi.click(); leaveSetup(); return true; }
            return super.mouseClicked(mx, my, button);
        }
        if (tab == 0) {
            ListTag rs = rows();
            for (int i = 0; i < rs.size(); i++) {
                CompoundTag r = rs.getCompound(i);
                int ry = y + 24 + i * ROW_P;
                if (WmdUi.over(mx, my, bx(x), byP(ry, 0), BTN_W, BTN_H)) {
                    int aa = r.getInt("aa");
                    if (!r.getBoolean("cf")) { if (!enabled(r)) { WmdUi.deny(); return true; } WmdUi.click(); send(aa, i, 0L); return true; }
                    return press(aa, i, 0L, enabled(r));
                }
                if (WmdUi.over(mx, my, bx(x), byP(ry, 1), BTN_W, BTN_H)) { WmdUi.click(); enterSetup(i); return true; }
            }
        } else {
            ListTag fs = flights();
            for (int i = 0; i < fs.size(); i++) {
                CompoundTag r = fs.getCompound(i);
                int ry = y + 24 + i * ROW_F;
                if (WmdUi.over(mx, my, bxF(x, 0), byF(ry, 0), BTN_W, BTN_H)) return press(LaserTabletItem.A_BURST, i, r.getLong("id"), r.getString("bwhy").isEmpty());
                if (WmdUi.over(mx, my, bxF(x, 1), byF(ry, 0), BTN_W, BTN_H)) return press(LaserTabletItem.A_ABORT, i, r.getLong("id"), true);
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
