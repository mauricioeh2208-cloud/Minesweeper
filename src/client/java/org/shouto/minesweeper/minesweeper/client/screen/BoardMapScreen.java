package org.shouto.minesweeper.minesweeper.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;

public class BoardMapScreen extends Screen {
    private final BoardSnapshotPayload snapshot;

    public BoardMapScreen(BoardSnapshotPayload snapshot) {
        super(Component.literal("Mapa del tablero"));
        this.snapshot = snapshot;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // En 1.21+, renderBackground puede aplicar blur y provocar "Can only blur once per frame"
        // cuando otra capa de UI ya lo aplico.
        drawPaperBackground(graphics);
        drawBoard(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_M || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void drawPaperBackground(GuiGraphics graphics) {
        int bg = 0xF2DCCAA7;
        int grain = 0x2A897D63;
        graphics.fill(0, 0, this.width, this.height, bg);

        for (int y = 0; y < this.height; y += 6) {
            for (int x = (y % 12 == 0 ? 0 : 3); x < this.width; x += 9) {
                graphics.fill(x, y, x + 1, y + 1, grain);
            }
        }
    }

    private void drawBoard(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        int marginTop = 28;
        int marginLeft = 30;
        int marginBottom = 20;
        int marginRight = 12;

        int availableW = this.width - marginLeft - marginRight;
        int availableH = this.height - marginTop - marginBottom;
        int cellSize = Math.max(4, Math.min(availableW / Math.max(1, snapshot.width()), availableH / Math.max(1, snapshot.height())));

        int boardW = snapshot.width() * cellSize;
        int boardH = snapshot.height() * cellSize;
        int startX = marginLeft + ((availableW - boardW) / 2);
        int startY = marginTop + ((availableH - boardH) / 2);

        graphics.drawString(font, "Mapa de tablero #" + snapshot.boardId(), 10, 8, 0xFF5A4F3D, false);
        graphics.drawString(font, "M para cerrar", this.width - 74, 8, 0xFF6C604B, false);

        for (int x = 0; x < snapshot.width(); x++) {
            if ((x + 1) % 2 == 0 || x == 0) {
                graphics.drawString(font, Integer.toString(x + 1), startX + (x * cellSize), startY - 12, 0xFF9C907B, false);
            }
        }
        for (int z = 0; z < snapshot.height(); z++) {
            if ((z + 1) % 2 == 0 || z == 0) {
                graphics.drawString(font, Integer.toString(z + 1), startX - 18, startY + (z * cellSize), 0xFF9C907B, false);
            }
        }

        for (int z = 0; z < snapshot.height(); z++) {
            for (int x = 0; x < snapshot.width(); x++) {
                int px = startX + (x * cellSize);
                int py = startY + (z * cellSize);
                byte cell = snapshot.cellAt(x, z);
                graphics.fill(px, py, px + cellSize, py + cellSize, colorForCell(cell));

                if (cellSize >= 8) {
                    drawCellSymbol(graphics, font, cell, px, py, cellSize);
                }
            }
        }

        graphics.renderOutline(startX - 1, startY - 1, boardW + 2, boardH + 2, 0xFF7F725A);

        if (snapshot.playerLocalX() >= 0 && snapshot.playerLocalZ() >= 0) {
            int px = startX + (snapshot.playerLocalX() * cellSize);
            int py = startY + (snapshot.playerLocalZ() * cellSize);
            int marker = 0xFFCF4635;
            graphics.fill(px + 1, py + 1, px + cellSize - 1, py + cellSize - 1, 0x3FCF4635);
            graphics.renderOutline(px, py, cellSize, cellSize, marker);
        }
    }

    private static void drawCellSymbol(GuiGraphics graphics, Font font, byte cell, int x, int y, int size) {
        String symbol = switch (cell) {
            case BoardSnapshotPayload.CELL_NUMBER_1 -> "1";
            case BoardSnapshotPayload.CELL_NUMBER_2 -> "2";
            case BoardSnapshotPayload.CELL_NUMBER_3 -> "3";
            case BoardSnapshotPayload.CELL_NUMBER_4 -> "4";
            case BoardSnapshotPayload.CELL_NUMBER_5 -> "5";
            case BoardSnapshotPayload.CELL_FLAG -> "F";
            case BoardSnapshotPayload.CELL_REVEALED_MINE -> "X";
            case BoardSnapshotPayload.CELL_DISARMED_MINE -> "O";
            default -> "";
        };
        if (symbol.isEmpty()) {
            return;
        }

        int symbolX = x + Math.max(1, (size / 2) - 3);
        int symbolY = y + Math.max(0, (size / 2) - 4);
        graphics.drawString(font, symbol, symbolX, symbolY, numberColor(cell), false);
    }

    private static int colorForCell(byte cell) {
        return switch (cell) {
            case BoardSnapshotPayload.CELL_HIDDEN -> 0xFFB8B0A2;
            case BoardSnapshotPayload.CELL_NUMBER_0 -> 0xFFCEC6B8;
            case BoardSnapshotPayload.CELL_NUMBER_1,
                    BoardSnapshotPayload.CELL_NUMBER_2,
                    BoardSnapshotPayload.CELL_NUMBER_3,
                    BoardSnapshotPayload.CELL_NUMBER_4,
                    BoardSnapshotPayload.CELL_NUMBER_5 -> 0xFFD9D2C3;
            case BoardSnapshotPayload.CELL_FLAG -> 0xFFDBD2C0;
            case BoardSnapshotPayload.CELL_DISARMED_MINE -> 0xFF7D8D73;
            case BoardSnapshotPayload.CELL_REVEALED_MINE -> 0xFF3A342C;
            default -> 0xFFB8B0A2;
        };
    }

    private static int numberColor(byte cell) {
        return switch (cell) {
            case BoardSnapshotPayload.CELL_NUMBER_1 -> 0xFF2D60DB;
            case BoardSnapshotPayload.CELL_NUMBER_2 -> 0xFF1F9F2E;
            case BoardSnapshotPayload.CELL_NUMBER_3 -> 0xFFC13125;
            case BoardSnapshotPayload.CELL_NUMBER_4 -> 0xFF6B2DB5;
            case BoardSnapshotPayload.CELL_NUMBER_5 -> 0xFFA0481A;
            case BoardSnapshotPayload.CELL_FLAG -> 0xFFD93D3D;
            case BoardSnapshotPayload.CELL_REVEALED_MINE -> 0xFFE3D7C8;
            case BoardSnapshotPayload.CELL_DISARMED_MINE -> 0xFFE3D7C8;
            default -> 0xFFFFFFFF;
        };
    }
}
