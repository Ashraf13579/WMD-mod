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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

public class RailgunBlockEntity extends ItemProcessBlockEntity implements Owned, CcAnimated, ScreenCommandTarget {

    public static final int X0 = -7, X1 = 7, Y0 = 0, Y1 = 9, Z0 = 0, Z1 = 14;
    public static final int IDLE = 0, SLEW = 1, LOAD = 2, CHARGE = 3, AFTER = 4, ABORT = 5;

    public static final int R_NONE = 0, R_NO_TARGET = 1, R_NO_SLUG = 2, R_ARC = 3, R_BLOCKED = 4, R_EMP = 5, R_HIT = 6, R_STOPPED = 7, R_BUSY = 8;

    public static final int CMD_TARGET = 0, CMD_FIRE = 1, CMD_STOP = 2;

    static final float AZ_SIGN = 1.0F, EL_SIGN = 1.0F;

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override public void claim(Player player) { ownerData.claim(player); setChanged(); }

    private final Slot slug = new Slot(4, false, s -> s.is(Wmd.RAILGUN_SLUG.get()));
    private final Slot[] slots = { slug };
    private final List<FluidTank> tanks = List.of(slug.gauge);
    private final IItemHandler slugIn = inHandler(slug);

    private int state = IDLE, stateTicks, chargeTicks, why;
    private boolean hasTarget, loaded;
    private int tx, tz;
    private boolean finalWarned;
    private float aimYaw, aimPitch, fromYaw, fromPitch;
    private boolean cutsceneSent;
    private int lastSync = -1, lastSlugs = -1, prevCharge = -1;
    private int clientSlugs;

