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

    private record Face(TextureAtlasSprite sprite, int r, int g, int b) {}

    static void draw(MeshModel mesh, String bone, Matrix4f pose, Matrix3f normal, BlockState skin, Level level, BlockPos pos, MultiBufferSource buffers, int light, int overlay) {
        if (mesh == null || skin == null || pose == null || normal == null) return;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(skin);
        Map<Direction, Face> faces = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) faces.put(d, face(model, skin, d, level, pos));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vector3f n = new Vector3f();
        mesh.forEachQuad(bone, (v, i) -> {

            float ax = v[i + 16] - v[i], ay = v[i + 17] - v[i + 1], az = v[i + 18] - v[i + 2];
            float bx = v[i + 24] - v[i + 8], by = v[i + 25] - v[i + 9], bz = v[i + 26] - v[i + 10];
            float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
            float mx = Math.abs(nx), my = Math.abs(ny), mz = Math.abs(nz);
            int axis = mx >= my && mx >= mz ? 0 : my >= mz ? 1 : 2;
            Direction dir = axis == 0 ? (nx > 0 ? Direction.EAST : Direction.WEST) : axis == 1 ? (ny > 0 ? Direction.UP : Direction.DOWN) : (nz > 0 ? Direction.SOUTH : Direction.NORTH);
            Face f = faces.get(dir);

            int ia = axis == 0 ? 2 : 0, ib = axis == 1 ? 2 : 1;
            float oa = 0.5F, ob = ib == 1 ? 0.0F : 0.5F;
            float ca = (v[i + ia] + v[i + 8 + ia] + v[i + 16 + ia] + v[i + 24 + ia]) * 0.25F + oa;
            float cb = (v[i + ib] + v[i + 8 + ib] + v[i + 16 + ib] + v[i + 24 + ib]) * 0.25F + ob;
            float ta = (float) Math.floor(ca), tb = (float) Math.floor(cb);
            for (int k = 0; k < 32; k += 8) {
                float u = clamp01(v[i + k + ia] + oa - ta), w = clamp01(v[i + k + ib] + ob - tb);
                float tu = u, tv = ib == 1 ? 1.0F - w : w;
                n.set(v[i + k + 5], v[i + k + 6], v[i + k + 7]).mul(normal);
                vc.addVertex(pose, v[i + k], v[i + k + 1], v[i + k + 2]).setColor(f.r(), f.g(), f.b(), 255).setUv(f.sprite().getU(tu), f.sprite().getV(tv))
                        .setOverlay(overlay).setLight(light).setNormal(n.x, n.y, n.z);
            }
        });
    }

    private static float clamp01(float x) { return x < 0.0F ? 0.0F : Math.min(x, 1.0F); }

    private static Face face(BakedModel model, BlockState skin, Direction dir, Level level, BlockPos pos) {
        List<BakedQuad> qs = model.getQuads(skin, dir, RandomSource.create(42L), ModelData.EMPTY, null);
        BakedQuad q = qs.isEmpty() ? null : qs.get(0);
        TextureAtlasSprite sp = q != null ? q.getSprite() : model.getParticleIcon(ModelData.EMPTY);
        int col = 0xFFFFFF;
        if (q != null && q.isTinted() && level != null) col = Minecraft.getInstance().getBlockColors().getColor(skin, level, pos, q.getTintIndex());
        return new Face(sp, (col >> 16) & 255, (col >> 8) & 255, col & 255);
    }
}
