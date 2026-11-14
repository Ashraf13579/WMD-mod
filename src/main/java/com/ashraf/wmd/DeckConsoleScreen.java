package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

final class DeckConsoleScreen extends Screen {

    static final int PW = 300, PH = 232, MAP_X = 8, MAP_Y = 26, MAP_W = 180, MAP_H = 150, SIDE_X = 194, SIDE_W = 98, ROW_H = 19, ROWS = 7, BTN_W = 40, BTN_H = 13;
    static final int WING_Y = 26, WING_ROW = 24, WING_ROWS = 5, COL_FUEL = 76, FUEL_W = 30, COL_ARMS = 132, COL_DMG = 158, DMG_W = 42, ORD_W = 26, ORD_STEP = 27,
            ORD_H = 14, FOOT_Y = 200;

    static final int LB_Y = 179, LB_W = 29, LB_STEP = 31, LB_H = 14, LB_N = 9;
    private static final String P = "gui.wmd.deck_console.";

    private static final int[] CHIP_BG = { 0xFF2A3340, 0xFF1F4D2E, 0xFF4D1F22, 0xFF4D3A1F, 0xFF1F3550 }, CHIP_FG = { 0xFFAFC0D2, WmdUi.GOOD, WmdUi.BAD, WmdUi.WARN, 0xFF6FA8FF };

    private record Row(int kind, Component name, Component state, int lamp, Component button, int action, long target, int arg, boolean enabled, boolean danger,
                       long hiPos) {}

    private final BlockPos pos;
    private int tab, scroll, ticks, selRow = -1;

    private boolean picking;
    private long pickTarget;
    private int pickY, pickHeading;
    static final int PK_W = 132, PK_H = 62;
    private static final int[] PK_ORDERS = { TurntableBlockEntity.O_LEFT, TurntableBlockEntity.O_HALF, TurntableBlockEntity.O_RIGHT, TurntableBlockEntity.O_NORTH,
            TurntableBlockEntity.O_EAST, TurntableBlockEntity.O_SOUTH, TurntableBlockEntity.O_WEST };
    private static final String[] PK_KEYS = { "gui.wmd.turntable.left", "gui.wmd.turntable.half", "gui.wmd.turntable.right", "gui.wmd.turntable.north",
            "gui.wmd.turntable.east", "gui.wmd.turntable.south", "gui.wmd.turntable.west", "gui.wmd.deck_console.b_cancel" };

    private int pkX(int x) { return x + SIDE_X - PK_W - 4; }
    private int pkY(int y) { return Math.max(y + MAP_Y, Math.min(pickY, y + MAP_Y + MAP_H - PK_H)); }

    private static int[] pkBtn(int x0, int y0, int k) {
        if (k < 3) { int w = (PK_W - 12 - 4) / 3; return new int[] { x0 + 6 + k * (w + 2), y0 + 14, w, 13 }; }
        if (k < 7) { int w = (PK_W - 12 - 6) / 4; return new int[] { x0 + 6 + (k - 3) * (w + 2), y0 + 30, w, 13 }; }
        return new int[] { x0 + 6, y0 + 46, PK_W - 12, 13 };
    }
    private long confirmUntil = Long.MIN_VALUE, confirmTarget;
    private List<Row> rows = List.of();

    private final java.util.Set<Long> picked = new java.util.LinkedHashSet<>();
    private double dragX0 = Double.NaN, dragY0, dragX1, dragY1;
    private static final String[] LB_KEYS = { "lb_all", "lb_none", "lb_blue", "lb_white", "lb_red", "lb_green", "lb_beacon", "lb_off", "lb_on" };

    DeckConsoleScreen(BlockPos pos) {
        super(Component.translatable("block.wmd.deck_console"));
        this.pos = pos;
    }

    BlockPos pos() { return pos; }

    @Override
    protected void init() {
        super.init();
        DeckConsoleBlockEntity b = be();
        rows = b == null ? List.of() : deckRows(b);
    }

    private int px() { return (width - PW) / 2 + 12; }
    private int py() { return (height - PH) / 2; }

