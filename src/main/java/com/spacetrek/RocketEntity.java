package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RocketEntity extends Entity {

    public record Part(int x, int y, int z, BlockState state, @Nullable CompoundTag nbt) {
    }

    private static final EntityDataAccessor<CompoundTag> DATA_PARTS =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.COMPOUND_TAG);

    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.95;
    public static final double MAX_THRUST = 0.12;
    public static final double THRUST_PER_WEIGHT = 20.0;
    public static final double FUEL_PER_ENGINE_TICK = 1.5;
    private static final int LAND_DELAY = 25;

    private List<Part> parts = new ArrayList<>();
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private int engines;
    private float boxWidth = 1.0f;
    private float boxHeight = 1.0f;
    private double fuelMb = 0.0;
    private int landedTicks = 0;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public List<Part> getParts() {
        return parts;
    }

    public double getOriginX() {
        return (minX + maxX + 1) / 2.0;
    }

    public double getOriginY() {
        return minY;
    }

    public double getOriginZ() {
        return (minZ + maxZ + 1) / 2.0;
    }

    public double getFuelMb() {
        return fuelMb;
    }

    public void setParts(List<Part> list, double fuel) {
        parts = new ArrayList<>(list);
        fuelMb = fuel;
        recompute();
        entityData.set(DATA_PARTS, writeParts(false));
        refreshDimensions();
    }

    private void recompute() {
        if (parts.isEmpty()) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 0;
            engines = 0;
            boxWidth = 1.0f;
            boxHeight = 1.0f;
            return;
        }
        minX = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
        maxZ = Integer.MIN_VALUE;
        engines = 0;
        for (Part part : parts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
            if (part.state().is(ModBlocks.ROCKET_ENGINE_BLOCK)) engines++;
        }
        boxWidth = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        boxHeight = maxY - minY + 1;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(boxWidth, boxHeight);
    }

    private CompoundTag writeParts(boolean includeNbt) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Part part : parts) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", part.x());
            entry.putInt("Y", part.y());
            entry.putInt("Z", part.z());
            entry.put("State", NbtUtils.writeBlockState(part.state()));
            if (includeNbt && part.nbt() != null) {
                entry.put("Nbt", part.nbt());
            }
            list.add(entry);
        }
        tag.put("Parts", list);
        return tag;
    }

    private void readParts(CompoundTag tag) {
        List<Part> out = new ArrayList<>();
        var blocks = level().registryAccess().lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("Parts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockState state = NbtUtils.readBlockState(blocks, entry.getCompound("State"));
            CompoundTag nbt = entry.contains("Nbt") ? entry.getCompound("Nbt") : null;
            out.add(new Part(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"), state, nbt));
        }
        parts = out;
        recompute();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PARTS, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PARTS.equals(key) && level().isClientSide) {
            readParts(entityData.get(DATA_PARTS));
            refreshDimensions();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readParts(tag);
        fuelMb = tag.getDouble("Fuel");
        entityData.set(DATA_PARTS, writeParts(false));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Parts", writeParts(true).getList("Parts", Tag.TAG_COMPOUND));
        tag.putDouble("Fuel", fuelMb);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            player.startRiding(this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        if (!hasPassenger(passenger)) return;
        double localX = 0.5 - getOriginX();
        double localZ = 0.5 - getOriginZ();
        double localY = -getOriginY();
        double angle = Math.toRadians(-getYRot());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double rx = localX * cos + localZ * sin;
        double rz = -localX * sin + localZ * cos;
        callback.accept(passenger, getX() + rx, getY() + localY, getZ() + rz);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(getX() + boxWidth / 2.0 + 1.0, getY(), getZ());
    }

    @Nullable
    private Player rider() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (parts.isEmpty()) {
            discard();
            return;
        }

        Player rider = rider();
        Vec3 velocity = getDeltaMovement();
        boolean thrusting = false;

        if (rider != null) {
            setYRot(Mth.rotLerp(0.12f, getYRot(), rider.getYRot()));
            setXRot(Mth.lerp(0.12f, getXRot(), Mth.clamp(rider.getXRot(), -90.0f, 90.0f)));

            if (rider.zza > 0.0f && fuelMb > 0.0 && engines > 0) {
                double weight = Math.max(1, parts.size());
                double accel = Math.min(MAX_THRUST, GRAVITY * engines * THRUST_PER_WEIGHT / weight);
                float pitch = getXRot() * Mth.DEG_TO_RAD;
                float yaw = getYRot() * Mth.DEG_TO_RAD;
                Vec3 direction = new Vec3(-Mth.cos(pitch) * Mth.sin(yaw), -Mth.sin(pitch), Mth.cos(pitch) * Mth.cos(yaw));
                velocity = velocity.add(direction.scale(accel));
                fuelMb = Math.max(0.0, fuelMb - FUEL_PER_ENGINE_TICK * engines);
                thrusting = true;
            } else if (rider.zza < 0.0f) {
                velocity = velocity.scale(0.9);
            }
        }

        velocity = velocity.add(0.0, -GRAVITY, 0.0);
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);

        Vec3 after = getDeltaMovement().scale(DRAG);
        if (onGround()) {
            after = new Vec3(after.x * 0.7, after.y, after.z * 0.7);
        }
        setDeltaMovement(after);

        if (getY() < level().getMinBuildHeight() + 1) {
            setPos(getX(), level().getMinBuildHeight() + 1, getZ());
            setDeltaMovement(after.x, 0.0, after.z);
        }

        if (rider instanceof ServerPlayer player && tickCount % 5 == 0) {
            Component status = Component.translatable("message.space-trek.rocket_status",
                    String.format(Locale.ROOT, "%.1f", fuelMb / 1000.0),
                    engines,
                    String.format(Locale.ROOT, "%.1f", after.length() * 20.0));
            player.displayClientMessage(status, true);
        }

        if (thrusting && tickCount % 3 == 0 && level() instanceof ServerLevel serverLevel) {
            float pitch = getXRot() * Mth.DEG_TO_RAD;
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double dx = Mth.cos(pitch) * Mth.sin(yaw);
            double dy = Mth.sin(pitch);
            double dz = -Mth.cos(pitch) * Mth.cos(yaw);
            double reach = boxHeight / 2.0 + 0.5;
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    getX() + dx * reach, getY() + boxHeight / 2.0 + dy * reach, getZ() + dz * reach,
                    4, 0.2, 0.2, 0.2, 0.02);
        }

        if (rider == null && onGround()) {
            landedTicks++;
            if (landedTicks >= LAND_DELAY) {
                if (!disassemble()) landedTicks = 0;
            }
        } else {
            landedTicks = 0;
        }
    }

    private boolean disassemble() {
        if (!(level() instanceof ServerLevel level)) return false;

        int quarter = Math.floorMod(Math.round(getYRot() / 90.0f), 4);
        Rotation rotation = switch (quarter) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };

        double cx = 0.5 - getOriginX();
        double cz = 0.5 - getOriginZ();
        for (int i = 0; i < quarter; i++) {
            double nx = -cz;
            double nz = cx;
            cx = nx;
            cz = nz;
        }
        BlockPos baseGuess = new BlockPos(Mth.floor(getX() + cx), Mth.floor(getY() - getOriginY() + 0.5), Mth.floor(getZ() + cz));

        BlockPos base = null;
        for (int lift = 0; lift <= 3 && base == null; lift++) {
            BlockPos candidate = baseGuess.above(lift);
            if (fits(level, candidate, quarter)) base = candidate;
        }
        if (base == null) return false;

        List<BlockPos> placed = new ArrayList<>();
        List<FuelTankBlockEntity> tanks = new ArrayList<>();

        for (Part part : parts) {
            BlockPos target = base.offset(rotateX(part.x(), part.z(), quarter), part.y(), rotateZ(part.x(), part.z(), quarter));
            level.setBlock(target, part.state().rotate(rotation), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            placed.add(target);
        }
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.nbt() == null) continue;
            BlockEntity be = level.getBlockEntity(placed.get(i));
            if (be != null) {
                be.loadWithComponents(part.nbt(), level.registryAccess());
            }
            if (be instanceof FuelTankBlockEntity tank) tanks.add(tank);
        }
        FuelTankBlockEntity.fillRocketFuel(tanks, (int) fuelMb);

        discard();
        return true;
    }

    private boolean fits(ServerLevel level, BlockPos base, int quarter) {
        for (Part part : parts) {
            BlockPos target = base.offset(rotateX(part.x(), part.z(), quarter), part.y(), rotateZ(part.x(), part.z(), quarter));
            BlockState existing = level.getBlockState(target);
            if (!existing.isAir() && !existing.canBeReplaced()) return false;
        }
        return true;
    }

    private static int rotateX(int x, int z, int quarter) {
        int cx = x;
        int cz = z;
        for (int i = 0; i < quarter; i++) {
            int nx = -cz;
            int nz = cx;
            cx = nx;
            cz = nz;
        }
        return cx;
    }

    private static int rotateZ(int x, int z, int quarter) {
        int cx = x;
        int cz = z;
        for (int i = 0; i < quarter; i++) {
            int nx = -cz;
            int nz = cx;
            cx = nx;
            cz = nz;
        }
        return cz;
    }
}
