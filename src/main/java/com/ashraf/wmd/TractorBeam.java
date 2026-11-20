package com.ashraf.wmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

@net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
public final class TractorBeam {

    private TractorBeam() {}

    public static final int OFF = 0, SEARCH = 1, HOLD = 2, PULL = 3, THROW = 4;

    static final class Hold {
        int entity = -1;
        UUID ship;
        double dist;
        int sent = -1;
        long sentAt;
    }

    private static final Map<UUID, Hold> HOLDS = Statics.server(new HashMap<>());

    static void tick(ServerPlayer p, ItemStack stack) {
        ServerLevel sl = p.serverLevel();
        Hold h = HOLDS.computeIfAbsent(p.getUUID(), u -> new Hold());
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        if (h.entity < 0 && h.ship == null) {
            if (!HandTech.use(stack, Balance.TRB_SEARCH_FE)) { empty(p); return; }
            acquire(sl, p, h, eye, look);
            if (h.entity < 0 && h.ship == null) { fx(p, h, SEARCH, eye.add(look.scale(Balance.TRB_RANGE)), -1); return; }
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.GR_OPEN.get(), SoundSource.PLAYERS, Balance.SND_CLICK, 1.4F);
        }
        boolean pulling = p.isCrouching();
        if (h.entity >= 0) {
            Entity e = sl.getEntity(h.entity);
            if (e == null || !e.isAlive() || !allowed(sl, p, e) || e.distanceTo(p) > Balance.TRB_RANGE + Balance.TRB_SLACK) { drop(p); return; }
            if (!HandTech.use(stack, Balance.TRB_HOLD_FE)) { empty(p); return; }
            if (pulling) h.dist = Math.max(Balance.TRB_PULL_MIN, h.dist - Balance.TRB_PULL_RATE);
            Vec3 want = eye.add(look.scale(h.dist));
            Vec3 c = e.getBoundingBox().getCenter();
            Vec3 v = want.subtract(c).scale(Balance.TRB_HOLD_K);
            if (v.length() > Balance.TRB_HOLD_VMAX) v = v.normalize().scale(Balance.TRB_HOLD_VMAX);
            e.setDeltaMovement(v.add(0.0, 0.04, 0.0));
            e.hurtMarked = true;
            e.resetFallDistance();
            fx(p, h, pulling ? PULL : HOLD, c, e.getId());
            return;
        }
        SableBridge.ShipInfo s = SableBridge.shipInfoFresh(sl, h.ship);
        if (!craftAllowed(sl, p, s) || s.blockCount() > Balance.TRB_CRAFT_MAX_BLOCKS || s.worldCenter().distanceTo(eye) > Balance.TRB_RANGE + Balance.TRB_SLACK + halfSize(s)) { drop(p); return; }
        if (!HandTech.use(stack, Balance.TRB_TUG_FE)) { empty(p); return; }
        if (pulling) h.dist = Math.max(Balance.TRB_PULL_MIN + halfSize(s), h.dist - Balance.TRB_PULL_RATE);
        Vec3 want = eye.add(look.scale(h.dist));
        Vec3 vWant = want.subtract(s.worldCenter()).scale(Balance.TRB_TUG_GAIN);
        if (vWant.length() > Balance.TRB_TUG_VMAX) vWant = vWant.normalize().scale(Balance.TRB_TUG_VMAX);
        Vec3 dv = vWant.subtract(s.linearVelocity());
        double max = Balance.TRB_TUG_DV * massScale(s);
        if (dv.length() > max) dv = dv.normalize().scale(max);
        SableBridge.shipPush(sl, h.ship, dv, Vec3.ZERO);
        fx(p, h, pulling ? PULL : HOLD, s.worldCenter(), -1);
    }

    static double massScale(SableBridge.ShipInfo s) { return Math.min(1.0, Balance.TRB_TUG_REF / Math.max(1.0, s.blockCount())); }

    static double halfSize(SableBridge.ShipInfo s) {
        AABB b = s.worldBounds();
        return b == null ? 2.0 : Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize())) / 2.0;
    }

    private static void acquire(ServerLevel sl, ServerPlayer p, Hold h, Vec3 eye, Vec3 look) {
        Vec3 end = eye.add(look.scale(Balance.TRB_RANGE));
        Vec3 wall = SableBridge.clipWorld(sl, new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p), null).getLocation();
        double best = wall.distanceTo(eye);
        Entity pick = null;
        for (Entity e : sl.getEntities(p, new AABB(eye, end).inflate(Balance.TRB_AIM_GROW + 1.0), e -> allowed(sl, p, e))) {
            var hit = e.getBoundingBox().inflate(Balance.TRB_AIM_GROW).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceTo(eye);
            if (d < best) { best = d; pick = e; }
        }
        UUID ship = null;
        List<SableBridge.ShipInfo> ships = SableBridge.shipsAlongSegment(sl, eye, end);
        if (!ships.isEmpty()) {
            ProjectilePath.PathHit ph = SableBridge.exactHit(sl, eye, end, ships);
            if (ph != null && ph.shipId() != null && ph.pos().distanceTo(eye) < best + 0.5) {
                SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ph.shipId());
                if (s != null && s.blockCount() <= Balance.TRB_CRAFT_MAX_BLOCKS) {
                    if (craftAllowed(sl, p, s)) { ship = s.id(); pick = null; best = ph.pos().distanceTo(eye); }
                }
                else if (s != null) { p.displayClientMessage(Component.translatable("message.wmd.tractor_beam.too_heavy", s.blockCount(), Balance.TRB_CRAFT_MAX_BLOCKS).withStyle(ChatFormatting.YELLOW), true); return; }
            }
        }
        if (pick != null) { h.entity = pick.getId(); h.dist = Math.max(Balance.TRB_PULL_MIN, pick.getBoundingBox().getCenter().distanceTo(eye)); }
        else if (ship != null) {
            SableBridge.ShipInfo s = SableBridge.shipInfo(sl, ship);
            h.ship = ship;
            h.dist = s == null ? best : Math.max(Balance.TRB_PULL_MIN + halfSize(s), s.worldCenter().distanceTo(eye));
        }
    }

    static boolean allowed(ServerLevel sl, ServerPlayer p, Entity e) {
        if (e == p || !e.isAlive() || e.isSpectator() || e.isPassenger() || e instanceof HangingEntity) return false;
        if (e.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) return false;
        if (Wmd.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace())) return false;
        if (e instanceof Player o) {
            if (o.isCreative() || !sl.getServer().isPvpAllowed()) return false;
            return !ClaimGuard.protectedAt(sl, o.blockPosition());
        }
        if (e instanceof LivingEntity || e instanceof VehicleEntity || e instanceof PrimedTnt) {
            return ClaimGuard.mayPlaceQuiet(sl, p, e.blockPosition());
        }
        return e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof FallingBlockEntity || e instanceof Projectile;
    }

    static boolean craftAllowed(ServerLevel sl, ServerPlayer p, SableBridge.ShipInfo s) {
        if (s == null || s.worldCenter() == null) return false;
        if (!ClaimGuard.mayPlaceQuiet(sl, p, net.minecraft.core.BlockPos.containing(s.worldCenter()))) return false;
        UUID owner = Crafts.owner(sl, s.id());

        return owner == null || ShipStrike.sameSide(owner, p.getUUID()) || sl.getServer().isPvpAllowed();
    }

    static void fling(ServerPlayer p) {
        Hold h = HOLDS.get(p.getUUID());
        ItemStack s = p.getUseItem();
        if (p.isSpectator() || h == null || (h.entity < 0 && h.ship == null) || !(s.getItem() instanceof TractorBeamItem) || p.getCooldowns().isOnCooldown(s.getItem())) return;
        if (!HandTech.use(s, Balance.TRB_THROW_FE)) { empty(p); return; }
        ServerLevel sl = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 at = p.getEyePosition();
        if (h.entity >= 0) {
            Entity e = sl.getEntity(h.entity);
            if (e != null && e.isAlive() && allowed(sl, p, e) && e.distanceTo(p) <= Balance.TRB_RANGE + Balance.TRB_SLACK) {
                e.setDeltaMovement(look.scale(Balance.TRB_THROW_SPEED).add(0.0, 0.15, 0.0));
                e.hurtMarked = true;
                if (e instanceof LivingEntity le) le.setLastHurtByPlayer(p);
                at = e.getBoundingBox().getCenter();
            }
        } else {
            SableBridge.ShipInfo si = SableBridge.shipInfoFresh(sl, h.ship);
            if (craftAllowed(sl, p, si)) {
                SableBridge.shipPush(sl, h.ship, look.scale(Balance.TRB_THROW_CRAFT * massScale(si)), Vec3.ZERO);
                at = si.worldCenter();
            }
        }
        fx(p, h, THROW, at, h.entity);
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.TB_THROW.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.0F);
        p.getCooldowns().addCooldown(s.getItem(), Balance.TRB_COOLDOWN);
        HOLDS.remove(p.getUUID());
        p.stopUsingItem();
    }

    public static void drop(ServerPlayer p) {
        Hold h = HOLDS.remove(p.getUUID());
        if (h != null) send(p, OFF, Vec3.ZERO, -1);
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        HOLDS.remove(event.getEntity().getUUID());
    }

    private static void empty(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("message.wmd.hand_tool.empty").withStyle(ChatFormatting.YELLOW), true);
        drop(p);
        p.stopUsingItem();
    }

    private static void fx(ServerPlayer p, Hold h, int state, Vec3 at, int target) {
        long now = p.serverLevel().getGameTime();
        if (state == h.sent && now - h.sentAt < Balance.TRB_FX_EVERY) return;
        h.sent = state; h.sentAt = now;
        send(p, state, at, target);
    }

    private static void send(ServerPlayer p, int state, Vec3 at, int target) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new HandToolFxPayload(p.getId(), HandToolFxPayload.TRACTOR, state, (float) at.x, (float) at.y, (float) at.z, target, 0.0F));
    }
}
