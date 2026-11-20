package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

public class TeslaTowerBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ModeMachine, CopycatState.Host {

    public static final int X0 = -1, X1 = 1, Y0 = 0, Y1 = 8, Z0 = -1, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).power(0, 0, Z1, Direction.SOUTH, false);
    public static final int ST_NO_OWNER = 0, ST_OFF = 1, ST_NO_POWER = 2, ST_WATCHING = 3, ST_CHARGING = 4, ST_STRIKE = 5;

    static final double TOR_Y = 126.0, BRK_R = 31.5, DOOR_SLIDE = 9.0;
    static final float DOOR_SIGN = 1.0F, BRK_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();
    private int mode;

    private int chargeTicks;
    private Set<BlockPos> ownCellsCache;
    private BlockState ownCellsFor;

    private int status = ST_NO_OWNER, targets, lastHits;
    private boolean charging;
    private long lastTargetAt = Long.MIN_VALUE / 2, lastStrikeAt = Long.MIN_VALUE / 2;
    private float brk, chargePm;

    private float prevBrk, gapAng, doorVal;
    private double lastTT = Double.NaN;
    private boolean placedClient;

    public TeslaTowerBlockEntity(BlockPos pos, BlockState state) {
        super(TeslaBlocks.TESLA_TOWER_BE.get(), pos, state, Balance.TT_IDLE_FE, Balance.TT_BUFFER_FE, Balance.TT_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    @Override public int mode() { return mode; }
    @Override public int modeCount() { return 2; }
    @Override public void setMode(int m) { mode = Mth.clamp(m, 0, 1); sync(); }
    @Override public void setModeClient(int m) { mode = Mth.clamp(m, 0, 1); }
    public boolean off() { return mode == 1; }

    @Override public CopycatState copycat() { return copycat; }
    @Override public void copycatChanged() { sync(); }

    @Override
    public List<net.minecraft.world.item.ItemStack> takeUpgrades() {
        List<net.minecraft.world.item.ItemStack> l = super.takeUpgrades();
        net.minecraft.world.item.ItemStack k = copycat.takeItem();
        if (!k.isEmpty()) l.add(k);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int teslaStatus() { return status; }
    public int targetsInRange() { return targets; }
    public int lastHits() { return lastHits; }
    public int chargePercent() { return Math.round(chargePm / 10.0F); }
    public int secondsSinceStrike() { return level == null || lastStrikeAt < 0 ? -1 : (int) ((level.getGameTime() - lastStrikeAt) / 20L); }
    public double range() { return level instanceof ServerLevel sl ? range(sl) : Balance.TT_RANGE; }

    private record Tgt(Vec3 pos, int kind, Object ref) {}

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        if (getOwner() == null || off()) {
            chargeTicks = 0;
            setState(getOwner() == null ? ST_NO_OWNER : ST_OFF, false, 0);
            return false;
        }
        boolean due = (now + (worldPosition.asLong() & 3L)) % 5 == 0;
        if (!due && !charging) return true;
        if (!due && chargeTicks + 1 < Balance.TT_CHARGE_TICKS) {
            if (useExtraEnergy(Balance.TT_STRIKE_FE / Balance.TT_CHARGE_TICKS)) chargeTicks++;
            setState(ST_CHARGING, true, targets);
            return true;
        }
        List<Tgt> ts = scan(sl);
        if (ts.isEmpty()) {
            chargeTicks = 0;
            setState(ST_WATCHING, false, 0);
            return true;
        }
        lastTargetAt = now;
        Vec3 top = toroid();
        Tgt first = ts.get(0);
        double best = Double.MAX_VALUE;
        for (Tgt t : ts) { double d = t.pos().distanceToSqr(top); if (d < best) { best = d; first = t; } }
        float want = spikeAngle(first.pos());
        if (Math.abs(Mth.wrapDegrees(want - brk)) > 1.0F) { brk = want; sync(); }
        if (useExtraEnergy(Balance.TT_STRIKE_FE / Balance.TT_CHARGE_TICKS)) chargeTicks++;
        if (chargeTicks >= Balance.TT_CHARGE_TICKS) {
            chargeTicks = 0;
            strike(sl, first, ts, now);
            setState(ST_STRIKE, true, ts.size());
        } else setState(ST_CHARGING, true, ts.size());
        return true;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level != null && !level.isClientSide && status != ST_NO_POWER) { charging = false; setState(ST_NO_POWER, false, 0); }
    }

    private void setState(int st, boolean ch, int n) {
        float pm = chargeTicks * 1000.0F / Balance.TT_CHARGE_TICKS;
        if (st != status || ch != charging || n != targets || Math.abs(pm - chargePm) > 150.0F) {
            status = st;
            charging = ch;
            targets = n;
            chargePm = pm;
            sync();
        }
    }

    private double range(ServerLevel sl) {
        BlockPos top = worldPosition.above(9);
        return sl.isThundering() && sl.isRainingAt(top) || StormField.raining(sl, toroid()) ? Balance.TT_STORM_RANGE : Balance.TT_RANGE;
    }

    private List<Tgt> scan(ServerLevel sl) {
        List<Tgt> out = new ArrayList<>();
        Vec3 top = toroid();
        double r = range(sl);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(top, top).inflate(r), e -> DefenceTargets.hostile(sl, this, e)))
            if (aim(e).distanceTo(top) <= r && sees(sl, top, aim(e))) out.add(new Tgt(aim(e), 0, e));
        if (sl.getServer() != null)
            for (Drones.Drone d : Drones.get(sl.getServer()).drones())
                if (d.airborne() && !getOwner().equals(d.owner()) && d.pos().distanceTo(top) <= r && sees(sl, top, d.pos())) out.add(new Tgt(d.pos(), 1, d));
        long now = sl.getGameTime();
        for (Flight f : FlightManager.get(sl).flights()) {
            MissileBuild b = f.build();
            if (b != null && b.isInterceptor()) continue;
            if (!f.ownerId().isEmpty()) {
                try { UUID fo = UUID.fromString(f.ownerId()); if (Access.trusts(this, fo) || Access.listedFriend(this, fo)) continue; } catch (IllegalArgumentException ignored) {}
            }
            Vec3 p = f.position(now);
            if (p.distanceTo(top) <= Balance.TT_MISSILE_R) out.add(new Tgt(p, 2, f));
        }
        if (AirGate.on())
            for (AirMissiles.Shot s : AirMissiles.inFlight(sl))
                if (s.end == null && s.pos.distanceTo(top) <= Balance.TT_MISSILE_R && (s.owner == null || !(Access.trusts(this, s.owner) || Access.listedFriend(this, s.owner)))) out.add(new Tgt(s.pos, 3, s));
        return out;
    }

    private static Vec3 aim(Entity e) { return e.position().add(0.0, e.getBbHeight() * 0.55, 0.0); }

    private void strike(ServerLevel sl, Tgt first, List<Tgt> all, long now) {
        List<Vec3> pts = new ArrayList<>();
        Vec3 from = spikeTip();
        pts.add(from);
        List<Tgt> left = new ArrayList<>(all);
        Tgt cur = first;
        float dmg = Balance.TT_DMG;
        int hits = 0, water = 0;
        while (cur != null && hits < Balance.TT_CHAIN) {
            left.remove(cur);
            boolean wet = false;
            switch (cur.kind()) {
                case 0 -> {
                    LivingEntity e = (LivingEntity) cur.ref();
                    wet = e.isInWater();
                    if (!CraftShield.stopsAt(sl, cur.pos(), pts.get(pts.size() - 1), Balance.CSH_ARC_FE, "a Tesla arc"))
                        e.hurt(sl.damageSources().lightningBolt(), wet ? Balance.TT_DMG : dmg);
                }
                case 1 -> Drones.get(sl.getServer()).shootDown(sl, (Drones.Drone) cur.ref(), getOwner(), getOwnerName());
                case 2 -> FlightManager.get(sl).kill(sl, (Flight) cur.ref());
                default -> ((AirMissiles.Shot) cur.ref()).end = "zapped";
            }
            if (wet) water |= 1 << hits;
            pts.add(cur.pos());
            hits++;
            dmg *= 0.5F;
            Tgt next = null;
            double best = Balance.TT_JUMP * Balance.TT_JUMP;
            for (Tgt t : left) { double d = t.pos().distanceToSqr(cur.pos()); if (d <= best && sees(sl, cur.pos(), t.pos())) { best = d; next = t; } }
            cur = next;
        }
        lastHits = hits;
        lastStrikeAt = now;
        sl.playSound(null, from.x, from.y, from.z, WmdSounds.TESLA_CRACK.get(), SoundSource.BLOCKS, Balance.SND_RELEASE, 0.9F + sl.random.nextFloat() * 0.2F);
        TeslaStrikePayload pay = new TeslaStrikePayload(pts, water);
        for (ServerPlayer p : sl.players()) if (p.position().distanceTo(from) <= 128.0) PacketDistributor.sendToPlayer(p, pay);
        sync();
    }

    private boolean sees(ServerLevel sl, Vec3 from, Vec3 to) {
        Set<BlockPos> own = ownCells();
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, sl, (lv, pos) -> {
            if (own.contains(pos)) return null;
            BlockState st = lv.getBlockState(pos);
            return lv.clipWithInteractionOverride(from, to, pos, st.getCollisionShape(lv, pos), st);
        }, lv -> null);
        if (hit != null && hit.getType() != HitResult.Type.MISS) return false;
        return SmokeScreens.entry(sl, from, to) < 0.0;
    }

    private Set<BlockPos> ownCells() {
        if (ownCellsCache != null && ownCellsFor == getBlockState()) return ownCellsCache;
        Set<BlockPos> s = new HashSet<>();
        s.add(worldPosition);
        if (getBlockState().getBlock() instanceof MachineBlock mb)
            for (BlockPos l : mb.footprint()) s.add(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), l.getX(), l.getY(), l.getZ()));
        ownCellsFor = getBlockState();
        return ownCellsCache = s;
    }

    private Vec3 world(double x, double y, double z) {
        double[] w = worldPoint(x / 16.0, y / 16.0, z / 16.0);
        return new Vec3(w[0], w[1], w[2]);
    }

    Vec3 toroid() { return world(0.0, TOR_Y, 0.0); }

    Vec3 spikeTip() {
        double a = Math.toRadians(brk);
        return world(Math.cos(a) * BRK_R, TOR_Y, -Math.sin(a) * BRK_R);
    }

    private float spikeAngle(Vec3 p) {
        Vec3 c = toroid(), e = world(16.0, TOR_Y, 0.0), n = world(0.0, TOR_Y, -16.0);
        Vec3 ex = e.subtract(c).normalize(), nz = n.subtract(c).normalize(), d = p.subtract(c);
        return (float) Math.toDegrees(Math.atan2(d.dot(nz), d.dot(ex)));
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }

    static void clientTickTesla(Level level, BlockPos pos, BlockState state, TeslaTowerBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (!be.placedClient) { be.placedClient = true; be.prevBrk = be.brk; }
        boolean storm = level.isThundering() && level.isRainingAt(pos.above(9)) && be.status >= ST_WATCHING;
        if (be.charging || storm) ClientTesla.streamers(pos, be.toroid(), 22.0 / 16.0, level.getGameTime(), be.charging);
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double dt = Double.isNaN(lastTT) ? 0.0 : Mth.clamp(tt - lastTT, 0.0, 5.0);
        lastTT = tt;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        boolean live = !dark && status >= ST_WATCHING;
        double c = chargePm / 1000.0;
        double v = !live ? 0.0 : charging ? 18.0 + 30.0 * c : 2.5;
        gapAng = (float) ((gapAng + v * dt) % 360.0);
        final float g = (float) Math.toRadians(gapAng);
        model.getBone("gap").ifPresent(b -> b.setRotZ(g));
        double since = tt - lastTargetAt;
        double want = live && since < Balance.TT_DOOR_HOLD ? 1.0 : 0.0;
        doorVal += (float) ((want - doorVal) * Math.min(1.0, dt * 0.12));
        final float door = DOOR_SIGN * (float) (DOOR_SLIDE * doorVal);
        model.getBone("door_l").ifPresent(b -> b.setPosZ(door));
        model.getBone("door_r").ifPresent(b -> b.setPosZ(door));
        for (int i = 0; i < 4; i++) {
            final boolean lit = live && (i == 0 || (charging && c >= 0.25 * i) || tt - lastStrikeAt < 6.0);
            model.getBone("prim" + i).ifPresent(b -> b.setHidden(!lit));
        }
        float a = prevBrk + Mth.wrapDegrees(brk - prevBrk) * Math.min(1.0F, (float) dt * 0.5F + 0.1F);
        prevBrk = a;
        final float br = BRK_SIGN * (float) Math.toRadians(a);
        model.getBone("breakout").ifPresent(b -> b.setRotY(br));
        final boolean tg = live && (charging || tt - lastStrikeAt < 10.0);
        model.getBone("toroidglow").ifPresent(b -> b.setHidden(!tg));
        model.getBone("lamp").ifPresent(b -> b.setHidden(!live));
        final float spin = (float) ((tt * 0.3) % (Math.PI * 2.0));
        model.getBone("beacon").ifPresent(b -> { b.setHidden(!(live && charging)); b.setRotY(spin); });
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float progress01() { return charging ? chargePm / 1000.0F : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.tesla_tower", (int) Balance.TT_RANGE, (int) Balance.TT_STORM_RANGE, Balance.TT_CHAIN, (int) Balance.TT_JUMP),
                Component.translatable("gui.wmd.info.tesla_tower_fe", String.format("%,d", Balance.TT_STRIKE_FE), Balance.TT_CHARGE_TICKS / 20.0F));
    }

    @Override
    protected String detail() { return "status " + status + ", targets " + targets + ", last hits " + lastHits + (off() ? ", OFF" : ""); }

    @Override protected SoundEvent runSound() { return WmdSounds.TESLA_BUZZ_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 0); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        tag.putInt("tMode", mode);
        put(tag);
    }

    private void put(CompoundTag tag) {
        tag.putInt("tSt", status);
        tag.putInt("tTg", targets);
        tag.putInt("tHits", lastHits);
        tag.putBoolean("tCh", charging);
        tag.putLong("tLast", lastTargetAt);
        tag.putLong("tStrike", lastStrikeAt);
        tag.putFloat("tBrk", brk);
        tag.putFloat("tPm", chargePm);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        mode = Mth.clamp(tag.getInt("tMode"), 0, 1);
        status = tag.getInt("tSt");
        targets = tag.getInt("tTg");
        lastHits = tag.getInt("tHits");
        charging = tag.getBoolean("tCh");
        if (tag.contains("tLast")) lastTargetAt = tag.getLong("tLast");
        if (tag.contains("tStrike")) lastStrikeAt = tag.getLong("tStrike");
        brk = tag.getFloat("tBrk");
        chargePm = tag.getFloat("tPm");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        tag.putInt("tMode", mode);
        put(tag);
        return tag;
    }
}
