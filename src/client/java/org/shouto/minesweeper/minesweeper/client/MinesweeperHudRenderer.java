package org.shouto.minesweeper.minesweeper.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.shouto.minesweeper.minesweeper.client.MinesweeperClientState.ActiveInteractionProgress;
import org.shouto.minesweeper.minesweeper.client.MinesweeperClientState.InteractionProgressKind;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

public final class MinesweeperHudRenderer {
    private static final int MAP_SIZE_PX = 76;
    private static final int CELL_SIZE_PX = 7;
    private static final int GRID_RADIUS = 5;
    private static final int PROGRESS_PANEL_W = 138;
    private static final int PROGRESS_PANEL_H = 30;
    private static final int PROGRESS_BAR_W = 82;
    private static final int PROGRESS_BAR_H = 10;

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

        if (snapshot != null) {
            int startX = 16;
            int startY = 16;
            int centerX = startX + (MAP_SIZE_PX / 2);
            int centerY = startY + (MAP_SIZE_PX / 2);
            drawSquareBackground(graphics, startX, startY, MAP_SIZE_PX, 0xD9CDBA96);
            drawCells(graphics, snapshot, centerX, centerY);
            drawSquareOutline(graphics, startX, startY, MAP_SIZE_PX, 0xFF7F725A);
        }

        renderInteractionProgress(graphics, client);
    }

    private static void drawCells(
            GuiGraphics graphics,
            BoardSnapshotPayload snapshot,
            int centerX,
            int centerY
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

    private static void drawSquareBackground(GuiGraphics graphics, int startX, int startY, int size, int color) {
        graphics.fill(startX, startY, startX + size, startY + size, color);
    }

    private static void drawSquareOutline(GuiGraphics graphics, int startX, int startY, int size, int color) {
        graphics.fill(startX, startY, startX + size, startY + 1, color);
        graphics.fill(startX, startY + size - 1, startX + size, startY + size, color);
        graphics.fill(startX, startY, startX + 1, startY + size, color);
        graphics.fill(startX + size - 1, startY, startX + size, startY + size, color);
    }

    private static void renderInteractionProgress(GuiGraphics graphics, Minecraft client) {
        ActiveInteractionProgress progress = MinesweeperClientState.getInteractionProgress();
        if (progress == null) {
            return;
        }

        Font font = client.font;
        int x = (graphics.guiWidth() - PROGRESS_PANEL_W) / 2;
        int y = graphics.guiHeight() - 74;
        int barX = x + 28;
        int barY = y + 14;
        int fillW = Math.max(0, Math.min(PROGRESS_BAR_W, Math.round(PROGRESS_BAR_W * progress.progress())));

        graphics.fill(x, y, x + PROGRESS_PANEL_W, y + PROGRESS_PANEL_H, 0xCC221C16);
        graphics.renderOutline(x, y, PROGRESS_PANEL_W, PROGRESS_PANEL_H, 0xFFB89361);

        String title = progress.kind() == InteractionProgressKind.DISARM ? "DESACTIVANDO..." : "DESCUBRIENDO...";
        int titleColor = progress.kind() == InteractionProgressKind.DISARM ? 0xFFF6C56A : 0xFFFFA33A;
        graphics.drawCenteredString(font, title, x + (PROGRESS_PANEL_W / 2), y + 4, titleColor);

        graphics.fill(barX, barY, barX + PROGRESS_BAR_W, barY + PROGRESS_BAR_H, 0xFF3C3B33);
        graphics.fill(barX + 1, barY + 1, barX + fillW, barY + PROGRESS_BAR_H - 1, progressFillColor(progress.kind()));
        graphics.fill(barX + 1, barY + 1, barX + fillW, barY + 3, 0x55FFF7B3);
        graphics.renderOutline(barX, barY, PROGRESS_BAR_W, PROGRESS_BAR_H, 0xFFB89361);

        graphics.renderItem(leftIcon(progress.kind()), x + 6, y + 9);
        graphics.renderItem(MinesweeperItems.MINE_OPEN_BLOCK_ITEM.getDefaultInstance(), x + PROGRESS_PANEL_W - 22, y + 9);

        int percent = Math.round(progress.progress() * 100.0F);
        graphics.drawCenteredString(font, percent + "%", barX + (PROGRESS_BAR_W / 2), y + 15, 0xFFE7D9C4);
    }

    private static ItemStack leftIcon(InteractionProgressKind kind) {
        return switch (kind) {
            case DISARM -> MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance();
            case REVEAL -> MinesweeperItems.INTERACCION.getDefaultInstance();
        };
    }

    private static int progressFillColor(InteractionProgressKind kind) {
        return switch (kind) {
            case DISARM -> 0xFF41A65A;
            case REVEAL -> 0xFF94C93D;
        };
    }
}
