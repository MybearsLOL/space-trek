package com.spacetrek.client;

import com.spacetrek.ModBlockEntities;
import com.spacetrek.ModBlocks;
import com.spacetrek.ModDimensions;
import com.spacetrek.ModFluids;
import com.spacetrek.ModMenuTypes;
import foundry.veil.platform.VeilEventPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class SpaceTrekClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Rocket fuel
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.ROCKET_FUEL_SOURCE,
				ModFluids.ROCKET_FUEL_FLOWING,
				SimpleFluidRenderHandler.coloredWater(0xFFD700)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(
				RenderType.translucent(),
				ModFluids.ROCKET_FUEL_SOURCE,
				ModFluids.ROCKET_FUEL_FLOWING
		);

		// Oxygen
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.OXYGEN_SOURCE,
				ModFluids.OXYGEN_FLOWING,
				SimpleFluidRenderHandler.coloredWater(0xAADDFF)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(
				RenderType.translucent(),
				ModFluids.OXYGEN_SOURCE,
				ModFluids.OXYGEN_FLOWING
		);

		// Kerosene
		FluidRenderHandlerRegistry.INSTANCE.register(
				ModFluids.KEROSENE_SOURCE,
				ModFluids.KEROSENE_FLOWING,
				SimpleFluidRenderHandler.coloredWater(0xD42B2B)
		);
		BlockRenderLayerMap.INSTANCE.putFluids(
				RenderType.translucent(),
				ModFluids.KEROSENE_SOURCE,
				ModFluids.KEROSENE_FLOWING
		);

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FUEL_TANK_BLOCK, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.INTAKE_BLOCK, RenderType.cutout());

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.KEROSENE_DISTILLER_BLOCK, RenderType.cutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FUEL_SYNTHESIZER_BLOCK, RenderType.cutout());

		BlockEntityRenderers.register(ModBlockEntities.FUEL_TANK, FuelTankBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.INTAKE, IntakeBlockEntityRenderer::new);
		MenuScreens.register(ModMenuTypes.INTAKE, IntakeScreen::new);
		MenuScreens.register(ModMenuTypes.KEROSENE_DISTILLER, KeroseneDistillerScreen::new);

		HudRenderCallback.EVENT.register((gui, deltaTracker) -> {
			float partial = deltaTracker.getGameTimeDeltaPartialTick(true);
			FuelTankHud.render(gui, partial);
			SynthesizerHud.render(gui, partial);
		});

		// Live planet editing: edit config/space-trek-planets.json while the game runs
		PlanetConfig.init();
		SkyConfig.init();

		// "Planet Editor" window in the Veil editor (F6). Only called when ImGuiMC is installed.
		VeilEventPlatform.INSTANCE.onVeilRegisterInspectors(registry ->
				{
					registry.registerInspector(new PlanetInspector());
					registry.registerInspector(new SkyInspector());
				}
		);

		// Overworld: fade the space sky in as the player climbs (see ModDimensions.SPACE_TRANSITION_*)
		VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
				(stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) -> {
					if (stage == foundry.veil.api.event.VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
						// Our own sky where the Sky Editor enables one; otherwise the plain fade-in above y 500
						float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
						if (!SpaceSkyRenderer.renderDimensionSky(camera, partialTick)) {
							SpaceSkyRenderer.renderTransition(camera);
						}
					}
				}
		);

		// Space dimension: our own sky (black + stars + planets), no clouds, no weather
		DimensionRenderingRegistry.registerSkyRenderer(ModDimensions.SPACE_LEVEL, new SpaceSkyRenderer());
		DimensionRenderingRegistry.registerCloudRenderer(ModDimensions.SPACE_LEVEL, context -> {});
		DimensionRenderingRegistry.registerWeatherRenderer(ModDimensions.SPACE_LEVEL, context -> {});
	}
}