package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class BoneDevCommand {
    private BoneDevCommand() {}

    @SubscribeEvent
    static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wmd")
                .then(Commands.literal("dev").requires(s -> !FMLEnvironment.production || s.hasPermission(2))
                        .then(Commands.literal("bone")
                                .then(Commands.literal("list").executes(BoneDevCommand::list))
                                .then(Commands.literal("export").executes(BoneDevCommand::export))
                                .then(Commands.literal("clear").executes(c -> {
                                    int n = BoneOverrides.clearAll();
                                    c.getSource().sendSuccess(() -> Component.literal("Bone overrides cleared (" + n + ")"), false);
                                    return n;
                                }))
                                .then(Commands.argument("model", StringArgumentType.word()).suggests(BoneDevCommand::models)
                                        .then(Commands.argument("bone", StringArgumentType.word()).suggests(BoneDevCommand::bones)
                                                .then(Commands.argument("channel", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BoneOverrides.CHANNELS, b))
                                                        .then(Commands.literal("flip").executes(BoneDevCommand::flip))
                                                        .then(Commands.literal("reset").executes(BoneDevCommand::reset))
                                                        .then(Commands.literal("offset")
                                                                .then(Commands.argument("v", FloatArgumentType.floatArg(-3600.0F, 3600.0F)).executes(BoneDevCommand::offset)))))))));
    }

    private record Target(String model, String bone, String ch) {}

    private static Target target(CommandContext<CommandSourceStack> c) {
        String ch = StringArgumentType.getString(c, "channel");
        if (BoneOverrides.channel(ch) < 0) {
            c.getSource().sendFailure(Component.literal("Channel must be one of rotX rotY rotZ posX posY posZ"));
            return null;
        }
        Target t = new Target(StringArgumentType.getString(c, "model"), StringArgumentType.getString(c, "bone"), ch);
        if (!knownBone(t.model(), t.bone()))
            c.getSource().sendSuccess(() -> Component.literal("Note: bone '" + t.bone() + "' was not found in geo/block/" + t.model() + ".geo.json (kept anyway)").withStyle(ChatFormatting.GRAY), false);
        return t;
    }

    private static int flip(CommandContext<CommandSourceStack> c) {
        Target t = target(c);
        if (t == null) return 0;
        float s = BoneOverrides.flip(t.model(), t.bone(), t.ch());
        c.getSource().sendSuccess(() -> Component.literal(t.model() + " / " + t.bone() + " / " + t.ch() + ": sign " + (s < 0 ? "-1 (flipped)" : "+1 (normal)")), false);
        return 1;
    }

    private static int offset(CommandContext<CommandSourceStack> c) {
        Target t = target(c);
        if (t == null) return 0;
        float v = FloatArgumentType.getFloat(c, "v");
        BoneOverrides.offset(t.model(), t.bone(), t.ch(), v);
        c.getSource().sendSuccess(() -> Component.literal(t.model() + " / " + t.bone() + " / " + t.ch() + ": offset " + v + (t.ch().startsWith("rot") ? " deg" : " px")), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        Target t = target(c);
        if (t == null) return 0;
        boolean had = BoneOverrides.reset(t.model(), t.bone(), t.ch());
        c.getSource().sendSuccess(() -> Component.literal(t.model() + " / " + t.bone() + " / " + t.ch() + (had ? ": reset" : ": had no override")), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<Object[]> rows = BoneOverrides.rows();
        c.getSource().sendSuccess(() -> Component.literal("Bone overrides: " + rows.size() + " (" + BoneOverrides.file() + ")").withStyle(ChatFormatting.GOLD), false);
        for (Object[] r : rows) {
            String line = r[0] + " / " + r[1] + " / " + r[2] + ": " + BoneOverrides.describe((Float) r[3], (Float) r[4], (String) r[2]);
            c.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        }
        return rows.size();
    }

    private static int export(CommandContext<CommandSourceStack> c) {
        String text = BoneOverrides.export();
        List<Object[]> rows = BoneOverrides.rows();
        c.getSource().sendSuccess(() -> Component.literal("Exported " + rows.size() + " bone overrides to " + BoneOverrides.exportFile() + " (click a line to copy it, or the header to copy all)")
                .withStyle(s -> s.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))), false);
        for (Object[] r : rows) {
            String java = BoneOverrides.javaLine((String) r[0], (String) r[1], (String) r[2], (Float) r[3], (Float) r[4]);
            MutableComponent line = Component.literal(java.replace('\n', ' '))
                    .withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, java))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
            c.getSource().sendSuccess(() -> line, false);
        }
        return rows.size();
    }

    private static CompletableFuture<Suggestions> models(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        List<String> names = new ArrayList<>(BoneOverrides.SEEN);
        for (ResourceLocation rl : GeckoLibCache.getBakedModels().keySet())
            if (rl.getNamespace().equals(Wmd.MODID) && rl.getPath().startsWith("geo/block/")) {
                String n = BoneOverrides.modelName(rl);
                if (!names.contains(n)) names.add(n);
            }
        return SharedSuggestionProvider.suggest(names, b);
    }

    private static CompletableFuture<Suggestions> bones(CommandContext<CommandSourceStack> c, SuggestionsBuilder b) {
        List<String> out = new ArrayList<>();
        BakedGeoModel m = baked(StringArgumentType.getString(c, "model"));
        if (m != null) for (GeoBone bone : m.topLevelBones()) collect(bone, out);
        return SharedSuggestionProvider.suggest(out, b);
    }

    private static void collect(GeoBone bone, List<String> out) {
        out.add(bone.getName());
        for (GeoBone ch : bone.getChildBones()) collect(ch, out);
    }

    private static BakedGeoModel baked(String model) {
        return GeckoLibCache.getBakedModels().get(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "geo/block/" + model + ".geo.json"));
    }

    private static boolean knownBone(String model, String bone) {
        BakedGeoModel m = baked(model);
        return m == null || m.getBone(bone).isPresent();
    }
}
