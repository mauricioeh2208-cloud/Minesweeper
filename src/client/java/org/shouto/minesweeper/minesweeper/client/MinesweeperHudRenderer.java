package org.shouto.minesweeper.minesweeper.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;

public final class MinesweeperHudRenderer {
    private static final int MAP_RADIUS_PX = 38;
    private static final int CELL_SIZE_PX = 7;
    private static final int GRID_RADIUS = 5;

    private MinesweeperHudRenderer() {
    }

    public static void initialize() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        if (!MinesweeperClientState.isRoundActive()) {
            return;
        }

        BoardSnapshotPayload snapshot = MinesweeperClientState.getLastSnapshot();
        if (snapshot == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }

        int centerX = 16 + MAP_RADIUS_PX;
        int centerY = 16 + MAP_RADIUS_PX;
        drawCircleBackground(graphics, centerX, centerY, MAP_RADIUS_PX, 0xD9CDBA96);
        drawCells(graphics, snapshot, centerX, centerY, MAP_RADIUS_PX);
        drawCircleOutline(graphics, centerX, centerY, MAP_RADIUS_PX, 0xFF7F725A);
    }

    private static void drawCells(
            GuiGraphics graphics,
            BoardSnapshotPayload snapshot,
            int centerX,
            int centerY,
            int circleRadius
    ) {
        int focusX = snapshot.playerLocalX() >= 0 ? snapshot.playerLocalX() : snapshot.width() / 2;
        int focusZ = snapshot.playerLocalZ() >= 0 ? snapshot.playerLocalZ() : snapshot.height() / 2;

        Font font = Minecraft.getInstance().font;
        for (int dz = -GRID_RADIUS; dz <= GRID_RADIUS; dz++) {
            for (int dx = -GRID_RADIUS; dx <= GRID_RADIUS; dx++) {
                int screenX = centerX + (dx * CELL_SIZE_PX) - (CELL_SIZE_PX / 2);
                int screenY = centerY + (dz * CELL_SIZE_PX) - (CELL_SIZE_PX / 2);

                int localX = focusX + dx;
                int localZ = focusZ + dz;
                byte state = snapshot.cellAt(localX, localZ);

                int tileCenterX = screenX + (CELL_SIZE_PX / 2);
                int tileCenterY = screenY + (CELL_SIZE_PX / 2);
                int circleDx = tileCenterX - centerX;
                int circleDy = tileCenterY - centerY;
                if ((circleDx * circleDx) + (circleDy * circleDy) > circleRadius * circleRadius) {
                    continue;
                }

                graphics.fill(screenX, screenY, screenX + CELL_SIZE_PX, screenY + CELL_SIZE_PX, colorForCell(state));
                drawSymbol(graphics, font, state, screenX, screenY);
            }
        }

        int marker = 0xFFCF4635;
        graphics.fill(centerX - 1, centerY - 3, centerX + 1, centerY + 3, marker);
        graphics.fill(centerX - 3, centerY - 1, centerX + 3, centerY + 1, marker);
    }

    private static void drawSymbol(GuiGraphics graphics, Font font, byte state, int screenX, int screenY) {
        String symbol = switch (state) {
            case BoardSnapshotPayload.CELL_NUMBER_1 -> "1";
            case BoardSnapshotPayload.CELL_NUMBER_2 -> "2";
            case BoardSnapshotPayload.CELL_NUMBER_3 -> "3";
            case BoardSnapshotPayload.CELL_NUMBER_4 -> "4";
            case BoardSnapshotPayload.CELL_NUMBER_5 -> "5";
            case BoardSnapshotPayload.CELL_FLAG -> "F";
            default -> "";
        };
        if (symbol.isEmpty()) {
            return;
        }

        int color = numberColor(state);
        graphics.drawString(font, symbol, screenX + 1, screenY, color, false);
    }

    private static int numberColor(byte state) {
        return switch (state) {
            case BoardSnapshotPayload.CELL_NUMBER_1 -> 0xFF2D60DB;
            case BoardSnapshotPayload.CELL_NUMBER_2 -> 0xFF1F9F2E;
            case BoardSnapshotPayload.CELL_NUMBER_3 -> 0xFFC13125;
            case BoardSnapshotPayload.CELL_NUMBER_4 -> 0xFF6B2DB5;
            case BoardSnapshotPayload.CELL_NUMBER_5 -> 0xFFA0481A;
            case BoardSnapshotPayload.CELL_FLAG -> 0xFFD93D3D;
            default -> 0xFFFFFFFF;
        };
    }

    private static int colorForCell(byte state) {
        return switch (state) {
            case BoardSnapshotPayload.CELL_HIDDEN -> 0xFFB8B0A2;
            case BoardSnapshotPayload.CELL_NUMBER_0 -> 0xFFCEC6B8;
            case BoardSnapshotPayload.CELL_NUMBER_1,
                    BoardSnapshotPayload.CELL_NUMBER_2,
                    BoardSnapshotPayload.CELL_NUMBER_3,
                    BoardSnapshotPayload.CELL_NUMBER_4,
                    BoardSnapshotPayload.CELL_NUMBER_5 -> 0xFFD9D2C3;
            case BoardSnapshotPayload.CELL_FLAG -> 0xFFDBD2C0;
            case BoardSnapshotPayload.CELL_DISARMED_MINE -> 0xFF8C9A81;
            case BoardSnapshotPayload.CELL_REVEALED_MINE -> 0xFF36322C;
            default -> 0xFFB8B0A2;
        };
    }

    private static void drawCircleBackground(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int width = (int) Math.sqrt((radius * radius) - (y * y));
            graphics.fill(centerX - width, centerY + y, centerX + width + 1, centerY + y + 1, color);
        }
    }

    private static void drawCircleOutline(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                int dist = (x * x) + (y * y);
                if (dist <= outer && dist >= inner) {
                    graphics.fill(centerX + x, centerY + y, centerX + x + 1, centerY + y + 1, color);
                }
            }
        }
    }
}
