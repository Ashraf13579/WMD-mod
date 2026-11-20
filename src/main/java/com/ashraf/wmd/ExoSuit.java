package com.ashraf.wmd;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Wmd.MODID)
public final class ExoSuit {

    private ExoSuit() {}

    public static final int BOOST = 1, HOVER_ON = 2, HOVER_OFF = 3, LEAP = 4, BRAKE = 5, CROUCH_ON = 6, CROUCH_OFF = 7, EMP = 8;

    private static final Map<UUID, Long> HOVER = Statics.server(new HashMap<>());
    private static final Map<UUID, Long> COOL = Statics.server(new HashMap<>());
    private static final Map<UUID, Long> LOCK = Statics.server(new HashMap<>());

    static boolean locked(ServerPlayer p, long now) { Long l = LOCK.get(p.getUUID()); return l != null && l > now; }
    private static final Map<UUID, Integer> SPENT = Statics.server(new HashMap<>());
    private static final Set<UUID> CROUCH = Statics.server(new HashSet<>());
    private static final Map<UUID, Long> BRAKE_COOL = Statics.server(new HashMap<>());

    private static final ResourceLocation MELEE = id("exo_melee"), MINING = id("exo_mining"), STEP = id("exo_step"), REACH_B = id("exo_reach_block"),
            REACH_E = id("exo_reach_entity"), SLOW = id("exo_dead_weight");

