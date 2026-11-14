package com.ashraf.wmd;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public class AirframeScreen extends Screen {

    static final int PW = 320, PH = 214, VIEW_X = 6, VIEW_Y = 26, VIEW_W = 186, VIEW_H = 162, SIDE_X = 198, SIDE_W = 116;
    static final int ROW_H = 10, LIST_Y_REC = 90, LIST_Y_CMP = 62, LIST_END = 176, FOOT_Y = 194;
    static final int SLIDER_W = 6, SIDE_END = LIST_END + 10, RELINK_H = 13;
    private static final String P = "gui.wmd.airframe.";

    private CompoundTag t;
    private AirframeView view;
    private UUID ship;
    private int mode, base;
    private float yaw = 135.0F, pitch = 28.0F, zoom = 1.0F;
    private boolean dragView, dragSlice;
    private int filter, hover = -1, sel = -1, scroll, age, confirmUntil = -1;
    private final List<Row> rows = new ArrayList<>();
    private int missing, broken;

    private record Row(Component label, int colour, Item need, int count, Component right, BitSet cells) {}

    private AirframeScreen(CompoundTag t) {
        super(Component.translatable(P + "title"));
        accept(t);
    }

    static void show(CompoundTag t) {
        if (!AirGate.AVAILABLE || t == null || !t.hasUUID("id")) { Wmd.LOGGER.info("[airframe] client: page dropped (no id)"); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.screen instanceof AirframeScreen s && t.getUUID("id").equals(s.ship)) { s.accept(t); s.rebuildWidgets(); }
        else mc.setScreen(new AirframeScreen(t));
    }

    private void accept(CompoundTag nt) {
        boolean full = nt.getBoolean("full");
        if (full || view == null) {
            if (view != null) view.close();
            view = new AirframeView(nt);
            t = nt;
        } else {

            for (String k : nt.getAllKeys()) t.put(k, nt.get(k).copy());
        }
        ship = nt.getUUID("id");
        mode = nt.getInt("mode");
        base = nt.getInt("base");
        if (mode == 1) view.setStatus(nt.getByteArray("st")); else view.setStatus(null);
        buildRows();
    }

    private int px() { return (width - PW) / 2; }

    private int py() { return (height - PH) / 2; }

    @Override
    protected void init() {
        int x = px(), y = py() + FOOT_Y;
        if (mode == 0) {
            addRenderableWidget(new WmdUi.KitButton(x + 6, y, 170, 14, Component.translatable(P + "record"), b -> ask(AirframeRecords.A_RECORD, null)));
            addRenderableWidget(new WmdUi.KitButton(x + 180, y, 64, 14, Component.translatable(P + "flight"), b -> flight()));
            addRenderableWidget(new WmdUi.KitButton(x + 248, y, 66, 14, Component.translatable(P + "close"), b -> onClose()));
            ListTag rl = t.getList("relink", Tag.TAG_COMPOUND);
            for (int i = 0; i < rl.size(); i++) {
                CompoundTag c = rl.getCompound(i);
                UUID old = c.getUUID("id");
                String n = c.getString("n");
                addRenderableWidget(new WmdUi.KitButton(x + SIDE_X, py() + SIDE_END - (rl.size() - i) * RELINK_H + 1, SIDE_W, 12,
                        Component.translatable(P + "relink", n.isEmpty() ? Component.translatable(P + "unnamed") : Component.literal(n)), b -> ask(AirframeRecords.A_RELINK, old)));
            }
        } else {
            addRenderableWidget(new WmdUi.KitButton(x + 6, y, 110, 14, Component.translatable(P + "show"), b -> showInWorld()));
            boolean confirm = confirmUntil > age;
            addRenderableWidget(new WmdUi.KitButton(x + 120, y, 98, 14, Component.translatable(P + (confirm ? "record_confirm" : "record_again")), b -> {
                if (confirmUntil > age) { confirmUntil = -1; ask(AirframeRecords.A_RECORD, null); }
                else { confirmUntil = age + 60; rebuildWidgets(); }
            }, confirm));
            addRenderableWidget(new WmdUi.KitButton(x + 222, y, 44, 14, Component.translatable(P + "flight"), b -> flight()));
            addRenderableWidget(new WmdUi.KitButton(x + 270, y, 44, 14, Component.translatable(P + "close"), b -> onClose()));
            if (t.getBoolean("prev")) {
                int bw = (SIDE_W - 4) / 2;
                addRenderableWidget(new WmdUi.KitButton(x + SIDE_X, py() + 37, bw, 11, Component.translatable(P + "base_now"), b -> setBase(0)) {
                    { active = base != 0; }
                });
                addRenderableWidget(new WmdUi.KitButton(x + SIDE_X + bw + 4, py() + 37, bw, 11, Component.translatable(P + "base_prev"), b -> setBase(1)) {
                    { active = base != 1; }
                });
            }
        }
    }

    private void setBase(int b) {
        if (b == base) return;
        base = b;
        PacketDistributor.sendToServer(new AirframePayload.Ask(AirframeRecords.A_OPEN, ship, null, base));
    }

    private void ask(int action, UUID arg) { PacketDistributor.sendToServer(new AirframePayload.Ask(action, ship, arg, base)); }

    private void flight() { PacketDistributor.sendToServer(new FlightCheckPayload.Ask(ship)); }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (confirmUntil >= 0 && age == confirmUntil) rebuildWidgets();
        if (mode == 1 && age % Balance.REC_REFRESH_TICKS == 0 && ship != null) ask(AirframeRecords.A_REFRESH, null);
    }

    @Override
    public void removed() {
        if (view != null) view.close();
        super.removed();
    }

    private void buildRows() {
        rows.clear();
        missing = 0;
        broken = 0;
        if (view == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mode == 0) {
            Map<Item, int[]> n = new LinkedHashMap<>();
            Map<Item, BitSet> cells = new LinkedHashMap<>();
            for (int i = 0; i < view.n; i++) {
                Item it = view.st[i].getBlock().asItem();
                if (it == net.minecraft.world.item.Items.AIR) continue;
                n.computeIfAbsent(it, k -> new int[1])[0]++;
                cells.computeIfAbsent(it, k -> new BitSet()).set(i);
            }
            n.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).forEach(e ->
                    rows.add(new Row(name(e.getKey()), WmdUi.TEXT, null, e.getValue()[0], Component.literal(Integer.toString(e.getValue()[0])), cells.get(e.getKey()))));
            return;
        }
        byte[] st = new byte[view.n];
        for (int i = 0; i < view.n; i++) st[i] = view.status(i);

        Map<Item, BitSet> miss = new LinkedHashMap<>();
        Map<String, BitSet> amber = new LinkedHashMap<>();
        Map<String, Item> amberNeed = new LinkedHashMap<>();
        Map<String, Component> amberLabel = new LinkedHashMap<>();
        CompoundTag skins = t.getCompound("skin");
        var blocks = mc.level.holderLookup(Registries.BLOCK);
        int[] hp = t.getIntArray("hp");
        for (int i = 0; i < view.n; i++) {
            byte s = st[i];
            if (s == AirframeRecords.OK) continue;
            Item it = view.st[i].getBlock().asItem();
            if (it == net.minecraft.world.item.Items.AIR) {
                if (s == AirframeRecords.MISSING) missing++; else broken++;
                amber.computeIfAbsent("p:", k -> new BitSet()).set(i);
                amberLabel.putIfAbsent("p:", Component.translatable(P + "k_part"));
                continue;
            }
            if (s == AirframeRecords.MISSING) { missing++; miss.computeIfAbsent(it, k -> new BitSet()).set(i); continue; }
            broken++;
            String key;
            Component label;
            Item need = null;
            switch (s) {
                case AirframeRecords.WRONG -> { key = "w:" + it; label = Component.translatable(P + "k_wrong", name(it)); need = it; }
                case AirframeRecords.SHAPE -> { key = "s:" + it; label = Component.translatable(P + "k_shape", name(it)); }
                case AirframeRecords.DAMAGED -> {
                    int h = -1;
                    for (int j = 0; j + 1 < hp.length; j += 2) if (hp[j] == i) h = hp[j + 1];
                    key = "d:" + i;
                    label = Component.translatable(P + "k_damaged", name(it), Math.max(0, Math.round(100.0F * h / Balance.ENGINE_HP)));
                    need = Wmd.ENGINE_REPAIR_KIT.get();
                }
                case AirframeRecords.SKIN -> {
                    Item skin = null;
                    if (skins.contains(Integer.toString(i))) {
                        BlockState sk = NbtUtils.readBlockState(blocks, skins.getCompound(Integer.toString(i)));
                        if (!sk.isAir()) skin = sk.getBlock().asItem();
                    }
                    key = "k:" + skin;
                    label = skin == null ? Component.translatable(P + "k_skin_steel") : Component.translatable(P + "k_skin", name(skin));
                    need = skin;
                }
                default -> { key = "t:" + it; label = Component.translatable(P + "k_setting", name(it)); }
            }
            amber.computeIfAbsent(key, k -> new BitSet()).set(i);
            amberLabel.putIfAbsent(key, label);
            if (need != null) amberNeed.putIfAbsent(key, need);
        }
        rows.add(new Row(Component.translatable(P + "g_missing", missing), WmdUi.BAD, null, 0, null, null));
        miss.entrySet().stream().sorted((a, b) -> b.getValue().cardinality() - a.getValue().cardinality()).forEach(e -> {
            int need = e.getValue().cardinality(), have = have(e.getKey());
            rows.add(new Row(name(e.getKey()), WmdUi.BAD, e.getKey(), need, haveNeed(have, need), e.getValue()));
        });
        rows.add(new Row(Component.translatable(P + "g_broken", broken), WmdUi.WARN, null, 0, null, null));
        for (Map.Entry<String, BitSet> e : amber.entrySet()) {
            Item need = amberNeed.get(e.getKey());
            int count = e.getValue().cardinality();
            Component right = need == null ? Component.literal("x" + count) : haveNeed(have(need), count);
            rows.add(new Row(amberLabel.get(e.getKey()), WmdUi.WARN, need, count, right, e.getValue()));
        }
        int add = t.getInt("add");
        if (add > 0) rows.add(new Row(Component.translatable(P + "g_added", add), WmdUi.TEXT_MUTED, null, 0, null, null));
        if (sel >= rows.size()) sel = -1;
    }

    private static Component name(Item it) { return it == null ? Component.literal("?") : new ItemStack(it).getHoverName(); }

    private static int have(Item it) {
        Minecraft mc = Minecraft.getInstance();
        return it == null || mc.player == null ? 0 : mc.player.getInventory().countItem(it);
    }

    private static Component haveNeed(int have, int need) {
        return Component.literal(have + " / " + need).withColor(have >= need ? WmdUi.GOOD : WmdUi.BAD);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        String n = t.getString("n");
        Component title = n.isEmpty() ? Component.translatable(P + "title") : Component.translatable(P + "title_of", n);
        Component chip;
        int kind;
        if (mode == 0) { chip = Component.translatable(P + "chip_none"); kind = WmdUi.K_WARN; }
        else if (missing + broken == 0) { chip = Component.translatable(P + "chip_ok"); kind = WmdUi.K_GOOD; }
        else { chip = Component.translatable(P + "chip_bad", missing + broken); kind = WmdUi.K_BAD; }
        ItemStack icon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flight_check_tablet")));
        WmdUi.header(g, font, x, y, PW, icon, title, chip, kind, kind == WmdUi.K_BAD);

        int vx = x + VIEW_X, vy = y + VIEW_Y;
        WmdUi.well(g, vx, vy, VIEW_W, VIEW_H);
        if (view != null) {
            float time = WmdUi.time();
            Row r = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
            view.draw(g, vx, vy, VIEW_W - SLIDER_W - 4, VIEW_H, yaw, pitch, zoom, mode == 1 ? filter : 0, r == null || r.cells() == null ? null : r.cells()::get, time);
        }
        if (mode == 1) {
            String[] f = { "f_all", "f_missing", "f_broken" };
            int cx = vx + 3;
            for (int i = 0; i < 3; i++) {
                Component c = Component.translatable(P + f[i]);
                int w = font.width(c) + 6;
                g.fill(cx, vy + 3, cx + w, vy + 14, filter == i ? 0xFF2D3A4A : 0xCC202833);
                if (filter == i) g.fill(cx, vy + 13, cx + w, vy + 14, WmdUi.BLUE);
                g.drawString(font, c, cx + 3, vy + 5, filter == i ? WmdUi.TEXT : WmdUi.TEXT_MUTED, false);
                cx += w + 3;
            }
        }

        int sx = vx + VIEW_W - SLIDER_W - 2, sy0 = vy + 16, sy1 = vy + VIEW_H - 16;
        g.fill(sx, sy0, sx + SLIDER_W, sy1, 0xFF0B0E12);
        if (view != null && view.sy > 1) {
            float f = view.slice() / (float) (view.sy - 1);
            int ky = (int) (sy1 - 4 - f * (sy1 - sy0 - 4));
            g.fill(sx - 1, ky, sx + SLIDER_W + 1, ky + 4, dragSlice ? WmdUi.BLUE_LIGHT : WmdUi.BLUE);
        }
        g.pose().pushPose();
        g.pose().translate(vx + 3, vy + VIEW_H - 9, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, Component.translatable(P + "hint"), 0, 0, WmdUi.TEXT_DIM, false);
        g.pose().popPose();

        int s = x + SIDE_X;
        if (mode == 0) {
            int cw = (SIDE_W - 4) / 2;
            WmdUi.card(g, font, s, y + 26, cw, Component.translatable(P + "blocks"), Component.literal(Integer.toString(view == null ? 0 : view.n)), WmdUi.TEXT);
            WmdUi.card(g, font, s + cw + 4, y + 26, cw, Component.translatable(P + "machines"), Component.literal(Integer.toString(t.getInt("mc"))), WmdUi.TEXT);
            WmdUi.card(g, font, s, y + 50, cw, Component.translatable(P + "skins"), Component.literal(Integer.toString(t.getInt("sk"))), WmdUi.TEXT);
            WmdUi.card(g, font, s + cw + 4, y + 50, cw, Component.translatable(P + "settings"), Component.literal(Integer.toString(t.getInt("set"))), WmdUi.TEXT);
            WmdUi.divider(g, s, y + 75, SIDE_W);
            listHead(g, s, y + 79, "used", "count");
            drawRows(g, s, y + LIST_Y_REC, y + recordListEnd(), mx, my);
        } else {
            long when = t.getLong("t");
            String date = when <= 0 ? "-" : DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(when).atZone(ZoneId.systemDefault()));
            WmdUi.drawFit(g, font, Component.translatable(P + (base == 1 ? "recorded_prev" : "recorded"), date), s, y + 27, SIDE_W, WmdUi.TEXT_MUTED);
            listHead(g, s, y + 51, "to_fix", "have_need");
            drawRows(g, s, y + LIST_Y_CMP, y + LIST_END, mx, my);
            ListTag pieces = t.getList("pieces", Tag.TAG_COMPOUND);
            if (!pieces.isEmpty()) {
                CompoundTag pc = pieces.getCompound(0);
                WmdUi.drawFit(g, font, Component.translatable(P + "piece", pc.getInt("d"), pc.getString("dir")), s, y + LIST_END + 4, SIDE_W, WmdUi.WARN);
            }
        }
    }

    private int recordListEnd() {
        int k = t.getList("relink", Tag.TAG_COMPOUND).size();
        return k == 0 ? SIDE_END : SIDE_END - k * RELINK_H - 2;
    }

    private void listHead(GuiGraphics g, int x, int y, String l, String r) {
        Component a = Component.translatable(P + l), b = Component.translatable(P + r);
        g.drawString(font, a, x + 2, y, WmdUi.TEXT_MUTED, false);
        g.drawString(font, b, x + SIDE_W - 2 - font.width(b), y, WmdUi.TEXT_MUTED, false);
    }

    private int visibleRows(int y0, int y1) { return Math.max(1, (y1 - y0) / ROW_H); }

    private void drawRows(GuiGraphics g, int x, int y0, int y1, int mx, int my) {
        WmdUi.well(g, x, y0 - 1, SIDE_W, y1 - y0 + 1);
        int vis = visibleRows(y0, y1);
        scroll = Math.max(0, Math.min(scroll, rows.size() - vis));
        for (int k = 0; k < vis && scroll + k < rows.size(); k++) {
            int i = scroll + k, ry = y0 + k * ROW_H;
            Row r = rows.get(i);
            boolean over = WmdUi.over(mx, my, x, ry, SIDE_W, ROW_H);
            if (i == sel) { g.fill(x, ry, x + SIDE_W, ry + ROW_H, 0xFF2D3A4A); g.fill(x, ry, x + 1, ry + ROW_H, WmdUi.BLUE); }
            else if (over && r.cells() != null) g.fill(x, ry, x + SIDE_W, ry + ROW_H, 0xFF263040);
            if (r.cells() == null) {
                WmdUi.drawFit(g, font, r.label(), x + 3, ry + 1, SIDE_W - 6, r.colour());
                continue;
            }
            g.fill(x + 3, ry + 2, x + 9, ry + 8, 0xFF000000 | r.colour());
            int rw = r.right() == null ? 0 : font.width(r.right());
            WmdUi.drawFit(g, font, r.label(), x + 12, ry + 1, SIDE_W - 16 - rw, WmdUi.TEXT, 0.7F);
            if (r.right() != null) g.drawString(font, r.right(), x + SIDE_W - 3 - rw, ry + 1, WmdUi.TEXT_MUTED, false);
        }
        if (rows.size() > vis) {
            int h = y1 - y0, bh = Math.max(6, h * vis / rows.size()), by = y0 + (h - bh) * scroll / Math.max(1, rows.size() - vis);
            g.fill(x + SIDE_W - 2, by, x + SIDE_W - 1, by + bh, WmdUi.TEXT_DIM);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (view == null || dragView) return;
        int vx = px() + VIEW_X, vy = py() + VIEW_Y;
        hover = WmdUi.over(mx, my, vx, vy, VIEW_W - SLIDER_W - 4, VIEW_H) ? view.pick(mx, my) : -1;
        if (hover < 0) return;
        List<Component> tip = new ArrayList<>();
        tip.add(view.st[hover].getBlock().getName());
        byte s = view.status(hover);
        if (mode == 1) tip.add(Component.translatable(P + "s" + s).withColor(s == AirframeRecords.OK ? WmdUi.GOOD : s == AirframeRecords.MISSING ? WmdUi.BAD : WmdUi.WARN));
        tip.add(Component.literal(view.x[hover] + ", " + view.y[hover] + ", " + view.z[hover]).withColor(WmdUi.TEXT_DIM));
        g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int x = px(), y = py(), vx = x + VIEW_X, vy = y + VIEW_Y;
        if (mode == 1 && my >= vy + 3 && my < vy + 14) {
            String[] f = { "f_all", "f_missing", "f_broken" };
            int cx = vx + 3;
            for (int i = 0; i < 3; i++) {
                int w = font.width(Component.translatable(P + f[i])) + 6;
                if (mx >= cx && mx < cx + w) { filter = i; WmdUi.tab(); return true; }
                cx += w + 3;
            }
        }
        int sx = vx + VIEW_W - SLIDER_W - 2;
        if (WmdUi.over(mx, my, sx - 2, vy + 12, SLIDER_W + 4, VIEW_H - 24)) { dragSlice = true; slice(my); return true; }
        if (WmdUi.over(mx, my, vx, vy, VIEW_W - SLIDER_W - 4, VIEW_H)) { dragView = true; return true; }
        int y0 = y + (mode == 0 ? LIST_Y_REC : LIST_Y_CMP), y1 = y + (mode == 0 ? recordListEnd() : LIST_END);
        if (WmdUi.over(mx, my, x + SIDE_X, y0, SIDE_W, y1 - y0)) {
            int i = scroll + (int) ((my - y0) / ROW_H);
            if (i < rows.size() && rows.get(i).cells() != null) { sel = sel == i ? -1 : i; WmdUi.click(); }
            return true;
        }
        return false;
    }

    private void slice(double my) {
        if (view == null || view.sy <= 1) return;
        int vy = py() + VIEW_Y, sy0 = vy + 16, sy1 = vy + VIEW_H - 16;
        float f = (float) ((sy1 - 2 - my) / (sy1 - sy0 - 4));
        view.setSlice(Math.round(Math.max(0.0F, Math.min(1.0F, f)) * (view.sy - 1)));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSlice) { slice(my); return true; }
        if (dragView) {
            yaw += (float) dx * 1.2F;
            pitch = Math.max(-80.0F, Math.min(89.0F, pitch + (float) dy * 1.2F));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragView = false;
        dragSlice = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int x = px(), y = py();
        if (WmdUi.over(mx, my, x + VIEW_X, y + VIEW_Y, VIEW_W, VIEW_H)) { zoom = Math.max(0.4F, Math.min(4.0F, zoom * (sy > 0 ? 1.12F : 0.89F))); return true; }
        if (WmdUi.over(mx, my, x + SIDE_X, y + 26, SIDE_W, SIDE_END - 26)) { scroll = Math.max(0, scroll - (int) Math.signum(sy)); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void showInWorld() {
        if (view == null) return;
        List<BlockPos> cells = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        Row r = sel >= 0 && sel < rows.size() ? rows.get(sel) : null;
        for (int i = 0; i < view.n; i++) {
            byte s = view.status(i);
            if (s == AirframeRecords.OK || (r != null && r.cells() != null && !r.cells().get(i))) continue;
            cells.add(new BlockPos(view.ox + view.x[i], view.oy + view.y[i], view.oz + view.z[i]));
            colours.add(s == AirframeRecords.MISSING ? AirframeView.RED : AirframeView.AMBER);
        }
        AirframeHighlight.show(cells, colours);
        WmdUi.confirm();
        onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
