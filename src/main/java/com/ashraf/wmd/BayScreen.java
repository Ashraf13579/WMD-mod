package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class BayScreen extends AbstractContainerScreen<BayMenu> {

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD;
    private static final String[] LABEL = { "airframe", "motor", "guidance", "warhead" };

    private static final int SOCK_BUILT = 0xFF1F4D2E, SOCK_BUILT_EDGE = 0xFF35D25A, SOCK_OPEN = 0xFF2D3A4A, SOCK_LOCKED = 0xFF161B21, SOCK_EDGE = WmdUi.BLUE,
            SOCK_LOCKED_EDGE = WmdUi.DIVIDER, NEEDED = WmdUi.WARN, SEL = 0xFF2D3A4A;

    private static final int[][] GRID = {
            { -1, 0, 0 }, { 0, 1, 0 }, { 1, 2, 0 }, { 2, 3, 0 },
            { 3, 0, 1 }, { 4, 1, 1 }, { 5, 2, 1 }, { -2, 3, 1 },
            { 6, 0, 2 }, { 7, 1, 2 }, { 8, 2, 2 },
            { 9, 0, 3 }, { 10, 1, 3 },
            { 11, 0, 4 }, { 12, 1, 4 }, { 13, 2, 4 },
            { 14, 0, 5 }, { 15, 1, 5 } };
    private static final int GX = 12, GY = 20, GW = 36, GH = 11, GSTEP_X = 38, GSTEP_Y = 14;

    static final int MODE_W = 72, COL_X = 180, COL_W = 62, BTN_H = 18, BTN_STEP = 20, BTN_Y0 = 33;
    private int tab;
    private final WmdUi.Smooth progSmooth = new WmdUi.Smooth(8.0F), feSmooth = new WmdUi.Smooth(6.0F);

    public BayScreen(BayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176 + MODE_W;
        this.imageHeight = 232 + BayMenu.ROW2;
        this.inventoryLabelY = BayMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 17, imageWidth - 16);
        modeColumn(g, x, y, mouseX, mouseY);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BayMenu.INV_Y + row * 18, 0);
        for (int col = 0; col < 9; col++) WmdUi.slot(g, x + 8 + col * 18, y + BayMenu.HOTBAR_Y, 0);
        WmdHelp.button(g, x + 99, y + 4);
        tabButton(g, x + 112, y + 4, "build", tab == 0, mouseX, mouseY);
        tabButton(g, x + 144, y + 4, "line", tab == 1, mouseX, mouseY);
        if (tab == 1) { renderLine(g, x, y); return; }
        for (int i = 0; i < 4; i++) WmdUi.slot(g, x + BayMenu.PART_X + i * BayMenu.PART_STEP, y + BayMenu.SLOT_Y, WmdUi.BLUE);
        WmdUi.slot(g, x + BayMenu.OUT_X, y + BayMenu.SLOT_Y, WmdUi.ORANGE);
        boolean air = menu.mode() == BayModuleBlockEntity.MODE_AIR;
        WmdUi.slot(g, x + BayMenu.UPLINK_X, y + BayMenu.MOD_Y, air ? 0 : WmdUi.BLUE);
        if (air && !menu.getSlot(BayMenu.UPLINK_INDEX).hasItem()) g.fill(x + BayMenu.UPLINK_X + 2, y + BayMenu.MOD_Y + 13, x + BayMenu.UPLINK_X + 14, y + BayMenu.MOD_Y + 14, WmdUi.TEXT_DIM);
        if (menu.getSlot(BayMenu.CAM_INDEX).hasItem()) WmdUi.slot(g, x + BayMenu.CAM_X, y + BayMenu.MOD_Y, WmdUi.ORANGE);
        WmdUi.slot(g, x + BayMenu.SEEKER_X, y + BayMenu.MOD_Y, WmdUi.BLUE);
        g.drawString(font, "▶", x + BayMenu.ARROW_X, y + BayMenu.SLOT_Y + 4, menu.running() ? WmdUi.ORANGE : WmdUi.TEXT_DIM, false);

        int bx = x + 12, by = y + 62 + BayMenu.ROW2, bw = 152, bh = 8;
        WmdUi.progressBar(g, bx, by, bw - 6, bh, progSmooth.cycle(menu.progress()), menu.running());

        hEnergy(g, bx, y + 74 + BayMenu.ROW2, bw, 6, feSmooth.to(Math.min(1.0F, menu.energy() / (float) Balance.BAY_BUFFER_FE)));
    }

    private static int[] modes() { return AirGate.AVAILABLE ? new int[] { 0, 1, 2, 3, 4, 5, 6 } : new int[] { 0, 1, 2, 3, 4, 5 }; }

    private Component modeBlock(int m) {
        if (m == menu.mode()) return null;
        if (menu.stage() != BayModuleBlockEntity.Stage.IDLE) return Component.translatable("gui.wmd.bay.mtip.busy");
        int need = BayModuleBlockEntity.MODE_MIN_SIZE[m];
        if (menu.sizeLevel() < need) return Component.translatable("gui.wmd.bay.mtip.size", need, menu.sizeLevel());
        return null;
    }

    private static ItemStack modeIcon(int m) {
        return new ItemStack(switch (m) {
            case 1 -> Wmd.STAGE_CORE.get();
            case 2 -> Wmd.STAGE_UPPER.get();
            case 3 -> Wmd.STAGE_BOOSTER.get();
            case 4 -> Wmd.CRUISE_MISSILE.get();
            case 5 -> Wmd.SLBM.get();
            case 6 -> AamItem.of(AirMissiles.HEAT);
            default -> Wmd.MISSILE_T2.get();
        });
    }

    private int modeAt(double mx, double my) {
        int[] ms = modes();
        for (int i = 0; i < ms.length; i++) if (WmdUi.over(mx, my, leftPos + COL_X, topPos + BTN_Y0 + i * BTN_STEP, COL_W, BTN_H)) return ms[i];
        return -1;
    }

    private void modeColumn(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int cx = x + COL_X;
        g.fill(cx - 4, y + 21, cx - 3, y + imageHeight - 8, WmdUi.DIVIDER);
        g.drawString(font, Component.translatable("gui.wmd.bay.modes"), cx + 1, y + 22, WmdUi.TEXT_MUTED, false);
        int[] ms = modes();
        for (int i = 0; i < ms.length; i++) {
            int m = ms[i], by = y + BTN_Y0 + i * BTN_STEP;
            boolean on = m == menu.mode(), off = !on && modeBlock(m) != null, hover = WmdUi.over(mouseX, mouseY, cx, by, COL_W, BTN_H);
            if (on) {
                g.fill(cx, by, cx + COL_W, by + BTN_H, WmdUi.BLUE);
                g.fill(cx + 1, by + 1, cx + COL_W - 1, by + BTN_H - 1, SEL);
                g.fill(cx + 1, by + 1, cx + 3, by + BTN_H - 1, WmdUi.BLUE_LIGHT);
            } else WmdUi.button(g, font, cx, by, COL_W, BTN_H, Component.empty(), off ? WmdUi.B_DISABLED : WmdUi.B_NORMAL, hover && !off);
            g.renderItem(modeIcon(m), cx + 4, by + 1);
            if (off) g.fill(cx + 4, by + 1, cx + 20, by + 17, 0xA0141A21);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.bay.mbtn." + m), cx + 22, by + 5, COL_W - 25, on ? TEXT : off ? WmdUi.TEXT_DIM : WmdUi.TEXT_MUTED, 0.7F);
        }

        int iy = y + BTN_Y0 + ms.length * BTN_STEP + 4, ih = y + imageHeight - 8 - iy;
        WmdUi.well(g, cx, iy, COL_W, ih);
        g.drawString(font, Component.translatable("gui.wmd.bay.makes"), cx + 3, iy + 3, WmdUi.TEXT_MUTED, false);
        ItemStack made = product();
        WmdUi.slot(g, cx + 3, iy + 16, WmdUi.ORANGE);
        if (made.isEmpty()) { WmdUi.drawFit(g, font, Component.translatable("gui.wmd.bay.makes_none"), cx + 3, iy + 39, COL_W - 6, WmdUi.TEXT_DIM, 0.7F); return; }
        g.renderItem(made, cx + 3, iy + 16);
        g.drawString(font, "x " + made.getCount(), cx + 24, iy + 20, WmdUi.ORANGE, false);
        int ak = menu.mode() == BayModuleBlockEntity.MODE_AIR ? airK() : -1;
        WmdUi.drawFit(g, font, ak >= 0 ? Component.translatable("gui.wmd.bay.air_short." + ak) : made.getHoverName(), cx + 3, iy + 39, COL_W - 6, TEXT, 0.7F);
        int tier = menu.mode() == BayModuleBlockEntity.MODE_AIR ? Math.max(2, Math.min(3, partTier(0))) : Math.max(1, Math.min(4, partTier(0)));
        if (ih > 60) WmdUi.drawFit(g, font, Component.translatable("gui.wmd.bay.paint_tier." + tier), cx + 3, iy + 50, COL_W - 6, WmdUi.TEXT_MUTED, 0.7F);
    }

    private int airK() {
        boolean jet = menu.slots.get(1).getItem().getItem() instanceof MissilePartItem p && p.isJet();
        return BayModuleBlockEntity.airKindOf(SeekerModuleItem.kindOf(menu.slots.get(BayMenu.SEEKER_INDEX).getItem()), jet);
    }

    private int partTier(int i) { return menu.slots.get(i).getItem().getItem() instanceof MissilePartItem p ? p.tier() : 1; }

    private ItemStack product() {
        int m = menu.mode();
        boolean any = false;
        for (int i = 0; i < 4; i++) any |= menu.slots.get(i).hasItem();
        if (!any) return ItemStack.EMPTY;
        if (m == BayModuleBlockEntity.MODE_AIR) {
            int k = airK();
            if (k == BayModuleBlockEntity.AIR_ASHM) return AirGate.AVAILABLE ? new ItemStack(AntiShipItems.ASHM.get(), Balance.ASHM_BAY_YIELD) : ItemStack.EMPTY;
            net.minecraft.world.item.Item it = k < 0 ? null : AamItem.of(k);
            return it == null ? ItemStack.EMPTY : new ItemStack(it, Balance.BAY_AIR_YIELD);
        }
        if (m == 1) return new ItemStack(Wmd.STAGE_CORE.get());
        if (m == 2) return new ItemStack(Wmd.STAGE_UPPER.get());
        if (m == 3) return new ItemStack(Wmd.STAGE_BOOSTER.get());
        if (m == BayModuleBlockEntity.MODE_CRUISE) return new ItemStack(Wmd.CRUISE_MISSILE.get());
        if (m == BayModuleBlockEntity.MODE_SLBM) return new ItemStack(Wmd.SLBM.get());
        int t = Math.max(1, Math.min(3, partTier(0)));
        boolean icpt = menu.slots.get(3).getItem().getItem() instanceof MissilePartItem w && "interceptor".equals(w.variant());
        return new ItemStack(icpt ? (t == 1 ? Wmd.INTERCEPTOR_T1.get() : t == 2 ? Wmd.INTERCEPTOR_T2.get() : Wmd.INTERCEPTOR_T3.get())
                : t == 1 ? Wmd.MISSILE_T1.get() : t == 2 ? Wmd.MISSILE_T2.get() : Wmd.MISSILE_T3.get());
    }

    private List<Component> modeTip(int m) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("gui.wmd.bay.mbtn." + m));
        tip.add(Component.translatable("gui.wmd.bay.mtip." + m).withStyle(net.minecraft.ChatFormatting.GRAY));
        Component why = modeBlock(m);
        if (why != null) tip.add(why.copy().withStyle(net.minecraft.ChatFormatting.RED));
        else if (m != menu.mode()) tip.add(Component.translatable("gui.wmd.bay.mtip.pick").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return tip;
    }

    static void hEnergy(GuiGraphics g, int x, int y, int w, int h, float frac) {
        WmdUi.well(g, x, y, w, h);
        int fill = Math.round(w * Math.max(0.0F, Math.min(1.0F, frac)));
        if (frac > 0 && fill == 0) fill = 1;
        for (int px = 0; px < fill; px++) {
            if (px % 4 == 3) continue;
            g.fill(x + px, y + 1, x + px + 1, y + h - 1, WmdUi.lerp(0xFF1E7F8E, WmdUi.CYAN, px / (float) w));
        }
        if (fill > 1) g.fill(x + fill - 1, y + 1, x + fill, y + h - 1, 0xFFBFF4FA);
    }

    private void tabButton(GuiGraphics g, int x, int y, String key, boolean on, int mouseX, int mouseY) {
        Component t = Component.translatable("gui.wmd.bay.tab." + key);
        if (on) {
            g.fill(x, y, x + 30, y + 12, WmdUi.BLUE);
            g.fill(x + 1, y + 1, x + 29, y + 11, SEL);
        } else {
            WmdUi.button(g, font, x, y, 30, 12, Component.empty(), WmdUi.B_NORMAL, WmdUi.over(mouseX, mouseY, x, y, 30, 12));
        }
        float sc = Math.min(1.0F, 26.0F / Math.max(1, font.width(t)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + 15 - font.width(t) * sc / 2, y + 2.5F + (1.0F - sc) * 3.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, t, 0, 0, on ? TEXT : WmdUi.TEXT_MUTED, false);
        pose.popPose();
    }

    private int cellAt(double mx, double my) {
        for (int i = 0; i < GRID.length; i++) {
            int cx = leftPos + GX + GRID[i][1] * GSTEP_X, cy = topPos + GY + GRID[i][2] * GSTEP_Y;
            if (mx >= cx && mx < cx + GW && my >= cy && my < cy + GH) return i;
        }
        return -1;
    }

    private void renderLine(GuiGraphics g, int x, int y) {
        int built = menu.builtMask(), open = menu.placeableMask(), need = menu.neededStations();
        for (int[] c : GRID) {
            int cx = x + GX + c[1] * GSTEP_X, cy = y + GY + c[2] * GSTEP_Y, s = c[0];
            if (s == -2) continue;
            boolean isBuilt = s == -1 || (built >> s & 1) != 0, isOpen = s >= 0 && (open >> s & 1) != 0;
            int fill = isBuilt ? SOCK_BUILT : isOpen ? SOCK_OPEN : SOCK_LOCKED;
            g.fill(cx - 1, cy - 1, cx + GW + 1, cy + GH + 1, isOpen ? SOCK_EDGE : isBuilt ? SOCK_BUILT_EDGE : SOCK_LOCKED_EDGE);
            g.fill(cx, cy, cx + GW, cy + GH, fill);
            if (s >= 0 && !isBuilt && ((s == 3 && need >= 1) || (s == 4 && need >= 2) || (s == 5 && need >= 3))) g.fill(cx + GW - 5, cy + 1, cx + GW - 1, cy + 5, NEEDED);
            Component name = Component.translatable("gui.wmd.bay.sock." + (s < 0 ? "core" : BayBuilder.Socket.values()[s].name().toLowerCase()));
            float sc = Math.min(0.85F, (GW - 4.0F) / Math.max(1, font.width(name)));
            var pose = g.pose();
            pose.pushPose();
            pose.translate(cx + GW / 2.0F - font.width(name) * sc / 2, cy + GH / 2.0F - 4.0F * sc, 0);
            pose.scale(sc, sc, 1.0F);
            g.drawString(font, name, 0, 0, isBuilt ? GOOD : isOpen ? TEXT : WmdUi.TEXT_DIM, false);
            pose.popPose();
        }
    }

    private void lineLabels(GuiGraphics g) {
        int need = menu.neededStations();
        int lvl = menu.sizeLevel(), want = Math.max(1, Math.min(4, menu.airframeGrade()));
        fitted(g, Component.translatable("gui.wmd.bay.line.size", lvl, want), GX + 3 * GSTEP_X - 1, GY + GSTEP_Y + 4, lvl >= want ? GOOD : BAD, GW + 2);
        var r = menu.result();
        if (r != BayBuilder.Result.NONE) {
            boolean good = r == BayBuilder.Result.BUILT || r == BayBuilder.Result.REMOVED;
            fitted(g, Component.translatable("gui.wmd.bay.line.result." + r.name().toLowerCase(), menu.resultArg()), GX, 103, good ? GOOD : BAD, 152);
        }
        fitted(g, Component.translatable("gui.wmd.bay.line.table"), GX, 113, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.bay.line.needs", Component.translatable("gui.wmd.bay.line.needs." + need)), GX, 122, TEXT, 152);
        fitted(g, Component.translatable("gui.wmd.bay.line.note"), GX, 131, WmdUi.TEXT_MUTED, 152);
    }

    private List<Component> socketTip(int cell) {
        int s = GRID[cell][0];
        List<Component> tip = new ArrayList<>();
        if (s == -2) return tip;
        tip.add(Component.translatable("gui.wmd.bay.sock.full." + (s < 0 ? "core" : BayBuilder.Socket.values()[s].name().toLowerCase())));
        if (s < 0) { tip.add(Component.translatable("gui.wmd.bay.sock.tip.core")); return tip; }
        boolean isBuilt = (menu.builtMask() >> s & 1) != 0;
        if (isBuilt) tip.add(Component.translatable((menu.removableMask() >> s & 1) != 0 ? "gui.wmd.bay.sock.tip.remove" : menu.running() ? "gui.wmd.bay.sock.tip.busy" : "gui.wmd.bay.sock.tip.keep"));
        else if (menu.running()) tip.add(Component.translatable("gui.wmd.bay.sock.tip.busy"));
        else tip.add(Component.translatable((menu.placeableMask() >> s & 1) != 0 ? "gui.wmd.bay.sock.tip.build" : "gui.wmd.bay.sock.tip.locked"));
        return tip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = mx - leftPos, ly = my - topPos;
        if (button == 0) {
            int m = modeAt(mx, my);
            if (m >= 0) {
                if (m == menu.mode()) return true;
                if (modeBlock(m) != null) { WmdUi.deny(); return true; }
                WmdUi.click();
                press(BayMenu.MODE_BTN + m);
                return true;
            }
        }
        if (button == 0 && ly >= 4 && ly < 16) {
            int t = lx >= 112 && lx < 142 ? 0 : lx >= 144 && lx < 174 ? 1 : -1;
            if (t >= 0) { tab = t; menu.lineTab = t == 1; return true; }
        }
        if (tab == 1 && button == 0) {
            int c = cellAt(mx, my);
            if (c >= 0 && GRID[c][0] >= 0) {
                int s = GRID[c][0];
                boolean isBuilt = (menu.builtMask() >> s & 1) != 0;
                if (isBuilt) { if (hasShiftDown()) press(16 + s); }
                else press(s);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        float ts = Math.min(1.0F, 89.0F / Math.max(1, font.width(title)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(8, 6 + (1.0F - ts) * 4.0F, 0);
        pose.scale(ts, ts, 1.0F);
        g.drawString(font, title, 0, 0, TEXT, false);
        pose.popPose();
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        if (tab == 1) { lineLabels(g); return; }
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.translate(BayMenu.PART_X + i * BayMenu.PART_STEP - 1, BayMenu.SLOT_Y + 21, 0);
            pose.scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.bay.part." + LABEL[i]), 0, 0, WmdUi.TEXT_MUTED, false);
            pose.popPose();
        }
        boolean air = menu.mode() == BayModuleBlockEntity.MODE_AIR;
        pose.pushPose();
        pose.translate(BayMenu.UPLINK_X - 1, BayMenu.MOD_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable(air ? "gui.wmd.bay.no_uplink" : "gui.wmd.bay.part.uplink"), 0, 0, air ? WmdUi.TEXT_DIM : WmdUi.TEXT_MUTED, false);
        pose.popPose();
        if (menu.getSlot(BayMenu.CAM_INDEX).hasItem()) {
            pose.pushPose();
            pose.translate(BayMenu.CAM_X - 1, BayMenu.MOD_Y + 21, 0);
            pose.scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, Component.translatable("gui.wmd.bay.part.campod"), 0, 0, WmdUi.TEXT_MUTED, false);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(BayMenu.SEEKER_X - 1, BayMenu.MOD_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable(air ? "gui.wmd.bay.part.seeker_req" : "gui.wmd.bay.part.seeker"), 0, 0, air ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED, false);
        pose.popPose();
        if (air) {
            int k = airK();
            Component l1 = k < 0 ? Component.translatable("gui.wmd.bay.air_hint1")
                    : Component.translatable("gui.wmd.bay.air_need1", Component.translatable("gui.wmd.bay.air_short." + k), BayModuleBlockEntity.AIR_TIER[k]);
            Component l2 = k < 0 ? Component.translatable("gui.wmd.bay.air_hint2")
                    : Component.translatable(k == BayModuleBlockEntity.AIR_ASHM ? "gui.wmd.bay.air_need2_ashm" : k == AirMissiles.AGM ? "gui.wmd.bay.air_need2_he" : "gui.wmd.bay.air_need2_frag");
            fitted(g, l1, BayMenu.CAM_X + 24, BayMenu.MOD_Y + 1, k < 0 ? WmdUi.TEXT_MUTED : WmdUi.BLUE_LIGHT, 164 - BayMenu.CAM_X - 24);
            fitted(g, l2, BayMenu.CAM_X + 24, BayMenu.MOD_Y + 10, WmdUi.TEXT_MUTED, 164 - BayMenu.CAM_X - 24);
        } else fitted(g, Component.translatable("gui.wmd.bay.modules"), BayMenu.CAM_X + 24, BayMenu.MOD_Y + 5, WmdUi.TEXT_MUTED, 164 - BayMenu.CAM_X - 24);
        pose.pushPose();
        pose.translate(BayMenu.OUT_X - 2, BayMenu.SLOT_Y + 21, 0);
        pose.scale(0.5F, 0.5F, 1.0F);
        g.drawString(font, Component.translatable("gui.wmd.bay.part.missile"), 0, 0, WmdUi.TEXT_MUTED, false);
        pose.popPose();
        fitted(g, Component.translatable("gui.wmd.bay.mline." + menu.mode()), 12, 128 + BayMenu.ROW2, WmdUi.BLUE_LIGHT, 152);
        boolean running = menu.stage() != BayModuleBlockEntity.Stage.IDLE;
        Component status = running ? Component.translatable("gui.wmd.bay.stage." + menu.stage().name().toLowerCase())
                : Component.translatable((menu.flags() & BayModuleBlockEntity.FLAG_LINKED) == 0 ? "gui.wmd.bay.not_linked"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_MODE) != 0 ? (menu.mode() != BayModuleBlockEntity.MODE_CRUISE && jetIn() && !(menu.mode() == BayModuleBlockEntity.MODE_AIR && airK() == BayModuleBlockEntity.AIR_ASHM) ? "gui.wmd.bay.jet_needs_cruise"
                                : menu.mode() == BayModuleBlockEntity.MODE_AIR && BayModuleBlockEntity.airKindOf(SeekerModuleItem.kindOf(menu.slots.get(BayMenu.SEEKER_INDEX).getItem())) < 0 ? "gui.wmd.bay.air_seeker"
                                : menu.mode() != BayModuleBlockEntity.MODE_AIR && MissileBuild.SEEK_RADAR.equals(SeekerModuleItem.kindOf(menu.slots.get(BayMenu.SEEKER_INDEX).getItem())) ? "gui.wmd.bay.radar_seeker_air"
                                : menu.mode() == 0 ? "gui.wmd.bay.t4_is_stages" : "gui.wmd.bay.mode_airframe." + menu.mode())
                        : (menu.flags() & BayModuleBlockEntity.FLAG_GRADE) != 0 ? "gui.wmd.bay.grade_too_high"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_SIZE) != 0 ? "gui.wmd.bay.size_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_NEEDS) != 0 ? "gui.wmd.bay.need_station_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_RUNWAY) != 0 ? "gui.wmd.bay.runway_short"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_OUT) != 0 ? "gui.wmd.bay.output_full"
                        : (menu.flags() & BayModuleBlockEntity.FLAG_READY) != 0 ? "gui.wmd.bay.stage.idle" : "gui.wmd.bay.waiting");
        boolean bad = !running && (menu.flags() & BayModuleBlockEntity.FLAG_READY) == 0;

        WmdUi.led(g, 12, 87 + BayMenu.ROW2, bad ? WmdUi.K_BAD : WmdUi.K_GOOD, running);
        fitted(g, status, 20, 86 + BayMenu.ROW2, bad ? BAD : GOOD, 144);
        fitted(g, Component.translatable("gui.wmd.bay.energy", menu.energy() / 1000, menu.needKfe()), 12, 97 + BayMenu.ROW2, WmdUi.TEXT_MUTED);

        if (air && menu.partsIn() >= 4) {
            int k = airK();
            if (k >= 0) {
                boolean as = k == BayModuleBlockEntity.AIR_ASHM;
                fitted(g, Component.translatable("gui.wmd.bay.air_stats1", Component.translatable("gui.wmd.bay.air_short." + k), String.format("%,d", (int) (as ? Balance.ASHM_RANGE : Balance.AAM_RANGE[k]))),
                        12, 110 + BayMenu.ROW2, TEXT);
                fitted(g, Component.translatable("gui.wmd.bay.air_stats2", Math.round((as ? Balance.ASHM_SPEED : Balance.AAM_SPEED[k]) * 20), Component.translatable("gui.wmd.bay.air_seek." + k),
                        Component.translatable("gui.wmd.bay.air_paint." + BayModuleBlockEntity.AIR_TIER[k])), 12, 120 + BayMenu.ROW2, TEXT);
            } else fitted(g, Component.translatable("gui.wmd.bay.air_seeker"), 12, 110 + BayMenu.ROW2, WmdUi.TEXT_MUTED);
        } else if (menu.partsIn() >= 4) {
            fitted(g, Component.translatable("gui.wmd.bay.stats1", String.format("%.1f", menu.statWeight()), String.format("%,d", menu.statRange())), 12, 110 + BayMenu.ROW2, TEXT);
            fitted(g, Component.translatable(menu.mode() == BayModuleBlockEntity.MODE_CRUISE ? "gui.wmd.bay.stats2_cruise" : menu.mode() == BayModuleBlockEntity.MODE_SLBM ? "gui.wmd.bay.stats2_slbm" : "gui.wmd.bay.stats2",
                    String.format("%.1f", menu.statSpeed()), menu.statHealth(), String.format("%,d", menu.statFuel())), 12, 120 + BayMenu.ROW2, TEXT);
        } else {
            fitted(g, Component.translatable("gui.wmd.bay.stats_none"), 12, 110 + BayMenu.ROW2, WmdUi.TEXT_MUTED);
        }
    }

    private boolean jetIn() { return menu.slots.size() > 1 && menu.slots.get(1).getItem().getItem() instanceof MissilePartItem p && p.isJet(); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour) { fitted(g, text, x, y, colour, 152); }

    private void fitted(GuiGraphics g, Component text, int x, int y, int colour, int width) {
        float sc = Math.min(1.0F, (float) width / Math.max(1, font.width(text)));
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y + (1.0F - sc) * 4.0F, 0);
        pose.scale(sc, sc, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        pose.popPose();
    }

    private int airShowK() {
        int k = airK();
        return k >= 0 ? k : (int) ((System.currentTimeMillis() / 1500L) % 4L);
    }

    static ItemStack airNeed(int i, int k) {
        boolean t2 = BayModuleBlockEntity.AIR_TIER[k] == 2, he = k == AirMissiles.AGM || k == BayModuleBlockEntity.AIR_ASHM;
        net.minecraft.world.item.Item it = switch (i) {
            case 0 -> t2 ? Wmd.AIRFRAME_T2.get() : Wmd.AIRFRAME_T3.get();
            case 1 -> k == BayModuleBlockEntity.AIR_ASHM ? Wmd.JET_ENGINE.get() : t2 ? Wmd.MOTOR_T2.get() : Wmd.MOTOR_T3.get();
            case 2 -> t2 ? Wmd.GUIDANCE_T2.get() : Wmd.GUIDANCE_T3.get();
            case 3 -> he ? Wmd.WARHEAD_HE_T1.get() : Wmd.WARHEAD_FRAG.get();
            default -> k == AirMissiles.HEAT ? Wmd.HEAT_SEEKER.get() : k == AirMissiles.ARM ? Wmd.ARM_SEEKER.get() : k == AirMissiles.AGM ? Wmd.LASER_SEEKER.get()
                    : net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "radar_seeker"));
        };
        return new ItemStack(it);
    }

    private static final int[] AIR_HINT_SLOTS = { 0, 1, 2, 3, BayMenu.SEEKER_INDEX };

    private void airGhosts(GuiGraphics g) {
        if (tab != 0 || menu.mode() != BayModuleBlockEntity.MODE_AIR || !AirGate.AVAILABLE) return;
        int k = airShowK();
        for (int idx : AIR_HINT_SLOTS) {
            net.minecraft.world.inventory.Slot s = menu.slots.get(idx);
            if (s.hasItem()) continue;
            int x = leftPos + s.x, y = topPos + s.y;
            g.renderFakeItem(airNeed(idx, k), x, y);
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            g.fill(x, y, x + 16, y + 16, 0xA00E1218);
            g.pose().popPose();
        }
    }

    private List<Component> airSlotTip() {
        if (tab != 0 || menu.mode() != BayModuleBlockEntity.MODE_AIR || hoveredSlot == null || hoveredSlot.hasItem() || !AirGate.AVAILABLE) return null;
        int idx = -1;
        for (int i : AIR_HINT_SLOTS) if (menu.slots.get(i) == hoveredSlot) idx = i;
        if (idx < 0) return null;
        List<Component> tip = new ArrayList<>();
        int k = airK();
        if (k >= 0) tip.add(Component.translatable("gui.wmd.bay.air_tip_needs", airNeed(idx, k).getHoverName()));
        else {
            tip.add(Component.translatable(idx == BayMenu.SEEKER_INDEX ? "gui.wmd.bay.air_tip_seeker" : "gui.wmd.bay.air_tip_first"));
            for (int a = 0; a < 4; a++)
                tip.add(Component.translatable("gui.wmd.bay.air_tip_row", Component.translatable("gui.wmd.bay.air_short." + a), airNeed(idx, a).getHoverName())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return tip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        airGhosts(g);
        List<Component> airTip = menu.getCarried().isEmpty() ? airSlotTip() : null;
        if (airTip != null) { g.renderComponentTooltip(font, airTip, mouseX, mouseY); return; }
        renderTooltip(g, mouseX, mouseY);
        if (menu.getCarried().isEmpty()) { int m = modeAt(mouseX, mouseY); if (m >= 0) g.renderComponentTooltip(font, modeTip(m), mouseX, mouseY); }
        if (tab == 1 && menu.getCarried().isEmpty()) {
            int c = cellAt(mouseX, mouseY);
            if (c >= 0) { List<Component> tip = socketTip(c); if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY); }
        }
    }
}
