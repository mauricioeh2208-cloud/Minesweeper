package org.shouto.minesweeper.minesweeper;

import net.fabricmc.api.ModInitializer;
import org.shouto.minesweeper.minesweeper.command.MinesweeperCommands;
import org.shouto.minesweeper.minesweeper.game.MinesweeperGameplay;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardStorage;
import org.shouto.minesweeper.minesweeper.network.MinesweeperNetworking;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperBlocks;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperEntities;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItemGroups;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

public class Minesweeper implements ModInitializer {
    public static final String MOD_ID = "minesweeper";

    @Override
    public void onInitialize() {
        MinesweeperEntities.initialize();
        MinesweeperBlocks.initialize();
        MinesweeperItems.initialize();
        MinesweeperItemGroups.initialize();
        MinesweeperNetworking.initialize();
        MinesweeperCommands.initialize();
        MinesweeperGameplay.initialize();
        MinesweeperBoardStorage.initialize();
    }
}
