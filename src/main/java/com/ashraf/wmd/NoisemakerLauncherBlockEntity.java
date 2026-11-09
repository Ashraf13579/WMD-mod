package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class NoisemakerLauncherBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    public static final int S_READY = 0, S_EMPTY = 1, S_CHARGING = 2, S_NO_POWER = 3, S_BLOCKED = 4, S_DRY = 5, S_FIRED = 99;
    public static final int A_FIRE = 0, A_AUTO = 1;
    private static final PortMap PORT_MAP = new PortMap(0, 0, 1).power(0, 0, 0, Direction.SOUTH, false);

    static final int DOOR_OPEN = 3, DOOR_SHUT = 24;
    static final float DOOR_SIGN = 1.0F;

    static final float LEVER_SIGN = 1.0F;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot mag = new Slot(Balance.DECOY_MAG, false, s -> s.is(Wmd.NOISEMAKER.get()));
    private final Slot[] slots = { mag };
    private final List<FluidTank> gauges = List.of(mag.gauge);
    private final OwnerData ownerData = new OwnerData();
    private int charge = Balance.DECOY_CHARGE_TICKS, status = S_EMPTY;
    private boolean auto, lastSignal;
    private long lastFire = -1000L;
    private UUID lastDecoy;

    private int shownCount;

    public NoisemakerLauncherBlockEntity(BlockPos pos, BlockState state) { super(Wmd.NOISEMAKER_LAUNCHER_BE.get(), pos, state, Balance.DECOY_FE); }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean auto() { return auto; }
    public int fireStatus() { return status; }
    public int loaded() { return level != null && level.isClientSide ? shownCount : mag.count(); }
    public long lastFire() { return lastFire; }
    public int charge01x100() { return Math.min(100, charge * 100 / Math.max(1, Balance.DECOY_CHARGE_TICKS)); }

    public void act(Player by, int action, int value) {
        if (action == A_FIRE) {
            int r = fire();
            if (r != S_FIRED && by != null) by.displayClientMessage(Component.translatable("message.wmd.noisemaker_launcher.st." + r), true);
        } else if (action == A_AUTO) auto = value != 0;
        status = why();
        setChanged();
        sync();
    }

    Vec3[] worldMuzzle() {
        double[] p = worldPoint(0.0, 0.5, -0.5), q = worldPoint(0.0, 0.5, -1.5);
        Vec3 a = new Vec3(p[0], p[1], p[2]), d = new Vec3(q[0] - p[0], q[1] - p[1], q[2] - p[2]);
        return new Vec3[] { a, d.lengthSqr() < 1e-6 ? new Vec3(0, 0, -1) : d.normalize() };
    }

    private boolean blocked() {
        if (level == null) return true;
        BlockPos front = Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), 0, 0, -1);
        return !level.getBlockState(front).getCollisionShape(level, front).isEmpty();
    }

    private boolean wet() {
        if (level == null) return false;
        Vec3[] m = worldMuzzle();
        return level.getFluidState(BlockPos.containing(m[0].add(m[1].scale(0.6)))).is(FluidTags.WATER);
    }

    private int why() {
        if (mag.stack.isEmpty()) return S_EMPTY;
        if (charge < Balance.DECOY_CHARGE_TICKS) return hasPower() ? S_CHARGING : S_NO_POWER;
        if (blocked()) return S_BLOCKED;
        if (!wet()) return S_DRY;
        return S_READY;
    }

    public int fire() {
        if (!(level instanceof ServerLevel sl)) return S_EMPTY;
        int w = why();
        if (w != S_READY) return w;
        Vec3[] m = worldMuzzle();
        NoisemakerEntity e = NoisemakerEntity.eject(sl, m[0].add(m[1].scale(0.35)), m[1], getOwner());
        lastDecoy = e.getUUID();
        mag.take(1);
        charge = 0;
        lastFire = sl.getGameTime();
        Vec3 out = m[0].add(m[1].scale(0.5));
        sl.sendParticles(ParticleTypes.BUBBLE, out.x, out.y, out.z, 24, 0.25, 0.25, 0.25, 0.15);
        sl.playSound(null, out.x, out.y, out.z, WmdSounds.TP_TUBE_FIRE.get(), SoundSource.BLOCKS, 0.9F, 1.6F);
        status = why();
        setChanged();
        sync();
        return S_FIRED;
    }

    private boolean autoThreat(ServerLevel sl) {
        if (lastDecoy != null && sl.getEntity(lastDecoy) instanceof NoisemakerEntity n && n.isAlive() && n.running()) return false;
        Vec3 muzzle = worldMuzzle()[0];
        UUID ship = SableBridge.active() ? SableBridge.shipIdAt(sl, worldPosition) : null;
        SableBridge.ShipInfo si = ship == null ? null : SableBridge.shipInfo(sl, ship);
        AABB box = (si != null ? si.worldBounds() : new AABB(muzzle, muzzle)).inflate(Balance.DECOY_AUTO_RANGE);
        double r2 = Balance.DECOY_AUTO_RANGE * Balance.DECOY_AUTO_RANGE;
        for (TorpedoEntity t : sl.getEntitiesOfClass(TorpedoEntity.class, box)) {
            if (!hostile(t)) continue;
            if (si == null && t.position().distanceToSqr(muzzle) > r2) continue;
            return true;
        }
        return false;
    }

    boolean hostile(TorpedoEntity t) {
        if (t == null || !t.isAlive()) return false;
        TorpedoKind k = t.kind();
        if (k == null || k == TorpedoKind.ATT || k.drone()) return false;
        UUID o = t.owner();
        return o == null || !Access.trusts(this, o);
    }

    @Override
    protected boolean process() {
        boolean busy = false, changed = false;
        if (charge < Balance.DECOY_CHARGE_TICKS) { charge++; busy = true; if (charge == Balance.DECOY_CHARGE_TICKS) changed = true; }
        boolean sig = signal();
        if (sig && !lastSignal) { fire(); changed = true; }
        lastSignal = sig;
        if (auto && level instanceof ServerLevel sl && sl.getGameTime() % Balance.DECOY_SCAN_TICKS == Math.floorMod(worldPosition.asLong(), Balance.DECOY_SCAN_TICKS)
                && !mag.stack.isEmpty() && charge >= Balance.DECOY_CHARGE_TICKS && autoThreat(sl)) {
            if (fire() == S_FIRED) Wmd.LOGGER.info("Decoy launcher at {} fired AUTO", worldPosition.toShortString());
            changed = true;
        }
        if (changed || (level != null && level.getGameTime() % 20 == 0)) {
            int old = status;
            status = why();
            if (changed || old != status) { setChanged(); sync(); }
        }
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        lastSignal = signal();
        if (level != null && level.getGameTime() % 20 == 0) { int old = status; status = why(); if (old != status) { setChanged(); sync(); } }
    }

    private boolean signal() { return level != null && level.hasNeighborSignal(worldPosition); }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        float since = level == null ? 999.0F : (float) (level.getGameTime() - lastFire) + partialTick;
        float open = since < 0 ? 0.0F : since < DOOR_OPEN ? since / DOOR_OPEN : since < DOOR_SHUT - 8 ? 1.0F : since < DOOR_SHUT ? (DOOR_SHUT - since) / 8.0F : 0.0F;
        final float d = (float) Math.toRadians(100.0) * open * DOOR_SIGN;
        model.getBone("door").ifPresent(b -> b.setRotX(d));
        final float lv = since < 10.0F ? (float) Math.toRadians(35.0) * LEVER_SIGN * (since < 2.0F ? since / 2.0F : 1.0F - (since - 2.0F) / 8.0F) : 0.0F;
        model.getBone("lever").ifPresent(b -> b.setRotX(lv));
        final int n = shownCount;
        for (int i = 0; i < 4; i++) { final boolean on = i < n; model.getBone("mag" + i).ifPresent(b -> b.setHidden(!on)); }
        final boolean ready = status == S_READY;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!ready));
    }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY); }
    @Override protected int inputTanks() { return 1; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return charge < Balance.DECOY_CHARGE_TICKS ? charge / (float) Balance.DECOY_CHARGE_TICKS : -1.0F; }
    @Override protected List<String> labelKeys() { return List.of("noisemaker"); }
    @Override protected String[] iconIds() { return new String[] { "noisemaker" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.TP_TUBE_FIRE.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected BlockPos soundLocal() { return BlockPos.ZERO; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 0, Direction.SOUTH) ? energy() : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", Balance.DECOY_FE * 20),
                Component.translatable("gui.wmd.info.noisemaker_launcher", Balance.DECOY_MAG, Balance.TORP_DECOY_TICKS / 20, (int) Balance.DECOY_AUTO_RANGE));
    }

    @Override
    protected String detail() {
        return mag.count() + " / " + Balance.DECOY_MAG + " noisemakers, charge " + charge01x100() + "%, AUTO " + (auto ? "on" : "off") + ", status " + status;
    }

    @Override
    protected void saveExtra(CompoundTag tag) {
        tag.putInt("dlCharge", charge);
        tag.putBoolean("dlAuto", auto);
        tag.putLong("dlLast", lastFire);
        tag.putInt("dlStatus", status);
        if (lastDecoy != null) tag.putUUID("dlDecoy", lastDecoy);
        ownerData.write(tag);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        charge = tag.contains("dlCharge") ? tag.getInt("dlCharge") : Balance.DECOY_CHARGE_TICKS;
        auto = tag.getBoolean("dlAuto");
        lastFire = tag.contains("dlLast") ? tag.getLong("dlLast") : -1000L;
        status = tag.getInt("dlStatus");
        lastDecoy = tag.hasUUID("dlDecoy") ? tag.getUUID("dlDecoy") : null;
        ownerData.read(tag);
        if (tag.contains("dlCount")) shownCount = tag.getInt("dlCount");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveExtra(tag);
        tag.putInt("dlCount", mag.count());
        return tag;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide && mag.count() != shownCount) { shownCount = mag.count(); sync(); }
    }
}
