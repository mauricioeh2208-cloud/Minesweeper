package org.shouto.minesweeper.minesweeper.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.BoardData;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.RevealResult;
import org.shouto.minesweeper.minesweeper.network.payload.PlayerInteractionAnimPayload;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperBlocks;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MinesweeperGameplay {
    private static final int PLAYER_STARTING_LIVES = 3;
    private static final int INTERACTION_HOLD_TICKS = 20;
    private static final int DISARM_HOLD_TICKS = 20;
    private static final int INTERACTION_COOLDOWN_TICKS = 12;
    private static final int PRE_EXPLOSION_TICKS = 8;
    private static final int FLAG_PICKUP_ANIMATION_TICKS = 12;
    private static final int SNAPSHOT_SYNC_INTERVAL_TICKS = 10;
    private static final int EXPLOSION_GECKO_LIFE_TICKS = MineExplosionEntity.ANIMATION_DURATION_TICKS;
    private static final double EXPLOSION_GECKO_HEIGHT_OFFSET = 0.85D;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 4.0D;
    private static final double MINE_EXPLOSION_RADIUS = 4.5D;
    private static final double MINE_EXPLOSION_RADIUS_SQR = MINE_EXPLOSION_RADIUS * MINE_EXPLOSION_RADIUS;
    private static final double MINE_EXPLOSION_VERTICAL_REACH = 4.0D;
    private static final double MINE_EXPLOSION_PUSH = 0.0D;

    private static final Map<UUID, Integer> PLAYER_LIVES = new HashMap<>();
    private static final Map<UUID, Integer> INTERACTION_USE_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, PendingReveal> PENDING_REVEALS = new HashMap<>();
    private static final Map<UUID, PendingHoldDisarm> PENDING_DISARM_HOLDS = new HashMap<>();
    private static final Map<UUID, PendingMineTrigger> PENDING_MINE_TRIGGERS = new HashMap<>();
    private static final Map<BoardCellKey, PendingExplosion> PENDING_EXPLOSIONS = new HashMap<>();
    private static final Map<UUID, BoardCellKey> LAST_LIFE_LOSS_MINE = new HashMap<>();

    private MinesweeperGameplay() {
    }

    public static void initialize() {
        UseBlockCallback.EVENT.register(MinesweeperGameplay::onUseBlock);
        UseItemCallback.EVENT.register(MinesweeperGameplay::onUseItem);
        AttackBlockCallback.EVENT.register(MinesweeperGameplay::onAttackBlock);
        ServerTickEvents.END_WORLD_TICK.register(MinesweeperGameplay::onWorldTick);
        ServerPlayerEvents.AFTER_RESPAWN.register(MinesweeperGameplay::onPlayerRespawn);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(MinesweeperGameplay::allowRoundDamage);
    }

    public static void resetRoundState() {
        PLAYER_LIVES.clear();
        INTERACTION_USE_RELEASE_TICK.clear();
        PENDING_REVEALS.clear();
        PENDING_DISARM_HOLDS.clear();
        PENDING_MINE_TRIGGERS.clear();
        PENDING_EXPLOSIONS.clear();
        LAST_LIFE_LOSS_MINE.clear();
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!MinesweeperRoundManager.isActive() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        MinesweeperRoundManager.ensureParticipant(serverPlayer);
        if (!MinesweeperSessionManager.isGameplayOpen()) {
            return InteractionResult.FAIL;
        }

        ItemStack held = player.getItemInHand(hand);
        if (isCarryingFlag(player) && !held.is(MinesweeperItems.BANDERA)) {
            player.displayClientMessage(Component.literal("Con una bandera no puedes usar otros objetos."), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!MinesweeperRoundManager.isActive()) {
            return InteractionResult.PASS;
        }
        MinesweeperRoundManager.ensureParticipant(serverPlayer);
        if (!MinesweeperSessionManager.isGameplayOpen()) {
            return InteractionResult.FAIL;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.is(MinesweeperItems.INTERACCION)) {
            return handleBoardInteraction(serverPlayer, serverLevel, hand, pos, held);
        }
        if (isCarryingFlag(player) && !held.is(MinesweeperItems.BANDERA)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (!MinesweeperRoundManager.isActive()) {
            return InteractionResult.PASS;
        }
        MinesweeperRoundManager.ensureParticipant(serverPlayer);
        if (!MinesweeperSessionManager.isGameplayOpen()) {
            serverPlayer.displayClientMessage(Component.literal("La partida aun no esta habilitada para jugar."), true);
            return InteractionResult.FAIL;
        }

        ItemStack held = player.getItemInHand(hand);
        BlockPos clickedPos = hit.getBlockPos();
        Block clickedBlock = level.getBlockState(clickedPos).getBlock();

        if (clickedBlock == MinesweeperBlocks.FLAG_CRATE_BLOCK) {
            playInteractionAnimation(serverPlayer, level.getServer().getTickCount(), hand, clickedPos, PlayerInteractionAnimation.FLAG_PICKUP);
            ItemStack flagStack = MinesweeperItems.BANDERA.getDefaultInstance();
            if (player.getItemInHand(hand).isEmpty()) {
                player.setItemInHand(hand, flagStack);
            } else {
                player.getInventory().add(flagStack);
            }
            player.displayClientMessage(Component.literal("Recogiste una bandera."), true);
            return InteractionResult.SUCCESS;
        }

        if (clickedBlock == MinesweeperBlocks.FLAG_BLOCK) {
            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoardAt(serverLevel, clickedPos.below());
            if (boardOptional.isPresent()) {
                BoardData board = boardOptional.get();
                if (MinesweeperBoardManager.removeFlag(serverLevel, board, clickedPos.below())) {
                    player.getInventory().add(MinesweeperItems.BANDERA.getDefaultInstance());
                    MinesweeperBoardSync.broadcastBoardSnapshot(serverLevel, board);
                }
            } else {
                serverLevel.setBlock(clickedPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                player.getInventory().add(MinesweeperItems.BANDERA.getDefaultInstance());
            }
            return InteractionResult.SUCCESS;
        }

        if (isCarryingFlag(player) && !held.is(MinesweeperItems.BANDERA)) {
            player.displayClientMessage(Component.literal("Con una bandera no puedes usar otros objetos."), true);
            return InteractionResult.FAIL;
        }

        return handleBoardInteraction(serverPlayer, serverLevel, hand, clickedPos, held);
    }

    private static InteractionResult handleBoardInteraction(
            ServerPlayer player,
            ServerLevel level,
            InteractionHand hand,
            BlockPos clickedPos,
            ItemStack held
    ) {
        if (player.isSpectator()) {
            return InteractionResult.FAIL;
        }

        Optional<BoardCellTarget> targetOptional = resolveBoardCell(level, clickedPos);
        if (targetOptional.isEmpty()) {
            return InteractionResult.PASS;
        }

        BoardCellTarget target = targetOptional.get();
        BlockPos cellPos = target.cellPos();
        BoardData board = target.board();

        if (!isWithinInteractionRange(player, cellPos)) {
            player.displayClientMessage(Component.literal("Rango maximo: 2 bloques."), true);
            return InteractionResult.FAIL;
        }

        if (held.is(MinesweeperItems.DESACTIVADOR_MINA)) {
            return useDisabler(player, level, board, cellPos, held, hand);
        }
        if (held.is(MinesweeperItems.INTERACCION)) {
            return startRevealProgress(player, level, board, cellPos, held, hand);
        }
        if (held.is(MinesweeperItems.BANDERA)) {
            if (MinesweeperBoardManager.placeFlag(level, board, cellPos)) {
                held.shrink(1);
                MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.FAIL;
        }

        return InteractionResult.PASS;
    }

    private static InteractionResult useDisabler(
            ServerPlayer player,
            ServerLevel level,
            BoardData board,
            BlockPos cellPos,
            ItemStack held,
            InteractionHand hand
    ) {
        if (player.getCooldowns().isOnCooldown(held)) {
            return InteractionResult.FAIL;
        }

        if (!MinesweeperBoardManager.isMine(board, cellPos) || MinesweeperBoardManager.isResolvedMine(board, cellPos)) {
            player.displayClientMessage(Component.literal("no se encontro minas"), true);
            player.getCooldowns().addCooldown(held, disablerCooldownTicks());
            return InteractionResult.SUCCESS;
        }

        PendingHoldDisarm current = PENDING_DISARM_HOLDS.get(player.getUUID());
        if (current != null
                && current.boardId() == board.boardId()
                && current.cellPos().equals(cellPos)
                && current.hand() == hand) {
            return InteractionResult.SUCCESS;
        }

        int tick = level.getServer().getTickCount();
        playInteractionAnimation(player, tick, hand, cellPos, PlayerInteractionAnimation.INTERACT);
        PENDING_DISARM_HOLDS.put(
                player.getUUID(),
                new PendingHoldDisarm(board.boardId(), cellPos.immutable(), hand, tick + DISARM_HOLD_TICKS)
        );
        sendDisarmProgress(player, tick, tick + DISARM_HOLD_TICKS);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult startRevealProgress(
            ServerPlayer player,
            ServerLevel level,
            BoardData board,
            BlockPos cellPos,
            ItemStack held,
            InteractionHand hand
    ) {
        if (player.getCooldowns().isOnCooldown(held)) {
            return InteractionResult.FAIL;
        }
        if (MinesweeperBoardManager.isFlagged(board, cellPos)) {
            player.displayClientMessage(Component.literal("Quita la bandera antes de descubrir esa casilla."), true);
            return InteractionResult.FAIL;
        }
        if (MinesweeperBoardManager.isRevealed(board, cellPos) || MinesweeperBoardManager.isResolvedMine(board, cellPos)) {
            return InteractionResult.FAIL;
        }

        PendingReveal current = PENDING_REVEALS.get(player.getUUID());
        if (current != null
                && current.boardId() == board.boardId()
                && current.cellPos().equals(cellPos)
                && current.hand() == hand) {
            return InteractionResult.SUCCESS;
        }

        int tick = level.getServer().getTickCount();
        playInteractionAnimation(player, tick, hand, cellPos, PlayerInteractionAnimation.INTERACT);
        PENDING_REVEALS.put(player.getUUID(), new PendingReveal(board.boardId(), cellPos.immutable(), hand, tick + INTERACTION_HOLD_TICKS));
        sendRevealProgress(player, tick, tick + INTERACTION_HOLD_TICKS);
        return InteractionResult.SUCCESS;
    }

    private static void onWorldTick(ServerLevel level) {
        if (!MinesweeperRoundManager.isActive()) {
            return;
        }

        int tick = level.getServer().getTickCount();
        for (ServerPlayer player : level.players()) {
            MinesweeperRoundManager.ensureParticipant(player);
        }
        MinesweeperSessionManager.tick(level.getServer(), tick);
        if (!MinesweeperRoundManager.isActive()) {
            return;
        }

        processPendingReveals(level, tick);
        processPendingDisarms(level, tick);
        processPendingMineTriggers(level, tick);
        processPendingExplosions(level, tick);

        if (tick % SNAPSHOT_SYNC_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (MinesweeperRoundManager.isParticipant(player)) {
                    syncPlayerLifeState(player);
                    MinesweeperBoardSync.sendBestSnapshot(player);
                }
            }
        }

        if (!MinesweeperSessionManager.isGameplayOpen()) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (!MinesweeperRoundManager.isParticipant(player)) {
                continue;
            }

            releaseInteractionUseIfReady(player, tick);
            syncPlayerLifeState(player);
            if (player.isSpectator()) {
                continue;
            }

            BlockPos below = player.blockPosition().below();
            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoardAt(level, below);
            if (boardOptional.isEmpty()) {
                continue;
            }

            BoardData board = boardOptional.get();
            if (!MinesweeperBoardManager.isMine(board, below) || MinesweeperBoardManager.isResolvedMine(board, below)) {
                continue;
            }

            armMineUnderPlayer(level, board, below, player, tick);
        }
    }

    private static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (!MinesweeperRoundManager.isActive()) {
            return;
        }

        MinesweeperRoundManager.ensureParticipant(newPlayer);
        if (MinesweeperSessionManager.shouldForceSpectator(newPlayer)) {
            newPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            return;
        }

        applyLifeHealth(newPlayer, PLAYER_LIVES.getOrDefault(newPlayer.getUUID(), PLAYER_STARTING_LIVES));
        syncPlayerLifeState(newPlayer);
        newPlayer.setCamera(newPlayer);
    }

    private static boolean allowRoundDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!MinesweeperRoundManager.isActive() || !(entity instanceof ServerPlayer player)) {
            return true;
        }

        return !MinesweeperRoundManager.isParticipant(player);
    }

    private static void processPendingReveals(ServerLevel level, int tick) {
        List<Map.Entry<UUID, PendingReveal>> snapshot = new ArrayList<>(PENDING_REVEALS.entrySet());
        for (Map.Entry<UUID, PendingReveal> entry : snapshot) {
            UUID playerId = entry.getKey();
            PendingReveal pending = entry.getValue();
            if (PENDING_REVEALS.get(playerId) != pending) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                PENDING_REVEALS.remove(playerId, pending);
                continue;
            }
            if (player.level() != level) {
                continue;
            }
            if (player.isSpectator() || !MinesweeperRoundManager.isParticipant(player)) {
                PENDING_REVEALS.remove(playerId, pending);
                continue;
            }

            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(pending.boardId());
            if (boardOptional.isEmpty()) {
                PENDING_REVEALS.remove(playerId, pending);
                continue;
            }

            BoardData board = boardOptional.get();
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }
            if (!player.getItemInHand(pending.hand()).is(MinesweeperItems.INTERACCION)
                    || !isWithinInteractionRange(player, pending.cellPos())) {
                PENDING_REVEALS.remove(playerId, pending);
                player.stopUsingItem();
                continue;
            }

            if (tick < pending.finishTick()) {
                sendRevealProgress(player, tick, pending.finishTick());
                continue;
            }

            PENDING_REVEALS.remove(playerId, pending);
            player.stopUsingItem();
            player.getCooldowns().addCooldown(MinesweeperItems.INTERACCION.getDefaultInstance(), INTERACTION_COOLDOWN_TICKS);

            RevealResult result = MinesweeperBoardManager.reveal(level, board, pending.cellPos());
            if (result == RevealResult.MINE) {
                queueMineExplosion(level, board, pending.cellPos(), tick);
            } else if (result == RevealResult.NUMBER) {
                int adjacent = MinesweeperBoardManager.getAdjacentMines(board, pending.cellPos());
                player.displayClientMessage(Component.literal("Casilla revelada: " + Math.min(5, adjacent)), true);
            }

            MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
            checkCompletion(level, board);
        }
    }

    private static void processPendingDisarms(ServerLevel level, int tick) {
        List<Map.Entry<UUID, PendingHoldDisarm>> snapshot = new ArrayList<>(PENDING_DISARM_HOLDS.entrySet());
        for (Map.Entry<UUID, PendingHoldDisarm> entry : snapshot) {
            UUID playerId = entry.getKey();
            PendingHoldDisarm pending = entry.getValue();
            if (PENDING_DISARM_HOLDS.get(playerId) != pending) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                PENDING_DISARM_HOLDS.remove(playerId, pending);
                continue;
            }
            if (player.level() != level) {
                continue;
            }
            if (player.isSpectator() || !MinesweeperRoundManager.isParticipant(player)) {
                PENDING_DISARM_HOLDS.remove(playerId, pending);
                continue;
            }

            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(pending.boardId());
            if (boardOptional.isEmpty()) {
                PENDING_DISARM_HOLDS.remove(playerId, pending);
                continue;
            }

            BoardData board = boardOptional.get();
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }
            if (!player.getItemInHand(pending.hand()).is(MinesweeperItems.DESACTIVADOR_MINA)
                    || !isWithinInteractionRange(player, pending.cellPos())) {
                PENDING_DISARM_HOLDS.remove(playerId, pending);
                player.stopUsingItem();
                continue;
            }

            if (tick < pending.finishTick()) {
                sendDisarmProgress(player, tick, pending.finishTick());
                continue;
            }

            PENDING_DISARM_HOLDS.remove(playerId, pending);
            player.stopUsingItem();

            cancelPendingExplosion(board.boardId(), pending.cellPos());
            if (MinesweeperBoardManager.disarmMine(level, board, pending.cellPos())) {
                player.displayClientMessage(Component.literal("Mina desactivada."), true);
                checkCompletion(level, board);
            } else {
                player.displayClientMessage(Component.literal("La mina ya fue resuelta."), true);
            }

            player.getCooldowns().addCooldown(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance(), disablerCooldownTicks());
            MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
        }
    }

    private static void processPendingMineTriggers(ServerLevel level, int tick) {
        List<Map.Entry<UUID, PendingMineTrigger>> snapshot = new ArrayList<>(PENDING_MINE_TRIGGERS.entrySet());
        for (Map.Entry<UUID, PendingMineTrigger> entry : snapshot) {
            UUID playerId = entry.getKey();
            PendingMineTrigger pending = entry.getValue();
            if (PENDING_MINE_TRIGGERS.get(playerId) != pending || tick < pending.triggerTick()) {
                continue;
            }

            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(pending.boardId());
            if (boardOptional.isEmpty()) {
                PENDING_MINE_TRIGGERS.remove(playerId, pending);
                continue;
            }

            BoardData board = boardOptional.get();
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }

            PENDING_MINE_TRIGGERS.remove(playerId, pending);
            if (!MinesweeperBoardManager.isResolvedMine(board, pending.minePos())) {
                queueMineExplosion(level, board, pending.minePos(), tick);
            }
        }
    }

    private static void processPendingExplosions(ServerLevel level, int tick) {
        List<Map.Entry<BoardCellKey, PendingExplosion>> snapshot = new ArrayList<>(PENDING_EXPLOSIONS.entrySet());
        for (Map.Entry<BoardCellKey, PendingExplosion> entry : snapshot) {
            PendingExplosion pending = entry.getValue();
            if (PENDING_EXPLOSIONS.get(entry.getKey()) != pending || tick < pending.explodeTick()) {
                continue;
            }

            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(pending.boardId());
            if (boardOptional.isEmpty()) {
                PENDING_EXPLOSIONS.remove(entry.getKey(), pending);
                continue;
            }

            BoardData board = boardOptional.get();
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }
            if (MinesweeperBoardManager.isResolvedMine(board, pending.minePos())) {
                PENDING_EXPLOSIONS.remove(entry.getKey(), pending);
                continue;
            }

            PENDING_EXPLOSIONS.remove(entry.getKey(), pending);
            triggerMine(level, board, pending.minePos());
        }
    }

    private static void queueMineExplosion(ServerLevel level, BoardData board, BlockPos minePos, int tick) {
        if (!MinesweeperBoardManager.isMine(board, minePos) || MinesweeperBoardManager.isResolvedMine(board, minePos)) {
            return;
        }

        BoardCellKey key = new BoardCellKey(board.boardId(), minePos.immutable());
        if (PENDING_EXPLOSIONS.containsKey(key)) {
            return;
        }

        MinesweeperBoardManager.showMineAsActive(level, board, minePos);
        PENDING_EXPLOSIONS.put(key, new PendingExplosion(board.boardId(), minePos.immutable(), tick + PRE_EXPLOSION_TICKS));
    }

    private static void cancelPendingExplosion(int boardId, BlockPos minePos) {
        PENDING_EXPLOSIONS.remove(new BoardCellKey(boardId, minePos.immutable()));
    }

    private static void armMineUnderPlayer(ServerLevel level, BoardData board, BlockPos minePos, ServerPlayer player, int tick) {
        if (MinesweeperBoardManager.isRevealed(board, minePos) || MinesweeperBoardManager.isResolvedMine(board, minePos)) {
            return;
        }

        PendingMineTrigger current = PENDING_MINE_TRIGGERS.get(player.getUUID());
        if (current != null && current.boardId() == board.boardId() && current.minePos().equals(minePos)) {
            return;
        }

        int triggerDelayTicks = Math.max(1, mineTriggerDelayTicks());
        PENDING_MINE_TRIGGERS.put(player.getUUID(), new PendingMineTrigger(board.boardId(), minePos.immutable(), tick + triggerDelayTicks));
        MinesweeperBoardManager.reveal(level, board, minePos);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
        player.displayClientMessage(Component.literal("Mina activada..."), true);
    }

    private static void playInteractionAnimation(
            ServerPlayer player,
            int tick,
            InteractionHand hand,
            BlockPos targetPos,
            PlayerInteractionAnimation animationKind
    ) {
        player.startUsingItem(hand);
        player.swing(hand, true);
        if (player.level() instanceof ServerLevel serverLevel) {
            broadcastInteractionAnimation(serverLevel, player, animationKind);
        }
        INTERACTION_USE_RELEASE_TICK.put(player.getUUID(), tick + animationKind.durationTicks());
    }

    private static void broadcastInteractionAnimation(
            ServerLevel level,
            ServerPlayer source,
            PlayerInteractionAnimation animationKind
    ) {
        PlayerInteractionAnimPayload payload = new PlayerInteractionAnimPayload(
                source.getId(),
                animationKind.networkId(),
                animationKind.durationTicks()
        );
        for (ServerPlayer target : level.players()) {
            if (MinesweeperRoundManager.isParticipant(target)) {
                ServerPlayNetworking.send(target, payload);
            }
        }
    }

    private static void releaseInteractionUseIfReady(ServerPlayer player, int tick) {
        Integer stopUseTick = INTERACTION_USE_RELEASE_TICK.get(player.getUUID());
        if (stopUseTick != null
                && tick >= stopUseTick
                && !PENDING_REVEALS.containsKey(player.getUUID())
                && !PENDING_DISARM_HOLDS.containsKey(player.getUUID())) {
            player.stopUsingItem();
            INTERACTION_USE_RELEASE_TICK.remove(player.getUUID());
        }
    }

    private static void triggerMine(ServerLevel level, BoardData board, BlockPos minePos) {
        if (!MinesweeperBoardManager.explodeMine(level, board, minePos)) {
            return;
        }

        BoardCellKey mineKey = new BoardCellKey(board.boardId(), minePos.immutable());
        double centerX = minePos.getX() + 0.5D;
        double centerY = minePos.getY() + 0.5D + EXPLOSION_GECKO_HEIGHT_OFFSET;
        double centerZ = minePos.getZ() + 0.5D;

        MineExplosionEntity.spawn(level, minePos.getCenter().add(0.0D, EXPLOSION_GECKO_HEIGHT_OFFSET, 0.0D), EXPLOSION_GECKO_LIFE_TICKS);
        level.playSound(null, centerX, centerY, centerZ, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 1.0F);
        spawnExplosionBurst(level, centerX, centerY, centerZ);

        for (ServerPlayer other : level.players()) {
            if (other.isSpectator() || !MinesweeperRoundManager.isParticipant(other)) {
                continue;
            }
            if (mineKey.equals(LAST_LIFE_LOSS_MINE.get(other.getUUID()))) {
                continue;
            }

            double deltaX = other.getX() - centerX;
            double deltaZ = other.getZ() - centerZ;
            double horizontalDistanceSqr = (deltaX * deltaX) + (deltaZ * deltaZ);
            if (horizontalDistanceSqr > MINE_EXPLOSION_RADIUS_SQR || Math.abs(other.getY() - centerY) > MINE_EXPLOSION_VERTICAL_REACH) {
                continue;
            }

            double distance = Math.sqrt(horizontalDistanceSqr);
            double blastStrength = Math.max(0.0D, 1.0D - (distance / MINE_EXPLOSION_RADIUS));
            if (blastStrength > 0.0D) {
                double pushX = distance < 1.0E-4D ? 0.0D : (deltaX / distance) * (MINE_EXPLOSION_PUSH * blastStrength);
                double pushZ = distance < 1.0E-4D ? 0.0D : (deltaZ / distance) * (MINE_EXPLOSION_PUSH * blastStrength);
                other.push(pushX, 0.14D + (blastStrength * 0.24D), pushZ);
            }

            consumeLife(level, other, mineKey);
        }

        checkCompletion(level, board);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
    }

    private static void consumeLife(ServerLevel level, ServerPlayer player, BoardCellKey mineKey) {
        int remaining = Math.max(0, PLAYER_LIVES.getOrDefault(player.getUUID(), PLAYER_STARTING_LIVES) - 1);
        PLAYER_LIVES.put(player.getUUID(), remaining);
        LAST_LIFE_LOSS_MINE.put(player.getUUID(), mineKey);
        applyLifeHealth(player, remaining);
        syncPlayerLifeState(player);
        PENDING_REVEALS.remove(player.getUUID());
        PENDING_MINE_TRIGGERS.remove(player.getUUID());
        PENDING_DISARM_HOLDS.remove(player.getUUID());
        player.stopUsingItem();

        if (remaining > 0) {
            player.displayClientMessage(Component.literal("Perdiste una vida. Te quedan " + remaining + "."), true);
            return;
        }

        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        player.displayClientMessage(Component.literal("Sin vidas. Ahora spectearas el resto de la ronda."), false);
        MinesweeperSessionManager.handleTeamEliminated(level.getServer(), MinesweeperSessionManager.teamKeyForPlayer(player));
    }

    private static void syncPlayerLifeState(ServerPlayer player) {
        if (!MinesweeperRoundManager.isActive() || !MinesweeperRoundManager.isParticipant(player)) {
            return;
        }

        int lives = PLAYER_LIVES.computeIfAbsent(player.getUUID(), ignored -> PLAYER_STARTING_LIVES);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        double targetMaxHealth = targetMaxHealthForLives(lives);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - targetMaxHealth) > 0.01D) {
            maxHealth.setBaseValue(targetMaxHealth);
        }
        player.setInvulnerable(true);
        if (player.getFoodData().getFoodLevel() != 17) {
            player.getFoodData().setFoodLevel(17);
        }
        if (Math.abs(player.getFoodData().getSaturationLevel()) > 0.01F) {
            player.getFoodData().setSaturation(0.0F);
        }

        if (lives > 0 && player.gameMode() == net.minecraft.world.level.GameType.SPECTATOR) {
            player.setGameMode(MinesweeperRoundManager.participantGameMode(player.getUUID()));
        }
        float targetHealth = targetHealthForLives(lives);
        if (Math.abs(player.getHealth() - targetHealth) > 0.01F) {
            player.setHealth(targetHealth);
        }
    }

    private static void applyLifeHealth(ServerPlayer player, int lives) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        double targetMaxHealth = targetMaxHealthForLives(lives);
        if (maxHealth != null && Math.abs(maxHealth.getBaseValue() - targetMaxHealth) > 0.01D) {
            maxHealth.setBaseValue(targetMaxHealth);
        }

        float targetHealth = targetHealthForLives(lives);
        if (Math.abs(player.getHealth() - targetHealth) > 0.01F) {
            player.setHealth(targetHealth);
        }
    }

    private static double targetMaxHealthForLives(int lives) {
        return Math.max(2.0D, Math.min(PLAYER_STARTING_LIVES * 2.0D, lives * 2.0D));
    }

    private static float targetHealthForLives(int lives) {
        return (float) (lives > 0 ? targetMaxHealthForLives(lives) : 1.0D);
    }

    private static void spawnExplosionBurst(ServerLevel level, double centerX, double centerY, double centerZ) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centerX, centerY - 0.35D, centerZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, centerX, centerY - 0.2D, centerZ, 8, 0.18D, 0.10D, 0.18D, 0.01D);
        level.sendParticles(ParticleTypes.POOF, centerX, centerY - 0.15D, centerZ, 10, 0.22D, 0.10D, 0.22D, 0.03D);
        level.sendParticles(ParticleTypes.FLAME, centerX, centerY - 0.2D, centerZ, 8, 0.16D, 0.08D, 0.16D, 0.01D);
        level.sendParticles(ParticleTypes.SMOKE, centerX, centerY - 0.1D, centerZ, 8, 0.20D, 0.10D, 0.20D, 0.01D);
    }

    private static void sendRevealProgress(ServerPlayer player, int tick, int finishTick) {
        int remaining = Math.max(0, finishTick - tick);
        int progress = Math.max(0, INTERACTION_HOLD_TICKS - remaining);
        int filled = Math.min(10, (progress * 10) / INTERACTION_HOLD_TICKS);
        String bar = "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
        player.displayClientMessage(Component.literal("Descubriendo " + bar), true);
    }

    private static void sendDisarmProgress(ServerPlayer player, int tick, int finishTick) {
        int remaining = Math.max(0, finishTick - tick);
        int progress = Math.max(0, DISARM_HOLD_TICKS - remaining);
        int filled = Math.min(10, (progress * 10) / DISARM_HOLD_TICKS);
        String bar = "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
        player.displayClientMessage(Component.literal("Desactivando " + bar), true);
    }

    private static Optional<BoardCellTarget> resolveBoardCell(ServerLevel level, BlockPos clickedPos) {
        Optional<BoardData> direct = MinesweeperBoardManager.getBoardAt(level, clickedPos);
        if (direct.isPresent()) {
            return Optional.of(new BoardCellTarget(direct.get(), clickedPos.immutable()));
        }

        Optional<BoardData> below = MinesweeperBoardManager.getBoardAt(level, clickedPos.below());
        if (below.isPresent()) {
            return Optional.of(new BoardCellTarget(below.get(), clickedPos.below().immutable()));
        }

        return Optional.empty();
    }

    private static boolean isWithinInteractionRange(Player player, BlockPos cellPos) {
        return player.distanceToSqr(
                cellPos.getX() + 0.5D,
                cellPos.getY() + 0.5D,
                cellPos.getZ() + 0.5D
        ) <= MAX_INTERACTION_DISTANCE_SQR;
    }

    private static boolean isCarryingFlag(Player player) {
        return player.getInventory().contains(stack -> stack.is(MinesweeperItems.BANDERA));
    }

    private static void checkCompletion(ServerLevel level, BoardData board) {
        if (!MinesweeperBoardManager.isCompleted(board)) {
            return;
        }
        if (MinesweeperSessionManager.handleBoardCompletion(level, board)) {
            return;
        }

        Component message = Component.literal("Tablero #" + board.boardId() + " completado.");
        for (ServerPlayer player : level.players()) {
            if (MinesweeperRoundManager.isParticipant(player)) {
                player.displayClientMessage(message, false);
            }
        }
    }

    private static int mineTriggerDelayTicks() {
        return MinesweeperRoundManager.activeSettings().mineTriggerDelayTicks();
    }

    private static int disablerCooldownTicks() {
        return MinesweeperRoundManager.activeSettings().disablerCooldownTicks();
    }

    private enum PlayerInteractionAnimation {
        INTERACT((byte) 0, INTERACTION_HOLD_TICKS),
        FLAG_PICKUP((byte) 1, FLAG_PICKUP_ANIMATION_TICKS);

        private final byte networkId;
        private final int durationTicks;

        PlayerInteractionAnimation(byte networkId, int durationTicks) {
            this.networkId = networkId;
            this.durationTicks = durationTicks;
        }

        private byte networkId() {
            return networkId;
        }

        private int durationTicks() {
            return durationTicks;
        }
    }

    private record BoardCellTarget(BoardData board, BlockPos cellPos) {
    }

    private record PendingReveal(int boardId, BlockPos cellPos, InteractionHand hand, int finishTick) {
    }

    private record PendingHoldDisarm(int boardId, BlockPos cellPos, InteractionHand hand, int finishTick) {
    }

    private record PendingMineTrigger(int boardId, BlockPos minePos, int triggerTick) {
    }

    private record PendingExplosion(int boardId, BlockPos minePos, int explodeTick) {
    }

    private record BoardCellKey(int boardId, BlockPos minePos) {
    }
}
