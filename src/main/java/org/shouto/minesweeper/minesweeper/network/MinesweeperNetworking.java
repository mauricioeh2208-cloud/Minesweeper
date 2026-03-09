package org.shouto.minesweeper.minesweeper.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardSync;
import org.shouto.minesweeper.minesweeper.game.MinesweeperGameplay;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.DisarmResultPayload;
import org.shouto.minesweeper.minesweeper.network.payload.OpenDisarmPayload;
import org.shouto.minesweeper.minesweeper.network.payload.PlayerInteractionAnimPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RequestBoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RoundStatePayload;

public final class MinesweeperNetworking {
    private MinesweeperNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(OpenDisarmPayload.TYPE, OpenDisarmPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DisarmResultPayload.TYPE, DisarmResultPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoundStatePayload.TYPE, RoundStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardSnapshotPayload.TYPE, BoardSnapshotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerInteractionAnimPayload.TYPE, PlayerInteractionAnimPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestBoardSnapshotPayload.TYPE, RequestBoardSnapshotPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DisarmResultPayload.TYPE, (payload, context) ->
                context.server().execute(() -> MinesweeperGameplay.handleDisarmResult(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(RequestBoardSnapshotPayload.TYPE, (payload, context) ->
                context.server().execute(() -> MinesweeperBoardSync.handleSnapshotRequest(context.player(), payload))
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(new RoundStatePayload(
                    MinesweeperRoundManager.isActive() && MinesweeperRoundManager.isParticipant(handler.player)
            ));
            MinesweeperBoardSync.sendBestSnapshot(handler.player);
        });
    }
}
