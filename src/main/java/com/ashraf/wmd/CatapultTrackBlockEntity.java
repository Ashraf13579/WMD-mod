package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class CatapultTrackBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    public static final int IDLE = 0, HOOKED = 1, STROKE = 2, RETURN = 3;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean sig;

    private int length;
    private int state;
    private long stateAt, spoolSince = -1;
    private UUID jet;
    private BlockPos gearPos;

    private float shuttle;

    private boolean crewPulse;

    private double lastT = Double.NaN;
    private float shuttleShown;

    public CatapultTrackBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.CATAPULT_TRACK_BE.get(), pos, state, 0, Balance.CAT_BUFFER, Balance.CAT_RECEIVE);
    }

    public boolean head() { return length > 0; }
    public int length() { return length; }
    public int catState() { return state; }

    boolean crewLaunch() {
        if (state != HOOKED || length <= 0) return false;
        crewPulse = true;
        return true;
    }

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

    private CatapultTrackBlockEntity seg(BlockPos p) {
        return level != null && level.getBlockEntity(p) instanceof CatapultTrackBlockEntity c && c.facing() == facing() ? c : null;
    }

    private List<CatapultTrackBlockEntity> segments() {
        List<CatapultTrackBlockEntity> l = new ArrayList<>();
        Direction f = facing();
        for (int i = 0; i < Balance.CAT_MAX; i++) { CatapultTrackBlockEntity c = seg(worldPosition.relative(f, i)); if (c == null) break; l.add(c); }
        return l;
    }

    private int measure() {
        if (facing().getAxis() == Direction.Axis.Y || seg(worldPosition.relative(facing().getOpposite())) != null) return 0;
        int n = segments().size();
        return n >= Balance.CAT_MIN ? n : 0;
    }

    private long charge() { long e = 0; for (CatapultTrackBlockEntity c : segments()) e += c.energy().getEnergyStored(); return e; }

    private void pay(int fe) {
        for (CatapultTrackBlockEntity c : segments()) {
            int take = Math.min(fe, c.energy().getEnergyStored());
            if (take > 0 && c.useExtraEnergy(take)) fe -= take;
            if (fe <= 0) return;
        }
    }

    private Vec3 along(double s) {
        Direction f = facing();
        return Vec3.atCenterOf(worldPosition).add(f.getStepX() * s, 0.5 + 0.1, f.getStepZ() * s);
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) return false;
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 31L)) % 40 == 0 && state == IDLE) { int n = measure(); if (n != length) { length = n; sync(); } }
        if (length <= 0) return false;
        boolean pulse = sl.hasNeighborSignal(worldPosition);
        boolean rising = pulse && !sig;
        sig = pulse;
        if (crewPulse) { rising = state == HOOKED; crewPulse = false; }
        UUID deck = SableBridge.shipIdAt(sl, worldPosition);
        switch (state) {
            case IDLE -> {
                LandingGearBlockEntity g = noseOn(sl, deck);
                if (g != null) { jet = g.ship(); gearPos = g.getBlockPos(); state = HOOKED; stateAt = now; spoolSince = -1; playOnce(sl, WmdSounds.CAT_HOOK.get(), Balance.SND_CLICK, 1.0F); sync(); }
            }
            case HOOKED -> {
                LandingGearBlockEntity g = gearPos != null && sl.isLoaded(gearPos) && sl.getBlockEntity(gearPos) instanceof LandingGearBlockEntity lg ? lg : null;
                if (g == null || !g.locked() || jet == null || !jet.equals(g.ship()) || wheelOff(sl, deck, g) > Balance.CAT_HOOK_R * 1.5) { state = IDLE; jet = null; sync(); break; }
                ShipControl.Order o = ShipControl.newest(sl, jet);
                boolean full = o != null && (o.afterburner() || o.throttle() >= 0.95);
                spoolSince = full ? (spoolSince < 0 ? now : spoolSince) : -1;
                if ((rising || (spoolSince >= 0 && now - spoolSince >= Balance.CAT_SPOOL_TICKS))) {
                    if (charge() < Balance.CAT_FE) { if (rising || (now - spoolSince) % 40 == 0) tellPilot(sl, "message.wmd.catapult.no_power"); break; }
                    pay(Balance.CAT_FE);
                    state = STROKE;
                    stateAt = now;
                    shuttle = 0.0F;
                    playOnce(sl, WmdSounds.CAT_SHOT.get(), Balance.SND_RELEASE, 1.0F);
                    Wmd.LOGGER.info("Catapult at {}: launching {} ({} blocks)", worldPosition, jet, length);
                    sync();
                }
            }
            case STROKE -> stroke(sl, deck, now);
            case RETURN -> {
                shuttle = (float) Math.max(0.0, (length - 1) * (1.0 - (now - stateAt) / (double) Balance.CAT_RETURN_TICKS));
                if (now - stateAt >= Balance.CAT_RETURN_TICKS) { state = IDLE; shuttle = 0.0F; sync(); }
                else if ((now - stateAt) % 4 == 0) sync();
            }
            default -> state = IDLE;
        }
        return false;
    }

    private void stroke(ServerLevel sl, UUID deck, long now) {
        LandingGearBlockEntity g = gearPos != null && sl.isLoaded(gearPos) && sl.getBlockEntity(gearPos) instanceof LandingGearBlockEntity lg ? lg : null;
        SableBridge.ShipInfo ji = jet == null ? null : SableBridge.shipInfo(sl, jet);
        double len = Math.max(1.0, length - 1);
        if (g == null || ji == null || now - stateAt > Balance.CAT_MAX_TICKS) { endStroke(sl, now); return; }
        Vec3 wheel = g.wheelWorld(sl);
        Vec3 wl = wheel == null ? null : (deck == null ? wheel : SableBridge.toShip(sl, deck, wheel));
        Direction f = facing();
        Vec3 fw = new Vec3(f.getStepX(), 0.0, f.getStepZ());
        double s = wl == null ? shuttle : wl.subtract(along(0.0)).dot(fw);
        shuttle = (float) Math.max(0.0, Math.min(len, s));
        if (s >= len) { endStroke(sl, now); return; }
        double acc = Balance.CAT_END_SPEED * Balance.CAT_END_SPEED / (2.0 * len);
        double want = Math.sqrt(2.0 * acc * Math.max(0.25, s + 0.5));
        Vec3 fwWorld = deck == null ? fw : SableBridge.toWorld(sl, along(1.0)).subtract(SableBridge.toWorld(sl, along(0.0)));
        if (fwWorld == null || fwWorld.lengthSqr() < 1.0E-9) { endStroke(sl, now); return; }
        fwWorld = fwWorld.normalize();
        Vec3 v = ShipDefence.velocity(sl, ji).scale(20.0);
        if (deck != null) { SableBridge.ShipInfo d = SableBridge.shipInfo(sl, deck); if (d != null) v = v.subtract(ShipDefence.velocity(sl, d).scale(20.0)); }
        double have = v.dot(fwWorld);
        if (want > have) SableBridge.shipPush(sl, jet, fwWorld.scale(Math.min(want - have, acc / 20.0 * 2.5)), Vec3.ZERO);
        Vec3 slot = SableBridge.toWorld(sl, along(shuttle));
        if (slot != null) sl.sendParticles(ParticleTypes.CLOUD, slot.x, slot.y, slot.z, 3, 0.15, 0.05, 0.15, 0.02);
        if ((now - stateAt) % 2 == 0) sync();
    }

    private void endStroke(ServerLevel sl, long now) {
        Wmd.LOGGER.info("Catapult at {}: stroke ended ({} ticks)", worldPosition, now - stateAt);
        Vec3 end = SableBridge.toWorld(sl, along(length - 1));
        if (end != null) sl.sendParticles(ParticleTypes.CLOUD, end.x, end.y, end.z, 30, 0.4, 0.2, 0.4, 0.06);
        state = RETURN;
        stateAt = now;
        jet = null;
        gearPos = null;
        playOnce(sl, WmdSounds.CAT_RETURN.get(), Balance.SND_MECH, 1.0F);
        sync();
    }

    private LandingGearBlockEntity noseOn(ServerLevel sl, UUID deck) {
        Vec3 sw = SableBridge.toWorld(sl, along(0.0));
        if (sw == null) return null;
        for (LandingGearBlockEntity g : LandingGearBlockEntity.gearsNear(sl, sw, 6.0)) {
            if (g.role() != LandingGearBlockEntity.NOSE || !g.locked() || !g.wow || g.ship() == null || g.ship().equals(deck)) continue;
            if (wheelOff(sl, deck, g) <= Balance.CAT_HOOK_R) return g;
        }
        return null;
    }

    private double wheelOff(ServerLevel sl, UUID deck, LandingGearBlockEntity g) {
        Vec3 w = g.wheelWorld(sl), sw = SableBridge.toWorld(sl, along(0.0));
        if (w == null || sw == null) return Double.MAX_VALUE;
        return Math.hypot(w.x - sw.x, w.z - sw.z) + Math.max(0.0, Math.abs(w.y - sw.y) - 0.6);
    }

    private void tellPilot(ServerLevel sl, String key) {
        if (jet == null) return;
        for (net.minecraft.server.level.ServerPlayer sp : sl.players())
            if (jet.equals(SableBridge.shipUnder(sp))) sp.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (!Double.isNaN(lastT) && tt > lastT) shuttleShown += (float) ((shuttle - shuttleShown) * Math.min(1.0, 0.6 * Math.min(5.0, tt - lastT)));
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        final float z = -shuttleShown * 16.0F;
        final boolean show = length > 0;
        model.getBone("shuttle").ifPresent(b -> { b.setPosZ(z); b.setHidden(!show); });
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return side == Direction.DOWN ? energy() : null; }

    @Override protected String detail() { return length > 0 ? "head, " + length + " long, " + new String[] { "ready", "HOOKED " + jet, "LAUNCHING", "shuttle back" }[Math.max(0, Math.min(3, state))] + ", " + charge() + " FE" : "segment"; }

    @Override
    public Component status() {
        String k = length <= 0 ? "message.wmd.catapult.segment" : state == HOOKED ? "message.wmd.catapult.hooked" : state == STROKE ? "message.wmd.catapult.launching"
                : charge() < Balance.CAT_FE ? "message.wmd.catapult.no_power" : "message.wmd.catapult.ready";
        return Component.translatable("block.wmd.catapult_track").append(": ").append(Component.translatable(k, length, (int) Balance.CAT_END_SPEED, Balance.CAT_MIN));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("ctLen")) { length = tag.getInt("ctLen"); state = tag.getInt("ctState"); shuttle = tag.getFloat("ctShuttle"); }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("ctLen", length);
        tag.putInt("ctState", state);
        tag.putFloat("ctShuttle", shuttle);
        return tag;
    }
}
