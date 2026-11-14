package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CargoRampScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 156;
    static final String P = "gui.wmd.ramp.";

    public CargoRampScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CargoRampBlockEntity ramp() { return menu.machine() instanceof CargoRampBlockEntity r ? r : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return ramp() != null ? PW : 0; }

    private WmdUi.KitButton staticB;

    private void send(int action) {
        CargoRampBlockEntity r = ramp();
        if (r != null) PacketDistributor.sendToServer(new ShorePayload(r.getBlockPos(), action, new int[0]));
    }

    @Override
    protected void init() {
        super.init();
        CargoRampBlockEntity r = ramp();
        if (r == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, r.getBlockPos())));
        int x = px() + 6, q = (PW - 12 - 6) / 4, t = (PW - 12 - 4) / 3;
        addRenderableWidget(new WmdUi.KitButton(x, topPos + 100, q, 14, Component.translatable(P + "open"), w -> send(CargoRampBlockEntity.A_OPEN)));
        addRenderableWidget(new WmdUi.KitButton(x + (q + 2), topPos + 100, q, 14, Component.translatable(P + "level"), w -> send(CargoRampBlockEntity.A_LEVEL)));
        addRenderableWidget(new WmdUi.KitButton(x + 2 * (q + 2), topPos + 100, q, 14, Component.translatable(P + "ground"), w -> send(CargoRampBlockEntity.A_GROUND)));
        addRenderableWidget(new WmdUi.KitButton(x + 3 * (q + 2), topPos + 100, q, 14, Component.translatable(P + "close"), w -> send(CargoRampBlockEntity.A_CLOSE)));
        addRenderableWidget(new WmdUi.KitButton(x, topPos + 118, t, 14, Component.translatable(P + "next"), w -> send(CargoRampBlockEntity.A_NEXT)));
        addRenderableWidget(new WmdUi.KitButton(x + (t + 2), topPos + 118, t, 14, Component.translatable(P + "all"), w -> send(CargoRampBlockEntity.A_ALL), true));
        staticB = addRenderableWidget(new WmdUi.KitButton(x + 2 * (t + 2), topPos + 118, t, 14, staticLabel(r), w -> send(CargoRampBlockEntity.A_STATIC)));
    }

    private static Component staticLabel(CargoRampBlockEntity r) { return Component.translatable(r.staticLineOn() ? P + "static_on" : P + "static_off"); }

    private static int kind(int st) {
        return switch (st) {
            case CargoRampBlockEntity.S_MOVING -> WmdUi.K_INFO;
            case CargoRampBlockEntity.S_LEVEL, CargoRampBlockEntity.S_GROUND -> WmdUi.K_GOOD;
            case CargoRampBlockEntity.S_CLOSED -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        CargoRampBlockEntity r = ramp();
        if (r == null) return;
        if (staticB != null) staticB.setMessage(staticLabel(r));
        int x = px(), y = topPos, tw = PW - 14, st = r.rampState();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(r.getBlockState().getBlock().asItem()), Component.translatable(r.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind(st), st == CargoRampBlockEntity.S_MOVING);
        WmdUi.drawFit(g, font, Component.translatable(P + "size", r.width(), r.height(), r.leafRows(), r.doorRows()), x + 7, y + 26, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "cost", r.width() * r.height() * Balance.RAMP_FE_PER_TILE), x + 7, y + 37, tw, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + 50, PW - 10, 30);
        int n = r.laneLight() + r.laneHeavy();
        WmdUi.drawFit(g, font, Component.translatable(P + "lane", r.laneLight(), r.laneHeavy()), x + 7, y + 53, tw, n > 0 ? WmdUi.ORANGE : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(r.staticLineOn() ? P + "static_line_on" : P + "static_line_off", (int) Balance.STATIC_LINE_MIN), x + 7, y + 65, tw,
                r.staticLineOn() ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint"), x + 7, y + 86, tw, WmdUi.TEXT_DIM);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (ramp() != null && inPanel(mx, my)) {
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
