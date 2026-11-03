package com.ashraf.wmd;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public final class BoneOverrides {
    private BoneOverrides() {}

    public static final String[] CHANNELS = { "rotX", "rotY", "rotZ", "posX", "posY", "posZ" };

    private static final Map<String, Map<String, float[]>> OV = new ConcurrentHashMap<>();

    private static final Map<GeoModel<?>, Map<String, float[]>> APPLIED = new WeakHashMap<>();

    static final Set<String> SEEN = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded;

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("wmd").resolve("bone_overrides.json"); }
    static Path exportFile() { return FMLPaths.CONFIGDIR.get().resolve("wmd").resolve("bone_overrides_export.txt"); }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (OV) {
            if (loaded) return;
            loaded = true;
            Path f = file();
            if (!Files.exists(f)) return;
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                JsonObject root = new Gson().fromJson(r, JsonObject.class);
                if (root == null) return;
                for (Map.Entry<String, JsonElement> m : root.entrySet()) {
                    if (!m.getValue().isJsonObject()) continue;
                    for (Map.Entry<String, JsonElement> b : m.getValue().getAsJsonObject().entrySet()) {
                        if (!b.getValue().isJsonObject()) continue;
                        for (Map.Entry<String, JsonElement> c : b.getValue().getAsJsonObject().entrySet()) {
                            if (channel(c.getKey()) < 0 || !c.getValue().isJsonObject()) continue;
                            JsonObject o = c.getValue().getAsJsonObject();
                            float sign = o.has("sign") ? (o.get("sign").getAsFloat() < 0 ? -1.0F : 1.0F) : 1.0F;
                            float off = o.has("offset") ? o.get("offset").getAsFloat() : 0.0F;
                            if (sign != 1.0F || off != 0.0F) OV.computeIfAbsent(m.getKey(), k -> new ConcurrentHashMap<>()).put(b.getKey() + "/" + c.getKey(), new float[] { sign, off });
                        }
                    }
                }
                Wmd.LOGGER.info("Bone overrides loaded from {}: {}", f, count());
            } catch (Exception e) {
                Wmd.LOGGER.error("Could not read bone overrides {}", f, e);
            }
        }
    }

    static int channel(String ch) {
        for (int i = 0; i < CHANNELS.length; i++) if (CHANNELS[i].equals(ch)) return i;
        return -1;
    }

    static int count() { int n = 0; for (Map<String, float[]> m : OV.values()) n += m.size(); return n; }

    public static String modelName(ResourceLocation geo) {
        String p = geo.getPath();
        int s = p.lastIndexOf('/');
        if (s >= 0) p = p.substring(s + 1);
        if (p.endsWith(".geo.json")) p = p.substring(0, p.length() - ".geo.json".length());
        else if (p.endsWith(".json")) p = p.substring(0, p.length() - 5);
        return p;
    }

    public static void apply(GeoModel<?> model, ResourceLocation geo) {
        ensureLoaded();
        String name = modelName(geo);
        SEEN.add(name);
        Map<String, float[]> last = APPLIED.get(model);
        if (last != null && !last.isEmpty()) {
            for (Map.Entry<String, float[]> e : last.entrySet()) {
                String[] bc = e.getKey().split("/", 2);
                GeoBone b = model.getBone(bc[0]).orElse(null);
                int ch = channel(bc[1]);
                if (b != null && ch >= 0 && get(b, ch) == e.getValue()[1]) set(b, ch, e.getValue()[0]);
            }
            last.clear();
        }
        Map<String, float[]> ov = OV.get(name);
        if (ov == null || ov.isEmpty()) return;
        if (last == null) { last = new java.util.HashMap<>(); APPLIED.put(model, last); }
        for (Map.Entry<String, float[]> e : ov.entrySet()) {
            String[] bc = e.getKey().split("/", 2);
            GeoBone b = model.getBone(bc[0]).orElse(null);
            int ch = channel(bc[1]);
            if (b == null || ch < 0) continue;
            float v = get(b, ch);
            float off = ch < 3 ? (float) Math.toRadians(e.getValue()[1]) : e.getValue()[1];
            float nv = e.getValue()[0] * v + off;
            set(b, ch, nv);
            last.put(e.getKey(), new float[] { v, nv });
        }
    }

    private static float get(GeoBone b, int ch) {
        return switch (ch) {
            case 0 -> b.getRotX(); case 1 -> b.getRotY(); case 2 -> b.getRotZ();
            case 3 -> b.getPosX(); case 4 -> b.getPosY(); default -> b.getPosZ();
        };
    }

    private static void set(GeoBone b, int ch, float v) {
        switch (ch) {
            case 0 -> b.setRotX(v); case 1 -> b.setRotY(v); case 2 -> b.setRotZ(v);
            case 3 -> b.setPosX(v); case 4 -> b.setPosY(v); default -> b.setPosZ(v);
        }
    }

    public static float deg(String model, String bone, String ch, float deg) {
        ensureLoaded();
        SEEN.add(model);
        Map<String, float[]> ov = OV.get(model);
        if (ov == null) return deg;
        float[] o = ov.get(bone + "/" + ch);
        return o == null ? deg : o[0] * deg + o[1];
    }

    public static float px(String model, String bone, String ch) {
        Map<String, float[]> ov = OV.get(model);
        if (ov == null) return 0.0F;
        float[] o = ov.get(bone + "/" + ch);
        return o == null ? 0.0F : o[1];
    }

    static float flip(String model, String bone, String ch) {
        ensureLoaded();
        float[] o = OV.computeIfAbsent(model, k -> new ConcurrentHashMap<>()).computeIfAbsent(bone + "/" + ch, k -> new float[] { 1.0F, 0.0F });
        o[0] = -o[0];
        tidy(model, bone + "/" + ch);
        save();
        return o[0];
    }

    static void offset(String model, String bone, String ch, float v) {
        ensureLoaded();
        float[] o = OV.computeIfAbsent(model, k -> new ConcurrentHashMap<>()).computeIfAbsent(bone + "/" + ch, k -> new float[] { 1.0F, 0.0F });
        o[1] = v;
        tidy(model, bone + "/" + ch);
        save();
    }

    static boolean reset(String model, String bone, String ch) {
        ensureLoaded();
        Map<String, float[]> m = OV.get(model);
        boolean had = m != null && m.remove(bone + "/" + ch) != null;
        if (m != null && m.isEmpty()) OV.remove(model);
        save();
        return had;
    }

    static int clearAll() { ensureLoaded(); int n = count(); OV.clear(); save(); return n; }

    private static void tidy(String model, String key) {
        Map<String, float[]> m = OV.get(model);
        if (m == null) return;
        float[] o = m.get(key);
        if (o != null && o[0] == 1.0F && o[1] == 0.0F) m.remove(key);
        if (m.isEmpty()) OV.remove(model);
    }

    static List<Object[]> rows() {
        ensureLoaded();
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, float[]>> m : new TreeMap<>(OV).entrySet())
            for (Map.Entry<String, float[]> e : new TreeMap<>(m.getValue()).entrySet()) {
                String[] bc = e.getKey().split("/", 2);
                out.add(new Object[] { m.getKey(), bc[0], bc[1], e.getValue()[0], e.getValue()[1] });
            }
        return out;
    }

    static String describe(float sign, float off, String ch) {
        String u = ch.startsWith("rot") ? " deg" : " px";
        if (sign < 0 && off != 0.0F) return "flipped, offset " + off + u;
        if (sign < 0) return "flipped";
        return "offset " + off + u;
    }

    static String javaLine(String model, String bone, String ch, float sign, float off) {
        String cap = ch.substring(0, 1).toUpperCase() + ch.substring(1);
        String offTxt = off == 0.0F ? "" : ch.startsWith("rot") ? " + (float) Math.toRadians(" + off + ")" : " + " + off + "F";
        String val = (sign < 0 ? "-" : "") + "b.get" + cap + "()" + offTxt;
        return "// " + model + ": " + describe(sign, off, ch) + "\ngetBone(\"" + bone + "\").ifPresent(b -> b.set" + cap + "(" + val + "));";
    }

    static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Map<String, float[]>> m : new TreeMap<>(OV).entrySet()) {
            JsonObject mo = new JsonObject();
            for (Map.Entry<String, float[]> e : new TreeMap<>(m.getValue()).entrySet()) {
                String[] bc = e.getKey().split("/", 2);
                JsonObject bo = mo.has(bc[0]) ? mo.getAsJsonObject(bc[0]) : new JsonObject();
                JsonObject co = new JsonObject();
                co.addProperty("sign", e.getValue()[0] < 0 ? -1 : 1);
                co.addProperty("offset", e.getValue()[1]);
                bo.add(bc[1], co);
                mo.add(bc[0], bo);
            }
            root.add(m.getKey(), mo);
        }
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Gson g = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) { g.toJson(root, w); }
        } catch (Exception e) {
            Wmd.LOGGER.error("Could not write bone overrides {}", f, e);
        }
    }

    static String export() {
        StringBuilder sb = new StringBuilder("// WMD bone overrides export (" + count() + "), from config/wmd/bone_overrides.json\n");
        sb.append("// GeckoLib models: paste each line at the END of that model's setCustomAnimations (or flip the sign constant it names);\n");
        sb.append("// satellites / drone / payload unit (SatelliteMesh.drawBone): rot values there are DEGREES, change the angle passed in instead.\n");
        for (Object[] r : rows()) sb.append(javaLine((String) r[0], (String) r[1], (String) r[2], (Float) r[3], (Float) r[4])).append('\n');
        save();
        try {
            Files.createDirectories(exportFile().getParent());
            Files.writeString(exportFile(), sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Wmd.LOGGER.error("Could not write {}", exportFile(), e);
        }
        return sb.toString();
    }
}
