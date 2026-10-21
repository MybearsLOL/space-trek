package com.spacetrek;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class Shipyard {

    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SpaceTrek.id("shipyard"));
    public static final int ORIGIN = 20_000_000;
    public static final int PLOT = 256;
    public static final int PER_ROW = 64;
    public static final int ANCHOR_Y = 96;
    public static final int REACH = 120;

    private static final Map<Integer, RocketEntity> SHIPS = new ConcurrentHashMap<>();

    private Shipyard() {
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static BlockPos anchor(int index) {
        return new BlockPos(ORIGIN + (index % PER_ROW) * PLOT + PLOT / 2, ANCHOR_Y, ORIGIN + (index / PER_ROW) * PLOT + PLOT / 2);
    }

    public static boolean contains(double x, double z) {
        return x >= ORIGIN - 1.0 && z >= ORIGIN - 1.0;
    }

    public static int indexAt(double x, double z) {
        int col = (int) Math.floor((x - ORIGIN) / PLOT);
        int row = (int) Math.floor((z - ORIGIN) / PLOT);
        if (col < 0 || col >= PER_ROW || row < 0) return -1;
        return row * PER_ROW + col;
    }

    public static int allocate(ServerLevel yard) {
        Data data = yard.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), "space_trek_shipyard");
        int index = data.next++;
        data.setDirty();
        return index;
    }

    public static void register(int index, RocketEntity ship) {
        SHIPS.put(index, ship);
    }

    @Nullable
    public static RocketEntity find(int index) {
        RocketEntity ship = SHIPS.get(index);
        if (ship == null || ship.isRemoved()) return null;
        return ship;
    }

    @Nullable
    public static Vec3 toWorld(double x, double y, double z) {
        int index = indexAt(x, z);
        if (index < 0) return null;
        RocketEntity ship = find(index);
        if (ship == null) return null;
        return ship.yardToWorld(x, y, z);
    }

    public static final class Data extends SavedData {
        int next = 0;

        public Data() {
        }

        public static Data load(CompoundTag tag, HolderLookup.Provider provider) {
            Data data = new Data();
            data.next = tag.getInt("Next");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
            tag.putInt("Next", next);
            return tag;
        }
    }
}
