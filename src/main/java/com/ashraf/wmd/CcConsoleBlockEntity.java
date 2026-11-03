package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class CcConsoleBlockEntity extends CcModuleBlockEntity {

    private ListTag rows = new ListTag();
    private boolean global;
    private long armedUntil;
    private boolean refresh = true;
    private int[] intel = new int[0];
    private String intelNames = "";
    private long intelSnap = -1L, intelAt = -1000L;
    private static final int INTEL_TICKS = 200, INTEL_MAX = 96;

    public int[] intel() { return intel; }

    public String intelName(int i) {
        String[] n = intelNames.split(";", -1);
        return i >= 0 && i < n.length ? n[i] : "";
    }

    protected boolean wantsIntel() { return false; }

    private boolean refreshIntel(ServerLevel sl, long now) {
        ReconData.Entry e = ReconData.get(sl.getServer()).entry(getOwner());
        ReconData.Snapshot s = e != null && e.hasTarget ? e.snap : null;
        long t = s == null ? 0L : s.timeMs();
        if (t == intelSnap && now - intelAt < INTEL_TICKS) return false;
        intelSnap = t;
        intelAt = now;
        int[] ic = s == null ? new int[0] : s.icons();
        int n = Math.min(INTEL_MAX, ic.length / 3);
        int[] a = new int[n * 3];
        System.arraycopy(ic, 0, a, 0, n * 3);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) names.append(';');
            names.append(CcIntel.ownerName(sl.getServer(), a[i * 3], a[i * 3 + 1]));
        }
        String nm = names.toString();
        if (java.util.Arrays.equals(a, intel) && nm.equals(intelNames)) return false;
        intel = a;
        intelNames = nm;
        return true;
    }

    protected CcConsoleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int fe) { super(type, pos, state, fe); }

    protected abstract ListTag buildRows(ServerLevel level, UUID owner);

    protected abstract Component act(ServerPlayer player, CcBPayload p);

    public ListTag rows() { return rows; }

    public boolean global() { return global; }
    public boolean armed(long now) { return now < armedUntil; }
    public long armedUntil() { return armedUntil; }

    protected void arm() {
        if (level == null) return;
        armedUntil = level.getGameTime() + CcSpecB.ARMED_TICKS;
        setChanged();
        sync();
    }

    protected void refreshSoon() { refresh = true; }

    final void order(ServerPlayer player, CcBPayload p) {
        Component answer = act(player, p);
        if (answer != null) player.displayClientMessage(answer, false);
        refreshSoon();
    }

    @Override
    protected void moduleTick(Level level, long now) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!refresh && (now + (worldPosition.asLong() & 15L)) % 20 != 3) return;
        refresh = false;
        ListTag n = isOnline() ? buildRows(sl, getOwner()) : new ListTag();
        boolean g = isOnline() && globalReach(sl);
        boolean ic = isOnline() && wantsIntel() && refreshIntel(sl, now);
        if (!n.equals(rows) || g != global || ic) {
            rows = n;
            global = g;
            sync();
        }
    }

    private boolean globalReach(ServerLevel sl) {
        for (CcBuildingBlockEntity t : CommandCenter.activeBuildings(getOwner()))
            if (t.moduleCount(CcModule.COMMS_ARRAY) > 0) return CcReach.relay(sl.getServer(), getOwner());
        return false;
    }

    @Override
    protected void onlineChanged() { refreshSoon(); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("rows", rows);
        tag.putBoolean("global", global);
        tag.putLong("armed", armedUntil);
        if (wantsIntel()) { tag.putIntArray("intel", intel); tag.putString("intelN", intelNames); }
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("rows")) rows = tag.getList("rows", Tag.TAG_COMPOUND);
        global = tag.getBoolean("global");
        armedUntil = tag.getLong("armed");
        if (tag.contains("intel")) { int[] a = tag.getIntArray("intel"); intel = a.length % 3 == 0 ? a : new int[0]; intelNames = tag.getString("intelN"); }
    }
}
