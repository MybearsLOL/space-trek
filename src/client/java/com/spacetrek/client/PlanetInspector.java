package com.spacetrek.client;

import foundry.veil.api.client.editor.SingleWindowInspector;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A "Planet Editor" window inside the Veil editor (press F6, then pick "Planet Editor").
 *
 * Every slider changes the planet on screen immediately. "Save to file" writes the values to
 * config/space-trek-planets.json so they are still there next time.
 */
public class PlanetInspector extends SingleWindowInspector {

    private static final Component TITLE = Component.literal("Planet Editor");
    private static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.parse("space-trek:textures/planet/example_planet.png");

    private static final ResourceLocation DEFAULT_CLOUD_TEXTURE =
            ResourceLocation.parse("space-trek:textures/planet/example_clouds.png");

    private final List<Editable> planets = new ArrayList<>();
    private final float[] ambient = new float[1];
    private final float[] terminatorSoftness = new float[1];
    private final float[] sunIntensity = new float[1];
    private final float[] mieStrength = new float[1];

    // ---- sky ----
    private final ImBoolean skyShader = new ImBoolean(true);
    private final float[] starDensity = new float[1];
    private final float[] starBrightness = new float[1];
    private final float[] starSize = new float[1];
    private final float[] twinkle = new float[1];
    private final float[] bandStrength = new float[1];
    private final float[] bandWidth = new float[1];
    private final float[] bandTilt = new float[1];
    private final float[] bandRotation = new float[1];
    private final float[] bandColorA = new float[3];
    private final float[] bandColorB = new float[3];
    private final float[] nebulaStrength = new float[1];
    private final float[] nebulaScale = new float[1];
    private final float[] nebulaColorA = new float[3];
    private final float[] nebulaColorB = new float[3];
    private final float[] skySeed = new float[1];

    private int seenVersion = -1;
    private String status = "";

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    public Component getGroup() {
        return DEFAULT_GROUP;
    }

