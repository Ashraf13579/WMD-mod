package com.ashraf.wmd;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class NavalReactorRenderer extends StagedUnitRenderer<NavalReactorBlockEntity> {

    private static final Set<String> BONES = new HashSet<>();
    static {
        BONES.add("core"); BONES.add("band"); BONES.add("water"); BONES.add("glass");
        for (int k = 0; k < 12; k++) BONES.add("light" + k);
        for (int k = 0; k < 4; k++) BONES.add("strip" + k);
    }
    private static final int GLASS_ALPHA = 70, GLASS_TINT = 0xC8E6FF;

    public NavalReactorRenderer() { super("naval_reactor", 3, 2); }

    @Override protected Set<String> attachBones() { return BONES; }
    @Override protected boolean drawsItself(String bone) { return BONES.contains(bone); }

    @Override
    protected void drawAttached(NavalReactorBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel mesh = MeshModel.named("naval_reactor");
        double tt = (be.getLevel() != null ? be.getLevel().getGameTime() : 0);
        coloured(mesh, "core", be.coreColour(tt), poses, normals, buffers, light, overlay);
        coloured(mesh, "band", be.bandColour(tt), poses, normals, buffers, light, overlay);
        for (int k = 0; k < 12; k++) coloured(mesh, "light" + k, be.lightColour(k, tt), poses, normals, buffers, light, overlay);
        int strip = be.stripColour(tt);
        for (int k = 0; k < 4; k++) coloured(mesh, "strip" + k, strip, poses, normals, buffers, light, overlay);
        int wt = be.waterTint();
        tinted(mesh, "water", wt & 0xFFFFFF, wt >>> 24, poses, normals, buffers, light, overlay);
        tinted(mesh, "glass", GLASS_TINT, GLASS_ALPHA, poses, normals, buffers, light, overlay);
    }

    private static PoseStack at(String bone, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals) {
        Matrix4f m = poses.get(bone);
        if (m == null) return null;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get(bone));
        return ps;
    }

    private static void coloured(MeshModel mesh, String bone, int rgb, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        PoseStack ps = at(bone, poses, normals);
        if (ps != null) mesh.drawColoured(bone, ps.last(), buffers, light, overlay, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255);
    }

    private static void tinted(MeshModel mesh, String bone, int rgb, int alpha, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        PoseStack ps = at(bone, poses, normals);
        if (ps != null) mesh.drawTinted(bone, ps.last(), buffers, light, overlay, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, alpha, false);
    }
}
