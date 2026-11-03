package com.ashraf.wmd;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

public class ReconMapScreen extends Screen {

    static final int W = 224, H = 238, MAP = 128;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT;
    private static final int NO_DATA = 0xFF2A2A2A;

    static final int[] ICON_COL = { 0xFFE03030, 0xFFFF9020, 0xFFFFE040, 0xFF40D0FF, 0xFFB0B0B0, 0xFFC890FF };
    static final int DOT_COL = 0xFFFFFFFF;
    static final String[] ICON_KEY = { "launch", "defence", "radar", "station", "other", "airship" };
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dynamic/recon_map");

    private final Screen parent;
    private final BlockPos pos;
    private EditBox xBox, zBox;
    private int ticks, shownVersion = -1, lastMapAsk = -1000;
    private boolean filled, texReady;
    private DynamicTexture texture;

    private String hover;

    private boolean picked;
    private int pickX, pickZ;
    private Button designateButton;

    static final int BW = 150, BH = 130, THUMB = 64;
    private boolean showBda;

    public ReconMapScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.wmd.recon.title"));
        this.parent = parent;
        this.pos = pos;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private int mapX() { return left() + (W - MAP) / 2; }
    private int mapY() { return top() + 30; }

    @Override
    protected void init() {
        int l = left(), t = top(), y = t + 196;
        String ox = xBox != null ? xBox.getValue() : "", oz = zBox != null ? zBox.getValue() : "";
        xBox = new EditBox(font, l + 18, y + 1, 46, 14, Component.literal("x"));
        zBox = new EditBox(font, l + 78, y + 1, 46, 14, Component.literal("z"));
        for (EditBox b : new EditBox[] { xBox, zBox }) { b.setFilter(s -> s.matches("-?\\d{0,8}")); b.setMaxLength(9); addRenderableWidget(b); }
        xBox.setValue(ox);
        zBox.setValue(oz);
        addRenderableWidget(new WmdUi.KitButton(l + 130, y, 40, 16, Component.translatable("gui.wmd.recon.set"), b -> setTarget()));
        addRenderableWidget(new WmdUi.KitButton(l + 174, y, 42, 16, Component.translatable("gui.wmd.security.back"), b -> onClose()));
        designateButton = addRenderableWidget(new WmdUi.KitButton(l + 146, t + 216, 70, 16, Component.translatable("gui.wmd.recon.designate"), b -> designate()));
        designateButton.active = picked;
        addRenderableWidget(new WmdUi.KitButton(l + W - 60, t + 4, 54, 12, Component.translatable("gui.wmd.recon.bda"), b -> showBda = !showBda));
        if (ticks == 0) {
            ReconView.clear();
            ask(ReconPayload.MAP);
            PacketDistributor.sendToServer(new ReconPayload(pos, ReconPayload.BDA, BdaView.key(), 0));
        }
    }

    private void ask(int action) { PacketDistributor.sendToServer(new ReconPayload(pos, action, 0, 0)); }

