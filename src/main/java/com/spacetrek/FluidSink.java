package com.spacetrek;

public interface FluidSink {
    boolean acceptBucket(FuelTankBlockEntity.FluidType type);
}
