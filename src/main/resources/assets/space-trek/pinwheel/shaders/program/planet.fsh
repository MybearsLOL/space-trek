#version 150

#include veil:space_helper

uniform sampler2D DiffuseSampler0;
uniform sampler2D DiffuseDepthSampler;
uniform float GameTime;
uniform vec2 ScreenSize;

in vec2 texCoord;
out vec4 fragColor;

// Simple noise for cloud/land patterns
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
            mix(hash(i), hash(i + vec2(1,0)), f.x),
            mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x),
            f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec4 scene = texture(DiffuseSampler0, texCoord);
    float depth = texture(DiffuseDepthSampler, texCoord).r;

    // Only draw on sky (far depth)
    if (depth < 0.9999) {
        fragColor = scene;
        return;
    }

    // Planet position and size on screen
    vec2 uv = texCoord;
    vec2 aspect = vec2(ScreenSize.x / ScreenSize.y, 1.0);
    vec2 center = vec2(0.75, 0.65); // position in sky
    float radius = 0.12;            // size

    vec2 diff = (uv - center) * aspect;
    float dist = length(diff);

    if (dist > radius) {
        fragColor = scene;
        return;
    }

    // Ray hit the sphere - calculate surface point
    float z = sqrt(radius * radius - dist * dist);
    vec3 normal = normalize(vec3(diff, z));

    // Rotate planet slowly over time
    float angle = GameTime * 0.01;
    vec2 rotated = vec2(
            normal.x * cos(angle) - normal.z * sin(angle),
            normal.z * cos(angle) + normal.x * sin(angle)
    );

    // UV on sphere surface
    vec2 sphereUV = vec2(
            atan(rotated.x, rotated.y) / 6.2832 + 0.5,
            normal.y * 0.5 + 0.5
    );

    // Generate terrain
    float land = fbm(sphereUV * 4.0);
    float clouds = fbm(sphereUV * 6.0 + vec2(GameTime * 0.001));

    // Colors
    vec3 ocean = vec3(0.05, 0.2, 0.6);
    vec3 shallows = vec3(0.1, 0.45, 0.7);
    vec3 sand = vec3(0.76, 0.7, 0.5);
    vec3 grass = vec3(0.15, 0.45, 0.1);
    vec3 forest = vec3(0.05, 0.3, 0.05);
    vec3 snow = vec3(0.95, 0.95, 1.0);
    vec3 cloudColor = vec3(1.0, 1.0, 1.0);

    // Pick surface color based on height
    vec3 surface;
    if (land < 0.35) surface = ocean;
    else if (land < 0.42) surface = shallows;
    else if (land < 0.46) surface = sand;
    else if (land < 0.58) surface = grass;
    else if (land < 0.68) surface = forest;
    else surface = snow;

    // Blend clouds on top
    float cloudMask = smoothstep(0.55, 0.7, clouds);
    surface = mix(surface, cloudColor, cloudMask);

    // Simple lighting from top-left
    vec3 lightDir = normalize(vec3(-0.5, 1.0, 0.5));
    float diffuse = max(dot(normal, lightDir), 0.0);
    float ambient = 0.15;
    surface *= (ambient + diffuse * 0.85);

    // Atmosphere glow at edges
    float edge = 1.0 - smoothstep(radius * 0.8, radius, dist);
    float atmStrength = 1.0 - edge;
    vec3 atmColor = vec3(0.3, 0.6, 1.0);
    float atmFactor = pow(atmStrength, 3.0) * 0.7;
    surface = mix(surface, atmColor, atmFactor);

    // Soft edge blend with sky
    float alpha = smoothstep(radius, radius * 0.95, dist);
    fragColor = mix(scene, vec4(surface, 1.0), alpha);
}