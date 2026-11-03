package com.ashraf.wmd;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class RefineryUnitRenderer<T extends ProcessUnitBlockEntity> extends GeoBlockRenderer<T> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static class UnitModel<T extends ProcessUnitBlockEntity> extends GeoModel<T> {
        private final String name;
        private final int up;
        UnitModel(String name, int up) { this.name = name; this.up = up; }
        @Override public ResourceLocation getModelResource(T be) { return rl("geo/block/" + name + ".geo.json"); }
        @Override public ResourceLocation getTextureResource(T be) { return rl("textures/block/" + name + ".png"); }
        @Override public ResourceLocation getAnimationResource(T be) { return rl("animations/block/" + name + ".animation.json"); }

        @Override
        public void setCustomAnimations(T be, long instanceId, AnimationState<T> state) {
            float a = be.spinAngle(state.getPartialTick());
            getBone("shaft").ifPresent(b -> b.setRotX(a));

            float needle = (float) Math.toRadians(-135.0 + 270.0 * be.flowFraction());

            double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + state.getPartialTick();
            float run = be.isRunning() ? 1.0F : 0.0F;
            float shake = run * 0.015F * (float) Math.sin(tt * 3.7);
            getBone("needle").ifPresent(b -> b.setRotZ(needle + shake));
            getBone("fan").ifPresent(b -> b.setRotX(a * 0.6F));

            final boolean acrossZ = name.equals("rolling_mill");
            getBone("roll0").ifPresent(b -> { if (acrossZ) b.setRotZ(a * 1.3F); else b.setRotX(a * 1.3F); });
            getBone("roll1").ifPresent(b -> { if (acrossZ) b.setRotZ(-a * 1.3F); else b.setRotX(-a * 1.3F); });

            for (int k = 0; k < 3; k++) {
                final float ph = k * 2.1F;
                getBone("elec" + k).ifPresent(b -> b.setPosY(run * (-8.0F + 2.2F * (float) Math.sin(tt * 0.31 + ph) + 1.0F * (float) Math.sin(tt * 1.7 + ph * 2.0))));
            }

            for (int k = 0; k < 6; k++) {
                final float ph = k * 1.1F;
                getBone("anode" + k).ifPresent(b -> b.setPosY(run * (-0.5F + 1.6F * (float) Math.sin(tt * 0.16 + ph) + 0.5F * (float) Math.sin(tt * 0.9 + ph * 2.0))));
            }

            getBone("agit").ifPresent(b -> b.setRotY(a * 0.8F));

            getBone("rotor").ifPresent(b -> b.setRotY(a * 4.0F));

            final boolean hotCell = name.equals("reprocessing_plant");
            getBone("arm").ifPresent(b -> b.setRotY(run * (hotCell ? 0.055F : 0.35F) * (float) Math.sin(tt * 0.05)));
            getBone("motor").ifPresent(b -> { b.setPosY(run * 0.12F * (float) Math.sin(tt * 2.1)); b.setPosZ(run * 0.08F * (float) Math.cos(tt * 1.7)); });

            final float pulse = 0.78F + 0.22F * (float) Math.sin(tt * 0.35);
            for (String ln : new String[] { "lamp", "lamp1", "lamp2", "lamp3", "lamp4" })
                getBone(ln).ifPresent(b -> b.setHidden(!be.isRunning() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)));
            getBone("beacon").ifPresent(b -> b.setHidden(!be.isRunning() || ((long) tt) % 40 > 20));            for (String gn : new String[] { "glow", "glow1", "glow2" }) getBone(gn).ifPresent(b -> b.setHidden(!be.isRunning() || ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5)));
            if (name.equals("rolling_mill")) millStock(be, tt);
            getBone("pulley").ifPresent(b -> b.setRotX(a * 0.8F));

            getBone("hshaft").ifPresent(b -> b.setRotZ(a));
            getBone("hpulley").ifPresent(b -> b.setRotZ(a * 2.5F));

            final float hs = name.equals("hydrogen_furnace") ? -1.0F : 1.0F;
            getBone("hpiston").ifPresent(b -> b.setPosX(hs * 3.0F * (1.0F - (float) Math.cos(a))));

            final float amp = run * (0.35F + 0.65F * be.flowFraction());
            final float sway = Math.min(up, 12) * 0.00005F;
            getBone("base").ifPresent(b -> { b.setPosX(amp * 0.05F * (float) Math.sin(tt * 2.9)); b.setPosZ(amp * 0.05F * (float) Math.cos(tt * 2.3));
                b.setRotZ(amp * sway * (float) Math.sin(tt * 1.3)); b.setRotX(amp * sway * (float) Math.cos(tt * 1.1)); });
            getBone("level").ifPresent(b -> b.setScaleY(Math.max(be.levelShown(), 0.001F)));

            if (be instanceof MiningRigBlockEntity) {
                final float feed = -40.0F * Math.max(0.0F, be.guiProgress() / 1000.0F);
                getBone("table").ifPresent(b -> b.setRotY(a * 0.7F));
                getBone("kelly").ifPresent(b -> { b.setRotY(a * 0.7F); b.setPosY(feed); });
                getBone("hook").ifPresent(b -> b.setPosY(feed));
            }

            if (be instanceof WaterIntakeBlockEntity w) {
                float px = w.extend01() * w.reachPx();
                final float s1 = Math.min(px, 26.0F), s2 = Math.max(0.0F, Math.min(px - 26.0F, 26.0F));
                final float surface = Math.max(0.0F, px - 6.0F);
                getBone("snork1").ifPresent(b -> b.setPosY(-s1));
                getBone("snork2").ifPresent(b -> b.setPosY(-s2));
                getBone("float").ifPresent(b -> { b.setPosY(-surface); b.setHidden(px < 4.0F); });
            }

            final float crank = 3.0F, rodLen = 9.0F;
            final float sn = (float) Math.sin(a), cs = (float) Math.cos(a);
            final float slide = crank + rodLen - crank * cs - (float) Math.sqrt(rodLen * rodLen - crank * crank * sn * sn);
            final float tilt = (float) -Math.asin(crank * sn / rodLen);
            getBone("piston").ifPresent(b -> b.setPosZ(slide));

            getBone("vpiston").ifPresent(b -> b.setPosY(name.equals("reprocessing_plant") ? -slide * 4.0F / 6.0F : -slide));
            getBone("vrod").ifPresent(b -> { b.setPosY(-slide); b.setRotX(tilt); });
            getBone("rod").ifPresent(b -> { b.setPosZ(slide); b.setRotX(tilt); });

            for (int i = 0; i < 4; i++) {
                final float ph = ((a * 0.05F + i * 0.25F) % 1.0F + 1.0F) % 1.0F;
                getBone("cat" + i).ifPresent(b -> { b.setHidden(!be.isRunning()); b.setPosY(ph * 80.0F); });
            }

            for (int i = 0; i < 4; i++) {
                final float ph2 = ((a * 0.05F + i * 0.25F) % 1.0F + 1.0F) % 1.0F;
                getBone("riser" + i).ifPresent(b -> { b.setHidden(!be.isRunning()); b.setPosY(ph2 * 64.0F); });
                getBone("drop" + i).ifPresent(b -> { b.setHidden(!be.isRunning()); b.setPosY(-ph2 * 48.0F); });
            }
            for (int i = 0; i < 2; i++) {
                final float flick = 0.92F + 0.08F * (float) Math.sin(tt * (3.9 + 3.0 * i) + i * 2.0);
                getBone("fire" + i).ifPresent(b -> { b.setHidden(!be.isRunning()); b.setScaleX(flick); b.setScaleY(flick); });
            }

            if (be instanceof BlastFurnaceBlockEntity) {
                final double loop = be.isRunning() ? (tt % BlastFurnaceBlockEntity.SKIP_LOOP) / BlastFurnaceBlockEntity.SKIP_LOOP : -1.0;
                final float lift = loop < 0.0 ? 0.0F : loop < 0.35 ? 22.0F * smooth((float) (loop / 0.35)) : loop < 0.5 ? 22.0F
                        : loop < 0.85 ? 22.0F * (1.0F - smooth((float) ((loop - 0.5) / 0.35))) : 0.0F;
                getBone("skip").ifPresent(b -> b.setPosY(lift));
                final boolean pour = loop >= BlastFurnaceBlockEntity.POUR_FROM && loop < BlastFurnaceBlockEntity.POUR_TO
                        && !ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
                getBone("pour").ifPresent(b -> b.setHidden(!pour));
                final boolean blow = be.isRunning() && be.flowFraction() > 0.6F;
                getBone("fan").ifPresent(b -> b.setRotX(blow ? a * 3.0F : 0.0F));
            }

            final boolean on = be.isRunning();
            if (be instanceof CokeOvenBlockEntity) {

                final double p = on ? (tt % Balance.CO_CYCLE_TICKS) / Balance.CO_CYCLE_TICKS : 0.0;
                final float door = p < CokeOvenBlockEntity.DOOR_FROM ? 0.0F : p < 0.85 ? 7.0F * smooth((float) ((p - CokeOvenBlockEntity.DOOR_FROM) / 0.05))
                        : p < 0.95 ? 7.0F : 7.0F * (1.0F - smooth((float) ((p - 0.95) / 0.05)));
                final float cake = p < 0.85 ? 0.0F : p < 0.92 ? -2.6F * smooth((float) ((p - 0.85) / 0.07)) : p < 0.95 ? -2.6F : 0.0F;
                getBone("door").ifPresent(b -> b.setPosY(door));
                getBone("cake").ifPresent(b -> b.setPosZ(cake));
            }
            if (be instanceof StarterGeneratorBlockEntity) {

                final float t = a * 0.25F, cr = 2.5F, rl = 8.0F;
                final float st = (float) Math.sin(t), ct = (float) Math.cos(t);
                final float y = cr * ct + (float) Math.sqrt(rl * rl - cr * cr * st * st) - (cr + rl);
                getBone("flywheel").ifPresent(b -> b.setRotZ(-t));
                getBone("xhead").ifPresent(b -> b.setPosY(y));
                getBone("conrod").ifPresent(b -> { b.setPosY(y); b.setRotZ((float) Math.asin(cr * st / rl)); });
                getBone("gov").ifPresent(b -> b.setRotY(a * 0.6F));
            }
            if (be instanceof StampMillBlockEntity) {

                final int loop = StampMillBlockEntity.CAM_LOOP;
                final float cam = on ? (float) ((tt % loop) / loop * Math.PI * 2.0) : 0.0F;
                getBone("cam").ifPresent(b -> b.setRotX(cam));
                getBone("mpulley").ifPresent(b -> b.setRotX(cam * 2.67F));
                for (int k = 0; k < 3; k++) {
                    final double p = on ? ((tt + k * loop / 3.0) % loop) / loop : 1.0;
                    final float lift = p < 0.55 ? 6.0F * smooth((float) (p / 0.55)) : p < 0.68 ? 6.0F * (1.0F - (float) Math.pow((p - 0.55) / 0.13, 2.0)) : 0.0F;
                    getBone("stamp" + k).ifPresent(b -> b.setPosY(lift));
                }
            }
            if (be instanceof PowderMillBlockEntity) {

                final float orbit = a * 0.12F;
                getBone("runner").ifPresent(b -> b.setRotY(orbit));
                getBone("wheel0").ifPresent(b -> b.setRotX(-orbit * 1.2F));
                getBone("wheel1").ifPresent(b -> b.setRotX(orbit * 1.2F));
                getBone("pinion").ifPresent(b -> b.setRotX(a * 0.6F));
            }

            if (be instanceof BatteryTierBlockEntity bt) {
                final int segs = bt.segments(), lit = bt.shownFill() <= 0.001F ? 0 : Math.max(1, Math.round(bt.shownFill() * segs));
                final boolean darkHere = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
                for (int k = 0; k < segs; k++) { final int kk = k; getBone("chg" + k).ifPresent(b -> b.setHidden(kk >= lit || darkHere)); }
                getBone("fan0").ifPresent(b -> b.setRotY(a * 0.8F));
                getBone("fan1").ifPresent(b -> b.setRotY(-a * 0.7F));
            }

            if (be instanceof SentryTurretBlockEntity st) {
                final float pt = state.getPartialTick();
                getBone("yaw").ifPresent(b -> b.setRotY(st.yaw(pt) * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("pitch").ifPresent(b -> b.setRotX(st.pitch(pt) * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("flash").ifPresent(b -> b.setHidden(!st.firingShown() || ((long) tt) % 2 != 0));
            }

            if (be instanceof CctvCameraBlockEntity cc) {
                final float pt = state.getPartialTick();
                getBone("pan").ifPresent(b -> b.setRotY(cc.shownPan(pt) * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("lamp").ifPresent(b -> b.setHidden(!cc.isRunning() || (!cc.viewedShown() && ((long) tt) % 40 > 20)));
            }
            if (be instanceof PerimeterSensorBlockEntity ps) {
                getBone("ledok").ifPresent(b -> b.setHidden(!ps.isRunning() || ps.trippedShown()));
                getBone("ledalarm").ifPresent(b -> b.setHidden(!ps.trippedShown() || ((long) tt) % 8 > 4));
            }
            if (be instanceof FloodlightBlockEntity fl) {
                final float pt = state.getPartialTick();
                getBone("pan").ifPresent(b -> b.setRotY(fl.yaw(pt) * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("tilt").ifPresent(b -> b.setRotX(fl.pitch(pt) * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("lens").ifPresent(b -> b.setHidden(!fl.onShown()));
            }

            if (be instanceof PortableJammerBlockEntity jm && be.getLevel() != null) {
                final float d = jm.deployed(be.getLevel(), state.getPartialTick());
                final float lid = Math.min(1.0F, d * 2.0F), ant = Math.max(0.0F, d * 2.0F - 1.0F);
                getBone("lid").ifPresent(b -> b.setRotX((1.0F - lid) * 1.75F));
                for (int k = 0; k < 3; k++) getBone("ant" + k).ifPresent(b -> b.setRotX((1.0F - ant) * -1.5708F));
                getBone("lamp").ifPresent(b -> b.setHidden(!jm.jamming() || ((long) tt) % 16 > 8));
            }
            if (be instanceof SeismicScannerBlockEntity sz && be.getLevel() != null) {
                final float lift = sz.lift(be.getLevel(), state.getPartialTick());
                getBone("weight").ifPresent(b -> b.setPosY(lift * 18.0F));
                getBone("pulley").ifPresent(b -> b.setRotZ(lift * 18.0F / 1.2F));
                getBone("lamp").ifPresent(b -> b.setHidden(!sz.sweeping(be.getLevel()) || ((long) tt) % 10 > 5));
            }

            if (be instanceof SolarPanelBlockEntity sp && be.getLevel() != null) {
                final float tiltR = SolarPanelBlockEntity.tiltDeg(be.getLevel(), state.getPartialTick()) * net.minecraft.util.Mth.DEG_TO_RAD;
                getBone("tilt").ifPresent(b -> b.setRotZ(tiltR));
                getBone("capN").ifPresent(b -> b.setHidden(!sp.showsCapN()));
                getBone("capS").ifPresent(b -> b.setHidden(!sp.showsCapS()));
                getBone("socket").ifPresent(b -> b.setHidden(!sp.showsSocket()));
            }

            if (be instanceof LavaGeneratorBlockEntity lg) {
                final int h = be.isRunning() ? lg.heatShown() : 0;
                getBone("heat").ifPresent(b -> b.setHidden(h == 0));
                getBone("heat2").ifPresent(b -> b.setHidden(h < 2 || ((long) tt) % 16 > 11));
                getBone("topfan").ifPresent(b -> b.setRotY(a * (h == 2 ? 1.2F : 0.5F)));
            }
            if (be instanceof SteamEngineBlockEntity se) {
                final float t = se.crankAngle(state.getPartialTick()), cr = 4.0F, rl = 14.0F;
                final float st = (float) Math.sin(t), ct = (float) Math.cos(t);
                final float dx = 18.0F - cr * ct - (float) Math.sqrt(rl * rl - cr * cr * st * st);
                getBone("flywheel").ifPresent(b -> b.setRotZ(-t));
                getBone("xhead").ifPresent(b -> b.setPosX(-dx));
                getBone("conrod").ifPresent(b -> { b.setPosX(-dx); b.setRotZ((float) Math.asin(cr * st / rl)); });
                getBone("dpulley").ifPresent(b -> b.setRotZ(-t * 4.0F));
                getBone("gov").ifPresent(b -> b.setRotY(a * 0.6F));
            }
            if (be instanceof WindTurbineBlockEntity wt) {
                final float pt = state.getPartialTick();
                getBone("yaw").ifPresent(b -> b.setRotY(wt.yawDeg() * net.minecraft.util.Mth.DEG_TO_RAD));
                getBone("blades").ifPresent(b -> b.setRotZ(wt.rotorAngle(pt)));
                getBone("anemo").ifPresent(b -> b.setRotY(wt.cupAngle(pt)));
                getBone("vane").ifPresent(b -> b.setRotY(0.1F * (float) Math.sin(tt * 0.21) + 0.05F * (float) Math.sin(tt * 0.83)));
            }
            if (be instanceof FenceEnergizerBlockEntity fe && be.getLevel() != null) getBone("pulse").ifPresent(b -> b.setHidden(!fe.pulseLit(be.getLevel())));

            if (be instanceof CcAnimated ca) ca.ccAnimate(ClientCcBones.of(this), tt, state.getPartialTick());
            BoneOverrides.apply(this, getModelResource(be));
        }

        private void millStock(T be, double tt) {
            boolean run = be.isRunning();
            float p = run ? (float) ((tt % Balance.MILL_CYCLE_TICKS) / Balance.MILL_CYCLE_TICKS) : -1.0F;
            final float xs = -1.0F;
            float slabX, slabS, plateX, plateS;
            boolean slabOn, plateOn;
            if (p < 0.0F) { slabOn = true; slabX = 0.0F; slabS = 1.0F; plateOn = false; plateX = 0.0F; plateS = 1.0F; }
            else if (p < 0.30F) { slabOn = true; slabX = -9.0F + 14.6F * smooth(p / 0.30F); slabS = 1.0F; plateOn = false; plateX = 0.0F; plateS = 0.02F; }
            else if (p < 0.55F) { float k = (p - 0.30F) / 0.25F; slabOn = true; slabX = 5.6F; slabS = Math.max(0.02F, 1.0F - k); plateOn = true; plateX = 0.0F; plateS = Math.max(0.02F, k); }
            else if (p < 0.90F) { slabOn = false; slabX = 5.6F; slabS = 0.02F; plateOn = true; plateX = 6.0F * smooth((p - 0.55F) / 0.35F); plateS = 1.0F; }
            else { slabOn = false; slabX = 0.0F; slabS = 1.0F; plateOn = false; plateX = 6.0F; plateS = 1.0F; }
            final float sx = slabX, ss = slabS, px = plateX, ps = plateS;
            final boolean so = slabOn, po = plateOn, dark = ClientHemp.darkAt(be.getBlockPos().getX() + 0.5, be.getBlockPos().getZ() + 0.5);
            getBone("slab").ifPresent(b -> { b.setHidden(!so); b.setPosX(xs * sx); b.setScaleX(ss); });
            getBone("glow1").ifPresent(b -> b.setHidden(!so || !run || dark));
            getBone("plate").ifPresent(b -> { b.setHidden(!po); b.setPosX(xs * px); b.setScaleX(ps); });
            getBone("phot").ifPresent(b -> b.setHidden(!po || dark || p >= 0.68F));
            getBone("pwarm").ifPresent(b -> b.setHidden(!po || p < 0.68F || p >= 0.80F));
        }

        private static float smooth(float k) { k = Math.max(0.0F, Math.min(1.0F, k)); return k * k * (3.0F - 2.0F * k); }
    }

    private final int up;
    private final String name;

    private final Map<String, Matrix4f> meshPoses = new HashMap<>();
    private final Map<String, Matrix3f> meshNormals = new HashMap<>();
    private MeshModel mesh;

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (mesh != null && mesh.has(bone.getName())) {
            if (!bone.isHidden()) {
                meshPoses.put(bone.getName(), new Matrix4f(poseStack.last().pose()));
                meshNormals.put(bone.getName(), new Matrix3f(poseStack.last().normal()));
            }
            return;
        }
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        mesh = MeshModel.named(name);
        meshPoses.clear();
        meshNormals.clear();
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    public RefineryUnitRenderer(String name, int up) {
        super(new UnitModel<T>(name, up));
        this.up = up;
        this.name = name;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(6, 0, 6).expandTowards(0, up + 1, 0).expandTowards(0, -8, 0);
    }

    @Override
    public int getViewDistance() { return 128; }
}
