package com.spacetrek;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class IntakeBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public static final int DATA_COUNT = 3;
    public static final int MAX_OXYGEN = 8000;
    public static final int BUCKET_AMOUNT = 1000;

    private static final int OXYGEN_PER_TICK = 5;
    private static final int COAL_BURN_TICKS = 1600;
    private static final int LAVA_BURN_TICKS = 20000;
    private static final int RESYNC_INTERVAL = 40;

    private static final float MAX_SPIN_DEGREES_PER_TICK = 16f;
    private static final float IDLE_SPEED = 0.15f;
    private static final float LOW_FUEL_TICKS = 300f;
    private static final float LOW_FUEL_MIN_SPEED = 0.12f;
    private static final float SPEED_UP_RATE = 0.08f;
    private static final float SLOW_DOWN_RATE = 0.05f;

    private final SimpleContainer fuelSlot = new SimpleContainer(1);

    private int burnTime = 0;
    private int burnTotal = 0;
    private int oxygen = 0;
    private boolean producing = false;

    private float fanSpeed = 0f;
    private float fanAngle = 0f;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnTotal;
                case 2 -> oxygen;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnTotal = value;
                case 2 -> oxygen = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public IntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTAKE, pos, state);
        fuelSlot.addListener(container -> onFuelChanged());
    }

    public static int getFuelTime(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return COAL_BURN_TICKS;
        if (stack.is(Items.LAVA_BUCKET)) return LAVA_BURN_TICKS;
        return 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuelTime(stack) > 0;
    }

    public SimpleContainer getFuelContainer() {
        return fuelSlot;
    }

    public int getOxygen() {
        return oxygen;
    }

    public boolean hasBucketReady() {
        return oxygen >= BUCKET_AMOUNT;
    }

    public void extractBucket() {
        if (oxygen < BUCKET_AMOUNT) return;
        oxygen -= BUCKET_AMOUNT;
        setChanged();
        syncToClients();
    }

    public float getFanAngle(float partialTick) {
        return fanAngle + fanSpeed * MAX_SPIN_DEGREES_PER_TICK * partialTick;
    }

    private void onFuelChanged() {
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean wasProducing = producing;
        boolean dirty = false;

        if (oxygen < MAX_OXYGEN) {
            if (burnTime <= 0) {
                ItemStack fuel = fuelSlot.getItem(0);
                int burn = getFuelTime(fuel);
                if (burn > 0) {
                    Item fuelItem = fuel.getItem();
                    burnTotal = burn;
                    burnTime = burn;
                    fuelSlot.removeItem(0, 1);
                    Item remainder = fuelItem.getCraftingRemainingItem();
                    if (remainder != null && fuelSlot.getItem(0).isEmpty()) {
                        fuelSlot.setItem(0, new ItemStack(remainder));
                    }
                    dirty = true;
                }
            }

            if (burnTime > 0) {
                burnTime--;
                oxygen = Math.min(MAX_OXYGEN, oxygen + OXYGEN_PER_TICK);
                producing = true;
            } else {
                producing = false;
            }
        } else {
            producing = false;
        }

        if (producing != wasProducing) dirty = true;
        if (producing && level.getGameTime() % RESYNC_INTERVAL == 0) dirty = true;

        if (dirty) {
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    public void clientTick() {
        if (producing && burnTime > 0 && oxygen < MAX_OXYGEN) {
            burnTime--;
            oxygen = Math.min(MAX_OXYGEN, oxygen + OXYGEN_PER_TICK);
        }

        float target = targetFanSpeed();
        float rate = target > fanSpeed ? SPEED_UP_RATE : SLOW_DOWN_RATE;
        fanSpeed += (target - fanSpeed) * rate;
        if (target == 0f && fanSpeed < 0.01f) fanSpeed = 0f;

        fanAngle = (fanAngle + fanSpeed * MAX_SPIN_DEGREES_PER_TICK) % 360f;
    }

    private float targetFanSpeed() {
        boolean hasFuel = !fuelSlot.getItem(0).isEmpty();

        if (producing && burnTime > 0) {
            if (!hasFuel && burnTime < LOW_FUEL_TICKS) {
                return Math.max(LOW_FUEL_MIN_SPEED, burnTime / LOW_FUEL_TICKS);
            }
            return 1f;
        }

        if (burnTime > 0 || hasFuel) return IDLE_SPEED;
        return 0f;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuelSlot.getItem(0).saveOptional(registries));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Oxygen", oxygen);
        tag.putBoolean("Producing", producing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelSlot.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("Fuel")));
        burnTime = tag.getInt("BurnTime");
        burnTotal = tag.getInt("BurnTotal");
        oxygen = tag.getInt("Oxygen");
        producing = tag.getBoolean("Producing");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.space-trek.intake");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new IntakeMenu(containerId, inventory, worldPosition, fuelSlot, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
