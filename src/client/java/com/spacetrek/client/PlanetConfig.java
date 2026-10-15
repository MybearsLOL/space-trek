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
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Live planet editing. Planets and atmosphere settings are read from
 *
 *     config/space-trek-planets.json      (in your dev setup: run/config/space-trek-planets.json)
 *
 * The file is created with defaults the first time. While the game is running, just edit it
 * in any text editor and save: the planets update within half a second, no restart needed.
 * A "Planet file error" message appears on the action bar if the JSON is invalid; the old
 * planets stay on screen until you fix it.
 */
public class PlanetConfig {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("space-trek-planets.json");
    private static FileTime lastModified = null;
    private static int tickCounter = 0;
    private static volatile int version = 0;

    /** Goes up every time the planets were replaced from the file (the editor uses this). */
    public static int version() {
        return version;
    }

    /** Re-reads the file right now. */
    public static void reloadNow() {
        reload(true);
    }

    /** Writes the planets and settings to the file (used by the in-game editor's Save button). */
    public static void save(List<PlanetRenderer.Planet> list, float[] s) {
        JsonObject root = new JsonObject();

        JsonObject settings = new JsonObject();
        settings.addProperty("ambient", s[0]);
        settings.addProperty("terminatorSoftness", s[1]);
        settings.addProperty("sunIntensity", s[2]);
        settings.addProperty("mieStrength", s[3]);
        root.add("settings", settings);

        JsonArray array = new JsonArray();
        for (PlanetRenderer.Planet p : list) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.name() == null ? "" : p.name());
            o.addProperty("texture", p.texture().toString());
            o.addProperty("x", p.x());
            o.addProperty("y", p.y());
            o.addProperty("z", p.z());
            o.addProperty("radius", p.radius());
            o.addProperty("spin", p.spinDegPerTick());
            o.addProperty("tilt", p.axialTiltDeg());
            o.addProperty("rotationX", p.rotationX());
            o.addProperty("rotationY", p.rotationY());
            o.addProperty("rotationZ", p.rotationZ());
            o.addProperty("shadowAngle", p.shadowAngleDeg());
            o.addProperty("shadowTilt", p.shadowTiltDeg());
            o.addProperty("atmosphereColor", String.format("#%06X", p.atmosphereColor() & 0xFFFFFF));
            o.addProperty("twilightColor", String.format("#%06X", p.twilightColor() & 0xFFFFFF));
            o.addProperty("atmosphereHeight", p.atmosphereHeight());
            o.addProperty("atmosphereDensity", p.atmosphereDensity());
            o.addProperty("atmosphereBrightness", p.atmosphereBrightness());
            o.addProperty("dimension", p.dimension() == null ? "" : p.dimension());
            PlanetRenderer.Clouds clouds = p.clouds();
            if (clouds != null) {
                JsonObject c = new JsonObject();
                c.addProperty("texture", clouds.texture().toString());
                c.addProperty("altitude", clouds.altitude());
                c.addProperty("opacity", clouds.opacity());
                c.addProperty("spin", clouds.spinDegPerTick());
                c.addProperty("useAlpha", clouds.useAlpha());
                o.add("clouds", c);
            }
            array.add(o);
        }
        root.add("planets", array);

        try {
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            lastModified = Files.getLastModifiedTime(FILE); // so the watcher does not reload our own save
            message("Planets saved");
        } catch (IOException e) {
            SpaceTrek.LOGGER.error("Could not save {}", FILE, e);
            message("Could not save planets: " + e.getMessage());
        }
    }

    private static final String DEFAULT_JSON = """
            {
              "settings": {
                "ambient": 0.06,
                "terminatorSoftness": 0.06,
                "sunIntensity": 20.0,
                "mieStrength": 0.2
              },
              "planets": [
                {
                  "name": "earth",
                  "texture": "space-trek:textures/planet/example_planet.png",
                  "x": 0, "y": 300, "z": -6000,
                  "radius": 2500,
                  "spin": 0.002,
                  "tilt": 15,
                  "shadowAngle": 200,
                  "shadowTilt": 10,
                  "atmosphereColor": "#4DA8FF",
                  "twilightColor": "#FF8A3D",
                  "atmosphereHeight": 0.06,
                  "atmosphereDensity": 0.35,
                  "atmosphereBrightness": 1.0,
                  "dimension": "minecraft:overworld"
                },
                {
                  "name": "red",
                  "texture": "space-trek:textures/planet/example_planet.png",
                  "x": -14000, "y": 200, "z": 4000,
                  "radius": 6000,
                  "spin": 0.001,
                  "tilt": -20,
                  "shadowAngle": 60,
                  "shadowTilt": 0,
                  "atmosphereColor": "#D9A066",
                  "twilightColor": "#FF5533",
                  "atmosphereHeight": 0.05,
                  "atmosphereDensity": 0.5,
                  "atmosphereBrightness": 1.0
                }
              ]
            }
            """;

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

        // Check the file twice a second
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

    private static void reload(boolean notify) {
        try {
            lastModified = Files.getLastModifiedTime(FILE);
        } catch (IOException e) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();

            JsonObject settings = root.has("settings") ? root.getAsJsonObject("settings") : new JsonObject();
            PlanetRenderer.applySettings(
                    getFloat(settings, "ambient", 0.06f),
                    getFloat(settings, "terminatorSoftness", 0.06f),
                    getFloat(settings, "sunIntensity", 20.0f),
                    getFloat(settings, "mieStrength", 0.2f)
            );

            List<PlanetRenderer.Planet> list = new ArrayList<>();
            JsonArray array = root.getAsJsonArray("planets");
            for (JsonElement element : array) {
                list.add(parsePlanet(element.getAsJsonObject()));
            }
            PlanetRenderer.setPlanets(list);
            version++;

            if (notify) {
                message("Planets reloaded (" + list.size() + ")");
            }
        } catch (Exception e) {
            SpaceTrek.LOGGER.warn("Planet file error: {}", e.toString());
            message("Planet file error: " + e.getMessage());
        }
    }

    private static PlanetRenderer.Planet parsePlanet(JsonObject o) {
        PlanetRenderer.Clouds clouds = null;
        if (o.has("clouds") && o.get("clouds").isJsonObject()) {
            JsonObject c = o.getAsJsonObject("clouds");
            clouds = new PlanetRenderer.Clouds(
                    ResourceLocation.parse(getString(c, "texture", "space-trek:textures/planet/example_clouds.png")),
                    getFloat(c, "altitude", 10.0f),
                    getFloat(c, "opacity", 0.9f),
                    getFloat(c, "spin", 0.003f),
                    c.has("useAlpha") ? c.get("useAlpha").getAsBoolean() : true
            );
        }

        return new PlanetRenderer.Planet(
                ResourceLocation.parse(getString(o, "texture", "space-trek:textures/planet/example_planet.png")),
                getDouble(o, "x", 0.0),
                getDouble(o, "y", 300.0),
                getDouble(o, "z", -6000.0),
                getFloat(o, "radius", 1000.0f),
                getFloat(o, "spin", 0.002f),
                getFloat(o, "tilt", 0.0f),
                getFloat(o, "shadowAngle", 200.0f),
                getFloat(o, "shadowTilt", 0.0f),
                getColor(o, "atmosphereColor", 0x4DA8FF),
                getColor(o, "twilightColor", 0xFF8A3D),
                getFloat(o, "atmosphereHeight", 0.06f),
                getFloat(o, "atmosphereDensity", 0.35f),
                getFloat(o, "atmosphereBrightness", 1.0f),
                clouds,
                getString(o, "dimension", "").trim(),
                getString(o, "name", "").trim(),
                getFloat(o, "rotationX", 0.0f),
                getFloat(o, "rotationY", 0.0f),
                getFloat(o, "rotationZ", 0.0f)
        );
    }

    // ---- small JSON helpers ----

    private static double getDouble(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
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