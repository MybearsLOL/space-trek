package com.spacetrek;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {

    public static final BlockEntityType<FuelTankBlockEntity> FUEL_TANK =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "fuel_tank"),
                    FabricBlockEntityTypeBuilder.create(FuelTankBlockEntity::new,
                            ModBlocks.FUEL_TANK_BLOCK).build());

    public static final BlockEntityType<IntakeBlockEntity> INTAKE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "intake"),
                    FabricBlockEntityTypeBuilder.create(IntakeBlockEntity::new,
                            ModBlocks.INTAKE_BLOCK).build());

    public static final BlockEntityType<CoalGeneratorBlockEntity> COAL_GENERATOR =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "coal_generator"),
                    FabricBlockEntityTypeBuilder.create(CoalGeneratorBlockEntity::new,
                            ModBlocks.COAL_GENERATOR_BLOCK).build());

    public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "solar_panel"),
                    FabricBlockEntityTypeBuilder.create(SolarPanelBlockEntity::new,
                            ModBlocks.SOLAR_PANEL_BLOCK).build());

    public static final BlockEntityType<KeroseneDistillerBlockEntity> KEROSENE_DISTILLER =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "kerosene_distiller"),
                    FabricBlockEntityTypeBuilder.create(KeroseneDistillerBlockEntity::new,
                            ModBlocks.KEROSENE_DISTILLER_BLOCK).build());

    public static final BlockEntityType<FuelSynthesizerBlockEntity> FUEL_SYNTHESIZER =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "fuel_synthesizer"),
                    FabricBlockEntityTypeBuilder.create(FuelSynthesizerBlockEntity::new,
                            ModBlocks.FUEL_SYNTHESIZER_BLOCK).build());

    public static final BlockEntityType<CoolerControllerBlockEntity> COOLER_CONTROLLER =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "cooler_controller"),
                    FabricBlockEntityTypeBuilder.create(CoolerControllerBlockEntity::new,
                            ModBlocks.COOLER_CONTROLLER_BLOCK).build());

    public static final BlockEntityType<CoolerPortBlockEntity> COOLER_PORT =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "cooler_port"),
                    FabricBlockEntityTypeBuilder.create(CoolerPortBlockEntity::new,
                            ModBlocks.COOLER_PORT_BLOCK).build());

    public static final BlockEntityType<HoloProjectorBlockEntity> HOLO_PROJECTOR =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "holo_projector"),
                    FabricBlockEntityTypeBuilder.create(HoloProjectorBlockEntity::new,
                            ModBlocks.HOLO_PROJECTOR_BLOCK).build());

    public static final BlockEntityType<FluidPumpBlockEntity> FLUID_PUMP =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "fluid_pump"),
                    FabricBlockEntityTypeBuilder.create(FluidPumpBlockEntity::new,
                            ModBlocks.FLUID_PUMP_BLOCK).build());

    public static void initialize() {}
}