package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

public class AirEngineRenderer implements BlockEntityRenderer<AirEngineBlockEntity> {

    static final Map<String, float[][]> HOUSING = Map.of(
            "ducted_fan_pod", new float[][] { { -6.4F, 29.6F, -1.0F, 22.4F, 30.6F, 33.0F } },
            "ducted_fan_nacelle", new float[][] { { -8F, 0F, -8F, 24F, 1.6F, 40F }, { -8F, 1.6F, -8F, -6.4F, 30.4F, 40F }, { 22.4F, 1.6F, -8F, 24F, 30.4F, 40F },
                    { -8F, 30.4F, -8F, 24F, 32F, 8F }, { -8F, 30.4F, 24F, 24F, 32F, 40F }, { 8F, 30.4F, 8F, 24F, 32F, 24F } },
            "turbofan_pod", new float[][] { { -13.6F, 45.3F, 2.0F, 13.6F, 46.6F, 62.0F } },
            "turbofan_nacelle", new float[][] { { -24F, 0F, -8F, 24F, 1.6F, 56F }, { -24F, 1.6F, -8F, -22.4F, 46.4F, 56F }, { 22.4F, 1.6F, -8F, 24F, 46.4F, 56F },
                    { -24F, 46.4F, -8F, 24F, 48F, 24F }, { -24F, 46.4F, 24F, -8F, 48F, 56F }, { 8F, 46.4F, 24F, 24F, 48F, 56F } },
            "jato_pod", new float[][] { { -6.6F, 15.0F, -7.0F, 6.6F, 15.6F, 39.0F } },
            "jato_nacelle", new float[][] { { -8F, 14.6F, -8F, 8F, 16F, 40F }, { -8F, 1F, -8F, -6.6F, 14.6F, 40F }, { 6.6F, 1F, -8F, 8F, 14.6F, 40F } });

    private final Variant pod, nacelle;

    public AirEngineRenderer(String mesh, int up, int reach) {
        pod = new Variant(mesh + "_pod", up, reach);
        nacelle = new Variant(mesh + "_nacelle", up, reach);
    }

    private Variant of(AirEngineBlockEntity be) { return be.nacelle() ? nacelle : pod; }

    @Override
    public void render(AirEngineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        of(be).render(be, partialTick, poseStack, buffers, light, overlay);
    }

    @Override public AABB getRenderBoundingBox(AirEngineBlockEntity be) { return of(be).getRenderBoundingBox(be); }
    @Override public boolean shouldRenderOffScreen(AirEngineBlockEntity be) { return true; }
    @Override public int getViewDistance() { return 192; }

    static final class Variant extends StagedUnitRenderer<AirEngineBlockEntity> {
        private static final Set<String> ATTACH = Set.of("housing");
        private final float[][] boxes;
        private BlockState skin;

        Variant(String name, int up, int reach) {
            super(name, up, reach);
            boxes = HOUSING.getOrDefault(name, new float[0][]);
        }

        @Override protected Set<String> attachBones() { return ATTACH; }
        @Override protected boolean drawsItself(String bone) { return skin != null && "housing".equals(bone); }

        @Override
        public void render(AirEngineBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
            skin = be.copyShown();
            super.render(be, partialTick, poseStack, buffers, light, overlay);
            skin = null;
        }

        @Override
        protected void drawAttached(AirEngineBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
            if (skin == null || !poses.containsKey("housing")) return;
            CopycatSkin.drawBoxes(buffers, poses.get("housing"), normals.get("housing"), skin, be, boxes, light, overlay);
        }
    }

    static final class CopycatSkin {
        private CopycatSkin() {}

        static void drawBoxes(MultiBufferSource buffers, Matrix4f pose, Matrix3f nrm, BlockState skin, net.minecraft.world.level.block.entity.BlockEntity be,
                              float[][] boxes, int light, int overlay) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(skin);
            for (float[] b : boxes) {
                for (Direction dir : Direction.values()) face(vc, pose, nrm, model, skin, be, dir, b, light, overlay);
            }
        }

