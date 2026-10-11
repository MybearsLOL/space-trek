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

    public static void initialize() {}
}