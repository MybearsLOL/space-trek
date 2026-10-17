package com.spacetrek;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

public final class CoolerStructure {

    public record Cell(BlockPos pos, Block block, boolean controller) {
    }

    private CoolerStructure() {
    }

    public static List<Cell> cells(BlockPos origin, Direction controllerFacing) {
        Direction back = controllerFacing.getOpposite();
        Direction right = controllerFacing.getClockWise();
        List<Cell> cells = new ArrayList<>();

        for (int r = -1; r <= 1; r++) {
            for (int u = 0; u <= 1; u++) {
                for (int d = 0; d <= 1; d++) {
                    BlockPos pos = origin.relative(right, r).above(u).relative(back, d);
                    if (r == 0 && u == 0 && d == 0) {
                        cells.add(new Cell(pos, ModBlocks.COOLER_CONTROLLER_BLOCK, true));
                    } else if (d == 1 && u == 0 && r != 0) {
                        cells.add(new Cell(pos, ModBlocks.COOLER_PORT_BLOCK, false));
                    } else {
                        cells.add(new Cell(pos, ModBlocks.COOLER_CASING_BLOCK, false));
                    }
                }
            }
        }
        return cells;
    }
}
