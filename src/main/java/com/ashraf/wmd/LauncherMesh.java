package com.ashraf.wmd;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

public final class LauncherMesh {

    private LauncherMesh() {}

    public static boolean has(String bone) { return MeshModel.LAUNCHER.has(bone); }

    public static void draw(String bone, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) { MeshModel.LAUNCHER.draw(bone, pose, buffers, light, overlay); }
}
