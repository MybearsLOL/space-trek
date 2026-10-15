#version 330 core

// =====================================================================================
// Procedural space sky: stars, Milky Way band, nebulae.
//
// Nothing here is a texture, so it stays sharp at any zoom (spyglass, FOV changes...).
// The view direction is rebuilt every pixel from the camera's right/up/forward vectors,
// so the sky is fixed to the world and rotates correctly as you look around.
// =====================================================================================

uniform float TanHalfFovX;
uniform float TanHalfFovY;

uniform float CamRightX;
uniform float CamRightY;
uniform float CamRightZ;
uniform float CamUpX;
uniform float CamUpY;
uniform float CamUpZ;
uniform float CamForwardX;
uniform float CamForwardY;
uniform float CamForwardZ;

uniform float Time;

uniform float StarDensity;
uniform float StarBrightness;
uniform float StarSize;
uniform float Twinkle;

uniform float BandStrength;
uniform float BandWidth;
uniform float BandNormalX;
uniform float BandNormalY;
uniform float BandNormalZ;
uniform float BandColorAR;
uniform float BandColorAG;
uniform float BandColorAB;
uniform float BandColorBR;
uniform float BandColorBG;
uniform float BandColorBB;

uniform float NebulaStrength;
uniform float NebulaScale;
uniform float NebulaColorAR;
uniform float NebulaColorAG;
uniform float NebulaColorAB;
uniform float NebulaColorBR;
uniform float NebulaColorBG;
uniform float NebulaColorBB;

uniform float Seed;

// 1 = fully covers what is behind (space dimension), lower = fades in over the normal sky
uniform float Opacity;

// ---- turning of the sky (day/night): celestial direction = rotate(world direction, axis, SkyAngle)
uniform float SkyAxisX;
uniform float SkyAxisY;
uniform float SkyAxisZ;
uniform float SkyAngle;

// ---- atmosphere glow pass (GlowOnly > 0.5 draws only the day sky colour, with transparency)
uniform float GlowOnly;
uniform float SunWorldX;
uniform float SunWorldY;
uniform float SunWorldZ;
uniform float AtmoZenithR;
uniform float AtmoZenithG;
uniform float AtmoZenithB;
uniform float AtmoHorizonR;
uniform float AtmoHorizonG;
uniform float AtmoHorizonB;
uniform float AtmoTwilightR;
uniform float AtmoTwilightG;
uniform float AtmoTwilightB;
uniform float AtmoAlpha;     // how much the glow hides what is behind it (0 at night)
uniform float AtmoDay;       // 0 = night, 1 = full day
uniform float AtmoTwilight;  // 1 = sun on the horizon (sunrise / sunset), 0 = far from it
uniform float SunGlow;       // strength of the soft glow around the invisible sun

in vec2 vNdc;
out vec4 fragColor;

// ---------------------------------------------------------------- hashing and noise

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
            mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
        mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
            mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y),
        f.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amplitude * noise(p);
        p = p * 2.02 + vec3(17.1, 3.7, 9.2);
        amplitude *= 0.5;
    }
    return sum;
}

// ---------------------------------------------------------------- stars

// One layer of stars. 'scale' sets how many (bigger = more, smaller stars).
// Every star is a tiny gaussian dot placed by a hash, so there is no resolution limit.
vec3 starLayer(vec3 dir, float scale, float probability, float sizeMul, float brightMul,
               float pixel, float offset, float bandBoost) {
    vec3 p = dir * scale + vec3(offset);
    vec3 baseId = floor(p);
    vec3 total = vec3(0.0);

    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            for (int z = -1; z <= 1; z++) {
                vec3 id = baseId + vec3(float(x), float(y), float(z));

                if (hash13(id + 7.7) > probability * StarDensity * bandBoost) {
                    continue;
                }

                vec3 h = hash33(id);
                vec3 starPos = id + 0.2 + 0.6 * h;
                vec3 starDir = normalize(starPos - vec3(offset));
                float d = length(dir - starDir);

                float magnitude = pow(h.z, 5.0);
                float radius = StarSize * sizeMul * (0.0007 + 0.0016 * magnitude);

                // Never draw a star smaller than a pixel, but fade it so energy is conserved
                float r = max(radius, pixel * 0.9);
                float energy = (radius * radius) / (r * r);

                float spot = exp(-(d * d) / (r * r * 0.5));
                float twinkle = 1.0 - Twinkle * 0.5 * (1.0 + sin(Time * (1.5 + 3.0 * h.x) + h.y * 40.0));
                float intensity = (0.12 + 2.2 * magnitude) * brightMul * StarBrightness * energy * twinkle;

                // Star colour: mostly white, some blue, yellow and red ones
                float t = hash13(id + 13.3);
                vec3 color = vec3(1.0);
                if (t < 0.2) {
                    color = vec3(0.75, 0.85, 1.0);
                } else if (t > 0.9) {
                    color = vec3(1.0, 0.65, 0.45);
                } else if (t > 0.7) {
                    color = vec3(1.0, 0.9, 0.7);
                }

                total += color * spot * intensity;
            }
        }
    }
    return total;
}

