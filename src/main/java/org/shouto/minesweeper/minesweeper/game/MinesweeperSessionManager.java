package org.shouto.minesweeper.minesweeper.game;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.BoardData;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager.RoundDifficulty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MinesweeperSessionManager {
    private static final int INTRO_DURATION_TICKS = 0;
    private static final int INTRO_LOCK_EFFECT_TICKS = 30;
    private static final int CAMERA_REFRESH_INTERVAL_TICKS = 20;
    private static final List<RoundDifficulty> FULL_STAGE_ORDER = List.of(
            RoundDifficulty.FACIL,
            RoundDifficulty.MEDIO,
            RoundDifficulty.DIFICIL
    );
    private static final IntroCue[] INTRO_CUES = new IntroCue[0];

    private static SessionState currentSession;
    private static int lastProcessedTick = -1;

    private MinesweeperSessionManager() {
    }

    public static StartSessionResult startSession(MinecraftServer server) {
        stopSession();

        List<ServerPlayer> participants = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MinesweeperRoundManager.isParticipant(player)) {
                participants.add(player);
            }
        }

        if (participants.isEmpty()) {
            return StartSessionResult.failure("No hay participantes para iniciar la sesion.");
        }

        LinkedHashMap<String, TeamSessionState> teams = buildTeams(participants);
        if (teams.isEmpty()) {
            return StartSessionResult.failure("No se pudieron formar participantes para la sesion.");
        }

        Map<RoundDifficulty, List<BoardData>> boardsByDifficulty = collectBoardsByDifficulty();
        List<RoundDifficulty> stageOrder = resolveStageOrder(boardsByDifficulty, teams.size());
        if (stageOrder.isEmpty()) {
            return StartSessionResult.failure(buildMissingBoardsMessage(boardsByDifficulty, teams.size()));
        }

        int introStartTick = server.getTickCount();
        int introEndTick = introStartTick + INTRO_DURATION_TICKS;
        int playEndTick = introStartTick + MinesweeperRoundManager.activeSettings().roundDurationTicks();
        SessionState session = new SessionState(introStartTick, introEndTick, playEndTick, stageOrder);
        session.phase = SessionPhase.PLAYING;

        for (TeamSessionState team : teams.values()) {
            session.teams.put(team.teamKey, team);
            for (UUID memberId : team.memberIds) {
                session.playerTeams.put(memberId, team.teamKey);
            }
        }

        assignBoards(session, boardsByDifficulty);
        currentSession = session;
        lastProcessedTick = -1;
        MinesweeperRoundManager.setRoundTiming(introEndTick, playEndTick);

        for (TeamSessionState team : session.teams.values()) {
            teleportTeamToCurrentBoard(server, team, false);
        }

        broadcastParticipants(server, Component.literal("Sesion de buscaminas iniciada."), false);
        return StartSessionResult.success(
                "Sesion iniciada para " + participants.size()
                        + " jugador(es) en modo " + describeStageOrder(stageOrder) + "."
        );
    }

    public static void stopSession() {
        currentSession = null;
        lastProcessedTick = -1;
    }

    public static boolean hasActiveSession() {
        return currentSession != null;
    }

    public static boolean isGameplayOpen() {
        return currentSession == null || currentSession.phase == SessionPhase.PLAYING;
    }

    public static boolean shouldForceSpectator(ServerPlayer player) {
        TeamSessionState team = teamState(player);
        return team != null && (team.eliminated || team.completed);
    }

    public static String teamKeyForPlayer(ServerPlayer player) {
        if (currentSession == null) {
            return "solo:" + player.getUUID();
        }

        return currentSession.playerTeams.get(player.getUUID());
    }

    public static boolean isSameTeam(ServerPlayer player, String teamKey) {
        if (teamKey == null) {
            return false;
        }

        return teamKey.equals(teamKeyForPlayer(player));
    }

    public static ServerPlayer resolveBoardFocusPlayer(ServerPlayer viewer) {
        if (currentSession == null || !(viewer.level() instanceof ServerLevel level)) {
            return viewer;
        }

        UUID targetId = currentSession.spectatorTargets.get(viewer.getUUID());
        if (targetId == null) {
            return viewer;
        }

        ServerPlayer target = level.getServer().getPlayerList().getPlayer(targetId);
        return target != null ? target : viewer;
    }

    public static boolean handleBoardCompletion(ServerLevel level, BoardData board) {
        if (currentSession == null || currentSession.phase != SessionPhase.PLAYING) {
            return false;
        }

        BoardAssignment assignment = currentSession.boardAssignments.get(board.boardId());
        if (assignment == null) {
            return false;
        }

        TeamSessionState team = currentSession.teams.get(assignment.teamKey());
        if (team == null || team.eliminated || team.completed || team.currentStageIndex != assignment.stageIndex()) {
            return false;
        }

        team.completedMineScore += board.mineCount();
        team.bestScore = Math.max(team.bestScore, team.completedMineScore);

        if (team.currentStageIndex + 1 < currentSession.stageOrder.size()) {
            team.currentStageIndex++;
            String nextDifficulty = currentSession.stageOrder.get(team.currentStageIndex).id();
            broadcastParticipants(
                    level.getServer(),
                    Component.literal(team.displayName + " completo " + assignment.difficulty().id() + " y pasa a " + nextDifficulty + "."),
                    false
            );
            teleportTeamToCurrentBoard(level.getServer(), team, true);
            updateTeamScore(team);
        } else {
            team.completed = true;
            String finishedMessage = currentSession.stageOrder.size() > 1
                    ? team.displayName + " completo los " + currentSession.stageOrder.size() + " tableros con " + team.bestScore + " punto(s)."
                    : team.displayName + " completo " + currentSession.stageOrder.get(0).id() + " con " + team.bestScore + " punto(s).";
            broadcastParticipants(level.getServer(), Component.literal(finishedMessage), false);
            moveTeamToObserverMode(
                    level.getServer(),
                    team,
                Component.literal("Terminaste el recorrido. Ahora spectearas a los que sigan jugando.")
        );
        }

        finishIfNoActiveTeams(level.getServer(), "Todos los jugadores terminaron su recorrido.");
        return true;
    }

    public static void handleTeamEliminated(MinecraftServer server, String teamKey) {
        if (currentSession == null || teamKey == null) {
            return;
        }

        TeamSessionState team = currentSession.teams.get(teamKey);
        if (team == null || team.eliminated || team.completed) {
            return;
        }

        team.eliminated = true;
        updateTeamScore(team);
        broadcastParticipants(
                server,
                Component.literal(team.displayName + " quedo fuera con " + team.bestScore + " punto(s)."),
                false
        );
        moveTeamToObserverMode(
                server,
                team,
                Component.literal("Ya no tienes vidas. Ahora spectearas a los que sigan jugando.")
        );

        finishIfNoActiveTeams(server, "Ya no quedan jugadores jugando.");
    }

    public static void tick(MinecraftServer server, int tick) {
        if (currentSession == null || tick == lastProcessedTick) {
            return;
        }
        lastProcessedTick = tick;

        if (currentSession.phase == SessionPhase.INTRO) {
            tickIntro(server, tick);
            return;
        }

        updateAllScores();
        if (tick >= currentSession.playEndTick) {
            finishSessionAndRound(server, "Tiempo agotado. Ronda finalizada.");
            return;
        }

        if (tick % CAMERA_REFRESH_INTERVAL_TICKS == 0) {
            refreshObserverCameras(server);
        }
    }

    public static List<String> describeSession() {
        if (currentSession == null) {
            return List.of("Sin sesion activa.");
        }

        List<String> lines = new ArrayList<>();
        lines.add(
                "fase=" + currentSession.phase.id
                        + ", jugadores=" + currentSession.teams.size()
                        + ", intro_restante=" + formatSeconds(Math.max(0, currentSession.introEndTick - currentSession.introStartTick))
        );

        List<TeamSessionState> sortedTeams = new ArrayList<>(currentSession.teams.values());
        sortedTeams.sort(Comparator.comparingInt((TeamSessionState team) -> team.bestScore).reversed()
                .thenComparing(team -> team.displayName));
        for (TeamSessionState team : sortedTeams) {
            String status = team.completed ? "completado" : team.eliminated ? "eliminado" : currentSession.stageOrder.get(team.currentStageIndex).id();
            lines.add(team.displayName + ": puntos=" + team.bestScore + ", estado=" + status);
        }
        return lines;
    }

    private static void tickIntro(MinecraftServer server, int tick) {
        int elapsed = tick - currentSession.introStartTick;
        int remaining = Math.max(0, currentSession.introEndTick - tick);

        for (IntroCue cue : INTRO_CUES) {
            if (elapsed >= cue.offsetTicks() && currentSession.firedCueOffsets.add(cue.offsetTicks())) {
                sendCue(currentSession, server, cue);
            }
        }

        for (ServerPlayer player : sessionParticipants(server)) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, INTRO_LOCK_EFFECT_TICKS, 6, false, false, false));
            player.setDeltaMovement(Vec3.ZERO);
            player.stopUsingItem();
        }

        if (remaining > 0 && remaining <= 20 * 5 && remaining % 20 == 0) {
            broadcastParticipants(server, Component.literal("La partida comienza en " + (remaining / 20) + "..."), true);
        }

        if (tick < currentSession.introEndTick) {
            return;
        }

        currentSession.phase = SessionPhase.PLAYING;
        broadcastParticipants(server, Component.literal("La partida comenzo. Buena suerte."), false);
        for (ServerPlayer player : sessionParticipants(server)) {
            player.removeEffect(MobEffects.SLOWNESS);
            player.setCamera(player);
            if (!shouldForceSpectator(player) && player.gameMode() == GameType.SPECTATOR) {
                player.setGameMode(MinesweeperRoundManager.participantGameMode(player.getUUID()));
            }
            MinesweeperBoardSync.sendBestSnapshot(player);
        }
    }

    private static void sendCue(SessionState session, MinecraftServer server, IntroCue cue) {
        broadcastParticipants(server, Component.literal(cue.text()), cue.actionBar());
        session.firedCueOffsets.add(cue.offsetTicks());
    }

    private static void updateAllScores() {
        if (currentSession == null) {
            return;
        }

        for (TeamSessionState team : currentSession.teams.values()) {
            updateTeamScore(team);
        }
    }

    private static void updateTeamScore(TeamSessionState team) {
        int score = team.completedMineScore;
        if (!team.completed && !team.eliminated) {
            BoardData board = currentBoard(team);
            if (board != null) {
                score += discoveredMineCount(board);
            }
        }
        team.bestScore = Math.max(team.bestScore, score);
    }

    private static void finishIfNoActiveTeams(MinecraftServer server, String message) {
        if (countActiveTeams() <= 0) {
            finishSessionAndRound(server, message);
        }
    }

    private static int countActiveTeams() {
        if (currentSession == null) {
            return 0;
        }

        int activeTeams = 0;
        for (TeamSessionState team : currentSession.teams.values()) {
            if (!team.eliminated && !team.completed) {
                activeTeams++;
            }
        }
        return activeTeams;
    }

    private static void finishSessionAndRound(MinecraftServer server, String message) {
        if (currentSession == null) {
            return;
        }

        updateAllScores();
        broadcastParticipants(server, Component.literal(message), false);

        List<TeamSessionState> standings = new ArrayList<>(currentSession.teams.values());
        standings.sort(Comparator.comparingInt((TeamSessionState team) -> team.bestScore).reversed()
                .thenComparing(team -> team.displayName));

        for (int index = 0; index < standings.size(); index++) {
            TeamSessionState team = standings.get(index);
            broadcastParticipants(
                    server,
                    Component.literal((index + 1) + ". " + team.displayName + " - " + team.bestScore + " punto(s)"),
                    false
            );
        }

        stopSession();
        MinesweeperGameplay.resetRoundState();
        MinesweeperBoardManager.resetAllBoardStates(server);
        MinesweeperRoundManager.stopRound(server);
    }

    private static void assignBoards(SessionState session, Map<RoundDifficulty, List<BoardData>> boardsByDifficulty) {
        List<TeamSessionState> orderedTeams = new ArrayList<>(session.teams.values());
        orderedTeams.sort(Comparator.comparing(team -> team.displayName));

        for (int stageIndex = 0; stageIndex < session.stageOrder.size(); stageIndex++) {
            RoundDifficulty difficulty = session.stageOrder.get(stageIndex);
            List<BoardData> boards = boardsByDifficulty.getOrDefault(difficulty, List.of());
            for (int teamIndex = 0; teamIndex < orderedTeams.size(); teamIndex++) {
                TeamSessionState team = orderedTeams.get(teamIndex);
                BoardData board = boards.get(teamIndex);
                team.stageBoardIds.put(stageIndex, board.boardId());
                session.boardAssignments.put(board.boardId(), new BoardAssignment(team.teamKey, difficulty, stageIndex));
            }
        }
    }

    private static Map<RoundDifficulty, List<BoardData>> collectBoardsByDifficulty() {
        Map<RoundDifficulty, List<BoardData>> result = new HashMap<>();
        for (BoardData board : MinesweeperBoardManager.getAllBoards()) {
            RoundDifficulty difficulty = classifyBoard(board);
            if (difficulty == null) {
                continue;
            }
            result.computeIfAbsent(difficulty, key -> new ArrayList<>()).add(board);
        }

        Comparator<BoardData> boardComparator = Comparator
                .comparing((BoardData board) -> board.dimension().toString())
                .thenComparingInt(board -> board.origin().getX())
                .thenComparingInt(board -> board.origin().getY())
                .thenComparingInt(board -> board.origin().getZ());

        for (List<BoardData> boards : result.values()) {
            boards.sort(boardComparator);
        }

        return result;
    }

    private static RoundDifficulty classifyBoard(BoardData board) {
        for (RoundDifficulty difficulty : FULL_STAGE_ORDER) {
            if (board.width() == difficulty.boardWidth()
                    && board.height() == difficulty.boardHeight()
                    && board.mineCount() == difficulty.mineCount()) {
                return difficulty;
            }
        }

        return null;
    }

    private static List<RoundDifficulty> resolveStageOrder(Map<RoundDifficulty, List<BoardData>> boardsByDifficulty, int teamCount) {
        if (hasEnoughBoards(boardsByDifficulty, FULL_STAGE_ORDER, teamCount)) {
            return FULL_STAGE_ORDER;
        }

        try {
            RoundDifficulty selected = RoundDifficulty.byId(MinesweeperRoundManager.activeSettings().label());
            if (hasEnoughBoards(boardsByDifficulty, List.of(selected), teamCount)) {
                return List.of(selected);
            }
        } catch (IllegalArgumentException ignored) {
        }

        return List.of();
    }

    private static boolean hasEnoughBoards(
            Map<RoundDifficulty, List<BoardData>> boardsByDifficulty,
            List<RoundDifficulty> stageOrder,
            int teamCount
    ) {
        for (RoundDifficulty difficulty : stageOrder) {
            if (boardsByDifficulty.getOrDefault(difficulty, List.of()).size() < teamCount) {
                return false;
            }
        }
        return true;
    }

    private static String buildMissingBoardsMessage(Map<RoundDifficulty, List<BoardData>> boardsByDifficulty, int teamCount) {
        List<String> missing = new ArrayList<>();
        for (RoundDifficulty difficulty : FULL_STAGE_ORDER) {
            int available = boardsByDifficulty.getOrDefault(difficulty, List.of()).size();
            if (available < teamCount) {
                missing.add(difficulty.id() + " " + available + "/" + teamCount);
            }
        }

        if (missing.isEmpty()) {
            return "No hay un conjunto valido de tableros para esta sesion.";
        }

        return "Faltan tableros clasicos para la sesion: " + String.join(", ", missing) + ".";
    }

    private static String describeStageOrder(List<RoundDifficulty> stageOrder) {
        List<String> ids = new ArrayList<>();
        for (RoundDifficulty difficulty : stageOrder) {
            ids.add(difficulty.id());
        }
        return String.join("->", ids);
    }

    private static LinkedHashMap<String, TeamSessionState> buildTeams(List<ServerPlayer> participants) {
        participants.sort(Comparator.comparing(ServerPlayer::getScoreboardName));

        LinkedHashMap<String, TeamSessionState> teams = new LinkedHashMap<>();
        for (ServerPlayer player : participants) {
            PlayerTeam team = player.getTeam();
            String teamKey = "solo:" + player.getUUID();
            String displayName = player.getScoreboardName();

            TeamSessionState teamState = teams.computeIfAbsent(teamKey, key -> new TeamSessionState(teamKey, displayName));
            teamState.memberIds.add(player.getUUID());
        }
        return teams;
    }

    private static TeamSessionState teamState(ServerPlayer player) {
        if (currentSession == null) {
            return null;
        }

        String teamKey = currentSession.playerTeams.get(player.getUUID());
        if (teamKey == null) {
            return null;
        }

        return currentSession.teams.get(teamKey);
    }

    private static BoardData currentBoard(TeamSessionState team) {
        Integer boardId = team.stageBoardIds.get(team.currentStageIndex);
        if (boardId == null) {
            return null;
        }

        return MinesweeperBoardManager.getBoard(boardId).orElse(null);
    }

    private static int discoveredMineCount(BoardData board) {
        int count = 0;
        for (long mine : board.mines()) {
            if (board.disarmedMines().contains(mine) || board.explodedMines().contains(mine) || board.flagged().contains(mine) || board.revealed().contains(mine)) {
                count++;
            }
        }
        return count;
    }

    private static void teleportTeamToCurrentBoard(MinecraftServer server, TeamSessionState team, boolean announceStage) {
        BoardData board = currentBoard(team);
        if (board == null) {
            return;
        }

        ServerLevel level = server.getLevel(board.dimension());
        if (level == null) {
            return;
        }

        List<ServerPlayer> players = onlinePlayers(server, team);
        for (int index = 0; index < players.size(); index++) {
            ServerPlayer player = players.get(index);
            Vec3 spawnPos = stageSpawnPosition(board, index, Math.max(1, players.size()));
            teleportPlayer(player, level, spawnPos);
            player.setCamera(player);
        }

        if (announceStage) {
            String difficulty = currentSession.stageOrder.get(team.currentStageIndex).id();
            sendTeamMessage(server, team, Component.literal("Nuevo tablero asignado: " + difficulty + "."), false);
        }
    }

    private static Vec3 stageSpawnPosition(BoardData board, int playerIndex, int playerCount) {
        double centerX = board.origin().getX() + (board.width() / 2.0D);
        double spacing = 1.35D;
        double startX = centerX - (((playerCount - 1) * spacing) * 0.5D);
        double x = startX + (playerIndex * spacing);
        double y = board.origin().getY() + 1.0D;
        double z = board.origin().getZ() - 2.5D;
        return new Vec3(x, y, z);
    }

    private static void moveTeamToObserverMode(MinecraftServer server, TeamSessionState team, Component message) {
        ServerPlayer target = selectSpectatorTarget(server, team.teamKey);
        for (ServerPlayer player : onlinePlayers(server, team)) {
            player.stopUsingItem();
            player.setGameMode(GameType.SPECTATOR);
            player.displayClientMessage(message, false);
            attachSpectatorTarget(player, target);
        }
    }

    private static void refreshObserverCameras(MinecraftServer server) {
        if (currentSession == null) {
            return;
        }

        for (TeamSessionState team : currentSession.teams.values()) {
            if (!team.eliminated && !team.completed) {
                continue;
            }

            ServerPlayer target = selectSpectatorTarget(server, team.teamKey);
            for (ServerPlayer player : onlinePlayers(server, team)) {
                if (player.gameMode() != GameType.SPECTATOR) {
                    player.setGameMode(GameType.SPECTATOR);
                }
                attachSpectatorTarget(player, target);
            }
        }
    }

    private static void attachSpectatorTarget(ServerPlayer viewer, ServerPlayer target) {
        if (currentSession == null) {
            return;
        }

        UUID currentTarget = currentSession.spectatorTargets.get(viewer.getUUID());
        if (target == null) {
            viewer.setCamera(viewer);
            currentSession.spectatorTargets.remove(viewer.getUUID());
            return;
        }
        if (target.getUUID().equals(currentTarget)) {
            viewer.setCamera(target);
            return;
        }

        teleportPlayer(viewer, (ServerLevel) target.level(), target.position());
        viewer.setCamera(target);
        currentSession.spectatorTargets.put(viewer.getUUID(), target.getUUID());
    }

    private static ServerPlayer selectSpectatorTarget(MinecraftServer server, String excludedTeamKey) {
        ServerPlayer fallback = null;
        for (ServerPlayer player : sessionParticipants(server)) {
            String playerTeamKey = teamKeyForPlayer(player);
            if (playerTeamKey == null || playerTeamKey.equals(excludedTeamKey)) {
                continue;
            }

            TeamSessionState otherTeam = currentSession != null ? currentSession.teams.get(playerTeamKey) : null;
            if (otherTeam == null || otherTeam.eliminated || otherTeam.completed) {
                continue;
            }

            if (fallback == null) {
                fallback = player;
            }
            if (!player.isSpectator()) {
                return player;
            }
        }

        return fallback;
    }

    private static void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 position) {
        player.teleportTo(level, position.x, position.y, position.z, Collections.<Relative>emptySet(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static List<ServerPlayer> onlinePlayers(MinecraftServer server, TeamSessionState team) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID memberId : team.memberIds) {
            ServerPlayer player = server.getPlayerList().getPlayer(memberId);
            if (player != null && MinesweeperRoundManager.isParticipant(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static List<ServerPlayer> sessionParticipants(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (MinesweeperRoundManager.isParticipant(player)) {
                players.add(player);
            }
        }
        return players;
    }

    private static void sendTeamMessage(MinecraftServer server, TeamSessionState team, Component message, boolean actionBar) {
        for (ServerPlayer player : onlinePlayers(server, team)) {
            player.displayClientMessage(message, actionBar);
        }
    }

    private static void broadcastParticipants(MinecraftServer server, Component message, boolean actionBar) {
        for (ServerPlayer player : sessionParticipants(server)) {
            player.displayClientMessage(message, actionBar);
        }
    }

    private static String formatSeconds(int ticks) {
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    public record StartSessionResult(boolean success, String message) {
        public static StartSessionResult success(String message) {
            return new StartSessionResult(true, message);
        }

        public static StartSessionResult failure(String message) {
            return new StartSessionResult(false, message);
        }
    }

    private enum SessionPhase {
        INTRO("intro"),
        PLAYING("jugando");

        private final String id;

        SessionPhase(String id) {
            this.id = id;
        }
    }

    private record IntroCue(int offsetTicks, String text, boolean actionBar) {
    }

    private record BoardAssignment(String teamKey, RoundDifficulty difficulty, int stageIndex) {
    }

    private static final class SessionState {
        private final int introStartTick;
        private final int introEndTick;
        private final int playEndTick;
        private final List<RoundDifficulty> stageOrder;
        private final Map<String, TeamSessionState> teams = new LinkedHashMap<>();
        private final Map<UUID, String> playerTeams = new HashMap<>();
        private final Map<Integer, BoardAssignment> boardAssignments = new HashMap<>();
        private final Map<UUID, UUID> spectatorTargets = new HashMap<>();
        private final Set<Integer> firedCueOffsets = new HashSet<>();
        private SessionPhase phase = SessionPhase.INTRO;

        private SessionState(int introStartTick, int introEndTick, int playEndTick, List<RoundDifficulty> stageOrder) {
            this.introStartTick = introStartTick;
            this.introEndTick = introEndTick;
            this.playEndTick = playEndTick;
            this.stageOrder = new ArrayList<>(stageOrder);
        }
    }

    private static final class TeamSessionState {
        private final String teamKey;
        private final String displayName;
        private final List<UUID> memberIds = new ArrayList<>();
        private final Map<Integer, Integer> stageBoardIds = new HashMap<>();
        private int currentStageIndex;
        private int completedMineScore;
        private int bestScore;
        private boolean eliminated;
        private boolean completed;

        private TeamSessionState(String teamKey, String displayName) {
            this.teamKey = teamKey;
            this.displayName = displayName;
        }
    }
}
