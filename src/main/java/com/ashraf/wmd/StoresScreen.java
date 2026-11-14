package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class StoresScreen extends Screen {

    static final int PW = 220, PH = 150, ROW = 18;
    private static final int[] GROUP_COL = { 0xFF3A7BD5, 0xFFD58A3A, 0xFF3AC2D5, 0xFF8FE3A8 };
    private final CompoundTag tag;
    private int x0, y0;

    private StoresScreen(CompoundTag tag) {
        super(Component.translatable("gui.wmd.stores.title"));
        this.tag = tag;
    }

    static void show(CompoundTag tag) { Minecraft.getInstance().setScreen(new StoresScreen(tag)); }

    @Override
    protected void init() {
        x0 = (width - PW) / 2;
        y0 = (height - PH) / 2;
        if (tag.contains("depot")) {
            WmdUi.KitButton b = new WmdUi.KitButton(x0 + PW - 96, y0 + PH - 22, 88, 14, Component.translatable("gui.wmd.stores.stock_here"), x -> {
                CompoundTag a = new CompoundTag();
                a.putString("a", "stock");
                a.putUUID("ship", tag.getUUID("ship"));
                PacketDistributor.sendToServer(new LoadoutPayload.Act(BlockPos.of(tag.getLong("depot")), a));
                WmdUi.confirm();
                onClose();
            });
            b.active = tag.getBoolean("parked") && tag.getInt("depotMode") == 0;
            addRenderableWidget(b);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        WmdUi.panel(g, x0, y0, PW, PH);
        boolean dep = tag.contains("depot");
        WmdUi.header(g, font, x0, y0, PW, new ItemStack(AirBlocks.JET_COCKPIT_ITEM.get()), title,
                Component.translatable(dep ? (tag.getInt("depotMode") != 0 ? "gui.wmd.stores.chip_stocking" : "gui.wmd.stores.chip_depot") : "gui.wmd.stores.chip_away"),
                dep ? WmdUi.K_GOOD : WmdUi.K_IDLE, dep && tag.getInt("depotMode") != 0);
        ListTag gs = tag.getList("groups", Tag.TAG_COMPOUND);
        int sel = tag.getInt("sel"), y = y0 + 26;
        for (int i = 0; i < gs.size() && i < 4; i++, y += ROW) {
            CompoundTag e = gs.getCompound(i);
            if (i == sel) { g.fill(x0 + 8, y - 2, x0 + PW - 8, y + ROW - 4, 0xFF2D3A4A); g.fill(x0 + 8, y - 2, x0 + 9, y + ROW - 4, GROUP_COL[i]); }
            g.drawString(font, String.valueOf(i + 1), x0 + 14, y + 3, GROUP_COL[i], false);
            ItemStack it = e.contains("item") ? ItemStack.parseOptional(minecraft.level.registryAccess(), e.getCompound("item")) : ItemStack.EMPTY;
            if (!it.isEmpty()) g.renderItem(it, x0 + 24, y - 1);
            int n = e.getInt("n");
            Component name = n == 0 ? Component.translatable("gui.wmd.stores.no_group", i + 1)
                    : Component.translatable("gui.wmd.stores.group", Component.translatable("gui.wmd.loadout.gmode" + e.getInt("mode")), e.getInt("loaded"), n);
            WmdUi.drawFit(g, font, name, x0 + 44, y + 3, PW - 56, n == 0 ? WmdUi.TEXT_DIM : e.getInt("loaded") > 0 ? WmdUi.TEXT : WmdUi.WARN);
        }
        WmdUi.divider(g, x0 + 8, y0 + PH - 46, PW - 16);
        int gun = tag.getInt("gun");
        Component line = tag.contains("rockets")
                ? Component.translatable("gui.wmd.stores.line_rockets", gun < 0 ? "-" : String.valueOf(gun), tag.getInt("rockets") + "/" + tag.getInt("rocketTubes"), tag.getInt("flares"), tag.getInt("chaff"))
                : Component.translatable("gui.wmd.stores.line", gun < 0 ? "-" : String.valueOf(gun), tag.getInt("flares"), tag.getInt("chaff"));
        WmdUi.drawFit(g, font, line, x0 + 8, y0 + PH - 40, PW - 16, WmdUi.TEXT_MUTED);
        WmdUi.drawFit(g, font, Component.translatable(dep ? (tag.getBoolean("parked") ? "gui.wmd.stores.hint_depot" : "gui.wmd.stores.hint_air") : "gui.wmd.stores.hint_away"),
                x0 + 8, y0 + PH - 18, PW - 112, WmdUi.TEXT_DIM);
        super.render(g, mx, my, pt);
    }
}
