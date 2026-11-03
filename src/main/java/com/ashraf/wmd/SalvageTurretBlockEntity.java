package com.ashraf.wmd;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SalvageTurretBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final float SCALE = 0.6F;
    static final double GUN_H = 56.0 / 16.0 * SCALE;
    static final double MUZZLE = 42.0 / 16.0 * SCALE;

    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "salvage_turret"));

    static final int IDLE = 0, WINDUP = 1, FIRE = 2;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private BlockPos generator;
    private boolean powered = true;
    private int state = IDLE;
    private float az, el;
    private long firingUntil = -1;

    private UUID target;
    private long stateSince, lastSeen, lastHurt;
    private float pending;

    float shownAz, shownEl, spin, spinSpeed, recoil, heat, podAngle;
    int prevState;
    Object loop;

    public SalvageTurretBlockEntity(BlockPos pos, BlockState state) { super(Wmd.SALVAGE_TURRET_BE.get(), pos, state); }

    public void setGenerator(BlockPos p) { generator = p == null ? null : p.immutable(); setChanged(); }

    public boolean powered() { return powered; }
    public int state() { return state; }
    public boolean firing(long now) { return now < firingUntil; }

    Vec3 gunPos() { return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + GUN_H, worldPosition.getZ() + 0.5); }

    public static void serverTick(Level level, BlockPos pos, BlockState st, SalvageTurretBlockEntity be) {
        if (level instanceof ServerLevel sl) be.serverStep(sl, level.getGameTime());
    }

    private long nextGenScan;

    private boolean generatorAlive(ServerLevel sl) {
        if (generator == null) {
            long now = sl.getGameTime();
            if (now < nextGenScan) return false;
            nextGenScan = now + 200 + Math.floorMod(worldPosition.hashCode(), 40);
            if (sl.getNearestPlayer(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, Balance.SALVAGE_TURRET_RANGE + 16.0, false) == null)
                return false;
            for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-8, -4, -8), worldPosition.offset(8, 4, 8)))
                if (sl.isLoaded(p) && sl.getBlockState(p).is(Wmd.SALVAGE_GENERATOR.get())) { generator = p.immutable(); setChanged(); return true; }
            return false;
        }
        if (!sl.isLoaded(generator)) return powered;
        return sl.getBlockState(generator).is(Wmd.SALVAGE_GENERATOR.get());
    }

    public String debugLine(ServerLevel sl, ServerPlayer p) {
        Vec3 gun = gunPos();
        double dist = aimPoint(p).distanceTo(gun);
        boolean genOk = generatorAlive(sl);
        return "turret " + worldPosition.toShortString() + ": powered=" + powered + " generator=" + (generator == null ? "none" : generator.toShortString()) + " alive=" + genOk
                + " emp=" + EmpField.jammed(sl, worldPosition) + " difficulty=" + sl.getDifficulty().getKey()
                + " | you: valid=" + valid(p) + " (creative=" + p.isCreative() + ") dist=" + String.format("%.1f", dist) + "/" + Balance.SALVAGE_TURRET_RANGE
                + " sees=" + sees(sl, gun, p) + " | state=" + (state == IDLE ? "IDLE" : state == WINDUP ? "WINDUP" : "FIRE") + " target=" + (target != null)
                + " az=" + String.format("%.0f", az) + " el=" + String.format("%.0f", el);
    }

    private static boolean valid(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    private static boolean clearLine(ServerLevel sl, Vec3 from, Vec3 to) {
        Boolean blocked = net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, sl, (level, p) -> {
            if (!level.isLoaded(p)) return null;
            BlockState s = level.getBlockState(p);
            var shape = s.getCollisionShape(level, p, net.minecraft.world.phys.shapes.CollisionContext.empty());
            return level.clipWithInteractionOverride(from, to, p, shape, s) != null ? Boolean.TRUE : null;
        }, level -> Boolean.FALSE);
        return !blocked;
    }

    private static Vec3 aimPoint(ServerPlayer p) { return p.getEyePosition().subtract(0.0, 0.55, 0.0); }

    private boolean sees(ServerLevel sl, Vec3 gun, ServerPlayer p) {
        Vec3 aim = aimPoint(p);
        if (aim.distanceTo(gun) > Balance.SALVAGE_TURRET_RANGE) return false;
        Vec3 dir = aim.subtract(gun).normalize();
        return clearLine(sl, gun.add(dir.scale(1.0)), aim);
    }

    private void setState(int s, long now) { state = s; stateSince = now; }

    private void serverStep(ServerLevel sl, long now) {
        boolean changed = false;
        if (now % 20 == 0) {
            boolean p = generatorAlive(sl);
            if (p != powered) {
                powered = p; changed = true;
                if (!p) {
                    sl.playSound(null, worldPosition, WmdSounds.CIWS_END.get(), SoundSource.BLOCKS, 3.0F, 0.7F);
                    setState(IDLE, now); target = null;
                }
            }
        }
        boolean jammed = EmpField.jammed(sl, worldPosition);
        if (!powered || jammed || sl.getDifficulty() == Difficulty.PEACEFUL) {
            if (state != IDLE) { setState(IDLE, now); target = null; changed = true; }
            if (el > -12.0F) { el = Math.max(-12.0F, el - 1.0F); changed = true; }
            if (changed && now % 2 == 0) sync();
            return;
        }
        Vec3 gun = gunPos();
        ServerPlayer tp = target == null ? null : (sl.getPlayerByUUID(target) instanceof ServerPlayer sp ? sp : null);
        if (tp != null && (!valid(tp) || tp.level() != sl || aimPoint(tp).distanceTo(gun) > Balance.SALVAGE_TURRET_RANGE + 4)) { tp = null; target = null; }

        boolean see = false;
        if (now % 5 == 0 || tp != null) {
            if (tp != null) see = sees(sl, gun, tp);
            if (tp == null || (!see && now % 5 == 0)) {
                ServerPlayer best = null;
                double bd = Balance.SALVAGE_TURRET_RANGE;
                for (ServerPlayer p : sl.players()) {
                    if (!valid(p)) continue;
                    double d = aimPoint(p).distanceTo(gun);
                    if (d < bd && sees(sl, gun, p)) { bd = d; best = p; }
                }
                if (best != null && best != tp) { tp = best; target = best.getUUID(); see = true; }
            }
        }
        if (see) lastSeen = now;
        if (tp == null || now - lastSeen > 40) {
            if (state != IDLE) { setState(IDLE, now); changed = true; }
            target = null;
            az = (float) LauncherGeometry.wrap(az + 0.6F);
            el += (float) Mth.clamp(4.0 - el, -0.5, 0.5);
            if (now % 4 == 0) changed = true;
            if (changed && now % 2 == 0) sync();
            return;
        }

        Vec3 aim = aimPoint(tp);
        Vec3 d = aim.subtract(gun);
        double tAz = LauncherGeometry.wrap(LauncherGeometry.azimuthFor(Direction.NORTH, d.x, d.z) + 180.0);
        double tEl = Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -20.0, 80.0);
        double dAz = LauncherGeometry.wrap(tAz - az), dEl = tEl - el;
        az = (float) LauncherGeometry.wrap(az + Mth.clamp(dAz, -Balance.SALVAGE_TURRET_TURN, Balance.SALVAGE_TURRET_TURN));
        el += (float) Mth.clamp(dEl, -Balance.SALVAGE_TURRET_TURN, Balance.SALVAGE_TURRET_TURN);
        changed = true;
        if (state == IDLE) {
            setState(WINDUP, now);
            sl.playSound(null, worldPosition, WmdSounds.CIWS_START.get(), SoundSource.HOSTILE, 4.0F, 1.15F);
        }
        if (state == WINDUP) {
            if ((now - stateSince) % 6 == 0) sl.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 2.0F, 1.8F);
            if (now - stateSince >= Balance.SALVAGE_TURRET_WINDUP) setState(FIRE, now);
        }
        if (state == FIRE) {
            long phase = (now - stateSince) % (Balance.SALVAGE_TURRET_BURST + Balance.SALVAGE_TURRET_PAUSE);
            boolean aligned = Math.abs(LauncherGeometry.wrap(tAz - az)) < 6.0 && Math.abs(tEl - el) < 6.0;
            if (phase < Balance.SALVAGE_TURRET_BURST && see && aligned && now % 2 == 0) {
                double dist = d.length();
                double q = Math.pow(Math.max(0.0, 1.0 - dist / Balance.SALVAGE_TURRET_RANGE), 1.2);
                double chance = Balance.SALVAGE_TURRET_HIT_FAR + (Balance.SALVAGE_TURRET_HIT_CLOSE - Balance.SALVAGE_TURRET_HIT_FAR) * q;
                if (tp.isCrouching()) chance *= 0.7;
                boolean hit = sl.random.nextDouble() < chance;
                if (hit) pending += Balance.SALVAGE_TURRET_DAMAGE;
                Vec3 dir = d.normalize();
                Vec3 muzzle = gun.add(dir.scale(MUZZLE));
                Vec3 to = hit ? aim : aim.add((sl.random.nextDouble() - 0.5) * 2.5, (sl.random.nextDouble() - 0.5) * 1.5, (sl.random.nextDouble() - 0.5) * 2.5);
                PacketDistributor.sendToPlayersNear(sl, null, muzzle.x, muzzle.y, muzzle.z, 160, new CiwsFirePayload(worldPosition, muzzle.x, muzzle.y, muzzle.z, to.x, to.y, to.z, hit));
                firingUntil = now + 6;
            }
            if (pending > 0 && now - lastHurt >= 10) {
                DamageSource src = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE));
                tp.hurt(src, pending);
                pending = 0;
                lastHurt = now;
            }
        }
        if (changed && (now % 2 == 0 || now < firingUntil)) sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState st, SalvageTurretBlockEntity be) {
        long now = level.getGameTime();
        be.recoil *= 0.6F;
        be.heat = Math.max(0.0F, be.heat - 0.006F);
        boolean firing = be.firing(now);
        if (firing) { be.recoil = 1.6F; be.heat = Math.min(1.0F, be.heat + 0.02F); }
        be.podAngle += firing ? 0.25F : (be.powered ? 0.03F : 0.0F);
        be.shownAz += (float) (LauncherGeometry.wrap(be.az - be.shownAz) * 0.55);
        be.shownEl += (be.el - be.shownEl) * 0.55F;
        float want = firing ? 38.0F : be.state == FIRE ? 26.0F : be.state == WINDUP ? 22.0F : 0.0F;
        be.spinSpeed += (want - be.spinSpeed) * 0.12F;
        be.spin = (be.spin + be.spinSpeed) % 360.0F;
        if (be.state == FIRE && be.prevState != FIRE) ClientSalvageTurret.start(be);
        else if (be.state != FIRE && be.prevState == FIRE) ClientSalvageTurret.stop(be);
        be.prevState = be.state;
        if (be.heat > 0.03F) {
            Vec3 g = be.gunPos();
            VeilBridge.machineGlow(0x5A17_0000_0000_0002L ^ pos.asLong(), g.x, g.y, g.z, 1.0F, 0.38F, 0.1F, 3.0F + 2.0F * be.heat, 0.6F * be.heat, now);
        }
    }

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
        if (generator != null) tag.put("generator", NbtUtils.writeBlockPos(generator));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
        generator = NbtUtils.readBlockPos(tag, "generator").orElse(null);
    }

    private void write(CompoundTag tag) {
        tag.putBoolean("powered", powered); tag.putInt("state", state);
        tag.putFloat("az", az); tag.putFloat("el", el); tag.putLong("firingUntil", firingUntil);
    }

    private void read(CompoundTag tag) {
        powered = !tag.contains("powered") || tag.getBoolean("powered");
        state = tag.getInt("state");
        az = tag.getFloat("az"); el = tag.getFloat("el");
        firingUntil = tag.contains("firingUntil") ? tag.getLong("firingUntil") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        write(tag);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        read(pkt.getTag());
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
