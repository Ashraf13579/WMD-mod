package com.ashraf.wmd;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

public final class OpacBridge {

    private OpacBridge() {}

    public static final String MODID = "openpartiesandclaims";
    private static boolean failed;
    private static long failedAt;
    static final long RETRY_MS = 5 * 60_000L;
    private static Boolean present;

    public static boolean active() {
        if (failed && System.currentTimeMillis() - failedAt < RETRY_MS) return false;
        failed = false;
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded(MODID);
        return present;
    }

    static void fail(Throwable t) {
        if (!failed) Wmd.LOGGER.error("[war] Open Parties and Claims call failed, the war system is OFF for 5 minutes, then it tries again (check docs/deps/OPAC.md)", t);
        failed = true;
        failedAt = System.currentTimeMillis();
    }

    public static void registerHooks() {
        if (!active() || VnBridge.present()) return;
        try { OpacImpl.registerHooks(); } catch (Throwable t) { fail(t); }
    }

    static boolean transferring;

    public static UUID nationOf(MinecraftServer server, UUID player) {
        if (!active() || server == null || player == null) return null;
        try { return OpacImpl.nationOf(server, player); } catch (Throwable t) { fail(t); return null; }
    }

    public static String nationName(MinecraftServer server, UUID nation) {
        if (!active() || server == null || nation == null) return "";
        try { return OpacImpl.nationName(server, nation); } catch (Throwable t) { fail(t); return ""; }
    }

    public static boolean leads(MinecraftServer server, UUID player) {
        if (!active() || server == null || player == null) return false;
        try { return OpacImpl.leads(server, player); } catch (Throwable t) { fail(t); return false; }
    }

    public static int online(MinecraftServer server, UUID nation) {
        if (!active() || server == null || nation == null) return 0;
        try { return OpacImpl.online(server, nation); } catch (Throwable t) { fail(t); return 0; }
    }

    public static List<UUID> members(MinecraftServer server, UUID nation) {
        if (!active() || server == null || nation == null) return List.of();
        try { return OpacImpl.members(server, nation); } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static Set<UUID> allies(MinecraftServer server, UUID nation) {
        if (!active() || server == null || nation == null) return Set.of();
        try { return OpacImpl.allies(server, nation); } catch (Throwable t) { fail(t); return Set.of(); }
    }

    public static boolean mutualAllies(MinecraftServer server, UUID a, UUID b) {
        return a != null && b != null && allies(server, a).contains(b) && allies(server, b).contains(a);
    }

    public static List<UUID> nations(MinecraftServer server) {
        if (!active() || server == null) return List.of();
        try { return OpacImpl.nations(server); } catch (Throwable t) { fail(t); return List.of(); }
    }

    public static UUID claimOwner(ServerLevel level, int cx, int cz) {
        if (!active() || level == null) return null;
        try { return OpacImpl.claimOwner(level, cx, cz); } catch (Throwable t) { fail(t); return null; }
    }

    public static UUID nationAt(ServerLevel level, BlockPos pos) {
        UUID owner = claimOwner(level, pos.getX() >> 4, pos.getZ() >> 4);
        return owner == null ? null : nationOf(level.getServer(), owner);
    }

    public static int claimCount(MinecraftServer server, UUID player) {
        if (!active() || server == null || player == null) return 0;
        try { return OpacImpl.claimCount(server, player); } catch (Throwable t) { fail(t); return 0; }
    }

    public static boolean transfer(ServerLevel level, int cx, int cz, UUID toPlayer) {
        if (!active() || level == null || toPlayer == null) return false;
        try { return OpacImpl.transfer(level, cx, cz, toPlayer); } catch (Throwable t) { fail(t); return false; }
    }
}
