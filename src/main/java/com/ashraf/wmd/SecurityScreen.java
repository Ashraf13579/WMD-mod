package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SecurityScreen extends Screen {
    private static final int W = 176;
    private final Screen parent;
    private final BlockPos pos;
    private EditBox nameBox;
    private int shownVersion = -1;

    public SecurityScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.wmd.security.title"));
        this.parent = parent;
        this.pos = pos;
    }

    private boolean mine() { return pos.equals(SecurityClientState.pos); }

    private int height() { return 108 + 13 * Math.max(1, mine() ? SecurityClientState.names.size() : 1); }

    @Override
    protected void init() {
        if (shownVersion < 0) PacketDistributor.sendToServer(new SecurityActionPayload(pos, 0, ""));
        int left = (width - W) / 2, top = (height - height()) / 2;
        boolean edit = mine() && SecurityClientState.canEdit;
        boolean pub = mine() && SecurityClientState.isPublic;
        Button mode = new WmdUi.KitButton(left + 8, top + 30, W - 16, 18, Component.translatable(pub ? "gui.wmd.security.public" : "gui.wmd.security.private"),
                b -> PacketDistributor.sendToServer(new SecurityActionPayload(pos, 1, "")));
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(pub ? "gui.wmd.security.public.tip" : "gui.wmd.security.private.tip")));
        mode.active = edit;
        addRenderableWidget(mode);
        List<String> names = mine() ? SecurityClientState.names : List.of();
        int y = top + 64;
        for (String n : names) {
            final String name = n;
            Button rm = new WmdUi.KitButton(left + W - 8 - 14, y - 1, 14, 12, Component.literal("x"), b -> PacketDistributor.sendToServer(new SecurityActionPayload(pos, 3, name)), true);
            rm.active = edit;
            addRenderableWidget(rm);
            y += 13;
        }
        if (names.isEmpty()) y += 13;
        nameBox = new EditBox(font, left + 8, y + 4, 112, 14, Component.translatable("gui.wmd.security.name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.wmd.security.name"));
        nameBox.setEditable(edit);
        addRenderableWidget(nameBox);
        Button add = new WmdUi.KitButton(left + 124, y + 3, W - 8 - 124, 16, Component.translatable("gui.wmd.security.add"), b -> {
            if (!nameBox.getValue().isBlank()) { PacketDistributor.sendToServer(new SecurityActionPayload(pos, 2, nameBox.getValue())); nameBox.setValue(""); }
        });
        add.active = edit;
        addRenderableWidget(add);
        addRenderableWidget(new WmdUi.KitButton(left + 8, y + 24, W - 16, 16, Component.translatable("gui.wmd.security.back"), b -> onClose()));
        shownVersion = SecurityClientState.version;
    }

    @Override
    public void tick() {
        super.tick();
        if (SecurityClientState.version != shownVersion) rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - height()) / 2;
        WmdUi.panel(g, left, top, W, height() + 14);
        WmdUi.drawFit(g, font, title, left + 8, top + 6, W - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        WmdUi.divider(g, left + 8, top + 16, W - 16);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        if (mine()) {
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.security.owner", SecurityClientState.ownerName), left + 8, top + 19, W - 16, WmdUi.TEXT_MUTED);
            g.drawString(font, Component.translatable("gui.wmd.security.whitelist"), left + 8, top + 52, WmdUi.TEXT, false);
            int y = top + 64;
            for (String n : SecurityClientState.names) {
                g.fill(left + 8, y - 1, left + W - 8 - 16, y + 11, 0xFF263040);
                g.drawString(font, n, left + 10, y + 1, WmdUi.TEXT, false);
                y += 13;
            }
            if (SecurityClientState.names.isEmpty()) g.drawString(font, Component.translatable("gui.wmd.security.none"), left + 10, y + 1, WmdUi.TEXT_DIM, false);
            if (!SecurityClientState.canEdit) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.security.readonly"), left + 8, top + height() - 2, W - 16, WmdUi.BAD);
        }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
