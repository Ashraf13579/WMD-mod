package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class HullPage {

    private HullPage() {}

    static final int BOW = 0, MID = 1, STERN = 2;

    static CompoundTag compute(ServerLevel sl, BlockPos pos, Direction screen) {
        CompoundTag t = new CompoundTag();
        UUID id = DeepSeasBridge.subAt(sl, pos);
        DeepSeasBridge.SubState st = id == null ? null : DeepSeasBridge.state(id);
        if (st == null) { t.putBoolean("on", false); t.putBoolean("ds", DeepSeasBridge.active()); return t; }
        t.putBoolean("on", true);
        t.putInt("d", Math.max(0, st.depth()));
        t.putInt("c", DeepSeasBridge.crushDepth(sl, id));
        String w = DeepSeasBridge.weakestHullBlock(sl, id);
        if (w != null) t.putString("w", w);
        t.putInt("b", (int) Math.round(DeepHull.implodeBonus(sl, id)));
        t.putBoolean("s", st.sealed());
        t.putBoolean("x", st.breached());
        t.putInt("k", st.cracks());
        t.putBoolean("i", st.imploding());
        Set<BlockPos> water = DeepSeasBridge.waterCells(id), room = new java.util.HashSet<>(DeepSeasBridge.interiorCells(id));
        room.addAll(water);

        UUID ship = SableBridge.shipIdAt(sl, pos);
        int[] box = ship == null ? null : SableBridge.plotBox(sl, ship);
        List<BlockPos> holes = openings(sl, pos, box, room);
        t.putBoolean("o", room.isEmpty() || !holes.isEmpty());
        Map<BlockPos, Boolean> leaks = DeepSeasBridge.leaks(id);

        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : room) { x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX()); z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ()); }
        if (room.isEmpty()) {
            if (box != null) { x0 = box[0]; x1 = box[3]; z0 = box[2]; z1 = box[5]; }
            else { x0 = x1 = pos.getX(); z0 = z1 = pos.getZ(); }
        }
        boolean alongX = x1 - x0 >= z1 - z0;
        Direction look = screen.getOpposite();
        int sign = alongX ? (look.getStepX() != 0 ? look.getStepX() : 1) : (look.getStepZ() != 0 ? look.getStepZ() : 1);
        int lo = alongX ? (sign > 0 ? x0 : -x1) : (sign > 0 ? z0 : -z1), hi = alongX ? (sign > 0 ? x1 : -x0) : (sign > 0 ? z1 : -z0);
        int[] fw = new int[3], fr = new int[3];
        for (BlockPos p : room) fr[section(fwd(p, alongX, sign), lo, hi)]++;
        for (BlockPos p : water) fw[section(fwd(p, alongX, sign), lo, hi)]++;
        t.putIntArray("fw", fw);
        t.putIntArray("fr", fr);

        int me = fwd(pos, alongX, sign);
        List<Map.Entry<BlockPos, Boolean>> ls = new ArrayList<>(leaks.entrySet());
        ls.sort((a, b) -> a.getValue() != b.getValue() ? (a.getValue() ? -1 : 1) : Double.compare(a.getKey().distSqr(pos), b.getKey().distSqr(pos)));
        ListTag lk = new ListTag();
        for (BlockPos p : holes) {
            if (lk.size() >= Balance.SONAR_HULL_LEAKS) break;
            CompoundTag l = new CompoundTag();
            l.putBoolean("o", true);
            l.putInt("s", section(fwd(p, alongX, sign), lo, hi));
            l.putInt("a", fwd(p, alongX, sign) - me);
            l.putInt("h", p.getY() - pos.getY());
            lk.add(l);
        }
        for (Map.Entry<BlockPos, Boolean> e : ls) {
            if (lk.size() >= Balance.SONAR_HULL_LEAKS) break;
            CompoundTag l = new CompoundTag();
            BlockPos p = e.getKey();
            l.putBoolean("t", e.getValue());
            l.putInt("s", section(fwd(p, alongX, sign), lo, hi));
            l.putInt("a", fwd(p, alongX, sign) - me);
            l.putInt("h", p.getY() - pos.getY());
            lk.add(l);
        }
        t.put("lk", lk);
        t.putInt("ln", holes.size() + ls.size());
        return t;
    }

    static List<BlockPos> openings(ServerLevel sl, BlockPos pos, int[] box, java.util.Collection<BlockPos> rooms) {
        List<BlockPos> out = new ArrayList<>();
        if (box == null) return out;
        int x0 = box[0], y0 = box[1], z0 = box[2], sx = box[3] - x0 + 1, sy = box[4] - y0 + 1, sz = box[5] - z0 + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > Balance.HULL_SCAN_MAX) return out;
        boolean[] solid = new boolean[sx * sy * sz];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int[] colTop = new int[sx * sz], colBot = new int[sx * sz], xMin = new int[sy * sz], xMax = new int[sy * sz], zMin = new int[sx * sy], zMax = new int[sx * sy];
        java.util.Arrays.fill(colTop, -1); java.util.Arrays.fill(colBot, Integer.MAX_VALUE);
        java.util.Arrays.fill(xMin, Integer.MAX_VALUE); java.util.Arrays.fill(xMax, -1);
        java.util.Arrays.fill(zMin, Integer.MAX_VALUE); java.util.Arrays.fill(zMax, -1);
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            m.set(x0 + x, y0 + y, z0 + z);
            if (sl.getBlockState(m).getCollisionShape(sl, m).isEmpty()) continue;
            solid[(x * sy + y) * sz + z] = true;
            int c = x * sz + z, r = y * sz + z, q = x * sy + y;
            colTop[c] = Math.max(colTop[c], y); colBot[c] = Math.min(colBot[c], y);
            xMin[r] = Math.min(xMin[r], x); xMax[r] = Math.max(xMax[r], x);
            zMin[q] = Math.min(zMin[q], z); zMax[q] = Math.max(zMax[q], z);
        }
        int px = pos.getX() - x0, py = pos.getY() - y0, pz = pos.getZ() - z0;
        boolean[] seen = new boolean[solid.length];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();

        List<int[]> starts = new ArrayList<>();
        for (Direction d : Direction.values()) starts.add(new int[] { px + d.getStepX(), py + d.getStepY(), pz + d.getStepZ() });
        if (rooms != null) for (BlockPos r : rooms) starts.add(new int[] { r.getX() - x0, r.getY() - y0, r.getZ() - z0 });
        for (int[] st : starts) {
            int nx = st[0], ny = st[1], nz = st[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
            int i = (nx * sy + ny) * sz + nz;
            if (seen[i] || solid[i] || outside(nx, ny, nz, sy, sz, colTop, colBot, xMin, xMax, zMin, zMax)) continue;
            seen[i] = true;
            q.add(new int[] { nx, ny, nz });
        }
        if (q.isEmpty()) return out;
        int n = 0;
        while (!q.isEmpty() && n++ < Balance.HULL_SCAN_CELLS) {
            int[] c = q.poll();
            for (Direction d : Direction.values()) {
                int nx = c[0] + d.getStepX(), ny = c[1] + d.getStepY(), nz = c[2] + d.getStepZ();
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                int i = (nx * sy + ny) * sz + nz;
                if (seen[i] || solid[i]) continue;
                seen[i] = true;
                if (outside(nx, ny, nz, sy, sz, colTop, colBot, xMin, xMax, zMin, zMax)) {
                    BlockPos hole = new BlockPos(x0 + nx, y0 + ny, z0 + nz);
                    boolean known = false;
                    for (BlockPos h : out) if (h.distManhattan(hole) <= Balance.HULL_HOLE_JOIN) { known = true; break; }
                    if (!known) out.add(hole);
                    continue;
                }
                q.add(new int[] { nx, ny, nz });
            }
        }
        out.sort((a, b) -> Double.compare(a.distSqr(pos), b.distSqr(pos)));
        return out;
    }

    private static boolean outside(int x, int y, int z, int sy, int sz, int[] colTop, int[] colBot, int[] xMin, int[] xMax, int[] zMin, int[] zMax) {
        int c = x * sz + z, r = y * sz + z, q = x * sy + y;
        return y > colTop[c] || y < colBot[c] || x < xMin[r] || x > xMax[r] || z < zMin[q] || z > zMax[q];
    }

    private static int fwd(BlockPos p, boolean alongX, int sign) { return (alongX ? p.getX() : p.getZ()) * sign; }

    static int section(int f, int lo, int hi) {
        if (hi <= lo) return MID;
        double k = (f - lo) / (double) (hi - lo + 1);
        return k >= 2.0 / 3.0 ? BOW : k < 1.0 / 3.0 ? STERN : MID;
    }

    static boolean alarm(CompoundTag h) {
        if (!h.getBoolean("on")) return false;
        int c = h.getInt("c");
        return h.getBoolean("x") || h.getBoolean("o") || h.getBoolean("i") || c > 0 && h.getInt("d") >= c * Balance.SONAR_HULL_WARN;
    }

    static int flooded(CompoundTag h) {
        int n = 0;
        for (int v : h.getIntArray("fw")) n += v;
        return n;
    }

    static String chip(CompoundTag h) {
        if (!h.getBoolean("on")) return "none";
        if (h.getBoolean("i")) return "implode";
        if (h.getBoolean("x")) return "breach";
        if (h.getBoolean("o")) return "hull_open";
        int c = h.getInt("c");
        if (c > 0 && h.getInt("d") >= c * Balance.SONAR_HULL_WARN) return "crush";
        if (flooded(h) > 0) return "flood";
        return h.getBoolean("s") ? "sealed" : "open";
    }

    static int chipKind(CompoundTag h) {
        String c = chip(h);
        return switch (c) { case "implode", "breach", "crush", "hull_open" -> WmdUi.K_BAD; case "flood", "open" -> WmdUi.K_WARN; case "sealed" -> WmdUi.K_GOOD; default -> WmdUi.K_IDLE; };
    }

    static String sectionKey(int s) { return "gui.wmd.sonar.hull_sec" + (s == BOW ? "_bow" : s == MID ? "_mid" : "_stern"); }

    private static final int BAR_BG = 0xFF1A222B, RED = 0xFFE05050, AMBER = 0xFFE0A030, CYAN = 0xFF3AC2D5, WATER = 0xFF3A7BD5, TICK = 0xFFFF8A8A;

    private static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }

    static void paint(LiveScreenRenderer.Canvas k, CompoundTag h, boolean compact) {
        float w = k.width(), ht = k.height(), u = compact ? ht / 64.0F : ht / 82.0F, pad = 4.0F * u, ts = (compact ? 7.0F : 6.5F) * u;
        if (h.isEmpty()) { k.text(tr("gui.wmd.sonar.hull_wait"), w / 2.0F, ht * 0.42F, ts, w - 2 * pad, true, 0xFF8E99A6); return; }
        if (!h.getBoolean("on")) {
            k.text(tr(h.getBoolean("ds") ? "gui.wmd.sonar.hull_nosub1" : "gui.wmd.sonar.hull_nods1"), w / 2.0F, ht * 0.38F, ts, w - 2 * pad, true, 0xFF8E99A6);
            k.text(tr(h.getBoolean("ds") ? "gui.wmd.sonar.hull_nosub2" : "gui.wmd.sonar.hull_nods2"), w / 2.0F, ht * 0.38F + ts * 1.4F, ts * 0.85F, w - 2 * pad, true, 0xFF6F7C8A);
            return;
        }
        int d = h.getInt("d"), c = h.getInt("c");
        boolean alarm = alarm(h);

        float y = pad;
        k.text(c > 0 ? tr("gui.wmd.sonar.hull_depth", d, c, Math.round(100.0 * d / c)) : tr("gui.wmd.sonar.hull_depth_nc", d), pad, y, ts, w - 2 * pad, false, alarm ? RED : 0xFFDCE3EA);
        y += ts + 2.0F * u;
        float bx0 = pad, bx1 = w - pad, bh = 5.0F * u;
        k.fill(bx0, y, bx1, y + bh, BAR_BG);
        if (c > 0) {
            float f = (float) Math.min(1.0, d / (double) c);
            k.fill(bx0, y, bx0 + (bx1 - bx0) * f, y + bh, f >= Balance.SONAR_HULL_WARN ? RED : f >= 0.7F ? AMBER : CYAN);
            float tx = bx0 + (bx1 - bx0) * (float) Balance.SONAR_HULL_WARN;
            k.fill(tx, y - 1.0F * u, tx + 0.8F * u, y + bh + 1.0F * u, TICK);
        }
        y += bh + (compact ? 5.0F : 4.0F) * u;

        int[] fw = h.getIntArray("fw"), fr = h.getIntArray("fr");
        k.text(tr("gui.wmd.sonar.hull_flood", flooded(h)), pad, y, ts, w - 2 * pad, false, flooded(h) > 0 ? AMBER : 0xFF8E99A6);
        y += ts + 2.0F * u;
        float lab = (compact ? 22.0F : 26.0F) * u, rowH = compact ? (ht - y - pad) / 3.0F : 9.0F * u;
        for (int s = 0; s < 3; s++) {
            int water = fw.length > s ? fw[s] : 0, room = fr.length > s ? fr[s] : 0;
            float ry = y + s * rowH, by0 = ry + rowH * 0.15F, by1 = ry + rowH * 0.85F;
            k.text(tr(sectionKey(s)), pad, ry + (rowH - ts) / 2.0F, ts, lab - 2.0F * u, false, 0xFFDCE3EA);
            float x0 = pad + lab, x1 = w - pad - (compact ? 0.0F : 22.0F * u);
            k.fill(x0, by0, x1, by1, BAR_BG);
            float f = room > 0 ? Math.min(1.0F, water / (float) room) : water > 0 ? 1.0F : 0.0F;
            if (f > 0) k.fill(x0, by0, x0 + Math.max(0.8F * u, (x1 - x0) * f), by1, f >= 0.5F ? RED : WATER);
            if (!compact) k.text(room > 0 ? Math.round(f * 100.0F) + "%" : water > 0 ? String.valueOf(water) : "-", x1 + 2.0F * u, ry + (rowH - ts) / 2.0F, ts, 20.0F * u, false, f > 0 ? AMBER : 0xFF8E99A6);
        }
    }

    static Component leakLine(CompoundTag l) {
        int a = l.getInt("a"), hh = l.getInt("h");
        Component along = a == 0 ? Component.translatable("gui.wmd.sonar.hull_here") : Component.translatable(a > 0 ? "gui.wmd.sonar.hull_fwd" : "gui.wmd.sonar.hull_aft", Math.abs(a));
        Component up = hh == 0 ? Component.translatable("gui.wmd.sonar.hull_level") : Component.translatable(hh > 0 ? "gui.wmd.sonar.hull_up" : "gui.wmd.sonar.hull_down", Math.abs(hh));
        return Component.translatable("gui.wmd.sonar.hull_leak", Component.translatable(l.getBoolean("o") ? "gui.wmd.sonar.hull_opening" : l.getBoolean("t") ? "gui.wmd.sonar.hull_breach" : "gui.wmd.sonar.hull_crack"),
                Component.translatable(sectionKey(l.getInt("s"))), along, up);
    }

    static List<CompoundTag> leaks(CompoundTag h) {
        List<CompoundTag> l = new ArrayList<>();
        for (Tag t : h.getList("lk", Tag.TAG_COMPOUND)) if (t instanceof CompoundTag c) l.add(c);
        return l;
    }
}
