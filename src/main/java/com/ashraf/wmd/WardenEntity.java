package com.ashraf.wmd;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

public class WardenEntity extends Mob {

    public static final int DORMANT = 0, KLAXON = 1, HATCH = 2, RISING = 3, DOOR = 4, ACTIVE = 5, DYING = 6;
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> LEGS = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> SHIELD = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SHIELD_PCT = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> RAIL = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> AIM = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> POD = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CIWS_HOT = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MORTAR_N = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EYE = SynchedEntityData.defineId(WardenEntity.class, EntityDataSerializers.INT);

    static final int LEASH = 20;

    final WardenPart body, core, bay;
    final WardenPart[] legs = new WardenPart[4];
    final WardenPart[] parts;
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.wmd.warden"), BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.NOTCHED_10);

    float hp = Balance.WARDEN_HP, maxHp = Balance.WARDEN_HP;
    boolean scaled;
    int modeTicks, lonely;

    public float walkDist, walkDistO;

    public float gaitAmt, gaitAmtO;
    private double gaitX = Double.NaN, gaitZ;
    public int clientModeTicks;
    private int lastMode = -1;

    int ciwsBurst, ciwsWait = 40, rocketsLeft, rocketWait = 80, mortarWait = 120;
    float ciwsAcc;
    Vec3 rocketAim = Vec3.ZERO;
    final java.util.List<WardenAttacks.Shell> shells = new java.util.ArrayList<>();

    boolean ownFire;

    final java.util.List<WardenOverdrive.Drone> drones = new java.util.ArrayList<>();
    int nextDrone, droneWait = 60, empWait = 200, slamWait = 120, slamT = -1, napalmWait = 160, napalmLeft;
    Vec3 slamFrom = Vec3.ZERO, slamTo = Vec3.ZERO, napalmFrom = Vec3.ZERO, napalmDir = new Vec3(0.0, 0.0, 1.0), empAt = Vec3.ZERO;
    long empUntil;

    float shieldHp, shieldMax, railCoreDmg;
    int shieldDownT, enrageLeft, railWait = 200, railT = -1;
    Vec3 railAim;

    int ciwsSpin, podT, eyeT = -1, eyeWait = 140;

    int hugT, slamCrouch;
    Vec3 eyeAim;

    public float barrel, barrelO, barrelSpeed, ringAng, ringAngO;
    public Vec3 clientAim, clientAimO, clientLockEye, clientLockRail;
    public int clientMortarSeen = -1, clientMortarKick = 99, clientPodPrev;

    public final float[] clientLegDrop = new float[4], clientLegDropO = new float[4];

    int cineKind = -1;
    long cineStart, cineUntil;
    public int clientStageTicks;
    private int lastStage = -1;

    boolean cine() { return level().getGameTime() < cineUntil; }

    private Vec3 hitFrom;

    int nodesSeen = -1;
    final float[] legArmor = {Balance.WARDEN_LEG_ARMOR, Balance.WARDEN_LEG_ARMOR, Balance.WARDEN_LEG_ARMOR, Balance.WARDEN_LEG_ARMOR};

    public WardenEntity(EntityType<? extends WardenEntity> type, Level level) {
        super(type, level);
        body = new WardenPart(this, "body", 7.0F, 5.0F);
        core = new WardenPart(this, "core", 3.0F, 3.0F);
        bay = new WardenPart(this, "bay", 3.0F, 2.0F);
        for (int i = 0; i < 4; i++) legs[i] = new WardenPart(this, "leg" + i, 2.6F, 11.0F);
        parts = new WardenPart[] {body, core, bay, legs[0], legs[1], legs[2], legs[3]};
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        setNoGravity(true);
        setPersistenceRequired();
        noCulling = true;
        xpReward = 0;
        bar.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1_000.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(MODE, DORMANT);
        b.define(STAGE, 1);
        b.define(LEGS, 0);
        b.define(SHIELD, 0);
        b.define(SHIELD_PCT, 0);
        b.define(RAIL, 0);
        b.define(AIM, -1);
        b.define(POD, 0);
        b.define(CIWS_HOT, 0);
        b.define(MORTAR_N, 0);
        b.define(EYE, 0);
    }

