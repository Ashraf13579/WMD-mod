package com.ashraf.wmd;

import java.util.List;

import net.minecraft.network.chat.Component;

public interface NavalPanel extends ModeMachine {

    String panelTitle();

    List<Component> panelLines();

    List<Component> panelRows();

    default int panelPicked() { return -1; }

    default boolean panelDanger(int row) { return false; }

    default boolean panelEnabled(int row) { return true; }

    default boolean panelClient(int row) { return false; }

    default int panelChipKind() { return WmdUi.K_IDLE; }

    default Component panelChip() { return Component.empty(); }

    @Override default int mode() { return panelPicked(); }
    @Override default int modeCount() { return panelRows().size(); }
    @Override default void setModeClient(int mode) {}
}
