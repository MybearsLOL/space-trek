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
            drawn = drawProceduralSky(context.camera(), 1.0f);
        }
        if (!drawn) {
            renderSkybox(context.positionMatrix());
        }

        PlanetRenderer.render(context.camera());
    }

    // ---------------------------------------------------------------------------------
    // PROCEDURAL SKY (Veil shader)
    // ---------------------------------------------------------------------------------

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
            drawProceduralSky(camera, alpha);
        }

        Vec3 viewPosition = cameraPos;
        if (home != null) {
            double altitude = Math.max(0.0, cameraPos.y - ModDimensions.HOME_SURFACE_Y);
            Vec3 offset = PlanetGates.capOffset(cameraPos.x, cameraPos.z, home.radius() + altitude);
            viewPosition = new Vec3(home.x() + offset.x, home.y() + offset.y, home.z() + offset.z);
        }
        PlanetRenderer.render(camera, alpha, viewPosition);
    }

    private static boolean drawProceduralSky(Camera camera, float opacity) {
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

        if (opacity < 0.999f) {
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