    public int mode() { return entityData.get(MODE); }
    public int stage() { return entityData.get(STAGE); }
    public int brokenLegs() { return entityData.get(LEGS); }
    public boolean legBroken(int i) { return (brokenLegs() & (1 << i)) != 0; }
    public int shield() { return entityData.get(SHIELD); }
    public int shieldPct() { return entityData.get(SHIELD_PCT); }
    public int rail() { return entityData.get(RAIL); }
    void setShield(int s) { entityData.set(SHIELD, s); }
    void setShieldPct(int p) { entityData.set(SHIELD_PCT, Math.max(0, Math.min(100, p))); }
    void setRail(int t) { entityData.set(RAIL, t); }
    public int aimId() { return entityData.get(AIM); }
    public int pod() { return entityData.get(POD); }
    public boolean ciwsHot() { return entityData.get(CIWS_HOT) != 0; }
    public int mortarShots() { return entityData.get(MORTAR_N); }
    public int eye() { return entityData.get(EYE); }
    void setAim(int id) { if (aimId() != id) entityData.set(AIM, id); }
    void setPod(int t) { if (pod() != t) entityData.set(POD, t); }
    void setCiwsHot(boolean h) { if (ciwsHot() != h) entityData.set(CIWS_HOT, h ? 1 : 0); }
    void shotMortar() { entityData.set(MORTAR_N, mortarShots() + 1); }
    void setEye(int t) { if (eye() != t) entityData.set(EYE, t); }

    public Entity aimTarget() { int id = aimId(); return id < 0 ? null : level().getEntity(id); }

    void barName(Component c) { bar.setName(c); }
    java.util.Collection<ServerPlayer> barPlayers() { return bar.getPlayers(); }

    void music(String what) {
        if (!(level() instanceof ServerLevel sl)) return;
        java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
        for (ServerPlayer p : bar.getPlayers()) ids.add(p.getUUID());
        WardenMusic.send(sl, blockPosition(), bar.getPlayers(), what);
        MusicTriggersHook.boss(sl, what, blockPosition(), ids);
    }

    Vec3 local(double lx, double ly, double lz) {
        if (ly > 3.0) ly -= sag(lx, lz);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        return new Vec3(getX() + rx * lx + fx * lz, getY() + ly, getZ() + rz * lx + fz * lz);
    }

    static final double[] LEG_X = {-7.2, 7.2, -7.2, 7.2}, LEG_Z = {6.7, 6.7, -6.7, -6.7};

    static final float GAIT_CYCLE = 8.0F, GAIT_STRIDE = 3.0F, GAIT_LIFT = 3.0F, GAIT_SWING = 0.25F;
    static final float[] GAIT_PHASE = {0.5F, 0.0F, 0.75F, 0.25F};

    static float gaitQ(float walk, int i) {
        float q = (walk / GAIT_CYCLE + GAIT_PHASE[i]) % 1.0F;
        return q < 0.0F ? q + 1.0F : q;
    }

    static float gaitOff(float q) {
        float st = 1.0F - GAIT_SWING;
        if (q < st) return GAIT_STRIDE - 2.0F * GAIT_STRIDE * q / st;
        float s = (q - st) / GAIT_SWING;
        s = s * s * (3.0F - 2.0F * s);
        return -GAIT_STRIDE + 2.0F * GAIT_STRIDE * s;
    }

    static float gaitLift(float q) {
        float st = 1.0F - GAIT_SWING;
        if (q < st) return 0.0F;
        float s = (q - st) / GAIT_SWING;
        return Mth.sin(Mth.PI * (float) Math.sqrt(s)) * GAIT_LIFT;
    }

    float legDrop(int i) {
        if (level().isClientSide) return clientLegDrop[i];
        return legBroken(i) ? (float) Balance.WARDEN_LEG_DROP : 0.0F;
    }

    double sag(double lx, double lz) {
        float d0 = legDrop(0), d1 = legDrop(1), d2 = legDrop(2), d3 = legDrop(3);
        if (d0 + d1 + d2 + d3 <= 0.0F) return 0.0;
        double avg = (d0 + d1 + d2 + d3) / 4.0, side = ((d1 + d3) - (d0 + d2)) / 4.0, fwd = ((d0 + d1) - (d2 + d3)) / 4.0;
        return avg + side * lx / LEG_X[1] + fwd * lz / LEG_Z[0];
    }

    static final double RAIL_MX = 0.0, RAIL_MY = 15.1, RAIL_MZ = 0.9;
    Vec3 railMuzzle() { return local(RAIL_MX, RAIL_MY, RAIL_MZ); }

