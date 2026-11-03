package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class CcWarningBoardBlockEntity extends CcModuleBlockEntity {

    private List<CommandCenter.Warning> warnings = List.of();

    public static final int MODE_INCOMING = 0, MODE_OUTBOUND = 1, MAX_OUT = 16, OUT_LINGER_TICKS = 200;
    private int mode = MODE_INCOMING;
    private List<Out> outbound = List.of();

    private boolean domeDown;

    public boolean domeDown() { return domeDown; }

    public record Out(long id, int ox, int oz, int tx, int tz, long launchGt, long impactGt, int tier, String warhead, boolean sat) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id); t.putInt("ox", ox); t.putInt("oz", oz); t.putInt("tx", tx); t.putInt("tz", tz);
            t.putLong("l", launchGt); t.putLong("i", impactGt); t.putInt("tier", tier); t.putString("w", warhead); t.putBoolean("s", sat);
            return t;
        }
        static Out load(CompoundTag t) {
            return new Out(t.getLong("id"), t.getInt("ox"), t.getInt("oz"), t.getInt("tx"), t.getInt("tz"), t.getLong("l"), t.getLong("i"), t.getInt("tier"),
                    t.getString("w"), t.getBoolean("s"));
        }
    }

    public int mode() { return mode; }
    public List<Out> outbound() { return outbound; }

    void setMode(int m) {
        m = m == MODE_OUTBOUND ? MODE_OUTBOUND : MODE_INCOMING;
        if (m == mode) return;
        mode = m;
        if (mode == MODE_INCOMING) outbound = List.of();
        else if (level instanceof net.minecraft.server.level.ServerLevel sl) outbound = List.copyOf(collectOut(sl, level.getGameTime()));
        setChanged();
        sync();
    }

    private List<Out> collectOut(net.minecraft.server.level.ServerLevel sl, long now) {
        java.util.UUID o = getOwner();
        List<Out> l = new ArrayList<>();
        if (o == null || !isOnline()) return l;
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (Flight f : FlightManager.get(sl).flights()) {
            if (f.build() != null && f.build().isInterceptor()) continue;
            if (!f.isOwnedBy(o, getOwnerName()) || now > f.endTime()) continue;
            boolean sat = Satellites.toOrbit(f.build());
            l.add(new Out(f.id(), (int) Math.round(f.ox()), (int) Math.round(f.oz()), f.tx(), f.tz(), f.start(), f.endTime(), f.tier(),
                    f.build() != null ? f.build().warheadType() : "", sat));
            ids.add(f.id());
        }
        for (Out old : outbound) if (!ids.contains(old.id()) && !old.sat() && now <= old.impactGt() + OUT_LINGER_TICKS) l.add(old);
        l.sort((a, b) -> Long.compare(a.launchGt(), b.launchGt()));
        while (l.size() > MAX_OUT) l.remove(0);
        return l;
    }

    public CcWarningBoardBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CC_WARNING_BOARD_BE.get(), pos, state, Balance.CC_WARNING_FE); }

    @Override public CcModule kind() { return CcModule.WARNING_BOARD; }

    public List<CommandCenter.Warning> warnings() { return warnings; }

    void setWarnings(List<CommandCenter.Warning> l) {
        if (l.equals(warnings)) return;
        warnings = List.copyOf(l);
        setChanged();
        sync();
        updateAlert();
    }

    public boolean alert() { return isOnline() && (!warnings.isEmpty() || sirens()); }

    private boolean sirens() {
        CcBuildingBlockEntity b = building();
        return b != null && b.sealed() && b.defcon() <= 2;
    }

    public int defcon() {
        CcBuildingBlockEntity b = building();
        return b != null ? b.defcon() : CcDefcon.PEACE;
    }

    private void updateAlert() {
        if (level == null || level.isClientSide) return;
        BlockState st = level.getBlockState(worldPosition);
        if (st.hasProperty(CcModuleBlock.WarningBoard.ALERT) && st.getValue(CcModuleBlock.WarningBoard.ALERT) != alert())
            level.setBlock(worldPosition, st.setValue(CcModuleBlock.WarningBoard.ALERT, alert()), 3);
    }

    private boolean pulled;

    @Override
    protected void moduleTick(Level level, long now) {
        if (!pulled) {
            pulled = true;
            setWarnings(CommandCenter.warnings(getOwner()));
        }
        if (now % 20 == 9) updateAlert();
        if (now % 20 == 6) {
            boolean d = isOnline() && IronDome.downFor(getOwner());
            if (d != domeDown) { domeDown = d; sync(); }
        }
        if (now % 20 == 4 && level instanceof net.minecraft.server.level.ServerLevel sl) {
            List<Out> l = mode == MODE_OUTBOUND ? collectOut(sl, now) : List.of();
            if (!l.equals(outbound)) {
                outbound = List.copyOf(l);
                sync();
            }
        }
    }

    @Override protected void onlineChanged() { updateAlert(); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        boolean on = isOnline() && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean warn = on && !warnings.isEmpty();
        boolean out = on && !warn && mode == MODE_OUTBOUND && !outbound.isEmpty();
        long step = (long) (tt / 4.0);

        int dc = defcon();
        boolean quiet = on && !warn && !out, flash = ((long) (tt / 8.0)) % 2 == 0;
        int redN = !quiet ? 0 : dc <= 1 ? 9 : (5 - dc) * 2;
        for (int i = 0; i < 9; i++) {
            final boolean dcRed = i < redN, dcLit = dcRed && (dc > 1 || flash);
            if (dcRed) {
                final boolean hasR = model.getBone("lamp" + i + CcModelSpec.LAMP_RED_SUFFIX).isPresent();
                final int k0 = i;
                model.getBone("lamp" + k0).ifPresent(b -> b.setHidden(hasR || !dcLit));
                model.getBone("lamp" + k0 + CcModelSpec.LAMP_RED_SUFFIX).ifPresent(b -> b.setHidden(!dcLit));
                continue;
            }
            final boolean blink = warn && ((step + i) % 9 < 5);
            final boolean run = out && ((step + i) % 9 >= 3);
            final boolean hasRed = model.getBone("lamp" + i + CcModelSpec.LAMP_RED_SUFFIX).isPresent();
            final int k = i;
            model.getBone("lamp" + k).ifPresent(b -> b.setHidden(!on || (warn && (hasRed || !blink)) || (out && !run)));
            model.getBone("lamp" + k + CcModelSpec.LAMP_RED_SUFFIX).ifPresent(b -> b.setHidden(!blink));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("warnings", CommandCenter.saveList(warnings));
        tag.putInt("mode", mode);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        warnings = List.copyOf(CommandCenter.loadList(tag.getList("warnings", Tag.TAG_COMPOUND)));
        mode = tag.getInt("mode") == MODE_OUTBOUND ? MODE_OUTBOUND : MODE_INCOMING;
        domeDown = tag.getBoolean("domeDown");
        if (tag.contains("out", Tag.TAG_LIST)) {
            List<Out> l = new ArrayList<>();
            for (Tag x : tag.getList("out", Tag.TAG_COMPOUND)) if (x instanceof CompoundTag c) l.add(Out.load(c));
            outbound = List.copyOf(l);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.put("warnings", CommandCenter.saveList(warnings));
        tag.putInt("mode", mode);
        net.minecraft.nbt.ListTag out = new net.minecraft.nbt.ListTag();
        for (Out o : outbound) out.add(o.save());
        tag.put("out", out);
        tag.putBoolean("domeDown", domeDown);
        return tag;
    }

    public long nextOwnImpact(long now) {
        long best = -1;
        for (Out o : new ArrayList<>(outbound)) {
            if (o.sat() || o.impactGt() < now) continue;
            long d = o.impactGt() - now;
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    public long nearestImpact(long now) {
        long best = -1;
        for (CommandCenter.Warning w : new ArrayList<>(warnings)) {
            if (w.sat() || w.impactGt() < now) continue;
            long d = w.impactGt() - now;
            if (best < 0 || d < best) best = d;
        }
        return best;
    }
}
