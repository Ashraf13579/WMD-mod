package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

public class PlannerMapScreen extends Screen {

    private static final int SP = 180, TOP = 26, M = 8;
    private static final int C_SRC = 0xFF3A7BD5, C_DST = 0xFFD58A3A, C_BOTH = 0xFF3AC2D5, C_ROBOT = 0xFFE8EEF4, C_XL = 0xFFB07CFF, C_RUNNER = 0xFF8FE3A8, C_STUCK = 0xFFFF4040;
    private static final String P = "gui.wmd.planmap.";
    private static final int[] RT_COLS = { 0xFFFF7AD9, 0xFF7AFFE0, 0xFFFFE07A, 0xFFA0FF7A, 0xFFFF9A6A };
    private static final int C_WAIT = 0xFF5A6068;
    private final Screen parent;
    private final BlockPos pos;
    private final int cx, cz, size;
    private ResourceLocation tex;
    private DynamicTexture dyn;

    private double vx, vz, zoom = 1.0;
    private int mapX, mapY, mapW;

    private CompoundTag dragFrom;
    private double dragMx, dragMy;
    private boolean panning;
    private int selRoute = -1;
    private long selBox;
    private int listScroll;

    private int layer;

    private boolean picking, multi;

    private int selVeh = -1, selRid, addStage;
    private long stepSrc, stepDst;
    private final List<Btn> btns = new ArrayList<>();

    private record Btn(int x, int y, int w, int h, Runnable run) {}

    public PlannerMapScreen(Screen parent, BlockPos pos) {
        super(Component.translatable("gui.wmd.planmap.title"));
        this.parent = parent;
        this.pos = pos;
        this.cx = pos.getX();
        this.cz = pos.getZ();
        this.size = CcPlannerBlockEntity.MAP_R * 2;
        this.vx = cx + 0.5;
        this.vz = cz + 0.5;
    }

