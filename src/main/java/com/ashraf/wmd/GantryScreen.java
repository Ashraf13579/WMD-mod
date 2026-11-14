package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class GantryScreen extends AbstractContainerScreen<GantryMenu> {

    static final int PW = 140, PH = 166, LINE_Y = 36, LINE_H = 11, HINT_Y = 94, BTN_Y = 126;
    private static final String P = "gui.wmd.gantry.";
    private WmdUi.KitButton run;

    public GantryScreen(GantryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = GantryMenu.INV_Y - 11;
    }

    private int px() { return leftPos - PW - 2; }

    private int py() { return topPos; }

    @Override
    protected void init() {
        super.init();
        run = addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y, PW - 12, 14, Component.translatable(P + "start"), b -> click(0)));
        addRenderableWidget(new WmdUi.KitButton(px() + 6, py() + BTN_Y + 18, PW - 12, 14, Component.translatable(P + "airframe"), b -> click(1)));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (run == null) return;
        boolean on = menu.get(GantryMenu.D_RUN) == 1;
        run.setMessage(Component.translatable(P + (on ? "stop" : "start")));
        int st = menu.get(GantryMenu.D_STATUS);
        run.active = on || st == GantryCabinetBlockEntity.ST_READY || st == GantryCabinetBlockEntity.ST_PARTS || st == GantryCabinetBlockEntity.ST_HATCH;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            WmdUi.slot(g, x + s.x, y + s.y, i < GantryMenu.PARTS ? WmdUi.BLUE : 0);
        }
        int fix = menu.get(GantryMenu.D_FIX);
        ItemStack icon = new ItemStack(AirBlocks.GANTRY_CABINET_ITEM.get());
        WmdUi.header(g, font, x, y, imageWidth, icon, title, Component.translatable(P + "chip", fix), fix == 0 ? WmdUi.K_GOOD : WmdUi.K_WARN, false);

        int sx = px(), sy = py();
        WmdUi.panel(g, sx, sy, PW, PH);
        g.drawString(font, Component.translatable(P + "panel"), sx + 7, sy + 6, WmdUi.TEXT, false);
        WmdUi.divider(g, sx + 6, sy + 16, PW - 12);
        int st = menu.get(GantryMenu.D_STATUS);
        int kind = st == GantryCabinetBlockEntity.ST_WORKING ? WmdUi.K_GOOD : st == GantryCabinetBlockEntity.ST_READY || st == GantryCabinetBlockEntity.ST_DONE ? WmdUi.K_INFO
                : st == GantryCabinetBlockEntity.ST_PARTS || st == GantryCabinetBlockEntity.ST_NO_CRAFT ? WmdUi.K_WARN : WmdUi.K_BAD;
        WmdUi.chip(g, font, sx + 6, sy + 20, Component.translatable(P + "st." + st), kind, st == GantryCabinetBlockEntity.ST_WORKING);
        int len = menu.get(GantryMenu.D_LEN), span = menu.get(GantryMenu.D_SPAN);
        Component[] lines = {
            len > 0 ? Component.translatable(P + "rails", len, span) : Component.translatable(P + "no_rails"),
            Component.translatable(P + "to_fix", fix),
            Component.translatable(P + "missing", menu.get(GantryMenu.D_MISSING)),
            Component.translatable(P + "tote", menu.get(GantryMenu.D_TOTE), Balance.GANTRY_TOTE),
            Component.translatable(P + "power", String.format("%,d", menu.get(GantryMenu.D_FE) * 10)),
        };
        int[] col = { len > 0 ? WmdUi.TEXT : WmdUi.WARN, WmdUi.TEXT, menu.get(GantryMenu.D_MISSING) > 0 ? WmdUi.WARN : WmdUi.TEXT_MUTED, WmdUi.TEXT_MUTED, WmdUi.CYAN };
        for (int i = 0; i < lines.length; i++) WmdUi.drawFit(g, font, lines[i], sx + 7, sy + LINE_Y + i * LINE_H, PW - 14, col[i]);
        var pose = g.pose();
        for (int i = 0; i < 2; i++) {
            pose.pushPose();
            pose.translate(sx + 7, sy + HINT_Y + i * 9, 0);
            pose.scale(0.75F, 0.75F, 1.0F);
            boolean hatch = menu.get(GantryMenu.D_STATUS) == GantryCabinetBlockEntity.ST_HATCH;
            g.drawString(font, Component.translatable(P + (hatch ? "hint_hatch." : "hint.") + i), 0, 0, hatch ? WmdUi.WARN : WmdUi.TEXT_DIM, false);
            pose.popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