    Vec3 forward() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    void setMode(int m) {
        entityData.set(MODE, m);
        modeTicks = 0;
        bar.setVisible(m == ACTIVE || m == DYING);
    }

    private void setStage(int s) {
        entityData.set(STAGE, s);
        if (s < 3) bar.setName(Component.translatable("entity.wmd.warden"));
        bar.setColor(s == 1 ? BossEvent.BossBarColor.BLUE : s == 2 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
    }

    @Override public boolean isMultipartEntity() { return true; }
    @Override public PartEntity<?>[] getParts() { return parts; }

    @Override
    public void setId(int id) {
        super.setId(id);
        if (parts != null) for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }

    private void placeParts() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double sink = mode() == DYING ? Math.min(1.0, modeTicks / 60.0) * 6.0 : 0.0;
        place(body, fx, fz, rx, rz, 0.0, 8.0 - sink - sag(0.0, 0.0), 0.0);
        place(core, fx, fz, rx, rz, 0.0, 7.1 - sink - sag(0.0, 3.6), 3.6);
        place(bay, fx, fz, rx, rz, 0.0, 12.4 - sink - sag(0.0, -3.6), -3.6);
        for (int i = 0; i < 4; i++) place(legs[i], fx, fz, rx, rz, LEG_X[i], 0.0, LEG_Z[i]);
    }

    private void place(WardenPart p, double fx, double fz, double rx, double rz, double lx, double ly, double lz) {
        double x = getX() + rx * lx + fx * lz, y = getY() + ly, z = getZ() + rz * lx + fz * lz;
        p.xo = p.getX(); p.yo = p.getY(); p.zo = p.getZ();
        p.xOld = p.xo; p.yOld = p.yo; p.zOld = p.zo;
        p.setPos(x, y, z);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return hurtPart(body, source, amount); }

    void blast(DamageSource src, Vec3 pos, double reach, double lethal, double dmgMul) {
        if (ownFire || level().isClientSide || WardenFight.WARDEN_OWNER.equals(ShipStrike.shooter())) return;
        WardenPart best = null;
        double bd = Double.MAX_VALUE;
        for (WardenPart p : parts) {
            double q = p.getBoundingBox().distanceToSqr(pos);
            if (q < bd) { bd = q; best = p; }
        }
        double d = Math.sqrt(bd);
        if (best == null || d > reach) return;

        if (level() instanceof ServerLevel sl && !WardenFight.inside(WardenState.get(sl.getServer()), pos.x, pos.y, pos.z, stage() >= 3) && !openLine(sl, pos, best.getBoundingBox().getCenter())) return;
        double k = d < lethal ? 1.0 : 1.0 - (d - lethal) / Math.max(1.0E-6, reach - lethal);
        double size = Mth.clamp(reach / Balance.WARDEN_BLAST_SIZE, 1.0, Balance.WARDEN_BLAST_SIZE_MAX);
        hitFrom = pos;
        try { hurtPart(best, src, (float) ((60.0 * k * k + 4.0) * dmgMul * size)); } finally { hitFrom = null; }
    }

