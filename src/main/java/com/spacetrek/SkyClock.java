package com.spacetrek;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Takes over Minecraft's day/night cycle when the world's "Day length (minutes)" gamerule is not 20,
 * or when a sky in the Sky Editor has "Take over the day/night cycle" ticked.
 *
 * The world's time of day then runs at the speed you choose ("day length", in ticks), so the
 * sky, the block lighting, mob behaviour and so on all follow the same clock. Everything that
 * changes the time still works: /time set, /time add, sleeping.
 *
 * Reads config/space-trek-skies.json (the file the Sky Editor saves) and picks up changes while
 * the game runs. The first sky that is enabled and has the takeover ticked is used.
 */
public final class SkyClock {

    private record Setting(double dayLengthTicks, boolean freeze) {
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("space-trek-skies.json");

    private static volatile Setting setting = null;
    private static FileTime lastModified = null;
    private static int tickCounter = 0;

    // Our own running clock (fractional, so slow or fast days stay smooth)
    private static boolean running = false;
    private static double accumulator = 0.0;
    private static long lastSet = 0L;

    private SkyClock() {
    }

    /** Call once per server tick, after the world has ticked. */
    public static void tick(MinecraftServer server) {
        checkFile();

        // Day length: the world's gamerule (set when the world is created) wins when it is not the
        // normal 20 minutes; otherwise the Sky Editor's setting is used, if the takeover is ticked.
        Setting fromEditor = setting;
        int minutes = server.getGameRules().getInt(ModGameRules.DAY_LENGTH_MINUTES);
        double dayLength;
        boolean freeze;
        if (minutes != 20) {
            dayLength = minutes * 1200.0; // 20 ticks a second, 60 seconds a minute
            freeze = fromEditor != null && fromEditor.freeze();
        } else if (fromEditor != null) {
            dayLength = fromEditor.dayLengthTicks();
            freeze = fromEditor.freeze();
        } else {
            running = false;
            return;
        }
        // /gamerule doDaylightCycle false also stops the clock
        if (!server.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            freeze = true;
        }

        ServerLevel clockLevel = server.overworld(); // the time of day is shared by all dimensions
        long worldTime = clockLevel.getDayTime();

        // First tick, or somebody changed the time (/time set, sleeping...): continue from there.
        // (Vanilla itself may add 1 per tick, so a difference of 1 is normal.)
        if (!running || Math.abs(worldTime - lastSet) > 1L) {
            accumulator = worldTime;
            lastSet = worldTime;
            running = true;
        }

        if (!freeze) {
            // 24000 vanilla time units make one full day
            accumulator += 24000.0 / dayLength;
        }
        long next = freeze ? lastSet : (long) accumulator;

        clockLevel.setDayTime(next);
        lastSet = next;

        // Tell the players every tick, so their sky and lighting stay exactly in step
        server.getPlayerList().broadcastAll(new ClientboundSetTimePacket(clockLevel.getGameTime(), next, true));
    }

    private static void checkFile() {
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
            Setting found = null;
            if (root.has("skies")) {
                for (JsonElement element : root.getAsJsonArray("skies")) {
                    JsonObject o = element.getAsJsonObject();
                    boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
                    boolean control = o.has("controlTime") && o.get("controlTime").getAsBoolean();
                    if (enabled && control) {
                        double length = o.has("dayLengthTicks") ? o.get("dayLengthTicks").getAsDouble() : 24000.0;
                        boolean freeze = o.has("freezeTime") && o.get("freezeTime").getAsBoolean();
                        found = new Setting(Math.max(20.0, length), freeze);
                        break;
                    }
                }
            }
            setting = found;
        } catch (Exception e) {
            SpaceTrek.LOGGER.warn("Could not read the day/night settings: {}", e.toString());
        }
    }
}