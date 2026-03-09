package org.shouto.minesweeper.minesweeper.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import net.minecraft.world.scores.PlayerTeam;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager;
import org.shouto.minesweeper.minesweeper.game.MinesweeperGameplay;
import org.shouto.minesweeper.minesweeper.game.MinesweeperRoundManager;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MinesweeperCommands {
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
                .then(Commands.literal("borrar_tablero")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(MinesweeperCommands::deleteBoard)))
                .then(Commands.literal("dar_objetos")
                        .executes(MinesweeperCommands::giveItems))
                .then(Commands.literal("equipos_aleatorios")
                        .executes(MinesweeperCommands::assignRandomTeams))
                .then(Commands.literal("iniciar_ronda")
                        .executes(MinesweeperCommands::startRound))
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
        int teams = assignRandomTeams(context);
        if (teams <= 0) {
            return teams;
        }

        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            player.getInventory().add(MinesweeperItems.INTERACCION.getDefaultInstance());
            player.getInventory().add(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance());
            player.getInventory().add(MinesweeperItems.TOTEM_INMORTALIDAD.getDefaultInstance());
        }

        MinesweeperRoundManager.setActive(context.getSource().getServer(), true);
        context.getSource().sendSuccess(() -> Component.literal("Ronda iniciada: equipos listos y objetos entregados."), true);
        return 1;
    }

    private static int stopRound(CommandContext<CommandSourceStack> context, boolean markAsFinished) {
        MinesweeperRoundManager.setActive(context.getSource().getServer(), false);
        MinesweeperGameplay.resetRoundState();

        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        }

        String msg = markAsFinished ? "Ronda terminada." : "Ronda detenida.";
        context.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
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
