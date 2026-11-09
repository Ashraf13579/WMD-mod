package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class SolarPanelBlockEntity extends ProcessUnitBlockEntity {

    private int stored;

    private int size = 1;
    private boolean capN = true, capS = true, socket = true;

    private int skyTiles = 1;

    private BlockPos controller;

    private BlockPos sentController;

    private int groupNote;
    private int groupW, groupD;
    private int fePerTickNow;
    private int syncedInfo = -1;

    private final IEnergyStorage out = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) {
            int t = Math.min(Math.min(n, stored), Balance.SOLAR_OUT_PER_TICK);
            if (t > 0 && !sim) { stored -= t; setChanged(); }
            return t;
        }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.SOLAR_BUFFER_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SOLAR_PANEL_BE.get(), pos, state, 0); }

    public boolean showsSocket() { return socket; }
    public boolean showsCapN() { return capN; }
    public boolean showsCapS() { return capS; }
    public int arraySize() { return size; }

    static double hourAngle(Level level, float partialTick) {
        double h = level.getTimeOfDay(partialTick) * 360.0;
        return h > 180.0 ? h - 360.0 : h;
    }

    static double sun(Level level) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasFixedTime()) return 0.0;
        double a = Math.abs(hourAngle(level, 0.0F));
        if (a >= 90.0) return 0.0;
        double off = Math.max(0.0, a - Balance.SOLAR_TRACK_DEG);
        return Math.cos(Math.toRadians(off)) * Mth.clamp((90.0 - a) / Balance.SOLAR_DUSK_DEG, 0.0, 1.0);
    }

    public static float tiltDeg(Level level, float partialTick) {
        double h = hourAngle(level, partialTick), lim = Balance.SOLAR_TRACK_DEG, a = Math.abs(h);
        if (a <= 90.0) return (float) Mth.clamp(h, -lim, lim);
        if (a < 90.0 + Balance.SOLAR_STOW_DEG) return (float) (Math.signum(h) * lim * (90.0 + Balance.SOLAR_STOW_DEG - a) / Balance.SOLAR_STOW_DEG);
        return 0.0F;
    }

    private boolean isTile(BlockPos p) {
        return level != null && level.isLoaded(p) && level.getBlockEntity(p) instanceof SolarPanelBlockEntity;
    }

    private void scan() {
        Set<BlockPos> group = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        group.add(worldPosition);
        todo.add(worldPosition);
        int cap = Balance.SOLAR_MAX_N * Balance.SOLAR_MAX_N;
        while (!todo.isEmpty() && group.size() <= cap) {
            BlockPos p = todo.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (!group.contains(q) && isTile(q)) { group.add(q); todo.add(q); }
            }
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : group) { x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX()); z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ()); }
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        groupW = w;
        groupD = d;
        boolean square = group.size() <= cap && w == d && w >= Balance.SOLAR_MIN_N && group.size() == w * d;
        groupNote = group.size() == 1 || square ? 0 : group.size() > cap ? 2 : 1;
        int n;
        boolean cn, cs, so;
        if (square) {
            n = w;
            cn = worldPosition.getZ() == z0;
            cs = worldPosition.getZ() == z1;
            controller = new BlockPos(x1, worldPosition.getY(), z1);
            so = worldPosition.equals(controller);
        } else {
            n = 1;
            cn = cs = so = true;
            controller = worldPosition;
        }
        if (so) {
            int sky = 0;
            for (BlockPos p : n == 1 ? List.of(worldPosition) : group) if (level.canSeeSky(p.above(2))) sky++;
            skyTiles = sky;
        }
        if (n != size || cn != capN || cs != capS || so != socket || !controller.equals(sentController)) {
            sentController = controller;
            size = n;
            capN = cn;
            capS = cs;
            socket = so;
            if (!so) stored = 0;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private int makeNow() {
        if (!socket || level == null) return 0;
        double s = sun(level);
        if (s <= 0.0) return 0;
        if (level.isRaining() && level.getBiome(worldPosition).value().hasPrecipitation())
            s *= level.isThundering() ? Balance.SOLAR_THUNDER : Balance.SOLAR_RAIN;
        double peak = Balance.SOLAR_ARRAY_FE[size] * (skyTiles / (double) (size * size));
        return (int) Math.round(peak * s);
    }

    @Override
    public void empDrain() { stored = 0; setChanged(); }

    @Override
    protected boolean work() {
        if (level == null) return false;
        boolean scanNow = controller == null || (level.getGameTime() + (worldPosition.asLong() & 15)) % Balance.SOLAR_SCAN_TICKS == 0;
        if (scanNow) scan();
        fePerTickNow = makeNow();
        if (scanNow && info() != syncedInfo) {
            syncedInfo = info();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (fePerTickNow <= 0) return false;
        int add = Math.min(fePerTickNow, Balance.SOLAR_BUFFER_FE - stored);
        if (add <= 0) return false;
        stored += add;
        flowAcc += add;
        return true;
    }

    private SolarPanelBlockEntity master() {
        if (controller != null && !controller.equals(worldPosition) && level != null && level.isLoaded(controller)
                && level.getBlockEntity(controller) instanceof SolarPanelBlockEntity c) return c;
        return this;
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return Math.max(1, Balance.SOLAR_ARRAY_FE[size]); }
    @Override public IEnergyStorage guiEnergy() { return master().out; }
    @Override public int guiFeUse() { return 0; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    @Override
    public List<Component> guiInfo() {
        SolarPanelBlockEntity m = master();
        Component shape = groupNote == 1 ? Component.translatable("gui.wmd.info.solar_not_square", groupW, groupD, Balance.SOLAR_MIN_N, Balance.SOLAR_MIN_N, Balance.SOLAR_MAX_N, Balance.SOLAR_MAX_N)
                : groupNote == 2 ? Component.translatable("gui.wmd.info.solar_too_big", Balance.SOLAR_MAX_N, Balance.SOLAR_MAX_N)
                : m.size == 1 ? Component.translatable("gui.wmd.info.solar_alone", Balance.SOLAR_MIN_N, Balance.SOLAR_MIN_N, Balance.SOLAR_MAX_N, Balance.SOLAR_MAX_N)
                : Component.translatable("gui.wmd.info.solar_array", m.size, m.size, m.size * m.size);
        int sunPct = level == null ? 0 : (int) Math.round(sun(level) * 100.0);
        return List.of(shape,
                Component.translatable("gui.wmd.info.solar_output", m.fePerTickNow * 20, Balance.SOLAR_ARRAY_FE[m.size] * 20, sunPct),
                Component.translatable("gui.wmd.info.solar_sky", m.skyTiles, m.size * m.size),
                Component.translatable(m == this ? (m.size > 1 ? "gui.wmd.info.solar_socket_here" : "gui.wmd.info.solar_socket_alone") : "gui.wmd.info.solar_socket"));
    }

    @Override protected SoundEvent runSound() { return WmdSounds.BAT_HUM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected float soundVolume() { return Balance.SND_AMBIENT; }

    @Override
    protected String detail() { return String.format("%d x %d, %d FE/t, stored %,d FE", size, size, fePerTickNow, stored); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return socket && at(part, side, 0, 0, 0, Direction.SOUTH) ? out : null; }

    private static final PortMap PORTS = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, true);
    private static final PortMap NO_PORTS = new PortMap(0, 0, 1);
    @Override public PortMap guiPortMap() { return socket ? PORTS : NO_PORTS; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("stored", stored);
        tag.putInt("sol", role());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("stored")) stored = Mth.clamp(tag.getInt("stored"), 0, Balance.SOLAR_BUFFER_FE);
        if (tag.contains("sol")) setRole(tag.getInt("sol"));
        if (tag.contains("solinfo")) {
            int i = tag.getInt("solinfo");
            fePerTickNow = i & 4095;
            skyTiles = (i >> 12) & 63;
            groupNote = (i >> 18) & 3;
            groupW = (i >> 20) & 63;
            groupD = (i >> 26) & 63;
        }
        if (tag.contains("solc")) controller = BlockPos.of(tag.getLong("solc"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("sol", role());
        tag.putInt("solinfo", info());
        if (controller != null) tag.putLong("solc", controller.asLong());
        return tag;
    }

    private int role() { return (capN ? 1 : 0) | (capS ? 2 : 0) | (socket ? 4 : 0) | (size << 3); }

    private int info() { return Math.min(fePerTickNow, 4095) | Math.min(skyTiles, 63) << 12 | groupNote << 18 | Math.min(groupW, 63) << 20 | Math.min(groupD, 63) << 26; }

    private void setRole(int r) {
        capN = (r & 1) != 0;
        capS = (r & 2) != 0;
        socket = (r & 4) != 0;
        size = Mth.clamp(r >> 3, 1, Balance.SOLAR_MAX_N);
    }

    @Override protected void clientEffects(Level level) {}
}
