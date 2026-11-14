package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

@EventBusSubscriber(modid = Wmd.MODID)
public class FuelTankBlockEntity extends P6DriveBlockEntity implements P7Parts {

    private static final Set<String> MOVED_DROP = Set.of("tank", "tank_dent", "tank_burnt");

    private static final Map<ResourceKey<Level>, Map<UUID, Set<Long>>> TANKS = Statics.server(new HashMap<>());

    private static final List<Falling> FALLING = Statics.server(new ArrayList<>());

    private static final class Falling {
        final ResourceKey<Level> dim;
        Vec3 pos, vel;
        int age;

        boolean burning;
        float fill;
        Falling(ResourceKey<Level> dim, Vec3 pos, Vec3 vel) { this.dim = dim; this.pos = pos; this.vel = vel; }
    }

    private final boolean drop;
    private final FluidTank fuel;
    private boolean gone;
    private boolean blown;
    private UUID regShip;
    private int sentMb = -1;

    private float cFill, tFill;
    private boolean cGone;

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.is(AirBlocks.DROP_TANK.get()));
    }

    private FuelTankBlockEntity(BlockPos pos, BlockState state, boolean drop) {
        super(drop ? AirBlocks.DROP_TANK_BE.get() : AirBlocks.FUEL_TANK_BE.get(), pos, state, 0, 1, 0);
        this.drop = drop;
        this.fuel = new FluidTank(drop ? Balance.DROP_TANK_MB : Balance.FUEL_TANK_MB, fs -> fs.getFluid() == WmdFluids.JET_FUEL.source().get());
    }

    private static List<FuelTankBlockEntity> on(ServerLevel level, UUID ship) {
        List<FuelTankBlockEntity> out = new ArrayList<>();
        if (!AirGate.on() || level == null || ship == null) return out;
        Map<UUID, Set<Long>> m = TANKS.get(level.dimension());
        Set<Long> at = m == null ? null : m.get(ship);
        if (at == null) return out;
        for (Long p : new ArrayList<>(at))
            if (level.isLoaded(BlockPos.of(p)) && level.getBlockEntity(BlockPos.of(p)) instanceof FuelTankBlockEntity t && !t.isRemoved() && !t.gone) out.add(t);
        return out;
    }

    static int[] poolOf(ServerLevel level, UUID ship) {
        int[] out = { 0, 0 };
        for (FuelTankBlockEntity t : on(level, ship)) { out[0] += t.fuel.getFluidAmount(); out[1] += t.fuel.getCapacity(); }
        return out;
    }

    static int drain(ServerLevel level, UUID ship, int mb) {
        if (mb <= 0) return 0;
        List<FuelTankBlockEntity> l = on(level, ship);
        l.sort((a, b) -> Boolean.compare(b.drop, a.drop));
        int got = 0;
        for (FuelTankBlockEntity t : l) {
            if (got >= mb) break;
            if (EmpField.jammed(level, t.worldPosition)) continue;
            int d = t.fuel.drain(mb - got, IFluidHandler.FluidAction.EXECUTE).getAmount();
            if (d > 0) { got += d; t.setChanged(); }
        }
        return got;
    }

    static int fill(ServerLevel level, UUID ship, int mb) {
        if (mb <= 0) return 0;
        List<FuelTankBlockEntity> l = on(level, ship);
        l.sort((a, b) -> Boolean.compare(a.drop, b.drop));
        int put = 0;
        for (FuelTankBlockEntity t : l) {
            if (put >= mb) break;
            int n = t.fuel.fill(new FluidStack(WmdFluids.JET_FUEL.source().get(), mb - put), IFluidHandler.FluidAction.EXECUTE);
            if (n > 0) { put += n; t.setChanged(); }
        }
        return put;
    }

    static int jettisonAll(ServerLevel level, UUID ship) {
        int n = 0;
        for (FuelTankBlockEntity t : on(level, ship)) if (t.drop && t.jettison(level)) n++;
        return n;
    }

    private void register(ServerLevel sl) {
        UUID now = gone ? null : SableBridge.shipIdAt(sl, worldPosition);
        if (now != null && now.equals(regShip)) {
            TANKS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(now, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
            return;
        }
        unregister(sl);
        regShip = now;
        ship = now;
        if (now != null) TANKS.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(now, k -> new LinkedHashSet<>()).add(worldPosition.asLong());
    }

    private void unregister(ServerLevel sl) {
        if (regShip == null) return;
        Map<UUID, Set<Long>> m = TANKS.get(sl.dimension());
        if (m != null) {
            Set<Long> s = m.get(regShip);
            if (s != null) { s.remove(worldPosition.asLong()); if (s.isEmpty()) m.remove(regShip); }
            if (m.isEmpty()) TANKS.remove(sl.dimension());
        }
        regShip = null;
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel sl) unregister(sl);
        super.setRemoved();
    }

    private boolean jettison(ServerLevel sl) {
        if (!drop || gone) return false;
        boolean wasBurning = TankDamage.burning(damageStage(), fuel.getFluid());
        float wasFill = fuel.getCapacity() <= 0 ? 0.0F : fuel.getFluidAmount() / (float) fuel.getCapacity();
        gone = true;
        fuel.setFluid(FluidStack.EMPTY);
        Vec3 at = worldVec(0.0, 7.0 / 16.0, 1.0);
        SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
        Vec3 v = info == null ? Vec3.ZERO : info.linearVelocity().scale(1.0 / 20.0);
        Falling fl = new Falling(sl.dimension(), at.add(0.0, -0.6, 0.0), v.add(0.0, -0.05, 0.0));
        fl.burning = wasBurning;
        fl.fill = wasFill;
        FALLING.add(fl);
        if (wasBurning) Wmd.LOGGER.info("Drop tank at {}: jettisoned BURNING, it goes up where it lands", worldPosition.toShortString());
        sl.playSound(null, at.x, at.y, at.z, WmdSounds.TANK_JETTISON.get(), SoundSource.BLOCKS, Balance.SND_CLICK, 1.0F);
        unregister(sl);
        setChanged();
        syncNow();
        Wmd.LOGGER.info("Drop tank at {}: jettisoned", worldPosition.toShortString());
        return true;
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (FALLING.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        for (Iterator<Falling> it = FALLING.iterator(); it.hasNext();) {
            Falling f = it.next();
            if (f.dim != sl.dimension()) continue;
            f.vel = f.vel.scale(Balance.DROP_TANK_DRAG).add(0.0, -Balance.DROP_TANK_G, 0.0);
            Vec3 next = f.pos.add(f.vel);
            BlockPos bp = BlockPos.containing(next);
            if (!sl.isLoaded(bp) || ++f.age > Balance.DROP_TANK_MAX_TICKS) { it.remove(); continue; }
            if (f.age % 2 == 0) sl.sendParticles(ParticleTypes.CLOUD, f.pos.x, f.pos.y, f.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
            if (f.burning) sl.sendParticles(WmdParticles.ROCKET_FLAME.get(), f.pos.x, f.pos.y, f.pos.z, 2, 0.15, 0.15, 0.15, 0.01);
            BlockState hit = sl.getBlockState(bp);
            if (!hit.isAir() && (!hit.getCollisionShape(sl, bp).isEmpty() || !hit.getFluidState().isEmpty())) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), next.x, next.y + 0.5, next.z, 40, 0.6, 0.3, 0.6, 0.15);
                sl.sendParticles(ParticleTypes.SMOKE, next.x, next.y + 0.5, next.z, 12, 0.5, 0.3, 0.5, 0.02);
                sl.playSound(null, next.x, next.y, next.z, WmdSounds.TANK_IMPACT.get(), SoundSource.BLOCKS, Balance.SND_MECH, 1.0F);
                if (f.burning) TankDamage.explode(sl, next.add(0.0, 0.5, 0.0), new FluidStack(WmdFluids.JET_FUEL.source().get(), Math.max(1, Math.round(f.fill * Balance.DROP_TANK_MB))),
                        f.fill, TankDamage.Kind.DROP, false, null);
                it.remove();
                continue;
            }
            f.pos = next;
        }
    }

    @Override protected double[] intakeLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double[] thrustLocal() { return new double[] { 0.0, 0.5, 0.0 }; }
    @Override protected double airflow() { return 0.0; }

    @Override protected boolean damageable() { return !gone && Config.Server.tankDamage(); }
    @Override protected int spoolUp() { return 1; }
    @Override protected int spoolDown() { return 1; }

    @Override
    protected boolean work() {
        if (!(getLevel() instanceof ServerLevel sl)) return false;
        if (!AirGate.on()) { unregister(sl); return false; }
        long now = sl.getGameTime();
        if (now % 20 == (worldPosition.asLong() & 15)) register(sl);
        if (!gone) damageTick(sl, now);
        if (isRemoved()) return false;
        int mb = fuel.getFluidAmount();
        if (Math.abs(mb - sentMb) >= fuel.getCapacity() / 50 || (mb == 0) != (sentMb == 0)) { sentMb = mb; syncNow(); }
        force = null;
        return false;
    }

    private void damageTick(ServerLevel sl, long now) {
        int st = damageStage();
        if (st == 0) return;
        if (now % 20 == (worldPosition.asLong() & 7) && st < 3 && !fuel.isEmpty()) {
            fuel.drain(drop ? Balance.TANK_LEAK_MB_DROP : Balance.TANK_LEAK_MB_FUEL, IFluidHandler.FluidAction.EXECUTE);
            setChanged();
            if (st == 2 && !fuel.isEmpty()) damage(sl, Balance.TANK_BURN_HP_FUEL);
        }
        if (TankDamage.burning(damageStage(), fuel.getFluid()) && now % 4 == 0) {
            SableBridge.ShipInfo info = ship == null ? null : SableBridge.shipInfo(sl, ship);
            Vec3 v = info == null ? Vec3.ZERO : info.linearVelocity().scale(1.0 / 20.0);
            TankDamage.burnFx(sl, TankDamage.fireId(sl, worldPosition), centre(), 0.6F, 0.6F, drop ? 1.5F : 0.6F, v, TankDamage.Burn.VOLATILE);
        }
        if (damageStage() == 3 && !blown) {
            blown = true;
            FluidStack in = fuel.getFluid().copy();
            float f = fuel.getCapacity() <= 0 ? 0.0F : in.getAmount() / (float) fuel.getCapacity();
            Vec3 at = centre();
            boolean onShip = ship != null;
            fuel.setFluid(FluidStack.EMPTY);
            unregister(sl);
            sl.removeBlock(worldPosition, false);
            TankDamage.explode(sl, at, in, f, drop ? TankDamage.Kind.DROP : TankDamage.Kind.FUEL, onShip, null);
        }
    }

    private Vec3 centre() { return drop ? worldVec(0.0, 7.0 / 16.0, 1.0) : worldVec(0.0, 0.5, 0.0); }

    @Override
    protected void leakFx(Level lv, double x, double y, double z) {
        if (cFill <= 0.001F) return;
        lv.addParticle(WmdParticles.VAPOUR.get(), x + (lv.random.nextDouble() - 0.5) * 0.4, y - 0.2, z + (lv.random.nextDouble() - 0.5) * 0.4, 0.0, -0.02, 0.0);
    }

    public IFluidHandler fluidPort(BlockPos part, Direction side) {
        if (gone) return null;
        return at(part, side, 0, 0, 0, drop ? Direction.UP : Direction.NORTH) ? fillOnly(fuel) : null;
    }

    @Override
    public Component status() {
        String k = gone ? "message.wmd.fuel_tank.gone" : "message.wmd.fuel_tank.level";
        return Component.translatable(drop ? "block.wmd.drop_tank" : "block.wmd.fuel_tank").append(": ")
                .append(Component.translatable(k, String.format("%,d", fuel.getFluidAmount()), String.format("%,d", fuel.getCapacity())));
    }

    @Override protected String detail() { return fuel.getFluidAmount() + " / " + fuel.getCapacity() + " mB" + (gone ? ", jettisoned" : "") + (regShip != null ? ", ship " + regShip : ", not on a ship"); }

    public int fuelMb() { return gone ? 0 : fuel.getFluidAmount(); }

    @Override protected List<FluidTank> tanks() { return List.of(fuel); }

    @Override
    protected void clientExtra(Level lv) { cFill += (tFill - cFill) * 0.1F; }

    @Override protected boolean runningClient() { return false; }

    @Override
    public void ccAnimate(CcBones model, double tt, float pt) {
        if (drop) return;
        final float a = -cFill * 270.0F * Mth.DEG_TO_RAD * RY;
        model.getBone("needle").ifPresent(b -> b.setRotY(a));
    }

    @Override public Set<String> movedBones() { return drop ? MOVED_DROP : Set.of(); }

    @Override public float[] boneShift(String bone, float pt) { return cGone || !bone.equals(damageBone()) ? null : new float[3]; }

    private String damageBone() { return damageStage() >= 2 ? "tank_burnt" : hp() < Balance.ENGINE_HP ? "tank_dent" : "tank"; }

    @Override protected SoundEvent runSound() { return WmdSounds.COCKPIT_SWITCH.get(); }
    @Override protected float soundVolume() { return 0.0F; }
    @Override protected boolean soundAlive() { return false; }

    @Override
    protected void putExtra(CompoundTag tag) {
        tag.putFloat("ftFill", fuel.getCapacity() <= 0 ? 0.0F : fuel.getFluidAmount() / (float) fuel.getCapacity());
        tag.putBoolean("ftGone", gone);
    }

    @Override
    protected void readExtra(CompoundTag tag, boolean client) {
        boolean g = tag.getBoolean("ftGone");
        if (client) { tFill = Mth.clamp(tag.getFloat("ftFill"), 0.0F, 1.0F); cGone = g; return; }
        gone = g;
    }
}
