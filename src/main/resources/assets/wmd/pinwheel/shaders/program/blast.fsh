#include veil:space_helper

// WMD: blast screen effects (bloom + heat haze + impact shock ring)
uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float VeilRenderTime;

uniform vec4 Shock;        // xyz world position of the impact, w = ring radius (blocks)
uniform vec4 ShockParams;  // x = ring thickness (blocks), y = strength 0..1
uniform vec4 Haze;         // xyz world position of the flame, w = size (blocks)
uniform float HazeStrength;
uniform vec4 Lens;         // 1 Nov: xyz = the gravity well's black hole, w = its horizon radius (blocks)
uniform vec4 LensParams;   // x = the Einstein ring / horizon ratio, y = strength 0..1
uniform float BloomStrength;

in vec2 texCoord;
out vec4 fragColor;

// own helpers (Veil's shortcut macros differ between versions)
vec3 toLocal(vec3 world) { return world - VeilCamera.CameraPosition; }
vec3 toView(vec3 world) { return (VeilCamera.ViewMat * vec4(world - VeilCamera.CameraPosition, 1.0)).xyz; }
vec2 toScreen(vec3 world) {
    vec4 clip = VeilCamera.ProjMat * (VeilCamera.ViewMat * vec4(world - VeilCamera.CameraPosition, 1.0));
    return clip.xy / clip.w * 0.5 + 0.5;
}

float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec2 uv = texCoord;
    float aspect = VeilCamera.ProjMat[1][1] / VeilCamera.ProjMat[0][0];
    float depth = texture(DiffuseDepthSampler, uv).r;
    vec4 ndc = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 vp = VeilCamera.IProjMat * ndc;
    vec3 local = (VeilCamera.IViewMat * (vp / vp.w)).xyz;   // this pixel, relative to the camera

    // ---- shock ring: the picture is pushed outwards where the ring passes
    if (ShockParams.y > 0.001) {
        vec3 c = toLocal(Shock.xyz);
        float d = distance(local, c);
        float x = clamp(abs(d - Shock.w) / max(ShockParams.x, 0.1), 0.0, 1.0);
        float ring = 1.0 - x;
        ring = ring * ring * (3.0 - 2.0 * ring);
        if (toView(Shock.xyz).z < 0.0) {
            vec2 cuv = toScreen(Shock.xyz);
            vec2 away = (uv - cuv) * vec2(aspect, 1.0);
            away = away / max(length(away), 0.0001);
            uv += away * vec2(1.0 / aspect, 1.0) * ring * ShockParams.y * 0.03;
        } else {
            uv.y += ring * ShockParams.y * 0.02;
        }
    }

    // ---- heat haze: hot air wobbling around (and behind) the flame
    if (HazeStrength > 0.001 && Haze.w > 0.0) {
        vec3 fl = toLocal(Haze.xyz);
        vec3 fv = toView(Haze.xyz);
        if (fv.z < -1.0) {
            vec2 fuv = toScreen(Haze.xyz);
            float size = Haze.w / -fv.z * VeilCamera.ProjMat[1][1] * 0.5;   // flame size on the screen (fraction of height)
            vec2 dd = (uv - fuv) * vec2(aspect, 1.0);
            float m = 1.0 - smoothstep(0.0, size * 2.2, length(dd - vec2(0.0, size * 0.6)));
            // only what is behind the flame shimmers, not what stands in front of it
            m *= step(length(fl) - Haze.w, length(local));
            float t = VeilRenderTime;
            vec2 wob = vec2(sin(uv.y * 70.0 - t * 11.0) + sin(uv.y * 31.0 + t * 7.0), cos(uv.x * 55.0 + t * 9.0)) * 0.5;
            uv += wob * m * HazeStrength * 0.006 * vec2(1.0 / aspect, 1.0);
        }
    }

    // ---- 1 Nov: gravitational lensing round the black hole: what is BEHIND it is bent round it (an Einstein ring), the hole itself stays black
    if (LensParams.y > 0.001 && Lens.w > 0.0) {
        vec3 lv = toView(Lens.xyz);
        if (lv.z < -0.5 && length(local) > length(toLocal(Lens.xyz)) - Lens.w) {
            vec2 luv = toScreen(Lens.xyz);
            float rs = Lens.w / -lv.z * VeilCamera.ProjMat[1][1] * 0.5;
            vec2 dd = (uv - luv) * vec2(aspect, 1.0);
            float d = length(dd);
            float re = rs * LensParams.x;
            if (d > rs && d < re * 8.0) {
                float dp = d - re * re / d;
                float edge = rs * 1.15;
                if (dp < edge) dp = edge + (edge - dp);
                float fade = (1.0 - smoothstep(re * 4.0, re * 8.0, d)) * LensParams.y;
                vec2 dir = dd / d;
                uv = luv + dir * mix(d, dp, fade) * vec2(1.0 / aspect, 1.0);
            }
        }
    }

    vec3 base = texture(DiffuseSampler0, uv).rgb;

    // ---- bloom: bright pixels spill light over their surroundings
    if (BloomStrength > 0.01) {
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 24; i++) {
            float a = float(i) * 2.39996;
            float r = sqrt((float(i) + 0.5) / 24.0);
            vec2 o = vec2(cos(a), sin(a)) * r * 0.03 * vec2(1.0 / aspect, 1.0);
            vec3 s = texture(DiffuseSampler0, uv + o).rgb;
            glow += s * smoothstep(0.72, 1.0, lum(s)) * (1.0 - r * 0.5);
        }
        vec3 wide = vec3(0.0);
        for (int i = 0; i < 16; i++) {
            float a = float(i) * 2.39996 + 1.0;
            float r = sqrt((float(i) + 0.5) / 16.0);
            vec2 o = vec2(cos(a), sin(a)) * r * 0.09 * vec2(1.0 / aspect, 1.0);
            vec3 s = texture(DiffuseSampler0, uv + o).rgb;
            wide += s * smoothstep(0.72, 1.0, lum(s)) * (1.0 - r * 0.5);
        }
        base += (glow / 24.0 * 1.4 + wide / 16.0 * 1.0) * BloomStrength;
    }

    fragColor = vec4(base, 1.0);
}
