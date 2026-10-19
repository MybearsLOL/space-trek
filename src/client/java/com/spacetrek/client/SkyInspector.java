package com.spacetrek.client;

import foundry.veil.api.client.editor.SingleWindowInspector;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A "Sky Editor" window inside the Veil editor (press F6, then pick "Sky Editor").
 *
 * Two parts:
 *   - Space look: stars, Milky Way and nebulae (shared by every sky)
 *   - Dimension skies: for each dimension, which planet it is on, which other planets can be
 *     seen from there, and the atmosphere glow that covers the sky in the day
 *
 * Every slider applies immediately. "Save to file" writes config/space-trek-skies.json.
 */
public class SkyInspector extends SingleWindowInspector {

    private static final Component TITLE = Component.literal("Sky Editor");

    // ---- space look (shared) ----
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
    private final float[] seed = new float[1];

    // ---- dimension skies ----
    private final List<EditableSky> skies = new ArrayList<>();

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
        if (seenVersion != SkyConfig.version()) {
            load();
        }

        // The planets that exist right now (from the Planet Editor)
        List<String> planetKeys = new ArrayList<>();
        List<PlanetRenderer.Planet> planets = PlanetRenderer.getPlanets();
        for (int i = 0; i < planets.size(); i++) {
            planetKeys.add(PlanetRenderer.planetKey(planets.get(i), i));
        }

        // Which of those are stars (suns)
        List<String> starKeys = new ArrayList<>();
        for (int i = 0; i < planets.size(); i++) {
            if (planets.get(i).star() != null) {
                starKeys.add(planetKeys.get(i));
            }
        }

        boolean starsChanged = false;
        boolean skiesChanged = false;

        // ------------------------------------------------------------------ space look
        if (ImGui.collapsingHeader("Space look (stars, Milky Way, nebulae)", ImGuiTreeNodeFlags.DefaultOpen)) {
            starsChanged |= ImGui.checkbox("Use shader sky in space (off = image skybox)", skyShader);
            starsChanged |= ImGui.sliderFloat("Star density", starDensity, 0.0f, 3.0f);
            starsChanged |= ImGui.sliderFloat("Star brightness", starBrightness, 0.0f, 4.0f);
            starsChanged |= ImGui.sliderFloat("Star size", starSize, 0.2f, 4.0f);
            starsChanged |= ImGui.sliderFloat("Twinkle", twinkle, 0.0f, 1.0f);

            ImGui.separator();
            starsChanged |= ImGui.sliderFloat("Milky Way strength", bandStrength, 0.0f, 3.0f);
            starsChanged |= ImGui.sliderFloat("Milky Way width", bandWidth, 0.05f, 0.6f);
            starsChanged |= ImGui.sliderFloat("Milky Way tilt", bandTilt, 0.0f, 90.0f);
            starsChanged |= ImGui.sliderFloat("Milky Way rotation", bandRotation, 0.0f, 360.0f);
            starsChanged |= ImGui.colorEdit3("Milky Way outer colour", bandColorA);
            starsChanged |= ImGui.colorEdit3("Milky Way core colour", bandColorB);

            ImGui.separator();
            starsChanged |= ImGui.sliderFloat("Nebula strength", nebulaStrength, 0.0f, 3.0f);
            starsChanged |= ImGui.sliderFloat("Nebula scale", nebulaScale, 0.5f, 8.0f);
            starsChanged |= ImGui.colorEdit3("Nebula colour A", nebulaColorA);
            starsChanged |= ImGui.colorEdit3("Nebula colour B", nebulaColorB);

            ImGui.separator();
            starsChanged |= ImGui.sliderFloat("Sky seed (new random sky)", seed, 0.0f, 100.0f);
        }

