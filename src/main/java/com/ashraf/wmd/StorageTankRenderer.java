package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class StorageTankRenderer extends GeoBlockRenderer<StorageTankBlockEntity> {

    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(Wmd.MODID, path); }

    static final float LIQ_R = 31.6F / 16F, LIQ_Y0 = 16.8F / 16F, LIQ_Y1 = 111.6F / 16F, CZ = 32F / 16F;
    static final float POUR_X = -16F / 16F, POUR_Y = 100.4F / 16F, DRAIN_X = 24F / 16F;

    private static final int SEG = 39;
    private static final float GRID = 0.5F;
    static final int GLASS_ALPHA = 56, SHARD_ALPHA = 170;

    static class TankModel extends GeoModel<StorageTankBlockEntity> {
        @Override public ResourceLocation getModelResource(StorageTankBlockEntity be) { return rl("geo/block/storage_tank.geo.json"); }
        @Override public ResourceLocation getTextureResource(StorageTankBlockEntity be) { return rl("textures/block/storage_tank_geo.png"); }
        @Override public ResourceLocation getAnimationResource(StorageTankBlockEntity be) { return rl("animations/block/storage_tank.animation.json"); }

        @Override
        public void setCustomAnimations(StorageTankBlockEntity be, long instanceId, AnimationState<StorageTankBlockEntity> state) {
            BoneOverrides.apply(this, getModelResource(be));
        }
    }

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
    public void render(StorageTankBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        mesh = MeshModel.named("storage_tank");
        meshPoses.clear();
        meshNormals.clear();
        super.render(be, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        PoseStack.Pose glassPose = null, shardPose = null;
        int stage = be.damageStage();

        String glass = stage >= 1 && mesh.has("glass_holed") ? "glass_holed" : "glasspane";
        for (Map.Entry<String, Matrix4f> e : meshPoses.entrySet()) {
            PoseStack ps = new PoseStack();
            ps.last().pose().set(e.getValue());
            ps.last().normal().set(meshNormals.get(e.getKey()));
            String bone = e.getKey();
            if (bone.equals("glasspane") || bone.equals("glass_holed")) { if (bone.equals(glass)) glassPose = ps.last(); continue; }
            if (bone.equals("shards")) { if (stage >= 1) shardPose = ps.last(); continue; }
            if (bone.equals("soot") && stage < 2) continue;
            if (e.getKey().equals("accent")) {
                int c = accentColour(be);
                mesh.drawColoured("accent", ps.last(), bufferSource, packedLight, packedOverlay, (c >> 16) & 255, (c >> 8) & 255, c & 255);
                continue;
            }
            mesh.draw(e.getKey(), ps.last(), bufferSource, packedLight, packedOverlay);
        }
        if (glassPose != null) {
            drawLiquid(be, partialTick, glassPose, bufferSource, packedLight, packedOverlay);
            drawDamage(be, partialTick, glassPose, bufferSource, packedLight, packedOverlay);
            if (shardPose != null) mesh.drawTinted("shards", shardPose, bufferSource, packedLight, packedOverlay, 255, 255, 255, SHARD_ALPHA, false);
            mesh.drawGlass(glass, glassPose, bufferSource, packedLight, packedOverlay, GLASS_ALPHA);
        }
        meshPoses.clear();
        meshNormals.clear();
    }

    private static final List<float[]> PIECES = buildPieces();

    private static List<float[]> buildPieces() {
        List<float[]> out = new ArrayList<>();
        float[] circle = new float[SEG * 2];
        for (int k = 0; k < SEG; k++) {
            double a = 2 * Math.PI * k / SEG;
            circle[2 * k] = (float) (Math.cos(a) * LIQ_R);
            circle[2 * k + 1] = (float) (Math.sin(a) * LIQ_R);
        }
        int n = (int) Math.ceil(LIQ_R / GRID);
        for (int i = -n; i < n; i++) for (int j = -n; j < n; j++) {
            float x0 = i * GRID, x1 = x0 + GRID, z0 = j * GRID, z1 = z0 + GRID;
            float[] p = clip(circle, 0, x0, true);
            p = clip(p, 0, x1, false);
            p = clip(p, 1, z0, true);
            p = clip(p, 1, z1, false);
            if (p.length < 6) continue;
            float[] piece = new float[p.length + 2];
            piece[0] = (float) Math.floor(x0);
            piece[1] = (float) Math.floor(z0);
            System.arraycopy(p, 0, piece, 2, p.length);
            out.add(piece);
        }
        return out;
    }

    private static float[] clip(float[] poly, int axis, float v, boolean keepAbove) {
        int m = poly.length / 2;
        if (m == 0) return poly;
        float[] out = new float[poly.length * 2 + 4];
        int o = 0;
        for (int k = 0; k < m; k++) {
            int n = (k + 1) % m;
            float ax = poly[2 * k], az = poly[2 * k + 1], bx = poly[2 * n], bz = poly[2 * n + 1];
            float ac = axis == 0 ? ax : az, bc = axis == 0 ? bx : bz;
            boolean ain = keepAbove ? ac >= v : ac <= v, bin = keepAbove ? bc >= v : bc <= v;
            if (ain) { out[o++] = ax; out[o++] = az; }
            if (ain != bin) {
                float t = (v - ac) / (bc - ac);
                out[o++] = ax + (bx - ax) * t;
                out[o++] = az + (bz - az) * t;
            }
        }
        float[] r = new float[o];
        System.arraycopy(out, 0, r, 0, o);
        return r;
    }

    private static void drawLiquid(StorageTankBlockEntity be, float partial, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
        FluidStack fs = be.fluidStack();
        float lvl = be.shownLevel();
        be.logLiquid(fs, lvl);
        if (fs.isEmpty() || lvl < 0.002F) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fs.getFluid());
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite still = atlas.apply(ext.getStillTexture(fs));
        TextureAtlasSprite flow = atlas.apply(ext.getFlowingTexture(fs));
        int tint = ext.getTintColor(fs);
        int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255;
        int a = 225;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        float t = ((be.getLevel() != null ? be.getLevel().getGameTime() : 0) + partial) / 20F;
        float rot = be.swirl(partial);
        float pour = be.pour(partial), drain = be.drain(partial);
        float yL = LIQ_Y0 + (LIQ_Y1 - LIQ_Y0) * lvl;
        float cos = Mth.cos(rot), sin = Mth.sin(rot);

        for (int k = 0; k < SEG; k++) {
            float a0 = rot + Mth.TWO_PI * k / SEG, a1 = rot + Mth.TWO_PI * (k + 1) / SEG, am = (a0 + a1) * 0.5F;
            float x0 = Mth.cos(a0) * LIQ_R, z0 = CZ + Mth.sin(a0) * LIQ_R, x1 = Mth.cos(a1) * LIQ_R, z1 = CZ + Mth.sin(a1) * LIQ_R;
            float nx = Mth.cos(am), nz = Mth.sin(am);
            float u0 = still.getU((k % 3) / 3F), u1 = still.getU((k % 3 + 1) / 3F);
            for (float y = LIQ_Y0; y < yL - 0.001F; y += 1F) {
                float y1 = Math.min(y + 1F, yL);
                float v0 = still.getV(0F), v1 = still.getV(y1 - y);
                quad(vc, pose, x0, y, z0, u0, v1, x1, y, z1, u1, v1, x1, y1, z1, u1, v0, x0, y1, z0, u0, v0, r, g, b, a, light, overlay, nx, 0, nz);
            }
        }

        float px = POUR_X, pz = CZ;
        for (float[] piece : PIECES) {
            int m = (piece.length - 2) / 2;
            float[] xs = new float[m], ys = new float[m], zs = new float[m], us = new float[m], vs = new float[m];
            for (int k = 0; k < m; k++) {
                float lx = piece[2 + 2 * k], lz = piece[3 + 2 * k];
                float x = lx * cos - lz * sin, z = CZ + lx * sin + lz * cos;
                float d2 = (lx * lx + lz * lz) / (LIQ_R * LIQ_R);
                float rim = Math.max(0F, 1F - d2);
                float wave = 0.014F * (Mth.sin(1.7F * x + 1.1F * t) + Mth.sin(2.3F * z - 0.9F * t)) * 0.5F;
                float dp = Mth.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                wave += pour * 0.05F * Mth.sin(9F * dp - 8F * t) * (float) Math.exp(-1.4F * dp);
                float dd = (x - DRAIN_X) * (x - DRAIN_X) + (z - CZ) * (z - CZ);
                wave -= drain * 0.10F * (float) Math.exp(-2.5F * dd);
                xs[k] = x; zs[k] = z;
                ys[k] = Mth.clamp(yL + wave * rim, LIQ_Y0, LIQ_Y1);
                us[k] = still.getU(Mth.clamp(lx - piece[0], 0F, 1F));
                vs[k] = still.getV(Mth.clamp(lz - piece[1], 0F, 1F));
            }
            for (int k = 1; k + 1 < m; k += 2) {
                int k2 = Math.min(k + 2, m - 1);
                quad(vc, pose, xs[0], ys[0], zs[0], us[0], vs[0], xs[k], ys[k], zs[k], us[k], vs[k], xs[k + 1], ys[k + 1], zs[k + 1], us[k + 1], vs[k + 1],
                        xs[k2], ys[k2], zs[k2], us[k2], vs[k2], r, g, b, a, light, overlay, 0, 1, 0);
            }
        }

        if (pour > 0.02F && POUR_Y > yL + 0.05F) {
            float w = 0.075F * pour;
            float fu0 = flow.getU(0F), fu1 = flow.getU(0.25F);
            int lr = Math.min(255, r + 40), lg = Math.min(255, g + 40), lb = Math.min(255, b + 40);
            for (float y = POUR_Y; y > yL + 0.001F; y -= 1F) {
                float y0 = Math.max(y - 1F, yL);
                float v0 = flow.getV(0F), v1 = flow.getV((y - y0) * 0.5F);
                quad(vc, pose, px - w, y, pz - w, fu0, v0, px + w, y, pz - w, fu1, v0, px + w, y0, pz - w, fu1, v1, px - w, y0, pz - w, fu0, v1, lr, lg, lb, 235, light, overlay, 0, 0, -1);
                quad(vc, pose, px + w, y, pz + w, fu0, v0, px - w, y, pz + w, fu1, v0, px - w, y0, pz + w, fu1, v1, px + w, y0, pz + w, fu0, v1, lr, lg, lb, 235, light, overlay, 0, 0, 1);
                quad(vc, pose, px - w, y, pz + w, fu0, v0, px - w, y, pz - w, fu1, v0, px - w, y0, pz - w, fu1, v1, px - w, y0, pz + w, fu0, v1, lr, lg, lb, 235, light, overlay, -1, 0, 0);
                quad(vc, pose, px + w, y, pz - w, fu0, v0, px + w, y, pz + w, fu1, v0, px + w, y0, pz + w, fu1, v1, px + w, y0, pz - w, fu0, v1, lr, lg, lb, 235, light, overlay, 1, 0, 0);
            }
            float su0 = still.getU(0.4F), su1 = still.getU(0.5F), sv0 = still.getV(0.4F), sv1 = still.getV(0.5F);
            for (int i = 0; i < 7; i++) {
                float p = (t * 1.7F + i / 7F) % 1F;
                float ang = i * 2.4F + rot;
                float dist = 0.08F + 0.42F * p;
                float dx = px + Mth.cos(ang) * dist, dz = pz + Mth.sin(ang) * dist;
                float dy = yL + 0.32F * pour * Mth.sin(Mth.PI * p);
                float s = 0.035F * pour * (1F - 0.5F * p);
                int da = (int) (235 * (1F - p * p));
                quad(vc, pose, dx - s, dy - s, dz, su0, sv1, dx + s, dy - s, dz, su1, sv1, dx + s, dy + s, dz, su1, sv0, dx - s, dy + s, dz, su0, sv0, lr, lg, lb, da, light, overlay, 0, 0, 1);
                quad(vc, pose, dx, dy - s, dz - s, su0, sv1, dx, dy - s, dz + s, su1, sv1, dx, dy + s, dz + s, su1, sv0, dx, dy + s, dz - s, su0, sv0, lr, lg, lb, da, light, overlay, 1, 0, 0);
            }
        }
    }

    private static final float HOLE_A = 225F * Mth.DEG_TO_RAD, HOLE_Y = LIQ_Y0 + 0.9F, GLASS_R = 32.7F / 16F;

    private static final float[][] SITES = { { 225F, HOLE_Y, 0.0F }, { 270F, 4.4F, 0.2F }, { 180F, 3.1F, 0.35F }, { 315F, 5.6F, 0.5F } };

    private static final float[][] CRACKS = { { 0.3F, 0.9F }, { 1.2F, 0.6F }, { 2.3F, 1.1F }, { 3.4F, 0.5F }, { 4.2F, 0.8F }, { 5.3F, 0.7F } };

    private static void drawDamage(StorageTankBlockEntity be, float partial, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
        int stage = be.damageStage();
        float dmg = be.damageFraction();
        if (dmg <= 0.0F || stage == 3) return;
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));

        TextureAtlasSprite white = atlas.apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
        float wu = white.getU(0.5F), wv = white.getV(0.5F), w = 0.018F;
        for (float[] s : SITES) {
            if (dmg <= s[2]) continue;
            float grow = Math.min(1.0F, 0.3F + (dmg - s[2]) * 2.0F);
            float a = s[0] * Mth.DEG_TO_RAD, snx = Mth.cos(a), snz = Mth.sin(a), stx = -snz, stz = snx;
            float sx = snx * GLASS_R, sy = s[1], sz = CZ + snz * GLASS_R;
            for (float[] c : CRACKS) {
                float du = Mth.cos(c[0] + s[0]), dv = Mth.sin(c[0] + s[0]), len = c[1] * grow;
                float ex = sx + stx * du * len, ey = sy + dv * len, ez = sz + stz * du * len;
                float px = -dv * w, py = du * w;
                quad(vc, pose, sx + stx * px, sy + py, sz + stz * px, wu, wv, ex + stx * px, ey + py, ez + stz * px, wu, wv, ex - stx * px, ey - py, ez - stz * px, wu, wv,
                        sx - stx * px, sy - py, sz - stz * px, wu, wv, 235, 240, 245, 200, light, overlay, snx, 0, snz);
            }
        }
        if (stage == 0) return;
        float nx = Mth.cos(HOLE_A), nz = Mth.sin(HOLE_A);
        float tx = -nz, tz = nx;
        float hx = nx * GLASS_R, hz = CZ + nz * GLASS_R;

        FluidStack fs = be.fluidStack();
        float yL = LIQ_Y0 + (LIQ_Y1 - LIQ_Y0) * be.shownLevel();
        if (fs.isEmpty() || yL < HOLE_Y + 0.05F) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fs.getFluid());
        TextureAtlasSprite flow = atlas.apply(ext.getFlowingTexture(fs)), still = atlas.apply(ext.getStillTexture(fs));
        int tint = ext.getTintColor(fs);
        int r = (tint >> 16) & 255, g = (tint >> 8) & 255, b = tint & 255;
        float v0 = 0.10F + 0.06F * Mth.sqrt(yL - HOLE_Y), grav = 0.08F, jw = 0.06F;
        float fu0 = flow.getU(0F), fu1 = flow.getU(0.2F);
        float px0 = hx, py0 = HOLE_Y, pz0 = hz, landX = hx, landZ = hz;
        for (int i = 1; i <= 24; i++) {
            float t = i * 0.5F;
            float x = hx + nx * v0 * t, z = hz + nz * v0 * t, y = HOLE_Y - 0.5F * grav * t * t;
            boolean onPlinth = Math.abs(x) <= 2.5F && Math.abs(z - CZ) <= 2.5F;
            float floor = onPlinth ? 0.29F : 0.02F;
            boolean last = y <= floor;
            if (last) y = floor;
            float va = flow.getV(((i - 1) * 0.1F) % 0.5F), vb = flow.getV(((i - 1) * 0.1F) % 0.5F + 0.1F);
            quad(vc, pose, px0 - tx * jw, py0, pz0 - tz * jw, fu0, va, px0 + tx * jw, py0, pz0 + tz * jw, fu1, va, x + tx * jw, y, z + tz * jw, fu1, vb,
                    x - tx * jw, y, z - tz * jw, fu0, vb, r, g, b, 230, light, overlay, nx, 0.5F, nz);
            quad(vc, pose, px0, py0 - jw, pz0, fu0, va, px0, py0 + jw, pz0, fu1, va, x, y + jw, z, fu1, vb, x, y - jw, z, fu0, vb, r, g, b, 230, light, overlay, tx, 0, tz);
            px0 = x; py0 = y; pz0 = z;
            if (last) { landX = x; landZ = z; break; }
        }

        boolean onPlinth = Math.abs(landX) <= 2.5F && Math.abs(landZ - CZ) <= 2.5F;
        float py = onPlinth ? 0.30F : 0.03F, pr = 0.55F;
        float su = still.getU(0.5F), sv = still.getV(0.5F);
        for (int k = 0; k < 8; k += 2) {
            float a0 = k * Mth.TWO_PI / 8, a1 = (k + 1) * Mth.TWO_PI / 8, a2 = (k + 2) * Mth.TWO_PI / 8;
            quad(vc, pose, landX, py, landZ, su, sv, landX + Mth.cos(a0) * pr, py, landZ + Mth.sin(a0) * pr, still.getU(0.5F + Mth.cos(a0) * 0.45F), still.getV(0.5F + Mth.sin(a0) * 0.45F),
                    landX + Mth.cos(a1) * pr, py, landZ + Mth.sin(a1) * pr, still.getU(0.5F + Mth.cos(a1) * 0.45F), still.getV(0.5F + Mth.sin(a1) * 0.45F),
                    landX + Mth.cos(a2) * pr, py, landZ + Mth.sin(a2) * pr, still.getU(0.5F + Mth.cos(a2) * 0.45F), still.getV(0.5F + Mth.sin(a2) * 0.45F), r, g, b, 210, light, overlay, 0, 1, 0);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3,
                             int r, int g, int b, int a, int light, int overlay, float nx, float ny, float nz) {
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public StorageTankRenderer() {
        super(new TankModel());
    }

    @Override
    public AABB getRenderBoundingBox(StorageTankBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(6, 0, 6).expandTowards(0, 8.2, 0);
    }

    @Override
    public int getViewDistance() { return 128; }

    private static int accentColour(StorageTankBlockEntity be) {
        int idx = be.fluidIndex();
        if (idx < 0 || idx >= WmdFluids.ALL.size()) return 0xFFFFFF;
        int argb = WmdFluids.ALL.get(idx).color();
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        return (Math.min(255, (int) (r * 1.3 + 25)) << 16) | (Math.min(255, (int) (g * 1.3 + 25)) << 8) | Math.min(255, (int) (b * 1.3 + 25));
    }
}
