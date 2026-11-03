package com.ashraf.wmd;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class DhShells {

    private DhShells() {}

    private static final Map<Long, IDhApiRenderableBoxGroup> GROUPS = new HashMap<>();
    private static final Color BODY = new Color(70, 72, 76), GLINT = new Color(255, 214, 150);

    static void tick(IDhApiCustomRenderRegister register, Vec3 cam, double near, long now) {
        for (ArtShell s : ArtilleryView.SHELLS.values()) {
            boolean flying = now >= s.start() && now <= s.endTime();
            boolean far = s.position(now).distanceTo(cam) > near;
            IDhApiRenderableBoxGroup g = GROUPS.get(s.id());
            if (flying && far && g == null) {
                Vec3 p = s.position(now);
                g = DhApi.Delayed.customRenderObjectFactory.createRelativePositionedGroup(Wmd.MODID + ":shell", new DhApiVec3d(p.x, p.y, p.z), build(s, now, p, cam));
                g.setSkyLight(15);
                g.setBlockLight(6);
                final IDhApiRenderableBoxGroup gg = g;
                final long id = s.id();
                g.setPreRenderFunc(param -> update(id, gg));
                register.add(g);
                GROUPS.put(s.id(), g);
            } else if (g != null && (!flying || !far)) {
                register.remove(g.getId());
                GROUPS.remove(s.id());
            }
        }
        GROUPS.entrySet().removeIf(e -> {
            if (ArtilleryView.SHELLS.containsKey(e.getKey())) return false;
            register.remove(e.getValue().getId());
            return true;
        });
    }

    static void reset(IDhApiCustomRenderRegister register) {
        if (register != null) for (IDhApiRenderableBoxGroup g : GROUPS.values()) { try { register.remove(g.getId()); } catch (RuntimeException ignored) { } }
        GROUPS.clear();
    }

    private static void update(long id, IDhApiRenderableBoxGroup g) {
        Minecraft mc = Minecraft.getInstance();
        ArtShell s = ArtilleryView.SHELLS.get(id);
        if (s == null || mc.level == null) { g.setActive(false); return; }
        double t = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        if (t < s.start() || t > s.endTime()) { g.setActive(false); return; }
        Vec3 p = s.position(t);
        g.setActive(true);
        g.setOriginBlockPos(new DhApiVec3d(p.x, p.y, p.z));
        List<DhApiRenderableBox> boxes = build(s, t, p, mc.gameRenderer.getMainCamera().getPosition());
        g.clear();
        g.addAll(boxes);
        g.triggerBoxChange();
    }

    private static DhApiRenderableBox cube(Vec3 c, double size, Color color, EDhApiBlockMaterial m) {
        double h = size * 0.5;
        return new DhApiRenderableBox(new DhApiVec3d(c.x - h, c.y - h, c.z - h), new DhApiVec3d(c.x + h, c.y + h, c.z + h), color, m);
    }

    private static List<DhApiRenderableBox> build(ArtShell s, double t, Vec3 p, Vec3 cam) {
        List<DhApiRenderableBox> boxes = new ArrayList<>(24);
        double sc = Mth.clamp(p.distanceTo(cam) / 1500.0, 0.8, 2.5);
        Vec3 v = s.velocity(t);
        Vec3 dir = v.lengthSqr() < 1.0E-8 ? new Vec3(0, -1, 0) : v.normalize();
        double w = (s.gun() == Artillery.HOWITZER ? 0.7 : 0.5) * sc;
        if (s.gun() == Artillery.SUPER_GUN) w = 1.4 * sc;
        boxes.add(cube(Vec3.ZERO, w, BODY, EDhApiBlockMaterial.METAL));
        boxes.add(cube(dir.scale(-w), w * 0.9, BODY, EDhApiBlockMaterial.METAL));
        boxes.add(cube(dir.scale(w * 0.8), w * 0.6, GLINT, EDhApiBlockMaterial.ILLUMINATED));
        double speed = Math.max(0.05, v.length());
        double dt = Mth.clamp(w * 1.2 / speed, 0.5, 6.0);
        for (int k = 1; k <= 16; k++) {
            double tk = t - k * dt;
            if (tk < s.start()) break;
            boxes.add(cube(s.position(tk).subtract(p), w * (0.5 + k * 0.03), new Color(205, 205, 210, Math.max(10, 110 - k * 6)), EDhApiBlockMaterial.STONE));
        }
        return boxes;
    }
}
