package com.spacetrek;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpaceTrek implements ModInitializer {
    public static final String MOD_ID = "space-trek";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("SpaceTrek loaded! Preparing for launch...");
        ModFluids.initialize();
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        ModEntities.initialize();
        PayloadTypeRegistry.playC2S().register(RocketInputPayload.TYPE, RocketInputPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RocketInputPayload.TYPE, (payload, context) -> {
            if (context.player().getVehicle() instanceof RocketEntity rocket) {
                rocket.setInput(payload.flags());
            }
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof RocketEntity rocket && !rocket.isAimedAt(player)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        ModMenuTypes.initialize();
        ModDimensions.register();
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.LAKES,
                ResourceKey.create(Registries.PLACED_FEATURE, id("crude_oil_pool")));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}