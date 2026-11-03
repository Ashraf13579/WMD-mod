package com.ashraf.wmd;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class TerminalScreen extends AbstractContainerScreen<TerminalMenu> {

    private static final int TEXT = WmdUi.TEXT, TEXT_GOOD = WmdUi.GOOD, TEXT_BAD = WmdUi.BAD, TEXT_WARN = WmdUi.WARN;
    private static final int BAR_HOT = 0xFFE0372D;
    private static final String P = "screen.wmd.terminal.";

    private EditBox xBox, yBox, zBox;
    private Button launchButton, modeButton, asatButton, fuseButton, mirvButton;

    private boolean decoyMode;

    public TerminalScreen(TerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 276 + menu.invDy();
        this.inventoryLabelY = TerminalMenu.INV_Y + menu.invDy() - 11;
    }

    private EditBox makeBox(int x, int w, int value, String label) {
        EditBox box = new EditBox(font, leftPos + x, topPos + 28, w, 14, Component.literal(label));
        box.setFilter(s -> s.matches("-?\\d*"));
        box.setMaxLength(9);
        box.setValue(Integer.toString(value));
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 46, "icon_uplink", Component.translatable("gui.wmd.uplink.tab_tip"),
                b -> minecraft.setScreen(new UplinkScreen(this, menu))));
        xBox = makeBox(14, 46, menu.getInitialX(), "X");
        yBox = makeBox(66, 36, menu.getInitialY(), "Y");
        zBox = makeBox(108, 46, menu.getInitialZ(), "Z");
        addRenderableWidget(new WmdUi.KitButton(leftPos + 14, topPos + 46, 80, 18, Component.translatable(P + "set_target"), b -> sendTarget()));
        launchButton = new WmdUi.KitButton(leftPos + 98, topPos + 46, 64, 18, Component.translatable(P + "launch"),
                b -> PacketDistributor.sendToServer(new LaunchPayload(menu.getPos())), true);
        launchButton.active = false;
        addRenderableWidget(launchButton);
        modeButton = new WmdUi.KitButton(leftPos + 14, topPos + 65, 148, 11, Component.translatable(P + "mode_real"), b -> toggleMode());
        modeButton.visible = false;
        addRenderableWidget(modeButton);
        asatButton = new WmdUi.KitButton(leftPos + 14, topPos + 65, 148, 11, Component.translatable(P + "asat_pick"), b -> nextAsat());
        asatButton.visible = false;
        addRenderableWidget(asatButton);
        fuseButton = new WmdUi.KitButton(leftPos + 14, topPos + 65, 148, 11, Component.translatable(P + "fuse_ground"),
                b -> PacketDistributor.sendToServer(new S5Payload(menu.getPos(), S5Payload.FUSE, 0)));
        fuseButton.visible = false;
        addRenderableWidget(fuseButton);
        mirvButton = new WmdUi.KitButton(leftPos + 14, topPos + 65, 148, 11, Component.translatable(P + "mirv", Balance.MIRV_SPREAD_DEFAULT),
                b -> PacketDistributor.sendToServer(new S5Payload(menu.getPos(), S5Payload.MIRV, 0)));
        mirvButton.visible = false;
        addRenderableWidget(mirvButton);
    }

    private boolean mirvHere() {
        LaunchTerminalBlockEntity t = terminal();
        if (t != null && t.getLoadedTier() > 0) return t.getLoadedBuild().isMirv();
        for (Slot sl : menu.slots) {
            if (!(sl instanceof net.neoforged.neoforge.items.SlotItemHandler)) continue;
            MissileBuild b = sl.getItem().get(WmdComponents.MISSILE_BUILD.get());
            if (b != null && b.isMirv()) return true;
        }
        return false;
    }

    private boolean nukeHere() {
        LaunchTerminalBlockEntity t = terminal();
        if (t != null && t.getLoadedTier() > 0) return WarheadFx.airburstable(WarheadType.of(t.getLoadedBuild().warheadType()));
        for (Slot sl : menu.slots) {
            if (!(sl instanceof net.neoforged.neoforge.items.SlotItemHandler)) continue;
            net.minecraft.world.item.ItemStack st = sl.getItem();
            MissileBuild b = st.get(WmdComponents.MISSILE_BUILD.get());
            if (b == null && st.getItem() instanceof StageItem si) b = si.defaultBuild();
            if (b != null && WarheadFx.airburstable(WarheadType.of(b.warheadType()))) return true;
        }
        return false;
    }

    private LaunchTerminalBlockEntity terminal() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(menu.getPos()) instanceof LaunchTerminalBlockEntity t ? t : null;
    }

    private boolean asatHere() {
        LaunchTerminalBlockEntity t = terminal();
        if (t != null && t.getLoadedTier() > 0) return Asat.isAsat(t.getLoadedBuild());
        for (Slot sl : menu.slots) {
            if (!(sl instanceof net.neoforged.neoforge.items.SlotItemHandler sh) || sh.getSlotIndex() != LaunchTerminalBlockEntity.SLOT_UPPER) continue;
            net.minecraft.world.item.ItemStack st = sl.getItem();
            if (st.getItem() instanceof StageItem si && si.stageKind() == StageItem.UPPER && Asat.isAsat(MissileBuild.of(st))) return true;
        }
        return false;
    }

    private java.util.List<OrbitMath.Sat> enemySats() {
        LaunchTerminalBlockEntity t = terminal();
        java.util.List<OrbitMath.Sat> l = new java.util.ArrayList<>();
        java.util.UUID own = t != null ? t.getOwner() : null;
        for (OrbitMath.Sat s : OrbitView.sats()) if (own == null || !own.equals(s.owner())) l.add(s);
        return l;
    }

    private void nextAsat() {
        LaunchTerminalBlockEntity t = terminal();
        java.util.List<OrbitMath.Sat> l = enemySats();
        if (t == null) return;
        if (l.isEmpty()) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "asat_none"), true); return; }
        int cur = t.asatSat(), next = l.get(0).id();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id() == cur) { next = l.get((i + 1) % l.size()).id(); break; }
        PacketDistributor.sendToServer(new S5Payload(menu.getPos(), S5Payload.ASAT_PICK, next));
    }

    private OrbitMath.Sat asatTarget() {
        LaunchTerminalBlockEntity t = terminal();
        if (t == null || t.asatSat() <= 0) return null;
        for (OrbitMath.Sat s : OrbitView.sats()) if (s.id() == t.asatSat()) return s;
        return null;
    }

    private void toggleMode() {
        decoyMode = !decoyMode;
        applyMode();
    }

    private void applyMode() {
        modeButton.setMessage(Component.translatable(decoyMode ? P + "mode_decoy" : P + "mode_real"));
        xBox.setValue(Integer.toString(decoyMode ? (menu.isFakeSet() ? menu.getFakeX() : menu.getTargetX()) : menu.getTargetX()));
        zBox.setValue(Integer.toString(decoyMode ? (menu.isFakeSet() ? menu.getFakeZ() : menu.getTargetZ()) : menu.getTargetZ()));
        yBox.setEditable(!decoyMode);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        launchButton.active = menu.getStage() == LaunchTerminalBlockEntity.Stage.READY && menu.getHint() == LaunchTerminalBlockEntity.OK;
        modeButton.visible = menu.hasDivert();
        if (!menu.hasDivert() && decoyMode) { decoyMode = false; applyMode(); }
        boolean asat = asatHere();
        asatButton.visible = asat && !menu.hasDivert();
        boolean nuke = !asat && nukeHere();
        boolean mirv = !asat && !nuke && mirvHere();
        mirvButton.visible = mirv;
        mirvButton.setX(leftPos + (mirv && menu.hasDivert() ? 90 : 14));
        mirvButton.setWidth(menu.hasDivert() ? 72 : 148);
        if (mirv) { LaunchTerminalBlockEntity tm = terminal(); mirvButton.setMessage(Component.translatable(P + "mirv", tm != null ? tm.mirvSpread() : Balance.MIRV_SPREAD_DEFAULT)); }
        if (mirv) modeButton.setWidth(72);
        fuseButton.visible = nuke;
        modeButton.setWidth(nuke ? 72 : 148);
        fuseButton.setX(leftPos + (nuke && menu.hasDivert() ? 90 : 14));
        fuseButton.setWidth(menu.hasDivert() ? 72 : 148);
        LaunchTerminalBlockEntity tf = terminal();
        if (nuke) fuseButton.setMessage(Component.translatable(tf != null && tf.airburst() ? P + "fuse_air" : P + "fuse_ground"));
        if (asat) {
            OrbitMath.Sat s = asatTarget();
            asatButton.setMessage(s == null ? Component.translatable(P + "asat_pick") : Component.translatable(P + "asat_target", s.id(), OrbitData.typeName(s.type()), s.ownerName()));
        }
    }

    private static int parse(EditBox box) {
        try {
            return Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void sendTarget() {
        if (decoyMode) PacketDistributor.sendToServer(new SetDecoyPayload(menu.getPos(), parse(xBox), parse(zBox)));
        else PacketDistributor.sendToServer(new SetTargetPayload(menu.getPos(), parse(xBox), parse(yBox), parse(zBox)));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] {xBox, yBox, zBox}) {
                if (box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 16, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 8 - WmdHelp.SIZE, y + 4);

        for (Slot slot : menu.slots) WmdUi.slot(g, x + slot.x, y + slot.y, slot.container instanceof Inventory ? 0 : WmdUi.BLUE);

        int bx = x + 14, by = y + 115, bw = 142;
        LaunchTerminalBlockEntity.Stage st = menu.getStage();
        float p = switch (st) {
            case IDLE -> 0.0F;
            case READY -> 1.0F;
            default -> menu.getProgress();
        };
        boolean hot = st == LaunchTerminalBlockEntity.Stage.COUNTDOWN || st == LaunchTerminalBlockEntity.Stage.LAUNCHING;
        if (hot) {
            WmdUi.well(g, bx, by, bw + 6, 4);
            int fill = Math.round((bw + 6) * p);
            if (fill > 0) g.fill(bx, by, bx + fill, by + 4, WmdUi.lerp(WmdUi.ORANGE, BAR_HOT, WmdUi.pulse(0.6F)));
        } else {
            WmdUi.progressBar(g, bx, by, bw, 4, bar.cycle(p), st == LaunchTerminalBlockEntity.Stage.LOADING || st == LaunchTerminalBlockEntity.Stage.FUELLING);
        }
    }

    private final WmdUi.Smooth bar = new WmdUi.Smooth(8.0F);

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {

        WmdUi.drawFit(g, font, title, 8, 6, imageWidth - 16 - WmdHelp.ROOM, TEXT);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, WmdUi.TEXT_MUTED, false);
        g.drawString(font, "X", 14, 19, WmdUi.TEXT_MUTED, false);
        g.drawString(font, "Y", 66, 19, WmdUi.TEXT_MUTED, false);
        g.drawString(font, "Z", 108, 19, WmdUi.TEXT_MUTED, false);

        caption(g, P + "slot_card", TerminalMenu.CARD_X);
        caption(g, P + "slot_missile", TerminalMenu.MISSILE_X);
        int tf = menu.getTankFluid();
        caption(g, tf == 2 ? P + "slot_fuel_alky" : tf == 1 ? P + "slot_fuel" : P + "slot_fuel_any", TerminalMenu.FUEL_X);
        caption(g, P + "slot_counter", TerminalMenu.COUNTER_X, TerminalMenu.COUNTER_Y);
        if (menu.getTier() >= 4) {
            String[] names = { "slot_stage_core", "slot_stage_upper", "slot_stage_boost", "slot_stage_boost" };
            for (int i = 0; i < 2 + LaunchTerminalBlockEntity.BOOSTERS; i++) caption(g, P + names[i], 14 + i * 24, TerminalMenu.STAGE_Y);
        }

        int need = menu.getFuelNeed();
        g.drawString(font, String.format("%,d", menu.getJetMb()), TerminalMenu.FUEL_X - 2, TerminalMenu.SLOT_Y + 1, menu.getJetMb() >= need ? TEXT_GOOD : TEXT, false);
        g.drawString(font, String.format("/%,d", need), TerminalMenu.FUEL_X - 2, TerminalMenu.SLOT_Y + 10, WmdUi.TEXT_DIM, false);

        g.drawString(font, Component.translatable(P + "power"), 118, TerminalMenu.SLOT_Y - 12, WmdUi.TEXT_MUTED, false);
        g.drawString(font, String.format("%,d FE", menu.getEnergy()), 118, TerminalMenu.SLOT_Y + 1, WmdUi.CYAN, false);
        g.drawString(font, String.format("of %,d", LaunchTerminalBlockEntity.ENERGY_CAPACITY), 118, TerminalMenu.SLOT_Y + 10, WmdUi.TEXT_DIM, false);

        LaunchTerminalBlockEntity.Stage st = menu.getStage();
        Component stage = switch (st) {
            case IDLE -> Component.translatable(P + "stage.idle");
            case LOADING -> Component.translatable(P + "stage.loading", Math.round(menu.getProgress() * 100));
            case FUELLING -> Component.translatable(P + "stage.fuelling", Math.round(menu.getProgress() * 100));
            case READY -> Component.translatable(P + "stage.ready");
            case COUNTDOWN -> Component.translatable(P + "stage.countdown", menu.getSecondsLeft());
            case LAUNCHING -> Component.translatable(P + "stage.launching");
            case COOLDOWN -> Component.translatable(P + "stage.cooldown", menu.getSecondsLeft());
        };

        int stageKind = switch (st) {
            case READY -> WmdUi.K_GOOD;
            case COUNTDOWN, LAUNCHING -> WmdUi.K_BAD;
            case COOLDOWN -> WmdUi.K_WARN;
            case LOADING, FUELLING -> WmdUi.K_INFO;
            default -> WmdUi.K_IDLE;
        };
        WmdUi.chip(g, font, 14, 103, stage, stageKind, st != LaunchTerminalBlockEntity.Stage.IDLE && st != LaunchTerminalBlockEntity.Stage.READY);

        int range = menu.getRange();
        g.drawString(font, Component.translatable(P + "range", String.format("%,d", range)), 14, 124, range > 0 ? TEXT_GOOD : TEXT_BAD, false);
        int dist = distance();
        g.drawString(font, Component.translatable(P + "distance", String.format("%,d", dist)), 14, 134,
                range > 0 && dist <= range ? TEXT : TEXT_BAD, false);

        int rs = menu.getRadarState();
        if ((rs != 2 || !menu.isTargetInArc()) && menu.hasUplinkCell() && menu.uplinkCoverage()) {
            g.drawString(font, Component.translatable(P + "uplink_guided"), 14, 144, TEXT_GOOD, false);
        } else if (rs == 0) {
            g.drawString(font, Component.translatable(P + "radar_none"), 14, 144, TEXT_BAD, false);
        } else if (rs == 1) {
            g.drawString(font, Component.translatable(P + "radar_offline"), 14, 144, TEXT_BAD, false);
        } else if (rs == 3) {
            g.drawString(font, Component.translatable(P + "radar_seeking"), 14, 144, TEXT_BAD, false);
        } else {
            boolean in = menu.isTargetInArc();
            g.drawString(font, Component.translatable(in ? P + "in_arc" : P + "out_of_arc",
                            String.format("%03d", Math.round(menu.getRadarBearing()) % 360), String.format("%03d", Math.round(menu.getTargetBearing()) % 360)),
                    14, 144, in ? TEXT_GOOD : TEXT_BAD, false);
        }

        if (menu.hasDivert()) {
            if (menu.isFakeSet()) {
                double dd = Math.hypot(menu.getFakeX() - menu.getTargetX(), menu.getFakeZ() - menu.getTargetZ());
                g.drawString(font, Component.translatable(P + "decoy", menu.getFakeX(), menu.getFakeZ(), Math.round(dd), (int) Flight.MAX_DECOY), 14, 164,
                        dd <= Flight.MAX_DECOY ? TEXT_GOOD : TEXT_BAD, false);
            } else {
                g.drawString(font, Component.translatable(P + "decoy_none"), 14, 164, TEXT, false);
            }
        }

        if (menu.getStatWeight() > 0) {
            g.drawString(font, Component.translatable(P + "stats", String.format("%.1f", menu.getStatWeight()), String.format("%.1f", menu.getStatSpeed()), menu.getStatHealth()), 14, 174, WmdUi.TEXT_MUTED, false);
        }

        if (asatHere() && !menu.hasDivert()) {
            OrbitMath.Sat s = asatTarget();
            if (s != null) {
                double px = menu.getPos().getX() + 0.5, pz = menu.getPos().getZ() + 0.5;
                long now = OrbitView.now(), over = OrbitMath.overheadLeft(s, px, pz, now);
                g.drawString(font, over > 0 ? Component.translatable(P + "asat_over", OrbitMath.mmss(over)) : Component.translatable(P + "asat_wait", OrbitMath.mmss(OrbitMath.nextPass(s, px, pz, now))),
                        14, 164, over > 0 ? TEXT_GOOD : TEXT_WARN, false);
            }
        }

        int hint = menu.getHint();
        if (!(st == LaunchTerminalBlockEntity.Stage.LOADING || st == LaunchTerminalBlockEntity.Stage.FUELLING) || hint != LaunchTerminalBlockEntity.OK) {
            boolean good = hint == LaunchTerminalBlockEntity.OK;
            if (st == LaunchTerminalBlockEntity.Stage.IDLE || st == LaunchTerminalBlockEntity.Stage.READY || !good) {
                Component hl = hint == LaunchTerminalBlockEntity.ORBIT_FULL
                        ? Component.translatable(P + "hint." + hint, Math.max(OrbitView.count(), Balance.SAT_MAX), Balance.SAT_MAX) : Component.translatable(P + "hint." + hint);
                g.drawString(font, hl, 14, 154, good ? TEXT_GOOD : TEXT_WARN, false);
            }
        }
    }

    private void caption(GuiGraphics g, String key, int slotX) { caption(g, key, slotX, TerminalMenu.SLOT_Y); }

    private void caption(GuiGraphics g, String key, int slotX, int slotY) {
        Component c = Component.translatable(key);
        float scale = 0.75F;
        int w = Math.round(font.width(c) * scale);
        g.pose().pushPose();
        g.pose().translate(slotX + 8 - w / 2.0F, slotY - 9, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, c, 0, 0, WmdUi.TEXT_MUTED, false);
        g.pose().popPose();
    }

    private int distance() {
        double dx = menu.getTargetX() + 0.5 - menu.getPos().getX();
        double dz = menu.getTargetZ() + 0.5 - menu.getPos().getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
