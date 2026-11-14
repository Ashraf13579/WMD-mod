package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class FireControlScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 172, PH = 224, PAGE_Y = 24, MAP_Y = 37, MAP_H = 60, ROW_Y = 128;
    static final int S_LINE_Y = 38, S_BOX_Y = 48, S_HINT_Y = 65, S_DETAIL_Y = 86, S_OWN_Y = 97, S_OWN_BTN_Y = 109;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, WARN = WmdUi.WARN, BAD = WmdUi.BAD;
    private static final String P = "gui.wmd.fcc.";

    private static boolean strikePage;

    private static boolean gunsPage;

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private boolean filled;

    public FireControlScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private FireControlConsoleBlockEntity be() { return menu.machine() instanceof FireControlConsoleBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private void send(int action, int a, int b) {
        FireControlConsoleBlockEntity c = be();
        if (c != null) PacketDistributor.sendToServer(new FireControlPayload(c.getBlockPos(), action, a, b));
    }

    @Override
    protected void init() {
        super.init();
        FireControlConsoleBlockEntity c = be();
        xBox = zBox = null;
        if (c == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, c.getBlockPos())));
        addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this, c::frequency, f -> send(FireControlConsoleBlockEntity.A_FREQ, f, 0))));
        int x = px(), y = py();

        WmdUi.KitButton torp = new WmdUi.KitButton(x + 42, y + PAGE_Y, 40, 11, Component.translatable(P + "page_torp"), b -> page(0));
        WmdUi.KitButton strike = new WmdUi.KitButton(x + 84, y + PAGE_Y, 40, 11, Component.translatable(P + "page_strike"), b -> page(1));
        WmdUi.KitButton guns = new WmdUi.KitButton(x + 126, y + PAGE_Y, 40, 11, Component.translatable(P + "page_guns"), b -> page(2));
        torp.active = strikePage || gunsPage;
        strike.active = !strikePage;
        guns.active = !gunsPage;
        addRenderableWidget(torp);
        addRenderableWidget(strike);
        addRenderableWidget(guns);
        if (gunsPage) { initGuns(c, x, y); return; }
        if (strikePage) { initStrike(c, x, y); return; }

        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 111, 20, 12, Component.literal("<"), b -> pickTarget(-1)));
        addRenderableWidget(new WmdUi.KitButton(x + 28, y + 111, 20, 12, Component.literal(">"), b -> pickTarget(1)));
        addRenderableWidget(new WmdUi.KitButton(x + 50, y + 111, 40, 12, Component.translatable(P + "clear"), b -> send(FireControlConsoleBlockEntity.A_TARGET, -1, 0)));
        addRenderableWidget(new WmdUi.KitButton(x + 92, y + 111, 74, 12, Component.translatable(P + "solve"), b -> send(FireControlConsoleBlockEntity.A_SOLVE, 0, 0)));

        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 190, 38, 12, Component.translatable(P + "depth_minus"), b -> adjust(FireControlConsoleBlockEntity.A_DEPTH, "d", -1, 0, (int) Balance.TORP_MAX_DEPTH)));
        addRenderableWidget(new WmdUi.KitButton(x + 46, y + 190, 38, 12, Component.translatable(P + "depth_plus"), b -> adjust(FireControlConsoleBlockEntity.A_DEPTH, "d", 1, 0, (int) Balance.TORP_MAX_DEPTH)));
        addRenderableWidget(new WmdUi.KitButton(x + 86, y + 190, 38, 12, Component.translatable(P + "gyro_left"), b -> adjust(FireControlConsoleBlockEntity.A_GYRO, "g", -5, -(int) Balance.GYRO_MAX, (int) Balance.GYRO_MAX)));
        addRenderableWidget(new WmdUi.KitButton(x + 126, y + 190, 40, 12, Component.translatable(P + "gyro_right"), b -> adjust(FireControlConsoleBlockEntity.A_GYRO, "g", 5, -(int) Balance.GYRO_MAX, (int) Balance.GYRO_MAX)));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 206, 78, 14, Component.translatable(P + "fire"), b -> send(FireControlConsoleBlockEntity.A_FIRE, c.selected(), 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 206, 78, 14, Component.translatable(P + "salvo"), b -> send(FireControlConsoleBlockEntity.A_SALVO, 0, 0), true));
    }

    private void initStrike(FireControlConsoleBlockEntity c, int x, int y) {
        if (!filled && c.strikeSet()) { xText = Integer.toString(c.strikeX()); zText = Integer.toString(c.strikeZ()); filled = true; }
        xBox = box(x + 6, y + S_BOX_Y, xText);
        zBox = box(x + 60, y + S_BOX_Y, zText);
        addRenderableWidget(new WmdUi.KitButton(x + 114, y + S_BOX_Y, 26, 14, Component.translatable("gui.wmd.lc.set"), b -> {
            Integer[] v = typed();
            if (v != null) send(FireControlConsoleBlockEntity.A_STRIKE, v[0], v[1]);
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 142, y + S_BOX_Y, 24, 14, Component.translatable("gui.wmd.lc.clear"), b -> {
            send(FireControlConsoleBlockEntity.A_STRIKE_CLR, 0, 0); xBox.setValue(""); zBox.setValue("");
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + S_OWN_BTN_Y, 96, 12, Component.translatable(P + "own_give"), b -> {
            Integer[] v = typed();
            if (v == null || c.strikeRow() == null) return;
            send(FireControlConsoleBlockEntity.A_OWN_X, c.strikeSelected(), v[0]);
            send(FireControlConsoleBlockEntity.A_OWN_Z, c.strikeSelected(), v[1]);
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 104, y + S_OWN_BTN_Y, 62, 12, Component.translatable(P + "own_clear"), b -> {
            if (c.strikeRow() != null) send(FireControlConsoleBlockEntity.A_OWN_CLR, c.strikeSelected(), 0);
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 206, 78, 14, Component.translatable(P + "fire"), b -> send(FireControlConsoleBlockEntity.A_SFIRE, c.strikeSelected(), 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 206, 78, 14, Component.translatable(P + "strike_salvo_btn"), b -> send(FireControlConsoleBlockEntity.A_SSALVO, 0, 0), true));
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, 50, 14, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    private static Integer parse(EditBox b) {
        try { return b == null || b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private Integer[] typed() {
        Integer x = parse(xBox), z = parse(zBox);
        if (x == null || z == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("gui.wmd.lc.need_xz"), true); return null; }
        return new Integer[] { x, z };
    }

    private void page(int p) {
        if (strikePage == (p == 1) && gunsPage == (p == 2)) return;
        strikePage = p == 1;
        gunsPage = p == 2;
        rebuildWidgets();
    }

    private void initGuns(FireControlConsoleBlockEntity c, int x, int y) {
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + S_OWN_BTN_Y, 96, 12, Component.translatable(P + "gun.heavy_btn"), b -> {
            CompoundTag r = c.gunRow();
            if (r != null && r.getBoolean("xl")) send(FireControlConsoleBlockEntity.A_GHEAVY, c.gunSelected(), 0);
        }));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 206, 78, 14, Component.translatable(P + "fire"), b -> send(FireControlConsoleBlockEntity.A_GFIRE, c.gunSelected(), 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 88, y + 206, 78, 14, Component.translatable(P + "salvo"), b -> send(FireControlConsoleBlockEntity.A_SALVO, 0, 0), true));
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private void pickTarget(int step) {
        FireControlConsoleBlockEntity c = be();
        if (c == null) return;
        int n = c.targetRows().size();
        if (n == 0) return;
        int t = c.target() < 0 ? (step > 0 ? 0 : n - 1) : Math.floorMod(c.target() + step, n);
        send(FireControlConsoleBlockEntity.A_TARGET, t, 0);
    }

    private void adjust(int action, String key, int step, int min, int max) {
        FireControlConsoleBlockEntity c = be();
        if (c == null) return;
        CompoundTag r = c.selectedRow();
        if (r == null || r.getBoolean("cell") || r.getBoolean("bat")) return;
        int s = hasShiftDown() ? step * 5 : step;
        send(action, c.selected(), Math.max(min, Math.min(max, r.getInt(key) + s)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        FireControlConsoleBlockEntity c = be();
        if (button == 0 && c != null) {
            int x = px(), y = py();
            int n = Math.min(Balance.FCC_ROWS, gunsPage ? c.gunRows().size() : strikePage ? c.strikeRows().size() : c.launcherRows().size());
            for (int i = 0; i < n; i++)
                if (WmdUi.over(mx, my, x + 5, y + ROW_Y + i * 10 - 1, PW - 10, 10)) {
                    send(gunsPage ? FireControlConsoleBlockEntity.A_GSEL : strikePage ? FireControlConsoleBlockEntity.A_SSEL : FireControlConsoleBlockEntity.A_SELECT, i, 0);
                    WmdUi.click(); return true;
                }

            int wx = x + 6, wy = y + MAP_Y, ww = PW - 12;
            if (!strikePage && !gunsPage && WmdUi.over(mx, my, wx, wy, ww, MAP_H)) {
                int t = FireControlPicture.targetAt(ww, MAP_H, c, mx - wx, my - wy);
                if (t >= 0) { send(FireControlConsoleBlockEntity.A_TARGET, t, 0); WmdUi.click(); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= py() && my < py() + PH; }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] { xBox, zBox })
                if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        FireControlConsoleBlockEntity c = be();
        if (c == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        boolean on = c.isRunning();
        List<CompoundTag> rows = gunsPage ? c.gunRows() : strikePage ? c.strikeRows() : c.launcherRows(), tg = c.targetRows();
        Component chip = !on ? Component.translatable(P + "offline") : rows.isEmpty() ? Component.translatable(P + (gunsPage ? "gun.none" : strikePage ? "s_none" : "none"))
                : Component.translatable(P + (gunsPage ? "gun.chip" : strikePage ? "s_chip" : "launchers"), rows.size());
        WmdUi.header(g, font, x, y, PW, new ItemStack(c.getBlockState().getBlock().asItem()), Component.translatable(c.getBlockState().getBlock().getDescriptionId()), chip,
                !on ? WmdUi.K_BAD : rows.isEmpty() ? WmdUi.K_WARN : WmdUi.K_GOOD, on && !rows.isEmpty());

        drawLeft(g, Component.translatable(P + "freq", c.frequency()), x + 6, y + PAGE_Y + 2, 34, GREY);
        int ux = gunsPage ? x + 126 : strikePage ? x + 84 : x + 42;
        g.fill(ux + 3, y + PAGE_Y + 11, ux + 37, y + PAGE_Y + 12, WmdUi.BLUE_LIGHT);
        if (gunsPage) { renderGuns(g, c, x, y, cx, rows); return; }
        if (strikePage) { renderStrike(g, c, x, y, cx, rows); return; }
        int wx = x + 6, wy = y + MAP_Y, ww = PW - 12;
        WmdUi.well(g, wx, wy, ww, MAP_H);
        g.enableScissor(wx, wy, wx + ww, wy + MAP_H);
        FireControlPicture.map(new LiveScreenRenderer.GuiCanvas(g, font).box(wx, wy, ww, MAP_H), c, c.getLevel() == null ? 0.0 : c.getLevel().getGameTime() + partialTick);
        g.disableScissor();

        CompoundTag t = c.targetRow();
        fit(g, t == null ? Component.translatable(tg.isEmpty() ? P + "no_targets" : P + "pick_target", tg.size())
                : FireControlPicture.targetWords(c, t, c.target()), cx, y + 101, t == null ? GREY : WmdUi.BAD);

        WmdUi.divider(g, x + 5, y + ROW_Y - 4, PW - 10);
        if (rows.isEmpty()) fit(g, Component.translatable(P + "how"), cx, y + ROW_Y + 8, GREY);
        for (int i = 0; i < rows.size() && i < Balance.FCC_ROWS; i++) drawRow(g, x, y, i, rows.get(i), i == c.selected(), FireControlPicture.launcherWords(rows.get(i), i));
    }

    private void renderStrike(GuiGraphics g, FireControlConsoleBlockEntity c, int x, int y, int cx, List<CompoundTag> rows) {
        fit(g, c.strikeSet() ? Component.translatable(P + "s_target", c.strikeX(), c.strikeZ()) : Component.translatable(P + "s_no_target"), cx, y + S_LINE_Y,
                c.strikeSet() ? TEXT : GREY);
        fit(g, Component.translatable(P + "s_hint1"), cx, y + S_HINT_Y, GREY);

        CompoundTag r = c.strikeRow();
        if (r == null) fit(g, Component.translatable(P + "s_pick"), cx, y + S_HINT_Y + 10, GREY);
        else {
            fit(g, FireControlPicture.rangeWords(r, c.strikeSelected()), cx, y + S_HINT_Y + 10, TEXT);
            int rc = r.getInt("rc");
            fit(g, FireControlPicture.statusWords(r), cx, y + S_DETAIL_Y, r.getInt("st") == TorpedoLauncherBlockEntity.S_READY ? GOOD : rc == FireControlConsoleBlockEntity.RC_OK ? TEXT : WARN);
            fit(g, FireControlPicture.aimWords(r), cx, y + S_OWN_Y, r.getInt("src") == LaunchCellBlockEntity.SRC_NONE ? GREY : TEXT);
        }
        WmdUi.divider(g, x + 5, y + ROW_Y - 4, PW - 10);
        if (rows.isEmpty()) fit(g, Component.translatable(P + "s_how"), cx, y + ROW_Y + 8, GREY);
        for (int i = 0; i < rows.size() && i < Balance.FCC_ROWS; i++) drawRow(g, x, y, i, rows.get(i), i == c.strikeSelected(), FireControlPicture.strikeWords(rows.get(i), i));
        fit(g, Component.translatable(P + "s_hint_fire"), cx, y + 192, GREY);
    }

    private void renderGuns(GuiGraphics g, FireControlConsoleBlockEntity c, int x, int y, int cx, List<CompoundTag> rows) {
        CompoundTag t = c.targetRow();
        Component tw = t != null ? Component.translatable(P + "gun.t_contact", c.target() + 1, t.getInt("x"), t.getInt("z"))
                : c.strikeSet() ? Component.translatable(P + "gun.t_xz", c.strikeX(), c.strikeZ()) : Component.translatable(P + "gun.t_none");
        fit(g, tw, cx, y + S_LINE_Y, t != null || c.strikeSet() ? TEXT : GREY);
        fit(g, Component.translatable(t != null && t.hasUUID("sh") ? P + "gun.lead" : t != null ? P + "gun.no_lead" : P + "gun.t_how"), cx, y + S_LINE_Y + 11, GREY);
        fit(g, Component.translatable(P + "gun.air"), cx, y + S_HINT_Y, GREY);
        CompoundTag r = c.gunRow();
        if (r == null) fit(g, Component.translatable(P + "gun.pick"), cx, y + S_HINT_Y + 10, GREY);
        else {
            int st = r.getInt("st");
            fit(g, Component.translatable(P + "gun.st_line", gunLabel(r, c.gunSelected()), Component.translatable(P + "gun.st." + st)), cx, y + S_HINT_Y + 10,
                    st == NavalGunBlockEntity.ST_READY ? GOOD : gunBad(st) ? BAD : WARN);
            int nx = r.getInt("nx");
            Component next = nx < 0 ? Component.translatable("message.wmd.naval_gun.none") : NavalGunBlockEntity.roundItem(nx).getHoverName();
            fit(g, r.getBoolean("xl") ? Component.translatable(P + "gun.ammo_xl", r.getInt("n"), r.getInt("max"), r.getInt("c"), r.getInt("cmax"), next,
                    Component.translatable(r.getBoolean("hv") ? P + "gun.mode_heavy" : P + "gun.mode_shell"))
                    : Component.translatable(P + "gun.ammo", r.getInt("n"), r.getInt("max"), next), cx, y + S_DETAIL_Y, TEXT);
            int rc = r.getInt("rc");
            fit(g, rc == FireControlConsoleBlockEntity.RC_NONE ? Component.translatable(P + "gun.r_none")
                    : Component.translatable(P + "gun.r_" + (rc == FireControlConsoleBlockEntity.RC_OK ? "ok" : rc == FireControlConsoleBlockEntity.RC_FAR ? "far" : "close"), r.getInt("d")),
                    cx, y + S_OWN_Y, rc == FireControlConsoleBlockEntity.RC_OK ? GOOD : rc == FireControlConsoleBlockEntity.RC_NONE ? GREY : WARN);
        }
        WmdUi.divider(g, x + 5, y + ROW_Y - 4, PW - 10);
        if (rows.isEmpty()) fit(g, Component.translatable(P + "gun.how"), cx, y + ROW_Y + 8, GREY);
        for (int i = 0; i < rows.size() && i < Balance.FCC_ROWS; i++) {
            CompoundTag row = rows.get(i);
            int ry = y + ROW_Y + i * 10, st = row.getInt("st");
            boolean sel = i == c.gunSelected();
            if (sel) g.fill(x + 5, ry - 1, x + PW - 5, ry + 9, 0x402D6BB0);
            g.fill(x + 7, ry, x + 9, ry + 8, st == NavalGunBlockEntity.ST_READY ? GOOD : gunBad(st) ? BAD : WARN);
            Component words = Component.translatable(P + "gun.row", gunLabel(row, i), Component.translatable(P + "gun.st." + st), row.getInt("n"),
                    row.getInt("d") < 0 ? "--" : String.format("%,d", row.getInt("d")));
            drawLeft(g, words, x + 12, ry, PW - 18, sel ? TEXT : GREY);
        }
        fit(g, Component.translatable(P + "gun.hint_fire"), cx, y + 192, GREY);
    }

    private static Component gunLabel(CompoundTag r, int i) { return Component.translatable(r.getBoolean("xl") ? P + "gun.label_xl" : P + "gun.label", i + 1); }

    private static boolean gunBad(int st) {
        return st == NavalGunBlockEntity.ST_EMPTY || st == NavalGunBlockEntity.ST_NO_CRAFT || st == NavalGunBlockEntity.ST_NO_POWER || st == NavalGunBlockEntity.ST_BLOCKED
                || st == NavalGunBlockEntity.ST_NO_CHARGE || st == NavalGunBlockEntity.ST_OUT;
    }

    private void drawRow(GuiGraphics g, int x, int y, int i, CompoundTag r, boolean sel, Component words) {
        int ry = y + ROW_Y + i * 10;
        if (sel) g.fill(x + 5, ry - 1, x + PW - 5, ry + 9, 0x402D6BB0);
        int st = r.getInt("st");
        int col = st == TorpedoLauncherBlockEntity.S_READY ? GOOD : st == TorpedoLauncherBlockEntity.S_NO_POWER || st == TorpedoLauncherBlockEntity.S_BLOCKED ? BAD : WARN;
        g.fill(x + 7, ry, x + 9, ry + 8, col);
        drawLeft(g, words, x + 12, ry, PW - 18, sel ? TEXT : GREY);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
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
