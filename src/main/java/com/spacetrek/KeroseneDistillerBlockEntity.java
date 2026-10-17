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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class KeroseneDistillerBlockEntity extends BlockEntity
        implements ExtendedScreenHandlerFactory<BlockPos>, PowerAcceptor {

    public static final int DATA_COUNT = 5;
    public static final int MAX_FLUID = 8000;
    public static final int MAX_ENERGY = 20000;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int PROCESS_TICKS = 400;
    public static final int POWER_PER_TICK = 40;
    public static final int CRUDE_PER_BATCH = 2000;
    public static final int KEROSENE_PER_BATCH = 1000;

    private final SimpleContainer catalystSlot = new SimpleContainer(1);

    private int progress = 0;
    private int crude = 0;
    private int kerosene = 0;
    private int energy = 0;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> PROCESS_TICKS;
                case 2 -> crude;
                case 3 -> kerosene;
                case 4 -> energy;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 2 -> crude = value;
                case 3 -> kerosene = value;
                case 4 -> energy = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public KeroseneDistillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KEROSENE_DISTILLER, pos, state);
        catalystSlot.addListener(container -> setChanged());
    }

    public static boolean isCatalyst(ItemStack stack) {
        return stack.is(ModItems.CATALYST);
    }

    public SimpleContainer getCatalystContainer() {
        return catalystSlot;
    }

    public int getProgress() {
        return progress;
    }

    public int getCrude() {
        return crude;
    }

    public int getKerosene() {
        return kerosene;
    }

    public float getProgressFraction(float partialTick) {
        if (progress <= 0) return 0f;
        float smooth = progress + (getBlockState().getValue(KeroseneDistillerBlock.LIT) ? partialTick : 0f);
        return Math.min(1f, smooth / PROCESS_TICKS);
    }

    @Override
    public int receivePower(int amount) {
        int accepted = Math.min(amount, MAX_ENERGY - energy);
        if (accepted <= 0) return 0;
        energy += accepted;
        setChanged();
        return accepted;
    }

    public boolean addCrude() {
        if (crude + BUCKET_AMOUNT > MAX_FLUID) return false;
        crude += BUCKET_AMOUNT;
        sync();
        return true;
    }

    public boolean hasBucketReady() {
        return kerosene >= BUCKET_AMOUNT;
    }

    public void extractBucket() {
        if (kerosene < BUCKET_AMOUNT) return;
        kerosene -= BUCKET_AMOUNT;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean working = false;
        boolean needsSync = false;

        if (progress > 0) {
            if (energy >= POWER_PER_TICK) {
                energy -= POWER_PER_TICK;
                progress++;
                working = true;
                if (progress >= PROCESS_TICKS) {
                    progress = 0;
                    kerosene = Math.min(MAX_FLUID, kerosene + KEROSENE_PER_BATCH);
                    needsSync = true;
                } else if (progress % 20 == 0) {
                    needsSync = true;
                }
            }
        } else if (crude >= CRUDE_PER_BATCH
                && kerosene + KEROSENE_PER_BATCH <= MAX_FLUID
                && energy >= POWER_PER_TICK
                && isCatalyst(catalystSlot.getItem(0))) {
            crude -= CRUDE_PER_BATCH;
            catalystSlot.removeItem(0, 1);
            energy -= POWER_PER_TICK;
            progress = 1;
            working = true;
            needsSync = true;
        }

        if (state.getValue(KeroseneDistillerBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(KeroseneDistillerBlock.LIT, working), 3);
        }

        if (needsSync) {
            sync();
        } else if (working) {
            setChanged();
        }
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(KeroseneDistillerBlock.LIT) && progress > 0 && progress < PROCESS_TICKS) {
            progress++;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Catalyst", catalystSlot.getItem(0).saveOptional(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Crude", crude);
        tag.putInt("Kerosene", kerosene);
        tag.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        catalystSlot.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("Catalyst")));
        progress = tag.getInt("Progress");
        crude = tag.getInt("Crude");
        kerosene = tag.getInt("Kerosene");
        energy = tag.getInt("Energy");
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
        return Component.translatable("block.space-trek.kerosene_distiller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new KeroseneDistillerMenu(containerId, inventory, worldPosition, catalystSlot, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
