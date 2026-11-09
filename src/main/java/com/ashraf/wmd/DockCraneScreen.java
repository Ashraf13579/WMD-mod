package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class DockCraneScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 128;
    static final String P = "gui.wmd.crane.";

    public DockCraneScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private DockCraneBlockEntity crane() { return menu.machine() instanceof DockCraneBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return crane() != null ? PW : 0; }

    private WmdUi.KitButton toggle;

    @Override
    protected void init() {
        super.init();
        DockCraneBlockEntity c = crane();
        if (c == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, c.getBlockPos())));
        toggle = addRenderableWidget(new WmdUi.KitButton(px() + 6, topPos + 108, PW - 12, 14, label(c),
                w -> { DockCraneBlockEntity k = crane(); if (k != null) PacketDistributor.sendToServer(new ShorePayload(k.getBlockPos(), DockCraneBlockEntity.A_TOGGLE, new int[0])); }));
    }

    private static Component label(DockCraneBlockEntity c) { return Component.translatable(c.enabled() ? P + "turn_off" : P + "turn_on"); }

    private static int kind(int st) {
        return switch (st) {
            case DockCraneBlockEntity.S_WORKING -> WmdUi.K_INFO;
            case DockCraneBlockEntity.S_IDLE, DockCraneBlockEntity.S_NO_LOAD -> WmdUi.K_GOOD;
            case DockCraneBlockEntity.S_NO_RECEIVER, DockCraneBlockEntity.S_HOLDING -> WmdUi.K_WARN;
            case DockCraneBlockEntity.S_OFF -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        DockCraneBlockEntity c = crane();
        if (c == null) return;
        if (toggle != null) toggle.setMessage(label(c));
        int x = px(), y = topPos, tw = PW - 14;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        int st = c.enabled() ? c.craneStatus() : DockCraneBlockEntity.S_OFF;
        WmdUi.header(g, font, x, y, PW, new ItemStack(c.getBlockState().getBlock().asItem()), Component.translatable(c.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind(st), st == DockCraneBlockEntity.S_WORKING);
        WmdUi.drawFit(g, font, Component.translatable(P + "reach", String.format("%.1f", DockCraneBlockEntity.reachMin()), String.format("%.1f", DockCraneBlockEntity.reachMax()),
                (int) Balance.CRANE_DROP), x + 7, y + 26, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "found", c.sourcesInReach(), c.receiversInReach()), x + 7, y + 37, tw, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + 50, PW - 10, 32);
        String job = c.jobText();
        WmdUi.drawFit(g, font, job.isEmpty() ? Component.translatable(P + "no_job") : Component.literal(job), x + 7, y + 52, tw, job.isEmpty() ? WmdUi.TEXT_MUTED : WmdUi.TEXT);
        ItemStack load = c.carried();
        WmdUi.drawFit(g, font, load.isEmpty() ? Component.translatable(P + "hook_empty") : Component.translatable(P + "hook_load", load.getHoverName()), x + 7, y + 62, tw,
                load.isEmpty() ? WmdUi.TEXT_MUTED : WmdUi.ORANGE);
        WmdUi.drawFit(g, font, Component.translatable(P + "phase." + c.phase()), x + 7, y + 72, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint1"), x + 7, y + 86, tw, WmdUi.TEXT_DIM);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint2"), x + 7, y + 96, tw, WmdUi.TEXT_DIM);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (crane() != null && inPanel(mx, my)) {
            if (super.mouseClicked(mx, my, button)) return true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
