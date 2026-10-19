package com.spacetrek.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.spacetrek.ModDimensions;
import com.spacetrek.PlanetGates;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The sky of the space dimension.
 *
 * Normally it is drawn by the Veil shader assets/space-trek/pinwheel/shaders/program/sky.*
 * (procedural stars, Milky Way and nebulae, sharp at any zoom). If that shader is switched
 * off or fails to compile, the old image skybox below is drawn instead.
 */
public class SpaceSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {

    private static final ResourceLocation SKY_SHADER =
            ResourceLocation.fromNamespaceAndPath(ModDimensions.NAMESPACE, "sky");

    // ---- old image skybox (fallback) ----
    private static final ResourceLocation[] FACES = new ResourceLocation[6];

    static {
        for (int i = 0; i < 6; i++) {
            FACES[i] = ResourceLocation.fromNamespaceAndPath("spacetrek", "textures/environment/space/space_" + i + ".png");
        }
    }

    @Override
    public void render(WorldRenderContext context) {
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

        boolean drawn = false;
        if (SkySettings.useShader && isSkyShaderReady()) {
            drawn = drawSkyShader(camera(context), 1.0f, 0.0f, SPACE_AXIS, null);
        }
        if (!drawn) {
            renderSkybox(context.positionMatrix());
        }

        PlanetRenderer.render(context.camera());
    }

    // ---------------------------------------------------------------------------------
    // PROCEDURAL SKY (Veil shader)
    // ---------------------------------------------------------------------------------

    private static Camera camera(WorldRenderContext context) {
        return context.camera();
    }

    private static final Vector3f SPACE_AXIS = new Vector3f(0.0f, 0.0f, -1.0f);

