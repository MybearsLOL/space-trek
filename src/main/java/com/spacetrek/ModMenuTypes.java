package com.spacetrek;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public class ModMenuTypes {

    public static final MenuType<IntakeMenu> INTAKE = Registry.register(
            BuiltInRegistries.MENU,
            SpaceTrek.id("intake"),
            new ExtendedScreenHandlerType<IntakeMenu, BlockPos>(
                    (syncId, inventory, pos) -> new IntakeMenu(syncId, inventory),
                    BlockPos.STREAM_CODEC)
    );

    public static void initialize() {}
}
