package com.ashraf.wmd;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.HtmlMarker;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.Marker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

final class BlueMapMarkers {

    private BlueMapMarkers() {}

    private static volatile BlueMapAPI api;

    static final String S_HIST = "wmd-history", S_ZONES = "wmd-zones", S_BORDER = "wmd-border", S_ADM_M = "wmd-admin-machines", S_ADM_F = "wmd-admin-flights",
            S_WAR_M = "wmd-war-machines", S_WAR_F = "wmd-war-flights", S_WAR_S = "wmd-war-ships";
    private static final String[] ALL = { S_HIST, S_ZONES, S_BORDER, S_ADM_M, S_ADM_F, S_WAR_M, S_WAR_F, S_WAR_S };

    private static final Map<MarkerSet, Map<String, String>> SIGS = Statics.server(java.util.Collections.synchronizedMap(new IdentityHashMap<>()));

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    static void register() {
        BlueMapAPI.onEnable(a -> {
            api = a;
            Wmd.LOGGER.info("BlueMap {} found: WMD map layers ready (history {}, zones {}, border {}, admin {}, war {})", a.getBlueMapVersion(), Config.Server.blueMapHistory(),
                    Config.Server.blueMapZones(), Config.Server.blueMapBorder(), Config.Server.blueMapAdminLayer(), Config.Server.blueMapWarLayer());
        });
        BlueMapAPI.onDisable(a -> { api = null; SIGS.clear(); });
    }

    static boolean connected() { return api != null; }

    static void sync(MinecraftServer server, MapFeed.Snapshot s) {
        BlueMapAPI a = api;
        if (a == null) return;
        for (ServerLevel l : server.getAllLevels()) {
            Optional<BlueMapWorld> w = a.getWorld(l);
            if (w.isEmpty()) continue;
            String dim = l.dimension().location().toString();
            for (BlueMapMap map : w.get().getMaps()) syncMap(map, dim, s);
        }
    }

    private static void syncMap(BlueMapMap map, String dim, MapFeed.Snapshot s) {

        draw(map, S_HIST, "WMD impacts", false, 10, s.history(), history(dim, s));
        draw(map, S_ZONES, "WMD active zones", false, 11, s.zones(), zones(dim, s));
        draw(map, S_BORDER, "World border", false, 12, s.border(), border(dim, s));

        draw(map, S_ADM_M, "WMD admin: machines", true, 20, s.admin(), machines(dim, s, true));
        draw(map, S_ADM_F, "WMD admin: missiles", true, 21, s.admin(), flights(dim, s));

        draw(map, S_WAR_M, "WMD war map: machines", false, 30, s.war(), machines(dim, s, false));
        draw(map, S_WAR_F, "WMD war map: missiles", false, 31, s.war(), flights(dim, s));
        draw(map, S_WAR_S, "WMD war map: airships", false, 32, s.war(), ships(dim, s));
    }

    private static void draw(BlueMapMap map, String id, String label, boolean hidden, int sorting, boolean on, Map<String, Object[]> want) {
        Map<String, MarkerSet> sets = map.getMarkerSets();
        if (!on) {
            MarkerSet old = sets.remove(id);
            if (old != null) SIGS.remove(old);
            return;
        }
        MarkerSet set = sets.get(id);
        if (set == null) {
            set = new MarkerSet(label, true, hidden);
            set.setSorting(sorting);
            sets.put(id, set);
        }
        Map<String, String> have = SIGS.computeIfAbsent(set, k -> new HashMap<>());
        for (String key : new ArrayList<>(set.getMarkers().keySet())) {
            if (!want.containsKey(key)) { set.remove(key); have.remove(key); }
        }
        for (Map.Entry<String, Object[]> e : want.entrySet()) {
            String sig = (String) e.getValue()[1];
            if (sig.equals(have.get(e.getKey())) && set.getMarkers().containsKey(e.getKey())) continue;
            set.put(e.getKey(), (Marker) e.getValue()[0]);
            have.put(e.getKey(), sig);
        }
    }

