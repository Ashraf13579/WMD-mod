package com.ashraf.wmd;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SMOKE_DENSITY = BUILDER
            .comment("How much launch smoke to draw. 1.0 = full, 0.3 = light (for slower PCs), 2.0 = extreme.")
            .defineInRange("smokeDensity", 1.0, 0.1, 2.0);

    public static final ModConfigSpec.BooleanValue HEMP_GLITCH = BUILDER
            .comment("HEMP strike: a short static / tear glitch over the screen when the pulse hits (false = off, for players who dislike screen effects).")
            .define("hempScreenGlitch", true);

    public static final ModConfigSpec.BooleanValue EXPLOSION_SCREEN = BUILDER
            .comment("Explosions: a white flash over the screen close to a blast and when the pressure wave hits you (false = off; the camera jolt stays).")
            .define("explosionScreenEffects", true);

    public static final ModConfigSpec.BooleanValue GHOST_PREVIEW = BUILDER
            .comment("Holding a big machine: show where it will go (outline, red blocked cells, front arrow, ports) before you place it.")
            .define("ghostPreview", true);

    public static final ModConfigSpec.BooleanValue PORT_OVERLAY = BUILDER
            .comment("Holding a fluid pipe, busbar or wrench: machines within 16 blocks show their ports in the world (blue = in, orange = out, cyan = power).")
            .define("portOverlay", false);

    public enum HoloDetail { LOW, MEDIUM, HIGH }

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE = BUILDER
            .comment("Camera shake from launches, blasts, guns and quakes: 1.0 = full, 0.5 = half, 0 = none.")
            .defineInRange("cameraShake", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FAR_BOOM_VOLUME = BUILDER
            .comment("Volume of explosions heard from far away (more than ~150 blocks): 1.0 = full, 0 = silent. Near blasts are not changed.")
            .defineInRange("farBoomVolume", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.EnumValue<HoloDetail> HOLOGRAM_DETAIL = BUILDER
            .comment("Command table hologram terrain: HIGH = 1 cell per block (128 x 128), MEDIUM = 2 blocks per cell, LOW = 4 blocks per cell (for slower PCs).")
            .defineEnum("hologramDetail", HoloDetail.HIGH);

    public static final ModConfigSpec.BooleanValue DOME_PILLARS = BUILDER
            .comment("Iron Dome: draw the tall glowing marker pillars (false = only the ground rings).")
            .define("domePillars", true);

    public static final ModConfigSpec.IntValue TRAIL_DRAW_DISTANCE = BUILDER
            .comment("Smoke trails of missiles, interceptors, cruise missiles, shells and rockets are drawn up to this many blocks away.")
            .defineInRange("trailDrawDistance", 700, 64, 2000);

    public static final ModConfigSpec.BooleanValue MISSILE_CAM_HUD = BUILDER
            .comment("Missile camera: show the HUD (reticle, altitude / speed / time, target). false = the clean camera image (the back hint stays).")
            .define("missileCamHud", true);

    public static final ModConfigSpec.BooleanValue STRIKE_TOAST = BUILDER
            .comment("Strike result card: when your strike / salvo / fire mission is over, a short card shows hits, intercepted rounds and the damage report state.")
            .define("strikeToast", true);

    public static final ModConfigSpec.BooleanValue NEW_SOUNDS = BUILDER
            .comment("Launch, artillery, flight, sonic boom, interface and impact sounds: true = the recorded WMD set (rocket motors per launch pad, guns, whistles, sonic boom, button clicks, HEMP / ASAT / drone / interceptor impacts), false = the older vanilla-based sounds.")
            .define("recordedLaunchSounds", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean newSounds() { return !SPEC.isLoaded() || NEW_SOUNDS.get(); }

    public static boolean hempGlitch() { return !SPEC.isLoaded() || HEMP_GLITCH.get(); }

    public static boolean explosionScreen() { return !SPEC.isLoaded() || EXPLOSION_SCREEN.get(); }

    private ClientConfig() {}

    public static float cameraShake() { return SPEC.isLoaded() ? CAMERA_SHAKE.get().floatValue() : 1.0F; }

    public static float farBoom(double d) {
        float v = SPEC.isLoaded() ? FAR_BOOM_VOLUME.get().floatValue() : 1.0F;
        if (v >= 1.0F || d <= 100.0) return 1.0F;
        if (d >= 200.0) return v;
        float k = (float) ((d - 100.0) / 100.0);
        return 1.0F + (v - 1.0F) * k;
    }

    public static int holoStep() {
        HoloDetail d = SPEC.isLoaded() ? HOLOGRAM_DETAIL.get() : HoloDetail.HIGH;
        return d == HoloDetail.LOW ? 4 : d == HoloDetail.MEDIUM ? 2 : 1;
    }

    public static boolean domePillars() { return !SPEC.isLoaded() || DOME_PILLARS.get(); }

    public static double trailRange() { return SPEC.isLoaded() ? TRAIL_DRAW_DISTANCE.get() : 700.0; }

    public static boolean missileCamHud() { return !SPEC.isLoaded() || MISSILE_CAM_HUD.get(); }

    public static boolean strikeToast() { return !SPEC.isLoaded() || STRIKE_TOAST.get(); }

    public static int count(float base, net.minecraft.util.RandomSource random) {
        double d = SPEC.isLoaded() ? SMOKE_DENSITY.get() : 1.0;
        double n = base * d;
        int whole = (int) n;
        return whole + (random.nextDouble() < n - whole ? 1 : 0);
    }
}