    private CcBuildingBlockEntity building() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CcModuleBlockEntity m ? m.building() : null;
    }

    private CcTarget designated() { CcBuildingBlockEntity b = building(); return b != null ? b.designated() : null; }

    private void designate() {
        if (!picked) return;
        PacketDistributor.sendToServer(new CcActionPayload(pos, CcActionPayload.DESIGNATE, pickX, pickZ));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ReconMapPayload m = ReconView.map;
        int x0 = mapX(), y0 = mapY();
        if (button == 0 && m != null && m.n() > 0 && mx >= x0 && mx < x0 + MAP && my >= y0 && my < y0 + MAP) {
            double span = m.n() * (double) m.step();
            pickX = m.x0() + (int) Math.floor((mx - x0) / MAP * span);
            pickZ = m.z0() + (int) Math.floor((my - y0) / MAP * span);
            int[] ic = m.icons();
            double best = 3.5;
            for (int i = 0; i + 2 < ic.length; i += 3) {
                double ix = x0 + (ic[i] - m.x0()) / span * MAP, iz = y0 + (ic[i + 1] - m.z0()) / span * MAP, d = Math.hypot(ix - mx, iz - my);
                if (d <= best) { best = d; pickX = ic[i]; pickZ = ic[i + 1]; }
            }
            picked = true;
            if (designateButton != null) designateButton.active = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void setTarget() {
        try {
            int x = Integer.parseInt(xBox.getValue().trim()), z = Integer.parseInt(zBox.getValue().trim());
            PacketDistributor.sendToServer(new ReconPayload(pos, ReconPayload.TARGET, x, z));
        } catch (NumberFormatException ignored) {}
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (ticks % Math.max(10, Balance.RECON_DOT_REFRESH_TICKS) == 0) ask(ReconPayload.DOTS);
        if (showBda && ticks % 40 == 0) PacketDistributor.sendToServer(new ReconPayload(pos, ReconPayload.BDA, BdaView.key(), 0));
        if (ReconView.stale() && ticks - lastMapAsk >= 40) { lastMapAsk = ticks; ask(ReconPayload.MAP); }
        ReconMapPayload p = ReconView.last;
        if (!filled && p != null && p.hasTarget() && !xBox.isFocused() && !zBox.isFocused()) {
            xBox.setValue(Integer.toString(p.tx()));
            zBox.setValue(Integer.toString(p.tz()));
            filled = true;
        }
        if (ReconView.version != shownVersion) rebuildTexture();
    }

    private void rebuildTexture() {
        shownVersion = ReconView.version;
        ReconMapPayload m = ReconView.map;
        releaseTexture();
        if (m == null || m.n() <= 0) return;
        int n = m.n();
        NativeImage img = new NativeImage(n, n, false);
        byte[] col = m.col();
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            int v = col[z * n + x] & 0xFF;
            img.setPixelRGBA(x, z, v < 4 ? NO_DATA : MapColor.getColorFromPackedId(v));
        }
        texture = new DynamicTexture(img);
        minecraft.getTextureManager().register(TEX, texture);
        texReady = true;
    }

    private void releaseTexture() {
        if (texture != null) { minecraft.getTextureManager().release(TEX); texture = null; }
        texReady = false;
    }

    @Override
    public void removed() {
        releaseTexture();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int l = left(), t = top(), cx = l + W / 2;
        WmdUi.panel(g, l, t, W, H);
        fit(g, title, cx, t + 6, TEXT);
        WmdUi.divider(g, l + 6, t + 16, W - 12);
        WmdHelp.button(g, l + W - 60 - WmdHelp.ROOM, t + 4);
        ReconMapPayload p = ReconView.last;
        long since = System.currentTimeMillis() - ReconView.receivedMs;

        Component status;
        int sc = GREY;
        if (p == null) status = Component.translatable("gui.wmd.recon.loading");
        else if (p.status() == Recon.STATUS_NONE) { status = Component.translatable("gui.wmd.recon.none"); sc = BAD; }
        else if (p.status() == Recon.STATUS_NO_CONTROL) { status = Component.translatable("gui.wmd.recon.no_control"); sc = BAD; }
        else if (!p.hasTarget()) { status = Component.translatable("gui.wmd.recon.no_target"); sc = BLUE; }
        else if (p.status() == Recon.STATUS_STATION) { status = Component.translatable("gui.wmd.recon.station"); sc = GOOD; }
        else if (p.status() == Recon.STATUS_DRONE) { status = Component.translatable("gui.wmd.recon.drone", OrbitMath.mmss(p.passMs() - since)); sc = GOOD; }
        else if (p.status() == Recon.STATUS_OVERHEAD) { status = Component.translatable("gui.wmd.recon.overhead", p.satId(), OrbitMath.mmss(p.passMs() - since)); sc = GOOD; }
        else status = Component.translatable("gui.wmd.recon.next", p.satId(), OrbitMath.mmss(p.passMs() - since));
        fit(g, status, cx, t + 18, sc);

        int x0 = mapX(), y0 = mapY();
        WmdUi.well(g, x0, y0, MAP, MAP);
        ReconMapPayload m = ReconView.map;
        if (texReady && m != null) g.blit(TEX, x0, y0, 0.0F, 0.0F, MAP, MAP, MAP, MAP);
        else {
            g.fill(x0, y0, x0 + MAP, y0 + MAP, NO_DATA);
            fit(g, Component.translatable("gui.wmd.recon.no_image"), x0 + MAP / 2, y0 + MAP / 2 - 4, GREY);
        }
        hover = null;
        if (m != null && m.n() > 0) {
            double span = m.n() * (double) m.step();

            if (p != null && p.hasTarget()) {
                int tx = x0 + (int) Math.floor((p.tx() - m.x0()) / span * MAP), tz = y0 + (int) Math.floor((p.tz() - m.z0()) / span * MAP);
                if (tx >= x0 && tx < x0 + MAP && tz >= y0 && tz < y0 + MAP) {
                    g.fill(Math.max(x0, tx - 4), tz, Math.min(x0 + MAP, tx + 5), tz + 1, 0xC0FFFFFF);
                    g.fill(tx, Math.max(y0, tz - 4), tx + 1, Math.min(y0 + MAP, tz + 5), 0xC0FFFFFF);
                }
            }

            CcTarget des = designated();
            if (des != null) {
                int dx = x0 + (int) Math.floor((des.x() - m.x0()) / span * MAP), dz = y0 + (int) Math.floor((des.z() - m.z0()) / span * MAP);
                if (dx >= x0 && dx < x0 + MAP && dz >= y0 && dz < y0 + MAP) {
                    for (int a = 0; a < 16; a++) {
                        int rx = dx + (int) Math.round(5 * Math.cos(a * Math.PI / 8)), rz = dz + (int) Math.round(5 * Math.sin(a * Math.PI / 8));
                        g.fill(rx, rz, rx + 1, rz + 1, 0xFFFF2020);
                    }
                    g.fill(dx - 7, dz, dx + 8, dz + 1, 0xFFFF2020);
                    g.fill(dx, dz - 7, dx + 1, dz + 8, 0xFFFF2020);
                    if (Math.abs(mx - dx) <= 4 && Math.abs(my - dz) <= 4) hover = Component.translatable("gui.wmd.cc.des.line", des.x(), des.z(), Component.translatable(des.label())).getString();
                }
            }
            if (picked) {
                int px = x0 + (int) Math.floor((pickX - m.x0()) / span * MAP), pz = y0 + (int) Math.floor((pickZ - m.z0()) / span * MAP);
                if (px >= x0 && px < x0 + MAP && pz >= y0 && pz < y0 + MAP) {
                    g.fill(px - 3, pz - 3, px + 4, pz - 2, 0xFFFFFFFF); g.fill(px - 3, pz + 3, px + 4, pz + 4, 0xFFFFFFFF);
                    g.fill(px - 3, pz - 3, px - 2, pz + 4, 0xFFFFFFFF); g.fill(px + 3, pz - 3, px + 4, pz + 4, 0xFFFFFFFF);
                }
            }
            int[] ic = m.icons();
            for (int i = 0; i + 2 < ic.length; i += 3) {
                int ix = x0 + (int) Math.floor((ic[i] - m.x0()) / span * MAP), iz = y0 + (int) Math.floor((ic[i + 1] - m.z0()) / span * MAP);
                if (ix < x0 || ix >= x0 + MAP || iz < y0 || iz >= y0 + MAP) continue;
                int k = Math.max(0, Math.min(ICON_COL.length - 1, ic[i + 2]));
                if (k == Recon.ICON_AIRSHIP) {
                    g.fill(ix - 4, iz - 2, ix + 5, iz + 3, 0xFF000000);
                    g.fill(ix - 3, iz - 1, ix + 4, iz + 2, ICON_COL[k]);
                    if (Math.abs(mx - ix) <= 4 && Math.abs(my - iz) <= 2) {
                        String[] sl = m.shipLabel(ic[i], ic[i + 1]);
                        hover = sl != null ? Component.translatable("gui.wmd.recon.airship_hover", sl[0], sl[1], ic[i], ic[i + 1]).getString()
                                : Component.translatable("gui.wmd.recon.icon.airship").getString() + "  " + ic[i] + " " + ic[i + 1];
                    }
                    continue;
                }
                g.fill(ix - 2, iz - 2, ix + 3, iz + 3, 0xFF000000);
                g.fill(ix - 1, iz - 1, ix + 2, iz + 2, ICON_COL[k]);
                if (Math.abs(mx - ix) <= 2 && Math.abs(my - iz) <= 2)
                    hover = Component.translatable("gui.wmd.recon.icon." + ICON_KEY[k]).getString() + "  " + ic[i] + " " + ic[i + 1];
            }
            if (p != null && (p.status() == Recon.STATUS_OVERHEAD || p.status() == Recon.STATUS_DRONE || p.status() == Recon.STATUS_STATION)) {
                int[] d = p.dots();
                boolean blink = (System.currentTimeMillis() / 400L) % 2L == 0L;
                for (int i = 0; i + 1 < d.length; i += 2) {
                    int dx = x0 + (int) Math.floor((d[i] - m.x0()) / span * MAP), dz = y0 + (int) Math.floor((d[i + 1] - m.z0()) / span * MAP);
                    if (dx < x0 || dx >= x0 + MAP || dz < y0 || dz >= y0 + MAP) continue;
                    g.fill(dx - 2, dz - 2, dx + 3, dz + 3, 0xFF000000);
                    g.fill(dx - 1, dz - 1, dx + 2, dz + 2, blink ? DOT_COL : 0xFFFF6060);
                    if (Math.abs(mx - dx) <= 2 && Math.abs(my - dz) <= 2) hover = Component.translatable("gui.wmd.recon.icon.player").getString() + "  " + d[i] + " " + d[i + 1];
                }
                int[] fl = p.flights();
                for (int i = 0; i + 2 < fl.length; i += 3) {
                    int fx = x0 + (int) Math.floor((fl[i] - m.x0()) / span * MAP), fz = y0 + (int) Math.floor((fl[i + 1] - m.z0()) / span * MAP);
                    if (fx < x0 || fx >= x0 + MAP || fz < y0 || fz >= y0 + MAP) continue;
                    int c = fl[i + 2] == 1 ? 0xFF50A0FF : 0xFFFF3030;
                    g.fill(fx - 1, fz - 3, fx + 2, fz + 4, 0xFF000000);
                    g.fill(fx - 3, fz - 1, fx + 4, fz + 2, 0xFF000000);
                    g.fill(fx, fz - 2, fx + 1, fz + 3, c);
                    g.fill(fx - 2, fz, fx + 3, fz + 1, c);
                    if (Math.abs(mx - fx) <= 3 && Math.abs(my - fz) <= 3) hover = Component.translatable("gui.wmd.recon.missile").getString() + "  " + fl[i] + " " + fl[i + 1];
                }
            }
            long age = (p != null ? p.snapAgeMs() : 0L) + since;
            if (p != null && p.fill() != -1) fit(g, fillLine(p.fill(), m.loadedPct()), cx, t + 162, BLUE);
            else fit(g, Component.translatable("gui.wmd.recon.age", OrbitMath.mmss(age), m.x0() + m.n() * m.step() / 2, m.z0() + m.n() * m.step() / 2, m.loadedPct()),
                    cx, t + 162, TEXT);
        } else if (p != null && p.fill() != -1) fit(g, fillLine(p.fill(), 0), cx, t + 162, BLUE);
        else fit(g, Component.translatable("gui.wmd.recon.age_none"), cx, t + 162, GREY);

        String[] legKey = { "launch", "defence", "radar", "station", "other", "airship", "player" };
        int[] legCol = { ICON_COL[0], ICON_COL[1], ICON_COL[2], ICON_COL[3], ICON_COL[4], ICON_COL[5], DOT_COL };
        for (int i = 0; i < legKey.length; i++) {
            int lx = i < 4 ? l + 10 + i * 54 : l + 10 + (i - 4) * 70, ly = t + 175 + (i < 4 ? 0 : 10);
            g.fill(lx, ly, lx + 6, ly + 6, 0xFF000000);
            g.fill(lx + 1, ly + 1, lx + 5, ly + 5, legCol[i]);
            g.drawString(font, Component.translatable("gui.wmd.recon.icon." + legKey[i]), lx + 9, ly - 1, TEXT, false);
        }
        g.drawString(font, "X", l + 9, t + 200, TEXT, false);
        g.drawString(font, "Z", l + 69, t + 200, TEXT, false);

        CcTarget d = designated();
        Component row = picked ? Component.translatable("gui.wmd.recon.picked", pickX, pickZ)
                : d != null ? Component.translatable("gui.wmd.cc.des.short", d.x(), d.z())
                : Component.translatable("gui.wmd.recon.pick_hint");
        int rw = font.width(row), maxW = 132;
        if (rw <= maxW) g.drawString(font, row, l + 10, t + 220, picked ? TEXT : d != null ? BAD : GREY, false);
        else {
            float k = maxW / (float) rw;
            g.pose().pushPose();
            g.pose().translate(l + 10, t + 220 + (1.0F - k) * 4.0F, 0.0F);
            g.pose().scale(k, k, 1.0F);
            g.drawString(font, row, 0, 0, picked ? TEXT : d != null ? BAD : GREY, false);
            g.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (showBda) damagePanel(g);
        if (hover != null) g.renderTooltip(font, Component.literal(hover), mx, my);
    }

    private void damagePanel(GuiGraphics g) {
        int l = left(), t = top();
        int x = l + W + 4 + BW <= width ? l + W + 4 : l - BW - 4 >= 0 ? l - BW - 4 : l + (W - BW) / 2;
        int y = t + 30, cx = x + BW / 2;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 200.0F);
        WmdUi.panel(g, x, y, BW, BH);
        BdaPayload p = BdaView.last;
        BdaPayload done = BdaView.done();
        fitIn(g, Component.translatable("gui.wmd.recon.bda.title"), cx, y + 6, BW - 10, TEXT);
        if (!p.has()) { fitIn(g, Component.translatable("gui.wmd.recon.bda.none"), cx, y + 40, BW - 10, GREY); fitIn(g, Component.translatable("gui.wmd.recon.bda.hint"), cx, y + 52, BW - 10, GREY); g.pose().popPose(); return; }
        if (done == null) {
            fitIn(g, Component.translatable("gui.wmd.recon.bda.pending", p.x(), p.z()), cx, y + 40, BW - 10, BLUE);
            fitIn(g, Component.translatable("gui.wmd.recon.bda.pending2", Math.max(1, p.pending())), cx, y + 52, BW - 10, GREY);
            g.pose().popPose();
            return;
        }
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm", java.util.Locale.ENGLISH);
        Component wh = done.warhead().isEmpty() ? Component.literal("?") : Component.translatable("warhead.wmd." + done.warhead());
        fitIn(g, Component.translatable("gui.wmd.recon.bda.line", wh, done.x(), done.z(), hm.format(java.time.Instant.ofEpochMilli(done.impactMs()).atZone(java.time.ZoneId.systemDefault()))),
                cx, y + 17, BW - 10, GREY);
        int bx = x + (BW - 2 * THUMB - 6) / 2, ax = bx + THUMB + 6, ty = y + 28;
        WmdUi.well(g, bx, ty, THUMB, THUMB);
        WmdUi.well(g, ax, ty, THUMB, THUMB);
        if (CcRenderers.ReconWall.bdaTextures()) {
            g.blit(CcRenderers.ReconWall.BDA_BEFORE, bx, ty, 0.0F, 0.0F, THUMB, THUMB, THUMB, THUMB);
            g.blit(CcRenderers.ReconWall.BDA_AFTER, ax, ty, 0.0F, 0.0F, THUMB, THUMB, THUMB, THUMB);
        }
        double span = CcBda.CROP * (double) done.step();
        int[] lost = done.lost();
        for (int i = 0; i + 2 < lost.length; i += 3) {
            int qx = ax + (int) Math.floor((lost[i] - done.cx0()) / span * THUMB), qz = ty + (int) Math.floor((lost[i + 1] - done.cz0()) / span * THUMB);
            if (qx < ax || qx >= ax + THUMB || qz < ty || qz >= ty + THUMB) continue;
            for (int k = -2; k <= 2; k++) { g.fill(qx + k, qz + k, qx + k + 1, qz + k + 1, 0xFFFF2020); g.fill(qx + k, qz - k, qx + k + 1, qz - k + 1, 0xFFFF2020); }
        }
        int ix = (int) Math.floor((done.x() - done.cx0()) / span * THUMB), iz = (int) Math.floor((done.z() - done.cz0()) / span * THUMB);
        for (int ox : new int[] { bx, ax }) {
            g.fill(ox + ix - 3, ty + iz, ox + ix + 4, ty + iz + 1, 0xFFFFFFFF);
            g.fill(ox + ix, ty + iz - 3, ox + ix + 1, ty + iz + 4, 0xFFFFFFFF);
        }
        fitIn(g, Component.translatable("gui.wmd.recon.bda.before"), bx + THUMB / 2, ty + THUMB + 3, THUMB, GREY);
        fitIn(g, Component.translatable("gui.wmd.recon.bda.after"), ax + THUMB / 2, ty + THUMB + 3, THUMB, GREY);
        int n = lost.length / 3;
        fitIn(g, Component.translatable("gui.wmd.recon.bda.destroyed", n, done.beforeIcons().length / 3), cx, y + BH - 22, BW - 10, n > 0 ? BAD : GOOD);
        fitIn(g, Component.translatable("gui.wmd.recon.bda.standing", done.standing()), cx, y + BH - 12, BW - 10, GREY);
        g.pose().popPose();
    }

    private void fitIn(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static Component fillLine(int fill, int seen) {
        return fill == -2 ? Component.translatable("gui.wmd.recon.fill_queued") : Component.translatable("gui.wmd.recon.filling", Math.max(0, fill), seen);
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = W - 12, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { if (xBox.isFocused() || zBox.isFocused()) { setTarget(); return true; } }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
