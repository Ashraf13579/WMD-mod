package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 132;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;

    static final int ROW_SEL = 0xFF2D3A4A, ROW_HOVER = 0xFF263040;
    private int mouseX, mouseY;
    private boolean autoOpened;

    private static final int TAB_H = 14, TABLE_LIST = 42 + TAB_H + CcBuildingBlockEntity.N * 10, AD_ROWS_Y = 44;
    static final int TAB_OVERVIEW = 0, TAB_DEFCON = 1, TAB_LOG = 2, TAB_SAT = 3, TAB_DRONES = 4;

    private static final int[] TAB_X = { 2, 29, 64, 87, 108 }, TAB_W = { 26, 34, 22, 20, 22 };

    static final int DR_Y = 34, DR_ROWS_Y = 45, DR_ROW = 11, DR_ROWS = 6, DR_BTN_Y = 113, SITE_Y = 133, SITE_ROWS_Y = 144, SITE_BTN_Y = 212, SITE_INFO_Y = 231;
    private Button recallButton, watchButton, launchButton, routeButton;
    private int selDrone = -1;
    private BlockPos selSite;
    private static final int SAT_ROWS_Y = 46, SAT_BTN_Y = 140;
    static final int LOG_Y = 34, LOG_ROW = 20, LOG_ROWS = 10;
    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private Button viewButton, clearButton, markButton, modeButton, sourceButton;
    private final Button[] tabButtons = new Button[5], defconButtons = new Button[5];
    private int tab = TAB_OVERVIEW, logScroll, ticks;
    private long selectedWarning = Long.MIN_VALUE;

    private static final int BOARD_PH = 238, BOARD_SRC_Y = 197;
    private int scroll;

    public CcScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return panelHeight() > 0 ? PW : 0; }

    private int py() { return Math.max(4, Math.min(topPos, height - panelHeight() - 4)); }

    private int panelHeight() {
        Object m = menu.machine();
        if (m instanceof CcMapTableBlockEntity) return TABLE_LIST + 56;
        if (m instanceof CcAirDefenceBlockEntity) return AD_ROWS_Y + CcAirDefenceBlockEntity.MAX_SITES * 20 + 12;
        if (m instanceof CcWarningBoardBlockEntity) return BOARD_PH;
        if (m instanceof CcReconWallBlockEntity) return 70;
        return 0;
    }

    @Override
    protected void init() {
        super.init();
        if (!(menu.machine() instanceof BlockEntity be)) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        if (be instanceof CcReconWallBlockEntity wall) {
            addRenderableWidget(new WmdUi.KitButton(px() + 8, py() + 70 - 23, PW - 16, 16, Component.translatable("gui.wmd.cc.recon_map"), b -> openMap(wall)));
        }
        xBox = zBox = null;
        viewButton = clearButton = markButton = modeButton = sourceButton = null;
        recallButton = watchButton = launchButton = routeButton = null;
        java.util.Arrays.fill(tabButtons, null);
        java.util.Arrays.fill(defconButtons, null);
        if (be instanceof CcWarningBoardBlockEntity wb) {
            sourceButton = addRenderableWidget(new WmdUi.KitButton(px() + 8, py() + BOARD_SRC_Y, PW - 16, 16, Component.translatable("gui.wmd.cc.board.source"), b -> designateSource(wb)));
            modeButton = addRenderableWidget(new WmdUi.KitButton(px() + 8, py() + BOARD_PH - 21, PW - 16, 16, Component.empty(), b -> action(wb, CcActionPayload.BOARD_MODE,
                    wb.mode() == CcWarningBoardBlockEntity.MODE_OUTBOUND ? CcWarningBoardBlockEntity.MODE_INCOMING : CcWarningBoardBlockEntity.MODE_OUTBOUND, 0)));
            updateBoardButton(wb);
        }
        if (be instanceof CcMapTableBlockEntity t) {
            int x = px(), y0 = py();
            String[] tabKeys = { "gui.wmd.cc.tab.overview", "gui.wmd.cc.tab.defcon", "gui.wmd.cc.tab.log", "gui.wmd.cc.tab.sat", "gui.wmd.cc.tab.drones" };
            for (int i = 0; i < 5; i++) {
                final int k = i;
                tabButtons[i] = addRenderableWidget(new WmdUi.KitButton(x + TAB_X[i], y0 + 16, TAB_W[i], 12, Component.translatable(tabKeys[i], t.defcon()), b -> switchTab(k)));
            }
            if (tab == TAB_OVERVIEW) {
                int y = y0 + TABLE_LIST + 16;
                viewButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y, 78, 16, Component.empty(), b -> action(t, CcActionPayload.VIEW, t.view() == CcMapTableBlockEntity.VIEW_RECON
                        ? CcMapTableBlockEntity.VIEW_HOME : CcMapTableBlockEntity.VIEW_RECON, 0)));
                clearButton = addRenderableWidget(new WmdUi.KitButton(x + 86, y, PW - 92, 16, Component.translatable("gui.wmd.cc.des.clear"), b -> action(t, CcActionPayload.UNDESIGNATE, 0, 0)));
                xBox = box(x + 6, y + 19, 40, xText, "X");
                zBox = box(x + 48, y + 19, 40, zText, "Z");
                markButton = addRenderableWidget(new WmdUi.KitButton(x + 90, y + 18, PW - 96, 16, Component.translatable("gui.wmd.cc.des.mark"), b -> mark(t)));
            } else if (tab == TAB_SAT) {
                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.open"), b -> {
                    int[] c = satCentre(t);
                    minecraft.setScreen(new AreaViewScreen(c[0], c[1]));
                }));

                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y + 36, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.station"),
                        b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new StationPayloads.Ask(-1))));

                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y + 56, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.rod"), b -> {
                    CcTarget d = t.designated();
                    RodScreen.ccTarget = d == null ? null : new int[] { d.x(), d.z() };
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RodPayloads.Ask(-1));
                }));

                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y + 76, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.mirror"), b -> {
                    CcTarget d = t.designated();
                    MirrorScreen.ccTarget = d == null ? null : new int[] { d.x(), d.z() };
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new MirrorPayloads.Ask(-1));
                }));

                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y + 96, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.asteroid"), b -> {
                    CcTarget d = t.designated();
                    AsteroidBayScreen.ccTarget = d == null ? null : new int[] { d.x(), d.z() };
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AsteroidBayPayloads.Ask(-1));
                }));

                addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SAT_BTN_Y + 116, PW - 12, 16, Component.translatable("gui.wmd.cc.sat.replay"), b -> ClientReplay.play())).active = ClientReplay.available();
            } else if (tab == TAB_DRONES) {
                recallButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + DR_BTN_Y, 58, 14, Component.translatable("gui.wmd.cc.drones.recall"), b -> action(t, CcActionPayload.DRONE_RECALL, selDrone, 0)));
                watchButton = addRenderableWidget(new WmdUi.KitButton(x + 68, y0 + DR_BTN_Y, PW - 74, 14, Component.translatable("gui.wmd.cc.drones.watch"), b -> action(t, CcActionPayload.DRONE_WATCH, selDrone, 0)));
                launchButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y0 + SITE_BTN_Y, 58, 14, Component.translatable("gui.wmd.cc.drones.launch"),
                        b -> { if (selSite != null) action(t, CcActionPayload.SITE_LAUNCH, (int) (selSite.asLong() >>> 32), (int) selSite.asLong()); }, true));
                routeButton = addRenderableWidget(new WmdUi.KitButton(x + 68, y0 + SITE_BTN_Y, PW - 74, 14, Component.translatable("gui.wmd.cc.drones.route"), b -> {
                    CcDronesPayload.Site st = site(t);
                    if (st != null) minecraft.setScreen(new RouteChartScreen(this, t, st));
                }));
                action(t, CcActionPayload.DRONES, 0, 0);
            } else if (tab == TAB_DEFCON) {
                for (int i = 0; i < 5; i++) {
                    final int level = 5 - i;
                    defconButtons[i] = addRenderableWidget(new WmdUi.KitButton(x + 6 + i * 24, y0 + DEFCON_BTN_Y, 22, 14, Component.literal(Integer.toString(level)), b -> action(t, CcActionPayload.DEFCON, level, 0)));
                }
            } else {
                action(t, CcActionPayload.BATTLE_LOG, 0, 0);
            }
            updateTableButtons(t);
        }
    }

    private static final int DEFCON_BIG_Y = 34, DEFCON_NAME_Y = 74, DEFCON_BTN_Y = 86, DEFCON_FX_Y = 106, DEFCON_FX_ROW = 22, DEFCON_DOOR_Y = 222;

    private static int[] satCentre(CcMapTableBlockEntity t) {
        CcTarget d = t.designated();
        if (d != null) return new int[] { d.x(), d.z() };
        DesigStatusPayload st = DesigStatusPayload.last();
        if (st.satId() >= 0) return new int[] { st.x(), st.z() };
        return new int[] { t.getBlockPos().getX(), t.getBlockPos().getZ() };
    }

    private void satTab(GuiGraphics g, CcMapTableBlockEntity t, int x, int y, int cx) {
        int[] c = satCentre(t);
        fit(g, Component.translatable("gui.wmd.cc.sat.centre", c[0], c[1]), cx, y + 34, GREY);
        var p = minecraft.player;
        long now = OrbitView.now();
        int n = 0;
        for (OrbitMath.Sat s : OrbitView.sats()) {
            if (!"designator".equals(s.type()) || p == null || !p.getUUID().equals(s.owner()) || now >= s.endMs() || n >= 6) continue;
            boolean over = OrbitMath.isOverhead(s, c[0] + 0.5, c[1] + 0.5, now);
            long ms = over ? OrbitMath.overheadLeft(s, c[0] + 0.5, c[1] + 0.5, now) : OrbitMath.nextPass(s, c[0] + 0.5, c[1] + 0.5, now);
            String mmss = String.format("%d:%02d", ms / 60000, ms / 1000 % 60);
            fit(g, Component.translatable(over ? "gui.wmd.cc.sat.row_over" : "gui.wmd.cc.sat.row_next", s.id(), mmss), cx, y + SAT_ROWS_Y + n * 11, over ? GOOD : TEXT);
            n++;
        }
        if (n == 0) fit(g, Component.translatable("gui.wmd.cc.sat.none"), cx, y + SAT_ROWS_Y, GREY);
        DesigStatusPayload st = DesigStatusPayload.last();
        if (st.satId() >= 0) fit(g, Component.translatable("gui.wmd.cc.sat.lasing", st.satId(), st.x(), st.z()), cx, y + SAT_BTN_Y - 14, st.state() == SatDesignator.LIT ? GOOD : WmdUi.WARN);
        fit(g, Component.translatable("gui.wmd.cc.sat.hint"), cx, y + SAT_BTN_Y + 22, GREY);
    }

    private void switchTab(int k) {
        if (k == tab) return;
        tab = k;
        logScroll = 0;
        rebuildWidgets();
    }

    private EditBox box(int x, int y, int w, String value, String hint) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.literal(hint));
        b.setMaxLength(9);
        b.setFilter(v -> v.matches("-?\\d*"));
        b.setValue(value);
        b.setHint(Component.literal(hint));
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private void action(BlockEntity be, int action, int a, int b) { PacketDistributor.sendToServer(new CcActionPayload(be.getBlockPos(), action, a, b)); }

    private void mark(CcMapTableBlockEntity t) {
        try {
            int x = Integer.parseInt(xBox.getValue().trim()), z = Integer.parseInt(zBox.getValue().trim());
            action(t, CcActionPayload.DESIGNATE, x, z);
        } catch (NumberFormatException ignored) {}
    }

    private boolean owner(CcModuleBlockEntity m) { return minecraft.player != null && minecraft.player.getUUID().equals(m.getOwner()); }

    private void updateTableButtons(CcMapTableBlockEntity t) {
        boolean own = owner(t), on = t.isOnline();
        for (int i = 0; i < 5; i++) if (tabButtons[i] != null) {
            tabButtons[i].active = i != tab;
            if (i == TAB_DEFCON) tabButtons[i].setMessage(Component.translatable("gui.wmd.cc.tab.defcon", t.defcon()));
        }
        for (int i = 0; i < 5; i++) if (defconButtons[i] != null) {
            defconButtons[i].visible = own;
            defconButtons[i].active = own && on && t.defcon() != 5 - i;
        }
        if (recallButton != null) {
            CcDronesPayload.Row r = drone(t);
            CcDronesPayload.Site st = site(t);
            String doing = r == null ? "" : CcDronesView.doing(r.kind(), r.phase());
            recallButton.active = own && r != null && r.kind() >= 1 && !doing.equals("home") && !doing.equals("down");
            watchButton.active = own && r != null && r.kind() >= 3 && !doing.equals("down");
            launchButton.active = own && on && st != null && st.status() == BigDroneSite.S_READY;
            routeButton.active = own && st != null;
        }
        if (viewButton == null) return;
        viewButton.setMessage(Component.translatable(t.view() == CcMapTableBlockEntity.VIEW_RECON ? "gui.wmd.cc.view.recon" : "gui.wmd.cc.view.home"));
        viewButton.active = own && on;
        clearButton.active = own && on && t.designated() != null;
        markButton.active = own && on && xBox != null && xBox.getValue().matches("-?\\d+") && zBox.getValue().matches("-?\\d+");
    }

    private static List<CommandCenter.Warning> shownWarnings(CcWarningBoardBlockEntity b) {
        List<CommandCenter.Warning> ws = b.warnings();
        return new ArrayList<>(ws.subList(Math.max(0, ws.size() - 8), ws.size()));
    }

    private CommandCenter.Warning selectedWarning(CcWarningBoardBlockEntity b) {
        for (CommandCenter.Warning w : shownWarnings(b)) if (w.id() == selectedWarning) return w;
        return null;
    }

    private void designateSource(CcWarningBoardBlockEntity b) {
        CommandCenter.Warning w = selectedWarning(b);
        if (w != null && !w.sat()) action(b, CcActionPayload.DESIGNATE_SOURCE, (int) w.id(), (int) (w.id() >>> 32));
    }

    private void updateBoardButton(CcWarningBoardBlockEntity b) {
        if (modeButton == null) return;
        modeButton.setMessage(Component.translatable(b.mode() == CcWarningBoardBlockEntity.MODE_OUTBOUND ? "gui.wmd.cc.board.outbound" : "gui.wmd.cc.board.incoming"));
        modeButton.active = owner(b) && b.isOnline();
        if (sourceButton != null) {
            CommandCenter.Warning w = b.mode() == CcWarningBoardBlockEntity.MODE_INCOMING ? selectedWarning(b) : null;
            sourceButton.visible = b.mode() == CcWarningBoardBlockEntity.MODE_INCOMING;
            sourceButton.active = owner(b) && b.isOnline() && w != null && !w.sat();
        }
    }

    private CcDronesPayload.Row drone(CcMapTableBlockEntity t) {
        for (CcDronesPayload.Row r : CcDronesView.drones(t.getBlockPos())) if (r.id() == selDrone) return r;
        return null;
    }

    private CcDronesPayload.Site site(CcMapTableBlockEntity t) {
        for (CcDronesPayload.Site st : CcDronesView.sites(t.getBlockPos())) if (st.pos().equals(selSite)) return st;
        return null;
    }

    private void dronesTab(GuiGraphics g, CcMapTableBlockEntity t, int x, int y, int cx) {
        List<CcDronesPayload.Row> rs = CcDronesView.drones(t.getBlockPos());
        fit(g, Component.translatable("gui.wmd.cc.drones.air", rs.size()), cx, y + DR_Y, TEXT);
        if (rs.isEmpty()) fit(g, Component.translatable("gui.wmd.cc.drones.none_air"), cx, y + DR_ROWS_Y + 2, GREY);
        for (int i = 0; i < Math.min(DR_ROWS, rs.size()); i++) {
            CcDronesPayload.Row r = rs.get(i);
            int ly = y + DR_ROWS_Y + i * DR_ROW;
            if (r.id() == selDrone) selRow(g, x + 4, ly - 1, x + PW - 4, ly + 9);
            else if (WmdUi.over(mouseX, mouseY, x + 4, ly - 1, PW - 8, DR_ROW)) g.fill(x + 4, ly - 1, x + PW - 4, ly + 9, ROW_HOVER);
            String doing = CcDronesView.doing(r.kind(), r.phase());
            Component row = Component.translatable("gui.wmd.cc.drones.row", r.id(), r.kind() == 0 ? "R" : "T" + r.kind(), Component.translatable("gui.wmd.cc.drones.do." + doing),
                    r.battery() < 0 ? "-" : r.battery() + "%", r.dist() >= 1000 ? String.format("%.1fk", r.dist() / 1000.0) : Integer.toString(r.dist()));
            drawLeft(g, row, x + 7, ly, PW - 12, doing.equals("down") ? BAD : doing.equals("target") ? WmdUi.WARN : TEXT);
        }
        if (rs.size() > DR_ROWS) fit(g, Component.translatable("gui.wmd.cc.drones.more", rs.size() - DR_ROWS), cx, y + DR_BTN_Y - 10, GREY);
        WmdUi.divider(g, x + 5, y + SITE_Y - 4, PW - 10);
        List<CcDronesPayload.Site> ss = CcDronesView.sites(t.getBlockPos());
        fit(g, Component.translatable("gui.wmd.cc.drones.sites", ss.size()), cx, y + SITE_Y, TEXT);
        if (ss.isEmpty()) fit(g, Component.translatable("gui.wmd.cc.drones.none_sites"), cx, y + SITE_ROWS_Y + 2, GREY);
        for (int i = 0; i < Math.min(DR_ROWS, ss.size()); i++) {
            CcDronesPayload.Site st = ss.get(i);
            int ly = y + SITE_ROWS_Y + i * DR_ROW;
            if (st.pos().equals(selSite)) selRow(g, x + 4, ly - 1, x + PW - 4, ly + 9);
            else if (WmdUi.over(mouseX, mouseY, x + 4, ly - 1, PW - 8, DR_ROW)) g.fill(x + 4, ly - 1, x + PW - 4, ly + 9, ROW_HOVER);
            WmdUi.led(g, x + 7, ly + 1, st.status() == BigDroneSite.S_READY || st.status() == BigDroneSite.S_OUT ? WmdUi.K_GOOD
                    : st.status() == BigDroneSite.S_EMPTY || st.status() == BigDroneSite.S_NO_TARGET ? WmdUi.K_IDLE : WmdUi.K_WARN, st.status() == BigDroneSite.S_OUT);
            Component row = Component.translatable("gui.wmd.cc.drones.site", Component.translatable(st.hangar() ? "gui.wmd.cc.drones.hangar" : "gui.wmd.cc.drones.catapult"),
                    st.at().getX(), st.at().getZ(), st.tier() == 0 ? "-" : "T" + st.tier(), st.bombs());
            drawLeft(g, row, x + 16, ly, PW - 21, TEXT);
        }
        CcDronesPayload.Site st = site(t);
        if (st != null) {
            Component status = st.status() == BigDroneSite.S_OUT ? Component.translatable("gui.wmd.big_drone.st." + st.status(), st.out()) : Component.translatable("gui.wmd.big_drone.st." + st.status());
            fit(g, status, cx, y + SITE_INFO_Y, st.status() == BigDroneSite.S_READY ? GOOD : TEXT);
            int[] m = st.mission();
            fit(g, m.length < 3 ? Component.translatable("gui.wmd.big_drone.no_route")
                    : Component.translatable("gui.wmd.cc.drones.mission", m[0], m[1 + 2 * m[0]], m[2 + 2 * m[0]]), cx, y + SITE_INFO_Y + 10, GREY);
        } else fit(g, Component.translatable("gui.wmd.cc.drones.pick"), cx, y + SITE_INFO_Y + 5, GREY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && tab == TAB_DRONES && menu.machine() instanceof CcMapTableBlockEntity t) {
            int x = px(), y = py();
            if (mx >= x + 4 && mx < x + PW - 4) {
                List<CcDronesPayload.Row> rs = CcDronesView.drones(t.getBlockPos());
                int i = (int) Math.floor((my - (y + DR_ROWS_Y - 1)) / DR_ROW);
                if (my >= y + DR_ROWS_Y - 1 && i >= 0 && i < Math.min(DR_ROWS, rs.size())) { selDrone = rs.get(i).id() == selDrone ? -1 : rs.get(i).id(); WmdUi.click(); return true; }
                List<CcDronesPayload.Site> ss = CcDronesView.sites(t.getBlockPos());
                int k = (int) Math.floor((my - (y + SITE_ROWS_Y - 1)) / DR_ROW);
                if (my >= y + SITE_ROWS_Y - 1 && k >= 0 && k < Math.min(DR_ROWS, ss.size())) { selSite = ss.get(k).pos().equals(selSite) ? null : ss.get(k).pos(); WmdUi.click(); return true; }
            }
        }
        if (button == 0 && menu.machine() instanceof CcWarningBoardBlockEntity b && b.mode() == CcWarningBoardBlockEntity.MODE_INCOMING) {
            int x = px(), y = py() + 32;
            List<CommandCenter.Warning> ws = shownWarnings(b);
            if (mx >= x + 4 && mx < x + PW - 4 && my >= y - 2 && my < y - 2 + ws.size() * 20) {
                CommandCenter.Warning w = ws.get(Math.min(ws.size() - 1, (int) ((my - y + 2) / 20)));
                selectedWarning = selectedWarning == w.id() ? Long.MIN_VALUE : w.id();
                updateBoardButton(b);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
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
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        boolean over = mx >= px() && mx < px() + PW && my >= py() && my < py() + panelHeight();
        if (over && menu.machine() instanceof CcAirDefenceBlockEntity a) {
            int n = a.sites().length / CcAirDefenceBlockEntity.ROW, max = Math.max(0, n - CcAirDefenceBlockEntity.MAX_SITES);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
            return true;
        }
        if (over && tab == TAB_LOG && menu.machine() instanceof CcMapTableBlockEntity t) {
            int n = CcBattleLogView.eventsFor(t.getBlockPos()).size();
            logScroll = Math.max(0, Math.min(Math.max(0, n - LOG_ROWS), logScroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (menu.machine() instanceof CcMapTableBlockEntity t) {
            updateTableButtons(t);
            if (tab == TAB_LOG && ticks % 40 == 0) action(t, CcActionPayload.BATTLE_LOG, 0, 0);
            if (tab == TAB_DRONES && ticks % 40 == 0) action(t, CcActionPayload.DRONES, 0, 0);
        }
        if (menu.machine() instanceof CcWarningBoardBlockEntity wb) updateBoardButton(wb);
        if (!autoOpened && menu.machine() instanceof CcReconWallBlockEntity wall) {
            autoOpened = true;
            openMap(wall);
        }
    }

    @Override
    public void removed() {
        super.removed();
        CcBattleLogView.clear();
    }

    private void openMap(CcReconWallBlockEntity wall) {
        if (wall.isOnline()) minecraft.setScreen(new ReconMapScreen(this, wall.getBlockPos()));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        int ph = panelHeight();
        if (ph <= 0) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, ph);
        drawSelectedTab(g);
        Object m = menu.machine();
        if (m instanceof CcMapTableBlockEntity t) {
            fit(g, Component.translatable("gui.wmd.cc.panel"), cx, y + 6, TEXT);
            g.fill(x + TAB_X[tab] + 2, y + 29, x + TAB_X[tab] + TAB_W[tab] - 2, y + 30, WmdUi.BLUE);
            if (tab == TAB_DEFCON) defcon(g, t, x, y, cx);
            else if (tab == TAB_LOG) battleLog(g, t, x, y, cx);
            else if (tab == TAB_SAT) satTab(g, t, x, y, cx);
            else if (tab == TAB_DRONES) dronesTab(g, t, x, y, cx);
            else table(g, t, x, y, cx);
        }
        else if (m instanceof CcModuleBlockEntity mod) {
            fit(g, Component.translatable(mod.kind().langKey()), cx, y + 6, TEXT);
            WmdUi.divider(g, x + 6, y + 15, PW - 12);
            fit(g, Component.translatable(mod.isOnline() ? "gui.wmd.cc.online" : "gui.wmd.cc.offline"), cx, y + 18, mod.isOnline() ? GOOD : BAD);
            if (mod instanceof CcWarningBoardBlockEntity b) board(g, b, x, y + 32, cx);
            else if (mod instanceof CcAirDefenceBlockEntity a) airDefence(g, a, x, y + 30, cx);
            else if (mod instanceof CcReconWallBlockEntity) fit(g, Component.translatable("gui.wmd.cc.recon_hint"), cx, y + 32, GREY);
        }
    }

    private void table(GuiGraphics g, CcMapTableBlockEntity t, int x, int y, int cx) {
        int ly = y + 18 + TAB_H;
        Component st = !t.buildingFound() ? Component.translatable("gui.wmd.ccb.state.no_building")
                : Component.translatable(t.buildingPowered() ? "gui.wmd.ccb.state.online" : "gui.wmd.ccb.state.no_power");
        fit(g, st, cx, ly, t.buildingFound() && t.buildingPowered() ? GOOD : BAD);
        ly += 11;
        fit(g, Component.translatable(t.satsControlled() ? "gui.wmd.cc.sats_on" : "gui.wmd.cc.sats_off"), cx, ly, t.satsControlled() ? GOOD : GREY);
        ly += 13;
        WmdUi.divider(g, x + 5, ly - 4, PW - 10);
        for (CcModule k : CcModule.values()) {
            boolean in = t.installed(k), on = t.moduleOnline(k), blocked = t.moduleBlocked(k);
            int colour = !k.built ? WmdUi.TEXT_DIM : on ? GOOD : blocked ? BAD : in ? BLUE : TEXT;
            if (on || blocked || in) WmdUi.led(g, x + 7, ly + 1, on ? WmdUi.K_GOOD : blocked ? WmdUi.K_BAD : WmdUi.K_INFO, blocked);
            else emptyLed(g, x + 7, ly + 1);
            Component name = k.built ? Component.translatable(k.langKey()) : Component.translatable("gui.wmd.cc.not_built", Component.translatable(k.langKey()));
            drawLeft(g, name, x + 16, ly, PW - 21, colour);
            ly += 10;
        }

        WmdUi.divider(g, x + 5, y + TABLE_LIST + 1, PW - 10);
        CcTarget d = t.designated();
        fit(g, d == null ? Component.translatable("gui.wmd.cc.des.none") : Component.translatable("gui.wmd.cc.des.short", d.x(), d.z()),
                cx, y + TABLE_LIST + 5, d == null ? GREY : BAD);
    }

    private void defcon(GuiGraphics g, CcMapTableBlockEntity t, int x, int y, int cx) {
        int d = t.defcon();
        int col = CcDefcon.colour(d);
        boolean blink = d <= 1 && (System.currentTimeMillis() / 400L) % 2L == 0L;
        WmdUi.well(g, x + 6, y + DEFCON_BIG_Y - 2, PW - 12, 40);
        g.fill(x + 6, y + DEFCON_BIG_Y - 2, x + PW - 6, y + DEFCON_BIG_Y - 1, col);
        g.fill(x + 6, y + DEFCON_BIG_Y + 37, x + PW - 6, y + DEFCON_BIG_Y + 38, col);
        String label = "DEFCON " + d;
        float s = 2.0F;
        int lw = font.width(label);
        if (lw * s > PW - 16) s = (PW - 16) / (float) lw;
        g.pose().pushPose();
        g.pose().translate(cx - lw * s / 2.0F, y + DEFCON_BIG_Y + 18 - 4.5F * s, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, label, 0, 0, blink ? 0xFFFFFFFF : col, true);
        g.pose().popPose();
        fit(g, Component.translatable("gui.wmd.cc.defcon.name." + d), cx, y + DEFCON_NAME_Y, TEXT);
        if (!owner(t)) fit(g, Component.translatable("gui.wmd.cc.defcon.owner_only"), cx, y + DEFCON_BTN_Y + 3, GREY);
        for (int lvl = 5, i = 0; lvl >= 1; lvl--, i++) {
            int ry = y + DEFCON_FX_Y + i * DEFCON_FX_ROW;
            boolean on = d <= lvl;
            WmdUi.divider(g, x + 5, ry - 3, PW - 10);
            g.fill(x + 7, ry + 1, x + 12, ry + 6, 0xFF0A0D11);
            g.fill(x + 8, ry + 2, x + 11, ry + 5, on ? CcDefcon.colour(lvl) : 0xFF2A3340);
            drawLeft(g, Component.translatable("gui.wmd.cc.defcon.fx." + lvl + ".a"), x + 16, ry, PW - 21, on ? TEXT : GREY);
            drawLeft(g, Component.translatable("gui.wmd.cc.defcon.fx." + lvl + ".b"), x + 16, ry + 9, PW - 21, on ? TEXT : GREY);
        }
        CcBuildingBlockEntity b = t.building();
        Component door = b == null ? Component.empty() : b.sealed() ? Component.translatable("gui.wmd.cc.defcon.sealed")
                : Component.translatable(b.isDoorOpen() ? "gui.wmd.cc.defcon.door_open" : "gui.wmd.cc.defcon.door_closed");
        fit(g, door, cx, y + DEFCON_DOOR_Y, b != null && b.sealed() ? BAD : TEXT);
        if (b != null) fit(g, Component.translatable(b.lockdownToggle() ? "gui.wmd.cc.defcon.lockdown_on" : "gui.wmd.cc.defcon.lockdown_off"), cx, y + DEFCON_DOOR_Y + 12,
                b.lockdownToggle() ? BAD : GREY);
    }

    private void battleLog(GuiGraphics g, CcMapTableBlockEntity t, int x, int y, int cx) {
        List<CcBattleLog.Event> ev = CcBattleLogView.eventsFor(t.getBlockPos());
        if (ev.isEmpty()) { fit(g, Component.translatable("gui.wmd.cc.log.none"), cx, y + LOG_Y + 4, GREY); return; }
        int n = ev.size();
        logScroll = Math.max(0, Math.min(Math.max(0, n - LOG_ROWS), logScroll));
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH);
        int ly = y + LOG_Y;
        for (int k = logScroll; k < Math.min(n, logScroll + LOG_ROWS); k++) {
            CcBattleLog.Event e = ev.get(n - 1 - k);
            WmdUi.divider(g, x + 5, ly - 3, PW - 10);
            g.fill(x + 6, ly, x + 8, ly + 7, 0xFF000000 | logColour(e.kind()));
            String when = hm.format(java.time.Instant.ofEpochMilli(e.timeMs()).atZone(java.time.ZoneId.systemDefault()));
            drawLeft(g, Component.literal(when).append("  ").append(Component.translatable("gui.wmd.cc.log.kind." + e.kind())), x + 11, ly, PW - 16, GREY);
            drawLeft(g, e.text(), x + 11, ly + 9, PW - 16, TEXT);
            ly += LOG_ROW;
        }
        fit(g, Component.translatable("gui.wmd.cc.site_scroll", logScroll + 1, Math.min(n, logScroll + LOG_ROWS), n), cx, y + LOG_Y + LOG_ROWS * LOG_ROW + 2, GREY);
    }

    static int logColour(int kind) {
        return switch (kind) {
            case CcBattleLog.LAUNCH_IN, CcBattleLog.LOST, CcBattleLog.ALARM -> 0xD03030;
            case CcBattleLog.INTERCEPT, CcBattleLog.HIT, CcBattleLog.BDA -> 0x30A040;
            case CcBattleLog.DEFCON, CcBattleLog.COUNTER -> 0xE0A020;
            case CcBattleLog.SAT, CcBattleLog.JAM -> 0x9040D0;
            default -> 0x3070D0;
        };
    }

    private void board(GuiGraphics g, CcWarningBoardBlockEntity b, int x, int y, int cx) {
        if (b.mode() == CcWarningBoardBlockEntity.MODE_OUTBOUND) { outbound(g, b, x, y, cx); return; }
        List<CommandCenter.Warning> ws = shownWarnings(b);
        if (ws.isEmpty()) { fit(g, Component.translatable("gui.wmd.cc.no_warnings"), cx, y + 4, GREY); return; }
        long now = b.getLevel() != null ? b.getLevel().getGameTime() : 0L;
        int ly = y;
        for (CommandCenter.Warning w : ws) {
            if (w.id() == selectedWarning) selRow(g, x + 4, ly - 1, x + PW - 4, ly + 17);
            else if (WmdUi.over(mouseX, mouseY, x + 4, ly - 2, PW - 8, 20)) g.fill(x + 4, ly - 1, x + PW - 4, ly + 17, ROW_HOVER);
            WmdUi.divider(g, x + 5, ly - 3, PW - 10);
            boolean airship = AirshipWatch.isWarning(w.id());
            boolean drone = w.what() == CommandCenter.Warning.WHAT_DRONE, bomb = w.what() == CommandCenter.Warning.WHAT_BOMB;
            Component first = airship ? Component.translatable("gui.wmd.cc.warn_airship", w.ox(), w.oz())
                    : drone ? Component.translatable("gui.wmd.cc.warn_drone", w.tier(), w.ox(), w.oz())
                    : bomb ? Component.translatable("gui.wmd.cc.warn_bomb", w.ox(), w.oz())
                    : Component.translatable("gui.wmd.cc.warn_site", w.ox(), w.oz());
            if (w.hyper()) first = Component.translatable("gui.wmd.cc.warn_hyper").append(first);
            fit(g, first, cx, ly, BAD);
            Component second = airship || drone ? Component.translatable("gui.wmd.cc.warn_airship_head", w.ix(), w.iz())
                    : w.sat() ? Component.translatable("gui.wmd.cc.warn_orbit")
                    : Component.translatable("gui.wmd.cc.warn_impact", w.ix(), w.iz(), OrbitMath.mmss(Math.max(0L, w.impactGt() - now) * 50L));
            if (w.intel()) second = second.copy().append(Component.translatable("gui.wmd.cc.warn_intel", Component.translatable("warhead.wmd." + w.warhead()), w.tier(),
                    Component.translatable("gui.wmd.cc.intel.seeker." + (w.seeker().isEmpty() ? "none" : w.seeker()))));
            fit(g, second, cx, ly + 9, w.intel() ? WmdUi.WARN : TEXT);
            ly += 20;
        }
    }

    private void outbound(GuiGraphics g, CcWarningBoardBlockEntity b, int x, int y, int cx) {
        List<CcWarningBoardBlockEntity.Out> os = b.outbound();
        if (os.isEmpty()) { fit(g, Component.translatable("gui.wmd.cc.no_outbound"), cx, y + 4, GREY); return; }
        long now = b.getLevel() != null ? b.getLevel().getGameTime() : 0L;
        int ly = y;
        for (int i = Math.max(0, os.size() - 8); i < os.size(); i++) {
            CcWarningBoardBlockEntity.Out o = os.get(i);
            WmdUi.divider(g, x + 5, ly - 3, PW - 10);
            fit(g, o.sat() ? Component.translatable("gui.wmd.cc.warn_orbit") : Component.translatable("gui.wmd.cc.out_target", o.tx(), o.tz(), o.tier()), cx, ly, BLUE);
            Component wh = o.warhead().isEmpty() ? Component.literal("?") : Component.translatable("warhead.wmd." + o.warhead());
            Component second = o.impactGt() < now ? Component.translatable("gui.wmd.cc.out_hit", wh)
                    : Component.translatable("gui.wmd.cc.out_eta", wh, OrbitMath.mmss(Math.max(0L, o.impactGt() - now) * 50L));
            fit(g, second, cx, ly + 9, TEXT);
            ly += 20;
        }
    }

    private void airDefence(GuiGraphics g, CcAirDefenceBlockEntity a, int x, int y, int cx) {
        fit(g, Component.translatable(a.satTracking() ? "gui.wmd.cc.track_on" : "gui.wmd.cc.track_off"), cx, y, a.satTracking() ? GOOD : GREY);
        int[] s = a.sites();
        int ly = y + 14, R = CcAirDefenceBlockEntity.ROW, n = s.length / R;
        if (s.length == 0) { fit(g, Component.translatable("gui.wmd.cc.no_sites"), cx, ly, GREY); return; }
        scroll = Math.max(0, Math.min(Math.max(0, n - CcAirDefenceBlockEntity.MAX_SITES), scroll));
        int first = scroll, last = Math.min(n, first + CcAirDefenceBlockEntity.MAX_SITES);
        fit(g, Component.translatable("gui.wmd.cc.site_scroll", first + 1, last, n), cx, y + 14 + CcAirDefenceBlockEntity.MAX_SITES * 20, GREY);
        for (int i = first * R; i < last * R; i += R) {
            WmdUi.divider(g, x + 5, ly - 3, PW - 10);
            Component tier = s[i + 3] == 0 ? Component.translatable("gui.wmd.cc.site_ciws") : Component.translatable("gui.wmd.cc.site_tier", s[i + 3]);
            fit(g, Component.translatable("gui.wmd.cc.site_row", tier, s[i], s[i + 1], s[i + 2]), cx, ly, TEXT);
            int st = s[i + 4];
            String key = st == CcAirDefenceBlockEntity.READY ? "ready" : st == CcAirDefenceBlockEntity.RELOADING ? "reloading" : "empty";
            fit(g, Component.translatable("gui.wmd.cc.site_range", Component.translatable("gui.wmd.cc.site_" + key, s[i + 5], s[i + 6]), String.format("%,d", s[i + 7])), cx, ly + 9, st == CcAirDefenceBlockEntity.READY ? GOOD : st == CcAirDefenceBlockEntity.RELOADING ? BLUE : BAD);
            ly += 20;
        }
    }

    static void selRow(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, ROW_SEL);
        g.fill(x0, y0, x0 + 2, y1, WmdUi.BLUE);
    }

    static void emptyLed(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 5, y + 5, 0xFF0A0D11);
        g.fill(x + 1, y + 1, x + 4, y + 4, 0xFF1A2129);
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
