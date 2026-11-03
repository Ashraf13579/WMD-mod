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
public class RodScreen extends Screen {

    static final int W = 292, H = 214, MAP = 164, ARM_TICKS = 60;
    private RodPayloads.Info info;
    private int ticks, armed;
    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private WmdUi.KitButton set, arm, stand, useCc;

    static volatile int[] ccTarget;
    private int[] cc;

    public RodScreen(RodPayloads.Info info) {
        super(Component.translatable("gui.wmd.rod.title"));
        this.info = info;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!RodPayloads.openPending) return;
        RodPayloads.openPending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || RodPayloads.latest == null) return;
        if (mc.screen instanceof RodScreen s) {
            boolean rebuild = s.info.owner() != RodPayloads.latest.owner();
            s.info = RodPayloads.latest;
            if (rebuild) s.rebuildWidgets();
        } else {
            RodScreen s = new RodScreen(RodPayloads.latest);
            s.cc = ccTarget;
            ccTarget = null;
            if (RodPayloads.latest.target()) { s.xText = Integer.toString(RodPayloads.latest.tx()); s.zText = Integer.toString(RodPayloads.latest.tz()); }
            mc.setScreen(s);
        }
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int rx() { return left() + 8 + MAP + 10; }
    private int rw() { return W - 8 - MAP - 10 - 8; }

    @Override
    protected void init() {
        set = null; arm = null; stand = null; useCc = null;
        addRenderableWidget(new WmdUi.TabButton(left() - 23, top() + 22, "icon_camera", Component.translatable("gui.wmd.kit.tab.camera"),
                b -> { StationModuleBlockEntity cm = ClientCutscene.findModule(Station.ROD, info.slot()); if (cm != null) { minecraft.setScreen(null); ClientCutscene.watchModule(cm); } else ClientCutscene.camUnavailable(); }));
        if (!info.owner()) return;
        if (cc != null) useCc = addRenderableWidget(new WmdUi.KitButton(left() + 8, top() + 26 + MAP + 3, MAP, 13, Component.translatable("gui.wmd.rod.use_cc", cc[0], cc[1]), b -> {
            xText = Integer.toString(cc[0]); zText = Integer.toString(cc[1]);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            PacketDistributor.sendToServer(new RodPayloads.Cmd(info.slot(), RodOfDeath.CMD_TARGET, cc[0], cc[1]));
        }));
        int top = top(), rx = rx(), rw = rw();
        xBox = new EditBox(font, rx + 10, top + 90, rw / 2 - 14, 14, Component.literal("X"));
        zBox = new EditBox(font, rx + rw / 2 + 10, top + 90, rw / 2 - 10, 14, Component.literal("Z"));
        for (EditBox e : new EditBox[] { xBox, zBox }) {
            e.setMaxLength(9);
            e.setFilter(t -> t.isEmpty() || t.equals("-") || t.matches("-?\\d{1,8}"));
            addRenderableWidget(e);
        }
        xBox.setValue(xText); zBox.setValue(zText);
        xBox.setResponder(t -> { xText = t; armed = 0; });
        zBox.setResponder(t -> { zText = t; armed = 0; });
        set = addRenderableWidget(new WmdUi.KitButton(rx, top + 108, rw, 13, Component.translatable("gui.wmd.rod.set"), b -> sendTarget()));
        arm = addRenderableWidget(new WmdUi.KitButton(rx, top + H - 44, rw, 16, Component.translatable("gui.wmd.rod.arm"), b -> pressArm(), true));
        stand = addRenderableWidget(new WmdUi.KitButton(rx, top + H - 24, rw, 16, Component.translatable("gui.wmd.rod.stand_down"),
                b -> PacketDistributor.sendToServer(new RodPayloads.Cmd(info.slot(), RodOfDeath.CMD_STAND_DOWN, 0, 0))));
    }

    private int[] typed() {
        try { return new int[] { Integer.parseInt(xText.trim()), Integer.parseInt(zText.trim()) }; } catch (NumberFormatException e) { return null; }
    }

    private void sendTarget() {
        int[] t = typed();
        if (t != null) PacketDistributor.sendToServer(new RodPayloads.Cmd(info.slot(), RodOfDeath.CMD_TARGET, t[0], t[1]));
    }

    private void pressArm() {
        if (armed <= 0) { armed = ARM_TICKS; return; }
        armed = 0;
        PacketDistributor.sendToServer(new RodPayloads.Cmd(info.slot(), RodOfDeath.CMD_ARM, 0, 0));
    }

    private boolean aimable() { return info.state() == RodOfDeath.IDLE || info.state() == RodOfDeath.COOL; }

    @Override
    public void tick() {
        if (++ticks % 20 == 0) PacketDistributor.sendToServer(new RodPayloads.Ask(info.slot()));
        if (armed > 0) armed--;
        if (arm == null) return;
        boolean idle = info.state() == RodOfDeath.IDLE;
        arm.active = idle && info.target() && info.rods() > 0 && info.powered() && info.energy() >= Balance.ROD_SHOT_FE;
        arm.setMessage(Component.translatable(armed > 0 ? "gui.wmd.rod.arm_confirm" : "gui.wmd.rod.arm"));
        stand.active = info.state() >= RodOfDeath.ARM && info.state() <= RodOfDeath.FINAL;
        set.active = aimable() && typed() != null;
        if (useCc != null) useCc.active = aimable();
        xBox.setEditable(aimable()); zBox.setEditable(aimable());
    }

    private static int[] frame() {
        int[] f = ClientPlanet.nearFrame();
        if (f == null) return null;
        int span = f[2] * f[3];
        return new int[] { f[0] - span / 2, f[1] - span / 2, span };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int mx0 = left() + 8, my0 = top() + 26;
        int[] f = frame();
        if (button == 0 && info.owner() && aimable() && f != null && WmdUi.over(mx, my, mx0, my0, MAP, MAP)) {
            int x = (int) Math.floor(f[0] + (mx - mx0) / MAP * f[2]), z = (int) Math.floor(f[1] + (my - my0) / MAP * f[2]);
            xText = Integer.toString(x); zText = Integer.toString(z);
            if (xBox != null) { xBox.setValue(xText); zBox.setValue(zText); }
            PacketDistributor.sendToServer(new RodPayloads.Cmd(info.slot(), RodOfDeath.CMD_TARGET, x, z));
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
        int kind = st == -2 ? WmdUi.K_BAD : st >= RodOfDeath.ARM && st <= RodOfDeath.FALL ? WmdUi.K_BAD : st == RodOfDeath.IDLE ? WmdUi.K_GOOD : WmdUi.K_WARN;
        WmdUi.header(g, font, left, top, W, new ItemStack(Wmd.ORBITAL_ROD.get()), Component.translatable("gui.wmd.rod.title"), stateName(st), kind, st >= RodOfDeath.ARM && st <= RodOfDeath.FALL);

        int x0 = left + 8, y0 = top + 26;
        WmdUi.well(g, x0, y0, MAP, MAP);
        int[] f = frame();
        if (f == null || !ClientPlanet.drawMap(g, x0, y0, MAP)) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.no_picture"), x0 + 6, y0 + MAP / 2 - 4, MAP - 12, WmdUi.TEXT_MUTED);
        else {

            int[] a = toMapAt(f, info.gx() - Balance.ROD_TARGET_R, info.gz() - Balance.ROD_TARGET_R), b = toMapAt(f, info.gx() + Balance.ROD_TARGET_R, info.gz() + Balance.ROD_TARGET_R);
            outline(g, Math.max(x0, a[0]), Math.max(y0, a[1]), Math.min(x0 + MAP, b[0]), Math.min(y0 + MAP, b[1]), 0x806FA8FF);
            int[] gp = toMapAt(f, info.gx(), info.gz());
            g.fill(gp[0] - 2, gp[1] - 2, gp[0] + 2, gp[1] + 2, 0xFF3A7BD5);
            if (info.target()) {
                int[] t = toMapAt(f, info.tx(), info.tz());
                boolean live = st >= RodOfDeath.ARM && st <= RodOfDeath.FALL;
                int col = live && (ticks / 5) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF3B30;
                g.fill(t[0] - 5, t[1], t[0] + 6, t[1] + 1, col);
                g.fill(t[0], t[1] - 5, t[0] + 1, t[1] + 6, col);
                int r = Math.max(2, Math.round(224.0F / f[2] * MAP));
                ring(g, t[0], t[1], r, 0xC0FF3B30);
            }
            if (WmdUi.over(mx, my, x0, y0, MAP, MAP)) {
                int wx = (int) Math.floor(f[0] + (mx - x0) / (double) MAP * f[2]), wz = (int) Math.floor(f[1] + (my - y0) / (double) MAP * f[2]);
                WmdUi.drawFit(g, font, Component.literal(wx + ", " + wz), x0 + 3, y0 + MAP - 10, MAP - 6, WmdUi.TEXT);
            }
        }
        if (useCc == null) WmdUi.drawFit(g, font, Component.translatable(info.owner() ? "gui.wmd.rod.hint" : "gui.wmd.rod.not_owner"), x0, y0 + MAP + 5, MAP, WmdUi.TEXT_MUTED);

        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.rods", info.rods(), Balance.ROD_RACK_MAX), rx, top + 28, rw, info.rods() > 0 ? WmdUi.TEXT : WmdUi.BAD);
        boolean fe = info.energy() >= Balance.ROD_SHOT_FE;
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.power", String.format("%,d", info.energy() / 1000)), rx, top + 40, rw, fe && info.powered() ? WmdUi.GOOD : WmdUi.BAD);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.shot", String.format("%,d", Balance.ROD_SHOT_FE / 1000)), rx, top + 52, rw, WmdUi.TEXT_MUTED);
        WmdUi.divider(g, rx, top + 64, rw);
        WmdUi.drawFit(g, font, info.target() ? Component.translatable("gui.wmd.rod.target", info.tx(), info.tz()) : Component.translatable("gui.wmd.rod.no_target"),
                rx, top + 69, rw, info.target() ? WmdUi.ORANGE_LIGHT : WmdUi.TEXT_MUTED);
        if (info.owner()) {
            WmdUi.drawFit(g, font, Component.literal("X"), rx + 2, top + 93, 8, WmdUi.TEXT_MUTED);
            WmdUi.drawFit(g, font, Component.literal("Z"), rx + rw / 2 + 2, top + 93, 8, WmdUi.TEXT_MUTED);
        }
        WmdUi.divider(g, rx, top + 126, rw);
        Component stLine = st == -1 ? Component.translatable("gui.wmd.rod.none") : st == -2 ? Component.translatable("gui.wmd.rod.wrecked")
                : Component.translatable("gui.wmd.rod.state_pct", stateName(st), info.pct());
        WmdUi.drawFit(g, font, stLine, rx, top + 131, rw, kind == WmdUi.K_BAD ? WmdUi.BAD : kind == WmdUi.K_WARN ? WmdUi.WARN : WmdUi.GOOD);
        WmdUi.progressBar(g, rx, top + 143, rw, 6, info.pct() / 100.0F, st > RodOfDeath.IDLE);
        if (info.secs() >= 0)
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.rod.impact", String.format("%d:%02d", info.secs() / 60, info.secs() % 60)), rx, top + 154, rw,
                    (ticks / 10) % 2 == 0 ? WmdUi.BAD : WmdUi.TEXT);
    }

    static Component stateName(int st) { return Component.translatable("gui.wmd.rod.state." + Math.max(-2, Math.min(8, st))); }

    private static int[] toMap(int[] f, double wx, double wz) {
        return new int[] { (int) Math.round((wx - f[0]) / f[2] * MAP), (int) Math.round((wz - f[1]) / f[2] * MAP) };
    }

    private int[] toMapAt(int[] f, double wx, double wz) { int[] p = toMap(f, wx, wz); return new int[] { p[0] + left() + 8, p[1] + top() + 26 }; }

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
