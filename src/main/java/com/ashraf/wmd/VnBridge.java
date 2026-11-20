package com.ashraf.wmd;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

public final class VnBridge {

    private VnBridge() {}

    public static final String MODID = "verenigdenaties";
    private static final String API = "com.yousef.verenigdenaties.api.VerenigdeNatiesAPI", EV = "com.yousef.verenigdenaties.api.event.";
    static final long RETRY_MS = 5 * 60_000L;

    private static Boolean present;
    private static boolean ready, failed;
    private static long failedAt;
    private static Method apiVersie, isBeschikbaar, landVan, eigenaar, inOorlog, oorlogen, bondgenoten, actieveMoties, actieveSancties;
    private static final Map<UUID, Object[]> COUNTRY = Statics.server(new HashMap<>());

    public static boolean present() {
        if (present == null) present = ModList.get() != null && ModList.get().isLoaded(MODID);
        return present;
    }

    private static void fail(Throwable t) {
        if (!failed) Wmd.LOGGER.error("[vn] United Nations (verenigdenaties) API call failed; the VN bridge is off for 5 minutes (check docs/deps/VN.md)", t);
        failed = true;
        failedAt = System.currentTimeMillis();
    }

    private static boolean init() {
        if (ready) return true;
        try {
            Class<?> a = Class.forName(API);
            apiVersie = a.getMethod("apiVersie");
            isBeschikbaar = a.getMethod("isBeschikbaar");
            landVan = a.getMethod("landVan", UUID.class);
            eigenaar = a.getMethod("eigenaar", Level.class, BlockPos.class);
            inOorlog = a.getMethod("inOorlog", String.class, String.class);
            oorlogen = a.getMethod("oorlogen");
            bondgenoten = a.getMethod("bondgenoten", String.class, String.class);
            actieveMoties = a.getMethod("actieveMoties");
            actieveSancties = a.getMethod("actieveSancties", String.class);
            ready = true;
            Wmd.LOGGER.info("[vn] United Nations API found: WMD's own war system stands down, VN runs the wars");
            return true;
        } catch (Throwable t) { fail(t); return false; }
    }

    public static boolean active() {
        if (!present()) return false;
        if (failed && System.currentTimeMillis() - failedAt < RETRY_MS) return false;
        failed = false;
        if (!init()) return false;
        try { return (int) apiVersie.invoke(null) == 1 && (boolean) isBeschikbaar.invoke(null); } catch (Throwable t) { fail(t); return false; }
    }

    private static Object call(Method m, Object... args) throws ReflectiveOperationException { return m.invoke(null, args); }

    private static Object field(Object record, String accessor) throws ReflectiveOperationException { return record.getClass().getMethod(accessor).invoke(record); }

    public static String countryOf(UUID player) {
        if (player == null || !active()) return null;
        long t = System.currentTimeMillis();
        Object[] c = COUNTRY.get(player);
        if (c != null && t - (Long) c[1] < 1_000L) return ((String) c[0]).isEmpty() ? null : (String) c[0];
        String id = null;
        try {
            Optional<?> o = (Optional<?>) call(landVan, player);
            if (o.isPresent()) id = (String) field(o.get(), "id");
        } catch (Throwable e) { fail(e); }
        COUNTRY.put(player, new Object[] { id == null ? "" : id, t });
        return id;
    }

    public static String countryAt(Level level, BlockPos pos) {
        if (level == null || pos == null || !active()) return null;
        try {
            Optional<?> o = (Optional<?>) call(eigenaar, level, pos);
            return o.isPresent() ? (String) field(o.get(), "land") : null;
        } catch (Throwable e) { fail(e); return null; }
    }

    public static boolean atWar(String a, String b) {
        if (a == null || b == null || !active()) return false;
        try { return (boolean) call(inOorlog, a, b); } catch (Throwable e) { fail(e); return false; }
    }

    public static boolean allies(String a, String b) {
        if (a == null || b == null || !active()) return false;
        try { return (boolean) call(bondgenoten, a, b); } catch (Throwable e) { fail(e); return false; }
    }

    public static List<String[]> wars() {
        List<String[]> out = new ArrayList<>();
        if (!active()) return out;
        try {
            for (Object w : (List<?>) call(oorlogen)) out.add(new String[] { (String) field(w, "landA"), (String) field(w, "landB"), (String) field(w, "aanvaller") });
        } catch (Throwable e) { fail(e); }
        return out;
    }

    public static String launchBanReason(String country) {
        if (country == null || !active()) return null;
        try {
            for (Object m : (List<?>) call(actieveMoties))
                if ("verenigdenaties:lanceerverbod".equals(field(m, "soort")) && country.equals(field(m, "param"))) return "motion";
            for (Object s : (List<?>) call(actieveSancties, country)) {
                String soort = String.valueOf(field(s, "soort"));
                if (soort.contains("wmd") || soort.contains("raket") || soort.contains("kernwapen") || soort.contains("lanceer")) return "sanction";
            }
        } catch (Throwable e) { fail(e); }
        return null;
    }

    public static Component name(String id) {
        if (id == null || id.isEmpty()) return Component.literal("?");
        StringBuilder b = new StringBuilder();
        for (String w : id.split("-")) if (!w.isEmpty()) b.append(b.length() == 0 ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return Component.translatableWithFallback("land.verenigdenaties." + id, b.toString());
    }

    public static String plainName(String id) { return name(id).getString(); }

    public static String tag(UUID player) {
        String c = countryOf(player);
        return c == null ? "" : " (" + plainName(c) + ")";
    }

    public enum Rel { NONE, SAME, ALLY, WAR, PEACE }

    public static Rel relation(UUID me, UUID other) {
        if (me == null || other == null || !active()) return Rel.NONE;
        String a = countryOf(me), b = countryOf(other);
        if (a == null || b == null) return Rel.NONE;
        if (a.equals(b)) return Rel.SAME;
        if (allies(a, b)) return Rel.ALLY;
        return atWar(a, b) ? Rel.WAR : Rel.PEACE;
    }

    public static boolean spares(UUID defender, UUID other) {
        Rel r = relation(defender, other);
        return r == Rel.SAME || r == Rel.ALLY;
    }

    public interface WarListener { void on(int kind, String attacker, String defender, String warId, String winner); }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void listen(WarListener handler) {
        if (!present()) return;
        String[] names = { "OorlogVerklaardEvent", "OorlogBegonnenEvent", "OorlogGeeindigdEvent" };
        for (int i = 0; i < names.length; i++) {
            final int kind = i;
            try {
                Class<? extends Event> c = (Class<? extends Event>) Class.forName(EV + names[i]);
                Consumer<Event> h = e -> {
                    try {
                        String a = (String) field(e, "aanvaller"), d = (String) field(e, "verdediger");
                        String id = kind == 0 ? "" : String.valueOf(field(e, "oorlogId"));
                        String w = kind == 2 ? String.valueOf(field(e, "winnaar")) : "";
                        handler.on(kind, a, d, id, w == null || "null".equals(w) ? "" : w);
                    } catch (Throwable t) { Wmd.LOGGER.warn("[vn] war event {} failed: {}", kind, t.toString()); }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, (Class) c, (Consumer) h);
            } catch (Throwable t) { fail(t); }
        }
        Wmd.LOGGER.info("[vn] listening to United Nations war events (DEFCON, war report)");
    }
}
