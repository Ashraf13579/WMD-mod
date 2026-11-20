package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SuitRackScreen extends AbstractContainerScreen<SuitRackMenu> {

    private static final int[] ROW_Y = { 27, 51, 75, 99 };
    private final WmdUi.KitButton[] rf = new WmdUi.KitButton[4];

    public SuitRackScreen(SuitRackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 190;
        imageHeight = 210;
        inventoryLabelY = 117;
    }

    @Override
    protected void init() {
        super.init();
        String[] t = { "-10", "-1", "+1", "+10" };
        for (int i = 0; i < 4; i++) {
            final int id = i;
            rf[i] = new WmdUi.KitButton(leftPos + 96 + i * 23, topPos + 99, 21, 14, Component.literal(t[i]), b -> {
                if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            });
            addRenderableWidget(rf[i]);
        }
    }

    private ItemStack chest() { return menu.rack == null ? ItemStack.EMPTY : menu.rack.piece(1); }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean on = !chest().isEmpty() && ExoModule.RF.on(chest());
        for (WmdUi.KitButton b : rf) b.visible = on;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        super.render(g, mouseX, mouseY, pt);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < SuitRackMenu.SLOTS && menu.piecePresent(hoveredSlot.index))
            g.renderTooltip(font, slotName(hoveredSlot.index), mouseX, mouseY);
    }

    private Component slotName(int k) {
        return k == SuitRackMenu.AMMO ? Component.translatable("item.wmd.exo_micro_missiles") : Component.translatable("item.wmd.exo_mod_" + ExoModule.ALL.get(k).id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 16, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 8 - WmdHelp.SIZE, y + 4);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot sl = menu.slots.get(i);
            if (i < SuitRackMenu.SLOTS) {
                if (!menu.piecePresent(i)) continue;
                WmdUi.slot(g, x + sl.x, y + sl.y, i == SuitRackMenu.AMMO ? WmdUi.ORANGE : WmdUi.BLUE);
                if (!sl.hasItem()) {
                    ItemStack ghost = i == SuitRackMenu.AMMO ? new ItemStack(ExoSuitBlocks.MICRO_MISSILES.get()) : new ItemStack(ExoSuitBlocks.MODULES.get(ExoModule.ALL.get(i)).get());
                    g.renderFakeItem(ghost, x + sl.x, y + sl.y);
                    g.fill(x + sl.x, y + sl.y, x + sl.x + 16, y + sl.y + 16, 200, 0xB0141A22);
                }
            } else WmdUi.slot(g, x + sl.x, y + sl.y, 0);
        }
        ItemStack c = chest();
        if (!c.isEmpty()) {
            float f = ExoSuitItem.fe(c) / (float) Balance.EXO_FE_CAP;
            WmdUi.well(g, x + 96, y + 79, 86, 7);
            WmdUi.energyGauge(g, x + 96, y + 79, 86, 7, f);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        WmdUi.drawFit(g, font, title, 8, 6, imageWidth - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        String[] rows = { "helmet", "chest", "legs", "boots" };
        for (int p = 0; p < 4; p++) {
            boolean here = menu.rack != null && !menu.rack.piece(p).isEmpty();
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.suit_rack." + rows[p]), 8, ROW_Y[p] + 4, 50, here ? WmdUi.TEXT : WmdUi.TEXT_DIM);
            if (!here) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.suit_rack.missing"), 62, ROW_Y[p] + 4, 120, WmdUi.TEXT_DIM);
        }
        ItemStack c = chest();
        if (!c.isEmpty()) {
            if (ExoModule.MISSILES.on(c)) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.suit_rack.ammo"), 160, 41, 28, WmdUi.TEXT_MUTED);
            int pc = Math.round(100.0F * ExoSuitItem.fe(c) / Balance.EXO_FE_CAP);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.suit_rack.charge", pc), 96, 69, 86, menu.rack != null && menu.rack.charging() ? WmdUi.CYAN : WmdUi.TEXT_MUTED);
            if (ExoModule.RF.on(c)) {
                int ch = c.getOrDefault(ExoSuitBlocks.EXO_RF.get(), 0);
                WmdUi.drawFit(g, font, ch == 0 ? Component.translatable("gui.wmd.suit_rack.rf_off") : Component.translatable("gui.wmd.suit_rack.rf", ch), 96, 89, 86, WmdUi.BLUE_LIGHT);
            }
        }
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
    }
}
