package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public class Drones extends SavedData {

    private static final String NAME = "wmd_drones";
    public static final int CLIMB = 0, OUT = 1, LOITER = 2, RETURN = 3, LANDING = 4, FALLING = 5;
    static final int MAX_PER_OWNER = 2, IMAGE_EVERY_TICKS = 800, SYNC_TICKS = 10, DEFENCE_TICKS = 10, LOITER_RADIUS = 48, DRONE_CRUISE = 60;
    private static final double CLIMB_RATE = 0.5, ALT_RATE = 0.4, GRAVITY = 0.04;

    public static final class Drone {
        int id;
        UUID owner;
        String ownerName = "";
        double hx, hy, hz, x, y, z, vx, vy, vz, angle;
        int tx, tz, phase, ticks;
        float health = Balance.DRONE_HEALTH, yaw;
        long nextImage;
        UUID entity;

        int kind, hiveX, hiveY, hiveZ, bay, battery, cool;
        boolean huntPlayers = true, huntMobs = true;
        UUID target, pilot;
        float ctlF, ctlS, ctlU, ctlYaw;
        boolean ctlFire, ctlLeave, ctlBoost;

        int[] wx = new int[0], wz = new int[0], bombs = new int[0];
        int wpI, spotId = -1;
        double fx, fz, roll;
        int timer;

        int circuit = -1;
        double circuitSide = 1.0;

        double lx, ly, lz;
        boolean lased;

        boolean ship, deck;
        double px, py, pz;
        float pyaw;

        public int kind() { return kind; }

        public int id() { return id; }
        public UUID owner() { return owner; }
        public int phase() { return phase; }
        public Vec3 pos() { return new Vec3(x, y, z); }
        boolean airborne() { return phase != FALLING; }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id); t.putUUID("owner", owner); t.putString("name", ownerName);
            t.putDouble("hx", hx); t.putDouble("hy", hy); t.putDouble("hz", hz);
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z);
            t.putDouble("vx", vx); t.putDouble("vy", vy); t.putDouble("vz", vz); t.putDouble("a", angle);
            t.putInt("tx", tx); t.putInt("tz", tz); t.putInt("phase", phase); t.putInt("ticks", ticks);
            t.putFloat("hp", health); t.putFloat("yaw", yaw);
            if (kind != 0) {
                t.putInt("kind", kind); t.putInt("hiveX", hiveX); t.putInt("hiveY", hiveY); t.putInt("hiveZ", hiveZ); t.putInt("bay", bay);
                t.putInt("battery", battery); t.putBoolean("huntP", huntPlayers); t.putBoolean("huntM", huntMobs);
                if (kind >= 3) {
                    t.putIntArray("wx", wx); t.putIntArray("wz", wz); t.putIntArray("bombs", bombs); t.putInt("wpI", wpI);
                    t.putDouble("fx", fx); t.putDouble("fz", fz); t.putDouble("roll", roll); t.putInt("timer", timer);
                    if (ship) { t.putBoolean("ship", true); t.putBoolean("deck", deck); t.putDouble("px", px); t.putDouble("py", py); t.putDouble("pz", pz); t.putFloat("pyaw", pyaw); }
                }
            }
            return t;
        }

        static Drone load(CompoundTag t) {
            if (!t.hasUUID("owner")) return null;
            Drone d = new Drone();
            d.id = t.getInt("id"); d.owner = t.getUUID("owner"); d.ownerName = t.getString("name");
            d.hx = t.getDouble("hx"); d.hy = t.getDouble("hy"); d.hz = t.getDouble("hz");
            d.x = t.getDouble("x"); d.y = t.getDouble("y"); d.z = t.getDouble("z");
            d.vx = t.getDouble("vx"); d.vy = t.getDouble("vy"); d.vz = t.getDouble("vz"); d.angle = t.getDouble("a");
            d.tx = t.getInt("tx"); d.tz = t.getInt("tz"); d.phase = t.getInt("phase"); d.ticks = t.getInt("ticks");
            d.health = t.getFloat("hp"); d.yaw = t.getFloat("yaw");
            d.kind = t.getInt("kind");
            if (d.kind != 0) {
                d.hiveX = t.getInt("hiveX"); d.hiveY = t.getInt("hiveY"); d.hiveZ = t.getInt("hiveZ"); d.bay = t.getInt("bay");
                d.battery = t.getInt("battery"); d.huntPlayers = t.getBoolean("huntP"); d.huntMobs = t.getBoolean("huntM");
                if (d.phase == StrikeDrones.PILOT) d.phase = StrikeDrones.HOME;
                if (d.kind >= 3) {
                    d.wx = t.getIntArray("wx"); d.wz = t.getIntArray("wz"); d.bombs = t.getIntArray("bombs"); d.wpI = t.getInt("wpI");
                    d.fx = t.getDouble("fx"); d.fz = t.getDouble("fz"); d.roll = t.getDouble("roll"); d.timer = t.getInt("timer");
                    d.ship = t.getBoolean("ship"); d.deck = t.getBoolean("deck");
                    d.px = t.getDouble("px"); d.py = t.getDouble("py"); d.pz = t.getDouble("pz"); d.pyaw = t.getFloat("pyaw");
                }
            }
            return d;
        }
    }

    private final List<Drone> drones = new ArrayList<>();
    private int nextId = 1;

    private static final Map<Integer, double[]> SHOTS = new HashMap<>();
    private static final Map<Integer, Object[]> SHOOTERS = new HashMap<>();

    private static final Map<UUID, Map<Integer, DroneView.Contact>> VIEWS = new HashMap<>();
    private static final UUID NOBODY = new UUID(0L, 0L);

    public static Drones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Drones::new, Drones::load, null), NAME);
    }

    private static Drones peek(MinecraftServer server) { return server == null ? null : get(server); }

    public List<Drone> drones() { return List.copyOf(drones); }

    int newId() { setDirty(); return nextId++; }
    void add(Drone d) { drones.add(d); setDirty(); }

    Drone byId(int id) {
        for (Drone d : drones) if (d.id == id) return d;
        return null;
    }

    static boolean alive(MinecraftServer server, int id) {
        Drones ds = peek(server);
        return ds != null && ds.byId(id) != null;
    }

    static long loiterLeftMs(MinecraftServer server, UUID owner) {
        Drones ds = peek(server);
        if (ds == null || owner == null) return 0L;
        long best = 0L;
        for (Drone d : ds.drones) if (owner.equals(d.owner) && d.phase == LOITER) best = Math.max(best, d.ticks * 50L);
        return best;
    }

    static boolean sees(MinecraftServer server, UUID owner, double x, double z) {
        Drones ds = peek(server);
        if (ds == null || owner == null) return false;
        int r = Balance.DRONE_RADIUS;
        for (Drone d : ds.drones)
            if (owner.equals(d.owner) && d.phase == LOITER && Math.abs(x - (d.tx + 0.5)) <= r && Math.abs(z - (d.tz + 0.5)) <= r) return true;
        return false;
    }

    static Component launch(ServerPlayer p, int tx, int tz) {
        MinecraftServer server = p.server;
        ServerLevel ow = server.overworld();
        if (p.level() != ow) return Component.translatable("message.wmd.drone.overworld");
        Drones ds = get(server);
        int mine = 0;
        for (Drone d : ds.drones) if (p.getUUID().equals(d.owner) && d.kind == 0) mine++;
        if (mine >= MAX_PER_OWNER) return Component.translatable("message.wmd.drone.too_many", MAX_PER_OWNER);
        int dist = (int) Math.round(Math.hypot(tx + 0.5 - p.getX(), tz + 0.5 - p.getZ()));
        if (dist > Balance.DRONE_RANGE) return Component.translatable("message.wmd.drone.too_far", dist, Balance.DRONE_RANGE);
        Drone d = new Drone();
        d.id = ds.nextId++;
        d.owner = p.getUUID();
        d.ownerName = p.getGameProfile().getName();
        d.hx = d.x = p.getX(); d.hy = d.y = p.getY() + 1.5; d.hz = d.z = p.getZ();
        d.tx = tx; d.tz = tz;
        d.phase = CLIMB;
        d.yaw = p.getYRot();
        ds.drones.add(d);
        ds.setDirty();
        p.sendSystemMessage(Component.translatable("message.wmd.drone.launched", d.id, tx, tz, dist).withStyle(ChatFormatting.AQUA));
        ow.playSound(null, p.blockPosition(), SoundEvents.BEEHIVE_EXIT, SoundSource.PLAYERS, 1.0F, 0.6F);
        CcBattleLog.add(server, d.owner, CcBattleLog.LAUNCH_OUT, "log.wmd.drone_launch", Integer.toString(tx), Integer.toString(tz));
        Wmd.LOGGER.info("Recon drone #{} of {} launched from {} {} towards {} {}", d.id, d.ownerName, Math.round(d.hx), Math.round(d.hz), tx, tz);
        return null;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Drones ds = peek(server);
        if (ds == null) return;
        BigDrones.tickFalls(server.overworld(), server.overworld().getGameTime());
        if (ds.drones.isEmpty()) {
            boolean any = false;
            for (Map<Integer, DroneView.Contact> v : VIEWS.values()) if (!v.isEmpty()) { any = true; break; }
            if (any) syncViews(server.overworld(), List.of(), server.overworld().getGameTime());
            return;
        }
        ds.tick(server.overworld());
    }

    private void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        for (Iterator<Drone> it = drones.iterator(); it.hasNext(); ) {
            Drone d = it.next();
            boolean done;
            try { done = step(level, d, now); }
            catch (RuntimeException ex) { Wmd.LOGGER.warn("WMD recon drone #{} failed: removed", d.id, ex); done = true; }
            if (done) { it.remove(); dropEntity(level, d); SHOTS.remove(d.id); SHOOTERS.remove(d.id); continue; }
            proxy(level, d);
        }
        if (now % DEFENCE_TICKS == 3) defence(level, now);
        if (now % DroneWarnings.TICKS == 17) DroneWarnings.drones(level, drones, now);
        shotsArrive(level, now);
        if (now % 20 == 0) setDirty();
        if (now % SYNC_TICKS == 0) syncViews(level, drones, now);
    }

    private static void syncViews(ServerLevel level, List<Drone> drones, long now) {
        java.util.Set<UUID> here = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) here.add(p.getUUID());
        VIEWS.keySet().retainAll(here);
        FlightIntel.Radars radars = new FlightIntel.Radars(level, now);
        for (ServerPlayer p : level.players()) sendView(p, drones, radars, false);
    }

    private static void sendView(ServerPlayer p, List<Drone> drones, FlightIntel.Radars radars, boolean full) {
        Map<Integer, DroneView.Contact> had = full ? new HashMap<>() : VIEWS.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        java.util.Set<UUID> team = CommandCenter.ownersTrusting(p.getUUID());
        record Pick(DroneView.Contact c, double d) {}
        List<Pick> picks = new ArrayList<>();
        for (Drone d : drones) {
            boolean mine = FlightIntel.friendly(p, d.owner, team);
            double keep = had.containsKey(d.id) ? 1.1 : 1.0;
            if (!mine && !FlightIntel.near(p, d.x, d.z, keep) && !radars.seesPoint(p.getUUID(), team, d.x, d.z, Double.MAX_VALUE, keep)) continue;
            picks.add(new Pick(new DroneView.Contact(d.id, mine ? d.owner : NOBODY, d.x, d.y, d.z, d.phase), mine ? -1.0 : Math.hypot(d.x - p.getX(), d.z - p.getZ())));
        }
        if (picks.size() > DronesPayload.MAX) {
            picks.sort((a, b) -> Double.compare(a.d(), b.d()));
            picks = new ArrayList<>(picks.subList(0, DronesPayload.MAX));
        }
        Map<Integer, DroneView.Contact> next = new HashMap<>();
        List<DroneView.Contact> up = new ArrayList<>();
        for (Pick k : picks) {
            next.put(k.c().id(), k.c());
            if (!k.c().equals(had.get(k.c().id()))) up.add(k.c());
        }
        List<Integer> gone = new ArrayList<>();
        if (!full) for (Integer id : had.keySet()) if (!next.containsKey(id)) gone.add(id);
        VIEWS.put(p.getUUID(), next);
        if (full || !up.isEmpty() || !gone.isEmpty()) PacketDistributor.sendToPlayer(p, new DronesPayload(full, up, gone));
    }

    private static void sendFull(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        if (level != level.getServer().overworld()) {
            VIEWS.remove(p.getUUID());
            PacketDistributor.sendToPlayer(p, new DronesPayload(true, List.of(), List.of()));
            return;
        }
        Drones ds = peek(level.getServer());
        sendView(p, ds == null ? List.of() : ds.drones, new FlightIntel.Radars(level, level.getGameTime()), true);
    }

    @SubscribeEvent
    static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendFull(p);
    }

    @SubscribeEvent
    static void onDimensionChange(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sendFull(p);
    }

    static int groundAt(ServerLevel level, double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        LevelChunk ch = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
        if (ch == null) return Integer.MIN_VALUE;
        return ch.getHeight(Heightmap.Types.MOTION_BLOCKING, bx & 15, bz & 15) + 1;
    }

    private boolean step(ServerLevel level, Drone d, long now) {
        if (d.kind >= 3 && d.phase != FALLING) return BigDrones.step(this, level, d, now);
        if (d.kind != 0 && d.phase != FALLING) {
            Vec3 was = d.pos();
            if (StrikeDrones.step(this, level, d, now)) return true;
            Forcefield.Crossing c = ForcefieldBlockEntity.LOADED.isEmpty() ? null : Forcefield.crossing(level, was, d.pos());
            if (c != null && !Forcefield.mayCross(c.be(), d.owner) && Forcefield.shellHit(level, c, 1, WarheadType.HE)) { shootDown(level, d, null, "forcefield"); return true; }
            return false;
        }
        MinecraftServer server = level.getServer();
        double speed = Balance.DRONE_SPEED;
        switch (d.phase) {
            case CLIMB -> {
                int g = groundAt(level, d.x, d.z);
                double want = (g == Integer.MIN_VALUE ? d.hy : Math.max(d.hy, g)) + DRONE_CRUISE;
                d.vx = 0; d.vz = 0; d.vy = CLIMB_RATE;
                d.y = Math.min(want, d.y + CLIMB_RATE);
                if (d.y >= want - 0.01) d.phase = OUT;
            }
            case OUT -> {
                if (flyTo(level, d, d.tx + 0.5, d.tz + 0.5, speed) <= LOITER_RADIUS) startLoiter(server, d, now);
            }
            case LOITER -> {
                d.angle += speed / LOITER_RADIUS;
                double px = d.tx + 0.5 + Math.cos(d.angle) * LOITER_RADIUS, pz = d.tz + 0.5 + Math.sin(d.angle) * LOITER_RADIUS;
                flyTo(level, d, px, pz, speed * 1.5);
                if (now >= d.nextImage) image(server, d, now);
                if (--d.ticks <= 0) {
                    d.phase = RETURN;
                    tell(server, d.owner, Component.translatable("message.wmd.drone.home", d.id).withStyle(ChatFormatting.GRAY));
                }
            }
            case RETURN -> {
                if (flyTo(level, d, d.hx, d.hz, speed) <= 1.0) d.phase = LANDING;
            }
            case LANDING -> {
                d.vx = 0; d.vz = 0;
                d.y = Math.max(d.hy, d.y - ALT_RATE);
                d.vy = -ALT_RATE;
                if (d.y <= d.hy + 0.05 && level.getChunkSource().getChunkNow((int) Math.floor(d.hx) >> 4, (int) Math.floor(d.hz) >> 4) != null) {
                    ItemEntity it = new ItemEntity(level, d.hx, d.hy, d.hz, new ItemStack(Wmd.RECON_DRONE.get()));
                    it.setDeltaMovement(Vec3.ZERO);
                    level.addFreshEntity(it);
                    tell(server, d.owner, Component.translatable("message.wmd.drone.landed", d.id, Math.round(d.hx), Math.round(d.hz)).withStyle(ChatFormatting.AQUA));
                    Wmd.LOGGER.info("Recon drone #{} of {} landed", d.id, d.ownerName);
                    return true;
                }
            }
            case FALLING -> {
                d.vy -= GRAVITY;
                d.vx *= 0.98; d.vz *= 0.98;
                d.x += d.vx; d.y += d.vy; d.z += d.vz;
                int g = groundAt(level, d.x, d.z);
                if ((g != Integer.MIN_VALUE && d.y <= g) || ++d.ticks > 400 || d.y < level.getMinBuildHeight()) {
                    if (g != Integer.MIN_VALUE) {
                        level.sendParticles(ParticleTypes.LARGE_SMOKE, d.x, g + 0.5, d.z, 12, 0.4, 0.3, 0.4, 0.02);
                        level.sendParticles(ParticleTypes.EXPLOSION, d.x, g + 0.5, d.z, 1, 0.0, 0.0, 0.0, 0.0);
                        level.playSound(null, BlockPos.containing(d.x, g, d.z), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6F, 1.8F);
                    }
                    return true;
                }
            }
            default -> { return true; }
        }
        if (d.phase != FALLING && d.phase != CLIMB && d.phase != LANDING) d.vy = 0;
        return false;
    }

    private static double flyTo(ServerLevel level, Drone d, double px, double pz, double speed) {
        double dx = px - d.x, dz = pz - d.z, dist = Math.sqrt(dx * dx + dz * dz);
        double s = Math.min(speed, dist);
        d.vx = dist > 1.0E-6 ? dx / dist * s : 0.0;
        d.vz = dist > 1.0E-6 ? dz / dist * s : 0.0;
        d.x += d.vx; d.z += d.vz;
        if (s > 1.0E-3) d.yaw = (float) Math.toDegrees(Math.atan2(-d.vx, d.vz));
        int g = groundAt(level, d.x, d.z);
        if (g != Integer.MIN_VALUE) {
            double want = g + DRONE_CRUISE;
            d.y += Math.max(-ALT_RATE, Math.min(ALT_RATE * 2.0, want - d.y));
            if (d.y < g + 4.0) d.y = g + 4.0;
        }
        return Math.max(0.0, dist - s);
    }

    private void startLoiter(MinecraftServer server, Drone d, long now) {
        d.phase = LOITER;
        d.ticks = Balance.DRONE_LOITER_S * 20;
        d.angle = Math.atan2(d.z - (d.tz + 0.5), d.x - (d.tx + 0.5));
        d.nextImage = now;
        ReconData rd = ReconData.get(server);
        ReconData.Entry e = rd.entry(d.owner);
        int half = Recon.samples() * Math.max(1, Balance.RECON_SNAPSHOT_STEP) / 2, r = Balance.DRONE_RADIUS;
        if (e == null || !e.hasTarget || Math.abs(d.tx - e.tx) > half - r || Math.abs(d.tz - e.tz) > half - r) {
            Recon.cancel(d.owner);
            rd.setTarget(d.owner, d.tx, d.tz);
            tell(server, d.owner, Component.translatable("message.wmd.drone.retarget", d.tx, d.tz).withStyle(ChatFormatting.GRAY));
        }
        tell(server, d.owner, Component.translatable("message.wmd.drone.loiter", d.id, d.tx, d.tz, r, Balance.DRONE_LOITER_S).withStyle(ChatFormatting.AQUA));
    }

    private static void image(MinecraftServer server, Drone d, long now) {
        d.nextImage = now + IMAGE_EVERY_TICKS;
        ReconData.Entry e = ReconData.get(server).entry(d.owner);
        int half = Recon.samples() * Math.max(1, Balance.RECON_SNAPSHOT_STEP) / 2, r = Balance.DRONE_RADIUS;
        if (e == null || !e.hasTarget || Math.abs(d.tx - e.tx) > half - r || Math.abs(d.tz - e.tz) > half - r) return;
        ReconFill.area(server, d.owner, e, d.tx, d.tz, r);
        Recon.imageShips(server, d.owner, d.tx, d.tz, r);
    }

    static void tell(MinecraftServer server, UUID owner, Component c) {
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) p.sendSystemMessage(c);
    }

    private static void proxy(ServerLevel level, Drone d) {
        Entity e = d.entity != null ? level.getEntity(d.entity) : null;
        if (!level.isPositionEntityTicking(BlockPos.containing(d.x, d.y, d.z))) {
            if (e != null) e.discard();
            d.entity = null;
            return;
        }
        if (!(e instanceof ReconDroneEntity de) || e.isRemoved()) {
            ReconDroneEntity ne = d.kind == 0 ? new ReconDroneEntity(Wmd.RECON_DRONE_ENTITY.get(), level)
                    : new StrikeDroneEntity(d.kind >= 3 ? Wmd.BIG_DRONE_ENTITY.get() : Wmd.STRIKE_DRONE_ENTITY.get(), level);
            ne.setup(d.id, d.phase);
            if (ne instanceof StrikeDroneEntity se) se.setKind(d.kind);
            ne.moveTo(d.x, d.y, d.z, d.yaw, 0.0F);
            if (level.addFreshEntity(ne)) d.entity = ne.getUUID();
            return;
        }
        de.setPhase(d.phase);
        de.setPos(d.x, d.y, d.z);
        de.setYRot(d.yaw);
        de.setDeltaMovement(d.vx, d.vy, d.vz);
    }

    private static void dropEntity(ServerLevel level, Drone d) {
        Entity e = d.entity != null ? level.getEntity(d.entity) : null;
        if (e != null) e.discard();
        d.entity = null;
    }

    static void hurt(ServerLevel level, int id, float amount, Entity attacker) {
        Drones ds = peek(level.getServer());
        Drone d = ds == null ? null : ds.byId(id);
        if (d == null || !d.airborne() || amount <= 0.0F) return;
        UUID by = attacker != null ? attacker.getUUID() : null;
        String name = attacker instanceof ServerPlayer sp ? sp.getGameProfile().getName() : attacker != null ? attacker.getName().getString() : "?";
        ds.damage(level, d, amount, attacker instanceof ServerPlayer ? by : null, name);
    }

    void damage(ServerLevel level, Drone d, float amount, UUID shooter, String shooterName) {
        if (!d.airborne()) return;
        d.health -= amount;
        setDirty();
        if (d.health <= 0.0F) shootDown(level, d, shooter, shooterName);
    }

    void shootDown(ServerLevel level, Drone d, UUID shooter, String shooterName) {
        MinecraftServer server = level.getServer();
        if (d.kind != 0) StrikeDrones.endPilot(server, d);
        if (d.spotId >= 0) { LaserSpots.remove(d.spotId); d.spotId = -1; }
        d.phase = FALLING;
        d.ticks = 0;
        d.vy = 0.1;
        SHOTS.remove(d.id);
        SHOOTERS.remove(d.id);
        String x = Long.toString(Math.round(d.x)), z = Long.toString(Math.round(d.z)), who = shooterName == null || shooterName.isEmpty() ? "?" : shooterName;
        PacketDistributor.sendToPlayersNear(level, null, d.x, d.y, d.z, LaunchSoundPayload.range(LaunchSoundPayload.DRONE), new LaunchSoundPayload(LaunchSoundPayload.DRONE, d.x, d.y, d.z));
        if (level.getChunkSource().getChunkNow((int) Math.floor(d.x) >> 4, (int) Math.floor(d.z) >> 4) != null)
            level.sendParticles(ParticleTypes.LARGE_SMOKE, d.x, d.y, d.z, 8, 0.3, 0.3, 0.3, 0.02);
        CcBattleLog.add(server, d.owner, CcBattleLog.LOST, "log.wmd.drone_lost", x, z, who);
        tell(server, d.owner, Component.translatable(d.kind == 0 ? "message.wmd.drone.lost" : "message.wmd.hive_drone.lost", d.id, x, z, who).withStyle(ChatFormatting.RED));
        if (shooter != null && !shooter.equals(d.owner)) {
            CcBattleLog.add(server, shooter, CcBattleLog.INTERCEPT, "log.wmd.drone_kill", d.ownerName, x, z);
            tell(server, shooter, Component.translatable("message.wmd.drone.kill", d.ownerName, x, z).withStyle(ChatFormatting.GREEN));
        }
        setDirty();
        Wmd.LOGGER.info("Recon drone #{} of {} shot down near {} {} by {}", d.id, d.ownerName, x, z, who);
    }

    static Drone ciwsTarget(ServerLevel level, UUID owner, int frequency, Vec3 gun, double range, long now) {
        Drones ds = peek(level.getServer());
        if (ds == null || owner == null || level != level.getServer().overworld()) return null;
        Drone best = null;
        double bd = range * range;
        for (Drone d : ds.drones) {
            if (!d.airborne() || owner.equals(d.owner)) continue;
            double q = gun.distanceToSqr(d.x, d.y, d.z);
            if (q > bd || d.y < gun.y + 2.0 || !TrackNet.seesPoint(level, owner, frequency, d.x, d.z, now)) continue;
            bd = q;
            best = d;
        }
        return best;
    }

    static Vec3 lead(Drone d, double ticks) { return new Vec3(d.x + d.vx * ticks, d.y + d.vy * ticks, d.z + d.vz * ticks); }

    static void ciwsHit(ServerLevel level, Drone d, float dmg, UUID shooter, String shooterName) {
        Drones ds = peek(level.getServer());
        if (ds != null && ds.drones.contains(d)) ds.damage(level, d, dmg, shooter, shooterName);
    }

    static int[] siteKind(InterceptorSite s) {
        if (s instanceof InterceptorLauncherBlockEntity) return new int[] { 1, 0 };
        if (s instanceof InterceptorBatteryBlockEntity) return new int[] { 2, 3 };
        if (s instanceof InterceptorSiloBlockEntity) return new int[] { 3, 1 };
        if (s instanceof InterceptorSiloT4BlockEntity) return new int[] { 4, 2 };
        return null;
    }

    private void defence(ServerLevel level, long now) {
        List<InterceptorSite> sites = null;
        for (Drone d : drones) {
            if (!d.airborne() || SHOTS.containsKey(d.id)) continue;
            if (sites == null) sites = CommandCenter.allSites();
            for (InterceptorSite s : sites) {
                if (!(s instanceof BlockEntity be) || be.isRemoved() || be.getLevel() != level) continue;
                UUID o = s.getOwner();
                int[] k = siteKind(s);
                if (k == null || o == null || o.equals(d.owner) || s.loaded() <= 0 || s.cooldownTicks() > 0 || s.energy() < Balance.INT_SHOT_FE) continue;
                if (EmpField.jammed(level, be.getBlockPos())) continue;
                Vec3 from = SableBridge.worldPos(level, be.getBlockPos()).add(0.0, 2.0, 0.0);
                double dist = from.distanceTo(d.pos());
                if (dist > Interceptors.rangeOf(k[0], o) || !TrackNet.seesPoint(level, o, s.getFrequency(), d.x, d.z, now)) continue;
                if (!takeOne(s.magazineHandler())) continue;
                s.shipShot(Balance.INT_SHOT_FE, Balance.INT_RELOAD_TICKS);
                int dur = Math.max(10, (int) Math.ceil(dist / Balance.INT_SPEED[k[0] - 1]));
                Vec3 to = lead(d, dur);
                long shotId = FlightManager.get(level).nextShotId();
                SHOTS.put(d.id, new double[] { now + dur, to.x, to.y, to.z });
                SHOOTERS.put(d.id, new Object[] { o, s instanceof Owned ow ? ow.getOwnerName() : "?" });
                PacketDistributor.sendToPlayersNear(level, null, to.x, to.y, to.z, 6000, new InterceptPayload(shotId, from.x, from.y, from.z, to.x, to.y, to.z, now, dur, k[1]));
                Wmd.LOGGER.info("Interceptor (tier {}) fired at recon drone #{} of {}", k[0], d.id, d.ownerName);
                break;
            }
        }
    }

    static boolean takeOne(ItemStackHandler h) {
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty() && !h.extractItem(i, 1, false).isEmpty()) return true;
        return false;
    }

    private void shotsArrive(ServerLevel level, long now) {
        if (SHOTS.isEmpty()) return;
        for (Iterator<Map.Entry<Integer, double[]>> it = SHOTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, double[]> e = it.next();
            if (now < e.getValue()[0]) continue;
            it.remove();
            Object[] who = SHOOTERS.remove(e.getKey());
            Drone d = byId(e.getKey());
            if (d == null || !d.airborne()) continue;
            PacketDistributor.sendToPlayersNear(level, null, d.x, d.y, d.z, 6000, new InterceptFxPayload(d.x, d.y, d.z, true, 0));
            shootDown(level, d, who != null ? (UUID) who[0] : null, who != null ? (String) who[1] : "?");
        }
    }

    int killAll(ServerLevel level, int id, String by) {
        int n = 0;
        for (Drone d : new ArrayList<>(drones)) if (d.airborne() && (id <= 0 || d.id == id)) { shootDown(level, d, null, by); n++; }
        return n;
    }

    @SubscribeEvent
    static void onStopping(ServerStoppingEvent event) { SHOTS.clear(); SHOOTERS.clear(); VIEWS.clear(); DroneWarnings.clear(); }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag l = new ListTag();
        for (Drone d : drones) l.add(d.save());
        tag.put("drones", l);
        tag.putInt("nextId", nextId);
        return tag;
    }

    public static Drones load(CompoundTag tag, HolderLookup.Provider registries) {
        Drones ds = new Drones();
        for (Tag t : tag.getList("drones", Tag.TAG_COMPOUND)) { Drone d = Drone.load((CompoundTag) t); if (d != null) ds.drones.add(d); }
        ds.nextId = Math.max(1, tag.getInt("nextId"));
        return ds;
    }
}
