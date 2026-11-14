package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class TankDamage {

    private TankDamage() {}

    public enum Burn {

        GAS,

        VOLATILE,

        HEAVY,

        SMOULDER,

        STEAM,

        TOXIC,

        INERT;

        public boolean flammable() { return this == GAS || this == VOLATILE || this == HEAVY || this == SMOULDER; }
    }

    static Burn byId(String id) {
        return switch (id) {
            case "lpg", "hydrogen", "deuterium", "tritium" -> Burn.GAS;
            case "jet_fuel", "gasoline", "naphtha", "reformate", "alkylate", "kerosene" -> Burn.VOLATILE;
            case "crude_oil", "desalted_crude", "hot_crude", "diesel", "fuel_oil", "lvgo", "hvgo", "coal_tar" -> Burn.HEAVY;
            case "bitumen" -> Burn.SMOULDER;
            case "steam", "spent_steam" -> Burn.STEAM;
            case "chlorine", "titanium_tetrachloride" -> Burn.TOXIC;
            default -> Burn.INERT;
        };
    }

    public static Burn of(FluidStack fs) {
        if (fs == null || fs.isEmpty()) return Burn.INERT;
        String id = idOf(fs.getFluid());
        if (id != null) return byId(id);
        return fs.getFluid().isSame(Fluids.LAVA) ? Burn.HEAVY : Burn.INERT;
    }

    private static String idOf(Fluid f) {
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == f || e.flowing().get() == f) return e.id();
        return null;
    }

    static boolean boilsOver(FluidStack fs) {
        String id = fs == null || fs.isEmpty() ? null : idOf(fs.getFluid());
        return "crude_oil".equals(id) || "desalted_crude".equals(id) || "hot_crude".equals(id);
    }

    static int colour(FluidStack fs) {
        if (fs == null || fs.isEmpty()) return 0xFF8090A0;
        for (WmdFluids.Entry e : WmdFluids.ALL) if (e.source().get() == fs.getFluid() || e.flowing().get() == fs.getFluid()) return e.color();
        return fs.getFluid().isSame(Fluids.LAVA) ? 0xFFE06010 : 0xFF3F76E4;
    }

    static final class Health {
        final int max;
        int hp;

        Health(int max) { this.max = max; this.hp = max; }

        int stage() { return stageOf(hp, max); }

        static int stageOf(int hp, int max) {
            double f = hp / (double) max;
            return hp <= 0 ? 3 : f < Balance.TANK_FIRE ? 2 : f < Balance.TANK_LEAK ? 1 : 0;
        }

        boolean hit(double amount) {
            if (hp <= 0 || amount <= 0.0) return false;
            int before = stage();
            hp = Math.max(0, hp - (int) Math.ceil(amount));
            return stage() != before;
        }

        boolean repair() { if (hp >= max) return false; hp = max; return true; }

        void save(CompoundTag t) { t.putInt("tkHp", hp); }

        void load(CompoundTag t) { hp = t.contains("tkHp") ? Mth.clamp(t.getInt("tkHp"), 0, max) : max; }
    }

    interface Holder {
        Level tankLevel();
        boolean tankGone();

        AABB tankBox();

        void tankHit(ServerLevel sl, double hp);

        boolean tankRepair(ServerLevel sl);

        boolean tankAtRest(ServerLevel sl);
    }

    private static final Set<Holder> LIVE = Statics.server(new HashSet<>());

    private static final Map<Object, double[]> LAST = Statics.server(new WeakHashMap<>());

    static void track(Holder h) { LIVE.add(h); }

    static void untrack(Holder h) { LIVE.remove(h); }

    private static List<Holder> live(ServerLevel sl) {
        List<Holder> out = new ArrayList<>();
        LIVE.removeIf(h -> h.tankGone() || h.tankLevel() == null);
        for (Holder h : LIVE) if (h.tankLevel() == sl) out.add(h);
        return out;
    }

    private static double distance(Vec3 p, AABB b) {
        double dx = Math.max(Math.max(b.minX - p.x, 0.0), p.x - b.maxX);
        double dy = Math.max(Math.max(b.minY - p.y, 0.0), p.y - b.maxY);
        double dz = Math.max(Math.max(b.minZ - p.z, 0.0), p.z - b.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean seen(ServerLevel sl, Object tank, Vec3 pos) {
        double[] l = LAST.get(tank);
        long now = sl.getGameTime();
        if (l != null && (long) l[0] == now && pos.distanceToSqr(l[1], l[2], l[3]) < 2.25) return true;
        LAST.put(tank, new double[] { now, pos.x, pos.y, pos.z });
        return false;
    }

    public static void blastAt(ServerLevel sl, Vec3 pos, double r) {
        if (sl == null || pos == null || r <= 0.0 || LIVE.isEmpty() || !Config.Server.tankDamage()) return;
        double k = Balance.TANK_BLAST_DMG * Mth.clamp(r / Balance.TANK_BLAST_R_REF, 0.5, 3.0);
        double reach = r + Balance.TANK_BLAST_PAD;
        for (Holder h : live(sl)) {
            double d = distance(pos, h.tankBox());
            if (d >= reach || seen(sl, h, pos)) continue;
            h.tankHit(sl, k * (1.0 - d / reach));
        }
    }

    public static void hitAt(ServerLevel sl, Vec3 point, int rounds) {
        if (sl == null || point == null || rounds <= 0 || LIVE.isEmpty() || !Config.Server.tankDamage()) return;
        for (Holder h : live(sl)) if (h.tankBox().inflate(0.35).contains(point)) h.tankHit(sl, Balance.TANK_ROUND_DMG * rounds);
    }

    public static void rayAt(ServerLevel sl, Vec3 from, Vec3 to, int blocks) {
        if (sl == null || from == null || to == null || blocks <= 0 || LIVE.isEmpty() || !Config.Server.tankDamage()) return;
        for (Holder h : live(sl)) {
            AABB b = h.tankBox().inflate(0.3);
            if (b.contains(from) || b.clip(from, to).isPresent()) h.tankHit(sl, Balance.TANK_ROUND_DMG * blocks);
        }
    }

    static boolean second(Health h, FluidTank tank, double burnHp, int leakMb) {
        int st = h.stage();
        if (st == 0 || st == 3 || tank.isEmpty()) return false;
        tank.drain(leakMb, IFluidHandler.FluidAction.EXECUTE);
        if (st == 2 && of(tank.getFluid()).flammable() && !tank.isEmpty()) h.hit(burnHp);
        return true;
    }

    static boolean burning(int stage, FluidStack fs) { return stage == 2 && !fs.isEmpty() && of(fs).flammable(); }

    static void burnFx(ServerLevel sl, UUID id, Vec3 c, float hx, float hy, float hz, Vec3 vel, Burn b) {
        float heat = b == Burn.SMOULDER ? 0.15F : b == Burn.HEAVY ? 0.7F : 1.0F;
        PacketDistributor.sendToPlayersNear(sl, null, c.x, c.y, c.z, Balance.SHIP_FIRE_VIEW,
                new ShipFirePayload(id, c.x, c.y, c.z, hx, hy, hz, (float) vel.x, (float) vel.y, (float) vel.z, heat));
    }

    static void leakFx(ServerLevel sl, Vec3 at, FluidStack fs) {
        int c = colour(fs);
        sl.sendParticles(new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), 1.2F), at.x, at.y, at.z, 4, 0.15, 0.05, 0.15, 0.0);
    }

    static UUID fireId(Level level, net.minecraft.core.BlockPos pos) {
        return UUID.nameUUIDFromBytes(("wmd_tank_fire:" + level.dimension().location() + ":" + pos.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public enum Kind {
        FUEL(Balance.TANK_R_FUEL), DROP(Balance.TANK_R_DROP), CORE(Balance.TANK_R_CORE), STORAGE(Balance.TANK_R_STORAGE);
        final double r;
        Kind(double r) { this.r = r; }
    }

    public static void explode(ServerLevel sl, Vec3 at, FluidStack fs, float fill, Kind kind, boolean onShip, UUID owner) {
        Burn b = of(fs);
        boolean low = fs.isEmpty() || fill < Balance.TANK_BLAST_MIN_FILL;
        int c = colour(fs);
        Wmd.LOGGER.info("[tank] {} at {} failed: {} {} mB ({}%), {}{}", kind, BlockPosStr(at), b, fs.getAmount(), Math.round(fill * 100.0F), low ? "low: no blast" : "full blast",
                onShip ? ", on a craft" : "");
        switch (b) {
            case GAS -> {
                if (low) { fx(sl, at, TankFxPayload.POP, kind.r * 0.5, c); WarheadFx.hurt(sl, at, kind.r * 0.5, 2.0, 0.3, 0.5, 1.0, 3, owner); }
                else {
                    double r = kind == Kind.STORAGE ? Balance.TANK_R_GAS : kind.r * 1.5;
                    blast(sl, at, r, onShip, owner, 1.2, 1.5);
                    fx(sl, at, TankFxPayload.BLEVE, r * Balance.TANK_FIREBALL_GAS, c);
                }
            }
            case VOLATILE -> {
                if (low) { fx(sl, at, TankFxPayload.POP, kind.r * 0.6, c); WarheadFx.hurt(sl, at, kind.r * 0.5, 2.0, 0.3, 0.4, 1.0, 4, owner); }
                else {
                    blast(sl, at, kind.r, onShip, owner, 1.0, 1.2);
                    fx(sl, at, TankFxPayload.FIREBALL, kind.r, c);
                    if (!onShip) WarheadFx.tankTask(sl, false, at.x, at.y, at.z, kind.r * Balance.TANK_POOL_R, Balance.TANK_POOL_TICKS);
                }
            }
            case HEAVY -> {
                fx(sl, at, TankFxPayload.SPLASH, kind.r, c);
                WarheadFx.hurt(sl, at, kind.r * 0.5, 2.0, 0.2, 0.3, 1.5, 6, owner);
                if (!onShip) {
                    WarheadFx.tankTask(sl, false, at.x, at.y, at.z, kind.r * 1.5, Balance.TANK_OIL_TICKS);
                    burnAfter(sl, at, kind, fs, Balance.TANK_OIL_TICKS, boilsOver(fs) && !low ? sl.getGameTime() + Balance.TANK_BOILOVER_TICKS : -1L);
                }
            }
            case SMOULDER -> {
                fx(sl, at, TankFxPayload.SPLASH, kind.r, c);
                if (!onShip) burnAfter(sl, at, kind, fs, Balance.TANK_POOL_TICKS, -1L);
            }
            case STEAM -> {
                fx(sl, at, TankFxPayload.STEAM, Balance.TANK_STEAM_R, c);
                WarheadFx.hurt(sl, at, Balance.TANK_STEAM_R / 3.0, 3.0, 0.2, 0.6, 0.0, 0, owner);
            }
            case TOXIC -> {
                fx(sl, at, TankFxPayload.SPLASH, kind.r, c);
                if (!low) WarheadFx.tankTask(sl, true, at.x, at.y, at.z, Balance.TANK_TOXIC_R * (kind == Kind.STORAGE ? 1.0 : 0.4), Balance.TANK_TOXIC_TICKS);
            }
            case INERT -> fx(sl, at, TankFxPayload.SPLASH, kind.r, c);
        }
        if (b != Burn.GAS && b != Burn.VOLATILE) {
            ShipStrike.blast(sl, at, kind.r * 0.5, Balance.TANK_SHIP_POWER * 0.4F, owner);
            FusionReactorBlockEntity.blastAt(sl, at, kind.r * 0.5);
        }
    }

    private static String BlockPosStr(Vec3 v) { return Math.round(v.x) + " " + Math.round(v.y) + " " + Math.round(v.z); }

    private static void blast(ServerLevel sl, Vec3 at, double r, boolean onShip, UUID owner, double dmgMul, double burnMul) {
        WarheadFx.hurt(sl, at, r, 3.0, 0.5, dmgMul, burnMul, 5, owner);
        if (!onShip) {
            FlightManager fm = FlightManager.get(sl);
            int gx = Mth.floor(at.x), gy = Mth.floor(at.y), gz = Mth.floor(at.z);
            Crater crater = new Crater(gx, gy, gz, r, 0.5, 1.0, 0.0, Mlrs.HOLD);
            fm.craters.add(crater);
            Debris.blast(sl, gx, gy, gz, r, crater);
            fm.setDirty();
        }
        ShipStrike.blast(sl, at, r, Balance.TANK_SHIP_POWER, owner);
        FusionReactorBlockEntity.blastAt(sl, at, r);
    }

    private static void fx(ServerLevel sl, Vec3 at, int kind, double r, int colour) {
        PacketDistributor.sendToPlayersNear(sl, null, at.x, at.y, at.z, 384.0, new TankFxPayload(kind, at.x, at.y, at.z, (float) r, colour));
        sl.playSound(null, at.x, at.y, at.z, kind == TankFxPayload.STEAM ? SoundEvents.FIRE_EXTINGUISH : kind == TankFxPayload.SPLASH ? SoundEvents.GENERIC_SPLASH
                : SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, kind == TankFxPayload.BLEVE ? Balance.SND_ALARM : kind == TankFxPayload.FIREBALL ? Balance.SND_RELEASE : Balance.SND_MECH,
                kind == TankFxPayload.BLEVE ? 0.6F : 0.9F);
    }

    private static final class After {
        final ResourceKey<Level> dim;
        final Vec3 at;
        final UUID id;
        final Kind kind;
        final FluidStack fs;
        final Burn b;
        long until, boilAt;
        After(ResourceKey<Level> dim, Vec3 at, Kind kind, FluidStack fs, long until, long boilAt) {
            this.dim = dim; this.at = at; this.kind = kind; this.fs = fs.copy(); this.b = of(fs); this.until = until; this.boilAt = boilAt;
            this.id = UUID.nameUUIDFromBytes(("wmd_tank_after:" + dim.location() + ":" + at).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static final List<After> AFTER = Statics.server(new ArrayList<>());

    private static void burnAfter(ServerLevel sl, Vec3 at, Kind kind, FluidStack fs, int ticks, long boilAt) {
        AFTER.add(new After(sl.dimension(), at, kind, fs, sl.getGameTime() + ticks, boilAt));
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (AFTER.isEmpty() || !(event.getLevel() instanceof ServerLevel sl)) return;
        long now = sl.getGameTime();
        for (Iterator<After> it = AFTER.iterator(); it.hasNext();) {
            After a = it.next();
            if (a.dim != sl.dimension()) continue;
            if (now >= a.until || !sl.isLoaded(net.minecraft.core.BlockPos.containing(a.at))) { it.remove(); continue; }
            float half = (float) a.kind.r;
            if (now % 5 == 0) burnFx(sl, a.id, a.at.add(0.0, half, 0.0), half, half, half, Vec3.ZERO, a.b);
            if (a.boilAt > 0 && now >= a.boilAt) {
                a.boilAt = -1L;
                Wmd.LOGGER.info("[tank] boil-over at {}", BlockPosStr(a.at));
                blast(sl, a.at, Balance.TANK_BOILOVER_R, false, null, 0.8, 1.5);
                fx(sl, a.at, TankFxPayload.FIREBALL, Balance.TANK_BOILOVER_R * 1.5, colour(a.fs));
                WarheadFx.tankTask(sl, false, a.at.x, a.at.y, a.at.z, a.kind.r * 2.5, Balance.TANK_POOL_TICKS);
            }
        }
    }
}
