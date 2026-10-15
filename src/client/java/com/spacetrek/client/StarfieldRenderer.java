package com.spacetrek.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A fixed field of stars drawn around the camera. The vanilla sky is switched off in the
 * space dimension, so this replaces the stars.
 */
public class StarfieldRenderer {

    private static final int STAR_COUNT = 2200;
    private static final float DISTANCE = 90.0f;
    private static final long SEED = 10842L;

    // 4 corners per star, 3 floats each
    private static final float[] POS = new float[STAR_COUNT * 4 * 3];
    // r, g, b per star
    private static final float[] COL = new float[STAR_COUNT * 3];

    static {
        Random random = new Random(SEED);
        for (int i = 0; i < STAR_COUNT; i++) {
            // Uniformly distributed direction on a sphere
            double z = random.nextDouble() * 2.0 - 1.0;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double ring = Math.sqrt(1.0 - z * z);
            Vector3f dir = new Vector3f((float) (ring * Math.cos(angle)), (float) z, (float) (ring * Math.sin(angle)));

            // Two directions perpendicular to dir so the quad faces the centre
            Vector3f ref = Math.abs(dir.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f u = dir.cross(ref, new Vector3f()).normalize();
            Vector3f v = dir.cross(u, new Vector3f()).normalize();

            // Most stars are tiny, a few are bigger
            float size = 0.10f + random.nextFloat() * random.nextFloat() * 0.32f;

            float cx = dir.x * DISTANCE;
            float cy = dir.y * DISTANCE;
            float cz = dir.z * DISTANCE;

            int p = i * 12;
            // corner 0: -u -v
            POS[p] = cx - u.x * size - v.x * size;
            POS[p + 1] = cy - u.y * size - v.y * size;
            POS[p + 2] = cz - u.z * size - v.z * size;
            // corner 1: +u -v
            POS[p + 3] = cx + u.x * size - v.x * size;
            POS[p + 4] = cy + u.y * size - v.y * size;
            POS[p + 5] = cz + u.z * size - v.z * size;
            // corner 2: +u +v
            POS[p + 6] = cx + u.x * size + v.x * size;
            POS[p + 7] = cy + u.y * size + v.y * size;
            POS[p + 8] = cz + u.z * size + v.z * size;
            // corner 3: -u +v
            POS[p + 9] = cx - u.x * size + v.x * size;
            POS[p + 10] = cy - u.y * size + v.y * size;
            POS[p + 11] = cz - u.z * size + v.z * size;

            // Brightness and a slight tint (blue-white, white, or warm)
            float brightness = 0.45f + random.nextFloat() * 0.55f;
            float tint = random.nextFloat();
            float r, g, b;
            if (tint < 0.25f) {
                r = 0.80f; g = 0.90f; b = 1.00f;
            } else if (tint < 0.85f) {
                r = 1.00f; g = 1.00f; b = 1.00f;
            } else {
                r = 1.00f; g = 0.93f; b = 0.78f;
            }
            COL[i * 3] = r * brightness;
            COL[i * 3 + 1] = g * brightness;
            COL[i * 3 + 2] = b * brightness;
        }
    }

    public static void render(Camera camera) {
        Quaternionf inverseCamRot = camera.rotation().conjugate(new Quaternionf());
        Matrix4f matrix = new Matrix4f().rotation(inverseCamRot);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < STAR_COUNT; i++) {
            int p = i * 12;
            float r = COL[i * 3];
            float g = COL[i * 3 + 1];
            float b = COL[i * 3 + 2];

            // two triangles: 0-1-2 and 0-2-3
            vertex(buffer, matrix, POS[p], POS[p + 1], POS[p + 2], r, g, b);
            vertex(buffer, matrix, POS[p + 3], POS[p + 4], POS[p + 5], r, g, b);
            vertex(buffer, matrix, POS[p + 6], POS[p + 7], POS[p + 8], r, g, b);

            vertex(buffer, matrix, POS[p], POS[p + 1], POS[p + 2], r, g, b);
            vertex(buffer, matrix, POS[p + 6], POS[p + 7], POS[p + 8], r, g, b);
            vertex(buffer, matrix, POS[p + 9], POS[p + 10], POS[p + 11], r, g, b);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix,
                               float x, float y, float z, float r, float g, float b) {
        buffer.addVertex(matrix, x, y, z).setColor(r, g, b, 1.0f);
    }
}