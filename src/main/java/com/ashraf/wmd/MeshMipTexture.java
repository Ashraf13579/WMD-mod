package com.ashraf.wmd;

import java.io.IOException;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

final class MeshMipTexture extends SimpleTexture {

    MeshMipTexture(ResourceLocation location) { super(location); }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        TextureImage ti = getTextureImage(resourceManager);
        ti.throwIfError();
        NativeImage img = ti.getImage();
        int lv = Math.max(0, Math.min(Balance.MESH_MIP_LEVELS, Integer.numberOfTrailingZeros(Math.min(img.getWidth(), img.getHeight()))));
        NativeImage[] mips = lv > 0 ? MipmapGenerator.generateMipLevels(new NativeImage[] { img }, lv) : new NativeImage[] { img };
        if (!RenderSystem.isOnRenderThreadOrInit()) RenderSystem.recordRenderCall(() -> upload(mips));
        else upload(mips);
    }

    private volatile int levels;

    @Override
    public void setFilter(boolean blur, boolean mipmap) { super.setFilter(blur, mipmap || levels > 0); }

    private void upload(NativeImage[] mips) {
        levels = mips.length - 1;
        int w = mips[0].getWidth(), h = mips[0].getHeight();
        TextureUtil.prepareImage(getId(), mips.length - 1, w, h);
        for (int i = 0; i < mips.length; i++) mips[i].upload(i, 0, 0, 0, 0, w >> i, h >> i, false, false, mips.length > 1, true);
    }
}
