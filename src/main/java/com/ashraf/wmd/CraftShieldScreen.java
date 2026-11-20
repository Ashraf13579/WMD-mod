package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CraftShieldScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 164, PH = 140, BAR_Y = 40, CORE_Y = 64, LINE_Y = 78, LINE_H = 12, BTN_Y = 118, BTN_H = 16;
    private static final String P = "gui.wmd.csh.";
    private WmdUi.KitButton power;

    public CraftShieldScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CraftShieldBlockEntity shield() { return menu.machine() instanceof CraftShieldBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return shield() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        CraftShieldBlockEntity c = shield();
        if (c == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, c.getBlockPos())));
        power = addRenderableWidget(new WmdUi.KitButton(px() + 10, py() + BTN_Y, PW - 20, BTN_H, label(c), b -> {
            int next = c.switchedOn() ? 0 : 1;
            c.setModeClient(next);
            PacketDistributor.sendToServer(new SetMachineModePayload(c.getBlockPos(), next));
            b.setMessage(label(c));
        }));
    }

    private static Component label(CraftShieldBlockEntity c) { return Component.translatable(c.switchedOn() ? P + "switch_off" : P + "switch_on"); }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CraftShieldBlockEntity c = shield();
        if (c == null) return;
        if (power != null) power.setMessage(label(c));
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = c.shieldState();
        int kind = switch (st) {
            case CraftShieldBlockEntity.ST_UP -> WmdUi.K_GOOD;
            case CraftShieldBlockEntity.ST_RISING, CraftShieldBlockEntity.ST_COOLDOWN -> WmdUi.K_WARN;
            case CraftShieldBlockEntity.ST_OFF -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
        WmdUi.header(g, font, x, y, PW, new ItemStack(c.getBlockState().getBlock().asItem()), Component.translatable("block.wmd.craft_shield"),
                Component.translatable(P + "st" + st), kind, st == CraftShieldBlockEntity.ST_RISING || st == CraftShieldBlockEntity.ST_UP);
        boolean cooling = st == CraftShieldBlockEntity.ST_COOLDOWN;
        WmdUi.drawFit(g, font, Component.translatable(cooling ? P + "cooldown" : P + "rise"), x + 8, y + 28, PW - 16, WmdUi.TEXT_MUTED);
        WmdUi.progressBar(g, x + 8, y + BAR_Y, PW - 16, 8, cooling ? c.coolLeft() : c.riseProgress(), st == CraftShieldBlockEntity.ST_RISING || cooling);
        WmdUi.drawFit(g, font, Component.translatable(P + "core", c.corePct()), x + 8, y + CORE_Y - 12, PW - 16, WmdUi.TEXT_MUTED);
        WmdUi.progressBar(g, x + 8, y + CORE_Y, PW - 16, 8, c.corePct() / 100.0F, false);
        int fx = x + 8 + Math.round((PW - 16) * (float) Balance.CSH_FLOOR);
        g.fill(fx, y + CORE_Y - 1, fx + 1, y + CORE_Y + 9, 0xFFE08A3C);
        Component[] lines = {
                Component.translatable(P + "use", String.format("%,d", Balance.CSH_FE)),
                Component.translatable(P + "floor", Math.round(Balance.CSH_FLOOR * 100.0)),
                Component.translatable(P + "through") };
        for (int i = 0; i < lines.length; i++) WmdUi.drawFit(g, font, lines[i], x + 8, y + LINE_Y + i * LINE_H, PW - 16, i == 0 ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
    }
}
