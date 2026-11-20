package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class WarRoomScreen extends Screen {

    static final int PW = 300, PH = 222, ROW_W = 32, ROW_N = 20, BTN_W = 56, BTN_H = 13, FOOT_Y = 196, CELL = 9;
    private static WarRoomScreen open;
    private CompoundTag data;
    private int tab, ticks, armed = -1, armedKey = Integer.MIN_VALUE, scroll;
    private long armedAt;
    private String msg = "";
    private int msgAt;

    private WarRoomScreen(CompoundTag data) {
        super(Component.translatable("gui.wmd.war.title"));
        this.data = data;
        take(data);
    }

    static void onData(WarRoomPayloads.Data p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WarRoomScreen s) { s.data = p.data(); s.take(p.data()); return; }
        if (p.open()) { open = new WarRoomScreen(p.data()); mc.setScreen(open); }
    }

    private void take(CompoundTag d) {
        String m = d.getString("msg");
        if (!m.isEmpty()) { msg = m; msgAt = ticks; }
        if (tab == 3 && !d.contains("take")) tab = 0;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void removed() { if (open == this) open = null; }

    private void ask(int action, UUID target, int x, int z) { PacketDistributor.sendToServer(new WarRoomPayloads.Ask(action, target, x, z, false)); }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) ask(WarRoomPayloads.A_REFRESH, null, 0, 0);
        if (armed >= 0 && ticks - armedAt > 60) armed = -1;
    }

    private int px() { return (width - PW) / 2 + 12; }
    private int py() { return (height - PH) / 2; }

    private int tabs() { return data.contains("take") ? 4 : 3; }
    private static String icon(int i) { return i == 0 ? "icon_freq" : i == 1 ? "icon_wing" : i == 2 ? "icon_info" : "icon_ports"; }
    private static Component t(String k, Object... a) { return Component.translatable("gui.wmd.war." + k, a); }

    private boolean isArmed(int action, int key) { return armed == action && armedKey == key && ticks - armedAt <= 60; }

    private static String mins(long ms) { long m = Math.max(0L, (ms + 59_999L) / 60_000L); return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min"; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = px(), y = py();
        for (int i = 0; i < tabs(); i++) if (i != tab) WmdUi.tab(g, x - 23, y + 22 + i * 24, icon(i), false, WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22));
        WmdUi.panel(g, x, y, PW, PH);
        WmdUi.tab(g, x - 23, y + 22 + tab * 24, icon(tab), true, false);
        boolean opac = data.getBoolean("opac");
        String me = data.getString("meName");
        Component chip = !opac ? t("chip_no_opac") : me.isEmpty() ? t("chip_no_nation") : Component.literal(me + (data.getBoolean("lead") ? " *" : ""));
        int atWar = 0;
        for (Tag tg : data.getList("wars", Tag.TAG_COMPOUND)) if (((CompoundTag) tg).getInt("st") == WarData.ACTIVE) atWar++;
        WmdUi.header(g, font, x, y, PW, new ItemStack(Items.RED_BANNER), title, chip, atWar > 0 ? WmdUi.K_BAD : me.isEmpty() ? WmdUi.K_IDLE : WmdUi.K_GOOD, atWar > 0);
        switch (tab) {
            case 0 -> drawWars(g, x, y, mx, my);
            case 1 -> drawNations(g, x, y, mx, my);
            case 2 -> {
                drawBoard(g, x, y);
                int tr = data.getInt("treaty");
                if (!data.getString("meName").isEmpty() && tr != 2)
                    button(g, mx, my, treatyBtnX(), treatyBtnY(), tr == 1 ? WarRoomPayloads.A_TREATY_WITHDRAW : WarRoomPayloads.A_TREATY_SIGN, 0, t(tr == 1 ? "b_withdraw" : "b_sign"), data.getBoolean("lead"), tr == 1);
            }
            default -> drawTerritory(g, x, y, mx, my);
        }
        WmdUi.divider(g, x + 8, y + FOOT_Y - 4, PW - 16);
        boolean fresh = !msg.isEmpty() && ticks - msgAt < 160;
        boolean good = fresh && msg.startsWith("ok_");
        WmdUi.drawFit(g, font, fresh ? Component.translatable("message.wmd.war." + msg) : t("hint" + tab), x + 8, y + FOOT_Y, PW - 16, fresh ? (good ? WmdUi.GOOD : WmdUi.WARN) : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, data.getBoolean("vn") ? t("vn_mode") : data.getBoolean("lead") ? t("lead_yes") : t("lead_no"), x + 8, y + FOOT_Y + 11, PW - 16, data.getBoolean("vn") ? WmdUi.WARN : WmdUi.TEXT_DIM);
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, int action, int key, Component label, boolean on, boolean danger) {
        if (data.getBoolean("vn")) return;
        boolean a = isArmed(action, key);
        WmdUi.button(g, font, bx, by, BTN_W, BTN_H, a ? t("confirm") : label, !on ? WmdUi.B_DISABLED : a || danger ? WmdUi.B_DANGER : WmdUi.B_NORMAL, on && WmdUi.over(mx, my, bx, by, BTN_W, BTN_H));
    }

    private void drawWars(GuiGraphics g, int x, int y, int mx, int my) {
        ListTag wars = data.getList("wars", Tag.TAG_COMPOUND), calls = data.getList("calls", Tag.TAG_COMPOUND);
        boolean lead = data.getBoolean("lead");
        int ry = y + 26;
        if (wars.isEmpty() && calls.isEmpty()) WmdUi.drawFit(g, font, t("at_peace"), x + 8, ry, PW - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < wars.size() && ry < y + FOOT_Y - ROW_W; i++, ry += ROW_W) {
            CompoundTag w = wars.getCompound(i);
            boolean active = w.getInt("st") == WarData.ACTIVE;
            WmdUi.led(g, x + 8, ry + 2, active ? WmdUi.K_BAD : WmdUi.K_WARN, active);
            int tw = PW - 24 - 2 * BTN_W - 8;
            WmdUi.drawFit(g, font, data.getBoolean("vn") ? t("vn_war") : t(active ? "war_active" : "war_declared", w.getInt("id"), mins(w.getLong("left"))), x + 16, ry, tw, WmdUi.TEXT);
            WmdUi.drawFit(g, font, Component.literal(w.getString("a") + "  vs  " + w.getString("b")), x + 16, ry + 10, tw, WmdUi.TEXT_MUTED);
            if (active && !data.getBoolean("vn")) WmdUi.drawFit(g, font, t("score", w.getInt("pa"), w.getInt("pb")), x + 16, ry + 20, tw, WmdUi.BLUE_LIGHT);
            int peace = w.getInt("peace");
            int b1 = x + PW - 8 - 2 * BTN_W - 4, b2 = x + PW - 8 - BTN_W;
            if (w.getBoolean("principal")) button(g, mx, my, b1, ry + 2, WarRoomPayloads.A_PEACE, w.getInt("id"), t(peace == 2 ? "b_accept" : peace == 1 ? "b_offered" : "b_peace"), lead && peace != 1, false);
            if (w.getBoolean("principal")) button(g, mx, my, b2, ry + 2, WarRoomPayloads.A_SURRENDER, w.getInt("id"), t("b_surrender"), lead, true);
            if (peace == 2) WmdUi.drawFit(g, font, t("they_offer"), b1, ry + 17, 2 * BTN_W + 4, WmdUi.GOOD);
        }
        for (int i = 0; i < calls.size() && ry < y + FOOT_Y - 20; i++, ry += 20) {
            CompoundTag c = calls.getCompound(i);
            WmdUi.led(g, x + 8, ry + 2, WmdUi.K_WARN, true);
            WmdUi.drawFit(g, font, t("call", c.getString("allyN"), c.getString("enemyN")), x + 16, ry + 3, PW - 32 - BTN_W, WmdUi.WARN);
            button(g, mx, my, x + PW - 8 - BTN_W, ry + 1, WarRoomPayloads.A_JOIN, 1000 + c.getInt("id"), t("b_join"), lead, false);
        }
    }

    private String declareWhy(CompoundTag n) {
        if (!data.getBoolean("lead")) return "why_lead";
        if (n.getBoolean("self")) return "why_self";
        if (n.getBoolean("ally")) return "why_ally";
        if (n.getInt("war") != 0) return "why_war";
        if (n.contains("cool")) return "why_cool";
        if (!data.getBoolean("cc")) return "why_cc";
        return null;
    }

    private void drawNations(GuiGraphics g, int x, int y, int mx, int my) {
        ListTag ns = data.getList("nations", Tag.TAG_COMPOUND);
        int rows = (FOOT_Y - 30) / ROW_N;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ns.size() - rows)));
        if (ns.isEmpty()) WmdUi.drawFit(g, font, t("no_nations"), x + 8, y + 26, PW - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < rows && scroll + i < ns.size(); i++) {
            CompoundTag n = ns.getCompound(scroll + i);
            int ry = y + 26 + i * ROW_N, war = n.getInt("war");
            boolean open = n.getBoolean("open");
            WmdUi.led(g, x + 8, ry + 2, n.getBoolean("self") ? WmdUi.K_INFO : war == 2 ? WmdUi.K_BAD : war == 1 ? WmdUi.K_WARN : n.getBoolean("ally") ? WmdUi.K_GOOD : WmdUi.K_IDLE, war == 2);
            int tw = PW - 32 - BTN_W;
            WmdUi.drawFit(g, font, Component.literal(n.getString("n") + (n.getBoolean("self") ? "  (you)" : "")), x + 16, ry, tw, WmdUi.TEXT);
            Component st = war == 2 ? t(open ? "st_open" : "st_closed") : war == 1 ? t("st_declared") : n.getBoolean("ally") ? t("st_ally")
                    : n.contains("cool") ? t("st_cool", n.getInt("cool")) : t("st_peace");
            WmdUi.drawFit(g, font, data.getBoolean("vn") ? st : t("members", n.getInt("on"), n.getInt("m")).copy().append("  ").append(st), x + 16, ry + 9, tw, war == 2 ? WmdUi.BAD : WmdUi.TEXT_MUTED);
            if (!n.getBoolean("self")) button(g, mx, my, x + PW - 8 - BTN_W, ry + 2, WarRoomPayloads.A_DECLARE, n.getUUID("id").hashCode(), t("b_declare"), declareWhy(n) == null, true);
        }
    }

    private void drawBoard(GuiGraphics g, int x, int y) {
        ListTag b = data.getList("board", Tag.TAG_COMPOUND);
        WmdUi.drawFit(g, font, t("board_cols"), x + 8, y + 26, PW - 16, WmdUi.TEXT_MUTED);
        if (b.isEmpty()) WmdUi.drawFit(g, font, t("board_empty"), x + 8, y + 40, PW - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < b.size() && i < 10; i++) {
            CompoundTag r = b.getCompound(i);
            int[] v = r.getIntArray("v");
            if (v.length < 4) continue;
            int ry = y + 40 + i * 12;
            WmdUi.drawFit(g, font, Component.literal((i + 1) + ". " + r.getString("n")), x + 8, ry, 130, i == 0 ? WmdUi.WARN : WmdUi.TEXT);
            WmdUi.drawFit(g, font, Component.literal(v[0] + " pts   " + v[1] + " / " + v[2] + " / " + v[3] + (r.getBoolean("breaker") ? "   BREAKER" : "")), x + 150, ry, PW - 158,
                    r.getBoolean("breaker") ? WmdUi.BAD : WmdUi.TEXT);
        }
        if (data.getBoolean("vn")) return;

        int ty = y + FOOT_Y - 22, tr = data.getInt("treaty");
        WmdUi.divider(g, x + 8, ty - 4, PW - 16);
        Component line = tr == 1 ? t("treaty_in", data.getInt("signers")) : tr == 2 ? t("treaty_leaving", mins(data.getLong("treatyLeft"))) : t("treaty_out", data.getInt("signers"));
        WmdUi.drawFit(g, font, line, x + 8, ty, PW - 24 - BTN_W, tr == 1 ? WmdUi.GOOD : WmdUi.TEXT_MUTED);
    }

    private int treatyBtnX() { return px() + PW - 8 - BTN_W; }
    private int treatyBtnY() { return py() + FOOT_Y - 24; }

    private void drawTerritory(GuiGraphics g, int x, int y, int mx, int my) {
        CompoundTag tk = data.getCompound("take");
        WmdUi.drawFit(g, font, t("take_head", tk.getInt("left"), tk.getString("loser"), mins(tk.getLong("until"))), x + 8, y + 24, PW - 16, WmdUi.WARN);
        int n = 2 * WarManager.TAKE_REACH + 1, gx = x + 8, gy = y + 36;
        byte[] grid = tk.getByteArray("grid");
        WmdUi.well(g, gx, gy, n * CELL, n * CELL);
        int hover = -1;
        for (int i = 0; i < grid.length && i < n * n; i++) {
            int cx = gx + (i % n) * CELL, cy = gy + (i / n) * CELL;
            int col = switch (grid[i]) { case 1 -> 0xFFB03A30; case 2 -> 0xFF4A2A28; case 3 -> 0xFF3A7BD5; default -> 0xFF1A2028; };
            if (isArmed(WarRoomPayloads.A_TAKE, i)) col = 0xFFFF6040;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, col);
            if (WmdUi.over(mx, my, cx, cy, CELL, CELL)) { hover = i; g.renderOutline(cx - 1, cy - 1, CELL + 1, CELL + 1, 0xFFFFFFFF); }
        }
        int mid = WarManager.TAKE_REACH * n + WarManager.TAKE_REACH;
        g.renderOutline(gx + (mid % n) * CELL + 2, gy + (mid / n) * CELL + 2, CELL - 5, CELL - 5, 0xFFFFFFFF);
        int lx = gx + n * CELL + 10;
        WmdUi.drawFit(g, font, t("legend_take"), lx, gy, PW - (lx - x) - 8, 0xFFE07060);
        WmdUi.drawFit(g, font, t("legend_no"), lx, gy + 12, PW - (lx - x) - 8, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t("legend_mine"), lx, gy + 24, PW - (lx - x) - 8, WmdUi.BLUE_LIGHT);
        WmdUi.drawFit(g, font, t("legend_you"), lx, gy + 36, PW - (lx - x) - 8, WmdUi.TEXT);
        if (hover >= 0) {
            int cx = tk.getInt("cx") + hover % n - WarManager.TAKE_REACH, cz = tk.getInt("cz") + hover / n - WarManager.TAKE_REACH;
            WmdUi.drawFit(g, font, t("chunk_at", cx * 16 + 8, cz * 16 + 8), lx, gy + 56, PW - (lx - x) - 8, WmdUi.TEXT);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = px(), y = py();
        String[] tips = { "tab_wars", "tab_nations", "tab_board", "tab_territory" };
        for (int i = 0; i < tabs(); i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { g.renderTooltip(font, t(tips[i]), mx, my); return; }
        if (tab == 1) {
            ListTag ns = data.getList("nations", Tag.TAG_COMPOUND);
            int rows = (FOOT_Y - 30) / ROW_N;
            for (int i = 0; i < rows && scroll + i < ns.size(); i++) {
                CompoundTag n = ns.getCompound(scroll + i);
                String why = declareWhy(n);
                if (why != null && !n.getBoolean("self") && WmdUi.over(mx, my, x + PW - 8 - BTN_W, y + 26 + i * ROW_N + 2, BTN_W, BTN_H)) g.renderTooltip(font, t(why), mx, my);
            }
        }
    }

    private boolean press(int action, int key, UUID target, int cx, int cz) {
        if (!isArmed(action, key)) { armed = action; armedKey = key; armedAt = ticks; WmdUi.click(); return true; }
        armed = -1;
        WmdUi.confirm();
        ask(action, target, cx, cz);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        int x = px(), y = py();
        if (data.getBoolean("vn")) {
            for (int i = 0; i < tabs(); i++) if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { if (tab != i) { tab = i; scroll = 0; WmdUi.tab(); } return true; }
            return super.mouseClicked(mx, my, button);
        }
        for (int i = 0; i < tabs(); i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { if (tab != i) { tab = i; armed = -1; scroll = 0; WmdUi.tab(); } return true; }
        boolean lead = data.getBoolean("lead");
        if (tab == 0) {
            ListTag wars = data.getList("wars", Tag.TAG_COMPOUND), calls = data.getList("calls", Tag.TAG_COMPOUND);
            int ry = y + 26;
            for (int i = 0; i < wars.size() && ry < y + FOOT_Y - ROW_W; i++, ry += ROW_W) {
                CompoundTag w = wars.getCompound(i);
                int b1 = x + PW - 8 - 2 * BTN_W - 4, b2 = x + PW - 8 - BTN_W, peace = w.getInt("peace");
                UUID enemy = w.hasUUID("enemy") ? w.getUUID("enemy") : null;
                if (w.getBoolean("principal") && WmdUi.over(mx, my, b1, ry + 2, BTN_W, BTN_H)) {
                    if (!lead || peace == 1) { WmdUi.deny(); return true; }
                    WmdUi.click();
                    ask(WarRoomPayloads.A_PEACE, enemy, 0, 0);
                    return true;
                }
                if (w.getBoolean("principal") && WmdUi.over(mx, my, b2, ry + 2, BTN_W, BTN_H)) {
                    if (!lead) { WmdUi.deny(); return true; }
                    return press(WarRoomPayloads.A_SURRENDER, w.getInt("id"), enemy, 0, 0);
                }
            }
            for (int i = 0; i < calls.size() && ry < y + FOOT_Y - 20; i++, ry += 20) {
                CompoundTag c = calls.getCompound(i);
                if (WmdUi.over(mx, my, x + PW - 8 - BTN_W, ry + 1, BTN_W, BTN_H)) {
                    if (!lead) { WmdUi.deny(); return true; }
                    WmdUi.click();
                    ask(WarRoomPayloads.A_JOIN, c.getUUID("ally"), 0, 0);
                    return true;
                }
            }
        } else if (tab == 1) {
            ListTag ns = data.getList("nations", Tag.TAG_COMPOUND);
            int rows = (FOOT_Y - 30) / ROW_N;
            for (int i = 0; i < rows && scroll + i < ns.size(); i++) {
                CompoundTag n = ns.getCompound(scroll + i);
                if (n.getBoolean("self") || !WmdUi.over(mx, my, x + PW - 8 - BTN_W, y + 26 + i * ROW_N + 2, BTN_W, BTN_H)) continue;
                if (declareWhy(n) != null) { WmdUi.deny(); return true; }
                return press(WarRoomPayloads.A_DECLARE, n.getUUID("id").hashCode(), n.getUUID("id"), 0, 0);
            }
        } else if (tab == 2 && !data.getString("meName").isEmpty() && data.getInt("treaty") != 2 && WmdUi.over(mx, my, treatyBtnX(), treatyBtnY(), BTN_W, BTN_H)) {
            if (!lead) { WmdUi.deny(); return true; }
            return press(data.getInt("treaty") == 1 ? WarRoomPayloads.A_TREATY_WITHDRAW : WarRoomPayloads.A_TREATY_SIGN, 0, null, 0, 0);
        } else if (tab == 3 && data.contains("take")) {
            CompoundTag tk = data.getCompound("take");
            int n = 2 * WarManager.TAKE_REACH + 1, gx = x + 8, gy = y + 36;
            byte[] grid = tk.getByteArray("grid");
            for (int i = 0; i < grid.length && i < n * n; i++) {
                if (!WmdUi.over(mx, my, gx + (i % n) * CELL, gy + (i / n) * CELL, CELL, CELL)) continue;
                if (grid[i] != 1 || !lead) { WmdUi.deny(); return true; }
                return press(WarRoomPayloads.A_TAKE, i, null, tk.getInt("cx") + i % n - WarManager.TAKE_REACH, tk.getInt("cz") + i / n - WarManager.TAKE_REACH);
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (tab != 1) return false;
        scroll = Math.max(0, scroll - (int) Math.signum(dy));
        return true;
    }
}
