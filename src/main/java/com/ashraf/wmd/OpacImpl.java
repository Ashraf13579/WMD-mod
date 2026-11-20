package com.ashraf.wmd;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.event.api.v2.OPACServerAddonRegisterEvent;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverride;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverrideType;
import xaero.pac.common.server.claims.protection.override.api.IChunkAccessOverriderAPI;
import net.minecraft.server.level.ServerLevel;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.result.api.ClaimResult;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

final class OpacImpl {

    private OpacImpl() {}

    private static OpenPACServerAPI api(MinecraftServer server) { return OpenPACServerAPI.get(server); }

    private static IServerPartyAPI party(MinecraftServer server, UUID nation) { return api(server).getPartyManager().getPartyById(nation); }

    static UUID nationOf(MinecraftServer server, UUID player) {
        IServerPartyAPI p = api(server).getPartyManager().getPartyByMember(player);
        return p == null ? null : p.getId();
    }

    static String nationName(MinecraftServer server, UUID nation) {
        IServerPartyAPI p = party(server, nation);
        if (p == null) return "";
        IPlayerConfigAPI cfg = api(server).getPlayerConfigManager().getLoadedConfig(p.getOwner().getUUID());
        String n = cfg == null ? null : cfg.getEffective(PlayerConfigOptions.PARTY_NAME);
        return n == null || n.isBlank() ? p.getDefaultName() : n;
    }

    static boolean leads(MinecraftServer server, UUID player) {
        IServerPartyAPI p = api(server).getPartyManager().getPartyByMember(player);
        if (p == null) return false;
        IPartyMemberAPI m = p.getMemberInfo(player);
        return m != null && (m.isOwner() || m.getRank() == PartyMemberRank.ADMIN);
    }

    static int online(MinecraftServer server, UUID nation) {
        IServerPartyAPI p = party(server, nation);
        return p == null ? 0 : (int) p.getOnlineMemberStream().count();
    }

    static List<UUID> members(MinecraftServer server, UUID nation) {
        IServerPartyAPI p = party(server, nation);
        return p == null ? List.of() : p.getMemberInfoStream().map(IPartyMemberAPI::getUUID).toList();
    }

    static Set<UUID> allies(MinecraftServer server, UUID nation) {
        IServerPartyAPI p = party(server, nation);
        if (p == null) return Set.of();
        Set<UUID> out = new HashSet<>();
        p.getAllyPartiesStream().forEach(a -> out.add(a.getPartyId()));
        return out;
    }

    static List<UUID> nations(MinecraftServer server) {
        return api(server).getPartyManager().getAllStream().map(IServerPartyAPI::getId).toList();
    }

    static UUID claimOwner(ServerLevel level, int cx, int cz) {
        IPlayerChunkClaimAPI c = api(level.getServer()).getServerClaimsManager().get(level.dimension().location(), cx, cz);
        return c == null ? null : c.getPlayerId();
    }

    static int claimCount(MinecraftServer server, UUID player) {
        return api(server).getServerClaimsManager().getPlayerInfo(player).getClaimCount();
    }

    static boolean transfer(ServerLevel level, int cx, int cz, UUID toPlayer) {
        var dim = level.dimension().location();
        OpacBridge.transferring = true;
        try {
            ClaimResult<IPlayerChunkClaimAPI> r = api(level.getServer()).getServerClaimsManager().tryToClaim(dim, toPlayer, 0, dim, cx, cz, cx, cz, true);
            return r != null && r.getResultType().success;
        } finally { OpacBridge.transferring = false; }
    }

    static void registerHooks() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(OpacImpl::onAddons); }

    private static final ChunkAccessOverride ALLOW = new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);

    private static void onAddons(OPACServerAddonRegisterEvent event) {
        var ctx = event.getContext();
        ctx.getChunkAccessOverriderManagerAPI().register(new IChunkAccessOverriderAPI() {
            @Override public String getName() { return "wmd_war"; }
            @Override public ChunkAccessOverride overrideChunkAccess(ResourceLocation dim, int x, int z, IPlayerConfigAPI claimConfig, Entity accessor, UUID accessorId,
                                                                      MinecraftServer server, ChunkAccessOverride current) {
                try {
                    UUID who = accessor instanceof net.minecraft.world.entity.player.Player p ? p.getUUID() : accessorId;
                    if (claimConfig != null && WarGate.playerMay(server, who, claimConfig.getPlayerId())) return ALLOW;
                } catch (Throwable t) { Wmd.LOGGER.warn("[war] chunk access override failed: {}", t.toString()); }
                return current;
            }
        });
        ctx.getClaimActionListenerManagerAPI().register(new IClaimActionListenerAPI() {
            @Override public String getName() { return "wmd_war"; }
            @Override public ClaimActionPermissionOverride overrideClaimingActionPermission(UUID player, ResourceLocation dim, int x, int z, ClaimingAction action,
                                                                                            IServerClaimsManagerAPI manager, ClaimActionPermissionOverride current, MinecraftServer server) {
                try {
                    boolean claims = action == ClaimingAction.CLAIM || action == ClaimingAction.UNCLAIM || action == ClaimingAction.UNCLAIM_UNANCHORED;
                    if (claims && !OpacBridge.transferring && WarGate.frozen(server, player))
                        return new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID, net.minecraft.network.chat.Component.translatable("message.wmd.war.claims_frozen"));
                } catch (Throwable t) { Wmd.LOGGER.warn("[war] claim freeze check failed: {}", t.toString()); }
                return current;
            }
            @Override public void handleSuccessfulClaimingAction(UUID player, ResourceLocation dim, int x, int z, ClaimingAction action, IServerClaimsManagerAPI manager, MinecraftServer server) {}
        });
        Wmd.LOGGER.info("[war] Open Parties and Claims hooks registered (open enemy claims during an open war, claim freeze during a war)");
    }
}
