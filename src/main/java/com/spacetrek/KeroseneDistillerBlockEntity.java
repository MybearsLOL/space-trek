package com.spacetrek;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class KeroseneDistillerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {

    public static final int DATA_COUNT = 3;
    public static final int MAX_KEROSENE = 8000;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int PROCESS_TICKS = 300;
    public static final int KEROSENE_PER_ITEM = 1000;

    private final SimpleContainer feedstock = new SimpleContainer(1);

    private int progress = 0;
    private int kerosene = 0;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> PROCESS_TICKS;
                case 2 -> kerosene;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 2 -> kerosene = value;
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
        feedstock.addListener(container -> setChanged());
    }

    public static boolean isFeedstock(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    public SimpleContainer getFeedstockContainer() {
        return feedstock;
    }

    public boolean hasBucketReady() {
        return kerosene >= BUCKET_AMOUNT;
    }

    public void extractBucket() {
        if (kerosene < BUCKET_AMOUNT) return;
        kerosene -= BUCKET_AMOUNT;
        setChanged();
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean dirty = false;

        if (progress > 0) {
            progress++;
            if (progress >= PROCESS_TICKS) {
                progress = 0;
                kerosene = Math.min(MAX_KEROSENE, kerosene + KEROSENE_PER_ITEM);
            }
            dirty = true;
        } else if (kerosene + KEROSENE_PER_ITEM <= MAX_KEROSENE) {
            ItemStack stack = feedstock.getItem(0);
            if (isFeedstock(stack)) {
                feedstock.removeItem(0, 1);
                progress = 1;
                dirty = true;
            }
        }

        boolean lit = progress > 0;
        if (state.getValue(KeroseneDistillerBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(KeroseneDistillerBlock.LIT, lit), 3);
        }

        if (dirty) setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Feedstock", feedstock.getItem(0).saveOptional(registries));
        tag.putInt("Progress", progress);
        tag.putInt("Kerosene", kerosene);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        feedstock.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("Feedstock")));
        progress = tag.getInt("Progress");
        kerosene = tag.getInt("Kerosene");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.space-trek.kerosene_distiller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new KeroseneDistillerMenu(containerId, inventory, worldPosition, feedstock, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
