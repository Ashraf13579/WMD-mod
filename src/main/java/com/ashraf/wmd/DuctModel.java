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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

public final class DuctModel {

    public static final ResourceLocation LOADER_ID = ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "duct");
    private static final String[] TEXTURES = { "frame", "glass", "rim", "in", "out", "off", "screen", "dark" };

    private DuctModel() {}

    public static final class Loader implements IGeometryLoader<Geometry> {
        public static final Loader INSTANCE = new Loader();

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) { return new Geometry(json.has("kind") ? json.get("kind").getAsString() : "duct"); }
    }

    public record Geometry(String kind) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
            TextureAtlasSprite[] sprites = new TextureAtlasSprite[TEXTURES.length];
            for (int i = 0; i < TEXTURES.length; i++) sprites[i] = spriteGetter.apply(context.getMaterial(TEXTURES[i]));
            return new Baked(kind, sprites, spriteGetter.apply(context.getMaterial("particle")), context.getTransforms());
        }
    }

    public static final class Baked implements IDynamicBakedModel {
        private static final ChunkRenderTypeSet TRANSLUCENT = ChunkRenderTypeSet.of(RenderType.translucent());
        private final String kind;
        private final TextureAtlasSprite[] sprites;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;
        private final Map<Integer, List<BakedQuad>> cache = new ConcurrentHashMap<>();

        Baked(String kind, TextureAtlasSprite[] sprites, TextureAtlasSprite particle, ItemTransforms transforms) {
            this.kind = kind;
            this.sprites = sprites;
            this.particle = particle;
            this.transforms = transforms;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
            if (side != null) return List.of();
            int key = 0;
            int[] c = new int[6];
            if (state != null && state.getBlock() instanceof ItemDuctBlock) {
                for (Direction d : Direction.values()) { c[d.ordinal()] = state.getValue(ItemDuctBlock.prop(d)).ordinal(); key = key * 5 + c[d.ordinal()]; }
            } else if (state != null && state.getBlock() instanceof SorterBlock) {
                for (Direction d : Direction.values()) { c[d.ordinal()] = state.getValue(SorterBlock.prop(d)).ordinal(); key = key * 3 + c[d.ordinal()]; }
            } else if (state != null && state.getBlock() instanceof FloodgateBlock) {
                key = state.getValue(FloodgateBlock.FACING).ordinal() * 2 + (state.getValue(FloodgateBlock.POURING) ? 1 : 0);
            } else if (state != null && state.getBlock() instanceof SupplyInterfaceBlock) {
                key = switch (state.getValue(SupplyInterfaceBlock.FACING)) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
            } else {
                key = -1;
            }
            final int k = key;
            return cache.computeIfAbsent(key, x -> bake(switch (kind) {
                case "sorter" -> DuctMesh.sorter(k < 0 ? new int[] { 0, 0, 1, 2, 0, 0 } : c);
                case "supply" -> DuctMesh.supply(Math.max(0, k));
                case "floodgate" -> DuctMesh.floodgate(k < 0 ? 2 : k / 2, k >= 0 && k % 2 == 1);
                default -> DuctMesh.duct(c);
            }));
        }

        private List<BakedQuad> bake(List<DuctMesh.Quad> mesh) {
            BakedQuad[] outq = new BakedQuad[mesh.size()];
            QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
            for (int qi = 0; qi < mesh.size(); qi++) {
                DuctMesh.Quad q = mesh.get(qi);
                TextureAtlasSprite s = sprites[q.mat];
                double[] n0 = q.nrm[0];
                b.setSprite(s);
                b.setDirection(Direction.getNearest((float) n0[0], (float) n0[1], (float) n0[2]));
                b.setShade(false);
                b.setHasAmbientOcclusion(false);
                b.setTintIndex(-1);
                for (int i = 0; i < 4; i++) {
                    double[] p = q.pos[i], n = q.nrm[i], uv = q.uv[i];
                    double br = n[0] * n[0] * 0.6 + n[2] * n[2] * 0.8 + n[1] * n[1] * (n[1] > 0 ? 1.0 : 0.5);
                    int c = q.mat == DuctMesh.SCREEN ? 255 : (int) Math.round(Math.min(1.0, br) * 255);
                    b.addVertex((float) (p[0] / 16.0), (float) (p[1] / 16.0), (float) (p[2] / 16.0));
                    b.setColor(c, c, c, 255);
                    b.setUv(s.getU0() + (s.getU1() - s.getU0()) * (float) uv[0], s.getV0() + (s.getV1() - s.getV0()) * (float) uv[1]);
                    b.setUv2(0, 0);
                    b.setNormal((float) n[0], (float) n[1], (float) n[2]);
                }
                outq[qi] = b.bakeQuad();
            }
            return List.of(outq);
        }

        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) { return TRANSLUCENT; }
        @Override public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) { return List.of(RenderTypeHelper.getEntityRenderType(RenderType.translucent(), fabulous)); }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
