package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CoolerControllerBlockEntity extends BlockEntity implements PowerAcceptor, FluidSink {

    public static final int MAX_FLUID = 8000;
    public static final int MAX_ENERGY = 12000;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int PROCESS_TICKS = 200;
    public static final int POWER_PER_TICK = 25;
    public static final int BUCKETS_BEFORE_COOLDOWN = 3;
    public static final int COOLDOWN_TICKS = 700;

    private int hot = 0;
    private int cooled = 0;
    private int energy = 0;
    private int progress = 0;
    private int batchCount = 0;
    private int cooldown = 0;
    private boolean formed = false;
    private final List<BlockPos> ports = new ArrayList<>();

    public CoolerControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOLER_CONTROLLER, pos, state);
    }

    public boolean isFormed() { return formed; }
    public int getHot() { return hot; }
    public int getCooled() { return cooled; }
    public int getEnergy() { return energy; }
    public int getProgress() { return progress; }
    public int getCooldown() { return cooldown; }

    public float getProgressFraction(float partialTick) {
        if (progress <= 0) return 0f;
        float smooth = progress + (getBlockState().getValue(CoolerControllerBlock.LIT) ? partialTick : 0f);
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

    @Override
    public boolean acceptBucket(FuelTankBlockEntity.FluidType type) {
        if (!formed || type != FuelTankBlockEntity.FluidType.CRUDE_OIL) return false;
        if (hot + BUCKET_AMOUNT > MAX_FLUID) return false;
        hot += BUCKET_AMOUNT;
        sync();
        return true;
    }

    public ItemInteractionResult useBucket(ItemStack stack, Level level, Player player, InteractionHand hand) {
        boolean pouring = stack.is(ModItems.CRUDE_OIL_BUCKET);
        boolean taking = stack.is(Items.BUCKET);
        if (!pouring && !taking) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.sidedSuccess(true);

        revalidate();
        if (!formed) {
            player.displayClientMessage(Component.translatable("message.space-trek.cooler_incomplete"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }

        if (pouring) {
            if (!acceptBucket(FuelTankBlockEntity.FluidType.CRUDE_OIL)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
            level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        } else {
            if (cooled < BUCKET_AMOUNT) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            cooled -= BUCKET_AMOUNT;
            sync();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ModItems.COOLED_CRUDE_BUCKET)));
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(false);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private static BlockPos at(BlockPos origin, Direction right, Direction back, int r, int u, int d) {
        return origin.relative(right, r).above(u).relative(back, d);
    }

    private void revalidate() {
        if (level == null) return;
        boolean wasFormed = formed;
        formed = validate(level, worldPosition, getBlockState());
        if (!formed) {
            ports.clear();
        }
        if (wasFormed != formed) sync();
    }

    private boolean validate(Level level, BlockPos origin, BlockState state) {
        Direction back = state.getValue(CoolerControllerBlock.FACING).getOpposite();
        Direction right = state.getValue(CoolerControllerBlock.FACING).getClockWise();
        List<BlockPos> foundPorts = new ArrayList<>();

        for (int r = -1; r <= 1; r++) {
            for (int u = 0; u <= 1; u++) {
                for (int d = 0; d <= 1; d++) {
                    if (r == 0 && u == 0 && d == 0) continue;
                    BlockPos p = at(origin, right, back, r, u, d);
                    if (!level.hasChunkAt(p)) return false;
                    Block block = level.getBlockState(p).getBlock();
                    boolean portSlot = d == 1 && u == 0 && r != 0;
                    if (portSlot) {
                        if (block != ModBlocks.COOLER_PORT_BLOCK) return false;
                        foundPorts.add(p);
                    } else if (block != ModBlocks.COOLER_CASING_BLOCK) {
                        return false;
                    }
                }
            }
        }

        ports.clear();
        ports.addAll(foundPorts);
        for (BlockPos p : foundPorts) {
            if (level.getBlockEntity(p) instanceof CoolerPortBlockEntity port) {
                port.setController(origin);
            }
        }
        return true;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 == 0 || (!formed && level.getGameTime() % 5 == 0)) {
            revalidate();
        }

        boolean working = false;
        boolean needsSync = false;

        if (cooldown > 0) {
            cooldown--;
            if (cooldown == 0 || cooldown % 20 == 0) needsSync = true;
        }

        if (formed) {
            if (progress > 0) {
                if (energy >= POWER_PER_TICK) {
                    energy -= POWER_PER_TICK;
                    progress++;
                    working = true;
                    if (progress >= PROCESS_TICKS) {
                        progress = 0;
                        cooled = Math.min(MAX_FLUID, cooled + BUCKET_AMOUNT);
                        batchCount++;
                        if (batchCount >= BUCKETS_BEFORE_COOLDOWN) {
                            batchCount = 0;
                            cooldown = COOLDOWN_TICKS;
                        }
                        needsSync = true;
                    } else if (progress % 20 == 0) {
                        needsSync = true;
                    }
                }
            } else if (cooldown <= 0 && hot >= BUCKET_AMOUNT && cooled + BUCKET_AMOUNT <= MAX_FLUID && energy >= POWER_PER_TICK) {
                hot -= BUCKET_AMOUNT;
                energy -= POWER_PER_TICK;
                progress = 1;
                working = true;
                needsSync = true;
            }

            if (cooled >= BUCKET_AMOUNT && level.getGameTime() % 20 == 0) {
                boolean pushed = FluidNetwork.pushBucket(level, pos, FuelTankBlockEntity.FluidType.COOLED_CRUDE);
                for (int i = 0; i < ports.size() && !pushed; i++) {
                    pushed = FluidNetwork.pushBucket(level, ports.get(i), FuelTankBlockEntity.FluidType.COOLED_CRUDE);
                }
                if (pushed) {
                    cooled -= BUCKET_AMOUNT;
                    needsSync = true;
                }
            }
        } else if (progress > 0) {
            progress = 0;
            needsSync = true;
        }

        if (state.getValue(CoolerControllerBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(CoolerControllerBlock.LIT, working), 3);
        }

        if (needsSync) {
            sync();
        } else if (working) {
            setChanged();
        }
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (cooldown > 0) cooldown--;
        if (!formed || !state.getValue(CoolerControllerBlock.LIT)) return;

        if (progress > 0 && progress < PROCESS_TICKS) progress++;

        Direction facing = state.getValue(CoolerControllerBlock.FACING);
        Direction back = facing.getOpposite();
        Direction right = facing.getClockWise();
        RandomSource random = level.random;

        if (random.nextInt(2) == 0) {
            double r = (random.nextDouble() - 0.5) * 2.6;
            double d = random.nextDouble();
            double x = pos.getX() + 0.5 + right.getStepX() * r + back.getStepX() * d;
            double z = pos.getZ() + 0.5 + right.getStepZ() * r + back.getStepZ() * d;
            level.addParticle(ParticleTypes.CLOUD, x, pos.getY() + 2.05, z,
                    0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
        }

        if (random.nextInt(8) == 0) {
            double r = (random.nextDouble() - 0.5) * 1.8;
            double x = pos.getX() + 0.5 + right.getStepX() * r + back.getStepX() * 0.5;
            double z = pos.getZ() + 0.5 + right.getStepZ() * r + back.getStepZ() * 0.5;
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, pos.getY() + 2.1, z,
                    0.0, 0.06, 0.0);
        }

        if (random.nextInt(3) == 0) {
            double sideways = (random.nextDouble() - 0.5) * 0.5;
            double x = pos.getX() + 0.5 + facing.getStepX() * 0.56 + right.getStepX() * sideways;
            double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.56 + right.getStepZ() * sideways;
            level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.25 + random.nextDouble() * 0.25, z,
                    facing.getStepX() * 0.02, 0.02, facing.getStepZ() * 0.02);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Hot", hot);
        tag.putInt("Cooled", cooled);
        tag.putInt("Energy", energy);
        tag.putInt("Progress", progress);
        tag.putInt("Batch", batchCount);
        tag.putInt("Cooldown", cooldown);
        tag.putBoolean("Formed", formed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hot = tag.getInt("Hot");
        cooled = tag.getInt("Cooled");
        energy = tag.getInt("Energy");
        progress = tag.getInt("Progress");
        batchCount = tag.getInt("Batch");
        cooldown = tag.getInt("Cooldown");
        formed = tag.getBoolean("Formed");
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
}
