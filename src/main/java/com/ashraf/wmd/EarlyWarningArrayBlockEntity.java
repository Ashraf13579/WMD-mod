package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class EarlyWarningArrayBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated {

    public static final int X0 = -7, X1 = 7, Y0 = 0, Y1 = 17, Z0 = 0, Z1 = 8;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 8, Direction.SOUTH, false);
    public static final int WARN_KEEP = 5;

    private final OwnerData ownerData = new OwnerData();

    private final List<long[]> recent = new ArrayList<>();

    public EarlyWarningArrayBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.EARLY_WARNING_ARRAY_BE.get(), pos, state, Balance.EWA_FE_PER_TICK);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private static final Set<EarlyWarningArrayBlockEntity> ARRAYS = Collections.newSetFromMap(new WeakHashMap<>());
    static void clearStatics() { synchronized (ARRAYS) { ARRAYS.clear(); } }
    private boolean registered;

    static List<EarlyWarningArrayBlockEntity> all() {
        synchronized (ARRAYS) { return new ArrayList<>(ARRAYS); }
    }

    public static void serverTickArray(Level level, BlockPos pos, BlockState state, EarlyWarningArrayBlockEntity be) {
        if (!be.registered) {
            synchronized (ARRAYS) { ARRAYS.add(be); }
            be.registered = true;
        }
        ProcessUnitBlockEntity.serverTick(level, pos, state, be);
        RadarEmit.note(be);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (ARRAYS) { ARRAYS.remove(this); }
        registered = false;
    }

    @Override
    protected boolean work() {
        reason = 0;
        return true;
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 8, Direction.SOUTH) ? energy() : null; }

    void noteWarning(long ox, long oz, long ix, long iz, long launchGt, long impactGt, boolean sat) {
        recent.add(new long[] { ox, oz, ix, iz, launchGt, impactGt, sat ? 1 : 0 });
        while (recent.size() > WARN_KEEP) recent.remove(0);
        contactBlip();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    void noteAirship(long x, long z, int blocks, boolean hostile, long now) {
        recent.add(new long[] { x, z, blocks, hostile ? 0 : 1, now, now, 2 });
        while (recent.size() > WARN_KEEP) recent.remove(0);
        contactBlip();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public List<long[]> recent() { return List.copyOf(recent); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.EWA_FE_PER_TICK * 20),
                Component.translatable("gui.wmd.info.early_warning_array", String.format("%,d", Balance.EWA_RANGE)));
    }

    @Override
    protected String detail() {
        return (isRunning() ? "watching" : "no power") + ", range " + Balance.EWA_RANGE + ", warnings raised (kept) " + recent.size();
    }

    @Override protected SoundEvent runSound() { return WmdSounds.EWA_RUN_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 4); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }

    static final double HW = 104.0, VC = 126.0, R = HW / Math.cos(Math.toRadians(30.0));
    static final int CELLS = 54, ROWS = 6, ROW_TICKS = 20, BOOT_STEP = 4, BOOT_TICKS = 30, RED_TICKS = 60, AIRSHIP_TRACK = 600, TRACK_MAX = 2400, FRESH = 100;
    static final double BEAM_R = R * 0.36;
    static final double[] CELL_U = new double[CELLS], CELL_V = new double[CELLS];
    static final String[] CELL_BONE = new String[CELLS];
    static {
        double rp = R - 0.5;
        int n = 0;
        for (int k = 0; k < 6; k++) {
            double a0 = Math.toRadians(90 + 60 * k), a1 = Math.toRadians(150 + 60 * k);
            double[][] t = { { 0.0, VC }, { rp * Math.cos(a0), VC + rp * Math.sin(a0) }, { rp * Math.cos(a1), VC + rp * Math.sin(a1) } };
            double icu = (t[0][0] + t[1][0] + t[2][0]) / 3, icv = (t[0][1] + t[1][1] + t[2][1]) / 3;
            double s = 1.0 - 2.0 / (rp / (2 * Math.sqrt(3)));
            double[] c = { icu + (t[0][0] - icu) * s, icv + (t[0][1] - icv) * s }, a = { icu + (t[1][0] - icu) * s, icv + (t[1][1] - icv) * s },
                    b = { icu + (t[2][0] - icu) * s, icv + (t[2][1] - icv) * s };
            for (int i = 0; i < 3; i++)
                for (int j = 0; j < 3 - i; j++)
                    for (int d = 0; d < (i + j <= 1 ? 2 : 1); d++) {
                        double fi = d == 0 ? i + 1.0 / 3.0 : i + 2.0 / 3.0, fj = d == 0 ? j + 1.0 / 3.0 : j + 2.0 / 3.0;
                        CELL_U[n] = c[0] + (a[0] - c[0]) * fi / 3 + (b[0] - c[0]) * fj / 3;
                        CELL_V[n] = c[1] + (a[1] - c[1]) * fi / 3 + (b[1] - c[1]) * fj / 3;
                        CELL_BONE[n] = String.format("ec%02d", n);
                        n++;
                    }
        }
    }

    private double animLastT = -1, rasterT, fanAngle, fanSpeed, bootT = -1, beatT = -1e9;
    private long seenKey = Long.MIN_VALUE;

    int sectorToward(double wx, double wz) {
        double dx = wx - (worldPosition.getX() + 0.5), dz = wz - (worldPosition.getZ() + 0.5);
        double lx, lz;
        switch (facing()) {
            case EAST -> { lx = dz; lz = -dx; }
            case SOUTH -> { lx = -dx; lz = -dz; }
            case WEST -> { lx = -dz; lz = dx; }
            default -> { lx = dx; lz = dz; }
        }
        if (lx * lx + lz * lz < 1e-6) return 0;
        double ang = Math.toDegrees(Math.atan2(-lz, lx));
        return Math.floorMod((int) Math.floor((ang - 90.0) / 60.0), 6);
    }

    private int trackSector(long now) {
        if (recent.isEmpty()) return -1;
        long[] w = recent.get(recent.size() - 1);
        boolean live = w[6] == 2 ? now - w[4] < AIRSHIP_TRACK : now < w[5] && now - w[4] < TRACK_MAX;
        return live ? sectorToward(w[0], w[1]) : -1;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        boolean powered = isRunning();
        boolean on = powered && !ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        double dt = animLastT < 0 ? 0.0 : Math.max(0.0, Math.min(5.0, tt - animLastT));
        animLastT = tt;
        long now = (long) tt;

        long key = recent.isEmpty() ? 0L : recent.get(recent.size() - 1)[4] * 31L + recent.get(recent.size() - 1)[0] * 7L + recent.get(recent.size() - 1)[1];
        if (seenKey == Long.MIN_VALUE) seenKey = key;
        else if (key != seenKey) {
            seenKey = key;
            if (on && !recent.isEmpty() && now - recent.get(recent.size() - 1)[4] < FRESH) beatT = tt;
        }
        if (!on) bootT = -1;
        else if (bootT < 0) bootT = tt;
        double boot = on ? tt - bootT : 0.0, beat = tt - beatT;
        int track = on && beat > 12 ? trackSector(now) : -1;
        boolean searching = on && boot >= BOOT_TICKS;
        if (searching) rasterT += dt * (track >= 0 ? 0.5 : 1.0);
        rasterT %= ROWS * ROW_TICKS;

        int row = (int) (rasterT / ROW_TICKS);
        double f = (rasterT - row * ROW_TICKS) / ROW_TICKS, rowH = 2.0 * R / ROWS;
        double bv = VC + R - rowH * (row + 0.5), bu = (row % 2 == 0) ? -R + 2.0 * R * f : R - 2.0 * R * f;
        boolean flash = on && ((beat >= 0 && beat < 4) || (beat >= 7 && beat < 11));
        boolean pulse = (now % 6) < 3;
        for (int n = 0; n < CELLS; n++) {
            int sec = n / 9;
            boolean lit;
            if (flash) lit = true;
            else if (!searching) lit = false;
            else if (sec == track) lit = pulse;
            else {
                double du = CELL_U[n] - bu, dv = CELL_V[n] - bv;
                lit = du * du + dv * dv < BEAM_R * BEAM_R;
            }
            final boolean hide = !lit;
            model.getBone(CELL_BONE[n]).ifPresent(b -> b.setHidden(hide));
        }
        for (int k = 0; k < 6; k++) {
            final boolean hide = !on || boot < k * BOOT_STEP;
            model.getBone("sec" + k).ifPresent(b -> b.setHidden(hide));
        }
        boolean red = on && beat >= 0 && beat < RED_TICKS && (now % 10) < 5;
        boolean amber = track >= 0 && !(on && beat >= 0 && beat < RED_TICKS);
        boolean green = searching && track < 0 && !(beat >= 0 && beat < RED_TICKS);
        model.getBone("st_r").ifPresent(b -> b.setHidden(!red));
        model.getBone("st_a").ifPresent(b -> b.setHidden(!amber));
        model.getBone("st_g").ifPresent(b -> b.setHidden(!green));
        final boolean chine = on && !(beat >= 0 && beat < 11 && (((long) beat) % 4) >= 2);
        model.getBone("chine").ifPresent(b -> b.setHidden(!chine));
        final boolean av = on && Math.floorMod(now + (worldPosition.asLong() & 31L), 40) < 20;
        model.getBone("avlamp").ifPresent(b -> b.setHidden(!av));

        fanSpeed += ((powered ? 1.0 : 0.0) - fanSpeed) * Math.min(1.0, dt * 0.03);
        fanAngle = (fanAngle + fanSpeed * dt * 0.33) % (Math.PI * 2);
        final float fa = (float) fanAngle;
        model.getBone("cfan0").ifPresent(b -> b.setRotY(fa));
        model.getBone("cfan1").ifPresent(b -> b.setRotY(fa + 0.6F));
        model.getBone("sweep").ifPresent(b -> b.setHidden(true));
    }

    private void contactBlip() {
        if (level == null || level.isClientSide || !isRunning()) return;
        BlockPos p = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 8, 2);
        level.playSound(null, p, WmdSounds.CC_PING.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
    }

    private void writeRecent(CompoundTag tag) {
        ListTag l = new ListTag();
        for (long[] w : recent) l.add(new LongArrayTag(w));
        tag.put("ewaRecent", l);
    }

    private void readRecent(CompoundTag tag) {
        if (!tag.contains("ewaRecent")) return;
        recent.clear();
        for (Tag x : tag.getList("ewaRecent", Tag.TAG_LONG_ARRAY)) {
            long[] w = ((LongArrayTag) x).getAsLongArray();
            if (w.length == 7) recent.add(w);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        writeRecent(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        readRecent(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        writeRecent(tag);
        return tag;
    }
}
