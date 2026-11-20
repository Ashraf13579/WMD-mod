package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class HoloControlScreen extends Screen {

    private static final int W = 196, H = 176;
    private static final String P = "gui.wmd.holot.";
    private final Screen parent;
    private final BlockPos pos;

    public HoloControlScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.wmd.holot.title"));
        this.parent = parent;
        this.pos = pos;
    }

    private HoloTableBlockEntity table() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof HoloTableBlockEntity t ? t : null;
    }

    private void send(int action, int a) {
        PacketDistributor.sendToServer(new HoloPayloads.Action(pos, action, a));
    }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = (height - H) / 2, cx = left + W / 2;
        addRenderableWidget(new WmdUi.KitButton(cx - 52, top + 68, 48, 18, Component.translatable(P + "zoom_in"), b -> send(HoloTableBlockEntity.A_ZOOM_IN, 0)));
        addRenderableWidget(new WmdUi.KitButton(cx + 4, top + 68, 48, 18, Component.translatable(P + "zoom_out"), b -> send(HoloTableBlockEntity.A_ZOOM_OUT, 0)));
        addRenderableWidget(new WmdUi.KitButton(cx - 24, top + 90, 48, 18, Component.translatable(P + "pan_n"), b -> send(HoloTableBlockEntity.A_PAN, 0)));
        addRenderableWidget(new WmdUi.KitButton(cx - 82, top + 110, 48, 18, Component.translatable(P + "pan_w"), b -> send(HoloTableBlockEntity.A_PAN, 3)));
        addRenderableWidget(new WmdUi.KitButton(cx - 28, top + 110, 56, 18, Component.translatable(P + "centre"), b -> send(HoloTableBlockEntity.A_CENTRE, 0)));
        addRenderableWidget(new WmdUi.KitButton(cx + 34, top + 110, 48, 18, Component.translatable(P + "pan_e"), b -> send(HoloTableBlockEntity.A_PAN, 1)));
        addRenderableWidget(new WmdUi.KitButton(cx - 24, top + 130, 48, 18, Component.translatable(P + "pan_s"), b -> send(HoloTableBlockEntity.A_PAN, 2)));
        int bw = (W - 24) / 2;
        addRenderableWidget(new WmdUi.KitButton(left + 8, top + H - 26, bw, 18, Component.translatable(P + "clear"), b -> send(HoloTableBlockEntity.A_CLEAR, 0), true));
        addRenderableWidget(new WmdUi.KitButton(left + 16 + bw, top + H - 26, bw, 18, Component.translatable("gui.wmd.security.back"), b -> onClose()));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - H) / 2;
        WmdUi.panel(g, left, top, W, H);
        WmdUi.drawFit(g, font, title, left + 8, top + 6, W - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        WmdUi.divider(g, left + 8, top + 16, W - 16);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        HoloTableBlockEntity t = table();
        if (t == null) { WmdUi.drawFit(g, font, Component.translatable(P + "gone"), left + 12, top + 24, W - 24, WmdUi.BAD); return; }
        int own = 0, other = 0;
        int[] tr = t.tracks();
        for (int i = 0; i + 4 < tr.length; i += 5) { if (tr[i + 3] <= HoloTableBlockEntity.K_OWN_SHIP) own++; else other++; }
        Component span = t.view() == CcMapTableBlockEntity.VIEW_RECON ? Component.translatable(P + "span_recon")
                : t.zoom() == 0 ? Component.translatable(P + "span_local", t.span()) : Component.translatable(P + "span", t.span(), t.span() / 2);
        WmdUi.drawFit(g, font, span, left + 12, top + 24, W - 24, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "centre_at", (int) Math.floor(t.viewX()), (int) Math.floor(t.viewZ())), left + 12, top + 35, W - 24, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "tracks", own, other), left + 12, top + 46, W - 24, WmdUi.TEXT_MUTED);
        Component terrain = Component.translatable(P + (HoloTerrainView.disabled() ? "terrain_off" : "terrain_on"));
        WmdUi.drawFit(g, font, terrain, left + 12, top + 57, W - 24, HoloTerrainView.disabled() ? WmdUi.WARN : WmdUi.TEXT_MUTED);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
