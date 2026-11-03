package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SecurityMonitorScreen extends Screen {

    private static final String P = "gui.wmd.cctv.";
    private CctvPayloads.Open data;
    private int page, ticks, nextTile, lastTile = -1;
    private final long[] askedAt = new long[4];

    public SecurityMonitorScreen(CctvPayloads.Open data) {
        super(Component.translatable(P + "title"));
        this.data = data;
        if (data.alert() >= 0) page = data.alert() / 4;
    }

    @Override public boolean isPauseScreen() { return false; }

    void refresh(CctvPayloads.Open d) {
        data = d;
        page = Math.min(page, Math.max(0, pages() - 1));
    }

    private int pages() { return Math.max(1, (data.cams().size() + 3) / 4); }
    private int W() { return Math.min(width - 16, 460); }
    private int H() { return Math.min(height - 16, 300); }
    private int x0() { return (width - W()) / 2; }
    private int y0() { return (height - H()) / 2; }
    private int tw() { return (W() - 24) / 2; }
    private int th() { return (H() - 26 - 30 - 6) / 2; }
    private int tx(int i) { return x0() + 8 + (i % 2) * (tw() + 8); }
    private int ty(int i) { return y0() + 26 + (i / 2) * (th() + 6); }

    private CctvPayloads.CamInfo camOf(int tile) {
        int i = page * 4 + tile;
        return i < data.cams().size() ? data.cams().get(i) : null;
    }

    @Override
    protected void init() {
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.0F, 0.6F));
        int by = y0() + H() - 24;
        addRenderableWidget(new WmdUi.KitButton(x0() + 8, by, 40, 16, Component.literal("<"), b -> turn(-1)));
        addRenderableWidget(new WmdUi.KitButton(x0() + W() - 48, by, 40, 16, Component.literal(">"), b -> turn(1)));
        resetTiles();
    }

    private void turn(int d) {
        int np = Math.floorMod(page + d, pages());
        if (np == page) return;
        page = np;
        if (minecraft != null) minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(WmdSounds.CCTV_SWITCH.get(), 1.0F, 0.6F));
        resetTiles();
    }

    private void resetTiles() {
        for (var m : AreaViewClient.CCTV) m.close();
        java.util.Arrays.fill(askedAt, -1L);
        nextTile = 0;
        lastTile = -1;
    }

    @Override
    public void removed() { if (!ClientCctv.active()) for (var m : AreaViewClient.CCTV) m.close(); }

    private static int[] centre(CctvPayloads.CamInfo c) {
        var lens = CctvPayloads.lens(new CctvNet.Cam(BlockPos.of(c.pos()), c.yaw(), c.covert()));
        var f = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, c.yaw()).scale(Balance.CCTV_VIEW / 2.0 - 4.0);
        return new int[] { (int) Math.floor(lens.x + f.x), (int) Math.floor(lens.z + f.z) };
    }

    @Override
    public void tick() {
        ticks++;

        boolean waiting = lastTile >= 0 && !AreaViewClient.CCTV[lastTile].answered() && ticks - askedAt[lastTile] < 200;
        if (ticks % 10 == 0 && !waiting) {
            for (int k = 0; k < 4; k++) {
                int i = (nextTile + k) % 4;
                CctvPayloads.CamInfo c = camOf(i);
                if (c == null || c.state() == CcSecurityMonitorBlockEntity.LOST) continue;
                var m = AreaViewClient.CCTV[i];
                if (askedAt[i] >= 0 && (m.ready() || !m.refused().isEmpty() ? ticks - askedAt[i] < 1200 : ticks - askedAt[i] < 100)) continue;
                int[] ctr = centre(c);
                m.request(AreaView.MODE_CCTV, ctr[0], ctr[1], Balance.CCTV_VIEW, false);
                askedAt[i] = ticks;
                lastTile = i;
                nextTile = (i + 1) % 4;
                break;
            }
        }

        if (ticks % 10 == 5) {
            List<Long> l = new ArrayList<>();
            for (int i = 0; i < 4; i++) { CctvPayloads.CamInfo c = camOf(i); if (c != null) l.add(c.pos()); }
            if (!l.isEmpty()) PacketDistributor.sendToServer(new CctvPayloads.FeedRequest(data.monitor(), l));
        }
        for (int i = 0; i < 4; i++) {
            CctvPayloads.CamInfo c = camOf(i);
            CctvPayloads.FeedData f = c == null ? null : ClientCctv.feed(c.pos());
            if (f == null) continue;
            List<AreaMarkersPayload.Marker> ms = new ArrayList<>();
            for (CctvPayloads.Mark mk : f.marks()) ms.add(new AreaMarkersPayload.Marker(mk.kind(), mk.x(), mk.y(), mk.z(), 0.5F));
            AreaViewClient.CCTV[i].setMarkers(ms);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = x0(), y = y0(), W = W(), H = H();
        WmdUi.panel(g, x, y, W, H);
        boolean alert = data.alert() >= 0;
        WmdUi.header(g, font, x, y, W, new ItemStack(Wmd.CC_SECURITY_MONITOR_ITEM.get()), title,
                Component.translatable(!data.online() ? P + "offline" : alert ? P + "alert" : P + "watching", data.cams().size()),
                !data.online() ? WmdUi.K_BAD : alert ? WmdUi.K_BAD : WmdUi.K_GOOD, alert);
        long t = Minecraft.getInstance().level == null ? ticks : Minecraft.getInstance().level.getGameTime();
        for (int i = 0; i < 4; i++) tile(g, i, mx, my, t);
        Component pg = Component.translatable(P + "page", page + 1, pages(), data.frequency());
        g.drawCenteredString(font, pg, x + W / 2, y + H - 20, WmdUi.TEXT_MUTED);
        if (data.cams().isEmpty()) {
            int yy = y + H / 2 - 10;
            for (var line : font.split(Component.translatable(P + "none", data.frequency(), Balance.CCTV_DIRECT), W - 40)) {
                g.drawString(font, line, x + W / 2 - font.width(line) / 2, yy, WmdUi.WARN, false);
                yy += 10;
            }
        }
    }

    private void tile(GuiGraphics g, int i, int mx, int my, long t) {
        int x = tx(i), y = ty(i), w = tw(), h = th();
        WmdUi.well(g, x, y, w, h);
        CctvPayloads.CamInfo c = camOf(i);
        if (c == null) return;
        int idx = page * 4 + i;
        CctvPayloads.FeedData f = ClientCctv.feed(c.pos());
        int state = f != null ? f.state() : c.state();
        var m = AreaViewClient.CCTV[i];
        if (state == CcSecurityMonitorBlockEntity.LIVE && m.ready())
            m.draw(g, x, y, w, h, c.yaw() - 180.0F, 38.0F, 1.15F, Integer.MIN_VALUE, 0, Integer.MIN_VALUE, 0);
        else if (state == CcSecurityMonitorBlockEntity.LIVE) g.drawCenteredString(font, Component.translatable(m.refused().isEmpty() ? P + "loading" : m.refused()), x + w / 2, y + h / 2 - 4, WmdUi.TEXT_MUTED);
        if (state == CcSecurityMonitorBlockEntity.LIVE && f != null && f.dark()) g.fill(x, y, x + w, y + h, 0x401AFF40);
        if (state != CcSecurityMonitorBlockEntity.LIVE) {
            long seed = t / 2 + i * 97L;
            for (int k = 0; k < 40; k++) {
                long hsh = (seed * 31 + k) * 0x9E3779B97F4A7C15L;
                int sx = x + (int) (((hsh >>> 8) & 1023) * (w - 6) / 1024), sy = y + (int) (((hsh >>> 20) & 1023) * (h - 2) / 1024);
                g.fill(sx, sy, sx + 6, sy + 2, 0x60FFFFFF);
            }
            g.drawCenteredString(font, Component.translatable(state == CcSecurityMonitorBlockEntity.STATIC ? "hud.wmd.cctv.static" : "hud.wmd.cctv.lost"), x + w / 2, y + h / 2 - 4, WmdUi.BAD);
        }

        BlockPos p = BlockPos.of(c.pos());
        g.fill(x, y, x + w, y + 11, 0x90000000);
        WmdUi.drawFit(g, font, Component.translatable(c.covert() ? "hud.wmd.cctv.cam_covert" : "hud.wmd.cctv.cam", idx + 1).copy().append("  " + p.getX() + " " + p.getZ()),
                x + 3, y + 2, w - 40, WmdUi.TEXT);
        if (state == CcSecurityMonitorBlockEntity.LIVE) {
            if ((t / 10) % 2 == 0) g.fill(x + w - 30, y + 3, x + w - 26, y + 7, 0xFFFF3030);
            g.drawString(font, "REC", x + w - 24, y + 2, 0xFFFF7070, false);
            if (f != null) g.drawString(font, Component.translatable("hud.wmd.cctv.seen", f.marks().size()), x + 3, y + h - 10, WmdUi.TEXT_MUTED, true);
        }
        boolean over = WmdUi.over(mx, my, x, y, w, h);
        int frame = idx == data.alert() ? 0xFFFF4040 : over ? 0xFF6FA8FF : 0;
        if (frame != 0) {
            g.fill(x - 1, y - 1, x + w + 1, y, frame); g.fill(x - 1, y + h, x + w + 1, y + h + 1, frame);
            g.fill(x - 1, y, x, y + h, frame); g.fill(x + w, y, x + w + 1, y + h, frame);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int i = 0; i < 4; i++) {
            CctvPayloads.CamInfo c = camOf(i);
            if (c != null && WmdUi.over(mx, my, tx(i), ty(i), tw(), th())) { WmdUi.click(); ClientCctv.enter(c, page * 4 + i); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }
}
