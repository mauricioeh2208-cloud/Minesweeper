package org.shouto.minesweeper.minesweeper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager;
import org.shouto.minesweeper.minesweeper.game.MinesweeperGameplay;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager;
import org.shouto.minesweeper.minesweeper.game.MinesweeperSessionManager;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager.RoundDifficulty;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager.RoundSettings;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager.TeamMode;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class MinesweeperCommands {
    private static final String ARG_PLAYERS = "jugadores";
    private static final String ARG_TEAM_MODE = "modo_equipos";
    private static final String ARG_TEAM_SIZE = "tamano_equipo";
    private static final String ARG_GIVE_ITEMS = "dar_objetos";
    private static final String ARG_MINE_DELAY = "retardo_mina_ticks";
    private static final String ARG_RESPAWN_SECONDS = "respawn_segundos";
    private static final String ARG_DISARM_SECONDS = "tiempo_desactivacion_segundos";
    private static final String ARG_DISABLER_COOLDOWN = "cooldown_desactivador_ticks";
    private static final String ARG_TOTEM_SECONDS = "cooldown_totem_segundos";

    private MinesweeperCommands() {
    }

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(MinesweeperCommands::register);
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext registryAccess,
            Commands.CommandSelection environment
    ) {
        dispatcher.register(Commands.literal("buscaminas")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("crear_tablero")
                        .then(Commands.argument("ancho", IntegerArgumentType.integer(2, 64))
                                .then(Commands.argument("alto", IntegerArgumentType.integer(2, 64))
                                        .then(Commands.argument("minas", IntegerArgumentType.integer(1, 4096))
                                                .executes(context -> createBoard(context, sourcePosition(context)))
                                                .then(Commands.argument("origen", BlockPosArgument.blockPos())
                                                        .executes(context -> createBoard(
                                                                context,
                                                                BlockPosArgument.getBlockPos(context, "origen")
                                                        )))
                                        ))))
                .then(buildClassicBoardCommand())
                .then(Commands.literal("borrar_tablero")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(MinesweeperCommands::deleteBoard)))
                .then(buildGiveItemsCommand())
                .then(buildConfigureRoundCommand())
                .then(buildStartRoundCommand())
                .then(Commands.literal("detener_ronda")
                        .executes(context -> stopRound(context, false)))
                .then(Commands.literal("terminar_ronda")
                        .executes(context -> stopRound(context, true)))
                .then(Commands.literal("test_explosion_animacion")
                        .executes(context -> testExplosionAnimation(context, MineExplosionEntity.ANIMATION_DURATION_TICKS))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, MineExplosionEntity.ANIMATION_DURATION_TICKS))
                                .executes(context -> testExplosionAnimation(
                                        context,
                                        IntegerArgumentType.getInteger(context, "ticks")
                                )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildGiveItemsCommand() {
        return Commands.literal("dar_objetos")
                .executes(MinesweeperCommands::giveItemsToSourcePlayer)
                .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                        .executes(context -> giveItems(context, EntityArgument.getPlayers(context, ARG_PLAYERS))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClassicBoardCommand() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("crear_tablero_clasico");
        for (RoundDifficulty difficulty : RoundDifficulty.values()) {
            builder.then(Commands.literal(difficulty.id())
                    .executes(context -> createClassicBoard(context, difficulty, sourcePosition(context)))
                    .then(Commands.argument("origen", BlockPosArgument.blockPos())
                            .executes(context -> createClassicBoard(
                                    context,
                                    difficulty,
                                    BlockPosArgument.getBlockPos(context, "origen")
                            ))));
        }
        return builder;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRandomTeamsCommand() {
        return Commands.literal("equipos_aleatorios")
                .executes(context -> assignRandomTeams(context, context.getSource().getServer().getPlayerList().getPlayers(), 5))
                .then(Commands.argument(ARG_TEAM_SIZE, IntegerArgumentType.integer(1, 64))
                        .executes(context -> assignRandomTeams(
                                context,
                                context.getSource().getServer().getPlayerList().getPlayers(),
                                IntegerArgumentType.getInteger(context, ARG_TEAM_SIZE)
                        )))
                .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                        .executes(context -> assignRandomTeams(context, EntityArgument.getPlayers(context, ARG_PLAYERS), 5))
                        .then(Commands.argument(ARG_TEAM_SIZE, IntegerArgumentType.integer(1, 64))
                                .executes(context -> assignRandomTeams(
                                        context,
                                        EntityArgument.getPlayers(context, ARG_PLAYERS),
                                        IntegerArgumentType.getInteger(context, ARG_TEAM_SIZE)
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildConfigureRoundCommand() {
        LiteralArgumentBuilder<CommandSourceStack> difficultyBranch = Commands.literal("dificultad");
        for (RoundDifficulty difficulty : RoundDifficulty.values()) {
            difficultyBranch.then(Commands.literal(difficulty.id())
                    .executes(context -> configureDifficulty(context, difficulty)));
        }

        return Commands.literal("configurar_ronda")
                .then(difficultyBranch)
                .then(Commands.literal("personalizada")
                        .then(buildCustomSettingsArguments(MinesweeperCommands::configureCustomRound)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildStartRoundCommand() {
        return Commands.literal("iniciar_ronda")
                .executes(context -> startRound(
                        context,
                        selectDefaultRoundPlayers(context.getSource().getServer()),
                        MinesweeperRoundManager.configuredSettings()
                ))
                .then(buildDifficultyStartBranch(null))
                .then(Commands.literal("personalizada")
                        .then(buildCustomSettingsArguments(context -> startCustomRound(
                                context,
                                selectDefaultRoundPlayers(context.getSource().getServer())
                        ))))
                .then(Commands.argument(ARG_PLAYERS, EntityArgument.players())
                        .executes(context -> startRound(
                                context,
                                EntityArgument.getPlayers(context, ARG_PLAYERS),
                                MinesweeperRoundManager.configuredSettings()
                        ))
                        .then(buildDifficultyStartBranch(ARG_PLAYERS))
                        .then(Commands.literal("personalizada")
                                .then(buildCustomSettingsArguments(context -> startCustomRound(
                                        context,
                                        resolvePlayers(context, ARG_PLAYERS)
                                )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDifficultyStartBranch(String playersArgumentName) {
        LiteralArgumentBuilder<CommandSourceStack> branch = Commands.literal("dificultad");
        for (RoundDifficulty difficulty : RoundDifficulty.values()) {
            branch.then(Commands.literal(difficulty.id())
                    .executes(context -> startRound(
                            context,
                            resolvePlayers(context, playersArgumentName),
                            MinesweeperRoundManager.configuredSettings().withDifficulty(difficulty)
                    )));
        }
        return branch;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Boolean> buildCustomSettingsArguments(
            RoundCommandExecutor executor
    ) {
        return Commands.argument(ARG_GIVE_ITEMS, BoolArgumentType.bool())
                .then(Commands.argument(ARG_MINE_DELAY, IntegerArgumentType.integer(1, 200))
                        .then(Commands.argument(ARG_RESPAWN_SECONDS, IntegerArgumentType.integer(1, 600))
                                .then(Commands.argument(ARG_DISARM_SECONDS, IntegerArgumentType.integer(1, 600))
                                        .then(Commands.argument(ARG_DISABLER_COOLDOWN, IntegerArgumentType.integer(1, 72000))
                                                .then(Commands.argument(ARG_TOTEM_SECONDS, IntegerArgumentType.integer(1, 3600))
                                                        .executes(executor::run))))));
    }

    private static int createBoard(CommandContext<CommandSourceStack> context, BlockPos origin) {
        int width = IntegerArgumentType.getInteger(context, "ancho");
        int height = IntegerArgumentType.getInteger(context, "alto");
        int mines = IntegerArgumentType.getInteger(context, "minas");

        MinesweeperBoardManager.CreateBoardResult result = MinesweeperBoardManager.createBoard(
                context.getSource().getLevel(),
                origin,
                width,
                height,
                mines
        );

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Tablero #" + result.boardId()
                                + " creado en (" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"
                                + " tamaño " + width + "x" + height
                                + " con " + result.placedMines() + " minas."
                ),
                true
        );
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Caja de banderas en (" + result.flagCratePosition().getX()
                                + ", " + result.flagCratePosition().getY()
                                + ", " + result.flagCratePosition().getZ() + ")."
                ),
                false
        );
        if (result.placedMines() < result.requestedMines()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Se ajustaron minas porque había menos casillas disponibles."),
                    false
            );
        }
        return result.boardId();
    }

    private static int deleteBoard(CommandContext<CommandSourceStack> context) {
        int boardId = IntegerArgumentType.getInteger(context, "id");
        boolean removed = MinesweeperBoardManager.deleteBoard(context.getSource().getServer(), boardId);
        if (!removed) {
            context.getSource().sendFailure(Component.literal("No existe un tablero con id " + boardId + "."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Tablero #" + boardId + " borrado y restaurado."), true);
        return 1;
    }

    private static int createClassicBoard(CommandContext<CommandSourceStack> context, RoundDifficulty difficulty, BlockPos origin) {
        MinesweeperBoardManager.CreateBoardResult result = MinesweeperBoardManager.createBoard(
                context.getSource().getLevel(),
                origin,
                difficulty.boardWidth(),
                difficulty.boardHeight(),
                difficulty.mineCount()
        );

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Tablero clasico " + difficulty.id()
                                + " #" + result.boardId()
                                + " creado en (" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"
                                + " tamano " + difficulty.boardWidth() + "x" + difficulty.boardHeight()
                                + " con " + difficulty.mineCount() + " minas."
                ),
                true
        );
        return result.boardId();
    }

    private static int giveItems(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getPlayer() == null) {
            context.getSource().sendFailure(Component.literal("Este comando debe ejecutarlo un jugador."));
            return 0;
        }

        var player = context.getSource().getPlayer();
        player.getInventory().add(MinesweeperItems.INTERACCION.getDefaultInstance());
        player.getInventory().add(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance());
        player.getInventory().add(MinesweeperItems.TOTEM_INMORTALIDAD.getDefaultInstance());
        context.getSource().sendSuccess(() -> Component.literal("Recibiste Interacción, Desactivador y Tótem."), false);
        return 1;
    }

    private static int assignRandomTeams(CommandContext<CommandSourceStack> context) {
        List<ServerPlayer> players = new ArrayList<>(context.getSource().getServer().getPlayerList().getPlayers());
        if (players.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No hay jugadores conectados."));
            return 0;
        }

        Collections.shuffle(players);
        ServerScoreboard scoreboard = context.getSource().getServer().getScoreboard();
        List<PlayerTeam> currentTeams = new ArrayList<>(scoreboard.getPlayerTeams());
        for (PlayerTeam team : currentTeams) {
            if (team.getName().startsWith("ms_team_")) {
                scoreboard.removePlayerTeam(team);
            }
        }

        int teamIndex = 1;
        int currentTeamCount = 0;
        PlayerTeam team = scoreboard.addPlayerTeam("ms_team_" + teamIndex);

        for (ServerPlayer player : players) {
            if (currentTeamCount >= 5) {
                teamIndex++;
                currentTeamCount = 0;
                team = scoreboard.addPlayerTeam("ms_team_" + teamIndex);
            }

            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
            currentTeamCount++;
        }

        final int createdTeams = teamIndex;
        context.getSource().sendSuccess(
                () -> Component.literal("Equipos aleatorios creados: " + createdTeams + " equipo(s) de hasta 5 jugadores."),
                true
        );
        return 1;
    }

    private static int startRound(CommandContext<CommandSourceStack> context) {
        MinesweeperGameplay.resetRoundState();
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            player.getInventory().add(MinesweeperItems.INTERACCION.getDefaultInstance());
            player.getInventory().add(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance());
            player.getInventory().add(MinesweeperItems.TOTEM_INMORTALIDAD.getDefaultInstance());
        }

        MinesweeperRoundManager.setActive(context.getSource().getServer(), true);
        context.getSource().sendSuccess(() -> Component.literal("Ronda iniciada: objetos entregados."), true);
        return 1;
    }

    private static int stopRound(CommandContext<CommandSourceStack> context, boolean markAsFinished) {
        MinecraftServer server = context.getSource().getServer();
        boolean wasActive = MinesweeperRoundManager.isActive();
        resetRound(server);

        String msg = markAsFinished
                ? "Ronda terminada. Tableros y jugadores restaurados."
                : "Ronda detenida. Tableros y jugadores restaurados.";
        context.getSource().sendSuccess(() -> Component.literal(msg), true);
        return wasActive ? 1 : 0;
    }

    private static int giveItemsToSourcePlayer(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getPlayer() == null) {
            context.getSource().sendFailure(Component.literal("Debes indicar jugadores si ejecutas el comando desde consola."));
            return 0;
        }
        return giveItems(context, List.of(context.getSource().getPlayer()));
    }

    private static int giveItems(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        if (players.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No hay jugadores para entregar objetos."));
            return 0;
        }

        for (ServerPlayer player : players) {
            giveRoundItems(player);
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Objetos entregados a " + players.size() + " jugador(es)."),
                true
        );
        return players.size();
    }

    private static int assignRandomTeams(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players, int teamSize) {
        int createdTeams = assignRandomTeamsToPlayers(
                context.getSource().getServer(),
                players,
                teamSize,
                "ms_team_",
                false
        );
        if (createdTeams <= 0) {
            context.getSource().sendFailure(Component.literal("No hay jugadores para asignar."));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Equipos aleatorios creados: " + createdTeams
                                + " equipo(s) con tamano maximo de " + teamSize + "."
                ),
                true
        );
        return createdTeams;
    }

    private static int configureDifficulty(CommandContext<CommandSourceStack> context, RoundDifficulty difficulty) {
        RoundSettings settings = MinesweeperRoundManager.configuredSettings().withDifficulty(difficulty);
        MinesweeperRoundManager.setConfiguredSettings(settings);
        context.getSource().sendSuccess(
                () -> Component.literal("Configuracion guardada: " + describeSettings(settings) + "."),
                true
        );
        return 1;
    }

    private static int configureCustomRound(CommandContext<CommandSourceStack> context) {
        RoundSettings settings = customSettingsFromContext(context);
        if (settings == null) {
            return 0;
        }

        MinesweeperRoundManager.setConfiguredSettings(settings);
        context.getSource().sendSuccess(
                () -> Component.literal("Configuracion personalizada guardada: " + describeSettings(settings) + "."),
                true
        );
        return 1;
    }

    private static int startCustomRound(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        RoundSettings settings = customSettingsFromContext(context);
        if (settings == null) {
            return 0;
        }
        return startRound(context, players, settings);
    }

    private static int startRound(
            CommandContext<CommandSourceStack> context,
            Collection<ServerPlayer> players,
            RoundSettings settings
    ) {
        List<ServerPlayer> participants = new ArrayList<>(players);
        if (participants.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No hay jugadores validos para iniciar la ronda."));
            return 0;
        }

        MinecraftServer server = context.getSource().getServer();
        if (MinesweeperRoundManager.isActive()) {
            resetRound(server);
        }

        MinesweeperGameplay.resetRoundState();
        MinesweeperBoardManager.resetAllBoardStates(server);
        MinesweeperRoundManager.startRound(server, participants, settings);

        if (settings.giveItems()) {
            for (ServerPlayer player : participants) {
                giveRoundItems(player);
            }
        }

        MinesweeperSessionManager.StartSessionResult sessionResult = MinesweeperSessionManager.startSession(server);
        if (!sessionResult.success()) {
            context.getSource().sendFailure(Component.literal(sessionResult.message()));
            resetRound(server);
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Ronda iniciada para " + participants.size() + " jugador(es). "
                                + describeSettings(settings) + ". "
                                + sessionResult.message()
                ),
                true
        );
        return participants.size();
    }

    private static Collection<ServerPlayer> resolvePlayers(CommandContext<CommandSourceStack> context, String playersArgumentName) {
        if (playersArgumentName == null) {
            return selectDefaultRoundPlayers(context.getSource().getServer());
        }
        try {
            return EntityArgument.getPlayers(context, playersArgumentName);
        } catch (CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal("No se pudieron resolver los jugadores seleccionados."));
            return List.of();
        }
    }

    private static List<ServerPlayer> selectDefaultRoundPlayers(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.gameMode() == GameType.CREATIVE || player.gameMode() == GameType.SPECTATOR) {
                continue;
            }
            players.add(player);
        }
        return players;
    }

    private static void giveRoundItems(ServerPlayer player) {
        player.getInventory().add(MinesweeperItems.INTERACCION.getDefaultInstance());
        player.getInventory().add(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance());
        player.getInventory().add(MinesweeperItems.TOTEM_INMORTALIDAD.getDefaultInstance());
    }

    private static int assignRandomTeamsToPlayers(
            MinecraftServer server,
            Collection<ServerPlayer> players,
            int teamSize,
            String prefix,
            boolean trackTeams
    ) {
        List<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
        if (shuffledPlayers.isEmpty()) {
            return 0;
        }

        Collections.shuffle(shuffledPlayers);
        ServerScoreboard scoreboard = server.getScoreboard();
        clearTeamsWithPrefix(scoreboard, prefix);

        int teamIndex = 1;
        int currentTeamCount = 0;
        PlayerTeam currentTeam = createTeam(scoreboard, prefix + teamIndex, trackTeams);

        for (ServerPlayer player : shuffledPlayers) {
            if (currentTeamCount >= teamSize) {
                teamIndex++;
                currentTeamCount = 0;
                currentTeam = createTeam(scoreboard, prefix + teamIndex, trackTeams);
            }

            scoreboard.addPlayerToTeam(player.getScoreboardName(), currentTeam);
            currentTeamCount++;
        }

        return teamIndex;
    }

    private static PlayerTeam createTeam(ServerScoreboard scoreboard, String name, boolean trackTeam) {
        PlayerTeam team = scoreboard.addPlayerTeam(name);
        if (trackTeam) {
            MinesweeperRoundManager.registerTemporaryTeam(team);
        }
        return team;
    }

    private static void clearTeamsWithPrefix(ServerScoreboard scoreboard, String prefix) {
        List<PlayerTeam> teams = new ArrayList<>(scoreboard.getPlayerTeams());
        for (PlayerTeam team : teams) {
            if (team.getName().startsWith(prefix)) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    private static void resetRound(MinecraftServer server) {
        MinesweeperSessionManager.stopSession();
        MinesweeperGameplay.resetRoundState();
        MinesweeperBoardManager.resetAllBoardStates(server);
        MinesweeperRoundManager.stopRound(server);
    }

    private static RoundSettings customSettingsFromContext(CommandContext<CommandSourceStack> context) {
        TeamMode teamMode = TeamMode.MANTENER;
        int teamSize = 1;
        boolean giveItems = BoolArgumentType.getBool(context, ARG_GIVE_ITEMS);
        int mineDelayTicks = IntegerArgumentType.getInteger(context, ARG_MINE_DELAY);
        int respawnSeconds = IntegerArgumentType.getInteger(context, ARG_RESPAWN_SECONDS);
        int disarmSeconds = IntegerArgumentType.getInteger(context, ARG_DISARM_SECONDS);
        int disablerCooldownTicks = IntegerArgumentType.getInteger(context, ARG_DISABLER_COOLDOWN);
        int totemCooldownSeconds = IntegerArgumentType.getInteger(context, ARG_TOTEM_SECONDS);

        return RoundSettings.custom(
                "personalizada",
                teamMode,
                teamSize,
                giveItems,
                mineDelayTicks,
                respawnSeconds * 20,
                disarmSeconds * 20,
                disablerCooldownTicks,
                totemCooldownSeconds * 20
        );
    }

    private static String describeSettings(RoundSettings settings) {
        return "dificultad=" + settings.label()
                + ", dar_objetos=" + (settings.giveItems() ? "si" : "no")
                + ", retardo_mina=" + settings.mineTriggerDelayTicks() + "t"
                + ", respawn=" + (settings.respawnWaitTicks() / 20) + "s"
                + ", desactivacion=" + (settings.disarmTimeoutTicks() / 20) + "s"
                + ", cooldown_desactivador=" + settings.disablerCooldownTicks() + "t"
                + ", cooldown_totem=" + (settings.totemCooldownTicks() / 20) + "s"
                + ", tiempo_ronda=" + (settings.roundDurationTicks() / 1200) + "m";
    }

    @FunctionalInterface
    private interface RoundCommandExecutor {
        int run(CommandContext<CommandSourceStack> context);
    }

    private static int testExplosionAnimation(CommandContext<CommandSourceStack> context, int ticks) {
        if (context.getSource().getPlayer() == null || !(context.getSource().getPlayer().level() instanceof ServerLevel level)) {
            context.getSource().sendFailure(Component.literal("Este comando debe ejecutarlo un jugador."));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayer();
        Vec3 spawnPos = player.position().add(player.getLookAngle().scale(2.0D)).add(0.0D, 0.1D, 0.0D);
        int appliedTicks = Math.max(1, Math.min(MineExplosionEntity.ANIMATION_DURATION_TICKS, ticks));
        MineExplosionEntity.spawn(level, spawnPos, appliedTicks);
        context.getSource().sendSuccess(
                () -> Component.literal("Explosion Gecko generada por " + appliedTicks + " ticks."),
                false
        );
        return 1;
    }

    private static BlockPos sourcePosition(CommandContext<CommandSourceStack> context) {
        return BlockPos.containing(context.getSource().getPosition());
    }
}
