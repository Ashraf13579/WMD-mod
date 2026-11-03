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
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CcScreenArtillery extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 184, PH = 248;
    private static final int TEXT = WmdUi.TEXT, GREY = WmdUi.TEXT_MUTED, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, BLUE = WmdUi.BLUE_LIGHT, AMBER = WmdUi.WARN;
    private static final String P = "gui.wmd.art_console.";
    private static final int ROW_Y = 45, ROW_H = 10, MAX_ROWS = 8, BOX_Y = 128, DES_Y = 146, TYPE_Y = 166, ROUND_Y = 184, FIRE_Y = 204, HINT_Y = 226;
    private static final long CONFIRM_MS = 5000L;

    private static final int[] TYPE_COL = { 0xFFE05050, 0xFF50C060, 0xFFE0B040, 0xFF5090E0, 0xFFB8C2CC };

    private EditBox xBox, zBox;
    private String xText = "", zText = "";
    private Button fireBtn, ceaseBtn, desBtn, reconBtn;
    private final Button[] typeBtn = new Button[Artillery.TYPES.length];
    private final Set<Long> picked = new LinkedHashSet<>();
    private int type = Artillery.HE, roundIdx = 0, charge = 0, reconIndex;
    private long confirmUntil;
    private int mouseX, mouseY;

    public CcScreenArtillery(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private CcArtilleryConsoleBlockEntity be() { return menu.machine() instanceof CcArtilleryConsoleBlockEntity c ? c : null; }

    private int px() { return leftPos - PW - 2; }

    private int py() { return Math.max(4, Math.min(topPos, height - PH - 4)); }

    @Override
    protected int sidePanelWidth() { return be() != null ? PW : 0; }

    private boolean owner() {
        CcArtilleryConsoleBlockEntity b = be();
        return b != null && minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(b.getOwner());
    }

    @Override
    protected void init() {
        super.init();
        CcArtilleryConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py();
        addActionTab("icon_lock", Component.translatable("gui.wmd.kit.tab.lock"), () -> minecraft.setScreen(new SecurityScreen(this, b.getBlockPos())));
        xBox = box(x + 16, y + BOX_Y, 52, xText);
        zBox = box(x + 82, y + BOX_Y, 52, zText);
        reconBtn = addRenderableWidget(new WmdUi.KitButton(x + 138, y + BOX_Y - 1, PW - 144, 15, Component.translatable("gui.wmd.cc.b.recon"), w -> nextRecon()));
        desBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + DES_Y, PW - 12, 15, Component.empty(), w -> useDesignated()));
        for (int t = 0; t < typeBtn.length; t++) {
            final int k = t;
            typeBtn[t] = addRenderableWidget(new WmdUi.KitButton(x + 6 + t * 43, y + TYPE_Y, 41, 14, Component.translatable(P + "t." + Artillery.TYPES[t]),
                    w -> { type = k; confirmUntil = 0; updateButtons(); }));
            if (typeBtn.length > 4) { typeBtn[t].setX(x + 6 + t * 34); typeBtn[t].setWidth(32); }
        }
        addRenderableWidget(new WmdUi.KitButton(x + 6, y + ROUND_Y, 12, 14, Component.literal("<"), w -> { roundIdx = Math.floorMod(roundIdx - 1, CcArtilleryConsoleBlockEntity.ROUNDS.length); confirmUntil = 0; }));
        addRenderableWidget(new WmdUi.KitButton(x + 76, y + ROUND_Y, 12, 14, Component.literal(">"), w -> { roundIdx = Math.floorMod(roundIdx + 1, CcArtilleryConsoleBlockEntity.ROUNDS.length); confirmUntil = 0; }));
        addRenderableWidget(new WmdUi.KitButton(x + 96, y + ROUND_Y, 12, 14, Component.literal("<"), w -> { charge = Math.floorMod(charge - 1, Balance.HOWITZER_CHARGES + 1); confirmUntil = 0; }));
        addRenderableWidget(new WmdUi.KitButton(x + 166, y + ROUND_Y, 12, 14, Component.literal(">"), w -> { charge = Math.floorMod(charge + 1, Balance.HOWITZER_CHARGES + 1); confirmUntil = 0; }));
        fireBtn = addRenderableWidget(new WmdUi.KitButton(x + 6, y + FIRE_Y, 112, 16, Component.translatable(P + "fire"), w -> fire(), true));
        ceaseBtn = addRenderableWidget(new WmdUi.KitButton(x + 122, y + FIRE_Y, PW - 128, 16, Component.translatable(P + "cease"), w -> cease()));
        updateButtons();
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox b = new EditBox(font, x, y, w, 14, Component.empty());
        b.setMaxLength(9);
        b.setFilter(s -> s.matches("-?\\d*"));
        b.setValue(value);
        b.setResponder(s -> confirmUntil = 0);
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void rebuildWidgets() {
        if (xBox != null) { xText = xBox.getValue(); zText = zBox.getValue(); }
        super.rebuildWidgets();
    }

    private static int parse(EditBox b) {
        try { return b == null ? 0 : Integer.parseInt(b.getValue()); } catch (NumberFormatException e) { return 0; }
    }

    private boolean hasTarget() {
        return xBox != null && !xBox.getValue().isEmpty() && !xBox.getValue().equals("-") && !zBox.getValue().isEmpty() && !zBox.getValue().equals("-");
    }

    private CcTarget designated() {
        CcArtilleryConsoleBlockEntity b = be();
        CcBuildingBlockEntity bb = b != null ? b.building() : null;
        return bb != null ? bb.designated() : null;
    }

    private void useDesignated() {
        CcTarget t = designated();
        if (t == null || xBox == null) return;
        xBox.setValue(Integer.toString(t.x()));
        zBox.setValue(Integer.toString(t.z()));
        confirmUntil = 0;
        updateButtons();
    }

    private void nextRecon() {
        CcArtilleryConsoleBlockEntity b = be();
        int[] ic = b != null ? b.intel() : new int[0];
        if (ic.length < 3 || xBox == null) { if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("gui.wmd.cc.b.recon_none"), true); return; }
        int n = ic.length / 3;
        reconIndex = Math.floorMod(reconIndex, n);
        int x = ic[reconIndex * 3], z = ic[reconIndex * 3 + 1], k = Math.max(0, Math.min(ReconMapScreen.ICON_KEY.length - 1, ic[reconIndex * 3 + 2]));
        xBox.setValue(Integer.toString(x));
        zBox.setValue(Integer.toString(z));
        String owner = b.intelName(reconIndex);
        Component kind = Component.translatable("gui.wmd.recon.icon." + ReconMapScreen.ICON_KEY[k]);
        if (minecraft.player != null) minecraft.player.displayClientMessage(owner.isEmpty()
                ? Component.translatable("gui.wmd.cc.b.recon_pick", kind, x, z, reconIndex + 1, n)
                : Component.translatable("gui.wmd.cc.b.recon_pick_owner", kind, owner, x, z, reconIndex + 1, n), true);
        reconIndex++;
        confirmUntil = 0;
        updateButtons();
    }

    private void fire() {
        CcArtilleryConsoleBlockEntity b = be();
        if (b == null || picked.isEmpty() || !hasTarget() || !owner()) return;
        long now = System.currentTimeMillis();
        if (now >= confirmUntil) { confirmUntil = now + CONFIRM_MS; updateButtons(); return; }
        long arg = (type & 0xFF) | ((long) (CcArtilleryConsoleBlockEntity.ROUNDS[roundIdx] & 0xFF) << 8) | ((long) (charge & 0xFF) << 16);
        PacketDistributor.sendToServer(new CcBPayload(b.getBlockPos(), CcBPayload.ART_FIRE, arg, parse(xBox), parse(zBox), "", List.copyOf(picked)));
        confirmUntil = 0;
        updateButtons();
    }

    private void cease() {
        CcArtilleryConsoleBlockEntity b = be();
        if (b == null || picked.isEmpty()) return;
        PacketDistributor.sendToServer(new CcBPayload(b.getBlockPos(), CcBPayload.ART_CEASE, 0L, 0, 0, "", List.copyOf(picked)));
    }

    private List<CompoundTag> rows() {
        CcArtilleryConsoleBlockEntity b = be();
        List<CompoundTag> l = b == null ? new ArrayList<>() : b.gunRows();
        return l.size() > MAX_ROWS ? l.subList(0, MAX_ROWS) : l;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && be() != null) {
            int x = px(), y = py() + ROW_Y;
            List<CompoundTag> rows = rows();
            if (mx >= x + 4 && mx < x + PW - 4 && my >= y && my < y + rows.size() * ROW_H) {
                long k = rows.get((int) ((my - y) / ROW_H)).getLong("p");
                if (!picked.remove(k) && picked.size() < CcBPayload.MAX_PADS) picked.add(k);
                confirmUntil = 0;
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) for (EditBox box : new EditBox[] { xBox, zBox })
            if (box != null && box.isFocused() && (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        Set<Long> live = new java.util.HashSet<>();
        for (CompoundTag c : rows()) live.add(c.getLong("p"));
        picked.retainAll(live);
        updateButtons();
    }

    private void updateButtons() {
        CcArtilleryConsoleBlockEntity b = be();
        if (b == null || fireBtn == null) return;
        boolean online = b.isOnline() && owner();
        boolean confirming = System.currentTimeMillis() < confirmUntil;
        fireBtn.active = online && !picked.isEmpty() && hasTarget();
        fireBtn.setMessage(Component.translatable(confirming ? P + "confirm" : P + "fire", picked.size()));
        ceaseBtn.active = online && !picked.isEmpty();
        CcTarget t = designated();
        desBtn.active = t != null;
        desBtn.setMessage(t == null ? Component.translatable("gui.wmd.cc.b.use_des_none") : Component.translatable("gui.wmd.cc.b.use_des_at", t.x(), t.z()));

        boolean[] can = fireable();
        if (!can[Artillery.clampType(type)]) type = Artillery.HE;
        for (int i = 0; i < typeBtn.length; i++) typeBtn[i].active = online && can[i];
    }

    private boolean[] fireable() {
        boolean[] can = new boolean[typeBtn.length];
        boolean any = false;
        for (CompoundTag c : rows()) {
            if (!picked.contains(c.getLong("p"))) continue;
            any = true;
            for (int t = 0; t < can.length; t++) if (Artillery.canFire(c.getInt("g"), t)) can[t] = true;
        }
        if (!any) java.util.Arrays.fill(can, true);
        can[Artillery.HE] = true;
        return can;
    }

    static int gunKind(CompoundTag c) {
        if (!c.getBoolean("in") || c.getBoolean("dim")) return WmdUi.K_IDLE;
        int st = c.getInt("st");
        if (c.getInt("m") != 0 || st == ArtilleryGunBlockEntity.S_FIRING || st == ArtilleryGunBlockEntity.S_LAYING) return WmdUi.K_WARN;
        if (st == ArtilleryGunBlockEntity.S_READY || st == ArtilleryGunBlockEntity.S_RELOADING || st == ArtilleryGunBlockEntity.S_NO_TARGET
                || st == ArtilleryGunBlockEntity.S_FIRED) return WmdUi.K_GOOD;
        return WmdUi.K_BAD;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        super.renderBg(g, partialTick, mx, my);
        CcArtilleryConsoleBlockEntity b = be();
        if (b == null) return;
        int x = px(), y = py(), cx = x + PW / 2;
        WmdUi.panel(g, x, y, PW, PH);
        drawSelectedTab(g);
        WmdUi.header(g, font, x, y, PW, new ItemStack(b.getBlockState().getBlock().asItem()), Component.translatable(b.kind().langKey()),
                Component.translatable(b.isOnline() ? "gui.wmd.cc.online" : "gui.wmd.cc.offline"), b.isOnline() ? WmdUi.K_GOOD : WmdUi.K_BAD, !b.isOnline());
        Component reach = !b.isOnline() ? Component.translatable("gui.wmd.cc.offline")
                : b.global() ? Component.translatable("gui.wmd.cc.b.reach_global") : Component.translatable("gui.wmd.cc.b.reach", String.format("%,d", Balance.CC_REACH));
        fit(g, reach, cx, y + 24, b.isOnline() ? GOOD : BAD);
        fit(g, Component.translatable(P + "guns", picked.size(), CcBPayload.MAX_PADS), cx, y + 34, TEXT);

        List<CompoundTag> rows = rows();
        int ly = y + ROW_Y;
        if (rows.isEmpty()) fit(g, Component.translatable(P + "no_guns"), cx, ly + 2, GREY);
        for (CompoundTag c : rows) {
            long k = c.getLong("p");
            boolean on = picked.contains(k);
            if (on) CcScreen.selRow(g, x + 4, ly - 1, x + PW - 4, ly + ROW_H - 1);
            else if (WmdUi.over(mouseX, mouseY, x + 4, ly, PW - 8, ROW_H)) g.fill(x + 4, ly - 1, x + PW - 4, ly + ROW_H - 1, CcScreen.ROW_HOVER);
            if (!on) CcScreen.emptyLed(g, x + 7, ly + 1);
            else WmdUi.led(g, x + 7, ly + 1, gunKind(c), false);
            BlockPos p = BlockPos.of(k);
            int gid = c.getInt("g");
            boolean can = Artillery.canFire(gid, type);
            Component row = Component.translatable(P + "row", Component.translatable(Artillery.gunNameKey(gid)), p.getX(), p.getZ(), c.getInt("a" + type),
                    Component.translatable(P + "t." + Artillery.TYPES[type]),
                    can ? Component.translatable("gui.wmd.art.st." + c.getInt("st")) : Component.translatable(P + "cant_type"));
            drawLeft(g, row, x + 15, ly, PW - 20, c.getBoolean("in") && !c.getBoolean("dim") && can ? TEXT : GREY);
            ly += ROW_H;
        }

        g.drawString(font, "X", x + 7, y + BOX_Y + 3, TEXT, false);
        g.drawString(font, "Z", x + 73, y + BOX_Y + 3, TEXT, false);

        for (int t = 0; t < typeBtn.length; t++) {
            int bx = x + 6 + t * 43;
            if (typeBtn.length > 4) {
                bx = x + 6 + t * 34;
                g.fill(bx + 2, y + TYPE_Y + 15, bx + 30, y + TYPE_Y + 16, t == type ? TYPE_COL[t % TYPE_COL.length] : 0x40FFFFFF & TYPE_COL[t % TYPE_COL.length]);
                continue;
            }
            g.fill(bx + 2, y + TYPE_Y + 15, bx + 39, y + TYPE_Y + 16, t == type ? TYPE_COL[t] : 0x40FFFFFF & TYPE_COL[t]);
        }
        int r = CcArtilleryConsoleBlockEntity.ROUNDS[roundIdx];
        centre(g, r <= 0 ? Component.translatable(P + "rounds_all") : Component.translatable(P + "rounds", r), x + 47, y + ROUND_Y + 3, 54, TEXT);
        centre(g, charge <= 0 ? Component.translatable(P + "charge_auto") : Component.translatable(P + "charge", charge), x + 137, y + ROUND_Y + 3, 54, TEXT);

        Component hint;
        int col = GREY;
        if (picked.isEmpty()) hint = Component.translatable(P + "pick");
        else if (!hasTarget()) hint = Component.translatable(P + "no_target");
        else {
            int in = 0;
            double tx = parse(xBox) + 0.5, tz = parse(zBox) + 0.5;
            for (CompoundTag c : rows) {
                if (!picked.contains(c.getLong("p")) || !Artillery.canFire(c.getInt("g"), type)) continue;
                BlockPos p = BlockPos.of(c.getLong("p"));
                int gi = Math.max(0, Math.min(Artillery.GUNS.length - 1, c.getInt("g")));
                double d = Math.hypot(tx - p.getX() - 0.5, tz - p.getZ() - 0.5);
                if (c.getInt("g") == Artillery.SUPER_GUN) { if (d >= Balance.SUPER_GUN_MIN && d <= Balance.SUPER_GUN_RANGE) in++; continue; }
                if (d >= Artillery.minRange(gi) && d <= Artillery.range(gi)) in++;
            }
            int skip = 0;
            for (CompoundTag c : rows) if (picked.contains(c.getLong("p")) && !Artillery.canFire(c.getInt("g"), type)) skip++;
            hint = skip > 0 ? Component.translatable(P + "in_range_skip", in, picked.size(), skip) : Component.translatable(P + "in_range", in, picked.size());
            col = in == picked.size() && skip == 0 ? BLUE : in > 0 ? AMBER : BAD;
        }
        if (System.currentTimeMillis() < confirmUntil) { hint = Component.translatable(P + "confirm_hint"); col = BAD; }
        fit(g, hint, cx, y + HINT_Y, col);
        fit(g, Component.translatable(P + "lamps"), cx, y + HINT_Y + 11, GREY);
    }

    private void centre(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        int w = font.width(text);
        if (w <= maxW) { g.drawString(font, text, cx - w / 2, y, colour, false); return; }
        float s = maxW / (float) w;
        g.pose().pushPose();
        g.pose().translate(cx - maxW / 2.0F, y + (1.0F - s) * 4.0F, 0.0F);
        g.pose().scale(s, s, 1.0F);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void fit(GuiGraphics g, Component text, int cx, int y, int colour) { centre(g, text, cx, y, PW - 10, colour); }

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
