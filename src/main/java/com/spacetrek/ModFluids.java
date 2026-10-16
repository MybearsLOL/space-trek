package com.spacetrek;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FlowingFluid;

public class ModFluids {

    public static final FlowingFluid ROCKET_FUEL_SOURCE =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "rocket_fuel"),
                    new RocketFuelFluid.Source());

    public static final FlowingFluid ROCKET_FUEL_FLOWING =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "rocket_fuel_flowing"),
                    new RocketFuelFluid.Flowing());

    public static final FlowingFluid OXYGEN_SOURCE =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "oxygen"),
                    new OxygenFluid.Source());

    public static final FlowingFluid OXYGEN_FLOWING =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "oxygen_flowing"),
                    new OxygenFluid.Flowing());

    public static final FlowingFluid KEROSENE_SOURCE =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "kerosene"),
                    new KeroseneFluid.Source());

    public static final FlowingFluid KEROSENE_FLOWING =
            Registry.register(BuiltInRegistries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("space-trek", "kerosene_flowing"),
                    new KeroseneFluid.Flowing());

    public static void initialize() {}
}