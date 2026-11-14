package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class AirStoreMeshes {

    private AirStoreMeshes() {}

    static boolean draw(int kind, PoseStack ps, MultiBufferSource buffers, int light) { return draw(kind, ps, buffers, light, false); }

    static boolean draw(int kind, PoseStack ps, MultiBufferSource buffers, int light, boolean inBay) {
        if (kind == AirStores.ASHM) { ClientAshm.drawStore(ps, buffers, light, inBay); return true; }
        if (!AirStores.isDrop(kind)) return false;
        BombType bt = AirStores.bomb(kind);
        if (bt != null) {
            MeshModel bm = MeshModel.named(bt.mesh);
            if (!bm.has("part")) return true;
            ps.pushPose();
            ps.translate(0.0F, (inBay ? 0.0F : -bt.radius) / 16.0F, bt.length / 2.0F / 16.0F);
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
            BombRenderer.drawWhole(bm, ps.last(), buffers, light);
            if (bt == BombType.GLIDE) { bm.draw("wing_l", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY); bm.draw("wing_r", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY); }
            ps.popPose();
            return true;
        }
        TorpedoKind tk = AirStores.torpedo(kind);
        if (tk != null) {
            MeshModel tm = MeshModel.named(tk.mesh());
            if (!tm.has("part")) return true;
            ps.pushPose();
            ps.translate(0.0F, -(inBay ? 1.0F : 2.0F) * tk.axisPx() / 16.0F, 0.0F);
            TorpedoRenderer.draw(tm, tk, ps.last(), buffers, light);
            ps.popPose();
            return true;
        }
        MeshModel mm = MeshModel.named(kind == AirStores.SONOBUOY ? "sonobuoy" : "depth_charge");
        if (!mm.has("part")) return true;
        ps.pushPose();
        if (kind == AirStores.DEPTH_CHARGE) {
            ps.translate(0.0F, -5.0F / 16.0F, 0.0F);
            ps.mulPose(Axis.YP.rotationDegrees(90.0F));
            ps.translate(0.0F, -5.0F / 16.0F, 0.0F);
        } else {
            ps.translate(0.0F, -3.0F / 16.0F, 0.0F);
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        }
        mm.draw("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        return true;
    }
}
