package com.spacetrek.client;

import com.spacetrek.ModBlockEntities;
import com.spacetrek.ModBlocks;
import com.spacetrek.ModFluids;
import foundry.veil.platform.VeilEventPlatform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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

		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.FUEL_TANK_BLOCK, RenderType.translucent());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.INTAKE_BLOCK, RenderType.cutout());

		BlockEntityRenderers.register(ModBlockEntities.FUEL_TANK, FuelTankBlockEntityRenderer::new);
		BlockEntityRenderers.register(ModBlockEntities.INTAKE, IntakeBlockEntityRenderer::new);

		HudRenderCallback.EVENT.register((gui, deltaTracker) ->
				FuelTankHud.render(gui, deltaTracker.getGameTimeDeltaPartialTick(true))
		);

		VeilEventPlatform.INSTANCE.onVeilRenderLevelStage(
				(stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) -> {
					if (stage == foundry.veil.api.event.VeilRenderLevelStageEvent.Stage.AFTER_SKY) {
						PlanetRenderer.render(camera);
					}
				}
		);
	}
}