package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CcActionPayload(BlockPos pos, int action, int a, int b) implements CustomPacketPayload {

    public static final int TARGET = 1, DEORBIT = 2, EW = 3, LOG = 4, CLEAR = 5, DESIGNATE = 6, UNDESIGNATE = 7, VIEW = 8, BOARD_MODE = 9, REKEY = 10;
    public static final int DEFCON = 11, BATTLE_LOG = 12, DESIGNATE_SOURCE = 13, LOCKDOWN = 14, PRINT_MODE = 15;
    public static final int DRONES = 16, DRONE_RECALL = 17, DRONE_WATCH = 18, SITE_LAUNCH = 19;
    private static final int LIMIT = 29_000_000;
    private static final Map<UUID, Long> LAST_LOG = new HashMap<>(), LAST_DRONES = new HashMap<>();

    static void clearStatics() { LAST_LOG.clear(); LAST_DRONES.clear(); }

    static final String[] ICON_KEY = { "launch", "defence", "radar", "station", "other", "airship" };

    public static final Type<CcActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_action"));

    public static final StreamCodec<FriendlyByteBuf, CcActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeVarInt(p.action()); buf.writeInt(p.a()); buf.writeInt(p.b()); },
            buf -> new CcActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static String reconLabel(ServerPlayer sp, UUID owner, int x, int z) {
        ReconData.Entry e = ReconData.get(sp.server).entry(owner);
        int[] ic = e != null && e.snap != null ? e.snap.icons() : new int[0];
        int best = -1;
        long bd = 36;
        for (int i = 0; i + 2 < ic.length; i += 3) {
            long dx = ic[i] - x, dz = ic[i + 1] - z, d = dx * dx + dz * dz;
            if (d <= bd) { bd = d; best = ic[i + 2]; }
        }
        return best < 0 ? "gui.wmd.cc.des.spot" : "gui.wmd.recon.icon." + ICON_KEY[Math.max(0, Math.min(ICON_KEY.length - 1, best))];
    }

    public static void handle(CcActionPayload p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof MachineMenu menu) || !(menu.machine() instanceof CcModuleBlockEntity m)
                    || m.isRemoved() || !m.getBlockPos().equals(p.pos()) || m.getOwner() == null) return;
            UUID owner = m.getOwner();
            boolean isOwner = owner.equals(sp.getUUID());
            switch (p.action()) {
                case TARGET -> {
                    if (!(m instanceof CcSatGlobeBlockEntity) || !m.isOnline() || !isOwner) return;
                    int x = Math.max(-LIMIT, Math.min(LIMIT, p.a())), z = Math.max(-LIMIT, Math.min(LIMIT, p.b()));
                    Recon.cancel(owner);
                    ReconData.get(sp.server).setTarget(owner, x, z);
                    sp.displayClientMessage(Component.translatable("message.wmd.cc.globe.target", x, z).withStyle(ChatFormatting.AQUA), true);
                }
                case DEORBIT -> {
                    if (!(m instanceof CcSatGlobeBlockEntity) || !m.isOnline() || !isOwner) return;
                    OrbitMath.Sat sat = null;
                    for (OrbitMath.Sat s : OrbitData.get(sp.server).sats()) if (s.id() == p.a()) sat = s;
                    if (sat == null || !sp.getUUID().equals(sat.owner())) return;
                    if (OrbitData.get(sp.server).remove(sp.server, sat.id())) {
                        Wmd.LOGGER.info("Satellite #{} ({}) deorbited by its owner {} from the command center", sat.id(), sat.type(), sp.getGameProfile().getName());
                        sp.sendSystemMessage(Component.translatable("message.wmd.cc.globe.deorbited", OrbitData.typeName(sat.type()), sat.id()).withStyle(ChatFormatting.YELLOW));
                    }
                }
                case REKEY -> {
                    if (!(m instanceof CcSatGlobeBlockEntity) || !m.isOnline() || !isOwner) return;
                    sp.displayClientMessage(SatHack.rekey(sp.server, sp.getUUID(), p.a(), false), false);
                }
                case EW -> {
                    if (!(m instanceof CcEwRackBlockEntity r) || !(isOwner || sp.hasPermissions(2))) return;
                    r.toggle();
                }
                case LOG -> {
                    if (!(m instanceof CcAlarmBoardBlockEntity)) return;
                    long tick = sp.server.getTickCount();
                    Long last = LAST_LOG.get(sp.getUUID());
                    if (last != null && tick >= last && tick - last < 10) return;
                    LAST_LOG.put(sp.getUUID(), tick);
                    if (LAST_LOG.size() > 64) LAST_LOG.keySet().removeIf(u -> sp.server.getPlayerList().getPlayer(u) == null);
                    PacketDistributor.sendToPlayer(sp, new CcAlarmLogPayload(m.getBlockPos(), CcAlarms.events(sp.server, owner)));
                }
                case CLEAR -> {
                    if (!(m instanceof CcAlarmBoardBlockEntity) || !isOwner) return;
                    CcAlarms.clear(sp.server, owner);
                    PacketDistributor.sendToPlayer(sp, new CcAlarmLogPayload(m.getBlockPos(), CcAlarms.events(sp.server, owner)));
                }
                case DESIGNATE, UNDESIGNATE -> {
                    if (!(m instanceof CcMapTableBlockEntity || m instanceof CcReconWallBlockEntity) || !m.isOnline()) return;
                    if (!isOwner) { sp.displayClientMessage(Component.translatable("message.wmd.cc.des.owner_only").withStyle(ChatFormatting.RED), true); return; }
                    CcBuildingBlockEntity b = m.building();
                    if (b == null) return;
                    CcTarget t = null;
                    if (p.action() == DESIGNATE) {
                        int x = Math.max(-LIMIT, Math.min(LIMIT, p.a())), z = Math.max(-LIMIT, Math.min(LIMIT, p.b()));
                        boolean recon = m instanceof CcReconWallBlockEntity;
                        t = new CcTarget(x, z, recon ? reconLabel(sp, owner, x, z) : "gui.wmd.cc.des.spot", recon ? CcTarget.SRC_RECON : CcTarget.SRC_TABLE,
                                sp.getGameProfile().getName(), System.currentTimeMillis());
                    }
                    CcTarget.designate(sp, java.util.List.of(b), t);
                }
                case VIEW -> {
                    if (!(m instanceof CcMapTableBlockEntity tb) || !isOwner) return;
                    tb.setView(p.a());
                }
                case BOARD_MODE -> {
                    if (!(m instanceof CcWarningBoardBlockEntity wb) || !isOwner) return;
                    wb.setMode(p.a());
                }

                case DEFCON -> {
                    if (!(m instanceof CcMapTableBlockEntity) || !m.isOnline()) return;
                    if (!isOwner) { sp.displayClientMessage(Component.translatable("message.wmd.cc.owner_only").withStyle(ChatFormatting.RED), true); return; }
                    CcBuildingBlockEntity b = m.building();
                    if (b == null) return;
                    int old = b.setDefcon(p.a());
                    if (old != b.defcon()) CcDefcon.changed(sp, b, old, b.defcon());
                }
                case BATTLE_LOG -> {
                    if (!(m instanceof CcMapTableBlockEntity)) return;
                    long tick = sp.server.getTickCount();
                    Long last = LAST_LOG.get(sp.getUUID());
                    if (last != null && tick >= last && tick - last < 10) return;
                    LAST_LOG.put(sp.getUUID(), tick);
                    if (LAST_LOG.size() > 64) LAST_LOG.keySet().removeIf(u -> sp.server.getPlayerList().getPlayer(u) == null);
                    PacketDistributor.sendToPlayer(sp, new CcBattleLogPayload(m.getBlockPos(), CcBattleLog.events(sp.server, owner)));
                }
                case DESIGNATE_SOURCE -> {
                    if (!(m instanceof CcWarningBoardBlockEntity) || !m.isOnline()) return;
                    if (!isOwner) { sp.displayClientMessage(Component.translatable("message.wmd.cc.des.owner_only").withStyle(ChatFormatting.RED), true); return; }
                    CcBuildingBlockEntity b = m.building();
                    CommandCenter.Warning w = null;
                    long wid = Integer.toUnsignedLong(p.a()) | ((long) p.b() << 32);
                    for (CommandCenter.Warning x : CommandCenter.warnings(owner)) if (x.id() == wid) w = x;
                    if (b == null || w == null) return;
                    CcTarget t = new CcTarget(w.ox(), w.oz(), "gui.wmd.cc.des.launch_site", CcTarget.SRC_COUNTER, sp.getGameProfile().getName(), System.currentTimeMillis());
                    if (CcTarget.designate(sp, java.util.List.of(b), t) > 0)
                        sp.displayClientMessage(Component.translatable("message.wmd.cc.counter.ready").withStyle(ChatFormatting.GOLD), false);
                }
                case LOCKDOWN -> {
                    if (!(m instanceof CcAlarmBoardBlockEntity) || !isOwner) return;
                    CcBuildingBlockEntity b = m.building();
                    if (b == null) return;
                    b.setLockdownToggle(p.a() != 0);
                    if (p.a() == 0) b.endLockdown();
                }
                case PRINT_MODE -> {
                    if (!(m instanceof CcMapPrinterBlockEntity pr) || !isOwner) return;
                    pr.setMode(p.a());
                }

                case DRONES -> {
                    if (!(m instanceof CcMapTableBlockEntity)) return;
                    long tick = sp.server.getTickCount();
                    Long last = LAST_DRONES.get(sp.getUUID());
                    if (last != null && tick >= last && tick - last < 10) return;
                    LAST_DRONES.put(sp.getUUID(), tick);
                    if (LAST_DRONES.size() > 64) LAST_DRONES.keySet().removeIf(u -> sp.server.getPlayerList().getPlayer(u) == null);
                    PacketDistributor.sendToPlayer(sp, CcDronesPayload.of(sp.serverLevel(), m.getBlockPos(), owner));
                }
                case DRONE_RECALL, DRONE_WATCH -> {
                    if (!(m instanceof CcMapTableBlockEntity)) return;
                    if (!isOwner) { sp.displayClientMessage(Component.translatable("message.wmd.cc.owner_only").withStyle(ChatFormatting.RED), true); return; }
                    Drones.Drone d = Drones.get(sp.server).byId(p.a());
                    if (d == null || !owner.equals(d.owner) || !d.airborne()) return;
                    if (p.action() == DRONE_RECALL) {
                        if (d.kind >= 3) BigDrones.recall(sp.server, d.id);
                        else if (d.kind >= 1) StrikeDrones.recall(sp.server, d.id);
                        else return;
                        sp.displayClientMessage(Component.translatable("message.wmd.hive.recalled", d.id), true);
                    } else {
                        Component why = BigDrones.watch(sp, d.id);
                        if (why != null) sp.displayClientMessage(why, true); else sp.closeContainer();
                    }
                }
                case SITE_LAUNCH -> {
                    if (!(m instanceof CcMapTableBlockEntity) || !m.isOnline()) return;
                    if (!isOwner) { sp.displayClientMessage(Component.translatable("message.wmd.cc.owner_only").withStyle(ChatFormatting.RED), true); return; }
                    long key = ((long) p.a() << 32) | (p.b() & 0xFFFFFFFFL);
                    for (BigDroneSite s : BigDrones.sitesOf(owner, sp.level()))
                        if (s.sitePos().asLong() == key) { s.act(BigDroneSite.A_LAUNCH, new int[0], sp); return; }
                }
                default -> {}
            }
        });
    }
}
