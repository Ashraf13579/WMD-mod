package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;

public class CollectorRigBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 4, Z0 = -2, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).item(2, 0, 1, Direction.EAST, true).power(-2, 0, 2, Direction.SOUTH, false);
    public static final int A_LAUNCH = 0, A_RECALL = 1;
    public static final int C_DOCKED = 0, C_DESCEND = 1, C_SCAN = 2, C_DRIVE = 3, C_COLLECT = 4, C_UNLOAD = 5, C_RETURN = 6, C_ASCEND = 7;
    public static final int S_READY = 0, S_NO_CRAWLER = 1, S_WORKING = 2, S_NO_WATER = 3, S_TOO_DEEP = 4, S_DONE = 5, S_FULL = 6, S_RETURNING = 7;

    static final double CHUTE_Y = 12.0, CHUTE_Z = -39.0, RISER_OUT_Z = -44.0, CRAWLER_OUT = 2.5;

    static final double OUTFALL_X = -10.0, OUTFALL_Y = 28.0, OUTFALL_Z = -40.0, SPIGOT_X = 2.0, SPIGOT_Y = 20.0, SPIGOT_Z = 14.0, FLAP_X = 38.0, FLAP_Y = 13.0, FLAP_Z = 16.0;

    static final float LW_TRAVEL = 4.0F, GATE_OPEN = 0.6F, GATE_SIGN = -1.0F;

    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return false; }

    private final Slot crawler = new Slot(1, false, s -> s.is(Wmd.SEABED_CRAWLER.get()));
    private final Slot hopper = new Slot(Balance.RIG_HOPPER, false, s -> s.is(Wmd.MANGANESE_NODULE.get()));
    private final Slot mn = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot nico = new Slot(64, true, s -> false).chute(2, 0, 1, Direction.EAST);
    private final Slot[] slots = { crawler, hopper, mn, nico };
    private final List<FluidTank> gauges = List.of(crawler.gauge, hopper.gauge, mn.gauge, nico.gauge);
    private final IItemHandler outH = outHandler(mn, nico);
    private final OwnerData ownerData = new OwnerData();

    private int sepProgress, status = S_READY, cState = C_DOCKED, cTicks, pending, collected, scanX, pathIdx, targetsLeft;
    private boolean recall;
    private long stateStart;
    private ItemStack deployed = ItemStack.EMPTY;
    private Vec3 cPos;
    private float cYaw, head;
    private BlockPos foot, start;
    private BlockPos target;
    private final List<BlockPos> targets = new ArrayList<>();
    private List<BlockPos> path;
    private UUID crawlerId;

    private int beats, sepBeats;
    private int syncedHop = -1;
    private boolean syncedPow;

    public CollectorRigBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.COLLECTOR_RIG_BE.get(), pos, state, Balance.RIG_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) { ownerData.claim(player); setChanged(); sync(); }

    public int rigStatus() { return status; }
    public int crawlerState() { return cState; }
    public int targetsLeft() { return targetsLeft; }
    public int collected() { return collected; }

    final NoduleSurvey.Hint surveyHint = new NoduleSurvey.Hint();

    public NoduleSurvey.Hint surveyHint() { return surveyHint; }
    public boolean hasCrawler() { return !crawler.stack.isEmpty() || !deployed.isEmpty(); }

    public void act(Player by, int action, int value) {
        switch (action) {
            case A_LAUNCH -> { if (cState == C_DOCKED && level instanceof ServerLevel sl) launch(sl); }
            case A_RECALL -> { if (cState != C_DOCKED) { recall = true; setStatus(S_RETURNING); } }
            default -> {}
        }
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private void syncLook(boolean pow, boolean force) {
        if (!force && pow == syncedPow && hopper.count() == syncedHop) return;
        syncedPow = pow;
        syncedHop = hopper.count();
        sync();
    }

    private void setStatus(int s) { if (status != s) { status = s; setChanged(); sync(); } }

    private void setState(int s) {
        cState = s;
        cTicks = 0;
        stateStart = level != null ? level.getGameTime() : 0L;
        setChanged();
        sync();
    }

    private Vec3 local(double lx, double ly, double lz) { double[] w = worldPoint(lx, ly, lz); return new Vec3(w[0], w[1], w[2]); }

    private Vec3 chute() { return local(0.0, CHUTE_Y / 16.0, CHUTE_Z / 16.0); }

    private Vec3 outward() {
        Vec3 a = local(0.0, 0.0, 0.0), b = local(0.0, 0.0, -1.0);
        Vec3 d = new Vec3(b.x - a.x, 0.0, b.z - a.z);
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, -1) : d.normalize();
    }

    private void launch(ServerLevel sl) {
        if (crawler.stack.isEmpty()) { setStatus(S_NO_CRAWLER); return; }
        Vec3 riser = local(0.0, 0.0, RISER_OUT_Z / 16.0), drop = riser.add(outward().scale(CRAWLER_OUT));
        int top = Mth.floor(riser.y) - 1;
        NoduleMining.Seabed a = NoduleMining.seabed(sl, Mth.floor(riser.x), top, Mth.floor(riser.z), Balance.RIG_RISER);
        NoduleMining.Seabed b = NoduleMining.seabed(sl, Mth.floor(drop.x), top, Mth.floor(drop.z), Balance.RIG_RISER);
        if (a == null || b == null) { setStatus(S_TOO_DEEP); return; }
        if (!a.wet() || !b.wet()) { setStatus(S_NO_WATER); return; }
        foot = new BlockPos(Mth.floor(riser.x), a.floorY(), Mth.floor(riser.z));
        start = new BlockPos(Mth.floor(drop.x), b.floorY(), Mth.floor(drop.z));
        deployed = crawler.stack.copy();
        crawler.take(1);
        cPos = new Vec3(start.getX() + 0.5, riser.y - 1.0, start.getZ() + 0.5);
        Vec3 o = outward();
        cYaw = (float) Math.toDegrees(Math.atan2(-o.x, o.z));
        head = 0.0F;
        targets.clear();
        target = null;
        path = null;
        recall = false;
        collected = 0;
        pending = 0;
        targetsLeft = 0;
        setStatus(S_WORKING);
        setState(C_DESCEND);
        sl.playSound(null, cPos.x, cPos.y, cPos.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.4F, 0.7F);
        playOnce(sl, WmdSounds.T4_WINCH.get(), 1.0F, 0.8F);
    }

    @Override
    protected boolean process() {
        if (!(level instanceof ServerLevel sl)) return false;
        if (surveyHint.refresh(sl, getOwner(), worldPosition)) sync();
        syncLook(true, false);
        boolean busy = separate();
        if (cState != C_DOCKED) { crawlerTick(sl); busy = true; }
        return busy;
    }

    @Override
    protected void idle() {
        super.idle();
        if (level instanceof ServerLevel hs && surveyHint.refresh(hs, getOwner(), worldPosition)) sync();
        syncLook(false, false);
        if (cState != C_DOCKED && level instanceof ServerLevel sl) feed(sl, false);
    }

    private boolean separate() {
        if (hopper.count() < Balance.RIG_SEP_IN || !mn.fits(Wmd.MANGANESE_CONCENTRATE.get(), Balance.RIG_MN_OUT)
                || !nico.fits(Wmd.NICKEL_COBALT_CONCENTRATE.get(), Balance.RIG_NICO_OUT)) { sepProgress = 0; return false; }
        if (++sepProgress >= Balance.RIG_SEP_TICKS) {
            sepProgress = 0;
            hopper.take(Balance.RIG_SEP_IN);
            mn.add(Wmd.MANGANESE_CONCENTRATE.get(), Balance.RIG_MN_OUT);
            nico.add(Wmd.NICKEL_COBALT_CONCENTRATE.get(), Balance.RIG_NICO_OUT);
            setChanged();
            sepBeats++;
            syncLook(true, true);
        }
        return true;
    }

    private void crawlerTick(ServerLevel sl) {
        cTicks++;
        if (cPos == null || foot == null || start == null) { dock(sl); return; }
        switch (cState) {
            case C_DESCEND -> {
                double floorTop = start.getY() + 1.0;
                if (recall) { setState(C_ASCEND); break; }
                cPos = new Vec3(cPos.x, Math.max(floorTop, cPos.y - Balance.CRAWLER_SINK), cPos.z);
                if (cPos.y <= floorTop + 1.0E-6) { scanX = -Balance.CRAWLER_RADIUS; setState(C_SCAN); }
            }
            case C_SCAN -> {
                if (recall) { setState(C_RETURN); path = null; break; }
                int r = Balance.CRAWLER_RADIUS;
                for (int k = 0; k < Balance.CRAWLER_SCAN_ROWS && scanX <= r; k++, scanX++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (scanX * scanX + dz * dz > r * r) continue;
                        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                        for (int y = foot.getY() + 8; y >= foot.getY() - 8; y--) {
                            p.set(foot.getX() + scanX, y, foot.getZ() + dz);
                            if (NoduleMining.openNodule(sl, p)) { targets.add(p.immutable()); break; }
                        }
                    }
                if (scanX > r) { targetsLeft = targets.size(); nextTarget(sl); }
            }
            case C_DRIVE -> {
                if (recall) { setState(C_RETURN); path = null; break; }
                if (target == null || !NoduleMining.openNodule(sl, target)) { if (target != null) targets.remove(target); nextTarget(sl); break; }
                if (drive(sl, target)) { head = 0.0F; setState(C_COLLECT); }
            }
            case C_COLLECT -> {
                head = Mth.clamp(cTicks / 10.0F, 0.0F, 1.0F);
                if (cTicks >= Balance.CRAWLER_COLLECT_TICKS) {
                    int n = target != null ? NoduleMining.collect(sl, target, Balance.CRAWLER_YIELD_MIN, Balance.CRAWLER_YIELD_MAX) : 0;
                    if (target != null) targets.remove(target);
                    target = null;
                    pending += n;
                    collected += n;
                    targetsLeft = targets.size();
                    setState(C_UNLOAD);
                }
            }
            case C_UNLOAD -> {
                int room = Math.min(pending, Math.max(0, Math.min(Balance.RIG_HOPPER, Wmd.MANGANESE_NODULE.get().getDefaultMaxStackSize()) - hopper.count()));
                if (room > 0) {
                    hopper.add(Wmd.MANGANESE_NODULE.get(), room); pending -= room; setChanged();
                    beats++;
                    playOnce(sl, WmdSounds.BREACHER_RATTLE.get(), Balance.SND_MECH, 0.85F + sl.random.nextFloat() * 0.2F);
                    syncLook(true, true);
                }
                if (pending > 0) { setStatus(S_FULL); break; }
                head = 0.0F;
                if (status == S_FULL) setStatus(recall ? S_RETURNING : S_WORKING);
                if (recall) { setState(C_RETURN); path = null; } else nextTarget(sl);
            }
            case C_RETURN -> {
                if (drive(sl, start)) setState(C_ASCEND);
            }
            case C_ASCEND -> {
                head = 0.0F;
                double deck = worldPoint(0.0, 0.0, 0.0)[1];
                cPos = new Vec3(cPos.x, cPos.y + Balance.CRAWLER_SINK, cPos.z);
                if (cPos.y >= deck - 1.0) { dock(sl); return; }
            }
            default -> {}
        }
        feed(sl, cState == C_DRIVE || cState == C_RETURN);
    }

    private void nextTarget(ServerLevel sl) {
        targets.removeIf(p -> !NoduleMining.openNodule(sl, p));
        targetsLeft = targets.size();
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : targets) {
            double d = cPos.distanceToSqr(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
            if (d < bd) { bd = d; best = p; }
        }
        target = best;
        path = null;
        if (best == null) { setStatus(recall ? S_RETURNING : S_DONE); setState(C_RETURN); return; }
        setState(C_DRIVE);
    }

    private boolean drive(ServerLevel sl, BlockPos goal) {
        int cx = Mth.floor(cPos.x), cz = Mth.floor(cPos.z);
        if (cx == goal.getX() && cz == goal.getZ() && Math.abs(cPos.y - (goal.getY() + 1.0)) < 0.05) return true;
        if (path == null) {
            int fy = SeabedPath.floor(sl, cx, cz, Mth.floor(cPos.y) - 1);
            BlockPos from = new BlockPos(cx, fy == Integer.MIN_VALUE ? Mth.floor(cPos.y) - 1 : fy, cz);
            path = SeabedPath.find(sl, from, goal.getX(), goal.getZ(), foot, Balance.CRAWLER_RADIUS + 3);
            pathIdx = 0;
            if (path == null) {
                if (goal.equals(start)) { path = List.of(goal); }
                else { targets.remove(goal); target = null; nextTarget(sl); return false; }
            }
        }
        if (pathIdx >= path.size()) { cPos = new Vec3(goal.getX() + 0.5, goal.getY() + 1.0, goal.getZ() + 0.5); return true; }
        BlockPos w = path.get(pathIdx);
        Vec3 wp = new Vec3(w.getX() + 0.5, w.getY() + 1.0, w.getZ() + 0.5);
        Vec3 d = wp.subtract(cPos);
        double hd = Math.hypot(d.x, d.z);
        if (hd > 1.0E-3) {
            float want = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            cYaw += Mth.clamp(Mth.wrapDegrees(want - cYaw), -12.0F, 12.0F);
        }
        double step = Balance.CRAWLER_SPEED;
        if (hd <= step) { cPos = wp; pathIdx++; }
        else cPos = new Vec3(cPos.x + d.x / hd * step, cPos.y + d.y * Math.min(1.0, step / hd), cPos.z + d.z / hd * step);
        return false;
    }

    private void dock(ServerLevel sl) {
        removeCrawler(sl);
        if (!deployed.isEmpty()) { if (crawler.stack.isEmpty()) crawler.stack = deployed.copy(); crawler.sync(); deployed = ItemStack.EMPTY; }
        if (pending > 0) { int room = Math.max(0, Balance.RIG_HOPPER - hopper.count()); hopper.add(Wmd.MANGANESE_NODULE.get(), Math.min(room, pending)); pending = 0; }
        cPos = null;
        target = null;
        path = null;
        targets.clear();
        targetsLeft = 0;
        if (status == S_WORKING || status == S_RETURNING || status == S_FULL) status = S_READY;
        recall = false;
        setState(C_DOCKED);
        sl.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 1.0F, 1.1F);
    }

    private void feed(ServerLevel sl, boolean moving) {
        if (cPos == null || foot == null) return;
        SeabedCrawlerEntity e = crawlerEntity(sl);
        if (e != null) e.follow(cPos, cYaw, chute(), SeabedCrawlerEntity.top(foot), head, moving);
    }

    private SeabedCrawlerEntity crawlerEntity(ServerLevel sl) {
        Entity e = crawlerId != null ? sl.getEntity(crawlerId) : null;
        if (e instanceof SeabedCrawlerEntity c && c.isAlive()) return c;
        if (!sl.isLoaded(BlockPos.containing(cPos))) return null;
        SeabedCrawlerEntity c = new SeabedCrawlerEntity(Wmd.SEABED_CRAWLER_ENTITY.get(), sl);
        c.setPos(cPos.x, cPos.y, cPos.z);
        c.setYRot(cYaw);
        sl.addFreshEntity(c);
        crawlerId = c.getUUID();
        return c;
    }

    private void removeCrawler(ServerLevel sl) {
        Entity e = crawlerId != null ? sl.getEntity(crawlerId) : null;
        if (e != null) e.discard();
        crawlerId = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) removeCrawler(sl);
        super.setRemoved();
    }

    @Override
    public List<ItemStack> takeAll() {
        List<ItemStack> l = new ArrayList<>(super.takeAll());
        if (!deployed.isEmpty()) { l.add(deployed.copy()); deployed = ItemStack.EMPTY; }
        return l;
    }

    private double lastTt = -1.0;
    private float reelSpd, reelAng, lwPhase, pumpSpd, coupAng, cycSpd, swirlAng, fillShown, gateOpen, kick;
    private int beatsSeen = -1, sepSeen = -1, clientHop;
    private boolean clientPow, burstBeat, burstSep;
    private double seaY = Double.NaN;
    private long seaAt = -1000L;

    private static float ease(float v, float target, float k, double dt) { return v + (target - v) * (float) (1.0 - Math.exp(-k * dt)); }

    private double seaSurface(Level lv, double[] o) {
        if (lv.getGameTime() - seaAt < 40L) return seaY;
        seaAt = lv.getGameTime();
        seaY = Double.NaN;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < 24; dy++) {
            p.set(Mth.floor(o[0]), Mth.floor(o[1]) - dy, Mth.floor(o[2]));
            FluidState fs = lv.getFluidState(p);
            if (fs.is(FluidTags.WATER)) { seaY = p.getY() + fs.getHeight(lv, p); break; }
            if (!lv.getBlockState(p).getCollisionShape(lv, p).isEmpty()) break;
        }
        return seaY;
    }

    private boolean isFault() { return status == S_NO_WATER || status == S_TOO_DEEP || status == S_NO_CRAWLER; }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        final double dt = lastTt < 0.0 ? 0.0 : Mth.clamp(tt - lastTt, 0.0, 5.0);
        lastTt = tt;
        final boolean on = isRunning();

        final float rT = !on ? 0.0F : cState == C_DESCEND ? 1.0F : cState == C_ASCEND ? -1.0F
                : (cState == C_DRIVE || cState == C_RETURN) ? 0.12F * (float) Math.sin(tt * 0.05) : 0.0F;
        reelSpd = ease(reelSpd, rT, 0.08F, dt);
        reelAng += reelSpd * 0.12F * (float) dt;
        lwPhase += reelSpd * 0.12F * (float) dt / (3.0F * Mth.TWO_PI);
        final float tri = 1.0F - 4.0F * Math.abs((lwPhase - (float) Math.floor(lwPhase)) - 0.5F);

        pumpSpd = ease(pumpSpd, on ? 1.0F : 0.0F, 0.04F, dt);
        cycSpd = ease(cycSpd, on ? 1.0F : 0.0F, 0.03F, dt);
        kick *= (float) Math.exp(-0.06 * dt);
        coupAng = (coupAng + pumpSpd * 0.7F * (float) dt) % (Mth.TWO_PI * 64.0F);
        swirlAng = (swirlAng + (cycSpd + kick * 1.2F) * 0.3F * (float) dt) % (Mth.TWO_PI * 64.0F);
        reelAng %= Mth.TWO_PI * 64.0F;
        gateOpen = Math.max(0.0F, gateOpen - 0.05F * (float) dt);
        final float fillT = Mth.clamp(clientHop / (float) Math.min(Balance.RIG_HOPPER, 64), 0.0F, 1.0F);
        fillShown = ease(fillShown, fillT, 0.08F, dt);
        final float v = Mth.clamp(0.1F + 0.55F * pumpSpd + 0.06F * pumpSpd * (float) Math.sin(tt * 0.8) + 0.3F * kick, 0.0F, 1.0F);
        final boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        final boolean warn = status == S_FULL || status == S_DONE, fault = isFault();
        final float k = kick, ps = pumpSpd, fs = fillShown;
        model.getBone("reel").ifPresent(b -> b.setRotX(reelAng));
        model.getBone("spool").ifPresent(b -> b.setPosX(LW_TRAVEL * tri));
        model.getBone("coupling").ifPresent(b -> b.setRotY(coupAng));
        model.getBone("motor").ifPresent(b -> {
            b.setPosX(0.3F * k * (float) Math.sin(tt * 2.7) + 0.04F * ps * (float) Math.sin(tt * 3.3));
            b.setPosY(0.05F * ps * (float) Math.sin(tt * 2.1));
            b.setPosZ(0.3F * k * (float) Math.cos(tt * 2.3));
        });
        model.getBone("glow").ifPresent(b -> b.setHidden(dark || ps < 0.3F));
        model.getBone("needle").ifPresent(b -> b.setRotZ((float) Math.toRadians(-135.0 + 270.0 * v)));
        model.getBone("swirl").ifPresent(b -> b.setRotY(swirlAng));
        model.getBone("heap").ifPresent(b -> { b.setHidden(fs < 0.03F); b.setScaleY(Math.max(0.08F, fs)); });
        model.getBone("gate").ifPresent(b -> b.setRotZ(GATE_SIGN * GATE_OPEN * Math.min(1.0F, gateOpen * 3.0F)));
        model.getBone("standby").ifPresent(b -> b.setHidden(dark || !clientPow));
        model.getBone("lgreen").ifPresent(b -> b.setHidden(dark || !on || warn || fault));
        model.getBone("lamber").ifPresent(b -> b.setHidden(dark || !clientPow || !warn));
        model.getBone("lred").ifPresent(b -> b.setHidden(dark || !clientPow || !fault || ((long) tt) % 20 >= 10));
        if ((burstBeat || burstSep) && level != null) bursts(level);
    }

    private void bursts(Level lv) {
        var r = lv.random;
        if (burstBeat) {
            burstBeat = false;
            double[] s = worldPoint(SPIGOT_X / 16.0, SPIGOT_Y / 16.0, SPIGOT_Z / 16.0);
            ItemParticleOption nod = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Wmd.MANGANESE_NODULE.get()));
            for (int i = 0; i < 12; i++)
                lv.addParticle(nod, s[0] + (r.nextDouble() - 0.5) * 0.15, s[1], s[2] + (r.nextDouble() - 0.5) * 0.15, (r.nextDouble() - 0.5) * 0.12, -0.05, (r.nextDouble() - 0.5) * 0.12);
            mud(lv, 18, true);
        }
        if (burstSep) {
            burstSep = false;
            double[] f = worldPoint(FLAP_X / 16.0, FLAP_Y / 16.0, FLAP_Z / 16.0);
            ItemParticleOption con = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Wmd.MANGANESE_CONCENTRATE.get()));
            for (int i = 0; i < 4; i++)
                lv.addParticle(con, f[0] + (r.nextDouble() - 0.5) * 0.1, f[1], f[2] + (r.nextDouble() - 0.5) * 0.1, (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
        }
    }

    private void mud(Level lv, int n, boolean gush) {
        var r = lv.random;
        double[] o = worldPoint(OUTFALL_X / 16.0, OUTFALL_Y / 16.0, OUTFALL_Z / 16.0);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.MUD.defaultBlockState());
        for (int i = 0; i < n; i++) lv.addParticle(dust, o[0] + (r.nextDouble() - 0.5) * 0.2, o[1], o[2] + (r.nextDouble() - 0.5) * 0.2, 0.0, 0.0, 0.0);
        double sy = seaSurface(lv, o);
        if (Double.isNaN(sy)) return;
        BlockParticleOption silt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState());
        for (int i = 0; i < (gush ? 8 : 1); i++) {
            lv.addParticle(ParticleTypes.SPLASH, o[0] + (r.nextDouble() - 0.5) * 0.5, sy + 0.05, o[2] + (r.nextDouble() - 0.5) * 0.5, 0.0, 0.1, 0.0);
            lv.addParticle(silt, o[0] + (r.nextDouble() - 0.5) * 0.8, sy - 0.3 - r.nextDouble() * 0.5, o[2] + (r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.06, -0.02, (r.nextDouble() - 0.5) * 0.06);
        }
    }

    @Override
    protected void clientEffects(Level lv) {
        if (cycSpd > 0.3F && lv.random.nextFloat() < 0.6F * cycSpd) mud(lv, 1, false);
    }

    @Override
    protected float soundPitch() { return 0.92F + 0.08F * pumpSpd + 0.12F * kick; }

    @Override protected Slot[] slots() { return slots; }
    @Override protected List<FluidTank> tanks() { return gauges; }
    @Override protected List<Fluid> tankFluids() { List<Fluid> l = new ArrayList<>(); for (int i = 0; i < slots.length; i++) l.add(Fluids.EMPTY); return l; }
    @Override protected int inputTanks() { return 2; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return sepProgress > 0 ? sepProgress / (float) Balance.RIG_SEP_TICKS : -1.0F; }
    @Override protected List<String> labelKeys() { return List.of("crawler", "nodules", "mn_conc", "nico_conc"); }
    @Override protected String[] iconIds() { return new String[] { "seabed_crawler", "manganese_nodule", "manganese_concentrate", "nickel_cobalt_concentrate" }; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected SoundEvent runSound() { return WmdSounds.T4_FUEL_LOOP.get(); }
    @Override protected float soundVolume() { return Balance.SND_MACHINE; }
    @Override protected BlockPos soundLocal() { return new BlockPos(-1, 1, 0); }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, -2, 0, 2, Direction.SOUTH) ? energy() : null; }

    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 2, 0, 1, Direction.EAST) ? outH : null; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", guiFeUse() * 20), Component.translatable("gui.wmd.info.collector_rig"));
    }

    @Override
    protected String detail() { return hopper.count() + " nodules, crawler state " + cState + ", status " + status + ", " + targetsLeft + " targets, " + collected + " collected"; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        ownerData.write(tag);
        tag.putInt("crSep", sepProgress); tag.putInt("crStatus", status); tag.putInt("crState", cState); tag.putInt("crTicks", cTicks);
        tag.putLong("crStart", stateStart); tag.putInt("crPending", pending); tag.putInt("crCollected", collected); tag.putInt("crScan", scanX);
        tag.putInt("crLeft", targetsLeft); tag.putBoolean("crRecall", recall); tag.putFloat("crYaw", cYaw); tag.putFloat("crHead", head);
        if (!deployed.isEmpty() && level != null) tag.put("crDeployed", deployed.save(level.registryAccess()));
        if (cPos != null) { tag.putDouble("crX", cPos.x); tag.putDouble("crY", cPos.y); tag.putDouble("crZ", cPos.z); }
        if (foot != null) tag.putLong("crFoot", foot.asLong());
        if (start != null) tag.putLong("crStartPos", start.asLong());
        if (target != null) tag.putLong("crTarget", target.asLong());
        long[] t = new long[targets.size()];
        for (int i = 0; i < t.length; i++) t[i] = targets.get(i).asLong();
        tag.put("crTargets", new LongArrayTag(t));
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        ownerData.read(tag);
        sepProgress = tag.getInt("crSep"); status = tag.getInt("crStatus"); cState = tag.getInt("crState"); cTicks = tag.getInt("crTicks");
        stateStart = tag.getLong("crStart"); pending = tag.getInt("crPending"); collected = tag.getInt("crCollected"); scanX = tag.getInt("crScan");
        targetsLeft = tag.getInt("crLeft"); recall = tag.getBoolean("crRecall"); cYaw = tag.getFloat("crYaw"); head = tag.getFloat("crHead");
        deployed = tag.contains("crDeployed") && level != null ? ItemStack.parseOptional(level.registryAccess(), tag.getCompound("crDeployed"))
                : tag.contains("crDeployed") ? new ItemStack(Wmd.SEABED_CRAWLER.get()) : ItemStack.EMPTY;
        cPos = tag.contains("crX") ? new Vec3(tag.getDouble("crX"), tag.getDouble("crY"), tag.getDouble("crZ")) : null;
        foot = tag.contains("crFoot") ? BlockPos.of(tag.getLong("crFoot")) : null;
        start = tag.contains("crStartPos") ? BlockPos.of(tag.getLong("crStartPos")) : null;
        target = tag.contains("crTarget") ? BlockPos.of(tag.getLong("crTarget")) : null;
        targets.clear();
        if (tag.contains("crTargets", Tag.TAG_LONG_ARRAY)) for (long l : tag.getLongArray("crTargets")) targets.add(BlockPos.of(l));
        path = null;
        if (tag.contains("crBeat") && level != null && level.isClientSide) {
            int b = tag.getInt("crBeat"), sn = tag.getInt("crSepN");
            if (beatsSeen >= 0 && b != beatsSeen) { kick = 1.0F; burstBeat = true; }
            if (sepSeen >= 0 && sn != sepSeen) { gateOpen = 1.0F; burstSep = true; }
            beatsSeen = b;
            sepSeen = sn;
            clientHop = tag.getInt("crHop");
            clientPow = tag.getBoolean("crPow");
        }
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        tag.putInt("crStatus", status); tag.putInt("crState", cState); tag.putLong("crStart", stateStart);
        tag.putInt("crLeft", targetsLeft); tag.putInt("crCollected", collected);
        if (!deployed.isEmpty()) tag.putBoolean("crOut", true);
        tag.putInt("crBeat", beats); tag.putInt("crSepN", sepBeats); tag.putInt("crHop", hopper.count()); tag.putBoolean("crPow", syncedPow);
        surveyHint.write(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        status = tag.getInt("crStatus"); cState = tag.getInt("crState"); stateStart = tag.getLong("crStart");
        targetsLeft = tag.getInt("crLeft"); collected = tag.getInt("crCollected");
        clientOut = tag.getBoolean("crOut");
        surveyHint.read(tag);
    }

    private boolean clientOut;

    public boolean crawlerOut() { return level != null && level.isClientSide ? clientOut : !deployed.isEmpty(); }
}
