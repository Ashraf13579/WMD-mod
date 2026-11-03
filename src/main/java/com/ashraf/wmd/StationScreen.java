package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class StationScreen extends Screen {

    static final int W = 232, H = 214, ROWS_Y = 76, ROW = 11, MAX_ROWS = 7, LW = 150, GAP = 4, MAX_PODS = 4, ARM_TICKS = 60;
    private StationPayloads.Info info;
    private int ticks;
    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private WmdUi.KitButton burn;
    private int armed;
    private static int tab;
    private static final int MAX_TARGETS = 8;
    private WmdUi.KitButton charge, standDown, autoBtn;
    private final java.util.List<WmdUi.KitButton> fireBtns = new java.util.ArrayList<>();
    private int chargeArmed;

    public StationScreen(StationPayloads.Info info) {
        super(Component.translatable("gui.wmd.station.title"));
        this.info = info;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!StationPayloads.openPending) return;
        StationPayloads.openPending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || StationPayloads.latest == null) return;
        if (mc.screen instanceof StationScreen s) {
            boolean same = s.layout(s.info).equals(s.layout(StationPayloads.latest));
            s.info = StationPayloads.latest;
            if (!same) s.rebuildWidgets();
        }
        else mc.setScreen(new StationScreen(StationPayloads.latest));
    }

    private String layout(StationPayloads.Info i) {
        StringBuilder b = new StringBuilder().append(i.owner()).append(':').append(i.nextPort()).append(':').append(tab).append(':').append(i.laser() >= -1 ? 1 : 0);
        for (StationPayloads.LTarget t : i.targets()) b.append(':').append(t.kind()).append(t.id());
        for (StationPayloads.Seen v : i.seen()) b.append(':').append(v.slot());
        b.append(':').append(i.boardPick()).append(':').append(i.sensor());
        for (int[] q : i.pods()) b.append(':').append(q[0]);
        return b.toString();
    }

    private int left() { return (width - W - GAP - LW) / 2; }

    @Override
    protected void init() {
        int left = left(), top = (height - H) / 2, lx = left + W + GAP;
        String[] tabs = { "gui.wmd.station.tab_logistics", "gui.wmd.station.tab_sensor", "gui.wmd.station.tab_laser" };
        fireBtns.clear(); charge = null; standDown = null; autoBtn = null; burn = null;
        addRenderableWidget(new WmdUi.TabButton(left - 23, top + 22, "icon_camera", Component.translatable("gui.wmd.kit.tab.camera"), b -> {
            StationModuleBlockEntity cm = ClientCutscene.findModule(Station.LASER, info.slot());
            if (cm == null) cm = ClientCutscene.findModule(Station.CORE, info.slot());
            if (cm != null) { minecraft.setScreen(null); ClientCutscene.watchModule(cm); } else ClientCutscene.camUnavailable();
        }));
        for (int k = 0; k < 3; k++) {
            final int t = k;
            WmdUi.KitButton tb = new WmdUi.KitButton(lx + 5 + k * 47, top + 4, 45, 12, Component.translatable(tabs[k]), b -> { tab = t; rebuildWidgets(); });
            tb.active = tab != t;
            addRenderableWidget(tb);
        }
        if (info.owner() && tab == 0) logisticsWidgets(lx, top);
        if (info.owner() && tab == 1) boardWidgets(lx, top);
        if (info.owner() && tab == 2) laserWidgets(lx, top);
    }

    private void logisticsWidgets(int x, int top) {
        int i = 0;
        for (int[] q : info.pods()) {
            if (i >= MAX_PODS) break;
            final int port = q[0];
            addRenderableWidget(new WmdUi.KitButton(x + LW - 8 - 40, top + 58 + i * 14, 40, 12, Component.translatable("gui.wmd.station.pod_open"),
                    b -> PacketDistributor.sendToServer(new StationPayloads.OpenPod(info.slot(), port))));
            i++;
        }
        xBox = new EditBox(font, x + 18, top + 133, 50, 14, Component.literal("X"));
        zBox = new EditBox(font, x + 88, top + 133, 50, 14, Component.literal("Z"));
        for (EditBox e : new EditBox[] { xBox, zBox }) {
            e.setMaxLength(9);
            e.setFilter(t -> t.isEmpty() || t.equals("-") || t.matches("-?\\d{1,8}"));
            addRenderableWidget(e);
        }
        xBox.setValue(xText); zBox.setValue(zText);
        xBox.setResponder(t -> { xText = t; armed = 0; });
        zBox.setResponder(t -> { zText = t; armed = 0; });
        burn = new WmdUi.KitButton(x + 8, top + H - 24, LW - 16, 16, Component.translatable("gui.wmd.station.burn"), b -> pressBurn(), true);
        addRenderableWidget(burn);
    }

    private int[] target() {
        try { return new int[] { Integer.parseInt(xText.trim()), Integer.parseInt(zText.trim()) }; } catch (NumberFormatException e) { return null; }
    }

    private void pressBurn() {
        int[] t = target();
        if (t == null) return;
        if (armed <= 0) { armed = ARM_TICKS; return; }
        armed = 0;
        PacketDistributor.sendToServer(new StationPayloads.Burn(info.slot(), t[0], t[1]));
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0 || (tab == 2 && ticks % 10 == 0 && info.laser() >= StationLaser.CHARGING)) PacketDistributor.sendToServer(new StationPayloads.Ask(info.slot()));
        if (armed > 0) armed--;
        if (ramArmed > 0 && --ramArmed == 0 && ramBtn != null) ramBtn.setMessage(Component.translatable("gui.wmd.station.ram"));
        if (chargeArmed > 0) chargeArmed--;
        if (charge != null) {
            int st = info.laser();
            charge.active = st == StationLaser.IDLE && info.energy() >= (closeMode() ? Balance.LASER_CLOSE_SHOT_FE : Balance.LASER_SHOT_FE);
            charge.setMessage(Component.translatable(chargeArmed > 0 ? "gui.wmd.station.lz_charge_confirm" : "gui.wmd.station.lz_charge"));
            standDown.active = st == StationLaser.CHARGING || st == StationLaser.ARMED;
            autoBtn.setMessage(Component.translatable(info.auto() ? "gui.wmd.station.lz_auto_on" : "gui.wmd.station.lz_auto_off"));
            for (WmdUi.KitButton f : fireBtns) f.active = st == StationLaser.ARMED;
        }
        if (burn != null) {
            int[] t = target();
            double d = t == null ? 0.0 : Math.hypot(t[0] - info.groundX(), t[1] - info.groundZ());
            int need = Reboost.cells(d);
            burn.active = t != null && info.rbPct() < 0 && d >= 16.0 && d <= Balance.REBOOST_MAX && need <= info.cells();
            burn.setMessage(armed > 0 ? Component.translatable("gui.wmd.station.burn_confirm", need) : Component.translatable("gui.wmd.station.burn"));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = left(), top = (height - H) / 2, w = W - 16, x = left + 8;
        WmdUi.panel(g, left, top, W, H);
        logistics(g, left + W + GAP, top);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.title_slot", info.slot()), x, top + 6, w - WmdHelp.ROOM, WmdUi.TEXT);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        WmdUi.divider(g, x, top + 16, w);

        float frac = info.max() <= 0 ? 0.0F : (float) info.energy() / info.max();
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.power", String.format("%,d", info.energy() / 1000), String.format("%,d", info.max() / 1000)), x, top + 21, w, WmdUi.TEXT);
        WmdUi.energyGauge(g, x, top + 31, w, 6, frac);
        int net = info.make() - info.use();
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.flow", info.make(), info.use()), x, top + 40, w, net >= 0 ? WmdUi.GOOD : WmdUi.BAD);

        boolean sun = info.orbitT() < Balance.STATION_SUN_TICKS;
        int left_t = (sun ? Balance.STATION_SUN_TICKS : Balance.STATION_ORBIT_TICKS) - info.orbitT();
        String mmss = String.format("%d:%02d", left_t / 1200, (left_t / 20) % 60);
        WmdUi.drawFit(g, font, Component.translatable(sun ? "gui.wmd.station.sun" : "gui.wmd.station.shadow", mmss), x, top + 51, w, sun ? 0xFFFFD27F : 0xFF7FA8FF);
        WmdUi.divider(g, x, top + 62, w);

        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.modules", info.mods().size()), x, top + 66, w, WmdUi.TEXT_MUTED);
        int i = 0;
        for (StationPayloads.Mod m : info.mods()) {
            if (i >= MAX_ROWS) break;
            int y = top + ROWS_Y + i * ROW;
            Component name = Component.translatable("station.wmd.module." + m.type());
            Component port = m.port() >= Station.SIDE0 ? Component.translatable("gui.wmd.station.on_side." + (m.port() - Station.SIDE0))
                    : m.port() >= 4 ? Component.translatable("gui.wmd.station.on_far", m.port() - 4)
                    : m.port() >= 0 ? Component.translatable("gui.wmd.station.on_port", m.port()) : Component.empty();
            Component state = m.state() == 3 ? Component.translatable("gui.wmd.station.wrecked") : m.state() == 1 ? Component.translatable("gui.wmd.station.arriving") : m.state() == 2 ? Component.translatable("gui.wmd.station.deploying")
                    : m.breach() ? Component.translatable("gui.wmd.station.breach") : Station.boom(m.type()) ? Component.translatable("gui.wmd.station.ok")
                    : Component.translatable("gui.wmd.station.air", m.air());
            int col = m.breach() || m.state() == 3 ? WmdUi.BAD : m.state() != 0 ? 0xFFFFC060 : WmdUi.TEXT;
            WmdUi.drawFit(g, font, Component.literal("").append(name).append(port), x + 4, y, w / 2 + 10, col);
            WmdUi.drawFit(g, font, state, x + w / 2 + 18, y, w / 2 - 22, col);
            i++;
        }
        int cy = top + ROWS_Y + MAX_ROWS * ROW + 2;
        WmdUi.divider(g, x, cy, w);
        Component crew = info.crew().isEmpty() ? Component.translatable("gui.wmd.station.nobody") : Component.translatable("gui.wmd.station.crew", String.join(", ", info.crew()));
        WmdUi.drawFit(g, font, crew, x, cy + 4, w, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, info.owner() ? Component.translatable("gui.wmd.station.fixed") : Component.translatable("gui.wmd.station.not_owner"), x, cy + 16, w, WmdUi.TEXT_MUTED);
    }

    private void logistics(GuiGraphics g, int lx, int top) {
        int x = lx + 8, w = LW - 16;
        WmdUi.panel(g, lx, top, LW, H);
        WmdUi.divider(g, x, top + 18, w);
        if (tab == 1) { sensor(g, x, top, w); return; }
        if (tab == 2) { laser(g, x, top, w); return; }
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.ground", info.groundX(), info.groundZ()), x, top + 21, w, WmdUi.TEXT);
        WmdUi.drawFit(g, font, info.rbPct() >= 0 ? Component.translatable("gui.wmd.station.rb_burning", info.rbPct(), info.rbX(), info.rbZ())
                : Component.translatable("gui.wmd.station.rb_none"), x, top + 32, w, info.rbPct() >= 0 ? WmdUi.CYAN : WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x, top + 43, w);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.pods", info.pods().size()), x, top + 47, w, WmdUi.TEXT_MUTED);
        int i = 0;
        for (int[] q : info.pods()) {
            if (i >= MAX_PODS) break;
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.pod_row", q[0], q[1]), x + 4, top + 60 + i * 14, w - (info.owner() ? 48 : 4), WmdUi.TEXT);
            i++;
        }
        if (info.pods().isEmpty()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.no_pods"), x + 4, top + 60, w - 4, WmdUi.TEXT_DIM);
        WmdUi.divider(g, x, top + 116, w);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.rb_title"), x, top + 121, w, WmdUi.TEXT_MUTED);
        if (!info.owner()) { WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.owner_only"), x, top + 136, w, WmdUi.TEXT_DIM); return; }
        g.drawString(font, "X", x + 2, top + 136, WmdUi.TEXT_MUTED, false);
        g.drawString(font, "Z", x + 72, top + 136, WmdUi.TEXT_MUTED, false);
        int[] t = target();
        Component cost;
        int col = WmdUi.TEXT_MUTED;
        if (t == null) cost = Component.translatable("gui.wmd.station.rb_enter");
        else {
            double d = Math.hypot(t[0] - info.groundX(), t[1] - info.groundZ());
            int need = Reboost.cells(d), tk = Reboost.ticks(d);
            if (d > Balance.REBOOST_MAX) { cost = Component.translatable("gui.wmd.station.rb_far", String.format("%,d", Balance.REBOOST_MAX)); col = WmdUi.BAD; }
            else { cost = Component.translatable("gui.wmd.station.rb_cost", String.format("%,d", Math.round(d)), need, String.format("%d:%02d", tk / 1200, tk / 20 % 60)); col = need <= info.cells() ? WmdUi.GOOD : WmdUi.BAD; }
        }
        WmdUi.drawFit(g, font, cost, x, top + 152, w, col);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.rb_cells", info.cells()), x, top + 163, w, WmdUi.TEXT_MUTED);
    }

    private void laserWidgets(int lx, int top) {
        if (info.laser() == -1) return;
        int x = lx + 8;
        charge = new WmdUi.KitButton(x, top + 33, 44, 12, Component.translatable("gui.wmd.station.lz_charge"), b -> {
            if (chargeArmed <= 0) { chargeArmed = ARM_TICKS; return; }
            chargeArmed = 0;
            laserCmd(StationLaser.CMD_CHARGE, 0);
        }, true);
        standDown = new WmdUi.KitButton(x + 46, top + 33, 44, 12, Component.translatable("gui.wmd.station.lz_stand_down"), b -> laserCmd(StationLaser.CMD_STAND_DOWN, 0));
        autoBtn = new WmdUi.KitButton(x + 92, top + 33, 42, 12, Component.empty(), b -> laserCmd(StationLaser.CMD_AUTO, 0));
        addRenderableWidget(charge); addRenderableWidget(standDown); addRenderableWidget(autoBtn);
        int i = 0;
        for (StationPayloads.LTarget t : info.targets()) {
            if (i >= MAX_TARGETS) break;
            int y = top + 58 + i * 15;
            final StationPayloads.LTarget tt = t;
            WmdUi.KitButton f = new WmdUi.KitButton(lx + LW - 8 - 28, y, 28, 12, Component.translatable("gui.wmd.station.lz_fire"),
                    b -> laserCmd(tt.kind() == StationLaser.T_SAT ? StationLaser.CMD_FIRE_SAT : tt.kind() == StationLaser.T_TUG ? StationLaser.CMD_FIRE_TUG : StationLaser.CMD_FIRE_MODULE, tt.id()), true);
            fireBtns.add(f);
            addRenderableWidget(f);
            if (t.kind() == StationLaser.T_MODULE)
                addRenderableWidget(new WmdUi.KitButton(lx + LW - 8 - 58, y, 28, 12, Component.translatable("gui.wmd.station.lz_asat"), b -> laserCmd(StationLaser.CMD_ASAT_PICK, tt.id())));
            i++;
        }
        if (info.asatPick() >= 0)
            addRenderableWidget(new WmdUi.KitButton(lx + LW - 8 - 34, top + H - 20, 34, 12, Component.translatable("gui.wmd.station.lz_clear"), b -> laserCmd(StationLaser.CMD_ASAT_PICK, -1)));
    }

    private boolean closeMode() {
        for (StationPayloads.Seen s : info.seen()) if (s.dist() >= 0 && s.dist() <= Balance.RDV_CLOSE) return true;
        return false;
    }

    private void laserCmd(int cmd, long arg) { PacketDistributor.sendToServer(new StationPayloads.Laser(info.slot(), cmd, arg)); }

    private void laser(GuiGraphics g, int x, int top, int w) {
        int st = info.laser();
        Component line = switch (st) {
            case -1 -> Component.translatable("gui.wmd.station.lz_none");
            case -2 -> Component.translatable("gui.wmd.station.lz_wrecked");
            case StationLaser.CHARGING -> Component.translatable("gui.wmd.station.lz_charging", info.laserPct());
            case StationLaser.ARMED -> Component.translatable("gui.wmd.station.lz_armed");
            case StationLaser.LOCK -> Component.translatable("gui.wmd.station.lz_lock");
            case StationLaser.FIRE -> Component.translatable("gui.wmd.station.lz_fire_now");
            case StationLaser.COOL -> Component.translatable("gui.wmd.station.lz_cool", (100 - info.laserPct()) * Balance.LASER_COOL_TICKS / 2000);
            default -> closeMode() ? Component.translatable("gui.wmd.station.lz_idle_close", String.format("%,d", Balance.LASER_CLOSE_SHOT_FE / 1000))
                    : Component.translatable("gui.wmd.station.lz_idle", String.format("%,d", Balance.LASER_SHOT_FE / 1000));
        };
        int col = st < 0 ? (st == -2 ? WmdUi.BAD : WmdUi.TEXT_DIM) : st == StationLaser.ARMED || st == StationLaser.FIRE || st == StationLaser.LOCK ? WmdUi.BAD
                : st == StationLaser.CHARGING ? WmdUi.WARN : st == StationLaser.COOL ? WmdUi.CYAN : WmdUi.TEXT;
        WmdUi.drawFit(g, font, line, x, top + 22, w, col);
        if (st == -1) return;
        if (!info.owner()) { WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.owner_only"), x, top + 36, w, WmdUi.TEXT_DIM); return; }
        WmdUi.divider(g, x, top + 47, w);
        if (info.sensor() != 3) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.lz_blind"), x, top + 50, w, WmdUi.TEXT_MUTED);
        else WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.lz_targets", info.targets().size()), x, top + 50, w, WmdUi.TEXT_MUTED);
        int i = 0;
        for (StationPayloads.LTarget t : info.targets()) {
            if (i >= MAX_TARGETS) break;
            int y = top + 60 + i * 15, tw = w - (t.kind() == StationLaser.T_MODULE ? 62 : 32);
            Component lab = t.kind() == StationLaser.T_SAT ? Component.literal(t.label()) : Component.translatable("gui.wmd.station.lz_module_row", t.label(), t.health());
            WmdUi.drawFit(g, font, lab, x, y, tw, t.kind() == StationLaser.T_SAT ? WmdUi.BLUE_LIGHT : WmdUi.TEXT);
            i++;
        }
        WmdUi.divider(g, x, top + H - 26, w);
        WmdUi.drawFit(g, font, info.asatPick() >= 0 ? Component.translatable("gui.wmd.station.lz_asat_aim", info.asatLabel()) : Component.translatable("gui.wmd.station.lz_asat_none"),
                x, top + H - 18, info.asatPick() >= 0 ? w - 38 : w, info.asatPick() >= 0 ? WmdUi.WARN : WmdUi.TEXT_DIM);
    }

    private static final int SEEN_Y = 92, SEEN_ROW = 22, MAX_SEEN = 3;
    private WmdUi.KitButton ramBtn;
    private int ramArmed;

    private void boardWidgets(int lx, int top) {
        ramBtn = null;
        if (info.seen().isEmpty()) return;
        int i = 0;
        for (StationPayloads.Seen v : info.seen()) {
            if (i >= MAX_SEEN) break;
            final int target = v.slot();
            WmdUi.KitButton b = new WmdUi.KitButton(lx + LW - 8 - 34, top + SEEN_Y + i * SEEN_ROW, 34, 12, Component.translatable("gui.wmd.station.pick"),
                    btn -> laserCmd(StationLaser.CMD_BOARD_PICK, target), true);
            b.active = info.boardPick() != target;
            addRenderableWidget(b);
            i++;
        }
        if (info.boardPick() >= 0) {
            int bw = (LW - 16 - 4) / 3, y = top + H - 22;
            addRenderableWidget(new WmdUi.KitButton(lx + 8, y, bw, 13, Component.translatable("gui.wmd.station.match"), b -> laserCmd(StationLaser.CMD_MATCH, info.boardPick())));
            ramBtn = new WmdUi.KitButton(lx + 8 + bw + 2, y, bw, 13, Component.translatable(ramArmed > 0 ? "gui.wmd.station.ram_sure" : "gui.wmd.station.ram"), b -> {
                if (ramArmed > 0) { ramArmed = 0; laserCmd(StationLaser.CMD_RAM, info.boardPick()); b.setMessage(Component.translatable("gui.wmd.station.ram")); }
                else { ramArmed = ARM_TICKS; b.setMessage(Component.translatable("gui.wmd.station.ram_sure")); }
            }, true);
            addRenderableWidget(ramBtn);
            addRenderableWidget(new WmdUi.KitButton(lx + 8 + 2 * (bw + 2), y, bw, 13, Component.translatable("gui.wmd.station.lz_clear"), b -> laserCmd(StationLaser.CMD_BOARD_PICK, -1)));
        }
    }

    private void sensor(GuiGraphics g, int x, int top, int w) {
        int st = info.sensor();
        int col = st == 3 ? WmdUi.GOOD : st == 2 ? WmdUi.WARN : st == 1 ? WmdUi.BAD : WmdUi.TEXT_DIM;
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.sensor_state." + st), x, top + 22, w, col);
        if (st != 3 && info.seen().isEmpty()) { WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.blind"), x, top + 33, w, WmdUi.TEXT_MUTED); return; }
        int y = top + 34;
        if (st != 3) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.by_eye"), x, y, w, WmdUi.WARN);
        else {
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.threats", info.threats().size()), x, y, w, info.threats().isEmpty() ? WmdUi.TEXT_MUTED : WmdUi.BAD);
            y += 11;
            int n = 0;
            for (StationPayloads.Threat t : info.threats()) {
                if (n++ >= 3) break;
                WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.threat_row", Component.translatable("sensor.wmd.threat." + t.kind()), t.from(), t.secs()), x + 4, y, w - 4, WmdUi.BAD);
                y += 10;
            }
        }
        WmdUi.divider(g, x, top + 77, w);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.seen", info.seen().size()), x, top + 81, w, WmdUi.TEXT_MUTED);
        if (info.seen().isEmpty()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.seen_none"), x + 4, top + SEEN_Y, w - 4, WmdUi.TEXT_DIM);
        int i = 0, tw = w - (info.owner() ? 40 : 4);
        for (StationPayloads.Seen s : info.seen()) {
            if (i >= MAX_SEEN) break;
            y = top + SEEN_Y + i * SEEN_ROW;
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.station.seen_row", s.owner(), s.slot(), s.modules(), s.health()), x + 4, y, tw, s.health() < 50 ? WmdUi.WARN : WmdUi.TEXT);
            Component over = s.dist() < 0 ? Component.translatable("gui.wmd.station.seen_over", s.gx(), s.gz())
                    : Component.translatable("gui.wmd.station.seen_dist", String.format("%,d", s.dist()));
            WmdUi.drawFit(g, font, over, x + 10, y + 10, tw - 6, s.dist() >= 0 && s.dist() <= Balance.RDV_CLOSE ? WmdUi.BAD : s.dist() >= 0 && s.dist() <= Balance.RDV_SIGHT ? WmdUi.WARN : WmdUi.TEXT_DIM);
            i++;
        }
        WmdUi.divider(g, x, top + H - 38, w);
        WmdUi.drawFit(g, font, info.boardPick() >= 0 ? Component.translatable("gui.wmd.station.board_pick", info.boardLabel(), info.boardPick()) : Component.translatable("gui.wmd.station.board_none"),
                x, top + H - 34, w, info.boardPick() >= 0 ? WmdUi.WARN : WmdUi.TEXT_DIM);
    }

    @Override public boolean isPauseScreen() { return false; }
}