    public RailgunBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.RAILGUN_BE.get(), pos, state, Balance.RG_FE_PER_TICK);
    }

    private double[] solve(int x, int z) {
        double[] c = worldPoint(0.0, 8.0, 7.0);
        double dx = x + 0.5 - c[0], dz = z + 0.5 - c[2];
        double rel = LauncherGeometry.wrap(LauncherGeometry.azimuthFor(facing(), dx, dz) + 180.0);
        double dist = Math.sqrt(dx * dx + dz * dz);
        double el = Balance.RG_EL_MIN + (Balance.RG_EL_MAX - Balance.RG_EL_MIN) * Math.min(1.0, dist / 20_000.0);
        return new double[] { rel, el, dist };
    }

    private static double[] dirLocal(double yawDeg, double pitchDeg) {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);

        return new double[] { -Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p) };
    }

    private boolean barrelClear(double yaw, double pitch) {
        double[] d = dirLocal(yaw, pitch);
        for (double s = 2.0; s <= 28.0; s += 0.5) {
            double[] w = worldPoint(d[0] * s, 8.0 + d[1] * s, 7.0 + d[2] * s);
            BlockPos p = BlockPos.containing(w[0], w[1], w[2]);
            if (!level.hasChunkAt(p)) continue;
            BlockState b = level.getBlockState(p);
            if (b.is(Wmd.MACHINE_PART.get()) || b.getBlock() instanceof RailgunBlock) continue;
            if (!b.getCollisionShape(level, p).isEmpty()) return false;
        }
        return true;
    }

    static double minPitch(double yaw) { return Math.abs(yaw) > Balance.RG_CORNER_YAW ? Balance.RG_CORNER_MIN_PITCH : Balance.RG_EL_MIN; }

    int slewTicks() {
        double turn = Math.max(Math.abs(aimYaw - fromYaw), Math.abs(aimPitch - fromPitch));
        return Math.max(Balance.RG_SLEW_TICKS, (int) Math.ceil(turn / Balance.RG_SLEW_DEG_PER_TICK));
    }

    double[] muzzle() {
        double[] d = dirLocal(aimYaw, aimPitch);
        return worldPoint(d[0] * 28.0, 8.0 + d[1] * 28.0, 7.0 + d[2] * 28.0);
    }

    @Override
    public void command(net.minecraft.server.level.ServerPlayer sp, int cmd, int x, int z) { command((Player) sp, cmd, x, z); }

    void command(Player p, int cmd, int x, int z) {
        if (!Access.mayUse(this, p)) { Access.deny(this, p); return; }
        if (cmd == CMD_TARGET) {
            if (state != IDLE) { why = R_BUSY; sync(true); return; }
            tx = x; tz = z; hasTarget = true; why = R_NONE;
            setChanged(); sync(true);
        } else if (cmd == CMD_FIRE) fire(p);
        else if (cmd == CMD_STOP && (state == SLEW || state == LOAD || state == CHARGE)) {
            why = R_STOPPED;
            broadcast(RailgunEventPayload.ENDED);
            TargetSnapshot.stop(worldPosition.asLong());
            go(AFTER);
            stateTicks = Balance.RG_AFTER_TICKS - 200;
        }
    }

    private void fire(Player p) {
        if (state != IDLE) { why = R_BUSY; sync(true); return; }
        if (!hasTarget) { why = R_NO_TARGET; sync(true); return; }
        if (!loaded && slug.count() < 1) { why = R_NO_SLUG; sync(true); return; }
        double[] s = solve(tx, tz);
        s[1] = Math.max(s[1], minPitch(s[0]));
        if (!barrelClear(s[0], s[1])) { why = R_BLOCKED; sync(true); return; }
        why = R_NONE;
        fromYaw = 0.0F; fromPitch = 0.0F;
        aimYaw = (float) s[0]; aimPitch = (float) s[1];
        chargeTicks = 0; cutsceneSent = false;
        Wmd.LOGGER.info("[railgun] {} fires the railgun at {} towards {} {} ({} blocks)", p.getGameProfile().getName(), worldPosition.toShortString(), tx, tz, Math.round(s[2]));
        go(SLEW);
    }

    private void go(int s) {
        if (s == LOAD && state != LOAD && level instanceof ServerLevel sl) { double[] c = worldPoint(0.0, 8.0, 12.0); sl.playSound(null, c[0], c[1], c[2], WmdSounds.RG_LOAD.get(), SoundSource.BLOCKS, 3.0F, 1.0F); }
        state = s;
        stateTicks = 0;
        setChanged();
        sync(true);
    }

    private void warnAll(ServerLevel sl) {
        long left = Math.max(0, Balance.RG_CHARGE_TICKS - chargeTicks), secs = (left + 19) / 20, gt = sl.getGameTime();
        String mmss = String.format("%d:%02d", secs / 60, secs % 60), who = getOwnerName() == null ? "?" : getOwnerName();
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("title.wmd.railgun.strike").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("title.wmd.railgun.strike_sub", tx, tz, mmss).withStyle(net.minecraft.ChatFormatting.GOLD)));
            p.sendSystemMessage(Component.translatable("message.wmd.railgun.incoming", who, tx, tz, mmss).withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD));
            CcBattleLog.add(sl.getServer(), p.getUUID(), CcBattleLog.LAUNCH_IN, "log.wmd.railgun.incoming", Integer.toString(tx), Integer.toString(tz));
            CommandCenter.addWarning(sl.getServer(), p.getUUID(), new CommandCenter.Warning(worldPosition.asLong() ^ p.getUUID().getLeastSignificantBits(), tx, tz, tx, tz, gt, gt + left, false));
            AlarmNet.raiseAll(sl, p.getUUID());
        }
    }

    private void broadcast(int stage) {
        if (!(level instanceof ServerLevel sl)) return;
        double[] m = muzzle();
        long fire = sl.getGameTime() + Math.max(0, Balance.RG_CHARGE_TICKS - chargeTicks);
        int ty = sl.hasChunkAt(new BlockPos(tx, 64, tz)) ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) : 64;
        PacketDistributor.sendToPlayersInDimension(sl, new RailgunEventPayload(worldPosition.asLong(), stage, tx, ty, tz, fire, (float) m[0], (float) m[1], (float) m[2],
                getOwnerName() == null ? "?" : getOwnerName(), RailgunEventPayload.KIND_RAILGUN));
    }

    public static void serverTickRailgun(Level level, BlockPos pos, BlockState bs, RailgunBlockEntity be) {
        ProcessUnitBlockEntity.serverTick(level, pos, bs, be);
        if (level instanceof ServerLevel sl) be.stateTick(sl);
    }

    @Override
    protected boolean process() {
        if (state != CHARGE) return fail(0);
        chargeTicks++;
        if (chargeTicks % 200 == 0) setChanged();
        return true;
    }

    private void stateTick(ServerLevel sl) {
        if (EmpField.jammed(sl, worldPosition) && (state == SLEW || state == LOAD || state == CHARGE)) { abort(R_EMP); return; }
        stateTicks++;
        switch (state) {
            case SLEW -> { if (stateTicks >= slewTicks()) { go(loaded ? CHARGE : LOAD); if (state == CHARGE) beginCharge(sl); } }
            case LOAD -> {
                if (stateTicks == Balance.RG_LOAD_TICKS * 3 / 10) {
                    if (slug.count() < 1) { why = R_NO_SLUG; go(IDLE); return; }
                    slug.take(1); loaded = true; setChanged();
                }
                if (stateTicks >= Balance.RG_LOAD_TICKS) { go(CHARGE); beginCharge(sl); }
            }
            case CHARGE -> {
                if (cutsceneSent && chargeTicks == prevCharge && chargeTicks < Balance.RG_CHARGE_TICKS) chargeTicks++;
                prevCharge = chargeTicks;
                if (stateTicks % Balance.RG_HOLD_RAMP_TICKS == 0) holdRamp(sl);
                if (stateTicks % 200 == 0 && !TargetSnapshot.has(worldPosition.asLong())) TargetSnapshot.start(sl, worldPosition.asLong(), tx, tz, null);
                if (stateTicks % 100 == 0) broadcast(chargeTicks >= Balance.RG_CHARGE_TICKS - Balance.RG_FINAL_TICKS ? RailgunEventPayload.FINAL : RailgunEventPayload.CHARGING);
                if (chargeTicks >= Balance.RG_CHARGE_TICKS - Balance.RG_FINAL_TICKS) {
                    if (!finalWarned) { finalWarned = true; warnAll(sl); }
                    if (stateTicks % 100 == 0) for (net.minecraft.server.level.ServerPlayer p : sl.players()) AlarmNet.raiseAll(sl, p.getUUID());
                }
                if (!cutsceneSent && chargeTicks >= Balance.RG_CHARGE_TICKS - Balance.RG_CUTSCENE_LEAD) {
                    cutsceneSent = true;
                    double[] m = muzzle();
                    int ty = sl.hasChunkAt(new BlockPos(tx, 64, tz)) ? sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz) : 64;
                    int lead = Math.max(20, Balance.RG_CHARGE_TICKS - chargeTicks);
                    PacketDistributor.sendToPlayersInDimension(sl, new CutscenePayload(CutscenePayload.KIND_RAILGUN, (float) m[0], (float) m[1], (float) m[2], tx + 0.5F, ty, tz + 0.5F,
                            lead, lead + Balance.RG_CUTSCENE_TICKS - Balance.RG_CUTSCENE_LEAD, getOwnerName() == null ? "" : getOwnerName()));
                }
                if (chargeTicks >= Balance.RG_CHARGE_TICKS) shoot(sl);
                else if (stateTicks % 10 == 0) sync(false);
            }
            case AFTER -> { if (stateTicks >= Balance.RG_AFTER_TICKS) go(IDLE); else if (stateTicks % 20 == 0) sync(false); }
            case ABORT -> { if (stateTicks >= Balance.RG_ABORT_TICKS) go(IDLE); }
            default -> { if (slug.count() != lastSlugs) { lastSlugs = slug.count(); sync(true); } }
        }
    }

    private void beginCharge(ServerLevel sl) {
        finalWarned = false;
        broadcast(RailgunEventPayload.CHARGING);
        MusicTriggersHook.fire(sl, "wmd:railgun_charge", worldPosition);
        holdRamp(sl);
        TargetSnapshot.start(sl, worldPosition.asLong(), tx, tz, null);
    }

    private void holdRamp(ServerLevel sl) {
        double full = WarheadFx.holdRadius(4, WarheadType.RAILGUN);
        double r = Math.min(full, Balance.RG_HOLD_RAMP_START + (double) Balance.RG_HOLD_RAMP_STEP * (stateTicks / Balance.RG_HOLD_RAMP_TICKS));
        FlightManager.get(sl).holdForTest(sl, tx, tz, r);
    }

    boolean testSkip(int seconds) {
        if (state != CHARGE) return false;
        chargeTicks = Math.max(chargeTicks, Balance.RG_CHARGE_TICKS - Math.max(1, seconds) * 20);
        if (seconds * 20 > Balance.RG_CUTSCENE_LEAD) cutsceneSent = false;
        setChanged();
        sync(true);
        return true;
    }

    private void scorch(ServerLevel sl) {
        double[] d = dirLocal(aimYaw, 0.0);
        double half = Math.tan(Math.toRadians(Balance.RG_SCORCH_HALF_DEG));
        net.minecraft.util.RandomSource rnd = sl.random;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (double s = 9.0; s <= Balance.RG_SCORCH_LEN + 9.0; s += 0.7) {
            for (double lat = -s * half; lat <= s * half; lat += 0.7) {
                double[] w = worldPoint(d[0] * s - d[2] * lat, 0.0, 7.0 + d[2] * s + d[0] * lat);
                int x = (int) Math.floor(w[0]), z = (int) Math.floor(w[2]);
                if (!done.add(BlockPos.asLong(x, 0, z)) || !sl.hasChunkAt(p.set(x, 0, z))) continue;
                if (rnd.nextFloat() > 1.0F - 0.6F * (float) ((s - 9.0) / Balance.RG_SCORCH_LEN)) continue;
                int top = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                for (int k = 0; k < 3; k++) {
                    p.set(x, top - k, z);
                    BlockState b = sl.getBlockState(p);
                    if (b.isAir() || b.is(Wmd.MACHINE_PART.get()) || b.getBlock() instanceof RailgunBlock || !Config.Server.canBreak(sl, p)) break;
                    if (b.is(net.minecraft.tags.BlockTags.LEAVES) || b.canBeReplaced() || b.is(net.minecraft.world.level.block.Blocks.SNOW) || b.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
                        sl.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        continue;
                    }
                    if (b.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK) || b.is(net.minecraft.world.level.block.Blocks.PODZOL) || b.is(net.minecraft.world.level.block.Blocks.MYCELIUM)) {
                        sl.setBlock(p, (rnd.nextBoolean() ? net.minecraft.world.level.block.Blocks.COARSE_DIRT : net.minecraft.world.level.block.Blocks.DIRT).defaultBlockState(), 3);
                        BlockPos up = p.above();
                        if (rnd.nextFloat() < 0.04F && sl.getBlockState(up).isAir()) sl.setBlock(up, net.minecraft.world.level.block.BaseFireBlock.getState(sl, up), 3);
                    }
                    break;
                }
            }
        }
    }

    private void shoot(ServerLevel sl) {
        loaded = false;
        chargeTicks = 0;
        scorch(sl);
        TargetSnapshot.stopIn(worldPosition.asLong(), sl, Balance.RG_TRAVEL_TICKS + Balance.RG_CUTSCENE_TICKS);
        double[] m = muzzle();
        RailgunStrikes.schedule(sl, tx, tz, sl.getGameTime() + Balance.RG_TRAVEL_TICKS, getOwnerName() == null ? "?" : getOwnerName(), getOwner(), m);
        MusicTriggersHook.fire(sl, "wmd:railgun_fire", BlockPos.containing(m[0], m[1], m[2]));
        sl.playSound(null, m[0], m[1], m[2], WmdSounds.RG_SHOT.get(), SoundSource.BLOCKS, 16.0F, 1.0F);
        broadcast(RailgunEventPayload.FIRED);
        Wmd.LOGGER.info("[railgun] gun at {} FIRED at {} {}", worldPosition.toShortString(), tx, tz);
        go(AFTER);
    }

    private void abort(int reason) {
        why = reason;
        chargeTicks = 0;
        Wmd.LOGGER.info("[railgun] gun at {} ABORTED (reason {})", worldPosition.toShortString(), reason);
        TargetSnapshot.stop(worldPosition.asLong());
        broadcast(RailgunEventPayload.ABORTED);
        if (level instanceof ServerLevel sl) { double[] c = worldPoint(0.0, 5.0, 7.0); sl.playSound(null, c[0], c[1], c[2], WmdSounds.RG_ABORT.get(), SoundSource.BLOCKS, 6.0F, 1.0F); }
        go(ABORT);
    }

    @Override
    public void empDrain() {
        super.empDrain();
        if (state == SLEW || state == LOAD || state == CHARGE) abort(R_EMP);
    }

    static final java.util.Set<RailgunBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) LOADED.add(this); }
    @Override public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            LOADED.remove(this);

            net.minecraft.world.level.chunk.LevelChunk lc = level.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
            if (lc != null && lc.getBlockState(worldPosition).getBlock() != getBlockState().getBlock()) TargetSnapshot.stop(worldPosition.asLong());
        }
    }

    public static void blastAt(ServerLevel sl, net.minecraft.world.phys.Vec3 pos, double r) {
        for (RailgunBlockEntity be : new java.util.ArrayList<>(LOADED)) {
            if (be.level != sl || be.isRemoved() || !(be.state == SLEW || be.state == LOAD || be.state == CHARGE)) continue;
            double[] c = be.worldPoint(0.0, 4.0, 7.0);
            if (Math.sqrt((c[0] - pos.x) * (c[0] - pos.x) + (c[1] - pos.y) * (c[1] - pos.y) + (c[2] - pos.z) * (c[2] - pos.z)) > r + Balance.RG_ABORT_REACH) continue;
            be.abort(R_HIT);
        }
    }

    static RailgunBlockEntity nearest(ServerLevel sl, net.minecraft.world.phys.Vec3 at, double reach) {
        RailgunBlockEntity best = null;
        double bd = reach * reach;
        for (RailgunBlockEntity be : LOADED) {
            if (be.level != sl || be.isRemoved()) continue;
            double d = be.worldPosition.getCenter().distanceToSqr(at);
            if (d < bd) { bd = d; best = be; }
        }
        return best;
    }

    static boolean cutsceneNear(ServerLevel sl, int x, int z, double reach) {
        for (RailgunBlockEntity be : LOADED)
            if (be.level == sl && (be.state == CHARGE || be.state == AFTER && be.stateTicks < 300) && Math.hypot(be.tx - x, be.tz - z) <= reach) return true;
        return false;
    }

    private void sync(boolean now) {
        if (level == null || level.isClientSide) return;
        int h = (((state * 31 + why) * 31 + chargeTicks / 60) * 31 + (loaded ? 1 : 0) + stateTicks / 20 * 7) * 31 + slug.count();
        if (!now && h == lastSync) return;
        lastSync = h;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int state() { return state; }
    public int why() { return why; }
    public boolean hasTarget() { return hasTarget; }
    public int targetX() { return tx; }
    public int targetZ() { return tz; }
    public boolean loaded() { return loaded; }
    public int slugs() { return level != null && level.isClientSide ? clientSlugs : slug.count(); }

    public float charge01() { return Mth.clamp(chargeTicks / (float) Balance.RG_CHARGE_TICKS, 0.0F, 1.0F); }
    public int secondsToShot() { return Math.max(0, (Balance.RG_CHARGE_TICKS - chargeTicks) / 20); }
    public float aimYaw() { return aimYaw; }
    public float aimPitch() { return aimPitch; }
    public int stateTicks() { return stateTicks; }

    private int clientState = -1;
    private double clientFrom;

    static float ease(float x) { x = Mth.clamp(x, 0.0F, 1.0F); return x * x * (3.0F - 2.0F * x); }

    float[] shownAim(double t) {
        return switch (state) {
            case SLEW -> {
                double f = t / slewTicks();
                yield new float[] { Mth.lerp(ease((float) ((f - 0.2) / 0.8)), fromYaw, aimYaw), Mth.lerp(ease((float) (f / 0.35)), fromPitch, aimPitch) };
            }
            case LOAD, CHARGE -> new float[] { aimYaw, aimPitch };
            case AFTER -> home((t - (Balance.RG_AFTER_TICKS - 200)) / 200.0);
            case ABORT -> home(t / Balance.RG_ABORT_TICKS);
            default -> new float[] { 0.0F, 0.0F };
        };
    }

    private float[] home(double f) {
        return new float[] { Mth.lerp(ease((float) (f / 0.7)), aimYaw, 0.0F), Mth.lerp(ease((float) ((f - 0.6) / 0.4)), aimPitch, 0.0F) };
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (clientState != state) { clientState = state; clientFrom = tt - stateTicks; }
        double t = tt - clientFrom;
        boolean dark = ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5);
        float ch = state == CHARGE ? charge01() : 0.0F;
        boolean busy = state == SLEW || state == LOAD || state == CHARGE;
        boolean fin = state == CHARGE && chargeTicks >= Balance.RG_CHARGE_TICKS - Balance.RG_FINAL_TICKS;
        float[] aim = shownAim(t);
        set(model, "turret", b -> b.setRotY((float) Math.toRadians(AZ_SIGN * aim[0])));
        set(model, "cradle", b -> b.setRotX((float) Math.toRadians(EL_SIGN * aim[1])));

        float rec = state == AFTER ? (t < 2 ? 32.0F * (float) (t / 2.0) : 32.0F * (1.0F - ease((float) ((t - 2.0) / 60.0)))) : 0.0F;
        set(model, "recoil", b -> b.setPosZ(rec));

        float lf = state == LOAD ? (float) (t / Balance.RG_LOAD_TICKS) : -1.0F;
        float breech = lf < 0 ? 0.0F : lf < 0.2F ? ease(lf / 0.2F) : lf < 0.75F ? 1.0F : 1.0F - ease((lf - 0.75F) / 0.15F);
        set(model, "breech", b -> b.setPosY(-26.0F * breech));
        float arm = lf < 0.2F ? 0.0F : lf < 0.45F ? ease((lf - 0.2F) / 0.25F) : lf < 0.6F ? 1.0F : 1.0F - ease((lf - 0.6F) / 0.2F);
        set(model, "loader", b -> b.setRotX((float) Math.toRadians(-50.0 * arm)));
        float ram = lf < 0.45F ? 0.0F : lf < 0.6F ? ease((lf - 0.45F) / 0.15F) : lf < 0.75F ? 1.0F - ease((lf - 0.6F) / 0.15F) : 0.0F;
        set(model, "rammer", b -> b.setPosZ(-10.0F * ram));
        float lug = lf < 0.9F ? (state == LOAD ? 0.0F : 1.0F) : ease((lf - 0.9F) / 0.1F);
        for (int i = 0; i < 4; i++) { final int s = i % 2 == 0 ? 1 : -1; set(model, "lug" + i, b -> b.setRotZ((float) Math.toRadians(s * 90.0 * lug))); }

        int fill = state == CHARGE ? Math.min(6, (int) (ch * 6.0F)) : 0;
        int head = colHead(tt, ch);
        boolean beat = ((long) tt) % 10 < 6;
        for (int k = 0; k < 4; k++)
            for (int m = 0; m < 6; m++) {
                final int mm = m;
                final boolean on = !dark && busy && (fin ? beat || mm < fill : mm < fill || mm == head);
                set(model, "cap" + k + m, b -> b.setHidden(!on));
            }

        int litR = state == CHARGE ? (int) (ch * 13.0F) : 0;
        int chase = (int) Math.floor(tt * 0.5) % 4;
        boolean blaze = state == CHARGE && chargeTicks >= Balance.RG_CHARGE_TICKS - 40;
        for (int i = 0; i < 12; i++) {
            final boolean on = !dark && state == CHARGE && (blaze || i < litR && !(fin && Math.floorMod(chase - i, 4) == 0));
            set(model, "ring" + i, b -> b.setHidden(!on));
        }

        for (int i = 0; i < 13; i++) set(model, "line" + i, b -> b.setHidden(true));
        final boolean hot = !dark && state == AFTER && t < Balance.RG_AFTER_TICKS * 0.6 && (t > 120 || ((long) tt) % 3 != 0);
        set(model, "heat", b -> b.setHidden(!hot));

        float open = state == CHARGE ? 0.6F * ease((ch - 0.1F) / 0.6F) + 0.4F * ease((float) ((chargeTicks - (Balance.RG_CHARGE_TICKS - 200)) / 30.0))
                : state == AFTER ? 1.0F - ease((float) ((t - 200) / 60.0)) : 0.0F;
        for (int k = 0; k < 6; k++) {
            final org.joml.Vector3f e = clawEuler(k, CLAW_OPEN * open);
            set(model, "claw" + k, b -> { b.setRotX(e.x); b.setRotY(e.y); b.setRotZ(e.z); });
            final boolean tip = !dark && open > 0.3F && (state == CHARGE || t < 200) && (!fin || ((long) tt + k) % 4 != 0);
            set(model, "tip" + k, b -> b.setHidden(!tip));
        }

        for (int i = 0; i < 3; i++) {
            final boolean on = !dark && state == CHARGE && (ch > 0.5F + 0.15F * i || fin);
            set(model, "coil" + i, b -> b.setHidden(!on));
        }
        final boolean ap = !dark && (state == CHARGE && secondsToShot() <= 10 || state == AFTER && t < 40);
        set(model, "aperture", b -> b.setHidden(!ap));

        float vent0 = state == AFTER ? (t < 10 ? ease((float) (t / 10.0)) : 1.0F - ease((float) ((t - 300) / 60.0))) : 0.0F;
        for (int i = 0; i < 4; i++) { final float x = FIN_XS * (i % 2 == 0 ? -4.0F : 4.0F) * vent0; set(model, "fin" + i, b -> b.setPosX(x)); }

        double fan = busy || state == AFTER ? tt * (0.15 + 1.2 * ch + (state == AFTER ? 0.6 * (1.0 - t / Balance.RG_AFTER_TICKS) : 0.0)) : 0.0;
        for (int i = 0; i < 2; i++) { final float a = (float) (fan + i); set(model, "fan" + i, b -> b.setRotX(a)); }
        double q = ch * 4.0 - Math.floor(ch * 4.0);
        float vent = state == CHARGE && ch > 0.05F && q < 0.08 ? 1.0F : state == AFTER && t < 200 ? 1.0F : 0.0F;
        set(model, "vent0", b -> b.setRotZ((float) Math.toRadians(-40.0 * vent)));
        set(model, "vent1", b -> b.setRotZ((float) Math.toRadians(40.0 * vent)));
        set(model, "vent2", b -> b.setRotZ((float) Math.toRadians(-40.0 * vent)));
        set(model, "vent3", b -> b.setRotZ((float) Math.toRadians(40.0 * vent)));
        float cont = state == CHARGE ? Math.min(1.0F, (float) Math.floor(ch * 4.0) / 3.0F) : 0.0F;
        set(model, "contactor0", b -> b.setRotZ((float) Math.toRadians(-60.0 * (1.0 - cont))));
        set(model, "contactor1", b -> b.setRotZ((float) Math.toRadians(60.0 * (1.0 - cont))));

        final float dish = busy ? (float) (0.35 * Math.sin(tt * 0.05)) : 0.0F;
        set(model, "dish", b -> b.setRotY(dish));
        for (int i = 0; i < 2; i++) {
            final float a = (float) (tt * 0.6 + i * Math.PI);
            set(model, "strobe" + i, b -> { b.setHidden(dark || !fin); b.setRotY(a); });
        }
        final boolean live = !dark && (busy || state == AFTER);
        set(model, "seam", b -> b.setHidden(!live));
        set(model, "ringglow", b -> b.setHidden(!live || state == AFTER && ((long) tt) % 20 < 10));
        set(model, "optic", b -> b.setHidden(dark || !(busy || state == AFTER)));
        set(model, "hubglow", b -> b.setHidden(!live));

        int litH = state == CHARGE ? (int) (ch * 13.0F) : 0, spinH = (int) Math.floor(tt * (fin ? 0.8 : 0.3)) % 12;
        for (int i = 0; i < 12; i++) {
            final boolean on = !dark && busy && (fin ? Math.floorMod(spinH - i, 12) < 4 : i < litH || i == spinH);
            set(model, "hublamp" + i, b -> b.setHidden(!on));
        }
        set(model, "desk", b -> b.setHidden(dark));
    }

    private static void set(CcBones m, String n, java.util.function.Consumer<CcBones.Bone> f) { m.getBone(n).ifPresent(f); }

    static final double[] RING_Z = new double[12];
    static { for (int i = 0; i < 12; i++) RING_Z[i] = 30.0 + (-274.0) * Math.pow(i / 11.0, 0.75); }

    static double collarS(int i) { return (112.0 - RING_Z[i]) / 16.0; }
    static final float CLAW_OPEN = 35.0F;

    static final float FIN_XS = -1.0F;

    static org.joml.Vector3f clawEuler(int k, double deg) {
        double a = Math.toRadians(90.0 + 60.0 * k);
        return new org.joml.Quaternionf().rotateAxis((float) Math.toRadians(deg), (float) Math.sin(a), (float) -Math.cos(a), 0.0F).getEulerAnglesZYX(new org.joml.Vector3f());
    }

    static int colHead(double tt, float ch) { return (int) Math.floor(tt * (0.12 + 0.5 * ch)) % 8; }

    static double colCellY(int m) { return (m < 3 ? 57.5 + 7.0 * m : 84.5 + 7.0 * (m - 3)) / 16.0; }

    public static void clientTickRailgun(Level level, BlockPos pos, BlockState bs, RailgunBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, bs, be);
        ClientRailgun.tickGun(be, level);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.RG_CHARGE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 6, 7); }
    @Override protected float soundVolume() { return 2.5F; }
    @Override protected float soundPitch() { return 0.75F + 0.6F * charge01(); }

    @Override protected Slot[] slots() { return slots; }
    @Override protected String[] iconIds() { return new String[] { "railgun_slug" }; }
    @Override protected List<FluidTank> tanks() { return tanks; }
    @Override protected List<Fluid> tankFluids() { return List.of(Fluids.EMPTY); }
    @Override protected List<String> labelKeys() { return List.of("railgun_slug"); }
    @Override protected int inputTanks() { return 1; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public String guiTankUnit(int t) { return "items"; }
    @Override protected float progress01() { return state == CHARGE ? charge01() : -1.0F; }
    @Override protected boolean acceptsUpgrade(UpgradeItem.Type t) { return t == UpgradeItem.Type.MUFFLER; }
    @Override public boolean guiRunning() { return state == CHARGE; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.use", String.format("%,d", Balance.RG_FE_PER_TICK * 20)),
                Component.translatable("gui.wmd.info.railgun", Balance.RG_CHARGE_TICKS / 1200),
                Component.translatable("gui.wmd.info.railgun_warn"));
    }

    @Override
    protected String detail() {
        return "state " + state + " (" + stateTicks + " t), charge " + Math.round(charge01() * 100) + "%, target " + (hasTarget ? tx + " " + tz : "none") + ", loaded " + loaded
                + ", slugs " + slug.count() + ", why " + why;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, -3, 0, 14, Direction.SOUTH) ? energy() : null; }

    @Override
    public IItemHandler itemPort(BlockPos part, Direction side) { return at(part, side, 3, 0, 14, Direction.SOUTH) ? slugIn : null; }

    private static final PortMap PORT_MAP = new PortMap(-7, 7, 15).power(-3, 0, 14, Direction.SOUTH, false).item(3, 0, 14, Direction.SOUTH, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveExtra(CompoundTag tag) {
        putState(tag);
        ownerData.write(tag);
        tag.putBoolean("rg_full", true);
    }

    private void putState(CompoundTag tag) {
        tag.putInt("rg_state", state); tag.putInt("rg_t", stateTicks); tag.putInt("rg_charge", chargeTicks); tag.putInt("rg_why", why);
        tag.putBoolean("rg_has", hasTarget); tag.putBoolean("rg_loaded", loaded); tag.putInt("rg_tx", tx); tag.putInt("rg_tz", tz);
        tag.putFloat("rg_yaw", aimYaw); tag.putFloat("rg_pitch", aimPitch); tag.putFloat("rg_fy", fromYaw); tag.putFloat("rg_fp", fromPitch);
        tag.putBoolean("rg_cut", cutsceneSent);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        if (!tag.contains("rg_state")) return;
        state = Mth.clamp(tag.getInt("rg_state"), IDLE, ABORT); stateTicks = tag.getInt("rg_t"); chargeTicks = tag.getInt("rg_charge"); why = tag.getInt("rg_why");
        hasTarget = tag.getBoolean("rg_has"); loaded = tag.getBoolean("rg_loaded"); tx = tag.getInt("rg_tx"); tz = tag.getInt("rg_tz");
        aimYaw = tag.getFloat("rg_yaw"); aimPitch = tag.getFloat("rg_pitch"); fromYaw = tag.getFloat("rg_fy"); fromPitch = tag.getFloat("rg_fp");
        cutsceneSent = tag.getBoolean("rg_cut");
        if (tag.contains("rg_slugs")) clientSlugs = tag.getInt("rg_slugs");
        if (tag.getBoolean("rg_full")) ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        tag.putInt("rg_slugs", slug.count());
        return tag;
    }
}