    /** Settings of the atmosphere glow pass (the day sky that covers the stars). */
    private record Glow(float alpha, float day, float twilight, float sunGlow, Vector3f sunDir,
                        int zenith, int horizon, int twilightColor) {
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        if (edge1 <= edge0) {
            return x >= edge1 ? 1.0f : 0.0f;
        }
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Our own sky for a dimension (the Sky Editor decides which ones). Draws, in order:
     *   1. stars, Milky Way, nebulae, turned by the day/night rotation
     *   2. the planets, seen from the point on the home planet
     *   3. the atmosphere glow, which hides the stars and planets by day and is gone at night
     * The vanilla sun, moon and sky colour are covered completely. There is no sun or moon
     * disc: they come later, when you add them.
     *
     * Returns false if this dimension has no enabled sky (vanilla sky stays).
     * Called after the vanilla sky has been drawn (Veil's AFTER_SKY stage).
     */
    public static boolean renderDimensionSky(Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || level.dimension() == ModDimensions.SPACE_LEVEL) {
            return false;
        }

        DimensionSky sky = SkyConfig.get(level.dimension().location().toString());
        if (sky == null || !sky.enabled || !isSkyShaderReady()) {
            return false;
        }

        Vec3 cameraPos = camera.getPosition();

        // The planet this dimension is on, and the star that lights it
        PlanetRenderer.Planet home = PlanetRenderer.findPlanet(sky.homePlanet);
        PlanetRenderer.Planet sunBody = PlanetRenderer.findPlanet(sky.sun);
        if (sunBody != null && sunBody.star() == null) {
            sunBody = null; // only stars can be the sun
        }

        // The point in space the sky is seen from: a spot on the home planet's surface (above the
        // camera's x / z), or the camera itself when no home planet is set
        Vec3 viewPosition = cameraPos;
        if (home != null) {
            double altitude = Math.max(0.0, cameraPos.y - ModDimensions.HOME_SURFACE_Y);
            Vec3 offset = PlanetGates.capOffset(cameraPos.x, cameraPos.z, home.radius() + altitude);
            viewPosition = PlanetRenderer.positionOf(home).add(offset);
        }

        // Where the sun is in the (turning) celestial sky: toward the sun body, measured from that
        // same point, so the day/night cycle and the drawn sun always point the same way.
        // Without a sun body it is straight up, which is the vanilla sun path.
        Vector3f sunCelestial = new Vector3f(0.0f, 1.0f, 0.0f);
        if (sunBody != null) {
            Vec3 toSun = PlanetRenderer.positionOf(sunBody).subtract(viewPosition);
            if (toSun.lengthSqr() > 1.0e-6) {
                sunCelestial.set((float) toSun.x, (float) toSun.y, (float) toSun.z).normalize();
            }
        }

        // The sky turns like the vanilla sun does: one full turn per day
        float angle = level.getTimeOfDay(partialTick) * ((float) Math.PI * 2.0f);
        double tilt = Math.toRadians(sky.poleTilt);
        Vector3f axis = new Vector3f(0.0f, (float) Math.sin(tilt), (float) -Math.cos(tilt))
                .rotateY((float) Math.toRadians(sky.poleTurn));
        Quaternionf celestialToWorld = new Quaternionf().rotationAxis(-angle, axis.x, axis.y, axis.z);
        Vector3f sunWorld = celestialToWorld.transform(sunCelestial.x, sunCelestial.y, sunCelestial.z, new Vector3f());

        // Day / night from the sun's height above the horizon
        float elevation = (float) Math.toDegrees(Math.asin(Math.max(-1.0f, Math.min(1.0f, sunWorld.y))));
        float day = smoothstep(sky.dayStartElevation, sky.dayEndElevation, elevation);
        float twilight = (float) Math.exp(-(elevation * elevation) / (2.0 * 12.0 * 12.0));
        float altitudeFactor = 1.0f - smoothstep(sky.fadeStartY, sky.fadeEndY, (float) cameraPos.y);

        // 1) background: replace the vanilla sky completely, then the stars
        RenderSystem.clearColor(0.0f, 0.0f, 0.0f, 1.0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        drawSkyShader(camera, 1.0f, angle, axis, null);

        // 2) planets, as seen from the home planet

        // Bodies that the day sky covers (drawn before the glow) and bodies that stay visible in
        // the day (drawn after it, on top of the glow)
        List<PlanetRenderer.Planet> behind = new ArrayList<>();
        List<Float> behindAlpha = new ArrayList<>();
        List<PlanetRenderer.Planet> inFront = new ArrayList<>();
        List<Float> inFrontAlpha = new ArrayList<>();

        if (home != null) {
            // The planet we stand on only shows when climbing out of the atmosphere
            behind.add(home);
            behindAlpha.add(smoothstep((float) ModDimensions.SPACE_TRANSITION_START_Y,
                    (float) ModDimensions.SPACE_TRANSITION_END_Y, (float) cameraPos.y));
        }
        for (DimensionSky.SkyBody body : sky.planets) {
            PlanetRenderer.Planet planet = PlanetRenderer.findPlanet(body.key);
            if (planet == null || planet == home || planet == sunBody) {
                continue;
            }
            float opacity = Math.max(0.0f, Math.min(1.0f, body.opacity));
            if (body.visibleInDay) {
                inFront.add(planet);
                inFrontAlpha.add(opacity);
            } else {
                behind.add(planet);
                behindAlpha.add(opacity);
            }
        }
        PlanetRenderer.renderPlanets(camera, viewPosition, celestialToWorld, behind, toArray(behindAlpha), 1.0f);

        // 3) atmosphere glow on top
        float glowAlpha = sky.atmosphereStrength * altitudeFactor * day;
        float glowTwilight = twilight * altitudeFactor * sky.atmosphereStrength;
        if (glowAlpha > 0.002f || glowTwilight > 0.002f) {
            float haloStrength = sunBody != null ? 0.0f : sky.sunGlow; // a sun body has its own glow
            Glow glow = new Glow(glowAlpha, day, glowTwilight, haloStrength * altitudeFactor, sunWorld,
                    sky.atmosphereColor, sky.horizonColor, sky.twilightColor);
            drawSkyShader(camera, 1.0f, angle, axis, glow);
        }

        // 4) the sun, and bodies that are still seen in the day, over the glow
        if (sunBody != null) {
            inFront.add(sunBody);
            inFrontAlpha.add(1.0f);
        }
        if (!inFront.isEmpty()) {
            PlanetRenderer.renderPlanets(camera, viewPosition, celestialToWorld, inFront, toArray(inFrontAlpha), 1.0f);
        }
        return true;
    }

    private static float[] toArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static boolean isSkyShaderReady() {
        try {
            return VeilRenderSystem.renderer().getShaderManager().getShader(SKY_SHADER) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Inside a planet's dimension (normally the overworld): fades the space sky and the planets
     * in over the normal sky as the camera climbs from ModDimensions.SPACE_TRANSITION_START_Y
     * to SPACE_TRANSITION_END_Y. The server teleports the player to space at the end height.
     *
     * If a planet is linked to this dimension in the editor, the planets are drawn as seen from
     * the matching point above that planet, so the transition (and the teleport) never jumps.
     * Without a linked planet, only the plain overworld is handled and planets keep their
     * real positions.
     *
     * Called after the vanilla sky has been drawn (Veil's AFTER_SKY stage).
     */
    public static void renderTransition(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.dimension() == ModDimensions.SPACE_LEVEL) {
            return;
        }

        // Which planet (if any) is "home" for this dimension?
        String dimensionId = minecraft.level.dimension().location().toString();
        PlanetRenderer.Planet home = null;
        for (PlanetRenderer.Planet planet : PlanetRenderer.getPlanets()) {
            if (dimensionId.equals(planet.dimension())) {
                home = planet;
                break;
            }
        }
        if (home == null && minecraft.level.dimension() != Level.OVERWORLD) {
            return;
        }

        double start = ModDimensions.SPACE_TRANSITION_START_Y;
        double end = ModDimensions.SPACE_TRANSITION_END_Y;
        Vec3 cameraPos = camera.getPosition();
        float progress = (float) Mth.clamp((cameraPos.y - start) / (end - start), 0.0, 1.0);
        if (progress <= 0.001f) {
            return;
        }

        // Ease in and out so it starts and finishes gently
        float alpha = progress * progress * (3.0f - 2.0f * progress);

        if (isSkyShaderReady()) {
            drawSkyShader(camera, alpha, 0.0f, SPACE_AXIS, null);
        }

        Vec3 viewPosition = cameraPos;
        if (home != null) {
            double altitude = Math.max(0.0, cameraPos.y - ModDimensions.HOME_SURFACE_Y);
            Vec3 offset = PlanetGates.capOffset(cameraPos.x, cameraPos.z, home.radius() + altitude);
            Vec3 center = PlanetRenderer.positionOf(home);
            viewPosition = center.add(offset);
        }
        PlanetRenderer.render(camera, alpha, viewPosition);
    }

    private static boolean drawSkyShader(Camera camera, float opacity, float angle, Vector3f axis, Glow glow) {
        ShaderProgram shader = VeilRenderSystem.setShader(SKY_SHADER);
        if (shader == null) {
            return false;
        }
        shader.bind();

        // Camera orientation in world space
        Quaternionf rotation = camera.rotation();
        Vector3f right = rotation.transform(1.0f, 0.0f, 0.0f, new Vector3f());
        Vector3f up = rotation.transform(0.0f, 1.0f, 0.0f, new Vector3f());
        Vector3f forward = rotation.transform(0.0f, 0.0f, -1.0f, new Vector3f());

        // Field of view straight from the projection matrix (so spyglass zoom just works)
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        float tanX = 1.0f / projection.m00();
        float tanY = 1.0f / projection.m11();

        set(shader, "TanHalfFovX", tanX);
        set(shader, "TanHalfFovY", tanY);

        set(shader, "CamRightX", right.x);
        set(shader, "CamRightY", right.y);
        set(shader, "CamRightZ", right.z);
        set(shader, "CamUpX", up.x);
        set(shader, "CamUpY", up.y);
        set(shader, "CamUpZ", up.z);
        set(shader, "CamForwardX", forward.x);
        set(shader, "CamForwardY", forward.y);
        set(shader, "CamForwardZ", forward.z);

        set(shader, "Time", (Util.getMillis() % 3_600_000L) / 1000.0f);

        set(shader, "StarDensity", SkySettings.starDensity);
        set(shader, "StarBrightness", SkySettings.starBrightness);
        set(shader, "StarSize", SkySettings.starSize);
        set(shader, "Twinkle", SkySettings.twinkle);

        // Direction the Milky Way band is perpendicular to
        double tilt = Math.toRadians(SkySettings.bandTilt);
        double turn = Math.toRadians(SkySettings.bandRotation);
        set(shader, "BandStrength", SkySettings.bandStrength);
        set(shader, "BandWidth", SkySettings.bandWidth);
        set(shader, "BandNormalX", (float) (Math.sin(tilt) * Math.cos(turn)));
        set(shader, "BandNormalY", (float) Math.cos(tilt));
        set(shader, "BandNormalZ", (float) (Math.sin(tilt) * Math.sin(turn)));
        setColor(shader, "BandColorA", SkySettings.bandColorA);
        setColor(shader, "BandColorB", SkySettings.bandColorB);

        set(shader, "NebulaStrength", SkySettings.nebulaStrength);
        set(shader, "NebulaScale", SkySettings.nebulaScale);
        setColor(shader, "NebulaColorA", SkySettings.nebulaColorA);
        setColor(shader, "NebulaColorB", SkySettings.nebulaColorB);

        set(shader, "Seed", SkySettings.seed);
        set(shader, "Opacity", opacity);

        set(shader, "SkyAxisX", axis.x);
        set(shader, "SkyAxisY", axis.y);
        set(shader, "SkyAxisZ", axis.z);
        set(shader, "SkyAngle", angle);

        set(shader, "GlowOnly", glow != null ? 1.0f : 0.0f);
        if (glow != null) {
            set(shader, "SunWorldX", glow.sunDir().x);
            set(shader, "SunWorldY", glow.sunDir().y);
            set(shader, "SunWorldZ", glow.sunDir().z);
            setColor(shader, "AtmoZenith", glow.zenith());
            setColor(shader, "AtmoHorizon", glow.horizon());
            setColor(shader, "AtmoTwilight", glow.twilightColor());
            set(shader, "AtmoAlpha", glow.alpha());
            set(shader, "AtmoDay", glow.day());
            set(shader, "AtmoTwilight", glow.twilight());
            set(shader, "SunGlow", glow.sunGlow());
        }

        if (glow != null || opacity < 0.999f) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

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

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        return true;
    }

    private static void set(ShaderProgram shader, String name, float value) {
        shader.getUniformSafe(name).setFloat(value);
    }

    /** Sends a 0xRRGGBB colour as three uniforms: NameR, NameG, NameB. */
    private static void setColor(ShaderProgram shader, String name, int color) {
        set(shader, name + "R", ((color >> 16) & 0xFF) / 255.0f);
        set(shader, name + "G", ((color >> 8) & 0xFF) / 255.0f);
        set(shader, name + "B", (color & 0xFF) / 255.0f);
    }

    // ---------------------------------------------------------------------------------
    // OLD IMAGE SKYBOX (fallback)
    // ---------------------------------------------------------------------------------

    private void renderSkybox(Matrix4f base) {
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        for (int i = 0; i < 6; i++) {
            Matrix4f matrix = new Matrix4f(base);

            switch (i) {
                case 1 -> matrix.rotate(Axis.XP.rotationDegrees(90.0f));
                case 2 -> matrix.rotate(Axis.XP.rotationDegrees(-90.0f));
                case 3 -> matrix.rotate(Axis.XP.rotationDegrees(180.0f));
                case 4 -> matrix.rotate(Axis.ZP.rotationDegrees(90.0f));
                case 5 -> matrix.rotate(Axis.ZP.rotationDegrees(-90.0f));
                default -> {
                }
            }

            RenderSystem.setShaderTexture(0, FACES[i]);

            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.addVertex(matrix, -100.0f, -100.0f, -100.0f).setUv(0.0f, 0.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, -100.0f, -100.0f, 100.0f).setUv(0.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, 100.0f, -100.0f, 100.0f).setUv(1.0f, 1.0f).setColor(255, 255, 255, 255);
            buffer.addVertex(matrix, 100.0f, -100.0f, -100.0f).setUv(1.0f, 0.0f).setColor(255, 255, 255, 255);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
    }
}