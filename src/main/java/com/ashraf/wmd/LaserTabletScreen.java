package com.ashraf.wmd;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class LaserTabletScreen extends Screen {

    private static final int W = 220, H = 150;
    private static LaserTabletScreen open;
    private TabletInfoPayload info;
    private long armedAt = -1;
    private int ticks;

    private LaserTabletScreen(TabletInfoPayload info) {
        super(Component.translatable("gui.wmd.tablet.title"));
        this.info = info;
    }

    public static void show(TabletInfoPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == open && open != null) { open.info = p; return; }
        open = new LaserTabletScreen(p);
        mc.setScreen(open);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() { if (open == this) open = null; }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) PacketDistributor.sendToServer(new TabletFirePayload(info.mainHand(), false));
        if (armedAt >= 0 && ticks - armedAt > 60) armedAt = -1;
    }

    private int x0() { return (width - W) / 2; }
    private int y0() { return (height - H) / 2; }

    private static Component line(String s) {
        String[] p = s.split("\\|");
        Object[] args = new Object[p.length - 1];
        System.arraycopy(p, 1, args, 0, args.length);
        if (p[0].equals("gui.wmd.tablet.missile_laser") || p[0].equals("gui.wmd.tablet.missile_no_seeker"))
            if (args.length > 1) args[1] = Component.translatable("warhead.wmd." + args[1]);
        return Component.translatable(p[0], args);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        int x = x0(), y = y0();
        WmdUi.panel(g, x, y, W, H);
        WmdUi.header(g, font, x, y, W, new ItemStack(Wmd.LASER_TABLET.get()), title,
                Component.translatable(info.ready() ? "gui.wmd.tablet.ready" : "gui.wmd.tablet.not_ready"), info.ready() ? WmdUi.K_GOOD : WmdUi.K_WARN, info.ready());
        int ly = y + 28;
        List<String> lines = info.lines();
        for (String s : lines) { WmdUi.drawFit(g, font, line(s), x + 10, ly, W - 20, WmdUi.TEXT); ly += 12; }
        if (!info.ready()) WmdUi.drawFit(g, font, Component.translatable(info.why()), x + 10, ly + 4, W - 20, WmdUi.BAD);
        int by = y + H - 26;
        boolean armed = armedAt >= 0;
        int fw = W - 20 - 140;
        WmdUi.button(g, font, x + 10, by, fw, 16, Component.translatable(armed ? "gui.wmd.tablet.confirm" : "gui.wmd.tablet.fire"),
                !info.ready() ? WmdUi.B_DISABLED : armed ? WmdUi.B_DANGER : WmdUi.B_NORMAL, WmdUi.over(mx, my, x + 10, by, fw, 16));
        WmdUi.button(g, font, x + 14 + fw, by, 66, 16, Component.translatable("gui.wmd.tablet.drones"), WmdUi.B_NORMAL, WmdUi.over(mx, my, x + 14 + fw, by, 66, 16));
        WmdUi.button(g, font, x + 84 + fw, by, 66, 16, Component.translatable("gui.wmd.tablet.refresh"), WmdUi.B_NORMAL, WmdUi.over(mx, my, x + 84 + fw, by, 66, 16));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = x0(), y = y0(), by = y + H - 26, fw = W - 20 - 140;
        if (button == 0 && WmdUi.over(mx, my, x + 10, by, fw, 16) && info.ready()) {
            if (armedAt < 0) { armedAt = ticks; WmdUi.click(); }
            else { armedAt = -1; WmdUi.confirm(); PacketDistributor.sendToServer(new TabletFirePayload(info.mainHand(), true)); }
            return true;
        }
        if (button == 0 && WmdUi.over(mx, my, x + 14 + fw, by, 66, 16)) { WmdUi.click(); PacketDistributor.sendToServer(new DroneHiveTabletPayload(info.mainHand())); return true; }
        if (button == 0 && WmdUi.over(mx, my, x + 84 + fw, by, 66, 16)) { WmdUi.click(); PacketDistributor.sendToServer(new TabletFirePayload(info.mainHand(), false)); return true; }
        return super.mouseClicked(mx, my, button);
    }
}
