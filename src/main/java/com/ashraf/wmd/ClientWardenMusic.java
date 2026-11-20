package com.ashraf.wmd;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Wmd.MODID, value = Dist.CLIENT)
public final class ClientWardenMusic {

    private ClientWardenMusic() {}

    private static final int KEEP = 600, FADE_IN = 30, FADE_OUT = 40, FADE_CUT = 10;

    private static Track cur;
    private static int pendingLoop = -1, ticks, heard;

    private static boolean loop(int t) { return t >= 1 && t <= 3; }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            WardenMusicPayload.PENDING.clear();
            if (cur != null) { mc.getSoundManager().stop(cur); cur = null; }
            pendingLoop = -1;
            return;
        }
        ticks++;
        for (WardenMusicPayload p; (p = WardenMusicPayload.PENDING.poll()) != null; ) arrive(mc, p.track());
        if (cur != null && cur.ended(mc)) cur = null;
        if (cur == null && pendingLoop >= 0 && ticks - heard < KEEP) start(mc, pendingLoop, FADE_IN);
        if (cur != null && cur.loop && ticks - heard > KEEP) { cur.fadeOut(FADE_OUT); pendingLoop = -1; }
        if (cur != null && !cur.fading) mc.getMusicManager().stopPlaying();
    }

    private static void arrive(Minecraft mc, int t) {
        if (t < 0 || t >= WardenMusic.TRACKS.size()) return;
        if (!ClientConfig.wardenMusic()) {
            if (cur != null) cur.fadeOut(FADE_OUT);
            pendingLoop = -1;
            return;
        }
        heard = ticks;
        if (loop(t)) {
            pendingLoop = t;
            if (cur != null && !cur.fading && (cur.track == t || !cur.loop)) return;
            if (cur != null) cur.fadeOut(FADE_OUT);
            start(mc, t, FADE_IN);
        } else {
            pendingLoop = -1;
            if (cur != null) cur.fadeOut(FADE_CUT);
            start(mc, t, 0);
        }
    }

    private static void start(Minecraft mc, int t, int fadeIn) {
        cur = new Track(t, loop(t), fadeIn);
        mc.getMusicManager().stopPlaying();
        mc.getSoundManager().play(cur);
    }

    private static final class Track extends AbstractTickableSoundInstance {
        final int track;
        final boolean loop;
        private final int fadeIn;
        private int age, outLeft, outLen;
        boolean fading;

        Track(int track, boolean loop, int fadeIn) {
            super(WmdSounds.WARDEN_MUSIC.get(track).get(), SoundSource.MUSIC, RandomSource.create());
            this.track = track;
            this.loop = loop;
            this.fadeIn = fadeIn;
            this.looping = loop;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0.0; this.y = 0.0; this.z = 0.0;
            this.volume = fadeIn > 0 ? 0.01F : 1.0F;
        }

        void fadeOut(int len) {
            if (fading) return;
            fading = true;
            outLen = outLeft = Math.max(1, len);
        }

        boolean ended(Minecraft mc) { return isStopped() || (age > 40 && !mc.getSoundManager().isActive(this)); }

        @Override
        public void tick() {
            age++;
            float v = fadeIn > 0 ? Math.min(1.0F, age / (float) fadeIn) : 1.0F;
            if (fading) {
                v *= --outLeft / (float) outLen;
                if (outLeft <= 0) { stop(); return; }
            }
            volume = Math.max(0.01F, v);
        }
    }
}
