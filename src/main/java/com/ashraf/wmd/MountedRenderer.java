package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class MountedRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {

    private final BlockEntityRenderer<T> inner;

    private MountedRenderer(BlockEntityRenderer<T> inner) { this.inner = inner; }

    public static <T extends BlockEntity> void register(EntityRenderersEvent.RegisterRenderers event, BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
        event.registerBlockEntityRenderer(type, ctx -> new MountedRenderer<>(provider.create(ctx)));
    }

    @Override
    public void render(T be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (Machines.mount(be.getBlockState()) == Direction.UP) { inner.render(be, partialTick, ps, buffers, light, overlay); return; }
        ps.pushPose();
        MountRender.apply(ps, be.getBlockState());
        inner.render(be, partialTick, ps, buffers, light, overlay);
        ps.popPose();
    }

    @Override public boolean shouldRenderOffScreen(T be) { return inner.shouldRenderOffScreen(be) || spansCells(getRenderBoundingBox(be)); }

    static boolean spansCells(AABB b) { return b.getXsize() > 1.01 || b.getYsize() > 1.01 || b.getZsize() > 1.01; }
    @Override public int getViewDistance() { return inner.getViewDistance(); }
    @Override public boolean shouldRender(T be, Vec3 cam) { return inner.shouldRender(be, cam); }

    @Override
    public AABB getRenderBoundingBox(T be) {
        AABB b = inner.getRenderBoundingBox(be);
        Direction m = Machines.mount(be.getBlockState());
        if (m == Direction.UP || b == AABB.INFINITE || !Double.isFinite(b.getSize())) return b;
        Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double[] v = Machines.mountTurn(m, ((i & 1) == 0 ? b.minX : b.maxX) - c.x, ((i & 2) == 0 ? b.minY : b.maxY) - c.y, ((i & 4) == 0 ? b.minZ : b.maxZ) - c.z);
            x0 = Math.min(x0, v[0]); y0 = Math.min(y0, v[1]); z0 = Math.min(z0, v[2]);
            x1 = Math.max(x1, v[0]); y1 = Math.max(y1, v[1]); z1 = Math.max(z1, v[2]);
        }
        return new AABB(c.x + x0, c.y + y0, c.z + z0, c.x + x1, c.y + y1, c.z + z1);
    }
}