    private DeckConsoleBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DeckConsoleBlockEntity b ? b : null;
    }

    private long now() { return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L; }

    private void ask(int action, long target, UUID ship, int arg) {
        PacketDistributor.sendToServer(new DeckConsolePayloads.Ask(pos, action, target, ship, arg));
    }

    @Override
    public void tick() {
        DeckConsoleBlockEntity b = be();
        if (b == null || minecraft.player == null || !Reach.within(minecraft.player, pos, Balance.FDC_REACH + 2.0)) { onClose(); return; }
        if (ticks++ % Balance.FDC_SCAN_TICKS == Balance.FDC_SCAN_TICKS - 1) ask(DeckConsoleBlockEntity.A_REFRESH, 0L, null, 0);
        rows = deckRows(b);
    }

    @Override public boolean isPauseScreen() { return false; }

    private static Component t(String k, Object... a) { return Component.translatable(P + k, a); }

    private List<Row> deckRows(DeckConsoleBlockEntity b) {
        List<Row> l = new ArrayList<>();
        ListTag m = b.marks();
        List<CompoundTag> wing = DeckConsoleClient.rows(pos);
        int cats = 0, ramps = 0, wires = 0, wireWorst = 0, lights = 0, lit = 0, beaconOn = -1;
        long beaconPos = 0L;
        boolean consoleOff = false;
        long now = now();
        for (Tag tg : m) {
            CompoundTag x = (CompoundTag) tg;
            int k = x.getByte("k"), s = x.getInt("s");
            long p = x.getLong("p");
            switch (k) {
                case DeckConsoleBlockEntity.K_LIFT -> {
                    boolean up = s == DeckLiftBlockEntity.UP, down = s == DeckLiftBlockEntity.DOWN;
                    l.add(new Row(k, t("lift", x.getInt("a"), x.getInt("b")), t(up ? "st_up" : down ? "st_down" : "st_moving"), up ? WmdUi.K_GOOD : down ? WmdUi.K_IDLE : WmdUi.K_WARN,
                            t(up ? "b_lower" : down ? "b_raise" : "b_wait"), DeckConsoleBlockEntity.A_LIFT, p, 0, up || down, false, p));
                }
                case DeckConsoleBlockEntity.K_CAT -> {
                    cats++;
                    boolean hooked = s == CatapultTrackBlockEntity.HOOKED;
                    String st = hooked ? "st_hooked" : s == CatapultTrackBlockEntity.STROKE ? "st_stroke" : s == CatapultTrackBlockEntity.RETURN ? "st_return" : "st_ready";
                    boolean armed = confirmTarget == p && now < confirmUntil;
                    l.add(new Row(k, t("cat", cats), t(st), hooked ? WmdUi.K_WARN : s == CatapultTrackBlockEntity.IDLE ? WmdUi.K_IDLE : WmdUi.K_GOOD,
                            t(armed ? "b_launch_confirm" : "b_launch"), DeckConsoleBlockEntity.A_LAUNCH, p, 0, hooked, true, p));
                }
                case DeckConsoleBlockEntity.K_WIRE -> { wires++; wireWorst = Math.max(wireWorst, s); }
                case DeckConsoleBlockEntity.K_LIGHT -> {
                    if (x.getInt("m") == DeckLightBlockEntity.BEACON) { beaconOn = Math.max(beaconOn, x.getBoolean("on") ? 1 : 0); beaconPos = p; }
                    lights++;
                    if (x.getBoolean("on")) lit++;
                    consoleOff |= x.getBoolean("o");
                }
                case DeckConsoleBlockEntity.K_RAMP -> {
                    ramps++;
                    int c = x.getInt("c");
                    Component name = c >= 0 && c < wing.size() ? t("ramp_of", wing.get(c).getString("n")) : t("ramp", ramps);
                    boolean closed = s == CargoRampBlockEntity.S_CLOSED, open = s == CargoRampBlockEntity.S_LEVEL || s == CargoRampBlockEntity.S_GROUND;
                    String st = closed ? "st_closed" : s == CargoRampBlockEntity.S_LEVEL ? "st_level" : s == CargoRampBlockEntity.S_GROUND ? "st_ground"
                            : s == CargoRampBlockEntity.S_NO_RECT ? "st_no_rect" : "st_moving";
                    l.add(new Row(k, name, t(st), open ? WmdUi.K_GOOD : closed ? WmdUi.K_IDLE : WmdUi.K_WARN, t(closed ? "b_open" : open ? "b_close" : "b_wait"),
                            DeckConsoleBlockEntity.A_RAMP, p, 0, closed || open, false, p));
                }
                case DeckConsoleBlockEntity.K_TURN -> {
                    boolean busy = s == TurntableBlockEntity.S_TURNING;
                    String st = busy ? "st_turn_moving" : s == TurntableBlockEntity.S_BLOCKED ? "st_turn_stopped" : s == TurntableBlockEntity.S_NO_POWER ? "st_no_power" : "st_turn_ready";
                    int lamp = busy ? WmdUi.K_WARN : s == TurntableBlockEntity.S_IDLE ? WmdUi.K_GOOD : WmdUi.K_IDLE;
                    l.add(new Row(k, t("turn_name"), t(st), lamp, t("b_turn"), DeckConsoleBlockEntity.A_TURN, p, -1, !busy, false, p));
                }
                case DeckConsoleBlockEntity.K_DOOR -> {
                    boolean open = s == HangarDoorBlockEntity.S_OPEN, moving = s == HangarDoorBlockEntity.S_OPENING || s == HangarDoorBlockEntity.S_CLOSING;
                    String st = open ? "st_open" : s == HangarDoorBlockEntity.S_CLOSED ? "st_closed" : moving ? "st_moving" : s == HangarDoorBlockEntity.S_BLOCKED ? "st_blocked"
                            : s == HangarDoorBlockEntity.S_NO_POWER ? "st_no_power" : s == HangarDoorBlockEntity.S_HELD ? "st_held" : "st_no_rect";
                    l.add(new Row(k, t("door", x.getInt("a")), t(st), open ? WmdUi.K_GOOD : s == HangarDoorBlockEntity.S_CLOSED ? WmdUi.K_IDLE : WmdUi.K_WARN,
                            t(open ? "b_close" : moving ? "b_wait" : "b_open"), DeckConsoleBlockEntity.A_DOOR, p, open ? 0 : 1, !moving && s != HangarDoorBlockEntity.S_NO_RUN, false, p));
                }
                default -> { }
            }
        }
        if (wires > 0) l.add(new Row(DeckConsoleBlockEntity.K_WIRE, t("wires", wires), t(wireWorst == 1 ? "st_caught" : wireWorst == 2 ? "st_reset" : "st_armed"),
                wireWorst == 0 ? WmdUi.K_GOOD : WmdUi.K_WARN, null, -1, 0L, 0, false, false, 0L));
        if (lights > 0) l.add(new Row(DeckConsoleBlockEntity.K_LIGHT, t("lights", lights), t(consoleOff ? "st_lights_out" : "st_lights", lit, lights),
                lit > 0 ? WmdUi.K_GOOD : WmdUi.K_IDLE, t(consoleOff ? "b_on" : "b_off"), DeckConsoleBlockEntity.A_LIGHTS, 0L, consoleOff ? 0 : 1, true, false, 0L));
        if (beaconOn >= 0) l.add(new Row(DeckConsoleBlockEntity.K_LIGHT, t("beacon"), t(beaconOn > 0 ? "st_on" : "st_off"), beaconOn > 0 ? WmdUi.K_GOOD : WmdUi.K_IDLE,
                null, -1, 0L, 0, false, false, beaconPos));
        return l;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        DeckConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py();
        for (int i = 0; i < 2; i++) if (i != tab) WmdUi.tab(g, x - 23, y + 22 + i * 24, i == 0 ? "icon_deck" : "icon_wing", false, WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22));
        WmdUi.panel(g, x, y, PW, PH);
        WmdUi.tab(g, x - 23, y + 22 + tab * 24, tab == 0 ? "icon_deck" : "icon_wing", true, false);
        boolean np = b.noPower() || DeckConsoleClient.noPower(pos);
        List<CompoundTag> wing = DeckConsoleClient.rows(pos);
        Component chip = np ? t("chip_no_power") : tab == 0 ? t("chip_linked", rows.size()) : t("chip_parked", wing.size());
        int kind = np ? WmdUi.K_BAD : (tab == 0 ? rows.isEmpty() : wing.isEmpty()) ? WmdUi.K_IDLE : WmdUi.K_GOOD;
        WmdUi.header(g, font, x, y, PW, new ItemStack(DeckConsoleBlocks.DECK_CONSOLE_ITEM.get()), Component.translatable("block.wmd.deck_console"), chip, kind, np);
        if (tab == 0) drawDeck(g, b, x, y, mx, my, pt, np);
        else drawWing(g, x, y, mx, my, wing, np);

        WmdUi.divider(g, x + 8, y + FOOT_Y - 4, PW - 16);
        String msg = DeckConsoleClient.msg(pos, now());
        boolean good = msg.equals("sent") || msg.equals("launch") || msg.startsWith("lights") || msg.equals("fuel") || msg.equals("stock") || msg.equals("fix");
        Component top = !msg.isEmpty() ? Component.translatable("message.wmd.deck_console." + msg) : tab == 0 ? t("hint_deck") : sumLine();
        WmdUi.drawFit(g, font, top, x + 8, y + FOOT_Y, PW - 16, msg.isEmpty() ? (tab == 0 ? WmdUi.TEXT : WmdUi.GOOD) : good ? WmdUi.GOOD : WmdUi.WARN);
        WmdUi.drawFit(g, font, t(tab == 0 ? "hint_deck2" : "hint_wing"), x + 8, y + FOOT_Y + 11, PW - 16, WmdUi.TEXT_MUTED);
    }

    private Component sumLine() {
        CompoundTag s = DeckConsoleClient.sum(pos);
        return t("sum", s.getInt("arms"), s.getInt("armsBusy"), s.getInt("depots"), s.getInt("rounds"), s.getInt("gantries"), s.getInt("gantryOn"));
    }

    private int hoverRow(int x, int y, double mx, double my) {
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++)
            if (WmdUi.over(mx, my, x + SIDE_X - 2, y + MAP_Y + i * ROW_H - 1, SIDE_W + 2, ROW_H)) return scroll + i;
        return -1;
    }

    private void drawDeck(GuiGraphics g, DeckConsoleBlockEntity b, int x, int y, int mx, int my, float pt, boolean np) {
        int hr = hoverRow(x, y, mx, my), hl = hr >= 0 ? hr : selRow;
        Row h = hl >= 0 && hl < rows.size() ? rows.get(hl) : null;
        int mx0 = x + MAP_X, my0 = y + MAP_Y;
        WmdUi.well(g, mx0, my0, MAP_W, MAP_H);
        g.enableScissor(mx0, my0, mx0 + MAP_W, my0 + MAP_H);
        DeckConsolePicture.map(new LiveScreenRenderer.GuiCanvas(g, font).box(mx0, my0, MAP_W, MAP_H), b.marks(), h == null ? -1 : h.kind(), h == null ? 0L : h.hiPos(), -1,
                now() + pt);

        net.minecraft.nbt.ListTag marks = b.marks();
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag m = marks.getCompound(i);
            if (m.getByte("k") != DeckConsoleBlockEntity.K_LIGHT || !picked.contains(m.getLong("p"))) continue;
            float[] c = DeckConsolePicture.centre(marks, MAP_W, MAP_H, m);
            int cx = mx0 + Math.round(c[0]), cy = my0 + Math.round(c[1]);
            g.renderOutline(cx - 3, cy - 3, 7, 7, 0xFF6FE0FF);
        }
        if (!Double.isNaN(dragX0)) {
            int ax = (int) Math.min(dragX0, dragX1), ay = (int) Math.min(dragY0, dragY1), bw = (int) Math.abs(dragX1 - dragX0), bh = (int) Math.abs(dragY1 - dragY0);
            g.fill(ax, ay, ax + bw, ay + bh, 0x306FE0FF);
            g.renderOutline(ax, ay, Math.max(1, bw), Math.max(1, bh), 0xFF6FE0FF);
        }
        g.disableScissor();

        for (int i = 0; i < LB_N; i++) {
            int bx = x + 8 + i * LB_STEP, by = y + LB_Y;
            boolean sel = i >= 2, on = !np && (!sel || !picked.isEmpty()), over = WmdUi.over(mx, my, bx, by, LB_W, LB_H);
            WmdUi.button(g, font, bx, by, LB_W, LB_H, t(LB_KEYS[i]), !on ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, over && on);
            if (i >= 2 && i <= 6) g.fill(bx + 2, by + LB_H - 3, bx + LB_W - 2, by + LB_H - 2, DeckConsolePicture.LENS[i - 2]);
        }
        if (rows.isEmpty()) WmdUi.drawFit(g, font, t("empty_deck"), x + SIDE_X, y + MAP_Y + 2, SIDE_W, WmdUi.TEXT_MUTED);
        for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int ry = y + MAP_Y + i * ROW_H, sx = x + SIDE_X;
            if (scroll + i == hl) g.fill(sx - 2, ry - 1, sx + SIDE_W, ry + ROW_H - 1, 0xFF24384E);
            WmdUi.led(g, sx, ry + 2, r.lamp(), false);
            int tw = r.button() == null ? SIDE_W - 8 : SIDE_W - 10 - BTN_W;
            WmdUi.drawFit(g, font, r.name(), sx + 8, ry, tw, WmdUi.TEXT);
            WmdUi.drawFit(g, font, r.state(), sx + 8, ry + 9, tw, r.lamp() == WmdUi.K_GOOD ? WmdUi.GOOD : r.lamp() == WmdUi.K_WARN ? WmdUi.WARN : WmdUi.TEXT_MUTED);
            if (r.button() != null) {
                boolean on = r.enabled() && !np, over = WmdUi.over(mx, my, sx + SIDE_W - BTN_W, ry + 3, BTN_W, BTN_H);
                WmdUi.button(g, font, sx + SIDE_W - BTN_W, ry + 3, BTN_W, BTN_H, r.button(), !on ? WmdUi.B_DISABLED : r.danger() ? WmdUi.B_DANGER : WmdUi.B_NORMAL, over && on);
            }
            if (scroll + i + 1 < rows.size() && i + 1 < ROWS) g.fill(sx, ry + ROW_H - 1, sx + SIDE_W, ry + ROW_H, 0xFF222932);
        }
        if (rows.size() > ROWS) {
            int bx = x + SIDE_X + SIDE_W + 1, total = ROWS * ROW_H, bh = Math.max(8, total * ROWS / rows.size()), by = y + MAP_Y + (total - bh) * scroll / Math.max(1, rows.size() - ROWS);
            g.fill(bx, by, bx + 2, by + bh, 0xFF3A4552);
        }
        if (picking) {
            int x0 = pkX(x), y0 = pkY(y);
            g.fill(x0 - 1, y0 - 1, x0 + PK_W + 1, y0 + PK_H + 1, WmdUi.BLUE);
            g.fill(x0, y0, x0 + PK_W, y0 + PK_H, 0xF4121A22);
            WmdUi.drawFit(g, font, t("pick_title", pickHeading), x0 + 6, y0 + 3, PK_W - 12, WmdUi.TEXT);
            for (int k = 0; k < 8; k++) {
                int[] q = pkBtn(x0, y0, k);
                boolean on = k == 7 || !np;
                WmdUi.button(g, font, q[0], q[1], q[2], q[3], Component.translatable(PK_KEYS[k]), on ? WmdUi.B_NORMAL : WmdUi.B_DISABLED, on && WmdUi.over(mx, my, q[0], q[1], q[2], q[3]));
            }
        }
    }

    private int wingRowY(int y, int i) { return y + WING_Y + 14 + i * WING_ROW; }

    private int ordX(int x, int k) { return x + PW - 8 - 3 * ORD_STEP + k * ORD_STEP + 1; }

    private static final String[] ORDER = { "b_fuel", "b_arm", "b_fix" };

    private static boolean[] order(CompoundTag r, int k) {
        int f = r.getInt("f");
        return switch (k) {
            case 0 -> new boolean[] { (f & DeckConsoleBlockEntity.W_FUEL) != 0, (f & DeckConsoleBlockEntity.W_FUEL_BUSY) != 0 };
            case 1 -> new boolean[] { (f & DeckConsoleBlockEntity.W_STOCK) != 0, (f & DeckConsoleBlockEntity.W_STOCK_ON) != 0 };
            default -> new boolean[] { (f & DeckConsoleBlockEntity.W_FIX) != 0, (f & DeckConsoleBlockEntity.W_FIX_ON) != 0 };
        };
    }

    private void drawWing(GuiGraphics g, int x, int y, int mx, int my, List<CompoundTag> wing, boolean np) {
        int x0 = x + 8, y0 = y + WING_Y;
        WmdUi.drawFit(g, font, t("col_craft"), x0, y0, COL_FUEL - 4, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t("col_fuel"), x0 + COL_FUEL, y0, COL_ARMS - COL_FUEL - 4, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t("col_arms"), x0 + COL_ARMS, y0, COL_DMG - COL_ARMS - 2, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t("col_damage"), x0 + COL_DMG, y0, DMG_W, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t("col_orders"), ordX(x, 0), y0, 3 * ORD_STEP, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x0, y0 + 10, PW - 16);
        if (wing.isEmpty()) WmdUi.drawFit(g, font, t("empty_wing"), x0, y0 + 16, PW - 16, WmdUi.TEXT_MUTED);
        for (int i = 0; i < WING_ROWS && scroll + i < wing.size(); i++) {
            CompoundTag r = wing.get(scroll + i);
            int ry = wingRowY(y, i);
            if (WmdUi.over(mx, my, x0 - 2, ry - 2, PW - 12, WING_ROW)) g.fill(x0 - 2, ry - 2, x0 + PW - 14, ry + WING_ROW - 3, 0xFF24384E);
            WmdUi.drawFit(g, font, Component.literal(r.getString("n")), x0, ry, COL_FUEL - 4, WmdUi.TEXT);
            int dx = r.getInt("x"), dz = r.getInt("z");
            WmdUi.drawFit(g, font, t("where", (int) Math.round(Math.hypot(dx, dz)), AirframeRecords.compass(dx, dz)), x0, ry + 10, COL_FUEL - 4, WmdUi.TEXT_MUTED);
            int cap = r.getInt("fuelCap");
            if (cap > 0) {
                float fr = Math.min(1.0F, r.getInt("fuel") / (float) cap);
                WmdUi.well(g, x0 + COL_FUEL, ry + 3, FUEL_W, 5);
                g.fill(x0 + COL_FUEL, ry + 3, x0 + COL_FUEL + Math.round(FUEL_W * fr), ry + 8, fr > 0.25F ? 0xFF3AC2D5 : 0xFFE0A030);
                WmdUi.drawFit(g, font, Component.literal(Math.round(fr * 100) + "%"), x0 + COL_FUEL, ry + 10, COL_ARMS - COL_FUEL - 4, WmdUi.TEXT_MUTED);
            } else WmdUi.drawFit(g, font, t("none"), x0 + COL_FUEL, ry + 4, COL_ARMS - COL_FUEL - 4, WmdUi.TEXT_MUTED);
            int st = r.getInt("st");
            WmdUi.drawFit(g, font, st > 0 ? Component.literal(r.getInt("sl") + "/" + st) : t("none"), x0 + COL_ARMS, ry + 4, COL_DMG - COL_ARMS - 2, WmdUi.TEXT);
            int d = r.getInt("dmg"), dk = d == 0 ? WmdUi.K_GOOD : d < 0 ? WmdUi.K_IDLE : d <= 5 ? WmdUi.K_WARN : WmdUi.K_BAD;
            g.fill(x0 + COL_DMG, ry + 2, x0 + COL_DMG + DMG_W, ry + 13, CHIP_BG[dk]);
            WmdUi.led(g, x0 + COL_DMG + 3, ry + 5, dk, false);
            WmdUi.drawFit(g, font, d == 0 ? t("dmg_ok") : d == -1 ? t("dmg_none") : d == -2 ? t("dmg_big") : t("dmg_hits", d), x0 + COL_DMG + 10, ry + 4, DMG_W - 12,
                    CHIP_FG[dk]);
            for (int k = 0; k < 3; k++) {
                boolean[] o = order(r, k);
                boolean on = o[0] && !np;
                int bx = ordX(x, k);
                WmdUi.button(g, font, bx, ry + 1, ORD_W, ORD_H, o[1] && !o[0] ? Component.literal("...") : t(ORDER[k]), on ? WmdUi.B_NORMAL : WmdUi.B_DISABLED,
                        on && WmdUi.over(mx, my, bx, ry + 1, ORD_W, ORD_H));
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = px(), y = py();
        for (int i = 0; i < 2; i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { g.renderTooltip(font, t(i == 0 ? "tab_deck" : "tab_wing"), mx, my); return; }
        DeckConsoleBlockEntity b = be();
        if (b == null) return;
        if (tab == 0) {
            if (WmdUi.over(mx, my, x + MAP_X, y + MAP_Y, MAP_W, MAP_H)) {
                int i = DeckConsolePicture.markAt(b.marks(), MAP_W, MAP_H, mx - x - MAP_X, my - y - MAP_Y);
                int r = i < 0 ? -1 : rowOfMark(b.marks().getCompound(i));
                if (r >= 0) g.renderTooltip(font, rows.get(r).name(), mx, my);
            }
            return;
        }
        List<CompoundTag> wing = DeckConsoleClient.rows(pos);
        boolean np = b.noPower() || DeckConsoleClient.noPower(pos);
        for (int i = 0; i < WING_ROWS && scroll + i < wing.size(); i++)
            for (int k = 0; k < 3; k++)
                if (WmdUi.over(mx, my, ordX(x, k), wingRowY(y, i) + 1, ORD_W, ORD_H)) { g.renderTooltip(font, orderTip(wing.get(scroll + i), k, np), mx, my); return; }
    }

    private Component orderTip(CompoundTag r, int k, boolean np) {
        if (np) return t("tip_no_power");
        boolean[] o = order(r, k);
        CompoundTag s = DeckConsoleClient.sum(pos);
        String[] what = { "fuel", "arm", "fix" }, machines = { "arms", "depots", "gantries" };
        if (o[0]) return t("tip_" + what[k]);
        if (o[1]) return t("tip_" + what[k] + "_busy");
        return t(s.getInt(machines[k]) == 0 ? "tip_" + what[k] + "_none" : "tip_" + what[k] + "_out");
    }

    private int rowOfMark(CompoundTag m) {
        int k = m.getByte("k");
        long p = m.getLong("p");
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.kind() != k) continue;
            if (k == DeckConsoleBlockEntity.K_WIRE) return i;
            if (k == DeckConsoleBlockEntity.K_LIGHT) { if (r.hiPos() == p || (r.hiPos() == 0L && m.getInt("m") != DeckLightBlockEntity.BEACON)) return i; continue; }
            if (r.target() == p) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        int x = px(), y = py();
        for (int i = 0; i < 2; i++)
            if (WmdUi.over(mx, my, x - 23, y + 22 + i * 24, 23, 22)) { if (tab != i) { tab = i; scroll = 0; selRow = -1; picking = false; WmdUi.tab(); } return true; }
        DeckConsoleBlockEntity b = be();
        if (b == null) return false;
        boolean np = b.noPower() || DeckConsoleClient.noPower(pos);
        if (tab == 0) {
            if (picking) {
                int x0 = pkX(x), y0 = pkY(y);
                for (int k = 0; k < 8; k++) {
                    int[] q = pkBtn(x0, y0, k);
                    if (!WmdUi.over(mx, my, q[0], q[1], q[2], q[3])) continue;
                    picking = false;
                    if (k == 7) { WmdUi.click(); return true; }
                    if (np) { WmdUi.deny(); return true; }
                    WmdUi.click();
                    ask(DeckConsoleBlockEntity.A_TURN, pickTarget, null, PK_ORDERS[k]);
                    return true;
                }
                picking = false;
                if (WmdUi.over(mx, my, x0, y0, PK_W, PK_H)) return true;
            }
            for (int i = 0; i < LB_N; i++) {
                if (!WmdUi.over(mx, my, x + 8 + i * LB_STEP, y + LB_Y, LB_W, LB_H)) continue;
                if (np || (i >= 2 && picked.isEmpty())) { WmdUi.deny(); return true; }
                if (i == 0) { picked.clear(); for (int k = 0; k < b.marks().size(); k++) { CompoundTag m = b.marks().getCompound(k); if (m.getByte("k") == DeckConsoleBlockEntity.K_LIGHT) picked.add(m.getLong("p")); } }
                else if (i == 1) picked.clear();
                else {
                    int mode = i <= 6 ? i - 2 : i == 7 ? DeckConsoleBlockEntity.LS_OFF : DeckConsoleBlockEntity.LS_ON;
                    long[] ts = picked.stream().limit(Balance.FDC_MAX_LIGHTS).mapToLong(Long::longValue).toArray();
                    PacketDistributor.sendToServer(new DeckConsolePayloads.LightSet(pos, ts, mode));
                }
                WmdUi.click();
                return true;
            }
            if (WmdUi.over(mx, my, x + MAP_X, y + MAP_Y, MAP_W, MAP_H)) {
                int i = DeckConsolePicture.markAt(b.marks(), MAP_W, MAP_H, mx - x - MAP_X, my - y - MAP_Y);
                if (i >= 0 && b.marks().getCompound(i).getByte("k") == DeckConsoleBlockEntity.K_LIGHT) {
                    long lp = b.marks().getCompound(i).getLong("p");
                    if (!picked.remove(lp)) picked.add(lp);
                    WmdUi.click();
                    return true;
                }
                int r = i < 0 ? -1 : rowOfMark(b.marks().getCompound(i));
                if (r >= 0) { selRow = r; if (r < scroll) scroll = r; else if (r >= scroll + ROWS) scroll = r - ROWS + 1; WmdUi.click(); return true; }
                dragX0 = dragX1 = mx; dragY0 = dragY1 = my;
                return true;
            }
            for (int i = 0; i < ROWS && scroll + i < rows.size(); i++) {
                Row r = rows.get(scroll + i);
                int ry = y + MAP_Y + i * ROW_H, bx = x + SIDE_X + SIDE_W - BTN_W;
                if (r.button() == null || !WmdUi.over(mx, my, bx, ry + 3, BTN_W, BTN_H)) continue;
                if (!r.enabled() || np) { WmdUi.deny(); return true; }
                if (r.action() == DeckConsoleBlockEntity.A_LAUNCH && !(confirmTarget == r.target() && now() < confirmUntil)) {
                    confirmTarget = r.target();
                    confirmUntil = now() + Balance.FDC_CONFIRM_TICKS;
                    WmdUi.click();
                    rows = deckRows(b);
                    return true;
                }
                if (r.action() == DeckConsoleBlockEntity.A_TURN && r.arg() < 0) {
                    picking = true;
                    pickTarget = r.target();
                    pickY = ry - 2;
                    pickHeading = 0;
                    for (int m = 0; m < b.marks().size(); m++)
                        if (b.marks().getCompound(m).getLong("p") == pickTarget) pickHeading = b.marks().getCompound(m).getInt("h");
                    WmdUi.click();
                    return true;
                }
                confirmUntil = Long.MIN_VALUE;
                if (r.danger()) WmdUi.confirm(); else WmdUi.click();
                ask(r.action(), r.target(), null, r.arg());
                return true;
            }
            int hr = hoverRow(x, y, mx, my);
            if (hr >= 0) { selRow = hr; return true; }
            return false;
        }
        List<CompoundTag> wing = DeckConsoleClient.rows(pos);
        int[] acts = { DeckConsoleBlockEntity.A_FUEL, DeckConsoleBlockEntity.A_STOCK, DeckConsoleBlockEntity.A_FIX };
        for (int i = 0; i < WING_ROWS && scroll + i < wing.size(); i++)
            for (int k = 0; k < 3; k++) {
                if (!WmdUi.over(mx, my, ordX(x, k), wingRowY(y, i) + 1, ORD_W, ORD_H)) continue;
                CompoundTag r = wing.get(scroll + i);
                if (!order(r, k)[0] || np || !r.hasUUID("id")) { WmdUi.deny(); return true; }
                WmdUi.click();
                ask(acts[k], 0L, r.getUUID("id"), 0);
                return true;
            }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!Double.isNaN(dragX0)) {
            int x = px(), y = py();
            dragX1 = Math.max(x + MAP_X, Math.min(x + MAP_X + MAP_W, mx));
            dragY1 = Math.max(y + MAP_Y, Math.min(y + MAP_Y + MAP_H, my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (Double.isNaN(dragX0)) return super.mouseReleased(mx, my, button);
        DeckConsoleBlockEntity b = be();
        int x = px(), y = py();
        double ax = Math.min(dragX0, dragX1) - x - MAP_X, bx = Math.max(dragX0, dragX1) - x - MAP_X, ay = Math.min(dragY0, dragY1) - y - MAP_Y, by = Math.max(dragY0, dragY1) - y - MAP_Y;
        dragX0 = Double.NaN;
        if (b == null || bx - ax < 3 && by - ay < 3) return true;
        net.minecraft.nbt.ListTag marks = b.marks();
        int n = 0;
        for (int i = 0; i < marks.size(); i++) {
            CompoundTag m = marks.getCompound(i);
            if (m.getByte("k") != DeckConsoleBlockEntity.K_LIGHT) continue;
            float[] c = DeckConsolePicture.centre(marks, MAP_W, MAP_H, m);
            if (c[0] >= ax && c[0] <= bx && c[1] >= ay && c[1] <= by && picked.add(m.getLong("p"))) n++;
        }
        if (n > 0) WmdUi.click();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int n = tab == 0 ? rows.size() - ROWS : DeckConsoleClient.rows(pos).size() - WING_ROWS;
        if (n <= 0) { scroll = 0; return false; }
        scroll = Math.max(0, Math.min(n, scroll - (int) Math.signum(dy)));
        return true;
    }

}
