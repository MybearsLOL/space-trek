package com.spacetrek.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.spacetrek.SpaceTrek;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Live sky settings, separate from the planets. Read from
 *
 *     config/space-trek-skies.json      (in your dev setup: run/config/space-trek-skies.json)
 *
 * The file holds the look of the stars ("stars") and one sky per dimension ("skies").
 * Edit it while the game runs and save: it reloads within half a second. The "Sky Editor"
 * window in the Veil editor (F6) edits the same values with sliders.
 */
public class SkyConfig {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("space-trek-skies.json");
    private static FileTime lastModified = null;
    private static int tickCounter = 0;

    private static volatile List<DimensionSky> skies = List.of();
    private static volatile int version = 0;

    private static final String DEFAULT_JSON = """
            {
              "stars": {
                "useShader": true,
                "starDensity": 1.0,
                "starBrightness": 1.0,
                "starSize": 1.0,
                "twinkle": 0.3,
                "bandStrength": 1.0,
                "bandWidth": 0.22,
                "bandTilt": 60,
                "bandRotation": 25,
                "bandColorA": "#7FA0FF",
                "bandColorB": "#FFC890",
                "nebulaStrength": 0.6,
                "nebulaScale": 2.2,
                "nebulaColorA": "#7A3CFF",
                "nebulaColorB": "#1FB8C9",
                "seed": 1.0
              },
              "skies": [
                {
                  "dimension": "minecraft:overworld",
                  "enabled": true,
                  "homePlanet": "",
                  "planets": [],
                  "poleTilt": 0,
                  "atmosphereColor": "#4DA8FF",
                  "horizonColor": "#BFE3FF",
                  "twilightColor": "#FF8A3D",
                  "atmosphereStrength": 1.0,
                  "sunGlow": 0.5,
                  "dayStartElevation": -8,
                  "dayEndElevation": 12,
                  "fadeStartY": 250,
                  "fadeEndY": 500
                }
              ]
            }
            """;

    public static List<DimensionSky> getSkies() {
        return skies;
    }

    public static void setSkies(List<DimensionSky> list) {
        List<DimensionSky> copy = new ArrayList<>();
        for (DimensionSky sky : list) {
            copy.add(sky.copy());
        }
        skies = List.copyOf(copy);
    }

    /** Goes up every time the skies were replaced from the file (the editor uses this). */
    public static int version() {
        return version;
    }

    /** The sky configured for this dimension id, or null. */
    public static DimensionSky get(String dimensionId) {
        for (DimensionSky sky : skies) {
            if (sky.dimension.equals(dimensionId)) {
                return sky;
            }
        }
        return null;
    }

