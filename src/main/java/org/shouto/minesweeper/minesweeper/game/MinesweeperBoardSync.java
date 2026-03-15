package org.shouto.minesweeper.minesweeper.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.BoardData;
import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;
import org.shouto.minesweeper.minesweeper.network.payload.RequestBoardSnapshotPayload;

import java.util.List;
import java.util.Optional;

public final class MinesweeperBoardSync {
    private MinesweeperBoardSync() {
    }

    public static void handleSnapshotRequest(ServerPlayer player, RequestBoardSnapshotPayload payload) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (MinesweeperRoundManager.isActive()) {
            MinesweeperRoundManager.ensureParticipant(player);
        }

        Optional<BoardData> boardOptional;
        if (payload.boardId() > 0) {
            boardOptional = MinesweeperBoardManager.getBoard(payload.boardId())
                    .filter(board -> board.dimension().equals(level.dimension()));
        } else {
            boardOptional = findBestBoard(player);
        }

        boardOptional.ifPresent(board -> sendSnapshot(player, board));
    }

    public static void sendBestSnapshot(ServerPlayer player) {
        if (MinesweeperRoundManager.isActive()) {
            MinesweeperRoundManager.ensureParticipant(player);
        }
        findBestBoard(player).ifPresent(board -> sendSnapshot(player, board));
    }

    public static void broadcastBoardSnapshot(ServerLevel level, BoardData board) {
        for (ServerPlayer player : level.players()) {
            sendSnapshot(player, board);
        }
    }

    private static void sendSnapshot(ServerPlayer player, BoardData board) {
        ServerPlayer focusPlayer = MinesweeperSessionManager.resolveBoardFocusPlayer(player);
        if (!(focusPlayer.level() instanceof ServerLevel focusLevel) || !focusLevel.dimension().equals(board.dimension())) {
            return;
        }

        byte[] cells = buildCells(board);
        int localX = focusPlayer.blockPosition().getX() - board.origin().getX();
        int localZ = focusPlayer.blockPosition().getZ() - board.origin().getZ();
        if (localX < 0 || localZ < 0 || localX >= board.width() || localZ >= board.height()) {
            localX = -1;
            localZ = -1;
        }

        ServerPlayNetworking.send(player, new BoardSnapshotPayload(
                board.boardId(),
                board.origin(),
                board.width(),
                board.height(),
                localX,
                localZ,
                cells
        ));
    }

    private static Optional<BoardData> findBestBoard(ServerPlayer player) {
        ServerPlayer focusPlayer = MinesweeperSessionManager.resolveBoardFocusPlayer(player);
        if (!(focusPlayer.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }

        Optional<BoardData> direct = MinesweeperBoardManager.getBoardAt(level, focusPlayer.blockPosition());
        if (direct.isPresent()) {
            return direct;
        }

        List<BoardData> boards = MinesweeperBoardManager.getBoardsInDimension(level.dimension());
        if (boards.isEmpty()) {
            return Optional.empty();
        }

        BoardData best = null;
        double bestDist = Double.MAX_VALUE;
        for (BoardData board : boards) {
            double centerX = board.origin().getX() + (board.width() * 0.5D);
            double centerZ = board.origin().getZ() + (board.height() * 0.5D);
            double dx = focusPlayer.getX() - centerX;
            double dz = focusPlayer.getZ() - centerZ;
            double dist = (dx * dx) + (dz * dz);
            if (dist < bestDist) {
                bestDist = dist;
                best = board;
            }
        }

        if (best == null) {
            return Optional.empty();
        }
        if (bestDist > 256.0D * 256.0D) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    private static byte[] buildCells(BoardData board) {
        byte[] cells = new byte[board.width() * board.height()];
        for (int localZ = 0; localZ < board.height(); localZ++) {
            for (int localX = 0; localX < board.width(); localX++) {
                int index = localZ * board.width() + localX;
                cells[index] = resolveCellState(board, localX, localZ);
            }
        }
        return cells;
    }

    private static byte resolveCellState(BoardData board, int localX, int localZ) {
        BlockPos worldPos = board.origin().offset(localX, 0, localZ);
        long local = packLocal(localX, localZ);

        if (board.disarmedMines().contains(local)) {
            return BoardSnapshotPayload.CELL_DISARMED_MINE;
        }
        if (board.explodedMines().contains(local)) {
            return BoardSnapshotPayload.CELL_REVEALED_MINE;
        }
        if (board.flagged().contains(local)) {
            return BoardSnapshotPayload.CELL_FLAG;
        }
        if (!board.revealed().contains(local)) {
            return BoardSnapshotPayload.CELL_HIDDEN;
        }
        if (board.mines().contains(local)) {
            return BoardSnapshotPayload.CELL_REVEALED_MINE;
        }

        int adjacent = Math.min(5, MinesweeperBoardManager.getAdjacentMines(board, worldPos));
        return switch (adjacent) {
            case 0 -> BoardSnapshotPayload.CELL_NUMBER_0;
            case 1 -> BoardSnapshotPayload.CELL_NUMBER_1;
            case 2 -> BoardSnapshotPayload.CELL_NUMBER_2;
            case 3 -> BoardSnapshotPayload.CELL_NUMBER_3;
            case 4 -> BoardSnapshotPayload.CELL_NUMBER_4;
            default -> BoardSnapshotPayload.CELL_NUMBER_5;
        };
    }

    private static long packLocal(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
