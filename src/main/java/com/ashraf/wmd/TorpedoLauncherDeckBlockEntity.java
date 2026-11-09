package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class TorpedoLauncherDeckBlockEntity extends TorpedoLauncherBlockEntity {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 2;

    static final double AX = 1.0, AZ = 1.375, AY = 1.4, REACH = 1.6;

    public static final float TRAIN_SIGN = -1.0F;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 2, Direction.SOUTH, false);

    private int train;
    private float trainNow;

    private float shown = Float.NaN, prev;

    public TorpedoLauncherDeckBlockEntity(BlockPos pos, BlockState state) { super(Wmd.TORPEDO_LAUNCHER_DECK_BE.get(), pos, state, Balance.DECK_FE, 3, (int) Balance.DECK_DEPTH); }

    @Override protected int chargeTicks() { return Balance.DECK_CHARGE_TICKS; }
    @Override protected double exitSpeed() { return Balance.DECK_DROP_SPEED; }

    static final double[] TUBE_SIDE = { 0.0, 4.6 / 16.0, -4.6 / 16.0 }, TUBE_Y = { 26.6 / 16.0, 18.6 / 16.0, 18.6 / 16.0 };

    @Override
    protected double[][] muzzle() {
        double a = Math.toRadians(trainNow), dx = Math.sin(a), dz = -Math.cos(a);
        int t = firingSlot >= 0 ? firingSlot : nextSlot();
        double side = t >= 0 && t < 3 ? TUBE_SIDE[t] : 0.0, y = t >= 0 && t < 3 ? TUBE_Y[t] : AY;

        return new double[][] { { AX + dx * REACH + Math.cos(a) * side, y, AZ + dz * REACH + Math.sin(a) * side }, { dx, -0.25, dz } };
    }

    @Override
    protected void onFired(net.minecraft.server.level.ServerLevel sl, net.minecraft.world.phys.Vec3 wp, net.minecraft.world.phys.Vec3 wd) {
        for (int k = 0; k < 14; k++) {
            double v = 0.12 + sl.random.nextDouble() * 0.3;
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, wp.x + wd.x * 0.2, wp.y + wd.y * 0.2, wp.z + wd.z * 0.2, 0,
                    wd.x * v + (sl.random.nextDouble() - 0.5) * 0.08, 0.02 + sl.random.nextDouble() * 0.04, wd.z * v + (sl.random.nextDouble() - 0.5) * 0.08, 1.0);
        }
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, wp.x, wp.y, wp.z, 6, 0.15, 0.15, 0.15, 0.03);
    }

    @Override protected boolean onBearing() { return Math.abs(trainNow - train) < 0.5F; }

    @Override
    protected boolean extraWork() {
        if (onBearing()) { trainNow = train; return false; }
        trainNow += (float) Mth.clamp(train - trainNow, -Balance.DECK_TRAIN_SPEED, Balance.DECK_TRAIN_SPEED);
        if (onBearing()) { trainNow = train; sync(); }
        return true;
    }

    public int train() { return train; }

    public void setTrain(int deg) {
        deg = (int) Mth.clamp(deg, -Balance.DECK_TRAIN_MAX, Balance.DECK_TRAIN_MAX);
        if (deg == train) return;
        train = deg;
        setChanged();
        sync();
    }

    @Override public PortMap guiPortMap() { return PORT_MAP; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 2, Direction.SOUTH) ? energy() : null; }

    public boolean ready() { return fireStatus() == S_READY; }

    public float shownTrain(float pt) { return Float.isNaN(shown) ? train : prev + (shown - prev) * pt; }

    public float capShown(int t, float pt) { return capPrev[t] + (cap[t] - capPrev[t]) * pt; }
    public float doorShown(int t, float pt) { return doorPrev[t] + (door[t] - doorPrev[t]) * pt; }

    static final float TRAIN_EASE = 8.0F;

    public static void clientTickDeck(Level level, BlockPos pos, BlockState state, TorpedoLauncherDeckBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (Float.isNaN(be.shown)) be.shown = be.train;
        be.prev = be.shown;

        final float max = (float) Balance.DECK_TRAIN_SPEED, acc = max / TRAIN_EASE, d = be.train - be.shown;
        final float want = Math.signum(d) * Math.min(max, (float) Math.sqrt(2.0 * acc * Math.abs(d)));
        be.vel += Mth.clamp(want - be.vel, -acc, acc);
        if (Math.abs(d) < 0.05F && Math.abs(be.vel) <= acc) { be.shown = be.train; be.vel = 0.0F; }
        else if (Math.abs(be.vel) >= Math.abs(d) && Math.signum(be.vel) == Math.signum(d)) { be.shown = be.train; be.vel = 0.0F; }
        else be.shown += be.vel;

        final long gt = level.getGameTime();
        final int[] kinds = be.kinds();
        final int next = DeckLauncherAnim.nextTube(kinds);
        final boolean ready = be.fireStatus() == S_READY;
        final double since = gt - be.lastFire();
        if (be.reloadLeft > 0) {
            if (be.reloadTube < 0) { for (int i = 0; i < kinds.length; i++) if (kinds[i] < 0) { be.reloadTube = i; break; } }
            be.reloadLeft--;
        } else be.reloadTube = -1;
        final net.minecraft.world.phys.Vec3 at = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        for (int t = 0; t < 3; t++) {
            be.capPrev[t] = be.cap[t];
            be.doorPrev[t] = be.door[t];
            final boolean capOpen = (ready && t == next) || (t == be.lastSlot() && since >= 0 && since < DeckLauncherAnim.HOLD);
            final boolean doorOpen = t == be.reloadTube && be.reloadLeft > 10;
            if (capOpen && be.cap[t] == 0.0F)
                level.playLocalSound(at.x, at.y, at.z, WmdSounds.BAY_CLAMP.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH * 0.6F, 1.35F, false);
            if (doorOpen && be.door[t] == 0.0F)
                level.playLocalSound(at.x, at.y, at.z, WmdSounds.BAY_CLAMP.get(), net.minecraft.sounds.SoundSource.BLOCKS, Balance.SND_MECH * 0.6F, 1.0F, false);
            be.cap[t] = Mth.clamp(be.cap[t] + (capOpen ? 1.0F : -1.0F) / DeckLauncherAnim.CAP_T, 0.0F, 1.0F);
            be.door[t] = Mth.clamp(be.door[t] + (doorOpen ? 1.0F : -1.0F) / DeckLauncherAnim.DOOR_T, 0.0F, 1.0F);
        }

        be.trainLoop = LoopSound.keep(be.trainLoop, gt, WmdSounds.DECK_TRAIN_LOOP.get(), Balance.SND_MACHINE, 1.0F, () -> at,
                () -> !be.isRemoved() && (Math.abs(be.train - be.shown) > 0.5F || Math.abs(be.vel) > 0.05F));
    }

    private float vel;
    private final float[] cap = new float[3], capPrev = new float[3], door = new float[3], doorPrev = new float[3];
    private int reloadTube = -1;

    Object trainLoop;

    @Override
    protected void saveExtra(CompoundTag tag) {
        super.saveExtra(tag);
        tag.putInt("tpTrain", train);
        tag.putFloat("tpTrainNow", trainNow);
    }

    @Override
    protected void loadExtra(CompoundTag tag) {
        super.loadExtra(tag);
        train = tag.getInt("tpTrain");
        trainNow = tag.getFloat("tpTrainNow");
    }
}
