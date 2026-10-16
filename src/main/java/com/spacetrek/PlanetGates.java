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
 * The planets as the SERVER needs to know them: where they are (including orbits) and which
 * ones can be entered.
 *
 * It reads the same file the planet editor saves (config/space-trek-planets.json) and picks
 * up changes while the game runs. Only planets with a "dimension" set can be entered.
 *
 * How entering and leaving a planet lines up (so the view never jumps):
 *   - Inside the planet's dimension, the world around (x, z) is treated as the top cap of
 *     the planet, and the height above ground (y - HOME_SURFACE_Y) is the altitude.
 *   - That gives a position in space: planet centre + capOffset(x, z, radius + altitude).
 *   - Entering from space uses the same formula backwards: x and z in the dimension are the
 *     horizontal offset from the planet's centre.
 *
 * Orbits: a planet with an orbit parent moves around that parent; the position is a function
 * of the game time, so the client (drawing) and the server (teleports) always agree.
 */
public final class PlanetGates {

    /** One planet from the file (orbit values are all 0 / empty when it stands still). */
    private record Body(String key, double x, double y, double z, float radius,
                        String parent, float orbitRadius, float orbitPeriod,
                        float orbitInclination, float orbitNode, float orbitPhase) {
    }

    /** One enterable planet. */
    public record Gate(int bodyIndex, ResourceKey<Level> dimension) {
        public float radius() {
            List<Body> list = bodies;
            return bodyIndex < list.size() ? list.get(bodyIndex).radius() : 1000.0f;
        }

        /** Where the planet's centre is at this game time. */
        public Vec3 center(long gameTime) {
            return positionOf(bodyIndex, gameTime);
        }
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("space-trek-planets.json");

    private static volatile List<Body> bodies = List.of();
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
            List<Body> bodyList = new ArrayList<>();
            List<Gate> gateList = new ArrayList<>();

            int index = 0;
            for (JsonElement element : root.getAsJsonArray("planets")) {
                JsonObject o = element.getAsJsonObject();

                String name = o.has("name") ? o.get("name").getAsString().trim() : "";
                String key = name.isEmpty() ? "planet" + (index + 1) : name;

                JsonObject orbit = o.has("orbit") && o.get("orbit").isJsonObject() ? o.getAsJsonObject("orbit") : null;
                String parent = orbit != null && orbit.has("parent") ? orbit.get("parent").getAsString().trim() : "";

                bodyList.add(new Body(
                        key,
                        number(o, "x", 0.0),
                        number(o, "y", 300.0),
                        number(o, "z", -6000.0),
                        (float) number(o, "radius", 1000.0),
                        parent,
                        orbit != null ? (float) number(orbit, "radius", 5000.0) : 0.0f,
                        orbit != null ? (float) number(orbit, "periodTicks", 24000.0) : 0.0f,
                        orbit != null ? (float) number(orbit, "inclination", 0.0) : 0.0f,
                        orbit != null ? (float) number(orbit, "node", 0.0) : 0.0f,
                        orbit != null ? (float) number(orbit, "phase", 0.0) : 0.0f
                ));

                String dimension = o.has("dimension") ? o.get("dimension").getAsString().trim() : "";
                if (!dimension.isEmpty()) {
                    ResourceLocation id = ResourceLocation.tryParse(dimension);
                    if (id == null) {
                        SpaceTrek.LOGGER.warn("Planet has an invalid dimension id: {}", dimension);
                    } else {
                        gateList.add(new Gate(index, ResourceKey.create(Registries.DIMENSION, id)));
                    }
                }
                index++;
            }

            // Publish the bodies first: gates look their planet up in this list
            bodies = List.copyOf(bodyList);
            gates = List.copyOf(gateList);
        } catch (Exception e) {
            SpaceTrek.LOGGER.warn("Could not read planet gates: {}", e.toString());
        }
    }

    private static double number(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    // ---------------------------------------------------------------------------------
    // ORBITS
    // ---------------------------------------------------------------------------------

    /**
     * Where something is that orbits its parent: a circle of the given radius around the parent,
     * tilted by 'inclination' degrees, with the tilt direction turned by 'node' degrees around
     * the vertical axis. One full lap takes 'periodTicks' game ticks; 'phase' (degrees) is where
     * it starts. Returns the offset from the parent's centre.
     *
     * Used by the client too (PlanetRenderer), so drawing and teleports agree.
     */
    public static Vec3 orbitOffset(double radius, double periodTicks, double inclinationDeg,
                                   double nodeDeg, double phaseDeg, double gameTime) {
        double turns = periodTicks > 0.0 ? gameTime / periodTicks : 0.0;
        double angle = Math.toRadians(phaseDeg + 360.0 * (turns - Math.floor(turns)));

        double x0 = radius * Math.cos(angle);
        double z0 = radius * Math.sin(angle);

        double inclination = Math.toRadians(inclinationDeg);
        double y1 = -z0 * Math.sin(inclination);
        double z1 = z0 * Math.cos(inclination);

        double node = Math.toRadians(nodeDeg);
        double x2 = x0 * Math.cos(node) + z1 * Math.sin(node);
        double z2 = -x0 * Math.sin(node) + z1 * Math.cos(node);

        return new Vec3(x2, y1, z2);
    }

    /** Centre of body number 'index' (the order of the "planets" list in the file) at this game time. */
    public static Vec3 positionOf(int index, long gameTime) {
        return positionOf(bodies, index, gameTime, 0);
    }

    private static Vec3 positionOf(List<Body> list, int index, long gameTime, int depth) {
        if (index < 0 || index >= list.size()) {
            return Vec3.ZERO;
        }
        Body body = list.get(index);
        if (body.parent().isEmpty() || depth > 8) {
            return new Vec3(body.x(), body.y(), body.z());
        }
        for (int i = 0; i < list.size(); i++) {
            if (i != index && list.get(i).key().equals(body.parent())) {
                Vec3 parentPosition = positionOf(list, i, gameTime, depth + 1);
                return parentPosition.add(orbitOffset(body.orbitRadius(), body.orbitPeriod(),
                        body.orbitInclination(), body.orbitNode(), body.orbitPhase(), gameTime));
            }
        }
        // Parent not found: stand still
        return new Vec3(body.x(), body.y(), body.z());
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