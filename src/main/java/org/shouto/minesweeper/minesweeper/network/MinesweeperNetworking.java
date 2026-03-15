package org.shouto.minesweeper.minesweeper.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardSync;
import org.shouto.minesweeper.minesweeper.game.MinesweeperGameplay;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.InteractionHoldPayload;
import org.shouto.minesweeper.minesweeper.network.payload.InteractionProgressPayload;
import org.shouto.minesweeper.minesweeper.network.payload.PlayerInteractionAnimPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RequestBoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RoundStatePayload;

public final class MinesweeperNetworking {
    private MinesweeperNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(RoundStatePayload.TYPE, RoundStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardSnapshotPayload.TYPE, BoardSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerInteractionAnimPayload.TYPE, PlayerInteractionAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InteractionProgressPayload.TYPE, InteractionProgressPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBoardSnapshotPayload.TYPE, RequestBoardSnapshotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(InteractionHoldPayload.TYPE, InteractionHoldPayload.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(RequestBoardSnapshotPayload.TYPE, (payload, context) ->
                context.server().execute(() -> MinesweeperBoardSync.handleSnapshotRequest(context.player(), payload))
        );
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(InteractionHoldPayload.TYPE, (payload, context) ->
                context.server().execute(() -> MinesweeperGameplay.recordInteractionHold(context.player(), payload))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new RoundStatePayload(
                    MinesweeperRoundManager.isActive() && MinesweeperRoundManager.isParticipant(handler.player)
            ));
            MinesweeperBoardSync.sendBestSnapshot(handler.player);
        });
    }
}
