package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class ResearchStationScreen extends MachineScreen implements WmdUi.Kit {

    private static final int PW = 116, ROW_Y0 = 16, ROW_H = 12;

    private static final int VIS = 9;
    private static final int NEEDS_Y = 133, TIME_Y = 173, UNLOCKS_Y = 186, STATE_Y = 220;

    private static final int TEXT = WmdUi.TEXT, GOOD = WmdUi.GOOD, BAD = WmdUi.BAD, WARN = WmdUi.WARN, GREY = WmdUi.TEXT_MUTED;
    private static final int ROW_SEL = 0xFF2D3A4A, ROW_HOVER = 0xFF263040;

    private final List<Object[]> tips = new ArrayList<>();

    private int scroll;
    private boolean scrolledToPick;

    public ResearchStationScreen(MachineMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    private ResearchStationBlockEntity station() { return menu.machine() instanceof ResearchStationBlockEntity s ? s : null; }

    private int px() { return leftPos - PW - 2; }

    @Override
    protected int sidePanelWidth() { return station() != null ? PW : 0; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        super.renderBg(g, partialTick, mouseX, mouseY);
        tips.clear();
        ResearchStationBlockEntity st = station();
        if (st == null) return;
        int x = px(), y = topPos;
        WmdUi.panel(g, x, y, PW, imageHeight);
        drawSelectedTab(g);
        g.drawString(font, Component.translatable("gui.wmd.research.title"), x + 7, y + 5, TEXT, false);
        WmdUi.divider(g, x + 6, y + 14, PW - 12);

        List<ResearchRecipes.Research> all = ResearchRecipes.all();
        ResearchRecipes.Research sel = st.research();
        if (!scrolledToPick) {
            scrolledToPick = true;
            for (int i = 0; sel != null && i < all.size(); i++) if (all.get(i).id().equals(sel.id())) scroll = clampScroll(i - VIS / 2);
        }
        int rx1 = rowRight();
        for (int i = scroll; i < all.size() && i < scroll + VIS; i++) {
            ResearchRecipes.Research r = all.get(i);
            int ry = y + ROW_Y0 + (i - scroll) * ROW_H;
            boolean picked = sel != null && sel.id().equals(r.id());
            boolean hover = mouseX >= x + 4 && mouseX < x + rx1 && mouseY >= ry && mouseY < ry + ROW_H - 1;
            if (picked) { g.fill(x + 4, ry, x + rx1, ry + ROW_H - 1, WmdUi.BLUE); g.fill(x + 5, ry + 1, x + rx1 - 1, ry + ROW_H - 2, ROW_SEL); }
            else if (hover) g.fill(x + 4, ry, x + rx1, ry + ROW_H - 1, ROW_HOVER);
            g.pose().pushPose(); g.pose().translate(x + 6, ry + 0.5F, 0); g.pose().scale(0.6875F, 0.6875F, 1.0F);
            g.renderItem(ResearchRecipes.chip(r), 0, 0);
            g.pose().popPose();
            g.drawString(font, Component.translatable("research.wmd." + r.id()), x + 20, ry + 2, TEXT, false);
            if (hover) tips.add(new Object[] { x + 4, ry, x + rx1, ry + ROW_H - 1, List.of(Component.translatable("research.wmd." + r.id()),
                    Component.translatable(picked ? "gui.wmd.research.picked" : "gui.wmd.research.click").withStyle(ChatFormatting.GRAY)) });
        }
        if (all.size() > VIS) {
            int ty0 = y + ROW_Y0, th = VIS * ROW_H - 1;
            int thumb = Math.max(8, th * VIS / all.size()), tpos = ty0 + (th - thumb) * scroll / (all.size() - VIS);
            g.fill(x + PW - 8, ty0, x + PW - 5, ty0 + th, ROW_HOVER);
            g.fill(x + PW - 8, tpos, x + PW - 5, tpos + thumb, mouseX >= x + PW - 10 && mouseX < x + PW - 3 && mouseY >= ty0 && mouseY < ty0 + th ? WmdUi.BLUE : GREY);
        }
        WmdUi.divider(g, x + 5, y + NEEDS_Y - 5, PW - 10);

        if (sel == null) {
            g.drawWordWrap(font, Component.translatable("gui.wmd.research.pick"), x + 7, y + NEEDS_Y, PW - 14, GREY);
            return;
        }

        g.drawString(font, Component.translatable("gui.wmd.research.needs"), x + 7, y + NEEDS_Y, TEXT, false);
        int nx = x + 7, ny = y + NEEDS_Y + 11;
        boolean allThere = true;
        long t = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0;
        for (int i = 0; i < sel.samples().size() && i < 3; i++) {
            ResearchRecipes.Sample s = sel.samples().get(i);
            ItemStack in = menu.slots.get(i).getItem();
            int have = ResearchRecipes.matches(s, in) ? in.getCount() : 0;
            allThere &= have >= s.count();
            g.renderItem(ResearchRecipes.icon(s, t), nx + 2, ny);
            count(g, nx + 10, ny + 18, have, s.count(), 1.0F);
            tips.add(new Object[] { nx, ny, nx + 20, ny + 27, List.of(ResearchRecipes.name(s), haveLine(have, s.count(), "")) });
            nx += 20;
        }
        if (sel.fluidMb() > 0) {
            int have = menu.tankAmount(0);
            allThere &= have >= sel.fluidMb();
            int colour = 0xFF808080;
            for (WmdFluids.Entry e : WmdFluids.ALL) if (("wmd:" + e.id()).equals(sel.fluidId())) colour = e.color() | 0xFF000000;
            WmdUi.well(g, nx + 12, ny + 2, 12, 12);
            g.fill(nx + 12, ny + 2, nx + 24, ny + 14, WmdUi.displayColour(colour));
            count(g, nx + 18, ny + 18, have, sel.fluidMb(), 0.75F);
            tips.add(new Object[] { nx, ny, nx + 36, ny + 27, List.of(ResearchRecipes.fluid(sel).getFluidType().getDescription(), haveLine(have, sel.fluidMb(), " mB")) });
            nx += 36;
        }
        int blanks = menu.slots.get(3).getItem().getCount();
        allThere &= blanks >= 1;
        g.renderItem(new ItemStack(Wmd.CHIP_BLANK.get()), nx + 2, ny);
        count(g, nx + 10, ny + 18, blanks, 1, 1.0F);
        tips.add(new Object[] { nx, ny, nx + 20, ny + 27, List.of(Wmd.CHIP_BLANK.get().getDescription(), haveLine(blanks, 1, "")) });

        int sec = sel.ticks() / 20;
        g.drawString(font, Component.translatable("gui.wmd.research.time", sec / 60 + ":" + String.format("%02d", sec % 60), sel.fePerTick() * 20), x + 7, y + TIME_Y, TEXT, false);

        g.drawString(font, Component.translatable("gui.wmd.research.unlocks"), x + 7, y + UNLOCKS_Y, TEXT, false);
        int ux = x + 7, uy = y + UNLOCKS_Y + 11;
        for (String id : sel.unlocks()) {
            if (ux + 16 > x + PW - 5) { ux = x + 7; uy += 17; }
            g.renderItem(new ItemStack(ResearchRecipes.item(id)), ux, uy);
            tips.add(new Object[] { ux, uy, ux + 17, uy + 17, List.of(ResearchRecipes.item(id).getDescription(), Component.translatable("gui.wmd.research.uses_chip").withStyle(ChatFormatting.GRAY)) });
            ux += 17;
        }

        int status = menu.status();
        Component state;
        int colour;
        if (status == 1) { state = Component.translatable("gui.wmd.research.writing", Math.max(0, menu.progress()) / 10); colour = GOOD; }
        else if (status == 2) { state = Component.translatable("gui.wmd.state.no_power"); colour = BAD; }
        else if (status == 3) { state = Component.translatable("gui.wmd.research.take_chip"); colour = WARN; }
        else if (!allThere) { state = Component.translatable("gui.wmd.research.missing"); colour = WARN; }
        else { state = Component.translatable("gui.wmd.research.ready"); colour = GOOD; }
        g.drawWordWrap(font, state, x + 7, y + STATE_Y, PW - 14, colour);
    }

    private void count(GuiGraphics g, int cx, int y, int have, int need, float scale) {
        String s = Math.min(have, 9999) + "/" + need;
        int c = have >= need ? GOOD : BAD;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, s, -font.width(s) / 2, 0, c, false);
        g.pose().popPose();
    }

    private static Component haveLine(int have, int need, String unit) {
        return Component.translatable("gui.wmd.research.have", have + unit, need + unit).withStyle(have >= need ? ChatFormatting.GREEN : ChatFormatting.RED);
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

    private boolean inPicker(double mx, double my) { return mx >= px() && mx < px() + PW && my >= topPos && my < topPos + imageHeight; }

    private int rowRight() { return ResearchRecipes.all().size() > VIS ? PW - 10 : PW - 4; }

    private int clampScroll(int s) { return Math.max(0, Math.min(Math.max(0, ResearchRecipes.all().size() - VIS), s)); }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (station() != null && inPicker(mx, my) && my < topPos + ROW_Y0 + VIS * ROW_H) {
            scroll = clampScroll(scroll - (int) Math.signum(sy));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ResearchStationBlockEntity st = station();
        if (st != null && inPicker(mx, my)) {
            List<ResearchRecipes.Research> all = ResearchRecipes.all();
            int x = px();
            int ty0 = topPos + ROW_Y0, th = VIS * ROW_H - 1;
            if (all.size() > VIS && mx >= x + PW - 10 && mx < x + PW - 3 && my >= ty0 && my < ty0 + th) {
                scroll = clampScroll((int) Math.round((my - ty0) / th * (all.size() - VIS)));
                return true;
            }
            for (int i = scroll; i < all.size() && i < scroll + VIS; i++) {
                int ry = topPos + ROW_Y0 + (i - scroll) * ROW_H;
                if (mx >= x + 4 && mx < x + rowRight() && my >= ry && my < ry + ROW_H - 1) {
                    String id = all.get(i).id();
                    st.selectClient(id);
                    PacketDistributor.sendToServer(new SetResearchPayload(st.getBlockPos(), id));
                    WmdUi.click();
                    break;
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int guiLeft, int guiTop, int button) {
        return !inPicker(mx, my) && super.hasClickedOutside(mx, my, guiLeft, guiTop, button);
    }
}
