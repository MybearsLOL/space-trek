#version 330 core

uniform float TanHalfFovX;
uniform float TanHalfFovY;
uniform float PlanetCenterX;
uniform float PlanetCenterY;
uniform float PlanetCenterZ;
uniform float PlanetRadius;
uniform float AtmosphereRadius;
uniform float SunDirX;
uniform float SunDirY;
uniform float SunDirZ;

uniform float AtmosphereColorR;
uniform float AtmosphereColorG;
uniform float AtmosphereColorB;
uniform float TwilightColorR;
uniform float TwilightColorG;
uniform float TwilightColorB;
uniform float Density;
uniform float Exposure;
uniform float SunIntensity;
uniform float MieStrength;

in vec2 vNdc;
out vec4 fragColor;

const float PI = 3.14159265359;
const int SEGMENT_STEPS = 20;
const float RAYLEIGH_HEIGHT = 0.30;
const float MIE_HEIGHT = 0.07;
const float MIE_G = 0.76;
const float MIE_EXTINCTION = 1.1;
const float MIE_OPTICAL_DEPTH = 0.6;
const float TWILIGHT_ABSORPTION = 0.6;
const float MULTIPLE_SCATTERING = 0.12;
const float PENUMBRA = 0.12;
const float DEPTH_SCALE = 1.0;

vec2 raySphere(vec3 origin, vec3 dir, vec3 center, float radius) {
    vec3 oc = origin - center;
    float b = dot(oc, dir);
    vec3 perpendicular = oc - dir * b;
    float disc = radius * radius - dot(perpendicular, perpendicular);
    if (disc < 0.0) {
        return vec2(-1.0, -1.0);
    }
    float s = sqrt(disc);
    return vec2(-b - s, -b + s);
}

float chapmanRising(float x, float cosChi) {
    float z = sqrt(0.5 * x) * cosChi;
    float erfcx = 1.0 / (1.15801463 * z + sqrt(1.0 + 0.3832296 * z * z));
    return sqrt(0.5 * PI * x) * erfcx * (1.0 + 0.75 / x);
}

