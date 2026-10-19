package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RocketEntity extends Entity {

    public record Part(int x, int y, int z, BlockState state, @Nullable CompoundTag nbt) {
    }

    public record PartHit(Part part, double distance) {
    }

    private static final EntityDataAccessor<CompoundTag> DATA_PARTS =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final EntityDataAccessor<Float> DATA_YAW =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.FLOAT);

    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.95;
    public static final double SPACE_DRAG = 0.999;
    public static final double MAX_ACCEL = 0.15;
    public static final double THRUST_PER_WEIGHT = 20.0;
    public static final double FUEL_PER_ENGINE_TICK = 1.5;
    private static final float MAX_TURN = 3.0f;
    private static final int MAX_EXHAUST_ENGINES = 8;

    private List<Part> parts = new ArrayList<>();
    private final List<Part> seats = new ArrayList<>();
    private final List<Part> engineParts = new ArrayList<>();
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;
    private Vec3 thrustLocal = Vec3.ZERO;
    private float boxWidth = 1.0f;
    private float boxHeight = 1.0f;
    private double fuelMb = 0.0;
    private float yawVelocity = 0.0f;
    private float throttle = 0.0f;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
        super(type, level);
    }

    public float getThrottle() {
        return throttle;
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

    public void setParts(List<Part> list, double fuel) {
        parts = new ArrayList<>(list);
        fuelMb = fuel;
        recompute();
        entityData.set(DATA_PARTS, writeParts(false));
        refreshDimensions();
    }

    private void recompute() {
        seats.clear();
        engineParts.clear();
        thrustLocal = Vec3.ZERO;
        if (parts.isEmpty()) {
            minX = 0;
            minY = 0;
            minZ = 0;
            maxX = 0;
            maxY = 0;
            maxZ = 0;
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
        Vec3 thrust = Vec3.ZERO;
        for (Part part : parts) {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
            if (part.state().is(ModBlocks.ROCKET_SEAT_BLOCK)) {
                seats.add(part);
            }
            if (part.state().is(ModBlocks.ROCKET_ENGINE_BLOCK)) {
                engineParts.add(part);
                Direction facing = part.state().getValue(RocketEngineBlock.FACING);
                thrust = thrust.add(-facing.getStepX(), -facing.getStepY(), -facing.getStepZ());
            }
        }
        thrustLocal = thrust;
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
        builder.define(DATA_YAW, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_PARTS.equals(key) && level().isClientSide) {
            readParts(entityData.get(DATA_PARTS));
            refreshDimensions();
        }
        if (DATA_YAW.equals(key) && level().isClientSide) {
            setYRot(entityData.get(DATA_YAW));
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = 3;
    }

    private void tickLerp() {
        if (lerpSteps <= 0) return;
        double nx = getX() + (lerpX - getX()) / lerpSteps;
        double ny = getY() + (lerpY - getY()) / lerpSteps;
        double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
        lerpSteps--;
        setPos(nx, ny, nz);
    }

    public boolean isAimedAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.entityInteractionRange() + 2.0));
        return raycastParts(eye, end, position(), getYRot()) != null;
    }

    @Nullable
    public PartHit raycastParts(Vec3 from, Vec3 to, Vec3 origin, float yaw) {
        double angle = Math.toRadians(yaw);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        Vec3 a = unrotate(from.subtract(origin), sin, cos);
        Vec3 b = unrotate(to.subtract(origin), sin, cos);

        PartHit best = null;
        for (Part part : parts) {
            AABB box = new AABB(part.x() - getOriginX(), part.y() - getOriginY(), part.z() - getOriginZ(),
                    part.x() - getOriginX() + 1.0, part.y() - getOriginY() + 1.0, part.z() - getOriginZ() + 1.0);
            var hit = box.clip(a, b);
            if (hit.isPresent()) {
                double distance = hit.get().distanceTo(a);
                if (best == null || distance < best.distance()) {
                    best = new PartHit(part, distance);
                }
            }
        }
        return best;
    }

    private static Vec3 unrotate(Vec3 v, double sin, double cos) {
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
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
        return getPassengers().size() < seats.size();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;

        if (player.isSecondaryUseActive()) {
            boolean slow = getDeltaMovement().lengthSqr() < 0.02;
            if (!(slow || onGround()) || !disassemble()) {
                player.displayClientMessage(Component.translatable("message.space-trek.rocket_cannot_land"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (seats.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.space-trek.rocket_no_seat"), true);
            return InteractionResult.CONSUME;
        }
        player.startRiding(this);
        return InteractionResult.CONSUME;
    }

    private Vec3 toWorld(Vec3 local) {
        double angle = Math.toRadians(-getYRot());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new Vec3(local.x * cos + local.z * sin, local.y, -local.x * sin + local.z * cos);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        int index = getPassengers().indexOf(passenger);
        if (index < 0 || seats.isEmpty()) return;
        Part seat = seats.get(Math.min(index, seats.size() - 1));
        Vec3 local = new Vec3(seat.x() + 0.5 - getOriginX(), seat.y() - getOriginY() + 0.1, seat.z() + 0.5 - getOriginZ());
        Vec3 world = toWorld(local);
        callback.accept(passenger, getX() + world.x, getY() + world.y, getZ() + world.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(getX() + boxWidth / 2.0 + 1.0, getY(), getZ());
    }

    @Nullable
    private Player pilot() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickLerp();
            return;
        }
        if (parts.isEmpty()) {
            discard();
            return;
        }

        boolean inSpace = level().dimension() == ModDimensions.SPACE_LEVEL;
        Player pilot = pilot();
        Vec3 velocity = getDeltaMovement();
        boolean thrusting = false;

        float turnMax = MAX_TURN / (1.0f + parts.size() / 60.0f);
        float turnInput = pilot != null ? Math.signum(pilot.xxa) : 0.0f;
        yawVelocity = (yawVelocity - turnInput * turnMax * 0.18f) * 0.82f;
        yawVelocity = Mth.clamp(yawVelocity, -turnMax, turnMax);
        if (Math.abs(yawVelocity) < 0.001f) yawVelocity = 0.0f;
        setYRot(getYRot() + yawVelocity);
        entityData.set(DATA_YAW, getYRot());

        boolean wantsThrust = pilot != null && pilot.zza > 0.0f && fuelMb > 0.0
                && !engineParts.isEmpty() && thrustLocal.lengthSqr() > 1.0E-6;
        throttle += ((wantsThrust ? 1.0f : 0.0f) - throttle) * 0.2f;
        if (throttle < 0.01f) throttle = 0.0f;

        if (throttle > 0.0f && fuelMb > 0.0) {
            double weight = Math.max(1, parts.size());
            double perEngine = GRAVITY * THRUST_PER_WEIGHT / weight;
            Vec3 accel = toWorld(thrustLocal).scale(perEngine);
            if (accel.length() > MAX_ACCEL) {
                accel = accel.normalize().scale(MAX_ACCEL);
            }
            velocity = velocity.add(accel.scale(throttle));
            fuelMb = Math.max(0.0, fuelMb - FUEL_PER_ENGINE_TICK * engineParts.size() * throttle);
            thrusting = throttle > 0.05f;
        }

        if (pilot != null && pilot.zza < 0.0f) {
            velocity = velocity.scale(0.9);
        }

        boolean resting = onGround() && velocity.y <= 0.0;
        if (resting) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        } else if (!inSpace) {
            velocity = velocity.add(0.0, -GRAVITY, 0.0);
        }
        setDeltaMovement(velocity);
        move(MoverType.SELF, velocity);

        Vec3 after = getDeltaMovement().scale(inSpace ? SPACE_DRAG : DRAG);
        if (onGround()) {
            after = new Vec3(after.x * 0.7, Math.max(after.y, 0.0), after.z * 0.7);
        }
        if (after.lengthSqr() < 1.0E-6) {
            after = Vec3.ZERO;
        }
        setDeltaMovement(after);

        if (!inSpace && getY() < level().getMinBuildHeight() + 1) {
            setPos(getX(), level().getMinBuildHeight() + 1, getZ());
            setDeltaMovement(after.x, 0.0, after.z);
        }

        if (pilot instanceof ServerPlayer player && tickCount % 5 == 0) {
            Component status = Component.translatable("message.space-trek.rocket_status",
                    String.format(Locale.ROOT, "%.1f", fuelMb / 1000.0),
                    engineParts.size(),
                    String.format(Locale.ROOT, "%.1f", after.length() * 20.0));
            player.displayClientMessage(status, true);
        }

        if (thrusting && tickCount % 2 == 0 && level() instanceof ServerLevel serverLevel) {
            int shown = Math.min(engineParts.size(), MAX_EXHAUST_ENGINES);
            for (int i = 0; i < shown; i++) {
                Part engine = engineParts.get(i);
                Direction facing = engine.state().getValue(RocketEngineBlock.FACING);
                Vec3 nozzle = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                Vec3 local = new Vec3(engine.x() + 0.5 - getOriginX(), engine.y() - getOriginY() + 0.5, engine.z() + 0.5 - getOriginZ())
                        .add(nozzle.scale(0.8));
                Vec3 world = toWorld(local);
                Vec3 push = toWorld(nozzle);
                serverLevel.sendParticles(ParticleTypes.FLAME, getX() + world.x, getY() + world.y, getZ() + world.z,
                        0, push.x, push.y, push.z, 0.35);
            }
        }

        if (level() instanceof ServerLevel here) {
            tryChangeDimension(here);
        }
    }

    private boolean tryChangeDimension(ServerLevel here) {
        if (here.dimension() == ModDimensions.SPACE_LEVEL) {
            ModDimensions.PlanetEntry entry = ModDimensions.planetEntry(here, position());
            if (entry == null) return false;
            return transfer(entry.level(), entry.pos());
        }
        if (getY() < ModDimensions.SPACE_TRANSITION_END_Y) return false;
        ServerLevel space = ModDimensions.spaceLevel(here.getServer());
        if (space == null) return false;
        Vec3 target = ModDimensions.spaceArrival(here, position());
        if (target == null) return false;
        return transfer(space, target);
    }

    private boolean transfer(ServerLevel destination, Vec3 target) {
        DimensionTransition transition = new DimensionTransition(destination, target, getDeltaMovement(),
                getYRot(), getXRot(), DimensionTransition.DO_NOTHING);
        return changeDimension(transition) != null;
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

        ejectPassengers();

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
