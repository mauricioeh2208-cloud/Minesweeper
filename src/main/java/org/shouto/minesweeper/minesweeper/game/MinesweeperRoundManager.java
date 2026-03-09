package org.shouto.minesweeper.minesweeper.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.shouto.minesweeper.minesweeper.network.payload.RoundStatePayload;

public final class MinesweeperRoundManager {
    private static boolean active;

    private MinesweeperRoundManager() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(MinecraftServer server, boolean roundActive) {
        active = roundActive;
        broadcast(server);
    }

    public static void broadcast(MinecraftServer server) {
        RoundStatePayload payload = new RoundStatePayload(active);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