// Rotates v around the unit axis k by angle a (Rodrigues' formula)
vec3 rotateAround(vec3 v, vec3 k, float a) {
    float c = cos(a);
    float s = sin(a);
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

void main() {
    vec3 right = vec3(CamRightX, CamRightY, CamRightZ);
    vec3 up = vec3(CamUpX, CamUpY, CamUpZ);
    vec3 forward = vec3(CamForwardX, CamForwardY, CamForwardZ);

    // World-space direction of this pixel
    vec3 dirWorld = normalize(forward + right * (vNdc.x * TanHalfFovX) + up * (vNdc.y * TanHalfFovY));

    // ---- Atmosphere glow pass: the day sky that covers the stars ----
    if (GlowOnly > 0.5) {
        vec3 sunDir = normalize(vec3(SunWorldX, SunWorldY, SunWorldZ));
        vec3 zenith = vec3(AtmoZenithR, AtmoZenithG, AtmoZenithB);
        vec3 horizon = vec3(AtmoHorizonR, AtmoHorizonG, AtmoHorizonB);
        vec3 twilightColor = vec3(AtmoTwilightR, AtmoTwilightG, AtmoTwilightB);

        float upAmount = clamp(dirWorld.y, 0.0, 1.0);
        vec3 glowColor = mix(zenith, horizon, pow(1.0 - upAmount, 3.0));

        // The sky is darker when the sun is low
        glowColor *= mix(0.5, 1.0, AtmoDay);

        // Soft glow around the sun's position (the sun itself is not drawn)
        float sunDot = max(dot(dirWorld, sunDir), 0.0);
        float halo = (pow(sunDot, 6.0) * 0.35 + pow(sunDot, 48.0) * 0.5) * SunGlow;
        glowColor += mix(horizon, twilightColor, AtmoTwilight) * halo;

        // Warm band along the horizon, strongest on the side where the sun is
        vec2 flatDir = normalize(dirWorld.xz + vec2(0.00001));
        vec2 flatSun = normalize(sunDir.xz + vec2(0.00001));
        float towardSun = 0.5 + 0.5 * dot(flatDir, flatSun);
        float horizonBand = pow(1.0 - clamp(abs(dirWorld.y) * 2.5, 0.0, 1.0), 3.0) * towardSun * AtmoTwilight;
        glowColor = mix(glowColor, twilightColor, clamp(horizonBand, 0.0, 1.0));

        float glowAlpha = clamp(AtmoAlpha + horizonBand * 0.55, 0.0, 1.0);
        fragColor = vec4(glowColor, glowAlpha * Opacity);
        return;
    }

    // ---- Space pass: turn the sky with the day/night rotation ----
    vec3 axis = normalize(vec3(SkyAxisX, SkyAxisY, SkyAxisZ) + vec3(0.000001));
    vec3 dir = rotateAround(dirWorld, axis, SkyAngle);

    // How big one pixel is, in direction space (shrinks when you zoom in)
    float pixel = max(length(dFdx(dir)), length(dFdy(dir)));

    float offset = Seed * 13.37;

    // ---- Milky Way band ----
    vec3 bandNormal = normalize(vec3(BandNormalX, BandNormalY, BandNormalZ));
    float lat = dot(dir, bandNormal);
    float width = max(BandWidth, 0.02);
    float band = exp(-(lat * lat) / (width * width));

    vec3 coreDir = normalize(cross(bandNormal, vec3(0.3, 0.9, 0.2)) + vec3(0.00001));
    float core = smoothstep(-0.3, 0.9, dot(dir, coreDir));

    float cloudy = 0.35 + 0.65 * fbm(dir * 3.0 + offset);
    float lanes = smoothstep(0.45, 0.75, fbm(dir * 9.0 + 31.0 + offset));
    vec3 bandColor = mix(vec3(BandColorAR, BandColorAG, BandColorAB),
                         vec3(BandColorBR, BandColorBG, BandColorBB), core);
    vec3 milkyWay = bandColor * band * cloudy * (1.0 - 0.75 * lanes) * BandStrength * 0.55;

    // ---- Nebulae ----
    float mask = smoothstep(0.42, 0.78, fbm(dir * 1.6 + offset * 0.25));
    float cloud = fbm(dir * NebulaScale * 2.0 + 11.0 + offset);
    float cloud2 = fbm(dir * NebulaScale * 4.0 + 53.0);
    float nebulaDensity = mask * smoothstep(0.35, 0.8, cloud);
    vec3 nebulaColor = mix(vec3(NebulaColorAR, NebulaColorAG, NebulaColorAB),
                           vec3(NebulaColorBR, NebulaColorBG, NebulaColorBB),
                           smoothstep(0.3, 0.7, cloud2));
    vec3 nebula = nebulaColor * nebulaDensity * NebulaStrength * (0.4 + 0.8 * cloud2);

    // ---- Stars (more of them inside the Milky Way band) ----
    float bandBoost = 1.0 + 1.2 * band;
    vec3 stars = starLayer(dir, 22.0, 0.55, 1.7, 1.3, pixel, offset, bandBoost)
               + starLayer(dir, 55.0, 0.45, 1.1, 1.0, pixel, offset + 5.1, bandBoost)
               + starLayer(dir, 140.0, 0.35, 0.8, 0.7, pixel, offset + 9.7, bandBoost);

    vec3 color = milkyWay + nebula + stars;

    // Soft tone-mapping so bright stars never clip harshly
    color = vec3(1.0) - exp(-color * 1.4);

    fragColor = vec4(color, Opacity);
}
