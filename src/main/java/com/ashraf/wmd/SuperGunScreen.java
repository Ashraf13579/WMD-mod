package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class SuperGunScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 136, PH = 180;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    static final String P = "gui.wmd.art.", S = "gui.wmd.super.";

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private boolean filled;
    private final List<Object[]> tips = new ArrayList<>();

    public SuperGunScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private SuperGunBlockEntity gun() { return menu.machine() instanceof SuperGunBlockEntity g ? g : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return gun() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        SuperGunBlockEntity g = gun();
        if (g == null) return;
        int x = px(), y = topPos;
        if (!filled && g.hasTarget()) { xText = Integer.toString(g.targetX()); zText = Integer.toString(g.targetZ()); filled = true; }
        xBox = box(x + 14, y + 18, 50, xText);
        zBox = box(x + 78, y + 18, 50, zText);
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, g.getBlockPos())));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 36, 50, 16, Component.translatable(P + "set"), b -> setTarget()));
        addRenderableWidget(new WmdUi.KitButton(x + 60, y + 36, 70, 16, Component.translatable(P + "designated"), b -> send(ArtilleryGunBlockEntity.A_DESIGNATED, 0, 0)));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 158, 40, 16, Component.translatable(P + "fire1"), b -> send(ArtilleryGunBlockEntity.A_FIRE, 1, 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 48, y + 158, 46, 16, Component.translatable(P + "fire_all"), b -> send(ArtilleryGunBlockEntity.A_FIRE, -1, 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 96, y + 158, 34, 16, Component.translatable(P + "cease"), b -> send(ArtilleryGunBlockEntity.A_CEASE, 0, 0)));
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private static Integer parse(EditBox b) {
        try { return b == null || b.getValue().isEmpty() || b.getValue().equals("-") ? null : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return null; }
    }

    private void setTarget() {
        Integer x = parse(xBox), z = parse(zBox);
        if (x == null || z == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "need_xz"), true); return; }
        send(ArtilleryGunBlockEntity.A_TARGET, x, z);
    }

    private void send(int action, int a, int b) {
        SuperGunBlockEntity g = gun();
        if (g == null) return;
        PacketDistributor.sendToServer(new ArtGunPayload(g.getBlockPos(), action, a, b));
    }

    private int count(int slot) { return menu.slots.size() > slot ? menu.slots.get(slot).getItem().getCount() : 0; }

    private static int statusKind(int st) {
        return switch (st) {
            case ArtilleryGunBlockEntity.S_READY, ArtilleryGunBlockEntity.S_FIRING -> WmdUi.K_GOOD;
            case ArtilleryGunBlockEntity.S_LAYING, ArtilleryGunBlockEntity.S_RELOADING -> WmdUi.K_WARN;
            case ArtilleryGunBlockEntity.S_NO_TARGET -> WmdUi.K_IDLE;
            default -> WmdUi.K_BAD;
        };
    }

    private static int statusColour(int st) {
        return switch (statusKind(st)) { case WmdUi.K_GOOD -> GOOD; case WmdUi.K_WARN -> WmdUi.WARN; case WmdUi.K_IDLE -> GREY; default -> BAD; };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        SuperGunBlockEntity gun = gun();
        if (gun == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.drawFit(g, font, Component.translatable(S + "panel"), x + 7, y + 6, PW - 14, TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        g.drawString(font, "X", x + 6, y + 21, GREY, false);
        g.drawString(font, "Z", x + 69, y + 21, GREY, false);

        WmdUi.divider(g, x + 6, y + 53, PW - 12);
        g.drawString(font, Component.translatable(S + "ammo"), x + 7, y + 56, TEXT, false);
        String[] ids = { SuperGun.SHELL, SuperGun.CHARGE };
        String[] tipKeys = { "tooltip.wmd.shell_super_he", "tooltip.wmd.super_charge" };
        for (int i = 0; i < 2; i++) {
            int bx = x + 13 + i * 28, by = y + 66, n = count(i);
            WmdUi.well(g, bx, by, 26, 22);
            g.fill(bx + 1, by, bx + 25, by + 1, i == 0 ? 0xFFE0372D : 0xFF3A9BF5);
            ItemStack st = new ItemStack(ItemProcessBlockEntity.wmd(ids[i]));
            g.renderItem(st, bx + 1, by + 3);
            String c = Integer.toString(n);
            g.drawString(font, c, bx + 25 - font.width(c), by + 13, n > 0 ? TEXT : BAD, false);
            if (mouseX >= bx && mouseX < bx + 26 && mouseY >= by && mouseY < by + 22)
                tips.add(new Object[] { bx, by, bx + 26, by + 22, List.of(st.getHoverName(), Component.translatable(tipKeys[i], Balance.SUPER_GUN_CHARGES, Balance.SUPER_GUN_RANGE).withStyle(ChatFormatting.GRAY)) });
        }
        int k = gun.hasTarget() ? gun.usedCharge() : 0;
        fitLeft(g, k > 0 ? Component.translatable(S + "per_shot", k) : Component.translatable(S + "per_shot_auto"), x + 72, y + 68, PW - 78, TEXT);
        int shots = k > 0 ? Math.min(count(0), count(1) / k) : count(0);
        fitLeft(g, Component.translatable(S + "shots", shots), x + 72, y + 78, PW - 78, shots > 0 ? TEXT : BAD);

        WmdUi.well(g, x + 5, y + 98, PW - 10, 41);
        int ly = y + 100;
        if (gun.hasTarget()) {
            fitLeft(g, Component.translatable(P + "target", gun.targetX(), gun.targetZ()), x + 7, ly, PW - 14, TEXT);
            fitLeft(g, Component.translatable(P + "dist", gun.distance(), gun.minRange(), gun.range()), x + 7, ly + 10, PW - 14, TEXT);
            fitLeft(g, Component.translatable(gun.spotted() ? P + "spread_spotted" : P + "spread", String.format("%.1f", gun.spreadShown())), x + 7, ly + 20, PW - 14, gun.spotted() ? GOOD : TEXT);
            float el = gun.aimElevation();
            fitLeft(g, Float.isNaN(el) ? Component.translatable(P + "no_solution") : Component.translatable(P + "elev", String.format("%.1f", el)), x + 7, ly + 30, PW - 14, Float.isNaN(el) ? BAD : TEXT);
        } else fitLeft(g, Component.translatable(P + "no_target"), x + 7, ly, PW - 14, GREY);

        int st = gun.mission() != 0 && gun.gunStatus() == ArtilleryGunBlockEntity.S_READY ? ArtilleryGunBlockEntity.S_FIRING : gun.gunStatus();
        boolean busy = st == ArtilleryGunBlockEntity.S_FIRING || st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING;
        WmdUi.led(g, x + 8, y + 144, statusKind(st), busy);
        int left = minecraft != null && minecraft.level != null ? gun.reloadLeft(minecraft.level.getGameTime()) : 0;
        Component sl = st == ArtilleryGunBlockEntity.S_RELOADING && left > 0
                ? Component.translatable(S + "reloading", OrbitMath.mmss(left * 50L))
                : gun.mission() < 0 && st == ArtilleryGunBlockEntity.S_FIRING ? Component.translatable(P + "st." + st).append(Component.translatable(P + "st_all"))
                : Component.translatable(P + "st." + st);
        fitLeft(g, sl, x + 17, y + 143, PW - 24, statusColour(st));
        WmdUi.divider(g, x + 6, y + 154, PW - 12);
    }

    private void fitLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        for (Object[] tp : tips) {
            if (mouseX >= (int) tp[0] && mouseX < (int) tp[2] && mouseY >= (int) tp[1] && mouseY < (int) tp[3]) {
                @SuppressWarnings("unchecked") List<Component> lines = (List<Component>) tp[4];
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (gun() != null && inPanel(mx, my)) {
            if (super.mouseClicked(mx, my, button)) return true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPanel(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] { xBox, zBox })
                if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
