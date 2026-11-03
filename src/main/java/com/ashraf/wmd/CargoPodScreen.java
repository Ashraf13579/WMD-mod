package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class CargoPodScreen extends AbstractContainerScreen<CargoPodMenu> {

    private static final int ARM_TICKS = 60;
    private WmdUi.KitButton release;
    private int armed;

    public CargoPodScreen(CargoPodMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 188;
        this.inventoryLabelY = CargoPodMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        if (!menu.isBlock() || !menu.isOwner()) return;
        release = new WmdUi.KitButton(leftPos + imageWidth - 8 - 74, topPos + 78, 74, 14, Component.translatable("gui.wmd.cargo.release"), b -> press(), true);
        addRenderableWidget(release);
    }

    private void press() {
        if (armed <= 0) { armed = ARM_TICKS; return; }
        armed = 0;
        PacketDistributor.sendToServer(new CargoPayloads.Ask(menu.getPos()));
    }

    private int stacks() {
        int n = 0;
        for (int i = 0; i < Balance.CARGO_SLOTS; i++) if (menu.slots.get(i).hasItem()) n++;
        return n;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (armed > 0) armed--;
        if (release != null) {
            int n = stacks();
            release.setMessage(armed <= 0 ? Component.translatable("gui.wmd.cargo.release")
                    : n > 0 ? Component.translatable("gui.wmd.cargo.confirm_burn", n) : Component.translatable("gui.wmd.cargo.confirm"));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 16, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 8 - WmdHelp.SIZE, y + 4);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot sl = menu.slots.get(i);
            WmdUi.slot(g, x + sl.x, y + sl.y, i < Balance.CARGO_SLOTS ? WmdUi.BLUE : 0);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WmdUi.drawFit(g, font, title, 8, 6, imageWidth - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        int n = stacks();
        Component st;
        if (!menu.isBlock()) st = Component.translatable("gui.wmd.cargo.in_hand", n, Balance.CARGO_SLOTS);
        else {
            int port = -1;
            if (minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof CargoPodBlockEntity be) port = be.port();
            st = Component.translatable("gui.wmd.cargo.docked", port, n, Balance.CARGO_SLOTS);
        }
        int w = menu.isBlock() && menu.isOwner() ? imageWidth - 16 - 78 : imageWidth - 16;
        WmdUi.drawFit(g, font, st, 8, 82, w, WmdUi.TEXT_MUTED);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
    }
}
