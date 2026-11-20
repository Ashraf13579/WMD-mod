package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

public class WardenRenderer extends EntityRenderer<WardenEntity> {

    private static final ResourceLocation TEX = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");
    private static final float L1 = 6.5F, L2 = 11.0F;
    private static final int[] STEEL = {150, 158, 168}, DARK = {70, 76, 86}, BLUE = {90, 170, 255}, RED = {255, 60, 50}, CORE = {255, 190, 90}, LIGHTER = {185, 192, 200};

    public WardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(WardenEntity e) { return TEX; }

    @Override
    public void render(WardenEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        if (MeshModel.named(MESH).has("body")) { renderMesh(e, entityYaw, pt, ps, buffers, light); return; }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        int ov = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
        int mode = e.mode(), stage = e.stage();
        float t = e.tickCount + pt;
        float sink = mode == WardenEntity.DYING ? Math.min(1.0F, (e.clientModeTicks + pt) / 60.0F) * 6.0F : 0.0F;
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        float walk = Mth.lerp(pt, e.walkDistO, e.walkDist);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        PoseStack.Pose p = ps.last();
        float amt = Mth.lerp(pt, e.gaitAmtO, e.gaitAmt);
        float bob = -amt * 0.15F * Mth.cos(walk / WardenEntity.GAIT_CYCLE * Mth.TWO_PI * 4.0F);
        float hy = 10.5F - sink + bob;

        box(p, vc, 0, hy, 0, 3.5F, 1.75F, 4.0F, STEEL, light, ov);
        box(p, vc, 0, hy + 2.3F, 1.0F, 2.0F, 0.8F, 2.2F, LIGHTER, light, ov);
        boolean awake = mode != WardenEntity.DORMANT;
        int[] eye = stage == 1 ? BLUE : RED;
        box(p, vc, 0, hy + 2.3F, 3.25F, 1.2F, 0.22F, 0.06F, awake ? eye : DARK, awake ? LightTexture.FULL_BRIGHT : light, ov);

        box(p, vc, 2.6F, hy + 2.4F, -0.8F, 0.7F, 0.6F, 1.3F, DARK, light, ov);
        box(p, vc, -2.6F, hy + 2.4F, -0.8F, 0.7F, 0.6F, 1.3F, DARK, light, ov);
        box(p, vc, 0, hy - 1.3F, 4.6F, 0.5F, 0.5F, 0.8F, DARK, light, ov);
        box(p, vc, 0, hy + 2.6F, -2.8F, 0.45F, 0.9F, 0.45F, DARK, light, ov);

        float rise = stage >= 3 ? 1.0F : 0.0F;
        box(p, vc, 0, hy + 2.4F + rise, -1.0F + rise, 0.45F, 0.4F, 4.2F, DARK, light, ov);
        int lit = (int) (6.0F * Math.min(1.0F, e.rail() / (float) Balance.WARDEN_RAIL_CHARGE));
        if (stage >= 3) for (int i = 0; i < 6; i++)
            box(p, vc, 0, hy + 2.4F + rise, -3.5F + i * 1.2F + rise, 0.7F, 0.6F, 0.12F, i < lit ? BLUE : DARK, i < lit ? LightTexture.FULL_BRIGHT : light, ov);
        box(p, vc, 0, hy + 1.9F, -4.1F, 1.5F, 0.5F, 0.4F, stage == 2 ? RED : DARK, stage == 2 ? LightTexture.FULL_BRIGHT : light, ov);

        if (stage == 1 || stage == 2 && e.clientStageTicks < Balance.WARDEN_PLATE_FIRST) {
            box(p, vc, 3.7F, hy, 0, 0.15F, 1.4F, 3.5F, LIGHTER, light, ov);
            box(p, vc, -3.7F, hy, 0, 0.15F, 1.4F, 3.5F, LIGHTER, light, ov);
        }
        if (stage >= 2) for (int i = 0; i < 4; i++) {
            boolean on = ((int) (t / 5.0F) + i) % 2 == 0;
            box(p, vc, (i - 1.5F) * 2.0F, hy - 1.8F, 4.05F, 0.2F, 0.12F, 0.05F, on ? RED : DARK, on ? LightTexture.FULL_BRIGHT : light, ov);
        }

        float open = stage >= 3 ? 1.4F * ease((e.clientStageTicks - 10) / 30.0) : 0.0F;
        if (stage >= 3) {
            float g = 0.75F + 0.25F * Mth.sin(t * 0.4F);
            int[] c = {(int) (CORE[0] * g), (int) (CORE[1] * g), (int) (CORE[2] * g)};
            box(p, vc, 0, hy - 0.4F, 3.9F, 0.8F, 0.8F, 0.25F, c, LightTexture.FULL_BRIGHT, ov);
        }
        box(p, vc, 1.1F + open, hy - 0.4F, 4.1F, 1.0F, 1.1F, 0.15F, STEEL, light, ov);
        box(p, vc, -1.1F - open, hy - 0.4F, 4.1F, 1.0F, 1.1F, 0.15F, STEEL, light, ov);

        float moving = mode == WardenEntity.ACTIVE ? amt : 0.0F;
        for (int i = 0; i < 4; i++) {
            float side = i % 2 == 0 ? 1.0F : -1.0F, fore = i < 2 ? 1.0F : -1.0F;
            Vector3f hip = new Vector3f(side * 3.0F, hy - 0.6F, fore * 3.2F);
            float q = WardenEntity.gaitQ(walk, i), off = WardenEntity.gaitOff(q), lift = WardenEntity.gaitLift(q);
            float splay = sink * 0.6F;
            boolean broken = e.legBroken(i);
            if (broken) { lift = 0.0F; off = 0.0F; }
            Vector3f foot = new Vector3f(side * (7.0F + splay), lift * moving, fore * (6.6F + splay) + off * moving);
            Vector3f knee = knee(hip, foot);
            if (broken) knee.y -= 2.0F;
            beam(p, vc, hip, knee, 1.4F, DARK, light, ov);
            beam(p, vc, knee, foot, 1.1F, STEEL, light, ov);
            box(p, vc, foot.x, foot.y + 0.25F, foot.z, 0.9F, 0.25F, 0.9F, DARK, light, ov);
            box(p, vc, knee.x, knee.y, knee.z, 0.8F, 0.8F, 0.8F, LIGHTER, light, ov);
            if (broken) {
                Vector3f m = new Vector3f(hip).lerp(knee, 0.5F);
                box(p, vc, m.x, m.y, m.z, 0.35F, 0.35F, 0.35F, new int[] {90, 20, 15}, light, ov);
            } else if (stage == 1 && awake) {
                float g = 0.6F + 0.4F * Mth.sin(t * 0.3F + i);
                Vector3f m = new Vector3f(hip).lerp(knee, 0.5F);
                box(p, vc, m.x, m.y, m.z, 0.35F, 0.35F, 0.35F, new int[] {(int) (255 * g), (int) (140 * g), 40}, LightTexture.FULL_BRIGHT, ov);
            }
        }
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static final String MESH = "warden";
    private static final String[] LEG = {"fl", "fr", "rl", "rr"};
    private static final float[] LSX = {-1.0F, 1.0F, -1.0F, 1.0F}, LSZ = {-1.0F, -1.0F, 1.0F, 1.0F};

    private static Vector3f hipR(int i) { return new Vector3f(LSX[i] * 54.0F, 146.0F, LSZ[i] * 44.0F); }
    private static Vector3f kneeR(int i) { return new Vector3f(LSX[i] * 114.0F, 210.0F, LSZ[i] * 104.0F); }
    private static Vector3f ankleR(int i) { return new Vector3f(LSX[i] * 122.0F, 22.0F, LSZ[i] * 114.0F); }

    private static void about(PoseStack ps, float px, float py, float pz, org.joml.Quaternionf q) {
        ps.translate(px / 16.0F, py / 16.0F, pz / 16.0F);
        ps.mulPose(q);
        ps.translate(-px / 16.0F, -py / 16.0F, -pz / 16.0F);
    }

    private static Vector3f aimModel(WardenEntity e, float pt, float yawDeg, float yOff) {
        if (e.clientAim == null) return null;
        net.minecraft.world.phys.Vec3 a = e.clientAimO == null ? e.clientAim : e.clientAimO.lerp(e.clientAim, pt);
        double vx = a.x - Mth.lerp(pt, e.xo, e.getX()), vy = a.y - Mth.lerp(pt, e.yo, e.getY()), vz = a.z - Mth.lerp(pt, e.zo, e.getZ());
        float r = yawDeg * Mth.DEG_TO_RAD;
        double f = vx * -Mth.sin(r) + vz * Mth.cos(r), side = vx * -Mth.cos(r) + vz * -Mth.sin(r);
        return new Vector3f((float) (side * 16.0), (float) (vy * 16.0) - yOff, (float) (-f * 16.0));
    }

    private static org.joml.Quaternionf aimAt(Vector3f aim, float px, float py, float pz, float maxYaw, float minPitch, float maxPitch, boolean yaw) {
        float dx = aim.x - px, dy = aim.y - py, dz = aim.z - pz;
        float a = yaw ? Mth.clamp((float) Math.atan2(dx, -dz), -maxYaw * Mth.DEG_TO_RAD, maxYaw * Mth.DEG_TO_RAD) : 0.0F;
        float h = yaw ? (float) Math.sqrt(dx * dx + dz * dz) : Math.max(1.0E-3F, (float) Math.sqrt(dx * dx + dz * dz));
        float el = Mth.clamp((float) Math.atan2(dy, Math.max(1.0E-3F, h)), minPitch * Mth.DEG_TO_RAD, maxPitch * Mth.DEG_TO_RAD);
        if (yaw && minPitch == 0.0F && maxPitch == 0.0F) el = 0.0F;
        Vector3f d = new Vector3f(Mth.sin(a) * Mth.cos(el), Mth.sin(el), -Mth.cos(a) * Mth.cos(el));
        return new org.joml.Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), d);
    }

    static float podOpen(float pod) {
        if (pod <= 0.0F) return 0.0F;
        float last = WardenAttacks.podFire(Balance.WARDEN_ROCKETS - 1);
        return pod < last + 6.0F ? Math.min(1.0F, pod / 6.0F) : Math.max(0.0F, 1.0F - (pod - last - 6.0F) / 8.0F);
    }

    private static org.joml.Quaternionf rx(float deg) { return new org.joml.Quaternionf().rotationX(deg * Mth.DEG_TO_RAD); }
    private static org.joml.Quaternionf ry(float deg) { return new org.joml.Quaternionf().rotationY(deg * Mth.DEG_TO_RAD); }
    private static org.joml.Quaternionf rz(float deg) { return new org.joml.Quaternionf().rotationZ(deg * Mth.DEG_TO_RAD); }

    private void renderMesh(WardenEntity e, float entityYaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel m = MeshModel.named(MESH);
        int ov = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(e.hurtTime > 0));
        int full = LightTexture.FULL_BRIGHT, mode = e.mode(), stage = e.stage();
        float t = e.tickCount + pt;
        boolean awake = mode != WardenEntity.DORMANT, od = stage >= 2, md = stage >= 3, active = mode == WardenEntity.ACTIVE;
        float yaw = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot), walk = Mth.lerp(pt, e.walkDistO, e.walkDist);
        float sink = mode == WardenEntity.DYING ? Math.min(1.0F, (e.clientModeTicks + pt) / 60.0F) * 96.0F : 0.0F;

        float amt = active ? Mth.lerp(pt, e.gaitAmtO, e.gaitAmt) : 0.0F;
        float bob = -amt * 3.0F * Mth.cos(walk / WardenEntity.GAIT_CYCLE * Mth.TWO_PI * 4.0F), lean = 0.0F, nod = 0.0F;
        for (int i = 0; i < 4; i++) {
            if (e.legBroken(i)) continue;
            float lf = WardenEntity.gaitLift(WardenEntity.gaitQ(walk, i)) * amt;
            lean += lf * LSX[i] * 0.6F;
            nod += lf * -LSZ[i] * 0.4F;
        }
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        PoseStack.Pose root = ps.last();

        float[] dr = new float[4];
        for (int i = 0; i < 4; i++) dr[i] = Mth.lerp(pt, e.clientLegDropO[i], e.clientLegDrop[i]) * 16.0F;
        float avg = (dr[0] + dr[1] + dr[2] + dr[3]) / 4.0F, side = ((dr[1] + dr[3]) - (dr[0] + dr[2])) / 4.0F, fwd = ((dr[0] + dr[1]) - (dr[2] + dr[3])) / 4.0F;
        float sx = (float) WardenEntity.LEG_X[1] * 16.0F, sz = (float) WardenEntity.LEG_Z[0] * 16.0F;
        for (int i = 0; i < 4; i++) {
            Vector3f hp = hipR(i);
            float hd = avg + side * hp.x / sx + fwd * -hp.z / sz;
            leg(m, ps, buffers, light, ov, i, walk, e.legBroken(i) ? 0.0F : amt, e.legBroken(i), sink, hd);
        }
        ps.translate(0.0F, (bob - sink - avg) / 16.0F, 0.0F);
        if (side != 0.0F || fwd != 0.0F)
            about(ps, 0.0F, 140.0F, 0.0F, rz((float) Math.toDegrees(Math.asin(Mth.clamp(-side / sx, -1.0F, 1.0F))))
                    .mul(rx((float) Math.toDegrees(Math.asin(Mth.clamp(-fwd / sz, -1.0F, 1.0F))))));
        if (lean != 0.0F || nod != 0.0F) about(ps, 0.0F, 140.0F, 0.0F, rz(lean).mul(rx(nod)));

        Vector3f aim = active && !e.cine() ? aimModel(e, pt, yaw, bob - sink) : null;
        float pod = e.pod() > 0 ? e.pod() + pt : 0.0F, eyeK = Math.min(1.0F, e.eye() / (float) Balance.WARDEN_EYE_CHARGE);
        m.draw("body", ps.last(), buffers, light, ov);
        for (int i = 0; i < 8; i++)
            if (stage == 1 || stage == 2 && e.clientStageTicks < Balance.WARDEN_PLATE_FIRST + Balance.WARDEN_PLATE_GAP * i) m.draw("plate" + i, ps.last(), buffers, light, ov);
        if (od && ((int) (t / 5.0F)) % 2 == 0) m.draw("redlights", ps.last(), buffers, full, ov);
        int coreB = awake ? 10 + (int) (5.0F * (0.5F + 0.5F * Mth.sin(t * (md ? 0.3F : 0.12F)))) : 8;
        m.draw("core", ps.last(), buffers, md ? full : LightTexture.pack(coreB, coreB), ov);

        ps.pushPose();
        float headYaw = Mth.clamp(Mth.wrapDegrees(Mth.rotLerp(pt, e.yHeadRotO, e.yHeadRot) - yaw), -35.0F, 35.0F);
        about(ps, 0.0F, 168.0F, -64.0F, (aim != null ? aimAt(aim, 0.0F, 168.0F, -64.0F, 35.0F, -25.0F, 20.0F, true) : ry(-headYaw)).mul(rx(md ? 8.0F : 0.0F)));
        m.draw("head", ps.last(), buffers, light, ov);
        m.draw(od || eyeK > 0.0F || mode == WardenEntity.DYING ? "eyered" : "eye", ps.last(), buffers, awake ? full : light, ov);
        float lid = od ? 3.0F / 16.0F : 0.0F;
        ps.pushPose(); ps.translate(0.0F, lid, 0.0F); m.draw("lid_t", ps.last(), buffers, light, ov); ps.popPose();
        ps.pushPose(); ps.translate(0.0F, -lid, 0.0F); m.draw("lid_b", ps.last(), buffers, light, ov); ps.popPose();
        float jaw = od ? 22.0F : active ? 6.0F + 6.0F * Mth.sin(t * 0.05F) : 0.0F;
        ps.pushPose(); about(ps, -22.0F, 152.0F, -78.0F, ry(-jaw)); m.draw("mandible_l", ps.last(), buffers, light, ov); ps.popPose();
        ps.pushPose(); about(ps, 22.0F, 152.0F, -78.0F, ry(jaw)); m.draw("mandible_r", ps.last(), buffers, light, ov); ps.popPose();
        ps.popPose();

        ps.pushPose();

        about(ps, 0.0F, 140.0F, -76.0F, aim != null ? aimAt(aim, 0.0F, 140.0F, -76.0F, 70.0F, 0.0F, 0.0F, true) : ry(active ? Mth.sin(t * 0.07F) * 25.0F : 0.0F));
        m.draw("ciws", ps.last(), buffers, light, ov);
        about(ps, 0.0F, 128.0F, -80.0F, aim != null ? aimAt(aim, 0.0F, 128.0F, -80.0F, 0.0F, -55.0F, 20.0F, false) : rx(active ? 6.0F : 0.0F));
        m.draw("ciws_gun", ps.last(), buffers, light, ov);
        about(ps, 0.0F, 128.0F, -96.0F, rz(Mth.lerp(pt, e.barrelO, e.barrel)));
        m.draw("ciws_barrels", ps.last(), buffers, light, ov);
        ps.popPose();

        for (int s = -1; s <= 1; s += 2) {
            String n = s < 0 ? "l" : "r";
            ps.pushPose();

            float open = podOpen(pod), kick = 0.0F;
            for (int k = 0; k < Balance.WARDEN_ROCKETS; k++)
                if (WardenAttacks.podRight(k) == (s > 0) && pod >= WardenAttacks.podFire(k)) kick = Math.max(kick, 1.0F - (pod - WardenAttacks.podFire(k)) / 4.0F);
            ps.translate(0.0F, 0.0F, kick * 6.0F / 16.0F);
            org.joml.Quaternionf podQ = rx(awake ? -8.0F : 0.0F);
            if (aim != null && open > 0.0F) podQ = new org.joml.Quaternionf().slerp(aimAt(aim, s * 62.0F, 190.0F, -6.0F, 0.0F, -10.0F, 35.0F, false), open).mul(podQ);
            about(ps, s * 62.0F, 190.0F, -6.0F, podQ);
            m.draw("pod_" + n, ps.last(), buffers, light, ov);
            about(ps, s * 62.0F, 218.0F, -34.0F, rx(od ? 100.0F : 100.0F * open));
            m.draw("cover_" + n, ps.last(), buffers, light, ov);
            ps.popPose();
            ps.pushPose(); ps.translate(od ? s * 20.0F / 16.0F : 0.0F, 0.0F, 0.0F); m.draw("bay_" + n, ps.last(), buffers, light, ov); ps.popPose();
            float flap = od ? -s * 70.0F : active ? -s * (4.0F + 4.0F * Mth.sin(t * 0.5F + s)) : 0.0F;
            ps.pushPose(); about(ps, s * 18.0F, 226.0F, 70.0F, rz(flap)); m.draw("stack_" + n, ps.last(), buffers, light, ov); ps.popPose();
            ps.pushPose(); about(ps, s * 26.0F, 203.0F, 80.0F, md ? rz(-s * 80.0F) : ry(awake ? s * t * 6.0F : 0.0F)); m.draw("ant_" + n, ps.last(), buffers, light, ov); ps.popPose();
            float door = md ? ease((e.clientStageTicks - 10) / 30.0) : 0.0F;
            ps.pushPose(); ps.translate(s * 22.0F * door / 16.0F, 0.0F, 0.0F); m.draw("door_" + n, ps.last(), buffers, light, ov); ps.popPose();
        }
        float mk = Math.max(0.0F, 1.0F - (e.clientMortarKick + pt) / 6.0F);
        ps.pushPose(); ps.translate(0.0F, -mk * 4.0F / 16.0F, 0.0F); about(ps, -42.0F, 186.0F, 30.0F, rx(awake ? 10.0F + 6.0F * mk : 0.0F)); m.draw("mortar", ps.last(), buffers, light, ov); ps.popPose();
        for (int i = 0; i < 6; i++) {
            float fin = od ? -55.0F : active ? 6.0F * Mth.sin(t * 0.3F + i * 0.8F) : 0.0F;
            ps.pushPose(); about(ps, 0.0F, 204.0F, 46.0F + i * 6.0F, rx(fin)); m.draw("fin" + i, ps.last(), buffers, light, ov); ps.popPose();
        }

        ps.pushPose();
        if (md) { float up = ease((e.clientStageTicks - Balance.WARDEN_CINE_DOME) / 40.0); ps.translate(0.0F, up, 0.0F); about(ps, 0.0F, 196.0F, 118.0F, rx(10.0F * up)); }
        m.draw("rail", ps.last(), buffers, light, ov);
        int lit = (int) (6.0F * Math.min(1.0F, e.rail() / (float) Balance.WARDEN_RAIL_CHARGE));
        if (md) for (int i = 0; i < 6; i++) {
            ps.pushPose();
            about(ps, 0.0F, 204.0F, 16.5F + 16.0F * i, rz(Mth.lerp(pt, e.ringAngO, e.ringAng) * (i % 2 == 0 ? 1.0F : -1.0F)));
            m.draw("ring" + (i + 1), ps.last(), buffers, i < lit ? full : light, ov);
            ps.popPose();
        }
        ps.popPose();
        ps.popPose();
        super.render(e, entityYaw, pt, ps, buffers, light);
    }

    private static void leg(MeshModel m, PoseStack ps, MultiBufferSource buffers, int light, int ov, int i, float walk, float moving, boolean broken, float sink, float drop) {
        Vector3f h = hipR(i).add(0.0F, -sink - drop, 0.0F), k0 = kneeR(i), a0 = ankleR(i);
        float q = WardenEntity.gaitQ(walk, i), off = WardenEntity.gaitOff(q) * moving, lift = WardenEntity.gaitLift(q) * moving;
        float splay = sink * 0.3F;
        Vector3f a = new Vector3f(a0.x + LSX[i] * splay, a0.y + lift * 16.0F, a0.z + LSZ[i] * splay - off * 16.0F);
        float l1 = new Vector3f(k0).sub(hipR(i)).length(), l2 = new Vector3f(a0).sub(k0).length();
        Vector3f k = kneeIK(h, a, l1, l2);
        if (broken) k.y -= 18.0F;
        org.joml.Quaternionf qh = new org.joml.Quaternionf().rotationTo(new Vector3f(k0).sub(hipR(i)), new Vector3f(k).sub(h));
        org.joml.Quaternionf qs = new org.joml.Quaternionf().rotationTo(new Vector3f(a0).sub(k0), new Vector3f(a).sub(k));
        org.joml.Quaternionf qk = new org.joml.Quaternionf(qh).conjugate().mul(qs), qf = new org.joml.Quaternionf(qs).conjugate();
        String n = LEG[i];
        ps.pushPose();
        ps.translate(0.0F, (-sink - drop) / 16.0F, 0.0F);
        about(ps, hipR(i).x, hipR(i).y, hipR(i).z, qh);
        m.draw("hip_" + n, ps.last(), buffers, light, ov);
        m.draw("ram_" + n, ps.last(), buffers, light, ov);
        about(ps, k0.x, k0.y, k0.z, qk);
        m.draw("knee_" + n, ps.last(), buffers, light, ov);
        about(ps, a0.x, a0.y, a0.z, qf);
        m.draw("foot_" + n, ps.last(), buffers, light, ov);
        for (int j = 0; j < 3; j++) m.draw("toe_" + n + "_" + j, ps.last(), buffers, light, ov);
        ps.popPose();
    }

    private static Vector3f kneeIK(Vector3f hip, Vector3f ankle, float l1, float l2) {
        float dx = ankle.x - hip.x, dz = ankle.z - hip.z, dy = ankle.y - hip.y;
        float dh = (float) Math.sqrt(dx * dx + dz * dz);
        float d = Math.min(l1 + l2 - 0.01F, (float) Math.sqrt(dh * dh + dy * dy));
        float ang = (float) Math.acos(Mth.clamp((l1 * l1 + d * d - l2 * l2) / (2.0F * l1 * d), -1.0F, 1.0F));
        float phi = (float) Math.atan2(dy, dh) + ang;
        float hx = dh < 1.0E-4F ? 1.0F : dx / dh, hz = dh < 1.0E-4F ? 0.0F : dz / dh;
        return new Vector3f(hip.x + hx * Mth.cos(phi) * l1, hip.y + Mth.sin(phi) * l1, hip.z + hz * Mth.cos(phi) * l1);
    }

    private static float ease(double x) { float k = (float) Mth.clamp(x, 0.0, 1.0); return k * k * (3.0F - 2.0F * k); }

    private static Vector3f knee(Vector3f hip, Vector3f foot) {
        float dx = foot.x - hip.x, dz = foot.z - hip.z, dy = foot.y - hip.y;
        float dh = (float) Math.sqrt(dx * dx + dz * dz);
        float d = Math.min(L1 + L2 - 0.01F, (float) Math.sqrt(dh * dh + dy * dy));
        float a = (float) Math.acos(Mth.clamp((L1 * L1 + d * d - L2 * L2) / (2.0F * L1 * d), -1.0F, 1.0F));
        float phi = (float) Math.atan2(dy, dh) + a;
        float hx = dh < 1.0E-4F ? 1.0F : dx / dh, hz = dh < 1.0E-4F ? 0.0F : dz / dh;
        return new Vector3f(hip.x + hx * Mth.cos(phi) * L1, hip.y + Mth.sin(phi) * L1, hip.z + hz * Mth.cos(phi) * L1);
    }

    private static void beam(PoseStack.Pose p, VertexConsumer vc, Vector3f a, Vector3f b, float w, int[] col, int light, int ov) {
        Vector3f d = new Vector3f(b).sub(a);
        Vector3f c = new Vector3f(a).add(b).mul(0.5F);
        Vector3f az = new Vector3f(d).mul(0.5F);
        Vector3f ax = new Vector3f(d).cross(0.0F, 1.0F, 0.0F);
        if (ax.lengthSquared() < 1.0E-6F) ax.set(1.0F, 0.0F, 0.0F);
        ax.normalize().mul(w * 0.5F);
        Vector3f ay = new Vector3f(ax).cross(d).normalize().mul(w * 0.5F);
        quadBox(p, vc, c, ax, ay, az, col, light, ov);
    }

    private static void box(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float hx, float hy, float hz, int[] col, int light, int ov) {
        quadBox(p, vc, new Vector3f(x, y, z), new Vector3f(hx, 0, 0), new Vector3f(0, hy, 0), new Vector3f(0, 0, hz), col, light, ov);
    }

    private static void quadBox(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f ax, Vector3f ay, Vector3f az, int[] col, int light, int ov) {
        face(p, vc, c, ax, ay, az, col, light, ov);
        face(p, vc, c, new Vector3f(ax).negate(), ay, new Vector3f(az).negate(), col, light, ov);
        face(p, vc, c, az, ay, new Vector3f(ax).negate(), col, light, ov);
        face(p, vc, c, new Vector3f(az).negate(), ay, ax, col, light, ov);
        face(p, vc, c, ay, az, ax, col, light, ov);
        face(p, vc, c, new Vector3f(ay).negate(), az, new Vector3f(ax).negate(), col, light, ov);
    }

    private static void face(PoseStack.Pose p, VertexConsumer vc, Vector3f c, Vector3f n, Vector3f u, Vector3f v, int[] col, int light, int ov) {
        Vector3f nn = new Vector3f(n).normalize();
        float[][] k = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (float[] s : k) {
            float x = c.x + n.x + u.x * s[0] + v.x * s[1], y = c.y + n.y + u.y * s[0] + v.y * s[1], z = c.z + n.z + u.z * s[0] + v.z * s[1];
            vc.addVertex(p, x, y, z).setColor(col[0], col[1], col[2], 255).setUv((s[0] + 1.0F) * 0.5F, (s[1] + 1.0F) * 0.5F)
                    .setOverlay(ov).setLight(light).setNormal(p, nn.x, nn.y, nn.z);
        }
    }
}
