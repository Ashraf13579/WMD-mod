package com.ashraf.wmd;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class WmdSmokeParticle extends TextureSheetParticle {

    public enum Kind { BANK, TRAIL, FLAME, VAPOUR, GAS, SPARK, SPRAY, DUST }

    private final Kind kind;
    private float startSize, endSize;
    private final float maxAlpha;
    private final float shade;

    static float nextSize = 1.0F;

    protected WmdSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        pickSprite(sprites);
        this.roll = (random.nextFloat() - 0.5F) * 0.6F;
        this.oRoll = roll;
        switch (kind) {
            case BANK -> {
                lifetime = 260 + random.nextInt(220);
                startSize = 1.6F + random.nextFloat() * 1.2F;
                endSize = 5.5F + random.nextFloat() * 3.5F;
                maxAlpha = 0.92F;
                friction = 0.955F;
                gravity = -0.0015F;
                hasPhysics = true;
                shade = 0.62F + random.nextFloat() * 0.22F;
            }
            case TRAIL -> {
                lifetime = 300 + random.nextInt(200);
                startSize = 1.2F + random.nextFloat() * 0.8F;
                endSize = 4.0F + random.nextFloat() * 3.0F;
                maxAlpha = 0.85F;
                friction = 0.92F;
                gravity = -0.0008F;
                hasPhysics = false;
                shade = 0.78F + random.nextFloat() * 0.17F;
            }
            case FLAME -> {
                lifetime = 8 + random.nextInt(8);
                startSize = 1.1F + random.nextFloat() * 0.6F;
                endSize = 2.4F + random.nextFloat() * 1.2F;
                maxAlpha = 0.95F;
                friction = 0.86F;
                gravity = 0.0F;
                hasPhysics = false;
                shade = 1.0F;
            }
            case DUST -> {
                lifetime = 600 + random.nextInt(400);
                startSize = 1.8F + random.nextFloat() * 1.4F;
                endSize = 6.5F + random.nextFloat() * 4.5F;
                maxAlpha = 0.85F;
                friction = 0.965F;
                gravity = 0.0004F;
                hasPhysics = true;
                shade = 0.72F + random.nextFloat() * 0.2F;
            }
            case GAS -> {
                lifetime = 220 + random.nextInt(200);
                startSize = 1.6F + random.nextFloat() * 1.2F;
                endSize = 5.5F + random.nextFloat() * 3.5F;
                maxAlpha = 0.88F;
                friction = 0.955F;
                gravity = 0.0006F;
                hasPhysics = true;
                shade = 0.7F + random.nextFloat() * 0.25F;
            }
            case SPARK -> {
                lifetime = 30 + random.nextInt(40);
                startSize = 0.35F + random.nextFloat() * 0.2F;
                endSize = 0.08F;
                maxAlpha = 1.0F;
                friction = 1.0F;
                gravity = 0.0F;
                hasPhysics = false;
                shade = 1.0F;
            }
            case SPRAY -> {
                lifetime = 60 + random.nextInt(40);
                startSize = 1.2F + random.nextFloat() * 0.8F;
                endSize = 3.5F + random.nextFloat() * 2.0F;
                maxAlpha = 0.85F;
                friction = 0.985F;
                gravity = 0.035F / 0.04F;
                hasPhysics = false;
                shade = 0.92F + random.nextFloat() * 0.08F;
            }
            default -> {
                lifetime = 40 + random.nextInt(30);
                startSize = 0.35F + random.nextFloat() * 0.3F;
                endSize = 1.2F + random.nextFloat() * 0.8F;
                maxAlpha = 0.6F;
                friction = 0.9F;
                gravity = -0.002F;
                hasPhysics = false;
                shade = 0.95F;
            }
        }
        float m = nextSize;
        if (m > 1.0F) {
            startSize *= m; endSize *= m;
            if (kind != Kind.FLAME) lifetime = (int) (lifetime * (1.0F + (m - 1.0F) * 0.5F));
            if (kind == Kind.SPARK) endSize = startSize * 0.3F;
        }
        this.quadSize = startSize;
        setColour(0.0F);
        this.alpha = 0.0F;
    }

    private void setColour(float age) {
        if (kind == Kind.FLAME || kind == Kind.SPARK) {

            float r = 1.0F, g = Mth.lerp(age, 0.92F, 0.32F), b = Mth.lerp(age, 0.62F, 0.08F);
            setColor(r, g, b);
        } else {
            float s = shade;
            if (kind == Kind.GAS) { setColor(0.52F * s, 0.78F * s, 0.20F * s); return; }
            if (kind == Kind.DUST) { setColor(0.80F * s, 0.75F * s, 0.66F * s); return; }
            if (kind == Kind.SPRAY) { setColor(0.93F * s, 0.97F * s, 1.0F * s); return; }
            if (kind == Kind.BANK) s = Mth.lerp(Math.min(1.0F, age * 2.0F), shade * 0.82F, shade);
            setColor(s, s, s * 1.02F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        float a = Mth.clamp(age / (float) lifetime, 0.0F, 1.0F);
        float grow = 1.0F - (1.0F - a) * (1.0F - a);
        quadSize = Mth.lerp(grow, startSize, endSize);
        float fadeIn = kind == Kind.FLAME || kind == Kind.SPARK ? 1.0F : Mth.clamp(age / 6.0F, 0.0F, 1.0F);
        float fadeOut = a < 0.55F ? 1.0F : 1.0F - (a - 0.55F) / 0.45F;
        alpha = maxAlpha * fadeIn * Mth.clamp(fadeOut, 0.0F, 1.0F);
        setColour(a);
        if ((kind == Kind.BANK || kind == Kind.GAS) && onGround) {
            xd *= 1.04;
            zd *= 1.04;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        if (kind == Kind.FLAME || kind == Kind.SPARK) return 0xF000F0;

        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) return net.minecraft.client.renderer.LightTexture.pack(0, 15);
        return super.getLightColor(partialTick);
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new WmdSmokeParticle(level, x, y, z, vx, vy, vz, sprites, kind);
        }
    }
}