    public static void init() {
        try {
            if (!Files.exists(FILE)) {
                Files.createDirectories(FILE.getParent());
                Files.writeString(FILE, DEFAULT_JSON);
            }
        } catch (IOException e) {
            SpaceTrek.LOGGER.error("Could not create {}", FILE, e);
        }
        reload(false);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (++tickCounter % 10 != 0) {
                return;
            }
            try {
                FileTime now = Files.getLastModifiedTime(FILE);
                if (!now.equals(lastModified)) {
                    reload(true);
                }
            } catch (IOException ignored) {
                // file temporarily locked or missing while your editor saves it
            }
        });
    }

    public static void reloadNow() {
        reload(true);
    }

    private static void reload(boolean notify) {
        try {
            lastModified = Files.getLastModifiedTime(FILE);
        } catch (IOException e) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();

            if (root.has("stars")) {
                parseStars(root.getAsJsonObject("stars"));
            }

            List<DimensionSky> list = new ArrayList<>();
            if (root.has("skies")) {
                for (JsonElement element : root.getAsJsonArray("skies")) {
                    list.add(parseSky(element.getAsJsonObject()));
                }
            }
            skies = List.copyOf(list);
            version++;

            if (notify) {
                message("Skies reloaded (" + list.size() + ")");
            }
        } catch (Exception e) {
            SpaceTrek.LOGGER.warn("Sky file error: {}", e.toString());
            message("Sky file error: " + e.getMessage());
        }
    }

    /** Writes the current skies and star settings (used by the Sky Editor's Save button). */
    public static void save(List<DimensionSky> list) {
        JsonObject root = new JsonObject();
        root.add("stars", starsToJson());

        JsonArray array = new JsonArray();
        for (DimensionSky sky : list) {
            JsonObject o = new JsonObject();
            o.addProperty("dimension", sky.dimension);
            o.addProperty("enabled", sky.enabled);
            o.addProperty("homePlanet", sky.homePlanet);
            o.addProperty("sun", sky.sun);
            JsonArray planets = new JsonArray();
            for (DimensionSky.SkyBody body : sky.planets) {
                JsonObject b = new JsonObject();
                b.addProperty("name", body.key);
                b.addProperty("opacity", body.opacity);
                b.addProperty("visibleInDay", body.visibleInDay);
                planets.add(b);
            }
            o.add("planets", planets);
            o.addProperty("poleTilt", sky.poleTilt);
            o.addProperty("poleTurn", sky.poleTurn);
            o.addProperty("controlTime", sky.controlTime);
            o.addProperty("dayLengthTicks", sky.dayLengthTicks);
            o.addProperty("freezeTime", sky.freezeTime);
            o.addProperty("atmosphereColor", hex(sky.atmosphereColor));
            o.addProperty("horizonColor", hex(sky.horizonColor));
            o.addProperty("twilightColor", hex(sky.twilightColor));
            o.addProperty("atmosphereStrength", sky.atmosphereStrength);
            o.addProperty("sunGlow", sky.sunGlow);
            o.addProperty("dayStartElevation", sky.dayStartElevation);
            o.addProperty("dayEndElevation", sky.dayEndElevation);
            o.addProperty("fadeStartY", sky.fadeStartY);
            o.addProperty("fadeEndY", sky.fadeEndY);
            array.add(o);
        }
        root.add("skies", array);

        try {
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            lastModified = Files.getLastModifiedTime(FILE); // so the watcher does not reload our own save
            message("Skies saved");
        } catch (IOException e) {
            SpaceTrek.LOGGER.error("Could not save {}", FILE, e);
            message("Could not save skies: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------------------
    // parsing
    // ---------------------------------------------------------------------------------

    private static DimensionSky parseSky(JsonObject o) {
        DimensionSky sky = new DimensionSky();
        sky.dimension = getString(o, "dimension", sky.dimension).trim();
        sky.enabled = o.has("enabled") ? o.get("enabled").getAsBoolean() : sky.enabled;
        sky.homePlanet = getString(o, "homePlanet", "").trim();
        sky.sun = getString(o, "sun", "").trim();
        if (o.has("planets")) {
            for (JsonElement element : o.getAsJsonArray("planets")) {
                DimensionSky.SkyBody body = new DimensionSky.SkyBody();
                if (element.isJsonObject()) {
                    JsonObject b = element.getAsJsonObject();
                    body.key = getString(b, "name", "").trim();
                    body.opacity = getFloat(b, "opacity", 1.0f);
                    body.visibleInDay = b.has("visibleInDay") && b.get("visibleInDay").getAsBoolean();
                } else {
                    body.key = element.getAsString().trim(); // plain name: default look
                }
                if (!body.key.isEmpty()) {
                    sky.planets.add(body);
                }
            }
        }
        sky.poleTilt = getFloat(o, "poleTilt", sky.poleTilt);
        sky.poleTurn = getFloat(o, "poleTurn", sky.poleTurn);
        sky.controlTime = o.has("controlTime") ? o.get("controlTime").getAsBoolean() : sky.controlTime;
        sky.dayLengthTicks = getFloat(o, "dayLengthTicks", sky.dayLengthTicks);
        sky.freezeTime = o.has("freezeTime") ? o.get("freezeTime").getAsBoolean() : sky.freezeTime;
        sky.atmosphereColor = getColor(o, "atmosphereColor", sky.atmosphereColor);
        sky.horizonColor = getColor(o, "horizonColor", sky.horizonColor);
        sky.twilightColor = getColor(o, "twilightColor", sky.twilightColor);
        sky.atmosphereStrength = getFloat(o, "atmosphereStrength", sky.atmosphereStrength);
        sky.sunGlow = getFloat(o, "sunGlow", sky.sunGlow);
        sky.dayStartElevation = getFloat(o, "dayStartElevation", sky.dayStartElevation);
        sky.dayEndElevation = getFloat(o, "dayEndElevation", sky.dayEndElevation);
        sky.fadeStartY = getFloat(o, "fadeStartY", sky.fadeStartY);
        sky.fadeEndY = getFloat(o, "fadeEndY", sky.fadeEndY);
        return sky;
    }

    private static void parseStars(JsonObject o) {
        SkySettings.useShader = o.has("useShader") ? o.get("useShader").getAsBoolean() : SkySettings.useShader;
        SkySettings.starDensity = getFloat(o, "starDensity", SkySettings.starDensity);
        SkySettings.starBrightness = getFloat(o, "starBrightness", SkySettings.starBrightness);
        SkySettings.starSize = getFloat(o, "starSize", SkySettings.starSize);
        SkySettings.twinkle = getFloat(o, "twinkle", SkySettings.twinkle);
        SkySettings.bandStrength = getFloat(o, "bandStrength", SkySettings.bandStrength);
        SkySettings.bandWidth = getFloat(o, "bandWidth", SkySettings.bandWidth);
        SkySettings.bandTilt = getFloat(o, "bandTilt", SkySettings.bandTilt);
        SkySettings.bandRotation = getFloat(o, "bandRotation", SkySettings.bandRotation);
        SkySettings.bandColorA = getColor(o, "bandColorA", SkySettings.bandColorA);
        SkySettings.bandColorB = getColor(o, "bandColorB", SkySettings.bandColorB);
        SkySettings.nebulaStrength = getFloat(o, "nebulaStrength", SkySettings.nebulaStrength);
        SkySettings.nebulaScale = getFloat(o, "nebulaScale", SkySettings.nebulaScale);
        SkySettings.nebulaColorA = getColor(o, "nebulaColorA", SkySettings.nebulaColorA);
        SkySettings.nebulaColorB = getColor(o, "nebulaColorB", SkySettings.nebulaColorB);
        SkySettings.seed = getFloat(o, "seed", SkySettings.seed);
    }

    private static JsonObject starsToJson() {
        JsonObject o = new JsonObject();
        o.addProperty("useShader", SkySettings.useShader);
        o.addProperty("starDensity", SkySettings.starDensity);
        o.addProperty("starBrightness", SkySettings.starBrightness);
        o.addProperty("starSize", SkySettings.starSize);
        o.addProperty("twinkle", SkySettings.twinkle);
        o.addProperty("bandStrength", SkySettings.bandStrength);
        o.addProperty("bandWidth", SkySettings.bandWidth);
        o.addProperty("bandTilt", SkySettings.bandTilt);
        o.addProperty("bandRotation", SkySettings.bandRotation);
        o.addProperty("bandColorA", hex(SkySettings.bandColorA));
        o.addProperty("bandColorB", hex(SkySettings.bandColorB));
        o.addProperty("nebulaStrength", SkySettings.nebulaStrength);
        o.addProperty("nebulaScale", SkySettings.nebulaScale);
        o.addProperty("nebulaColorA", hex(SkySettings.nebulaColorA));
        o.addProperty("nebulaColorB", hex(SkySettings.nebulaColorB));
        o.addProperty("seed", SkySettings.seed);
        return o;
    }

    // ---------------------------------------------------------------------------------
    // small helpers
    // ---------------------------------------------------------------------------------

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private static float getFloat(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static String getString(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    /** Accepts "#RRGGBB", "0xRRGGBB" or a plain number. */
    private static int getColor(JsonObject o, String key, int fallback) {
        if (!o.has(key)) {
            return fallback;
        }
        JsonElement element = o.get(key);
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() & 0xFFFFFF;
        }
        String text = element.getAsString().trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        return Integer.parseInt(text, 16) & 0xFFFFFF;
    }

    private static void message(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(text), true);
        }
    }
}