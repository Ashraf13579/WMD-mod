package com.ashraf.wmd;

import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class StormGeneratorBlockEntity extends ProcessUnitBlockEntity implements Owned, CcAnimated, ScreenCommandTarget {

    public static final int X0 = -2, X1 = 2, Y0 = 0, Y1 = 15, Z0 = -2, Z1 = 2;
    private static final PortMap PORT_MAP = new PortMap(X0, X1, Z1 - Z0 + 1).fromZ(Z0).power(0, 0, Z1, Direction.SOUTH, false);
    public static final int P_IDLE = 0, P_BUILD = 1, P_STORM = 2, P_END = 3, P_COOL = 4;
    public static final int ST_NO_OWNER = 0, ST_NO_TARGET = 1, ST_RANGE = 2, ST_CHARGING = 3, ST_READY = 4, ST_BUILD = 5, ST_STORM = 6, ST_END = 7, ST_COOLING = 8;
    public static final int CMD_TARGET = 0, CMD_DESIG = 1, CMD_RADIUS = 2, CMD_FIRE = 3, CMD_STOP = 4;

    static final double SLIDE = 32.0, TILT_Y = 134.0, TIP_L = 17.6, JACK = 7.0, PET_R = 9.0, PET_Y = 128.6, LEAN = 4.0, RAM_FX = 11.4, RAM_FY = 122.6,
            OPEN = -72.0, TILT = -30.0, LOUV = -55.0, CLAMP = 1.4;
    static final float RX = 1.0F, RY = -1.0F, RZ = -1.0F, XS = -1.0F;
    private static final int[][] CLAMP_DIR = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } };

    private final OwnerData ownerData = new OwnerData();

    private int tx, tz, step = 1, phase = P_IDLE, stormId, status = ST_NO_OWNER, chargePm;
    private boolean hasTarget, sig;
    private long phaseAt, coolUntil;
    private float yaw;

    private double lastTT = Double.NaN;
    private float fanAng, reelAng;

    public StormGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(StormBlocks.STORM_GENERATOR_BE.get(), pos, state, 0, Balance.STGEN_BUFFER_FE, Balance.STGEN_RECEIVE_FE);
    }

    @Override public UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }

    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int stormStatus() { return status; }
    public int phase() { return phase; }
    public boolean hasTarget() { return hasTarget; }
    public int targetX() { return tx; }
    public int targetZ() { return tz; }
    public int radius() { return Balance.STGEN_RADII[Mth.clamp(step, 0, Balance.STGEN_RADII.length - 1)]; }
    public int radiusStep() { return step; }

    public int chargePermille() { return level != null && level.isClientSide ? chargePm : (int) Math.min(1000L, energy().getEnergyStored() * 1000L / Balance.STGEN_BUFFER_FE); }
    public int stormId() { return stormId; }

    public int secondsLeft() {
        if (level == null) return 0;
        long now = level.getGameTime(), end = switch (phase) {
            case P_BUILD -> phaseAt + StormField.BUILD;
            case P_STORM -> phaseAt + Balance.STGEN_STORM;
            case P_END, P_COOL -> coolUntil;
            default -> now;
        };
        return (int) Math.max(0L, (end - now + 19) / 20);
    }

    public int distance() {
        Vec3 c = world(0.0, 0.0, 0.0);
        return (int) Math.round(Math.hypot(tx + 0.5 - c.x, tz + 0.5 - c.z));
    }

    public int bearing() {
        Vec3 c = world(0.0, 0.0, 0.0);
        return (int) Math.round(Mth.wrapDegrees(Math.toDegrees(Math.atan2(tx + 0.5 - c.x, -(tz + 0.5 - c.z)))) + 360.0) % 360;
    }

    private boolean inRange() { int d = distance(); return d >= Balance.STGEN_MIN_RANGE && d <= Balance.STGEN_MAX_RANGE; }

    @Override
    public void command(ServerPlayer sp, int cmd, int x, int z) {
        if (!(level instanceof ServerLevel)) return;
        if (!Access.mayUse(this, sp)) { Access.deny(this, sp); return; }
        switch (cmd) {
            case CMD_TARGET -> setTarget(x, z);
            case CMD_DESIG -> {
                CcTarget t = CcTarget.of(getOwner() != null ? getOwner() : sp.getUUID());
                if (t == null) { sp.displayClientMessage(Component.translatable("message.wmd.storm.no_designated").withStyle(ChatFormatting.RED), true); return; }
                setTarget(t.x(), t.z());
            }
            case CMD_RADIUS -> { step = Mth.clamp(x, 0, Balance.STGEN_RADII.length - 1); sync(); }
            case CMD_FIRE -> {
                String why = fire(sp.getGameProfile().getName());
                if (why != null) sp.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.RED), true);
            }
            case CMD_STOP -> abort();
            default -> { }
        }
    }

    void setTarget(int x, int z) {
        tx = Mth.clamp(x, -29_999_000, 29_999_000);
        tz = Mth.clamp(z, -29_999_000, 29_999_000);
        hasTarget = true;
        refresh();
        sync();
    }

    public int order(int x, int z, String by) {
        setTarget(x, z);
        fire(by);
        return status;
    }

    public boolean remoteFire() { return fire("") == null; }

    String fire(String by) {
        if (!(level instanceof ServerLevel sl)) return "gui.wmd.storm.st." + status;
        refresh();
        if (status != ST_READY) return "gui.wmd.storm.st." + status;
        if (!useExtraEnergy(Balance.STGEN_BUFFER_FE)) return "gui.wmd.storm.st." + ST_CHARGING;
        Vec3 piv = world(0.0, TILT_Y + 3 * SLIDE + JACK, 0.0);
        Vec3 d = new Vec3(tx + 0.5 - piv.x, 0.0, tz + 0.5 - piv.z).normalize();
        double l = TIP_L / 16.0;
        Vec3 tip = piv.add(d.scale(l * Math.sin(Math.toRadians(-TILT)))).add(0.0, l * Math.cos(Math.toRadians(-TILT)), 0.0);
        yaw = bearingModel(new Vec3(tx + 0.5, piv.y, tz + 0.5));
        stormId = StormField.start(sl, worldPosition, getOwner(), tx, tz, radius(), tip);
        phase = P_BUILD;
        phaseAt = sl.getGameTime();
        HeatLog.fired(sl, worldPosition, getOwner());
        refresh();
        sync();
        return null;
    }

    void abort() {
        if (!(level instanceof ServerLevel sl) || (phase != P_BUILD && phase != P_STORM)) return;
        StormField.cut(sl, stormId);
        toEnd(sl.getGameTime());
    }

    private void toEnd(long now) {
        phase = P_END;
        phaseAt = now;
        coolUntil = now + Balance.STGEN_COOLDOWN;
        refresh();
        sync();
    }

    @Override
    public void empDrain() {
        super.empDrain();
        abort();
    }

    @Override
    protected void jammed(ServerLevel level) { abort(); }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        long now = sl.getGameTime();
        boolean pulse = sl.hasNeighborSignal(worldPosition);
        if (pulse && !sig && phase == P_IDLE) { refresh(); if (status == ST_READY) fire(""); }
        sig = pulse;
        switch (phase) {
            case P_BUILD -> {
                if (!StormField.live(sl, stormId)) { toEnd(now); return true; }
                if (now - phaseAt >= StormField.BUILD) { phase = P_STORM; phaseAt = now; refresh(); sync(); }
                return true;
            }
            case P_STORM -> {
                if (!StormField.live(sl, stormId) || now - phaseAt >= Balance.STGEN_STORM) toEnd(now);
                return true;
            }
            case P_END -> {
                if (now - phaseAt >= Balance.STGEN_FOLD + Balance.STGEN_LOWER) { phase = P_COOL; phaseAt = now; refresh(); sync(); }
                return true;
            }
            case P_COOL -> {
                if (now >= coolUntil) { phase = P_IDLE; phaseAt = now; refresh(); sync(); }
                else if (now % 20 == 0) refresh();
                return false;
            }
            default -> {
                if (now % 10 == 0) refresh();
                return false;
            }
        }
    }

    private void refresh() {
        int st = getOwner() == null ? ST_NO_OWNER
                : phase == P_BUILD ? ST_BUILD : phase == P_STORM ? ST_STORM : phase == P_END ? ST_END : phase == P_COOL ? ST_COOLING
                : !hasTarget ? ST_NO_TARGET : !inRange() ? ST_RANGE : energy().getEnergyStored() < Balance.STGEN_BUFFER_FE ? ST_CHARGING : ST_READY;
        int pm = chargePermille();
        if (st != status || Math.abs(pm - chargePm) >= 10 || (pm == 1000) != (chargePm == 1000)) { status = st; chargePm = pm; sync(); }
    }

    private Vec3 world(double x, double y, double z) {
        double[] w = worldPoint(x / 16.0, y / 16.0, z / 16.0);
        return new Vec3(w[0], w[1], w[2]);
    }

    private float bearingModel(Vec3 p) {
        Vec3 c = world(0.0, 0.0, 0.0), e = world(16.0, 0.0, 0.0), n = world(0.0, 0.0, -16.0);
        Vec3 ex = e.subtract(c).normalize(), nz = n.subtract(c).normalize(), d = p.subtract(c);
        return (float) Math.toDegrees(Math.atan2(d.dot(nz), d.dot(ex)));
    }

    public IEnergyStorage energyPort(BlockPos part, Direction side) { return at(part, side, 0, 0, Z1, Direction.SOUTH) ? energy() : null; }

    static void clientTickStorm(Level level, BlockPos pos, BlockState state, StormGeneratorBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, state, be);
        if (be.phase == P_COOL && level.getGameTime() - be.coolUntil + Balance.STGEN_COOLDOWN < Balance.STGEN_STEAM + Balance.STGEN_FOLD + Balance.STGEN_LOWER)
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(45.0 + 90.0 * k);
                ClientStorm.steam(pos.asLong() * 4 + k, be.world(Math.cos(a) * 49.0, 34.0, -Math.sin(a) * 49.0), level.getGameTime());
            }
    }

    private static double ease(double t) { t = Mth.clamp(t, 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

    private static float rad(double deg) { return (float) Math.toRadians(deg); }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        double dt = Double.isNaN(lastTT) ? 0.0 : Mth.clamp(tt - lastTT, 0.0, 5.0);
        lastTT = tt;
        double age = tt - phaseAt;
        int sec = Balance.STGEN_RAISE / 3, o0 = Balance.STGEN_SPIN + Balance.STGEN_RAISE;
        double fan = 0, louv = 0, e1 = 0, e2 = 0, e3 = 0, clamp = 0, pet = 0, jack = 0, aim = 0, reel = 0;
        boolean eglow = false, pglow = false, live = getOwner() != null;
        switch (phase) {
            case P_BUILD -> {
                fan = ease(age / Balance.STGEN_SPIN); louv = fan;
                e1 = ease((age - Balance.STGEN_SPIN) / sec); e2 = ease((age - Balance.STGEN_SPIN - sec) / sec); e3 = ease((age - Balance.STGEN_SPIN - 2 * sec) / sec);
                clamp = ease((age - Balance.STGEN_SPIN - sec + 8) / 8.0);
                reel = age >= Balance.STGEN_SPIN && age < o0 ? 1.0 : 0.0;
                pet = ease((age - o0) / Balance.STGEN_OPEN); jack = ease((age - o0 - 10) / 30.0); aim = ease((age - o0 - 20) / 20.0);
                eglow = age >= o0 + 20; pglow = age >= o0 + Balance.STGEN_OPEN;
            }
            case P_STORM -> {
                fan = louv = e1 = e2 = e3 = clamp = pet = jack = aim = 1.0;
                eglow = true;
                pglow = tt - ClientStorm.lastStrike(stormId) < 10.0;
            }
            case P_END -> {
                int f = Balance.STGEN_FOLD;
                louv = 1.0; fan = 1.0 - 0.7 * ease(age / (f + Balance.STGEN_LOWER));
                pet = 1.0 - ease(age / f); jack = pet; aim = 1.0 - ease(age / (f * 0.6));
                e3 = 1.0 - ease((age - f) / sec); e2 = 1.0 - ease((age - f - sec) / sec); e1 = 1.0 - ease((age - f - 2 * sec) / sec);
                clamp = age < f ? 1.0 : 0.0;
                reel = age >= f ? 1.0 : 0.0;
            }
            case P_COOL -> {
                fan = 0.3 * (1.0 - ease(age / 100.0));
                louv = 1.0 - ease((age - Balance.STGEN_STEAM) / 40.0);
            }
            default -> { }
        }
        if (ClientHemp.darkAt(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5)) { eglow = pglow = false; live = false; }
        fanAng = (float) ((fanAng + fan * 40.0 * dt) % 360.0);
        reelAng = (float) ((reelAng + reel * 18.0 * dt) % 360.0);
        final float fa = rad(fanAng), ra = rad(reelAng);
        model.getBone("fan0").ifPresent(b -> b.setRotZ(RZ * fa));
        model.getBone("fan2").ifPresent(b -> b.setRotZ(-RZ * fa));
        model.getBone("fan1").ifPresent(b -> b.setRotX(RX * fa));
        model.getBone("fan3").ifPresent(b -> b.setRotX(-RX * fa));
        final float lv = RZ * rad(LOUV * louv);
        for (int k = 0; k < 4; k++) for (int j = 0; j < 4; j++) model.getBone("louv" + k + "_" + j).ifPresent(b -> b.setRotZ(lv));
        model.getBone("reel0").ifPresent(b -> b.setRotX(RX * ra));
        model.getBone("reel1").ifPresent(b -> b.setRotX(RX * ra));
        for (int k = 0; k < 4; k++) {
            final float cx = XS * (float) (CLAMP_DIR[k][0] * CLAMP * clamp), cz = (float) (CLAMP_DIR[k][1] * CLAMP * clamp);
            model.getBone("clamp" + k).ifPresent(b -> { b.setPosX(cx); b.setPosZ(cz); });
        }
        final float y1 = (float) (SLIDE * e1), y2 = (float) (SLIDE * e2), y3 = (float) (SLIDE * e3);
        model.getBone("s1").ifPresent(b -> b.setPosY(y1));
        model.getBone("s2").ifPresent(b -> b.setPosY(y2));
        model.getBone("s3").ifPresent(b -> b.setPosY(y3));
        final boolean g1 = e1 > 0.98, g2 = e2 > 0.98, g3 = e3 > 0.98;
        model.getBone("sg1").ifPresent(b -> b.setHidden(!g1));
        model.getBone("sg2").ifPresent(b -> b.setHidden(!g2));
        model.getBone("sg3").ifPresent(b -> b.setHidden(!g3));
        double th = OPEN * pet;
        final float pr = RZ * rad(th);
        double[] ram = ramPose(th);
        final float rr = RZ * (float) ram[0], rx = XS * (float) ram[1], ry = (float) ram[2];
        final boolean pg = pglow;
        for (int k = 0; k < 6; k++) {
            model.getBone("petal" + k).ifPresent(b -> b.setRotZ(pr));
            model.getBone("ram" + k).ifPresent(b -> b.setRotZ(rr));
            model.getBone("rod" + k).ifPresent(b -> { b.setPosX(rx); b.setPosY(ry); });
            model.getBone("pglow" + k).ifPresent(b -> b.setHidden(!pg));
        }
        final float jy = (float) (JACK * jack), ya = RY * rad(yaw * aim), ti = RZ * rad(TILT * aim);
        model.getBone("ejack").ifPresent(b -> b.setPosY(jy));
        model.getBone("eyaw").ifPresent(b -> b.setRotY(ya));
        model.getBone("etilt").ifPresent(b -> b.setRotZ(ti));
        final boolean eg = eglow, up = phase == P_BUILD || phase == P_STORM || phase == P_END;
        model.getBone("eglow").ifPresent(b -> b.setHidden(!eg));
        model.getBone("baseglow").ifPresent(b -> b.setHidden(!up));
        final boolean lamp = live && (up || status == ST_READY), beacon = live && up && ((long) tt / 10L) % 2L == 0L;
        model.getBone("lamp").ifPresent(b -> b.setHidden(!lamp));
        model.getBone("beacon").ifPresent(b -> b.setHidden(!beacon));
    }

    static double[] ramPose(double th) {
        double l = Math.toRadians(LEAN);
        double lx = PET_R + Math.cos(l) * 3.4 - Math.sin(l) * 6.0, ly = PET_Y + Math.sin(l) * 3.4 + Math.cos(l) * 6.0;
        double c = Math.cos(Math.toRadians(th)), s = Math.sin(Math.toRadians(th));
        double nx = PET_R + (lx - PET_R) * c - (ly - PET_Y) * s, ny = PET_Y + (lx - PET_R) * s + (ly - PET_Y) * c;
        double a0 = Math.atan2(ly - RAM_FY, lx - RAM_FX), a1 = Math.atan2(ny - RAM_FY, nx - RAM_FX);
        double dl = Math.hypot(nx - RAM_FX, ny - RAM_FY) - Math.hypot(lx - RAM_FX, ly - RAM_FY);
        return new double[] { a1 - a0, dl * Math.cos(a0), dl * Math.sin(a0) };
    }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int rateMbPerTick() { return 0; }
    @Override public PortMap guiPortMap() { return PORT_MAP; }
    @Override protected float progress01() { return phase == P_IDLE ? chargePermille() / 1000.0F : -1.0F; }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.storm_generator", Balance.STGEN_MIN_RANGE, String.format("%,d", Balance.STGEN_MAX_RANGE),
                        Balance.STGEN_STORM / 20, Balance.STGEN_STRIKE_TICKS / 20.0F),
                Component.translatable("gui.wmd.info.storm_generator_fe", Balance.STGEN_BUFFER_FE / 1_000_000, Balance.STGEN_COOLDOWN / 1200));
    }

    @Override
    protected String detail() { return "phase " + phase + ", status " + status + ", target " + (hasTarget ? tx + " " + tz : "-") + ", r " + radius() + ", storm " + stormId; }

    @Override protected SoundEvent runSound() { return WmdSounds.STORM_DRONE_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 3, 0); }
    @Override protected float soundVolume() { return Balance.SND_GIANT; }
    @Override protected float soundPitch() { return phase == P_STORM ? 1.0F : 0.9F; }
    @Override protected SoundEvent overlaySound() { return phase == P_STORM ? WmdSounds.WI_WIND_LOOP.get() : null; }
    @Override protected float overlayVolume() { return Balance.SND_GIANT; }
    @Override protected float overlayPitch() { return 0.7F; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ownerData.write(tag);
        put(tag);
        tag.putBoolean("sgSig", sig);
    }

    private void put(CompoundTag tag) {
        tag.putInt("sgX", tx); tag.putInt("sgZ", tz); tag.putBoolean("sgT", hasTarget); tag.putInt("sgR", step);
        tag.putInt("sgP", phase); tag.putLong("sgAt", phaseAt); tag.putLong("sgCool", coolUntil); tag.putInt("sgId", stormId);
        tag.putInt("sgSt", status); tag.putFloat("sgYaw", yaw); tag.putInt("sgPm", chargePm);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerData.read(tag);
        tx = tag.getInt("sgX"); tz = tag.getInt("sgZ"); hasTarget = tag.getBoolean("sgT"); step = Mth.clamp(tag.contains("sgR") ? tag.getInt("sgR") : 1, 0, 2);
        phase = Mth.clamp(tag.getInt("sgP"), P_IDLE, P_COOL); phaseAt = tag.getLong("sgAt"); coolUntil = tag.getLong("sgCool"); stormId = tag.getInt("sgId");
        status = tag.getInt("sgSt"); yaw = tag.getFloat("sgYaw"); sig = tag.getBoolean("sgSig"); chargePm = tag.getInt("sgPm");

    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        ownerData.write(tag);
        put(tag);
        return tag;
    }
}
