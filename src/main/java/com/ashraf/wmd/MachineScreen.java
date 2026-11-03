package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class MachineScreen extends AbstractContainerScreen<MachineMenu> {

    private static final int GAUGE_Y = MachineLayout.GAUGE_Y, GAUGE_H = MachineLayout.GAUGE_H, ENERGY_X = MachineLayout.ENERGY_X, ENERGY_W = MachineLayout.ENERGY_W, FIRST_TANK_X = MachineLayout.FIRST_TANK_X;

    protected final boolean kit;
    private final boolean wide;
    private final boolean staggerLabels;

    static int layoutWidth(int tanks) { return MachineLayout.layoutWidth(tanks); }
    static int layoutStep(int tanks) { return MachineLayout.layoutStep(tanks); }
    static int layoutTankW(int tanks) { return MachineLayout.layoutTankW(tanks); }
    private final int tankW, tankStep;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.kit = true;

        this.wide = menu.machine() != null && menu.machine().guiTanks().size() > 3;
        int nt = menu.machine() != null ? menu.machine().guiTanks().size() : 1;
        this.tankW = layoutTankW(nt);
        this.tankStep = layoutStep(nt);
        this.imageWidth = layoutWidth(nt);
        boolean itemMode = menu.machine() != null && menu.machine().guiSlotIcons().stream().anyMatch(i -> !i.isEmpty());

        boolean touch = false;
        if (menu.machine() != null && !itemMode && nt >= 2) {
            net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
            java.util.List<Component> ls = menu.machine().guiTankLabels();
            for (int t = 0; t + 1 < nt && t + 1 < ls.size(); t++) if (tankStep - (f.width(ls.get(t)) + f.width(ls.get(t + 1))) / 2 < 6) touch = true;
        }
        this.staggerLabels = wide || touch;

        int base = (wide ? 156 : 136) + (!wide && touch ? 10 : 0);
        int needed = base;
        if (menu.machine() != null && !itemMode && !kit) {
            net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
            GuiMachine mm = menu.machine();
            int y = GAUGE_Y + GAUGE_H + 19 + (staggerLabels ? 10 : 0);
            if (mm.guiEnergy() != null) { y += 12; if (mm.guiFeUse() > 0) y += 11; }
            boolean skipFirst = mm.guiEnergy() != null && mm.guiFeUse() > 0;
            int n = 0;
            for (Component c : mm.guiInfo()) { if (n++ == 0 && skipFirst) continue; y += 10 * f.split(c, imageWidth - 16).size(); }
            needed = Math.max(base, y + 8);
        }
        this.imageHeight = needed + (itemMode ? ITEM_EXTRA : 0);
        this.inventoryLabelY = imageHeight - 96;
    }

    private static int colourOf(Fluid fluid) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == fluid) return e.color() | 0xFF000000;
        return 0xFF808080;
    }

    private int tankX(int t, int count) {

        GuiMachine m = menu.machine();
        int left = m != null && m.guiEnergy() != null ? FIRST_TANK_X : 14;
        int width = (count - 1) * tankStep + tankW;
        int space = imageWidth - 10 - left;
        return left + (space - width) / 2 + t * tankStep;
    }

    private static final int SLOT_Y = GAUGE_Y + 20;

    private boolean stagger() {
        GuiMachine m = menu.machine();
        return staggerLabels;
    }

    public static int slotAreaX(GuiMachine m, int t) { return MachineLayout.slotAreaX(m, t); }

    public static final int SLOT_AREA_Y = MachineLayout.SLOT_AREA_Y;
    public static final int ITEM_EXTRA = MachineLayout.ITEM_EXTRA;

    private final java.util.List<Object[]> portDots = new java.util.ArrayList<>();

    private static String worldName(net.minecraft.core.Direction d) {
        return switch (d) { case WEST -> "west"; case EAST -> "east"; case NORTH -> "north"; case SOUTH -> "south"; case UP -> "top"; default -> "bottom"; };
    }

    private static int fluidColour(String id) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.id().equals(id)) return e.color() | 0xFF000000;
        return 0xFF808080;
    }

    private static Component fluidName(String id) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.id().equals(id)) return e.type().get().getDescription();
        return Component.literal(id);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        kitBg(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        kitLabels(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (kit) {
            for (int i = 0; i < tabs.size(); i++) if (overTab(i, mouseX, mouseY)) { g.renderTooltip(font, tabs.get(i).name(), mouseX, mouseY); return; }
            if (menu.machine() != null && !WmdHelp.hovered(mouseX, mouseY) && WmdUi.over(mouseX, mouseY, chipX, topPos + 6, leftPos + imageWidth - 6 - chipX, 11)) {
                int st = menu.status();
                boolean hinted = st == 0 && menu.machine().guiEnergy() != null && MachineHints.of(menu) != null;
                g.renderTooltip(font, GuiMachine.lowPower(st) ? Component.translatable("gui.wmd.state.low_power", GuiMachine.lowPowerPct(st))
                        : Component.translatable(st == 0 && hinted ? "gui.wmd.kit.chip.needs" : "gui.wmd.state." + stateKey(st)), mouseX, mouseY); return;
            }
            for (int k = 0; k < 2; k++)
                if (cutLines[k] != null && WmdUi.over(mouseX, mouseY, leftPos + 8, topPos + footerY + k * 11 - 1, imageWidth - 16, 10)) {
                    g.renderTooltip(font, font.split(cutLines[k], 220), mouseX, mouseY); return;
                }
        }
        for (Object[] d : portDots) {
            if (mouseX >= (int) d[0] && mouseX < (int) d[2] && mouseY >= (int) d[1] && mouseY < (int) d[3]) {
                PortMap.Port p = (PortMap.Port) d[5];
                g.renderTooltip(font, Component.empty().append((Component) d[4]).append(Component.literal(" - ")).append(Component.translatable(p.out() ? "gui.wmd.ports.out" : "gui.wmd.ports.in"))
                        .append(Component.literal(" (" + worldName((net.minecraft.core.Direction) d[6]) + (p.side() == net.minecraft.core.Direction.NORTH ? ", machine front" : p.side() == net.minecraft.core.Direction.SOUTH ? ", machine back" : "") + ", level " + p.y() + ")")), mouseX, mouseY);
                return;
            }
        }
        if (kit && "upgrades".equals(shownDrawer) && drawerS.value() > 0.95F)
            for (int i = 0; i < ProcessUnitBlockEntity.UPGRADE_SLOTS; i++) {
                int code = menu.upgradeCode(i);
                if (code != 0 && WmdUi.over(mouseX, mouseY, upgradeX[i], upgradeY, 18, 18)) {
                    g.renderComponentTooltip(font, java.util.List.of(upgradeStack(code).getHoverName(), Component.translatable("gui.wmd.upgrade.level", code & 15, UpgradeItem.Type.byIndex((code >> 4) - 1) != null ? UpgradeItem.Type.byIndex((code >> 4) - 1).maxLevel : Balance.UPGRADE_MAX_LEVEL),
                            Component.translatable("gui.wmd.upgrade.take")), mouseX, mouseY);
                    return;
                }
            }
        GuiMachine m = menu.machine();
        if (m == null) return;
        IEnergyStorage e = m.guiEnergy();
        if (e != null && over(mouseX, mouseY, ENERGY_X, ENERGY_W))
            g.renderTooltip(font, Component.literal(String.format("%,d / %,d FE", menu.energy(), menu.energyCap())), mouseX, mouseY);
        int n = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS);

        if (!menu.getCarried().isEmpty()) {
            for (int si = 0; si < menu.machineSlotCount(); si++) {
                net.minecraft.world.inventory.Slot sl = menu.slots.get(si);
                if (mouseX >= leftPos + sl.x - 1 && mouseX < leftPos + sl.x + 17 && mouseY >= topPos + sl.y - 1 && mouseY < topPos + sl.y + 17 && !sl.mayPlace(menu.getCarried())) {
                    g.renderTooltip(font, Component.literal(sl.container instanceof net.minecraft.world.Container && m.guiSlotOutput(si + menu.itemTankOffset()) ? "Output slot: items cannot be put here" : "This slot does not take " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(menu.getCarried().getItem())), mouseX, mouseY);
                    return;
                }
            }
        }
        if (menu.getCarried().isEmpty() && overProgress(mouseX, mouseY) && RecipeLookup.available()) {
            g.renderTooltip(font, Component.translatable("gui.wmd.kit.recipes_hint"), mouseX, mouseY);
            return;
        }
        for (int t = 0; t < n; t++) {
            boolean isItem = !m.guiSlotIcons().isEmpty() && !m.guiSlotIcons().get(t).isEmpty();
            boolean hit = !isItem ? over(mouseX, mouseY, tankX(t, n), tankW)
                    : itemHit(t, mouseX, mouseY);
            if (hit && !(isItem && menu.getCarried().isEmpty() == false))
                g.renderTooltip(font, Component.empty().append(m.guiTankLabels().get(t)).append(Component.literal(
                        String.format(": %,d / %,d %s", menu.tankAmount(t), menu.tankCap(t), m.guiTankUnit(t)))), mouseX, mouseY);
        }
    }

    public Object[] fluidGaugeAt(double mouseX, double mouseY) {
        GuiMachine m = menu.machine();
        if (m == null) return null;
        int n = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS), mx = (int) mouseX, my = (int) mouseY;
        for (int t = 0; t < n; t++) {
            if (!m.guiSlotIcons().isEmpty() && !m.guiSlotIcons().get(t).isEmpty()) continue;
            if (!over(mx, my, tankX(t, n), tankW)) continue;
            net.minecraft.world.level.material.Fluid f = t < m.guiTankFluids().size() ? m.guiTankFluids().get(t) : null;
            if (f == null || f == net.minecraft.world.level.material.Fluids.EMPTY) return null;
            return new Object[] { f, leftPos + tankX(t, n), topPos + GAUGE_Y, tankW, GAUGE_H };
        }
        return null;
    }

    private boolean itemHit(int t, int mx, int my) {
        int si = t - menu.itemTankOffset();
        if (si < 0 || si >= menu.machineSlotCount()) return false;
        net.minecraft.world.inventory.Slot sl = menu.slots.get(si);
        return mx >= leftPos + sl.x - 1 && mx < leftPos + sl.x + 17 && my >= topPos + sl.y - 1 && my < topPos + sl.y + 17;
    }

    private boolean over(int mx, int my, int relX, int w) {
        int gx = leftPos + relX, gy = topPos + GAUGE_Y;
        return mx >= gx && mx < gx + w && my >= gy && my < gy + GAUGE_H;
    }

    private record Tab(String id, String icon, Component name, Runnable action) {}

    protected void addActionTab(String icon, Component name, Runnable action) { tabs.add(new Tab("action:" + icon, icon, name, action)); }

    protected int sidePanelWidth() { return 0; }

    private int tabX() { int sp = sidePanelWidth(); return leftPos - (sp > 0 ? sp + 2 : 0) + TAB_X; }

    private final Component[] cutLines = new Component[2];
    private int footerY, chipX = Integer.MAX_VALUE;

    private final java.util.List<Tab> tabs = new java.util.ArrayList<>();
    private static String lastTab = "ports";
    private String tab = "process", shownDrawer = "process";
    private final WmdUi.Smooth energyS = new WmdUi.Smooth(10.0F), progressS = new WmdUi.Smooth(14.0F), drawerS = new WmdUi.Smooth(16.0F);
    private final WmdUi.Smooth[] tankS = new WmdUi.Smooth[MachineMenu.MAX_TANKS];
    private int drawerW, drawerH;

    private static final int TAB_X = -23, TAB_Y = 22, TAB_STEP = 24;

    @Override
    protected void init() {
        super.init();
        if (!kit) return;
        tabs.clear();
        GuiMachine m = menu.machine();
        tabs.add(new Tab("process", "icon_process", Component.translatable("gui.wmd.kit.tab.process"), null));
        if (m != null && m.guiPortMap() != null) tabs.add(new Tab("ports", "icon_ports", Component.translatable("gui.wmd.kit.tab.ports"), null));
        if (m != null && !m.guiInfo().isEmpty()) tabs.add(new Tab("info", "icon_info", Component.translatable("gui.wmd.kit.tab.info"), null));
        if (m != null && m.guiUpgrades()) tabs.add(new Tab("upgrades", "icon_upgrades", Component.translatable("gui.wmd.kit.tab.upgrades"), null));
        if (m instanceof RadarLinked rl && m instanceof net.minecraft.world.level.block.entity.BlockEntity rbe)
            addActionTab("icon_freq", Component.translatable("gui.wmd.kit.tab.freq"), () -> minecraft.setScreen(new FrequencyScreen(this, rl::getFrequency,
                    f -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new SetMachineFrequencyPayload(rbe.getBlockPos(), f)))));
        tab = tabs.stream().anyMatch(t -> t.id().equals(lastTab)) ? lastTab : "process";
        shownDrawer = tab;
        for (int i = 0; i < tankS.length; i++) if (tankS[i] == null) tankS[i] = new WmdUi.Smooth(8.0F);

        if (m != null && BulkLoad.supports(m) && menu.machineSlotCount() > 0 && m instanceof net.minecraft.world.level.block.entity.BlockEntity be) {
            WmdUi.KitButton lb = new WmdUi.KitButton(leftPos + imageWidth - 8 - BULK_W, topPos + BULK_Y, BULK_W, 13, Component.translatable("gui.wmd.bulk.load"),
                    b -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BulkLoadPayload(be.getBlockPos())));
            lb.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.wmd.bulk.tip")));
            addRenderableWidget(lb);
        }
    }

    static final int BULK_W = 48, BULK_Y = 79;

    private boolean overTab(int i, double mx, double my) { return WmdUi.over(mx, my, tabX(), topPos + TAB_Y + i * TAB_STEP, 23, 22); }

    private boolean overDrawer(double mx, double my) {
        return !"process".equals(shownDrawer) && drawerS.value() > 0.05F && WmdUi.over(mx, my, leftPos + imageWidth - 4, topPos + 6, drawerW + 4, drawerH);
    }

    private int[] progressRect() {
        if (menu.machineSlotCount() <= 0 || menu.progress() < 0) return null;
        int x0 = Integer.MAX_VALUE, x1 = 0;
        for (int i = 0; i < menu.machineSlotCount(); i++) { x0 = Math.min(x0, menu.slots.get(i).x); x1 = Math.max(x1, menu.slots.get(i).x + 16); }
        int w = 60;
        return new int[] { (x0 + x1) / 2 - w / 2 - 3, SLOT_Y + 27, w, 5 };
    }

    private boolean overProgress(double mx, double my) {
        int[] pb = progressRect();
        return pb != null && WmdUi.over(mx, my, leftPos + pb[0] - 2, topPos + pb[1] - 4, pb[2] + 12, pb[3] + 8);
    }

    private ItemStack machineItem() {
        return menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be ? new ItemStack(be.getBlockState().getBlock().asItem()) : ItemStack.EMPTY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {

        if (button == 1 && overProgress(mx, my) && RecipeLookup.show(machineItem())) { WmdUi.click(); return true; }
        if (kit && button == 0) {
            for (int i = 0; i < tabs.size(); i++) {
                if (!overTab(i, mx, my)) continue;
                WmdUi.click();
                if (tabs.get(i).action() != null) { tabs.get(i).action().run(); return true; }
                String id = tabs.get(i).id();
                tab = id.equals(tab) && !"process".equals(id) ? "process" : id;
                lastTab = tab;
                return true;
            }

            if ("upgrades".equals(shownDrawer) && drawerS.value() > 0.95F && menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be)
                for (int i = 0; i < ProcessUnitBlockEntity.UPGRADE_SLOTS; i++)
                    if (menu.upgradeCode(i) != 0 && WmdUi.over(mx, my, upgradeX[i], upgradeY, 18, 18)) {
                        WmdUi.click();
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new UpgradeRemovePayload(be.getBlockPos(), i));
                        return true;
                    }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        if (kit) {
            for (int i = 0; i < tabs.size(); i++) if (overTab(i, mx, my)) return false;
            if (overDrawer(mx, my)) return false;
        }
        return super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }

    public java.util.List<net.minecraft.client.renderer.Rect2i> kitExtraAreas() {
        java.util.List<net.minecraft.client.renderer.Rect2i> out = new java.util.ArrayList<>();
        if (!kit) return out;
        if (!tabs.isEmpty()) out.add(new net.minecraft.client.renderer.Rect2i(tabX(), topPos + TAB_Y, 26, tabs.size() * TAB_STEP));
        if (sidePanelWidth() > 0) out.add(new net.minecraft.client.renderer.Rect2i(leftPos - sidePanelWidth() - 2, 0, sidePanelWidth(), this.height));
        if (!"process".equals(tab)) out.add(new net.minecraft.client.renderer.Rect2i(leftPos + imageWidth - 4, topPos + 6, drawerW + 4, drawerH));
        return out;
    }

    private Component chipText(int st, boolean hinted) {
        if (GuiMachine.lowPower(st)) return Component.translatable("gui.wmd.kit.chip.low_power", GuiMachine.lowPowerPct(st));
        String k = switch (st) { case 1 -> "running"; case 2 -> "no_power"; case 3 -> "full"; case 4 -> "needs"; case 5, 6, 7, 8, 9, 10, 11 -> "blocked"; default -> hinted ? "needs" : "idle"; };
        return Component.translatable("gui.wmd.kit.chip." + k);
    }

    private static int chipKind(int st, boolean hinted) {
        if (GuiMachine.lowPower(st)) return WmdUi.K_WARN;
        return switch (st) { case 1 -> WmdUi.K_GOOD; case 2, 5, 6, 7, 8, 9, 10, 11, 12 -> WmdUi.K_BAD; case 3, 4 -> WmdUi.K_WARN; default -> hinted ? WmdUi.K_WARN : WmdUi.K_IDLE; };
    }

    private static String stateKey(int st) {
        return switch (st) { case 1 -> "running"; case 2 -> "no_power"; case 3 -> "output_full"; case 4 -> "no_input"; case 5 -> "no_electrode"; case 6 -> "no_water"; case 7 -> "no_impeller"; case 8 -> "no_deposit"; case 9 -> "wrong_bit"; case 10 -> "no_bit"; case 11 -> "no_blades"; case 12 -> "field_down"; default -> "idle"; };
    }

    protected void drawSelectedTab(GuiGraphics g) {
        if (!kit) return;
        for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id().equals(tab)) WmdUi.tab(g, tabX(), topPos + TAB_Y + i * TAB_STEP, tabs.get(i).icon(), true, false);
    }

    private ItemStack machineIcon() {
        if (menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be) return new ItemStack(be.getBlockState().getBlock().asItem());
        return ItemStack.EMPTY;
    }

    private void kitBg(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GuiMachine m = menu.machine();

        float target = tab.equals(shownDrawer) && !"process".equals(tab) ? 1.0F : 0.0F;
        float e = drawerS.to(target);
        if (!tab.equals(shownDrawer) && e < 0.03F) shownDrawer = tab;
        portDots.clear();
        if (m != null && !"process".equals(shownDrawer) && e > 0.01F) {
            int[] size = "ports".equals(shownDrawer) && m.guiPortMap() != null ? portMapSize(m.guiPortMap()) : "upgrades".equals(shownDrawer) ? upgradesSize() : infoSize(m);
            drawerW = size[0]; drawerH = size[1];
            int dx = x + imageWidth - 4 - Math.round((1.0F - WmdUi.easeOut(e)) * (drawerW + 2));
            g.enableScissor(x + imageWidth - 4, y - 10, x + imageWidth + drawerW + 8, y + Math.max(imageHeight, drawerH) + 20);
            WmdUi.drawer(g, dx, y + 6, drawerW + 4, drawerH);
            if ("ports".equals(shownDrawer) && m.guiPortMap() != null) kitPortMap(g, m.guiPortMap(), dx + 4, y + 6, e > 0.95F);
            else if ("upgrades".equals(shownDrawer)) kitUpgrades(g, dx + 4, y + 6);
            else kitInfo(g, m, dx + 4, y + 6);
            g.disableScissor();
        }

        for (int i = 0; i < tabs.size(); i++) if (!tabs.get(i).id().equals(tab)) WmdUi.tab(g, tabX(), y + TAB_Y + i * TAB_STEP, tabs.get(i).icon(), false, overTab(i, mouseX, mouseY));
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        if (sidePanelWidth() <= 0) drawSelectedTab(g);

        if (m == null) return;
        int st = menu.status();
        MachineHints.Hint hint = m.guiEnergy() != null ? MachineHints.of(menu) : null;
        boolean hinted = hint != null && st == 0;
        int kind = chipKind(st, hinted);
        chipX = WmdUi.header(g, font, x, y, imageWidth, machineIcon(), title, chipText(st, hinted), kind, st == 1 || GuiMachine.lowPower(st) || kind == WmdUi.K_BAD);

        for (int i = menu.machineSlotCount(); i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot sl = menu.slots.get(i);
            WmdUi.slot(g, x + sl.x, y + sl.y, 0);
        }
        if (m.guiEnergy() != null) {
            float fe = menu.energyCap() <= 0 ? 0.0F : menu.energy() / (float) menu.energyCap();
            WmdUi.energyGauge(g, x + ENERGY_X, y + GAUGE_Y, ENERGY_W, GAUGE_H, energyS.to(fe));
            g.blitSprite(WmdUi.sprite("icon_power"), x + ENERGY_X + 1, y + GAUGE_Y + GAUGE_H + 3, 12, 12);
        }
        int[] pb = progressRect();
        if (pb != null)
            WmdUi.progressBar(g, x + pb[0], y + pb[1], pb[2], pb[3], progressS.cycle(menu.progress() / 1000.0F), menu.running());
        List<Fluid> fluids = m.guiTankFluids();
        int n = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS);
        List<ItemStack> icons = m.guiSlotIcons();
        for (int t = 0; t < n; t++) {
            if (icons.isEmpty() || icons.get(t).isEmpty()) {
                int rim = m.guiTankColoured() ? (m.guiTankOutput(t) ? WmdUi.ORANGE : WmdUi.BLUE) : 0;
                float f = menu.tankCap(t) <= 0 ? 0.0F : menu.tankAmount(t) / (float) menu.tankCap(t);
                WmdUi.fluidGauge(g, x + tankX(t, n), y + GAUGE_Y, tankW, GAUGE_H, fluids.get(t), colourOf(fluids.get(t)), tankS[t] != null ? tankS[t].to(f) : f, rim);
                continue;
            }
            int si = t - menu.itemTankOffset();
            if (si < 0 || si >= menu.machineSlotCount()) continue;
            net.minecraft.world.inventory.Slot sl = menu.slots.get(si);
            int ax = x + sl.x, ay = y + sl.y;
            WmdUi.slot(g, ax, ay, m.guiSlotOutput(t) ? WmdUi.ORANGE : WmdUi.BLUE);

        }
        int fy = y + GAUGE_Y + GAUGE_H + 19 + (stagger() ? 10 : 0);
        WmdUi.divider(g, x + 8, fy - 5, imageWidth - 16);
    }

    private void kitLabels(GuiGraphics g) {
        GuiMachine m = menu.machine();
        if (m == null) return;
        int n = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS);
        List<ItemStack> iconsL = m.guiSlotIcons();
        boolean items = iconsL.stream().anyMatch(i -> !i.isEmpty());
        for (int t = 0; t < n; t++) {
            if (!iconsL.isEmpty() && !iconsL.get(t).isEmpty()) continue;
            Component lb = m.guiTankLabels().get(t);
            int cx = tankX(t, n) + tankW / 2, ly = GAUGE_Y + GAUGE_H + 5 + (stagger() && t % 2 == 1 ? 10 : 0);
            g.drawString(font, lb, cx - font.width(lb) / 2, ly, WmdUi.TEXT_MUTED, false);
        }
        if (items) g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WmdUi.TEXT_MUTED, false);

        int y = GAUGE_Y + GAUGE_H + 19 + (stagger() ? 10 : 0);
        int st = menu.status();
        MachineHints.Hint hint = m.guiEnergy() != null ? MachineHints.of(menu) : null;
        Component l1 = null, l2 = null;
        int c1 = WmdUi.TEXT_MUTED;
        if (hint != null && hint.need() != null) { l1 = hint.need(); c1 = WmdUi.WARN; }
        else if (GuiMachine.lowPower(st)) { l1 = Component.translatable("gui.wmd.state.low_power", GuiMachine.lowPowerPct(st)); c1 = WmdUi.WARN; }
        else if (st >= 2) { l1 = Component.translatable("gui.wmd.state." + stateKey(st)); c1 = chipKind(st, false) == WmdUi.K_BAD ? WmdUi.BAD : WmdUi.WARN; }
        else if (m.guiEnergy() != null && m.guiFeUse() > 0) {
            int rated = m.guiFeUse() * 20, now = menu.running() ? rated : 0;
            l1 = Component.translatable("gui.wmd.power.rate", String.format("%,d", now), String.format("%,d", rated));
            c1 = menu.running() ? WmdUi.GOOD : WmdUi.TEXT_MUTED;
        } else if (!m.guiInfo().isEmpty() && m.guiEnergy() == null) l1 = m.guiInfo().get(0);
        if (hint != null && hint.how() != null) l2 = hint.how();
        cutLines[0] = l1 != null && WmdUi.drawFit(g, font, l1, 8, y, imageWidth - 16, c1) ? l1 : null;
        if (l2 == null && m.guiPortMap() != null) l2 = portSummary(m.guiPortMap());
        cutLines[1] = l2 != null && WmdUi.drawFit(g, font, l2, 8, y + 11, imageWidth - 16, hint != null && hint.how() != null ? WmdUi.BLUE_LIGHT : WmdUi.TEXT_MUTED) ? l2 : null;
        footerY = y;
    }

    private Component portSummary(PortMap pm) {
        net.minecraft.core.Direction facing = facing();
        java.util.LinkedHashSet<String> in = new java.util.LinkedHashSet<>(), out = new java.util.LinkedHashSet<>(), pw = new java.util.LinkedHashSet<>();
        for (PortMap.Port p : pm.ports) {
            String side = worldName(Machines.dirToWorld(facing, p.side()));
            (p.kind() == PortMap.POWER ? pw : p.out() ? out : in).add(side);
        }
        net.minecraft.network.chat.MutableComponent c = Component.empty();
        if (!in.isEmpty()) c.append(Component.translatable("gui.wmd.kit.in").withColor(WmdUi.BLUE_LIGHT)).append(Component.literal(" " + String.join(", ", in) + "   "));
        if (!out.isEmpty()) c.append(Component.translatable("gui.wmd.kit.out").withColor(WmdUi.ORANGE_LIGHT)).append(Component.literal(" " + String.join(", ", out) + "   "));
        if (!pw.isEmpty()) c.append(Component.translatable("gui.wmd.kit.power").withColor(WmdUi.CYAN)).append(Component.literal(" " + String.join(", ", pw)));
        return c;
    }

    private net.minecraft.core.Direction facing() {
        if (menu.machine() instanceof net.minecraft.world.level.block.entity.BlockEntity be && be.getBlockState().hasProperty(MachineBlock.FACING)) return be.getBlockState().getValue(MachineBlock.FACING);
        return net.minecraft.core.Direction.NORTH;
    }

    private int[] portFootprint(PortMap pm) {
        net.minecraft.core.Direction facing = facing();
        int wx0 = Integer.MAX_VALUE, wx1 = Integer.MIN_VALUE, wz0 = Integer.MAX_VALUE, wz1 = Integer.MIN_VALUE;
        for (int lx = pm.minX; lx <= pm.maxX; lx++) for (int lz = pm.minZ; lz < pm.minZ + pm.depth; lz++) {
            net.minecraft.core.BlockPos w = Machines.toWorld(net.minecraft.core.BlockPos.ZERO, facing, lx, 0, lz);
            wx0 = Math.min(wx0, w.getX()); wx1 = Math.max(wx1, w.getX()); wz0 = Math.min(wz0, w.getZ()); wz1 = Math.max(wz1, w.getZ());
        }
        return new int[] { wx0, wz0, wx1 - wx0 + 1, wz1 - wz0 + 1 };
    }

    private static int cellPx(int[] fp) { return Math.max(5, Math.min(11, 70 / Math.max(fp[2], fp[3]))); }

    private int[] portMapSize(PortMap pm) {
        int[] fp = portFootprint(pm);
        int c = cellPx(fp);
        int legend = 7 + 3 * 17 + font.width(Component.translatable("gui.wmd.kit.in")) + font.width(Component.translatable("gui.wmd.kit.out")) + font.width(Component.translatable("gui.wmd.kit.power"));
        return new int[] { Math.max(fp[2] * c + 24, Math.max(96, legend)), fp[3] * c + 42 };
    }

    private void kitPortMap(GuiGraphics g, PortMap pm, int x, int y, boolean hoverable) {
        int[] fp = portFootprint(pm);
        int w = fp[2], d = fp[3], c = cellPx(fp), pw = drawerW;
        net.minecraft.core.Direction facing = facing();
        g.drawString(font, Component.translatable("gui.wmd.kit.tab.ports"), x + 6, y + 6, WmdUi.TEXT, false);
        g.drawString(font, "N", x + pw - 12, y + 6, 0xFFE07070, false);
        g.fill(x + pw - 10, y + 15, x + pw - 9, y + 17, 0xFFE07070);
        int mx = x + (pw - w * c) / 2, my = y + 20;
        WmdUi.well(g, mx, my, w * c, d * c);
        g.fill(mx, my, mx + w * c, my + d * c, 0xFF2A323C);
        for (int i = 1; i < w; i++) g.fill(mx + i * c, my, mx + i * c + 1, my + d * c, 0xFF222932);
        for (int j = 1; j < d; j++) g.fill(mx, my + j * c, mx + w * c, my + j * c + 1, 0xFF222932);
        switch (facing) {
            case NORTH -> g.fill(mx, my, mx + w * c, my + 2, WmdUi.BLUE_LIGHT);
            case SOUTH -> g.fill(mx, my + d * c - 2, mx + w * c, my + d * c, WmdUi.BLUE_LIGHT);
            case WEST -> g.fill(mx, my, mx + 2, my + d * c, WmdUi.BLUE_LIGHT);
            default -> g.fill(mx + w * c - 2, my, mx + w * c, my + d * c, WmdUi.BLUE_LIGHT);
        }
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (PortMap.Port p : pm.ports) {
            net.minecraft.core.BlockPos wc = Machines.toWorld(net.minecraft.core.BlockPos.ZERO, facing, p.x(), 0, p.z());
            net.minecraft.core.Direction ws = Machines.dirToWorld(facing, p.side());
            int cx = mx + (wc.getX() - fp[0]) * c, cy = my + (wc.getZ() - fp[1]) * c;
            int dx = cx + c / 2, dy = cy + c / 2;
            int k = seen.merge(wc.getX() + "," + wc.getZ() + "," + ws, 1, Integer::sum) - 1;
            int off = (k % 3) * 4 - 4;
            switch (ws) {
                case WEST -> { dx = cx; dy += off; }
                case EAST -> { dx = cx + c; dy += off; }
                case NORTH -> { dy = cy; dx += off; }
                case SOUTH -> { dy = cy + c; dx += off; }
                default -> { dx += off; }
            }
            int inner = switch (p.kind()) { case PortMap.FLUID -> fluidColour(p.fluidId()); case PortMap.ITEM -> 0xFFEFEFEF; default -> WmdUi.CYAN; };
            g.fill(dx - 3, dy - 3, dx + 4, dy + 4, 0xFF05070A);
            g.fill(dx - 2, dy - 2, dx + 3, dy + 3, p.kind() == PortMap.POWER ? WmdUi.CYAN : p.out() ? WmdUi.ORANGE : WmdUi.BLUE);
            g.fill(dx - 1, dy - 1, dx + 2, dy + 2, inner);
            if (hoverable) {
                Component what = switch (p.kind()) { case PortMap.FLUID -> fluidName(p.fluidId()); case PortMap.ITEM -> Component.translatable("gui.wmd.ports.items"); default -> Component.translatable("gui.wmd.ports.power"); };
                portDots.add(new Object[] { dx - 4, dy - 4, dx + 5, dy + 5, what, p, ws });
            }
        }

        int ly = y + drawerH - 14, lx = x + 7;
        int[] cols = { WmdUi.BLUE, WmdUi.ORANGE, WmdUi.CYAN };
        String[] keys = { "gui.wmd.kit.in", "gui.wmd.kit.out", "gui.wmd.kit.power" };
        for (int i = 0; i < 3; i++) {
            Component t = Component.translatable(keys[i]);
            g.fill(lx, ly + 1, lx + 6, ly + 7, cols[i]);
            g.drawString(font, t, lx + 9, ly, WmdUi.TEXT_MUTED, false);
            lx += 9 + font.width(t) + 8;
        }
    }

    private final int[] upgradeX = new int[ProcessUnitBlockEntity.UPGRADE_SLOTS];
    private int upgradeY;

    private static ItemStack upgradeStack(int code) {
        UpgradeItem.Type t = UpgradeItem.Type.byIndex((code >> 4) - 1);
        if (t == null) return ItemStack.EMPTY;
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "upgrade_" + t.id)), code & 15);
    }

    private int[] upgradesSize() { return new int[] { INFO_W, 24 + 24 + font.split(Component.translatable("gui.wmd.upgrade.how"), INFO_W - 16).size() * 10 + 6 }; }

    private void kitUpgrades(GuiGraphics g, int x, int y) {
        g.drawString(font, Component.translatable("gui.wmd.kit.tab.upgrades"), x + 6, y + 6, WmdUi.TEXT, false);
        WmdUi.divider(g, x + 6, y + 17, INFO_W - 12);
        upgradeY = y + 24;
        for (int i = 0; i < ProcessUnitBlockEntity.UPGRADE_SLOTS; i++) {
            int sx = x + 8 + i * 24;
            upgradeX[i] = sx;
            int code = menu.upgradeCode(i);
            WmdUi.slot(g, sx, upgradeY, code != 0 ? WmdUi.BLUE : 0);
            if (code != 0) {
                ItemStack st = upgradeStack(code);
                g.renderItem(st, sx, upgradeY);
                g.renderItemDecorations(font, st, sx, upgradeY);
            }
        }
        int ly = upgradeY + 24;
        for (net.minecraft.util.FormattedCharSequence part : font.split(Component.translatable("gui.wmd.upgrade.how"), INFO_W - 16)) {
            g.drawString(font, part, x + 7, ly, 0xFFC9D3DD, false);
            ly += 10;
        }
    }

    private static final int INFO_W = 150;

    private int[] infoSize(GuiMachine m) {
        int lines = 0;
        for (Component c : m.guiInfo()) lines += font.split(c, INFO_W - 16).size();
        return new int[] { INFO_W, 24 + lines * 10 + 6 };
    }

    private void kitInfo(GuiGraphics g, GuiMachine m, int x, int y) {
        g.drawString(font, Component.translatable("gui.wmd.kit.tab.info"), x + 6, y + 6, WmdUi.TEXT, false);
        WmdUi.divider(g, x + 6, y + 17, INFO_W - 12);
        int ly = y + 22;
        for (Component c : m.guiInfo()) {
            for (net.minecraft.util.FormattedCharSequence part : font.split(c, INFO_W - 16)) {
                g.drawString(font, part, x + 7, ly, 0xFFC9D3DD, false);
                ly += 10;
            }
        }
    }
}
