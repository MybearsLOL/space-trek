package com.spacetrek;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ModItems {

    public static final BucketItem ROCKET_FUEL_BUCKET = register(
            "rocket_fuel_bucket",
            new BucketItem(ModFluids.ROCKET_FUEL_SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final BucketItem OXYGEN_BUCKET = register(
            "oxygen_bucket",
            new BucketItem(ModFluids.OXYGEN_SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final BucketItem KEROSENE_BUCKET = register(
            "kerosene_bucket",
            new BucketItem(ModFluids.KEROSENE_SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final BucketItem CRUDE_OIL_BUCKET = register(
            "crude_oil_bucket",
            new BucketItem(ModFluids.CRUDE_OIL_SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final BucketItem COOLED_CRUDE_BUCKET = register(
            "cooled_crude_bucket",
            new BucketItem(ModFluids.COOLED_CRUDE_SOURCE, new Item.Properties()
                    .craftRemainder(Items.BUCKET)
                    .stacksTo(1))
    );

    public static final Item CATALYST = register(
            "catalyst",
            new Item(new Item.Properties())
    );

    public static final BlockItem FUEL_TANK = register(
            "fuel_tank",
            new BlockItem(ModBlocks.FUEL_TANK_BLOCK, new Item.Properties())
    );

    public static final BlockItem INTAKE = register(
            "intake",
            new BlockItem(ModBlocks.INTAKE_BLOCK, new Item.Properties())
    );

    public static final BlockItem COAL_GENERATOR = register(
            "coal_generator",
            new BlockItem(ModBlocks.COAL_GENERATOR_BLOCK, new Item.Properties())
    );

    public static final BlockItem POWER_CABLE = register(
            "power_cable",
            new BlockItem(ModBlocks.POWER_CABLE_BLOCK, new Item.Properties())
    );

    public static final BlockItem KEROSENE_DISTILLER = register(
            "kerosene_distiller",
            new BlockItem(ModBlocks.KEROSENE_DISTILLER_BLOCK, new Item.Properties())
    );

    public static final BlockItem FUEL_SYNTHESIZER = register(
            "fuel_synthesizer",
            new BlockItem(ModBlocks.FUEL_SYNTHESIZER_BLOCK, new Item.Properties())
    );

    public static final BlockItem FLUID_PIPE = register(
            "fluid_pipe",
            new BlockItem(ModBlocks.FLUID_PIPE_BLOCK, new Item.Properties())
    );

    public static final BlockItem COOLER_CASING = register(
            "cooler_casing",
            new BlockItem(ModBlocks.COOLER_CASING_BLOCK, new Item.Properties())
    );

    public static final BlockItem COOLER_PORT = register(
            "cooler_port",
            new BlockItem(ModBlocks.COOLER_PORT_BLOCK, new Item.Properties())
    );

    public static final BlockItem COOLER_CONTROLLER = register(
            "cooler_controller",
            new BlockItem(ModBlocks.COOLER_CONTROLLER_BLOCK, new Item.Properties())
    );

    public static final BlockItem HOLO_PROJECTOR = register(
            "holo_projector",
            new BlockItem(ModBlocks.HOLO_PROJECTOR_BLOCK, new Item.Properties())
    );

    private static <T extends Item> T register(String name, T item) {
        return Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("space-trek", name), item);
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> {
                    entries.accept(ROCKET_FUEL_BUCKET);
                    entries.accept(OXYGEN_BUCKET);
                    entries.accept(KEROSENE_BUCKET);
                    entries.accept(CRUDE_OIL_BUCKET);
                    entries.accept(COOLED_CRUDE_BUCKET);
                    entries.accept(CATALYST);
                    entries.accept(FUEL_TANK);
                    entries.accept(INTAKE);
                    entries.accept(COAL_GENERATOR);
                    entries.accept(POWER_CABLE);
                    entries.accept(FLUID_PIPE);
                    entries.accept(COOLER_CONTROLLER);
                    entries.accept(COOLER_CASING);
                    entries.accept(COOLER_PORT);
                    entries.accept(HOLO_PROJECTOR);
                    entries.accept(KEROSENE_DISTILLER);
                    entries.accept(FUEL_SYNTHESIZER);
                });
    }
}