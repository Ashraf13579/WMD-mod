package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

public final class WingModel {

    public static final ResourceLocation LOADER_ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wing");

    private WingModel() {}

    public static final class Loader implements IGeometryLoader<Geometry> {
        public static final Loader INSTANCE = new Loader();
        @Override public Geometry read(JsonObject json, JsonDeserializationContext context) { return new Geometry(); }
    }

    public record Geometry() implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides) {
            return new Baked(sprites.apply(context.getMaterial("texture")), context.getTransforms());
        }
    }

    private record Key(BlockState state, BlockState skin, int halves) {}

    public static final class Baked implements IDynamicBakedModel {
        private final TextureAtlasSprite steel;
        private final ItemTransforms transforms;
        private final Map<Key, List<BakedQuad>> cache = new ConcurrentHashMap<>();

        Baked(TextureAtlasSprite steel, ItemTransforms transforms) { this.steel = steel; this.transforms = transforms; }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType rt) {
            if (side != null) return List.of();
            BlockState skin = data == null ? null : data.get(WingSectionBlock.SKIN);
            Integer hv = data == null ? null : data.get(WingSectionBlock.HALVES);
            int halves = hv == null ? 0 : hv;
            Key key = new Key(state, skin, halves);
            List<BakedQuad> q = cache.get(key);
            if (q != null) return q;
            q = bake(state, skin, halves);
            if (!q.isEmpty()) cache.put(key, q);
            return q;
        }

        private List<BakedQuad> bake(BlockState st, BlockState skin, int halves) {
            WingParts.Shape shape = st == null ? WingParts.Shape.PANEL : st.getValue(WingParts.SHAPE);
            Direction facing = st == null ? Direction.NORTH : st.getValue(WingParts.FACING);
            boolean fin = st != null && st.getValue(WingParts.FIN), mirror = st != null && st.getValue(WingParts.MIRROR);
            WingParts.Level level = st == null ? WingParts.Level.MID : st.getValue(WingParts.LEVEL);
            WingParts.Thick thick = st == null || !st.hasProperty(WingParts.THICK) ? WingParts.Thick.THIN : st.getValue(WingParts.THICK);
            WingParts.Grow grow = st == null || !st.hasProperty(WingParts.GROW) ? WingParts.Grow.BOTH : st.getValue(WingParts.GROW);
            MeshModel mesh = MeshModel.named("wing_section");
            String bone = shape.getSerializedName();
            float[] cx = { Float.MAX_VALUE, -Float.MAX_VALUE };
            mesh.forEachQuad(bone, (v, i) -> { for (int k = 0; k < 32; k += 8) { cx[0] = Math.min(cx[0], v[i + k]); cx[1] = Math.max(cx[1], v[i + k]); } });
            if (cx[0] > cx[1]) return List.of();
            float off = Math.round((cx[0] + cx[1]) * 0.5F);
            TextureAtlasSprite[] face = new TextureAtlasSprite[6];
            BakedModel sm = skin == null ? null : Minecraft.getInstance().getBlockRenderer().getBlockModel(skin);
            for (Direction d : Direction.values()) {
                TextureAtlasSprite s = steel;
                if (sm != null) {
                    List<BakedQuad> qs = sm.getQuads(skin, d, RandomSource.create(42L), ModelData.EMPTY, null);
                    s = qs.isEmpty() ? sm.getParticleIcon(ModelData.EMPTY) : qs.get(0).getSprite();
                }
                face[d.ordinal()] = s;
            }
            List<BakedQuad> out = new ArrayList<>();
            QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
            mesh.forEachQuad(bone, (v, i) -> {
                float[][] p = new float[4][];
                for (int k = 0; k < 4; k++) p[k] = WingParts.place(v[i + k * 8] - off, v[i + k * 8 + 1], v[i + k * 8 + 2], facing, fin, mirror, level, thick, grow);
                if (mirror) { float[] t = p[1]; p[1] = p[3]; p[3] = t; }
                quad(p, face, b, out);
            });
            if (halves != 0 && st != null && !fin) {
                net.minecraft.world.phys.AABB w = WingParts.shape(facing, false, level, thick, grow).bounds();
                for (float[] box : WingSectionBlock.halfBoxes(halves, (float) w.minY, (float) w.maxY)) box(box, face, b, out);
            }
            return List.copyOf(out);
        }

        private static void box(float[] k, TextureAtlasSprite[] face, QuadBakingVertexConsumer b, List<BakedQuad> out) {
            float x0 = k[0], y0 = k[1], z0 = k[2], x1 = k[3], y1 = k[4], z1 = k[5];
            float[][][] f = {
                { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } },
                { { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 } },
                { { x1, y1, z0 }, { x1, y0, z0 }, { x0, y0, z0 }, { x0, y1, z0 } },
                { { x0, y1, z1 }, { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 } },
                { { x0, y1, z0 }, { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 } },
                { { x1, y1, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x1, y1, z0 } } };
            for (float[][] q : f) quad(q, face, b, out);
        }

        private static void quad(float[][] p, TextureAtlasSprite[] face, QuadBakingVertexConsumer b, List<BakedQuad> out) {
                float ax = p[2][0] - p[0][0], ay = p[2][1] - p[0][1], az = p[2][2] - p[0][2], bx = p[3][0] - p[1][0], by = p[3][1] - p[1][1], bz = p[3][2] - p[1][2];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (nl < 1.0E-9F) return;
                nx /= nl; ny /= nl; nz /= nl;
                Direction dir = Direction.getNearest(nx, ny, nz);
                TextureAtlasSprite s = face[dir.ordinal()];
                int ia = dir.getAxis() == Direction.Axis.X ? 2 : 0, ib = dir.getAxis() == Direction.Axis.Y ? 2 : 1;
                float ca = 0.0F, cb = 0.0F;
                for (float[] q : p) { ca += q[ia]; cb += q[ib]; }
                float ta = (float) Math.floor(ca / 4.0F), tb = (float) Math.floor(cb / 4.0F);
                double br = nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (ny > 0 ? 1.0 : 0.5);
                int c = (int) Math.round(Math.min(1.0, br) * 255);
                b.setSprite(s);
                b.setDirection(dir);
                b.setShade(false);
                b.setHasAmbientOcclusion(false);
                b.setTintIndex(-1);
                for (float[] q : p) {
                    float u = clamp01(q[ia] - ta), w = clamp01(q[ib] - tb);
                    float tv = ib == 1 ? 1.0F - w : w;
                    b.addVertex(q[0], q[1], q[2]);
                    b.setColor(c, c, c, 255);
                    b.setUv(s.getU(u), s.getV(tv));
                    b.setUv2(0, 0);
                    b.setNormal(nx, ny, nz);
                }
                out.add(b.bakeQuad());
        }

        private static float clamp01(float x) { return x < 0.0F ? 0.0F : Math.min(1.0F, x); }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) { return ChunkRenderTypeSet.of(RenderType.cutout()); }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return steel; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
