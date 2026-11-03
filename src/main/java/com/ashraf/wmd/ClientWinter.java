package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWinter {

    private ClientWinter() {}

    static double k() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return 0.0;
        long s = NuclearWinter.cStart, e = NuclearWinter.cEnd;
        double t = mc.level.getGameTime();
        if (e <= s || t >= e + Balance.WINTER_FADE_TICKS || t < s) return 0.0;
        double in = Math.min(1.0, (t - s) / Balance.WINTER_FADE_TICKS), out = t > e ? 1.0 - (t - e) / Balance.WINTER_FADE_TICKS : 1.0;
        return Math.max(0.0, Math.min(in, out));
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        double k = k();
        if (k <= 0.01 || event.getType() != net.minecraft.world.level.material.FogType.NONE || ClientCutscene.active()) return;
        float far = (float) Math.min(event.getFarPlaneDistance(), event.getFarPlaneDistance() * (1.0 - k) + Balance.WINTER_FOG * k);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(far * 0.2F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        float k = (float) k();
        if (k <= 0.01F || ClientCutscene.active()) return;
        float grey = 0.42F;
        event.setRed(event.getRed() * (1.0F - k) + grey * k);
        event.setGreen(event.getGreen() * (1.0F - k) + grey * k);
        event.setBlue(event.getBlue() * (1.0F - k) + (grey + 0.03F) * k);
    }

    private static final class Wind extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
        private float sky = 1.0F;
        Wind() {
            super(WmdSounds.WI_WIND_LOOP.get(), net.minecraft.sounds.SoundSource.AMBIENT, net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom());
            looping = true; delay = 0; volume = 0.01F; relative = true;
            attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE;
        }
        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            double k = k();
            if (k <= 0.02 || mc.player == null || mc.level == null) { stop(); wind = null; return; }
            boolean open = mc.level.canSeeSky(mc.player.blockPosition().above());
            sky += ((open ? 1.0F : 0.25F) - sky) * 0.05F;
            volume = (float) (0.7 * k) * sky;
        }
    }

    private static Wind wind;

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        double k = k();
        if (wind != null && !mc.getSoundManager().isActive(wind)) wind = null;
        if (k > 0.05 && wind == null && mc.player != null) { wind = new Wind(); mc.getSoundManager().play(wind); }
        if (k <= 0.01 || mc.level == null || mc.player == null || mc.isPaused()) return;
        var r = mc.level.random;
        int n = (int) (12 * k);
        for (int i = 0; i < n; i++) {
            double x = mc.player.getX() + (r.nextDouble() - 0.5) * 32.0, z = mc.player.getZ() + (r.nextDouble() - 0.5) * 32.0, y = mc.player.getY() + 6.0 + r.nextDouble() * 10.0;
            if (mc.level.canSeeSky(net.minecraft.core.BlockPos.containing(x, y, z)))
                mc.level.addParticle(i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE, x, y, z, 0.02, -0.06, 0.01);
        }
    }
}