float sunColumn(float planetScaled, float heightScaled, float clearanceScaled, float cosChi) {
    float x = planetScaled + heightScaled;
    if (cosChi >= 0.0) {
        return exp(-heightScaled) * chapmanRising(x, cosChi);
    }
    float x0 = planetScaled + clearanceScaled;
    float tangent = 2.0 * sqrt(0.5 * PI * x0) * (1.0 + 0.375 / x0) * exp(-clearanceScaled);
    return max(tangent - exp(-heightScaled) * chapmanRising(x, -cosChi), 0.0);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 PlanetCenter = vec3(PlanetCenterX, PlanetCenterY, PlanetCenterZ);
    vec3 SunDir = normalize(vec3(SunDirX, SunDirY, SunDirZ));
    vec3 AtmosphereColor = vec3(AtmosphereColorR, AtmosphereColorG, AtmosphereColorB);
    vec3 TwilightColor = vec3(TwilightColorR, TwilightColorG, TwilightColorB);

    vec3 dir = normalize(vec3(vNdc.x * TanHalfFovX, vNdc.y * TanHalfFovY, -1.0));

    vec2 atmosphere = raySphere(vec3(0.0), dir, PlanetCenter, AtmosphereRadius);
    if (atmosphere.y <= 0.0) {
        discard;
    }
    float tStart = max(atmosphere.x, 0.0);
    float tEnd = atmosphere.y;

    vec2 planet = raySphere(vec3(0.0), dir, PlanetCenter, PlanetRadius);
    if (planet.x > 0.0) {
        tEnd = min(tEnd, planet.x);
    }
    if (tEnd <= tStart) {
        discard;
    }

    float thickness = AtmosphereRadius - PlanetRadius;
    float hR = thickness * RAYLEIGH_HEIGHT;
    float hM = thickness * MIE_HEIGHT;
    float planetScaledR = PlanetRadius / hR;
    float planetScaledM = PlanetRadius / hM;

    float depth = Density * DEPTH_SCALE;
    vec3 scatterDepthR = pow(AtmosphereColor, vec3(1.6)) * depth;
    vec3 extinctDepthR = scatterDepthR + (vec3(1.0) - TwilightColor) * (depth * TWILIGHT_ABSORPTION);
    float scatterDepthM = MieStrength * depth * MIE_OPTICAL_DEPTH;
    float extinctDepthM = scatterDepthM * MIE_EXTINCTION;

    vec3 scatterR = scatterDepthR / hR;
    vec3 extinctR = extinctDepthR / hR;
    float scatterM = scatterDepthM / hM;
    float extinctM = extinctDepthM / hM;

    float cosTheta = dot(dir, SunDir);
    float rayleighPhase = 3.0 / (16.0 * PI) * (1.0 + cosTheta * cosTheta);
    rayleighPhase = mix(rayleighPhase, 1.0 / (4.0 * PI), MULTIPLE_SCATTERING);
    float g2 = MIE_G * MIE_G;
    float miePhase = 3.0 / (8.0 * PI)
            * ((1.0 - g2) * (1.0 + cosTheta * cosTheta))
            / ((2.0 + g2) * pow(1.0 + g2 - 2.0 * MIE_G * cosTheta, 1.5));
    miePhase = mix(miePhase, 1.0 / (4.0 * PI), MULTIPLE_SCATTERING);

    float tPivot = clamp(dot(PlanetCenter, dir), tStart, tEnd);
    float penumbra = PENUMBRA * thickness;

    vec3 inscatter = vec3(0.0);
    vec3 viewTransmittance = vec3(1.0);

    for (int segment = 0; segment < 2; segment++) {
        float segmentLength = segment == 0 ? tPivot - tStart : tEnd - tPivot;
        if (segmentLength <= 0.0) {
            continue;
        }
        for (int k = 0; k < SEGMENT_STEPS; k++) {
            int index = segment == 0 ? SEGMENT_STEPS - 1 - k : k;
            float f0 = float(index) / float(SEGMENT_STEPS);
            float f1 = float(index + 1) / float(SEGMENT_STEPS);
            float nearEdge = f0 * f0;
            float farEdge = f1 * f1;
            float offset = segmentLength * 0.5 * (nearEdge + farEdge);
            float stepLength = segmentLength * (farEdge - nearEdge);
            float t = segment == 0 ? tPivot - offset : tPivot + offset;

            vec3 rel = dir * t - PlanetCenter;
            float r = length(rel);
            float height = max(r - PlanetRadius, 0.0);
            float densityR = exp(-height / hR);
            float densityM = exp(-height / hM);

            vec3 extinction = extinctR * densityR + vec3(extinctM * densityM);
            vec3 stepTransmittance = exp(-extinction * stepLength);

            float sunCos = dot(rel, SunDir) / max(r, 1e-4);
            float lit = 1.0;
            float clearance = 0.0;
            if (sunCos < 0.0) {
                vec3 perpendicular = rel - SunDir * dot(rel, SunDir);
                float closest = length(perpendicular);
                lit = smoothstep(-penumbra, penumbra, closest - PlanetRadius);
                clearance = max(closest - PlanetRadius, 0.0);
            }

            if (lit > 0.0) {
                float columnR = sunColumn(planetScaledR, height / hR, clearance / hR, sunCos);
                float columnM = sunColumn(planetScaledM, height / hM, clearance / hM, sunCos);
                vec3 sunTransmittance = exp(-(extinctDepthR * columnR + vec3(extinctDepthM * columnM)));
                vec3 sunLight = SunIntensity * lit * sunTransmittance;
                vec3 scattering = scatterR * (densityR * rayleighPhase) + vec3(scatterM * densityM * miePhase);
                vec3 stepIntegral = (vec3(1.0) - stepTransmittance) / max(extinction, vec3(1e-6));
                inscatter += viewTransmittance * sunLight * scattering * stepIntegral;
            }

            viewTransmittance *= stepTransmittance;
        }
    }

    vec3 color = vec3(1.0) - exp(-inscatter * Exposure);
    color = pow(color, vec3(1.0 / 2.0));
    color += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    color = max(color, vec3(0.0));

    float alpha = 1.0 - dot(viewTransmittance, vec3(1.0 / 3.0));
    fragColor = vec4(color, clamp(alpha, 0.0, 1.0));
}
