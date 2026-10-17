package com.spacetrek;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

    public static final LiquidBlock KEROSENE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "kerosene"),
            new LiquidBlock(ModFluids.KEROSENE_SOURCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .replaceable()
                            .noCollission()
                            .strength(100f)
                            .noLootTable()
                            .liquid())
    );

    public static final LiquidBlock CRUDE_OIL_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "crude_oil"),
            new HotLiquidBlock(ModFluids.CRUDE_OIL_SOURCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .replaceable()
                            .noCollission()
                            .strength(100f)
                            .noLootTable()
                            .liquid(),
                    2.0f)
    );

    public static final LiquidBlock COOLED_CRUDE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "cooled_crude"),
            new LiquidBlock(ModFluids.COOLED_CRUDE_SOURCE,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BROWN)
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

    public static final CoalGeneratorBlock COAL_GENERATOR_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "coal_generator"),
            new CoalGeneratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 10 : 0))
    );

    public static final PowerCableBlock POWER_CABLE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "power_cable"),
            new PowerCableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(1.5f)
                    .noOcclusion())
    );

    public static final KeroseneDistillerBlock KEROSENE_DISTILLER_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "kerosene_distiller"),
            new KeroseneDistillerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 9 : 0))
    );

    public static final FuelSynthesizerBlock FUEL_SYNTHESIZER_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "fuel_synthesizer"),
            new FuelSynthesizerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 9 : 0))
    );

    public static final FluidPipeBlock FLUID_PIPE_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "fluid_pipe"),
            new FluidPipeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(1.5f)
                    .noOcclusion())
    );

    public static final CoolerCasingBlock COOLER_CASING_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "cooler_casing"),
            new CoolerCasingBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK))
    );

    public static final CoolerPortBlock COOLER_PORT_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "cooler_port"),
            new CoolerPortBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK))
    );

    public static final CoolerControllerBlock COOLER_CONTROLLER_BLOCK = Registry.register(
            BuiltInRegistries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("space-trek", "cooler_controller"),
            new CoolerControllerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 7 : 0))
    );

    public static void initialize() {}
}