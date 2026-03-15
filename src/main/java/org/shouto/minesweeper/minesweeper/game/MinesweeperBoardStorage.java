package org.shouto.minesweeper.minesweeper.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.BoardData;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.MineCell;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MinesweeperBoardStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("Buscaminas");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("boards.json");

    private MinesweeperBoardStorage() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(MinesweeperBoardStorage::loadBoards);
        ServerLifecycleEvents.SERVER_STOPPING.register(MinesweeperBoardStorage::saveBoards);
    }

    public static void loadBoards(MinecraftServer server) {
        MinesweeperBoardManager.clearLoadedBoards();
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        StoredBoards stored;
        try (Reader reader = Files.newBufferedReader(CONFIG_FILE)) {
            stored = GSON.fromJson(reader, StoredBoards.class);
        } catch (IOException | JsonSyntaxException ignored) {
            return;
        }

        if (stored == null || stored.boards == null) {
            return;
        }

        for (StoredBoard board : stored.boards) {
            ServerLevel level = server.getLevel(parseDimension(board.dimension));
            if (level == null) {
                continue;
            }

            BlockPos origin = new BlockPos(board.x, board.y, board.z);
            if (board.mineCells != null && !board.mineCells.isEmpty()) {
                List<MineCell> mineCells = new ArrayList<>(board.mineCells.size());
                for (StoredMine mineCell : board.mineCells) {
                    mineCells.add(new MineCell(mineCell.x, mineCell.z));
                }
                MinesweeperBoardManager.createBoard(level, origin, board.width, board.height, mineCells);
                continue;
            }

            MinesweeperBoardManager.createBoard(level, origin, board.width, board.height, board.mines);
        }
    }

    public static void saveBoards(MinecraftServer server) {
        if (server == null) {
            return;
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            StoredBoards stored = new StoredBoards();

            List<BoardData> boards = new ArrayList<>(MinesweeperBoardManager.getAllBoards());
            boards.sort(Comparator.comparing((BoardData board) -> board.dimension().identifier().toString())
                    .thenComparingInt(board -> board.origin().getX())
                    .thenComparingInt(board -> board.origin().getY())
                    .thenComparingInt(board -> board.origin().getZ()));

            for (BoardData board : boards) {
                List<StoredMine> mineCells = new ArrayList<>();
                for (long mine : board.mines()) {
                    mineCells.add(new StoredMine(unpackLocalX(mine), unpackLocalZ(mine)));
                }
                mineCells.sort(Comparator.comparingInt((StoredMine mine) -> mine.x).thenComparingInt(mine -> mine.z));

                stored.boards.add(new StoredBoard(
                        board.dimension().identifier().toString(),
                        board.origin().getX(),
                        board.origin().getY(),
                        board.origin().getZ(),
                        board.width(),
                        board.height(),
                        board.mineCount(),
                        mineCells
                ));
            }

            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(stored, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static ResourceKey<Level> parseDimension(String raw) {
        String[] parts = raw != null ? raw.split(":", 2) : new String[0];
        String namespace = parts.length == 2 ? parts[0] : "minecraft";
        String path = parts.length == 2 ? parts[1] : (raw == null || raw.isBlank() ? "overworld" : raw);
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static int unpackLocalX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackLocalZ(long packed) {
        return (int) packed;
    }

    private static final class StoredBoards {
        private final List<StoredBoard> boards = new ArrayList<>();
    }

    private record StoredBoard(
            String dimension,
            int x,
            int y,
            int z,
            int width,
            int height,
            int mines,
            List<StoredMine> mineCells
    ) {
    }

    private static final class StoredMine {
        private int x;
        private int z;

        private StoredMine(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
}
