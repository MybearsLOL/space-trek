package com.spacetrek.client;

/**
 * Live settings for the procedural space sky. Changed from the "Planet Editor" window
 * (Veil editor, F6) or from the "sky" block in config/space-trek-planets.json.
 */
public final class SkySettings {

    private SkySettings() {
    }

    /** true = procedural shader sky, false = the old image skybox. */
    public static boolean useShader = true;

    // ---- Stars ----
    public static float starDensity = 1.0f;
    public static float starBrightness = 1.0f;
    public static float starSize = 1.0f;
    public static float twinkle = 0.3f;

    // ---- Milky Way band ----
    public static float bandStrength = 1.0f;
    /** Thickness of the band (0.05 = thin line, 0.5 = wide haze). */
    public static float bandWidth = 0.22f;
    /** How far the band is tilted away from the horizon, in degrees. */
    public static float bandTilt = 60.0f;
    /** Which way around the sky the band is turned, in degrees. */
    public static float bandRotation = 25.0f;
    public static int bandColorA = 0x7FA0FF;
    public static int bandColorB = 0xFFC890;

    // ---- Nebulae ----
    public static float nebulaStrength = 0.6f;
    public static float nebulaScale = 2.2f;
    public static int nebulaColorA = 0x7A3CFF;
    public static int nebulaColorB = 0x1FB8C9;

    /** Change this to get a completely different (but still random-looking) sky. */
    public static float seed = 1.0f;
}