package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ActiveCloakScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 164, PH = 128, BAR_Y = 40, LINE_Y = 56, LINE_H = 12, BTN_Y = 104, BTN_H = 16;
    private static final String P = "gui.wmd.cloak.";
    private WmdUi.KitButton power;

    public ActiveCloakScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private ActiveCloakBlockEntity cloak() { return menu.machine() instanceof ActiveCloakBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return cloak() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        ActiveCloakBlockEntity c = cloak();
        if (c == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, c.getBlockPos())));
        power = addRenderableWidget(new WmdUi.KitButton(px() + 10, py() + BTN_Y, PW - 20, BTN_H, label(c), b -> {
            int next = c.switchedOn() ? 0 : 1;
            c.setModeClient(next);
            PacketDistributor.sendToServer(new SetMachineModePayload(c.getBlockPos(), next));
            b.setMessage(label(c));
        }));
    }

    private static Component label(ActiveCloakBlockEntity c) { return Component.translatable(c.switchedOn() ? P + "switch_off" : P + "switch_on"); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        ActiveCloakBlockEntity c = cloak();
        if (c == null) return;
        if (power != null) power.setMessage(label(c));
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = c.cloakState();
        int kind = switch (st) {
            case ActiveCloakBlockEntity.ST_UP -> WmdUi.K_GOOD;
            case ActiveCloakBlockEntity.ST_STARTING, ActiveCloakBlockEntity.ST_RECHARGE -> WmdUi.K_WARN;
            case ActiveCloakBlockEntity.ST_OFF -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
        WmdUi.header(g, font, x, y, PW, new ItemStack(c.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.active_cloak"),
                Component.translatable(P + "st" + st), kind, st == ActiveCloakBlockEntity.ST_STARTING || st == ActiveCloakBlockEntity.ST_UP);
        WmdUi.drawFit(g, font, Component.translatable(P + "start"), x + 8, y + 28, PW - 16, WmdUi.TEXT_MUTED);
        WmdUi.progressBar(g, x + 8, y + BAR_Y, PW - 16, 8, c.startProgress(), st == ActiveCloakBlockEntity.ST_STARTING);
        Component[] lines = {
                Component.translatable(P + "use", String.format("%,d", Balance.CLOAK_FE)),
                Component.translatable(P + "cover", String.format("%,d", Balance.CLOAK_COVER_BLOCKS)),
                Component.translatable(P + "field", (int) Balance.CLOAK_FIELD_R) };
        for (int i = 0; i < lines.length; i++) WmdUi.drawFit(g, font, lines[i], x + 8, y + LINE_Y + i * LINE_H, PW - 16, i == 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
    }
}
