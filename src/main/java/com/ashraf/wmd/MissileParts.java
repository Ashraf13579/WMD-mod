package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class MissileParts {

    private MissileParts() {}

    private static final float MOTOR_L = Missiles.MOTOR_L, AIR_L = Missiles.AIR_L, GUID_L = Missiles.GUID_L, WAR_L = Missiles.WAR_L;

    private static final Set<String> WARHEADS = Set.of("he", "incendiary", "frag", "decoy", "thermobaric", "cluster", "breacher", "chaff", "interceptor",
            "bunker", "emp", "nuke", "thermo", "rod", "hemp", "seismic");

    private static ResourceLocation rl(String p) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, p); }

    private static final class PartVisual implements GeoAnimatable {
        final String id;
        int paint;
        float alpha = 1.0F;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

        PartVisual(String id) { this.id = id; }

        @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
        @Override public double getTick(Object object) { return 0; }
    }

    private static final class PartModel extends GeoModel<PartVisual> {
        @Override public ResourceLocation getModelResource(PartVisual v) { return rl("geo/missile_part/" + v.id + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(PartVisual v) { return texture(v.paint); }
        @Override public ResourceLocation getAnimationResource(PartVisual v) { return rl("animations/block/launch_pad.animation.json"); }
    }

    private static final class PartRenderer extends GeoObjectRenderer<PartVisual> {
        PartRenderer() { super(new PartModel()); }

        @Override
        public software.bernie.geckolib.util.Color getRenderColor(PartVisual v, float partialTick, int packedLight) {
            return v.alpha >= 0.999F ? software.bernie.geckolib.util.Color.WHITE : software.bernie.geckolib.util.Color.ofRGBA(255, 255, 255, Math.round(v.alpha * 255.0F));
        }

        @Override
        public void preRender(PoseStack poseStack, PartVisual animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                              VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
        }
    }

    private record Entry(PartRenderer renderer, PartVisual visual) {}
    private static final Map<String, Entry> PARTS = new HashMap<>();

    private static ResourceLocation texture(int paint) {
        return rl("textures/missile_part/parts_" + (paint <= 0 ? "bare" : paint >= 4 ? "int" : "t" + Math.min(3, paint)) + ".png");
    }

    public static float length(int calibre) {
        return Missiles.partsLength(calibre);
    }

    public static String motorId(MissileBuild b) {
        return switch (b.kind()) { case 1 -> "stage_core_engine"; case 2 -> "stage_upper_engine"; case 3 -> "stage_booster_nozzle"; case MissileBuild.KIND_CRUISE -> "jet_engine";
            default -> b.hypersonic() ? "motor_hypersonic" : b.divert() ? "motor_divert" : "motor_t" + Mth.clamp(b.motor(), 1, 4); };
    }

    public static String airframeId(MissileBuild b) {
        return switch (b.kind()) { case 1 -> "stage_core_body"; case 2 -> "stage_upper_body"; case 3 -> "stage_booster_body"; default -> "airframe_t" + b.calibre(); };
    }
    public static String guidanceId(MissileBuild b) { return "guidance_t" + Mth.clamp(b.guidance(), 1, 4); }
    public static String warheadId(MissileBuild b) {

        if (Asat.ID.equals(b.warheadType())) return MeshModel.named("part_warhead_asat").has("part") ? "warhead_asat" : "warhead_he";

        if (Cubesat.ID.equals(b.warheadType())) return MeshModel.named("part_warhead_cubesat").has("part") ? "warhead_cubesat" : "warhead_he";

        if ("antimatter".equals(b.warheadType())) return MeshModel.named("part_warhead_antimatter").has("part") ? "warhead_antimatter" : "warhead_thermo";
        if (b.isMirv()) return MeshModel.named("part_warhead_mirv").has("part") ? "warhead_mirv" : "warhead_he";
        String wt = b.warheadType();
        if ("nanite".equals(wt) || "gravity".equals(wt) || "cryo".equals(wt)) return MeshModel.named("part_warhead_" + wt).has("part") ? "warhead_" + wt : "warhead_he";
        return "warhead_" + (WARHEADS.contains(b.warheadType()) ? b.warheadType() : "he");
    }

    public static void render(MissileBuild b, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        render(b, true, ps, buffers, light, partialTick);
    }

    public static int paintOf(MissileBuild b) { return b.isInterceptor() ? 4 : Math.min(3, b.calibre()); }

    public static void render(MissileBuild b, boolean painted, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (b.staged()) { renderStack(b, 0, painted, ps, buffers, light, partialTick); return; }
        if (b.isRv()) {
            boolean have = MeshModel.named("part_warhead_rv").has("part");
            part(have ? "warhead_rv" : "warhead_nuke", 3, 0.0F, 4.8F / 9.0F, 22.4F / 27.0F, ps, buffers, light, partialTick);
            return;
        }
        int cal = b.calibre();
        float cl = (float) BayCalibre.len(cal), cr = (float) BayCalibre.rad(cal);
        int paint = painted ? paintOf(b) : 0;
        boolean stage = b.isStage();
        part(motorId(b), paint, 0.0F, stage ? 1.0F : cr, stage ? 1.0F : cl, ps, buffers, light, partialTick);
        part(airframeId(b), paint, MOTOR_L * cl, 1.0F, 1.0F, ps, buffers, light, partialTick);
        if (b.kind() == 1 || b.kind() == 3) return;
        part(guidanceId(b), paint, (MOTOR_L + AIR_L) * cl, cr, cl, ps, buffers, light, partialTick);
        if (b.uplink()) uplink(paint, (MOTOR_L + AIR_L) * cl, cr, cl, ps, buffers, light, partialTick);
        if (b.camPod()) campod(paint, paint > 0 ? 1.0F : 0.0F, (MOTOR_L + AIR_L) * cl, cr, cl, ps, buffers, light, partialTick);
        if (!b.seeker().isEmpty()) seeker(b.seeker(), paint, paint > 0 ? 1.0F : 0.0F, (MOTOR_L + AIR_L) * cl, cr, cl, ps, buffers, light, partialTick);
        part(warheadId(b), paint, (MOTOR_L + AIR_L + GUID_L) * cl, cr, cl, ps, buffers, light, partialTick);
    }

    private static final float CORE_TOP = Missiles.CORE_TOP, INTER_H = Missiles.INTER_H, UP_SCALE = Missiles.UP_SCALE, UP_AIR = Missiles.UP_AIR, UP_HEAD = Missiles.UP_HEAD;
    private static final float STACK_TOP = Missiles.STACK_TOP;

    public static float stackLength() { return Missiles.stackLength(); }

    public static final float PAYLOAD_H = Missiles.PAYLOAD_H, PAYLOAD_MOUNT = 10.4F;

    public static float stackLength(MissileBuild b) { return Missiles.stackLength(b); }

    public static final float CAPSULE_H = Missiles.CAPSULE_H;

    public static final float CORE_TOP_BLOCKS = CORE_TOP / 16.0F;

    private static final float BOOST_R = 21.6F + 16.2F + 1.5F;
    public static float boosterLength() { return (MOTOR_L * 2.0F + AIR_L * 2.0F + 54.0F) / 16.0F; }

    public static float kindHeight(MissileBuild b) {
        return switch (b.kind()) { case 1 -> CORE_TOP_BLOCKS; case 2 -> (MOTOR_L * 2.5F + AIR_L * 2.5F + GUID_L * 2.5F + WAR_L * 2.5F) / 16.0F; default -> boosterLength(); };
    }

    public static float lengthOf(MissileBuild b) { return Missiles.lengthOf(b); }

    public static void renderStack(MissileBuild b, int phase, boolean painted, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (Satellites.isSatellite(b) || StationKit.isKit(b) || Cargo.isCargo(b)) { renderSatStack(b, phase, painted, 0.0F, 0.0F, 1.0F, ps, buffers, light, partialTick); return; }
        if (Crew.isCrew(b)) { renderCrewStack(b, phase, painted, phase == 0, ps, buffers, light, partialTick); return; }
        int paint = painted ? paintOf(b) : 0;
        if (phase <= 1) renderCore(b, paint, ps, buffers, light, partialTick);
        part("interstage", paint, CORE_TOP, 1.0F, 1.0F, ps, buffers, light, partialTick);
        float y = CORE_TOP + INTER_H;
        part("stage_upper_body", paint, y, UP_SCALE, UP_SCALE, ps, buffers, light, partialTick);
        y += UP_AIR;
        part(guidanceId(b), paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (b.uplink()) uplink(paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (b.camPod()) campod(paint, paint > 0 ? 1.0F : 0.0F, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (!b.seeker().isEmpty()) seeker(b.seeker(), paint, paint > 0 ? 1.0F : 0.0F, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        y += GUID_L * UP_HEAD;
        part(warheadId(b), paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (phase == 0) for (int i = 0; i < b.boosters(); i++) {
            double a = Math.toRadians(boosterAngle(b.boosters(), i));
            ps.pushPose();
            ps.translate(Math.cos(a) * BOOST_R / 16.0, 0.0, Math.sin(a) * BOOST_R / 16.0);
            renderBooster(paint, ps, buffers, light, partialTick);
            ps.popPose();
        }
    }

    public static final float T_CORE0 = 0.06F, T_CORE1 = 0.30F, T_BOOST0 = 0.36F, T_BOOST_LEN = 0.10F, T_UP0 = 0.64F, T_UP1 = 0.88F;

    private static float ease(float x) { x = Mth.clamp(x, 0.0F, 1.0F); return x * x * (3.0F - 2.0F * x); }

    public static float coreLift(float p) { return p < T_CORE0 ? -1.0F : (1.0F - ease((p - T_CORE0) / (T_CORE1 - T_CORE0))) * 70.0F; }

    public static float upperLift(float p) { return p < T_UP0 ? -1.0F : (1.0F - ease((p - T_UP0) / (T_UP1 - T_UP0))) * 80.0F; }

    public static float boosterAway(int i, float p) { float s = T_BOOST0 + T_BOOST_LEN * i; return p < s ? -1.0F : 1.0F - ease((p - s) / T_BOOST_LEN); }

    public static void renderStackBuilt(MissileBuild b, PadRig.State st, boolean painted, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        int paint = painted ? paintOf(b) : 0;
        ps.pushPose();
        ps.translate(st.px[0] / 16.0, (st.py[0] - PadRig.MOUNT) / 16.0, (st.pz[0] - PadRig.CZ) / 16.0);
        renderCore(b, paint, ps, buffers, light, partialTick);
        ps.popPose();
        for (int i = 0; i < b.boosters() && i < 2; i++) {
            double a = Math.toRadians(boosterAngle(b.boosters(), i));
            double tx = Math.cos(a) * BOOST_R / 16.0, tz = Math.sin(a) * BOOST_R / 16.0;
            int k = 1 + i;
            if (Math.sin(a) < 0) k = 2; else k = 1;
            ps.pushPose();
            ps.translate(tx + (st.px[k] - PadRig.TX[k]) / 16.0, (st.py[k] - PadRig.MOUNT) / 16.0, tz + (st.pz[k] - PadRig.TZ[k]) / 16.0);
            renderBooster(paint, ps, buffers, light, partialTick);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(st.px[3] / 16.0, (st.py[3] - (PadRig.MOUNT + 300.0)) / 16.0, (st.pz[3] - PadRig.CZ) / 16.0);
        if (Crew.isCrew(b)) {
            ps.translate(0.0F, CORE_TOP / 16.0F, 0.0F);
            renderCapsule(true, ps, buffers, light);
            ps.popPose();
            return;
        }
        if (Satellites.isSatellite(b) || StationKit.isKit(b) || Cargo.isCargo(b)) {
            ps.translate(0.0F, CORE_TOP / 16.0F, 0.0F);
            renderPayload(StationKit.isKit(b) || Cargo.isCargo(b) ? "nav" : Satellites.typeOf(b), 0.0F, 0.0F, 1.0F, ps, buffers, light);
            ps.popPose();
            return;
        }
        part("interstage", paint, CORE_TOP, 1.0F, 1.0F, ps, buffers, light, partialTick);
        float y = CORE_TOP + INTER_H;
        part("stage_upper_body", paint, y, UP_SCALE, UP_SCALE, ps, buffers, light, partialTick);
        y += UP_AIR;
        part(guidanceId(b), paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (b.uplink()) uplink(paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (b.camPod()) campod(paint, paint > 0 ? 1.0F : 0.0F, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        if (!b.seeker().isEmpty()) seeker(b.seeker(), paint, paint > 0 ? 1.0F : 0.0F, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        y += GUID_L * UP_HEAD;
        part(warheadId(b), paint, y, UP_HEAD, UP_HEAD, ps, buffers, light, partialTick);
        ps.popPose();
    }

    public static void renderSatStack(MissileBuild b, int phase, boolean painted, float open, float deploy, float scale, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        int paint = painted ? paintOf(b) : 0;
        if (phase <= 1) renderCore(b, paint, ps, buffers, light, partialTick);
        if (phase == 0) for (int i = 0; i < b.boosters(); i++) {
            double a = Math.toRadians(boosterAngle(b.boosters(), i));
            ps.pushPose();
            ps.translate(Math.cos(a) * BOOST_R / 16.0, 0.0, Math.sin(a) * BOOST_R / 16.0);
            renderBooster(paint, ps, buffers, light, partialTick);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0.0F, CORE_TOP / 16.0F, 0.0F);
        renderPayload(StationKit.isKit(b) || Cargo.isCargo(b) ? "nav" : Satellites.typeOf(b), open, deploy, scale, ps, buffers, light);
        ps.popPose();
    }

    public static void renderCrewStack(MissileBuild b, int phase, boolean painted, boolean tower, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        int paint = painted ? paintOf(b) : 0;
        if (phase <= 1) renderCore(b, paint, ps, buffers, light, partialTick);
        if (phase == 0) for (int i = 0; i < b.boosters(); i++) {
            double a = Math.toRadians(boosterAngle(b.boosters(), i));
            ps.pushPose();
            ps.translate(Math.cos(a) * BOOST_R / 16.0, 0.0, Math.sin(a) * BOOST_R / 16.0);
            renderBooster(paint, ps, buffers, light, partialTick);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(0.0F, CORE_TOP / 16.0F, 0.0F);
        renderCapsule(tower, phase <= 1, ps, buffers, light);
        ps.popPose();
    }

    public static float hatchOpen;

    private static final Vector3f HINGE, HINGE_AXIS;
    private static final float HATCH_SIGN;
    static {
        double rc = PadCrew.rCone(PadCrew.HATCH_Y), ah = Math.toRadians(PadCrew.HATCH_A - Math.toDegrees(6.0 / rc)), r = rc + 0.2;
        HINGE = new Vector3f((float) (r * Math.cos(ah)), (float) PadCrew.HATCH_Y, (float) (r * Math.sin(ah)));
        double dr = (9.5 - 22.0) / 31.0, L = Math.hypot(dr, 1.0);
        HINGE_AXIS = new Vector3f((float) (Math.cos(ah) * dr / L), (float) (1.0 / L), (float) (Math.sin(ah) * dr / L)).normalize();
        double a = Math.toRadians(PadCrew.HATCH_A), rd = rc + 0.8;
        Vector3f c = new Vector3f((float) (rd * Math.cos(a)), (float) PadCrew.HATCH_Y, (float) (rd * Math.sin(a))).sub(HINGE);
        new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(60.0), HINGE_AXIS).transform(c);
        c.add(HINGE);
        HATCH_SIGN = Math.hypot(c.x, c.z) > rd ? 1.0F : -1.0F;
    }

    public static void renderCapsule(boolean tower, PoseStack ps, MultiBufferSource buffers, int light) { renderCapsule(tower, tower, ps, buffers, light); }

    public static void renderCapsule(boolean tower, boolean ring, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("crew_capsule");
        int ov = OverlayTexture.NO_OVERLAY;
        float open = hatchOpen;
        hatchOpen = 0.0F;
        if (!mesh.has("capsule")) { part("interstage", 3, 0.0F, 1.0F, 1.0F, ps, buffers, light, 0.0F); return; }
        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) PadCrew.CAP_YAW));
        mesh.draw("capsule", ps.last(), buffers, light, ov);
        if (ring && mesh.has("ring")) mesh.draw("ring", ps.last(), buffers, light, ov);
        if (mesh.has("hatch")) {
            ps.pushPose();
            if (open > 0.001F) {
                float k = 1.0F / 16.0F;
                ps.translate(HINGE.x * k, HINGE.y * k, HINGE.z * k);
                ps.mulPose(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(105.0 * open) * HATCH_SIGN, HINGE_AXIS));
                ps.translate(-HINGE.x * k, -HINGE.y * k, -HINGE.z * k);
            }
            mesh.draw("hatch", ps.last(), buffers, light, ov);
            ps.popPose();
        }
        if (tower && mesh.has("tower")) mesh.draw("tower", ps.last(), buffers, light, ov);
        ps.popPose();
    }

    public static final float FAIRING_OPEN_DEG = 110.0F;

    public static void renderPayload(String type, float open, float deploy, float scale, PoseStack ps, MultiBufferSource buffers, int light) {
        MeshModel mesh = MeshModel.named("payload_unit");
        int ov = OverlayTexture.NO_OVERLAY;
        if (scale <= 0.01F) return;
        ps.pushPose();
        if (scale < 1.0F) {
            ps.translate(0.0F, PAYLOAD_MOUNT / 16.0F, 0.0F);
            ps.scale(scale, scale, scale);
            ps.translate(0.0F, -PAYLOAD_MOUNT / 16.0F, 0.0F);
        }
        if (mesh.has("adapter")) {
            mesh.draw("adapter", ps.last(), buffers, light, ov);
            if (open < 1.0F) {
                float a = FAIRING_OPEN_DEG * Mth.clamp(open, 0.0F, 1.0F);
                SatelliteMesh.drawBone(mesh, "payload_unit", "fair_r", new float[] { 21.8F, 6.2F, 0.0F }, 0.0F, 0.0F, -a, ps, buffers, light, ov);
                SatelliteMesh.drawBone(mesh, "payload_unit", "fair_l", new float[] { -21.8F, 6.2F, 0.0F }, 0.0F, 0.0F, a, ps, buffers, light, ov);
            }
        } else {
            part("interstage", 3, 0.0F, 1.0F, 1.0F, ps, buffers, light, 0.0F);
        }
        if (open > 0.0F) {
            ps.pushPose();
            ps.translate(0.0F, PAYLOAD_MOUNT / 16.0F, 0.0F);
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            double tick = mc.level != null ? mc.level.getGameTime() : 0;
            SatelliteMesh.draw(type, deploy, deploy > 0.5F && SatelliteMesh.blink(tick, 0L), ps, buffers, light, ov);
            ps.popPose();
        }
        ps.popPose();
    }

    public static final float ROD_LEN = 30.0F;
    public static void renderRod(PoseStack ps, MultiBufferSource buffers, int light, float partialTick) { part("rod_spear", 0, 0.0F, 1.0F, 1.0F, ps, buffers, light, partialTick); }

    public static void renderShell(int paint, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) { part("stage_upper_body", paint, 0.0F, UP_SCALE, UP_SCALE, ps, buffers, light, partialTick); part("interstage", paint, UP_AIR, 1.0F, 1.0F, ps, buffers, light, partialTick); }

    public static float boosterAngle(int n, int i) {
        return switch (n) { case 1 -> 0.0F; case 2 -> 90.0F + 180.0F * i; case 3 -> 120.0F * i; default -> 45.0F + 90.0F * i; };
    }

    public static void renderCore(MissileBuild b, int paint, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        part("stage_core_engine", paint, 0.0F, 1.0F, 1.0F, ps, buffers, light, partialTick);
        part("stage_core_body", paint, MOTOR_L * 2.5F, 1.0F, 1.0F, ps, buffers, light, partialTick);
    }

    public static void renderBooster(int paint, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        part("stage_booster_nozzle", paint, 0.0F, 1.0F, 1.0F, ps, buffers, light, partialTick);
        part("stage_booster_body", paint, MOTOR_L * 2.0F, 1.0F, 1.0F, ps, buffers, light, partialTick);
    }

    public static final float[] BASE = { MOTOR_L, 0.0F, MOTOR_L + AIR_L, MOTOR_L + AIR_L + GUID_L };

    public static void renderPart(MissileBuild b, int slot, boolean painted, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        renderPart(b, slot, painted ? 1.0F : 0.0F, ps, buffers, light, partialTick);
    }

    public static void renderPart(MissileBuild b, int slot, float coverage, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        int cal = b.calibre();
        float cl = (float) BayCalibre.len(cal), cr = (float) BayCalibre.rad(cal);
        switch (slot) {
            case 0 -> part(airframeId(b), paintOf(b), coverage, 0.0F, 1.0F, 1.0F, ps, buffers, light, partialTick);
            case 1 -> part(motorId(b), paintOf(b), coverage, 0.0F, b.isStage() ? 1.0F : cr, b.isStage() ? 1.0F : cl, ps, buffers, light, partialTick);
            case 2 -> {
                part(guidanceId(b), paintOf(b), coverage, 0.0F, cr, cl, ps, buffers, light, partialTick);
                if (b.uplink()) uplink(paintOf(b), coverage, 0.0F, cr, cl, ps, buffers, light, partialTick);
                if (b.camPod()) campod(paintOf(b), coverage, 0.0F, cr, cl, ps, buffers, light, partialTick);
                if (!b.seeker().isEmpty()) seeker(b.seeker(), paintOf(b), coverage, 0.0F, cr, cl, ps, buffers, light, partialTick);
            }
            default -> part(warheadId(b), paintOf(b), coverage, 0.0F, cr, cl, ps, buffers, light, partialTick);
        }
    }

    private static void uplink(int paint, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        uplink(paint, paint > 0 ? 1.0F : 0.0F, y, r, l, ps, buffers, light, partialTick);
    }

    private static void uplink(int paint, float coverage, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (MeshModel.named("part_uplink").has("part")) part("uplink", paint, coverage, y, r, l, ps, buffers, light, partialTick);
    }

    private static void campod(int paint, float coverage, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (MeshModel.named("part_campod").has("part")) part("campod", paint, coverage, y, r, l, ps, buffers, light, partialTick);
    }

    private static void seeker(String kind, int paint, float coverage, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (MeshModel.named("part_seeker_" + kind).has("part")) part("seeker_" + kind, paint, coverage, y, r, l, ps, buffers, light, partialTick);
    }

    private static void part(String id, int paint, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        part(id, paint, paint > 0 ? 1.0F : 0.0F, y, r, l, ps, buffers, light, partialTick);
    }

    private static void part(String id, int paint, float coverage, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (paint <= 0 || coverage <= 0.004F) draw(id, 0, 1.0F, y, r, l, ps, buffers, light, partialTick);
        else if (coverage >= 0.996F) draw(id, paint, 1.0F, y, r, l, ps, buffers, light, partialTick);
        else {
            draw(id, 0, 1.0F, y, r, l, ps, buffers, light, partialTick);
            draw(id, paint, coverage, y, r * 1.004F, l, ps, buffers, light, partialTick);
        }
    }

    private static final int[][] TINT = { { 168, 174, 176 }, { 72, 89, 56 }, { 95, 100, 106 }, { 41, 42, 45 }, { 255, 255, 255 } };

    static int[] tint(int paint) { return TINT[paint <= 0 ? 0 : Math.min(paint, 4)]; }

    private static void drawMesh(MeshModel m, int paint, float alpha, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light) {
        int[] t = TINT[paint <= 0 ? 0 : Math.min(paint, 4)];
        ps.pushPose();
        ps.translate(0.0F, y / 16.0F, 0.0F);
        ps.scale(r, l, r);
        if (alpha >= 0.999F) m.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
        else m.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], Math.round(alpha * 255.0F), true);
        ps.popPose();
    }

    static float asatFinOpen;

    private static void asatFins(MeshModel m, int paint, float alpha, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light) {
        int[] t = TINT[paint <= 0 ? 0 : Math.min(paint, 4)];
        float open = Mth.clamp(asatFinOpen, 0.0F, 1.0F) * AsatSpec.FIN_OPEN_DEG;
        for (int i = 0; i < 4; i++) {
            String bone = "fin" + i;
            if (!m.has(bone)) continue;
            ps.pushPose();
            ps.translate(0.0F, y / 16.0F, 0.0F);
            ps.scale(r, l, r);
            if (open != 0.0F) {
                float a = AsatSpec.FIN_AXIS_DEG0 + 90.0F * i;
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(a));
                ps.translate(0.0F, AsatSpec.FIN_HINGE_Y / 16.0F, AsatSpec.FIN_HINGE_R / 16.0F);
                ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(open * AsatSpec.FIN_SIGN));
                ps.translate(0.0F, -AsatSpec.FIN_HINGE_Y / 16.0F, -AsatSpec.FIN_HINGE_R / 16.0F);
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-a));
            }
            if (alpha >= 0.999F) m.drawTinted(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
            else m.drawTinted(bone, ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], Math.round(alpha * 255.0F), true);
            ps.popPose();
        }
    }

    private static void draw(String id, int paint, float alpha, float y, float r, float l, PoseStack ps, MultiBufferSource buffers, int light, float partialTick) {
        if (id.equals("warhead_antimatter") && !MeshModel.named("part_warhead_antimatter").has("part")) id = "warhead_thermo";
        MeshModel mesh = MeshModel.named("part_" + id);
        if (mesh.has("part")) {
            drawMesh(mesh, paint, alpha, y, r, l, ps, buffers, light);
            if (id.equals("warhead_asat")) asatFins(mesh, paint, alpha, y, r, l, ps, buffers, light);
            return;
        }
        Entry e = PARTS.computeIfAbsent(id, k -> new Entry(new PartRenderer(), new PartVisual(k)));
        e.visual.paint = paint;
        e.visual.alpha = alpha;
        ps.pushPose();
        ps.translate(0.0F, y / 16.0F, 0.0F);
        ps.scale(r, l, r);
        RenderType type = alpha < 0.999F ? RenderType.entityTranslucent(texture(paint)) : e.renderer.getRenderType(e.visual, texture(paint), buffers, partialTick);
        e.renderer.render(ps, e.visual, buffers, type, buffers.getBuffer(type), light, partialTick);
        ps.popPose();
    }

    private static float[] lengths(int cal) {
        float cl = (float) BayCalibre.len(cal);
        return new float[] { AIR_L, MOTOR_L * cl, GUID_L * cl, WAR_L * cl };
    }

    public static float warheadLengthPx(int cal) { return WAR_L * (float) BayCalibre.len(cal); }

    public static float coverage(int cal, int slot, float progress) {
        float[] len = lengths(cal);
        float total = len[0] + len[1] + len[2] + len[3];
        float start = switch (slot) { case 3 -> 0.0F; case 2 -> len[3]; case 0 -> len[3] + len[2]; default -> len[3] + len[2] + len[0]; };
        return Mth.clamp((progress * total - start) / len[slot], 0.0F, 1.0F);
    }
}
