package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreenC extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 140;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final int SEL_FILL = 0xFF2D3A4A, HOVER_FILL = 0xFF263040;
    private static final int ROW_Y = 42, ROW_H = 22, VIEW_Y = 110, VIEW_H = 52;

    private EditBox xBox, zBox;
    private Button deorbitBtn, ewBtn, clearBtn, rekeyBtn, lockdownBtn, printModeBtn;
    private int selected = -1;
    private int armedId = -1;
    private long armedUntil;
    private int ticks;
    private int headChipX = Integer.MAX_VALUE;
    private Component headState = Component.empty();

    public CcScreenC(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    public static boolean handles(Object machine) {
        return machine instanceof CcSatGlobeBlockEntity || machine instanceof CcEwRackBlockEntity || machine instanceof CcGpsRackBlockEntity
                || machine instanceof CcAlarmBoardBlockEntity || machine instanceof CcMapPrinterBlockEntity || machine instanceof CcCommsArrayBlockEntity;
    }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return panelHeight() > 0 ? PW : 0; }

    private int panelHeight() {
        Object m = menu.machine();
        if (m instanceof CcSatGlobeBlockEntity) return 228;
        if (m instanceof CcEwRackBlockEntity) return 84;
        if (m instanceof CcGpsRackBlockEntity) return 80;
        if (m instanceof CcAlarmBoardBlockEntity) return 236;
        if (m instanceof CcMapPrinterBlockEntity) return 76;
        if (m instanceof CcCommsArrayBlockEntity) return 72;
        return 0;
    }

    private BlockEntity be() { return menu.machine() instanceof BlockEntity b ? b : null; }

    private UUID me() { return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null; }

    private void send(int action, int a, int b) {
        BlockEntity be = be();
        if (be != null) PacketDistributor.sendToServer(new CcActionPayload(be.getBlockPos(), action, a, b));
    }

    @Override
    protected void init() {
        super.init();
        BlockEntity be = be();
        if (be == null) return;
        int x = px(), y = topPos;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, be.getBlockPos())));
        if (be instanceof CcSatGlobeBlockEntity g) {
            xBox = new EditBox(font, x + 40, y + 168, 34, 14, Component.literal("x"));
            zBox = new EditBox(font, x + 78, y + 168, 34, 14, Component.literal("z"));
            for (EditBox b : new EditBox[] { xBox, zBox }) { b.setFilter(s -> s.matches("-?\\d{0,8}")); b.setMaxLength(9); addRenderableWidget(b); }
            if (minecraft.player != null) { xBox.setValue(Integer.toString(minecraft.player.getBlockX())); zBox.setValue(Integer.toString(minecraft.player.getBlockZ())); }
            addRenderableWidget(new WmdUi.KitButton(x + 115, y + 168, 20, 14, Component.translatable("gui.wmd.cc.globe.set"), b -> setTarget()));
            deorbitBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 187, PW - 16, 16, Component.translatable("gui.wmd.cc.globe.deorbit", "?"), b -> deorbit(), true));
            rekeyBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 207, PW - 16, 16, Component.translatable("gui.wmd.cc.globe.rekey", "?"), b -> rekey()));
        } else if (be instanceof CcEwRackBlockEntity r) {
            ewBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 62, PW - 16, 16, Component.empty(), b -> send(CcActionPayload.EW, 0, 0)));
        } else if (be instanceof CcAlarmBoardBlockEntity) {
            clearBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 196, PW - 16, 14, Component.translatable("gui.wmd.cc.alarm.clear"), b -> send(CcActionPayload.CLEAR, 0, 0)));
            lockdownBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 216, PW - 16, 14, Component.empty(), b -> {
                CcBuildingBlockEntity bb = ((CcAlarmBoardBlockEntity) be).building();
                send(CcActionPayload.LOCKDOWN, bb != null && bb.lockdownToggle() ? 0 : 1, 0);
            }));
            send(CcActionPayload.LOG, 0, 0);
        } else if (be instanceof CcMapPrinterBlockEntity pr) {
            printModeBtn = addRenderableWidget(new WmdUi.KitButton(x + 8, y + 30, PW - 16, 16, Component.empty(), b -> send(CcActionPayload.PRINT_MODE, (pr.mode() + 1) % 3, 0)));
        }
    }

    @Override
    public void removed() {
        super.removed();
        CcAlarmLogView.clear();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (menu.machine() instanceof CcAlarmBoardBlockEntity && ticks % 40 == 0) send(CcActionPayload.LOG, 0, 0);
    }

    private List<OrbitMath.Sat> mySats(CcSatGlobeBlockEntity g) {
        List<OrbitMath.Sat> l = new ArrayList<>();
        if (g.getOwner() != null) for (OrbitMath.Sat s : OrbitView.sats()) if (g.getOwner().equals(s.owner())) l.add(s);
        return l;
    }

    private OrbitMath.Sat selectedSat(List<OrbitMath.Sat> l) {
        for (OrbitMath.Sat s : l) if (s.id() == selected) return s;
        if (!l.isEmpty()) { selected = l.get(0).id(); return l.get(0); }
        return null;
    }

    private void setTarget() {
        try {
            send(CcActionPayload.TARGET, Integer.parseInt(xBox.getValue()), Integer.parseInt(zBox.getValue()));
        } catch (NumberFormatException ignored) {}
    }

    private void deorbit() {
        if (!(menu.machine() instanceof CcSatGlobeBlockEntity g)) return;
        OrbitMath.Sat s = selectedSat(mySats(g));
        if (s == null || !s.owner().equals(me())) return;
        long now = System.currentTimeMillis();
        if (armedId == s.id() && now < armedUntil) {
            send(CcActionPayload.DEORBIT, s.id(), 0);
            armedId = -1;
        } else {
            armedId = s.id();
            armedUntil = now + 3000L;
        }
    }

    private void rekey() {
        if (!(menu.machine() instanceof CcSatGlobeBlockEntity g)) return;
        OrbitMath.Sat s = selectedSat(mySats(g));
        if (s != null && s.owner().equals(me())) send(CcActionPayload.REKEY, s.id(), 0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.machine() instanceof CcSatGlobeBlockEntity g) {
            List<OrbitMath.Sat> l = mySats(g);
            int x = px();
            for (int i = 0; i < Math.min(3, l.size()); i++) {
                int ry = topPos + ROW_Y + i * ROW_H;
                if (mx >= x + 4 && mx < x + PW - 4 && my >= ry - 2 && my < ry + ROW_H - 2) {
                    selected = l.get(i).id();
                    armedId = -1;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key != 256) {
            for (EditBox b : new EditBox[] { xBox, zBox }) if (b != null && b.isFocused()) { b.keyPressed(key, scan, mods); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        super.render(g, mouseX, mouseY, partialTick);

        if (panelHeight() > 0 && headChipX == px() + PW - 17 && WmdUi.over(mouseX, mouseY, headChipX, topPos + 6, 11, 11)) g.renderTooltip(font, headState, mouseX, mouseY);
    }

    private void updateButtons() {
        Object m = menu.machine();
        if (m instanceof CcSatGlobeBlockEntity gl && deorbitBtn != null) {
            OrbitMath.Sat s = selectedSat(mySats(gl));
            boolean mine = s != null && s.owner().equals(me());
            deorbitBtn.visible = mine;
            deorbitBtn.active = mine && gl.isOnline();
            boolean armed = s != null && armedId == s.id() && System.currentTimeMillis() < armedUntil;
            deorbitBtn.setMessage(s == null ? Component.empty() : armed
                    ? Component.translatable("gui.wmd.cc.globe.confirm", s.id()).withStyle(net.minecraft.ChatFormatting.YELLOW)
                    : Component.translatable("gui.wmd.cc.globe.deorbit", s.id()));
            if (rekeyBtn != null) {
                int wait = s == null ? 0 : gl.rekeyWait(s.id());
                rekeyBtn.visible = mine;
                rekeyBtn.active = mine && gl.isOnline() && wait <= 0;
                rekeyBtn.setMessage(s == null ? Component.empty() : wait > 0 ? Component.translatable("gui.wmd.cc.globe.rekey_wait", OrbitMath.mmss(wait * 1000L))
                        : gl.hacked(s.id()) ? Component.translatable("gui.wmd.cc.globe.rekey", s.id()).withStyle(net.minecraft.ChatFormatting.RED)
                        : Component.translatable("gui.wmd.cc.globe.rekey", s.id()));
            }
        }
        if (m instanceof CcEwRackBlockEntity r && ewBtn != null) {
            ewBtn.setMessage(Component.translatable(r.isEnabled() ? "gui.wmd.cc.ew.turn_off" : "gui.wmd.cc.ew.turn_on"));
            ewBtn.active = r.getOwner() != null && r.getOwner().equals(me());
        }
        if (m instanceof CcAlarmBoardBlockEntity b && clearBtn != null) clearBtn.active = b.getOwner() != null && b.getOwner().equals(me());
        if (m instanceof CcAlarmBoardBlockEntity b && lockdownBtn != null) {
            CcBuildingBlockEntity bb = b.building();
            boolean on = bb != null && bb.lockdownToggle();
            lockdownBtn.setMessage(Component.translatable(on ? "gui.wmd.cc.alarm.lockdown_on" : "gui.wmd.cc.alarm.lockdown_off"));
            lockdownBtn.active = bb != null && b.getOwner() != null && b.getOwner().equals(me());
        }
        if (m instanceof CcMapPrinterBlockEntity pr && printModeBtn != null) {
            printModeBtn.setMessage(Component.translatable("gui.wmd.cc.printer.mode." + pr.mode()));
            printModeBtn.active = pr.getOwner() != null && pr.getOwner().equals(me());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        int ph = panelHeight();
        if (ph <= 0 || !(menu.machine() instanceof CcModuleBlockEntity mod)) return;
        int x = px(), y = topPos, cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, ph);
        drawSelectedTab(g);
        headState = Component.translatable(mod.isOnline() ? "gui.wmd.cc.online" : "gui.wmd.cc.offline");
        headChipX = WmdUi.header(g, font, x, y, PW, new ItemStack(((BlockEntity) mod).getBlockState().getBlock().asItem()), Component.translatable(mod.kind().langKey()),
                headState, mod.isOnline() ? WmdUi.K_GOOD : WmdUi.K_BAD, !mod.isOnline());
        if (mod instanceof CcSatGlobeBlockEntity gl) globe(g, gl, x, y, cx, partialTick, mouseX, mouseY);
        else if (mod instanceof CcEwRackBlockEntity r) ew(g, r, y, cx);
        else if (mod instanceof CcGpsRackBlockEntity r) gps(g, r, y, cx);
        else if (mod instanceof CcAlarmBoardBlockEntity b) alarms(g, b, x, y, cx);
        else if (mod instanceof CcCommsArrayBlockEntity c) comms(g, c, y, cx);
        else if (mod instanceof CcMapPrinterBlockEntity pr) fit(g, Component.translatable("gui.wmd.cc.printer.hint." + pr.mode()), cx, y + 54, GREY);
    }

    private void globe(GuiGraphics g, CcSatGlobeBlockEntity gl, int x, int y, int cx, float pt, int mouseX, int mouseY) {
        fit(g, Component.translatable(gl.stationOk() ? "gui.wmd.cc.globe.station_ok" : "gui.wmd.cc.globe.station_none"), cx, y + 28, gl.stationOk() ? GOOD : BAD);
        List<OrbitMath.Sat> l = mySats(gl);
        OrbitMath.Sat sel = selectedSat(l);
        long now = OrbitView.now();
        double sx = gl.getBlockPos().getX() + 0.5, sz = gl.getBlockPos().getZ() + 0.5;
        if (l.isEmpty()) fit(g, Component.translatable("gui.wmd.cc.globe.none"), cx, y + ROW_Y + 8, GREY);
        for (int i = 0; i < Math.min(3, l.size()); i++) {
            OrbitMath.Sat s = l.get(i);
            int ry = y + ROW_Y + i * ROW_H;
            if (s == sel) {
                g.fill(x + 4, ry - 2, x + PW - 4, ry + ROW_H - 3, SEL_FILL);
                g.fill(x + 4, ry - 2, x + 6, ry + ROW_H - 3, WmdUi.BLUE);
            } else if (WmdUi.over(mouseX, mouseY, x + 4, ry - 2, PW - 8, ROW_H - 1)) g.fill(x + 4, ry - 2, x + PW - 4, ry + ROW_H - 3, HOVER_FILL);
            g.fill(x + 5, ry - 3, x + PW - 5, ry - 2, WmdUi.DIVIDER);
            g.fill(x + 6, ry, x + 14, ry + 8, 0xFF0A0D11);
            g.fill(x + 7, ry + 1, x + 13, ry + 7, 0xFF000000 | colour(s.type()));
            long left = Math.max(0L, s.endMs() - now), h = left / 3_600_000L;
            Component life = Component.translatable("gui.wmd.ground_station.life", h / 24, h % 24);
            drawLeft(g, Component.translatable("gui.wmd.ground_station.row", OrbitData.typeName(s.type()), s.id(), life), x + 16, ry, PW - 22, TEXT);
            long over = OrbitMath.overheadLeft(s, sx, sz, now);
            boolean ctl = gl.satsControlled();
            Component second = over > 0 ? Component.translatable("gui.wmd.ground_station.overhead", OrbitMath.mmss(over))
                    : Component.translatable("gui.wmd.ground_station.next", OrbitMath.mmss(OrbitMath.nextPass(s, sx, sz, now)));
            Component state = Component.translatable(ctl ? "gui.wmd.cc.globe.controlled" : "gui.wmd.cc.globe.no_control");
            if (gl.hacked(s.id())) {
                boolean blink = (System.currentTimeMillis() / 400L) % 2L == 0L;
                drawLeft(g, Component.translatable("gui.wmd.cc.globe.being_hacked"), x + 16, ry + 10, PW - 22, blink ? BAD : 0xFFE06040);
                continue;
            }
            drawLeft(g, Component.empty().append(second).append(Component.literal("  ")).append(state), x + 16, ry + 10, PW - 22, ctl ? (over > 0 ? GOOD : GREY) : BAD);
        }

        int vx0 = x + 8, vx1 = x + PW - 8, vy0 = y + VIEW_Y, vy1 = vy0 + VIEW_H;
        WmdUi.well(g, vx0, vy0, vx1 - vx0, vy1 - vy0);
        g.fill(vx0, vy0, vx1, vy1, 0xFF0A1420);
        if (sel != null && SatelliteMesh.installed()) {
            double tt = (minecraft.level != null ? minecraft.level.getGameTime() : 0L) + pt;
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate((vx0 + vx1) / 2.0F, vy1 - 8.0F, 120.0F);
            float s = 20.0F;
            ps.scale(s, -s, s);
            ps.mulPose(Axis.XP.rotationDegrees(-15.0F));
            ps.mulPose(Axis.YP.rotationDegrees((float) (tt * 1.5) % 360.0F));
            Lighting.setupForEntityInInventory();
            SatelliteMesh.drawFired(sel.type(), 1.0F, SatelliteMesh.blink(tt, sel.id()), gl.spent(sel.id()) ? 1 : 0, ps, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            g.flush();
            Lighting.setupFor3DItems();
            ps.popPose();
        } else {
            fit(g, Component.translatable(sel == null ? "gui.wmd.cc.globe.no_view" : "gui.wmd.cc.globe.no_mesh"), cx, (vy0 + vy1) / 2 - 4, GREY);
        }
        g.drawString(font, Component.translatable("gui.wmd.cc.globe.recon"), x + 7, y + 171, TEXT, false);
    }

    static int colour(String type) {
        return switch (type) { case "recon" -> 0xF0F0F0; case "relay" -> 0xFFB030; case "interceptor" -> 0xFF3030; case "designator" -> 0xFF5050; default -> 0x40E0FF; };
    }

    private void ew(GuiGraphics g, CcEwRackBlockEntity r, int y, int cx) {
        Component st = !r.isEnabled() ? Component.translatable("gui.wmd.cc.ew.off")
                : r.isJamming() ? Component.translatable("gui.wmd.cc.ew.jamming") : Component.translatable("gui.wmd.cc.ew.standby");
        fit(g, st, cx, y + 30, !r.isEnabled() ? GREY : r.isJamming() ? GOOD : BLUE);
        fit(g, Component.translatable("gui.wmd.cc.ew.radius", String.format("%,d", Balance.CC_JAM_RADIUS)), cx, y + 42, GREY);
    }

    private void gps(GuiGraphics g, CcGpsRackBlockEntity r, int y, int cx) {
        fit(g, Component.translatable(r.live() ? "gui.wmd.cc.gps.lock" : "gui.wmd.cc.gps.no_lock"), cx, y + 30, r.live() ? GOOD : BAD);
        fit(g, Component.translatable("gui.wmd.cc.gps.users", Math.max(0, r.users() - 1)), cx, y + 42, TEXT);
        fit(g, Component.translatable("gui.wmd.cc.gps.hint"), cx, y + 56, GREY);
    }

    private void alarms(GuiGraphics g, CcAlarmBoardBlockEntity b, int x, int y, int cx) {
        List<CcAlarms.Event> ev = CcAlarmLogView.eventsFor(b.getBlockPos());
        if (ev.isEmpty()) { fit(g, Component.translatable("gui.wmd.cc.alarm.none"), cx, y + 36, GREY); return; }
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        int ly = y + 32;
        for (int i = ev.size() - 1; i >= 0 && ly < y + 192; i--) {
            CcAlarms.Event e = ev.get(i);
            g.fill(x + 5, ly - 2, x + PW - 5, ly - 1, WmdUi.DIVIDER);
            String time = hm.format(java.time.Instant.ofEpochMilli(e.timeMs()).atZone(java.time.ZoneId.systemDefault()));
            Component who = e.actor().isEmpty() ? Component.translatable("message.wmd.cc.alarm.someone") : Component.literal(e.actor());
            fit(g, Component.translatable("gui.wmd.cc.alarm.row1", time, who, Component.translatable("gui.wmd.cc.alarm.kind." + e.kindName())), cx, ly, e.kind() == CcAlarms.EXPLOSION || e.kind() == CcAlarms.DESTROYED ? BAD : TEXT);
            fit(g, Component.translatable("gui.wmd.cc.alarm.row2", Component.translatable(e.machine()), e.x(), e.y(), e.z()), cx, ly + 9, GREY);
            ly += 20;
        }
    }

    private void comms(GuiGraphics g, CcCommsArrayBlockEntity c, int y, int cx) {
        if (!c.isOnline()) { fit(g, Component.translatable("gui.wmd.cc.comms.roof"), cx, y + 30, BAD); return; }
        fit(g, Component.translatable(c.relayOk() ? "gui.wmd.cc.comms.relay" : "gui.wmd.cc.comms.no_relay"), cx, y + 30, c.relayOk() ? GOOD : GREY);
        fit(g, Component.translatable(c.relayOk() ? "gui.wmd.cc.comms.worldwide" : "gui.wmd.cc.comms.local", String.format("%,d", 2000)), cx, y + 42, TEXT);
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
