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

public class JammerPodBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> PODS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean on, flush, sig, jamming;
    private UUID ship;

    private double fanAngle, lastT = Double.NaN;
    private Vec3 lastPos;
    private float fanRate;

    public JammerPodBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.JAMMER_POD_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean flush() { return flush; }
    public boolean switchedOn() { return on; }
    public boolean jammingShown() { return jamming; }

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

    void toggleLook() { flush = !flush; sync(); }

    void setOn(boolean v) {
        if (v == on) return;
        on = v;
        if (level != null && !level.isClientSide) playOnce(level, WmdSounds.JAMMER_SWITCH.get(), Balance.SND_CLICK, v ? 1.1F : 0.9F);
        sync();
    }

    private static List<JammerPodBlockEntity> podsOf(ServerLevel level, UUID ship) {
        List<JammerPodBlockEntity> out = new java.util.ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = PODS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof JammerPodBlockEntity j && !j.isRemoved()) out.add(j);
        }
        return out;
    }

    public static int toggleAll(ServerLevel level, UUID ship) {
        List<JammerPodBlockEntity> l = podsOf(level, ship);
        if (l.isEmpty()) return -1;
        boolean any = false;
        for (JammerPodBlockEntity j : l) any |= j.on;
        for (JammerPodBlockEntity j : l) j.setOn(!any);
        return any ? 0 : 1;
    }

    public static boolean anyOn(ServerLevel level, UUID ship) {
        for (JammerPodBlockEntity j : podsOf(level, ship)) if (j.on) return true;
        return false;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        RadarEmit.note(this);
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); setJamming(false); return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; }
            if (ship != null) PODS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            RadarEmit.note(this);
        }
        boolean s = sl.hasNeighborSignal(worldPosition);
        if (s && !sig) setOn(!on);
        sig = s;
        boolean j = on && ship != null && ShipPower.draw(sl, ship, Balance.JAM_POD_FE);
        if (j) AirJam.jamming(sl, ship, now);
        setJamming(j);
        return j;
    }

    private void setJamming(boolean j) { if (j != jamming) { jamming = j; sync(); } }

    private void leave(ServerLevel sl) {
        if (ship == null) return;
        Map<UUID, Set<Long>> m = PODS.get(sl.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(ship); }
        ship = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) leave(sl);
        super.setRemoved();
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (level != null) {
            Vec3 p = ShipFrame.drawnWorld(level, worldPosition, Vec3.atCenterOf(worldPosition), pt);
            if (!Double.isNaN(lastT) && lastPos != null && tt > lastT) {
                double dt = Math.min(5.0, tt - lastT), v = p.distanceTo(lastPos) / Math.max(1.0E-3, tt - lastT);
                fanRate += (float) ((Math.min(Balance.JAM_POD_FAN_MAX, v * Balance.JAM_POD_FAN_GAIN) - fanRate) * Math.min(1.0, 0.1 * dt));
                fanAngle = (fanAngle + fanRate * dt) % (Math.PI * 2.0);
            }
            if (Double.isNaN(lastT) || tt > lastT) { lastT = tt; lastPos = p; }
        }
        final float a = (float) fanAngle;
        model.getBone("fan").ifPresent(b -> b.setRotZ(a));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        long t = (long) tt;
        final boolean gf = jamming && !dark && t % 16 < 8, gr = jamming && !dark && t % 16 >= 8;
        model.getBone("glow_f").ifPresent(b -> b.setHidden(!gf));
        model.getBone("glow_r").ifPresent(b -> b.setHidden(!gr));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.JAMMER_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 1); }

    @Override
    protected String detail() { return (on ? "on" : "off") + (jamming ? ", JAMMING" : "") + (ship == null ? ", not on a ship" : ", on " + ship); }

    @Override
    public Component status() {
        String k = jamming ? "message.wmd.jammer_pod.jamming" : on ? (ship == null ? "message.wmd.jammer_pod.no_ship" : "message.wmd.jammer_pod.no_power") : "message.wmd.jammer_pod.off";
        return Component.translatable("block.wmd.jammer_pod").append(": ").append(Component.translatable(k));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("jpOn", on);
        tag.putBoolean("jpFlush", flush);
        tag.putBoolean("jpSig", sig);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        on = tag.getBoolean("jpOn");
        flush = tag.getBoolean("jpFlush");
        if (tag.contains("jpJam")) jamming = tag.getBoolean("jpJam");
        if (!client) sig = tag.getBoolean("jpSig");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("jpOn", on);
        tag.putBoolean("jpFlush", flush);
        tag.putBoolean("jpJam", jamming);
        return tag;
    }
}
