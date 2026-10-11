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

    public static final BlockItem FUEL_TANK = register(
            "fuel_tank",
            new BlockItem(ModBlocks.FUEL_TANK_BLOCK, new Item.Properties())
    );

    public static final BlockItem INTAKE = register(
            "intake",
            new BlockItem(ModBlocks.INTAKE_BLOCK, new Item.Properties())
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
                    entries.accept(FUEL_TANK);
                    entries.accept(INTAKE);
                });
    }
}