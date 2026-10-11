package com.spacetrek.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlanetRenderer {

    private static final int LATITUDE_SEGMENTS = 48;
    private static final int LONGITUDE_SEGMENTS = 48;
    private static final float PLANET_RADIUS = 60.0f;

    private static final double PLANET_WORLD_X = 0.0;
    private static final double PLANET_WORLD_Y = 150.0;
    private static final double PLANET_WORLD_Z = -400.0;

    private static final float RENDER_DISTANCE = 300.0f;

    // Fake light direction for pseudo-3D shading
    private static final Vector3f LIGHT_DIR = new Vector3f(0.5f, 0.7f, 0.5f).normalize();

    public static void render(Camera camera) {
        Vec3 camPos = camera.getPosition();

        Vector3f direction = new Vector3f(
                (float) (PLANET_WORLD_X - camPos.x),
                (float) (PLANET_WORLD_Y - camPos.y),
                (float) (PLANET_WORLD_Z - camPos.z)
        ).normalize();

        Vector3f renderOffset = new Vector3f(direction).mul(RENDER_DISTANCE);

        Quaternionf inverseRotation = camera.rotation().conjugate(new Quaternionf());

        Matrix4f matrix = new Matrix4f()
                .rotation(inverseRotation)
                .translate(renderOffset);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableCull();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int lat = 0; lat < LATITUDE_SEGMENTS; lat++) {
            float theta1 = (float) (Math.PI * lat / LATITUDE_SEGMENTS);
            float theta2 = (float) (Math.PI * (lat + 1) / LATITUDE_SEGMENTS);

            for (int lon = 0; lon < LONGITUDE_SEGMENTS; lon++) {
                float phi1 = (float) (2 * Math.PI * lon / LONGITUDE_SEGMENTS);
                float phi2 = (float) (2 * Math.PI * (lon + 1) / LONGITUDE_SEGMENTS);

                // Winding reversed (swap last two vertices of each triangle)
                addVertex(buffer, matrix, theta1, phi1);
                addVertex(buffer, matrix, theta2, phi2);
                addVertex(buffer, matrix, theta2, phi1);

                addVertex(buffer, matrix, theta1, phi1);
                addVertex(buffer, matrix, theta1, phi2);
                addVertex(buffer, matrix, theta2, phi2);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void addVertex(BufferBuilder buffer, Matrix4f matrix, float theta, float phi) {
        float nx = (float) (Math.sin(theta) * Math.cos(phi));
        float ny = (float) Math.cos(theta);
        float nz = (float) (Math.sin(theta) * Math.sin(phi));

        float x = PLANET_RADIUS * nx;
        float y = PLANET_RADIUS * ny;
        float z = PLANET_RADIUS * nz;

        // Fake Lambertian lighting only — no hue, just grayscale brightness
        float brightness = Math.max(0.15f, nx * LIGHT_DIR.x + ny * LIGHT_DIR.y + nz * LIGHT_DIR.z);
        int gray = (int) (brightness * 255.0f);

        int rgb = (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
        buffer.addVertex(matrix, x, y, z).setColor(rgb);
    }
}