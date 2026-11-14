package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class OrdnanceLoaderBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, CopycatState.Host {

    static final float RX = 1F, RY = 1F, RZ = 1F, PX = -1F;

    record Geo(int n, double k, double stageLen, double baseFront, double gAz, double gE, double gExt, double hoistRise) {
        static final int STAGES = 6;
        int d() { return n + 1; }
        double c() { return 8.0 * (n - 1); }
        double platUp() { return 13.0; }
        double shY() { return 10.5; }
        double shZ() { return baseFront + stageLen + 0.4 - 3.0 * k; }
        double yawZ() { return shZ() - 4.0 * k; }
        double slideMax() { return 0.75 * stageLen; }
        double tipFront() { return baseFront - 0.8 * STAGES; }

        double sadC() { return tipFront() - 1.4 - 2.0 * k; }
        double sadTop() { return 13.7; }
        double hoistX() { return c() + 8.0 * k; }
        double hoistZ() { return -6.6 + 3.6 * k + 0.6; }

        double hoistTop() { return 10.5; }
        double footY() { return 7.6; }
        double footZ() { return shZ() - 8.5 * k; }
        double lugY() { return 8.8; }
        double lugZ() { return shZ() - 2.6 * k; }

        double l0() { return shZ() - sadC(); }
        double lAdd() { return STAGES * slideMax(); }

        double gWrist() { return gE - 90.0; }
    }

    static final Geo SMALL = new Geo(2, 1.0, 26.5, 6.4, -15.58, 26.87, 0.0, 3.836), HEAVY = new Geo(3, 1.6, 37.0, 10.0, -19.35, 27.38, 0.0102, 2.5);

    static final double DEMO_UP = 40.0, DEMO_NORTH = 80.0, DEMO_WEST = 24.0;

    private static final int RELEASED = 9;

    private final boolean heavy;
    private final Geo g;
    private final OwnerData ownerData = new OwnerData();
    private final CopycatState copycat = new CopycatState();

    private long cycleAt = -1;

    private long pausedAt = -1;
    private float tAz, tE, tExt, tLift, tRun;
    private ItemStack carried = ItemStack.EMPTY;

    private UUID jobShip;
    private BlockPos jobTarget, jobDepot;
    private boolean delivered, demo, jobOk;
    private int trackLen = -1;
    private long trackAt = Long.MIN_VALUE / 2;

    private boolean unloading;

    private static final int GRIPPED = 5;
    private int beatSeen = -1;

    public OrdnanceLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(state.getBlock() == AirBlocks.HEAVY_LOADER.get() ? AirBlocks.HEAVY_LOADER_BE.get() : AirBlocks.ORDNANCE_LOADER_BE.get(), pos, state,
                state.getBlock() == AirBlocks.HEAVY_LOADER.get() ? Balance.HEAVY_LOADER_FE : Balance.LOADER_FE, Balance.LOADER_BUFFER, Balance.LOADER_RECEIVE);
        heavy = state.getBlock() == AirBlocks.HEAVY_LOADER.get();
        g = heavy ? HEAVY : SMALL;
    }

    public boolean heavy() { return heavy; }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) AmmoDepots.addLoader(sl, worldPosition);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) AmmoDepots.removeLoader(sl, worldPosition);
        super.setRemoved();
    }
    Geo geo() { return g; }

    public ItemStack carried() { return carried; }

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
        if (!carried.isEmpty() && !demo) { l.add(carried); carried = ItemStack.EMPTY; }
        if (jobTarget != null && jobDepot != null && level != null && level.getBlockEntity(jobDepot) instanceof AmmoDepotBlockEntity d) d.done(jobTarget);
        return l;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    int[] beats() {
        int travel = (int) Math.ceil(tRun / 16.0 / Balance.LOADER_TRACK_SPEED);
        int[] b = unloading ? new int[] { Balance.LD_HATCH, Balance.LD_TABLE, Balance.LD_CARRY + travel, Balance.LD_PUSH, Balance.LD_GRIP, Balance.LD_RELEASE,
                Balance.LD_RETURN + travel, Balance.LD_HOIST, Balance.LD_RELEASE, Balance.LD_LIFT, Balance.LD_SWING, Balance.LD_TABLE, Balance.LD_HATCH } : new int[] { Balance.LD_HATCH, Balance.LD_TABLE, Balance.LD_HOIST, Balance.LD_SWING, Balance.LD_GRIP, Balance.LD_LIFT, Balance.LD_CARRY + travel, Balance.LD_PUSH,
                Balance.LD_RELEASE, Balance.LD_RETURN + travel, Balance.LD_TABLE, Balance.LD_HATCH };
        if (heavy) for (int i = 0; i < b.length; i++) b[i] = Math.round(b[i] * Balance.LOADER_HEAVY_SLOW);
        return b;
    }

    int cycleTicks() { int n = 0; for (int x : beats()) n += x; return n; }

    public boolean busy() { return cycleAt >= 0; }

    float[] solve(double tx, double topY, double tz, double roundH, double run) {
        double yz = g.yawZ() - run;
        double dx = tx - g.c(), dz = tz - yz;
        double az = Math.toDegrees(Math.atan2(-dx, -dz));
        double h = Math.hypot(dx, dz) + (g.shZ() - g.yawZ());
        double boomY = topY - roundH - 0.2 - (g.sadTop() - g.shY());
        double v = boomY - (g.shY() + g.platUp());
        double L = Math.hypot(h, v), E = Math.toDegrees(Math.atan2(v, h));
        double ext = (L - g.l0()) / g.lAdd();

        if (ext < -0.02 || ext > 1.02 || E < Balance.LOADER_E_MIN || E > 75.0) return null;
        float[] s = { (float) az, (float) E, (float) Mth.clamp(ext, 0.0, 1.0) };

        double[] at = reachOf(s[0], s[1], s[2], roundH, run);
        if (Math.abs(at[0] - tx) > 3.0 || Math.abs(at[1] - topY) > 3.0 || Math.abs(at[2] - tz) > 3.0) return null;
        return s;
    }

    double[] reachOf(double az, double E, double ext, double roundH, double run) {
        double L = g.l0() + g.lAdd() * ext, e = Math.toRadians(E), a = Math.toRadians(az);
        double h = L * Math.cos(e) - (g.shZ() - g.yawZ());
        double boomY = g.shY() + g.platUp() + L * Math.sin(e);
        return new double[] { g.c() - Math.sin(a) * h, boomY + roundH + 0.2 + (g.sadTop() - g.shY()), g.yawZ() - run - Math.cos(a) * h };
    }

    Vec3 local(ServerLevel sl, BlockPos target) {
        Vec3 w = SableBridge.worldPos(sl, target);
        UUID mine = SableBridge.shipIdAt(sl, worldPosition);
        Vec3 p = mine == null ? w : SableBridge.toShip(sl, mine, w);
        if (p == null) p = w;
        double dx = p.x - (worldPosition.getX() + 0.5), dy = p.y - worldPosition.getY(), dz = p.z - (worldPosition.getZ() + 0.5);
        double lx, lz;
        switch (facing()) {
            case EAST -> { lx = dz; lz = -dx; }
            case SOUTH -> { lx = -dx; lz = -dz; }
            case WEST -> { lx = -dz; lz = dx; }
            default -> { lx = dx; lz = dz; }
        }
        return new Vec3(lx * 16.0, dy * 16.0, lz * 16.0);
    }

    int trackLength() {
        if (level == null) return 0;
        if (level.getGameTime() - trackAt < 20 && trackLen >= 0) return trackLen;
        trackAt = level.getGameTime();
        trackLen = scanTrack();
        return trackLen;
    }

    private int scanTrack() {
        int n = 0;
        while (n < Balance.LOADER_TRACK_MAX) {
            BlockPos a = Machines.toWorld(worldPosition, facing(), 0, 0, -1 - n), b = Machines.toWorld(worldPosition, facing(), g.n() - 1, 0, -1 - n);
            if (!(level.getBlockState(a).getBlock() instanceof LoaderTrackBlock) || !(level.getBlockState(b).getBlock() instanceof LoaderTrackBlock)) break;
            n++;
        }
        return n;
    }

    double runFor(Vec3 t) {
        double max = trackLength() * 16.0;
        return Mth.clamp(g.yawZ() - t.z, 0.0, max);
    }

    boolean canServe(ServerLevel sl, BlockPos target) {
        Vec3 t = local(sl, target);
        return solve(t.x, t.y, t.z, 6.0, runFor(t)) != null;
    }

    String reachWhy(ServerLevel sl, BlockPos target) {
        Vec3 t = local(sl, target);
        double run = runFor(t), yz = g.yawZ() - run;
        double dx = t.x - g.c(), dz = t.z - yz;
        double h = Math.hypot(dx, dz) + (g.shZ() - g.yawZ());
        double boomY = t.y - 6.0 - 0.2 - (g.sadTop() - g.shY());
        double v = boomY - (g.shY() + g.platUp());
        double L = Math.hypot(h, v), E = Math.toDegrees(Math.atan2(v, h)), ext = (L - g.l0()) / g.lAdd();
        return String.format(java.util.Locale.ROOT, "%s side %.1f along %.1f up %.1f blk (track %d, run %.1f) ext %.2f E %.0f", target.toShortString(),
                dx / 16.0, dz / 16.0, t.y / 16.0, trackLength(), run / 16.0, ext, E);
    }

    boolean parked(ServerLevel sl, UUID craft) {
        if (!AirGate.on() || AirPicture.isAircraft(sl, craft)) return false;

        return Crafts.parked(sl, craft, SableBridge.shipIdAt(sl, worldPosition), Balance.LOADER_PARKED);
    }

    String demo(Player player) {
        if (level == null || level.isClientSide) return null;
        String why = blockedWhy();
        if (why != null) return why;
        String mesh = AamItem.mesh(AirMissiles.RADAR);
        float h = mesh == null ? 5.6F : AamMeshes.hangPx(mesh);
        float[] s = solve(-DEMO_WEST, 16.0 + DEMO_UP, -DEMO_NORTH, h, 0.0);
        if (s == null) return "message.wmd.loader.reach";
        tAz = s[0]; tE = s[1]; tExt = s[2]; tLift = 4.0F; tRun = 0.0F;
        unloading = false;
        carried = new ItemStack(AirBlocks.AAM_RADAR.get());
        demo = true;
        jobShip = null; jobTarget = null; jobDepot = null; delivered = false;
        cycleAt = level.getGameTime();
        pausedAt = -1;
        beatSeen = -1;
        sync();
        return "message.wmd.loader.demo";
    }

    private String blockedWhy() {
        if (busy()) return "message.wmd.loader.busy";
        if (!AirGate.on()) return "message.wmd.loader.off";
        if (hatchBlocked()) return "message.wmd.loader.blocked";
        if (guiEnergy().getEnergyStored() < (heavy ? Balance.HEAVY_LOADER_FE : Balance.LOADER_FE) * 20) return "message.wmd.loader.no_power";
        return null;
    }

    boolean hatchBlocked() {
        if (level == null) return false;

        Vec3 a = SableBridge.worldPos(level, worldPosition), b = SableBridge.worldPos(level, Machines.toWorld(worldPosition, facing(), g.n() - 1, 0, g.d() - 1));
        AABB box = new AABB(Math.min(a.x, b.x) - 0.5, a.y + 0.4, Math.min(a.z, b.z) - 0.5, Math.max(a.x, b.x) + 0.5, a.y + 2.2, Math.max(a.z, b.z) + 0.5);
        return !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box.inflate(0.25)).isEmpty();
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl) || !AirGate.on()) return false;
        long now = sl.getGameTime();
        if (cycleAt < 0) {
            if (!carried.isEmpty() && !demo && (now + worldPosition.asLong()) % Balance.LOADER_POLL == 0) returnCarried(sl);
            if (carried.isEmpty() && (now + worldPosition.asLong()) % Balance.LOADER_POLL == 0) seekJob(sl);
            return false;
        }
        if (pausedAt >= 0) {
            if (guiEnergy().getEnergyStored() < feCost() * 20) return false;
            cycleAt += now - pausedAt;
            pausedAt = -1;
            sync();
        }
        int t = (int) (now - cycleAt);
        if (t < 0) return false;
        int total = cycleTicks();
        if (t >= total) { finish(sl); return false; }
        int[] b = beats();
        int beat = 0, acc = 0;
        while (beat < b.length && t >= acc + b[beat]) { acc += b[beat]; beat++; }
        if (beat != beatSeen) {
            beatSeen = beat;
            beatSound(unloading ? UNLOAD_SOUND[Math.min(beat, UNLOAD_SOUND.length - 1)] : beat);
            if (!unloading && beat >= RELEASED && !delivered) deliver(sl);
            if (unloading && beat >= GRIPPED && !delivered) fetch(sl);
        }
        return true;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level != null && !level.isClientSide && cycleAt >= 0 && pausedAt < 0) { pausedAt = level.getGameTime(); sync(); }
    }

    private void seekJob(ServerLevel sl) {
        if (blockedWhy() != null) return;
        AmmoDepotBlockEntity d = AmmoDepots.nearest(sl, SableBridge.worldPos(sl, worldPosition), getOwner());
        if (d == null || !d.ordered()) return;
        AmmoDepotBlockEntity.Job j = d.claim(sl, this);
        if (j == null) return;
        Vec3 t = local(sl, j.target());
        double run = runFor(t);
        float h = 6.0F;
        if (j.item().getItem() instanceof AamItem a) { String m = AamItem.mesh(a.kind()); if (m != null) h = AamMeshes.hangPx(m); }
        float[] s = solve(t.x, t.y, t.z, h, run);
        if (s == null) { if (!j.unload()) d.giveBack(j.item()); d.done(j.target(), false); return; }
        tAz = s[0]; tE = s[1]; tExt = s[2]; tLift = 4.0F; tRun = (float) run;
        Wmd.LOGGER.info("[loader] {} loader at {}: {} {} x{} -> {} az {} (+ = towards its west) E {} ext {}", heavy ? "heavy" : "light",
                worldPosition.toShortString(), j.unload() ? "unload" : "stock", j.item().isEmpty() ? "-" : j.item().getHoverName().getString(),
                j.item().getCount(), reachWhy(sl, j.target()), String.format(java.util.Locale.ROOT, "%.0f", s[0]), String.format(java.util.Locale.ROOT, "%.0f", s[1]),
                String.format(java.util.Locale.ROOT, "%.2f", s[2]));
        carried = j.item();
        unloading = j.unload();
        demo = false;
        jobShip = j.ship(); jobTarget = j.target(); jobDepot = d.getBlockPos();
        delivered = false; jobOk = false;
        cycleAt = sl.getGameTime() + j.supply();
        pausedAt = -1;
        beatSeen = -1;
        sync();
    }

    private static final int[] UNLOAD_SOUND = { 0, 1, 3, 5, 4, 5, 6, 2, 8, 9, 3, 10, 11 };

    private void fetch(ServerLevel sl) {
        delivered = true;
        if (jobTarget == null) return;
        if (jobShip == null || parked(sl, jobShip)) {
            StockTargets.Target tg = sl.getBlockEntity(jobTarget) == null ? null : StockTargets.target(sl.getBlockEntity(jobTarget));
            if (tg != null && tg.unloadable()) carried = tg.unload(sl);
        }
        jobOk = !carried.isEmpty();
        sync();
    }

    private void deliver(ServerLevel sl) {
        delivered = true;
        if (demo || carried.isEmpty() || jobTarget == null) return;
        boolean ok = false;
        if (jobShip == null || parked(sl, jobShip)) {
            StockTargets.Target tg = sl.getBlockEntity(jobTarget) == null ? null : StockTargets.target(sl.getBlockEntity(jobTarget));

            while (tg != null && !carried.isEmpty() && tg.load(sl, carried.copyWithCount(1))) { carried.shrink(1); ok = true; }
        }
        if (carried.isEmpty()) carried = ItemStack.EMPTY;
        jobOk = ok;
        sync();
    }

    private void finish(ServerLevel sl) {
        if (jobTarget != null && jobDepot != null && sl.getBlockEntity(jobDepot) instanceof AmmoDepotBlockEntity d) d.done(jobTarget, jobOk);
        if (demo) carried = ItemStack.EMPTY;
        else returnCarried(sl);
        cycleAt = -1;
        pausedAt = -1;
        jobShip = null; jobTarget = null; delivered = false; demo = false; unloading = false;
        sync();
    }

    private void returnCarried(ServerLevel sl) {
        if (carried.isEmpty()) return;
        AmmoDepotBlockEntity d = jobDepot != null && sl.getBlockEntity(jobDepot) instanceof AmmoDepotBlockEntity x ? x
                : AmmoDepots.nearest(sl, SableBridge.worldPos(sl, worldPosition), getOwner());
        if (d != null && d.giveBack(carried)) carried = ItemStack.EMPTY;
        jobDepot = null;
        setChanged();
    }

    private void beatSound(int beat) {
        float hv = heavy ? 0.8F : 1.0F;
        switch (beat) {
            case 0, 11 -> playOnce(level, WmdSounds.OL_HATCH.get(), Balance.SND_MECH, hv);
            case 1, 10 -> playOnce(level, WmdSounds.OL_TABLE.get(), Balance.SND_MECH, hv);
            case 2 -> playOnce(level, WmdSounds.OL_HOIST.get(), Balance.SND_CLICK, hv);
            case 4, 8 -> playOnce(level, WmdSounds.OL_LATCH.get(), Balance.SND_MECH, hv);
            case 7 -> playOnce(level, WmdSounds.OL_RAM.get(), Balance.SND_MECH, hv);
            default -> { }
        }
    }

    double[] pose(double tt) {
        double[] p = new double[12];
        if (cycleAt < 0) return p;
        double t = (pausedAt >= 0 ? Math.min(tt, pausedAt) : tt) - cycleAt;
        if (t < 0) return p;
        int[] b = beats();
        double[] st = new double[b.length + 1];
        for (int i = 0; i < b.length; i++) st[i + 1] = st[i] + b[i];

        double gAz = g.gAz(), gE = g.gE(), gExt = g.gExt(), gW = g.gWrist(), R = 180.0;
        double r = tRun;

        double[][] k = {
                { 0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 1, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 1, 1, 0, 0, 0, Double.NaN, 0, 0, 1, 0, 0 },
                { 1, 1, 1, gAz, gE, gExt, gW, 0, 0, 1, 0, R },
                { 1, 1, 1, gAz, gE, gExt, gW, 0, 1, 2, 0, R },
                { 1, 1, 0, gAz, gE + 8, gExt, gW, 0, 1, 2, 0, R },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, 0, 1, 2, r, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, tLift, 1, 2, r, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, tLift, 0, 3, r, 0 },
                { 1, 1, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 },
                { 1, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 },
                { 0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 } };
        if (unloading) k = new double[][] {
                { 0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 1, 0, 0, 0, 0, Double.NaN, 0, 0, 0, 0, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, 0, 0, 0, r, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, tLift, 0, 0, r, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, tLift, 1, 2, r, 0 },
                { 1, 1, 0, tAz, tE, tExt, Double.NaN, 0, 1, 2, r, 0 },
                { 1, 1, 0, gAz, gE + 8, gExt, gW, 0, 1, 2, 0, R },
                { 1, 1, 1, gAz, gE, gExt, gW, 0, 1, 2, 0, R },
                { 1, 1, 1, gAz, gE, gExt, gW, 0, 0, 1, 0, R },
                { 1, 1, 0, gAz, gE + 8, gExt, gW, 0, 0, 3, 0, R },
                { 1, 1, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 },
                { 1, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 },
                { 0, 0, 0, 0, 0, 0, Double.NaN, 0, 0, 3, 0, 0 } };
        int i = 0;
        while (i < b.length && t >= st[i + 1]) i++;
        if (i >= b.length) { System.arraycopy(k[b.length], 0, p, 0, 12); p[6] = 0; return p; }
        double f = b[i] <= 0 ? 1.0 : Mth.clamp((t - st[i]) / b[i], 0.0, 1.0);
        f = f * f * (3.0 - 2.0 * f);
        double[] a = k[i], z = k[i + 1];
        for (int j = 0; j < 12; j++) {
            if (j == 6) {
                double e = a[4] + (z[4] - a[4]) * f;
                double wa = Double.isNaN(a[6]) ? -a[4] : a[6], wz = Double.isNaN(z[6]) ? -z[4] : z[6];
                p[6] = (Double.isNaN(a[6]) && Double.isNaN(z[6])) ? -e : wa + (wz - wa) * f;
            } else if (j == 3) p[3] = a[3] + Mth.wrapDegrees(z[3] - a[3]) * f;
            else if (j == 9) p[9] = f < 0.5 ? a[9] : z[9];
            else p[j] = a[j] + (z[j] - a[j]) * f;
        }
        return p;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double[] p = pose(tt);
        final float hatch = (float) Math.toRadians(172.0 * p[0]);
        model.getBone("hatch_l").ifPresent(b -> b.setRotZ(RZ * -hatch));
        model.getBone("hatch_r").ifPresent(b -> b.setRotZ(RZ * hatch));
        final float table = (float) (g.platUp() * p[1]);
        model.getBone("platform").ifPresent(b -> b.setPosY(table));
        final float run = (float) -p[10];
        model.getBone("carriage").ifPresent(b -> b.setPosZ(run));
        final float hoist = (float) (g.hoistRise() * p[2]);
        for (String h : new String[] { "hoist1", "hoist2", "hoist3" }) model.getBone(h).ifPresent(b -> b.setPosY(hoist));
        final float az = (float) Math.toRadians(p[3]), el = (float) Math.toRadians(p[4]);
        model.getBone("yaw").ifPresent(b -> b.setRotY(RY * az));
        model.getBone("boom").ifPresent(b -> b.setRotX(RX * el));
        final float slide = (float) (-g.slideMax() * p[5]);
        for (int i = 1; i <= Geo.STAGES; i++) model.getBone("tele" + i).ifPresent(b -> b.setPosZ(slide));
        final float roll = (float) Math.toRadians(p[11]);
        model.getBone("roll").ifPresent(b -> b.setRotZ(RZ * roll));
        final float wr = (float) Math.toRadians(p[6]);
        model.getBone("wrist").ifPresent(b -> b.setRotX(RX * wr));
        final float lift = (float) p[7];
        model.getBone("lift").ifPresent(b -> b.setPosY(lift));
        final float jaw = (float) Math.toRadians(80.0 * p[8]);
        model.getBone("jaw_l").ifPresent(b -> b.setRotZ(RZ * -jaw));
        model.getBone("jaw_r").ifPresent(b -> b.setRotZ(RZ * jaw));
        double[] r = ram(p[4]);
        final float th = (float) Math.toRadians(r[0]), rs = (float) (r[1] - ram(0.0)[1]);
        for (String c : new String[] { "ram_cyl", "ram_cyl_l" }) model.getBone(c).ifPresent(b -> b.setRotX(RX * th));
        for (String c : new String[] { "ram_rod", "ram_rod_l" }) model.getBone(c).ifPresent(b -> b.setPosY(rs));
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean on = !dark && busy();
        model.getBone("lamp").ifPresent(b -> b.setHidden(!on));
    }

    double[] ram(double E) {
        double t = Math.toRadians(E);
        double dy = g.lugY() - g.shY(), dz = g.lugZ() - g.shZ();
        double ly = g.shY() + dy * Math.cos(t) - dz * Math.sin(t), lz = g.shZ() + dy * Math.sin(t) + dz * Math.cos(t);
        double vy = ly - g.footY(), vz = lz - g.footZ();
        return new double[] { Math.toDegrees(Math.atan2(vz, vy)), Math.hypot(vy, vz) };
    }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, g.n() - 1, 0, g.d() - 1, Direction.EAST) ? energy() : null; }
    @Override public PortMap guiPortMap() { return new PortMap(0, g.n() - 1, g.d()).power(g.n() - 1, 0, g.d() - 1, Direction.EAST, false); }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override protected SoundEvent runSound() { return WmdSounds.OL_HYD_LOOP.get(); }
    @Override protected float soundVolume() { return heavy ? Balance.SND_HEAVY : Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 0, 0); }
    @Override protected String detail() { return (heavy ? "heavy" : "small") + (busy() ? ", cycle running" : ", idle") + ", track " + trackLength(); }

    @Override
    public Component status() {
        if (getOwner() == null) return Component.translatable("message.wmd.loader.unowned");
        String reach = String.format("%.1f", heavy ? Balance.HEAVY_LOADER_REACH : Balance.LOADER_REACH);
        if (busy()) return Component.translatable("message.wmd.loader.status_busy", reach);
        boolean depot = level instanceof ServerLevel sl && AmmoDepots.nearest(sl, SableBridge.worldPos(sl, worldPosition), getOwner()) != null;
        return Component.translatable(depot ? "message.wmd.loader.status_idle" : "message.wmd.loader.status_nodepot", reach, trackLength());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        copycat.save(tag, registries);
        writeCycle(tag, registries);
        if (jobDepot != null) tag.putLong("olDepot", jobDepot.asLong());
        tag.putBoolean("olDemo", demo);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        copycat.load(tag, registries, level != null && level.isClientSide);
        if (tag.contains("olAt")) {
            cycleAt = tag.getLong("olAt");
            pausedAt = tag.contains("olPause") ? tag.getLong("olPause") : -1;
            tAz = tag.getFloat("olAz"); tE = tag.getFloat("olE"); tExt = tag.getFloat("olExt"); tLift = tag.getFloat("olLift"); tRun = tag.getFloat("olRun");
            carried = tag.contains("olItem") ? ItemStack.parseOptional(registries, tag.getCompound("olItem")) : ItemStack.EMPTY;
            unloading = tag.getBoolean("olUnl");
        }
        if (tag.contains("olDepot")) jobDepot = BlockPos.of(tag.getLong("olDepot"));
        if (tag.contains("olDemo")) demo = tag.getBoolean("olDemo");
        if (tag.contains("energy") && cycleAt >= 0) { cycleAt = -1; pausedAt = -1; }
        if (tag.contains("energy") && demo) { demo = false; carried = ItemStack.EMPTY; }
    }

    private void writeCycle(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("olAt", cycleAt);
        tag.putLong("olPause", pausedAt);
        tag.putFloat("olAz", tAz); tag.putFloat("olE", tE); tag.putFloat("olExt", tExt); tag.putFloat("olLift", tLift); tag.putFloat("olRun", tRun);
        tag.putBoolean("olUnl", unloading);
        if (!carried.isEmpty()) tag.put("olItem", carried.save(registries));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        copycat.putSync(tag);
        writeCycle(tag, registries);
        return tag;
    }
}
