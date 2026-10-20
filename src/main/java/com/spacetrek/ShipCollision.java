package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ShipCollision {

    private static final double SEARCH_MARGIN = 48.0;
    private static final double STEP_HEIGHT = 0.6;
    private static volatile long lastActiveMillis = 0L;

    private ShipCollision() {
    }

    public static void markActive() {
        lastActiveMillis = System.currentTimeMillis();
    }

    public static boolean anyActive() {
        return System.currentTimeMillis() - lastActiveMillis < 3000L;
    }

    public static boolean isNearShip(Entity entity) {
        if (!anyActive()) return false;
        AABB area = entity.getBoundingBox().inflate(3.0);
        for (RocketEntity ship : entity.level().getEntitiesOfClass(RocketEntity.class, area.inflate(SEARCH_MARGIN))) {
            for (AABB box : ship.getCollisionBoxes()) {
                if (box.intersects(area)) return true;
            }
        }
        return false;
    }

    @Nullable
    public static Vec3 collide(Entity entity, Vec3 requested, Vec3 vanilla) {
        if (!anyActive()) return null;
        if (entity instanceof RocketEntity || entity.noPhysics || entity.getVehicle() instanceof RocketEntity) return null;

        AABB box = entity.getBoundingBox();
        AABB broad = box.expandTowards(vanilla).inflate(1.0 + STEP_HEIGHT);
        Vec3 current = vanilla;
        boolean changed = false;
        for (RocketEntity ship : entity.level().getEntitiesOfClass(RocketEntity.class, broad.inflate(SEARCH_MARGIN))) {
            if (entity.getRootVehicle() == ship) continue;
            boolean near = false;
            for (AABB shipBox : ship.getCollisionBoxes()) {
                if (shipBox.intersects(broad)) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;

            Quaternionf q = ship.getCollisionOrientation();
            Quaternionf inverse = new Quaternionf(q).conjugate();
            double pivot = ship.getPivot();
            Vec3 shipPos = ship.position();

            AABB local = toLocal(box, shipPos, inverse, pivot);
            Vec3 move = rotate(inverse, current);
            AABB search = local.expandTowards(move).inflate(0.5 + STEP_HEIGHT);
            List<VoxelShape> shapes = new ArrayList<>();
            for (AABB localBox : ship.getLocalBoxes()) {
                if (localBox.intersects(search)) shapes.add(Shapes.create(localBox));
            }
            if (shapes.isEmpty()) continue;

            Vec3 clipped = resolve(local, move, shapes, entity.onGround() || (requested.y < 0.0 && current.y != requested.y));
            if (clipped.equals(move)) continue;
            current = rotate(q, clipped);
            changed = true;
        }
        return changed ? current : null;
    }

    private static Vec3 resolve(AABB box, Vec3 movement, List<VoxelShape> shapes, boolean canStep) {
        Vec3 clipped = clip(box, movement, shapes);
        boolean hitHorizontal = clipped.x != movement.x || clipped.z != movement.z;
        if (hitHorizontal && canStep) {
            double up = Shapes.collide(Direction.Axis.Y, box, shapes, STEP_HEIGHT);
            if (up > 1.0E-4) {
                AABB lifted = box.move(0.0, up, 0.0);
                Vec3 stepped = clip(lifted, new Vec3(movement.x, 0.0, movement.z), shapes);
                double gained = stepped.x * stepped.x + stepped.z * stepped.z;
                double normal = clipped.x * clipped.x + clipped.z * clipped.z;
                if (gained > normal + 1.0E-7) {
                    AABB settled = lifted.move(stepped.x, 0.0, stepped.z);
                    double down = Shapes.collide(Direction.Axis.Y, settled, shapes, -up);
                    clipped = new Vec3(stepped.x, up + down, stepped.z);
                }
            }
        }
        return clipped;
    }

    private static Vec3 rotate(Quaternionf q, Vec3 v) {
        Vector3f t = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        q.transform(t);
        return new Vec3(t.x, t.y, t.z);
    }

    private static AABB toLocal(AABB world, Vec3 shipPos, Quaternionf inverse, double pivot) {
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
            Vec3 local = rotate(inverse, corner.subtract(shipPos).subtract(0.0, pivot, 0.0)).add(0.0, pivot, 0.0);
            loX = Math.min(loX, local.x);
            loY = Math.min(loY, local.y);
            loZ = Math.min(loZ, local.z);
            hiX = Math.max(hiX, local.x);
            hiY = Math.max(hiY, local.y);
            hiZ = Math.max(hiZ, local.z);
        }
        return new AABB(loX, loY, loZ, hiX, hiY, hiZ);
    }

    private static Vec3 clip(AABB start, Vec3 movement, List<VoxelShape> shapes) {
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        AABB current = start;

        if (y != 0.0) {
            y = Shapes.collide(Direction.Axis.Y, current, shapes, y);
            if (y != 0.0) current = current.move(0.0, y, 0.0);
        }
        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0) {
            z = Shapes.collide(Direction.Axis.Z, current, shapes, z);
            if (z != 0.0) current = current.move(0.0, 0.0, z);
        }
        if (x != 0.0) {
            x = Shapes.collide(Direction.Axis.X, current, shapes, x);
            if (x != 0.0) current = current.move(x, 0.0, 0.0);
        }
        if (!zFirst && z != 0.0) {
            z = Shapes.collide(Direction.Axis.Z, current, shapes, z);
        }
        return new Vec3(x, y, z);
    }
}
