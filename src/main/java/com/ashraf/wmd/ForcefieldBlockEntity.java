package com.ashraf.wmd;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public class ForcefieldBlockEntity extends ProcessUnitBlockEntity implements ModeMachine, Owned, CcAnimated {

    public static final int X0 = -3, X1 = 3, Y0 = 0, Y1 = 8, Z0 = -3, Z1 = 3;

    public static final int OFF = 0, WAIT = 1, BOOT = 2, UP = 3, CRACK = 4, DOWN = 5;

    public static final int RADII = (Balance.FF_R_MAX - Balance.FF_R_MIN) / Balance.FF_R_STEP + 1;
    public static final int MODE_ON = RADII, MODE_OFF = RADII + 1;

    public static final int B_NONE = 0, B_PIERCED = 1, B_OVERLOAD = 2, B_EMPTY = 3, B_EMP = 4, B_MOVED = 5;

    static final java.util.Map<java.util.UUID, Long> COOL_UNTIL = Statics.server(new java.util.HashMap<>());

    int coolLeft() {
        return state == CRACK ? Balance.FF_CRACK_TICKS - stateTicks + Balance.FF_DOWN_TICKS : state == DOWN ? Math.max(0, Balance.FF_DOWN_TICKS - stateTicks) : 0;
    }

    void leaveCooldown(long now) {
        java.util.UUID o = getOwner();
        if (o != null && coolLeft() > 0) COOL_UNTIL.merge(o, now + coolLeft(), Math::max);
    }

    private final OwnerData ownerData = new OwnerData();
    @Override public java.util.UUID getOwner() { return ownerData.id(); }
    @Override public String getOwnerName() { return ownerData.name(); }
    @Override public SecurityData security() { return ownerData.security(); }
    @Override
    public void claim(Player player) {
        ownerData.claim(player);
        Long until = level == null || level.isClientSide ? null : COOL_UNTIL.remove(player.getUUID());
        if (until != null && until > level.getGameTime()) {
            state = DOWN;
            broke = B_MOVED;
            stateTicks = (int) Math.max(0, Balance.FF_DOWN_TICKS - (until - level.getGameTime()));
            sync(true);
        }
        setChanged();
    }

    private int state = OFF, stateTicks, broke;
    private boolean on;
    private int radius = Balance.FF_R_DEFAULT;
    private int stored;
    private int chargedThisTick;
    private long chargeTick = -1;
    private int lastSentHash;

    private final IEnergyStorage socket = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int n, boolean sim) {
            if (level == null) return 0;
            long t = level.getGameTime();
            if (t != chargeTick) { chargeTick = t; chargedThisTick = 0; }
            int take = Math.max(0, Math.min(n, Math.min(Balance.FF_BUFFER_FE - stored, Balance.FF_CHARGE_IN - chargedThisTick)));
            if (!sim && take > 0) { stored += take; chargedThisTick += take; setChanged(); }
            return take;
        }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.FF_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return stored < Balance.FF_BUFFER_FE; }
    };

    private final IEnergyStorage gauge = new IEnergyStorage() {
        @Override public int receiveEnergy(int n, boolean sim) { return 0; }
        @Override public int extractEnergy(int n, boolean sim) { return 0; }
        @Override public int getEnergyStored() { return stored; }
        @Override public int getMaxEnergyStored() { return Balance.FF_BUFFER_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return false; }
    };

    public ForcefieldBlockEntity(BlockPos pos, BlockState state) {
        super(Wmd.FORCEFIELD_BE.get(), pos, state, 0);
    }

    public static void serverTickField(Level level, BlockPos pos, BlockState bs, ForcefieldBlockEntity be) {
        if (level instanceof ServerLevel sl && EmpField.jammed(sl, pos) && (be.state == BOOT || be.state == UP))
            be.breakField(sl, new Vec3(0.0, 1.0, 0.0), B_EMP);
        ProcessUnitBlockEntity.serverTick(level, pos, bs, be);
        be.sync(false);
    }

    @Override
    protected boolean work() {
        if (!(level instanceof ServerLevel sl)) return false;
        stateTicks++;
        switch (state) {
            case WAIT -> { if (pct() >= Balance.FF_BOOT_PCT) go(BOOT); }
            case BOOT, UP -> {
                int hold = holdFe();
                if (stored < hold) { breakField(sl, new Vec3(0.0, 1.0, 0.0), B_EMPTY); break; }
                stored -= hold;
                if (state == UP) wall(sl);
                if (state == BOOT && stateTicks >= Balance.FF_BOOT_TICKS) {
                    go(UP);
                    Wmd.LOGGER.info("[forcefield] {} UP: radius {}, buffer {} FE", worldPosition.toShortString(), radius, stored);
                }
            }
            case CRACK -> {
                if (stateTicks >= Balance.FF_CRACK_TICKS) {
                    Forcefield.send(sl, this, Forcefield.FX_SHATTER, new Vec3(0.0, 1.0, 0.0), 1.0F);
                    go(DOWN);
                    playOnce(sl, WmdSounds.FF_DOWN.get(), 3.0F, 1.0F);
                }
            }
            case DOWN -> {
                if (stateTicks >= Balance.FF_DOWN_TICKS && pct() >= Balance.FF_REBOOT_PCT) go(on ? BOOT : OFF);
                else if (stateTicks >= Balance.FF_DOWN_TICKS && !on) go(OFF);
            }
            default -> { }
        }
        if (state == BOOT) {
            if (stateTicks == 1) playOnce(level, WmdSounds.FF_BOOT.get(), 4.0F, 1.0F);
            for (int k = 0; k < 4; k++) if (stateTicks == 40 + 25 * k) playOnce(level, WmdSounds.FF_PYLON.get(), 2.5F, 0.9F + 0.07F * k);
            if (stateTicks == 140) playOnce(level, WmdSounds.FF_DOME_UP.get(), 8.0F, 1.0F);
        }
        return state == BOOT || state == UP;
    }

    private void go(int s) {
        state = s;
        stateTicks = 0;
        setChanged();
        sync(true);
    }

    private final java.util.Map<java.util.UUID, Boolean> side = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastTouch = new java.util.HashMap<>();

    boolean mayPass(Player p) { return Forcefield.mayWalkThrough(this, p); }

    static final double WALL_TRACK = 96.0;

    boolean wallInside(Vec3 at) {
        Vec3 d = at.subtract(centre());
        if (d.y < 0.0) d = new Vec3(d.x, 0.0, d.z);
        return d.length() < radius;
    }

    private void wall(ServerLevel sl) {
        Vec3 c = centre();
        double r = radius;
        java.util.Set<java.util.UUID> near = new java.util.HashSet<>();
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.5, 0.0);
            Vec3 d0 = at.subtract(c);

            boolean below = d0.y < 0.0;
            Vec3 d = below ? new Vec3(d0.x, 0.0, d0.z) : d0;
            double dist = d.length();

            if (dist > r + WALL_TRACK) continue;
            java.util.UUID id = p.getUUID();
            near.add(id);
            boolean in = dist < r;
            Boolean was = side.get(id);
            if (was == null || mayPass(p)) { side.put(id, in); continue; }
            boolean crossed = in != was, onShell = was ? dist > r - 1.0 : dist < r + 1.0;
            if (crossed || onShell) {
                Vec3 dir = dist < 1e-3 ? new Vec3(0.0, 1.0, 0.0) : d.scale(1.0 / dist);
                double keep = was ? r - 1.2 : r + 1.2;
                net.minecraft.world.entity.Entity e = p.getRootVehicle();
                Vec3 to = c.add(dir.scale(keep)).subtract(0.0, p.getBbHeight() * 0.5, 0.0);
                Vec3 shift = to.subtract(p.position());
                if (below) e.teleportTo(e.getX() + shift.x, e.getY(), e.getZ() + shift.z);
                else e.teleportTo(e.getX() + shift.x, Math.max(c.y, e.getY() + shift.y), e.getZ() + shift.z);
                e.setDeltaMovement(dir.scale(was ? -0.35 : 0.35).add(0.0, 0.1, 0.0));
                e.hurtMarked = true;
                long now = sl.getGameTime();
                if (now - lastTouch.getOrDefault(id, -100L) >= 10) {
                    lastTouch.put(id, now);
                    Forcefield.send(sl, this, Forcefield.FX_TOUCH, dir, 0.2F);
                }
            }
        }
        side.keySet().retainAll(near);
        lastTouch.keySet().retainAll(near);
    }

    public int holdFe() { return radius * radius / Balance.FF_HOLD_DIV; }

    public int pct() { return (int) (stored * 100L / Balance.FF_BUFFER_FE); }

    public Vec3 centre() {
        double[] w = worldPoint(0.0, 0.0, 0.0);
        return new Vec3(w[0], w[1], w[2]);
    }

    boolean protecting() { return state == UP; }

    boolean absorb(ServerLevel sl, Vec3 dir, int drain, boolean pierce) {
        if (pierce) { breakField(sl, dir, B_PIERCED); return false; }
        if (drain > stored) { breakField(sl, dir, B_OVERLOAD); return true; }
        stored -= drain;
        setChanged();
        sync(true);
        if (sl.getGameTime() - lastLogged >= 100L) { lastLogged = sl.getGameTime(); log(sl, "log.wmd.ff_hit", worldPosition.getX() + " " + worldPosition.getZ(), Integer.toString(pct())); }
        Forcefield.send(sl, this, Forcefield.FX_HIT, dir, Math.min(1.0F, drain / (Balance.FF_BUFFER_FE * 0.2F)));
        return true;
    }

    private long lastLogged = -1000L;

    private void log(ServerLevel sl, String key, String... args) {
        if (getOwner() != null) CcBattleLog.add(sl.getServer(), getOwner(), CcBattleLog.BDA, key, args);
    }

    void breakField(ServerLevel sl, Vec3 dir, int why) {
        if (state == CRACK || state == DOWN) return;
        broke = why;
        stored = 0;
        Wmd.LOGGER.info("[forcefield] {} BREAKS ({}), radius {}", worldPosition.toShortString(),
                why == B_PIERCED ? "railgun / antimatter" : why == B_OVERLOAD ? "overload" : why == B_EMPTY ? "out of power" : "EMP", radius);
        log(sl, "log.wmd.ff_down", worldPosition.getX() + " " + worldPosition.getZ(), why == B_PIERCED ? "railgun / antimatter" : why == B_OVERLOAD ? "overload" : why == B_EMPTY ? "out of power" : "EMP");
        Forcefield.send(sl, this, Forcefield.FX_CRACK, dir, 1.0F);
        go(CRACK);
    }

    @Override
    public void empDrain() {
        if (level instanceof ServerLevel sl && (state == BOOT || state == UP)) breakField(sl, new Vec3(0.0, 1.0, 0.0), B_EMP);
        stored = 0;
        setChanged();
    }

    static final java.util.Set<ForcefieldBlockEntity> LOADED = Statics.server(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    private void sync(boolean now) {
        if (level == null || level.isClientSide) return;
        int h = ((((state * 31 + (on ? 1 : 0)) * 31 + radius) * 31 + broke) * 31 + pct()) * 31 + (state == WAIT || state == DOWN ? stateTicks / 20 : 0);
        if (h == lastSentHash || (!now && level.getGameTime() % 10 != 0)) return;
        lastSentHash = h;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int mode() { return (radius - Balance.FF_R_MIN) / Balance.FF_R_STEP; }
    @Override public int modeCount() { return RADII + 2; }

    @Override
    public void setMode(int m) {
        if (m >= 0 && m < RADII) {
            if (state == BOOT || state == UP || state == CRACK) return;
            radius = Balance.FF_R_MIN + m * Balance.FF_R_STEP;
            setChanged(); sync(true);
        } else if (m == MODE_ON) {
            on = true;
            if (state == OFF) go(WAIT);
            else { setChanged(); sync(true); }
        } else if (m == MODE_OFF) {
            on = false;
            if (state == WAIT || state == BOOT || state == UP) { go(OFF); if (level != null) playOnce(level, WmdSounds.FF_OFF.get(), 3.0F, 1.0F); }
            else { setChanged(); sync(true); }
        }
    }

    @Override
    public void setModeClient(int m) {
        if (m >= 0 && m < RADII && state != BOOT && state != UP && state != CRACK) radius = Balance.FF_R_MIN + m * Balance.FF_R_STEP;
    }

    public int state() { return state; }
    public int stateTicks() { return stateTicks; }
    public boolean isOn() { return on; }
    public int radius() { return radius; }
    public int broke() { return broke; }
    public int storedShown() { return stored; }

    long clientT0 = Long.MIN_VALUE;
    private int clientState = -1;

    float gyroA0, gyroA1, gyroSpeed;

    public double shownTicks(double tt) {
        if (clientState != state || clientT0 == Long.MIN_VALUE) { clientState = state; clientT0 = (long) tt - stateTicks; }
        return tt - clientT0;
    }

    @Override
    public void ccAnimate(CcBones model, double tt, float partialTick) {
        double t = shownTicks(tt);
        int st = state;

        float y = switch (st) {
            case BOOT -> (float) (-6.0 + 6.0 * Mth.clamp(t / 40.0, 0.0, 1.0));
            case UP -> (float) (0.8 * Math.sin(tt * 0.05));
            case CRACK -> (float) (0.6 * Math.sin(tt * 2.3));
            default -> -6.0F;
        };
        model.getBone("ff_orb").ifPresent(b -> b.setPosY(y));

        for (int i = 0; i < 4; i++) {
            final int k = i;
            boolean lit = switch (st) {
                case BOOT -> t >= 40 + 25 * k;
                case UP -> !(pct() < Balance.FF_LOW_PCT && ((long) tt + k * 7) % 11 < 2);
                case WAIT -> pct() * 4 >= Balance.FF_BOOT_PCT * (k + 1);
                case CRACK -> ((long) tt + k) % 4 < 2;
                default -> false;
            };
            model.getBone("ff_py" + i).ifPresent(b -> b.setHidden(!lit));
        }
        boolean glow = st == UP || st == BOOT && t >= 140 || st == CRACK && ((long) tt) % 3 != 0;
        model.getBone("ff_glow").ifPresent(b -> b.setHidden(!glow));
        boolean arcs = (st == UP || st == BOOT && t >= 100) && Math.floorMod((long) tt * 7 + worldPosition.getX(), 13) < 9;
        model.getBone("ff_arcs").ifPresent(b -> b.setHidden(!arcs));
        float target = st == UP ? 0.09F : st == BOOT ? (float) (0.09 * Mth.clamp((t - 20) / 120.0, 0.0, 1.0)) : 0.0F;
        model.getBone("ff_gyro0").ifPresent(b -> b.setRotY(gyroA0));
        model.getBone("ff_gyro1").ifPresent(b -> b.setRotY(gyroA1));
        gyroTarget = target;
    }

    private float gyroTarget;

    public static void clientTickField(Level level, BlockPos pos, BlockState bs, ForcefieldBlockEntity be) {
        ProcessUnitBlockEntity.clientTick(level, pos, bs, be);
        be.gyroSpeed += (be.gyroTarget - be.gyroSpeed) * 0.03F;
        be.gyroA0 = (be.gyroA0 + be.gyroSpeed) % Mth.TWO_PI;
        be.gyroA1 = (be.gyroA1 - be.gyroSpeed * 1.45F) % Mth.TWO_PI;
        ClientForcefield.track(be);
    }

    @Override protected SoundEvent runSound() { return WmdSounds.FF_HUM_LOOP.get(); }
    @Override protected BlockPos soundLocal() { return new BlockPos(0, 6, 0); }
    @Override protected float soundVolume() { return Balance.SND_HEAVY; }
    @Override protected float soundPitch() { return 1.0F; }

    @Override protected List<FluidTank> tanks() { return List.of(); }
    @Override protected List<Fluid> tankFluids() { return List.of(); }
    @Override protected List<String> labelKeys() { return List.of(); }
    @Override protected int inputTanks() { return 0; }
    @Override protected int rateMbPerTick() { return 1; }
    @Override public IEnergyStorage guiEnergy() { return gauge; }
    @Override public int guiFeUse() { return state == BOOT || state == UP ? holdFe() : 0; }

    @Override
    public int guiStatus() {
        if (state == UP || state == BOOT) return 1;
        if (state == CRACK || state == DOWN) return 12;
        return 0;
    }

    @Override
    public List<Component> guiInfo() {
        return List.of(Component.translatable("gui.wmd.info.ff_buffer", String.format("%,d", Balance.FF_BUFFER_FE), String.format("%,d", Balance.FF_CHARGE_IN)),
                Component.translatable("gui.wmd.info.ff_hold", String.format("%,d", holdFe()), radius),
                Component.translatable("gui.wmd.info.ff_break"));
    }

    @Override
    protected String detail() {
        return "state " + state + " (" + stateTicks + " ticks), " + (on ? "ON" : "OFF") + ", radius " + radius + ", buffer " + stored + " FE (" + pct() + " %), hold " + holdFe() + " FE/t, broke " + broke;
    }

    @Override
    public IEnergyStorage energyPort(BlockPos part, Direction side) {
        return at(part, side, 3, 0, 0, Direction.EAST) || at(part, side, -3, 0, 0, Direction.WEST) ? socket : null;
    }

    private static final PortMap PORT_MAP = new PortMap(-3, 3, 7).power(3, 0, 0, Direction.EAST, false).power(-3, 0, 0, Direction.WEST, false);
    @Override public PortMap guiPortMap() { return PORT_MAP; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putState(tag);
        tag.putBoolean("ffsave", true);
        ownerData.write(tag);
    }

    private void putState(CompoundTag tag) {
        tag.putInt("ffstate", state);
        tag.putInt("ffticks", stateTicks);
        tag.putBoolean("ffon", on);
        tag.putInt("ffradius", radius);
        tag.putInt("ffbroke", broke);
        tag.putInt("ffstored", stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("ffstate")) {
            state = Mth.clamp(tag.getInt("ffstate"), OFF, DOWN);
            stateTicks = Math.max(0, tag.getInt("ffticks"));
            on = tag.getBoolean("ffon");
            radius = Mth.clamp(tag.getInt("ffradius"), Balance.FF_R_MIN, Balance.FF_R_MAX);
            broke = tag.getInt("ffbroke");
            stored = Mth.clamp(tag.getInt("ffstored"), 0, Balance.FF_BUFFER_FE);
        }
        if (tag.contains("ffsave")) ownerData.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        putState(tag);
        return tag;
    }
}
