package com.ashraf.wmd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;

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
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

public final class PipeModel {

    public static final ResourceLocation LOADER_ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pipe");
    private static final String[] TEXTURES = {"pipe", "collar", "bolt", "dark", "wheel_on", "wheel_off", "arrow"};

    private PipeModel() {}

    public static final class Loader implements IGeometryLoader<Geometry> {
        public static final Loader INSTANCE = new Loader();

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) { return new Geometry(json.has("kind") && "valve".equals(json.get("kind").getAsString())); }
    }

    public record Geometry(boolean valve) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides) {
            TextureAtlasSprite[] sprites = new TextureAtlasSprite[TEXTURES.length];
            for (int i = 0; i < TEXTURES.length; i++) sprites[i] = spriteGetter.apply(context.getMaterial(TEXTURES[i]));
            return new Baked(sprites, spriteGetter.apply(context.getMaterial("particle")), context.getTransforms(), valve);
        }
    }

    public static final class Baked implements IDynamicBakedModel {
        private final TextureAtlasSprite[] sprites;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final Map<Integer, List<BakedQuad>> cache = new ConcurrentHashMap<>();

        private final boolean valve;

        Baked(TextureAtlasSprite[] sprites, TextureAtlasSprite particle, ItemTransforms transforms, boolean valve) {
            this.valve = valve;
            this.sprites = sprites;
            this.particle = particle;
            this.transforms = transforms;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
            if (side != null) return List.of();
            if (state == null && valve) return cache.computeIfAbsent(-2, k -> bake(PipeMesh.valve(Direction.EAST.ordinal(), 1, 1, true, true)));
            int[] conn = new int[6];
            int key = 0;
            if (state != null && state.getBlock() instanceof FluidValveBlock) {
                int f = state.getValue(FluidValveBlock.FACING).ordinal(), fr = state.getValue(FluidValveBlock.FRONT).ordinal(), bk = state.getValue(FluidValveBlock.BACK).ordinal();
                boolean open = state.getValue(FluidValveBlock.OPEN), one = state.getValue(FluidValveBlock.ONE_WAY);
                int vkey = 100_000 + (((f * 3 + fr) * 3 + bk) * 2 + (open ? 1 : 0)) * 2 + (one ? 1 : 0);
                return cache.computeIfAbsent(vkey, k -> bake(PipeMesh.valve(f, fr, bk, open, one)));
            }
            if (state != null && state.getBlock() instanceof FluidPipeBlock) {
                for (Direction d : Direction.values()) {
                    conn[d.ordinal()] = state.getValue(FluidPipeBlock.prop(d)).ordinal();
                    key = key * 3 + conn[d.ordinal()];
                }
            } else {
                conn[Direction.NORTH.ordinal()] = 1; conn[Direction.SOUTH.ordinal()] = 1;
                key = -1;
            }
            return cache.computeIfAbsent(key, k -> bake(PipeMesh.build(conn)));
        }

        private List<BakedQuad> bake(List<PipeMesh.Quad> mesh) {
            BakedQuad[] out = new BakedQuad[mesh.size()];
            QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
            for (int qi = 0; qi < mesh.size(); qi++) {
                PipeMesh.Quad q = mesh.get(qi);
                TextureAtlasSprite s = sprites[q.mat];
                double[] avg = {0, 0, 0};
                for (double[] n : q.nrm) { avg[0] += n[0]; avg[1] += n[1]; avg[2] += n[2]; }
                b.setSprite(s);
                b.setDirection(Direction.getNearest((float) avg[0], (float) avg[1], (float) avg[2]));
                b.setShade(false);
                b.setHasAmbientOcclusion(false);
                b.setTintIndex(q.mat == 0 ? 0 : -1);
                for (int i = 0; i < 4; i++) {
                    double[] p = q.pos[i], n = q.nrm[i], uv = q.uv[i];

                    double br = n[0] * n[0] * 0.6 + n[2] * n[2] * 0.8 + n[1] * n[1] * (n[1] > 0 ? 1.0 : 0.5);
                    int c = (int) Math.round(Math.min(1.0, br) * 255);
                    float u = (float) (uv[0] - Math.floor(uv[0] * 0.9999)), v = (float) (uv[1] - Math.floor(uv[1] * 0.9999));
                    b.addVertex((float) (p[0] / 16.0), (float) (p[1] / 16.0), (float) (p[2] / 16.0));
                    b.setColor(c, c, c, 255);
                    b.setUv(s.getU0() + (s.getU1() - s.getU0()) * u, s.getV0() + (s.getV1() - s.getV0()) * v);
                    b.setUv2(0, 0);
                    b.setNormal((float) n[0], (float) n[1], (float) n[2]);
                }
                out[qi] = b.bakeQuad();
            }
            return List.of(out);
        }

        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