        // ------------------------------------------------------------------ dimension skies
        int removeIndex = -1;
        for (int i = 0; i < skies.size(); i++) {
            EditableSky e = skies.get(i);
            ImGui.pushID(i);

            String title = (e.appliedDimension.isEmpty() ? "(no dimension)" : e.appliedDimension) + "###sky" + i;
            if (ImGui.collapsingHeader(title, ImGuiTreeNodeFlags.DefaultOpen)) {
                if (ImGui.inputText("Dimension (press Enter)", e.dimension, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    e.appliedDimension = e.dimension.get().trim();
                    skiesChanged = true;
                }
                skiesChanged |= ImGui.checkbox("Replace the vanilla sky here", e.enabled);

                ImGui.separator();
                ImGui.text("Planets");
                String preview = e.homePlanet.isEmpty() ? "(none)" : e.homePlanet;
                if (ImGui.beginCombo("Planet this dimension is on", preview)) {
                    if (ImGui.selectable("(none)", e.homePlanet.isEmpty())) {
                        e.homePlanet = "";
                        skiesChanged = true;
                    }
                    for (String key : planetKeys) {
                        if (ImGui.selectable(key, key.equals(e.homePlanet))) {
                            e.homePlanet = key;
                            skiesChanged = true;
                        }
                    }
                    ImGui.endCombo();
                }

                String sunPreview = e.sun.isEmpty() ? "(none)" : e.sun;
                if (ImGui.beginCombo("Sun (star that lights this sky)", sunPreview)) {
                    if (ImGui.selectable("(none)", e.sun.isEmpty())) {
                        e.sun = "";
                        skiesChanged = true;
                    }
                    for (String key : starKeys) {
                        if (ImGui.selectable(key, key.equals(e.sun))) {
                            e.sun = key;
                            skiesChanged = true;
                        }
                    }
                    ImGui.endCombo();
                }
                if (starKeys.isEmpty()) {
                    ImGui.text("(no stars yet - press Add sun in the Planet Editor)");
                }

                ImGui.text("Other planets seen in this sky:");
                boolean any = false;
                for (String key : planetKeys) {
                    if (key.equals(e.homePlanet)) {
                        continue;
                    }
                    any = true;
                    EditableBody body = e.visible.computeIfAbsent(key, k -> new EditableBody());
                    skiesChanged |= ImGui.checkbox(key, body.visible);
                    if (body.visible.get()) {
                        ImGui.pushID(key);
                        skiesChanged |= ImGui.sliderFloat("   opacity", body.opacity, 0.0f, 1.0f);
                        skiesChanged |= ImGui.checkbox("   still seen in the day", body.day);
                        ImGui.popID();
                    }
                }
                if (!any) {
                    ImGui.text("(no other planets exist - add them in the Planet Editor)");
                }
                skiesChanged |= ImGui.sliderFloat("Sky turn axis tilt", e.poleTilt, -90.0f, 90.0f);
                skiesChanged |= ImGui.sliderFloat("Sky turn axis direction", e.poleTurn, -180.0f, 180.0f);

                ImGui.separator();
                ImGui.text("Day and night cycle");
                skiesChanged |= ImGui.checkbox("Take over the day/night cycle", e.controlTime);
                if (e.controlTime.get()) {
                    skiesChanged |= ImGui.dragFloat("Day length (ticks, 24000 = vanilla)", e.dayLength, 100.0f, 200.0f, 10000000.0f);
                    float minutes = e.dayLength[0] / 20.0f / 60.0f;
                    ImGui.text(String.format("One day = %.1f minutes. Use /time set to pick the time of day.", minutes));
                    ImGui.text("A world gamerule 'Day length (minutes)' other than 20 overrides this.");
                    skiesChanged |= ImGui.checkbox("Freeze time", e.freeze);
                } else {
                    ImGui.text("Off: Minecraft's normal 20-minute day runs the sky.");
                }

                ImGui.separator();
                ImGui.text("Atmosphere glow");
                skiesChanged |= ImGui.colorEdit3("Sky colour (overhead)", e.atmosphereColor);
                skiesChanged |= ImGui.colorEdit3("Horizon colour", e.horizonColor);
                skiesChanged |= ImGui.colorEdit3("Sunset / sunrise colour", e.twilightColor);
                skiesChanged |= ImGui.sliderFloat("Glow strength (1 = hides stars by day)", e.strength, 0.0f, 1.0f);
                skiesChanged |= ImGui.sliderFloat("Soft sun glow (only when no Sun is chosen)", e.sunGlow, 0.0f, 2.0f);
                skiesChanged |= ImGui.sliderFloat("Night when sun below (deg)", e.dayStart, -30.0f, 10.0f);
                skiesChanged |= ImGui.sliderFloat("Full day when sun above (deg)", e.dayEnd, -5.0f, 60.0f);
                skiesChanged |= ImGui.dragFloat("Glow starts thinning at height", e.fadeStartY, 5.0f, 0.0f, 100000.0f);
                skiesChanged |= ImGui.dragFloat("Glow gone at height", e.fadeEndY, 5.0f, 0.0f, 100000.0f);

                ImGui.separator();
                if (ImGui.button("Remove this sky")) {
                    removeIndex = i;
                }
            }

            ImGui.popID();
        }

        if (removeIndex >= 0) {
            skies.remove(removeIndex);
            skiesChanged = true;
        }

        ImGui.separator();
        if (ImGui.button("Add dimension sky")) {
            skies.add(EditableSky.from(new DimensionSky()));
            skiesChanged = true;
        }
        ImGui.sameLine();
        if (ImGui.button("Save to file")) {
            applyStars();
            SkyConfig.save(buildSkies());
            status = "Saved to config/space-trek-skies.json";
        }
        ImGui.sameLine();
        if (ImGui.button("Reload from file")) {
            SkyConfig.reloadNow();
            status = "Reloaded from file";
        }

        if (!status.isEmpty()) {
            ImGui.text(status);
        }

        if (starsChanged) {
            applyStars();
        }
        if (skiesChanged) {
            SkyConfig.setSkies(buildSkies());
        }
    }

