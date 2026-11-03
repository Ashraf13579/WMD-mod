package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SpecialShells {

    private SpecialShells() {}

    static boolean groundEffect(int type) {
        return type == Artillery.LASER || type == Artillery.ROCKET_ASSIST || type == Artillery.PIERCE || type == Artillery.INCENDIARY;
    }

    private static UUID owner(ArtShell s) {
        UUID o = s.owner();
        return o != null && (o.getMostSignificantBits() != 0L || o.getLeastSignificantBits() != 0L) ? o : null;
    }

    static void impact(ArtilleryManager mgr, ServerLevel level, FlightManager fm, ArtShell s, long now) {
        int gx = (int) Math.floor(s.tx()), gz = (int) Math.floor(s.tz());
        UUID o = owner(s);
        switch (s.type()) {
            case Artillery.LASER, Artillery.ROCKET_ASSIST -> {
                if (!s.prepared() && !level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) fm.holdForTest(level, gx, gz, ArtilleryManager.holdOf(s));
                int gy = s.type() == Artillery.LASER ? (int) Math.floor(s.ty()) : ArtilleryManager.ground(level, gx, gz, s.ty(), true);
                mgr.heBurstAt(level, fm, s, gx, gy, gz);
            }
            case Artillery.PIERCE, Artillery.INCENDIARY -> {
                if (!s.prepared() && !level.hasChunkAt(new BlockPos(gx, level.getMinBuildHeight(), gz))) fm.holdForTest(level, gx, gz, ArtilleryManager.holdOf(s));
                int gy = ArtilleryManager.ground(level, gx, gz, s.ty(), true);
                Vec3 v = s.velocity(s.endTime());
                UUID prev = ShipStrike.begin(o);
                try {
                    WarheadFx.detonate(level, fm, gx, gy, gz, 1, s.type() == Artillery.PIERCE ? WarheadType.BUNKER : WarheadType.INCENDIARY, v.x, v.z);
                } finally {
                    ShipStrike.end(prev);
                }
                if (s.type() == Artillery.INCENDIARY) thermite(level, gx, gy, gz);
                fm.setDirty();
                ArtilleryManager.sendImpact(level, s, gx + 0.5, gy, gz + 0.5);
            }
            case Artillery.SENSOR -> { sensor(level, fm, s, o); ArtilleryManager.sendImpact(level, s, s.tx(), s.ty(), s.tz()); }
            case Artillery.CAMERA -> { camera(level, s, o); ArtilleryManager.sendImpact(level, s, s.tx(), s.ty(), s.tz()); }
            case Artillery.JAMMING -> {
                UUID prev = ShipStrike.begin(o);
                try { WarheadFx.detonate(level, fm, gx, (int) Math.floor(s.ty()), gz, 1, WarheadType.CHAFF, 0.0, 0.0); }
                finally { ShipStrike.end(prev); }
                fm.setDirty();
                ArtilleryManager.sendImpact(level, s, s.tx(), s.ty(), s.tz());
            }
            case Artillery.LEAFLET -> { leaflet(level, s, mgr.takeLeaflet(s.id())); ArtilleryManager.sendImpact(level, s, s.tx(), s.ty(), s.tz()); }
            case Artillery.FLAK -> {
                Vec3 at = new Vec3(s.tx(), s.ty(), s.tz());
                burst(level, level == level.getServer().overworld() ? Drones.get(level.getServer()) : null, s, at, o);
                ArtilleryManager.sendImpact(level, s, at.x, at.y, at.z);
            }
            default -> ArtilleryManager.sendImpact(level, s, s.tx(), s.ty(), s.tz());
        }
    }

    private static void thermite(ServerLevel level, int gx, int gy, int gz) {
        int bottom = gy;
        for (int dy = 1; dy <= Balance.INCENDIARY_BURN; dy++) {
            BlockPos p = new BlockPos(gx, gy - dy, gz);
            BlockState st = level.getBlockState(p);
            if (st.isAir()) { bottom = gy - dy; continue; }

            if (!BlockRules.blastRemovable(level, p, st)) break;
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            bottom = gy - dy;
        }
        BlockPos fire = new BlockPos(gx, bottom, gz);
        if (BlockRules.blastMay(level, fire) && level.getBlockState(fire).isAir() && level.getBlockState(fire.below()).isFaceSturdy(level, fire.below(), net.minecraft.core.Direction.UP))
            level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
        level.sendParticles(ParticleTypes.LAVA, gx + 0.5, gy + 0.2, gz + 0.5, 12, 0.3, 0.1, 0.3, 0.0);
    }

    private static void sensor(ServerLevel level, FlightManager fm, ArtShell s, UUID o) {
        List<HeatLog.Hot> hot = new ArrayList<>(HeatLog.near(level, s.tx(), s.tz(), Balance.SENSOR_SCAN));
        hot.removeIf(h -> (o != null && o.equals(h.owner())) || HeatLog.hidden(level, h.pos()) || !level.isLoaded(h.pos()));
        hot.sort((a, b) -> Double.compare(a.pos().distToCenterSqr(s.tx(), s.ty(), s.tz()), b.pos().distToCenterSqr(s.tx(), s.ty(), s.tz())));
        level.sendParticles(ParticleTypes.POOF, s.tx(), s.ty(), s.tz(), 20, 1.0, 0.5, 1.0, 0.05);
        int n = 0;
        for (HeatLog.Hot h : hot) {
            if (n >= Balance.SENSOR_SUBS) break;
            Vec3 w = SableBridge.worldPos(level, h.pos());
            UUID prev = ShipStrike.begin(o);
            try { WarheadFx.detonate(level, fm, (int) Math.floor(w.x), (int) Math.floor(w.y) + 1, (int) Math.floor(w.z), 1, WarheadType.BREACHER, 0.0, -1.0); }
            finally { ShipStrike.end(prev); }
            n++;
        }
        fm.setDirty();
        if (o != null && level.getServer().getPlayerList().getPlayer(o) instanceof ServerPlayer p)
            p.displayClientMessage(Component.translatable(n > 0 ? "message.wmd.sensor.hits" : "message.wmd.sensor.none", n), true);
        Wmd.LOGGER.info("Sensor-fused shell #{} of {} burst at {} {}: {} war machine(s) struck", s.id(), s.ownerName(), Math.round(s.tx()), Math.round(s.tz()), n);
    }

    private static void leaflet(ServerLevel level, ArtShell s, String msg) {
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), s.tx(), s.ty(), s.tz(), 60, 6.0, 2.0, 6.0, 0.02);
        level.playSound(null, s.tx(), s.ty(), s.tz(), WmdSounds.FAIRING_POP.get(), SoundSource.BLOCKS, 3.0F, 1.1F);
        if (msg.isEmpty()) return;
        String from = s.ownerName() == null || s.ownerName().isEmpty() ? "?" : s.ownerName();

        var rnd = level.random;
        for (int i = 0; i < Balance.LEAFLET_DROPS; i++) {
            net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level, s.tx() + (rnd.nextDouble() - 0.5) * 10.0, s.ty(),
                    s.tz() + (rnd.nextDouble() - 0.5) * 10.0, LeafletItem.of(msg, from, 1));
            e.setDeltaMovement((rnd.nextDouble() - 0.5) * 0.3, 0.05, (rnd.nextDouble() - 0.5) * 0.3);
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        }
        for (ServerPlayer p : level.players()) {
            if (Math.hypot(p.getX() - s.tx(), p.getZ() - s.tz()) > Balance.LEAFLET_RADIUS) continue;
            p.sendSystemMessage(Component.translatable("message.wmd.leaflet", from, msg).withStyle(ChatFormatting.WHITE));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.wmd.leaflet.title")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(msg.length() > 60 ? msg.substring(0, 57) + "..." : msg)));
        }
        Wmd.LOGGER.info("Leaflet shell #{} of {} burst at {} {}", s.id(), from, Math.round(s.tx()), Math.round(s.tz()));
    }

    private static final Map<UUID, double[]> CAMS = new HashMap<>();

    private static void camera(ServerLevel level, ArtShell s, UUID o) {
        level.sendParticles(ParticleTypes.POOF, s.tx(), s.ty(), s.tz(), 10, 0.5, 0.3, 0.5, 0.02);
        if (o == null) return;
        CAMS.put(o, new double[] { s.tx(), s.tz(), System.currentTimeMillis() + Balance.CAMERA_SECONDS * 1000L });
        if (level.getServer().getPlayerList().getPlayer(o) instanceof ServerPlayer p)
            p.sendSystemMessage(Component.translatable("message.wmd.camera.down", Math.round(s.tx()), Math.round(s.tz()), Balance.CAMERA_SECONDS)
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wmdcam"))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.wmd.camera.click")))));
    }

    static double[] camera(ServerPlayer p) {
        double[] c = CAMS.get(p.getUUID());
        return c != null && System.currentTimeMillis() < c[2] ? c : null;
    }

    static boolean cameraSees(ServerPlayer p, int cx, int cz) {
        double[] c = camera(p);
        return c != null && Math.hypot(c[0] - cx, c[1] - cz) <= 8.0;
    }

    static void clear() { CAMS.clear(); }

    static Vec3 flakTick(ServerLevel level, ArtShell s, long now) {
        if (now < s.start() + 10 || now >= s.endTime()) return null;
        Vec3 pos = s.position(now);
        UUID o = owner(s);
        double f2 = Balance.FLAK_FUZE * Balance.FLAK_FUZE;
        boolean near = false;
        Drones ds = level == level.getServer().overworld() ? Drones.get(level.getServer()) : null;
        if (ds != null) for (Drones.Drone d : ds.drones()) if (d.airborne() && (o == null || !o.equals(d.owner)) && d.pos().distanceToSqr(pos) <= f2) { near = true; break; }
        if (!near) for (BombEntity b : BombEntity.FALLING) if (b.level() == level && !b.isRemoved() && (o == null || !o.equals(b.owner())) && b.position().distanceToSqr(pos) <= f2) { near = true; break; }
        if (!near && SableBridge.active()) {
            UUID own = s.gunPos() == 0L ? null : SableBridge.shipIdAt(level, BlockPos.of(s.gunPos()));
            for (SableBridge.ShipInfo sh : SableBridge.shipsIntersecting(level, new AABB(pos, pos).inflate(Balance.FLAK_FUZE)))
                if (!sh.id().equals(own)) { near = true; break; }
        }
        if (!near) return null;
        burst(level, ds, s, pos, o);
        ArtilleryManager.sendImpact(level, s, pos.x, pos.y, pos.z);
        return pos;
    }

    private static void burst(ServerLevel level, Drones ds, ArtShell s, Vec3 pos, UUID o) {
        double r2 = Balance.FLAK_RADIUS * Balance.FLAK_RADIUS;
        String name = s.ownerName() == null ? "?" : s.ownerName();
        if (ds != null) for (Drones.Drone d : ds.drones()) if (d.airborne() && (o == null || !o.equals(d.owner)) && d.pos().distanceToSqr(pos) <= r2) ds.damage(level, d, (float) Balance.FLAK_DRONE_DAMAGE, o, name);
        for (BombEntity b : new ArrayList<>(BombEntity.FALLING)) if (b.level() == level && !b.isRemoved() && (o == null || !o.equals(b.owner())) && b.position().distanceToSqr(pos) <= r2) b.shotDown(level, o, name);
        ShipStrike.blast(level, pos, Balance.FLAK_RADIUS, ShipStrike.powerForGun(Artillery.HOWITZER), o);
        WarheadFx.hurt(level, pos, 3.0, 2.0, 0.4, 0.5, 0.0, 0, o);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 24, 1.2, 1.2, 1.2, 0.02);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, pos.x, pos.y, pos.z, WmdSounds.FRAG_CRACK.get(), SoundSource.BLOCKS, 4.0F, 1.0F);
        level.playSound(null, pos.x, pos.y, pos.z, WmdSounds.HE_FAR.get(), SoundSource.BLOCKS, 6.0F, 1.2F);
    }

    static void canister(ServerLevel level, ArtilleryGunBlockEntity gun, Vec3 muzzle, Vec3 aim) {
        Vec3 dir = aim.subtract(muzzle).normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up).normalize(), up2 = side.cross(dir).normalize();
        double cone = Math.tan(Math.toRadians(Balance.CANISTER_CONE));
        UUID o = gun.getOwner();
        Entity shooter = o != null ? level.getServer().getPlayerList().getPlayer(o) : null;
        int hits = 0;
        for (int i = 0; i < Balance.CANISTER_FLECHETTES; i++) {
            double r = cone * Math.sqrt(level.random.nextDouble()), a = level.random.nextDouble() * Math.PI * 2.0;
            Vec3 d = dir.add(side.scale(Math.cos(a) * r)).add(up2.scale(Math.sin(a) * r)).normalize();
            Vec3 end = muzzle.add(d.scale(Balance.CANISTER_MAX));
            BlockHitResult bh = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
            if (bh.getType() == HitResult.Type.BLOCK) {
                end = bh.getLocation();
                BlockState st = level.getBlockState(bh.getBlockPos());
                if ((st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS) || st.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_PANES))
                        && BlockRules.blastMay(level, bh.getBlockPos())) level.destroyBlock(bh.getBlockPos(), false);
            }
            LivingEntity hit = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(muzzle, end).inflate(1.0))) {
                if (!e.isAlive()) continue;
                java.util.Optional<Vec3> c = e.getBoundingBox().inflate(0.2).clip(muzzle, end);
                if (c.isEmpty()) continue;
                double dd = c.get().distanceToSqr(muzzle);
                if (dd < best) { best = dd; hit = e; }
            }
            if (hit != null) {
                hit.hurt(level.damageSources().explosion(null, shooter), (float) Balance.CANISTER_DAMAGE);
                hits++;
                end = muzzle.add(d.scale(Math.sqrt(best)));
            }
            if (i % 4 == 0) for (int k = 1; k <= 6; k++) { Vec3 q = muzzle.lerp(end, k / 6.0); level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0); }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, muzzle.x, muzzle.y, muzzle.z, WmdSounds.FRAG_WHIZZ.get(), SoundSource.BLOCKS, 5.0F, 1.0F);
        Wmd.LOGGER.info("Canister shot of {} at {}: {} flechette hit(s)", gun.getOwnerName(), gun.getBlockPos().toShortString(), hits);
    }
}
