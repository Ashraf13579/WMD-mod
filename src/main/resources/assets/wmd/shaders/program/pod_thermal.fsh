#version 150

// The targeting pod's WHITE-HOT thermal picture (a post-shader filter).
// The scene becomes a soft grey-green: bright + warm things (fire, lava, lamps, engine plumes) run hot, blue sky and water run cold; a little sensor
// noise and a vignette. Living things the lens sees are drawn as white outlines by the vanilla glow outline (ClientPodThermal + GlowMixin).

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform float Time;

out vec4 fragColor;

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    vec3 b = (texture(DiffuseSampler, texCoord + vec2(oneTexel.x, 0.0)).rgb + texture(DiffuseSampler, texCoord - vec2(oneTexel.x, 0.0)).rgb
            + texture(DiffuseSampler, texCoord + vec2(0.0, oneTexel.y)).rgb + texture(DiffuseSampler, texCoord - vec2(0.0, oneTexel.y)).rgb) * 0.25;
    vec3 s = mix(c, b, 0.5);                                       // thermal sensors are soft
    float l = luma(s);
    float warm = clamp((s.r - s.b) * 1.5, 0.0, 1.0);               // red / orange / yellow
    float cold = clamp((s.b - s.r) * 2.0, 0.0, 1.0);               // sky, water, ice
    float glow = smoothstep(0.78, 1.0, max(s.r, max(s.g, s.b)));   // lamps, fire, plumes
    float heat = 0.14 + l * 0.42 + warm * l * 0.35 + glow * 0.55 - cold * 0.22;
    float n = fract(sin(dot(texCoord * 731.0 + vec2(Time * 13.0, Time * 7.0), vec2(12.9898, 78.233))) * 43758.5453);
    heat += (n - 0.5) * 0.045;
    vec2 d = texCoord - 0.5;
    heat *= 1.0 - dot(d, d) * 0.7;
    heat = clamp(heat, 0.0, 1.0);
    fragColor = vec4(vec3(heat) * vec3(0.90, 1.0, 0.92), 1.0);
}
