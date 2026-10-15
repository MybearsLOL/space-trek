package com.spacetrek;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The planets that can be entered, as the SERVER needs to know them.
 *
 * It reads the same file the planet editor saves (config/space-trek-planets.json) and picks
 * up changes while the game runs. Only planets with a "dimension" set count as enterable.
 *
 * How entering and leaving a planet lines up (so the view never jumps):
 *   - Inside the planet's dimension, the world around (x, z) is treated as the top cap of
 *     the planet, and the height above ground (y - HOME_SURFACE_Y) is the altitude.
 *   - That gives a position in space: planet centre + capOffset(x, z, radius + altitude).
 *   - Entering from space uses the same formula backwards: x and z in the dimension are the
 *     horizontal offset from the planet's centre.
 */
public final class PlanetGates {

    /** One enterable planet. */
    public record Gate(double x, double y, double z, float radius, ResourceKey<Level> dimension) {
        public Vec3 center() {
            return new Vec3(x, y, z);
        }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("space-trek-planets.json");

    private static volatile List<Gate> gates = List.of();
    private static FileTime lastModified = null;
    private static int tickCounter = 0;

    private PlanetGates() {
    }

    public static List<Gate> gates() {
        return gates;
    }

    public static Gate forDimension(ResourceKey<Level> dimension) {
        for (Gate gate : gates) {
            if (gate.dimension().equals(dimension)) {
                return gate;
            }
        }
        return null;
    }

    /** Call once per server tick. Checks the file about once a second. */
    public static void tick() {
        if (tickCounter++ % 20 != 0) {
            return;
        }
        try {
            FileTime now = Files.getLastModifiedTime(FILE);
            if (!now.equals(lastModified)) {
                lastModified = now;
                reload();
            }
        } catch (IOException ignored) {
            // file does not exist (yet) or is being saved
        }
    }

    private static void reload() {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            List<Gate> list = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("planets")) {
                JsonObject o = element.getAsJsonObject();
                String dimension = o.has("dimension") ? o.get("dimension").getAsString().trim() : "";
                if (dimension.isEmpty()) {
                    continue;
                }
                ResourceLocation id = ResourceLocation.tryParse(dimension);
                if (id == null) {
                    SpaceTrek.LOGGER.warn("Planet has an invalid dimension id: {}", dimension);
                    continue;
                }
                list.add(new Gate(
                        o.has("x") ? o.get("x").getAsDouble() : 0.0,
                        o.has("y") ? o.get("y").getAsDouble() : 300.0,
                        o.has("z") ? o.get("z").getAsDouble() : -6000.0,
                        o.has("radius") ? o.get("radius").getAsFloat() : 1000.0f,
                        ResourceKey.create(Registries.DIMENSION, id)
                ));
            }
            gates = List.copyOf(list);
        } catch (Exception e) {
            SpaceTrek.LOGGER.warn("Could not read planet gates: {}", e.toString());
        }
    }

    /**
     * A point on the upper cap of a sphere: horizontal position (x, z), and the height that puts
     * it exactly 'distance' away from the centre. Used by both the server (teleports) and the
     * client (drawing the planets), so they always agree.
     */
    public static Vec3 capOffset(double x, double z, double distance) {
        double horizontal = Math.hypot(x, z);
        double limit = distance * 0.999;
        if (horizontal > limit && horizontal > 0.0) {
            double scale = limit / horizontal;
            x *= scale;
            z *= scale;
            horizontal = limit;
        }
        double y = Math.sqrt(Math.max(0.0, distance * distance - horizontal * horizontal));
        return new Vec3(x, y, z);
    }
}