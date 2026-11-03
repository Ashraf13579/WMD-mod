package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CiwsBlockEntity extends BlockEntity implements Owned, GeoBlockEntity, MachinePorts, EmpVulnerable, InterceptorSite {
    private final SecurityData security = new SecurityData();
    @Override public SecurityData security() { return security; }

    private static final double GUN_Y = 56.0, GUN_Z = 16.0, BULLET_SPEED = 38.0;

    private static class Buffer extends EnergyStorage {
        Buffer() { super(Balance.INT_BUFFER_FE, Balance.INT_RECEIVE_FE, 0); }
        void use(int n) { energy = Math.max(0, energy - n); }
        void set(int e) { energy = Mth.clamp(e, 0, capacity); }
    }

    public final ItemStackHandler magazine = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack s) { return s.getItem() instanceof CiwsAmmoItem; }
        @Override protected void onContentsChanged(int slot) { setChanged(); sync(); }
    };
    private final Buffer store = new Buffer();
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private int frequency = 1, tracked, link, rounds;
    private UUID owner;
    private String ownerName = "";
    private float az, el;
    private long firingUntil = -1;

    private long targetId = -1;
    private List<Long> lastIds = new ArrayList<>();
    private final java.util.Map<Long, Boolean> los = new java.util.HashMap<>();

    private boolean droneClear;
    private int droneLos = -1;

    private final java.util.Set<Long> triedShells = new java.util.HashSet<>();

    private final java.util.Set<Integer> triedBombs = new java.util.HashSet<>();
    private long nextShipBurst;
    private long nextBurst;

    private final java.util.Set<Long> swarmBlocked = new java.util.HashSet<>();

    float shownAz, shownEl, spin, spinSpeed, recoil, heat, beltPhase, podAngle;
    long clientTicks;

    public CiwsBlockEntity(BlockPos pos, BlockState state) { super(Wmd.CIWS_BE.get(), pos, state); }

    @Override public ItemStackHandler magazineHandler() { return magazine; }
    @Override public int slotCount() { return 1; }
    @Override public UUID getOwner() { return owner; }
    @Override public int getFrequency() { return frequency; }
    @Override public int energy() { return store.getEnergyStored(); }
    @Override public int cooldownTicks() { return 0; }
    @Override public int trackedCount() { return tracked; }
    @Override public int linkState() { return link; }

    @Override public int loaded() { return rounds + magazine.getStackInSlot(0).getCount() * Balance.CIWS_BOX; }
    public int rounds() { return rounds; }

    private boolean bigDrum;

    public int drumSize() { return Balance.CIWS_DRUM + (bigDrum ? Balance.CIWS_DRUM_EXTRA : 0); }

    boolean fitDrum(Player player) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof UpgradeItem u) || u.type != UpgradeItem.Type.CIWS_DRUM) return false;
        if (bigDrum) { player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.upgrade.max", 1), true); return true; }
        bigDrum = true;
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.wmd.upgrade.ciws_drum", drumSize()), true);
        sync();
        return true;
    }
    public boolean firing(long now) { return now < firingUntil; }

    @Override public void empDrain() { store.set(0); setChanged(); }

    List<Long> trackedIds() { return lastIds; }

    @Override public String getOwnerName() { return ownerName; }
    @Override public void claim(Player p) { owner = p.getUUID(); ownerName = p.getGameProfile().getName(); setChanged(); sync(); }

    @Override public void setFrequency(int f) { frequency = Mth.clamp(f, 1, 9999); setChanged(); sync(); }

    private Direction facing() { return getBlockState().getValue(MachineBlock.FACING); }

    private static boolean clearLine(ServerLevel sl, Vec3 from, Vec3 to) {
        Boolean blocked = net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, sl, (level, pos) -> {
            if (!level.isLoaded(pos)) return null;
            BlockState st = level.getBlockState(pos);
            var shape = st.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.empty());
            return level.clipWithInteractionOverride(from, to, pos, shape, st) != null ? Boolean.TRUE : null;
        }, level -> Boolean.FALSE);
        return !blocked;
    }

    private Vec3 gunPos() { return SableBridge.toWorld(level, mounted(LauncherGeometry.toWorld(worldPosition, facing(), new double[] { 0.0, GUN_Y, GUN_Z }))); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CiwsBlockEntity be) {
        if (level instanceof net.minecraft.server.level.ServerLevel empSl && EmpField.jammed(empSl, pos)) return;
        CommandCenter.siteSeen(be);
        RadarEmit.note(be);
        if (level instanceof ServerLevel sl) be.serverStep(sl, level.getGameTime());
    }

    private void serverStep(ServerLevel sl, long now) {
        boolean changed = false;

        if (now % 10 == 0 && rounds + Balance.CIWS_BOX <= drumSize() && !magazine.getStackInSlot(0).isEmpty()) {
            magazine.extractItem(0, 1, false);
            rounds += Balance.CIWS_BOX;
            changed = true;
        }
        if (now % 10 == 0 && owner != null) {
            lastIds = TrackNet.tracks(sl, owner, frequency, now);
            int ls = TrackNet.linkState(sl, owner, frequency, now);
            if (lastIds.size() != tracked || ls != link) { tracked = lastIds.size(); link = ls; changed = true; }
        }
        boolean powered = store.getEnergyStored() >= Balance.CIWS_MOVE_FE;
        FlightManager mgr = FlightManager.get(sl);
        Vec3 gun = gunPos();

        IronDome.Dome dome = owner != null ? IronDome.domeOf(sl, this) : null;

        boolean droneOnly = dome != null && dome.targetOf(this, now) == null && Drones.ciwsTarget(sl, owner, frequency, gun, Balance.CIWS_RANGE, now) != null;

        if (dome != null && !droneOnly && dome.targetOf(this, now) == null && powered) {
            BombEntity bomb = bombTarget(sl, gun, now);
            if (bomb != null) {
                if ((bombStep(sl, now, gun, bomb) | changed) && (now % 4 == 0 || firingUntil == now + Balance.CRAM_BURST_TICKS)) sync();
                return;
            }
            if (now % 40 == 0) swarmBlocked.clear();
            Swarm.Target sw = swarmTarget(sl, owner, gun, now);
            if (sw != null) {
                if ((swarmStep(sl, now, gun, sw) | changed) && (now % 4 == 0 || firingUntil == now + Balance.SW_CIWS_GAP)) sync();
                return;
            }
        }
        if (dome != null && !droneOnly) {
            if ((domeStep(sl, now, gun, mgr, dome, powered) | changed) && (now % 4 == 0 || firingUntil == now + Balance.CRAM_BURST_TICKS)) sync();
            return;
        }

        if (now % 4 == 0 && !droneOnly) {
            los.clear();
            for (long id : lastIds) {
                Flight f = mgr.find(id);
                if (f == null) continue;
                Vec3 p = f.position(now);
                if (p.distanceTo(gun) > Balance.CIWS_RANGE) continue;
                Vec3 dir = p.subtract(gun).normalize();
                los.put(id, clearLine(sl, gun.add(dir.scale(4.0)), p));
            }
        }
        Flight target = null;
        double best = Balance.CIWS_RANGE;
        boolean clear = false;
        for (long id : droneOnly ? java.util.List.<Long>of() : lastIds) {
            Flight f = mgr.find(id);
            if (f == null) continue;
            Vec3 p = f.position(now);
            double d = p.distanceTo(gun);
            double elev = Math.toDegrees(Math.atan2(p.y - gun.y, Math.hypot(p.x - gun.x, p.z - gun.z)));
            boolean see = los.getOrDefault(id, Boolean.TRUE);

            if (d < best && elev > 2.0 && (see || target == null)) { best = d; target = f; clear = see; }
        }

        if (target == null && owner != null && !droneOnly) {
            ArtShell shell = cramTarget(sl, gun, now);
            if (shell != null) {
                if ((cramStep(sl, now, gun, shell, powered) | changed) && (now % 4 == 0 || firingUntil == now + Balance.CRAM_BURST_TICKS)) sync();
                return;
            }
        }

        if (target == null && owner != null && !droneOnly && powered) {
            BombEntity bomb = bombTarget(sl, gun, now);
            if (bomb != null) {
                if ((bombStep(sl, now, gun, bomb) | changed) && (now % 4 == 0 || firingUntil == now + Balance.CRAM_BURST_TICKS)) sync();
                return;
            }
        }

        if (target == null && owner != null && !droneOnly && powered) {
            if (now % 40 == 0) swarmBlocked.clear();
            Swarm.Target sw = swarmTarget(sl, owner, gun, now);
            if (sw != null) {
                if ((swarmStep(sl, now, gun, sw) | changed) && (now % 4 == 0 || firingUntil == now + Balance.SW_CIWS_GAP)) sync();
                return;
            }
        }

        Drones.Drone drone = null;
        if (target == null && owner != null) {
            drone = Drones.ciwsTarget(sl, owner, frequency, gun, Balance.CIWS_RANGE, now);
            if (drone != null) {
                Vec3 dp = drone.pos();
                best = dp.distanceTo(gun);
                if (now % 4 == 0 || droneLos != drone.id()) { droneClear = clearLine(sl, gun.add(dp.subtract(gun).normalize().scale(4.0)), dp); droneLos = drone.id(); }
                clear = droneClear;
            }
        }

        if (target == null && drone == null && owner != null && powered && !droneOnly) {
            ShipDefence.Target ship = ShipDefence.ciwsTarget(sl, owner, frequency, gun, now);
            if (ship != null) {
                if ((shipStep(sl, now, gun, ship) | changed) && (now % 4 == 0 || firingUntil == now + 6)) sync();
                return;
            }
        }
        boolean fired = false;
        if ((target != null || drone != null) && powered) {
            Vec3 aim = target != null ? target.position(now + best / BULLET_SPEED) : Drones.lead(drone, best / BULLET_SPEED);
            Vec3 d = aim.subtract(gun);
            double tAz = LauncherGeometry.wrap(LauncherGeometry.azimuthFor(facing(), d.x, d.z) + 180.0);
            double tEl = Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -5.0, 85.0);
            double dAz = LauncherGeometry.wrap(tAz - az), dEl = tEl - el;
            if (Math.abs(dAz) > 0.01) { az = (float) LauncherGeometry.wrap(az + Mth.clamp(dAz, -Balance.CIWS_AZ_RATE, Balance.CIWS_AZ_RATE)); changed = true; }
            if (Math.abs(dEl) > 0.01) { el += (float) Mth.clamp(dEl, -Balance.CIWS_EL_RATE, Balance.CIWS_EL_RATE); changed = true; }
            if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
            if (clear && Math.abs(dAz) < 5.0 && Math.abs(dEl) < 4.0 && rounds > 0 && store.getEnergyStored() >= Balance.CIWS_FIRE_FE) {
                int n = Math.min(rounds, Balance.CIWS_ROUNDS_PER_TICK);
                rounds -= n;
                store.use(Balance.CIWS_FIRE_FE);
                HeatLog.fired(level, worldPosition, getOwner());
                double q = Math.pow(Math.max(0.0, 1.0 - best / Balance.CIWS_RANGE), 1.5);
                double p = Balance.CIWS_HIT_FAR + (Balance.CIWS_HIT_CLOSE - Balance.CIWS_HIT_FAR) * q;
                if (target != null && Hypersonic.isHyper(target)) p *= Balance.HYPER_CIWS;
                float dmg = 0;
                for (int i = 0; i < n; i++) if (sl.random.nextDouble() < p) dmg += Balance.CIWS_DAMAGE;
                fired = true;
                firingUntil = now + 6;
                if (now % 2 == 0)
                    PacketDistributor.sendToPlayersNear(sl, null, gun.x, gun.y, gun.z, 900, new CiwsFirePayload(worldPosition, gun.x, gun.y, gun.z, aim.x, aim.y, aim.z, dmg > 0));
                if (dmg > 0 && target != null) mgr.damage(sl, target, dmg);
                else if (dmg > 0) Drones.ciwsHit(sl, drone, dmg, owner, ownerName);
                changed = true;
            }
        } else if (target == null && drone == null && Math.abs(el) > 0.1) {
            el += (float) Mth.clamp(-el, -1.0, 1.0); changed = true;
        }
        if (changed && (now % 4 == 0 || fired)) sync();
    }

    private ArtShell cramTarget(ServerLevel sl, Vec3 gun, long now) {
        List<ArtShell> all = ArtilleryManager.get(sl).shells();
        if (all.isEmpty()) { triedShells.clear(); return null; }
        if (now % 40 == 0) {
            java.util.Set<Long> live = new java.util.HashSet<>();
            for (ArtShell s : all) live.add(s.id());
            triedShells.retainAll(live);
        }
        ArtShell best = null;
        double bd = Balance.CRAM_RANGE * Balance.CRAM_RANGE;
        for (ArtShell s : all) {
            if (owner.equals(s.owner()) || !s.interceptable() || now < s.start() + 5 || triedShells.contains(s.id())) continue;
            Vec3 p = s.position(now);
            double d = p.distanceToSqr(gun);
            if (d >= bd) continue;
            if (Math.toDegrees(Math.atan2(p.y - gun.y, Math.hypot(p.x - gun.x, p.z - gun.z))) <= 2.0) continue;
            bd = d;
            best = s;
        }
        return best;
    }

    private BombEntity bombTarget(ServerLevel sl, Vec3 gun, long now) {
        if (BombEntity.FALLING.isEmpty()) { triedBombs.clear(); return null; }
        if (now % 40 == 0) {
            java.util.Set<Integer> live = new java.util.HashSet<>();
            for (BombEntity b : BombEntity.FALLING) live.add(b.getId());
            triedBombs.retainAll(live);
        }
        BombEntity best = null;
        double bd = Balance.CRAM_RANGE * Balance.CRAM_RANGE;
        for (BombEntity b : BombEntity.FALLING) {
            if (b.level() != sl || b.isRemoved() || owner.equals(b.owner()) || triedBombs.contains(b.getId())) continue;
            Vec3 p = b.position();
            double d = p.distanceToSqr(gun);
            if (d >= bd) continue;
            if (Math.toDegrees(Math.atan2(p.y - gun.y, Math.hypot(p.x - gun.x, p.z - gun.z))) <= 2.0) continue;
            bd = d;
            best = b;
        }
        return best;
    }

    private boolean bombStep(ServerLevel sl, long now, Vec3 gun, BombEntity b) {
        Vec3 p = b.position();
        double dist = p.distanceTo(gun);
        double t = dist / BULLET_SPEED;
        Vec3 v = b.velocity();
        Vec3 lead = p.add(v.x * t, v.y * t - 0.5 * Balance.BOMB_G * t * t, v.z * t);
        int r = slew(gun, lead);
        if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
        boolean changed = (r & 1) != 0;
        if ((r & 2) != 0 && burstReady(now)) {
            triedBombs.add(b.getId());
            if (!clearLine(sl, gun.add(p.subtract(gun).normalize().scale(4.0)), p)) return changed;
            if (burst(sl, now, gun, lead, Balance.BOMB_CIWS_HIT)) b.shotDown(sl, owner, ownerName);
            changed = true;
        }
        return changed;
    }

    private boolean swarmStep(ServerLevel sl, long now, Vec3 gun, Swarm.Target t) {
        Vec3 lead = Swarm.posOf(sl, t.id(), t.k(), now + Math.round(t.pos().distanceTo(gun) / BULLET_SPEED));
        if (lead == null) lead = t.pos();
        int r = slew(gun, lead);
        if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
        boolean changed = (r & 1) != 0;
        if ((r & 2) != 0 && rounds > 0 && store.getEnergyStored() >= Balance.SW_CIWS_FE && now >= nextBurst) {

            if (!clearLine(sl, gun.add(t.pos().subtract(gun).normalize().scale(4.0)), t.pos())) { swarmBlocked.add(Swarm.key(t.id(), t.k())); return changed; }
            int n = Math.min(rounds, Balance.SW_CIWS_ROUNDS);
            rounds -= n;
            store.use(Balance.SW_CIWS_FE);
            HeatLog.fired(sl, worldPosition, getOwner());
            boolean hit = sl.random.nextDouble() < Balance.SW_CIWS_HIT * Math.min(1.0, n / (double) Balance.SW_CIWS_ROUNDS);
            firingUntil = now + Balance.SW_CIWS_GAP;
            nextBurst = now + Balance.SW_CIWS_GAP;
            PacketDistributor.sendToPlayersNear(sl, null, gun.x, gun.y, gun.z, 900, new CiwsFirePayload(worldPosition, gun.x, gun.y, gun.z, lead.x, lead.y, lead.z, hit));
            if (hit) Swarm.shoot(sl, t.id(), t.k(), now);
            changed = true;
        }
        return changed;
    }

    private boolean shipStep(ServerLevel sl, long now, Vec3 gun, ShipDefence.Target t) {
        int r = slew(gun, t.aim());
        if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
        boolean changed = (r & 1) != 0;
        if ((r & 2) != 0 && now >= nextShipBurst && rounds >= Balance.CIWS_SHIP_ROUNDS && store.getEnergyStored() >= Balance.CIWS_FIRE_FE) {
            rounds -= Balance.CIWS_SHIP_ROUNDS;
            store.use(Balance.CIWS_FIRE_FE);
            HeatLog.fired(level, worldPosition, getOwner());
            nextShipBurst = now + Balance.CIWS_SHIP_BURST_TICKS;
            firingUntil = now + 6;
            Vec3 dir = t.aim().subtract(gun).normalize();
            int broken = ShipStrike.chew(sl, t.id(), gun, t.aim().add(dir.scale(200.0)), Balance.CIWS_SHIP_BLOCKS, (float) Balance.CIWS_SHIP_MAX_HARDNESS, owner);
            PacketDistributor.sendToPlayersNear(sl, null, gun.x, gun.y, gun.z, 900, new CiwsFirePayload(worldPosition, gun.x, gun.y, gun.z, t.aim().x, t.aim().y, t.aim().z, broken > 0));
            if (broken > 0) ShipDefence.logOnce(sl, owner, t.id(), "log.wmd.ship_ciws", t.name(), Integer.toString((int) gun.x), Integer.toString((int) gun.z));
            changed = true;
        }
        return changed;
    }

    private int slew(Vec3 gun, Vec3 aim) {
        Vec3 d = aim.subtract(gun);
        double tAz = LauncherGeometry.wrap(LauncherGeometry.azimuthFor(facing(), d.x, d.z) + 180.0);
        double tEl = Mth.clamp(Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -5.0, 85.0);
        double dAz = LauncherGeometry.wrap(tAz - az), dEl = tEl - el;
        int r = 0;
        if (Math.abs(dAz) > 0.01) { az = (float) LauncherGeometry.wrap(az + Mth.clamp(dAz, -Balance.CIWS_AZ_RATE, Balance.CIWS_AZ_RATE)); r |= 1; }
        if (Math.abs(dEl) > 0.01) { el += (float) Mth.clamp(dEl, -Balance.CIWS_EL_RATE, Balance.CIWS_EL_RATE); r |= 1; }
        if (Math.abs(dAz) < 5.0 && Math.abs(dEl) < 4.0) r |= 2;
        return r;
    }

    private boolean burstReady(long now) { return rounds > 0 && store.getEnergyStored() >= Balance.CRAM_BURST_FE && now >= nextBurst; }

    private boolean burst(ServerLevel sl, long now, Vec3 gun, Vec3 aim, double p) {
        int n = Math.min(rounds, Balance.CRAM_ROUNDS_PER_SHOT);
        rounds -= n;
        store.use(Balance.CRAM_BURST_FE);
        HeatLog.fired(sl, worldPosition, getOwner());
        boolean hit = sl.random.nextDouble() < p * Math.min(1.0, n / (double) Balance.CRAM_ROUNDS_PER_SHOT);
        firingUntil = now + Balance.CRAM_BURST_TICKS;
        nextBurst = now + Balance.CRAM_BURST_TICKS;
        PacketDistributor.sendToPlayersNear(sl, null, gun.x, gun.y, gun.z, 900, new CiwsFirePayload(worldPosition, gun.x, gun.y, gun.z, aim.x, aim.y, aim.z, hit));
        return hit;
    }

    private boolean cramStep(ServerLevel sl, long now, Vec3 gun, ArtShell s, boolean powered) {
        if (!powered) return false;
        Vec3 p = s.position(now);
        double dist = p.distanceTo(gun);
        Vec3 lead = s.position(now + dist / BULLET_SPEED);
        int r = slew(gun, lead);
        if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
        boolean changed = (r & 1) != 0;
        if ((r & 2) != 0 && burstReady(now)) {
            triedShells.add(s.id());
            if (!clearLine(sl, gun.add(p.subtract(gun).normalize().scale(4.0)), p)) return changed;
            if (burst(sl, now, gun, lead, Balance.CRAM_HIT)) ArtilleryManager.get(sl).shootDown(sl, s.id(), owner, ownerName);
            changed = true;
        }
        return changed;
    }

    private boolean domeStep(ServerLevel sl, long now, Vec3 gun, FlightManager mgr, IronDome.Dome dome, boolean powered) {
        IronDome.Target t = dome.targetOf(this, now);
        Vec3 p = t == null ? null : dome.position(t, now);
        if (p == null || !powered) {
            if (Math.abs(el) > 0.1) { el += (float) Mth.clamp(-el, -1.0, 1.0); return true; }
            return false;
        }
        double dist = p.distanceTo(gun);
        Vec3 lead = dome.position(t, now + dist / BULLET_SPEED);
        if (lead == null) lead = p;
        int r = slew(gun, lead);
        if (now % 4 == 0) store.use(Balance.CIWS_MOVE_FE);
        boolean changed = (r & 1) != 0;
        if ((r & 2) != 0 && burstReady(now)) {
            if (!clearLine(sl, gun.add(p.subtract(gun).normalize().scale(4.0)), p)) { dome.missed(this, t); return changed; }
            boolean hit = burst(sl, now, gun, lead, Balance.DOME_HIT);
            if (hit) {
                dome.done(t);
                if (t.shell()) ArtilleryManager.get(sl).shootDown(sl, t.id(), owner, ownerName);
                else {
                    Flight f = mgr.find(t.id());
                    if (f != null) {
                        CcBattleLog.add(sl.getServer(), owner, CcBattleLog.INTERCEPT, "log.wmd.dome_kill", f.launcher(), Integer.toString(f.tier()),
                                Long.toString(Math.round(p.x)), Long.toString(Math.round(p.z)));
                        mgr.damage(sl, f, Float.MAX_VALUE / 4.0F);
                    }
                }
            } else dome.missed(this, t);
            changed = true;
        }
        return changed;
    }

    private Vec3 mounted(Vec3 upright) {
        Direction m = Machines.mount(getBlockState());
        if (m == Direction.UP) return upright;
        return Machines.mountPoint(worldPosition, m, upright.x - worldPosition.getX() - 0.5, upright.y - worldPosition.getY(), upright.z - worldPosition.getZ() - 0.5);
    }

    float[] drawnAim(float pt) {
        double[] s = ShipFrame.modelAim(level, worldPosition, facing(), Machines.mount(getBlockState()), shownAz - 180.0, shownEl, pt);
        return new float[] { (float) LauncherGeometry.wrap(s[0] + 180.0), (float) s[1] };
    }

    Vec3 modelToWorld(double x, double y, double z) {
        float[] drawn = drawnAim(Float.NaN);
        double el = Math.toRadians(drawn[1]), ce = Math.cos(el), se = Math.sin(el);
        double ry = y - GUN_Y, rz = z - GUN_Z;
        double y1 = GUN_Y + ry * ce - rz * se, z1 = GUN_Z + ry * se + rz * ce;
        double az = Math.toRadians(drawn[0]), ca = Math.cos(az), sa = Math.sin(az);
        double dz = z1 - 16.0;

        return ShipFrame.drawnWorld(level, worldPosition, mounted(LauncherGeometry.toWorld(worldPosition, facing(), new double[] { x * ca + dz * sa, y1, -x * sa + dz * ca + 16.0 })), Float.NaN);
    }

    Vec3 drawnMuzzle, drawnEject;
    long drawnAt = -1000;

    private boolean drawnFresh() { return level != null && drawnMuzzle != null && Math.abs(level.getGameTime() - drawnAt) < 40; }

    Vec3 muzzleWorld() { return drawnFresh() && drawnOnGun() ? drawnMuzzle : modelToWorld(0.0, GUN_Y, -42.0); }
    Vec3 ejectWorld() { return drawnFresh() && drawnEject != null && drawnOnGun() ? drawnEject : modelToWorld(7.0, GUN_Y - 1.0, 6.5); }

    private boolean drawnOnGun() { return !ShipFrame.onShip(level, worldPosition) || drawnMuzzle.distanceToSqr(modelToWorld(0.0, GUN_Y, -42.0)) < 36.0; }

    Vec3 sideWorld() { return modelToWorld(10.0, GUN_Y, 6.5).subtract(modelToWorld(0.0, GUN_Y, 6.5)).normalize(); }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CiwsBlockEntity be) {
        be.clientTicks++;
        be.recoil *= 0.6F;
        be.heat = Math.max(0.0F, be.heat - 0.006F);
        be.beltPhase = (be.beltPhase + be.spinSpeed * 0.0016F) % 1.0F;
        be.podAngle += be.firing(level.getGameTime()) ? 0.25F : 0.03F;
        if (be.heat > 0.03F) {
            Vec3 h = be.modelToWorld(0.0, GUN_Y, -26.0);
            VeilBridge.machineGlow(0x2C15_0000_0000_0002L ^ pos.asLong(), h.x, h.y, h.z, 1.0F, 0.38F, 0.1F, 4.5F + 3.0F * be.heat, 0.75F * be.heat, level.getGameTime());
        }
        be.shownAz += (float) (LauncherGeometry.wrap(be.az - be.shownAz) * 0.55);
        be.shownEl += (be.el - be.shownEl) * 0.55F;
        float want = be.firing(level.getGameTime()) ? 38.0F : 0.0F;
        be.spinSpeed += (want - be.spinSpeed) * 0.12F;
        be.spin = (be.spin + be.spinSpeed) % 360.0F;
    }

    void dropContents() {
        if (level == null) return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, magazine.getStackInSlot(0));
        if (bigDrum) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, new ItemStack(Wmd.UPGRADE_CIWS_DRUM.get()));
    }

    private Swarm.Target swarmTarget(ServerLevel sl, UUID owner, Vec3 gun, long now) {
        if (now % 2 == 0) swarmLast = Swarm.nearestHostile(sl, owner, u -> false, gun, Balance.SW_CIWS_RANGE, now, swarmBlocked::contains);
        else if (swarmLast != null) {
            Vec3 p = swarmBlocked.contains(Swarm.key(swarmLast.id(), swarmLast.k())) ? null : Swarm.posOf(sl, swarmLast.id(), swarmLast.k(), now);
            swarmLast = p == null || p.distanceToSqr(gun) > (double) Balance.SW_CIWS_RANGE * Balance.SW_CIWS_RANGE ? null : new Swarm.Target(swarmLast.id(), swarmLast.k(), p);
        }
        return swarmLast;
    }
    private Swarm.Target swarmLast;

    void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public IEnergyStorage energyPort(BlockPos part, Direction side) { return Machines.atPart(worldPosition, facing(), Machines.mount(getBlockState()), part, side, 0, 0, 2, Direction.SOUTH) ? store : null; }

    private final IItemHandler magazinePort = new InsertOnlyHandler(magazine);

    @Override public IItemHandler itemPort(BlockPos part, Direction side) { return magazinePort; }

    private void readMagazine(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("magazine")) return;
        ItemStackHandler tmp = new ItemStackHandler(1);
        tmp.deserializeNBT(registries, tag.getCompound("magazine"));
        magazine.setStackInSlot(0, tmp.getSlots() > 0 ? tmp.getStackInSlot(0) : ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        security.write(tag);
        tag.put("magazine", magazine.serializeNBT(registries));
        tag.putInt("energy", store.getEnergyStored());
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        security.read(tag);
        readMagazine(registries, tag);
        store.set(tag.getInt("energy"));
        read(tag);
    }

    private void write(CompoundTag tag) {
        tag.putInt("frequency", frequency); tag.putInt("tracked", tracked); tag.putInt("link", link); tag.putInt("rounds", rounds); tag.putBoolean("bigDrum", bigDrum);
        tag.putFloat("az", az); tag.putFloat("el", el); tag.putLong("firingUntil", firingUntil);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
    }

    private void read(CompoundTag tag) {
        frequency = Math.max(1, tag.getInt("frequency")); tracked = tag.getInt("tracked"); link = tag.getInt("link"); rounds = tag.getInt("rounds"); bigDrum = tag.getBoolean("bigDrum");
        az = tag.getFloat("az"); el = tag.getFloat("el"); firingUntil = tag.contains("firingUntil") ? tag.getLong("firingUntil") : -1;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("magazine", magazine.serializeNBT(registries));
        write(tag);
        return tag;
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        readMagazine(registries, tag);
        read(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }
}
