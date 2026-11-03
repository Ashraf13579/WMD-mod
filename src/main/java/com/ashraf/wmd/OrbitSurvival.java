package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class OrbitSurvival {

    private OrbitSurvival() {}

    static final ResourceKey<DamageType> VACUUM = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "vacuum"));
    static final ResourceKey<DamageType> REENTRY = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "reentry"));
    private static final ResourceLocation GRAV_ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "orbit_gravity");

    private static final Set<UUID> THRUSTING = new HashSet<>();
    private static final Map<UUID, Integer> THRUST_TICKS = new HashMap<>(), LOST = new HashMap<>();

    static void clearStatics() { THRUSTING.clear(); THRUST_TICKS.clear(); LOST.clear(); CABIN.clear(); }

    static void thrust(ServerPlayer p, boolean on) {
        if (!on) { THRUSTING.remove(p.getUUID()); return; }
        if (THRUSTING.add(p.getUUID()) && Orbit.isOrbit(p.level())) puff(p);
    }

    private static void puff(ServerPlayer p) {
        p.serverLevel().playSound(null, p.blockPosition(), WmdSounds.OR_EVA_THRUST.get(), SoundSource.PLAYERS, 0.7F, 0.95F + p.getRandom().nextFloat() * 0.1F);
    }

    static boolean inAir(ServerPlayer p) {
        return (p.getVehicle() != null && p.getVehicle().getPersistentData().getBoolean("wmd_air")) || StationLife.cabinAir(p);
    }

    private static final Set<UUID> CABIN = new HashSet<>();

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean orbit = Orbit.isOrbit(p.level());
        gravity(p, orbit && !p.isSpectator() && !magnetic(p) && StationLife.inside(p) == null);
        if (!orbit) { THRUSTING.remove(p.getUUID()); LOST.remove(p.getUUID()); if (CABIN.remove(p.getUUID())) PacketDistributor.sendToPlayer(p, new StationCabinPayload(false)); return; }
        if (p.tickCount % 10 == 0) {
            boolean cabin = inAir(p);
            if (cabin != CABIN.contains(p.getUUID())) { if (cabin) CABIN.add(p.getUUID()); else CABIN.remove(p.getUUID()); PacketDistributor.sendToPlayer(p, new StationCabinPayload(cabin)); }
        }
        if (p.isCreative() || p.isSpectator()) { LOST.remove(p.getUUID()); return; }
        ServerLevel sl = p.serverLevel();
        boolean thrusting = THRUSTING.contains(p.getUUID()) && EvaSuitItem.fullSuit(p) && !p.onGround();
        if (thrusting) {
            THRUST_TICKS.merge(p.getUUID(), 1, Integer::sum);
            if (p.tickCount % 24 == 0) puff(p);
            if (p.tickCount % 2 == 0) {
                Vec3 back = Vec3.directionFromRotation(0.0F, p.yBodyRot).scale(-0.45);
                sl.sendParticles(ParticleTypes.CLOUD, p.getX() + back.x, p.getY() + 1.0, p.getZ() + back.z, 2, 0.15, 0.25, 0.15, 0.01);
            }
        }
        if (p.tickCount % 20 != 0) return;

        ItemStack torso = EvaSuitItem.torso(p);
        boolean air = inAir(p), suit = EvaSuitItem.fullSuit(p);
        int o2 = EvaSuitItem.oxygen(torso);
        if (air) {
            if (!torso.isEmpty()) EvaSuitItem.setOxygen(torso, o2 + Balance.O2_CABIN_REFILL);
        } else if (suit && o2 > 0) {
            int t = THRUST_TICKS.getOrDefault(p.getUUID(), 0);
            THRUST_TICKS.remove(p.getUUID());
            int use = Balance.O2_BREATH + t * (Balance.O2_THRUST_MULT - 1);
            int left = Math.max(0, o2 - use);
            EvaSuitItem.setOxygen(torso, left);
            warn(p, o2, left, 6_000, "message.wmd.oxygen.warn5", ChatFormatting.YELLOW);
            warn(p, o2, left, 1_200, "message.wmd.oxygen.warn1", ChatFormatting.GOLD);
            warn(p, o2, left, 200, "message.wmd.oxygen.warn10", ChatFormatting.RED);
            if (left == 0) p.displayClientMessage(Component.translatable("message.wmd.oxygen.empty").withStyle(ChatFormatting.DARK_RED), true);
        } else {
            p.hurt(new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VACUUM)), Balance.VACUUM_DAMAGE);
            p.displayClientMessage(Component.translatable(suit ? "message.wmd.oxygen.empty" : "message.wmd.vacuum.nosuit").withStyle(ChatFormatting.DARK_RED), true);
        }
        lost(p, sl);
    }

    private static void warn(ServerPlayer p, int before, int after, int at, String key, ChatFormatting colour) {
        if (before > at && after <= at) {
            p.displayClientMessage(Component.translatable(key).withStyle(colour), true);
            p.serverLevel().playSound(null, p.blockPosition(), WmdSounds.OR_SUIT_ALARM.get(), SoundSource.PLAYERS, 0.9F, at <= 200 ? 1.15F : 1.0F);
        }
    }

    private static void lost(ServerPlayer p, ServerLevel sl) {
        int n = (int) Math.round(p.getX() / Balance.ORBIT_SLOT_SPACING);
        double dx = Math.abs(p.getX() - (double) n * Balance.ORBIT_SLOT_SPACING), dz = Math.abs(p.getZ());
        boolean out = n < 0 || dx > Balance.ORBIT_LOST_R || dz > Balance.ORBIT_LOST_R || p.getY() < Balance.ORBIT_LOST_Y;
        if (!out) {
            if (LOST.remove(p.getUUID()) != null) p.displayClientMessage(Component.translatable("message.wmd.orbit.safe").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        Integer cur = LOST.get(p.getUUID());
        int left = cur == null ? Balance.ORBIT_LOST_SECONDS : cur;
        if (left <= 0) {
            LOST.remove(p.getUUID());
            p.hurt(new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(REENTRY)), Float.MAX_VALUE);
            return;
        }
        p.displayClientMessage(Component.translatable("message.wmd.orbit.lost", left).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        sl.playSound(null, p.blockPosition(), WmdSounds.OR_LOST_ALARM.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        LOST.put(p.getUUID(), left - 1);
    }

    static boolean magnetic(ServerPlayer p) {
        if (!EvaSuitItem.suitBoots(p) || p.getDeltaMovement().y > 0.15) return false;
        BlockPos f = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
        return !p.level().getBlockState(f).getCollisionShape(p.level(), f).isEmpty() || !p.level().getBlockState(f.below()).getCollisionShape(p.level(), f.below()).isEmpty();
    }

    private static void gravity(ServerPlayer p, boolean low) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g == null) return;
        boolean has = g.hasModifier(GRAV_ID);
        if (low && !has) g.addTransientModifier(new AttributeModifier(GRAV_ID, Balance.ORBIT_GRAVITY - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!low && has) g.removeModifier(GRAV_ID);
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (Orbit.isOrbit(event.getEntity().level())) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        THRUSTING.remove(id); THRUST_TICKS.remove(id); LOST.remove(id);
    }
}