    private static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, p); }

    static boolean free(Player p) { return p.isCreative() || p.isSpectator(); }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean any = ExoSuitItem.anyPiece(p);
        boolean full = any && ExoSuitItem.fullSet(p);
        ItemStack chest = ExoSuitItem.chest(p);
        ServerLevel sl = p.serverLevel();
        long now = sl.getGameTime();
        UUID u = p.getUUID();
        if (!full) {
            perks(p, false, false);
            if (HOVER.remove(u) != null) fx(p, HOVER_OFF);
            if (CROUCH.remove(u)) fx(p, CROUCH_OFF);
            return;
        }
        int fe = ExoSuitItem.fe(chest);
        Long lock = LOCK.get(u);
        if (lock != null && lock <= now) { LOCK.remove(u); lock = null; }
        if (lock != null && fe > 0) { ExoSuitItem.setFe(chest, 0); fe = 0; }
        if (now % 20 == 0 && EmpField.jammed(sl, p.blockPosition())) { empHit(p, now); fe = 0; }

        Long hs = HOVER.get(u);
        if (hs != null) {
            boolean stop = p.onGround() || now - hs >= Balance.EXO_HOVER_MAX || fe <= 0;
            if (stop) endHover(p, now);
            else if (!free(p)) SPENT.merge(u, Balance.EXO_HOVER_FE, Integer::sum);
        }
        if (now % 20 == 0 && !free(p)) {
            int use = (p.isSprinting() ? Balance.EXO_DRAIN_SPRINT : Balance.EXO_DRAIN) * 20 + SPENT.getOrDefault(u, 0);
            SPENT.remove(u);
            if (fe > 0) {
                int left = Math.max(0, fe - use);
                ExoSuitItem.setFe(chest, left);
                if (fe > Balance.EXO_FE_CAP * Balance.EXO_LOW && left <= Balance.EXO_FE_CAP * Balance.EXO_LOW)
                    p.displayClientMessage(Component.translatable("message.wmd.exo_suit.low").withStyle(ChatFormatting.GOLD), true);
                if (left == 0) p.displayClientMessage(Component.translatable("message.wmd.exo_suit.empty").withStyle(ChatFormatting.RED), true);
                fe = left;
            }
        }
        perks(p, fe > 0, true);
    }

    private static void endHover(ServerPlayer p, long now) {
        Long hs = HOVER.remove(p.getUUID());
        if (hs == null) return;
        long used = Math.max(1L, now - hs);
        COOL.put(p.getUUID(), now + Math.min(Balance.EXO_HOVER_COOL, used));
        fx(p, HOVER_OFF);
    }

    private static void perks(ServerPlayer p, boolean powered, boolean full) {
        mod(p, Attributes.ATTACK_DAMAGE, MELEE, powered, Balance.EXO_MELEE, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.BLOCK_BREAK_SPEED, MINING, powered, Balance.EXO_MINING, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(p, Attributes.STEP_HEIGHT, STEP, powered, Balance.EXO_STEP, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.BLOCK_INTERACTION_RANGE, REACH_B, powered, Balance.EXO_REACH, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_E, powered, Balance.EXO_REACH, AttributeModifier.Operation.ADD_VALUE);
        mod(p, Attributes.MOVEMENT_SPEED, SLOW, full && !powered && !free(p), Balance.EXO_EMPTY_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void mod(ServerPlayer p, Holder<Attribute> a, ResourceLocation id, boolean on, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(a);
        if (inst == null) return;
        boolean has = inst.hasModifier(id);
        if (on && !has) inst.addTransientModifier(new AttributeModifier(id, v, op));
        else if (!on && has) inst.removeModifier(id);
    }

    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !ExoSuitItem.powered(p)) return;
        float d = event.getDistance();
        if (d <= Balance.EXO_FALL_FREE) { event.setDistance(0.0F); return; }
        if (free(p)) { event.setDistance(0.0F); return; }
        ItemStack chest = ExoSuitItem.chest(p);
        int fe = ExoSuitItem.fe(chest);
        float over = d - Balance.EXO_FALL_FREE;
        int cost = (int) Math.ceil(over * Balance.EXO_FALL_FE);
        if (fe >= cost) {
            ExoSuitItem.setFe(chest, fe - cost);
            event.setDistance(0.0F);
        } else {
            ExoSuitItem.setFe(chest, 0);
            event.setDistance(d - fe / (float) Balance.EXO_FALL_FE);
        }
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.EXO_SERVO.get(), SoundSource.PLAYERS, Balance.SND_SMALL, 1.0F);
    }

    static void action(ServerPlayer p, int a) {
        if (p.isSpectator() || !p.isAlive()) return;
        long now = p.serverLevel().getGameTime();
        UUID u = p.getUUID();
        if (a == CROUCH_ON) { if (ExoSuitItem.powered(p) && CROUCH.add(u)) fx(p, a); return; }
        if (a == CROUCH_OFF) { if (CROUCH.remove(u)) fx(p, a); return; }
        if (a == HOVER_OFF) { endHover(p, now); return; }
        if (!ExoSuitItem.powered(p) || LOCK.containsKey(u)) return;
        ItemStack chest = ExoSuitItem.chest(p);
        int fe = ExoSuitItem.fe(chest);
        switch (a) {
            case BOOST, LEAP -> {
                int cost = a == BOOST ? Balance.EXO_BOOST_FE : Balance.EXO_LEAP_FE;
                if (!free(p)) ExoSuitItem.setFe(chest, fe - Math.min(fe, cost));
                p.resetFallDistance();
                fx(p, a);
            }
            case HOVER_ON -> {
                if (COOL.getOrDefault(u, Long.MIN_VALUE) > now || HOVER.containsKey(u)) return;
                HOVER.put(u, now);
                p.resetFallDistance();
                fx(p, a);
            }
            case BRAKE -> { if (now - BRAKE_COOL.getOrDefault(u, Long.MIN_VALUE) >= 10L) { BRAKE_COOL.put(u, now); fx(p, a); } }
            default -> {}
        }
    }

    public static void empHit(ServerPlayer p, long now) {
        if (!ExoSuitItem.anyPiece(p)) return;
        ItemStack chest = ExoSuitItem.chest(p);
        boolean was = ExoSuitItem.fe(chest) > 0 || !LOCK.containsKey(p.getUUID());
        ExoSuitItem.setFe(chest, 0);
        LOCK.put(p.getUUID(), now + Balance.EXO_EMP_LOCK);
        HOVER.remove(p.getUUID());
        if (was) {
            fx(p, EMP);
            p.displayClientMessage(Component.translatable("message.wmd.exo_suit.emp", Balance.EXO_EMP_LOCK / 20).withStyle(ChatFormatting.RED), true);
        }
    }

    static void fx(ServerPlayer p, int a) {
        ExoFxPayload pay = new ExoFxPayload(p.getId(), a);
        for (ServerPlayer o : p.serverLevel().players())
            if ((o != p || a == EMP || a >= ExoMods.THERMAL_ON) && o.distanceToSqr(p) <= Balance.EXO_FX_RANGE * Balance.EXO_FX_RANGE) PacketDistributor.sendToPlayer(o, pay);
    }

    @SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID u = event.getEntity().getUUID();
        HOVER.remove(u); COOL.remove(u); SPENT.remove(u); CROUCH.remove(u); BRAKE_COOL.remove(u);
    }
}
