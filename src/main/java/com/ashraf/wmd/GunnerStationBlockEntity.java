package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class GunnerStationBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float HOOD_SIGN = 1.0F, GRIP_SIGN = 1.0F;

    static final double SEAT_Y = 9.0 / 16.0, SEAT_Z = 16.0 / 16.0, STAND_X = 14.0 / 16.0;

    static final int B_NEXT = 1, B_POD = 2;

    static final int F_READY = 1, F_BLOCKED = 2, F_FIRING = 4, F_EMPTY = 8, F_HIT = 16, F_NO_CRAFT = 32, F_NOT_OWNER = 64, F_POD = 128, F_ARC = 256,
            F_DEPLOYING = 512, F_NO_TURRET = 1024, F_HEAVY = 2048;
    static final long NONE = Long.MIN_VALUE;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private UUID seatId;
    private boolean manned;
    private UUID ship;

    private int selected;
    private boolean podView, trigger;
    private Vec3 aimPoint;
    private long lastInput = Long.MIN_VALUE / 2, nextHud;

    private float hoodShown, gripY, gripX;
    private double lastT = Double.NaN;

    public GunnerStationBlockEntity(BlockPos pos, BlockState state) {
        super(GunnerBlocks.GUNNER_STATION_BE.get(), pos, state, 0, 1, 0);
    }

    public boolean manned() { return manned; }

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

    Vec3 seatPoint() { return plotPoint(0.0, SEAT_Y, SEAT_Z); }

    Vec3 standPoint() {
        Vec3 p = plotPoint(STAND_X, 0.05, SEAT_Z);
        return level == null ? p : SableBridge.toWorld(level, p);
    }

    ServerPlayer gunner(ServerLevel sl) {
        if (seatId == null) return null;
        Entity e = sl.getEntity(seatId);
        return e instanceof GunnerSeatEntity s && s.isAlive() && s.getFirstPassenger() instanceof ServerPlayer p ? p : null;
    }

    void sit(ServerPlayer p) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return;
        if (gunner(sl) != null) { p.displayClientMessage(Component.translatable("message.wmd.gunner.taken"), true); return; }
        GunnerSeatEntity s = seatId != null && sl.getEntity(seatId) instanceof GunnerSeatEntity e && e.isAlive() ? e : null;
        if (s == null) {
            s = new GunnerSeatEntity(GunnerBlocks.GUNNER_SEAT.get(), sl);
            s.setStation(worldPosition);
            Vec3 at = seatPoint();
            Direction ahead = Machines.dirToWorld(facing(), Machines.mount(getBlockState()), Direction.NORTH);
            s.moveTo(at.x, at.y, at.z, ahead.toYRot(), 0.0F);
            sl.addFreshEntity(s);
            seatId = s.getUUID();
        }
        if (!p.startRiding(s, true)) return;
        manned = true;
        lastInput = sl.getGameTime();
        ship = SableBridge.shipIdAt(sl, worldPosition);
        selected = 0; podView = false; trigger = false; aimPoint = null;
        beep(sl, 1.2F);
        p.displayClientMessage(Component.translatable(ship == null ? "message.wmd.gunner.no_craft" : "message.wmd.gunner.seated"), true);
        nextHud = 0;
        sync();
    }

    void left(Entity who) {
        if (!(level instanceof ServerLevel sl)) return;
        manned = false; trigger = false;
        beep(sl, 0.8F);
        sync();
    }

    private void beep(ServerLevel sl, float pitch) {
        sl.playSound(null, worldPosition, WmdSounds.GN_BEEP.get(), SoundSource.BLOCKS, Balance.SND_CLICK, pitch);
    }

    void input(ServerPlayer p, Vec3 aim, boolean fire, int buttons) {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return;
        long now = sl.getGameTime();
        lastInput = now;
        if ((buttons & B_NEXT) != 0) {
            int n = ownTurrets(sl).size();
            selected = n <= 1 ? 0 : selected < 0 ? 0 : selected + 1 >= n ? -1 : selected + 1;
            beep(sl, 1.4F);
            nextHud = 0;
        }
        if ((buttons & B_POD) != 0) {
            boolean has = ship != null && TargetingPodBlockEntity.podAboard(sl, ship) != null;
            podView = has && !podView;
            if (!has) p.displayClientMessage(Component.translatable("hud.wmd.podcam.none"), true);
            beep(sl, podView ? 1.3F : 0.9F);
            nextHud = 0;
        }
        Vec3 here = SableBridge.toWorld(sl, Vec3.atCenterOf(worldPosition));
        if (aim != null && here != null && aim.distanceTo(here) <= Balance.GUNNER_EYE_MAX + Balance.GUNNER_AIM_SLACK) aimPoint = aim;
        trigger = fire;
    }

    private List<GunnerGun> ownTurrets(ServerLevel sl) {
        List<GunnerGun> out = new ArrayList<>();
        if (ship == null || getOwner() == null) return out;
        for (GunTurretBlockEntity t : GunTurretBlockEntity.turretsOf(sl, ship)) if (Objects.equals(t.getOwner(), getOwner())) out.add(t);
        for (HeavyGunBlockEntity h : HeavyGunBlockEntity.gunsOf(sl, ship)) if (Objects.equals(h.getOwner(), getOwner())) out.add(h);
        for (NavalGunBlockEntity n : NavalGunBlockEntity.gunsOf(sl, ship)) if (Objects.equals(n.getOwner(), getOwner())) out.add(n);
        return out;
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { manned = false; return false; }
        long now = sl.getGameTime();
        ServerPlayer g = gunner(sl);
        if ((g != null) != manned) { manned = g != null; sync(); }
        if ((now + (worldPosition.asLong() & 15L)) % Balance.STATION_SHIP_TICKS == 0 || (manned && ship == null && now % 20 == 0)) ship = SableBridge.shipIdAt(sl, worldPosition);
        if (!manned) return false;
        boolean fresh = now - lastInput <= 10;
        boolean craftOwner = ship != null && getOwner() != null && getOwner().equals(Crafts.owner(sl, ship));
        List<GunnerGun> l = craftOwner ? ownTurrets(sl) : List.of();
        if (selected >= l.size()) selected = l.isEmpty() ? 0 : -1;
        boolean all = podView || selected < 0;
        if (fresh && craftOwner)
            for (int i = 0; i < l.size(); i++) if (all || i == selected) l.get(i).stationCommand(now, worldPosition, aimPoint, trigger);
        if (now >= nextHud) { hud(sl, g, l, craftOwner, all); nextHud = now + Balance.GUNNER_HUD_TICKS; }
        return false;
    }

    private void hud(ServerLevel sl, ServerPlayer g, List<GunnerGun> l, boolean craftOwner, boolean all) {
        long now = sl.getGameTime();
        GunnerGun focus = null;
        if (!l.isEmpty()) {
            if (!all) focus = l.get(Mth.clamp(selected, 0, l.size() - 1));
            else { for (GunnerGun t : l) if (t.deployed()) { focus = t; break; } if (focus == null) focus = l.get(0); }
        }
        int flags = 0, rounds = 0;
        if (ship == null) flags |= F_NO_CRAFT;
        else if (!craftOwner) flags |= F_NOT_OWNER;
        if (l.isEmpty()) flags |= F_NO_TURRET;
        if (podView) flags |= F_POD;
        int max = 0;
        if (all) for (GunnerGun t : l) { rounds += t.rounds(); max += t.roundsMax(); }
        else if (focus != null) { rounds = focus.rounds(); max = focus.roundsMax(); }
        byte[] arc = new byte[0];
        float yaw = 0.0F, elev = 0.0F;
        if (focus != null) {
            if (focus.deployed()) flags |= F_READY;
            else if (focus.deploy01() > 0.0F) flags |= F_DEPLOYING;
            if (focus.blockedNow()) flags |= F_BLOCKED;
            if (focus.firingNow()) flags |= F_FIRING;
            if (focus.rounds() <= 0) flags |= F_EMPTY;
            if (now - focus.lastHitAt() <= 6) flags |= F_HIT;
            if (focus.arcReady()) { flags |= F_ARC; arc = focus.arcMap().toByteArray(); }
            yaw = focus.yawDeg(); elev = focus.elevDeg();
            if (focus.heavy()) flags |= F_HEAVY;
        }
        if (all) for (GunnerGun t : l) if (now - t.lastHitAt() <= 6) flags |= F_HIT;
        BlockPos pod = ship == null ? null : TargetingPodBlockEntity.podAboard(sl, ship);
        PacketDistributor.sendToPlayer(g, new GunnerPayloads.Hud(worldPosition, l.size(), all ? -1 : selected, focus == null ? NONE : focus.gunPos().asLong(),
                rounds, Math.max(1, max), flags, yaw, elev, pod == null ? NONE : pod.asLong(), arc));
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        float wantHood = manned ? 1.0F : 0.0F, wy = 0.0F, wx = 0.0F;
        if (manned && level != null) {
            for (GunnerSeatEntity s : level.getEntitiesOfClass(GunnerSeatEntity.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(3.0))) {
                if (!s.station().equals(worldPosition) || !(s.getFirstPassenger() instanceof Player rider)) continue;
                wy = Mth.clamp(Mth.wrapDegrees(rider.getYRot() - s.getYRot()) * 0.15F, -12.0F, 12.0F);
                wx = Mth.clamp(rider.getXRot() * 0.15F, -12.0F, 12.0F);
            }
        }
        if (!Double.isNaN(lastT) && tt > lastT) {
            double dt = Math.min(5.0, tt - lastT);
            hoodShown += (float) Mth.clamp(wantHood - hoodShown, -dt / 12.0, dt / 12.0);
            gripY += (wy - gripY) * (float) Math.min(1.0, 0.3 * dt);
            gripX += (wx - gripX) * (float) Math.min(1.0, 0.3 * dt);
        }
        if (Double.isNaN(lastT) || tt > lastT) lastT = tt;
        final float h = (float) Math.toRadians(-90.0 * hoodShown) * HOOD_SIGN;
        final float gy = (float) Math.toRadians(gripY) * GRIP_SIGN, gx = (float) Math.toRadians(gripX) * GRIP_SIGN;
        final boolean on = manned;
        model.getBone("hood").ifPresent(b -> b.setRotX(h));
        model.getBone("grips").ifPresent(b -> { b.setRotY(gy); b.setRotX(gx); });
        model.getBone("screen_on").ifPresent(b -> b.setHidden(!on));
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.RADAR_RUN.get(); }
    @Override protected boolean soundAlive() { return false; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }

    @Override
    protected String detail() { return (manned ? "manned" : "empty") + (ship == null ? ", not on a craft" : ", on " + ship); }

    @Override
    public Component status() {
        String k = !manned ? "message.wmd.gunner.status_free" : "message.wmd.gunner.status_manned";
        return Component.translatable("block.wmd.gunner_station").append(": ").append(Component.translatable(k));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        if (seatId != null) tag.putUUID("gsSeat", seatId);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        boolean client = level != null && level.isClientSide;
        copycat.load(tag, registries, client);
        seatId = tag.hasUUID("gsSeat") ? tag.getUUID("gsSeat") : null;
        if (tag.contains("gsManned")) manned = tag.getBoolean("gsManned");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putBoolean("gsManned", manned);
        return tag;
    }
}
