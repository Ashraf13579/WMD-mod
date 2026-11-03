package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class CrewCapsuleScreen extends Screen {

    private static final int W = 196, H_ORBIT = 150, H_LANDED = 84;
    private final CrewCapsulePayloads.Open info;
    private EditBox xBox, zBox;

    public CrewCapsuleScreen(CrewCapsulePayloads.Open info) {
        super(Component.translatable("gui.wmd.capsule.title"));
        this.info = info;
    }

    private int h() { return info.landed() ? H_LANDED : H_ORBIT + (info.docked() ? 22 : 0); }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = (height - h()) / 2, bw = W - 16;
        if (info.landed()) {
            addRenderableWidget(new WmdUi.KitButton(left + 8, top + 36, bw, 18, Component.translatable("gui.wmd.capsule.recover"), b -> send(CrewCapsulePayloads.RECOVER, 0, 0)));
        } else {
            addRenderableWidget(new WmdUi.KitButton(left + 8, top + 36, bw, 18, Component.translatable("gui.wmd.capsule.seat"), b -> send(CrewCapsulePayloads.SEAT, 0, 0)));
            addRenderableWidget(new WmdUi.KitButton(left + 8, top + 58, bw, 18, Component.translatable("gui.wmd.capsule.return_pad", info.padX(), info.padZ()),
                    b -> send(CrewCapsulePayloads.RETURN_PAD, 0, 0)));
            int fw = (bw - 6) / 2;
            xBox = new EditBox(font, left + 8, top + 96, fw, 14, Component.literal("X"));
            zBox = new EditBox(font, left + 14 + fw, top + 96, fw, 14, Component.literal("Z"));
            for (EditBox e : new EditBox[] { xBox, zBox }) {
                e.setMaxLength(9);
                e.setFilter(s -> s.isEmpty() || s.matches("-?\\d{0,8}"));
                addRenderableWidget(e);
            }
            xBox.setHint(Component.literal("X")); zBox.setHint(Component.literal("Z"));
            addRenderableWidget(new WmdUi.KitButton(left + 8, top + 116, bw, 18, Component.translatable("gui.wmd.capsule.return_xz"), b -> {
                try { send(CrewCapsulePayloads.RETURN_XZ, Integer.parseInt(xBox.getValue().trim()), Integer.parseInt(zBox.getValue().trim())); }
                catch (NumberFormatException ignored) {}
            }));
            if (info.docked())
                addRenderableWidget(new WmdUi.KitButton(left + 8, top + 140, bw, 18, Component.translatable("gui.wmd.capsule.enter"), b -> send(CrewCapsulePayloads.ENTER, 0, 0)));
        }
    }

    private void send(int action, int x, int z) {
        PacketDistributor.sendToServer(new CrewCapsulePayloads.Action(info.pos(), action, x, z));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - h()) / 2;
        WmdUi.panel(g, left, top, W, h());
        WmdUi.drawFit(g, font, title, left + 8, top + 6, W - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        WmdUi.divider(g, left + 8, top + 16, W - 16);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        Component state = info.landed() ? Component.translatable("gui.wmd.capsule.landed")
                : Component.translatable("gui.wmd.capsule.orbit", info.seated(), Balance.CREW_SEATS);
        WmdUi.drawFit(g, font, state, left + 8, top + 22, W - 16, WmdUi.TEXT_MUTED);
        if (!info.landed()) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.capsule.coords"), left + 8, top + 84, W - 16, WmdUi.TEXT_MUTED);
    }

    @Override public boolean isPauseScreen() { return false; }
}
