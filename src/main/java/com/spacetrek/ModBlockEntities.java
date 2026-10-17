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

    public static void initialize() {}
}