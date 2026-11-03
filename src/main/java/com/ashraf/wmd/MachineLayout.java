package com.ashraf.wmd;

public final class MachineLayout {

    private MachineLayout() {}

    public static final int GAUGE_Y = 22, GAUGE_H = 60, ENERGY_X = 10, ENERGY_W = 14, FIRST_TANK_X = 36;
    public static final int SLOT_Y = GAUGE_Y + 20;
    public static final int SLOT_AREA_Y = SLOT_Y + 1;
    public static final int ITEM_EXTRA = 90;

    public static int layoutWidth(int tanks) { return tanks >= 6 ? 240 : tanks > 3 ? 216 : 176; }
    public static int layoutStep(int tanks) { return tanks >= 8 ? 21 : tanks >= 7 ? 27 : tanks >= 6 ? 30 : tanks > 3 ? 25 : 44; }
    public static int layoutTankW(int tanks) { return tanks > 3 ? 20 : 34; }

    public static int slotAreaX(GuiMachine m, int t) {
        int count = Math.min(m.guiTanks().size(), MachineMenu.MAX_TANKS);
        int tw = layoutTankW(m.guiTanks().size()), step = layoutStep(m.guiTanks().size()), width = layoutWidth(m.guiTanks().size());
        int left = m.guiEnergy() != null ? FIRST_TANK_X : 14;
        int total = (count - 1) * step + tw;
        int space = width - 10 - left;
        return left + (space - total) / 2 + t * step + tw / 2 - 9 + 1;
    }
}
