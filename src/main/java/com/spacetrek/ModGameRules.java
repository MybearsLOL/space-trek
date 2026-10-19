package com.spacetrek;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;

/**
 * World settings that show up in the game rules list.
 *
 * "Day length (minutes)" can be set BEFORE the world is created:
 *   Create New World -> More tab -> Game Rules -> Updates -> Day length (minutes)
 * and changed later with   /gamerule spacetrekDayLengthMinutes <minutes>
 *
 * 20 is Minecraft's normal day. Anything else makes SkyClock run the world's time at that speed.
 */
public final class ModGameRules {

    /** Real minutes one Minecraft day lasts (1 to 1440 = 24 hours). Default 20 = vanilla. */
    public static final GameRules.Key<GameRules.IntRule> DAY_LENGTH_MINUTES = GameRuleRegistry.register(
            "spacetrekDayLengthMinutes",
            GameRules.Category.UPDATES,
            GameRuleFactory.createIntRule(20, 1, 1440));

    private ModGameRules() {
    }

    /** Call once at startup so the rule is registered before any world is created. */
    public static void register() {
        // Touching this class registers the rule (see the static field above).
    }
}