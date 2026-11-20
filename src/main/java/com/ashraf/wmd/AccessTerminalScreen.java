package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public class AccessTerminalScreen extends Screen {

    static final int W = 320, H = 214, TAB_Y = 22, CT = 44, ROW = 13, PROWS = 9, MROWS = 10, RX = 150, RW = 162;
    private static final String P = "gui.wmd.access.";

    static AccessTerminalPayloads.State state;
    static int version;

    static void receive(AccessTerminalPayloads.State s) {
        state = s;
        version++;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AccessTerminalScreen open) || !open.pos.equals(s.pos())) mc.setScreen(new AccessTerminalScreen(s.pos()));
    }

    private final BlockPos pos;
    private boolean machinesTab;
    private String chosen;
    private int pScroll, mScroll, shown = -1;
    private EditBox nameBox;

    AccessTerminalScreen(BlockPos pos) {
        super(Component.translatable("block.wmd.access_terminal"));
        this.pos = pos;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }
    private List<AccessTerminalPayloads.PRow> players() { return state == null ? List.of() : state.players(); }
    private List<AccessTerminalPayloads.MRow> machines() { return state == null ? List.of() : state.machines(); }

    private AccessTerminalPayloads.PRow chosenRow() {
        for (AccessTerminalPayloads.PRow r : players()) if (r.id().equals(chosen)) return r;
        return null;
    }

    private void send(int action, String a, String b, int v) { PacketDistributor.sendToServer(new AccessTerminalPayloads.Act(pos, action, a, b, v)); }

    @Override
    protected void init() {
        int l = left(), t = top();
        if (chosenRow() == null) chosen = players().isEmpty() ? null : players().get(0).id();
        String typed = nameBox == null ? "" : nameBox.getValue();
        WmdUi.KitButton pt = addRenderableWidget(new WmdUi.KitButton(l + 8, t + TAB_Y, 76, 16, Component.translatable(P + "tab_players"), b -> { machinesTab = false; rebuildWidgets(); }));
        WmdUi.KitButton mt = addRenderableWidget(new WmdUi.KitButton(l + 88, t + TAB_Y, 76, 16, Component.translatable(P + "tab_machines"), b -> { machinesTab = true; rebuildWidgets(); }));
        pt.active = machinesTab;
        mt.active = !machinesTab;
        AccessTerminalPayloads.PRow c = chosenRow();
        if (!machinesTab) {
            nameBox = new EditBox(font, l + 8, t + 167, 90, 14, Component.translatable(P + "name"));
            nameBox.setMaxLength(16);
            nameBox.setHint(Component.translatable(P + "name"));
            nameBox.setValue(typed);
            addRenderableWidget(nameBox);
            addRenderableWidget(new WmdUi.KitButton(l + 102, t + 166, 40, 16, Component.translatable(P + "add"), b -> addTyped()));
            if (c != null) {
                int[] lv = { AccessStore.FRIEND, AccessStore.USE, AccessStore.CONFIGURE };
                for (int i = 0; i < 3; i++) {
                    final int v = lv[i];
                    WmdUi.KitButton b = addRenderableWidget(new WmdUi.KitButton(l + RX + i * 55, t + CT + 24, 51, 16, Component.translatable(P + "lv" + v),
                            x -> send(AccessTerminalPayloads.LEVEL, c.id(), "", v)));
                    b.active = c.level() != v;
                }
                WmdUi.KitButton all = addRenderableWidget(new WmdUi.KitButton(l + RX, t + CT + 58, 79, 16, Component.translatable(P + "scope_all"),
                        x -> send(AccessTerminalPayloads.SCOPE, c.id(), "", 1)));
                WmdUi.KitButton some = addRenderableWidget(new WmdUi.KitButton(l + RX + 83, t + CT + 58, 79, 16, Component.translatable(P + "scope_some"),
                        x -> send(AccessTerminalPayloads.SCOPE, c.id(), "", 0)));
                all.active = !c.all();
                some.active = c.all();
                addRenderableWidget(new WmdUi.KitButton(l + RX, t + 164, RW, 16, Component.translatable(P + "remove", c.name()),
                        x -> send(AccessTerminalPayloads.REMOVE, c.id(), "", 0), true));
            }
        } else if (c != null && !c.all()) {
            addRenderableWidget(new WmdUi.KitButton(l + W - 8 - 104, t + CT - 2, 50, 14, Component.translatable(P + "all_on"), x -> send(AccessTerminalPayloads.ALL_MACHINES, c.id(), "", 1)));
            addRenderableWidget(new WmdUi.KitButton(l + W - 8 - 50, t + CT - 2, 50, 14, Component.translatable(P + "all_off"), x -> send(AccessTerminalPayloads.ALL_MACHINES, c.id(), "", 0)));
        }
        shown = version;
    }

    private void addTyped() {
        if (nameBox == null || nameBox.getValue().isBlank()) return;
        send(AccessTerminalPayloads.ADD, nameBox.getValue().trim(), "", 0);
        nameBox.setValue("");
    }

    @Override
    public void tick() {
        super.tick();
        if (version != shown) rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nameBox != null && nameBox.isFocused() && (key == 257 || key == 335)) { addTyped(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int l = left(), t = top();
        WmdUi.panel(g, l, t, W, H);
        WmdUi.drawFit(g, font, title, l + 8, t + 6, W - 16 - WmdHelp.ROOM, WmdUi.TEXT);
        WmdUi.divider(g, l + 8, t + 17, W - 16);
        WmdHelp.button(g, l + W - 8 - WmdHelp.SIZE, t + 4);
        if (state != null) WmdUi.drawFit(g, font, Component.translatable(P + "owner", state.owner()), l + 172, t + TAB_Y + 4, W - 180, WmdUi.TEXT_MUTED);
        if (machinesTab) drawMachines(g, l, t, mx, my); else drawPlayers(g, l, t, mx, my);
    }

    private static Component levelName(int lv) { return Component.translatable(P + "lv" + lv); }

    private void drawPlayers(GuiGraphics g, int l, int t, int mx, int my) {
        List<AccessTerminalPayloads.PRow> ps = players();
        int lx = l + 8, ly = t + CT, lw = 134;
        WmdUi.well(g, lx, ly, lw, PROWS * ROW);
        pScroll = Math.max(0, Math.min(pScroll, Math.max(0, ps.size() - PROWS)));
        if (ps.isEmpty()) WmdUi.drawFit(g, font, Component.translatable(P + "none"), lx + 3, ly + 3, lw - 6, WmdUi.TEXT_DIM);
        for (int i = 0; i < PROWS && pScroll + i < ps.size(); i++) {
            AccessTerminalPayloads.PRow r = ps.get(pScroll + i);
            int y = ly + i * ROW;
            boolean sel = r.id().equals(chosen), hover = WmdUi.over(mx, my, lx, y, lw, ROW);
            if (sel || hover) g.fill(lx, y, lx + lw, y + ROW, sel ? 0xFF2A3A52 : 0xFF202833);
            WmdUi.drawFit(g, font, Component.literal(r.name()), lx + 3, y + 3, 78, WmdUi.TEXT);
            WmdUi.drawFit(g, font, levelName(r.level()), lx + 84, y + 3, lw - 87, r.level() == AccessStore.FRIEND ? WmdUi.TEXT_MUTED : WmdUi.BLUE_LIGHT);
        }
        if (ps.size() > PROWS) WmdUi.drawFit(g, font, Component.translatable(P + "more", pScroll + 1, Math.min(ps.size(), pScroll + PROWS), ps.size()), lx, t + 184, lw, WmdUi.TEXT_DIM);
        AccessTerminalPayloads.PRow c = chosenRow();
        int rx = l + RX;
        if (c == null) { WmdUi.drawFit(g, font, Component.translatable(P + "pick_player"), rx, t + CT, RW, WmdUi.TEXT_MUTED); return; }
        WmdUi.drawFit(g, font, Component.literal(c.name()), rx, t + CT, RW, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "level"), rx, t + CT + 12, RW, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "applies"), rx, t + CT + 46, RW, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "lv" + c.level() + ".tip"), rx, t + 124, RW, WmdUi.TEXT);
        WmdUi.drawFit(g, font, Component.translatable(P + "lv" + c.level() + ".tip2"), rx, t + 135, RW, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, c.all() ? Component.translatable(P + "scope_all.tip") : Component.translatable(P + "scope_some.tip", c.chosen().size()), rx, t + 146, RW, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(P + "hint"), l + 8, t + H - 12, W - 16, WmdUi.TEXT_DIM);
    }

    private void drawMachines(GuiGraphics g, int l, int t, int mx, int my) {
        List<AccessTerminalPayloads.MRow> ms = machines();
        AccessTerminalPayloads.PRow c = chosenRow();
        boolean ticks = c != null && !c.all();
        Component head = c == null ? Component.translatable(P + "for_none") : c.all() ? Component.translatable(P + "for_all", c.name())
                : Component.translatable(P + "for_some", c.name(), levelName(c.level()));
        WmdUi.drawFit(g, font, head, l + 8, t + CT + 1, ticks ? W - 16 - 110 : W - 16, ticks ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
        int lx = l + 8, ly = t + CT + 16, lw = W - 16;
        WmdUi.well(g, lx, ly, lw, MROWS * ROW);
        mScroll = Math.max(0, Math.min(mScroll, Math.max(0, ms.size() - MROWS)));
        if (ms.isEmpty()) WmdUi.drawFit(g, font, Component.translatable(P + "no_machines"), lx + 3, ly + 3, lw - 6, WmdUi.TEXT_DIM);
        for (int i = 0; i < MROWS && mScroll + i < ms.size(); i++) {
            int idx = mScroll + i;
            AccessTerminalPayloads.MRow m = ms.get(idx);
            int y = ly + i * ROW;
            if (WmdUi.over(mx, my, lx, y, lw, ROW)) g.fill(lx, y, lx + lw, y + ROW, 0xFF202833);
            int x = lx + 3;
            if (ticks) {
                boolean on = c.chosen().contains(idx);
                g.fill(x, y + 2, x + 9, y + 11, 0xFF5A6675);
                g.fill(x + 1, y + 3, x + 8, y + 10, on ? WmdUi.BLUE : WmdUi.WELL);
                x += 13;
            }
            WmdUi.drawFit(g, font, blockName(m.block()), x, y + 3, 128 - (ticks ? 13 : 0), WmdUi.TEXT);
            Component where = Component.translatable(m.craft() ? P + "where_craft" : P + "where", m.x(), m.y(), m.z(), m.dim());
            WmdUi.drawFit(g, font, where, lx + 136, y + 3, lw - 136 - 50, WmdUi.TEXT_MUTED);
            int bx = lx + lw - 47;
            boolean hb = WmdUi.over(mx, my, bx, y + 1, 44, 11);
            WmdUi.button(g, font, bx, y + 1, 44, 11, Component.translatable(m.pub() ? P + "public" : P + "private"), m.pub() ? WmdUi.B_DANGER : WmdUi.B_NORMAL, hb);
        }
        int from = ms.isEmpty() ? 0 : mScroll + 1, to = Math.min(ms.size(), mScroll + MROWS);
        Component foot = state != null && state.total() > ms.size() ? Component.translatable(P + "shown_capped", from, to, ms.size(), state.total())
                : Component.translatable(P + "shown", from, to, ms.size());
        WmdUi.drawFit(g, font, foot, l + 8, t + H - 12, W - 16, WmdUi.TEXT_DIM);
    }

    private static Component blockName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Component.literal(id) : BuiltInRegistries.BLOCK.get(rl).getName();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int l = left(), t = top();
        if (!machinesTab) {
            int lx = l + 8, ly = t + CT;
            if (WmdUi.over(mx, my, lx, ly, 134, PROWS * ROW)) {
                int i = pScroll + (int) ((my - ly) / ROW);
                if (i >= 0 && i < players().size()) { chosen = players().get(i).id(); rebuildWidgets(); }
                return true;
            }
            return false;
        }
        int lx = l + 8, ly = t + CT + 16, lw = W - 16;
        if (!WmdUi.over(mx, my, lx, ly, lw, MROWS * ROW)) return false;
        int idx = mScroll + (int) ((my - ly) / ROW);
        if (idx < 0 || idx >= machines().size()) return true;
        AccessTerminalPayloads.MRow m = machines().get(idx);
        int y = ly + (idx - mScroll) * ROW;
        if (WmdUi.over(mx, my, lx + lw - 47, y + 1, 44, 11)) { send(AccessTerminalPayloads.PUBLIC, "", m.key(), 0); return true; }
        AccessTerminalPayloads.PRow c = chosenRow();
        if (c != null && !c.all() && mx < lx + 136) send(AccessTerminalPayloads.TOGGLE, c.id(), m.key(), 0);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int step = dy > 0 ? -1 : dy < 0 ? 1 : 0;
        if (machinesTab) mScroll += step * 3; else pScroll += step;
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
