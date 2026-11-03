package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class MlrsScreen extends MachineScreen implements WmdUi.Kit {

    static final int PW = 136, PH = 196;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final int HE_COL = 0xFFE0372D, GUIDED_COL = 0xFFB8C2CC;
    static final String P = "gui.wmd.mlrs.", A = "gui.wmd.art.";
    static final int[] SALVOS = { 1, 2, 6, 12, -1 };
    private static int salvoIdx = 3;

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private boolean filled;
    private final List<Object[]> tips = new ArrayList<>();

    public MlrsScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private MlrsBlockEntity gun() { return menu.machine() instanceof MlrsBlockEntity g ? g : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return topPos; }

    @Override
    protected int sidePanelWidth() { return gun() != null ? PW : 0; }

    @Override
    protected void init() {
        super.init();
        MlrsBlockEntity g = gun();
        if (g == null) return;
        int x = px(), y = py();
        if (!filled && g.hasTarget()) { xText = Integer.toString(g.targetX()); zText = Integer.toString(g.targetZ()); filled = true; }
        xBox = box(x + 14, y + 18, 50, xText);
        zBox = box(x + 78, y + 18, 50, zText);
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, g.getBlockPos())));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 36, 50, 16, Component.translatable(A + "set"), b -> setTarget()));
        addRenderableWidget(new WmdUi.KitButton(x + 60, y + 36, 70, 16, Component.translatable(A + "designated"), b -> send(ArtilleryGunBlockEntity.A_DESIGNATED, 0, 0)));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 160, 14, 14, Component.literal("<"), b -> salvoIdx = Math.floorMod(salvoIdx - 1, SALVOS.length)));
        addRenderableWidget(new WmdUi.KitButton(x + PW - 20, y + 160, 14, 14, Component.literal(">"), b -> salvoIdx = Math.floorMod(salvoIdx + 1, SALVOS.length)));
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + 177, 80, 16, Component.translatable(P + "fire"), b -> send(ArtilleryGunBlockEntity.A_FIRE, SALVOS[salvoIdx], 0), true));
        addRenderableWidget(new WmdUi.KitButton(x + 90, y + 177, 40, 16, Component.translatable(A + "cease"), b -> send(ArtilleryGunBlockEntity.A_CEASE, 0, 0)));
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
        if (x == null || z == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(A + "need_xz"), true); return; }
        send(ArtilleryGunBlockEntity.A_TARGET, x, z);
    }

    private void send(int action, int a, int b) {
        MlrsBlockEntity g = gun();
        if (g == null) return;
        PacketDistributor.sendToServer(new ArtGunPayload(g.getBlockPos(), action, a, b));
    }

    private int pods(int k) { return menu.slots.size() > k ? menu.slots.get(k).getItem().getCount() : 0; }

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
        MlrsBlockEntity gun = gun();
        if (gun == null || minecraft == null || minecraft.level == null) return;
        double now = minecraft.level.getGameTime() + partialTick;
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.drawFit(g, font, Component.translatable(P + "panel"), x + 7, y + 6, PW - 14, TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        g.drawString(font, "X", x + 6, y + 21, GREY, false);
        g.drawString(font, "Z", x + 69, y + 21, GREY, false);

        WmdUi.divider(g, x + 6, y + 53, PW - 12);
        g.drawString(font, Component.translatable(P + "ammo"), x + 7, y + 56, TEXT, false);
        for (int k = 0; k < 2; k++) {
            int t = k == 0 ? Artillery.HE : Artillery.GUIDED;
            int bx = x + 6 + k * 64, by = y + 65;
            boolean picked = gun.picked() == t, hover = WmdUi.over(mouseX, mouseY, bx, by, 60, 22);
            WmdUi.well(g, bx, by, 60, 22);
            if (picked) { g.fill(bx - 1, by - 1, bx + 61, by + 23, WmdUi.BLUE); g.fill(bx, by, bx + 60, by + 22, 0xFF2D3A4A); }
            else if (hover) g.fill(bx, by, bx + 60, by + 22, 0xFF263040);
            g.fill(bx + 1, by, bx + 59, by + 1, k == 0 ? HE_COL : GUIDED_COL);
            ItemStack st = new ItemStack(Artillery.shellItem(Artillery.ROCKET, t));
            g.renderItem(st, bx + 1, by + 3);
            int n = pods(k), l = gun.loaded(t);
            WmdUi.drawFit(g, font, Component.translatable(P + "pods", n), bx + 18, by + 2, 40, n > 0 ? TEXT : BAD);
            WmdUi.drawFit(g, font, Component.translatable(P + "rockets", l), bx + 18, by + 12, 40, l > 0 ? GOOD : GREY);
            if (hover) tips.add(new Object[] { bx, by, bx + 60, by + 22, List.of(st.getHoverName(),
                    Component.translatable(t == Artillery.GUIDED ? "tooltip.wmd.mlrs_pod_guided" : "tooltip.wmd.mlrs_pod", MlrsBlockEntity.POD_ROCKETS).withStyle(ChatFormatting.GRAY),
                    Component.translatable(picked ? A + "picked" : A + "click").withStyle(ChatFormatting.GRAY)) });
        }

        int ly = y + 91;
        for (int p = 0; p < 2; p++) {
            int pt = gun.podType(p), left = gun.podLeft(p);
            for (int i = 0; i < MlrsBlockEntity.POD_ROCKETS; i++) {
                int col = i % 3, row = i / 3;
                int dx = p == 0 ? x + 8 + col * 6 : x + 28 + (2 - col) * 6;
                boolean in = pt >= 0 && i >= MlrsBlockEntity.POD_ROCKETS - left;
                int c = !in ? 0xFF1A2027 : pt == Artillery.GUIDED ? GUIDED_COL : HE_COL;
                g.fill(dx, ly + row * 5, dx + 5, ly + row * 5 + 4, c);
            }
        }
        if (gun.reloading(now)) {
            float f = gun.reload01(now);
            WmdUi.progressBar(g, x + 50, ly + 1, PW - 56, 7, f, true);
            int secs = (int) Math.ceil((1.0F - f) * Balance.MLRS_RELOAD_TICKS / 20.0F);
            tips.add(new Object[] { x + 50, ly, x + PW - 6, ly + 9, List.of(Component.translatable(P + "reloading", secs)) });
        } else {
            int tot = gun.podLeft(0) + gun.podLeft(1);
            WmdUi.drawFit(g, font, Component.translatable(P + "loaded", tot, 2 * MlrsBlockEntity.POD_ROCKETS), x + 50, ly + 1, PW - 56, tot > 0 ? TEXT : GREY);
        }

        WmdUi.well(g, x + 5, y + 102, PW - 10, 41);
        int sy = y + 104;
        if (gun.hasTarget()) {
            fit(g, Component.translatable(A + "target", gun.targetX(), gun.targetZ()), x + 7, sy, TEXT);
            fit(g, Component.translatable(A + "dist", gun.distance(), gun.minRange(), gun.range()), x + 7, sy + 10, TEXT);
            boolean guided = gun.picked() == Artillery.GUIDED;
            Component pat = guided && gun.navOk() ? Component.translatable(P + "pattern_guided", String.format("%.0f", Balance.MLRS_GUIDED_SPREAD))
                    : Component.translatable(gun.spotted() ? P + "pattern_spotted" : P + "pattern", String.format("%.0f", Balance.MLRS_SALVO_RADIUS),
                            String.format("%.0f", gun.spreadShown() * Balance.MLRS_AIM_ERROR));
            fit(g, pat, x + 7, sy + 20, guided && gun.navOk() || gun.spotted() ? GOOD : TEXT);
            float el = gun.aimElevation();
            fit(g, Float.isNaN(el) ? Component.translatable(A + "no_solution") : Component.translatable(P + "elev", String.format("%.1f", el), gun.usedCharge()),
                    x + 7, sy + 30, Float.isNaN(el) ? BAD : TEXT);
        } else fit(g, Component.translatable(A + "no_target"), x + 7, sy, GREY);

        int st = gun.mission() != 0 && gun.gunStatus() == ArtilleryGunBlockEntity.S_READY ? ArtilleryGunBlockEntity.S_FIRING : gun.gunStatus();
        boolean busy = st == ArtilleryGunBlockEntity.S_FIRING || st == ArtilleryGunBlockEntity.S_LAYING || st == ArtilleryGunBlockEntity.S_RELOADING;
        WmdUi.led(g, x + 8, y + 148, statusKind(st), busy);
        Component sl = gun.mission() < 0 && (st == ArtilleryGunBlockEntity.S_FIRING || st == ArtilleryGunBlockEntity.S_RELOADING)
                ? Component.translatable(A + "st." + st).append(Component.translatable(A + "st_all")) : Component.translatable(A + "st." + st);
        WmdUi.drawFit(g, font, sl, x + 17, y + 147, PW - 24, statusColour(st));
        WmdUi.divider(g, x + 6, y + 157, PW - 12);
        int sv = SALVOS[salvoIdx];
        Component salvo = sv < 0 ? Component.translatable(P + "salvo_all") : Component.translatable(P + "salvo", sv);
        int w = Math.min(font.width(salvo), PW - 48);
        WmdUi.drawFit(g, font, salvo, x + PW / 2 - w / 2, y + 163, PW - 48, TEXT);
    }

    private void fit(GuiGraphics g, Component text, int x, int y, int colour) { WmdUi.drawFit(g, font, text, x, y, PW - 14, colour); }

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

    private boolean inPanel(double mx, double my) { return mx >= px() && mx < px() + PW && my >= py() && my < py() + PH; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        MlrsBlockEntity gun = gun();
        if (gun != null && inPanel(mx, my)) {
            int x = px(), y = py();
            for (int k = 0; k < 2; k++) {
                int bx = x + 6 + k * 64, by = y + 65;
                if (WmdUi.over(mx, my, bx, by, 60, 22)) {
                    send(ArtilleryGunBlockEntity.A_PICK, k == 0 ? Artillery.HE : Artillery.GUIDED, 0);
                    WmdUi.click();
                    return true;
                }
            }
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