    private CcPlannerBlockEntity be() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof CcPlannerBlockEntity p ? p : null;
    }

    private List<CompoundTag> rows(int t) {
        List<CompoundTag> l = new ArrayList<>();
        CcPlannerBlockEntity b = be();
        if (b != null) for (Tag x : b.rows()) if (x instanceof CompoundTag c && c.getInt("t") == t) l.add(c);
        return l;
    }

    private void sample() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        NativeImage img = new NativeImage(size, size, false);
        int x0 = cx - size / 2, z0 = cz - size / 2;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++) {
            int prevH = Integer.MIN_VALUE;
            for (int j = 0; j < size; j++) {
                int x = x0 + i, z = z0 + j;
                int argb;
                if (!level.hasChunk(x >> 4, z >> 4)) argb = 0xFF101418;
                else {
                    int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    mp.set(x, h, z);
                    BlockState st = level.getBlockState(mp);
                    MapColor mc = st.getMapColor(level, mp);
                    int rgb = mc == null ? 0x303030 : mc.col;
                    double k = prevH == Integer.MIN_VALUE || h == prevH ? 0.86 : h > prevH ? 1.0 : 0.70;
                    if (!st.getFluidState().isEmpty()) k = 0.80;
                    prevH = h;
                    int r = (int) (((rgb >> 16) & 255) * k * 0.8), g = (int) (((rgb >> 8) & 255) * k * 0.8), b = (int) ((rgb & 255) * k * 0.8);
                    argb = 0xFF000000 | r << 16 | g << 8 | b;
                }
                img.setPixelRGBA(i, j, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF);
            }
        }
        if (dyn != null) dyn.close();
        dyn = new DynamicTexture(img);
        tex = Minecraft.getInstance().getTextureManager().register("wmd_planner_map", dyn);
    }

    @Override
    protected void init() {
        mapY = TOP;
        mapW = Math.max(120, Math.min(width - SP - 3 * M, height - TOP - M));
        mapX = M;
        if (tex == null) sample();
    }

    @Override
    public void removed() {
        if (dyn != null) { dyn.close(); dyn = null; }
        if (tex != null) { Minecraft.getInstance().getTextureManager().release(tex); tex = null; }
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    private int panelX() { return Math.min(width - SP - M, mapX + mapW + M); }

    private double scale() { return mapW / (double) size * zoom; }
    private double sx(double wx) { return mapX + mapW / 2.0 + (wx - vx) * scale(); }
    private double sy(double wz) { return mapY + mapW / 2.0 + (wz - vz) * scale(); }
    private double wx(double sx) { return vx + (sx - mapX - mapW / 2.0) / scale(); }
    private double wz(double sy) { return vz + (sy - mapY - mapW / 2.0) / scale(); }
    private boolean onMap(double mx, double my) { return WmdUi.over(mx, my, mapX, mapY, mapW, mapW); }

    private int[] rect(CompoundTag b) {
        int[] e = b.getIntArray("b");
        if (e.length < 4) e = new int[] { b.getInt("x"), b.getInt("z"), b.getInt("x"), b.getInt("z") };
        double x0 = sx(e[0]), y0 = sy(e[1]), x1 = sx(e[2] + 1), y1 = sy(e[3] + 1);
        double cxs = (x0 + x1) / 2, cys = (y0 + y1) / 2;
        double hw = Math.max(3, (x1 - x0) / 2), hh = Math.max(3, (y1 - y0) / 2);
        return new int[] { (int) (cxs - hw), (int) (cys - hh), (int) (cxs + hw), (int) (cys + hh) };
    }

    private static int common(CompoundTag src, CompoundTag dst) {
        int[] h = src.getIntArray("has"), o = dst.getIntArray("opt");
        if (dst.getBoolean("any")) return h.length > 0 ? h[0] : -1;
        for (int a : o) for (int b : h) if (a == b) return a;
        return -1;
    }

    private CompoundTag focus() { return dragFrom != null ? dragFrom : selBox != 0 ? boxByPos(selBox) : null; }

    private boolean visible(CompoundTag b) {
        if (addStage == 1) return (b.getInt("r") & 1) != 0;
        if (addStage == 2) return (b.getInt("r") & 2) != 0 && b.getLong("p") != stepSrc;
        if (layer != 0 && (b.getInt("cat") & layer) == 0) return false;
        CompoundTag f = focus();
        if (f == null || f.getLong("p") == b.getLong("p")) return true;
        boolean srcFocus = dragFrom != null || (f.getInt("r") & 2) == 0;
        if (srcFocus) return (b.getInt("r") & 2) != 0 && common(f, b) >= 0;
        boolean ok = (b.getInt("r") & 1) != 0 && common(b, f) >= 0;
        if ((f.getInt("r") & 1) != 0) ok |= (b.getInt("r") & 2) != 0 && common(f, b) >= 0;
        return ok;
    }

    private CompoundTag boxAt(double mx, double my) {
        CompoundTag best = null;
        double area = Double.MAX_VALUE;
        for (CompoundTag b : rows(0)) {
            if (!visible(b)) continue;
            int[] r = rect(b);
            if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) {
                double a = (r[2] - r[0]) * (double) (r[3] - r[1]);
                if (a < area) { area = a; best = b; }
            }
        }
        return best;
    }

    private CompoundTag boxByPos(long p) {
        for (CompoundTag b : rows(0)) if (b.getLong("p") == p) return b;
        return null;
    }

    private double[] centre(CompoundTag b) { int[] r = rect(b); return new double[] { (r[0] + r[2]) / 2.0, (r[1] + r[3]) / 2.0 }; }

    private double[] segment(CompoundTag r) {
        CompoundTag d = boxByPos(r.getLong("dst"));
        if (d == null) return null;
        double[] b = centre(d);
        CompoundTag s = r.getLong("src") == 0 ? null : boxByPos(r.getLong("src"));
        if (s == null) return new double[] { b[0] - 14, b[1] - 14, b[0], b[1] };
        double[] a = centre(s);
        return new double[] { a[0], a[1], b[0], b[1] };
    }

    private int routeAt(double mx, double my) {
        int best = -1;
        double bd = 5.0;
        for (CompoundTag r : rows(2)) {
            double[] s = segment(r);
            if (s == null) continue;
            double dx = s[2] - s[0], dy = s[3] - s[1], len2 = dx * dx + dy * dy;
            double t = len2 < 1e-6 ? 0 : Mth.clamp(((mx - s[0]) * dx + (my - s[1]) * dy) / len2, 0, 1);
            double d = Math.hypot(mx - (s[0] + t * dx), my - (s[1] + t * dy));
            if (d < bd) { bd = d; best = r.getInt("id"); }
        }
        return best;
    }

    private CompoundTag route(int id) {
        for (CompoundTag r : rows(2)) if (r.getInt("id") == id) return r;
        return null;
    }

    private int vehicleAt(double mx, double my) {
        for (CompoundTag v : rows(1)) if (Math.abs(mx - sx(v.getInt("x") + 0.5)) <= 4 && Math.abs(my - sy(v.getInt("z") + 0.5)) <= 4) return v.getInt("id");
        return -1;
    }

    private CompoundTag vehicleRow(int id) {
        for (CompoundTag v : rows(1)) if (v.getInt("id") == id) return v;
        return null;
    }

    private CompoundTag routine() {
        for (CompoundTag r : rows(3)) if (selRid != 0 ? r.getInt("rid") == selRid : selVeh >= 0 && r.getInt("veh") == selVeh) return r;
        return null;
    }

    private void select(int route, long box, int veh, int rid) { selRoute = route; selBox = box; selVeh = veh; selRid = rid; picking = false; multi = false; addStage = 0; }

    private static int stateColour(int st) {
        return switch (st) {
            case Logistics.ST_DONE -> 0xFF56C46A;
            case Logistics.ST_ON_WAY, Logistics.ST_NEXT -> 0xFF5AA8FF;
            case Logistics.ST_TOO_FAR -> 0xFFFF5050;
            case Logistics.ST_OFF -> 0xFF707880;
            case Logistics.ST_WAIT -> C_WAIT;
            case Logistics.ST_GONE, Logistics.ST_SRC_GONE -> 0xFFFF5050;
            default -> 0xFFF2B040;
        };
    }

    private static Component boxName(CompoundTag b) {
        if (b == null) return Component.translatable("gui.wmd.planmap.nearest");
        String n = b.getString("n");
        ResourceLocation id = ResourceLocation.tryParse(n.contains(":") ? n : "wmd:" + n);
        return id == null ? Component.literal(n) : BuiltInRegistries.BLOCK.get(id).getName();
    }

    private static int vehicleColour(CompoundTag v) {
        if (v.getInt("k") != 0) return C_STUCK;
        return switch (v.getInt("vk")) { case MissileRobotEntity.KIND_XL -> C_XL; case MissileRobotEntity.KIND_RUNNER -> C_RUNNER; default -> C_ROBOT; };
    }

    private void send(int action, long arg, int x, int z, List<Long> pads) {
        PacketDistributor.sendToServer(new CcBPayload(pos, action, arg, x, z, "", pads));
    }

    private void set(int field, int delta) {
        if (field == 1 && hasShiftDown()) delta *= 16;
        if (selRoute >= 0) send(CcBPayload.PLAN_SET, selRoute, field, delta, List.of());
    }

    private List<Integer> candidates(CompoundTag r, CompoundTag dst) {
        List<Integer> l = new ArrayList<>();
        if (dst.getBoolean("any")) {
            CompoundTag s = r.getLong("src") == 0 ? null : boxByPos(r.getLong("src"));
            if (s != null) for (int id : s.getIntArray("has")) if (!l.contains(id)) l.add(id);
            int main = BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.getString("f"))));
            if (!r.getBoolean("any") && !l.contains(main)) l.add(0, main);
            for (int id : r.getIntArray("also")) if (!l.contains(id)) l.add(id);
        } else for (int id : dst.getIntArray("opt")) l.add(id);
        return l;
    }

    private static boolean names(CompoundTag r, int id) {
        if (r.getBoolean("any")) return false;
        if (BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.getString("f")))) == id) return true;
        for (int a : r.getIntArray("also")) if (a == id) return true;
        return false;
    }

    private void rt(CompoundTag r, int op, int z) { send(CcBPayload.PLAN_RT, r.getInt("rid"), op, z, List.of()); }

    private static Component filterText(CompoundTag r) {
        String w = r.getString("fw");
        net.minecraft.network.chat.MutableComponent c = w.isEmpty() ? Component.translatable(P + "any_wh") : Component.translatable("warhead.wmd." + w);
        if (r.getBoolean("fu")) c = c.append(", ").append(Component.translatable(P + "f_uplink"));
        if (r.getBoolean("fr")) c = c.append(", ").append(Component.translatable(P + "f_refined"));
        return c;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (Btn b : new ArrayList<>(btns)) if (WmdUi.over(mx, my, b.x(), b.y(), b.w(), b.h())) { WmdUi.click(); b.run().run(); return true; }
        if (onMap(mx, my)) {
            if (button == 1) { panning = true; return true; }
            if (button == 0) {
                CompoundTag b = boxAt(mx, my);
                if (addStage == 1 || addStage == 2) {
                    if (b != null && addStage == 1) { stepSrc = b.getLong("p"); addStage = 2; WmdUi.click(); }
                    else if (b != null) { stepDst = b.getLong("p"); addStage = 3; WmdUi.click(); }
                    return true;
                }
                int v = vehicleAt(mx, my);
                if (v >= 0) { select(-1, 0, v, 0); return true; }
                if (b != null && (b.getInt("r") & 1) != 0) { dragFrom = b; dragMx = mx; dragMy = my; return true; }
                int r = routeAt(mx, my);
                if (r >= 0) { select(r, 0, -1, 0); return true; }
                if (b != null) { select(-1, b.getLong("p"), -1, 0); return true; }
                select(-1, 0, -1, 0);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning && button == 1) { vx -= dx / scale(); vz -= dy / scale(); clampView(); return true; }
        if (dragFrom != null) { dragMx = mx; dragMy = my; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1) panning = false;
        if (button == 0 && dragFrom != null) {
            CompoundTag from = dragFrom;
            dragFrom = null;
            CompoundTag to = onMap(mx, my) ? boxAt(mx, my) : null;
            if (to == from) {
                int r = routeAt(mx, my);
                if (r >= 0) select(r, 0, -1, 0); else select(-1, from.getLong("p"), -1, 0);
            } else if (to != null && (to.getInt("r") & 2) != 0) {
                WmdUi.confirm();
                send(CcBPayload.PLAN_ADD, from.getLong("p"), 0, 0, List.of(to.getLong("p")));
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sxs, double sys) {
        if (onMap(mx, my)) {
            double wxb = wx(mx), wzb = wz(my);
            zoom = Mth.clamp(zoom * (sys > 0 ? 1.25 : 0.8), 1.0, 8.0);
            vx = wxb - (mx - mapX - mapW / 2.0) / scale();
            vz = wzb - (my - mapY - mapW / 2.0) / scale();
            clampView();
            return true;
        }
        int px = panelX();
        if (WmdUi.over(mx, my, px, mapY, SP, mapW)) { listScroll = Math.max(0, listScroll - (int) Math.signum(sys)); return true; }
        return super.mouseScrolled(mx, my, sxs, sys);
    }

    private void clampView() {
        double half = size / 2.0 / zoom;
        vx = Mth.clamp(vx, cx - size / 2.0 + half, cx + size / 2.0 - half);
        vz = Mth.clamp(vz, cz - size / 2.0 + half, cz + size / 2.0 - half);
    }

    private static void line(GuiGraphics g, double x0, double y0, double x1, double y1, int col, int w, int dash) {
        double dx = x1 - x0, dy = y1 - y0;
        int n = (int) Math.max(Math.abs(dx), Math.abs(dy));
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && (i / dash) % 2 == 1) continue;
            double t = n == 0 ? 0 : i / (double) n;
            int x = (int) Math.round(x0 + dx * t), y = (int) Math.round(y0 + dy * t);
            g.fill(x - w / 2, y - w / 2, x - w / 2 + w, y - w / 2 + w, col);
        }
    }

    private static void arrow(GuiGraphics g, double x0, double y0, double x1, double y1, int col, int w) {
        line(g, x0, y0, x1, y1, col, w, 0);
        double a = Math.atan2(y1 - y0, x1 - x0), l = 7;
        line(g, x1, y1, x1 - l * Math.cos(a - 0.45), y1 - l * Math.sin(a - 0.45), col, w, 0);
        line(g, x1, y1, x1 - l * Math.cos(a + 0.45), y1 - l * Math.sin(a + 0.45), col, w, 0);
    }

    private void circle(GuiGraphics g, double wcx, double wcz, double r, int col) {
        int n = 96;
        for (int i = 0; i < n; i += 2) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            line(g, sx(wcx + Math.cos(a0) * r), sy(wcz + Math.sin(a0) * r), sx(wcx + Math.cos(a1) * r), sy(wcz + Math.sin(a1) * r), col, 1, 0);
        }
    }

    private void drawText(GuiGraphics g, Component c, int x, int y, int maxW, int col) { WmdUi.drawFit(g, font, c, x, y, maxW, col); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0xF00A0E13);
        btns.clear();
        CcPlannerBlockEntity pl = be();

        WmdUi.panel(g, M, 4, width - 2 * M, TOP - 6);
        drawText(g, title, M + 8, 10, 180, WmdUi.TEXT);
        for (int i = 0; i < 3; i++) {
            final int li = i;
            int tx = M + 190 + i * 64;
            button(g, mx, my, tx, 7, 60, 13, Component.translatable(P + "layer." + i), () -> { layer = li; selBox = 0; });
            if (layer == i) g.fill(tx + 2, 19, tx + 58, 20, WmdUi.BLUE_LIGHT);
        }
        drawText(g, Component.translatable(P + "help"), M + 390, 10, width - 2 * M - 390 - 110, WmdUi.TEXT_MUTED);
        button(g, mx, my, width - M - 100, 7, 92, 13, Component.translatable(P + "refresh"), () -> sample());

        WmdUi.well(g, mapX, mapY, mapW, mapW);
        g.enableScissor(mapX, mapY, mapX + mapW, mapY + mapW);
        if (tex != null) {
            double s = scale();
            double u0 = (vx - mapW / 2.0 / s) - (cx - size / 2.0), v0 = (vz - mapW / 2.0 / s) - (cz - size / 2.0);
            int vis = (int) Math.round(mapW / s);
            g.blit(tex, mapX, mapY, mapW, mapW, (float) u0, (float) v0, Math.max(1, vis), Math.max(1, vis), size, size);
        }
        List<CompoundTag> boxes = rows(0), vehicles = rows(1), routes = rows(2);
        CompoundTag hoverBox = onMap(mx, my) ? boxAt(mx, my) : null;

        CompoundTag zoneOf = dragFrom != null ? dragFrom : hoverBox != null && (hoverBox.getInt("r") & 1) != 0 ? hoverBox : selBox != 0 ? boxByPos(selBox) : null;
        if (zoneOf != null && (zoneOf.getInt("r") & 1) != 0) circle(g, zoneOf.getInt("x") + 0.5, zoneOf.getInt("z") + 0.5, Balance.ROBOT_ZONE, 0x803A7BD5);

        for (CompoundTag b : boxes) {
            if (!visible(b)) continue;
            int[] r = rect(b);
            int role = b.getInt("r"), col = role == 3 ? C_BOTH : role == 1 ? C_SRC : C_DST;
            boolean hi = b == hoverBox || b.getLong("p") == selBox;
            g.fill(r[0], r[1], r[2], r[3], (col & 0x00FFFFFF) | (hi ? 0x90000000 : 0x55000000));
            g.renderOutline(r[0], r[1], r[2] - r[0], r[3] - r[1], hi ? 0xFFFFFFFF : col);
        }

        g.fill((int) sx(cx) - 2, (int) sy(cz) - 2, (int) sx(cx) + 3, (int) sy(cz) + 3, 0xFFFFFFFF);

        for (CompoundTag r : routes) {
            double[] s = segment(r);
            if (s == null) continue;
            int col = stateColour(r.getInt("st"));
            boolean sel = r.getInt("id") == selRoute;
            if (r.getLong("src") == 0) {
                line(g, s[0], s[1], s[2], s[3], col, sel ? 3 : 2, 2);
                g.drawString(font, "A", (int) s[0] - 6, (int) s[1] - 8, col, true);
            } else if (r.getInt("st") == Logistics.ST_WAIT) line(g, s[0], s[1], s[2], s[3], col, sel ? 3 : 2, 3);
            else arrow(g, s[0], s[1], s[2], s[3], col, sel ? 3 : 2);
        }

        CompoundTag selRt = routine();
        for (CompoundTag rt : rows(3)) {
            int col = RT_COLS[Math.floorMod(rt.getInt("rid"), RT_COLS.length)];
            boolean sel = rt == selRt || selRt != null && selRt.getInt("rid") == rt.getInt("rid");
            if (!sel) col = (col & 0x00FFFFFF) | 0x90000000;
            net.minecraft.nbt.ListTag steps = rt.getList("steps", Tag.TAG_COMPOUND);
            for (int i = 0; i < steps.size(); i++) {
                CompoundTag st = steps.getCompound(i);
                CompoundTag a = boxByPos(st.getLong("src")), b = boxByPos(st.getLong("dst"));
                if (a == null || b == null) continue;
                double[] p0 = centre(a), p1 = centre(b);
                double len = Math.max(1, Math.hypot(p1[0] - p0[0], p1[1] - p0[1])), ox = -(p1[1] - p0[1]) / len * 4, oy = (p1[0] - p0[0]) / len * 4;
                arrow(g, p0[0] + ox, p0[1] + oy, p1[0] + ox, p1[1] + oy, col, sel ? 2 : 1);
                int nx = (int) ((p0[0] + p1[0]) / 2 + ox), ny = (int) ((p0[1] + p1[1]) / 2 + oy);
                String num = Integer.toString(i + 1);
                g.fill(nx - 5, ny - 5, nx + 5, ny + 5, 0xE0000000);
                g.renderOutline(nx - 5, ny - 5, 10, 10, col);
                g.drawString(font, num, nx - font.width(num) / 2, ny - 4, col, false);
            }
        }

        if (addStage >= 2 && boxByPos(stepSrc) != null) { int[] r = rect(boxByPos(stepSrc)); g.renderOutline(r[0] - 2, r[1] - 2, r[2] - r[0] + 4, r[3] - r[1] + 4, 0xFFFFFFFF); }

        for (CompoundTag v : vehicles) {
            int[] line = v.getIntArray("line");
            double px = sx(v.getInt("x") + 0.5), py = sy(v.getInt("z") + 0.5);
            int col = vehicleColour(v);
            for (int i = 0; i + 1 < line.length; i += 2) {
                double qx = sx(line[i] + 0.5), qy = sy(line[i + 1] + 0.5);
                line(g, px, py, qx, qy, (col & 0x00FFFFFF) | 0xA0000000, 1, 2);
                px = qx; py = qy;
            }
            int x = (int) sx(v.getInt("x") + 0.5), y = (int) sy(v.getInt("z") + 0.5);
            g.fill(x - 3, y - 3, x + 3, y + 3, 0xFF000000);
            g.fill(x - 2, y - 2, x + 2, y + 2, col);
            if (v.getInt("id") == selVeh) g.renderOutline(x - 5, y - 5, 10, 10, 0xFFFFFFFF);
            if (v.getBoolean("up")) g.renderOutline(x - 4, y - 4, 8, 8, 0xFF7FE8F0);
            g.drawString(font, v.getString("lb"), x + 4, y - 4, col, true);
        }

        if (dragFrom != null) {
            double[] a = centre(dragFrom);
            CompoundTag to = onMap(dragMx, dragMy) ? boxAt(dragMx, dragMy) : null;
            boolean ok = to != null && to != dragFrom && (to.getInt("r") & 2) != 0;
            arrow(g, a[0], a[1], dragMx, dragMy, ok ? 0xFF8FE3A8 : 0xFFE8EEF4, 2);
        }
        g.disableScissor();

        int px = panelX(), py = mapY;
        WmdUi.panel(g, px, py, SP, mapW);
        if (pl == null || !pl.isOnline()) drawText(g, Component.translatable("gui.wmd.cc.offline"), px + 8, py + 8, SP - 16, WmdUi.BAD);
        else if (selVeh >= 0 || selRid != 0) routinePanel(g, mx, my, px, py, routine());
        else if (selRoute >= 0 && route(selRoute) != null) routePanel(g, mx, my, px, py, route(selRoute));
        else if (selBox != 0 && boxByPos(selBox) != null) boxPanel(g, mx, my, px, py, boxByPos(selBox));
        else listPanel(g, mx, my, px, py, routes, vehicles);
        super.render(g, mx, my, pt);

        if (dragFrom == null && onMap(mx, my)) {
            for (CompoundTag v : vehicles) {
                int x = (int) sx(v.getInt("x") + 0.5), y = (int) sy(v.getInt("z") + 0.5);
                if (Math.abs(mx - x) <= 3 && Math.abs(my - y) <= 3) {
                    List<Component> tip = new ArrayList<>();
                    int vk = v.getInt("vk");
                    tip.add(Component.translatable(P + "vehicle", v.getString("lb"), Component.translatable(vk == MissileRobotEntity.KIND_XL ? "entity.wmd.xl_transporter"
                            : vk == MissileRobotEntity.KIND_RUNNER ? "entity.wmd.parts_runner" : "entity.wmd.missile_robot"), v.getInt("b")));
                    tip.add(Component.translatable(P + "vstate." + Mth.clamp(v.getInt("s"), 0, 6)));
                    if (v.getBoolean("up")) tip.add(Component.translatable(P + "upgraded", Balance.RUNNER_UP_STACKS).withStyle(net.minecraft.ChatFormatting.AQUA));
                    if (v.getInt("k") != 0) tip.add(Component.translatable("message.wmd.robot.stuck." + v.getInt("k")).withStyle(net.minecraft.ChatFormatting.RED));
                    g.renderComponentTooltip(font, tip, mx, my);
                    return;
                }
            }
            if (hoverBox != null) {
                List<Component> tip = new ArrayList<>();
                tip.add(boxName(hoverBox));
                tip.add(Component.literal(hoverBox.getInt("x") + ", " + hoverBox.getInt("z")).withStyle(net.minecraft.ChatFormatting.GRAY));
                tip.add(Component.translatable(P + "role." + hoverBox.getInt("r")).withStyle(net.minecraft.ChatFormatting.GRAY));
                g.renderComponentTooltip(font, tip, mx, my);
                return;
            }
            CompoundTag ra = route(routeAt(mx, my));
            if (ra != null) {
                List<Component> tip = new ArrayList<>();
                CompoundTag s = ra.getLong("src") == 0 ? null : boxByPos(ra.getLong("src")), d = boxByPos(ra.getLong("dst"));
                tip.add(Component.translatable(P + "row", boxName(s), d == null ? Component.translatable(P + "off_map") : boxName(d),
                        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ra.getString("f")))).getHoverName()));
                tip.add(Component.translatable("gui.wmd.planner.st." + ra.getInt("st")).withStyle(net.minecraft.ChatFormatting.GRAY));
                if (ra.getInt("cond") != 0) tip.add(Component.translatable(P + "tip_when", Component.translatable(P + "cond." + ra.getInt("cond"))).withStyle(net.minecraft.ChatFormatting.GRAY));
                if (ra.getBoolean("flt") && (!ra.getString("fw").isEmpty() || ra.getBoolean("fu") || ra.getBoolean("fr")))
                    tip.add(Component.translatable(P + "tip_filter", filterText(ra)).withStyle(net.minecraft.ChatFormatting.GRAY));
                g.renderComponentTooltip(font, tip, mx, my);
            }
        }
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component text, Runnable run) {
        boolean hov = WmdUi.over(mx, my, x, y, w, h);
        WmdUi.button(g, font, x, y, w, h, text, WmdUi.B_NORMAL, hov);
        btns.add(new Btn(x, y, w, h, run));
    }

    private void field(GuiGraphics g, int mx, int my, int x, int y, Component label, Component value, int f, boolean plusMinus) {
        field(g, mx, my, x, y, label, value, plusMinus, d -> set(f, d));
    }

    private void field(GuiGraphics g, int mx, int my, int x, int y, Component label, Component value, boolean plusMinus, java.util.function.IntConsumer change) {
        drawText(g, label, x, y + 3, 52, WmdUi.TEXT_MUTED);
        button(g, mx, my, x + 54, y, 14, 13, Component.literal(plusMinus ? "-" : "<"), () -> change.accept(-1));
        drawText(g, value, x + 72, y + 3, SP - 16 - 72 - 18, WmdUi.TEXT);
        button(g, mx, my, x + SP - 16 - 14, y, 14, 13, Component.literal(plusMinus ? "+" : ">"), () -> change.accept(1));
    }

    private void routePanel(GuiGraphics g, int mx, int my, int px, int py, CompoundTag r) {
        int x = px + 8, y = py + 8;
        drawText(g, Component.translatable(P + "route", r.getInt("id")), x, y, SP - 16, WmdUi.TEXT);
        y += 14;
        CompoundTag s = r.getLong("src") == 0 ? null : boxByPos(r.getLong("src")), d = boxByPos(r.getLong("dst"));
        drawText(g, Component.translatable(P + "from", boxName(s)), x, y, SP - 16, 0xFF8FC8FF);
        y += 11;
        drawText(g, Component.translatable(P + "to", d == null ? Component.translatable(P + "off_map") : boxName(d)), x, y, SP - 16, 0xFFF2C08A);
        y += 16;
        ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.getString("f"))));
        if (picking && d != null) { picker(g, mx, my, x, y, r, d); return; }
        Component itemName = r.getBoolean("any") ? Component.translatable(P + "any_item")
                : r.getIntArray("also").length > 0 ? Component.translatable(P + "items_n", item.getHoverName(), r.getIntArray("also").length) : item.getHoverName();
        field(g, mx, my, x, y, Component.translatable(P + "item"), itemName, 0, false);
        btns.add(new Btn(x + 70, y, SP - 16 - 70 - 16, 13, () -> { picking = true; multi = false; }));
        y += 16;
        if (r.getBoolean("dep")) { field(g, mx, my, x, y, Component.translatable(P + "keep"), Component.literal(Integer.toString(r.getInt("keep"))), 1, true); y += 16; }
        if (r.getBoolean("rn") || r.getBoolean("chest")) {
            int tw = (SP - 16 - 8) / 3;
            button(g, mx, my, x, y, tw, 13, Component.translatable(P + "items_btn"), () -> { picking = true; multi = true; });
            if (r.getLong("src") != 0) button(g, mx, my, x + tw + 4, y, tw, 13, Component.translatable(P + (r.getBoolean("any") ? "any_on" : "any_off")), () -> set(12, 0));
            button(g, mx, my, x + 2 * (tw + 4), y, tw, 13, Component.translatable(P + (r.getInt("mode") == PlannerRoute.MODE_ALL ? "mode_all" : "mode_keep")), () -> set(13, 0));
            y += 16;
            if (r.getBoolean("bulk") && r.getInt("mode") != PlannerRoute.MODE_ALL) {
                field(g, mx, my, x, y, Component.translatable(P + "keep"), Component.translatable(P + "keep_each", r.getInt("keep")), 1, true);
                y += 16;
            }
            if (r.getBoolean("bulk")) { drawText(g, Component.translatable(P + "needs_up"), x, y, SP - 16, 0xFF7FE8F0); y += 12; }
        }
        String vl = r.getString("vl");
        field(g, mx, my, x, y, Component.translatable(P + "vehicle_f"), vl.isEmpty() ? Component.translatable(P + "any") : Component.literal(vl), 2, false);
        y += 16;
        field(g, mx, my, x, y, Component.translatable(P + "prio"), Component.literal(Integer.toString(r.getInt("prio"))), 3, true);
        y += 16;
        field(g, mx, my, x, y, Component.translatable(P + "when"), Component.translatable(P + "cond." + r.getInt("cond")), 7, false);
        y += 16;
        if (r.getBoolean("flt")) {
            String w = r.getString("fw");
            field(g, mx, my, x, y, Component.translatable(P + "wh"), w.isEmpty() ? Component.translatable(P + "any_wh") : Component.translatable("warhead.wmd." + w), 8, false);
            y += 16;
            int hw = (SP - 16 - 4) / 2;
            button(g, mx, my, x, y, hw, 13, Component.translatable(P + "uplink." + (r.getBoolean("fu") ? 1 : 0)), () -> set(9, 0));
            button(g, mx, my, x + hw + 4, y, hw, 13, Component.translatable(P + "refined." + (r.getBoolean("fr") ? 1 : 0)), () -> set(10, 0));
            y += 16;
        }
        y += 4;
        boolean on = r.getBoolean("on");
        button(g, mx, my, x, y, 76, 14, Component.translatable(on ? P + "on" : P + "off"), () -> set(4, 0));
        if (r.getLong("src") != 0) button(g, mx, my, x + 82, y, SP - 16 - 82, 14, Component.translatable(P + "use_nearest"), () -> set(5, 0));
        y += 20;
        int st = r.getInt("st");
        WmdUi.led(g, x, y + 1, st == Logistics.ST_DONE ? WmdUi.K_GOOD : st == Logistics.ST_ON_WAY || st == Logistics.ST_NEXT ? WmdUi.K_INFO : st == Logistics.ST_OFF ? WmdUi.K_IDLE : WmdUi.K_WARN, st == Logistics.ST_ON_WAY);
        drawText(g, Component.translatable("gui.wmd.planner.st." + st), x + 8, y, SP - 24, stateColour(st));
        y += 18;
        final int id = r.getInt("id");
        int dw = SP - 16 - 50;
        WmdUi.button(g, font, x, y, dw, 14, Component.translatable(P + "delete"), WmdUi.B_DANGER, WmdUi.over(mx, my, x, y, dw, 14));
        btns.add(new Btn(x, y, dw, 14, () -> { send(CcBPayload.PLAN_DEL, id, 0, 0, List.of()); selRoute = -1; }));
        button(g, mx, my, x + dw + 4, y, 46, 14, Component.translatable(P + "back"), () -> selRoute = -1);
    }

    private void routinePanel(GuiGraphics g, int mx, int my, int px, int py, CompoundTag r) {
        int x = px + 8, y = py + 8, w = SP - 16;
        CompoundTag v = vehicleRow(selVeh);
        String lb = v != null ? v.getString("lb") : r != null ? r.getString("vl") : "?";
        drawText(g, Component.translatable(P + "rt_title", lb), x, y, w, WmdUi.TEXT);
        y += 14;
        if (r == null) {
            for (net.minecraft.util.FormattedCharSequence l : font.split(Component.translatable(P + "rt_none"), w)) { g.drawString(font, l, x, y, WmdUi.TEXT_MUTED, false); y += 10; }
            y += 6;
            if (v != null) { final int id = selVeh; button(g, mx, my, x, y, w, 14, Component.translatable(P + "rt_make"), () -> send(CcBPayload.PLAN_RT, id, 0, 0, List.of())); y += 20; }
            button(g, mx, my, x, y, w, 14, Component.translatable(P + "back"), () -> select(-1, 0, -1, 0));
            return;
        }
        int st = r.getInt("st");
        boolean run = r.getBoolean("run");
        WmdUi.led(g, x, y + 1, st == PlannerRoutine.RS_ON ? WmdUi.K_INFO : st == PlannerRoutine.RS_STOPPED || st == PlannerRoutine.RS_DONE ? WmdUi.K_IDLE : WmdUi.K_WARN, st == PlannerRoutine.RS_ON);
        drawText(g, Component.translatable(P + "rst." + Mth.clamp(st, 0, 6)), x + 8, y, w - 8, WmdUi.TEXT_MUTED);
        y += 14;
        field(g, mx, my, x, y, Component.translatable(P + "when"), Component.translatable(P + "cond." + r.getInt("cond")), false, d -> rt(r, 8, d));
        y += 16;
        int hw = (w - 4) / 2;
        button(g, mx, my, x, y, hw, 14, Component.translatable(P + (run ? "rt_stop" : "rt_start")), () -> rt(r, 7, 0));
        button(g, mx, my, x + hw + 4, y, hw, 14, Component.translatable(P + (r.getBoolean("loop") ? "rt_loop" : "rt_once")), () -> rt(r, 6, 0));
        y += 20;
        net.minecraft.nbt.ListTag steps = r.getList("steps", Tag.TAG_COMPOUND);
        drawText(g, Component.translatable(P + "rt_steps", steps.size(), PlannerRoutine.MAX_STEPS), x, y, w, WmdUi.TEXT);
        y += 12;
        if (addStage > 0) {
            if (addStage < 3) drawText(g, Component.translatable(P + (addStage == 1 ? "rt_pick_src" : "rt_pick_dst")), x, y, w, 0xFF8FC8FF);
            else stepPicker(g, mx, my, x, y + 12, r);
            if (addStage < 3) button(g, mx, my, x, y + 14, w, 14, Component.translatable(P + "rt_cancel"), () -> addStage = 0);
            return;
        }
        int col = RT_COLS[Math.floorMod(r.getInt("rid"), RT_COLS.length)], at = r.getInt("at");
        int bottom = py + mapW - 8 - 3 * 18, fit = Math.max(0, (bottom - y) / 22);
        for (int i = 0; i < steps.size() && i < fit; i++) {
            CompoundTag s = steps.getCompound(i);
            int ly = y + i * 22;
            g.fill(x - 2, ly - 1, x + w + 2, ly + 20, run && i == at ? 0xFF1E2A36 : 0xFF141A21);
            g.drawString(font, Integer.toString(i + 1), x, ly + 6, col, false);
            ItemStack it = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.getString("f"))));
            g.renderItem(it, x + 9, ly + 1);
            int tw = w - 27 - 26;
            drawText(g, Component.translatable(P + "rt_row", boxName(boxByPos(s.getLong("src"))), boxName(boxByPos(s.getLong("dst")))), x + 27, ly, tw, WmdUi.TEXT);
            Component amt = Component.translatable(P + "amt." + Mth.clamp(s.getInt("a"), 0, 2));
            boolean ah = WmdUi.over(mx, my, x + 27, ly + 10, tw, 10);
            drawText(g, amt, x + 27, ly + 10, tw, ah ? WmdUi.TEXT : WmdUi.TEXT_MUTED);
            final int si = i;
            btns.add(new Btn(x + 27, ly + 10, tw, 10, () -> rt(r, 5, si)));
            button(g, mx, my, x + w - 24, ly, 11, 10, Component.literal("^"), () -> rt(r, 2, si));
            button(g, mx, my, x + w - 24, ly + 10, 11, 10, Component.literal("v"), () -> rt(r, 3, si));
            button(g, mx, my, x + w - 11, ly, 11, 20, Component.literal("x"), () -> rt(r, 4, si));
        }
        if (steps.size() > fit) drawText(g, Component.translatable(P + "rt_more", steps.size() - fit), x, y + fit * 22, w, WmdUi.TEXT_MUTED);
        y = bottom;
        if (steps.size() < PlannerRoutine.MAX_STEPS) button(g, mx, my, x, y, w, 14, Component.translatable(P + "rt_add"), () -> { addStage = 1; stepSrc = 0; stepDst = 0; });
        y += 18;
        WmdUi.button(g, font, x, y, w, 14, Component.translatable(P + "rt_delete"), WmdUi.B_DANGER, WmdUi.over(mx, my, x, y, w, 14));
        btns.add(new Btn(x, y, w, 14, () -> { rt(r, 9, 0); selRid = 0; }));
        y += 18;
        button(g, mx, my, x, y, w, 14, Component.translatable(P + "back"), () -> select(-1, 0, -1, 0));
    }

    private void stepPicker(GuiGraphics g, int mx, int my, int x, int y, CompoundTag r) {
        CompoundTag dst = boxByPos(stepDst);
        if (dst == null) { addStage = 0; return; }
        drawText(g, Component.translatable(P + "rt_pick_item"), x, y - 12, SP - 16, WmdUi.TEXT);
        int vk = r.getInt("vk");
        List<Integer> ids = new ArrayList<>();
        CompoundTag srcBox = boxByPos(stepSrc);
        int[] offer = dst.getBoolean("any") ? (srcBox == null ? new int[0] : srcBox.getIntArray("has")) : dst.getIntArray("opt");
        for (int id : offer) {
            boolean ok = vk < 0;
            for (int k : Logistics.kindsFor(BuiltInRegistries.ITEM.byId(id))) ok |= k == vk;
            if (ok) ids.add(id);
        }
        ItemStack hover = ItemStack.EMPTY;
        for (int i = 0; i < ids.size(); i++) {
            int cx = x + (i % 8) * 20, cy = y + (i / 8) * 20;
            ItemStack it = new ItemStack(BuiltInRegistries.ITEM.byId(ids.get(i)));
            WmdUi.slot(g, cx, cy, 0);
            g.renderItem(it, cx + 1, cy + 1);
            if (WmdUi.over(mx, my, cx, cy, 18, 18)) hover = it;
            final int id = ids.get(i);
            final long sp = stepSrc, dp = stepDst;
            btns.add(new Btn(cx, cy, 18, 18, () -> { send(CcBPayload.PLAN_RT, r.getInt("rid"), 1, id, List.of(sp, dp)); addStage = 0; WmdUi.confirm(); }));
        }
        if (ids.isEmpty()) drawText(g, Component.translatable(P + "rt_cant"), x, y, SP - 16, WmdUi.TEXT_MUTED);
        y += Math.max(1, (ids.size() + 7) / 8) * 20 + 6;
        button(g, mx, my, x, y, SP - 16, 14, Component.translatable(P + "rt_cancel"), () -> addStage = 0);
        if (!hover.isEmpty()) drawText(g, hover.getHoverName(), x, y + 20, SP - 16, WmdUi.TEXT);
    }

    private void picker(GuiGraphics g, int mx, int my, int x, int y, CompoundTag r, CompoundTag dst) {
        drawText(g, Component.translatable(multi ? P + "pick_multi" : P + "pick", Balance.RUNNER_UP_ITEMS), x, y + 2, SP - 16, WmdUi.TEXT);
        y += 14;
        List<Integer> opt = candidates(r, dst);
        String cur = r.getString("f");
        ItemStack hover = ItemStack.EMPTY;
        int rows = Math.max(1, (mapY + mapW - 8 - 40 - y) / 20);
        for (int i = 0; i < opt.size() && i < rows * 8; i++) {
            int cx = x + (i % 8) * 20, cy = y + (i / 8) * 20;
            ItemStack it = new ItemStack(BuiltInRegistries.ITEM.byId(opt.get(i)));
            boolean sel = multi ? names(r, opt.get(i)) : BuiltInRegistries.ITEM.getKey(it.getItem()).toString().equals(cur), hov = WmdUi.over(mx, my, cx, cy, 18, 18);
            WmdUi.slot(g, cx, cy, sel ? WmdUi.BLUE : 0);
            g.renderItem(it, cx + 1, cy + 1);
            if (hov) hover = it;
            final int id = opt.get(i);
            btns.add(new Btn(cx, cy, 18, 18, multi ? () -> set(11, id) : () -> { set(6, id); picking = false; }));
        }
        if (opt.isEmpty()) drawText(g, Component.translatable(P + "pick_none"), x, y, SP - 16, WmdUi.TEXT_MUTED);
        y += Math.max(1, Math.min(rows, (opt.size() + 7) / 8)) * 20 + 6;
        button(g, mx, my, x, y, SP - 16, 14, Component.translatable(multi ? P + "done" : P + "back"), () -> { picking = false; multi = false; });
        if (!hover.isEmpty()) drawText(g, hover.getHoverName(), x, y + 20, SP - 16, WmdUi.TEXT);
    }

    private void boxPanel(GuiGraphics g, int mx, int my, int px, int py, CompoundTag b) {
        int x = px + 8, y = py + 8;
        drawText(g, boxName(b), x, y, SP - 16, WmdUi.TEXT);
        y += 12;
        drawText(g, Component.literal(b.getInt("x") + ", " + b.getInt("z")), x, y, SP - 16, WmdUi.TEXT_MUTED);
        y += 12;
        drawText(g, Component.translatable(P + "role." + b.getInt("r")), x, y, SP - 16, WmdUi.TEXT_MUTED);
        y += 18;
        if ((b.getInt("r") & 2) != 0) {
            final long p = b.getLong("p");
            button(g, mx, my, x, y, SP - 16, 14, Component.translatable(P + "from_nearest"), () -> send(CcBPayload.PLAN_ADD, 0L, 0, 0, List.of(p)));
            y += 20;
        }
        if ((b.getInt("r") & 1) != 0) { drawText(g, Component.translatable(P + "drag_hint"), x, y, SP - 16, 0xFF8FC8FF); y += 14; }
        button(g, mx, my, x, y, SP - 16, 14, Component.translatable(P + "back"), () -> selBox = 0);
        y += 22;

        List<CompoundTag[]> pairs = new ArrayList<>();
        for (CompoundTag o : rows(0)) {
            if (o.getLong("p") == b.getLong("p") || (layer != 0 && (o.getInt("cat") & layer) == 0)) continue;
            if ((b.getInt("r") & 2) != 0 && (o.getInt("r") & 1) != 0 && common(o, b) >= 0) pairs.add(new CompoundTag[] { o, b });
            else if ((b.getInt("r") & 1) != 0 && (o.getInt("r") & 2) != 0 && common(b, o) >= 0) pairs.add(new CompoundTag[] { b, o });
        }
        pairs.sort(java.util.Comparator.comparingDouble(pr -> { CompoundTag o = pr[0] == b ? pr[1] : pr[0]; return Math.hypot(o.getInt("x") - b.getInt("x"), o.getInt("z") - b.getInt("z")); }));
        drawText(g, Component.translatable(P + ((b.getInt("r") & 2) != 0 ? "avail_sources" : "avail_targets"), pairs.size()), x, y, SP - 16, WmdUi.TEXT);
        y += 13;
        if (pairs.isEmpty()) { drawText(g, Component.translatable(P + "avail_none"), x, y, SP - 16, WmdUi.TEXT_MUTED); return; }
        int rowsFit = Math.max(0, (py + mapW - 8 - y) / 22);
        for (int i = 0; i < rowsFit && i < pairs.size(); i++) {
            CompoundTag src = pairs.get(i)[0], dst = pairs.get(i)[1], other = src == b ? dst : src;
            int ly = y + i * 22;
            boolean hov = WmdUi.over(mx, my, x - 2, ly - 1, SP - 12, 21);
            g.fill(x - 2, ly - 1, x + SP - 14, ly + 20, hov ? 0xFF1E2A36 : 0xFF141A21);
            int id = common(src, dst);
            ItemStack it = new ItemStack(BuiltInRegistries.ITEM.byId(id));
            g.pose().pushPose();
            g.pose().translate(x, ly + 2, 0);
            g.pose().scale(0.95F, 0.95F, 1.0F);
            g.renderItem(it, 0, 0);
            g.pose().popPose();
            drawText(g, boxName(other), x + 20, ly + 1, SP - 40, WmdUi.TEXT);
            drawText(g, Component.translatable(P + "avail_row", other.getInt("x"), other.getInt("z"), it.getHoverName()), x + 20, ly + 10, SP - 40, WmdUi.TEXT_MUTED);
            final long sp = src.getLong("p"), dp = dst.getLong("p");
            btns.add(new Btn(x - 2, ly - 1, SP - 12, 21, () -> { WmdUi.confirm(); send(CcBPayload.PLAN_ADD, sp, 0, 0, List.of(dp)); }));
        }
    }

    private void listPanel(GuiGraphics g, int mx, int my, int px, int py, List<CompoundTag> routes, List<CompoundTag> vehicles) {
        int x = px + 8, y = py + 8;
        drawText(g, Component.translatable(P + "legend"), x, y, SP - 16, WmdUi.TEXT);
        y += 13;
        int[][] lg = { { C_SRC, 0 }, { C_DST, 1 }, { C_BOTH, 2 }, { C_ROBOT, 3 }, { C_XL, 4 }, { C_RUNNER, 6 }, { C_STUCK, 5 } };
        for (int[] l : lg) { g.fill(x, y + 1, x + 7, y + 8, l[0]); drawText(g, Component.translatable(P + "lg." + l[1]), x + 11, y, SP - 27, WmdUi.TEXT_MUTED); y += 10; }
        y += 6;

        drawText(g, Component.translatable(P + "rt_head"), x, y, SP - 16, WmdUi.TEXT);
        y += 13;
        y += Math.min(routineList(g, mx, my, x, y), 6) * 12 + 6;
        drawText(g, Component.translatable(P + "routes", routes.size(), vehicles.size()), x, y, SP - 16, WmdUi.TEXT);
        y += 13;
        int rows = Math.max(0, (py + mapW - 8 - y) / 12);
        listScroll = Math.min(listScroll, Math.max(0, routes.size() - rows));
        for (int i = 0; i < rows && i + listScroll < routes.size(); i++) {
            CompoundTag r = routes.get(i + listScroll);
            int ly = y + i * 12;
            boolean hov = WmdUi.over(mx, my, x - 2, ly - 1, SP - 12, 12);
            if (hov) g.fill(x - 2, ly - 1, x + SP - 14, ly + 11, 0xFF1E2A36);
            g.fill(x, ly + 2, x + 5, ly + 7, stateColour(r.getInt("st")));
            CompoundTag s = r.getLong("src") == 0 ? null : boxByPos(r.getLong("src")), d = boxByPos(r.getLong("dst"));
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.getString("f"))));
            drawText(g, Component.translatable(P + "row", boxName(s), d == null ? Component.translatable(P + "off_map") : boxName(d), item.getHoverName()), x + 8, ly, SP - 24, WmdUi.TEXT);
            final int id = r.getInt("id");
            btns.add(new Btn(x - 2, ly - 1, SP - 12, 12, () -> select(id, 0, -1, 0)));
        }
        if (routes.isEmpty()) drawText(g, Component.translatable(P + "empty"), x, y, SP - 16, WmdUi.TEXT_MUTED);
    }

    private int routineList(GuiGraphics g, int mx, int my, int x, int y) {
        List<CompoundTag> rts = rows(3);
        if (rts.isEmpty()) { drawText(g, Component.translatable(P + "rt_hint"), x, y, SP - 16, WmdUi.TEXT_MUTED); return 1; }
        for (int i = 0; i < rts.size() && i < 6; i++) {
            CompoundTag r = rts.get(i);
            int ly = y + i * 12;
            if (WmdUi.over(mx, my, x - 2, ly - 1, SP - 12, 12)) g.fill(x - 2, ly - 1, x + SP - 14, ly + 11, 0xFF1E2A36);
            g.fill(x, ly + 2, x + 5, ly + 7, RT_COLS[Math.floorMod(r.getInt("rid"), RT_COLS.length)]);
            drawText(g, Component.translatable(P + "rt_list", r.getString("vl"), r.getList("steps", Tag.TAG_COMPOUND).size(),
                    Component.translatable(P + "rst." + Mth.clamp(r.getInt("st"), 0, 6))), x + 8, ly, SP - 24, WmdUi.TEXT);
            final int rid = r.getInt("rid"), veh = r.getInt("veh");
            btns.add(new Btn(x - 2, ly - 1, SP - 12, 12, () -> select(-1, 0, veh, rid)));
        }
        return rts.size();
    }
}
