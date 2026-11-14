package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class DeckConsoleClient {

    private DeckConsoleClient() {}

    private static BlockPos pos;
    private static List<CompoundTag> rows = List.of();
    private static CompoundTag sum = new CompoundTag();
    private static String msg = "";
    private static long msgAt;
    private static boolean noPower;

    static void onWing(DeckConsolePayloads.Wing p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!p.console().equals(pos)) { msg = ""; }
        pos = p.console();
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : p.data().getList("rows", Tag.TAG_COMPOUND)) l.add((CompoundTag) t);
        rows = l;
        sum = p.data().getCompound("sum");
        noPower = p.data().getBoolean("np");
        String m = p.data().getString("msg");
        if (!m.isEmpty()) { msg = m; msgAt = mc.level.getGameTime(); }
        if (p.open() && !(mc.screen instanceof DeckConsoleScreen s && s.pos().equals(pos))) mc.setScreen(new DeckConsoleScreen(pos));
    }

    static List<CompoundTag> rows(BlockPos at) { return at.equals(pos) ? rows : List.of(); }
    static CompoundTag sum(BlockPos at) { return at.equals(pos) ? sum : new CompoundTag(); }
    static boolean noPower(BlockPos at) { return at.equals(pos) && noPower; }

    static String msg(BlockPos at, long now) { return at.equals(pos) && now - msgAt < 160 ? msg : ""; }
}
