package com.ashraf.wmd;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class FlightCheckScreen extends Screen {

    static final int PW = 224, PH = 184, CW = 103;
    private static final String P = "gui.wmd.flightcheck.";
    private CompoundTag t;
    private int age;

    public FlightCheckScreen(CompoundTag t) {
        super(Component.translatable(P + "title"));
        this.t = t;
    }

    static void show(CompoundTag t) {
        if (!AirGate.AVAILABLE || t == null || !t.hasUUID("id")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FlightCheckScreen s && t.getUUID("id").equals(s.ship())) s.t = t;
        else mc.setScreen(new FlightCheckScreen(t));
    }

    UUID ship() { return t.hasUUID("id") ? t.getUUID("id") : null; }

    private int px() { return (width - PW) / 2; }

    private int py() { return (height - PH) / 2; }

    @Override
    protected void init() {
        int half = (PW - 20) / 2;
        addRenderableWidget(new WmdUi.KitButton(px() + 8, py() + PH - 22, half, 14, Component.translatable(P + "airframe"),
                b -> {
                    if (ship() != null) PacketDistributor.sendToServer(new AirframePayload.Ask(AirframeRecords.A_OPEN, ship(), null, 0));
                }));
        addRenderableWidget(new WmdUi.KitButton(px() + 12 + half, py() + PH - 22, half, 14, Component.translatable(P + "close"), b -> onClose()));
    }

    @Override
    public void tick() {
        super.tick();
        if (++age % Balance.FC_REFRESH_TICKS == 0 && ship() != null) PacketDistributor.sendToServer(new FlightCheckPayload.Ask(ship()));
    }

    private double tw() { double w = t.getDouble("w"); return w > 0.0 ? t.getDouble("th") / w : 0.0; }

    private int engines() { return t.getInt("fan") + t.getInt("tf") + t.getInt("prop") + t.getInt("vtol"); }

    private double vtw() { double w = t.getDouble("w"); return w > 0.0 ? t.getDouble("vt") / w : 0.0; }

    private boolean hovers() { return vtw() >= 1.0; }

    private double fuelSeconds() { double b = t.getDouble("burn"); return b > 0.0 ? t.getInt("fuel") / b / 20.0 : -1.0; }

    private int verdict(String[] key) {
        if (engines() == 0) { key[0] = "adv_no_engine"; return WmdUi.K_BAD; }
        if (tw() < Balance.FC_TW_MIN && !hovers()) { key[0] = t.getDouble("vt") > 0.0 ? "adv_heavy_vtol" : "adv_heavy"; return WmdUi.K_BAD; }
        if (t.getInt("wing") == 0 && !hovers()) { key[0] = "adv_no_wing"; return WmdUi.K_WARN; }
        if (t.getDouble("lift") < t.getDouble("w") && !hovers()) { key[0] = "adv_small_wing"; return WmdUi.K_WARN; }
        if (t.getDouble("dem") > 0.0 && t.getInt("core") == 0) { key[0] = "adv_no_core"; return WmdUi.K_WARN; }
        if (t.getInt("tf") > 0 && fuelSeconds() < Balance.FC_FUEL_WARN_S) { key[0] = "adv_fuel"; return WmdUi.K_WARN; }
        key[0] = "adv_ready";
        return WmdUi.K_GOOD;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int x = px(), y = py();
        WmdUi.panel(g, x, y, PW, PH);
        String[] adv = { "" };
        int kind = verdict(adv);
        String name = t.getString("n");
        Component title = name.isEmpty() ? Component.translatable(P + "title") : Component.translatable(P + "title_of", name);
        Component chip = Component.translatable(P + (kind == WmdUi.K_GOOD ? "chip_ready" : kind == WmdUi.K_WARN ? "chip_check" : "chip_grounded"));
        ItemStack icon = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flight_check_tablet")));
        WmdUi.header(g, font, x, y, PW, icon, title, chip, kind, kind == WmdUi.K_BAD);

        int c1 = x + 6, c2 = x + 6 + CW + 6;
        double tw = tw();
        card(g, c1, y + 26, "mass", Component.translatable(P + "mass_v", num(t.getDouble("m")), t.getInt("b")), WmdUi.TEXT);
        double vtw = vtw();
        card(g, c2, y + 26, "tw", t.getDouble("vt") > 0.0 ? Component.translatable(P + "tw_vtol", String.format(Locale.ROOT, "%.2f", tw), String.format(Locale.ROOT, "%.2f", vtw))
                        : Component.translatable(P + "tw_v", String.format(Locale.ROOT, "%.2f", tw)),
                tw >= Balance.FC_TW_GOOD || vtw >= 1.2 ? WmdUi.GOOD : tw >= Balance.FC_TW_MIN || vtw >= 1.0 ? WmdUi.WARN : WmdUi.BAD);
        double w = t.getDouble("w"), lift = t.getDouble("lift");
        int pct = w > 0.0 ? (int) Math.round(100.0 * lift / w) : 0;
        card(g, c1, y + 50, "lift", t.getInt("wing") == 0 ? Component.translatable(P + "lift_none") : Component.translatable(P + "lift_v", pct, (int) Math.round(t.getDouble("cr"))),
                pct >= 100 ? WmdUi.GOOD : pct >= 70 ? WmdUi.WARN : WmdUi.BAD);
        double stall = t.getDouble("stall");
        card(g, c2, y + 50, "stall", stall < 0.0 ? Component.literal("-") : Component.translatable(P + "speed_v", (int) Math.round(stall)),
                stall < 0.0 || stall > t.getDouble("top") ? WmdUi.BAD : stall > t.getDouble("cr") ? WmdUi.WARN : WmdUi.TEXT);
        card(g, c1, y + 74, "power", powerLine(), powerColour());
        double fs = fuelSeconds();
        card(g, c2, y + 74, "fuel", t.getInt("tf") == 0 && t.getInt("core") == 0 ? Component.translatable(P + "fuel_none")
                        : fs < 0.0 ? Component.translatable(P + "fuel_mb", t.getInt("fuel")) : Component.translatable(P + "fuel_v", mins(fs), t.getInt("fuel")),
                fs >= 0.0 && fs < Balance.FC_FUEL_WARN_S ? WmdUi.WARN : WmdUi.TEXT);
        int agl = t.getInt("agl");
        card(g, c1, y + 98, "now", agl < 0 ? Component.translatable(P + "now_v", (int) Math.round(t.getDouble("v")), t.getInt("alt"))
                : Component.translatable(P + "now_agl", (int) Math.round(t.getDouble("v")), t.getInt("alt"), agl), WmdUi.BLUE_LIGHT);
        card(g, c2, y + 98, "engines", Component.translatable(P + "engines_v2", t.getInt("tf") + t.getInt("fan"), t.getInt("prop"), t.getInt("vtol"), t.getInt("jato")), engines() == 0 ? WmdUi.BAD : WmdUi.TEXT);

        WmdUi.divider(g, x + 5, y + 126, PW - 10);
        int col = kind == WmdUi.K_GOOD ? WmdUi.GOOD : kind == WmdUi.K_WARN ? WmdUi.WARN : WmdUi.BAD;
        WmdUi.drawFit(g, font, Component.translatable(P + adv[0], (int) Math.round(t.getDouble("top"))), x + 8, y + 131, PW - 16, col);
        WmdUi.drawFit(g, font, Component.translatable(P + "note"), x + 8, y + 143, PW - 16, WmdUi.TEXT_DIM);
    }

    private void card(GuiGraphics g, int x, int y, String label, Component value, int colour) {
        WmdUi.card(g, font, x, y, CW, Component.translatable(P + label), value, colour);
    }

    private Component powerLine() {
        int cores = t.getInt("core");
        double dem = t.getDouble("dem"), apu = t.getDouble("apu");
        if (cores == 0) return Component.translatable(dem > 0.0 ? P + "power_nocore" : P + "power_none");
        int fs = t.getInt("fs"), fc = t.getInt("fc");
        int chargePct = fc > 0 ? Math.round(100.0F * fs / fc) : 0;
        double net = dem - apu;
        if (net <= 0.0) return Component.translatable(P + "power_apu", (int) Math.round(dem), chargePct);
        return Component.translatable(P + "power_v", (int) Math.round(dem), mins(fs / net / 20.0), chargePct);
    }

    private int powerColour() {
        if (t.getInt("core") == 0) return t.getDouble("dem") > 0.0 ? WmdUi.WARN : WmdUi.TEXT_MUTED;
        double net = t.getDouble("dem") - t.getDouble("apu");
        return net <= 0.0 ? WmdUi.GOOD : t.getInt("fs") / net / 20.0 < Balance.FC_FUEL_WARN_S ? WmdUi.WARN : WmdUi.TEXT;
    }

    private static String num(double v) { return v >= 10_000 ? String.format(Locale.ROOT, "%.0fk", v / 1000.0) : String.format(Locale.ROOT, "%.0f", v); }

    private static String mins(double s) { return s >= 600 ? String.format(Locale.ROOT, "%.0f min", s / 60.0) : String.format(Locale.ROOT, "%d:%02d", (int) s / 60, (int) s % 60); }

    @Override
    public boolean isPauseScreen() { return false; }
}
