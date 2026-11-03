package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class CrewCapsuleRenderer implements BlockEntityRenderer<CrewCapsuleBlockEntity> {

    @Override
    public void render(CrewCapsuleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Direction f = be.getBlockState().hasProperty(MachineBlock.FACING) ? be.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        MeshModel mesh = MeshModel.named(be.landed() ? "crew_capsule_landed" : "crew_capsule");
        if (!mesh.has("capsule")) return;
        ps.pushPose();
        int port = be.boardPort();
        if (Boarding.side(port)) {
            CargoPodRenderer.portFrame(ps, Station.spotDir(port));
            ps.translate(0.75F, 1.5F, 0.0F);
            ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
        } else {
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case SOUTH -> 180.0F; case WEST -> 90.0F; case EAST -> 270.0F; default -> 0.0F; }));
        }
        var lv = be.getLevel();
        if (lv != null && be.boardSlot() >= 0 && !be.cutDone(lv.getGameTime()) && lv.random.nextFloat() < 0.5F) {
            var n = Boarding.side(port) ? Station.spotDir(port).getNormal() : new net.minecraft.core.Vec3i(0, -1, 0);
            var c = be.getBlockPos();
            boolean sd = Boarding.side(port);
            double ox = sd ? -n.getX() * 1.0 : 0.0, oz = sd ? -n.getZ() * 1.0 : 0.0, oy = sd ? 1.5 : 2.6;
            double a = lv.random.nextDouble() * Math.PI * 2.0;
            lv.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, c.getX() + 0.5 + ox + Math.cos(a) * 0.6, c.getY() + oy + Math.sin(a) * 0.6, c.getZ() + 0.5 + oz + Math.sin(a) * 0.6,
                    (lv.random.nextDouble() - 0.5) * 0.4, (lv.random.nextDouble() - 0.5) * 0.4, (lv.random.nextDouble() - 0.5) * 0.4);
        }
        mesh.draw("capsule", ps.last(), buffers, light, overlay);
        if (mesh.has("hatch")) mesh.draw("hatch", ps.last(), buffers, light, overlay);
        ps.popPose();
    }

    @Override public boolean shouldRenderOffScreen(CrewCapsuleBlockEntity be) { return true; }
    @Override public AABB getRenderBoundingBox(CrewCapsuleBlockEntity be) { return new AABB(be.getBlockPos()).inflate(2.0, 3.0, 2.0); }
    @Override public int getViewDistance() { return 256; }
}
