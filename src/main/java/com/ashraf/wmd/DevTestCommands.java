package com.ashraf.wmd;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Wmd.MODID)
public final class DevTestCommands {

    private DevTestCommands() {}

    private static final String[] TABS = { "wmd_air_battles", "wmd_deep_sea" };

    private static final String[] ARM_STORE = { "aam_heat", "aam_radar", "aam_arm", "aam_agm", "ashm", "bomb_iron", "bomb_laser", "bomb_glide", "bomb_cluster",
            "air_rocket", "ciws_ammo", "flare_pack", "chaff_pod", "jato_booster", "shell_howitzer_he", "shell_howitzer_flak" };

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        if (FMLEnvironment.production) return;
        event.getDispatcher().register(Commands.literal("wmd").requires(s -> s.hasPermission(2)).then(Commands.literal("dev").requires(TestCmds::test)
                .then(Commands.literal("kit")
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(sectionNames(), b))
                                .executes(c -> kit(c.getSource(), StringArgumentType.getString(c, "section")))))
                .then(Commands.literal("fuel").executes(c -> fuel(c.getSource())))
                .then(Commands.literal("arm").executes(c -> arm(c.getSource(), null))
                        .then(Commands.argument("item", StringArgumentType.word()).executes(c -> arm(c.getSource(), StringArgumentType.getString(c, "item")))))));
    }

    private static void say(CommandSourceStack src, String s) { src.sendSuccess(() -> Component.literal(s), false); }

    private static Map<String, Object[]> sections;

    private static Map<String, Object[]> sections() {
        if (sections != null) return sections;
        Map<String, Object[]> m = new LinkedHashMap<>();
        try (InputStream in = DevTestCommands.class.getResourceAsStream("/assets/wmd/creative_sections.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (String tab : TABS) {
                    JsonElement e = root.get(tab);
                    if (e == null || !e.isJsonArray()) continue;
                    for (JsonElement s : e.getAsJsonArray()) {
                        JsonObject o = s.getAsJsonObject();
                        String title = o.get("title").getAsString();
                        List<String> match = new ArrayList<>();
                        JsonArray a = o.getAsJsonArray("items");
                        if (a != null) for (JsonElement x : a) match.add(x.getAsString());
                        m.put(title.substring(title.lastIndexOf('.') + 1), new Object[] { tab, match });
                    }
                }
            }
        } catch (Exception ex) { Wmd.LOGGER.warn("dev kit: could not read creative_sections.json", ex); }
        sections = m;
        return m;
    }

    private static List<String> sectionNames() { List<String> l = new ArrayList<>(sections().keySet()); l.add("list"); return l; }

    private static boolean matches(String m, String path, String cls, String blockCls) {
        if (m.startsWith("@")) { String c = m.substring(1); return c.equals(cls) || c.equals(blockCls); }
        boolean a = m.startsWith("*"), z = m.endsWith("*") && m.length() > 1;
        String core = m.substring(a ? 1 : 0, m.length() - (z ? 1 : 0));
        if (a && z) return path.contains(core);
        if (a) return path.endsWith(core);
        if (z) return path.startsWith(core);
        return path.equals(core);
    }

    @SuppressWarnings("unchecked")
    private static int kit(CommandSourceStack src, String name) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("A player runs this.")); return 0; }
        if ("list".equals(name)) { say(src, "Kits: " + String.join(", ", sections().keySet())); return 1; }
        Object[] sec = sections().get(name);
        if (sec == null) { src.sendFailure(Component.literal("No section '" + name + "'. /wmd dev kit list")); return 0; }
        String tabPath = (String) sec[0];
        List<String> match = (List<String>) sec[1];
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, tabPath));
        if (tab == null) { src.sendFailure(Component.literal("Tab " + tabPath + " is not registered (1.2 gate off?)")); return 0; }
        ServerLevel sl = p.serverLevel();
        CreativeModeTabs.tryRebuildTabContents(sl.enabledFeatures(), true, sl.registryAccess());

        List<Map.Entry<String, Object[]>> order = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : sections().entrySet()) if (tabPath.equals(e.getValue()[0])) order.add(e);
        int given = 0;
        for (ItemStack shown : tab.getDisplayItems()) {
            Item item = shown.getItem();
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            String path = rl == null ? "" : rl.getPath(), cls = item.getClass().getSimpleName();
            String bcls = item instanceof BlockItem b ? b.getBlock().getClass().getSimpleName() : "";
            String first = null;
            for (Map.Entry<String, Object[]> e : order) {
                for (String m : (List<String>) e.getValue()[1]) if (matches(m, path, cls, bcls)) { first = e.getKey(); break; }
                if (first != null) break;
            }
            if (!name.equals(first)) continue;
            ItemStack give = shown.copyWithCount(Math.min(16, shown.getMaxStackSize()));
            if (!p.getInventory().add(give)) p.drop(give, false);
            given++;
        }
        final int n = given;
        say(src, "Kit '" + name + "': " + n + " items" + (n == 0 ? " (none: is the 1.2 gate on?)" : ""));
        return n;
    }

    private static UUID craft(CommandSourceStack src, ServerPlayer p) {
        UUID s = SableBridge.shipUnder(p);
        if (s == null) {
            double best = Double.MAX_VALUE;
            for (SableBridge.ShipInfo si : SableBridge.shipsIntersecting(p.serverLevel(), p.getBoundingBox().inflate(24.0))) {
                double d = si.worldCenter().distanceToSqr(p.position());
                if (d < best) { best = d; s = si.id(); }
            }
        }
        if (s == null) src.sendFailure(Component.literal("No assembled craft under you or within 24 blocks (assemble it first)."));
        return s;
    }

    private static int fuel(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        UUID ship = craft(src, p);
        if (ship == null) return 0;
        ServerLevel sl = p.serverLevel();
        int mb = PowerCoreBlockEntity.moveFuel(sl, ship, 10_000_000, false);
        long fe = 0;
        for (int i = 0; i < 4_000; i++) { int n = PowerCoreBlockEntity.chargeFrom(sl, ship, Integer.MAX_VALUE / 2); if (n <= 0) break; fe += n; }
        int[] f = PowerCoreBlockEntity.fuelOf(sl, ship), c = PowerCoreBlockEntity.chargeOf(sl, ship);
        say(src, String.format("Craft %s: +%,d mB jet fuel, +%,d FE. Now fuel %,d / %,d mB, charge %,d / %,d FE.", ship.toString().substring(0, 8), mb, fe,
                f[0], f[1], c[0], c[1]));
        return 1;
    }

    private static int arm(CommandSourceStack src, String only) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return 0;
        UUID ship = craft(src, p);
        if (ship == null) return 0;
        ServerLevel sl = p.serverLevel();
        SimpleContainer store;
        if (only != null) {
            Item it = BuiltInRegistries.ITEM.get(only.contains(":") ? ResourceLocation.parse(only) : ResourceLocation.fromNamespaceAndPath(Wmd.MODID, only));
            if (it == null || new ItemStack(it).isEmpty()) { src.sendFailure(Component.literal("Unknown item " + only)); return 0; }
            store = new SimpleContainer(new ItemStack(it, 64));
        } else {
            List<ItemStack> l = new ArrayList<>();
            for (String id : ARM_STORE) {
                Item it = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, id));
                if (it != null && !new ItemStack(it).isEmpty()) l.add(new ItemStack(it, 64));
            }
            store = new SimpleContainer(l.toArray(new ItemStack[0]));
        }
        List<StockTargets.Target> ts = StockTargets.of(sl, ship, p.getUUID());
        if (ts.isEmpty()) { src.sendFailure(Component.literal("No weapons of yours on that craft (or it is not your craft).")); return 0; }
        int rounds = 0, blocks = 0;
        for (StockTargets.Target t : ts) {
            int before = rounds;
            for (int guard = 0; guard < 400 && !t.full(); guard++) {
                ItemStack want;
                if (only != null) { want = store.getItem(0).copyWithCount(1); if (!t.accepts(want)) break; }
                else want = t.pick(store);
                if (want.isEmpty() || !t.load(sl, want)) break;
                rounds++;
            }
            if (rounds > before) blocks++;
        }
        say(src, String.format("Armed %d of %d weapon blocks, %d rounds loaded%s.", blocks, ts.size(), rounds, only == null ? "" : " (" + only + ")"));
        return rounds;
    }
}
