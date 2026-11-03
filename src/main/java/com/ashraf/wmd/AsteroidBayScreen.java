package com.ashraf.wmd;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public class AsteroidBayScreen extends Screen {

    static final int W = 384, H = 228, LW = 228, MAP = 132, ARM_TICKS = 60;

    static volatile int[] ccTarget;
    private int[] cc;
    private WmdUi.KitButton useCc;
    private AsteroidBayPayloads.Info info;
    private int ticks, armed;
    private WmdUi.KitButton build, launch, set, drop;
    private EditBox xBox, zBox;
    private String xText = "", zText = "";

    public AsteroidBayScreen(AsteroidBayPayloads.Info info) {
        super(Component.translatable("gui.wmd.abay.title"));
        this.info = info;
        if (info.aim()) { xText = Integer.toString(info.tx()); zText = Integer.toString(info.tz()); }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!AsteroidBayPayloads.openPending) return;
        AsteroidBayPayloads.openPending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || AsteroidBayPayloads.latest == null) return;
        if (mc.screen instanceof AsteroidBayScreen s) {
            boolean rebuild = s.info.owner() != AsteroidBayPayloads.latest.owner();
            s.info = AsteroidBayPayloads.latest;
            if (rebuild) s.rebuildWidgets();
        } else {
            AsteroidBayScreen s = new AsteroidBayScreen(AsteroidBayPayloads.latest);
            s.cc = ccTarget;
            ccTarget = null;
            mc.setScreen(s);
        }
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int rx() { return left() + LW + 16; }
    private int rw() { return W - LW - 24; }

    private void cmd(int c, int x, int z) { PacketDistributor.sendToServer(new AsteroidBayPayloads.Cmd(info.slot(), c, x, z)); }

    @Override
    protected void init() {
        build = null; launch = null; set = null; drop = null; xBox = null; zBox = null; useCc = null;
        addRenderableWidget(new WmdUi.TabButton(left() - 23, top() + 22, "icon_camera", Component.translatable("gui.wmd.kit.tab.camera"),
                b -> { StationModuleBlockEntity cm = ClientCutscene.findModule(Station.BAY, info.slot()); if (cm != null) { minecraft.setScreen(null); ClientCutscene.watchModule(cm); } else ClientCutscene.camUnavailable(); }));
        if (!info.owner()) return;
        if (cc != null) useCc = addRenderableWidget(new WmdUi.KitButton(rx(), top() + H - 18, rw(), 13, Component.translatable("gui.wmd.rod.use_cc", cc[0], cc[1]), b -> {
            xText = Integer.toString(cc[0]); zText = Integer.toString(cc[1]);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            cmd(AsteroidBay.CMD_TARGET, cc[0], cc[1]);
        }));
        int bw = (LW - 12) / 2, rx = rx(), rw = rw(), top = top();
        build = addRenderableWidget(new WmdUi.KitButton(left() + 8, top + H - 24, bw, 16, Component.translatable("gui.wmd.abay.build"), b -> cmd(AsteroidBay.CMD_BUILD, 0, 0)));
        launch = addRenderableWidget(new WmdUi.KitButton(left() + 12 + bw, top + H - 24, bw, 16, Component.translatable("gui.wmd.abay.launch"), b -> cmd(AsteroidBay.CMD_LAUNCH, 0, 0), true));
        xBox = new EditBox(font, rx + 10, top + 26 + MAP + 6, rw / 2 - 14, 14, Component.literal("X"));
        zBox = new EditBox(font, rx + rw / 2 + 10, top + 26 + MAP + 6, rw / 2 - 10, 14, Component.literal("Z"));
        for (EditBox e : new EditBox[] { xBox, zBox }) {
            e.setMaxLength(9);
            e.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,8}"));
            addRenderableWidget(e);
        }
        xBox.setValue(xText); zBox.setValue(zText);
        xBox.setResponder(s -> xText = s);
        zBox.setResponder(s -> zText = s);
        set = addRenderableWidget(new WmdUi.KitButton(rx, top + 26 + MAP + 23, rw / 2 - 2, 13, Component.translatable("gui.wmd.rod.set"), b -> {
            int[] t = typed();
            if (t != null) cmd(AsteroidBay.CMD_TARGET, t[0], t[1]);
        }));
        drop = addRenderableWidget(new WmdUi.KitButton(rx + rw / 2 + 2, top + 26 + MAP + 23, rw / 2 - 2, 13, Component.translatable("gui.wmd.abay.drop"), b -> {
            if (armed <= 0) { armed = ARM_TICKS; return; }
            armed = 0;
            cmd(AsteroidBay.CMD_DROP, 0, 0);
        }, true));
    }

    private int[] typed() {
        try { return new int[] { Integer.parseInt(xText.trim()), Integer.parseInt(zText.trim()) }; } catch (NumberFormatException e) { return null; }
    }

    private boolean partsIn() { return info.am() >= Balance.TUG_ANTIMATTER && info.dt() >= Balance.TUG_DT && info.cores() >= Balance.TUG_CORES; }

    private boolean rockHeld() { return info.state() == AsteroidBay.PUSH || info.state() == AsteroidBay.PARKED; }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) PacketDistributor.sendToServer(new AsteroidBayPayloads.Ask(info.slot()));
        if (armed > 0) armed--;
        if (build != null) build.active = info.state() == AsteroidBay.NONE && partsIn();
        if (launch != null) launch.active = info.state() == AsteroidBay.READY && info.pass() == 1 && !info.launched() && info.sensor();
        if (set != null) set.active = rockHeld() && typed() != null;
        if (useCc != null) useCc.active = rockHeld();
        if (drop != null) {
            drop.active = info.state() == AsteroidBay.PARKED && info.aim();
            drop.setMessage(Component.translatable(armed > 0 ? "gui.wmd.abay.drop_confirm" : "gui.wmd.abay.drop"));
        }
    }

    private static String clock(int s) { return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60); }

    private static int[] frame() {
        int[] f = ClientPlanet.nearFrame();
        if (f == null) return null;
        int span = f[2] * f[3];
        return new int[] { f[0] - span / 2, f[1] - span / 2, span };
    }

    private int[] toMapAt(int[] f, double wx, double wz) {
        return new int[] { rx() + (int) Math.round((wx - f[0]) / f[2] * MAP), top() + 26 + (int) Math.round((wz - f[1]) / f[2] * MAP) };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mx0 = rx(), my0 = top() + 26;
        int[] f = frame();
        if (button == 0 && info.owner() && rockHeld() && f != null && WmdUi.over(mx, my, mx0, my0, MAP, MAP)) {
            int x = (int) Math.floor(f[0] + (mx - mx0) / MAP * f[2]), z = (int) Math.floor(f[1] + (my - my0) / MAP * f[2]);
            xText = Integer.toString(x); zText = Integer.toString(z);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            cmd(AsteroidBay.CMD_TARGET, x, z);
            WmdUi.click();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = left(), top = top(), x = left + 8, w = LW - 8;
        WmdUi.panel(g, left, top, W, H);
        int st = info.state();
        boolean out = st >= AsteroidBay.LAUNCH;
        int kind = st < 0 || st == AsteroidBay.FALL ? WmdUi.K_BAD : st == AsteroidBay.READY || st == AsteroidBay.PARKED ? WmdUi.K_GOOD
                : st == AsteroidBay.BUILDING ? (info.paused() ? WmdUi.K_WARN : WmdUi.K_INFO) : out ? WmdUi.K_WARN : WmdUi.K_IDLE;
        WmdUi.header(g, font, left, top, W, new ItemStack(Wmd.STATION_KIT_BAY.get()), Component.translatable("gui.wmd.abay.title"), stateName(st), kind,
                st == AsteroidBay.BUILDING && !info.paused() || st == AsteroidBay.LAUNCH || st == AsteroidBay.CAPTURE || st == AsteroidBay.FALL);

        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.parts"), x, top + 26, w, WmdUi.TEXT_MUTED);
        ItemStack[] icons = { new ItemStack(Wmd.ANTIMATTER_CELL.get()), new ItemStack(Wmd.DT_FUEL_CANISTER.get()), new ItemStack(Wmd.TUG_CORE.get()) };
        int[] have = { info.am(), info.dt(), info.cores() }, need = { Balance.TUG_ANTIMATTER, Balance.TUG_DT, Balance.TUG_CORES };
        boolean inTug = st >= AsteroidBay.BUILDING;
        for (int i = 0; i < 3; i++) {
            int sx = x + 4 + i * 74, sy = top + 40;
            WmdUi.slot(g, sx, sy, WmdUi.BLUE);
            g.renderItem(icons[i], sx, sy);
            boolean ok = inTug || have[i] >= need[i];
            WmdUi.drawFit(g, font, Component.literal((inTug ? need[i] : have[i]) + " / " + need[i]), sx + 22, sy + 1, 48, ok ? WmdUi.GOOD : WmdUi.TEXT);
            WmdUi.drawFit(g, font, icons[i].getHoverName(), sx + 22, sy + 10, 48, WmdUi.TEXT_MUTED);
        }
        WmdUi.drawFit(g, font, Component.translatable(st == AsteroidBay.NONE ? "gui.wmd.abay.hint_load" : "gui.wmd.abay.hint_in"), x, top + 62, w, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, x, top + 74, w);

        Component line = st == -1 ? Component.translatable("gui.wmd.abay.none") : st == -2 ? Component.translatable("gui.wmd.abay.wrecked")
                : st >= AsteroidBay.READY ? Component.translatable("gui.wmd.abay.built")
                : st == AsteroidBay.BUILDING ? (info.paused() ? Component.translatable("gui.wmd.abay.paused", String.format("%,d", Balance.BAY_BUILD_MIN_BUFFER / 1_000_000L))
                        : Component.translatable("gui.wmd.abay.building", info.pm() / 10, clock(info.secs())))
                : Component.translatable(partsIn() ? "gui.wmd.abay.can_build" : "gui.wmd.abay.need_parts");
        int col = st < 0 ? WmdUi.BAD : st >= AsteroidBay.READY ? WmdUi.GOOD : st == AsteroidBay.BUILDING && info.paused() ? WmdUi.WARN : WmdUi.TEXT;
        WmdUi.drawFit(g, font, line, x, top + 80, w, col);
        WmdUi.progressBar(g, x, top + 92, w, 6, st >= AsteroidBay.READY ? 1.0F : st == AsteroidBay.BUILDING ? info.pm() / 1000.0F : 0.0F, st == AsteroidBay.BUILDING && !info.paused());
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.draw", String.format("%,d", Balance.BAY_BUILD_FE), Balance.TUG_BUILD_TICKS / 1200), x, top + 104, w, WmdUi.CYAN);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.power", String.format("%,d", info.energy() / 1000)), x, top + 116, w, WmdUi.TEXT_MUTED);

        WmdUi.divider(g, x, top + 128, w);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.tracker"), x, top + 134, w, WmdUi.TEXT_MUTED);
        Component pass = !info.sensor() ? Component.translatable("gui.wmd.abay.blind")
                : info.pass() == 1 ? Component.translatable("gui.wmd.abay.detected", info.passSize(), clock(info.passSecs()))
                : info.pass() == 2 ? Component.translatable("gui.wmd.abay.taken") : Component.translatable("gui.wmd.abay.no_object");
        boolean hot = info.sensor() && info.pass() == 1;
        WmdUi.drawFit(g, font, pass, x, top + 146, w, !info.sensor() ? WmdUi.BAD : hot ? ((ticks / 10) % 2 == 0 ? WmdUi.ORANGE_LIGHT : WmdUi.WARN) : WmdUi.TEXT_MUTED);
        Component tug = switch (st) {
            case AsteroidBay.LAUNCH -> Component.translatable("gui.wmd.abay.t.launch", clock(info.phaseSecs()));
            case AsteroidBay.CHASE -> Component.translatable("gui.wmd.abay.t.chase", clock(info.phaseSecs()));
            case AsteroidBay.CAPTURE -> Component.translatable("gui.wmd.abay.t.capture");
            case AsteroidBay.PUSH -> Component.translatable("gui.wmd.abay.t.push", info.rock(), clock(info.phaseSecs()));
            case AsteroidBay.PARKED -> Component.translatable("gui.wmd.abay.t.parked", info.rock(), Balance.ASTEROID_PARK_GAP);
            case AsteroidBay.RETURN -> Component.translatable("gui.wmd.abay.t.return", clock(info.phaseSecs()));
            case AsteroidBay.FALL -> Component.translatable("gui.wmd.abay.t.fall", info.tx(), info.tz(), clock(info.phaseSecs()));
            case AsteroidBay.READY -> Component.translatable(info.launched() ? "gui.wmd.abay.t.launched" : "gui.wmd.abay.t.ready");
            default -> Component.translatable("gui.wmd.abay.t.none");
        };
        WmdUi.drawFit(g, font, tug, x, top + 158, w, st == AsteroidBay.FALL ? ((ticks / 10) % 2 == 0 ? WmdUi.BAD : WmdUi.TEXT) : out ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT);
        if (st == AsteroidBay.LAUNCH) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.suits"), x, top + 170, w, (ticks / 10) % 2 == 0 ? WmdUi.BAD : WmdUi.WARN);
        if (st == AsteroidBay.FALL && info.pieces() > 1) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.pieces", info.pieces()), x, top + 170, w, WmdUi.WARN);
        if (!info.owner()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.abay.not_owner"), x, top + H - 20, w, WmdUi.TEXT_MUTED);

        int rx = rx(), rw = rw(), y0 = top + 26;
        WmdUi.well(g, rx, y0, MAP, MAP);
        int[] f = frame();
        if (f == null || !ClientPlanet.drawMap(g, rx, y0, MAP)) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.no_picture"), rx + 4, y0 + MAP / 2 - 4, MAP - 8, WmdUi.TEXT_MUTED);
        else {

            int[] a = toMapAt(f, info.groundX() - Balance.ASTEROID_TARGET_R, info.groundZ() - Balance.ASTEROID_TARGET_R), b = toMapAt(f, info.groundX() + Balance.ASTEROID_TARGET_R, info.groundZ() + Balance.ASTEROID_TARGET_R);
            outline(g, Math.max(rx, a[0]), Math.max(y0, a[1]), Math.min(rx + MAP, b[0]), Math.min(y0 + MAP, b[1]), 0x806FA8FF);
            int[] gp = toMapAt(f, info.groundX(), info.groundZ());
            g.fill(gp[0] - 2, gp[1] - 2, gp[0] + 2, gp[1] + 2, 0xFF3A7BD5);
            if (info.aim()) {
                int[] t = toMapAt(f, info.tx(), info.tz());
                int c = st == AsteroidBay.FALL && (ticks / 6) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF5040;
                g.fill(t[0] - 4, t[1], t[0] + 5, t[1] + 1, c);
                g.fill(t[0], t[1] - 4, t[0] + 1, t[1] + 5, c);
                ring(g, t[0], t[1], Math.max(2, Math.round(320.0F / f[2] * MAP)), 0xC0FF5040);
            }
            if (WmdUi.over(mx, my, rx, y0, MAP, MAP)) {
                int wx = (int) Math.floor(f[0] + (mx - rx) / (double) MAP * f[2]), wz = (int) Math.floor(f[1] + (my - y0) / (double) MAP * f[2]);
                WmdUi.drawFit(g, font, Component.literal(wx + ", " + wz), rx + 3, y0 + MAP - 10, MAP - 6, WmdUi.TEXT);
            }
        }
        if (info.owner()) {
            WmdUi.drawFit(g, font, Component.literal("X"), rx + 2, y0 + MAP + 9, 8, WmdUi.TEXT_MUTED);
            WmdUi.drawFit(g, font, Component.literal("Z"), rx + rw / 2 + 2, y0 + MAP + 9, 8, WmdUi.TEXT_MUTED);
        }
        Component tl = info.aim() ? Component.translatable("gui.wmd.abay.target", info.tx(), info.tz()) : Component.translatable(rockHeld() ? "gui.wmd.abay.no_target" : "gui.wmd.abay.no_rock");
        WmdUi.drawFit(g, font, tl, rx, top + H - 30, rw, info.aim() ? WmdUi.BAD : WmdUi.TEXT_MUTED);
    }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col); g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col); g.fill(x1 - 1, y0, x1, y1, col);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2.0 / 48.0;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, col);
        }
    }

    static Component stateName(int st) { return Component.translatable("gui.wmd.abay.state." + Math.max(-2, Math.min(9, st))); }

    @Override public boolean isPauseScreen() { return false; }
}