    // ---------------------------------------------------------------------------------
    // moving values between the editor and the live settings
    // ---------------------------------------------------------------------------------

    private void load() {
        seenVersion = SkyConfig.version();

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
        seed[0] = SkySettings.seed;

        skies.clear();
        for (DimensionSky sky : SkyConfig.getSkies()) {
            skies.add(EditableSky.from(sky));
        }
    }

    private void applyStars() {
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
        SkySettings.seed = seed[0];
    }

    private List<DimensionSky> buildSkies() {
        List<DimensionSky> list = new ArrayList<>();
        for (EditableSky e : skies) {
            DimensionSky sky = new DimensionSky();
            sky.dimension = e.appliedDimension;
            sky.enabled = e.enabled.get();
            sky.homePlanet = e.homePlanet;
            sky.sun = e.sun;
            for (Map.Entry<String, EditableBody> entry : e.visible.entrySet()) {
                EditableBody body = entry.getValue();
                if (body.visible.get() && !entry.getKey().equals(e.homePlanet)) {
                    sky.planets.add(new DimensionSky.SkyBody(entry.getKey(), body.opacity[0], body.day.get()));
                }
            }
            sky.poleTilt = e.poleTilt[0];
            sky.poleTurn = e.poleTurn[0];
            sky.controlTime = e.controlTime.get();
            sky.dayLengthTicks = e.dayLength[0];
            sky.freezeTime = e.freeze.get();
            sky.atmosphereColor = toColor(e.atmosphereColor);
            sky.horizonColor = toColor(e.horizonColor);
            sky.twilightColor = toColor(e.twilightColor);
            sky.atmosphereStrength = e.strength[0];
            sky.sunGlow = e.sunGlow[0];
            sky.dayStartElevation = e.dayStart[0];
            sky.dayEndElevation = e.dayEnd[0];
            sky.fadeStartY = e.fadeStartY[0];
            sky.fadeEndY = e.fadeEndY[0];
            list.add(sky);
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

    /** One planet or moon as it shows in a sky. */
    private static final class EditableBody {
        final ImBoolean visible = new ImBoolean(false);
        final float[] opacity = new float[]{1.0f};
        final ImBoolean day = new ImBoolean(false);
    }

    /** One dimension's sky in the form ImGui widgets need. */
    private static final class EditableSky {
        final ImString dimension = new ImString(128);
        String appliedDimension = "";
        final ImBoolean enabled = new ImBoolean(true);
        String homePlanet = "";
        String sun = "";
        final Map<String, EditableBody> visible = new LinkedHashMap<>();
        final float[] poleTilt = new float[1];
        final float[] poleTurn = new float[1];
        final ImBoolean controlTime = new ImBoolean(false);
        final float[] dayLength = new float[]{24000.0f};
        final ImBoolean freeze = new ImBoolean(false);
        final float[] atmosphereColor = new float[3];
        final float[] horizonColor = new float[3];
        final float[] twilightColor = new float[3];
        final float[] strength = new float[1];
        final float[] sunGlow = new float[1];
        final float[] dayStart = new float[1];
        final float[] dayEnd = new float[1];
        final float[] fadeStartY = new float[1];
        final float[] fadeEndY = new float[1];

        static EditableSky from(DimensionSky sky) {
            EditableSky e = new EditableSky();
            e.appliedDimension = sky.dimension;
            e.dimension.set(sky.dimension);
            e.enabled.set(sky.enabled);
            e.homePlanet = sky.homePlanet;
            e.sun = sky.sun;
            for (DimensionSky.SkyBody body : sky.planets) {
                EditableBody editable = new EditableBody();
                editable.visible.set(true);
                editable.opacity[0] = body.opacity;
                editable.day.set(body.visibleInDay);
                e.visible.put(body.key, editable);
            }
            e.poleTilt[0] = sky.poleTilt;
            e.poleTurn[0] = sky.poleTurn;
            e.controlTime.set(sky.controlTime);
            e.dayLength[0] = sky.dayLengthTicks;
            e.freeze.set(sky.freezeTime);
            fromColor(sky.atmosphereColor, e.atmosphereColor);
            fromColor(sky.horizonColor, e.horizonColor);
            fromColor(sky.twilightColor, e.twilightColor);
            e.strength[0] = sky.atmosphereStrength;
            e.sunGlow[0] = sky.sunGlow;
            e.dayStart[0] = sky.dayStartElevation;
            e.dayEnd[0] = sky.dayEndElevation;
            e.fadeStartY[0] = sky.fadeStartY;
            e.fadeEndY[0] = sky.fadeEndY;
            return e;
        }
    }
}