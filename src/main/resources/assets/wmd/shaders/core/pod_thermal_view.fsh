#version 150

// The targeting pod's WHITE-HOT thermal picture (ClientPodThermal; 19 Nov: our own screen pass, so it also runs under a shader pack).
// Sampler0 = a copy of the finished frame (after the pack drew it): it becomes a soft grey-green, bright + warm things (fire, lava, lamps, plumes)
// run hot, blue sky and water run cold, sensor noise, a vignette. Sampler1 = the HEAT MASK (white where a warm body the lens sees is): those bodies
// burn white with a soft halo. HeatOn 0 = no mask this frame (only the picture).

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform float GameTime;
uniform int HeatOn;

in vec2 texCoord;

out vec4 fragColor;

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    vec3 c = texture(Sampler0, texCoord).rgb;
    vec3 b = (texture(Sampler0, texCoord + vec2(px.x, 0.0)).rgb + texture(Sampler0, texCoord - vec2(px.x, 0.0)).rgb
            + texture(Sampler0, texCoord + vec2(0.0, px.y)).rgb + texture(Sampler0, texCoord - vec2(0.0, px.y)).rgb) * 0.25;
    vec3 s = mix(c, b, 0.5);                                       // thermal sensors are soft
    float l = luma(s);
    float warm = clamp((s.r - s.b) * 1.5, 0.0, 1.0);               // red / orange / yellow
    float cold = clamp((s.b - s.r) * 2.0, 0.0, 1.0);               // sky, water, ice
    float glow = smoothstep(0.78, 1.0, max(s.r, max(s.g, s.b)));   // lamps, fire, plumes
    float heat = 0.14 + l * 0.42 + warm * l * 0.35 + glow * 0.55 - cold * 0.22;
    if (HeatOn != 0) {
        vec2 hp = 1.0 / vec2(textureSize(Sampler1, 0));
        float body = texture(Sampler1, texCoord).r;
        float halo = 0.0;
        for (int i = 0; i < 8; i++) {                               // a soft rim round each body (warm air)
            float a = float(i) * 0.785398;
            halo += texture(Sampler1, texCoord + vec2(cos(a), sin(a)) * hp * 3.0).r;
        }
        halo *= 0.125;
        heat = mix(heat, 0.84 + l * 0.16, body);                    // white-hot, a little detail left inside
        heat += halo * (1.0 - body) * 0.22;
    }
    float t = floor(GameTime * 24000.0 * 1.5);                      // the sensor noise changes ~30 times a second
    float n = fract(sin(dot(floor(texCoord / px), vec2(12.9898, 78.233)) + t * 37.719) * 43758.5453);
    heat += (n - 0.5) * 0.045;
    vec2 d = texCoord - 0.5;
    heat *= 1.0 - dot(d, d) * 0.7;
    heat = clamp(heat, 0.0, 1.0);
    fragColor = vec4(vec3(heat) * vec3(0.90, 1.0, 0.92), 1.0);
}
