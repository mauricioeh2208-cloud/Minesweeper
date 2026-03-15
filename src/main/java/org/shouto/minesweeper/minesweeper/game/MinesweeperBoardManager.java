package org.shouto.minesweeper.minesweeper.game;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.shouto.minesweeper.minesweeper.block.MineOpenBlock;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperBlocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class MinesweeperBoardManager {
    private static final Map<Integer, BoardData> BOARDS = new HashMap<>();
    private static final AtomicInteger NEXT_BOARD_ID = new AtomicInteger(1);

    private MinesweeperBoardManager() {
    }

    public static CreateBoardResult createBoard(ServerLevel level, BlockPos origin, int width, int height, int requestedMines) {
        int totalCells = width * height;
        int mineCount = Math.min(requestedMines, totalCells);
        List<Long> slots = new ArrayList<>(totalCells);
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < height; z++) {
                slots.add(packLocal(x, z));
            }
        }
        Collections.shuffle(slots, new Random(level.getSeed() ^ origin.asLong() ^ System.nanoTime()));

        Set<Long> mines = new HashSet<>(mineCount);
        for (int i = 0; i < mineCount; i++) {
            mines.add(slots.get(i));
        }

        return createBoard(level, origin, width, height, requestedMines, mines);
    }

    public static CreateBoardResult createBoard(ServerLevel level, BlockPos origin, int width, int height, List<MineCell> mineCells) {
        Set<Long> mines = new HashSet<>();
        for (MineCell mineCell : mineCells) {
            if (mineCell.x() < 0 || mineCell.z() < 0 || mineCell.x() >= width || mineCell.z() >= height) {
                continue;
            }
            mines.add(packLocal(mineCell.x(), mineCell.z()));
        }

        return createBoard(level, origin, width, height, mines.size(), mines);
    }

    private static CreateBoardResult createBoard(
            ServerLevel level,
            BlockPos origin,
            int width,
            int height,
            int requestedMines,
            Set<Long> mines
    ) {
        int totalCells = width * height;
        int mineCount = Math.min(mines.size(), totalCells);
        Map<BlockPos, BlockState> snapshot = new HashMap<>();
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= height; z++) {
                BlockPos groundPos = origin.offset(x, 0, z);
                boolean border = x == -1 || z == -1 || x == width || z == height;
                BlockState groundState = border ? Blocks.QUARTZ_BLOCK.defaultBlockState() : hiddenGroundState(x, z);
                captureAndSet(level, snapshot, groundPos, groundState);
                captureAndSet(level, snapshot, groundPos.above(), Blocks.AIR.defaultBlockState());
            }
        }

        BlockPos cratePos = flagCratePosition(origin, width, height);
        captureAndSet(level, snapshot, cratePos, MinesweeperBlocks.FLAG_CRATE_BLOCK.defaultBlockState());
        captureAndSet(level, snapshot, cratePos.above(), Blocks.AIR.defaultBlockState());

        int boardId = NEXT_BOARD_ID.getAndIncrement();
        BoardData board = new BoardData(
                boardId,
                level.dimension(),
                origin.immutable(),
                width,
                height,
                mineCount,
                mines,
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(),
                new HashSet<>(),
                snapshot
        );
        BOARDS.put(boardId, board);
        MinesweeperBoardStorage.saveBoards(level.getServer());

        return new CreateBoardResult(boardId, totalCells, requestedMines, mineCount, cratePos);
    }

    public static boolean deleteBoard(MinecraftServer server, int boardId) {
        BoardData board = BOARDS.remove(boardId);
        if (board == null) {
            return false;
        }
        ServerLevel level = server.getLevel(board.dimension());
        if (level == null) {
            return false;
        }

        for (Map.Entry<BlockPos, BlockState> entry : board.snapshot().entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
        }
        MinesweeperBoardStorage.saveBoards(server);
        return true;
    }

    public static Optional<BoardData> getBoard(int boardId) {
        return Optional.ofNullable(BOARDS.get(boardId));
    }

    public static List<BoardData> getAllBoards() {
        return new ArrayList<>(BOARDS.values());
    }

    public static void clearLoadedBoards() {
        BOARDS.clear();
        NEXT_BOARD_ID.set(1);
    }

    public static List<BoardData> getBoardsInDimension(ResourceKey<Level> dimension) {
        List<BoardData> result = new ArrayList<>();
        for (BoardData board : BOARDS.values()) {
            if (board.dimension().equals(dimension)) {
                result.add(board);
            }
        }
        return result;
    }

    public static void resetAllBoardStates(MinecraftServer server) {
        for (BoardData board : BOARDS.values()) {
            ServerLevel level = server.getLevel(board.dimension());
            if (level != null) {
                resetBoardState(level, board);
            }
        }
    }

    public static Optional<BoardData> getBoardAt(ServerLevel level, BlockPos pos) {
        for (BoardData board : BOARDS.values()) {
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }
            if (isInside(board, pos)) {
                return Optional.of(board);
            }
        }
        return Optional.empty();
    }

    public static boolean isInside(BoardData board, BlockPos pos) {
        int x = pos.getX() - board.origin().getX();
        int z = pos.getZ() - board.origin().getZ();
        return pos.getY() == board.origin().getY() && x >= 0 && z >= 0 && x < board.width() && z < board.height();
    }

    public static boolean isMine(BoardData board, BlockPos cellPos) {
        return board.mines().contains(toLocal(board, cellPos));
    }

    public static boolean isDisarmed(BoardData board, BlockPos cellPos) {
        return board.disarmedMines().contains(toLocal(board, cellPos));
    }

    public static boolean isExploded(BoardData board, BlockPos cellPos) {
        return board.explodedMines().contains(toLocal(board, cellPos));
    }

    public static boolean isResolvedMine(BoardData board, BlockPos cellPos) {
        long local = toLocal(board, cellPos);
        return board.disarmedMines().contains(local) || board.explodedMines().contains(local);
    }

    public static boolean isFlagged(BoardData board, BlockPos cellPos) {
        return board.flagged().contains(toLocal(board, cellPos));
    }

    public static boolean isRevealed(BoardData board, BlockPos cellPos) {
        return board.revealed().contains(toLocal(board, cellPos));
    }

    public static int getAdjacentMines(BoardData board, BlockPos cellPos) {
        int count = 0;
        int localX = cellPos.getX() - board.origin().getX();
        int localZ = cellPos.getZ() - board.origin().getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int nx = localX + dx;
                int nz = localZ + dz;
                if (nx < 0 || nz < 0 || nx >= board.width() || nz >= board.height()) {
                    continue;
                }
                if (board.mines().contains(packLocal(nx, nz))) {
                    count++;
                }
            }
        }
        return count;
    }

    public static RevealResult reveal(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return RevealResult.OUTSIDE;
        }

        long local = toLocal(board, cellPos);
        if (board.revealed().contains(local) || board.disarmedMines().contains(local) || board.explodedMines().contains(local)) {
            return RevealResult.ALREADY_REVEALED;
        }
        if (board.flagged().contains(local)) {
            return RevealResult.FLAGGED;
        }

        board.revealed().add(local);
        if (board.mines().contains(local)) {
            exposeMine(level, board, cellPos, true);
            return RevealResult.MINE;
        }

        revealNumber(level, board, cellPos);
        return RevealResult.NUMBER;
    }

    public static boolean disarmMine(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return false;
        }

        long local = toLocal(board, cellPos);
        if (!board.mines().contains(local) || board.explodedMines().contains(local)) {
            return false;
        }

        board.disarmedMines().add(local);
        board.revealed().add(local);
        board.flagged().remove(local);
        exposeMine(level, board, cellPos, false);
        return true;
    }

    public static boolean explodeMine(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return false;
        }

        long local = toLocal(board, cellPos);
        if (!board.mines().contains(local) || board.disarmedMines().contains(local) || board.explodedMines().contains(local)) {
            return false;
        }

        board.explodedMines().add(local);
        board.revealed().add(local);
        board.flagged().remove(local);
        exposeMine(level, board, cellPos, false);
        return true;
    }

    public static void showMineAsActive(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return;
        }

        long local = toLocal(board, cellPos);
        if (!board.mines().contains(local) || board.disarmedMines().contains(local) || board.explodedMines().contains(local)) {
            return;
        }

        board.revealed().add(local);
        board.flagged().remove(local);
        exposeMine(level, board, cellPos, true);
    }

    public static void showMineAsInactive(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos) || !isMine(board, cellPos)) {
            return;
        }

        exposeMine(level, board, cellPos, false);
    }

    public static boolean placeFlag(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return false;
        }

        long local = toLocal(board, cellPos);
        if (board.flagged().contains(local) || board.revealed().contains(local) || board.disarmedMines().contains(local) || board.explodedMines().contains(local)) {
            return false;
        }

        BlockPos markerPos = cellPos.above();
        BlockState markerState = level.getBlockState(markerPos);
        if (!markerState.isAir()) {
            return false;
        }

        board.flagged().add(local);
        capture(board, level, markerPos);
        level.setBlock(markerPos, MinesweeperBlocks.FLAG_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    public static boolean removeFlag(ServerLevel level, BoardData board, BlockPos cellPos) {
        if (!isInside(board, cellPos)) {
            return false;
        }

        long local = toLocal(board, cellPos);
        if (!board.flagged().remove(local)) {
            return false;
        }

        BlockPos markerPos = cellPos.above();
        capture(board, level, markerPos);
        level.setBlock(markerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    public static boolean isCompleted(BoardData board) {
        for (long mine : board.mines()) {
            if (!board.disarmedMines().contains(mine) && !board.explodedMines().contains(mine)) {
                return false;
            }
        }
        return !board.mines().isEmpty();
    }

    private static void revealNumber(ServerLevel level, BoardData board, BlockPos cellPos) {
        int adjacent = getAdjacentMines(board, cellPos);
        Block numberBlock = MinesweeperBlocks.NUMBER_BLOCKS[Math.min(5, adjacent)];
        capture(board, level, cellPos);
        level.setBlock(cellPos, numberBlock.defaultBlockState(), Block.UPDATE_ALL);
        clearMarker(level, board, cellPos);
    }

    private static void exposeMine(ServerLevel level, BoardData board, BlockPos cellPos, boolean triggered) {
        capture(board, level, cellPos);
        level.setBlock(
                cellPos,
                hiddenGroundState(cellPos.getX() - board.origin().getX(), cellPos.getZ() - board.origin().getZ()),
                Block.UPDATE_ALL
        );

        BlockPos markerPos = cellPos.above();
        capture(board, level, markerPos);
        level.setBlock(
                markerPos,
                MinesweeperBlocks.MINE_OPEN_BLOCK.defaultBlockState().setValue(MineOpenBlock.TRIGGERED, triggered),
                Block.UPDATE_ALL
        );
    }

    private static void clearMarker(ServerLevel level, BoardData board, BlockPos cellPos) {
        BlockPos markerPos = cellPos.above();
        capture(board, level, markerPos);
        level.setBlock(markerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void resetBoardState(ServerLevel level, BoardData board) {
        board.disarmedMines().clear();
        board.explodedMines().clear();
        board.revealed().clear();
        board.flagged().clear();

        for (int x = -1; x <= board.width(); x++) {
            for (int z = -1; z <= board.height(); z++) {
                BlockPos groundPos = board.origin().offset(x, 0, z);
                boolean border = x == -1 || z == -1 || x == board.width() || z == board.height();
                BlockState groundState = border ? Blocks.QUARTZ_BLOCK.defaultBlockState() : hiddenGroundState(x, z);
                level.setBlock(groundPos, groundState, Block.UPDATE_ALL);
                level.setBlock(groundPos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        BlockPos cratePos = flagCratePosition(board.origin(), board.width(), board.height());
        level.setBlock(cratePos, MinesweeperBlocks.FLAG_CRATE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(cratePos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void capture(BoardData board, ServerLevel level, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        board.snapshot().putIfAbsent(immutable, level.getBlockState(immutable));
    }

    private static void captureAndSet(ServerLevel level, Map<BlockPos, BlockState> snapshot, BlockPos pos, BlockState newState) {
        BlockPos immutable = pos.immutable();
        snapshot.putIfAbsent(immutable, level.getBlockState(immutable));
        level.setBlock(immutable, newState, Block.UPDATE_ALL);
    }

    private static BlockState hiddenGroundState(int x, int z) {
        return ((x + z) & 1) == 0
                ? MinesweeperBlocks.HIDDEN_BLOCK_1.defaultBlockState()
                : MinesweeperBlocks.HIDDEN_BLOCK_2.defaultBlockState();
    }

    private static BlockPos flagCratePosition(BlockPos origin, int width, int height) {
        return origin.offset(width + 2, 0, Math.max(0, height / 2));
    }

    private static long toLocal(BoardData board, BlockPos worldPos) {
        return packLocal(worldPos.getX() - board.origin().getX(), worldPos.getZ() - board.origin().getZ());
    }

    private static long packLocal(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    public enum RevealResult {
        OUTSIDE,
        ALREADY_REVEALED,
        FLAGGED,
        NUMBER,
        MINE
    }

    public record CreateBoardResult(
            int boardId,
            int totalCells,
            int requestedMines,
            int placedMines,
            BlockPos flagCratePosition
    ) {
    }

    public record MineCell(int x, int z) {
    }

    public record BoardData(
            int boardId,
            ResourceKey<Level> dimension,
            BlockPos origin,
            int width,
            int height,
            int mineCount,
            Set<Long> mines,
            Set<Long> disarmedMines,
            Set<Long> explodedMines,
            Set<Long> revealed,
            Set<Long> flagged,
            Map<BlockPos, BlockState> snapshot
    ) {
    }
}
