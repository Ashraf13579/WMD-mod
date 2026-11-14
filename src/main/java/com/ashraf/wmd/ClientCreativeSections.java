package com.ashraf.wmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ClientCreativeSections {

    private ClientCreativeSections() {}

    private record Section(String title, List<String> match) {}

    private record Banner(int row, Component title, ItemStack icon) {}

    private static Map<String, List<Section>> config;
    private static final List<Banner> BANNERS = Statics.client(new ArrayList<>());

    public static boolean layout(CreativeModeTab tab, NonNullList<ItemStack> items) {
        BANNERS.clear();
        ResourceLocation id = tab == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
        if (id == null || !Wmd.MODID.equals(id.getNamespace())) return false;
        List<Section> secs = config().get(id.getPath());
        if (secs == null || secs.isEmpty()) return false;
        List<List<ItemStack>> groups = new ArrayList<>();
        for (int i = 0; i <= secs.size(); i++) groups.add(new ArrayList<>());
        for (ItemStack s : items) {
            if (s.isEmpty()) continue;
            groups.get(sectionOf(secs, s.getItem())).add(s);
        }
        items.clear();
        for (int i = 0; i <= secs.size(); i++) {
            List<ItemStack> g = groups.get(i);
            if (g.isEmpty()) continue;
            Component title = i < secs.size() ? Component.translatable(secs.get(i).title()) : Component.translatable("creative.wmd.section.more");
            BANNERS.add(new Banner(items.size() / 9, title, g.get(0).copyWithCount(1)));
            for (int k = 0; k < 9; k++) items.add(ItemStack.EMPTY);
            items.addAll(g);
            while (items.size() % 9 != 0) items.add(ItemStack.EMPTY);
        }
        return true;
    }

    private static int sectionOf(List<Section> secs, Item item) {
        ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
        String path = rl == null ? "" : rl.getPath();
        String cls = item.getClass().getSimpleName();
        String blockCls = item instanceof BlockItem b ? b.getBlock().getClass().getSimpleName() : "";
        for (int i = 0; i < secs.size(); i++)
            for (String m : secs.get(i).match()) if (matches(m, path, cls, blockCls)) return i;
        return secs.size();
    }

    static boolean matches(String m, String path, String cls, String blockCls) {
        if (m.startsWith("@")) { String c = m.substring(1); return c.equals(cls) || c.equals(blockCls); }
        boolean a = m.startsWith("*"), z = m.endsWith("*") && m.length() > 1;
        String core = m.substring(a ? 1 : 0, m.length() - (z ? 1 : 0));
        if (a && z) return path.contains(core);
        if (a) return path.endsWith(core);
        if (z) return path.startsWith(core);
        return path.equals(core);
    }

    private static Map<String, List<Section>> config() {
        if (config != null) return config;
        Map<String, List<Section>> m = new HashMap<>();
        try (InputStream in = ClientCreativeSections.class.getResourceAsStream("/assets/wmd/creative_sections.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                    if (e.getKey().startsWith("_") || !e.getValue().isJsonArray()) continue;
                    List<Section> l = new ArrayList<>();
                    for (JsonElement s : e.getValue().getAsJsonArray()) {
                        JsonObject o = s.getAsJsonObject();
                        List<String> match = new ArrayList<>();
                        JsonArray a = o.getAsJsonArray("items");
                        if (a != null) for (JsonElement x : a) match.add(x.getAsString());
                        l.add(new Section(o.get("title").getAsString(), match));
                    }
                    m.put(e.getKey(), l);
                }
            }
        } catch (Exception ex) {
            Wmd.LOGGER.warn("WMD creative sections: could not read creative_sections.json (the tabs show without sections)", ex);
        }
        config = m;
        return config;
    }

    public static void draw(GuiGraphics g, int left, int top, int firstRow) {
        if (BANNERS.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        for (Banner b : BANNERS) {
            int r = b.row() - firstRow;
            if (r < 0 || r >= 5) continue;
            int x = left + 8, y = top + 17 + r * 18, w = 162, h = 18;
            g.fill(x, y, x + w, y + h, 0xFF1B222B);
            g.fill(x, y, x + w, y + 1, 0xFF4A5868);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF0F1318);
            g.fill(x, y + 1, x + 2, y + h - 1, WmdUi.BLUE);
            g.renderItem(b.icon(), x + 4, y + 1);
            g.drawString(font, b.title(), x + 24, y + 5, WmdUi.BLUE_LIGHT, true);
        }
    }
}
