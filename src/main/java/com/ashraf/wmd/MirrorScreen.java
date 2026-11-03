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
public class MirrorScreen extends Screen {

    static final int W = 292, H = 214, MAP = 164, ARM_TICKS = 60;
    private MirrorPayloads.Info info;
    private int ticks, armed;
    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private WmdUi.KitButton set, fold, focus, off, useCc;

    static volatile int[] ccTarget;
    private int[] cc;

    public MirrorScreen(MirrorPayloads.Info info) {
        super(Component.translatable("gui.wmd.mirror.title"));
        this.info = info;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!MirrorPayloads.openPending) return;
        MirrorPayloads.openPending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || MirrorPayloads.latest == null) return;
        if (mc.screen instanceof MirrorScreen s) {
            boolean rebuild = s.info.owner() != MirrorPayloads.latest.owner();
            s.info = MirrorPayloads.latest;
            if (rebuild) s.rebuildWidgets();
        } else {
            MirrorScreen s = new MirrorScreen(MirrorPayloads.latest);
            s.cc = ccTarget;
            ccTarget = null;
            if (MirrorPayloads.latest.goal()) { s.xText = Integer.toString(MirrorPayloads.latest.gx()); s.zText = Integer.toString(MirrorPayloads.latest.gz()); }
            mc.setScreen(s);
        }
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int rx() { return left() + 8 + MAP + 10; }
    private int rw() { return W - 8 - MAP - 10 - 8; }

    @Override
    protected void init() {
        set = null; fold = null; focus = null; off = null; useCc = null;
        addRenderableWidget(new WmdUi.TabButton(left() - 23, top() + 22, "icon_camera", Component.translatable("gui.wmd.kit.tab.camera"),
                b -> { StationModuleBlockEntity cm = ClientCutscene.findModule(Station.MIRROR, info.slot()); if (cm != null) { minecraft.setScreen(null); ClientCutscene.watchModule(cm); } else ClientCutscene.camUnavailable(); }));
        if (!info.owner()) return;
        if (cc != null) useCc = addRenderableWidget(new WmdUi.KitButton(left() + 8, top() + 26 + MAP + 3, MAP, 13, Component.translatable("gui.wmd.rod.use_cc", cc[0], cc[1]), b -> {
            xText = Integer.toString(cc[0]); zText = Integer.toString(cc[1]);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            cmd(MirrorFocus.CMD_TARGET, cc[0], cc[1]);
        }));
        int top = top(), rx = rx(), rw = rw();
        xBox = new EditBox(font, rx + 10, top + 106, rw / 2 - 14, 14, Component.literal("X"));
        zBox = new EditBox(font, rx + rw / 2 + 10, top + 106, rw / 2 - 10, 14, Component.literal("Z"));
        for (EditBox e : new EditBox[] { xBox, zBox }) {
            e.setMaxLength(9);
            e.setFilter(t -> t.isEmpty() || t.equals("-") || t.matches("-?\\d{1,8}"));
            addRenderableWidget(e);
        }
        xBox.setValue(xText); zBox.setValue(zText);
        xBox.setResponder(t -> xText = t);
        zBox.setResponder(t -> zText = t);
        set = addRenderableWidget(new WmdUi.KitButton(rx, top + 124, rw, 13, Component.translatable("gui.wmd.rod.set"), b -> sendGoal()));
        fold = addRenderableWidget(new WmdUi.KitButton(rx, top + H - 62, rw, 16, Component.translatable("gui.wmd.mirror.deploy"),
                b -> cmd(info.state() == MirrorFocus.FOLDED ? MirrorFocus.CMD_DEPLOY : MirrorFocus.CMD_FOLD, 0, 0)));
        focus = addRenderableWidget(new WmdUi.KitButton(rx, top + H - 43, rw, 16, Component.translatable("gui.wmd.mirror.focus"), b -> pressFocus(), true));
        off = addRenderableWidget(new WmdUi.KitButton(rx, top + H - 24, rw, 16, Component.translatable("gui.wmd.mirror.off"), b -> cmd(MirrorFocus.CMD_OFF, 0, 0)));
    }

    private void cmd(int c, int x, int z) { PacketDistributor.sendToServer(new MirrorPayloads.Cmd(info.slot(), c, x, z)); }

    private int[] typed() {
        try { return new int[] { Integer.parseInt(xText.trim()), Integer.parseInt(zText.trim()) }; } catch (NumberFormatException e) { return null; }
    }

    private void sendGoal() { int[] t = typed(); if (t != null) cmd(MirrorFocus.CMD_TARGET, t[0], t[1]); }

    private void pressFocus() {
        if (armed <= 0) { armed = ARM_TICKS; return; }
        armed = 0;
        cmd(MirrorFocus.CMD_FOCUS, 0, 0);
    }

    private boolean up() { return info.state() == MirrorFocus.READY || info.state() == MirrorFocus.BURNING || info.state() == MirrorFocus.COOLING; }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) PacketDistributor.sendToServer(new MirrorPayloads.Ask(info.slot()));
        if (armed > 0) armed--;
        if (fold == null) return;
        int st = info.state();
        fold.setMessage(Component.translatable(st == MirrorFocus.FOLDED || st == MirrorFocus.DEPLOYING ? "gui.wmd.mirror.deploy" : "gui.wmd.mirror.fold"));
        fold.active = st == MirrorFocus.FOLDED || st == MirrorFocus.READY || st == MirrorFocus.COOLING;
        focus.active = st == MirrorFocus.READY && info.goal() && info.sunlit() && info.day() && info.clear();
        focus.setMessage(Component.translatable(armed > 0 ? "gui.wmd.mirror.focus_confirm" : "gui.wmd.mirror.focus"));
        off.active = st == MirrorFocus.BURNING;
        set.active = st >= MirrorFocus.FOLDED && typed() != null;
        if (useCc != null) useCc.active = st >= MirrorFocus.FOLDED;
    }

    private static int[] frame() {
        int[] f = ClientPlanet.nearFrame();
        if (f == null) return null;
        int span = f[2] * f[3];
        return new int[] { f[0] - span / 2, f[1] - span / 2, span };
    }

    private int[] toMapAt(int[] f, double wx, double wz) {
        return new int[] { left() + 8 + (int) Math.round((wx - f[0]) / f[2] * MAP), top() + 26 + (int) Math.round((wz - f[1]) / f[2] * MAP) };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mx0 = left() + 8, my0 = top() + 26;
        int[] f = frame();
        if (button == 0 && info.owner() && info.state() >= MirrorFocus.FOLDED && f != null && WmdUi.over(mx, my, mx0, my0, MAP, MAP)) {
            int x = (int) Math.floor(f[0] + (mx - mx0) / MAP * f[2]), z = (int) Math.floor(f[1] + (my - my0) / MAP * f[2]);
            xText = Integer.toString(x); zText = Integer.toString(z);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            cmd(MirrorFocus.CMD_TARGET, x, z);
            WmdUi.click();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = left(), top = top(), rx = rx(), rw = rw();
        WmdUi.panel(g, left, top, W, H);
        int st = info.state();
        int kind = st < 0 ? WmdUi.K_BAD : st == MirrorFocus.BURNING ? WmdUi.K_BAD : st == MirrorFocus.READY ? WmdUi.K_GOOD : WmdUi.K_WARN;
        WmdUi.header(g, font, left, top, W, new ItemStack(Wmd.STATION_KIT_MIRROR.get()), Component.translatable("gui.wmd.mirror.title"), stateName(st), kind, st == MirrorFocus.BURNING);

        int x0 = left + 8, y0 = top + 26;
        WmdUi.well(g, x0, y0, MAP, MAP);
        int[] f = frame();
        if (f == null || !ClientPlanet.drawMap(g, x0, y0, MAP)) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.no_picture"), x0 + 6, y0 + MAP / 2 - 4, MAP - 12, WmdUi.TEXT_MUTED);
        else {

            int[] a = toMapAt(f, info.groundX() - Balance.MIRROR_TARGET_R, info.groundZ() - Balance.MIRROR_TARGET_R), b = toMapAt(f, info.groundX() + Balance.MIRROR_TARGET_R, info.groundZ() + Balance.MIRROR_TARGET_R);
            outline(g, Math.max(x0, a[0]), Math.max(y0, a[1]), Math.min(x0 + MAP, b[0]), Math.min(y0 + MAP, b[1]), 0x806FA8FF);
            int[] gp = toMapAt(f, info.groundX(), info.groundZ());
            g.fill(gp[0] - 2, gp[1] - 2, gp[0] + 2, gp[1] + 2, 0xFF3A7BD5);
            if (info.goal()) {
                int[] t = toMapAt(f, info.gx(), info.gz());
                g.fill(t[0] - 4, t[1], t[0] + 5, t[1] + 1, 0xFFF2C08A);
                g.fill(t[0], t[1] - 4, t[0] + 1, t[1] + 5, 0xFFF2C08A);
            }
            if (info.spot()) {
                int[] s = toMapAt(f, info.sx(), info.sz());
                boolean burn = st == MirrorFocus.BURNING;
                int col = burn ? ((ticks / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFC040) : 0xFFFFE6A0;
                g.fill(s[0] - 2, s[1] - 2, s[0] + 3, s[1] + 3, col);
                ring(g, s[0], s[1], Math.max(2, Math.round((float) Balance.MIRROR_WARN_R / f[2] * MAP)), burn ? 0xC0FF5040 : 0x80FFD27F);
            }
            if (WmdUi.over(mx, my, x0, y0, MAP, MAP)) {
                int wx = (int) Math.floor(f[0] + (mx - x0) / (double) MAP * f[2]), wz = (int) Math.floor(f[1] + (my - y0) / (double) MAP * f[2]);
                WmdUi.drawFit(g, font, Component.literal(wx + ", " + wz), x0 + 3, y0 + MAP - 10, MAP - 6, WmdUi.TEXT);
            }
        }
        if (useCc == null) WmdUi.drawFit(g, font, Component.translatable(info.owner() ? "gui.wmd.mirror.hint" : "gui.wmd.rod.not_owner"), x0, y0 + MAP + 5, MAP, WmdUi.TEXT_MUTED);

        Component stLine = st == -1 ? Component.translatable("gui.wmd.mirror.none") : st == -2 ? Component.translatable("gui.wmd.rod.wrecked")
                : Component.translatable("gui.wmd.rod.state_pct", stateName(st), info.pct());
        WmdUi.drawFit(g, font, stLine, rx, top + 28, rw, kind == WmdUi.K_BAD ? WmdUi.BAD : kind == WmdUi.K_WARN ? WmdUi.WARN : WmdUi.GOOD);
        WmdUi.progressBar(g, rx, top + 40, rw, 6, info.pct() / 100.0F, st == MirrorFocus.BURNING || st == MirrorFocus.DEPLOYING || st == MirrorFocus.FOLDING);
        if (st == MirrorFocus.BURNING || st == MirrorFocus.COOLING)
            WmdUi.drawFit(g, font, Component.translatable(st == MirrorFocus.BURNING ? "gui.wmd.mirror.burn_left" : "gui.wmd.mirror.cool_left",
                    String.format("%d:%02d", info.secs() / 60, info.secs() % 60)), rx, top + 50, rw, st == MirrorFocus.BURNING ? WmdUi.BAD : WmdUi.WARN);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.mirror.sun"), rx, top + 62, rw, info.sunlit() ? WmdUi.GOOD : WmdUi.BAD);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.mirror.day"), rx, top + 72, rw, info.day() ? WmdUi.GOOD : WmdUi.BAD);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.mirror.clear"), rx, top + 82, rw, info.clear() ? WmdUi.GOOD : WmdUi.BAD);
        WmdUi.divider(g, rx, top + 93, rw);
        if (info.owner()) {
            WmdUi.drawFit(g, font, Component.literal("X"), rx + 2, top + 109, 8, WmdUi.TEXT_MUTED);
            WmdUi.drawFit(g, font, Component.literal("Z"), rx + rw / 2 + 2, top + 109, 8, WmdUi.TEXT_MUTED);
        }
        WmdUi.drawFit(g, font, info.goal() ? Component.translatable("gui.wmd.mirror.goal", info.gx(), info.gz()) : Component.translatable("gui.wmd.mirror.no_goal"),
                rx, top + 96, rw, info.goal() ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT_MUTED);
        if (!info.owner())
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.power", String.format("%,d", info.energy() / 1000)), rx, top + 140, rw, WmdUi.TEXT_MUTED);
    }

    static Component stateName(int st) { return Component.translatable("gui.wmd.mirror.state." + Math.max(-2, Math.min(5, st))); }

    private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int col) {
        g.fill(x0, y0, x1, y0 + 1, col); g.fill(x0, y1 - 1, x1, y1, col);
        g.fill(x0, y0, x0 + 1, y1, col); g.fill(x1 - 1, y0, x1, y1, col);
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2.0 / 48.0;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
