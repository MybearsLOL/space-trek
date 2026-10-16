package com.spacetrek.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import com.spacetrek.PlanetGates;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Renders textured planets with a raymarched, scattering atmosphere.
 *
 * - Planets are drawn at their TRUE world position and size (they grow as you approach).
 * - Planets beyond the far plane are drawn at a clamped distance and scaled down by the
 *   same factor, so their on-screen size is still exactly correct.
 * - Planet maps are equirectangular images (2:1, e.g. 2048x1024).
 * - The atmosphere is a Veil shader (assets/<NAMESPACE>/pinwheel/shaders/program/atmosphere.*)
 *   that raymarches Rayleigh + Mie single scattering. If the shader is missing or fails to
 *   compile, a simple glow is drawn instead so the planet never breaks.
 *
 * Put map images at:
 *   src/main/resources/assets/<NAMESPACE>/textures/planet/<name>.png
 */
public class PlanetRenderer {

    // ---------------------------------------------------------------------------------
    // CONFIG
    // ---------------------------------------------------------------------------------

    /** Must match the folder under assets/ that holds your textures and shaders (your mod id). */
    private static final String NAMESPACE = "space-trek";

    /** Veil shader program id -> assets/<NAMESPACE>/pinwheel/shaders/program/atmosphere.json */
    private static final ResourceLocation ATMOSPHERE_SHADER =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "atmosphere");

    /**
     * Planets used until the live settings file is read (see PlanetConfig). Normally you edit
     * config/space-trek-planets.json instead of this list.
     */
    private static final List<Planet> DEFAULT_PLANETS = List.of(
            // ---- Planet 1: huge Earth-like world, about 3,500 blocks away to the north ----
            new Planet(
                    tex("textures/planet/example_planet.png"), // equirectangular map
                    0.0, 300.0, -6000.0,                       // world position (centre)
                    2500.0f,                                   // radius in blocks (any size works)
                    0.002f,                                    // spin, degrees per tick
                    15.0f,                                     // axial tilt, degrees
                    200.0f,                                    // SHADOW ANGLE: 0 = right, 90 = top, 180 = left, 270 = bottom
                    10.0f,                                     // shadow tilt: 0 = exact half, + = less shadow, - = more
                    0x4DA8FF,                                  // ATMOSPHERE COLOUR (the sky colour), 0xRRGGBB
                    0xFF8A3D,                                  // TWILIGHT COLOUR (sunset colour), 0xRRGGBB
                    0.06f,                                     // atmosphere thickness, fraction of radius
                    0.35f,                                     // atmosphere DENSITY (thin 0.1 ... thick 1.0)
                    1.0f                                       // glow BRIGHTNESS (exposure)
            ),
            // ---- Planet 2: enormous red world far to the south-west ----
            new Planet(
                    tex("textures/planet/example_planet.png"),
                    -14000.0, 200.0, 4000.0,
                    6000.0f,
                    0.001f,
                    -20.0f,
                    60.0f,
                    0.0f,
                    0xD9A066,
                    0xFF5533,
                    0.05f,
                    0.5f,
                    1.0f
            )
    );

    /** The planets that are actually drawn. Replaced live whenever the settings file changes. */
    private static volatile List<Planet> planets = DEFAULT_PLANETS;

    public static void setPlanets(List<Planet> newPlanets) {
        planets = List.copyOf(newPlanets);
    }

    public static List<Planet> getPlanets() {
        return planets;
    }

    /** ambient, terminatorSoftness, sunIntensity, mieStrength */
    public static float[] getSettings() {
        return new float[]{AMBIENT, TERMINATOR_SOFTNESS, SUN_INTENSITY, MIE_STRENGTH};
    }

    public static void applySettings(float ambient, float terminatorSoftness, float sunIntensity, float mieStrength) {
        AMBIENT = ambient;
        TERMINATOR_SOFTNESS = Math.max(0.001f, terminatorSoftness);
        SUN_INTENSITY = sunIntensity;
        MIE_STRENGTH = mieStrength;
    }

    /** How dark the night side of the surface is (0 = pitch black, 1 = no shading). */
    private static float AMBIENT = 0.06f;

    /** Width of the blurred edge between day and night on the surface. */
    private static float TERMINATOR_SOFTNESS = 0.06f;

    /** Raymarched atmosphere: strength of the sunlight and of the forward haze (Mie). */
    private static float SUN_INTENSITY = 20.0f;
    private static float MIE_STRENGTH = 0.2f;

    /** Fallback glow only: how much glow stays on the night side, and rim thinness. */
    private static final float NIGHT_GLOW = 0.04f;
    private static final float RIM_POWER = 3.0f;

    /** Sphere detail. */
    private static final int LAT_SEGMENTS = 64;
    private static final int LON_SEGMENTS = 128;

    /** Fallback halo detail. */
    private static final int HALO_SLICES = 96;
    private static final int HALO_RINGS = 10;

    // ---------------------------------------------------------------------------------
    // PLANET DEFINITION
    // ---------------------------------------------------------------------------------

    /**
     * A transparent cloud shell around a planet.
     *
     * texture       - equirectangular cloud map (2:1), same kind of image as the planet map
     * altitude      - height above the surface, in blocks
     * opacity       - 0 = invisible, 1 = fully visible
     * spinDegPerTick- how fast the clouds turn (independent from the planet's own spin)
     * useAlpha      - true: the PNG has a transparent background.
     *                 false: the image is black where there are no clouds (no alpha channel needed)
     */
    /**
     * Makes a planet or moon move around another one. A planet WITHOUT an orbit stands still
     * at its x / y / z. With an orbit, its x / y / z are ignored and it circles its parent.
     *
     * parent      - key of the body to circle (its name, or "planet1", "planet2"... if unnamed)
     * radius      - distance from the parent's centre, in blocks
     * periodTicks - game ticks for one full lap (24000 = one Minecraft day)
     * inclination - tilt of the orbit in degrees (0 = flat, level with the ground)
     * node        - turns the direction of that tilt around the vertical axis, in degrees
     * phase       - where on the orbit it starts, in degrees
     */
    public record Orbit(String parent, float radius, float periodTicks,
                        float inclination, float node, float phase) {
    }

    public record Clouds(ResourceLocation texture,
                         float altitude,
                         float opacity,
                         float spinDegPerTick,
                         boolean useAlpha) {
    }

    /*
     * Planet orientation (rotationX / rotationY / rotationZ, in degrees), used to line a planet up
     * in the sky. It is added on top of axialTilt and the spin:
     *   rotationX - pitch: tips the planet's north pole toward or away from the viewer
     *   rotationY - turns the planet around its own pole (which continent faces you)
     *   rotationZ - roll: leans the planet's pole to the left or right
     */
    public record Planet(ResourceLocation texture,
                         double x, double y, double z,
                         float radius,
                         float spinDegPerTick,
                         float axialTiltDeg,
                         float shadowAngleDeg,
                         float shadowTiltDeg,
                         int atmosphereColor,
                         int twilightColor,
                         float atmosphereHeight,
                         float atmosphereDensity,
                         float atmosphereBrightness,
                         Clouds clouds,
                         String dimension,
                         String name,
                         float rotationX,
                         float rotationY,
                         float rotationZ,
                         Orbit orbit) {

        /** Planet without clouds (keeps the older 13-value constructor working). */
        public Planet(ResourceLocation texture,
                      double x, double y, double z,
                      float radius,
                      float spinDegPerTick,
                      float axialTiltDeg,
                      float shadowAngleDeg,
                      float shadowTiltDeg,
                      int atmosphereColor,
                      int twilightColor,
                      float atmosphereHeight,
                      float atmosphereDensity,
                      float atmosphereBrightness) {
            this(texture, x, y, z, radius, spinDegPerTick, axialTiltDeg, shadowAngleDeg, shadowTiltDeg,
                    atmosphereColor, twilightColor, atmosphereHeight, atmosphereDensity, atmosphereBrightness, null, "", "", 0.0f, 0.0f, 0.0f, null);
        }

        /**
         * Direction toward the star (world space), built from the two shadow angles.
         *
         * shadowAngleDeg - where the shadow sits, seen from the south looking north (-Z):
         *     0 = right, 90 = top, 180 = left, 270 = bottom (any angle in between works).
         * shadowTiltDeg  - 0 = shadow covers exactly half the planet. Positive turns the lit
         *     side toward you (shadow shrinks), negative grows it.
         */
        public Vector3f sunDir() {
            double a = Math.toRadians(shadowAngleDeg + 180.0f); // sun is opposite the shadow
            double e = Math.toRadians(shadowTiltDeg);
            return new Vector3f(
                    (float) (Math.cos(a) * Math.cos(e)),
                    (float) (Math.sin(a) * Math.cos(e)),
                    (float) Math.sin(e)
            ).normalize();
        }

        public boolean hasAtmosphere() {
            return atmosphereHeight > 0.0f && atmosphereDensity > 0.0f && atmosphereBrightness > 0.0f;
        }
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // ---------------------------------------------------------------------------------
    // PRECOMPUTED UNIT SPHERE
    // ---------------------------------------------------------------------------------

    private static final int COLS = LON_SEGMENTS + 1;
    private static final int VERT_COUNT = (LAT_SEGMENTS + 1) * COLS;

    private static final float[] NX = new float[VERT_COUNT];
    private static final float[] NY = new float[VERT_COUNT];
    private static final float[] NZ = new float[VERT_COUNT];
    private static final float[] U = new float[VERT_COUNT];
    private static final float[] V = new float[VERT_COUNT];

    // Scratch arrays reused every frame
    private static final float[] SHADE = new float[VERT_COUNT];
    private static final float[] CLOUD_SHADE = new float[VERT_COUNT];
    private static final float[] RIM_R = new float[VERT_COUNT];
    private static final float[] RIM_G = new float[VERT_COUNT];
    private static final float[] RIM_B = new float[VERT_COUNT];
    private static final float[] RIM_A = new float[VERT_COUNT];

    static {
        for (int lat = 0; lat <= LAT_SEGMENTS; lat++) {
            double theta = Math.PI * lat / LAT_SEGMENTS;
            for (int lon = 0; lon <= LON_SEGMENTS; lon++) {
                double phi = 2.0 * Math.PI * lon / LON_SEGMENTS;
                int i = lat * COLS + lon;
                NX[i] = (float) (Math.sin(theta) * Math.cos(phi));
                NY[i] = (float) Math.cos(theta);
                NZ[i] = (float) (Math.sin(theta) * Math.sin(phi));
                // u runs 1 -> 0 so the map is not mirrored when viewed from outside
                U[i] = 1.0f - (float) lon / LON_SEGMENTS;
                V[i] = (float) lat / LAT_SEGMENTS;
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // RENDER
    // ---------------------------------------------------------------------------------

    /** 1 = fully visible. Lower values fade the whole planet (surface, clouds, atmosphere) in or out. */
    private static float renderAlpha = 1.0f;

    public static void render(Camera camera) {
        render(camera, 1.0f);
    }

    public static void render(Camera camera, float alpha) {
        render(camera, alpha, camera.getPosition());
    }

    /**
     * Same as render(camera, alpha), but the planets are drawn as seen from 'viewPosition'
     * instead of the camera's real position. Used inside a planet's dimension (the overworld),
     * where the camera's position is mapped to a point above the planet.
     */
    public static void render(Camera camera, float alpha, Vec3 viewPosition) {
        renderPlanets(camera, viewPosition, null, planets, null, alpha);
    }

    /**
     * Where a planet's centre is right now. Planets without an orbit stand still at their x / y / z;
     * orbiting ones circle their parent (which may itself orbit something).
     */
    public static Vec3 positionOf(Planet planet) {
        return positionOf(planet, planets, 0);
    }

    private static Vec3 positionOf(Planet planet, List<Planet> all, int depth) {
        Orbit orbit = planet.orbit();
        if (orbit == null || orbit.parent() == null || orbit.parent().isEmpty() || depth > 8) {
            return new Vec3(planet.x(), planet.y(), planet.z());
        }
        for (int i = 0; i < all.size(); i++) {
            Planet other = all.get(i);
            if (other != planet && planetKey(other, i).equals(orbit.parent())) {
                Minecraft minecraft = Minecraft.getInstance();
                double gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0.0;
                return positionOf(other, all, depth + 1).add(PlanetGates.orbitOffset(
                        orbit.radius(), orbit.periodTicks(), orbit.inclination(),
                        orbit.node(), orbit.phase(), gameTime));
            }
        }
        // Parent not found: stand still
        return new Vec3(planet.x(), planet.y(), planet.z());
    }

    /**
     * The name the sky editor uses for a planet: its name, or "planet1", "planet2"... if it has none.
     */
    public static String planetKey(Planet planet, int index) {
        String name = planet.name() == null ? "" : planet.name().trim();
        return name.isEmpty() ? "planet" + (index + 1) : name;
    }

    /** The planet with this key (see planetKey), or null. */
    public static Planet findPlanet(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        List<Planet> list = planets;
        for (int i = 0; i < list.size(); i++) {
            if (planetKey(list.get(i), i).equals(key)) {
                return list.get(i);
            }
        }
        return null;
    }

    /** Sky turn applied while drawing (null = none). Set only during renderPlanets. */
    private static Quaternionf skyRotation = null;
    private static final Quaternionf NO_ROTATION = new Quaternionf();

    /**
     * Draws a list of planets as seen from viewPosition.
     *
     * skyRotation - turns the whole sky (positions, orientation and lighting) around the viewer;
     *               used for a planet's day/night rotation. null = no turn.
     * alphas      - one fade value per planet (0 = invisible, 1 = solid), or null to use defaultAlpha.
     */
    public static void renderPlanets(Camera camera, Vec3 viewPosition, Quaternionf rotation,
                                     List<Planet> list, float[] alphas, float defaultAlpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = viewPosition;
        Quaternionf inverseCamRot = camera.rotation().conjugate(new Quaternionf());

        // Anything farther than this gets clamped (and scaled) so it never leaves the far plane
        float maxDrawDist = mc.gameRenderer.getDepthFar() * 0.85f;

        // Smooth, frame-rate independent spin clock measured in ticks
        double ticks = Util.getMillis() / 50.0;

        boolean raymarch = isAtmosphereShaderReady();

        skyRotation = rotation;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        for (int i = 0; i < list.size(); i++) {
            float alpha = alphas != null && i < alphas.length ? alphas[i] : defaultAlpha;
            if (alpha <= 0.001f) {
                continue;
            }
            renderAlpha = Math.min(1.0f, alpha);
            drawPlanet(list.get(i), camPos, inverseCamRot, maxDrawDist, ticks, raymarch);
        }

        skyRotation = null;

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /** True when the Veil atmosphere shader has been loaded and compiled. */
    private static boolean isAtmosphereShaderReady() {
        try {
            return VeilRenderSystem.renderer().getShaderManager().getShader(ATMOSPHERE_SHADER) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void drawPlanet(Planet planet, Vec3 camPos, Quaternionf inverseCamRot,
                                   float maxDrawDist, double ticks, boolean raymarch) {

        Vec3 position = positionOf(planet);
        double dx = position.x - camPos.x;
        double dy = position.y - camPos.y;
        double dz = position.z - camPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Camera is inside the planet - nothing sensible to draw
        if (dist <= planet.radius()) return;

        // TRUE scale: only shrink the draw distance/radius if the planet is beyond the far plane.
        float scale = dist > maxDrawDist ? (float) (maxDrawDist / dist) : 1.0f;

        Vector3f offset = new Vector3f(
                (float) (dx * scale),
                (float) (dy * scale),
                (float) (dz * scale)
        );
        float drawRadius = planet.radius() * scale;

        Vector3f sun = planet.sunDir();

        // The whole sky turns with the day/night rotation (positions and the star's direction)
        if (skyRotation != null) {
            skyRotation.transform(offset);
            skyRotation.transform(sun);
        }

        // ---- 1) Fallback halo (only when the Veil shader is not available) ----
        if (planet.hasAtmosphere() && !raymarch) {
            drawHalo(planet, offset, drawRadius, sun, inverseCamRot);
        }

        // ---- 2) The planet itself ----
        float spinRad = (float) Math.toRadians(((ticks * planet.spinDegPerTick()) % 360.0) + planet.rotationY());
        float tiltRad = (float) Math.toRadians(planet.axialTiltDeg() + planet.rotationX());
        float rollRad = (float) Math.toRadians(planet.rotationZ());

        // v' = CameraInverse * Translate(offset) * Tilt * Spin * Scale * v
        Matrix4f matrix = new Matrix4f()
                .rotation(inverseCamRot)
                .translate(offset)
                .rotate(skyRotation != null ? skyRotation : NO_ROTATION)
                .rotateZ(rollRad)
                .rotateX(tiltRad)
                .rotateY(spinRad)
                .scale(drawRadius);

        // Surface normals in WORLD space (tilt + spin), used for day/night lighting
        Quaternionf surfaceRot = (skyRotation != null ? new Quaternionf(skyRotation) : new Quaternionf()).rotateZ(rollRad).rotateX(tiltRad).rotateY(spinRad);
        Vector3f n = new Vector3f();
        for (int i = 0; i < VERT_COUNT; i++) {
            surfaceRot.transform(NX[i], NY[i], NZ[i], n);
            float d = n.x * sun.x + n.y * sun.y + n.z * sun.z;
            float t = Math.max(0.0f, Math.min(1.0f,
                    (d + TERMINATOR_SOFTNESS) / (2.0f * TERMINATOR_SOFTNESS)));
            float smooth = t * t * (3.0f - 2.0f * t);
            SHADE[i] = AMBIENT + (1.0f - AMBIENT) * smooth;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, planet.texture());
        if (renderAlpha < 0.999f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.enableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                int i00 = lat * COLS + lon;
                int i01 = i00 + 1;
                int i10 = i00 + COLS;
                int i11 = i10 + 1;

                addPlanetVertex(buffer, matrix, i00);
                addPlanetVertex(buffer, matrix, i11);
                addPlanetVertex(buffer, matrix, i10);

                addPlanetVertex(buffer, matrix, i00);
                addPlanetVertex(buffer, matrix, i01);
                addPlanetVertex(buffer, matrix, i11);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        // ---- 3) Cloud shell (transparent, above the surface) ----
        Clouds clouds = planet.clouds();
        if (clouds != null && clouds.opacity() > 0.0f) {
            drawClouds(planet, clouds, offset, drawRadius, sun, inverseCamRot, ticks);
        }

        // ---- 4) Atmosphere on top of the planet ----
        if (planet.hasAtmosphere()) {
            if (raymarch) {
                drawAtmosphereRaymarch(planet, offset, drawRadius, sun, inverseCamRot);
            } else {
                drawRim(planet, offset, drawRadius, sun, inverseCamRot);
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // CLOUDS
    // ---------------------------------------------------------------------------------

    private static void drawClouds(Planet planet, Clouds clouds, Vector3f offset, float drawRadius,
                                   Vector3f sun, Quaternionf inverseCamRot, double ticks) {

        // Same scale factor as the planet: (radius + altitude) / radius
        float cloudRadius = drawRadius * (planet.radius() + Math.max(0.0f, clouds.altitude())) / planet.radius();

        float spinRad = (float) Math.toRadians(((ticks * clouds.spinDegPerTick()) % 360.0) + planet.rotationY());
        float tiltRad = (float) Math.toRadians(planet.axialTiltDeg() + planet.rotationX());
        float rollRad = (float) Math.toRadians(planet.rotationZ());

        Matrix4f matrix = new Matrix4f()
                .rotation(inverseCamRot)
                .translate(offset)
                .rotate(skyRotation != null ? skyRotation : NO_ROTATION)
                .rotateZ(rollRad)
                .rotateX(tiltRad)
                .rotateY(spinRad)
                .scale(cloudRadius);

        // Day / night lighting, same as the surface
        Quaternionf surfaceRot = (skyRotation != null ? new Quaternionf(skyRotation) : new Quaternionf()).rotateZ(rollRad).rotateX(tiltRad).rotateY(spinRad);
        Vector3f n = new Vector3f();
        for (int i = 0; i < VERT_COUNT; i++) {
            surfaceRot.transform(NX[i], NY[i], NZ[i], n);
            float d = n.x * sun.x + n.y * sun.y + n.z * sun.z;
            float t = Math.max(0.0f, Math.min(1.0f,
                    (d + TERMINATOR_SOFTNESS) / (2.0f * TERMINATOR_SOFTNESS)));
            float smooth = t * t * (3.0f - 2.0f * t);
            CLOUD_SHADE[i] = AMBIENT + (1.0f - AMBIENT) * smooth;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, clouds.texture());
        RenderSystem.enableBlend();
        if (clouds.useAlpha()) {
            RenderSystem.defaultBlendFunc();
        } else {
            // black = see-through, white = cloud (no alpha channel needed)
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        }

        // From inside the cloud shell (flying through the clouds) we see its inner side
        if (offset.length() < cloudRadius) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }

        float opacity = Math.max(0.0f, Math.min(1.0f, clouds.opacity())) * renderAlpha;
        boolean alphaMode = clouds.useAlpha();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                int i00 = lat * COLS + lon;
                int i01 = i00 + 1;
                int i10 = i00 + COLS;
                int i11 = i10 + 1;

                addCloudVertex(buffer, matrix, i00, opacity, alphaMode);
                addCloudVertex(buffer, matrix, i11, opacity, alphaMode);
                addCloudVertex(buffer, matrix, i10, opacity, alphaMode);

                addCloudVertex(buffer, matrix, i00, opacity, alphaMode);
                addCloudVertex(buffer, matrix, i01, opacity, alphaMode);
                addCloudVertex(buffer, matrix, i11, opacity, alphaMode);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addCloudVertex(BufferBuilder buffer, Matrix4f matrix, int i, float opacity, boolean alphaMode) {
        float s = CLOUD_SHADE[i];
        if (alphaMode) {
            buffer.addVertex(matrix, NX[i], NY[i], NZ[i]).setUv(U[i], V[i]).setColor(s, s, s, opacity);
        } else {
            float c = s * opacity;
            buffer.addVertex(matrix, NX[i], NY[i], NZ[i]).setUv(U[i], V[i]).setColor(c, c, c, 1.0f);
        }
    }

    private static void addPlanetVertex(BufferBuilder buffer, Matrix4f matrix, int i) {
        float s = SHADE[i];
        buffer.addVertex(matrix, NX[i], NY[i], NZ[i])
                .setUv(U[i], V[i])
                .setColor(s, s, s, renderAlpha);
    }

    // ---------------------------------------------------------------------------------
    // ATMOSPHERE: VEIL RAYMARCHED SCATTERING
    // ---------------------------------------------------------------------------------

    private static void drawAtmosphereRaymarch(Planet planet, Vector3f offset, float drawRadius,
                                               Vector3f sunWorld, Quaternionf inverseCamRot) {

        // Everything the shader needs is in VIEW space (camera at the origin, looking down -Z)
        Vector3f center = inverseCamRot.transform(new Vector3f(offset), new Vector3f());
        Vector3f sunView = inverseCamRot.transform(new Vector3f(sunWorld), new Vector3f()).normalize();
        float atmosphereRadius = drawRadius * (1.0f + planet.atmosphereHeight());

        // Field of view straight from the active projection matrix (works with FOV effects too)
        Matrix4f proj = RenderSystem.getProjectionMatrix();
        float tanX = 1.0f / proj.m00();
        float tanY = 1.0f / proj.m11();

        // Skip the whole full-screen pass when the atmosphere is completely off screen
        float d = center.length();
        if (d > atmosphereRadius) {
            float beta = (float) Math.acos(Math.max(-1.0f, Math.min(1.0f, -center.z / d)));
            float alpha = (float) Math.asin(Math.min(1.0f, atmosphereRadius / d));
            float halfDiagonal = (float) Math.atan(Math.sqrt(tanX * tanX + tanY * tanY));
            if (beta - alpha > halfDiagonal) return;
        }

        ShaderProgram shader = VeilRenderSystem.setShader(ATMOSPHERE_SHADER);
        if (shader == null) return;
        shader.bind();

        float[] atmosphereColor = rgb(planet.atmosphereColor());
        float[] twilightColor = rgb(planet.twilightColor());

        // Uniforms are written through getUniformSafe(...), which exists in this Veil version.
        setUniform(shader, "TanHalfFovX", tanX);
        setUniform(shader, "TanHalfFovY", tanY);
        setUniform(shader, "PlanetCenterX", center.x);
        setUniform(shader, "PlanetCenterY", center.y);
        setUniform(shader, "PlanetCenterZ", center.z);
        setUniform(shader, "PlanetRadius", drawRadius);
        setUniform(shader, "AtmosphereRadius", atmosphereRadius);
        setUniform(shader, "SunDirX", sunView.x);
        setUniform(shader, "SunDirY", sunView.y);
        setUniform(shader, "SunDirZ", sunView.z);
        setUniform(shader, "AtmosphereColorR", atmosphereColor[0]);
        setUniform(shader, "AtmosphereColorG", atmosphereColor[1]);
        setUniform(shader, "AtmosphereColorB", atmosphereColor[2]);
        setUniform(shader, "TwilightColorR", twilightColor[0]);
        setUniform(shader, "TwilightColorG", twilightColor[1]);
        setUniform(shader, "TwilightColorB", twilightColor[2]);
        setUniform(shader, "Density", planet.atmosphereDensity());
        setUniform(shader, "Exposure", planet.atmosphereBrightness() * renderAlpha);
        setUniform(shader, "SunIntensity", SUN_INTENSITY);
        setUniform(shader, "MieStrength", MIE_STRENGTH);

        // "Screen" blending: adds the glow without ever blowing out to pure white
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR);
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f);
        buffer.addVertex(-1.0f, -1.0f, 0.0f);
        buffer.addVertex(1.0f, 1.0f, 0.0f);
        buffer.addVertex(-1.0f, 1.0f, 0.0f);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** Writes one float uniform. getUniformSafe never returns null (missing uniforms are ignored). */
    private static void setUniform(ShaderProgram shader, String name, float value) {
        shader.getUniformSafe(name).setFloat(value);
    }

    // ---------------------------------------------------------------------------------
    // FALLBACK ATMOSPHERE (used only if the Veil shader is unavailable): HALO
    // ---------------------------------------------------------------------------------

    private static void drawHalo(Planet planet, Vector3f offset, float drawRadius,
                                 Vector3f sunWorld, Quaternionf inverseCamRot) {

        Vector3f center = inverseCamRot.transform(new Vector3f(offset), new Vector3f());
        Vector3f sunView = inverseCamRot.transform(new Vector3f(sunWorld), new Vector3f());

        float d = center.length();
        float outerWorld = drawRadius * (1.0f + planet.atmosphereHeight());

        // Inside the atmosphere - skip the halo
        if (d <= outerWorld * 1.01f) return;

        float rLimb = limbRadius(drawRadius, d);
        float rOut = limbRadius(outerWorld, d);
        float rIn = rLimb * 0.98f;

        Vector3f dir = new Vector3f(center).div(d);
        Vector3f up = Math.abs(dir.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        Vector3f u = dir.cross(up, new Vector3f()).normalize();
        Vector3f v = u.cross(dir, new Vector3f()).normalize();

        float[] day = rgb(planet.atmosphereColor());
        float[] twilight = rgb(planet.twilightColor());
        float strength = Math.max(0.0f, Math.min(1.0f, planet.atmosphereBrightness())) * renderAlpha;

        float[] wx = new float[HALO_SLICES + 1];
        float[] wy = new float[HALO_SLICES + 1];
        float[] wz = new float[HALO_SLICES + 1];
        float[] cr = new float[HALO_SLICES + 1];
        float[] cg = new float[HALO_SLICES + 1];
        float[] cb = new float[HALO_SLICES + 1];
        float[] ca = new float[HALO_SLICES + 1];

        for (int k = 0; k <= HALO_SLICES; k++) {
            double theta = 2.0 * Math.PI * k / HALO_SLICES;
            float c = (float) Math.cos(theta);
            float s = (float) Math.sin(theta);
            wx[k] = u.x * c + v.x * s;
            wy[k] = u.y * c + v.y * s;
            wz[k] = u.z * c + v.z * s;

            float sd = wx[k] * sunView.x + wy[k] * sunView.y + wz[k] * sunView.z;
            float lit = smoothstep(-0.25f, 0.25f, sd);
            float dayMix = smoothstep(0.05f, 0.6f, sd);

            cr[k] = lerp(twilight[0], day[0], dayMix);
            cg[k] = lerp(twilight[1], day[1], dayMix);
            cb[k] = lerp(twilight[2], day[2], dayMix);
            ca[k] = strength * (NIGHT_GLOW + (1.0f - NIGHT_GLOW) * lit);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int k = 0; k < HALO_SLICES; k++) {
            for (int ring = 0; ring < HALO_RINGS; ring++) {
                float t0 = (float) ring / HALO_RINGS;
                float t1 = (float) (ring + 1) / HALO_RINGS;
                float r0 = lerp(rIn, rOut, t0);
                float r1 = lerp(rIn, rOut, t1);
                float f0 = (1.0f - t0) * (1.0f - t0);
                float f1 = (1.0f - t1) * (1.0f - t1);

                haloVertex(buffer, center, wx[k], wy[k], wz[k], r0, cr[k], cg[k], cb[k], ca[k] * f0);
                haloVertex(buffer, center, wx[k + 1], wy[k + 1], wz[k + 1], r0, cr[k + 1], cg[k + 1], cb[k + 1], ca[k + 1] * f0);
                haloVertex(buffer, center, wx[k + 1], wy[k + 1], wz[k + 1], r1, cr[k + 1], cg[k + 1], cb[k + 1], ca[k + 1] * f1);

                haloVertex(buffer, center, wx[k], wy[k], wz[k], r0, cr[k], cg[k], cb[k], ca[k] * f0);
                haloVertex(buffer, center, wx[k + 1], wy[k + 1], wz[k + 1], r1, cr[k + 1], cg[k + 1], cb[k + 1], ca[k + 1] * f1);
                haloVertex(buffer, center, wx[k], wy[k], wz[k], r1, cr[k], cg[k], cb[k], ca[k] * f1);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void haloVertex(BufferBuilder buffer, Vector3f center,
                                   float wx, float wy, float wz, float r,
                                   float cr, float cg, float cb, float a) {
        buffer.addVertex(center.x + wx * r, center.y + wy * r, center.z + wz * r)
                .setColor(cr, cg, cb, a);
    }

    private static float limbRadius(float radius, float distance) {
        float t2 = distance * distance - radius * radius;
        if (t2 <= 0.0f) return radius;
        return radius * distance / (float) Math.sqrt(t2);
    }

    // ---------------------------------------------------------------------------------
    // FALLBACK ATMOSPHERE: RIM
    // ---------------------------------------------------------------------------------

    private static void drawRim(Planet planet, Vector3f offset, float drawRadius,
                                Vector3f sun, Quaternionf inverseCamRot) {

        float rp = drawRadius * 1.002f;
        float[] day = rgb(planet.atmosphereColor());
        float[] twilight = rgb(planet.twilightColor());
        float strength = Math.max(0.0f, Math.min(1.0f, planet.atmosphereBrightness())) * renderAlpha;

        for (int i = 0; i < VERT_COUNT; i++) {
            float px = offset.x + NX[i] * rp;
            float py = offset.y + NY[i] * rp;
            float pz = offset.z + NZ[i] * rp;
            float len = (float) Math.sqrt(px * px + py * py + pz * pz);

            float facing = -(NX[i] * px + NY[i] * py + NZ[i] * pz) / len;
            facing = Math.max(0.0f, Math.min(1.0f, facing));
            float rim = (float) Math.pow(1.0f - facing, RIM_POWER);

            float sd = NX[i] * sun.x + NY[i] * sun.y + NZ[i] * sun.z;
            float lit = smoothstep(-0.25f, 0.25f, sd);
            float dayMix = smoothstep(0.05f, 0.6f, sd);

            RIM_R[i] = lerp(twilight[0], day[0], dayMix);
            RIM_G[i] = lerp(twilight[1], day[1], dayMix);
            RIM_B[i] = lerp(twilight[2], day[2], dayMix);
            RIM_A[i] = Math.max(0.0f, Math.min(1.0f,
                    strength * rim * (NIGHT_GLOW + (1.0f - NIGHT_GLOW) * lit)));
        }

        Matrix4f matrix = new Matrix4f()
                .rotation(inverseCamRot)
                .translate(offset)
                .scale(rp);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                int i00 = lat * COLS + lon;
                int i01 = i00 + 1;
                int i10 = i00 + COLS;
                int i11 = i10 + 1;

                addRimVertex(buffer, matrix, i00);
                addRimVertex(buffer, matrix, i11);
                addRimVertex(buffer, matrix, i10);

                addRimVertex(buffer, matrix, i00);
                addRimVertex(buffer, matrix, i01);
                addRimVertex(buffer, matrix, i11);
            }
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void addRimVertex(BufferBuilder buffer, Matrix4f matrix, int i) {
        buffer.addVertex(matrix, NX[i], NY[i], NZ[i])
                .setColor(RIM_R[i], RIM_G[i], RIM_B[i], RIM_A[i]);
    }

    // ---------------------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------------------

    private static float[] rgb(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f
        };
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }
}