package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

@net.neoforged.fml.common.EventBusSubscriber(modid = Wmd.MODID)
public final class PlasmaCutter {

    private PlasmaCutter() {}

    public static final int OFF = 0, CUT = 1, BREACH = 2, OVERHEAT = 3, ARC = 4;

    static final class Cut {
        BlockPos at;
        boolean breach;
        final List<BlockPos> cells = new ArrayList<>();
        int idx;
        float progress, need;
        int sent = -1;
        long sentAt;
    }

    private static final Map<UUID, Cut> CUTS = Statics.server(new HashMap<>());

    static void tick(ServerPlayer p, ItemStack stack) {
        ServerLevel sl = p.serverLevel();
        if (HandTech.overheated(stack)) { stop(p); p.stopUsingItem(); return; }
        boolean breach = p.isCrouching();
        if (!HandTech.use(stack, breach ? Balance.PCU_BREACH_FE : Balance.PCU_CUT_FE)) {
            p.displayClientMessage(Component.translatable("message.wmd.hand_tool.empty").withStyle(ChatFormatting.YELLOW), true);
            stop(p); p.stopUsingItem(); return;
        }
        int heat = HandTech.heat(stack) + (breach ? Balance.PCU_HEAT_BREACH : Balance.PCU_HEAT_CUT);
        if (heat >= Balance.PCU_HEAT_MAX) {
            HandTech.setHeat(stack, Balance.PCU_HEAT_MAX + Balance.PCU_OVERHEAT_TICKS);
            sl.playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.RG_VENT.get(), SoundSource.PLAYERS, Balance.SND_MECH, 1.5F);
            p.displayClientMessage(Component.translatable("message.wmd.plasma_cutter.overheated").withStyle(ChatFormatting.RED), true);
            Cut c = CUTS.remove(p.getUUID());
            clearCrack(sl, p, c);
            send(p, OVERHEAT, p.getEyePosition(), 0.0F);
            p.stopUsingItem();
            return;
        }
        HandTech.setHeat(stack, heat);
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(Balance.PCU_REACH));
        Cut c = CUTS.computeIfAbsent(p.getUUID(), u -> new Cut());
        BlockHitResult hit = sl.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        Vec3 spot = SableBridge.clipWorld(sl, new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p), null).getLocation();
        burn(sl, p, eye, look, Math.min(Balance.PCU_BURN_REACH, spot.distanceTo(eye)));
        if (hit.getType() != HitResult.Type.BLOCK) { reset(sl, p, c); fx(p, c, ARC, end, 0.0F); return; }
        BlockPos at = hit.getBlockPos();
        boolean inPlan = breach && c.breach && c.cells.contains(at);
        if ((!at.equals(c.at) && !inPlan) || breach != c.breach) {
            reset(sl, p, c);
            c.at = at; c.breach = breach;
            if (breach) c.cells.addAll(doorway(sl, p, at, hit.getDirection()));
            else if (cuttable(sl, p, at)) c.cells.add(at);
            if (c.cells.isEmpty()) {
                p.displayClientMessage(Component.translatable("message.wmd.plasma_cutter.cannot").withStyle(ChatFormatting.YELLOW), true);
            } else {
                c.need = cutTicks(sl, c.cells.get(0)) * (breach ? Balance.PCU_BREACH_MULT : 1.0F);
                sl.playSound(null, p.getX(), p.getY(), p.getZ(), WmdSounds.PC_IGNITE.get(), SoundSource.PLAYERS, Balance.SND_CLICK, 1.0F);
            }
        }
        if (c.idx >= c.cells.size()) { fx(p, c, ARC, spot, 1.0F); return; }
        BlockPos cell = c.cells.get(c.idx);
        if (!stillCuttable(sl, cell)) { next(sl, p, c); fx(p, c, breach ? BREACH : CUT, spot, 0.0F); return; }
        c.progress += 1.0F;
        float k = Math.min(1.0F, c.progress / Math.max(1.0F, c.need));
        sl.destroyBlockProgress(crackId(p), cell, Math.min(9, (int) (k * 10.0F)));
        if (c.progress >= c.need) {
            melt(sl, p, cell, breach);
            next(sl, p, c);
        }
        fx(p, c, breach ? BREACH : CUT, spot, k);
    }

    private static void melt(ServerLevel sl, ServerPlayer p, BlockPos cell, boolean breach) {
        if (!BlockRules.blastMay(sl, cell) || !cuttable(sl, p, cell)) return;
        BlockState st = sl.getBlockState(cell);
        if (breach) {
            sl.levelEvent(2001, cell, net.minecraft.world.level.block.Block.getId(st));
            sl.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
        } else {
            sl.destroyBlock(cell, true, p);
        }
    }

    private static void next(ServerLevel sl, ServerPlayer p, Cut c) {
        sl.destroyBlockProgress(crackId(p), c.cells.get(Math.min(c.idx, c.cells.size() - 1)), -1);
        c.idx++;
        c.progress = 0.0F;
        if (c.idx < c.cells.size()) c.need = cutTicks(sl, c.cells.get(c.idx)) * (c.breach ? Balance.PCU_BREACH_MULT : 1.0F);
    }

    private static void reset(ServerLevel sl, ServerPlayer p, Cut c) {
        clearCrack(sl, p, c);
        c.at = null; c.cells.clear(); c.idx = 0; c.progress = 0.0F;
    }

    private static void clearCrack(ServerLevel sl, ServerPlayer p, Cut c) {
        if (c != null && c.idx < c.cells.size()) sl.destroyBlockProgress(crackId(p), c.cells.get(c.idx), -1);
    }

    private static int crackId(ServerPlayer p) { return 0x50430000 ^ p.getId(); }

    static float cutTicks(ServerLevel sl, BlockPos pos) {
        BlockState s = sl.getBlockState(pos);
        float h = Math.max(0.0F, s.getDestroySpeed(sl, pos));
        float t = (float) (Balance.PCU_BASE_TICKS * Math.pow(Math.max(0.1F, h) / 1.5F, Balance.PCU_HARD_EXP));
        return Math.max(2.0F, t) + Balance.PCU_HARDENED_TICKS * Hardened.rating(sl, pos, s);
    }

    static boolean cuttable(ServerLevel sl, ServerPlayer p, BlockPos pos) {
        BlockState s = sl.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(sl, pos) < 0.0F) return false;
        if (!BlockRules.blastMay(sl, pos) || !ClaimGuard.mayPlaceQuiet(sl, p, pos)) return false;
        if (p.isSpectator()) return false;
        GameType mode = p.gameMode.getGameModeForPlayer();
        if (mode == GameType.ADVENTURE || p.blockActionRestricted(sl, pos, mode)) return false;
        SableBridge.ShipInfo ship = SableBridge.shipAt(sl, pos);
        if (ship != null) {
            if (!TractorBeam.craftAllowed(sl, p, ship) || !BlockRules.blastMay(sl, BlockPos.containing(ship.worldCenter()))) return false;
        }
        return !net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(sl, mode, p, pos, s).isCanceled();
    }

    static boolean stillCuttable(ServerLevel sl, BlockPos pos) {
        BlockState s = sl.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.getDestroySpeed(sl, pos) < 0.0F) return false;
        return BlockRules.blastMay(sl, pos);
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Cut c = CUTS.remove(sp.getUUID());
            clearCrack(sp.serverLevel(), sp, c);
        }
    }

    static List<BlockPos> doorway(ServerLevel sl, ServerPlayer p, BlockPos at, Direction face) {
        Direction in = face.getOpposite();
        Direction second = face.getAxis().isHorizontal() ? Direction.UP : p.getDirection();
        List<BlockPos> out = new ArrayList<>();
        for (int d = 0; d < Balance.PCU_BREACH_DEPTH; d++) {
            BlockPos a = at.relative(in, d);
            for (BlockPos q : new BlockPos[] { a, a.relative(second) })
                if (cuttable(sl, p, q)) out.add(q);
        }
        return out;
    }

    private static void burn(ServerLevel sl, ServerPlayer p, Vec3 eye, Vec3 look, double reach) {
        if (sl.getGameTime() % Balance.PCU_DMG_EVERY != 0) return;
        Vec3 end = eye.add(look.scale(reach));
        for (Entity e : sl.getEntities(p, new AABB(eye, end).inflate(0.5), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            if (e.getBoundingBox().inflate(0.2).clip(eye, end).isEmpty()) continue;
            if (e.hurt(sl.damageSources().playerAttack(p), Balance.PCU_DMG)) e.igniteForSeconds(Balance.PCU_FIRE_SECS);
        }
    }

    public static void stop(ServerPlayer p) {
        Cut c = CUTS.remove(p.getUUID());
        if (c == null) return;
        clearCrack(p.serverLevel(), p, c);
        send(p, OFF, Vec3.ZERO, 0.0F);
    }

    private static void fx(ServerPlayer p, Cut c, int state, Vec3 at, float k) {
        long now = p.serverLevel().getGameTime();
        if (state == c.sent && now - c.sentAt < Balance.PCU_FX_EVERY) return;
        c.sent = state; c.sentAt = now;
        send(p, state, at, k);
    }

    private static void send(ServerPlayer p, int state, Vec3 at, float k) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new HandToolFxPayload(p.getId(), HandToolFxPayload.CUTTER, state, (float) at.x, (float) at.y, (float) at.z, -1, k));
    }
}
