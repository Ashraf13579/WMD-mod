package com.ashraf.wmd;

import java.util.Map;
import java.util.Set;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

public class WeaponsBayRenderer extends StagedUnitRenderer<WeaponsBayBlockEntity> {

    private static final Set<String> SKINNED = Set.of("housing", "housing_dl", "housing_dr");
    private static final Set<String> ATTACH = Set.of("housing", "housing_dl", "housing_dr", "store");
    private BlockState skin;

    public WeaponsBayRenderer() { super("weapons_bay", 1, 4); }

    @Override protected Set<String> attachBones() { return ATTACH; }
    @Override protected boolean drawsItself(String bone) { return skin != null && SKINNED.contains(bone); }

    @Override
    public void render(WeaponsBayBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        skin = be.copycat().shown();
        try {
            super.render(be, partialTick, poseStack, buffers, light, overlay);
        } finally {
            skin = null;
        }
    }

    @Override
    protected void drawAttached(WeaponsBayBlockEntity be, Map<String, Matrix4f> poses, Map<String, Matrix3f> normals, MultiBufferSource buffers, int light, int overlay) {
        MeshModel bay = MeshModel.named("weapons_bay");
        if (skin != null) for (String b : SKINNED) if (poses.containsKey(b))
            CopycatSkin.draw(bay, b, poses.get(b), normals.get(b), skin, be.getLevel(), be.getBlockPos(), buffers, light, overlay);
        Matrix4f m = poses.get("store");
        int kind = be.loadedShown();
        if (m != null && AirStores.isDrop(kind)) {
            PoseStack dp = new PoseStack();
            dp.last().pose().set(m);
            dp.last().normal().set(normals.get("store"));
            dp.translate(0.0F, WeaponsBayBlockEntity.STORE_Y / 16.0F, WeaponsBayBlockEntity.STORE_Z / 16.0F);
            AirStoreMeshes.draw(kind, dp, buffers, light, true);
            return;
        }
        String mesh = kind < 0 ? null : AamItem.mesh(kind);
        if (m == null || mesh == null) return;
        MeshModel mm = MeshModel.named(mesh);
        if (!mm.has("part")) return;
        PoseStack ps = new PoseStack();
        ps.last().pose().set(m);
        ps.last().normal().set(normals.get("store"));
        ps.translate(0.0F, (WeaponsBayBlockEntity.STORE_Y - AamMeshes.hangPx(mesh)) / 16.0F, WeaponsBayBlockEntity.STORE_Z / 16.0F);
        int[] t = MissileParts.tint(AamItem.paint(kind));
        mm.drawTinted("part", ps.last(), buffers, light, OverlayTexture.NO_OVERLAY, t[0], t[1], t[2], 255, false);
    }
}
