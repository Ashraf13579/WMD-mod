package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TurntableScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 160, PH = 156, TURN_Y = 108, NOSE_Y = 136;
    static final String P = "gui.wmd.turntable.";

    public TurntableScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private TurntableBlockEntity tt() { return menu.machine() instanceof TurntableBlockEntity t ? t : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return tt() != null ? PW : 0; }

    private void send(int action) {
        TurntableBlockEntity t = tt();
        if (t != null) PacketDistributor.sendToServer(new ShorePayload(t.getBlockPos(), action, new int[0]));
    }

    @Override
    protected void init() {
        super.init();
        TurntableBlockEntity t = tt();
        if (t == null) return;
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, t.getBlockPos())));
        int x = px() + 6, q = (PW - 12 - 4) / 3, f = (PW - 12 - 6) / 4;
        addRenderableWidget(new WmdUi.KitButton(x, topPos + TURN_Y, q, 14, Component.translatable(P + "left"), w -> send(TurntableBlockEntity.O_LEFT)));
        addRenderableWidget(new WmdUi.KitButton(x + (q + 2), topPos + TURN_Y, q, 14, Component.translatable(P + "half"), w -> send(TurntableBlockEntity.O_HALF)));
        addRenderableWidget(new WmdUi.KitButton(x + 2 * (q + 2), topPos + TURN_Y, q, 14, Component.translatable(P + "right"), w -> send(TurntableBlockEntity.O_RIGHT)));
        String[] way = { "north", "east", "south", "west" };
        for (int i = 0; i < 4; i++) {
            final int o = TurntableBlockEntity.O_NORTH + i;
            addRenderableWidget(new WmdUi.KitButton(x + i * (f + 2), topPos + NOSE_Y, f, 14, Component.translatable(P + way[i]), w -> send(o)));
        }
    }

    private static int kind(int st) {
        return switch (st) {
            case TurntableBlockEntity.S_TURNING -> WmdUi.K_INFO;
            case TurntableBlockEntity.S_IDLE -> WmdUi.K_GOOD;
            case TurntableBlockEntity.S_BLOCKED, TurntableBlockEntity.S_NO_POWER -> WmdUi.K_WARN;
            default -> WmdUi.K_BAD;
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        TurntableBlockEntity t = tt();
        if (t == null) return;
        int x = px(), y = topPos, tw = PW - 14, st = Math.max(0, Math.min(4, t.stat())), n = t.size();
        double now = t.getLevel() == null ? 0.0 : t.getLevel().getGameTime() + partialTick;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(t.getBlockState().getBlock().asItem()), Component.translatable(t.getBlockState().getBlock().getDescriptionId()),
                Component.translatable(P + "chip." + st), kind(st), st == TurntableBlockEntity.S_TURNING);
        WmdUi.drawFit(g, font, n > 0 ? Component.translatable(P + "size", n, n, n - 1) : Component.translatable(P + "no_square", Balance.TT_MIN, Balance.TT_MAX), x + 7, y + 26, tw, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "heading", Math.round(((t.angle(now) % 360.0) + 360.0) % 360.0) % 360,
                Math.round(((t.target() % 360.0) + 360.0) % 360.0) % 360), x + 7, y + 37, tw, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "cost", t.tripFe(90.0)), x + 7, y + 48, tw, WmdUi.TEXT_MUTED);
        WmdUi.well(g, x + 5, y + 60, PW - 10, 30);
        boolean craft = !t.craftName().isEmpty();
        WmdUi.drawFit(g, font, craft ? Component.translatable(P + "craft", t.craftName(), t.wheelsOn(), t.wheelsAll()) : Component.translatable(P + "no_craft"),
                x + 7, y + 63, tw, craft ? (t.wheelsOn() >= t.wheelsAll() ? WmdUi.TEXT : WmdUi.ORANGE) : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, t.answer(t.msg()), x + 7, y + 75, tw, st == TurntableBlockEntity.S_BLOCKED || st == TurntableBlockEntity.S_NO_POWER ? WmdUi.ORANGE : WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "redstone", Component.translatable(P + "order." + t.order())), x + 7, y + 94, tw, WmdUi.TEXT_DIM);
        WmdUi.drawFit(g, font, Component.translatable(P + "nose_to"), x + 7, y + 126, tw, WmdUi.TEXT_MUTED);
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tt() != null && inPanel(mx, my)) {
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