    private static Object[] mk(Marker m, String sig) { return new Object[] { m, sig }; }

    private static Map<String, Object[]> history(String dim, MapFeed.Snapshot s) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.Hit h : s.hits()) {
            if (!h.dim().equals(dim)) continue;
            int[] c = hitColor(h.type());
            ShapeMarker m = new ShapeMarker(typeLabel(h.type()) + " crater", Shape.createCircle(h.x(), h.z(), Math.max(2.0, h.r()), segments(h.r())), (float) h.y() + 0.5F);
            m.setFillColor(new Color(c[0], c[1], c[2], 0.22F));
            m.setLineColor(new Color(c[0], c[1], c[2], 0.95F));
            m.setLineWidth(2);
            m.setDepthTestEnabled(false);
            String detail = "<b>" + esc(typeLabel(h.type())) + (h.tier() > 0 ? " (tier " + h.tier() + (h.refined() ? ", refined" : "") + ")" : "") + "</b><br>crater radius "
                    + Math.round(h.r()) + " blocks<br>" + TIME.format(Instant.ofEpochMilli(h.realTime())) + (h.owner() != null ? "<br>fired by " + esc(h.owner()) : "");
            m.setDetail(detail);
            String id = "h-" + h.realTime() + "-" + (long) h.x() + "-" + (long) h.z();
            out.put(id, mk(m, detail + h.r()));
        }
        return out;
    }

    private static Map<String, Object[]> zones(String dim, MapFeed.Snapshot s) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.Zone z : s.zoneList()) {
            if (!z.dim().equals(dim)) continue;
            int[] c = zoneColor(z.kind());
            String name = zoneName(z.kind());
            ShapeMarker m = new ShapeMarker(name, Shape.createCircle(z.x(), z.z(), z.r(), segments(z.r())), (float) z.y() + 1.0F);
            m.setFillColor(new Color(c[0], c[1], c[2], 0.18F));
            m.setLineColor(new Color(c[0], c[1], c[2], 0.9F));
            m.setLineWidth(3);
            m.setDepthTestEnabled(false);
            String detail = "<b>" + esc(name) + "</b><br>radius " + Math.round(z.r()) + " blocks<br>about " + z.secondsLeft() + " s left";
            m.setDetail(detail);
            out.put("z-" + z.key(), mk(m, name + z.r() + (z.secondsLeft() / 10)));
        }
        return out;
    }

    private static Map<String, Object[]> border(String dim, MapFeed.Snapshot s) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.Border b : s.borders()) {
            if (!b.dim().equals(dim)) continue;
            double h = b.size() / 2.0;
            ShapeMarker m = new ShapeMarker("World border", Shape.createRect(b.cx() - h, b.cz() - h, b.cx() + h, b.cz() + h), 90.0F);
            m.setFillColor(new Color(255, 80, 80, 0.0F));
            m.setLineColor(new Color(255, 80, 80, 0.95F));
            m.setLineWidth(3);
            m.setDepthTestEnabled(false);
            m.setDetail("<b>World border</b><br>" + Math.round(b.size()) + " x " + Math.round(b.size()) + " blocks, centred on " + Math.round(b.cx()) + " " + Math.round(b.cz()));
            out.put("border", mk(m, b.cx() + "/" + b.cz() + "/" + b.size()));
        }
        return out;
    }

    private static Map<String, Object[]> machines(String dim, MapFeed.Snapshot s, boolean admin) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.Machine m : s.machines()) {
            if (!m.dim().equals(dim)) continue;
            if (!admin && m.camo()) continue;
            String color = kindColor(m.kind());
            String what = admin ? m.type().replace('_', ' ') + (m.decoy() ? " (DECOY)" : "") + (m.camo() ? " (camouflaged)" : "") : kindName(m.kind());
            String tip = esc(what + " - " + m.owner());
            String border = admin && m.decoy() ? "dashed" : "solid";
            String html = "<div title=\"" + tip + "\" style=\"width:12px;height:12px;border-radius:" + (m.kind() == 1 ? "2px" : "50%") + ";background:" + color
                    + ";border:2px " + border + " #fff;box-shadow:0 0 3px #000;\"></div>";
            HtmlMarker h = new HtmlMarker(what + " (" + m.owner() + ")", new Vector3d(m.x(), m.y(), m.z()), html, new Vector2i(8, 8));
            h.setMaxDistance(6000.0);
            out.put("m-" + (long) m.x() + "-" + (long) m.y() + "-" + (long) m.z(), mk(h, html + what));
        }
        return out;
    }

    private static Map<String, Object[]> flights(String dim, MapFeed.Snapshot s) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.FlightLine f : s.flights()) {
            if (!f.dim().equals(dim)) continue;
            int[] c = tierColor(f.tier());
            String what = (f.cruise() ? "Cruise missile" : "Missile") + " T" + f.tier() + " " + typeLabel(f.warhead());
            String info = "<b>" + esc(what) + "</b><br>fired by " + esc(f.owner()) + "<br>" + (f.targetKnown() ? "about " + f.secondsLeft() + " s to impact" : "target unknown");
            if (f.done().length >= 6) {
                LineMarker done = new LineMarker(what + " (flown)", line(f.done()));
                done.setLineColor(new Color(c[0], c[1], c[2], 0.95F));
                done.setLineWidth(3);
                done.setDepthTestEnabled(false);
                done.setDetail(info);
                out.put("f" + f.id() + "-d", mk(done, "d" + f.cx() / 8 + f.cz() / 8));
            }
            if (f.left().length >= 6) {
                LineMarker left = new LineMarker(what + " (to go)", line(f.left()));
                left.setLineColor(new Color(c[0], c[1], c[2], 0.4F));
                left.setLineWidth(2);
                left.setDepthTestEnabled(false);
                left.setDetail(info);
                out.put("f" + f.id() + "-l", mk(left, "l" + f.cx() / 8 + f.cz() / 8));
            }
            String html = "<div title=\"" + esc(what + " - " + f.owner()) + "\" style=\"width:10px;height:10px;transform:rotate(45deg);background:rgb(" + c[0] + "," + c[1] + "," + c[2]
                    + ");border:2px solid #fff;box-shadow:0 0 5px #fff;\"></div>";
            HtmlMarker now = new HtmlMarker(what, new Vector3d(f.cx(), f.cy(), f.cz()), html, new Vector2i(7, 7));
            out.put("f" + f.id() + "-n", mk(now, "n" + (long) (f.cx() / 4) + "/" + (long) (f.cz() / 4)));
        }
        return out;
    }

    private static Map<String, Object[]> ships(String dim, MapFeed.Snapshot s) {
        Map<String, Object[]> out = new LinkedHashMap<>();
        for (MapFeed.Ship sh : s.ships()) {
            if (!sh.dim().equals(dim)) continue;
            String what = "Airship " + (sh.name() == null || sh.name().isEmpty() || sh.name().startsWith("(") ? "" : sh.name() + " ") + "(" + sh.blocks() + " blocks, " + Math.round(sh.size()) + " long)";
            String html = "<div title=\"" + esc(what) + "\" style=\"width:14px;height:14px;transform:rotate(45deg);background:#f59e0b;border:2px solid #fff;box-shadow:0 0 4px #000;\"></div>";
            out.put("s-" + sh.id(), mk(new HtmlMarker(what, new Vector3d(sh.x(), sh.y(), sh.z()), html, new Vector2i(9, 9)), "s" + (long) (sh.x() / 4) + "/" + (long) (sh.z() / 4) + "/" + (long) (sh.y() / 4)));
        }
        return out;
    }

    private static Line line(double[] xyz) {
        Vector3d[] p = new Vector3d[xyz.length / 3];
        for (int i = 0; i < p.length; i++) p[i] = new Vector3d(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
        return new Line(p);
    }

    private static int segments(double r) { return (int) Math.max(16, Math.min(72, r * 0.6)); }

    static String esc(String s) { return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    static String typeLabel(String t) {
        if (t == null || t.isEmpty()) return "Blast";
        String x = t.replace('_', ' ');
        if (x.length() <= 3) return x.toUpperCase(Locale.ROOT);
        return Character.toUpperCase(x.charAt(0)) + x.substring(1);
    }

    private static int[] hitColor(String type) {
        return switch (type == null ? "" : type) {
            case "nuke", "thermo", "rod", "hemp" -> new int[] { 255, 40, 40 };
            case "thermobaric", "incendiary", "bunker" -> new int[] { 255, 130, 30 };
            default -> new int[] { 250, 190, 40 };
        };
    }

    private static int[] zoneColor(int kind) {
        if (kind == WarheadFx.EMP || kind == WarheadFx.HEMP_FIELD) return new int[] { 80, 200, 255 };
        if (kind == WarheadFx.SEISMIC_QUAKE) return new int[] { 160, 110, 60 };
        if (kind == WarheadFx.IGNITE) return new int[] { 255, 120, 30 };
        if (kind == WarheadFx.FUEL) return new int[] { 240, 220, 60 };
        if (kind == WarheadFx.CHAFF) return new int[] { 200, 200, 210 };
        if (kind == WarheadFx.ROD_FALL) return new int[] { 255, 40, 40 };
        return new int[] { 255, 80, 80 };
    }

    private static String zoneName(int kind) {
        if (kind == WarheadFx.EMP) return "EMP field";
        if (kind == WarheadFx.HEMP_FIELD) return "HEMP field";
        if (kind == WarheadFx.SEISMIC_QUAKE) return "Seismic zone";
        if (kind == WarheadFx.IGNITE) return "Fire zone";
        if (kind == WarheadFx.FUEL) return "Fuel cloud";
        if (kind == WarheadFx.CHAFF) return "Chaff cloud";
        if (kind == WarheadFx.ROD_FALL) return "Kinetic rod falling";
        if (kind == WarheadFx.BOMBLETS) return "Cluster carpet";
        if (kind == WarheadFx.SHOCK) return "Shockwave";
        return "Zone";
    }

    private static int[] tierColor(int tier) {
        return switch (tier) { case 1 -> new int[] { 250, 204, 21 }; case 2 -> new int[] { 251, 146, 60 }; case 3 -> new int[] { 239, 68, 68 }; default -> new int[] { 168, 85, 247 }; };
    }

    private static String kindColor(int kind) {
        return switch (kind) { case Recon.ICON_LAUNCH -> "#e5484d"; case Recon.ICON_DEFENCE -> "#3b82f6"; case Recon.ICON_RADAR -> "#22c55e"; case Recon.ICON_STATION -> "#a855f7"; default -> "#9ca3af"; };
    }

    private static String kindName(int kind) {
        return switch (kind) { case Recon.ICON_LAUNCH -> "launch site"; case Recon.ICON_DEFENCE -> "air defence"; case Recon.ICON_RADAR -> "radar"; case Recon.ICON_STATION -> "ground station"; default -> "base"; };
    }

    static String status() {
        BlueMapAPI a = api;
        if (a == null) return "BlueMap API not enabled";
        StringBuilder sb = new StringBuilder();
        int maps = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlueMapMap m : a.getMaps()) {
            maps++;
            for (String id : ALL) {
                MarkerSet set = m.getMarkerSets().get(id);
                if (set != null) counts.merge(id, set.getMarkers().size(), Integer::sum);
            }
        }
        sb.append(maps).append(" map(s); ");
        if (counts.isEmpty()) sb.append("no WMD marker sets yet (first refresh: up to ").append(Config.Server.blueMapUpdateSeconds()).append(" s)");
        for (Map.Entry<String, Integer> e : counts.entrySet()) sb.append(e.getKey()).append(' ').append(e.getValue()).append(", ");
        return sb.toString();
    }

    static void clearAll() {
        BlueMapAPI a = api;
        if (a == null) return;
        for (BlueMapMap m : a.getMaps()) for (String id : ALL) m.getMarkerSets().remove(id);
        SIGS.clear();
    }
}
