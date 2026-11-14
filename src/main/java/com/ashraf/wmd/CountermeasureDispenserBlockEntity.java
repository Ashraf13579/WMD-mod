package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CountermeasureDispenserBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> AT = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int flares, chaff;
    private UUID ship;
    private boolean powered;
    private long fireAt = Long.MIN_VALUE / 2;
    private int firedType;

    public CountermeasureDispenserBlockEntity(BlockPos pos, BlockState state) { super(AirBlocks.DISPENSER_BE.get(), pos, state, 0, 1, 0); }

    public int flares() { return flares; }
    public int chaff() { return chaff; }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); sync(); }
    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<ItemStack> takeUpgrades() {
        List<ItemStack> l = super.takeUpgrades();
        ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    Component reload(ServerLevel sl, int type) {
        if (!AirGate.on()) return Component.translatable("message.wmd.dispenser.off");
        if (ship != null && AirPicture.isAircraft(sl, ship)) return Component.translatable("message.wmd.dispenser.airborne");
        int cap = type == AirMissiles.FLARE ? Balance.AAM_DISPENSER_FLARES : Balance.AAM_DISPENSER_CHAFF;
        int now = type == AirMissiles.FLARE ? flares : chaff;
        if (now >= cap) return Component.translatable("message.wmd.dispenser.full");
        int n = Math.min(cap, now + Balance.DISP_PER_ITEM);
        if (type == AirMissiles.FLARE) flares = n; else chaff = n;
        playOnce(sl, WmdSounds.DISP_RELOAD.get(), Balance.SND_CLICK, 1.0F);
        sync();
        return null;
    }

    public Component status() {
        return Component.translatable("message.wmd.dispenser.status", flares, Balance.AAM_DISPENSER_FLARES, chaff, Balance.AAM_DISPENSER_CHAFF);
    }

    int salvo(ServerLevel sl, int type) {
        if (!AirGate.on() || ship == null) return AirMissiles.EMPTY;
        if ((type == AirMissiles.FLARE ? flares : chaff) <= 0) return AirMissiles.EMPTY;
        if (EmpField.jammed(sl, worldPosition)) return AirMissiles.EMPTY;
        double[] w = worldPoint(0.0, -0.5, 0.0);
        int r = AirMissiles.salvo(sl, ship, new Vec3(w[0], w[1], w[2]), type);
        if (r == AirMissiles.COOLING) return r;
        if (type == AirMissiles.FLARE) flares--; else chaff--;
        fireAt = sl.getGameTime();
        firedType = type;
        sync();
        return r;
    }

    public static int salvoOn(ServerLevel level, UUID ship, int type) {
        if (!AirGate.on() || level == null || ship == null) return AirMissiles.EMPTY;
        Map<UUID, Set<Long>> m = AT.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return AirMissiles.EMPTY;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (!level.isLoaded(bp) || !(level.getBlockEntity(bp) instanceof CountermeasureDispenserBlockEntity d)) continue;
            if ((type == AirMissiles.FLARE ? d.flares : d.chaff) <= 0) continue;
            return d.salvo(level, type);
        }
        return AirMissiles.EMPTY;
    }

    public static int[] loadOn(ServerLevel level, UUID ship) {
        int[] out = { 0, 0 };
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = AT.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s)
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof CountermeasureDispenserBlockEntity d) { out[0] += d.flares; out[1] += d.chaff; }
        return out;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { unregister(sl); return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) unregister(sl);
            ship = s;
            if (ship != null) AT.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        int sig = sl.getBestNeighborSignal(worldPosition);
        if (sig > 0 && !powered) salvo(sl, sig >= 8 ? AirMissiles.CHAFF : AirMissiles.FLARE);
        powered = sig > 0;
        return false;
    }

    private void unregister(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = AT.get(sl.dimension());
        if (m != null) {
            Set<Long> s = m.get(ship);
            if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
            if (m.isEmpty()) AT.remove(sl.dimension());
        }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) unregister(sl);
        super.setRemoved();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double since = tt - fireAt;
        float open = (float) (since < 0 ? 0 : since < 2 ? since / 2 : since < Balance.DISP_HATCH_TICKS ? 1 : since < Balance.DISP_HATCH_TICKS + 6 ? 1 - (since - Balance.DISP_HATCH_TICKS) / 6 : 0);
        final float f = firedType == AirMissiles.FLARE ? open : 0.0F, c = firedType == AirMissiles.CHAFF ? open : 0.0F;
        final float a = (float) Math.toRadians(100.0);
        model.getBone("hatch_f").ifPresent(b -> b.setRotZ(-a * f));
        model.getBone("hatch_c").ifPresent(b -> b.setRotZ(a * c));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean lamp = !dark && (flares > 0 || chaff > 0);
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }
    @Override protected String detail() { return flares + " flares, " + chaff + " chaff" + (ship == null ? ", not on a ship" : ""); }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("cdFlares", flares);
        tag.putInt("cdChaff", chaff);
        tag.putBoolean("cdPowered", powered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        flares = tag.getInt("cdFlares");
        chaff = tag.getInt("cdChaff");
        if (tag.contains("cdFireAt")) { fireAt = tag.getLong("cdFireAt"); firedType = tag.getInt("cdFired"); }
        if (!client) powered = tag.getBoolean("cdPowered");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("cdFlares", flares);
        tag.putInt("cdChaff", chaff);
        tag.putLong("cdFireAt", fireAt);
        tag.putInt("cdFired", firedType);
        return tag;
    }
}
