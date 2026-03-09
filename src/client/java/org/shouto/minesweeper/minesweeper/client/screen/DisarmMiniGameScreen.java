package org.shouto.minesweeper.minesweeper.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shouto.minesweeper.minesweeper.network.payload.DisarmResultPayload;
import org.shouto.minesweeper.minesweeper.network.payload.OpenDisarmPayload;

public class DisarmMiniGameScreen extends Screen {
    private static final int TIME_LIMIT_TICKS = 20 * 6;
    private static final float CURSOR_SPEED = 0.028F;
    private static final float SUCCESS_ZONE_WIDTH = 0.18F;

    private final OpenDisarmPayload payload;
    private float cursor;
    private float direction = 1.0F;
    private float successZoneStart;
    private int ticksAlive;
    private boolean resolved;

    public DisarmMiniGameScreen(OpenDisarmPayload payload) {
        super(Component.literal("Desactivador de minas"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        long seed = (payload.token() * 31L) ^ payload.cellPos().asLong();
        double normalized = ((seed & 0xFFFF) / 65535.0D);
        successZoneStart = (float) (0.08D + (normalized * 0.74D));
        cursor = 0.0F;
        ticksAlive = 0;
        resolved = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (resolved) {
            return;
        }

        ticksAlive++;
        cursor += CURSOR_SPEED * direction;
        if (cursor >= 1.0F) {
            cursor = 1.0F;
            direction = -1.0F;
        } else if (cursor <= 0.0F) {
            cursor = 0.0F;
            direction = 1.0F;
        }

        if (ticksAlive >= TIME_LIMIT_TICKS) {
            resolve(false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88231E17);

        int panelW = 240;
        int panelH = 128;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        graphics.fill(x, y, x + panelW, y + panelH, 0xEA2A2A24);
        graphics.renderOutline(x, y, panelW, panelH, 0xFFB89361);

        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, "Desactivar mina", x + 10, y + 10, 0xFFE7D9C4, false);
        graphics.drawString(font, "Pulsa ESPACIO o CLICK dentro de la zona verde.", x + 10, y + 24, 0xFFCCBEA6, false);

        int barX = x + 16;
        int barY = y + 62;
        int barW = panelW - 32;
        int barH = 18;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF3C3B33);
        graphics.renderOutline(barX, barY, barW, barH, 0xFFB89361);

        int zoneStartPx = barX + Math.round(barW * successZoneStart);
        int zoneEndPx = barX + Math.round(barW * Math.min(1.0F, successZoneStart + SUCCESS_ZONE_WIDTH));
        graphics.fill(zoneStartPx, barY + 1, zoneEndPx, barY + barH - 1, 0xFF2A843A);

        int cursorX = barX + Math.round(barW * cursor);
        graphics.fill(cursorX - 1, barY - 5, cursorX + 1, barY + barH + 5, 0xFFE6DFCF);

        int remaining = Math.max(0, (TIME_LIMIT_TICKS - ticksAlive) / 20);
        graphics.drawString(font, "Tiempo: " + remaining + "s", x + 10, y + 92, 0xFFD3C4AB, false);
        graphics.drawString(font, "ESC = fallar", x + panelW - 66, y + 92, 0xFFAD9C81, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (resolved) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            attemptDisarm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            resolve(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (resolved) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            attemptDisarm();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (!resolved) {
            resolve(false);
            return;
        }
        super.onClose();
    }

    private void attemptDisarm() {
        boolean success = cursor >= successZoneStart && cursor <= (successZoneStart + SUCCESS_ZONE_WIDTH);
        resolve(success);
    }

    private void resolve(boolean success) {
        if (resolved) {
            return;
        }
        resolved = true;

        ClientPlayNetworking.send(new DisarmResultPayload(
                payload.boardId(),
                payload.cellPos(),
                payload.token(),
                success
        ));

        if (Minecraft.getInstance().player != null) {
            String text = success ? "Desactivacion correcta." : "Fallaste la desactivacion.";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(text), true);
        }

        super.onClose();
    }
}
