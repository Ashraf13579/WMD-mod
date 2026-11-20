package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class WarManager {

    private WarManager() {}

    static final long PEACE_OFFER_MS = 5 * 60_000L;
    private static final String M = "message.wmd.war.";

    static final long HEAL_RETRY = 3_600_000L;
    static final int HEAL_TRIES = 48;
    static void healFinished(MinecraftServer server, int warId, boolean leftover) {
        WarData d = WarData.get(server);
        WarData.War w = d.byId(warId);
        if (w == null) return;
        if (!leftover || ++w.healTries >= HEAL_TRIES) {
            w.healDone = true;
            if (leftover) Wmd.LOGGER.info("[war] #{}: some claimed chunks were never loaded in {} tries; they stay as they are", warId, HEAL_TRIES);
        } else w.healAt = now() + HEAL_RETRY;
        d.setDirty();
    }

    static long now() { return System.currentTimeMillis(); }

    public static boolean atWar(MinecraftServer server, UUID a, UUID b) {
        if (server == null || a == null || b == null || a.equals(b)) return false;
        for (WarData.War w : WarData.get(server).wars) {
            if (w.state != WarData.ACTIVE) continue;
            int sa = w.sideOf(a), sb = w.sideOf(b);
            if (sa != 0 && sb != 0 && sa != sb) return true;
        }
        return false;
    }

    static List<WarData.War> warsOf(MinecraftServer server, UUID nation) {
        List<WarData.War> l = new ArrayList<>();
        if (nation == null) return l;
        for (WarData.War w : WarData.get(server).wars) if (w.live() && w.sideOf(nation) != 0) l.add(w);
        return l;
    }

    static WarData.War between(MinecraftServer server, UUID me, UUID enemy) {
        for (WarData.War w : WarData.get(server).wars) {
            if (!w.live()) continue;
            int a = w.sideOf(me), b = w.sideOf(enemy);
            if (a != 0 && b != 0 && a != b) return w;
        }
        return null;
    }

    static String name(MinecraftServer server, UUID nation) {
        String n = OpacBridge.nationName(server, nation);
        return n.isEmpty() ? "?" : n;
    }

    private static UUID leading(ServerPlayer p, String[] why) {
        if (VnBridge.present()) { why[0] = "vn"; return null; }
        if (!OpacBridge.active()) { why[0] = "no_opac"; return null; }
        UUID n = OpacBridge.nationOf(p.server, p.getUUID());
        if (n == null) { why[0] = "no_nation"; return null; }
        if (!OpacBridge.leads(p.server, p.getUUID())) { why[0] = "not_leader"; return null; }
        return n;
    }

    static String declare(ServerPlayer p, UUID target) {
        MinecraftServer server = p.server;
        String[] why = new String[1];
        UUID me = leading(p, why);
        if (me == null) return why[0];
        if (!Config.Server.warEnabled()) return "disabled";
        if (target == null || !OpacBridge.nations(server).contains(target)) return "no_target";
        if (target.equals(me)) return "self";
        if (OpacBridge.mutualAllies(server, me, target)) return "allies";
        if (between(server, me, target) != null) return "already";
        WarData d = WarData.get(server);
        Long cool = d.cooldowns.get(WarData.pair(me, target));
        if (cool != null && cool > now()) return "cooldown";
        if (Config.Server.warNeedCommandCenter() && !hasCommandCenter(server, me)) return "no_cc";
        WarData.War w = new WarData.War();
        w.id = d.nextId++;
        w.attacker = me;
        w.defender = target;
        w.sideA.add(me);
        w.sideB.add(target);
        long t = now();
        w.declaredAt = t;
        w.startsAt = t + Config.Server.warDeclareMinutes() * 60_000L;
        w.endsAt = w.startsAt + Config.Server.warMinutes() * 60_000L;
        w.state = WarData.DECLARED;
        for (UUID n : OpacBridge.nations(server)) {
            if (n.equals(me) || n.equals(target)) continue;
            boolean a = OpacBridge.mutualAllies(server, n, me), b = OpacBridge.mutualAllies(server, n, target);
            if (a && !b) w.callA.add(n);
            else if (b && !a) w.callB.add(n);
        }
        d.wars.add(w);
        d.setDirty();
        String an = name(server, me), dn = name(server, target);
        announce(server, Component.translatable("title.wmd.war.declared").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.translatable("title.wmd.war.declared_sub", an, dn), Component.translatable(M + "declared", an, dn, Config.Server.warDeclareMinutes()), WmdSounds.DEFCON_2.get());

        broadcast(server, Component.translatable(M + "rules", Config.Server.warDeclareMinutes()).withStyle(ChatFormatting.GOLD));
        for (UUID n : w.callA) tellNation(server, n, Component.translatable(M + "call", an, dn, an));
        for (UUID n : w.callB) tellNation(server, n, Component.translatable(M + "call", an, dn, dn));
        Wmd.LOGGER.info("[war] #{} {} declared war on {} (starts in {} min, by {})", w.id, an, dn, Config.Server.warDeclareMinutes(), p.getGameProfile().getName());
        for (UUID m : OpacBridge.members(server, me)) WmdTrigger.fire(server, m, "war_declared");
        return "ok_declared";
    }

    static String join(ServerPlayer p, UUID ally) {
        MinecraftServer server = p.server;
        String[] why = new String[1];
        UUID me = leading(p, why);
        if (me == null) return why[0];
        for (WarData.War w : WarData.get(server).wars) {
            if (!w.live()) continue;
            int s = w.sideOf(ally);
            if (s == 0 || w.sideOf(me) != 0) continue;
            if (!(s == 1 ? w.callA : w.callB).contains(me)) continue;
            w.side(s).add(me);
            w.callA.remove(me);
            w.callB.remove(me);
            WarData.get(server).setDirty();
            broadcast(server, Component.translatable(M + "joined", name(server, me), name(server, w.principal(s)), name(server, w.principal(3 - s))).withStyle(ChatFormatting.GOLD));
            return "ok_joined";
        }
        return "no_call";
    }

    static String peace(ServerPlayer p, UUID enemy) {
        MinecraftServer server = p.server;
        String[] why = new String[1];
        UUID me = leading(p, why);
        if (me == null) return why[0];
        WarData.War w = between(server, me, enemy);
        if (w == null) return "no_war";
        if (!me.equals(w.attacker) && !me.equals(w.defender)) return "not_principal_peace";
        int mine = w.sideOf(me);
        long t = now();
        if (w.peaceBy != 0 && w.peaceBy != mine && w.peaceUntil > t) {
            end(server, w, WarData.R_PEACE, null);
            return "ok_peace";
        }
        w.peaceBy = mine;
        w.peaceUntil = t + PEACE_OFFER_MS;
        WarData.get(server).setDirty();
        for (UUID n : w.side(3 - mine)) tellNation(server, n, Component.translatable(M + "peace_offer", name(server, me)));
        return "ok_offered";
    }

    static String surrender(ServerPlayer p, UUID enemy) {
        MinecraftServer server = p.server;
        String[] why = new String[1];
        UUID me = leading(p, why);
        if (me == null) return why[0];
        WarData.War w = between(server, me, enemy);
        if (w == null) return "no_war";
        if (!me.equals(w.attacker) && !me.equals(w.defender)) return "not_principal";
        end(server, w, WarData.R_SURRENDER, me.equals(w.attacker) ? w.defender : w.attacker);
        return "ok_surrendered";
    }

    static String take(ServerPlayer p) { return take(p, p.blockPosition().getX() >> 4, p.blockPosition().getZ() >> 4); }

    static final int TAKE_REACH = 7;

    static String take(ServerPlayer p, int cx, int cz) {
        MinecraftServer server = p.server;
        String[] why = new String[1];
        UUID me = leading(p, why);
        if (me == null) return why[0];
        ServerLevel level = p.serverLevel();
        if (Math.abs(cx - (p.blockPosition().getX() >> 4)) > TAKE_REACH || Math.abs(cz - (p.blockPosition().getZ() >> 4)) > TAKE_REACH) return "take_far";
        UUID claimer = OpacBridge.claimOwner(level, cx, cz);
        UUID owner = claimer == null ? null : OpacBridge.nationOf(server, claimer);
        if (owner == null) return "take_unclaimed";
        long t = now();
        WarData.War won = null;
        for (WarData.War w : WarData.get(server).wars)
            if (w.state == WarData.ENDED && me.equals(w.winner) && w.takeLeft > 0 && w.takeUntil > t && owner.equals(w.attacker.equals(me) ? w.defender : w.attacker)) won = w;
        if (won == null) return "take_none";
        String no = takeBlocked(level, owner, cx, cz);
        if (no != null) return no;
        if (!OpacBridge.transfer(level, cx, cz, p.getUUID())) return "take_failed";
        won.takeLeft--;
        won.taken.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz));
        won.takenDim = level.dimension().location().toString();
        WarData.get(server).setDirty();
        tellNation(server, owner, Component.translatable(M + "taken_from", name(server, me), cx * 16 + 8, cz * 16 + 8).withStyle(ChatFormatting.RED));
        Wmd.LOGGER.info("[war] #{} {} took chunk {} {} from {} ({} left)", won.id, name(server, me), cx, cz, name(server, owner), won.takeLeft);
        p.displayClientMessage(Component.translatable(M + "taken", won.takeLeft), false);
        return "";
    }

    static String takeBlocked(ServerLevel level, UUID owner, int cx, int cz) {
        int total = 0;
        for (UUID m : OpacBridge.members(level.getServer(), owner)) total += OpacBridge.claimCount(level.getServer(), m);
        if (total <= 1) return "take_last";
        if (commandCenterIn(level, owner, cx, cz)) return "take_cc";
        return null;
    }

    static WarData.War takeWar(MinecraftServer server, UUID me) {
        long t = now();
        for (WarData.War w : WarData.get(server).wars)
            if (w.state == WarData.ENDED && me != null && me.equals(w.winner) && w.takeLeft > 0 && w.takeUntil > t) return w;
        return null;
    }

    static boolean adminStart(MinecraftServer server, int id) {
        WarData.War w = WarData.get(server).byId(id);
        if (w == null || w.state != WarData.DECLARED) return false;
        w.startsAt = now();
        w.endsAt = w.startsAt + Config.Server.warMinutes() * 60_000L;
        tick(server);
        return true;
    }

    static boolean adminEnd(MinecraftServer server, int id) {
        WarData.War w = WarData.get(server).byId(id);
        if (w == null || !w.live()) return false;
        end(server, w, WarData.R_ADMIN, null);
        return true;
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0 || !OpacBridge.active()) return;
        tick(server);
        boolean any = false;
        for (WarData.War w : WarData.get(server).wars) {
            if (!w.live()) continue;
            if (w.state == WarData.ACTIVE) any = true;
            for (UUID n : w.sideA) WarGate.touch(server, n);
            for (UUID n : w.sideB) WarGate.touch(server, n);
        }
        WarGate.anyActive = any;
        WarGate.hud(server);
    }

    static void tick(MinecraftServer server) {
        WarTreaty.tick(server);
        WarData d = WarData.get(server);
        if (d.wars.isEmpty() && d.cooldowns.isEmpty()) return;
        long t = now();
        for (WarData.War w : new ArrayList<>(d.wars)) {
            if (w.state == WarData.DECLARED && t >= w.startsAt) {
                w.state = WarData.ACTIVE;
                d.setDirty();
                String an = name(server, w.attacker), dn = name(server, w.defender);
                announce(server, Component.translatable("title.wmd.war.started").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.translatable("title.wmd.war.declared_sub", an, dn), Component.translatable(M + "started", an, dn, Config.Server.warMinutes()), WmdSounds.DEFCON_1.get());
                Wmd.LOGGER.info("[war] #{} {} vs {}: the war has begun", w.id, an, dn);
            }
            if (w.state == WarData.ACTIVE && t >= w.endsAt) {
                int win = WarScore.winnerByScore(w);
                if (win != 0) end(server, w, WarData.R_SCORE, w.principal(win));
                else end(server, w, WarData.R_TIMEOUT, null);
            }
            if (w.live() && w.peaceBy != 0 && w.peaceUntil <= t) { w.peaceBy = 0; d.setDirty(); }

            if (!w.live() && w.healAt > 0 && !w.healDone && t >= w.healAt && !RollbackJob.warHealRunning(w.id)) {
                boolean first = !w.healed;
                w.healed = true;
                d.setDirty();
                RollbackJob.warHeal(server, w);
                if (first) broadcast(server, Component.translatable(M + "healing", name(server, w.attacker), name(server, w.defender)).withStyle(ChatFormatting.AQUA));
            }
        }
        d.cooldowns.values().removeIf(until -> until <= t);

        int ended = 0;
        for (WarData.War w : d.wars) if (!w.live()) ended++;
        for (int i = 0; i < d.wars.size() && ended > WarData.HISTORY; i++)
            if (!d.wars.get(i).live() && d.wars.get(i).takeUntil <= t && (d.wars.get(i).healAt == 0 || d.wars.get(i).healDone)) { d.wars.remove(i--);
                 ended--; d.setDirty(); }
    }

    static void end(MinecraftServer server, WarData.War w, int result, UUID winner) {
        WarData d = WarData.get(server);
        long t = now();
        w.state = WarData.ENDED;
        w.result = result;
        w.winner = winner;
        w.endedAt = t;
        w.peaceBy = 0;
        w.callA.clear();
        w.callB.clear();

        if ((result == WarData.R_SURRENDER || result == WarData.R_SCORE) && winner != null && w.startsAt <= t) { w.takeLeft = Config.Server.warTerritoryChunks(); w.takeUntil = t + Config.Server.warTakeMinutes() * 60_000L; }

        if (Config.Server.warHeal() && w.startsAt <= t) w.healAt = Math.max(t, w.takeUntil) + Config.Server.warHealMinutes() * 60_000L;
        d.cooldowns.put(WarData.pair(w.attacker, w.defender), t + Config.Server.warCooldownMinutes() * 60_000L);
        if (winner != null && result != WarData.R_PEACE && w.startsAt <= t)
            for (UUID n : w.side(w.sideOf(winner))) for (UUID m : OpacBridge.members(server, n)) WmdTrigger.fire(server, m, "war_won");
        int winSide = winner == null ? 0 : w.sideOf(winner);
        for (int s = 1; s <= 2; s++)
            for (UUID n : w.side(s)) {
                int[] tot = d.totals(n);
                tot[0] += w.points[s];
                if (winSide == 0) tot[3]++; else if (winSide == s) tot[1]++; else tot[2]++;
            }
        d.setDirty();
        String an = name(server, w.attacker), dn = name(server, w.defender);
        Component line = switch (result) {
            case WarData.R_PEACE -> Component.translatable(M + "end_peace", an, dn);
            case WarData.R_SURRENDER -> w.takeLeft > 0 ? Component.translatable(M + "end_surrender", name(server, winner.equals(w.attacker) ? w.defender : w.attacker), name(server, winner), w.takeLeft)
                    : Component.translatable(M + "end_surrender_early", name(server, winner.equals(w.attacker) ? w.defender : w.attacker), name(server, winner));
            case WarData.R_TIMEOUT -> Component.translatable(M + "end_timeout", an, dn, w.points[1], w.points[2]);
            case WarData.R_SCORE -> Component.translatable(M + "end_score", name(server, winner), name(server, winner.equals(w.attacker) ? w.defender : w.attacker),
                    w.points[w.sideOf(winner)], w.points[3 - w.sideOf(winner)], w.takeLeft);
            default -> Component.translatable(M + "end_admin", an, dn);
        };
        announce(server, Component.translatable("title.wmd.war.ended").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), line, line, WmdSounds.UI_CONFIRM.get());
        Wmd.LOGGER.info("[war] #{} {} vs {} ended: result {} winner {}", w.id, an, dn, result, winner == null ? "-" : name(server, winner));
    }

    static boolean hasCommandCenter(MinecraftServer server, UUID nation) {
        for (UUID m : OpacBridge.members(server, nation)) if (CommandCenter.active(m)) return true;
        return false;
    }

    private static boolean commandCenterIn(ServerLevel level, UUID nation, int cx, int cz) {
        List<UUID> members = OpacBridge.members(level.getServer(), nation);
        for (CcBuildingBlockEntity b : CommandCenter.buildingsIn(level))
            if ((b.getBlockPos().getX() >> 4) == cx && (b.getBlockPos().getZ() >> 4) == cz && members.contains(b.getOwner())) return true;
        return false;
    }

    static void broadcast(MinecraftServer server, Component line) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
    }

    private static void announce(MinecraftServer server, Component title, Component sub, Component line, SoundEvent sound) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.sendSystemMessage(line);
            p.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    static void tellNation(MinecraftServer server, UUID nation, Component line) {
        for (UUID m : OpacBridge.members(server, nation)) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) p.sendSystemMessage(line);
        }
    }
}
