package com.spacetrek;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {

    public static final LiquidBlock ROCKET_FUEL_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "rocket_fuel"),
            new LiquidBlock(ModFluids.ROCKET_FUEL_SOURCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_YELLOW)
                            .replaceable()
                            .noCollission()
                            .strength(100f)
                            .noLootTable()
                            .liquid())
    );

    public static final LiquidBlock OXYGEN_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "oxygen"),
            new LiquidBlock(ModFluids.OXYGEN_SOURCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .replaceable()
                            .noCollission()
                            .strength(100f)
                            .noLootTable()
                            .liquid())
    );

    public static final FuelTankBlock FUEL_TANK_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "fuel_tank"),
            new FuelTankBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .noOcclusion())
    );

    public static final IntakeBlock INTAKE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "intake"),
            new IntakeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion())
    );

    public static void initialize() {}
}