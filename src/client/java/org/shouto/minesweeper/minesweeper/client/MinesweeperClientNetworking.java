package org.shouto.minesweeper.minesweeper.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.shouto.minesweeper.minesweeper.client.screen.DisarmMiniGameScreen;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.OpenDisarmPayload;
import org.shouto.minesweeper.minesweeper.network.payload.PlayerInteractionAnimPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RoundStatePayload;

public final class MinesweeperClientNetworking {
    private MinesweeperClientNetworking() {
    }

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(RoundStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleRoundState(context.client(), payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(BoardSnapshotPayload.TYPE, (payload, context) ->
                context.client().execute(() -> MinesweeperClientState.setLastSnapshot(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(OpenDisarmPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handleOpenDisarm(context.client(), payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(PlayerInteractionAnimPayload.TYPE, (payload, context) ->
                context.client().execute(() -> handlePlayerInteractionAnimation(context.client(), payload))
        );
    }

    private static void handleRoundState(Minecraft client, RoundStatePayload payload) {
        MinesweeperClientState.setRoundActive(payload.active());
        if (!payload.active() && client.screen instanceof DisarmMiniGameScreen) {
            client.setScreen(null);
        }
    }

    private static void handleOpenDisarm(Minecraft client, OpenDisarmPayload payload) {
        if (client.player == null) {
            return;
        }
        client.setScreen(new DisarmMiniGameScreen(payload));
    }

    private static void handlePlayerInteractionAnimation(Minecraft client, PlayerInteractionAnimPayload payload) {
        if (client.level == null) {
            return;
        }

        MinesweeperClientState.startPlayerInteractionAnimation(
                payload.entityId(),
                payload.animationKind(),
                payload.durationTicks(),
                client.level.getGameTime()
        );
    }
}
