package com.ashraf.wmd;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class CcMapPrint {

    private CcMapPrint() {}

    public static final int MAX_DECOR = 32, DOSSIER_LINES = 20;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    public static ReconData.Snapshot snapshot(MinecraftServer server, UUID owner) {
        ReconData.Entry e = ReconData.get(server).entry(owner);
        ReconData.Snapshot s = e != null ? e.snap : null;
        return s != null && s.n() == 128 && s.step() == 4 && s.col().length == 128 * 128 ? s : null;
    }

    public static int[] centre(ReconData.Snapshot s) { return new int[] { s.x0() + s.n() * s.step() / 2, s.z0() + s.n() * s.step() / 2 }; }

    public static ItemStack print(MinecraftServer server, ReconData.Snapshot s) {
        if (s == null || s.n() != 128 || s.step() != 4 || s.col().length != 128 * 128) return null;
        ServerLevel ow = server.overworld();
        int[] c = centre(s);
        CompoundTag t = new CompoundTag();
        t.putString("dimension", "minecraft:overworld");
        t.putInt("xCenter", c[0]);
        t.putInt("zCenter", c[1]);
        t.putByte("scale", (byte) 2);
        t.putBoolean("trackingPosition", false);
        t.putBoolean("unlimitedTracking", false);
        t.putBoolean("locked", true);
        t.putByteArray("colors", s.col().clone());
        t.put("banners", new ListTag());
        t.put("frames", new ListTag());
        MapItemSavedData data = MapItemSavedData.load(t, ow.registryAccess());
        MapId id = ow.getFreeMapId();
        ow.setMapData(id, data);
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        String date = DATE.format(Instant.ofEpochMilli(s.timeMs()).atZone(ZoneId.systemDefault()));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Recon " + c[0] + " " + c[1] + ", " + date).withStyle(st -> st.withItalic(false)));
        decorate(ow, stack, data, s, c);
        dossier(server, stack, s, c);
        data.setDirty();
        return stack;
    }

    private static void dossier(MinecraftServer server, ItemStack stack, ReconData.Snapshot s, int[] c) {
        int[] ic = s.icons();
        int n = ic.length / 3;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(d2(ic, a, c), d2(ic, b, c)));
        java.util.List<Component> lines = new java.util.ArrayList<>();
        net.minecraft.network.chat.Style grey = net.minecraft.network.chat.Style.EMPTY.withItalic(false).withColor(net.minecraft.ChatFormatting.GRAY);
        lines.add(Component.translatable("item.wmd.intel.header", n).withStyle(grey.withColor(net.minecraft.ChatFormatting.GOLD)));
        for (int k = 0; k < Math.min(n, DOSSIER_LINES); k++) {
            int i = order[k];
            int x = ic[i * 3], z = ic[i * 3 + 1], kind = Math.max(0, Math.min(CcActionPayload.ICON_KEY.length - 1, ic[i * 3 + 2]));
            String owner = CcIntel.ownerName(server, x, z);
            lines.add(Component.translatable("item.wmd.intel.row", Component.translatable("gui.wmd.recon.icon." + CcActionPayload.ICON_KEY[kind]),
                    owner.isEmpty() ? Component.translatable("item.wmd.intel.unknown") : Component.literal(owner), x, z).withStyle(grey));
        }
        if (n > DOSSIER_LINES) lines.add(Component.translatable("item.wmd.intel.more", n - DOSSIER_LINES).withStyle(grey));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
    }

    private static void decorate(ServerLevel ow, ItemStack stack, MapItemSavedData data, ReconData.Snapshot s, int[] c) {
        int[] ic = s.icons();
        int n = ic.length / 3;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Long.compare(d2(ic, a, c), d2(ic, b, c)));
        int used = 0;
        for (int k = 0; k < n && used < MAX_DECOR; k++) {
            int i = order[k];
            int x = ic[i * 3], z = ic[i * 3 + 1], kind = ic[i * 3 + 2];
            if (Math.abs(x - c[0]) >= 256 || Math.abs(z - c[1]) >= 256) continue;
            Holder<MapDecorationType> type = switch (kind) {
                case Recon.ICON_LAUNCH -> MapDecorationTypes.RED_X;
                case Recon.ICON_DEFENCE -> MapDecorationTypes.RED_MARKER;
                default -> MapDecorationTypes.TARGET_POINT;
            };
            String key = "wmd_recon_" + used;
            MapItemSavedData.addTargetDecoration(stack, new BlockPos(x, 64, z), key, type);
            data.addDecoration(type, ow, key, x, z, 180.0, null);
            used++;
        }
    }

    public static final int LOG_PER_PAGE = 4, LOG_PAGES = 16;

    public static ItemStack printBda(MinecraftServer server, CcBda.Report r) {
        if (r == null || !r.done()) return null;
        ServerLevel ow = server.overworld();
        int px = Math.max(1, 128 / CcBda.CROP);
        int cx = r.cx0() + CcBda.CROP * r.step() / 2, cz = r.cz0() + CcBda.CROP * r.step() / 2;
        byte[] colours = new byte[128 * 128];
        byte[] a = r.after();
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int sx = Math.min(CcBda.CROP - 1, x / px), sz = Math.min(CcBda.CROP - 1, y / px);
            colours[y * 128 + x] = a[sz * CcBda.CROP + sx];
        }
        CompoundTag t = new CompoundTag();
        t.putString("dimension", "minecraft:overworld");
        t.putInt("xCenter", cx);
        t.putInt("zCenter", cz);
        t.putByte("scale", (byte) 0);
        t.putBoolean("trackingPosition", false);
        t.putBoolean("unlimitedTracking", false);
        t.putBoolean("locked", true);
        t.putByteArray("colors", colours);
        t.put("banners", new ListTag());
        t.put("frames", new ListTag());
        MapItemSavedData data = MapItemSavedData.load(t, ow.registryAccess());
        MapId id = ow.getFreeMapId();
        ow.setMapData(id, data);
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        stack.set(DataComponents.MAP_ID, id);
        String date = DATE.format(Instant.ofEpochMilli(r.impactMs()).atZone(ZoneId.systemDefault()));
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.wmd.bda.name", r.x(), r.z(), date).withStyle(st -> st.withItalic(false)));
        MapItemSavedData.addTargetDecoration(stack, new BlockPos(r.x(), 64, r.z()), "wmd_bda_impact", MapDecorationTypes.RED_X);
        data.addDecoration(MapDecorationTypes.RED_X, ow, "wmd_bda_impact", r.x(), r.z(), 180.0, null);
        int[] lost = r.lost();
        for (int i = 0; i + 2 < lost.length && i / 3 < MAX_DECOR - 1; i += 3) {
            String key = "wmd_bda_" + i / 3;
            MapItemSavedData.addTargetDecoration(stack, new BlockPos(lost[i], 64, lost[i + 1]), key, MapDecorationTypes.TARGET_X);
            data.addDecoration(MapDecorationTypes.TARGET_X, ow, key, lost[i], lost[i + 1], 180.0, null);
        }
        net.minecraft.network.chat.Style grey = net.minecraft.network.chat.Style.EMPTY.withItalic(false).withColor(net.minecraft.ChatFormatting.GRAY);
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable("item.wmd.bda.header").withStyle(grey.withColor(net.minecraft.ChatFormatting.GOLD)));
        lines.add(Component.translatable("item.wmd.bda.impact", r.warhead().isEmpty() ? Component.literal("?") : Component.translatable("warhead.wmd." + r.warhead()),
                r.x(), r.z()).withStyle(grey));
        lines.add(Component.translatable("item.wmd.bda.destroyed", r.destroyed(), r.beforeIcons().length / 3)
                .withStyle(grey.withColor(r.destroyed() > 0 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY)));
        for (int i = 0; i + 2 < lost.length && i / 3 < DOSSIER_LINES; i += 3) {
            int kind = Math.max(0, Math.min(CcActionPayload.ICON_KEY.length - 1, lost[i + 2]));
            String owner = CcIntel.ownerName(server, lost[i], lost[i + 1]);
            lines.add(Component.translatable("item.wmd.intel.row", Component.translatable("gui.wmd.recon.icon." + CcActionPayload.ICON_KEY[kind]),
                    owner.isEmpty() ? Component.translatable("item.wmd.intel.unknown") : Component.literal(owner), lost[i], lost[i + 1]).withStyle(grey));
        }
        lines.add(Component.translatable("item.wmd.bda.standing", r.afterIcons().length / 3).withStyle(grey));
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        data.setDirty();
        return stack;
    }

    public static ItemStack printLog(MinecraftServer server, String ownerName, java.util.List<CcBattleLog.Event> events) {
        if (events == null || events.isEmpty()) return null;
        DateTimeFormatter hm = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);
        java.util.List<net.minecraft.server.network.Filterable<Component>> pages = new java.util.ArrayList<>();
        net.minecraft.network.chat.MutableComponent page = null;
        int onPage = 0;
        for (int i = events.size() - 1; i >= 0 && pages.size() < LOG_PAGES; i--) {
            CcBattleLog.Event e = events.get(i);
            if (page == null) { page = Component.empty(); onPage = 0; }
            if (onPage > 0) page.append("\n\n");
            String when = hm.format(Instant.ofEpochMilli(e.timeMs()).atZone(ZoneId.systemDefault()));
            page.append(Component.literal(when + "\n").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            page.append(e.text().copy().withStyle(logColour(e.kind())));
            if (++onPage >= LOG_PER_PAGE) { pages.add(net.minecraft.server.network.Filterable.passThrough(page)); page = null; }
        }
        if (page != null && pages.size() < LOG_PAGES) pages.add(net.minecraft.server.network.Filterable.passThrough(page));
        String author = ownerName == null || ownerName.isEmpty() ? "?" : ownerName;
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("Battle log"), author, 0, pages, true));
        book.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                Component.translatable("item.wmd.battle_log.lore", events.size(), hm.format(Instant.ofEpochMilli(events.get(events.size() - 1).timeMs()).atZone(ZoneId.systemDefault())))
                        .withStyle(st -> st.withItalic(false).withColor(net.minecraft.ChatFormatting.GRAY)))));
        return book;
    }

    static net.minecraft.ChatFormatting logColour(int kind) {
        return switch (kind) {
            case CcBattleLog.LAUNCH_IN, CcBattleLog.LOST, CcBattleLog.ALARM -> net.minecraft.ChatFormatting.DARK_RED;
            case CcBattleLog.INTERCEPT, CcBattleLog.HIT, CcBattleLog.BDA -> net.minecraft.ChatFormatting.DARK_GREEN;
            case CcBattleLog.DEFCON, CcBattleLog.COUNTER -> net.minecraft.ChatFormatting.GOLD;
            case CcBattleLog.SAT, CcBattleLog.JAM -> net.minecraft.ChatFormatting.DARK_PURPLE;
            default -> net.minecraft.ChatFormatting.DARK_BLUE;
        };
    }

    private static long d2(int[] ic, int i, int[] c) {
        long dx = ic[i * 3] - c[0], dz = ic[i * 3 + 1] - c[1];
        return dx * dx + dz * dz;
    }
}
