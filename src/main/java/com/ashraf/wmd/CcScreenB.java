package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreenB extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 184;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT, AMBER = WmdUi.WARN;
    private int mouseX, mouseY;
    private static final String P = "gui.wmd.cc.b.";
    private static final int ROW_Y = 39, ROW_H = 10;

    private boolean jamFirst;
    private static final long CONFIRM_MS = 5000L;

    private EditBox xBox, zBox, nameBox;
    private String xText = "", zText = "", nameText = "";
    private Button aimButton, goButton, cancelButton, desButton;
    private long selected;
    private final Set<Long> picked = new LinkedHashSet<>();
    private final Set<Long> pkgPicked = new LinkedHashSet<>();
    private boolean pkg;
    private int pkgScroll;
    private int rowScroll;
    private Button counterButton, pkgButton, reconButton;
    private long counterConfirmUntil;
    private long confirmUntil;
    private int reconIndex;

    public CcScreenB(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CcConsoleBlockEntity be() { return menu.machine() instanceof CcConsoleBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return be() != null && panelHeight() > 0 ? PW : 0; }

    private int py() { return Math.max(4, Math.min(topPos, height - panelHeight() - 4)); }

    private int panelHeight() {
        CcConsoleBlockEntity b = be();
        if (b instanceof CcLaunchConsoleBlockEntity) return 208;
        if (b instanceof CcRetargetConsoleBlockEntity) return 172;
        if (b instanceof CcStrikePlannerBlockEntity) return 272;
        if (b instanceof CcMissileCamBlockEntity) return 136;
        return 0;
    }

    private int maxRows() {
        CcConsoleBlockEntity b = be();
        return b instanceof CcLaunchConsoleBlockEntity || b instanceof CcStrikePlannerBlockEntity ? 8 : 6;
    }

    @Override
    protected void init() {
        super.init();
        CcConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py();
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        xBox = zBox = nameBox = null;
        aimButton = goButton = cancelButton = desButton = null;
        counterButton = pkgButton = reconButton = null;
        if (b instanceof CcLaunchConsoleBlockEntity || b instanceof CcStrikePlannerBlockEntity) {
            coordBoxes(x, y + 123);
            reconButton = addRenderableWidget(new WmdUi.KitButton(x + 138, y + 122, PW - 144, 15, Component.translatable(P + "recon"), w -> nextRecon()));
            int dy = b instanceof CcLaunchConsoleBlockEntity ? 186 : 230;
            desButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + dy, PW - 12, 16, Component.translatable(P + "use_des"), w -> useDesignated()));
            if (b instanceof CcLaunchConsoleBlockEntity) {
                aimButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 141, 56, 16, Component.translatable(P + "aim"), w -> aim()));
                goButton = addRenderableWidget(new WmdUi.KitButton(x + 66, y + 141, PW - 72, 16, Component.translatable(P + "launch"), w -> launch(), true));
            } else {
                goButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 141, 112, 16, Component.translatable(P + "plan"), w -> plan(), true));
                cancelButton = addRenderableWidget(new WmdUi.KitButton(x + 122, y + 141, PW - 128, 16, Component.translatable(P + "cancel_plan"), w -> send(CcBPayload.of(b.getBlockPos(), CcBPayload.CANCEL, 0L, 0, 0))));
                addRenderableWidget(new WmdUi.KitButton(x + 6, y + 231, PW - 12, 16, Component.empty(), w -> jamFirst = !jamFirst) {
                    @Override public Component getMessage() { return Component.translatable(jamFirst ? P + "jam_on" : P + "jam_off"); }
                });

                counterButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 250, 108, 16, Component.empty(), w -> counter(), true));
                pkgButton = addRenderableWidget(new WmdUi.KitButton(x + 118, y + 250, PW - 124, 16, Component.empty(), w -> { pkg = !pkg; confirmUntil = 0; updateButtons(); }));
            }
        } else if (b instanceof CcRetargetConsoleBlockEntity) {
            coordBoxes(x, y + 103);
            nameBox = new EditBox(font, x + 6, y + 121, PW - 12, 14, Component.translatable("gui.wmd.uplink.lock_name"));
            nameBox.setMaxLength(16);
            nameBox.setFilter(s -> s.matches("[A-Za-z0-9_]*"));
            nameBox.setValue(nameText);
            nameBox.setHint(Component.translatable(P + "lock_hint"));
            addRenderableWidget(nameBox);
            goButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 139, PW - 12, 16, Component.translatable(P + "retarget"), w -> retarget(), true));
        } else if (b instanceof CcMissileCamBlockEntity) {
            goButton = addRenderableWidget(new WmdUi.KitButton(x + 6, y + 103, PW - 12, 16, Component.translatable(P + "watch"), w -> watch()));
        }
        updateButtons();
    }

    private void coordBoxes(int x, int y) {
        xBox = box(x + 16, y, 52, xText);
        zBox = box(x + 82, y, 52, zText);
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
        keepText();
        super.rebuildWidgets();
    }

    private void keepText() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        if (nameBox != null) nameText = nameBox.getValue();
    }

    private static int parse(EditBox b) {
        try { return b == null ? 0 : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    private CcTarget designated() {
        CcConsoleBlockEntity b = be();
        CcBuildingBlockEntity bb = b != null ? b.building() : null;
        return bb != null ? bb.designated() : null;
    }

    private void useDesignated() {
        CcTarget t = designated();
        if (t == null || xBox == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "no_des"), true); return; }
        xBox.setValue(Integer.toString(t.x()));
        zBox.setValue(Integer.toString(t.z()));
        confirmUntil = 0;
        updateButtons();
    }

    private boolean boxesEmpty() { return xBox != null && xBox.getValue().isEmpty() && zBox.getValue().isEmpty(); }

    private boolean hasTarget() { return xBox != null && !xBox.getValue().isEmpty() && !xBox.getValue().equals("-") && !zBox.getValue().isEmpty() && !zBox.getValue().equals("-"); }

    private void send(CcBPayload p) {
        PacketDistributor.sendToServer(p);
        confirmUntil = 0;
        updateButtons();
    }

    private boolean confirmed() {
        long now = System.currentTimeMillis();
        if (now < confirmUntil) return true;
        confirmUntil = now + CONFIRM_MS;
        updateButtons();
        return false;
    }

    private boolean asatSelected() {
        CompoundTag sel = selectedRow();
        return be() instanceof CcLaunchConsoleBlockEntity && sel != null && sel.getBoolean("asat");
    }

    private List<OrbitMath.Sat> enemySats() {
        CcConsoleBlockEntity b = be();
        List<OrbitMath.Sat> l = new ArrayList<>();
        if (b != null && b.getOwner() != null) for (OrbitMath.Sat s : OrbitView.sats()) if (!b.getOwner().equals(s.owner())) l.add(s);
        return l;
    }

    private void nextSat() {
        CcConsoleBlockEntity b = be();
        CompoundTag sel = selectedRow();
        List<OrbitMath.Sat> l = enemySats();
        if (b == null || sel == null) return;
        if (l.isEmpty()) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "asat_none"), true); return; }
        int cur = sel.getInt("as"), next = l.get(0).id();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id() == cur) { next = l.get((i + 1) % l.size()).id(); break; }
        send(CcBPayload.of(b.getBlockPos(), CcBPayload.ASAT_PICK, selected, next, 0));
    }

    private void aim() {
        if (asatSelected()) { nextSat(); return; }
        CcConsoleBlockEntity b = be();
        if (b == null || selected == 0 || !hasTarget()) return;
        send(CcBPayload.of(b.getBlockPos(), CcBPayload.AIM, selected, parse(xBox), parse(zBox)));
    }

    private void launch() {
        CcConsoleBlockEntity b = be();
        if (b != null && selected != 0 && asatSelected()) {
            if (confirmed()) send(CcBPayload.of(b.getBlockPos(), CcBPayload.LAUNCH, selected, 0, 0));
            return;
        }
        if (b == null || selected == 0 || !hasTarget() || !confirmed()) return;
        send(CcBPayload.of(b.getBlockPos(), CcBPayload.LAUNCH, selected, parse(xBox), parse(zBox)));
    }

    private CcTarget counterTarget() {
        CcTarget t = designated();
        return t != null && t.source() == CcTarget.SRC_COUNTER ? t : null;
    }

    private boolean counterArmed() {
        CcConsoleBlockEntity b = be();
        CcBuildingBlockEntity bb = b != null ? b.building() : null;
        return bb != null && b.getLevel() != null && bb.counterArmed(b.getLevel().getGameTime());
    }

    private int readyPads() {
        int n = 0;
        if (be() instanceof CcStrikePlannerBlockEntity s)
            for (CompoundTag c : s.terminals()) {
                if (c.getBoolean("cruise")) continue;
                if (c.getBoolean("gun")) {
                    if (c.getBoolean("in") && !c.getBoolean("dim") && c.getInt("m") == 0 && c.getInt("a" + Artillery.HE) > 0) n++;
                    continue;
                }
                if (c.getInt("s") == LaunchTerminalBlockEntity.Stage.READY.ordinal() && c.getBoolean("in") && !c.getBoolean("dim") && !c.getString("w").isEmpty()
                        && !c.getBoolean("sat") && !c.getBoolean("asat")) n++;
            }
        return n;
    }

    private void counter() {
        CcConsoleBlockEntity b = be();
        if (b == null || counterTarget() == null) return;
        long now = System.currentTimeMillis();
        if (counterArmed() || now < counterConfirmUntil) {
            counterConfirmUntil = 0;
            send(CcBPayload.of(b.getBlockPos(), CcBPayload.COUNTER, 0L, 0, 0));
            return;
        }
        counterConfirmUntil = now + CONFIRM_MS;
        updateButtons();
    }

    private List<Integer> pkgOrder() {
        CcConsoleBlockEntity b = be();
        List<Integer> l = new ArrayList<>();
        if (b == null) return l;
        int[] ic = b.intel();
        for (int i = 0; i + 2 < ic.length; i += 3) l.add(i / 3);
        CcBuildingBlockEntity bb = b.building();
        double hx = bb != null ? bb.hallCentre().getX() : b.getBlockPos().getX(), hz = bb != null ? bb.hallCentre().getZ() : b.getBlockPos().getZ();
        l.sort((p, q) -> {
            int a = CcStrikePlannerBlockEntity.priority(ic[p * 3 + 2]), c = CcStrikePlannerBlockEntity.priority(ic[q * 3 + 2]);
            if (a != c) return Integer.compare(a, c);
            return Double.compare(Math.hypot(ic[p * 3] - hx, ic[p * 3 + 1] - hz), Math.hypot(ic[q * 3] - hx, ic[q * 3 + 1] - hz));
        });
        return l;
    }

    private void sendPackage() {
        CcConsoleBlockEntity b = be();
        if (b == null || pkgPicked.isEmpty() || !confirmed()) return;
        send(new CcBPayload(b.getBlockPos(), CcBPayload.PACKAGE, 0L, 0, 0, "", List.copyOf(pkgPicked)));
        pkgPicked.clear();
    }

    private void plan() {
        if (pkg) { sendPackage(); return; }
        CcConsoleBlockEntity b = be();
        CcTarget des = boxesEmpty() ? designated() : null;
        if (b == null || picked.size() < CcStrikePlannerBlockEntity.MIN_PADS || (!hasTarget() && des == null) || !confirmed()) return;
        int tx = des != null ? des.x() : parse(xBox), tz = des != null ? des.z() : parse(zBox);
        send(new CcBPayload(b.getBlockPos(), CcBPayload.STRIKE, jamFirst ? 1L : 0L, tx, tz, "", List.copyOf(picked)));
        picked.clear();
    }

    private void retarget() {
        CcConsoleBlockEntity b = be();
        if (b == null || selected == 0 || (!hasTarget() && nameBox.getValue().isEmpty()) || !confirmed()) return;
        send(new CcBPayload(b.getBlockPos(), CcBPayload.RETARGET, selected, parse(xBox), parse(zBox), nameBox.getValue(), List.of()));
    }

    private void watch() {
        CcConsoleBlockEntity b = be();
        if (b == null || selected == 0) return;
        send(CcBPayload.of(b.getBlockPos(), CcBPayload.CAM, selected, 0, 0));
    }

    private void nextRecon() {
        CcConsoleBlockEntity b = be();
        int[] ic = b != null ? b.intel() : new int[0];
        boolean server = ic.length >= 3;
        if (!server) {
            ReconMapPayload m = ReconView.map != null ? ReconView.map : ReconView.last;
            ic = m == null || m.icons() == null ? new int[0] : m.icons();
        }
        if (ic.length < 3 || xBox == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(P + "recon_none"), true); return; }
        int n = ic.length / 3;
        reconIndex = Math.floorMod(reconIndex, n);
        int x = ic[reconIndex * 3], z = ic[reconIndex * 3 + 1], k = Math.max(0, Math.min(ReconMapScreen.ICON_KEY.length - 1, ic[reconIndex * 3 + 2]));
        xBox.setValue(Integer.toString(x));
        zBox.setValue(Integer.toString(z));
        String owner = server ? b.intelName(reconIndex) : "";
        Component kind = Component.translatable("gui.wmd.recon.icon." + ReconMapScreen.ICON_KEY[k]);
        if (minecraft.player != null) minecraft.player.displayClientMessage(owner.isEmpty()
                ? Component.translatable(P + "recon_pick", kind, x, z, reconIndex + 1, n)
                : Component.translatable(P + "recon_pick_owner", kind, owner, x, z, reconIndex + 1, n), true);
        reconIndex++;
        confirmUntil = 0;
        updateButtons();
    }

    private List<CompoundTag> allRows() {
        CcConsoleBlockEntity b = be();
        List<CompoundTag> l = new ArrayList<>();
        if (b == null) return l;
        if (b instanceof CcStrikePlannerBlockEntity s) l.addAll(s.terminals());
        else for (Tag t : b.rows()) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }

    private List<CompoundTag> rowList() {
        List<CompoundTag> l = allRows();
        rowScroll = Math.max(0, Math.min(Math.max(0, l.size() - maxRows()), rowScroll));
        return l.subList(rowScroll, Math.min(l.size(), rowScroll + maxRows()));
    }

    private void rowScrollBar(GuiGraphics g, int x, int y, int total) {
        if (total <= maxRows()) return;
        int top = y + ROW_Y - 1, h = maxRows() * ROW_H, bx = x + PW - 7;
        g.fill(bx, top, bx + 3, top + h, 0xFF0A0D11);
        int th = Math.max(6, h * maxRows() / total), ty = top + (h - th) * rowScroll / Math.max(1, total - maxRows());
        g.fill(bx, ty, bx + 3, ty + th, WmdUi.BLUE);
    }

    private int rowTextW(int base) { return allRows().size() > maxRows() ? base - 5 : base; }

    private static long keyOf(CompoundTag c) { return c.contains("p") ? c.getLong("p") : c.getLong("id"); }

    private CompoundTag selectedRow() {
        for (CompoundTag c : allRows()) if (keyOf(c) == selected) return c;
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && pkg && be() instanceof CcStrikePlannerBlockEntity s) {
            int x = px(), y = py() + ROW_Y;
            List<Integer> order = pkgOrder();
            int shown = Math.min(maxRows(), Math.max(0, order.size() - pkgScroll));
            if (mx >= x + 4 && mx < x + PW - 4 && my >= y && my < y + shown * ROW_H) {
                int i = order.get(pkgScroll + (int) ((my - y) / ROW_H));
                int[] ic = s.intel();
                long k = CcBPayload.packXZ(ic[i * 3], ic[i * 3 + 1]);
                if (!pkgPicked.remove(k) && pkgPicked.size() < CcBPayload.MAX_PADS) pkgPicked.add(k);
                confirmUntil = 0;
                updateButtons();
                return true;
            }
        }
        if (button == 0 && be() != null && !pkg) {
            int x = px(), y = py() + ROW_Y;
            List<CompoundTag> rows = rowList();
            if (mx >= x + 4 && mx < x + PW - 4 && my >= y && my < y + rows.size() * ROW_H) {
                CompoundTag c = rows.get((int) ((my - y) / ROW_H));
                long k = keyOf(c);
                if (be() instanceof CcStrikePlannerBlockEntity) {
                    if (!picked.remove(k) && picked.size() < CcStrikePlannerBlockEntity.MAX_PADS) picked.add(k);
                } else {
                    selected = selected == k ? 0 : k;
                    if (selected != 0 && be() instanceof CcLaunchConsoleBlockEntity && c.getBoolean("ts") && xBox != null && xBox.getValue().isEmpty()) {
                        xBox.setValue(Integer.toString(c.getInt("tx")));
                        zBox.setValue(Integer.toString(c.getInt("tz")));
                    }
                    if (selected != 0 && be() instanceof CcRetargetConsoleBlockEntity && xBox != null) {
                        xBox.setValue(Integer.toString(c.getInt("tx")));
                        zBox.setValue(Integer.toString(c.getInt("tz")));
                    }
                }
                confirmUntil = 0;
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (pkg && mx >= px() && mx < px() + PW && my >= py() && my < py() + panelHeight()) {
            pkgScroll = Math.max(0, Math.min(Math.max(0, pkgOrder().size() - maxRows()), pkgScroll - (int) Math.signum(sy)));
            return true;
        }
        if (!pkg && be() != null && mx >= px() && mx < px() + PW && my >= py() && my < py() + panelHeight() && allRows().size() > maxRows()) {
            rowScroll = Math.max(0, Math.min(allRows().size() - maxRows(), rowScroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] { xBox, zBox, nameBox })
                if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (selected != 0 && selectedRow() == null && !(be() instanceof CcStrikePlannerBlockEntity)) selected = 0;
        if (be() instanceof CcStrikePlannerBlockEntity s) {
            Set<Long> live = new java.util.HashSet<>();
            for (CompoundTag c : allRows()) live.add(keyOf(c));
            picked.retainAll(live);
            Set<Long> targets = new java.util.HashSet<>();
            int[] ic = s.intel();
            for (int i = 0; i + 2 < ic.length; i += 3) targets.add(CcBPayload.packXZ(ic[i], ic[i + 1]));
            pkgPicked.retainAll(targets);
        }
        updateButtons();
    }

    private void updateButtons() {
        CcConsoleBlockEntity b = be();
        if (b == null || goButton == null) return;
        boolean online = b.isOnline(), confirming = System.currentTimeMillis() < confirmUntil;
        if (desButton != null) {
            CcTarget t = designated();
            desButton.active = t != null;
            desButton.setMessage(t == null ? Component.translatable(P + "use_des_none") : Component.translatable(P + "use_des_at", t.x(), t.z()));
        }
        CompoundTag sel = selectedRow();
        if (b instanceof CcLaunchConsoleBlockEntity) {
            boolean ok = online && sel != null && sel.getBoolean("in") && !sel.getBoolean("dim") && hasTarget();
            aimButton.active = ok;
            goButton.active = ok;
            if (sel != null && sel.getBoolean("asat")) {
                boolean live = online && sel.getBoolean("in") && !sel.getBoolean("dim");
                aimButton.active = live;
                goButton.active = live && sel.getInt("as") > 0;
            }
            aimButton.setMessage(Component.translatable(sel != null && sel.getBoolean("asat") ? P + "asat_next" : P + "aim"));
            boolean cr = sel != null && sel.getBoolean("cruise");
            goButton.setMessage(Component.translatable(confirming ? (cr ? P + "confirm_cruise" : P + "confirm_launch") : cr ? P + "cruise_fire" : P + "launch"));
        } else if (b instanceof CcStrikePlannerBlockEntity) {
            if (pkg) {
                goButton.active = online && !pkgPicked.isEmpty();
                goButton.setMessage(confirming ? Component.translatable(P + "confirm_package", pkgPicked.size()) : Component.translatable(P + "package_go", pkgPicked.size()));
            } else {
                goButton.active = online && picked.size() >= CcStrikePlannerBlockEntity.MIN_PADS && (hasTarget() || (boxesEmpty() && designated() != null));
                goButton.setMessage(confirming ? Component.translatable(P + "confirm_plan", picked.size()) : Component.translatable(P + "plan"));
            }
            cancelButton.active = online;
            for (net.minecraft.client.gui.components.AbstractWidget w : new net.minecraft.client.gui.components.AbstractWidget[] { xBox, zBox, reconButton, desButton })
                if (w != null) w.visible = !pkg;
            if (pkgButton != null) pkgButton.setMessage(Component.translatable(pkg ? P + "view_pads" : P + "view_package"));
            if (counterButton != null) {
                CcTarget ct = counterTarget();
                boolean armed = counterArmed(), conf = System.currentTimeMillis() < counterConfirmUntil;
                counterButton.visible = ct != null;
                counterButton.active = online && ct != null && readyPads() > 0;
                counterButton.setMessage(ct == null ? Component.empty() : armed || conf
                        ? Component.translatable(P + "counter_confirm").withStyle(net.minecraft.ChatFormatting.RED)
                        : Component.translatable(P + "counter", readyPads()));
            }
        } else if (b instanceof CcRetargetConsoleBlockEntity) {
            boolean used = sel != null && (sel.getInt("uf") & Flight.U_RETARGETED) != 0;
            goButton.active = online && sel != null && sel.getBoolean("in") && sel.getBoolean("cov") && !used && (hasTarget() || (nameBox != null && !nameBox.getValue().isEmpty()));
            goButton.setMessage(Component.translatable(used ? "gui.wmd.uplink.used" : confirming ? P + "confirm_retarget" : P + "retarget"));
        } else if (b instanceof CcMissileCamBlockEntity) {
            goButton.active = online && sel != null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        CcConsoleBlockEntity b = be();
        int ph = panelHeight();
        if (b == null || ph <= 0) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, ph);
        drawSelectedTab(g);
        fit(g, Component.translatable(b.kind().langKey()), cx, y + 6, TEXT);
        WmdUi.divider(g, x + 6, y + 15, PW - 12);
        Component state = !b.isOnline() ? Component.translatable("gui.wmd.cc.offline")
                : b.global() ? Component.translatable(P + "reach_global") : Component.translatable(P + "reach", String.format("%,d", Balance.CC_REACH));
        fit(g, state, cx, y + 17, b.isOnline() ? GOOD : BAD);
        long now = b.getLevel() != null ? b.getLevel().getGameTime() : 0L;
        if (b instanceof CcLaunchConsoleBlockEntity) launchPanel(g, x, y, cx);
        else if (b instanceof CcStrikePlannerBlockEntity s) strikePanel(g, s, x, y, cx, now);
        else if (b instanceof CcRetargetConsoleBlockEntity) flightPanel(g, x, y, cx, now, true);
        else if (b instanceof CcMissileCamBlockEntity) flightPanel(g, x, y, cx, now, false);
    }

    private static int padColour(CompoundTag c) {
        if (!c.getBoolean("in") || c.getBoolean("dim")) return 0xFF6A6A6A;
        if (c.getBoolean("cruise")) {
            int st = c.getInt("st");
            if (st == CruiseLauncherBlockEntity.S_READY) return 0xFF35C24A;
            if (st == CruiseLauncherBlockEntity.S_RAISING || st == CruiseLauncherBlockEntity.S_FIRING || st == CruiseLauncherBlockEntity.S_LOWERING) return 0xFFE0A030;
            return 0xFFD04030;
        }
        if (c.getBoolean("gun")) {
            int k = CcScreenArtillery.gunKind(c);
            return k == WmdUi.K_GOOD ? 0xFF35C24A : k == WmdUi.K_WARN ? 0xFFE0A030 : 0xFFD04030;
        }
        int h = c.getInt("h"), st = c.getInt("s");
        if (h == LaunchTerminalBlockEntity.OK && st == LaunchTerminalBlockEntity.Stage.READY.ordinal()) return 0xFF35C24A;
        if (st != LaunchTerminalBlockEntity.Stage.IDLE.ordinal() && st != LaunchTerminalBlockEntity.Stage.READY.ordinal()) return 0xFFE0A030;
        return 0xFFD04030;
    }

    private static int padKind(CompoundTag c) {
        int col = padColour(c);
        return col == 0xFF35C24A ? WmdUi.K_GOOD : col == 0xFFE0A030 ? WmdUi.K_WARN : col == 0xFFD04030 ? WmdUi.K_BAD : WmdUi.K_IDLE;
    }

    private void rowBg(GuiGraphics g, int x, int ly, boolean on, boolean clickable) {
        if (on) CcScreen.selRow(g, x + 4, ly - 1, x + PW - 4, ly + ROW_H - 1);
        else if (clickable && WmdUi.over(mouseX, mouseY, x + 4, ly, PW - 8, ROW_H)) g.fill(x + 4, ly - 1, x + PW - 4, ly + ROW_H - 1, CcScreen.ROW_HOVER);
    }

    private Component padRow(CompoundTag c) {
        BlockPos p = BlockPos.of(c.getLong("p"));
        if (c.getBoolean("cruise"))
            return Component.translatable(P + "cruise_row", p.getX(), p.getZ(), c.getInt("n"), CruiseLauncherBlockEntity.CANISTERS);
        if (c.getBoolean("gun")) {
            int gid = c.getInt("g"), t = Artillery.canFire(gid, Artillery.clampType(c.getInt("pk"))) ? Artillery.clampType(c.getInt("pk")) : Artillery.HE;
            return Component.translatable(P + "gun_row", Component.translatable(Artillery.gunNameKey(gid)), p.getX(), p.getZ(),
                    Component.translatable("gui.wmd.art_console.t." + Artillery.TYPES[t]), c.getInt("a" + t));
        }
        String w = c.getString("w");
        Component missile = w.isEmpty() ? Component.translatable(P + "no_missile") : c.getBoolean("sat") ? Component.translatable(P + "sat_rocket")
                : Component.translatable(P + "missile", c.getInt("m"), Component.translatable("warhead.wmd." + w));
        return Component.translatable(P + "pad_row", c.getInt("t"), p.getX(), p.getY(), p.getZ(), missile);
    }

    private Component padStatus(CompoundTag c) {
        if (c.getBoolean("dim")) return Component.translatable(P + "other_dim");
        if (!c.getBoolean("in")) return Component.translatable(P + "out_of_reach");
        if (c.getBoolean("cruise")) return Component.translatable("gui.wmd.cruise.st." + c.getInt("st"));
        return Component.translatable("screen.wmd.terminal.hint." + c.getInt("h"));
    }

    private void padRows(GuiGraphics g, int x, int y, boolean ticks) {
        List<CompoundTag> rows = rowList();
        if (rows.isEmpty()) { fit(g, Component.translatable(P + "no_pads"), x + PW / 2, y + ROW_Y + 2, GREY); return; }
        int ly = y + ROW_Y;
        for (CompoundTag c : rows) {
            long k = keyOf(c);
            boolean on = ticks ? picked.contains(k) : k == selected;
            rowBg(g, x, ly, on, true);
            if (ticks && !on) CcScreen.emptyLed(g, x + 7, ly + 1);
            else WmdUi.led(g, x + 7, ly + 1, padKind(c), false);
            if (ticks && on) g.fill(x + 9, ly + 3, x + 10, ly + 4, 0xFFFFFFFF);
            drawLeft(g, padRow(c), x + 15, ly, rowTextW(PW - 20), c.getBoolean("in") && !c.getBoolean("dim") ? TEXT : GREY);
            ly += ROW_H;
        }
        rowScrollBar(g, x, y, allRows().size());
    }

    private void coordLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, "X", x + 7, y + 3, TEXT, false);
        g.drawString(font, "Z", x + 73, y + 3, TEXT, false);
    }

    private void launchPanel(GuiGraphics g, int x, int y, int cx) {
        fit(g, Component.translatable(P + "pads"), cx, y + 28, TEXT);
        padRows(g, x, y, false);
        coordLabels(g, x, y + 123);
        CompoundTag sel = selectedRow();
        if (sel == null) { fit(g, Component.translatable(P + "pick_pad"), cx, y + 162, GREY); return; }
        fit(g, padStatus(sel), cx, y + 162, padColour(sel) == 0xFF35C24A ? GOOD : sel.getBoolean("in") ? BAD : GREY);
        if (sel.getBoolean("asat")) { fit(g, asatLine(sel), cx, y + 173, BLUE); return; }
        if (sel.getBoolean("cruise")) {
            fit(g, sel.getBoolean("ts") ? Component.translatable(P + "cruise_target", sel.getInt("tx"), sel.getInt("tz"), sel.getInt("rl"), sel.getInt("rg"),
                    sel.getInt("eta") > 0 ? OrbitMath.mmss(sel.getInt("eta") * 50L) : "-") : Component.translatable(P + "cruise_no_target"), cx, y + 173, TEXT);
            return;
        }
        Component second = sel.getBoolean("ts")
                ? Component.translatable(P + "pad_target", sel.getInt("tx"), sel.getInt("tz"), sel.getInt("eta") > 0 ? OrbitMath.mmss(sel.getInt("eta") * 50L) : "-")
                : Component.translatable("screen.wmd.terminal.hint.2");
        fit(g, second, cx, y + 173, TEXT);
    }

    private Component asatLine(CompoundTag sel) {
        int id = sel.getInt("as");
        OrbitMath.Sat s = null;
        for (OrbitMath.Sat o : OrbitView.sats()) if (o.id() == id) s = o;
        if (id <= 0 || s == null) return Component.translatable(P + "asat_pick");
        BlockPos p = BlockPos.of(sel.getLong("p"));
        long now = OrbitView.now(), over = OrbitMath.overheadLeft(s, p.getX() + 0.5, p.getZ() + 0.5, now);
        return over > 0 ? Component.translatable(P + "asat_over", id, s.ownerName(), OrbitMath.mmss(over))
                : Component.translatable(P + "asat_next_pass", id, s.ownerName(), OrbitMath.mmss(OrbitMath.nextPass(s, p.getX() + 0.5, p.getZ() + 0.5, now)));
    }

    private void packageRows(GuiGraphics g, CcStrikePlannerBlockEntity s, int x, int y, int cx) {
        fit(g, Component.translatable(P + "package_pick", pkgPicked.size(), CcBPayload.MAX_PADS), cx, y + 28, TEXT);
        List<Integer> order = pkgOrder();
        int[] ic = s.intel();
        if (order.isEmpty()) { fit(g, Component.translatable(P + "package_none"), cx, y + ROW_Y + 2, GREY); fit(g, Component.translatable(P + "package_hint"), cx, y + ROW_Y + 14, GREY); return; }
        pkgScroll = Math.max(0, Math.min(Math.max(0, order.size() - maxRows()), pkgScroll));
        int ly = y + ROW_Y;
        for (int r = pkgScroll; r < Math.min(order.size(), pkgScroll + maxRows()); r++) {
            int i = order.get(r), kind = Math.max(0, Math.min(ReconMapScreen.ICON_KEY.length - 1, ic[i * 3 + 2]));
            boolean on = pkgPicked.contains(CcBPayload.packXZ(ic[i * 3], ic[i * 3 + 1]));
            rowBg(g, x, ly, on, true);
            g.fill(x + 7, ly + 1, x + 12, ly + 6, 0xFF0A0D11);
            g.fill(x + 8, ly + 2, x + 11, ly + 5, ReconMapScreen.ICON_COL[kind]);
            if (on) g.fill(x + 9, ly + 3, x + 10, ly + 4, 0xFFFFFFFF);
            String owner = s.intelName(i);
            Component row = Component.translatable(P + "package_row", Component.translatable("gui.wmd.recon.icon." + ReconMapScreen.ICON_KEY[kind]),
                    owner.isEmpty() ? Component.translatable("item.wmd.intel.unknown") : Component.literal(owner), ic[i * 3], ic[i * 3 + 1]);
            drawLeft(g, row, x + 15, ly, PW - 20, TEXT);
            ly += ROW_H;
        }
        if (order.size() > maxRows()) fit(g, Component.translatable("gui.wmd.cc.site_scroll", pkgScroll + 1, Math.min(order.size(), pkgScroll + maxRows()), order.size()), cx, y + 125, GREY);
        else fit(g, Component.translatable(P + "package_rule"), cx, y + 125, GREY);
    }

    private void strikePanel(GuiGraphics g, CcStrikePlannerBlockEntity s, int x, int y, int cx, long now) {
        if (pkg) packageRows(g, s, x, y, cx);
        else {
            fit(g, Component.translatable(P + "pads_pick", CcStrikePlannerBlockEntity.MIN_PADS, CcStrikePlannerBlockEntity.MAX_PADS, picked.size()), cx, y + 28, TEXT);
            padRows(g, x, y, true);
            coordLabels(g, x, y + 123);
        }
        List<CompoundTag> shots = s.shots();
        long impact = s.getLevel() != null ? s.nextImpact(s.getLevel()) : -1;
        fit(g, impact >= 0 ? Component.translatable(P + "impact_in", OrbitMath.mmss((impact - now) * 50L)) : Component.translatable(P + "no_plan"), cx, y + 161, impact >= 0 ? BAD : GREY);
        WmdUi.divider(g, x + 5, y + 171, PW - 10);
        int ly = y + 174;
        for (int i = 0; i < Math.min(6, shots.size()); i++) {
            CompoundTag c = shots.get(i);
            BlockPos p = BlockPos.of(c.getLong("p"));
            int st = c.getInt("st");
            long left = c.getLong("fire") - now;
            Component when = st == CcStrikeData.FIRED ? Component.translatable(P + "shot_fired")
                    : st == CcStrikeData.SKIPPED ? Component.translatable(P + "shot_skipped")
                    : Component.translatable(P + "shot_tminus", OrbitMath.mmss(Math.max(0L, left) * 50L));
            drawLeft(g, Component.translatable(P + "shot_row", p.getX(), p.getZ(), when), x + 8, ly, PW - 16,
                    st == CcStrikeData.FIRED ? GOOD : st == CcStrikeData.SKIPPED ? BAD : TEXT);
            ly += 9;
        }
    }

    private void flightPanel(GuiGraphics g, int x, int y, int cx, long now, boolean retarget) {
        fit(g, Component.translatable(retarget ? P + "flights" : P + "cams"), cx, y + 28, TEXT);
        List<CompoundTag> rows = rowList();
        int ly = y + ROW_Y;
        if (rows.isEmpty()) fit(g, Component.translatable(retarget ? P + "no_flights" : P + "no_cams"), cx, ly + 2, GREY);
        for (CompoundTag c : rows) {
            boolean on = keyOf(c) == selected;
            rowBg(g, x, ly, on, true);
            Component row = c.contains("dr")
                    ? Component.translatable(P + "cam_drone", c.getInt("dr"), c.getInt("t"), c.getInt("tx"), c.getInt("tz"), c.getInt("bat"))
                    : c.contains("p")
                    ? Component.translatable(P + "cam_pad", c.getInt("t"), Component.translatable("warhead.wmd." + c.getString("w")), c.getInt("tx"), c.getInt("tz"),
                            Component.translatable(P + "cam_pad." + c.getString("st")))
                    : Component.translatable("gui.wmd.uplink.row", c.getLong("id"), c.getInt("t"), Component.translatable("warhead.wmd." + c.getString("w")),
                    c.getInt("tx"), c.getInt("tz"), Math.max(0L, (c.getLong("end") - now) / 20));
            int col = !retarget ? TEXT : !c.getBoolean("in") ? GREY : (c.getInt("uf") & Flight.U_RETARGETED) != 0 ? AMBER : TEXT;
            drawLeft(g, row, x + 8, ly, rowTextW(PW - 16), col);
            ly += ROW_H;
        }
        rowScrollBar(g, x, y, allRows().size());
        if (retarget) {
            coordLabels(g, x, y + 103);
            CompoundTag sel = selectedRow();
            Component hint = sel == null ? Component.translatable("gui.wmd.uplink.pick")
                    : !sel.getBoolean("cov") ? Component.translatable("gui.wmd.uplink.no_coverage")
                    : !sel.getBoolean("in") ? Component.translatable(P + "out_of_reach")
                    : (sel.getInt("uf") & Flight.U_RETARGETED) != 0 ? Component.translatable("message.wmd.uplink.used", sel.getLong("id"))
                    : sel.contains("sk") ? Component.translatable(sel.getString("sk").equals("hyper") ? P + "retarget_hint_hyper" : P + "retarget_hint_seeker")
                    : Component.translatable(P + "retarget_hint");
            fit(g, hint, cx, y + 159, sel != null && sel.getBoolean("cov") && sel.getBoolean("in") ? BLUE : GREY);
        } else {
            fit(g, Component.translatable(P + "cam_hint"), cx, y + 123, BLUE);
        }
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) {
        int maxW = PW - 10, w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void drawLeft(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, x, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(x, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }
}
