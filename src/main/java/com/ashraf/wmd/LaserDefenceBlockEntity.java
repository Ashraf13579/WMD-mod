package com.ashraf.wmd;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class LaserDefenceBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, RadarLinked {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 4, Z0 = -1, Z1 = 1;
    public static final int IDLE = 0, SLEW = 1, CHARGE = 2, FIRE = 3, COOL = 4;

    static final double PIVOT_Y = 56.0 / 16.0, LENS = 18.4 / 16.0;

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private int state = IDLE, stateTicks;
    private int stored;
    private float az, el;
    private long targetId = -1;
    private Vec3 aim = Vec3.ZERO;
    private int bombTicks;
    private int swId, swK = -1;
    private int chargedThisTick;
    private int frequency = 1, link;
    private boolean nav;
    private java.util.Set<Long> locked = java.util.Set.of();
    private long linkAt = Long.MIN_VALUE;
    private long chargeTick = -1;

    private final IEnergyStorage socket = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean sim) {
            if (level == null) return 0;
            long t = level.getGameTime();
            if (t != chargeTick) { chargeTick = t; chargedThisTick = 0; }
            int take = Math.max(0, Math.min(n, Math.min(Balance.LD_BUFFER_FE - stored, Balance.LD_CHARGE_IN - chargedThisTick)));
            if (!sim && take > 0) { stored += take; chargedThisTick += take; setChanged(); }
            return take;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.LD_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return stored < Balance.LD_BUFFER_FE; }
    };

    public LaserDefenceBlockEntity(BlockPos pos, BlockState state) { super(Wmd.LASER_DEFENCE_BE.get(), pos, state, 0); }

    Vec3 pivot() { double[] w = worldPoint(0.0, PIVOT_Y, 0.0); return new Vec3(w[0], w[1], w[2]); }

    Vec3 muzzle() {
        double a = Math.toRadians(az), e = Math.toRadians(el);
        return pivot().add(Math.cos(e) * Math.cos(a) * LENS, Math.sin(e) * LENS, -Math.cos(e) * Math.sin(a) * LENS);
    }

    private double range(ServerLevel sl) {
        BlockPos p = BlockPos.containing(pivot());
        return Balance.LD_RANGE * (sl.isRainingAt(p.above(8)) && !nav ? Balance.LD_RAIN : 1.0);
    }

    private double optical(double full) { return full * Balance.LD_OPTICAL_R / Balance.LD_RANGE; }

    private void refreshLink(ServerLevel sl, long now) {
        if (now - linkAt < 10) return;
        linkAt = now;
        java.util.UUID o = getOwner();
        java.util.Set<Long> l = o == null ? java.util.Set.of() : new java.util.HashSet<>(TrackNet.tracks(sl, o, frequency, now));
        int ls = o == null ? TrackNet.NO_RADAR : TrackNet.linkState(sl, o, frequency, now);
        boolean nv = o != null && Uplink.active(sl.getServer(), o);
        locked = l;
        if (ls != link || nv != nav) { link = ls; nav = nv; setChanged(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    }

    private double reachFor(long id, boolean missile, double full) {
        if (missile && locked.contains(id)) return full;
        return link >= TrackNet.RADAR_SEEKING ? full : optical(full);
    }

    private static boolean clearLine(ServerLevel sl, Vec3 from, Vec3 to) {
        Boolean blocked = net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, sl, (lv, pos) -> {
            if (!lv.isLoaded(pos)) return null;
            BlockState st = lv.getBlockState(pos);
            var shape = st.getCollisionShape(lv, pos, net.minecraft.world.phys.shapes.CollisionContext.empty());
            return lv.clipWithInteractionOverride(from, to, pos, shape, st) != null ? Boolean.TRUE : null;
        }, lv -> Boolean.FALSE);
        return !blocked;
    }

    private boolean hostile(java.util.UUID other) {
        java.util.UUID me = getOwner();
        return other != null && me != null && !other.equals(me);
    }

    private Vec3 targetPos(ServerLevel sl, FlightManager mgr, long now, double r, Vec3 from) {
        Vec3 p = null;
        if (swK >= 0) p = Swarm.posOf(sl, swId, swK, now);
        else if (targetId >= 0) { Flight f = mgr.find(targetId); if (f != null) p = f.position(now); }
        else if (targetId < -1) {
            int id = (int) (-targetId - 2);
            for (BombEntity b : BombEntity.FALLING) if (b.getId() == id && !b.isRemoved()) p = b.position();
        }
        if (p == null || p.distanceTo(from) > reachFor(targetId, swK < 0 && targetId >= 0, r)) return null;
        return p;
    }

    private Vec3 pick(ServerLevel sl, FlightManager mgr, long now, double r, Vec3 from) {
        Vec3 best = null;
        double bd = r;
        long bid = -1;
        for (Flight f : mgr.flights()) {
            if (f.build() != null && f.build().isInterceptor()) continue;
            java.util.UUID o = null;
            try { o = f.ownerId().isEmpty() ? null : java.util.UUID.fromString(f.ownerId()); } catch (IllegalArgumentException ignored) { }
            if (!hostile(o)) continue;
            Vec3 p = f.position(now);
            double d = p.distanceTo(from);
            if (d > reachFor(f.id(), true, r)) continue;
            if (d >= bd || Math.toDegrees(Math.atan2(p.y - from.y, Math.hypot(p.x - from.x, p.z - from.z))) <= 2.0) continue;
            if (!clearLine(sl, from.add(p.subtract(from).normalize().scale(2.5)), p)) continue;
            bd = d; best = p; bid = f.id();
        }
        for (BombEntity b : BombEntity.FALLING) {
            if (b.level() != sl || b.isRemoved() || !hostile(b.owner())) continue;
            Vec3 p = b.position();
            double d = p.distanceTo(from);
            if (d > reachFor(-1, false, r)) continue;
            if (d >= bd || Math.toDegrees(Math.atan2(p.y - from.y, Math.hypot(p.x - from.x, p.z - from.z))) <= 2.0) continue;
            if (!clearLine(sl, from.add(p.subtract(from).normalize().scale(2.5)), p)) continue;
            bd = d; best = p; bid = -(long) b.getId() - 2;
        }
        swK = -1;
        Swarm.Target sw = Swarm.nearestHostile(sl, getOwner(), u -> false, from, Math.min(bd, reachFor(-1, false, r)), now);
        if (sw != null && Math.toDegrees(Math.atan2(sw.pos().y - from.y, Math.hypot(sw.pos().x - from.x, sw.pos().z - from.z))) > 2.0
                && clearLine(sl, from.add(sw.pos().subtract(from).normalize().scale(2.5)), sw.pos())) {
            best = sw.pos(); bid = -1; swId = sw.id(); swK = sw.k();
        }
        targetId = bid;
        return best;
    }

    private boolean slew(Vec3 from, Vec3 p) {
        Vec3 d = p.subtract(from);
        float tAz = (float) Math.toDegrees(Math.atan2(-d.z, d.x)), tEl = (float) Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -5.0, 85.0);
        float dAz = Mth.wrapDegrees(tAz - az), dEl = tEl - el;
        az = Mth.wrapDegrees(az + (float) Mth.clamp(dAz, -Balance.LD_AZ_RATE, Balance.LD_AZ_RATE));
        el += (float) Mth.clamp(dEl, -Balance.LD_EL_RATE, Balance.LD_EL_RATE);
        return Math.abs(dAz) < 2.0F && Math.abs(dEl) < 2.0F;
    }

    public static void serverTickLaser(Level level, BlockPos pos, BlockState bs, LaserDefenceBlockEntity be) {
        ProcessUnitBlockEntity.serverTick(level, pos, bs, be);
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || getOwner() == null) return false;
        long now = sl.getGameTime();
        stateTicks++;
        FlightManager mgr = FlightManager.get(sl);
        refreshLink(sl, now);
        Vec3 from = pivot();
        double r = range(sl);
        Vec3 p = state == IDLE || state == COOL ? null : targetPos(sl, mgr, now, r, from);
        int was = state;
        switch (state) {
            case IDLE -> {
                if (now % 4 == 0 && stored >= Balance.LD_FIRE_FE * 20) { p = pick(sl, mgr, now, r, from); if (p != null) go(SLEW); }
            }
            case SLEW -> {
                if (p == null) { go(IDLE); break; }
                if (slew(from, p)) go(CHARGE);
                if (stateTicks % 60 == 1) boom(WmdSounds.LD_SLEW_LOOP.get(), 2.0F);
            }
            case CHARGE -> {
                if (p == null) { go(COOL); break; }
                slew(from, p);
                if (stateTicks == 1) boom(WmdSounds.LD_CHARGE.get(), 3.0F);
                if (stateTicks >= Balance.LD_CHARGE_TICKS) go(FIRE);
            }
            case FIRE -> {
                if (p == null || stored < Balance.LD_FIRE_FE) { go(COOL); break; }
                slew(from, p);
                aim = p;
                stored -= Balance.LD_FIRE_FE;
                if (stateTicks % 80 == 1) boom(WmdSounds.LD_BEAM_LOOP.get(), 5.0F);
                if (swK >= 0) {
                    if (++bombTicks >= Balance.SW_LASER_TICKS) {
                        if (Swarm.shoot(sl, swId, swK, now)) burnt(sl, p);
                        Vec3 next = pick(sl, mgr, now, r, from);
                        if (next != null && swK >= 0) go(FIRE); else go(COOL);
                    }
                } else if (targetId >= 0) {
                    Flight f = mgr.find(targetId);
                    if (f != null) mgr.damage(sl, f, Balance.LD_DMG);
                    if (mgr.find(targetId) == null) burnt(sl, p);
                } else if (++bombTicks >= Balance.LD_BOMB_TICKS) {
                    bombTicks = 0;
                    int id = (int) (-targetId - 2);
                    for (BombEntity b : new java.util.ArrayList<>(BombEntity.FALLING))
                        if (b.getId() == id && !b.isRemoved()) { b.shotDown(sl, getOwner(), getOwnerName()); if (b.isRemoved()) burnt(sl, p); }
                }
            }
            case COOL -> {
                if (stateTicks == 1) boom(WmdSounds.LD_COOL.get(), 2.0F);
                if (stateTicks >= Balance.LD_COOL_TICKS) go(IDLE);
            }
            default -> { }
        }
        if (state != was || (state == FIRE || state == SLEW || state == CHARGE) && now % 2 == 0) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return state == FIRE;
    }

    private void burnt(ServerLevel sl, Vec3 p) {
        sl.playSound(null, p.x, p.y, p.z, WmdSounds.LD_BURN.get(), SoundSource.BLOCKS, 12.0F, 1.0F);
        Wmd.LOGGER.info("[laser] {} burnt its target at {} {} {}", worldPosition.toShortString(), Math.round(p.x), Math.round(p.y), Math.round(p.z));
        go(COOL);
    }

    private void go(int s) { state = s; stateTicks = 0; bombTicks = 0; setChanged(); }

    private void boom(SoundEvent e, float vol) {
        Vec3 p = pivot();
        if (level != null) level.playSound(null, p.x, p.y, p.z, e, SoundSource.BLOCKS, vol, 1.0F);
    }

    @Override public void empDrain() { stored = 0; if (state != IDLE) go(COOL); setChanged(); }

    public int state() { return state; }
    public float az() { return az; }
    public float el() { return el; }
    public Vec3 aim() { return aim; }
    public boolean firing() { return state == FIRE; }

    @Override public int getFrequency() { return frequency; }
    @Override
    public void setFrequency(int f) {
        frequency = Mth.clamp(f, 1, 9999);
        linkAt = Long.MIN_VALUE;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private float modelYaw() {
        float f = switch (facing()) { case EAST -> 90.0F; case SOUTH -> 180.0F; case WEST -> 270.0F; default -> 0.0F; };
        return (float) Math.toRadians(az + f);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        model.getBone("ld_yaw").ifPresent(b -> b.setRotY(modelYaw()));
        float k = ClientLaserDefence.kick(worldPosition) * 1.6F;
        double er = Math.toRadians(el);
        model.getBone("ld_pitch").ifPresent(b -> { b.setRotZ((float) er); b.setPosX(k * (float) Math.cos(er)); b.setPosY(-k * (float) Math.sin(er)); });
        boolean lens = state == FIRE || state == CHARGE && ((long) tt) % 3 != 0;
        float s = state == FIRE ? 1.0F + 0.45F * ClientLaserDefence.flash(worldPosition, tt) : 1.0F;
        model.getBone("ld_lens").ifPresent(b -> { b.setHidden(!lens); b.setScaleY(s); b.setScaleZ(s); });
    }

    public static void clientTickLaser(Level level, BlockPos pos, BlockState bs, LaserDefenceBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, bs, be);
        ClientLaserDefence.track(be);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.LD_BEAM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 0); }
    @Override protected float soundVolume() { return 0.0F; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int inputTanks() { return 0; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return socket; }
    @Override public int guiFeUse() { return state == FIRE ? Balance.LD_FIRE_FE : 0; }
    @Override public int guiStatus() { return state == FIRE || state == CHARGE || state == SLEW ? 1 : stored < Balance.LD_FIRE_FE * 20 ? 4 : 0; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.ld_range", Balance.LD_RANGE, Balance.LD_RANGE / 2),
                Component.translatable("gui.wmd.info.ld_power", String.format("%,d", Balance.LD_FIRE_FE), String.format("%,d", Balance.LD_BUFFER_FE)),
                Component.translatable("gui.wmd.info.ld_state." + state),
                Component.translatable("gui.wmd.info.ld_link", frequency, Component.translatable("gui.wmd.info.ld_link." + Mth.clamp(link, 0, 3)),
                        Balance.LD_RANGE, Balance.LD_OPTICAL_R),
                Component.translatable(nav ? "gui.wmd.info.ld_nav" : "gui.wmd.info.ld_nonav"));
    }

    @Override protected String detail() { return "state " + state + ", az " + Math.round(az) + ", el " + Math.round(el) + ", buffer " + stored + " FE, target " + targetId + ", freq " + frequency + " link " + link + (nav ? " nav" : "") + " locked " + locked.size(); }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 1, 0, 0, Direction.EAST) || at(part, side, -1, 0, 0, Direction.WEST) ? socket : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-1, 1, 3).power(1, 0, 0, Direction.EAST, false).power(-1, 0, 0, Direction.WEST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putState(tag);
        tag.putInt("ldstored", stored);
        tag.putBoolean("ldsave", true);
        ownerData.write(tag);
    }

    private void putState(CompoundTag tag) {
        tag.putInt("ldstate", state);
        tag.putFloat("ldaz", az);
        tag.putFloat("ldel", el);
        tag.putDouble("ldx", aim.x); tag.putDouble("ldy", aim.y); tag.putDouble("ldz", aim.z);
        tag.putInt("ldfreq", frequency); tag.putInt("ldlink", link); tag.putBoolean("ldnav", nav);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ldstate")) {
            state = Mth.clamp(tag.getInt("ldstate"), IDLE, COOL);
            az = tag.getFloat("ldaz"); el = tag.getFloat("ldel");
            aim = new Vec3(tag.getDouble("ldx"), tag.getDouble("ldy"), tag.getDouble("ldz"));
            if (tag.contains("ldfreq")) frequency = Mth.clamp(tag.getInt("ldfreq"), 1, 9999);
            link = Mth.clamp(tag.getInt("ldlink"), 0, 3);
            nav = tag.getBoolean("ldnav");
        }
        if (tag.contains("ldstored")) stored = Mth.clamp(tag.getInt("ldstored"), 0, Balance.LD_BUFFER_FE);
        if (tag.contains("ldsave")) { ownerData.read(tag); if (state != IDLE) state = COOL; }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        tag.putInt("ldstored", stored);
        return tag;
    }
}
