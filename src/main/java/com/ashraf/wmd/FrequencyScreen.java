package com.ashraf.wmd;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FrequencyScreen extends Screen {
    private static final int W = 176, H = 84;
    private final Screen parent;
    private final IntSupplier current;
    private final IntConsumer sender;
    private EditBox box;
    private boolean synced;

    public FrequencyScreen(Screen parent, IntSupplier current, IntConsumer sender) {
        super(Component.translatable("gui.wmd.frequency.title"));
        this.parent = parent;
        this.current = current;
        this.sender = sender;
    }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = (height - H) / 2;
        box = new EditBox(font, left + 12, top + 36, 60, 16, Component.translatable("gui.wmd.frequency.title"));
        box.setFilter(s -> s.matches("\\d{0,4}"));
        box.setMaxLength(4);
        if (current.getAsInt() > 0) { box.setValue(String.format("%04d", current.getAsInt())); synced = true; }
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(new WmdUi.KitButton(left + 78, top + 35, 40, 18, Component.translatable("screen.wmd.radar.set"), b -> send()));
        addRenderableWidget(new WmdUi.KitButton(left + 8, top + H - 26, W - 16, 18, Component.translatable("gui.wmd.security.back"), b -> onClose()));
    }

    private void send() {
        int f;
        try { f = Math.max(1, Math.min(9999, Integer.parseInt(box.getValue()))); } catch (NumberFormatException e) { f = 1; }
        box.setValue(String.format("%04d", f));
        sender.accept(f);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { send(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (!synced && current.getAsInt() > 0) { box.setValue(String.format("%04d", current.getAsInt())); synced = true; }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int left = (width - W) / 2, top = (height - H) / 2;
        WmdUi.panel(g, left, top, W, H);
        WmdUi.drawFit(g, font, title, left + 8, top + 6, W - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        WmdUi.divider(g, left + 8, top + 16, W - 16);
        WmdHelp.button(g, left + W - 8 - WmdHelp.SIZE, top + 4);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.frequency.label"), left + 12, top + 24, W - 24, WmdUi.TEXT_MUTED);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
