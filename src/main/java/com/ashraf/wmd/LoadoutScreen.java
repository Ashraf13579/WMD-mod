package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LoadoutScreen extends Screen {

    static final int SIDE_W = 92, FOOT_H = 58, ROW_H = 13, PW_MIN = 300, PW_MAX = 470, PH_MIN = 224, PH_MAX = 320, CRAFT_H = 22, TOOL_Y = 24, VIEW_Y = 40;
    private static final int[] GROUP_COL = { 0xFF3A7BD5, 0xFFD58A3A, 0xFF3AC2D5, 0xFF8FE3A8 };

    private CompoundTag snap;
    private BlockPos depot;
    private int craft, tab, setChoice, scroll, deck = -1, group, ticks, presetPick;
    private boolean fNotFull, fFar;
    private float zoom = 1.0F, panX, panY;
    private final Set<Long> sel = new HashSet<>();
    private final Set<String> folded = new HashSet<>();
    private double dragX, dragY;
    private boolean dragging, boxing;
    private EditBox name;

    private int x0, y0, w, h, sx, vx, vy, vw, vh, lx, lw;
    private final List<Row> rows = new ArrayList<>();

    private record Row(String group, CompoundTag target, int index, int count, int full) {}

    private LoadoutScreen(CompoundTag tag) {
        super(Component.translatable("gui.wmd.loadout.title"));
        apply(tag);
    }

    static void show(CompoundTag tag) {
        if (tag.getBoolean("stores")) { StoresScreen.show(tag); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LoadoutScreen s) { s.apply(tag); s.layout(); return; }
        mc.setScreen(new LoadoutScreen(tag));
    }

    private void apply(CompoundTag tag) {
        UUID keep = craftId();
        snap = tag;
        depot = BlockPos.of(tag.getLong("depot"));
        ListTag cs = crafts();
        craft = 0;
        if (keep != null) for (int i = 0; i < cs.size(); i++) if (cs.getCompound(i).getUUID("id").equals(keep)) craft = i;
    }

    private ListTag crafts() { return snap == null ? new ListTag() : snap.getList("crafts", Tag.TAG_COMPOUND); }
    private CompoundTag cur() { ListTag c = crafts(); return craft < c.size() ? c.getCompound(craft) : null; }
    private UUID craftId() { CompoundTag c = cur(); return c == null ? null : c.getUUID("id"); }
    private ListTag targets() { CompoundTag c = cur(); return c == null ? new ListTag() : c.getList("targets", Tag.TAG_COMPOUND); }

    @Override
    protected void init() {
        name = new EditBox(font, 0, 0, 70, 14, Component.translatable("gui.wmd.loadout.preset_name"));
        name.setMaxLength(24);
        name.setHint(Component.translatable("gui.wmd.loadout.preset_name"));
        layout();
    }

    private void layout() {
        if (font == null) return;
        clearWidgets();
        boolean big = targets().size() > 12;
        w = Math.max(PW_MIN, Math.min(big ? PW_MAX : 340, width - SIDE_W - 40));
        h = Math.max(PH_MIN, Math.min(big ? PH_MAX : 250, height - 12));
        int total = SIDE_W + 2 + w;
        sx = (width - total) / 2 + 23 / 2;
        x0 = sx + SIDE_W + 2;
        y0 = (height - h) / 2;
        vx = x0 + 8; vy = y0 + VIEW_Y; vw = (int) ((w - 24) * 0.42F); vh = h - VIEW_Y - FOOT_H - 4;
        lx = vx + vw + 8; lw = x0 + w - 8 - lx;
        String[][] tabs = { { "icon_process", "loadout" }, { "icon_freq", "groups" }, { "icon_upgrades", "store" } };
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            addRenderableWidget(new WmdUi.TabButton(sx - 23, y0 + 22 + i * 24, tabs[i][0], Component.translatable("gui.wmd.loadout.tab." + tabs[i][1]), b -> {
                WmdUi.tab();
                if (t == 2) act("store", null); else { tab = t; layout(); }
            }));
        }
        int fy = y0 + h - FOOT_H + 2;
        if (tab == 0) {
            addRenderableWidget(new WmdUi.KitButton(x0 + 102, fy, 32, 14, Component.translatable("gui.wmd.loadout.load"), b -> preset("preset_load")));
            addRenderableWidget(new WmdUi.KitButton(x0 + 136, fy, 32, 14, Component.translatable("gui.wmd.loadout.del"), b -> preset("preset_del")));
            name.setX(x0 + 172); name.setY(fy); name.setWidth(Math.max(40, w - 172 - 46));
            addRenderableWidget(name);
            addRenderableWidget(new WmdUi.KitButton(x0 + w - 42, fy, 34, 14, Component.translatable("gui.wmd.loadout.save"), b -> {
                CompoundTag a = new CompoundTag(); a.putString("n", name.getValue()); act("preset_save", a);
            }));
            int ry = fy + 18;
            addRenderableWidget(new WmdUi.KitButton(x0 + 8, ry, 14, 14, Component.literal("<"), b -> { setChoice--; }));
            addRenderableWidget(new WmdUi.KitButton(x0 + 24, ry, 96, 14, Component.translatable("gui.wmd.loadout.set"), b -> setSelection()));
            addRenderableWidget(new WmdUi.KitButton(x0 + 122, ry, 14, 14, Component.literal(">"), b -> { setChoice++; }));
            addRenderableWidget(new WmdUi.KitButton(x0 + 140, ry, 44, 14, Component.translatable("gui.wmd.loadout.clear"), b -> act("plan_clear", null)));
            addRenderableWidget(new WmdUi.KitButton(x0 + w - 106, ry, 48, 14, Component.translatable("gui.wmd.loadout.unload"), b -> act("unload", null)));
            addRenderableWidget(new WmdUi.KitButton(x0 + w - 56, ry, 48, 14, Component.translatable("gui.wmd.loadout.stock"), b -> { WmdUi.confirm(); act("stock", null); }));
        } else if (tab == 1) {
            for (int g = 0; g < LoadoutPlan.GROUPS; g++) {
                final int gg = g;
                addRenderableWidget(new WmdUi.KitButton(x0 + 8 + g * 20, y0 + h - FOOT_H + 2, 18, 14, Component.literal(String.valueOf(g + 1)), b -> group = gg));
            }
            addRenderableWidget(new WmdUi.KitButton(x0 + 92, y0 + h - FOOT_H + 2, 70, 14, Component.translatable("gui.wmd.loadout.mode"), b -> {
                CompoundTag a = new CompoundTag(); a.putInt("g", group); act("group_mode", a);
            }));
        }
        buildRows();
    }

    private void buildRows() {
        rows.clear();
        Map<String, List<CompoundTag>> by = new LinkedHashMap<>();
        for (Tag x : targets()) {
            CompoundTag t = (CompoundTag) x;
            if (!visible(t)) continue;
            by.computeIfAbsent(t.getString("g"), k -> new ArrayList<>()).add(t);
        }
        for (Map.Entry<String, List<CompoundTag>> e : by.entrySet()) {
            int full = 0;
            for (CompoundTag t : e.getValue()) if (t.getBoolean("full")) full++;
            rows.add(new Row(e.getKey(), null, 0, e.getValue().size(), full));
            if (folded.contains(e.getKey())) continue;
            int i = 1;
            for (CompoundTag t : e.getValue()) rows.add(new Row(e.getKey(), t, i++, 0, 0));
        }
        scroll = Math.max(0, Math.min(scroll, rows.size() - listRows()));
    }

    private int listRows() { return Math.max(1, (vh - 12) / ROW_H); }

    private boolean visible(CompoundTag t) {
        if (fNotFull && t.getBoolean("full")) return false;
        if (fFar && t.getBoolean("reach")) return false;
        return deck < 0 || t.getInt("y") / 4 == deck;
    }

    private void act(String what, CompoundTag extra) {
        CompoundTag a = extra == null ? new CompoundTag() : extra;
        a.putString("a", what);
        UUID id = craftId();
        if (id != null) a.putUUID("ship", id);
        PacketDistributor.sendToServer(new LoadoutPayload.Act(depot, a));
        WmdUi.click();
    }

    private long stockHintUntil;

    private void preset(String what) {
        if ("preset_load".equals(what)) stockHintUntil = System.currentTimeMillis() + 8000L;
        CompoundTag c = cur();
        if (c == null) return;
        int n = c.getList("presets", Tag.TAG_COMPOUND).size();
        if (n == 0) { WmdUi.deny(); return; }
        CompoundTag a = new CompoundTag();
        a.putInt("i", Math.floorMod(presetPick, n));
        act(what, a);
    }

    private List<ItemStack> stock() {
        List<ItemStack> l = new ArrayList<>();
        if (snap == null) return l;
        for (Tag x : snap.getList("stock", Tag.TAG_COMPOUND)) {
            ItemStack s = ItemStack.parseOptional(minecraft.level.registryAccess(), (CompoundTag) x);
            if (!s.isEmpty()) l.add(s);
        }
        return l;
    }

    private void setSelection() {
        List<ItemStack> st = stock();
        if (st.isEmpty() || sel.isEmpty()) { WmdUi.deny(); return; }
        ItemStack pick = st.get(Math.floorMod(setChoice, st.size())).copyWithCount(1);
        for (long p : sel) planFor(p, pick);
    }

    private void planFor(long pos, ItemStack item) {
        CompoundTag a = new CompoundTag();
        a.putLong("p", pos);
        if (item != null && !item.isEmpty()) a.put("i", item.saveOptional(minecraft.level.registryAccess()));
        act("plan", a);
    }

    private void cyclePlan(CompoundTag t) {
        ListTag opt = t.getList("opt", Tag.TAG_COMPOUND);
        if (opt.isEmpty()) { WmdUi.deny(); return; }
        ItemStack pl = item(t, "plan");
        int at = -1;
        for (int i = 0; i < opt.size(); i++) if (ItemStack.isSameItemSameComponents(ItemStack.parseOptional(minecraft.level.registryAccess(), opt.getCompound(i)), pl)) at = i;
        planFor(t.getLong("p"), ItemStack.parseOptional(minecraft.level.registryAccess(), opt.getCompound((at + 1) % opt.size())));
    }

    private ItemStack item(CompoundTag t, String key) {
        return t.contains(key) ? ItemStack.parseOptional(minecraft.level.registryAccess(), t.getCompound(key)) : ItemStack.EMPTY;
    }

    @Override
    public void tick() {
        if (++ticks % Balance.LO_REFRESH_TICKS == 0) {
            CompoundTag a = new CompoundTag();
            a.putString("a", "refresh");
            PacketDistributor.sendToServer(new LoadoutPayload.Act(depot, a));
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        WmdUi.panel(g, sx, y0, SIDE_W, h);
        WmdUi.panel(g, x0, y0, w, h);
        side(g, mx, my);
        CompoundTag c = cur();
        int mode = c == null ? 0 : c.getInt("mode");
        Component chip = c == null ? Component.translatable("gui.wmd.loadout.chip_none")
                : c.getBoolean("air") ? Component.translatable("gui.wmd.loadout.chip_air")
                : mode == AmmoDepotBlockEntity.MODE_STOCK ? Component.translatable("gui.wmd.loadout.chip_stock")
                : mode == AmmoDepotBlockEntity.MODE_UNLOAD ? Component.translatable("gui.wmd.loadout.chip_unload")
                : Component.translatable("gui.wmd.loadout.chip_ready");
        int kind = c == null ? WmdUi.K_IDLE : c.getBoolean("air") ? WmdUi.K_BAD : mode != 0 ? WmdUi.K_WARN : WmdUi.K_GOOD;
        Component title = c == null ? this.title : Component.translatable(tab == 1 ? "gui.wmd.loadout.title_groups" : "gui.wmd.loadout.title_craft", c.getString("name"));
        WmdUi.header(g, font, x0, y0, w, new ItemStack(AirBlocks.ORDNANCE_LOADER_ITEM.get()), title, chip, kind, mode != 0);
        if (c == null) {
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.no_craft"), x0 + 10, y0 + 40, w - 20, WmdUi.TEXT_MUTED);
            super.render(g, mx, my, pt);
            return;
        }
        toolbar(g, mx, my);
        view(g, mx, my);
        list(g, mx, my);
        footer(g, c);
        super.render(g, mx, my, pt);
        tooltip(g, mx, my);
    }

    private void side(GuiGraphics g, int mx, int my) {
        g.drawString(font, Component.translatable("gui.wmd.loadout.crafts"), sx + 7, y0 + 6, WmdUi.TEXT, false);
        WmdUi.divider(g, sx + 6, y0 + 16, SIDE_W - 12);
        ListTag cs = crafts();
        int y = y0 + 20;
        for (int i = 0; i < cs.size() && y + CRAFT_H < y0 + h - 30; i++, y += CRAFT_H) {
            CompoundTag c = cs.getCompound(i);
            if (i == craft) { g.fill(sx + 4, y, sx + SIDE_W - 4, y + CRAFT_H - 1, 0xFF2D3A4A); g.fill(sx + 4, y, sx + 6, y + CRAFT_H - 1, WmdUi.BLUE); }
            else if (WmdUi.over(mx, my, sx + 4, y, SIDE_W - 8, CRAFT_H - 1)) g.fill(sx + 4, y, sx + SIDE_W - 4, y + CRAFT_H - 1, 0xFF263040);
            int full = 0, n = 0;
            for (Tag x : c.getList("targets", Tag.TAG_COMPOUND)) { n++; if (((CompoundTag) x).getBoolean("full")) full++; }
            int k = c.getBoolean("air") ? WmdUi.K_BAD : c.getInt("mode") != 0 ? WmdUi.K_WARN : c.getBoolean("parked") ? WmdUi.K_GOOD : WmdUi.K_IDLE;
            WmdUi.led(g, sx + 9, y + 4, k, k == WmdUi.K_WARN);
            WmdUi.drawFit(g, font, Component.literal(c.getString("name")), sx + 18, y + 3, SIDE_W - 24, WmdUi.TEXT);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.kind_" + c.getString("kind"), full, n), sx + 18, y + 12, SIDE_W - 24, WmdUi.TEXT_MUTED);
        }
        WmdUi.divider(g, sx + 6, y0 + h - 30, SIDE_W - 12);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.depot_rounds", snap.getInt("rounds")), sx + 7, y0 + h - 25, SIDE_W - 12, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.loaders", snap.getInt("loaders")), sx + 7, y0 + h - 14, SIDE_W - 12,
                snap.getInt("loaders") > 0 ? WmdUi.TEXT_MUTED : WmdUi.WARN);
    }

    private int chipX;

    private int toolChip(GuiGraphics g, Component t, boolean on, int mx, int my) {
        int cw = font.width(t) + 10;
        g.fill(chipX, y0 + TOOL_Y, chipX + cw, y0 + TOOL_Y + 12, on ? 0xFF2D3A4A : WmdUi.lerp(0xFF1C232C, 0xFF263040, WmdUi.over(mx, my, chipX, y0 + TOOL_Y, cw, 12) ? 1.0F : 0.0F));
        if (on) g.fill(chipX, y0 + TOOL_Y + 11, chipX + cw, y0 + TOOL_Y + 12, WmdUi.BLUE);
        g.drawString(font, t, chipX + 5, y0 + TOOL_Y + 2, on ? WmdUi.TEXT : WmdUi.TEXT_MUTED, false);
        int x = chipX;
        lastChipW = cw;
        chipX += cw + 4;
        return x;
    }

    private int[] chips = new int[3], chipW = new int[3];
    private int lastChipW;

    private void toolbar(GuiGraphics g, int mx, int my) {
        chipX = x0 + 8;
        chips[0] = toolChip(g, Component.translatable("gui.wmd.loadout.f_notfull"), fNotFull, mx, my); chipW[0] = lastChipW;
        chips[1] = toolChip(g, Component.translatable("gui.wmd.loadout.f_far"), fFar, mx, my); chipW[1] = lastChipW;
        chips[2] = toolChip(g, deck < 0 ? Component.translatable("gui.wmd.loadout.deck_all") : Component.translatable("gui.wmd.loadout.deck", deck + 1), deck >= 0, mx, my); chipW[2] = lastChipW;
        Component z = Component.translatable("gui.wmd.loadout.zoom", String.format("%.1f", zoom));
        g.drawString(font, z, x0 + w - 8 - font.width(z), y0 + TOOL_Y + 2, WmdUi.TEXT_MUTED, false);
    }

    private float[] viewFrame() {
        CompoundTag c = cur();
        int gw = Math.max(1, c.getInt("gw")), gh = Math.max(1, c.getInt("gh"));
        float cs = Math.min((vw - 8) / (float) gw, (vh - 8) / (float) gh) * zoom;
        float ox = vx + vw / 2.0F - gw * cs / 2.0F + panX, oy = vy + vh / 2.0F - gh * cs / 2.0F + panY;
        return new float[] { cs, ox, oy, gw, gh };
    }

    private void view(GuiGraphics g, int mx, int my) {
        WmdUi.well(g, vx, vy, vw, vh);
        g.fill(vx, vy, vx + vw, vy + vh, 0xFF141A21);
        CompoundTag c = cur();
        g.enableScissor(vx, vy, vx + vw, vy + vh);
        float[] f = viewFrame();
        float cs = f[0];
        byte[] cells = c.getByteArray("grid");
        int gw = (int) f[3], gh = (int) f[4];
        for (int z = 0; z < gh; z++) for (int x = 0; x < gw; x++) {
            if (z * gw + x >= cells.length || cells[z * gw + x] == 0) continue;
            int px = (int) (f[1] + x * cs), py = (int) (f[2] + z * cs);
            g.fill(px, py, Math.max(px + 1, (int) (f[1] + (x + 1) * cs)), Math.max(py + 1, (int) (f[2] + (z + 1) * cs)), 0xFF5A6674);
        }
        for (Tag x : targets()) {
            CompoundTag t = (CompoundTag) x;
            if (!visible(t)) continue;
            int px = (int) (f[1] + (t.getInt("x") + 0.5F) * cs), py = (int) (f[2] + (t.getInt("z") + 0.5F) * cs);
            int col = tab == 1 ? (t.getInt("grp") >= 0 ? GROUP_COL[t.getInt("grp")] : 0xFF5E6B78) : stateColour(t);
            boolean s = sel.contains(t.getLong("p"));
            g.fill(px - 4, py - 4, px + 5, py + 5, s ? 0xFFFFFFFF : 0xFF0A0D11);
            g.fill(px - 3, py - 3, px + 4, py + 4, col);
            if (tab == 1 && t.getInt("grp") >= 0) g.drawString(font, String.valueOf(t.getInt("grp") + 1), px - 2, py - 3, 0xFF0A0D11, false);
        }
        if (boxing) {
            int ax = (int) Math.min(dragX, mx), bx = (int) Math.max(dragX, mx), ay = (int) Math.min(dragY, my), by = (int) Math.max(dragY, my);
            g.renderOutline(ax, ay, bx - ax, by - ay, WmdUi.BLUE_LIGHT);
        }
        g.disableScissor();
    }

    private int stateColour(CompoundTag t) {
        if (t.getBoolean("busy")) return WmdUi.ORANGE;
        if (!t.getBoolean("reach")) return 0xFFE0372D;
        ItemStack curI = item(t, "cur"), pl = item(t, "plan");
        if (!pl.isEmpty() && !curI.isEmpty() && !ItemStack.isSameItem(curI, pl)) return WmdUi.WARN;
        if (t.getBoolean("full") || (!pl.isEmpty() && ItemStack.isSameItem(curI, pl) && t.getString("fill").isEmpty())) return WmdUi.BLUE;
        return 0xFF5E6B78;
    }

    private void list(GuiGraphics g, int mx, int my) {
        WmdUi.well(g, lx, vy, lw, vh);
        g.drawString(font, Component.translatable(tab == 1 ? "gui.wmd.loadout.head_group" : "gui.wmd.loadout.head_now_plan"), lx + 4, vy + 2, WmdUi.TEXT_MUTED, false);
        int y = vy + 12;
        for (int i = scroll; i < rows.size() && i < scroll + listRows(); i++, y += ROW_H) {
            Row r = rows.get(i);
            boolean hover = WmdUi.over(mx, my, lx, y, lw, ROW_H);
            if (r.target() == null) {
                g.fill(lx, y, lx + lw, y + ROW_H - 1, 0xFF19202A);
                g.drawString(font, folded.contains(r.group()) ? ">" : "v", lx + 3, y + 3, WmdUi.TEXT_MUTED, false);
                WmdUi.drawFit(g, font, Component.translatable("block.wmd." + r.group()), lx + 12, y + 3, lw - 60, WmdUi.TEXT);
                Component n = Component.literal(r.full() + "/" + r.count());
                g.drawString(font, n, lx + lw - 6 - font.width(n), y + 3, WmdUi.TEXT_MUTED, false);
                continue;
            }
            CompoundTag t = r.target();
            boolean s = sel.contains(t.getLong("p"));
            if (s) { g.fill(lx, y, lx + lw, y + ROW_H - 1, 0xFF2D3A4A); g.fill(lx, y, lx + 1, y + ROW_H - 1, WmdUi.BLUE); }
            else if (hover) g.fill(lx, y, lx + lw, y + ROW_H - 1, 0xFF263040);
            g.drawString(font, String.valueOf(r.index()), lx + 12, y + 3, WmdUi.TEXT_MUTED, false);
            ItemStack curI = item(t, "cur"), pl = item(t, "plan");
            int ix = lx + 26;
            if (!curI.isEmpty()) icon(g, curI, ix, y); else g.fill(ix + 1, y + 2, ix + 10, y + 11, 0xFF11161C);
            for (int k = 0; k < 3; k++) g.fill(ix + 14 + k, y + 4 + k, ix + 15 + k, y + 9 - k, 0xFF4A5562);
            if (tab == 1) {
                int gr = t.getInt("grp");
                g.drawString(font, gr >= 0 ? String.valueOf(gr + 1) : "-", ix + 22, y + 3, gr >= 0 ? GROUP_COL[gr] : WmdUi.TEXT_DIM, false);
            } else if (!pl.isEmpty()) icon(g, pl, ix + 20, y);
            else g.drawString(font, "-", ix + 23, y + 3, WmdUi.TEXT_DIM, false);

            String fill = t.getString("fill");
            int fw = fill.isEmpty() ? 0 : font.width(fill) + 6;
            ItemStack nameOf = tab == 1 || pl.isEmpty() ? curI : pl;
            if (!nameOf.isEmpty()) WmdUi.drawFit(g, font, nameOf.getHoverName(), ix + 36, y + 3, lw - (ix + 36 - lx) - 14 - fw, WmdUi.TEXT);
            if (fw > 0) g.drawString(font, fill, lx + lw - 14 - font.width(fill), y + 3, WmdUi.TEXT_MUTED, false);
            int col = stateColour(t);
            int k = col == WmdUi.BLUE ? WmdUi.K_GOOD : col == WmdUi.ORANGE || col == WmdUi.WARN ? WmdUi.K_WARN : col == 0xFFE0372D ? WmdUi.K_BAD : WmdUi.K_IDLE;
            WmdUi.led(g, lx + lw - 9, y + 4, k, col == WmdUi.ORANGE);
        }
        if (rows.size() > listRows()) {
            int th = Math.max(8, (vh - 12) * listRows() / rows.size()), ty = vy + 12 + (vh - 12 - th) * scroll / Math.max(1, rows.size() - listRows());
            g.fill(lx + lw - 2, ty, lx + lw, ty + th, 0xFF4A5562);
        }
    }

    private void icon(GuiGraphics g, ItemStack s, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
    }

    private void footer(GuiGraphics g, CompoundTag c) {
        int fy = y0 + h - FOOT_H + 2;
        WmdUi.divider(g, x0 + 8, fy - 4, w - 16);
        if (tab == 0) {
            ListTag ps = c.getList("presets", Tag.TAG_COMPOUND);
            WmdUi.well(g, x0 + 8, fy, 90, 14);
            Component pn = ps.isEmpty() ? Component.translatable("gui.wmd.loadout.no_presets")
                    : Component.literal(ps.getCompound(Math.floorMod(presetPick, ps.size())).getString("n") + (Math.floorMod(presetPick, ps.size()) == c.getInt("active") ? " *" : ""));
            WmdUi.drawFit(g, font, pn, x0 + 12, fy + 3, 82, ps.isEmpty() ? WmdUi.TEXT_DIM : WmdUi.TEXT);
            List<ItemStack> st = stock();
            Component setName = st.isEmpty() ? Component.translatable("gui.wmd.loadout.none") : st.get(Math.floorMod(setChoice, st.size())).getHoverName();
            Component line = !sel.isEmpty() ? Component.translatable("gui.wmd.loadout.set_hint", setName, sel.size())
                    : System.currentTimeMillis() < stockHintUntil ? Component.translatable("gui.wmd.loadout.plan_stock")
                    : !c.getBoolean("hasPlan") ? Component.translatable("gui.wmd.loadout.default_plan")
                    : Component.translatable(c.getBoolean("holder") ? "gui.wmd.loadout.saved_craft" : "gui.wmd.loadout.saved_depot");
            WmdUi.drawFit(g, font, line, x0 + 8, y0 + h - 14, w - 16, !sel.isEmpty() || System.currentTimeMillis() < stockHintUntil ? WmdUi.BLUE_LIGHT : c.getBoolean("air") ? WmdUi.BAD : WmdUi.TEXT_MUTED);
        } else {
            int m = c.getIntArray("gmodes").length > group ? c.getIntArray("gmodes")[group] : 0;
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.group_line", group + 1, Component.translatable("gui.wmd.loadout.gmode" + m)),
                    x0 + 168, fy + 3, w - 176, GROUP_COL[group]);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.group_hint"), x0 + 8, fy + 21, w - 16, WmdUi.TEXT_MUTED);
            WmdUi.drawFit(g, font, Component.translatable("gui.wmd.loadout.group_cockpit"), x0 + 8, y0 + h - 14, w - 16, WmdUi.TEXT_DIM);
        }
    }

    private void tooltip(GuiGraphics g, int mx, int my) {
        if (!WmdUi.over(mx, my, lx, vy + 12, lw, vh - 12)) return;
        int i = scroll + (my - vy - 12) / ROW_H;
        if (i < 0 || i >= rows.size() || rows.get(i).target() == null) return;
        CompoundTag t = rows.get(i).target();
        List<Component> l = new ArrayList<>();
        ItemStack curI = item(t, "cur"), pl = item(t, "plan");
        l.add(Component.translatable("gui.wmd.loadout.tip_now", curI.isEmpty() ? Component.translatable("gui.wmd.loadout.none") : curI.getHoverName()));
        l.add(Component.translatable("gui.wmd.loadout.tip_plan", pl.isEmpty() ? Component.translatable("gui.wmd.loadout.none") : pl.getHoverName()));
        if (!t.getBoolean("reach")) l.add(Component.translatable("gui.wmd.loadout.tip_far"));
        l.add(Component.translatable("gui.wmd.loadout.tip_click"));
        g.renderComponentTooltip(font, l, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (cur() == null) return false;
        ListTag cs = crafts();
        for (int i = 0; i < cs.size(); i++) {
            int y = y0 + 20 + i * CRAFT_H;
            if (y + CRAFT_H < y0 + h - 30 && WmdUi.over(mx, my, sx + 4, y, SIDE_W - 8, CRAFT_H - 1)) {
                craft = i; sel.clear(); panX = panY = 0; zoom = 1.0F; scroll = 0; layout(); WmdUi.click(); return true;
            }
        }
        for (int k = 0; k < 3; k++) {
            if (chipW[k] > 0 && WmdUi.over(mx, my, chips[k], y0 + TOOL_Y, chipW[k], 12)) {
                if (k == 0) fNotFull = !fNotFull;
                else if (k == 1) fFar = !fFar;
                else deck = deck >= maxDeck() ? -1 : deck + 1;
                buildRows(); WmdUi.click(); return true;
            }
        }
        if (WmdUi.over(mx, my, x0 + 8, y0 + h - FOOT_H + 2, 90, 14) && tab == 0) { presetPick++; WmdUi.click(); return true; }
        if (WmdUi.over(mx, my, vx, vy, vw, vh)) {
            dragX = mx; dragY = my; dragging = true; boxing = hasShiftDown();
            CompoundTag t = markerAt(mx, my);
            if (t != null && !boxing) { clickStation(t, button); return true; }
            return true;
        }
        if (WmdUi.over(mx, my, lx, vy + 12, lw, vh - 12)) {
            int i = scroll + (int) ((my - vy - 12) / ROW_H);
            if (i < 0 || i >= rows.size()) return false;
            Row r = rows.get(i);
            if (r.target() == null) { if (!folded.remove(r.group())) folded.add(r.group()); buildRows(); WmdUi.click(); return true; }
            int ix = lx + 26 + 20;
            if (tab == 0 && mx >= ix && mx < ix + 12) {
                if (button == 1) planFor(r.target().getLong("p"), ItemStack.EMPTY); else cyclePlan(r.target());
                return true;
            }
            clickStation(r.target(), button);
            return true;
        }
        return false;
    }

    private void clickStation(CompoundTag t, int button) {
        long p = t.getLong("p");
        if (tab == 1) { CompoundTag a = new CompoundTag(); a.putInt("g", group); a.putLong("p", p); act("group", a); return; }
        if (button == 1) { planFor(p, ItemStack.EMPTY); return; }
        if (!hasShiftDown()) sel.clear();
        if (!sel.remove(p)) sel.add(p);
        WmdUi.click();
    }

    private int maxDeck() {
        int m = 0;
        for (Tag x : targets()) m = Math.max(m, ((CompoundTag) x).getInt("y") / 4);
        return m;
    }

    private CompoundTag markerAt(double mx, double my) {
        float[] f = viewFrame();
        for (Tag x : targets()) {
            CompoundTag t = (CompoundTag) x;
            if (!visible(t)) continue;
            float px = f[1] + (t.getInt("x") + 0.5F) * f[0], py = f[2] + (t.getInt("z") + 0.5F) * f[0];
            if (Math.abs(mx - px) <= 5 && Math.abs(my - py) <= 5) return t;
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && !boxing) { panX += (float) dx; panY += (float) dy; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && boxing) {
            float[] f = viewFrame();
            double ax = Math.min(dragX, mx), bx = Math.max(dragX, mx), ay = Math.min(dragY, my), by = Math.max(dragY, my);
            for (Tag x : targets()) {
                CompoundTag t = (CompoundTag) x;
                if (!visible(t)) continue;
                float px = f[1] + (t.getInt("x") + 0.5F) * f[0], py = f[2] + (t.getInt("z") + 0.5F) * f[0];
                if (px >= ax && px <= bx && py >= ay && py <= by) sel.add(t.getLong("p"));
            }
        }
        dragging = boxing = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxv, double syv) {
        if (cur() != null && WmdUi.over(mx, my, vx, vy, vw, vh)) { zoom = Math.max(1.0F, Math.min(6.0F, zoom * (syv > 0 ? 1.25F : 0.8F))); if (zoom == 1.0F) panX = panY = 0; return true; }
        if (WmdUi.over(mx, my, lx, vy, lw, vh)) { scroll = Math.max(0, Math.min(rows.size() - listRows(), scroll - (int) Math.signum(syv))); return true; }
        return super.mouseScrolled(mx, my, sxv, syv);
    }
}
