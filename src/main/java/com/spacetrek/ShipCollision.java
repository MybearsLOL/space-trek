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
        AABB search = box.expandTowards(vanilla).inflate(0.5);
        List<VoxelShape> shapes = new ArrayList<>();
        for (RocketEntity ship : entity.level().getEntitiesOfClass(RocketEntity.class, search.inflate(SEARCH_MARGIN))) {
            if (entity.getRootVehicle() == ship) continue;
            for (AABB shipBox : ship.getCollisionBoxes()) {
                if (shipBox.intersects(search.inflate(STEP_HEIGHT))) {
                    shapes.add(Shapes.create(shipBox));
                }
            }
        }
        if (shapes.isEmpty()) return null;

        Vec3 clipped = clip(box, vanilla, shapes);
        boolean hitHorizontal = clipped.x != vanilla.x || clipped.z != vanilla.z;
        if (hitHorizontal && (entity.onGround() || (requested.y < 0.0 && vanilla.y != requested.y))) {
            double up = Shapes.collide(Direction.Axis.Y, box, shapes, STEP_HEIGHT);
            if (up > 1.0E-4) {
                AABB lifted = box.move(0.0, up, 0.0);
                Vec3 stepped = clip(lifted, new Vec3(vanilla.x, 0.0, vanilla.z), shapes);
                double gained = stepped.x * stepped.x + stepped.z * stepped.z;
                double normal = clipped.x * clipped.x + clipped.z * clipped.z;
                if (gained > normal + 1.0E-7) {
                    AABB settled = lifted.move(stepped.x, 0.0, stepped.z);
                    double down = Shapes.collide(Direction.Axis.Y, settled, shapes, -up);
                    clipped = new Vec3(stepped.x, up + down, stepped.z);
                }
            }
        }

        if (clipped.equals(vanilla)) return null;
        return clipped;
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