    private boolean openLine(ServerLevel sl, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1.0) return true;
        Vec3 a = from.add(dir.normalize().scale(0.6));
        var hit = SableBridge.clipWorld(sl, new net.minecraft.world.level.ClipContext(a, to, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this), null);
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 9.0;
    }

    boolean hurtPart(WardenPart part, DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (source.is(DamageTypes.GENERIC_KILL)) { hp = 0.0F; startDying(); return true; }
        if (mode() != ACTIVE || cine()) return false;
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)) return false;
        float mul = zone(part);
        if (source.is(DamageTypeTags.IS_FIRE)) mul *= Balance.WARDEN_FIRE_MUL;
        float dmg = Math.min(hitFrom != null ? Balance.WARDEN_BLAST_CAP : Balance.WARDEN_HIT_CAP, amount * mul);
        if (dmg <= 0.0F) return false;
        if (stage() >= 3) {
            Vec3 from = hitFrom != null ? hitFrom : source.getSourcePosition() != null ? source.getSourcePosition() : source.getEntity() != null ? source.getEntity().position() : null;

            if (!WardenMeltdown.underDome(this, source.getEntity()) && WardenMeltdown.absorb(this, (ServerLevel) level(), from, dmg)) { lonely = 0; return true; }
            if (railT >= 0 && part == core) railCoreDmg += dmg;
        }
        hp = Math.max(0.0F, hp - dmg);
        lonely = 0;
        for (int i = 0; i < 4; i++) if (part == legs[i] && !legBroken(i) && (legArmor[i] -= dmg) <= 0.0F) breakLeg(i);
        level().broadcastDamageEvent(this, source);
        bar.setProgress(hp / maxHp);
        int st = hp <= maxHp * Balance.WARDEN_STAGE3 ? 3 : hp <= maxHp * Balance.WARDEN_STAGE2 ? 2 : 1;
        if (st > stage()) stageUp(st);
        if (hp <= 0.0F) startDying();
        return true;
    }

    float zone(WardenPart part) {
        if (part == core) return stage() >= 3 ? Balance.WARDEN_CORE_MUL : Balance.WARDEN_SHUT_MUL;
        if (part == bay) return stage() == 2 ? Balance.WARDEN_BAY_MUL : Balance.WARDEN_SHUT_MUL;
        for (int i = 0; i < 4; i++)
            if (part == legs[i]) return legBroken(i) ? Balance.WARDEN_LEG_MUL : Balance.WARDEN_VENT_MUL;
        return 1.0F;
    }

    private void stageUp(int st) {
        int was = stage();
        setStage(st);
        ServerLevel sl = (ServerLevel) level();
        WardenState d = WardenState.get(sl.getServer());
        if (was < 2 && st == 2) WardenCine.play(this, sl, d, WardenCine.OVERDRIVE, Balance.WARDEN_CINE_OVERDRIVE);
        if (was < 2 && st >= 3) WardenOverdrive.blowPlates(this, sl);
        if (was < 3 && st >= 3) {
            WardenMeltdown.begin(this, sl, d);
            WardenCine.play(this, sl, d, WardenCine.MELTDOWN, Balance.WARDEN_CINE_MELTDOWN);
        }
        WardenFight.sound(sl, getX(), getY() + 10.0, getZ(), WmdSounds.SIREN.get(), Balance.SND_ALARM, 1.0F);
        for (ServerPlayer p : bar.getPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.stage" + st), true);
        music("boss_stage_" + st);
    }

    void toStage(int st) {
        hp = st >= 3 ? maxHp * Balance.WARDEN_STAGE3 - 1.0F : st == 2 ? maxHp * Balance.WARDEN_STAGE2 - 1.0F : maxHp;
        if (st == 1) setStage(1); else if (st > stage()) stageUp(st); else setStage(st);
        bar.setProgress(hp / maxHp);
    }

    private void breakLeg(int i) {
        entityData.set(LEGS, brokenLegs() | (1 << i));
        ServerLevel sl = (ServerLevel) level();
        Vec3 at = legs[i].position().add(0.0, 6.0, 0.0);
        sl.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 12, 0.8, 1.5, 0.8, 0.1);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.8, 1.5, 0.8, 0.02);
        WardenFight.sound(sl, at.x, at.y, at.z, WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.8F);
        for (ServerPlayer p : bar.getPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.leg"), true);
    }

    private void startDying() {
        if (mode() == DYING) return;
        setMode(DYING);
        for (ServerPlayer adv : bar.getPlayers()) WmdTrigger.fire(adv, "warden_kill");
        bar.setName(Component.translatable("entity.wmd.warden"));
        setPod(0); setCiwsHot(false); setEye(0); setAim(-1);
        if (level() instanceof ServerLevel sl) WardenCine.play(this, sl, WardenState.get(sl.getServer()), WardenCine.DEFEAT, Balance.WARDEN_DYING_TICKS);
        bar.setProgress(0.0F);
    }

    @Override public boolean isPushable() { return false; }
    @Override public void push(Entity e) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean canBeLeashed() { return false; }

    @Override public boolean isPickable() { return false; }
    @Override protected void pushEntities() { }
    @Override public void travel(Vec3 v) { }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bar.removePlayer(p);
    }

    @Override
    public void tick() {
        super.tick();
        placeParts();
        if (level().isClientSide) {
            walkDistO = walkDist;
            if (Double.isNaN(gaitX)) { gaitX = getX(); gaitZ = getZ(); }
            double mv = Math.sqrt((getX() - gaitX) * (getX() - gaitX) + (getZ() - gaitZ) * (getZ() - gaitZ));
            gaitX = getX(); gaitZ = getZ();
            if (mv > 2.0) mv = 0.0;
            walkDist += (float) mv;
            gaitAmtO = gaitAmt;
            gaitAmt += Mth.clamp((mv > 0.004 && mode() == ACTIVE ? 1.0F : 0.0F) - gaitAmt, -0.08F, 0.08F);
            int m = mode();
            clientModeTicks = m == lastMode ? clientModeTicks + 1 : 0;
            lastMode = m;
            int st = stage();
            clientStageTicks = lastStage < 0 ? 10_000 : st == lastStage ? clientStageTicks + 1 : 0;
            lastStage = st;
            clientMotion();
        }
    }

    private void clientMotion() {
        barrelO = barrel;
        float want = ciwsHot() ? 48.0F : mode() == ACTIVE ? 3.0F : 0.0F;
        barrelSpeed += (want - barrelSpeed) * 0.12F;
        barrel = (barrel + barrelSpeed) % 3600.0F;
        ringAngO = ringAng;
        float rc = Math.min(1.0F, rail() / (float) Balance.WARDEN_RAIL_CHARGE);
        if (rail() > 0) ringAng = (ringAng + 3.0F + 45.0F * rc * rc) % 3600.0F;
        Entity tg = aimTarget();
        clientAimO = clientAim;
        if (tg != null) {
            Vec3 at = tg.getEyePosition();
            clientAim = clientAim == null ? at : clientAim.lerp(at, 0.25);
            if (clientAimO == null) clientAimO = clientAim;
        }
        int n = mortarShots();
        clientMortarKick = clientMortarSeen >= 0 && n != clientMortarSeen ? 0 : Math.min(99, clientMortarKick + 1);
        clientMortarSeen = n;
        for (int i = 0; i < 4; i++)
            if (gaitAmt > 0.3F && !legBroken(i) && gaitQ(walkDist, i) < gaitQ(walkDistO, i)) stomp(i);
        for (int i = 0; i < 4; i++) {
            clientLegDropO[i] = clientLegDrop[i];
            float drop = legBroken(i) ? (float) Balance.WARDEN_LEG_DROP : 0.0F;
            clientLegDrop[i] += Mth.clamp(drop - clientLegDrop[i], -0.15F, 0.15F);
        }
        if (eye() >= Balance.WARDEN_EYE_CHARGE - Balance.WARDEN_EYE_LOCK) { if (clientLockEye == null && tg != null) clientLockEye = tg.getEyePosition(); }
        else clientLockEye = null;
        if (rail() >= Balance.WARDEN_RAIL_CHARGE - Balance.WARDEN_RAIL_LOCK) { if (clientLockRail == null && tg != null) clientLockRail = tg.getEyePosition(); }
        else clientLockRail = null;
    }

    private void stomp(int i) {
        Vec3 f = local(Math.signum(LEG_X[i]) * 7.6, 0.1, Math.signum(LEG_Z[i]) * 7.1 + GAIT_STRIDE);
        level().playLocalSound(f.x, f.y, f.z, WmdSounds.BLAST_WHUMP.get(), net.minecraft.sounds.SoundSource.HOSTILE, Balance.SND_RELEASE,
                0.42F + random.nextFloat() * 0.08F, false);
        BlockState under = level().getBlockState(BlockPos.containing(f.x, f.y - 0.5, f.z));
        if (under.isAir()) return;
        net.minecraft.core.particles.BlockParticleOption dust = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, under);
        for (int k = 0; k < 14; k++) {
            double a = k / 14.0 * Mth.TWO_PI, r = 1.2 + random.nextDouble() * 0.6;
            level().addParticle(dust, f.x + Math.cos(a) * r, f.y + 0.1, f.z + Math.sin(a) * r, Math.cos(a) * 0.25, 0.15, Math.sin(a) * 0.25);
        }
    }

    @Override
    protected void customServerAiStep() {
        ServerLevel sl = (ServerLevel) level();
        WardenState d = WardenState.get(sl.getServer());
        if (sl != sl.getServer().overworld() || !d.placed) { discard(); return; }
        modeTicks++;
        switch (mode()) {
            case KLAXON -> klaxon(sl, d);
            case HATCH -> hatch(sl, d);
            case RISING -> rise(sl, d);
            case DOOR -> door(sl, d);
            case ACTIVE -> { if (cine()) cineStep(sl, d); else fight(sl, d); }
            case DYING -> dying(sl, d);
            default -> { }
        }
    }

    private BlockPos at(WardenState d, int x, int h, int z) { return new BlockPos(d.ox + x, d.oy + h, d.oz + z); }

    private void cineStep(ServerLevel sl, WardenState d) {
        long k = sl.getGameTime() - cineStart;
        if (k <= 1) { eyeT = -1; setEye(0); podT = 0; setPod(0); rocketsLeft = 0; ciwsBurst = 0; ciwsSpin = 0; setCiwsHot(false); }
        if (cineKind == WardenCine.OVERDRIVE) {
            long j = k - Balance.WARDEN_PLATE_FIRST;
            if (j >= 0 && j % Balance.WARDEN_PLATE_GAP == 0 && j / Balance.WARDEN_PLATE_GAP < 8) WardenOverdrive.blowPlate(this, sl, (int) (j / Balance.WARDEN_PLATE_GAP));
        } else if (cineKind == WardenCine.MELTDOWN) {
            if (k == Balance.WARDEN_CINE_ROOF) WardenFight.openRoof(sl, d);
            if (k == Balance.WARDEN_CINE_DOME) WardenMeltdown.raise(this, sl);
        }
    }

    private void klaxon(ServerLevel sl, WardenState d) {
        if (modeTicks == 1) WardenFight.sound(sl, d.ox + 0.5, d.oy + WardenBunker.F + 12, d.oz + 0.5, WmdSounds.SIREN.get(), Balance.SND_ALARM, 1.0F);
        if (modeTicks >= Balance.WARDEN_KLAXON_TICKS) setMode(HATCH);
    }

    private void hatch(ServerLevel sl, WardenState d) {
        if (modeTicks == 1) {
            WardenFight.sound(sl, d.ox + 0.5, d.oy + WardenBunker.F, d.oz + 0.5, WmdSounds.HD_START.get(), Balance.SND_MECH, 0.7F);
            WardenCine.play(this, sl, d, WardenCine.INTRO, Balance.WARDEN_HATCH_STEP * (WardenBunker.PIT + 2) + Balance.WARDEN_LIFT_STEP * WardenBunker.PIT_D);
        }
        if (modeTicks % Balance.WARDEN_HATCH_STEP != 0) return;
        int s = modeTicks / Balance.WARDEN_HATCH_STEP - 1;
        if (s > WardenBunker.PIT) { setMode(RISING); return; }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = -WardenBunker.PIT; z <= WardenBunker.PIT; z++) {
            WardenFight.write(sl, at(d, s, WardenBunker.F, z), air);
            WardenFight.write(sl, at(d, -s, WardenBunker.F, z), air);
        }
    }

    private void rise(ServerLevel sl, WardenState d) {
        int step = modeTicks / Balance.WARDEN_LIFT_STEP;
        int from = WardenBunker.F - WardenBunker.PIT_D;
        double frac = (modeTicks % Balance.WARDEN_LIFT_STEP) / (double) Balance.WARDEN_LIFT_STEP;
        setPos(d.ox + 0.5, d.oy + from + 1 + Math.min(WardenBunker.PIT_D, step + frac), d.oz + 0.5);
        if (modeTicks % 40 == 1) WardenFight.sound(sl, getX(), getY(), getZ(), WmdSounds.LIFT_LOOP.get(), Balance.SND_GIANT, 0.6F);
        if (modeTicks % Balance.WARDEN_LIFT_STEP != 0) return;
        if (step > WardenBunker.PIT_D) return;
        BlockState plate = Wmd.ARMOUR_PLATE.get().defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int h = from + step;
        for (int x = -WardenBunker.PIT; x <= WardenBunker.PIT; x++)
            for (int z = -WardenBunker.PIT; z <= WardenBunker.PIT; z++) {
                WardenFight.write(sl, at(d, x, h, z), plate);
                if (step > 0) WardenFight.write(sl, at(d, x, h - 1, z), air);
            }
        if (step == WardenBunker.PIT_D) {
            WardenFight.sound(sl, getX(), getY(), getZ(), WmdSounds.LIFT_CLUNK.get(), Balance.SND_MECH, 0.6F);
            setPos(d.ox + 0.5, d.oy + WardenBunker.F + 1, d.oz + 0.5);
            setMode(DOOR);
        }
    }

    private void door(ServerLevel sl, WardenState d) {
        float yaw = Mth.approachDegrees(getYRot(), 180.0F, Balance.WARDEN_TURN * 2.0F);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        double dx = d.ox + 0.5, dy = d.oy + WardenBunker.F + 8, dz = d.oz + WardenBunker.DOOR_Z;
        HangarDoorBlockEntity real = WardenFight.door(sl, d);
        if (real != null && AirGate.on()) {
            real.bunkerLock(WardenFight.WARDEN_OWNER);
            if (modeTicks == 1) real.command(true);
            real.bunkerPower();
            if (real.isOpen() || modeTicks > 600) { engage(sl); setMode(ACTIVE); }
            return;
        }
        if (modeTicks == 1) WardenFight.sound(sl, dx, dy, dz, WmdSounds.HD_START.get(), Balance.SND_MECH, 0.8F);
        if (modeTicks % 40 == 1) WardenFight.sound(sl, dx, dy, dz, WmdSounds.HD_LOOP_HEAVY.get(), Balance.SND_HEAVY, 0.8F);
        if (modeTicks % Balance.WARDEN_DOOR_STEP != 0) return;
        int s = modeTicks / Balance.WARDEN_DOOR_STEP - 1;
        if (s > WardenBunker.DOOR) {
            WardenFight.sound(sl, dx, dy, dz, WmdSounds.HD_STOP_TOP.get(), Balance.SND_MECH, 0.8F);
            engage(sl);
            setMode(ACTIVE);
            return;
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int h = WardenBunker.F + 1; h <= WardenBunker.F + WardenBunker.DOOR_H; h++) {
            WardenFight.write(sl, at(d, s, h, WardenBunker.DOOR_Z), air);
            WardenFight.write(sl, at(d, -s, h, WardenBunker.DOOR_Z), air);
        }
    }

    private void engage(ServerLevel sl) {
        int n = 0;
        double r2 = Balance.WARDEN_SCALE_R * Balance.WARDEN_SCALE_R;
        for (ServerPlayer p : sl.players()) if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(this) < r2) n++;
        float mul = Math.min(Balance.WARDEN_SCALE_MAX, 1.0F + Balance.WARDEN_SCALE_PER_PLAYER * Math.max(0, n - 1));
        maxHp = Balance.WARDEN_HP * mul;
        hp = maxHp;
        java.util.Arrays.fill(legArmor, Balance.WARDEN_LEG_ARMOR * mul);
        nodesSeen = -1;
        entityData.set(LEGS, 0);
        scaled = true;
        setStage(1);
        bar.setProgress(1.0F);
        music("boss_stage_1");
    }

    private void fight(ServerLevel sl, WardenState d) {
        Player t = target(sl, d);
        setAim(t == null ? -1 : t.getId());
        if (tickCount % Balance.WARDEN_MUSIC_REFIRE == 0) music("boss_stage_" + stage());
        if (t != null && slamT < 0) {
            double hx = t.getX() - getX(), hz = t.getZ() - getZ();
            if (hx * hx + hz * hz < Balance.WARDEN_HUG_R * Balance.WARDEN_HUG_R) { if (++hugT >= Balance.WARDEN_HUG_TICKS) { hugT = 0; WardenOverdrive.hop(this, sl); } }
            else hugT = Math.max(0, hugT - 2);
        }
        WardenAttacks.tick(this, sl, d, t);
        if (stage() >= 2) WardenOverdrive.tick(this, sl, d, t);
        if (stage() >= 3) WardenMeltdown.tick(this, sl, d, t);
        if (slamT >= 0) { WardenOverdrive.slamStep(this, sl); return; }
        if (t == null) {
            if (++lonely >= Balance.WARDEN_RESET_TICKS && scaled) {
                scaled = false;
                hp = maxHp;
                eyeT = -1; setEye(0);
                setStage(1);
                bar.setProgress(1.0F);
            }
            return;
        }
        lonely = 0;
        if (!scaled) engage(sl);
        double dx = t.getX() - getX(), dz = t.getZ() - getZ();
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(getYRot(), want, Balance.WARDEN_TURN);
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist <= Balance.WARDEN_KEEP || Math.abs(Mth.wrapDegrees(want - yaw)) > 35.0F) return;
        double sp = stage() == 1 ? Balance.WARDEN_SPEED_1 : stage() == 2 ? Balance.WARDEN_SPEED_2 : Balance.WARDEN_SPEED_3;
        sp *= Math.max(0.2, 1.0 - Balance.WARDEN_LEG_SLOW * Integer.bitCount(brokenLegs()));
        float r = yaw * Mth.DEG_TO_RAD;
        Vec3 step = new Vec3(-Mth.sin(r) * sp, 0.0, Mth.cos(r) * sp);
        double nx = getX() + step.x - d.ox - 0.5, nz = getZ() + step.z - d.oz - 0.5;
        if (Math.abs(nx) > LEASH || Math.abs(nz) > LEASH) return;
        move(MoverType.SELF, step);
    }

    private Player target(ServerLevel sl, WardenState d) {
        Player best = null;
        double bd = Balance.WARDEN_AGGRO * Balance.WARDEN_AGGRO;
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            if (!WardenFight.inside(d, p.getX(), p.getY(), p.getZ(), stage() >= 3)) continue;
            double q = p.distanceToSqr(this);
            if (q < bd) { bd = q; best = p; }
        }
        return best;
    }

    private void dying(ServerLevel sl, WardenState d) {
        if (modeTicks % 10 == 0) {
            double a = random.nextDouble() * Math.PI * 2.0;
            sl.sendParticles(ParticleTypes.EXPLOSION, getX() + Math.cos(a) * 3.0, getY() + 6.0 + random.nextDouble() * 6.0, getZ() + Math.sin(a) * 3.0, 3, 1.0, 1.0, 1.0, 0.0);
            WardenFight.sound(sl, getX(), getY() + 8.0, getZ(), WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.7F + random.nextFloat() * 0.3F);
        }
        if (modeTicks < Balance.WARDEN_DYING_TICKS) return;
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 4.0, getZ(), 2, 2.0, 2.0, 2.0, 0.0);
        WardenFight.sound(sl, getX(), getY() + 4.0, getZ(), WmdSounds.BLAST_WHUMP.get(), Balance.SND_RELEASE, 0.5F);
        for (ServerPlayer p : bar.getPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.down"), false);
        BlockPos crate = WardenFight.placeCrate(sl, position());
        if (crate != null) for (ServerPlayer p : bar.getPlayers()) p.displayClientMessage(Component.translatable("message.wmd.warden.crate", crate.getX(), crate.getY(), crate.getZ()), false);
        music("boss_defeated");
        MusicTriggersHook.flag(sl, "wmd:boss_fight", false, 0L);
        d.beaten(System.currentTimeMillis());
        Wmd.LOGGER.info("[warden] beaten at {} {} {}", (int) getX(), (int) getY(), (int) getZ());
        bar.removeAllPlayers();
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("WMode", mode());
        t.putInt("WStage", stage());
        t.putInt("WModeTicks", modeTicks);
        t.putFloat("WHp", hp);
        t.putFloat("WMaxHp", maxHp);
        t.putBoolean("WScaled", scaled);
        t.putInt("WLegs", brokenLegs());
        t.putInt("WShield", shield()); t.putFloat("WShieldHp", shieldHp); t.putFloat("WShieldMax", shieldMax); t.putInt("WShieldDown", shieldDownT);
        t.putInt("WEnrage", enrageLeft);
        for (int i = 0; i < 4; i++) t.putFloat("WLegArmor" + i, legArmor[i]);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        entityData.set(MODE, Mth.clamp(t.getInt("WMode"), DORMANT, DYING));
        setStage(Mth.clamp(t.getInt("WStage"), 1, 3));
        modeTicks = t.getInt("WModeTicks");
        maxHp = t.contains("WMaxHp") ? Math.max(1.0F, t.getFloat("WMaxHp")) : Balance.WARDEN_HP;
        hp = t.contains("WHp") ? Mth.clamp(t.getFloat("WHp"), 0.0F, maxHp) : maxHp;
        scaled = t.getBoolean("WScaled");
        entityData.set(LEGS, t.getInt("WLegs") & 15);
        setShield(t.getInt("WShield")); shieldHp = t.getFloat("WShieldHp"); shieldMax = t.getFloat("WShieldMax"); shieldDownT = t.getInt("WShieldDown");
        setShieldPct((int) (shieldHp * 100.0F / Math.max(1.0F, shieldMax)));
        enrageLeft = t.getInt("WEnrage");
        for (int i = 0; i < 4; i++) if (t.contains("WLegArmor" + i)) legArmor[i] = t.getFloat("WLegArmor" + i);
        bar.setVisible(mode() == ACTIVE || mode() == DYING);
        bar.setProgress(hp / maxHp);
    }
}