    @Override
    protected void renderComponents() {
        // Pull in the current values the first time, and whenever the file was reloaded
        if (seenVersion != PlanetConfig.version()) {
            loadFromRenderer();
        }

        boolean changed = false;

        if (ImGui.collapsingHeader("Global settings", ImGuiTreeNodeFlags.DefaultOpen)) {
            changed |= ImGui.sliderFloat("Night side brightness", ambient, 0.0f, 1.0f);
            changed |= ImGui.sliderFloat("Shadow edge softness", terminatorSoftness, 0.005f, 0.5f);
            changed |= ImGui.sliderFloat("Sun intensity", sunIntensity, 0.0f, 100.0f);
            changed |= ImGui.sliderFloat("Haze (Mie) strength", mieStrength, 0.0f, 2.0f);
        }

        boolean skyChanged = false;
        if (ImGui.collapsingHeader("Sky (stars, Milky Way, nebulae)", ImGuiTreeNodeFlags.DefaultOpen)) {
            skyChanged |= ImGui.checkbox("Use shader sky (off = image skybox)", skyShader);
            skyChanged |= ImGui.sliderFloat("Star density", starDensity, 0.0f, 3.0f);
            skyChanged |= ImGui.sliderFloat("Star brightness", starBrightness, 0.0f, 4.0f);
            skyChanged |= ImGui.sliderFloat("Star size", starSize, 0.2f, 4.0f);
            skyChanged |= ImGui.sliderFloat("Twinkle", twinkle, 0.0f, 1.0f);

            ImGui.separator();
            skyChanged |= ImGui.sliderFloat("Milky Way strength", bandStrength, 0.0f, 3.0f);
            skyChanged |= ImGui.sliderFloat("Milky Way width", bandWidth, 0.05f, 0.6f);
            skyChanged |= ImGui.sliderFloat("Milky Way tilt", bandTilt, 0.0f, 90.0f);
            skyChanged |= ImGui.sliderFloat("Milky Way rotation", bandRotation, 0.0f, 360.0f);
            skyChanged |= ImGui.colorEdit3("Milky Way outer colour", bandColorA);
            skyChanged |= ImGui.colorEdit3("Milky Way core colour", bandColorB);

            ImGui.separator();
            skyChanged |= ImGui.sliderFloat("Nebula strength", nebulaStrength, 0.0f, 3.0f);
            skyChanged |= ImGui.sliderFloat("Nebula scale", nebulaScale, 0.5f, 8.0f);
            skyChanged |= ImGui.colorEdit3("Nebula colour A", nebulaColorA);
            skyChanged |= ImGui.colorEdit3("Nebula colour B", nebulaColorB);

            ImGui.separator();
            skyChanged |= ImGui.sliderFloat("Sky seed (new random sky)", skySeed, 0.0f, 100.0f);
        }

        int removeIndex = -1;
        for (int i = 0; i < planets.size(); i++) {
            Editable p = planets.get(i);
            ImGui.pushID(i);

            if (ImGui.collapsingHeader("Planet " + (i + 1), ImGuiTreeNodeFlags.DefaultOpen)) {
                if (ImGui.inputText("Texture (press Enter)", p.texture, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    p.appliedTexture = p.texture.get();
                    changed = true;
                }
                changed |= ImGui.dragFloat3("Position x y z", p.position, 10.0f, -1000000.0f, 1000000.0f);
                changed |= ImGui.dragFloat("Radius", p.radius, 10.0f, 1.0f, 1000000.0f);
                changed |= ImGui.sliderFloat("Spin (deg/tick)", p.spin, -1.0f, 1.0f);
                changed |= ImGui.sliderFloat("Axial tilt", p.tilt, -90.0f, 90.0f);

                ImGui.separator();
                changed |= ImGui.sliderFloat("Shadow angle", p.shadowAngle, 0.0f, 360.0f);
                changed |= ImGui.sliderFloat("Shadow tilt", p.shadowTilt, -90.0f, 90.0f);

                ImGui.separator();
                changed |= ImGui.colorEdit3("Atmosphere colour", p.atmosphereColor);
                changed |= ImGui.colorEdit3("Twilight colour", p.twilightColor);
                changed |= ImGui.sliderFloat("Atmosphere thickness", p.height, 0.0f, 0.3f);
                changed |= ImGui.sliderFloat("Atmosphere density", p.density, 0.0f, 2.0f);
                changed |= ImGui.sliderFloat("Glow brightness", p.brightness, 0.0f, 4.0f);

                ImGui.separator();
                if (ImGui.inputText("Enter this planet -> dimension (Enter)", p.dimension, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    p.appliedDimension = p.dimension.get().trim();
                    changed = true;
                }
                ImGui.text("e.g. minecraft:overworld. Empty = cannot be entered. Press Save to file to apply the teleports.");

                ImGui.separator();
                changed |= ImGui.checkbox("Clouds", p.cloudsEnabled);
                if (p.cloudsEnabled.get()) {
                    if (ImGui.inputText("Cloud texture (press Enter)", p.cloudTexture, ImGuiInputTextFlags.EnterReturnsTrue)) {
                        p.appliedCloudTexture = p.cloudTexture.get();
                        changed = true;
                    }
                    changed |= ImGui.dragFloat("Cloud altitude (blocks)", p.cloudAltitude, 1.0f, 0.0f, 100000.0f);
                    changed |= ImGui.sliderFloat("Cloud opacity", p.cloudOpacity, 0.0f, 1.0f);
                    changed |= ImGui.sliderFloat("Cloud spin (deg/tick)", p.cloudSpin, -1.0f, 1.0f);
                    changed |= ImGui.checkbox("Cloud image has transparency", p.cloudAlpha);
                }

                if (ImGui.button("Remove this planet")) {
                    removeIndex = i;
                }
            }

            ImGui.popID();
        }

        if (removeIndex >= 0) {
            planets.remove(removeIndex);
            changed = true;
        }

        ImGui.separator();
        if (ImGui.button("Add planet")) {
            planets.add(Editable.defaults());
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.button("Save to file")) {
            PlanetConfig.save(buildPlanets(), buildSettings());
            status = "Saved to config/space-trek-planets.json";
        }
        ImGui.sameLine();
        if (ImGui.button("Reload from file")) {
            PlanetConfig.reloadNow();
            status = "Reloaded from file";
        }

        if (!status.isEmpty()) {
            ImGui.text(status);
        }

        if (skyChanged) {
            SkySettings.useShader = skyShader.get();
            SkySettings.starDensity = starDensity[0];
            SkySettings.starBrightness = starBrightness[0];
            SkySettings.starSize = starSize[0];
            SkySettings.twinkle = twinkle[0];
            SkySettings.bandStrength = bandStrength[0];
            SkySettings.bandWidth = bandWidth[0];
            SkySettings.bandTilt = bandTilt[0];
            SkySettings.bandRotation = bandRotation[0];
            SkySettings.bandColorA = toColor(bandColorA);
            SkySettings.bandColorB = toColor(bandColorB);
            SkySettings.nebulaStrength = nebulaStrength[0];
            SkySettings.nebulaScale = nebulaScale[0];
            SkySettings.nebulaColorA = toColor(nebulaColorA);
            SkySettings.nebulaColorB = toColor(nebulaColorB);
            SkySettings.seed = skySeed[0];
        }

        if (changed) {
            PlanetRenderer.applySettings(ambient[0], terminatorSoftness[0], sunIntensity[0], mieStrength[0]);
            PlanetRenderer.setPlanets(buildPlanets());
        }
    }

    // ---- conversion between the renderer's planets and the editable copies ----

    private void loadFromRenderer() {
        seenVersion = PlanetConfig.version();

        float[] s = PlanetRenderer.getSettings();
        ambient[0] = s[0];
        terminatorSoftness[0] = s[1];
        sunIntensity[0] = s[2];
        mieStrength[0] = s[3];

        skyShader.set(SkySettings.useShader);
        starDensity[0] = SkySettings.starDensity;
        starBrightness[0] = SkySettings.starBrightness;
        starSize[0] = SkySettings.starSize;
        twinkle[0] = SkySettings.twinkle;
        bandStrength[0] = SkySettings.bandStrength;
        bandWidth[0] = SkySettings.bandWidth;
        bandTilt[0] = SkySettings.bandTilt;
        bandRotation[0] = SkySettings.bandRotation;
        fromColor(SkySettings.bandColorA, bandColorA);
        fromColor(SkySettings.bandColorB, bandColorB);
        nebulaStrength[0] = SkySettings.nebulaStrength;
        nebulaScale[0] = SkySettings.nebulaScale;
        fromColor(SkySettings.nebulaColorA, nebulaColorA);
        fromColor(SkySettings.nebulaColorB, nebulaColorB);
        skySeed[0] = SkySettings.seed;

        planets.clear();
        for (PlanetRenderer.Planet planet : PlanetRenderer.getPlanets()) {
            planets.add(Editable.from(planet));
        }
    }

    private float[] buildSettings() {
        return new float[]{ambient[0], terminatorSoftness[0], sunIntensity[0], mieStrength[0]};
    }

    private List<PlanetRenderer.Planet> buildPlanets() {
        List<PlanetRenderer.Planet> list = new ArrayList<>();
        for (Editable e : planets) {
            ResourceLocation texture = ResourceLocation.tryParse(e.appliedTexture);
            if (texture == null) {
                texture = DEFAULT_TEXTURE;
            }
            PlanetRenderer.Clouds clouds = null;
            if (e.cloudsEnabled.get()) {
                ResourceLocation cloudTexture = ResourceLocation.tryParse(e.appliedCloudTexture);
                if (cloudTexture == null) {
                    cloudTexture = DEFAULT_CLOUD_TEXTURE;
                }
                clouds = new PlanetRenderer.Clouds(
                        cloudTexture, e.cloudAltitude[0], e.cloudOpacity[0], e.cloudSpin[0], e.cloudAlpha.get());
            }

            list.add(new PlanetRenderer.Planet(
                    texture,
                    e.position[0], e.position[1], e.position[2],
                    e.radius[0],
                    e.spin[0],
                    e.tilt[0],
                    e.shadowAngle[0],
                    e.shadowTilt[0],
                    toColor(e.atmosphereColor),
                    toColor(e.twilightColor),
                    e.height[0],
                    e.density[0],
                    e.brightness[0],
                    clouds,
                    e.appliedDimension
            ));
        }
        return list;
    }

    private static int toColor(float[] rgb) {
        int r = Math.max(0, Math.min(255, Math.round(rgb[0] * 255.0f)));
        int g = Math.max(0, Math.min(255, Math.round(rgb[1] * 255.0f)));
        int b = Math.max(0, Math.min(255, Math.round(rgb[2] * 255.0f)));
        return (r << 16) | (g << 8) | b;
    }

    private static void fromColor(int color, float[] rgb) {
        rgb[0] = ((color >> 16) & 0xFF) / 255.0f;
        rgb[1] = ((color >> 8) & 0xFF) / 255.0f;
        rgb[2] = (color & 0xFF) / 255.0f;
    }

    /** One planet's values in the form ImGui widgets need (arrays and a text buffer). */
    private static final class Editable {
        final ImString texture = new ImString(256);
        String appliedTexture = DEFAULT_TEXTURE.toString();
        final float[] position = new float[3];
        final float[] radius = new float[1];
        final float[] spin = new float[1];
        final float[] tilt = new float[1];
        final float[] shadowAngle = new float[1];
        final float[] shadowTilt = new float[1];
        final float[] atmosphereColor = new float[3];
        final float[] twilightColor = new float[3];
        final float[] height = new float[1];
        final float[] density = new float[1];
        final float[] brightness = new float[1];

        final ImString dimension = new ImString(128);
        String appliedDimension = "";

        final ImBoolean cloudsEnabled = new ImBoolean(false);
        final ImString cloudTexture = new ImString(256);
        String appliedCloudTexture = DEFAULT_CLOUD_TEXTURE.toString();
        final float[] cloudAltitude = new float[]{10.0f};
        final float[] cloudOpacity = new float[]{0.9f};
        final float[] cloudSpin = new float[]{0.003f};
        final ImBoolean cloudAlpha = new ImBoolean(true);

        static Editable from(PlanetRenderer.Planet p) {
            Editable e = new Editable();
            e.appliedTexture = p.texture().toString();
            e.texture.set(e.appliedTexture);
            e.position[0] = (float) p.x();
            e.position[1] = (float) p.y();
            e.position[2] = (float) p.z();
            e.radius[0] = p.radius();
            e.spin[0] = p.spinDegPerTick();
            e.tilt[0] = p.axialTiltDeg();
            e.shadowAngle[0] = p.shadowAngleDeg();
            e.shadowTilt[0] = p.shadowTiltDeg();
            fromColor(p.atmosphereColor(), e.atmosphereColor);
            fromColor(p.twilightColor(), e.twilightColor);
            e.height[0] = p.atmosphereHeight();
            e.density[0] = p.atmosphereDensity();
            e.brightness[0] = p.atmosphereBrightness();

            e.appliedDimension = p.dimension() == null ? "" : p.dimension();
            e.dimension.set(e.appliedDimension);

            e.cloudTexture.set(e.appliedCloudTexture);
            PlanetRenderer.Clouds clouds = p.clouds();
            if (clouds != null) {
                e.cloudsEnabled.set(true);
                e.appliedCloudTexture = clouds.texture().toString();
                e.cloudTexture.set(e.appliedCloudTexture);
                e.cloudAltitude[0] = clouds.altitude();
                e.cloudOpacity[0] = clouds.opacity();
                e.cloudSpin[0] = clouds.spinDegPerTick();
                e.cloudAlpha.set(clouds.useAlpha());
            }
            return e;
        }

        static Editable defaults() {
            return from(new PlanetRenderer.Planet(
                    DEFAULT_TEXTURE,
                    0.0, 300.0, -3000.0,
                    1000.0f, 0.002f, 0.0f,
                    200.0f, 0.0f,
                    0x4DA8FF, 0xFF8A3D,
                    0.06f, 0.35f, 1.0f
            ));
        }
    }
}