        static void face(VertexConsumer vc, Matrix4f pose, Matrix3f nrm, BakedModel model, BlockState skin, net.minecraft.world.level.block.entity.BlockEntity be,
                                 Direction dir, float[] b, int light, int overlay) {
            List<BakedQuad> qs = model.getQuads(skin, dir, RandomSource.create(42L), ModelData.EMPTY, null);
            BakedQuad q = qs.isEmpty() ? null : qs.get(0);
            TextureAtlasSprite sp = q != null ? q.getSprite() : model.getParticleIcon(ModelData.EMPTY);
            int col = 0xFFFFFF;
            if (q != null && q.isTinted() && be.getLevel() != null) col = Minecraft.getInstance().getBlockColors().getColor(skin, be.getLevel(), be.getBlockPos(), q.getTintIndex());
            int r = (col >> 16) & 255, g = (col >> 8) & 255, bl = col & 255;

            int ia, ib, id;
            float d;
            switch (dir) {
                case SOUTH -> { ia = 0; ib = 1; id = 2; d = b[5]; }
                case NORTH -> { ia = 0; ib = 1; id = 2; d = b[2]; }
                case EAST -> { ia = 2; ib = 1; id = 0; d = b[3]; }
                case WEST -> { ia = 2; ib = 1; id = 0; d = b[0]; }
                case UP -> { ia = 0; ib = 2; id = 1; d = b[4]; }
                default -> { ia = 0; ib = 2; id = 1; d = b[1]; }
            }
            float oa = ia == 1 ? 0.0F : -8.0F, ob = ib == 1 ? 0.0F : -8.0F;
            float[] n = nrmTransform(nrm, dir.getStepX(), dir.getStepY(), dir.getStepZ());
            for (float a0 = b[ia]; a0 < b[ia + 3] - 1.0E-3F; ) {
                float a1 = Math.min(b[ia + 3], gridNext(a0, oa));
                for (float b0 = b[ib]; b0 < b[ib + 3] - 1.0E-3F; ) {
                    float b1 = Math.min(b[ib + 3], gridNext(b0, ob));
                    tile(vc, pose, n, sp, dir, ia, ib, id, a0, a1, b0, b1, d, oa, ob, r, g, bl, light, overlay);
                    b0 = b1;
                }
                a0 = a1;
            }
        }

        private static float gridNext(float v, float o) { return o + (float) (Math.floor((v - o) / 16.0F + 1.0E-4F) + 1.0) * 16.0F; }

        private static float frac(float v, float o) {
            float f = ((v - o) % 16.0F) / 16.0F;
            return f < 0.0F ? f + 1.0F : f;
        }

        private static void tile(VertexConsumer vc, Matrix4f pose, float[] n, TextureAtlasSprite sp, Direction dir, int ia, int ib, int id,
                                 float a0, float a1, float b0, float b1, float d, float oa, float ob, int r, int g, int bl, int light, int overlay) {
            float ua0 = frac(a0, oa), ua1 = ua0 + (a1 - a0) / 16.0F, vb0 = frac(b0, ob), vb1 = vb0 + (b1 - b0) / 16.0F;

            float[][] ab = switch (dir) {
                case SOUTH, WEST, DOWN -> new float[][] { { a0, b0 }, { a1, b0 }, { a1, b1 }, { a0, b1 } };
                case UP -> new float[][] { { a0, b1 }, { a1, b1 }, { a1, b0 }, { a0, b0 } };
                default -> new float[][] { { a1, b0 }, { a0, b0 }, { a0, b1 }, { a1, b1 } };
            };
            for (float[] p : ab) {
                float[] xyz = new float[3];
                xyz[ia] = p[0];
                xyz[ib] = p[1];
                xyz[id] = d;
                float u = sp.getU(p[0] == a0 ? ua0 : ua1), v = sp.getV(1.0F - (p[1] == b0 ? vb0 : vb1));
                vc.addVertex(pose, xyz[0] / 16.0F, xyz[1] / 16.0F, xyz[2] / 16.0F).setColor(r, g, bl, 255).setUv(u, v).setOverlay(overlay).setLight(light)
                        .setNormal(n[0], n[1], n[2]);
            }
        }

        private static float[] nrmTransform(Matrix3f m, float x, float y, float z) {
            org.joml.Vector3f v = new org.joml.Vector3f(x, y, z).mul(m);
            return new float[] { v.x, v.y, v.z };
        }
    }
}
