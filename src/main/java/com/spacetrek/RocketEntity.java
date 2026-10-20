package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
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
import net.minecraft.server.level.TicketType;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class RocketEntity extends Entity {

    public record Part(int x, int y, int z, BlockState state, @Nullable CompoundTag nbt) {
    }

    public record PartHit(Part part, double distance) {
    }

    public static final int INPUT_ROLL_LEFT = 1;
    public static final int INPUT_ROLL_RIGHT = 2;
    public static final int INPUT_THRUST = 4;
    public static final int INPUT_DETHRUST = 8;
    public static final int INPUT_JUMP = 16;

    private static final EntityDataAccessor<CompoundTag> DATA_PARTS =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final EntityDataAccessor<Quaternionf> DATA_ORIENTATION =
            SynchedEntityData.defineId(RocketEntity.class, EntityDataSerializers.QUATERNION);

    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.95;
    public static final double SPACE_DRAG = 0.999;
    public static final double MAX_ACCEL = 0.15;
    public static final double MAX_SPEED = 2.4;
    public static final double THRUST_PER_WEIGHT = 20.0;
    public static final double FUEL_PER_ENGINE_TICK = 1.5;
    private static final float MAX_TURN_DEGREES = 2.5f;
    private static final int MAX_EXHAUST_ENGINES = 8;
    private static final int INPUT_TIMEOUT = 10;

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
    private Vec3 forwardLocal = new Vec3(0.0, 0.0, 1.0);
    private float boxWidth = 1.0f;
    private float boxHeight = 1.0f;
    private double fuelMb = 0.0;

    private final List<AABB> localBoxes = new ArrayList<>();
    private List<AABB> worldBoxes = new ArrayList<>();

    private final Quaternionf orientation = new Quaternionf();
    private final Quaternionf targetOrientation = new Quaternionf();
    private final Quaternionf renderOrientation = new Quaternionf();
    private final Quaternionf renderOrientationOld = new Quaternionf();
    private boolean orientationReceived = false;

    private float angularPitch = 0.0f;
    private float angularYaw = 0.0f;
    private float angularRoll = 0.0f;
    private float throttle = 0.0f;
    private float brake = 0.0f;

    private int inputFlags = 0;
    private int inputTick = -1000;
    private boolean dismountRequested = false;

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

    public float getShipSize() {
        return Math.max(boxWidth, boxHeight);
    }

    public void setInput(int flags) {
        if ((flags & INPUT_JUMP) != 0 && (inputFlags & INPUT_JUMP) == 0) {
            dismountRequested = true;
        }
        inputFlags = flags;
        inputTick = tickCount;
    }

    public Quaternionf getRenderOrientation(float partialTick) {
        return new Quaternionf(renderOrientationOld).slerp(renderOrientation, partialTick);
    }

    private Quaternionf currentOrientation() {
        return level().isClientSide ? renderOrientation : orientation;
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
        localBoxes.clear();
        thrustLocal = Vec3.ZERO;
        forwardLocal = new Vec3(0.0, 0.0, 1.0);
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
        if (!seats.isEmpty()) {
            Direction facing = seats.get(0).state().getValue(RocketSeatBlock.FACING);
            forwardLocal = new Vec3(facing.getStepX(), 0.0, facing.getStepZ());
        }
        boxWidth = Math.max(maxX - minX + 1, maxZ - minZ + 1);
        boxHeight = maxY - minY + 1;
        for (Part part : parts) {
            VoxelShape shape;
            try {
                shape = part.state().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            } catch (Exception e) {
                shape = Shapes.block();
            }
            for (AABB box : shape.toAabbs()) {
                localBoxes.add(box.move(part.x() - getOriginX(), part.y() - getOriginY(), part.z() - getOriginZ()));
            }
        }
    }

    public List<AABB> getCollisionBoxes() {
        return worldBoxes;
    }

    public List<AABB> getLocalBoxes() {
        return localBoxes;
    }

    public double getPivot() {
        return boxHeight / 2.0;
    }

    public Quaternionf getCollisionOrientation() {
        return new Quaternionf(currentOrientation());
    }

    public Quaternionf getViewOrientation(float partialTick) {
        return level().isClientSide ? getRenderOrientation(partialTick) : new Quaternionf(orientation);
    }

    public float getSeatYaw(Entity passenger) {
        int index = getPassengers().indexOf(passenger);
        if (index < 0 || seats.isEmpty()) return 0.0f;
        Part seat = seats.get(Math.min(index, seats.size() - 1));
        return seat.state().getValue(RocketSeatBlock.FACING).toYRot();
    }

    private void updateWorldBoxes() {
        List<AABB> out = new ArrayList<>(localBoxes.size());
        for (AABB box : localBoxes) {
            double loX = Double.MAX_VALUE;
            double loY = Double.MAX_VALUE;
            double loZ = Double.MAX_VALUE;
            double hiX = -Double.MAX_VALUE;
            double hiY = -Double.MAX_VALUE;
            double hiZ = -Double.MAX_VALUE;
            for (int i = 0; i < 8; i++) {
                Vec3 corner = new Vec3((i & 1) == 0 ? box.minX : box.maxX,
                        (i & 2) == 0 ? box.minY : box.maxY,
                        (i & 4) == 0 ? box.minZ : box.maxZ);
                Vec3 world = toWorld(corner);
                loX = Math.min(loX, world.x);
                loY = Math.min(loY, world.y);
                loZ = Math.min(loZ, world.z);
                hiX = Math.max(hiX, world.x);
                hiY = Math.max(hiY, world.y);
                hiZ = Math.max(hiZ, world.z);
            }
            out.add(new AABB(getX() + loX, getY() + loY, getZ() + loZ, getX() + hiX, getY() + hiY, getZ() + hiZ));
        }
        worldBoxes = out;
    }

    private boolean canCarry(Entity entity) {
        if (entity == this || entity instanceof RocketEntity || entity.isPassenger() || !entity.onGround()) return false;
        if (level().isClientSide) {
            return entity instanceof Player player && player.isLocalPlayer();
        }
        return !(entity instanceof Player);
    }

    private AABB localBoxOf(AABB world, Vec3 shipPos, Quaternionf q) {
        Quaternionf inverse = new Quaternionf(q).conjugate();
        double pivot = boxHeight / 2.0;
        double loX = Double.MAX_VALUE;
        double loY = Double.MAX_VALUE;
        double loZ = Double.MAX_VALUE;
        double hiX = -Double.MAX_VALUE;
        double hiY = -Double.MAX_VALUE;
        double hiZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? world.minX : world.maxX,
                    (i & 2) == 0 ? world.minY : world.maxY,
                    (i & 4) == 0 ? world.minZ : world.maxZ);
            Vec3 local = rotateVector(inverse, corner.subtract(shipPos).subtract(0.0, pivot, 0.0)).add(0.0, pivot, 0.0);
            loX = Math.min(loX, local.x);
            loY = Math.min(loY, local.y);
            loZ = Math.min(loZ, local.z);
            hiX = Math.max(hiX, local.x);
            hiY = Math.max(hiY, local.y);
            hiZ = Math.max(hiZ, local.z);
        }
        return new AABB(loX, loY, loZ, hiX, hiY, hiZ);
    }

    private void carryEntities(Vec3 oldPos, Quaternionf oldQ, List<AABB> oldBoxes) {
        if (oldBoxes.isEmpty()) return;
        Quaternionf newQ = currentOrientation();
        Vec3 shift = position().subtract(oldPos);
        boolean rotated = !oldQ.equals(newQ, 1.0E-6f);
        if (shift.lengthSqr() < 1.0E-10 && !rotated) return;

        Quaternionf delta = new Quaternionf(newQ).mul(new Quaternionf(oldQ).conjugate());
        double pivot = boxHeight / 2.0;
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        delta.transform(forward);
        float yawDelta = (float) -Math.toDegrees(Math.atan2(forward.x, forward.z));

        AABB area = getBoundingBox().inflate(Math.max(boxWidth, boxHeight) + 2.0);
        for (Entity entity : level().getEntitiesOfClass(Entity.class, area, this::canCarry)) {
            AABB feet = localBoxOf(entity.getBoundingBox(), oldPos, oldQ);
            boolean standing = false;
            for (AABB box : localBoxes) {
                if (feet.maxX > box.minX + 1.0E-3 && feet.minX < box.maxX - 1.0E-3
                        && feet.maxZ > box.minZ + 1.0E-3 && feet.minZ < box.maxZ - 1.0E-3
                        && feet.minY > box.maxY - 0.25 && feet.minY < box.maxY + 0.2) {
                    standing = true;
                    break;
                }
            }
            if (!standing) continue;

            Vec3 relative = entity.position().subtract(oldPos).subtract(0.0, pivot, 0.0);
            Vec3 turned = rotateVector(delta, relative);
            Vec3 target = position().add(0.0, pivot, 0.0).add(turned);
            entity.setPos(target.x, target.y, target.z);
            if (rotated && entity instanceof Player) {
                entity.setYRot(entity.getYRot() + yawDelta);
                entity.yRotO += yawDelta;
            }
        }
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
        builder.define(DATA_ORIENTATION, new Quaternionf());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        if (DATA_PARTS.equals(key)) {
            readParts(entityData.get(DATA_PARTS));
            refreshDimensions();
        }
        if (DATA_ORIENTATION.equals(key)) {
            targetOrientation.set(entityData.get(DATA_ORIENTATION));
            if (!orientationReceived) {
                orientationReceived = true;
                renderOrientation.set(targetOrientation);
                renderOrientationOld.set(targetOrientation);
            }
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

    private Vec3 rotateVector(Quaternionf q, Vec3 v) {
        Vector3f t = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        q.transform(t);
        return new Vec3(t.x, t.y, t.z);
    }

    private Vec3 toWorld(Vec3 local) {
        double pivot = boxHeight / 2.0;
        Vec3 rotated = rotateVector(currentOrientation(), new Vec3(local.x, local.y - pivot, local.z));
        return new Vec3(rotated.x, rotated.y + pivot, rotated.z);
    }

    public boolean isAimedAt(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(player.entityInteractionRange() + 2.0));
        return raycastParts(eye, end, position(), currentOrientation()) != null;
    }

    @Nullable
    public PartHit raycastParts(Vec3 from, Vec3 to, Vec3 origin, Quaternionf rotation) {
        Quaternionf inverse = new Quaternionf(rotation).conjugate();
        double pivot = boxHeight / 2.0;
        Vec3 a = unrotate(inverse, from.subtract(origin), pivot);
        Vec3 b = unrotate(inverse, to.subtract(origin), pivot);

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

    private Vec3 unrotate(Quaternionf inverse, Vec3 v, double pivot) {
        Vec3 rotated = rotateVector(inverse, new Vec3(v.x, v.y - pivot, v.z));
        return new Vec3(rotated.x, rotated.y + pivot, rotated.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        readParts(tag);
        fuelMb = tag.getDouble("Fuel");
        if (tag.contains("OrientW")) {
            orientation.set(tag.getFloat("OrientX"), tag.getFloat("OrientY"), tag.getFloat("OrientZ"), tag.getFloat("OrientW"));
            orientation.normalize();
        }
        entityData.set(DATA_PARTS, writeParts(false));
        entityData.set(DATA_ORIENTATION, new Quaternionf(orientation));
        refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Parts", writeParts(true).getList("Parts", Tag.TAG_COMPOUND));
        tag.putDouble("Fuel", fuelMb);
        tag.putFloat("OrientX", orientation.x);
        tag.putFloat("OrientY", orientation.y);
        tag.putFloat("OrientZ", orientation.z);
        tag.putFloat("OrientW", orientation.w);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
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
        int index = getPassengers().indexOf(passenger);
        if (index < 0 || seats.isEmpty()) {
            return new Vec3(getX() + boxWidth / 2.0 + 1.0, getY(), getZ());
        }
        Part seat = seats.get(Math.min(index, seats.size() - 1));
        Set<Long> occupied = new HashSet<>();
        for (Part part : parts) {
            occupied.add(BlockPos.asLong(part.x(), part.y(), part.z()));
        }
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int[] offset : offsets) {
                int cx = seat.x() + offset[0];
                int cz = seat.z() + offset[1];
                int cy = seat.y();
                if (occupied.contains(BlockPos.asLong(cx, cy, cz)) || occupied.contains(BlockPos.asLong(cx, cy + 1, cz))) continue;
                if (pass == 0 && !occupied.contains(BlockPos.asLong(cx, cy - 1, cz))) continue;
                Vec3 local = new Vec3(cx + 0.5 - getOriginX(), cy - getOriginY() + 0.05, cz + 0.5 - getOriginZ());
                Vec3 world = toWorld(local);
                return new Vec3(getX() + world.x, getY() + world.y, getZ() + world.z);
            }
        }
        Vec3 local = new Vec3(seat.x() + 0.5 - getOriginX(), seat.y() + 1.0 - getOriginY(), seat.z() + 0.5 - getOriginZ());
        Vec3 world = toWorld(local);
        return new Vec3(getX() + world.x, getY() + world.y, getZ() + world.z);
    }

    @Nullable
    private Player pilot() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    public void tick() {
        super.tick();
        ShipCollision.markActive();
        Vec3 oldPos = position();
        Quaternionf oldQ = new Quaternionf(currentOrientation());
        List<AABB> oldBoxes = worldBoxes;
        if (level().isClientSide) {
            tickLerp();
            renderOrientationOld.set(renderOrientation);
            renderOrientation.slerp(targetOrientation, 0.5f);
            updateWorldBoxes();
            carryEntities(oldPos, oldQ, oldBoxes);
            return;
        }
        if (parts.isEmpty()) {
            discard();
            return;
        }

        boolean inSpace = level().dimension() == ModDimensions.SPACE_LEVEL;
        Player pilot = pilot();
        if (dismountRequested) {
            dismountRequested = false;
            if (pilot() instanceof ServerPlayer rider) {
                Vec3 spot = getDismountLocationForPassenger(rider);
                rider.stopRiding();
                rider.connection.teleport(spot.x, spot.y, spot.z, rider.getYRot(), rider.getXRot());
            }
        }
        int flags = tickCount - inputTick <= INPUT_TIMEOUT ? inputFlags : 0;
        Vec3 velocity = getDeltaMovement();
        boolean thrusting = false;

        float maxRate = (float) Math.toRadians(MAX_TURN_DEGREES) / (1.0f + parts.size() / 60.0f);
        float yawInput = pilot != null ? Math.signum(pilot.xxa) : 0.0f;
        float pitchInput = pilot != null ? Math.signum(pilot.zza) : 0.0f;
        float rollInput = ((flags & INPUT_ROLL_RIGHT) != 0 ? 1.0f : 0.0f) - ((flags & INPUT_ROLL_LEFT) != 0 ? 1.0f : 0.0f);
        if (pilot == null) {
            yawInput = 0.0f;
            pitchInput = 0.0f;
            rollInput = 0.0f;
        }

        angularYaw += (yawInput * maxRate - angularYaw) * 0.15f;
        angularPitch += (pitchInput * maxRate - angularPitch) * 0.15f;
        angularRoll += (rollInput * maxRate - angularRoll) * 0.15f;
        if (Math.abs(angularYaw) < 1.0E-4f) angularYaw = 0.0f;
        if (Math.abs(angularPitch) < 1.0E-4f) angularPitch = 0.0f;
        if (Math.abs(angularRoll) < 1.0E-4f) angularRoll = 0.0f;

        if (angularYaw != 0.0f || angularPitch != 0.0f || angularRoll != 0.0f) {
            float fx = (float) forwardLocal.x;
            float fz = (float) forwardLocal.z;
            orientation.rotateAxis(angularYaw, 0.0f, 1.0f, 0.0f);
            orientation.rotateAxis(angularPitch, fz, 0.0f, -fx);
            orientation.rotateAxis(angularRoll, fx, 0.0f, fz);
            orientation.normalize();
            entityData.set(DATA_ORIENTATION, new Quaternionf(orientation));
        }

        boolean hasFuel = fuelMb > 0.0 && !engineParts.isEmpty();
        float thrustTarget = pilot != null && (flags & INPUT_THRUST) != 0 && hasFuel && thrustLocal.lengthSqr() > 1.0E-6 ? 1.0f : 0.0f;
        float brakeTarget = pilot != null && (flags & INPUT_DETHRUST) != 0 && hasFuel ? 1.0f : 0.0f;
        throttle += (thrustTarget - throttle) * 0.2f;
        brake += (brakeTarget - brake) * 0.2f;
        if (throttle < 0.01f) throttle = 0.0f;
        if (brake < 0.01f) brake = 0.0f;

        double weight = Math.max(1, parts.size());
        double perEngine = GRAVITY * THRUST_PER_WEIGHT / weight;

        if (throttle > 0.0f && fuelMb > 0.0) {
            Vec3 accel = rotateVector(orientation, thrustLocal).scale(perEngine);
            if (accel.length() > MAX_ACCEL) {
                accel = accel.normalize().scale(MAX_ACCEL);
            }
            velocity = velocity.add(accel.scale(throttle));
            fuelMb = Math.max(0.0, fuelMb - FUEL_PER_ENGINE_TICK * engineParts.size() * throttle);
            thrusting = throttle > 0.05f;
        }

        if (brake > 0.0f && fuelMb > 0.0) {
            double speed = velocity.length();
            if (speed > 1.0E-4) {
                double brakeAccel = Math.min(MAX_ACCEL, perEngine * engineParts.size()) * brake;
                double dv = Math.min(speed, brakeAccel);
                velocity = velocity.subtract(velocity.normalize().scale(dv));
                fuelMb = Math.max(0.0, fuelMb - FUEL_PER_ENGINE_TICK * engineParts.size() * brake * 0.5);
            }
        }

        boolean resting = onGround() && velocity.y <= 0.0;
        if (resting) {
            velocity = new Vec3(velocity.x, 0.0, velocity.z);
        } else if (!inSpace) {
            velocity = velocity.add(0.0, -GRAVITY, 0.0);
        }
        if (velocity.length() > MAX_SPEED) {
            velocity = velocity.normalize().scale(MAX_SPEED);
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

        updateWorldBoxes();
        carryEntities(oldPos, oldQ, oldBoxes);

        if (level() instanceof ServerLevel serverLevel && tickCount % 4 == 0) {
            serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(blockPosition()), 3, blockPosition());
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
                Vec3 push = rotateVector(orientation, nozzle);
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

        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        orientation.transform(forward);
        double yawDegrees = -Math.toDegrees(Math.atan2(forward.x, forward.z));
        int quarter = Math.floorMod(Math.round((float) (yawDegrees / 90.0)), 4);
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
        for (int lift = 0; lift <= 4 && base == null; lift++) {
            for (int dx = -2; dx <= 2 && base == null; dx++) {
                for (int dz = -2; dz <= 2 && base == null; dz++) {
                    BlockPos candidate = baseGuess.offset(dx, lift, dz);
                    if (fits(level, candidate, quarter)) base = candidate;
                }
            }
        }
        if (base == null) return false;

        List<BlockPos> placed = new ArrayList<>();
        List<BlockState> previous = new ArrayList<>();
        boolean failed = false;
        for (Part part : parts) {
            BlockPos target = base.offset(rotateX(part.x(), part.z(), quarter), part.y(), rotateZ(part.x(), part.z(), quarter));
            previous.add(level.getBlockState(target));
            placed.add(target);
            if (!level.setBlock(target, part.state().rotate(rotation), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                failed = true;
                break;
            }
        }
        if (failed) {
            for (int i = 0; i < placed.size(); i++) {
                level.setBlock(placed.get(i), previous.get(i), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return false;
        }

        ejectPassengers();

        List<FuelTankBlockEntity> tanks = new ArrayList<>();
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
            if (!level.isInWorldBounds(target) || !level.hasChunkAt(target)) return false;
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
