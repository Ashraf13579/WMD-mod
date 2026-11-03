package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientReplay {

    private ClientReplay() {}

    private static final int FEED_PER_TICK = 400;
    private static final long MAX_BYTES = 200L << 20;

    private static final class Rec {
        final List<SnapshotPayload> payloads = new ArrayList<>();
        final Set<Long> chunks = new HashSet<>(), far = new HashSet<>();
        long bytes;
        int id;
    }

    private static Rec rec, kept;
    private static CutscenePayload keptCut;
    private static long keptAt;
    private static AsteroidBayPayloads.Fall keptFall;
    private static final List<Object[]> IMPACTS = new ArrayList<>();
    private static boolean replaying;
    private static int feed = -1;
    private static long fakeKey;

    static void record(SnapshotPayload p) {
        if (replaying) return;
        if (p.op() == SnapshotPayload.START) { rec = new Rec(); rec.id = p.id(); rec.payloads.add(p); return; }
        if (rec == null || p.id() != rec.id || p.op() == SnapshotPayload.END || rec.bytes > MAX_BYTES) return;
        long k = ChunkPos.asLong(p.a(), p.b());
        if (!(p.op() == SnapshotPayload.FAR ? rec.far : rec.chunks).add(k)) return;
        rec.payloads.add(p);
        rec.bytes += p.data().length;
    }

    static void cutscene(CutscenePayload p) {
        if (replaying) return;
        int k = p.kind();
        if (k != CutscenePayload.KIND_RAILGUN && k != CutscenePayload.KIND_ROD && k != CutscenePayload.KIND_DROP) return;
        kept = rec;
        keptCut = p;
        keptAt = ClientClock.ticks();
        keptFall = null;
        IMPACTS.clear();
        if (k == CutscenePayload.KIND_DROP)
            for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
                AsteroidBayPayloads.Fall f = (AsteroidBayPayloads.Fall) e[0];
                if (Math.abs(f.tx() + 0.5 - p.dx()) < 2.0 && Math.abs(f.tz() + 0.5 - p.dz()) < 2.0) keptFall = f;
            }
    }

    static void impact(double x, double y, double z, int tier, WarheadType t, int stage, float air, boolean wet) {
        if (replaying || keptCut == null || stage != 0) return;
        if (t != WarheadType.RAILGUN && t != WarheadType.ORBITAL_ROD && t != WarheadType.ASTEROID) return;
        if (ClientClock.ticks() - keptAt > keptCut.total() + 200) return;
        IMPACTS.add(new Object[] { x, y, z, tier, t, air, wet });
    }

    static boolean available() { return keptCut != null && kept != null && !kept.payloads.isEmpty() && !IMPACTS.isEmpty() && !replaying; }

    static void play() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!available()) { mc.player.displayClientMessage(Component.translatable("message.wmd.replay.none").withStyle(ChatFormatting.GRAY), false); return; }
        if (ClientCutscene.active()) ClientCutscene.stop();
        replaying = true;
        feed = 0;
        mc.setScreen(null);
        mc.player.displayClientMessage(Component.translatable("message.wmd.replay.loading").withStyle(ChatFormatting.AQUA), true);
    }

    static boolean replaying() { return replaying; }

    static void hit() {
        if (!replaying) return;
        for (Object[] i : IMPACTS)
            ImpactFx.start((double) i[0], (double) i[1], (double) i[2], (int) i[3], (WarheadType) i[4], 0, (float) i[5], (boolean) i[6]);
        Object[] e = AsteroidBayPayloads.FALLS.get(fakeKey);
        if (e != null) {
            AsteroidBayPayloads.Fall f = (AsteroidBayPayloads.Fall) e[0];
            e[0] = new AsteroidBayPayloads.Fall(f.key(), 3, f.tx(), f.ty(), f.tz(), f.impact(), f.size(), f.pieces(), f.split(), f.seed(), f.sea(), f.owner());
        }
    }

    static void stopped() {
        if (!replaying || feed >= 0) return;
        replaying = false;
        AsteroidBayPayloads.FALLS.remove(fakeKey);
        if (kept != null) ClientSnapshot.handle(new SnapshotPayload(kept.id, SnapshotPayload.END, 0, 0, 0, 0, new byte[0]));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!replaying || mc.level == null) return;
        Object[] fe = AsteroidBayPayloads.FALLS.get(fakeKey);
        if (fe != null) fe[1] = ClientClock.ticks();
        if (feed < 0) return;
        List<SnapshotPayload> ps = kept.payloads;
        for (int n = 0; n < FEED_PER_TICK && feed < ps.size(); n++) ClientSnapshot.handle(ps.get(feed++));
        if (feed < ps.size()) return;
        feed = -1;
        long now = ClientClock.ticks();
        if (keptFall != null) {
            AsteroidBayPayloads.Fall f = keptFall;
            long d = now + keptCut.lead() - f.impact();
            fakeKey = -1L - (f.key() & 0xFFFFL);
            AsteroidBayPayloads.FALLS.put(fakeKey, new Object[] { new AsteroidBayPayloads.Fall(fakeKey, 1, f.tx(), f.ty(), f.tz(), f.impact() + d, f.size(), f.pieces(),
                    f.split() == 0 ? 0 : f.split() + d, f.seed(), f.sea(), f.owner()), now });
        }
        CutscenePayload.pending = keptCut;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        rec = null; kept = null; keptCut = null; keptFall = null; IMPACTS.clear(); replaying = false; feed = -1;
    }
}
