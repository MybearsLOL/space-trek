package com.spacetrek;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The space dimension: keys, the /space command, free flight, zero gravity and no void death.
 *
 * Call ModDimensions.register() from your main ModInitializer's onInitialize().
 *
 * The dimension itself is defined by two data files:
 *   data/space-trek/dimension_type/space.json
 *   data/space-trek/dimension/space.json
 *
 * The dimension is a completely empty void. While players are in it they get creative-style
 * flight at a higher speed, zero gravity, and they cannot be hurt by the void or by falling,
 * so they can fly any distance in any direction (up, down, sideways) without a care.
 * Dropped items, experience orbs and projectiles also have no gravity, so they float and
 * drift instead of falling.
 */
public class ModDimensions {

    /** Must match the folder under data/ (your mod id). */
    public static final String NAMESPACE = "space-trek";

    public static final ResourceKey<Level> SPACE_LEVEL = ResourceKey.create(
            Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "space"));

    /** Where you appear when you use /space. */
    private static final double ARRIVAL_X = 0.5;
    private static final double ARRIVAL_Y = 100.0;
    private static final double ARRIVAL_Z = 0.5;

    // ---- Leaving the overworld ----
    /** In the overworld the space sky starts fading in when the player passes this height... */
    public static final double SPACE_TRANSITION_START_Y = 500.0;
    /** ...and is fully visible at this height, where the player is teleported to space. */
    public static final double SPACE_TRANSITION_END_Y = 700.0;

    /** Ground level of the planet dimensions (sea level of the overworld). Altitude = y - this. */
    public static final double HOME_SURFACE_Y = 64.0;
    /** Players enter a planet this many blocks below SPACE_TRANSITION_END_Y, so they are not sent straight back. */
    private static final double ARRIVAL_MARGIN = 6.0;

    /** Marks players who just arrived from space; they descend slowly and take no fall damage. */
    private static final String REENTRY_TAG = "spacetrek_reentry";

    // ---- Flight ----
    /**
     * Flying speed in space. Vanilla is 0.05 (about 11 blocks/second). Holding sprint while
     * flying doubles it. 0.3 is roughly 6x vanilla. On a multiplayer server keep this at 0.4 or
     * lower, because the server rejects movement faster than about 200 blocks/second.
     */
    private static final float SPACE_FLY_SPEED = 0.3f;
    private static final float DEFAULT_FLY_SPEED = 0.05f;

    /** Marks players who were given flight by this mod (saved with the player). */
    private static final String FLIGHT_TAG = "spacetrek_space_flight";

    // ---- Zero gravity ----
    /** -1.0 removes all gravity (0 = normal gravity, -0.8 = 20% gravity, ...). */
    private static final double GRAVITY_AMOUNT = -1.0;
    private static final ResourceLocation GRAVITY_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(NAMESPACE, "space_gravity");

    // ---- Floating items ----
    /**
     * How much speed dropped items keep each tick.
     *   0.98  = vanilla air drag (default). Items just have no gravity.
     *   0.99  = they glide about twice as far before stopping
     *   1.0   = they never slow down and drift away forever
     * Anything other than 0.98 turns on extra per-tick velocity code.
     */
    private static final double ITEM_DRIFT = 0.98;
    /** Vanilla slows items by this much every tick in the air. */
    private static final double VANILLA_ITEM_DRAG = 0.98;

    /**
     * When true, every item that disappears in space prints why (in chat and in the log), for
     * example: reason=DISCARDED age=23 pos=(x, y, z). Set to false once items work.
     */
    private static final boolean DEBUG_ITEM_REMOVAL = true;

    public static void register() {
        ModGameRules.register();

        // /space  -> teleports you to the space dimension, and back again
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("space")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            return toggle(player);
                        })
                )
        );

        // No void death and no fall damage in space, at any height
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Arriving from space into a planet's dimension: no fall damage on the way down
            if (entity instanceof Player arriving
                    && source.is(DamageTypes.FALL)
                    && arriving.getTags().contains(REENTRY_TAG)) {
                return false;
            }
            if (entity.level().dimension() != SPACE_LEVEL) {
                return true;
            }
            if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
                return false;
            }
            if (entity instanceof Player && source.is(DamageTypes.FALL)) {
                return false;
            }
            return true;
        });

        // Dropped items, XP orbs and projectiles get no gravity the moment they appear in space
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (world.dimension() == SPACE_LEVEL && isWeightless(entity)) {
                entity.setNoGravity(true);
            }
        });

        // Every tick: re-assert "no gravity" on weightless things (in case an entity was created
        // in a way the load event missed). Only touches velocity if ITEM_DRIFT is not vanilla.
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.dimension() != SPACE_LEVEL) {
                return;
            }
            boolean adjustDrift = Math.abs(ITEM_DRIFT - VANILLA_ITEM_DRAG) > 1.0E-9;
            double boost = ITEM_DRIFT / VANILLA_ITEM_DRAG;
            for (Entity entity : world.getAllEntities()) {
                if (!isWeightless(entity)) {
                    continue;
                }
                if (!entity.isNoGravity()) {
                    entity.setNoGravity(true);
                }
                if (adjustDrift && entity instanceof ItemEntity item) {
                    item.setDeltaMovement(item.getDeltaMovement().scale(boost));
                }
            }
        });

        // Debug: say why an item disappeared
        if (DEBUG_ITEM_REMOVAL) {
            ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
                if (world.dimension() == SPACE_LEVEL && entity instanceof ItemEntity item) {
                    String message = String.format(
                            "[SpaceTrek] Item removed: reason=%s age=%d pos=(%.1f, %.1f, %.1f) motion=(%.2f, %.2f, %.2f)",
                            String.valueOf(item.getRemovalReason()), item.tickCount,
                            item.getX(), item.getY(), item.getZ(),
                            item.getDeltaMovement().x, item.getDeltaMovement().y, item.getDeltaMovement().z);
                    SpaceTrek.LOGGER.info(message);
                    for (ServerPlayer player : world.players()) {
                        player.sendSystemMessage(Component.literal(message));
                    }
                }
            });
        }

        // Flight and zero gravity while in space, back to normal when leaving
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PlanetGates.tick();
            SkyClock.tick(server);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean inSpace = player.level().dimension() == SPACE_LEVEL;
                boolean hasFlightTag = player.getTags().contains(FLIGHT_TAG);

                // Climbed out of a planet's atmosphere: continue in space, lined up with the planet
                if (!inSpace && player.getY() >= SPACE_TRANSITION_END_Y) {
                    if (leavePlanet(player)) {
                        continue;
                    }
                }

                // Flew down into a planet that can be entered (its "dimension" is set in the editor)
                if (inSpace && tryEnterPlanet(player)) {
                    continue;
                }

                handleReentry(player, inSpace);

                if (inSpace && !hasFlightTag) {
                    grantFlight(player);
                } else if (!inSpace && hasFlightTag) {
                    revokeFlight(player);
                } else if (inSpace) {
                    keepFlight(player);
                }

                setModifier(player.getAttribute(Attributes.GRAVITY), GRAVITY_MODIFIER_ID,
                        GRAVITY_AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, inSpace);
            }
        });
    }

    /** Things that should float in space instead of falling. */
    private static boolean isWeightless(Entity entity) {
        return entity instanceof ItemEntity
                || entity instanceof ExperienceOrb
                || entity instanceof Projectile;
    }

    /**
     * Player climbed above SPACE_TRANSITION_END_Y in a planet dimension: send them to space.
     * If a planet is linked to this dimension, they appear above that planet exactly where the
     * sky showed it; otherwise (plain overworld) they keep their coordinates.
     * Returns true if the player was teleported.
     */
    private static boolean leavePlanet(ServerPlayer player) {
        ServerLevel space = player.server.getLevel(SPACE_LEVEL);
        if (space == null) {
            return false;
        }

        PlanetGates.Gate gate = PlanetGates.forDimension(player.level().dimension());
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        if (gate != null) {
            double altitude = Math.max(0.0, y - HOME_SURFACE_Y);
            Vec3 offset = PlanetGates.capOffset(x, z, gate.radius() + altitude);
            Vec3 center = gate.center(player.level().getGameTime());
            x = center.x + offset.x;
            y = center.y + offset.y;
            z = center.z + offset.z;
        } else if (player.level().dimension() != Level.OVERWORLD) {
            return false;
        }

        player.removeTag(REENTRY_TAG);
        grantFlight(player);
        player.teleportTo(space, x, y, z, player.getYRot(), player.getXRot());
        return true;
    }

    /**
     * In space: if the player flew down to an enterable planet's entry altitude (from above),
     * teleport them into the planet's dimension, high above the ground, descending slowly.
     */
    private static boolean tryEnterPlanet(ServerPlayer player) {
        double arrivalY = SPACE_TRANSITION_END_Y - ARRIVAL_MARGIN;

        for (PlanetGates.Gate gate : PlanetGates.gates()) {
            Vec3 center = gate.center(player.level().getGameTime());
            double ox = player.getX() - center.x;
            double oy = player.getY() - center.y;
            double oz = player.getZ() - center.z;
            double entryDistance = gate.radius() + (arrivalY - HOME_SURFACE_Y);

            // Only from the upper half of the planet (that is the half the dimension maps onto)
            if (oy <= 0.0 || Math.sqrt(ox * ox + oy * oy + oz * oz) > entryDistance) {
                continue;
            }
            if (Math.hypot(ox, oz) > entryDistance * 0.999) {
                continue;
            }

            ServerLevel destination = player.server.getLevel(gate.dimension());
            if (destination == null) {
                continue;
            }

            player.addTag(REENTRY_TAG);
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.teleportTo(destination, ox, arrivalY, oz, player.getYRot(), player.getXRot());
            return true;
        }
        return false;
    }

    /** After arriving from space: slow falling until the player lands, so they never take fall damage. */
    private static void handleReentry(ServerPlayer player, boolean inSpace) {
        if (!player.getTags().contains(REENTRY_TAG)) {
            return;
        }
        if (inSpace || player.onGround() || player.isInWater() || player.getAbilities().flying) {
            player.removeTag(REENTRY_TAG);
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
        if (current == null || current.getDuration() < 20) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
        }
    }

    private static int toggle(ServerPlayer player) {
        MinecraftServer server = player.server;
        ServerLevel space = server.getLevel(SPACE_LEVEL);

        if (space == null) {
            player.sendSystemMessage(Component.literal(
                    "The space dimension is not loaded. Check that data/" + NAMESPACE
                            + "/dimension/space.json and dimension_type/space.json are in your resources."));
            return 0;
        }

        if (player.level().dimension() == SPACE_LEVEL) {
            ServerLevel overworld = server.overworld();
            int x = overworld.getSharedSpawnPos().getX();
            int z = overworld.getSharedSpawnPos().getZ();
            int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            player.teleportTo(overworld, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
        } else {
            // Flight first, so there is no moment where the player is falling
            grantFlight(player);
            player.teleportTo(space, ARRIVAL_X, ARRIVAL_Y, ARRIVAL_Z, player.getYRot(), player.getXRot());
        }
        return 1;
    }

    private static void grantFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.setFlyingSpeed(SPACE_FLY_SPEED);
        player.addTag(FLIGHT_TAG);
        player.onUpdateAbilities();
    }

    private static void revokeFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        // Creative and spectator players keep their normal flight
        if (!player.isCreative() && !player.isSpectator()) {
            abilities.mayfly = false;
            abilities.flying = false;
        }
        abilities.setFlyingSpeed(DEFAULT_FLY_SPEED);
        player.removeTag(FLIGHT_TAG);
        player.onUpdateAbilities();
    }

    /** Re-applies flight if something (like a game mode change) removed it while in space. */
    private static void keepFlight(ServerPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean changed = false;
        if (!abilities.mayfly) {
            abilities.mayfly = true;
            changed = true;
        }
        if (Math.abs(abilities.getFlyingSpeed() - SPACE_FLY_SPEED) > 0.0001f) {
            abilities.setFlyingSpeed(SPACE_FLY_SPEED);
            changed = true;
        }
        if (changed) {
            player.onUpdateAbilities();
        }
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount,
                                    AttributeModifier.Operation operation, boolean active) {
        if (attribute == null) return;
        boolean present = attribute.hasModifier(id);
        if (active && !present) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
        } else if (!active && present) {
            attribute.removeModifier(id);
        }
    }
}