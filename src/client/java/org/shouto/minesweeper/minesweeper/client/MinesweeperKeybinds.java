package org.shouto.minesweeper.minesweeper.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.shouto.minesweeper.minesweeper.client.screen.BoardMapScreen;
import org.shouto.minesweeper.minesweeper.network.payload.InteractionHoldPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RequestBoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

public final class MinesweeperKeybinds {
    private static final int SNAPSHOT_REQUEST_INTERVAL_TICKS = 10;
    private static KeyMapping openMapKey;

    private MinesweeperKeybinds() {
    }

    public static void initialize() {
        openMapKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.minesweeper.open_board_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(MinesweeperKeybinds::onClientTick);
    }

    private static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }

        sendInteractionHold(client);

        if (MinesweeperClientState.isRoundActive() && (client.level.getGameTime() % SNAPSHOT_REQUEST_INTERVAL_TICKS == 0)) {
            ClientPlayNetworking.send(new RequestBoardSnapshotPayload(-1));
        }

        while (openMapKey.consumeClick()) {
            openBoardMap(client);
        }
    }

    private static void openBoardMap(Minecraft client) {
        ClientPlayNetworking.send(new RequestBoardSnapshotPayload(-1));

        if (!MinesweeperClientState.isRoundActive()) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("No hay ronda activa."), true);
            }
            return;
        }

        if (MinesweeperClientState.getLastSnapshot() == null) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.literal("Esperando datos del tablero..."), true);
            }
            return;
        }

        client.setScreen(new BoardMapScreen(MinesweeperClientState.getLastSnapshot()));
    }

    private static void sendInteractionHold(Minecraft client) {
        if (!MinesweeperClientState.isRoundActive() || client.screen != null || !(client.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }

        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        if (!attackDown && !useDown) {
            return;
        }

        InteractionHand hand = resolveActiveHand(client, attackDown, useDown);
        if (hand == null) {
            return;
        }

        ClientPlayNetworking.send(new InteractionHoldPayload(
                blockHit.getBlockPos().getX(),
                blockHit.getBlockPos().getY(),
                blockHit.getBlockPos().getZ(),
                hand == InteractionHand.OFF_HAND ? (byte) 1 : (byte) 0
        ));
    }

    private static InteractionHand resolveActiveHand(Minecraft client, boolean attackDown, boolean useDown) {
        ItemStack mainHand = client.player.getMainHandItem();
        ItemStack offHand = client.player.getOffhandItem();

        if (attackDown && mainHand.is(MinesweeperItems.INTERACCION)) {
            return InteractionHand.MAIN_HAND;
        }
        if (useDown && (mainHand.is(MinesweeperItems.INTERACCION) || mainHand.is(MinesweeperItems.DESACTIVADOR_MINA))) {
            return InteractionHand.MAIN_HAND;
        }
        if (useDown && (offHand.is(MinesweeperItems.INTERACCION) || offHand.is(MinesweeperItems.DESACTIVADOR_MINA))) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
