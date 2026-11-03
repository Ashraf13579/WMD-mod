package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class RadarScreen extends AbstractContainerScreen<RadarMenu> {

    private static final int TEXT = WmdUi.TEXT, MUTED = WmdUi.TEXT_MUTED, TEXT_GOOD = WmdUi.GOOD, TEXT_BAD = WmdUi.BAD;
    private static final int SCOPE_BG = 0xFF14211A, RING = 0xFF3F7A55, ARC = 0xFF1F4A31, BEAM = 0xFF2E7A4A, NEEDLE = 0xFF7CFFA8, MARK = 0xFFFFB030;
    private static final int BLUE = 0x4DA3FF, RED = 0xFF4040, VIOLET = 0xB070FF, DRONE = 0xFFB030;
    private static final int BOMB = 0xFF7A30;
    private static final int SHIP_NEUTRAL = 0xD8D8D8;

    private static final int[] RANGES = {1000, 3000};
    private static final int ECHO_TICKS = 200;

    private int tab;
    private int zoom = 1;
    private boolean synced;
    private EditBox bearingBox, freqBox;
    private Button modeButton, turnButton, setButton;
    private final List<Button> compassButtons = new ArrayList<>();

    private record Seen(double x, double z, int tier, long tick, boolean own, boolean sat) {}
    private final Map<Long, Seen> seen = new HashMap<>();

    public RadarScreen(RadarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 184;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 22, "icon_lock", Component.translatable("gui.wmd.kit.tab.lock"),
                b -> minecraft.setScreen(new SecurityScreen(this, menu.getPos()))));
        addRenderableWidget(new WmdUi.TabButton(leftPos - 23, topPos + 46, "icon_freq", Component.translatable("gui.wmd.kit.tab.freq"),
                b -> minecraft.setScreen(new FrequencyScreen(this, menu::getFrequency, f -> sendConfig(menu.getMode(), f)))));
        compassButtons.clear();
        Button t0 = new WmdUi.KitButton(leftPos + 8, topPos + 16, 56, 16, Component.translatable("screen.wmd.radar.tab.control"), b -> { tab = 0; rebuildWidgets(); });
        Button t1 = new WmdUi.KitButton(leftPos + 66, topPos + 16, 56, 16, Component.translatable("screen.wmd.radar.tab.flights"), b -> { tab = 1; rebuildWidgets(); });
        t0.active = tab != 0;
        t1.active = tab != 1;
        addRenderableWidget(t0);
        addRenderableWidget(t1);
        if (tab == 0) initControl(); else initFlights();
        synced = false;
    }

    private void initControl() {
        modeButton = new WmdUi.KitButton(leftPos + 12, topPos + 84, 104, 16, modeText(),
                b -> sendConfig(menu.getMode() == RadarBlockEntity.MODE_SEEK ? RadarBlockEntity.MODE_TARGET : RadarBlockEntity.MODE_SEEK, menu.getFrequency()));
        addRenderableWidget(modeButton);

        bearingBox = new EditBox(font, leftPos + 12, topPos + 118, 40, 14, Component.literal("bearing"));
        bearingBox.setFilter(s -> s.matches("\\d{0,3}"));
        bearingBox.setMaxLength(3);
        addRenderableWidget(bearingBox);
        turnButton = new WmdUi.KitButton(leftPos + 56, topPos + 116, 48, 18, Component.translatable("screen.wmd.radar.turn"), b -> sendBearing(parse(bearingBox, 359, 0)));
        addRenderableWidget(turnButton);
        String[] names = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            final int deg = i * 90;
            Button c = new WmdUi.KitButton(leftPos + 12 + i * 23, topPos + 138, 21, 16, Component.literal(names[i]), b -> { bearingBox.setValue(Integer.toString(deg)); sendBearing(deg); });
            compassButtons.add(c);
            addRenderableWidget(c);
        }

        freqBox = new EditBox(font, leftPos + 150, topPos + 158, 44, 14, Component.literal("frequency"));
        freqBox.setFilter(s -> s.matches("\\d{0,4}"));
        freqBox.setMaxLength(4);
        setButton = new WmdUi.KitButton(leftPos + 198, topPos + 156, 44, 18, Component.translatable("screen.wmd.radar.set"), b -> sendConfig(menu.getMode(), parse(freqBox, 9999, 1)));
    }

    private void initFlights() {
        addRenderableWidget(new WmdUi.KitButton(leftPos + 190, topPos + 16, 58, 16, rangeText(), b -> { zoom = (zoom + 1) % RANGES.length; b.setMessage(rangeText()); }));
    }

    private Component modeText() {
        return Component.translatable(menu.getMode() == RadarBlockEntity.MODE_SEEK ? "screen.wmd.radar.mode.seek"
                : "screen.wmd.radar.mode.target");
    }

    private Component rangeText() {
        int r = RANGES[zoom];
        return Component.literal(r >= 1000 ? (r % 1000 == 0 ? Integer.toString(r / 1000) : Double.toString(r / 1000.0)) + " km" : r + " m");
    }

    private static int parse(EditBox box, int max, int min) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(box.getValue())));
        } catch (NumberFormatException e) {
            return min;
        }
    }

    private void sendBearing(int deg) { PacketDistributor.sendToServer(new SetRadarBearingPayload(menu.getPos(), deg)); }

    private void sendConfig(int mode, int frequency) { PacketDistributor.sendToServer(new SetRadarConfigPayload(menu.getPos(), mode, frequency)); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != 256) {
            for (EditBox box : new EditBox[] {bearingBox, freqBox}) {
                if (tab == 0 && box != null && box.isFocused()) {
                    if (box.keyPressed(keyCode, scanCode, modifiers) || box.canConsumeInput()) return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (tab == 0 && !synced && menu.getFrequency() > 0) {
            bearingBox.setValue(Integer.toString(Math.round(menu.getTargetBearing()) % 360));
            freqBox.setValue(String.format("%04d", menu.getFrequency()));
            synced = true;
        }
        if (tab == 0 && modeButton != null) {
            modeButton.setMessage(modeText());
            boolean manual = menu.getMode() == RadarBlockEntity.MODE_TARGET;
            bearingBox.setEditable(manual);
            turnButton.active = manual;
            for (Button b : compassButtons) b.active = manual;
        }
        updateSeen();
    }

    private boolean isOwnFlight(Flight f) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && f.isOwnedBy(mc.player.getUUID(), mc.player.getName().getString());
    }

    private Vec3 centre() {
        Minecraft mc = Minecraft.getInstance();
        return SableBridge.worldPos(mc.level, menu.getPos());
    }

    private float bearingOf(double x, double z) {
        Vec3 c = centre();
        double dx = x - c.x, dz = z - c.z;
        return RadarBlockEntity.norm((float) Math.toDegrees(Math.atan2(dx, -dz)));
    }

    private double distOf(double x, double z) {
        Vec3 c = centre();
        double dx = x - c.x, dz = z - c.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private List<RadarBlockEntity.ShipContact> visibleShips() {
        Minecraft mc = Minecraft.getInstance();
        boolean seeking = menu.isFormed() && menu.isPowered() && menu.getMode() == RadarBlockEntity.MODE_SEEK;
        if (!seeking || mc.level == null || !(mc.level.getBlockEntity(menu.getPos()) instanceof RadarBlockEntity r)) return List.of();
        return r.shipContacts();
    }

    private final List<int[]> shipHits = new ArrayList<>();
    private final List<RadarBlockEntity.ShipContact> shipHitShips = new ArrayList<>();
    private java.util.UUID pendingShip;
    private long pendingAt;

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && minecraft != null) {
            double lx = mx - leftPos, ly = my - topPos;
            for (int i = 0; i < shipHits.size(); i++) {
                int[] r = shipHits.get(i);
                if (lx < r[0] || lx >= r[2] || ly < r[1] || ly >= r[3]) continue;
                RadarBlockEntity.ShipContact s = shipHitShips.get(i);
                long t = System.currentTimeMillis();
                int action;
                if (s.designated()) action = ShipTargetPayload.CLEAR;
                else if (s.hostile() || (s.id().equals(pendingShip) && t - pendingAt < 6000)) action = ShipTargetPayload.CONFIRM;
                else {
                    pendingShip = s.id(); pendingAt = t;
                    if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable("message.wmd.ship.confirm", s.name().isEmpty() ? "airship" : s.name()), true);
                    WmdUi.click();
                    return true;
                }
                PacketDistributor.sendToServer(new ShipTargetPayload(menu.getPos(), s.id(), action));
                pendingShip = null;
                WmdUi.click();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static int shipColour(RadarBlockEntity.ShipContact s) { return s.hostile() ? RED : s.friendly() ? BLUE : SHIP_NEUTRAL; }

    private void updateSeen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        boolean online = menu.isFormed() && menu.isPowered();
        boolean seeking = online && menu.getMode() == RadarBlockEntity.MODE_SEEK;
        boolean satFeed = seeking && menu.isSatFeed();
        for (Flight f : ClientFlights.FLIGHTS.values()) {
            if (now < f.start() || now > f.endTime()) continue;
            Vec3 p = f.position(now);
            boolean own = isOwnFlight(f);
            double dist = distOf(p.x, p.z);
            boolean satSees = satFeed && !own && dist <= CruiseRoute.radarRange(f, TrackNet.SAT_REACH);
            if (dist > CruiseRoute.radarRange(f, RadarBlockEntity.DETECT_RANGE)) {
                if (satSees) seen.put(f.id(), new Seen(p.x, p.z, f.tier(), now, false, true));
                continue;
            }
            boolean visible = own || (seeking && Math.abs(RadarBlockEntity.delta(bearingOf(p.x, p.z) - menu.getBearing())) <= RadarBlockEntity.BEAM_HALF);
            if (visible) seen.put(f.id(), new Seen(p.x, p.z, f.tier(), now, own, false));
            else if (satSees) seen.put(f.id(), new Seen(p.x, p.z, f.tier(), now, false, true));
        }
        for (Iterator<Map.Entry<Long, Seen>> it = seen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Seen> e = it.next();
            Flight f = ClientFlights.FLIGHTS.get(e.getKey());
            boolean gone = f == null || now > f.endTime();
            if (gone || (!e.getValue().own() && now - e.getValue().tick() > ECHO_TICKS)) it.remove();
        }
    }

    private static void dot(GuiGraphics g, int x, int y, int colour) { g.fill(x, y, x + 1, y + 1, colour); }

    private void polar(GuiGraphics g, int cx, int cy, float deg, float radius, int colour) {
        double a = Math.toRadians(deg);
        dot(g, cx + (int) Math.round(Math.sin(a) * radius), cy - (int) Math.round(Math.cos(a) * radius), colour);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        WmdUi.panel(g, x, y, imageWidth, imageHeight);
        WmdUi.divider(g, x + 8, y + 14, imageWidth - 16);
        WmdHelp.button(g, x + imageWidth - 8 - WmdHelp.SIZE, y + 2);
        int tx = x + (tab == 0 ? 8 : 66);
        g.fill(tx + 2, y + 33, tx + 54, y + 34, WmdUi.BLUE);
        if (tab == 0) drawControlScope(g); else drawFlightScope(g);
    }

    private void drawControlScope(GuiGraphics g) {
        int cx = leftPos + 190, cy = topPos + 88, r = 42;
        WmdUi.well(g, cx - r - 3, cy - r - 3, 2 * r + 7, 2 * r + 7);
        g.fill(cx - r - 3, cy - r - 3, cx + r + 4, cy + r + 4, SCOPE_BG);
        float bearing = menu.getBearing();
        boolean seek = menu.getMode() == RadarBlockEntity.MODE_SEEK;
        float half = seek ? RadarBlockEntity.BEAM_HALF : 90.0F;
        for (float d = bearing - half; d <= bearing + half; d += 2.0F) {
            for (float rr = 4; rr < r; rr += 2.0F) polar(g, cx, cy, d, rr, seek ? BEAM : ARC);
        }
        for (int d = 0; d < 360; d += 3) polar(g, cx, cy, d, r, RING);
        for (float rr = 0; rr <= r; rr += 1.0F) {
            polar(g, cx, cy, bearing - half, rr, RING);
            polar(g, cx, cy, bearing + half, rr, RING);
        }
        for (float rr = 0; rr <= r; rr += 0.7F) polar(g, cx, cy, bearing, rr, NEEDLE);
        if (!seek) {
            float target = menu.getTargetBearing();
            if (Math.abs(RadarBlockEntity.delta(target - bearing)) > 0.5F) {
                for (float rr = r - 6; rr <= r + 2; rr += 0.7F) polar(g, cx, cy, target, rr, MARK);
            }
        }
    }

    private void drawFlightScope(GuiGraphics g) {
        int cx = leftPos + 70, cy = topPos + 108, r = 62;
        WmdUi.well(g, cx - r - 3, cy - r - 3, 2 * r + 7, 2 * r + 7);
        g.fill(cx - r - 3, cy - r - 3, cx + r + 4, cy + r + 4, SCOPE_BG);
        boolean seek = menu.getMode() == RadarBlockEntity.MODE_SEEK;
        float bearing = menu.getBearing();
        if (seek) {
            for (float d = bearing - RadarBlockEntity.BEAM_HALF; d <= bearing + RadarBlockEntity.BEAM_HALF; d += 1.5F) {
                for (float rr = 3; rr < r; rr += 2.0F) polar(g, cx, cy, d, rr, BEAM);
            }
        }
        for (int ring = 1; ring <= 4; ring++) {
            for (int d = 0; d < 360; d += 4) polar(g, cx, cy, d, r * ring / 4.0F, RING);
        }
        for (int d = 0; d < 360; d += 90) {
            for (float rr = 0; rr <= r; rr += 2.0F) polar(g, cx, cy, d, rr, RING);
        }
        for (float rr = 0; rr <= r; rr += 0.7F) polar(g, cx, cy, bearing, rr, NEEDLE);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        double range = RANGES[zoom];
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFFFFF);
        Vec3 rc = centre();
        double rx = rc.x, rz = rc.z;

        if (mc.level.getBlockEntity(menu.getPos()) instanceof RadarBlockEntity rbe && PortableJammerBlockEntity.jamsCached(mc.level, menu.getPos(), rbe.getOwner())) {
            for (int k = 0; k < 7; k++) {
                long h = (k * 0x9E3779B97F4A7C15L) ^ 0x5DEECE66DL;
                double a0 = ((h >>> 12) & 1023) / 1023.0 * Math.PI * 2, rr = (0.25 + ((h >>> 24) & 1023) / 1023.0 * 0.7) * r;
                double a = a0 + now * (0.004 + ((h >>> 40) & 7) * 0.002) * ((k & 1) == 0 ? 1 : -1);
                int gx = cx + (int) Math.round(Math.cos(a) * rr), gy = cy + (int) Math.round(Math.sin(a) * rr);
                int alpha = 120 + (int) (100 * Math.abs(Math.sin(now * 0.07 + k)));
                g.fill(gx - 1, gy - 1, gx + 2, gy + 2, (alpha << 24) | RED);
            }
        }
        for (Map.Entry<Long, Seen> e : seen.entrySet()) {
            Seen s = e.getValue();
            double px = s.x(), pz = s.z();
            Flight f = ClientFlights.FLIGHTS.get(e.getKey());
            long age = now - s.tick();
            if (f != null && (s.own() || s.sat() || age <= 2)) {
                Vec3 p = f.position(now + mc.getTimer().getGameTimeDeltaPartialTick(false));
                px = p.x; pz = p.z;
            }
            double dx = (px - rx) / range * r, dz = (pz - rz) / range * r;
            int a = s.own() || s.sat() ? 255 : (int) Math.max(40, 255 * (1.0 - age / (double) ECHO_TICKS));
            int colour = (a << 24) | (s.own() ? BLUE : s.sat() ? VIOLET : RED);
            int bx = cx + (int) Math.round(dx), by = cy + (int) Math.round(dz);
            boolean blipIn = dx * dx + dz * dz <= (double) r * r;
            if (f != null && f.targetKnown()) {

                Vec3 seen = s.own() ? f.target() : f.seenTarget(now);
                double ix = (seen.x - rx) / range * r, iz = (seen.z - rz) / range * r;
                int mx = cx + (int) Math.round(ix), my = cy + (int) Math.round(iz);
                boolean impactIn = ix * ix + iz * iz <= (double) r * r;
                int steps = (int) (Math.hypot(mx - bx, my - by) / 3.0);
                for (int i = 1; i < steps; i++) {
                    double t = i / (double) steps;
                    double lx = dx + (ix - dx) * t, lz = dz + (iz - dz) * t;
                    if (lx * lx + lz * lz <= (double) r * r) dot(g, cx + (int) Math.round(lx), cy + (int) Math.round(lz), colour);
                }
                if (impactIn) {
                    for (int d = -3; d <= 3; d++) {
                        dot(g, mx + d, my + d, colour);
                        dot(g, mx + d, my - d, colour);
                    }
                }
            }
            if (!blipIn) continue;
            int size = (s.own() || s.sat() || age <= 2) ? 2 : 1;
            g.fill(bx - size, by - size, bx + size + 1, by + size + 1, colour);
            if (f != null && menu.isLocked() && !s.own() && age <= 2) {
                for (int d = 0; d < 360; d += 30) polar(g, bx, by, d, 5, 0xFFFFB030);
            }
        }

        for (DroneView.Contact c : visibleDrones()) {
            double ddx = (c.x() - rx) / range * r, ddz = (c.z() - rz) / range * r;
            if (ddx * ddx + ddz * ddz > (double) r * r) continue;
            int qx = cx + (int) Math.round(ddx), qy = cy + (int) Math.round(ddz);
            int col = 0xFF000000 | (isOwnDrone(c) ? BLUE : DRONE);
            g.fill(qx - 2, qy - 2, qx + 3, qy - 1, col); g.fill(qx - 2, qy + 2, qx + 3, qy + 3, col);
            g.fill(qx - 2, qy - 2, qx - 1, qy + 3, col); g.fill(qx + 2, qy - 2, qx + 3, qy + 3, col);
        }

        for (BombView.Contact c : visibleBombs()) {
            double bdx = (c.x() - rx) / range * r, bdz = (c.z() - rz) / range * r;
            double idx = (c.ix() - rx) / range * r, idz = (c.iz() - rz) / range * r;
            int col = 0xFF000000 | (isOwnBomb(c) ? BLUE : BOMB);
            int qx = cx + (int) Math.round(bdx), qy = cy + (int) Math.round(bdz), mx = cx + (int) Math.round(idx), my = cy + (int) Math.round(idz);
            int steps = (int) (Math.hypot(mx - qx, my - qy) / 3.0);
            for (int i = 1; i < steps; i++) {
                double t = i / (double) steps, lx = bdx + (idx - bdx) * t, lz = bdz + (idz - bdz) * t;
                if (lx * lx + lz * lz <= (double) r * r) dot(g, cx + (int) Math.round(lx), cy + (int) Math.round(lz), col);
            }
            if (idx * idx + idz * idz <= (double) r * r) for (int d = -2; d <= 2; d++) { dot(g, mx + d, my + d, col); dot(g, mx + d, my - d, col); }
            if (bdx * bdx + bdz * bdz > (double) r * r) continue;
            g.fill(qx, qy - 2, qx + 1, qy + 3, col); g.fill(qx - 2, qy, qx + 3, qy + 1, col);
            g.fill(qx - 1, qy - 1, qx + 2, qy + 2, col);
        }

        for (double[] c : visibleFalls()) {
            double fdx = (c[0] - rx) / range * r, fdz = (c[1] - rz) / range * r, idx = (c[2] - rx) / range * r, idz = (c[3] - rz) / range * r;
            int col = 0xFF000000 | RED;
            int qx = cx + (int) Math.round(fdx), qy = cy + (int) Math.round(fdz), mx = cx + (int) Math.round(idx), my = cy + (int) Math.round(idz);
            int steps = (int) (Math.hypot(mx - qx, my - qy) / 3.0);
            for (int i = 1; i < steps; i++) {
                double t = i / (double) steps, lx = fdx + (idx - fdx) * t, lz = fdz + (idz - fdz) * t;
                if (lx * lx + lz * lz <= (double) r * r) dot(g, cx + (int) Math.round(lx), cy + (int) Math.round(lz), col);
            }
            if (idx * idx + idz * idz <= (double) r * r) for (int d = -3; d <= 3; d++) { dot(g, mx + d, my + d, col); dot(g, mx + d, my - d, col); }
            if (fdx * fdx + fdz * fdz > (double) r * r) continue;
            g.fill(qx, qy - 3, qx + 1, qy + 4, col); g.fill(qx - 3, qy, qx + 4, qy + 1, col);
            g.fill(qx - 1, qy - 1, qx + 2, qy + 2, 0xFFFFFFFF);
        }

        for (RadarBlockEntity.ShipContact s : visibleShips()) {
            double sdx = (s.x() - rx) / range * r, sdz = (s.z() - rz) / range * r;
            if (sdx * sdx + sdz * sdz > (double) r * r) continue;
            int qx = cx + (int) Math.round(sdx), qy = cy + (int) Math.round(sdz);
            int col = 0xFF000000 | shipColour(s);
            g.fill(qx - 3, qy - 2, qx + 4, qy - 1, col); g.fill(qx - 3, qy + 2, qx + 4, qy + 3, col);
            g.fill(qx - 4, qy - 1, qx - 3, qy + 2, col); g.fill(qx + 4, qy - 1, qx + 5, qy + 2, col);
            g.fill(qx, qy, qx + 1, qy + 1, col);
            if (s.hx() != 0.0F || s.hz() != 0.0F) for (int k = 4; k <= 8; k++) dot(g, qx + Math.round(s.hx() * k), qy + Math.round(s.hz() * k), col);
            if (s.locked()) for (int d = 0; d < 360; d += 30) polar(g, qx, qy, d, 7, 0xFFFFB030);
            String lab = "AIRSHIP " + s.blocks();
            float lw = font.width(lab) * 0.5F;
            int lx = (int) Math.max(cx - r, Math.min(cx + r - lw, qx - lw / 2.0F));
            g.pose().pushPose();
            g.pose().translate(lx, qy + 4, 0.0F);
            g.pose().scale(0.5F, 0.5F, 1.0F);
            g.drawString(font, lab, 0, 0, col, false);
            g.pose().popPose();
        }

        boolean seekOn = menu.isFormed() && menu.isPowered() && menu.getMode() == RadarBlockEntity.MODE_SEEK;
        java.util.UUID me = mc.player != null ? mc.player.getUUID() : null;
        for (ArtShell sh : ClientArtillery.visible(rx, rz, RadarBlockEntity.DETECT_RANGE, seekOn, me, now)) {
            Vec3 sp = sh.position(now + mc.getTimer().getGameTimeDeltaPartialTick(false));
            double ddx = (sp.x - rx) / range * r, ddz = (sp.z - rz) / range * r;
            if (ddx * ddx + ddz * ddz > (double) r * r) continue;
            int qx = cx + (int) Math.round(ddx), qy = cy + (int) Math.round(ddz);
            int col = 0xFF000000 | (me != null && me.equals(sh.owner()) ? BLUE : DRONE);
            g.fill(qx - 1, qy, qx + 2, qy + 1, col); g.fill(qx, qy - 1, qx + 1, qy + 2, col);
        }

        double lim = (double) r * r;
        for (ClientIntercepts.Shot sh : ClientIntercepts.SHOTS.values()) {
            if (now < sh.start() || now > sh.start() + sh.duration()) continue;
            Vec3 ip = ClientIntercepts.position(sh, now + mc.getTimer().getGameTimeDeltaPartialTick(false));
            double ax = (ip.x - rx) / range * r, az = (ip.z - rz) / range * r;
            double mx2 = (sh.to().x - rx) / range * r, mz2 = (sh.to().z - rz) / range * r;
            int steps = (int) (Math.hypot(mx2 - ax, mz2 - az) / 3.0);
            for (int i = 1; i < steps; i++) {
                double t2 = i / (double) steps, lx = ax + (mx2 - ax) * t2, lz = az + (mz2 - az) * t2;
                if (lx * lx + lz * lz <= lim) dot(g, cx + (int) Math.round(lx), cy + (int) Math.round(lz), 0xFF60E0FF);
            }
            if (mx2 * mx2 + mz2 * mz2 <= lim) {
                int qx = cx + (int) Math.round(mx2), qy = cy + (int) Math.round(mz2);
                for (int d = 0; d < 360; d += 45) polar(g, qx, qy, d, 3, 0xFF60E0FF);
            }
            if (ax * ax + az * az <= lim) {
                int qx = cx + (int) Math.round(ax), qy = cy + (int) Math.round(az);
                g.fill(qx - 1, qy - 1, qx + 2, qy + 2, 0xFFFFFFFF);
            }
        }
        for (ClientIntercepts.Burst b : ClientIntercepts.BURSTS) {
            double bx2 = (b.x() - rx) / range * r, bz2 = (b.z() - rz) / range * r;
            if (bx2 * bx2 + bz2 * bz2 > lim) continue;
            long age = now - b.tick();
            int al = (int) Math.max(70, 255 * (1.0 - age / (double) ClientIntercepts.BURST_KEEP));
            int qx = cx + (int) Math.round(bx2), qy = cy + (int) Math.round(bz2);
            if (b.kill()) {
                int col = (al << 24) | 0x40FF70;
                for (int d = 0; d < 360; d += 20) polar(g, qx, qy, d, 4, col);
                g.fill(qx, qy, qx + 1, qy + 1, col);
            } else {
                int col = (al << 24) | 0xFFA030;
                for (int d = -3; d <= 3; d++) { dot(g, qx + d, qy + d, col); dot(g, qx + d, qy - d, col); }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 4, TEXT, false);
        boolean online = menu.isFormed() && menu.isPowered();
        Component status = Component.translatable(!menu.isFormed() ? "screen.wmd.radar.not_formed"
                : online ? "screen.wmd.radar.online" : "screen.wmd.radar.offline");
        if (tab == 0) {
            g.drawString(font, Component.translatable("screen.wmd.radar.facing",
                    String.format("%03d", Math.round(menu.getBearing()) % 360)), 12, 40, TEXT, false);
            boolean seek = menu.getMode() == RadarBlockEntity.MODE_SEEK;
            Component line = seek ? Component.translatable(menu.isLocked() ? "screen.wmd.radar.locked" : "screen.wmd.radar.sweeping")
                    : Component.translatable("screen.wmd.radar.target", String.format("%03d", Math.round(menu.getTargetBearing()) % 360));
            g.drawString(font, line, 12, 50, menu.isLocked() ? TEXT_BAD : TEXT, false);
            g.drawString(font, status, 12, 62, online ? TEXT_GOOD : TEXT_BAD, false);
            g.drawString(font, String.format("%,d / %,d FE", menu.getEnergy(), RadarBlockEntity.CAPACITY), 12, 72, WmdUi.CYAN, false);
            g.drawString(font, Component.translatable("screen.wmd.radar.bearing_label"), 12, 106, MUTED, false);
            g.drawString(font, "N", 188, 33, MUTED, false);
            g.drawString(font, "S", 188, 135, MUTED, false);
            g.drawString(font, "E", 236, 85, MUTED, false);
            g.drawString(font, "W", 138, 85, MUTED, false);
        } else {
            g.drawString(font, status, 140, 38, online ? TEXT_GOOD : TEXT_BAD, false);
            g.fill(140, 49, 146, 55, 0xFF000000 | BLUE);
            g.drawString(font, Component.translatable("screen.wmd.radar.yours"), 150, 48, TEXT, false);
            g.fill(192, 49, 198, 55, 0xFF000000 | RED);
            g.drawString(font, Component.translatable("screen.wmd.radar.others"), 202, 48, TEXT, false);
            if (menu.isSatFeed()) {
                g.fill(212, 39, 218, 45, 0xFF000000 | VIOLET);
                g.drawString(font, Component.translatable("screen.wmd.radar.sat"), 222, 38, TEXT, false);
            }
            drawContactList(g);
        }
    }

    private List<DroneView.Contact> visibleDrones() {
        List<DroneView.Contact> out = new ArrayList<>();
        boolean seeking = menu.isFormed() && menu.isPowered() && menu.getMode() == RadarBlockEntity.MODE_SEEK;
        for (DroneView.Contact c : DroneView.contacts()) {
            if (c.phase() == Drones.FALLING) continue;
            if (isOwnDrone(c) || (seeking && distOf(c.x(), c.z()) <= RadarBlockEntity.DETECT_RANGE)) out.add(c);
        }
        return out;
    }

    private List<BombView.Contact> visibleBombs() {
        List<BombView.Contact> out = new ArrayList<>();
        boolean seeking = menu.isFormed() && menu.isPowered() && menu.getMode() == RadarBlockEntity.MODE_SEEK;
        for (BombView.Contact c : BombView.contacts())
            if (isOwnBomb(c) || (seeking && distOf(c.x(), c.z()) <= RadarBlockEntity.DETECT_RANGE)) out.add(c);
        return out;
    }

    private List<double[]> visibleFalls() {
        List<double[]> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(menu.isFormed() && menu.isPowered() && menu.getMode() == RadarBlockEntity.MODE_SEEK)) return out;
        long now = mc.level.getGameTime();
        for (Object[] e : RailgunEventPayload.EVENTS.values()) {
            RailgunEventPayload p = (RailgunEventPayload) e[0];
            long left = p.fireTick() - now;
            if (p.kind() != RailgunEventPayload.KIND_ROD || p.stage() != RailgunEventPayload.FINAL && p.stage() != RailgunEventPayload.FIRED || left <= 0 || left > Balance.ROD_FALL_TICKS) continue;
            if (distOf(p.tx() + 0.5, p.tz() + 0.5) <= RadarBlockEntity.DETECT_RANGE) out.add(new double[] { p.tx() + 0.5, p.tz() + 0.5, p.tx() + 0.5, p.tz() + 0.5, left / 20.0, 0 });
        }
        for (Object[] e : AsteroidBayPayloads.FALLS.values()) {
            AsteroidBayPayloads.Fall p = (AsteroidBayPayloads.Fall) e[0];
            long left = p.impact() - now;
            if (p.stage() != 1 || left <= 0) continue;
            int n = Math.max(1, p.pieces());
            net.minecraft.world.phys.Vec3 t = new net.minecraft.world.phys.Vec3(p.tx() + 0.5, p.ty(), p.tz() + 0.5);
            for (int i = 0; i < n; i++) {
                net.minecraft.world.phys.Vec3 q = AsteroidDrop.piecePos(t, p.seed(), left, i, n, p.split() == 0 ? 0.0 : p.impact() - p.split());
                net.minecraft.world.phys.Vec3 land = t.add(AsteroidDrop.scatter(p.seed(), i, n));
                if (distOf(q.x, q.z) <= RadarBlockEntity.DETECT_RANGE) out.add(new double[] { q.x, q.z, land.x, land.z, left / 20.0, 1 });
            }
        }
        return out;
    }

    private boolean isOwnBomb(BombView.Contact c) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(c.owner());
    }

    private boolean isOwnDrone(DroneView.Contact c) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getUUID().equals(c.owner());
    }

    private void drawContactList(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        List<Map.Entry<Long, Seen>> rows = new ArrayList<>(seen.entrySet());
        rows.sort((a, b) -> Double.compare(distOf(a.getValue().x(), a.getValue().z()), distOf(b.getValue().x(), b.getValue().z())));
        List<DroneView.Contact> drones = visibleDrones();
        drones.sort((a, b) -> Double.compare(distOf(a.x(), a.z()), distOf(b.x(), b.z())));
        shipHits.clear();
        shipHitShips.clear();
        List<RadarBlockEntity.ShipContact> ships = new ArrayList<>(visibleShips());
        ships.sort((a, b) -> Double.compare(distOf(a.x(), a.z()), distOf(b.x(), b.z())));
        List<BombView.Contact> bombs = new ArrayList<>(visibleBombs());
        bombs.sort((a, b) -> Double.compare(distOf(a.x(), a.z()), distOf(b.x(), b.z())));
        List<double[]> falls = visibleFalls();
        if (rows.isEmpty() && drones.isEmpty() && ships.isEmpty() && bombs.isEmpty() && falls.isEmpty()) {
            g.drawString(font, Component.translatable("screen.wmd.radar.no_contacts"), 140, 64, MUTED, false);
            return;
        }
        int line = 0;
        for (double[] c : falls) {
            if (line >= 5) break;
            int y = 64 + line * 22, secs = (int) c[4];
            g.fill(140, y + 1, 146, y + 7, 0xFF000000 | RED);
            g.drawString(font, Component.translatable(c[5] == 0 ? "screen.wmd.radar.rod" : "screen.wmd.radar.asteroid", String.format("%d:%02d", secs / 60, secs % 60)), 150, y, TEXT_BAD, false);
            g.drawString(font, Component.translatable("screen.wmd.radar.impact", dist(distOf(c[2], c[3]))), 150, y + 10, MUTED, false);
            line++;
        }
        for (Map.Entry<Long, Seen> e : rows) {
            if (line >= 5) break;
            Seen s = e.getValue();
            Flight f = ClientFlights.FLIGHTS.get(e.getKey());
            Vec3 p = f != null ? f.position(now) : new Vec3(s.x(), 0, s.z());
            String eta = "";
            String impact = "";
            if (f != null && !f.targetKnown()) { eta = "--:--"; impact = "?"; }
            else if (f != null) {
                int secs = (int) Math.max(0, (f.endTime() - now) / 20);
                eta = String.format("%d:%02d", secs / 60, secs % 60);
                Vec3 seen = s.own() ? f.target() : f.seenTarget(now);
                impact = dist(distOf(seen.x, seen.z));
            }
            int y = 64 + line * 22;
            g.fill(140, y + 1, 146, y + 7, 0xFF000000 | (s.own() ? BLUE : s.sat() ? VIOLET : RED));
            g.drawString(font, "T" + s.tier() + "  " + dist(distOf(p.x, p.z)) + "  " + eta, 150, y, TEXT, false);
            g.drawString(font, Component.translatable("screen.wmd.radar.impact", impact), 150, y + 10, MUTED, false);
            line++;
        }
        for (RadarBlockEntity.ShipContact s : ships) {
            if (line >= 5) break;
            int y = 64 + line * 22;
            shipHits.add(new int[] { 139, y - 1, 252, y + 20 });
            shipHitShips.add(s);
            g.fill(140, y + 1, 146, y + 7, 0xFF000000 | shipColour(s));
            if (s.locked()) { g.fill(139, y, 147, y + 1, 0xFFFFB030); g.fill(139, y + 7, 147, y + 8, 0xFFFFB030); }
            g.drawString(font, Component.translatable("screen.wmd.radar.airship", s.blocks(), dist(distOf(s.x(), s.z()))), 150, y, TEXT, false);
            Component rel = Component.translatable(s.locked() ? "screen.wmd.radar.ship_locked" : s.hostile() ? "screen.wmd.radar.ship_hostile"
                    : s.friendly() ? "screen.wmd.radar.ship_friendly" : "screen.wmd.radar.ship_neutral");
            String second = (s.designated() ? "[TARGET] " : "") + rel.getString() + (s.name().isEmpty() ? "" : " " + s.name());
            g.drawString(font, font.plainSubstrByWidth(second, 100), 150, y + 10, s.designated() ? 0xFFFFB030 : s.hostile() ? TEXT_BAD : MUTED, false);
            line++;
        }
        for (BombView.Contact c : bombs) {
            if (line >= 5) break;
            int y = 64 + line * 22;
            g.fill(140, y + 1, 146, y + 7, 0xFF000000 | (isOwnBomb(c) ? BLUE : BOMB));
            g.drawString(font, Component.translatable("screen.wmd.radar.bomb", dist(distOf(c.x(), c.z()))), 150, y, TEXT, false);
            g.drawString(font, Component.translatable("screen.wmd.radar.impact", dist(distOf(c.ix(), c.iz()))), 150, y + 10, MUTED, false);
            line++;
        }
        for (DroneView.Contact c : drones) {
            if (line >= 5) break;
            int y = 64 + line * 22;
            g.fill(140, y + 1, 146, y + 7, 0xFF000000 | (isOwnDrone(c) ? BLUE : DRONE));
            g.drawString(font, Component.translatable("screen.wmd.radar.drone", dist(distOf(c.x(), c.z()))), 150, y, TEXT, false);
            line++;
        }
    }

    private static String dist(double blocks) {
        return blocks < 1000 ? String.format("%dm", Math.round(blocks)) : String.format("%.1fkm", blocks / 1000.0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
