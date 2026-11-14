package com.ashraf.wmd;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.network.PacketDistributor;

public class GunPodBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float HATCH_SIGN = 1.0F;

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> PODS = Statics.server(new HashMap<>());

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private boolean flush, sig, firing;
    private int rounds, spin;
    private long triggerUntil, nextBurst, nextSound, hatchUntil;
    private UUID ship;

    private double barrelAngle, lastT = Double.NaN;
    private float barrelRate, hatch;

    public GunPodBlockEntity(BlockPos pos, BlockState state) {
        super(AirBlocks.GUN_POD_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean flush() { return flush; }
    public int rounds() { return rounds; }
    public boolean firingShown() { return firing; }

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

    String reload(Player player, ItemStack held) {
        String k = loadBox();
        if ("message.wmd.gun_pod.loaded".equals(k) && !player.getAbilities().instabuild) held.shrink(1);
        return k;
    }

    String loadBox() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return "message.wmd.gun_pod.off";
        UUID s = SableBridge.shipIdAt(sl, worldPosition);
        if (s != null && AirPicture.isAircraft(sl, s)) return "message.wmd.gun_pod.land_first";
        if (rounds + Balance.GPOD_BOX > Balance.GPOD_ROUNDS) return "message.wmd.gun_pod.full";
        rounds += Balance.GPOD_BOX;
        hatchUntil = sl.getGameTime() + Balance.GPOD_HATCH_TICKS;
        playOnce(sl, WmdSounds.GPOD_RELOAD.get(), Balance.SND_CLICK, 1.0F);
        sync();
        return "message.wmd.gun_pod.loaded";
    }

    private static List<GunPodBlockEntity> podsOf(ServerLevel level, UUID ship) {
        List<GunPodBlockEntity> out = new java.util.ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = PODS.get(level.dimension());
        Set<Long> s = m == null ? null : m.get(ship);
        if (s == null) return out;
        for (Long p : s) {
            BlockPos bp = BlockPos.of(p);
            if (level.isLoaded(bp) && level.getBlockEntity(bp) instanceof GunPodBlockEntity g && !g.isRemoved()) out.add(g);
        }
        return out;
    }

    public static int triggerAll(ServerLevel level, UUID ship, int holdTicks) {
        List<GunPodBlockEntity> l = podsOf(level, ship);
        if (l.isEmpty()) return -1;
        long until = level.getGameTime() + Math.max(1, Math.min(holdTicks, Balance.GPOD_HOLD_MAX));
        int left = 0;
        for (GunPodBlockEntity g : l) { g.triggerUntil = Math.max(g.triggerUntil, until); left += g.rounds; }
        return left;
    }

    Vec3[] muzzleRay(ServerLevel sl) {
        Direction f = facing();
        Vec3 nrm = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
        Vec3 plotMuzzle = Vec3.atCenterOf(worldPosition).add(nrm.scale(0.55));
        Vec3 m = SableBridge.toWorld(sl, plotMuzzle), ahead = SableBridge.toWorld(sl, plotMuzzle.add(nrm));
        if (m == null || ahead == null) return null;
        Vec3 dir = ahead.subtract(m);
        if (dir.lengthSqr() < 1.0E-6) return null;
        return new Vec3[] { m, dir.normalize() };
    }

    static Object[] pipper(ServerLevel sl, UUID ship, Vec3 lock) {
        if (!AirGate.on()) return null;
        List<GunPodBlockEntity> l = podsOf(sl, ship);
        if (l.isEmpty()) return null;
        Vec3[] ray = l.get(0).muzzleRay(sl);
        if (ray == null) return null;
        Vec3 m = ray[0], dir = ray[1];
        AirGuns.Jet jet = AirGuns.boresightTarget(sl, ship, m, dir, Balance.GPOD_RANGE, Balance.GPOD_CONE);
        if (jet != null) return new Object[] { m.add(dir.scale(jet.dist())), 1 };
        if (lock != null) {
            double d = lock.distanceTo(m);
            if (d <= Balance.GPOD_RANGE * 1.5) return new Object[] { m.add(dir.scale(d)), 2 };
        }
        Object[] g = TargetingPodBlockEntity.groundRay(sl, m, dir, Balance.GPOD_RANGE, ship);
        if (g != null) return new Object[] { g[0], 0 };
        return new Object[] { m.add(dir.scale(Balance.GPOD_RANGE)), 3 };
    }

    public static int roundsAboard(ServerLevel level, UUID ship) {
        List<GunPodBlockEntity> l = podsOf(level, ship);
        if (l.isEmpty()) return -1;
        int n = 0;
        for (GunPodBlockEntity g : l) n += g.rounds;
        return n;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { leave(sl); setFiring(false); spin = 0; return false; }
        long now = sl.getGameTime();
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0) {
            UUID s = SableBridge.shipIdAt(sl, worldPosition);
            if (s == null ? ship != null : !s.equals(ship)) { leave(sl); ship = s; }
            if (ship != null) PODS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ship, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
        }
        sig = sl.hasNeighborSignal(worldPosition);
        boolean want = (sig || now < triggerUntil) && rounds > 0 && ship != null;
        boolean paid = want && ShipPower.draw(sl, ship, Balance.GPOD_FE);
        spin = paid ? Math.min(spin + 1, Balance.GPOD_SPIN_UP) : Math.max(0, spin - 1);
        boolean shooting = paid && spin >= Balance.GPOD_SPIN_UP;
        if (shooting && now >= nextBurst) {
            burst(sl, now);
            nextBurst = now + Balance.GPOD_BURST_TICKS;
        }
        setFiring(shooting);
        return paid || spin > 0;
    }

    private void burst(ServerLevel sl, long now) {
        int n = Math.min(rounds, Balance.GPOD_ROUNDS_PER_BURST);
        if (!AirGate.on() || n <= 0 || ship == null) return;
        rounds -= n;
        Vec3[] ray = muzzleRay(sl);
        if (ray == null) return;
        Vec3 m = ray[0], dir = ray[1];
        SableBridge.ShipInfo own = SableBridge.shipInfo(sl, ship);
        Vec3 ownVel = own == null ? Vec3.ZERO : ShipDefence.velocity(sl, own);
        AirGuns.Jet jet = AirGuns.boresightTarget(sl, ship, m, dir, Balance.GPOD_RANGE, Balance.GPOD_CONE);
        Vec3 to = m.add(dir.scale(Balance.GPOD_RANGE));
        int hits = 0;
        if (jet != null) {
            double rel = jet.vel().subtract(ownVel).length() * 20.0;
            double p = AirGuns.roundHit(jet.dist(), Balance.GPOD_RANGE, jet.blocks(), rel);
            hits = AirGuns.burst(sl, jet.id(), m, jet.pos(), n, p, Balance.GPOD_MAX_BLOCKS, getOwner());
            to = m.add(dir.scale(jet.dist()));
        } else {
            Object[] g = TargetingPodBlockEntity.groundRay(sl, m, dir, Balance.GPOD_RANGE, ship);
            if (g != null) {
                to = (Vec3) g[0];
                if (g[1] instanceof UUID other) {
                    if (!other.equals(ship) && !ShipStrike.spare(sl, other, getOwner()))
                        for (int i = 0; i < n && hits < Balance.GPOD_MAX_BLOCKS; i++)
                            if (sl.random.nextDouble() < Balance.GUN_GROUND_CHEW) hits += ShipStrike.chew(sl, other, m, to.add(dir.scale(0.8)), 1, (float) Balance.CIWS_SHIP_MAX_HARDNESS, getOwner());
                } else hits = AirGuns.chewGround(sl, to, dir, n, Balance.GPOD_MAX_BLOCKS);
            }
        }
        HeatLog.fired(sl, worldPosition, getOwner());
        PacketDistributor.sendToPlayersNear(sl, null, m.x, m.y, m.z, 600, new CiwsFirePayload(worldPosition, m.x, m.y, m.z, to.x, to.y, to.z, hits > 0));
        if (now >= nextSound) {
            playOnce(sl, WmdSounds.GPOD_BURST.get(), Balance.SND_RELEASE, 1.0F);
            nextSound = now + Balance.GPOD_SOUND_TICKS;
        }
        if (rounds == 0) sync();
    }

    private void setFiring(boolean v) { if (v != firing) { firing = v; sync(); } }

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
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            barrelRate += (float) (((firing ? Balance.GPOD_BARREL_RATE : 0.0) - barrelRate) * Math.min(1.0, 0.15 * dt));
            barrelAngle = (barrelAngle + barrelRate * dt) % (Math.PI * 2.0);
            boolean open = level != null && level.getGameTime() < hatchUntil;
            hatch += (float) (((open ? 1.0F : 0.0F) - hatch) * Math.min(1.0, 0.25 * dt));
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        final float a = (float) barrelAngle;
        model.getBone("barrels").ifPresent(b -> b.setRotZ(a));
        final float h = (float) Math.toRadians(70.0) * hatch * HATCH_SIGN * (flush ? -1.0F : 1.0F);
        model.getBone("hatch").ifPresent(b -> b.setRotZ(h));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean fl = firing && !dark && ((long) tt) % 2 == 0;
        model.getBone("flash").ifPresent(b -> b.setHidden(!fl));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.GPOD_SPIN.get(); }
    @Override protected float soundVolume() { return Balance.SND_SMALL; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return rounds + " rounds" + (firing ? ", FIRING" : "") + (ship == null ? ", not on a ship" : ", on " + ship); }

    @Override
    public Component status() {
        String k = firing ? "message.wmd.gun_pod.firing" : rounds == 0 ? "message.wmd.gun_pod.empty" : ship == null ? "message.wmd.gun_pod.no_ship" : "message.wmd.gun_pod.ready";
        return Component.translatable("block.wmd.gun_pod").append(": ").append(Component.translatable(k, rounds, Balance.GPOD_ROUNDS));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putBoolean("gpFlush", flush);
        tag.putInt("gpRounds", rounds);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        flush = tag.getBoolean("gpFlush");
        rounds = Math.max(0, Math.min(Balance.GPOD_ROUNDS, tag.getInt("gpRounds")));
        if (tag.contains("gpFire")) firing = tag.getBoolean("gpFire");
        if (tag.contains("gpHatch")) hatchUntil = tag.getLong("gpHatch");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("gpFlush", flush);
        tag.putInt("gpRounds", rounds);
        tag.putBoolean("gpFire", firing);
        tag.putLong("gpHatch", hatchUntil);
        return tag;
    }
}
