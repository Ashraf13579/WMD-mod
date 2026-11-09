package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class TorpedoNetGhost {

    private TorpedoNetGhost() {}

    private static BlockPos lastA, lastB;
    private static long lastCheck;
    private static TorpedoNetItem.Plan last;

    @SubscribeEvent
    static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) return;
        ItemStack st = p.getMainHandItem().getItem() instanceof TorpedoNetItem ? p.getMainHandItem() : p.getOffhandItem();
        BlockPos a = TorpedoNetItem.corner(st, level);
        if (a == null) { last = null; lastA = null; return; }
        BlockPos b = TorpedoNetItem.aim(level, p);
        long now = level.getGameTime();
        if (b != null && (!b.equals(lastB) || !a.equals(lastA) || now - lastCheck >= 10 || last == null)) {
            last = TorpedoNetItem.check(level, p, a, b, false);
            lastA = a; lastB = b; lastCheck = now;
            p.displayClientMessage(last.message(), true);
        }
        Vec3 c = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(ps, vc, new AABB(a).inflate(0.03).move(-c.x, -c.y, -c.z), 1.0F, 0.82F, 0.35F, 1.0F);
        if (b != null && last != null && b.equals(lastB)) {
            boolean ok = last.ok();
            float r = ok ? 0.37F : 1.0F, g = ok ? 0.69F : 0.3F, bl = ok ? 1.0F : 0.25F;
            AABB cells = AABB.encapsulatingFullBlocks(last.lo(), last.hi());
            LevelRenderer.renderLineBox(ps, vc, cells.inflate(0.01).move(-c.x, -c.y, -c.z), r, g, bl, 0.9F);
            if (last.axis() != null) {
                AABB plane = last.axis() == Direction.Axis.X ? new AABB(cells.minX, cells.minY, cells.minZ + 0.47, cells.maxX, cells.maxY, cells.minZ + 0.53)
                        : new AABB(cells.minX + 0.47, cells.minY, cells.minZ, cells.minX + 0.53, cells.maxY, cells.maxZ);
                LevelRenderer.renderLineBox(ps, vc, plane.move(-c.x, -c.y, -c.z), r, g, bl, 0.6F);
            }
        }
        buf.endBatch(RenderType.lines());
    }
}
