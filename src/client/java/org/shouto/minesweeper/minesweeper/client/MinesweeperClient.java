package org.shouto.minesweeper.minesweeper.client;

import net.fabricmc.api.ClientModInitializer;

public class MinesweeperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MinesweeperClientEntities.initialize();
        MinesweeperClientNetworking.initialize();
        MinesweeperHudRenderer.initialize();
        MinesweeperKeybinds.initialize();
    }
}
