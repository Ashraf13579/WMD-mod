package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class TowedArrayWinchBlockEntity extends SonarSensorBlockEntity implements CcAnimated {

    public static final int X0 = 0, X1 = 1, Y0 = 0, Y1 = 1, Z0 = 0, Z1 = 1;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 + 1).power(0, 0, 1, Direction.SOUTH, false);

    static final double[] FAIRLEAD = { 0.5, 26.0 / 16.0, -7.7 / 16.0 }, OUTSIDE = { 0.5, 26.0 / 16.0, -1.7 };

    public static final int D_NONE = 0, D_STREAMING = 1, D_TURNING = 2, D_FAST = 3, D_BLOCKED = 4, D_STOWED = 5;
    private boolean blocked;

    private boolean stowed, redstone;

    private double out;
    private int deafWhy = D_STREAMING;

    private final ArrayDeque<Vec3> crumbs = new ArrayDeque<>();
    private double lastHeading = Double.NaN;
    private long lastHeadingTime, deafUntil;
    private Vec3 earNow;

    final ArrayDeque<Vec3> clientCrumbs = new ArrayDeque<>();
    private float outShown = Float.NaN;
    private long outShownTime;

    public TowedArrayWinchBlockEntity(BlockPos pos, BlockState state) { super(Wmd.TOWED_ARRAY_WINCH_BE.get(), pos, state, Balance.TOWED_FE); }

    @Override protected double range() { return Balance.TOWED_RANGE; }
    @Override protected int kind() { return SonarNet.TOWED; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 1, 0); }
    @Override public String modeKey() { return "gui.wmd.sonar.towed_mode"; }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, 1, Direction.SOUTH) ? energy() : null; }

    Vec3 head() { double[] p = worldPoint(FAIRLEAD[0], FAIRLEAD[1], FAIRLEAD[2]); return new Vec3(p[0], p[1], p[2]); }

    Vec3 aft() {
        double[] a = worldPoint(FAIRLEAD[0], FAIRLEAD[1], FAIRLEAD[2]), b = worldPoint(OUTSIDE[0], OUTSIDE[1], OUTSIDE[2]);
        Vec3 d = new Vec3(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
        return d.lengthSqr() < 1.0E-9 ? new Vec3(0.0, 0.0, -1.0) : d.normalize();
    }

    @Override
    public Vec3 ear() {
        if (level != null && !level.isClientSide && earNow != null) return earNow;
        return head();
    }

    public double cableOut() { return out; }
    public boolean stowed() { return stowed; }

    public boolean reeling() { return stowed || redstone; }

    public void setStowed(boolean v) { if (v != stowed) { stowed = v; setChanged(); sync(); } }

    private boolean signal() {
        for (int x = X0; x <= X1; x++) for (int y = Y0; y <= Y1; y++) for (int z = Z0; z <= Z1; z++)
            if (level.hasNeighborSignal(Machines.toWorld(worldPosition, facing(), Machines.mount(getBlockState()), x, y, z))) return true;
        return false;
    }
    public int deafWhy() { return deafWhy; }

    @Override
    protected boolean work() {
        if (level instanceof ServerLevel sl) {
            Vec3 h = head();
            TowedArray.crumb(crumbs, h, Balance.TOWED_LENGTH + 8.0);
            double was = out;

            if (sl.getGameTime() % 20 == 0) {
                blocked = !HullPlug.open(this, facing(), Machines.mount(getBlockState()), new int[] { 0, 1, -1 }, new int[] { 1, 1, -1 });
                boolean rs = signal();
                if (rs != redstone) { redstone = rs; setChanged(); sync(); }
            }
            out = blocked ? Math.max(0.0, out - Balance.TOWED_STREAM_SPEED * 2.0)
                    : reeling() ? Math.max(0.0, out - Balance.TOWED_STREAM_SPEED) : Math.min(Balance.TOWED_LENGTH, out + Balance.TOWED_STREAM_SPEED);
            earNow = TowedArray.tail(TowedArray.points(crumbs, h, aft(), out, Balance.TOWED_SINK * out / Balance.TOWED_LENGTH));
            if (was != out && (sl.getGameTime() % 10 == 0 || out >= Balance.TOWED_LENGTH || out <= 0.0)) { setChanged(); sync(); }
            boolean ran = super.work();
            return ran && !(reeling() && out <= 0.0);
        }
        return super.work();
    }

    @Override
    protected boolean deaf(ServerLevel sl) {
        long now = sl.getGameTime();
        int why = D_NONE;
        UUID ship = SableBridge.shipIdAt(sl, worldPosition);
        SableBridge.ShipInfo s = ship == null ? null : SableBridge.shipInfo(sl, ship);
        if (s != null) {
            Vec3 v = s.linearVelocity();
            double hv = Math.hypot(v.x, v.z);
            if (hv > 0.5) {
                double hd = (Math.toDegrees(Math.atan2(v.x, -v.z)) + 360.0) % 360.0;
                if (!Double.isNaN(lastHeading) && now > lastHeadingTime) {
                    double rate = TowedArray.headingDiff(hd, lastHeading) / ((now - lastHeadingTime) / 20.0);
                    if (rate > Balance.TOWED_DEAF_TURN) deafUntil = now + Balance.TOWED_SETTLE_TICKS;
                }
                lastHeading = hd;
                lastHeadingTime = now;
            } else lastHeading = Double.NaN;
            if (s.speed() > Balance.TOWED_DEAF_SPEED) why = D_FAST;
        }
        if (now < deafUntil && why == D_NONE) why = D_TURNING;
        if (out < Balance.TOWED_LENGTH * Balance.TOWED_MIN_OUT) why = D_STREAMING;
        if (reeling()) why = D_STOWED;
        if (blocked) why = D_BLOCKED;
        if (why != deafWhy) { deafWhy = why; setChanged(); sync(); }
        return why != D_NONE;
    }

    @Override
    public String deafKey() {
        return switch (deafWhy) {
            case D_STREAMING -> "gui.wmd.sonar.deaf_streaming";
            case D_TURNING -> "gui.wmd.sonar.deaf_turning";
            case D_FAST -> "gui.wmd.sonar.deaf_fast";
            case D_BLOCKED -> "gui.wmd.sonar.deaf_blocked";
            case D_STOWED -> out > 0.0 ? "gui.wmd.sonar.deaf_reeling" : "gui.wmd.sonar.deaf_stowed";
            default -> null;
        };
    }

    @Override
    public Component extraLine() {
        return Component.translatable("gui.wmd.sonar.cable", (int) Math.round(out), (int) Balance.TOWED_LENGTH, (int) Balance.TOWED_DEAF_SPEED, (int) Balance.TOWED_DEAF_TURN);
    }

    @Override
    protected String detail() { return super.detail() + ", cable " + (int) out + " / " + (int) Balance.TOWED_LENGTH + ", deaf " + deafWhy + (stowed ? ", stowed" : "") + (redstone ? ", redstone" : ""); }

    float outShown(double tt) {
        if (Float.isNaN(outShown)) { outShown = (float) out; outShownTime = (long) tt; }
        long dt = Math.max(0L, (long) tt - outShownTime);
        outShownTime = (long) tt;
        float step = (float) (Balance.TOWED_STREAM_SPEED * dt);
        outShown += (float) Math.max(-step, Math.min(step, out - outShown));
        return outShown;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        final float o = outShown(tt);
        model.getBone("base").ifPresent(b -> { b.setPosX(0.0F); b.setPosZ(0.0F); b.setRotX(0.0F); b.setRotZ(0.0F); });
        model.getBone("drum").ifPresent(b -> b.setRotX(-o / 0.86F));
        model.getBone("wind").ifPresent(b -> b.setPosX(6.0F * (float) Math.sin(o / 0.86F / 6.0F)));
    }

    @Override
    protected void writeExtra(CompoundTag tag) {
        tag.putDouble("taOut", out);
        tag.putInt("taDeaf", deafWhy);
        tag.putBoolean("taStow", stowed);
        tag.putBoolean("taRs", redstone);
    }

    @Override
    protected void readExtra(CompoundTag tag) {
        out = Math.max(0.0, Math.min(Balance.TOWED_LENGTH, tag.getDouble("taOut")));
        deafWhy = tag.getInt("taDeaf");
        stowed = tag.getBoolean("taStow");
        redstone = tag.getBoolean("taRs");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeExtra(tag);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        crumbs.clear();
        clientCrumbs.clear();
    }

    List<Vec3> clientPoints(double tt) {
        Vec3 h = head();
        TowedArray.crumb(clientCrumbs, h, Balance.TOWED_LENGTH + 8.0);
        float o = outShown(tt);
        return TowedArray.points(clientCrumbs, h, aft(), o, Balance.TOWED_SINK * o / Balance.TOWED_LENGTH);
    }
}
