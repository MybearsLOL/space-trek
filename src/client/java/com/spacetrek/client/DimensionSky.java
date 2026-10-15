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

    /** Other planets that can be seen in this dimension's sky (keys). */
    public List<String> planets = new ArrayList<>();

    /** Tilt of the sky's turning axis in degrees. 0 = the vanilla east-west sun path. */
    public float poleTilt = 0.0f;

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
        c.planets = new ArrayList<>(planets);
        c.poleTilt = poleTilt;
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