package com.ashraf.wmd;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

final class CopycatSkin {

    private CopycatSkin() {}

    static final class SkinInfo {
        final TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
        final int[] tintIdx = new int[6];
        final boolean anyTinted;

        SkinInfo(BlockState skin) {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(skin);
            RandomSource rnd = RandomSource.create(42L);
            boolean tinted = false;
            for (Direction d : Direction.values()) {
                rnd.setSeed(42L);
                List<BakedQuad> qs = model.getQuads(skin, d, rnd, ModelData.EMPTY, null);
                BakedQuad q = qs.isEmpty() ? null : qs.get(0);
                int o = d.ordinal();
                sprites[o] = q != null ? q.getSprite() : model.getParticleIcon(ModelData.EMPTY);
                tintIdx[o] = q != null && q.isTinted() ? q.getTintIndex() : -1;
                if (tintIdx[o] >= 0) tinted = true;
            }
            anyTinted = tinted;
        }
    }

    private static final Map<BlockState, SkinInfo> SKIN_CACHE = Statics.client(new java.util.IdentityHashMap<>());
    private static final Vector3f TMP_N = new Vector3f();
    private static final int[] FACE_R = new int[6], FACE_G = new int[6], FACE_B = new int[6];

    static void clearCache() { SKIN_CACHE.clear(); }

    static SkinInfo skinInfo(BlockState skin) {
        if (SKIN_CACHE.size() > 512) SKIN_CACHE.clear();
        return SKIN_CACHE.computeIfAbsent(skin, SkinInfo::new);
    }

    static void draw(MeshModel mesh, String bone, Matrix4f pose, Matrix3f normal, BlockState skin, Level level, BlockPos pos, MultiBufferSource buffers, int light, int overlay) {
        if (mesh == null || skin == null || pose == null || normal == null) return;
        SkinInfo info = skinInfo(skin);
        var colors = info.anyTinted && level != null ? Minecraft.getInstance().getBlockColors() : null;
        for (int o = 0; o < 6; o++) {
            int col = colors != null && info.tintIdx[o] >= 0 ? colors.getColor(skin, level, pos, info.tintIdx[o]) : 0xFFFFFF;
            FACE_R[o] = (col >> 16) & 255;
            FACE_G[o] = (col >> 8) & 255;
            FACE_B[o] = col & 255;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vector3f n = TMP_N;
        mesh.forEachQuad(bone, (v, i) -> {

            float ax = v[i + 16] - v[i], ay = v[i + 17] - v[i + 1], az = v[i + 18] - v[i + 2];
            float bx = v[i + 24] - v[i + 8], by = v[i + 25] - v[i + 9], bz = v[i + 26] - v[i + 10];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float mx = Math.abs(nx), my = Math.abs(ny), mz = Math.abs(nz);
            int axis = mx >= my && mx >= mz ? 0 : my >= mz ? 1 : 2;
            Direction dir = axis == 0 ? (nx > 0 ? Direction.EAST : Direction.WEST) : axis == 1 ? (ny > 0 ? Direction.UP : Direction.DOWN) : (nz > 0 ? Direction.SOUTH : Direction.NORTH);
            int o = dir.ordinal();
            TextureAtlasSprite sp = info.sprites[o];
            int fr = FACE_R[o], fg = FACE_G[o], fb = FACE_B[o];

            int ia = axis == 0 ? 2 : 0, ib = axis == 1 ? 2 : 1;
            float oa = 0.5F, ob = ib == 1 ? 0.0F : 0.5F;
            float ca = (v[i + ia] + v[i + 8 + ia] + v[i + 16 + ia] + v[i + 24 + ia]) * 0.25F + oa;
            float cb = (v[i + ib] + v[i + 8 + ib] + v[i + 16 + ib] + v[i + 24 + ib]) * 0.25F + ob;
            float ta = (float) Math.floor(ca), tb = (float) Math.floor(cb);
            for (int k = 0; k < 32; k += 8) {
                float u = clamp01(v[i + k + ia] + oa - ta), w = clamp01(v[i + k + ib] + ob - tb);
                float tu = u, tv = ib == 1 ? 1.0F - w : w;
                n.set(v[i + k + 5], v[i + k + 6], v[i + k + 7]).mul(normal);
                vc.addVertex(pose, v[i + k], v[i + k + 1], v[i + k + 2]).setColor(fr, fg, fb, 255).setUv(sp.getU(tu), sp.getV(tv))
                        .setOverlay(overlay).setLight(light).setNormal(n.x, n.y, n.z);
            }
        });
    }

    private static float clamp01(float x) { return x < 0.0F ? 0.0F : Math.min(x, 1.0F); }
}
