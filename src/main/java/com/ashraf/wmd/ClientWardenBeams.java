package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWardenBeams {

    private ClientWardenBeams() {}

    private record Shot(int kind, Vec3 from, Vec3 to, long start, int dur) {}

    private static final List<Shot> SHOTS = Statics.client(new ArrayList<>());

    static void add(WardenFxPayload p, long now) {
        SHOTS.add(new Shot(p.kind(), new Vec3(p.x(), p.y(), p.z()), new Vec3(p.tx(), p.ty(), p.tz()), now,
                p.kind() == WardenFxPayload.RAIL ? 16 : p.kind() == WardenFxPayload.EYE ? 9 : p.dur()));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { SHOTS.clear(); return; }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();

        float t = (now & 0xFFFFFL) + pt;
        SHOTS.removeIf(s -> now - s.start() > s.dur() + 1 || now < s.start() - 1);
        List<WardenEntity> ws = level.getEntitiesOfClass(WardenEntity.class, mc.player.getBoundingBox().inflate(160.0));
        if (ws.isEmpty() && SHOTS.isEmpty()) return;
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        Matrix4f m = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Draw d = new Draw(b, m, cam, left, up);
        for (WardenEntity w : ws) warden(d, level, w, pt, t);
        for (Shot s : SHOTS) shot(d, s, t, (now - s.start()) + pt);
        if (d.any) BufferUploader.drawWithShader(b.buildOrThrow()); else b.build();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    private static void warden(Draw d, ClientLevel level, WardenEntity w, float pt, float t) {
        if (w.mode() != WardenEntity.ACTIVE && w.mode() != WardenEntity.DYING) return;
        Vec3 shift = new Vec3(Mth.lerp(pt, w.xo, w.getX()) - w.getX(), Mth.lerp(pt, w.yo, w.getY()) - w.getY(), Mth.lerp(pt, w.zo, w.getZ()) - w.getZ());
        Entity tg = w.aimTarget();
        Vec3 tgEye = tg == null ? null : tg.getEyePosition(pt);
        float flick = 0.75F + 0.25F * Mth.sin(t * 2.7F);

        int pod = w.pod();
        if (pod > 0 && pod <= Balance.WARDEN_ROCKET_LOCK + 1 && tgEye != null) {
            float k = Math.min(1.0F, (pod + 0.0F) / 6.0F);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 a = w.local(s * 3.9, 12.6, 0.4).add(shift);
                Vec3 e = clip(level, a, tgEye);
                d.ribbon(a, e, 0.10F, 1.0F, 0.12F, 0.08F, 0.35F * k * flick);
                d.ribbon(a, e, 0.03F, 1.0F, 0.75F, 0.7F, 0.9F * k * flick);
                d.card(e, 0.35F, 1.0F, 0.15F, 0.1F, 0.8F * k);
                d.card(a, 0.4F, 1.0F, 0.2F, 0.1F, 0.6F * k);
            }
        }

        int eye = w.eye();
        if (eye > 0) {
            float k = Math.min(1.0F, eye / (float) Balance.WARDEN_EYE_CHARGE);
            Vec3 a = WardenOverdrive.eyeAt(w).add(shift);
            d.card(a, 0.4F + 1.4F * k, 1.0F, 0.15F, 0.08F, 0.5F + 0.5F * k);
            d.card(a, 0.2F + 0.4F * k, 1.0F, 0.85F, 0.8F, 0.6F + 0.4F * k);
            boolean locked = w.clientLockEye != null;
            Vec3 to = locked ? w.clientLockEye : tgEye;
            if (to != null) {
                Vec3 e = clip(level, a, to);
                if (locked) {
                    float pulse = 0.6F + 0.4F * Mth.sin(t * 4.0F);
                    d.ribbon(a, e, 0.16F, 1.0F, 0.1F, 0.05F, 0.45F * pulse);
                    d.ribbon(a, e, 0.05F, 1.0F, 0.7F, 0.6F, 0.95F);
                    d.card(e, 0.6F, 1.0F, 0.2F, 0.1F, 0.9F * pulse);
                } else {
                    d.ribbon(a, e, 0.025F, 1.0F, 0.25F, 0.2F, 0.35F * k * flick);
                }
            }
        }

        int rail = w.rail();
        if (rail > 0) {
            float u = Math.min(1.0F, rail / (float) Balance.WARDEN_RAIL_CHARGE);
            Vec3 a = w.railMuzzle().add(shift);
            boolean locked = w.clientLockRail != null;
            d.card(a, 0.6F + 2.4F * u, 0.45F, 0.75F, 1.0F, 0.35F + 0.5F * u);
            d.card(a, 0.3F + 0.9F * u, 0.85F, 0.95F, 1.0F, 0.5F + 0.5F * u);
            if (locked) {
                Vec3 e = clip(level, a, w.clientLockRail);
                float pulse = 0.6F + 0.4F * Mth.sin(t * 5.0F);
                d.ribbon(a, e, 0.12F, 1.0F, 0.15F, 0.1F, 0.4F * pulse);
                d.ribbon(a, e, 0.035F, 1.0F, 0.8F, 0.75F, 0.95F);
                d.card(e, 0.7F, 1.0F, 0.2F, 0.15F, 0.9F * pulse);
            }
        }

        if (w.stage() >= 3 && w.mode() == WardenEntity.ACTIVE) {
            float g = 0.5F + 0.5F * Mth.sin(t * 0.3F);
            Vec3 c = w.local(0.0, 8.6, 4.0).add(shift);
            d.card(c, 2.2F + 0.6F * g, 1.0F, 0.45F, 0.12F, 0.35F + 0.25F * g);
            d.card(c, 0.9F + 0.2F * g, 1.0F, 0.85F, 0.55F, 0.7F);
        }
    }

    private static void shot(Draw d, Shot s, float t, float age) {
        float len = s.dur();
        if (age < 0.0F || age > len) return;
        if (s.kind() == WardenFxPayload.RAIL) {
            float k = 1.0F - age / len, flash = age < 3.0F ? 1.0F : k;
            float grow = 1.0F + age * 0.12F;
            d.ribbon(s.from(), s.to(), 1.4F * grow, 0.3F, 0.5F, 1.0F, 0.22F * k);
            d.ribbon(s.from(), s.to(), 0.6F * grow, 0.5F, 0.8F, 1.0F, 0.55F * flash);
            d.ribbon(s.from(), s.to(), 0.18F, 1.0F, 1.0F, 1.0F, flash);
            d.card(s.from(), 3.0F * flash + 0.5F, 0.6F, 0.85F, 1.0F, flash);
            d.card(s.to(), 4.0F * flash + 1.0F, 0.8F, 0.9F, 1.0F, flash);
        } else if (s.kind() == WardenFxPayload.EYE) {
            float k = 1.0F - age / len;
            d.ribbon(s.from(), s.to(), 0.5F, 1.0F, 0.12F, 0.05F, 0.4F * k);
            d.ribbon(s.from(), s.to(), 0.16F, 1.0F, 0.6F, 0.4F, 0.95F * k);
            d.card(s.from(), 1.2F, 1.0F, 0.3F, 0.15F, k);
            d.card(s.to(), 1.8F, 1.0F, 0.45F, 0.15F, k);
        } else if (s.kind() == WardenFxPayload.RING) {
            float left = 1.0F - age / len;
            double r = Balance.MORTAR_HE_RADIUS * 3.5;
            float blink = left > 0.5F ? 0.6F : left > 0.2F ? 0.75F + 0.25F * Mth.sin(t * 1.5F) : 0.75F + 0.25F * Mth.sin(t * 3.0F);
            d.groundRing(s.to(), r, 0.22F, 1.0F, 0.15F, 0.1F, 0.8F * blink);
            d.groundRing(s.to(), r * left, 0.14F, 1.0F, 0.35F, 0.2F, 0.6F);
        } else if (s.kind() == WardenFxPayload.SHELL) {
            Vec3 a = shellAt(s, age), tail = shellAt(s, Math.max(0.0F, age - 2.5F));
            d.ribbon(tail, a, 0.12F, 1.0F, 0.5F, 0.15F, 0.6F);
            d.card(a, 0.7F, 1.0F, 0.6F, 0.2F, 0.9F);
            d.card(a, 0.3F, 1.0F, 0.95F, 0.8F, 1.0F);
        }
    }

    private static Vec3 shellAt(Shot s, float age) {
        float u = Mth.clamp(age / (float) Math.max(1, s.dur()), 0.0F, 1.0F);
        double h = 4.0 * Balance.WARDEN_MORTAR_APEX * u * (1.0F - u);
        return new Vec3(Mth.lerp(u, s.from().x, s.to().x), Mth.lerp(u, s.from().y, s.to().y) + h, Mth.lerp(u, s.from().z, s.to().z));
    }

    private static Vec3 clip(ClientLevel level, Vec3 a, Vec3 b) {
        BlockHitResult hit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? b : hit.getLocation();
    }

    private static final class Draw {
        final BufferBuilder b;
        final Matrix4f m;
        final Vec3 cam;
        final Vector3f left, up;
        boolean any;

        Draw(BufferBuilder b, Matrix4f m, Vec3 cam, Vector3f left, Vector3f up) { this.b = b; this.m = m; this.cam = cam; this.left = left; this.up = up; }

        private void v(double x, double y, double z, float r, float g, float bl, float a) {
            b.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, bl, Mth.clamp(a, 0.0F, 1.0F));
        }

        void ribbon(Vec3 p, Vec3 q, float w, float r, float g, float bl, float a) {
            if (a <= 0.003F) return;
            Vec3 pa = p.subtract(cam), qa = q.subtract(cam);
            Vec3 side = qa.subtract(pa).cross(pa.add(qa).scale(0.5));
            if (side.lengthSqr() < 1.0E-10) return;
            side = side.normalize().scale(w);
            v(p.x - side.x, p.y - side.y, p.z - side.z, r, g, bl, a);
            v(p.x + side.x, p.y + side.y, p.z + side.z, r, g, bl, a);
            v(q.x + side.x, q.y + side.y, q.z + side.z, r, g, bl, a * 0.85F);
            v(q.x - side.x, q.y - side.y, q.z - side.z, r, g, bl, a * 0.85F);
            any = true;
        }

        void card(Vec3 c, float size, float r, float g, float bl, float a) {
            if (a <= 0.003F || size <= 0.0F) return;
            int n = 12;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2.0 / n, a1 = (i + 1) * Math.PI * 2.0 / n;
                double x0 = Math.cos(a0) * size, y0 = Math.sin(a0) * size, x1 = Math.cos(a1) * size, y1 = Math.sin(a1) * size;
                v(c.x, c.y, c.z, r, g, bl, a);
                v(c.x, c.y, c.z, r, g, bl, a);
                v(c.x + left.x() * x1 + up.x() * y1, c.y + left.y() * x1 + up.y() * y1, c.z + left.z() * x1 + up.z() * y1, r, g, bl, 0.0F);
                v(c.x + left.x() * x0 + up.x() * y0, c.y + left.y() * x0 + up.y() * y0, c.z + left.z() * x0 + up.z() * y0, r, g, bl, 0.0F);
            }
            any = true;
        }

        void groundRing(Vec3 c, double rad, float w, float r, float g, float bl, float a) {
            if (a <= 0.003F || rad <= 0.05) return;
            int n = Math.max(24, (int) (rad * 6.0));
            double y = c.y + 0.08;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2.0 / n, a1 = (i + 1) * Math.PI * 2.0 / n;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                v(c.x + c0 * (rad - w), y, c.z + s0 * (rad - w), r, g, bl, a);
                v(c.x + c0 * (rad + w), y, c.z + s0 * (rad + w), r, g, bl, a);
                v(c.x + c1 * (rad + w), y, c.z + s1 * (rad + w), r, g, bl, a);
                v(c.x + c1 * (rad - w), y, c.z + s1 * (rad - w), r, g, bl, a);
            }
            any = true;
        }
    }
}
