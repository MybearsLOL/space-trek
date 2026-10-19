package com.spacetrek.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The sky of one dimension (for example minecraft:overworld). Edited in the "Sky Editor"
 * window and stored in config/space-trek-skies.json.
 */
public final class DimensionSky {

    /** Dimension id, e.g. "minecraft:overworld". */
    public String dimension = "minecraft:overworld";

    /** true = our sky replaces the vanilla sky (sun, moon, stars, blue gradient) in this dimension. */
    public boolean enabled = true;

    /** Planet this dimension is on (its key, see PlanetRenderer.planetKey). Empty = none. */
    public String homePlanet = "";

    /**
     * The star that is this dimension's sun (its key). It is drawn in the sky, always visible in the
     * day, and the day/night cycle follows it. Empty = no sun disc (the sky still has day and night).
     */
    public String sun = "";

    /** Other planets / moons that can be seen in this dimension's sky. */
    public List<SkyBody> planets = new ArrayList<>();

    /** How one planet or moon shows up in this sky. */
    public static final class SkyBody {
        /** Planet key (its name, or "planet1", "planet2"... if unnamed). */
        public String key = "";
        /** 0 = invisible, 1 = fully visible. */
        public float opacity = 1.0f;
        /** true = still seen in the daytime, on top of the atmosphere glow (for example just the moon). */
        public boolean visibleInDay = false;

        public SkyBody() {
        }

        public SkyBody(String key, float opacity, boolean visibleInDay) {
            this.key = key;
            this.opacity = opacity;
            this.visibleInDay = visibleInDay;
        }

        public SkyBody copy() {
            return new SkyBody(key, opacity, visibleInDay);
        }
    }

    /** Tilt of the sky's turning axis in degrees. 0 = the vanilla east-west sun path. */
    public float poleTilt = 0.0f;

    /** Turns the tilted axis around the vertical, in degrees. Changes the direction the sun path leans. */
    public float poleTurn = 0.0f;

    // ---- Day / night cycle ----
    /** true = this sky takes over Minecraft's day/night cycle (the world time then follows the settings below). */
    public boolean controlTime = false;
    /** How long one full day lasts, in ticks. 24000 = vanilla (20 minutes); 12000 = 10 minutes. */
    public float dayLengthTicks = 24000.0f;
    /** true = time stands still (use /time set to pick the moment). */
    public boolean freezeTime = false;

    // ---- Atmosphere glow (covers the stars in the day, gone at night) ----
    public int atmosphereColor = 0x4DA8FF;
    public int horizonColor = 0xBFE3FF;
    public int twilightColor = 0xFF8A3D;
    /** 0 = no glow, 1 = the day sky completely hides the stars. */
    public float atmosphereStrength = 1.0f;
    /** Soft glow around the (invisible) sun. 0 = none. */
    public float sunGlow = 0.5f;
    /** Sun height in degrees: below this it is full night (no glow)... */
    public float dayStartElevation = -8.0f;
    /** ...above this it is full day (full glow). */
    public float dayEndElevation = 12.0f;
    /** The glow starts thinning above this height (blocks)... */
    public float fadeStartY = 250.0f;
    /** ...and is gone at this height, leaving the pure space sky. */
    public float fadeEndY = 500.0f;

    public DimensionSky copy() {
        DimensionSky c = new DimensionSky();
        c.dimension = dimension;
        c.enabled = enabled;
        c.homePlanet = homePlanet;
        c.sun = sun;
        c.planets = new ArrayList<>();
        for (SkyBody body : planets) {
            c.planets.add(body.copy());
        }
        c.poleTilt = poleTilt;
        c.poleTurn = poleTurn;
        c.controlTime = controlTime;
        c.dayLengthTicks = dayLengthTicks;
        c.freezeTime = freezeTime;
        c.atmosphereColor = atmosphereColor;
        c.horizonColor = horizonColor;
        c.twilightColor = twilightColor;
        c.atmosphereStrength = atmosphereStrength;
        c.sunGlow = sunGlow;
        c.dayStartElevation = dayStartElevation;
        c.dayEndElevation = dayEndElevation;
        c.fadeStartY = fadeStartY;
        c.fadeEndY = fadeEndY;
        return c;
    }
}