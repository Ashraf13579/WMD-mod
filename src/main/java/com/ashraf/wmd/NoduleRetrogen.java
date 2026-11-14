package com.ashraf.wmd;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = Wmd.MODID)
public final class NoduleRetrogen {

    private NoduleRetrogen() {}

    static final int VERSION = 1;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Wmd.MODID);
    static final java.util.function.Supplier<AttachmentType<Integer>> DONE = ATTACHMENTS.register("nodule_retro",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    static void register(IEventBus modBus) { ATTACHMENTS.register(modBus); }

    private static final Map<ResourceKey<Level>, ArrayDeque<Long>> QUEUE = Statics.server(new HashMap<>());
    private static final Map<ResourceKey<Level>, Set<Long>> WAITING = Statics.server(new HashMap<>());

    private static boolean done(LevelChunk c) { return c.getData(DONE.get()) >= VERSION; }

    private static void mark(LevelChunk c) {
        c.setData(DONE.get(), VERSION);
        c.setUnsaved(true);
    }

    private static void queue(ServerLevel sl, long key) {
        ArrayDeque<Long> q = QUEUE.computeIfAbsent(sl.dimension(), k -> new ArrayDeque<>());
        if (!q.contains(key)) q.add(key);
    }

    @SubscribeEvent
    static void onLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || !(e.getChunk() instanceof LevelChunk c)) return;
        if (e.isNewChunk()) { mark(c); return; }
        if (done(c)) return;
        if (!Config.Server.noduleRetrogen() || !Config.oreGen("nodules")) return;
        queue(sl, c.getPos().toLong());
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post e) {
        if (!Config.Server.noduleRetrogen() || !Config.oreGen("nodules")) return;
        for (ServerLevel sl : e.getServer().getAllLevels()) {
            ArrayDeque<Long> q = QUEUE.get(sl.dimension());
            if (sl.getGameTime() % 100 == 0) {
                Set<Long> w = WAITING.get(sl.dimension());
                if (w != null && !w.isEmpty()) {
                    for (Iterator<Long> it = w.iterator(); it.hasNext(); ) { long k = it.next(); it.remove(); queue(sl, k); }
                    q = QUEUE.get(sl.dimension());
                }
            }
            if (q == null) continue;
            for (int n = 0; n < Balance.NODULE_RETRO_PER_TICK && !q.isEmpty(); n++) work(sl, q.poll());
        }
    }

    private static void work(ServerLevel sl, long key) {
        int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
        LevelChunk c = sl.getChunkSource().getChunkNow(cx, cz);
        if (c == null || done(c)) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (sl.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) {
                WAITING.computeIfAbsent(sl.dimension(), k -> new LinkedHashSet<>()).add(key);
                return;
            }
        RandomSource r = RandomSource.create(sl.getSeed() ^ (cx * 341_873_128_712L + cz * 132_897_987_541L) ^ 0x6E6F64756C65L);
        if (r.nextInt(Balance.NODULE_RETRO_RARITY) == 0) {
            int x = (cx << 4) + r.nextInt(16), z = (cz << 4) + r.nextInt(16);
            int y = sl.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
            BlockPos o = new BlockPos(x, y, z);
            if (sl.getBiome(o).is(BiomeTags.IS_DEEP_OCEAN)) {
                int placed = NoduleFieldFeature.placeField(sl, r, o, Heightmap.Types.OCEAN_FLOOR);
                if (placed > 0) Wmd.LOGGER.debug("[nodule-retrogen] field of {} at {} {} {}", placed, x, y, z);
            }
        }
        mark(c);
    }

    @SubscribeEvent
    static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2))
                .then(Commands.literal("retrogen").requires(TestCmds::test).then(Commands.literal("nodules")
                        .executes(c -> run(c.getSource(), 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 32))
                                .executes(c -> run(c.getSource(), IntegerArgumentType.getInteger(c, "radius")))))));
    }

    private static int run(CommandSourceStack src, int radius) {
        ServerLevel sl = src.getLevel();
        ChunkPos at = new ChunkPos(BlockPos.containing(src.getPosition()));
        int queued = 0, done = 0, unloaded = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            LevelChunk c = sl.getChunkSource().getChunkNow(at.x + dx, at.z + dz);
            if (c == null) { unloaded++; continue; }
            if (done(c)) { done++; continue; }
            queue(sl, c.getPos().toLong());
            queued++;
        }
        boolean on = Config.Server.noduleRetrogen() && Config.oreGen("nodules");
        int fq = queued, fd = done, fu = unloaded;
        src.sendSuccess(() -> Component.translatable(on ? "command.wmd.retrogen.nodules" : "command.wmd.retrogen.nodules_off", fq, fd, fu), false);
        return queued;
    }
}
