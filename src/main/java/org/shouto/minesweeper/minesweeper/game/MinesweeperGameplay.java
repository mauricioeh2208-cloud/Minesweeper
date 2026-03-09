package org.shouto.minesweeper.minesweeper.game;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.BoardData;
import org.shouto.minesweeper.minesweeper.game.MinesweeperBoardManager.RevealResult;
import org.shouto.minesweeper.minesweeper.network.payload.DisarmResultPayload;
import org.shouto.minesweeper.minesweeper.network.payload.OpenDisarmPayload;
import org.shouto.minesweeper.minesweeper.network.payload.PlayerInteractionAnimPayload;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperBlocks;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MinesweeperGameplay {
    private static final int INTERACCION_COOLDOWN_TICKS = 12;
    private static final int DESACTIVADOR_COOLDOWN_TICKS = 80;
    private static final int TOTEM_COOLDOWN_TICKS = 20 * 45;
    private static final int INTERACTION_ANIMATION_TICKS = 8;
    private static final int FLAG_PICKUP_ANIMATION_TICKS = 12;
    private static final int STEP_MINE_TRIGGER_DELAY_TICKS = 8;
    private static final int RESPAWN_WAIT_TICKS = 20 * 8;
    private static final int DISARM_CHALLENGE_TIMEOUT_TICKS = 20 * 10;
    private static final int SNAPSHOT_SYNC_INTERVAL_TICKS = 10;
    private static final int INACTIVE_MINE_EFFECT_COOLDOWN_TICKS = 14;
    private static final int EXPLOSION_GECKO_LIFE_TICKS = MineExplosionEntity.ANIMATION_DURATION_TICKS;
    private static final double EXPLOSION_GECKO_HEIGHT_OFFSET = 0.9D;
    private static final double MAX_INTERACTION_DISTANCE_SQR = 2.25D;

    private static final Map<UUID, Integer> TOTEM_READY_TICK = new HashMap<>();
    private static final Map<UUID, Integer> RESPAWN_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, Integer> INTERACTION_POSE_RELEASE_TICK = new HashMap<>();
    private static final Map<UUID, Integer> INTERACTION_USE_RELEASE_TICK = new HashMap<>();
    private static final Set<String> ELIMINATED_TEAMS = new HashSet<>();
    private static final Map<UUID, PendingDisarm> PENDING_DISARMS = new HashMap<>();
    private static final Map<UUID, PendingMineTrigger> PENDING_MINE_TRIGGERS = new HashMap<>();
    private static final Map<Long, Integer> LAST_INACTIVE_MINE_EFFECT_TICK = new HashMap<>();

    private MinesweeperGameplay() {
    }

    public static void initialize() {
        UseBlockCallback.EVENT.register(MinesweeperGameplay::onUseBlock);
        UseItemCallback.EVENT.register(MinesweeperGameplay::onUseItem);
        AttackBlockCallback.EVENT.register(MinesweeperGameplay::onAttackBlock);
        ServerTickEvents.END_WORLD_TICK.register(MinesweeperGameplay::onWorldTick);

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            int tick = newPlayer.level().getServer() != null ? newPlayer.level().getServer().getTickCount() : 0;
            PlayerTeam team = newPlayer.getTeam();
            if (team != null && ELIMINATED_TEAMS.contains(team.getName())) {
                newPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                return;
            }

            RESPAWN_RELEASE_TICK.put(newPlayer.getUUID(), tick + RESPAWN_WAIT_TICKS);
            PENDING_MINE_TRIGGERS.remove(newPlayer.getUUID());
            newPlayer.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            newPlayer.displayClientMessage(Component.literal("Respawn bloqueado por 8 segundos."), false);
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }
            if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
                return true;
            }

            int tick = player.level().getServer() != null ? player.level().getServer().getTickCount() : 0;
            if (canUseTotem(player, tick)) {
                consumeTotemCooldown(player, tick);
                player.setHealth(1.0F);
                player.animateHurt(0.0F);
                player.displayClientMessage(Component.literal("Totem de inmortalidad activado."), true);
                return false;
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                PENDING_MINE_TRIGGERS.remove(player.getUUID());
                evaluateTeamElimination(player);
            }
        });
    }

    public static void resetRoundState() {
        RESPAWN_RELEASE_TICK.clear();
        INTERACTION_POSE_RELEASE_TICK.clear();
        INTERACTION_USE_RELEASE_TICK.clear();
        ELIMINATED_TEAMS.clear();
        PENDING_DISARMS.clear();
        PENDING_MINE_TRIGGERS.clear();
        LAST_INACTIVE_MINE_EFFECT_TICK.clear();
    }

    public static void handleDisarmResult(ServerPlayer player, DisarmResultPayload payload) {
        if (!MinesweeperRoundManager.isActive()) {
            return;
        }

        PendingDisarm pending = PENDING_DISARMS.remove(player.getUUID());
        if (pending == null) {
            return;
        }
        if (pending.token() != payload.token() || pending.boardId() != payload.boardId() || !pending.cellPos().equals(payload.cellPos())) {
            return;
        }

        Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(payload.boardId());
        if (boardOptional.isEmpty()) {
            return;
        }

        BoardData board = boardOptional.get();
        if (!board.dimension().equals(player.level().dimension()) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (payload.success()) {
            if (MinesweeperBoardManager.disarmMine(level, board, payload.cellPos())) {
                player.displayClientMessage(Component.literal("Mina desactivada."), true);
                checkCompletion(level, board);
            }
        } else {
            MinesweeperBoardManager.reveal(level, board, payload.cellPos());
            triggerMine(level, board, payload.cellPos());
        }

        player.getCooldowns().addCooldown(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance(), DESACTIVADOR_COOLDOWN_TICKS);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
    }

    private static InteractionResult onUseItem(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (isCarryingFlag(player) && !held.is(MinesweeperItems.BANDERA)) {
            player.displayClientMessage(Component.literal("Con una bandera no puedes usar otros objetos."), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult onAttackBlock(Player player, Level level, InteractionHand hand, BlockPos pos, net.minecraft.core.Direction direction) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
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

        ItemStack held = player.getItemInHand(hand);
        BlockPos clickedPos = hit.getBlockPos();
        Block clickedBlock = level.getBlockState(clickedPos).getBlock();

        if (clickedBlock == MinesweeperBlocks.FLAG_CRATE_BLOCK) {
            playInteractionAnimation(
                    serverPlayer,
                    level.getServer().getTickCount(),
                    hand,
                    clickedPos,
                    PlayerInteractionAnimation.FLAG_PICKUP
            );
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
                    checkCompletion(serverLevel, board);
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

        Optional<BoardCellTarget> targetOptional = resolveBoardCell(serverLevel, clickedPos);
        if (targetOptional.isEmpty()) {
            return InteractionResult.PASS;
        }

        BoardCellTarget target = targetOptional.get();
        BlockPos cellPos = target.cellPos();
        BoardData board = target.board();

        if (!isWithinInteractionRange(player, cellPos)) {
            player.displayClientMessage(Component.literal("Rango maximo: 1 bloque."), true);
            return InteractionResult.FAIL;
        }

        if (held.is(MinesweeperItems.DESACTIVADOR_MINA)) {
            return useDisabler(serverPlayer, serverLevel, board, cellPos, held);
        }

        if (held.is(MinesweeperItems.INTERACCION)) {
            return useInteractionTool(serverPlayer, serverLevel, board, cellPos, held, hand);
        }

        if (held.is(MinesweeperItems.BANDERA)) {
            if (MinesweeperBoardManager.placeFlag(serverLevel, board, cellPos)) {
                held.shrink(1);
                checkCompletion(serverLevel, board);
                MinesweeperBoardSync.broadcastBoardSnapshot(serverLevel, board);
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
            ItemStack held
    ) {
        if (player.getCooldowns().isOnCooldown(held)) {
            return InteractionResult.FAIL;
        }

        if (MinesweeperBoardManager.isMine(board, cellPos) && !MinesweeperBoardManager.isDisarmed(board, cellPos)) {
            int tick = level.getServer().getTickCount();
            int token = Math.floorMod((tick * 37) ^ cellPos.hashCode() ^ board.boardId(), Integer.MAX_VALUE);

            PENDING_DISARMS.put(player.getUUID(), new PendingDisarm(board.boardId(), cellPos.immutable(), token, tick + DISARM_CHALLENGE_TIMEOUT_TICKS));
            ServerPlayNetworking.send(player, new OpenDisarmPayload(board.boardId(), cellPos.immutable(), token));
            player.displayClientMessage(Component.literal("Resuelve el minijuego para desactivar la mina."), true);
            return InteractionResult.SUCCESS;
        }

        RevealResult result = MinesweeperBoardManager.reveal(level, board, cellPos);
        if (result == RevealResult.NUMBER) {
            int adjacent = MinesweeperBoardManager.getAdjacentMines(board, cellPos);
            player.displayClientMessage(Component.literal("Casilla revelada: " + Math.min(5, adjacent)), true);
        } else if (result == RevealResult.MINE && !MinesweeperBoardManager.isDisarmed(board, cellPos)) {
            triggerMine(level, board, cellPos);
        }

        player.getCooldowns().addCooldown(held, DESACTIVADOR_COOLDOWN_TICKS);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult useInteractionTool(
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

        playInteractionAnimation(
                player,
                level.getServer().getTickCount(),
                hand,
                cellPos,
                PlayerInteractionAnimation.INTERACT
        );

        RevealResult result = MinesweeperBoardManager.reveal(level, board, cellPos);
        if (result == RevealResult.MINE && !MinesweeperBoardManager.isDisarmed(board, cellPos)) {
            triggerMine(level, board, cellPos);
        } else if (result == RevealResult.NUMBER) {
            int adjacent = MinesweeperBoardManager.getAdjacentMines(board, cellPos);
            player.displayClientMessage(Component.literal("Casilla revelada: " + Math.min(5, adjacent)), true);
        }

        player.getCooldowns().addCooldown(held, INTERACCION_COOLDOWN_TICKS);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
        checkCompletion(level, board);
        return InteractionResult.SUCCESS;
    }

    private static void onWorldTick(ServerLevel level) {
        if (!MinesweeperRoundManager.isActive()) {
            return;
        }

        int tick = level.getServer().getTickCount();

        clearExpiredDisarmChallenges(level, tick);
        processPendingMineTriggers(level, tick);

        if (tick % SNAPSHOT_SYNC_INTERVAL_TICKS == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    MinesweeperBoardSync.sendBestSnapshot(player);
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            releaseRespawnIfReady(player, tick);
            releaseInteractionPoseIfReady(player, tick);

            if (player.isSpectator()) {
                continue;
            }

            BlockPos below = player.blockPosition().below();
            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoardAt(level, below);
            if (boardOptional.isEmpty()) {
                continue;
            }

            BoardData board = boardOptional.get();
            if (!MinesweeperBoardManager.isMine(board, below)) {
                continue;
            }

            if (MinesweeperBoardManager.isDisarmed(board, below)) {
                showInactiveMineStepAnimation(level, board, below, player, tick);
                continue;
            }

            armMineUnderPlayer(level, board, below, player, tick);
        }
    }

    private static void processPendingMineTriggers(ServerLevel level, int tick) {
        Iterator<Map.Entry<UUID, PendingMineTrigger>> iterator = PENDING_MINE_TRIGGERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingMineTrigger> entry = iterator.next();
            PendingMineTrigger pending = entry.getValue();
            if (tick < pending.triggerTick()) {
                continue;
            }

            Optional<BoardData> boardOptional = MinesweeperBoardManager.getBoard(pending.boardId());
            if (boardOptional.isEmpty()) {
                iterator.remove();
                continue;
            }

            BoardData board = boardOptional.get();
            if (!board.dimension().equals(level.dimension())) {
                continue;
            }

            if (MinesweeperBoardManager.isDisarmed(board, pending.minePos())) {
                MinesweeperBoardManager.showMineAsInactive(level, board, pending.minePos());
                spawnInactiveMineFx(level, pending.minePos());
                MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
                iterator.remove();
                continue;
            }

            MinesweeperBoardManager.reveal(level, board, pending.minePos());
            triggerMine(level, board, pending.minePos());
            iterator.remove();
        }
    }

    private static void armMineUnderPlayer(
            ServerLevel level,
            BoardData board,
            BlockPos minePos,
            ServerPlayer player,
            int tick
    ) {
        PendingMineTrigger current = PENDING_MINE_TRIGGERS.get(player.getUUID());
        if (current == null || current.boardId() != board.boardId() || !current.minePos().equals(minePos)) {
            PENDING_MINE_TRIGGERS.put(
                    player.getUUID(),
                    new PendingMineTrigger(board.boardId(), minePos.immutable(), tick + STEP_MINE_TRIGGER_DELAY_TICKS)
            );
            MinesweeperBoardManager.reveal(level, board, minePos);
            MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
            player.displayClientMessage(Component.literal("Mina activada..."), true);
        }

        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STEP_MINE_TRIGGER_DELAY_TICKS + 4, 4, false, false, false));
        player.setPose(Pose.CROUCHING);
        INTERACTION_POSE_RELEASE_TICK.remove(player.getUUID());
        INTERACTION_USE_RELEASE_TICK.remove(player.getUUID());
        player.stopUsingItem();
    }

    private static void showInactiveMineStepAnimation(
            ServerLevel level,
            BoardData board,
            BlockPos minePos,
            ServerPlayer player,
            int tick
    ) {
        MinesweeperBoardManager.showMineAsInactive(level, board, minePos);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 1, false, false, false));
        player.setPose(Pose.STANDING);

        long key = inactiveMineKey(board.boardId(), minePos);
        Integer last = LAST_INACTIVE_MINE_EFFECT_TICK.get(key);
        if (last != null && tick - last < INACTIVE_MINE_EFFECT_COOLDOWN_TICKS) {
            return;
        }

        LAST_INACTIVE_MINE_EFFECT_TICK.put(key, tick);
        spawnInactiveMineFx(level, minePos);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
    }

    private static void spawnInactiveMineFx(ServerLevel level, BlockPos minePos) {
        level.sendParticles(
                ParticleTypes.CLOUD,
                minePos.getX() + 0.5D,
                minePos.getY() + 0.25D,
                minePos.getZ() + 0.5D,
                8,
                0.15D,
                0.05D,
                0.15D,
                0.01D
        );
    }

    private static void clearExpiredDisarmChallenges(ServerLevel level, int tick) {
        Iterator<Map.Entry<UUID, PendingDisarm>> iterator = PENDING_DISARMS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingDisarm> entry = iterator.next();
            PendingDisarm pending = entry.getValue();
            if (tick < pending.expireTick()) {
                continue;
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.displayClientMessage(Component.literal("Desactivacion cancelada por tiempo."), true);
                player.getCooldowns().addCooldown(MinesweeperItems.DESACTIVADOR_MINA.getDefaultInstance(), DESACTIVADOR_COOLDOWN_TICKS / 2);
            }
            iterator.remove();
        }
    }

    private static void releaseRespawnIfReady(ServerPlayer player, int tick) {
        Integer releaseTick = RESPAWN_RELEASE_TICK.get(player.getUUID());
        if (releaseTick == null || tick < releaseTick) {
            return;
        }

        PlayerTeam team = player.getTeam();
        if (team != null && ELIMINATED_TEAMS.contains(team.getName())) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            RESPAWN_RELEASE_TICK.remove(player.getUUID());
            return;
        }

        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.displayClientMessage(Component.literal("Ya puedes volver a jugar."), false);
        RESPAWN_RELEASE_TICK.remove(player.getUUID());
    }

    private static void playInteractionAnimation(
            ServerPlayer player,
            int tick,
            InteractionHand hand,
            BlockPos targetPos,
            PlayerInteractionAnimation animationKind
    ) {
        int animationTicks = animationKind.durationTicks();

        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(targetPos));
        player.setPose(Pose.CROUCHING);
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, animationTicks, 2, false, false, false));
        player.startUsingItem(hand);
        player.swing(hand, true);
        if (player.level() instanceof ServerLevel serverLevel) {
            broadcastInteractionAnimation(serverLevel, player, animationKind);
        }
        INTERACTION_POSE_RELEASE_TICK.put(player.getUUID(), tick + animationTicks);
        INTERACTION_USE_RELEASE_TICK.put(player.getUUID(), tick + animationTicks);
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
            ServerPlayNetworking.send(target, payload);
        }
    }

    private static void releaseInteractionPoseIfReady(ServerPlayer player, int tick) {
        Integer releaseTick = INTERACTION_POSE_RELEASE_TICK.get(player.getUUID());
        if (releaseTick == null || tick < releaseTick) {
            return;
        }

        if (!PENDING_MINE_TRIGGERS.containsKey(player.getUUID())) {
            player.setPose(Pose.STANDING);
        }
        INTERACTION_POSE_RELEASE_TICK.remove(player.getUUID());

        Integer stopUseTick = INTERACTION_USE_RELEASE_TICK.get(player.getUUID());
        if (stopUseTick != null && tick >= stopUseTick) {
            player.stopUsingItem();
            INTERACTION_USE_RELEASE_TICK.remove(player.getUUID());
        }
    }

    private static void triggerMine(ServerLevel level, BoardData board, BlockPos minePos) {
        MineExplosionEntity.spawn(
                level,
                minePos.getCenter().add(0.0D, EXPLOSION_GECKO_HEIGHT_OFFSET, 0.0D),
                EXPLOSION_GECKO_LIFE_TICKS
        );
        int tick = level.getServer().getTickCount();

        level.explode(
                null,
                minePos.getX() + 0.5D,
                minePos.getY() + 0.3D,
                minePos.getZ() + 0.5D,
                2.2F,
                false,
                Level.ExplosionInteraction.NONE
        );
        level.sendParticles(
                ParticleTypes.EXPLOSION,
                minePos.getX() + 0.5D,
                minePos.getY() + 0.45D,
                minePos.getZ() + 0.5D,
                10,
                0.25D,
                0.1D,
                0.25D,
                0.01D
        );
        double centerX = minePos.getX() + 0.5D;
        double centerY = minePos.getY() + 0.5D;
        double centerZ = minePos.getZ() + 0.5D;

        for (ServerPlayer other : level.players()) {
            if (other.isSpectator()) {
                continue;
            }
            if (Math.abs(other.getX() - centerX) > 1.5D || Math.abs(other.getZ() - centerZ) > 1.5D || Math.abs(other.getY() - centerY) > 2.5D) {
                continue;
            }

            other.animateHurt(0.0F);
            if (canUseTotem(other, tick)) {
                consumeTotemCooldown(other, tick);
                other.displayClientMessage(Component.literal("Totem de inmortalidad activado."), true);
                continue;
            }

            other.hurt(level.damageSources().explosion(null, null), 9999.0F);
        }

        checkCompletion(level, board);
        MinesweeperBoardSync.broadcastBoardSnapshot(level, board);
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

    private static boolean canUseTotem(ServerPlayer player, int tick) {
        if (!player.getInventory().contains(stack -> stack.is(MinesweeperItems.TOTEM_INMORTALIDAD))) {
            return false;
        }
        return tick >= TOTEM_READY_TICK.getOrDefault(player.getUUID(), 0);
    }

    private static void consumeTotemCooldown(ServerPlayer player, int tick) {
        TOTEM_READY_TICK.put(player.getUUID(), tick + TOTEM_COOLDOWN_TICKS);
    }

    private static void checkCompletion(ServerLevel level, BoardData board) {
        if (!MinesweeperBoardManager.isCompleted(board)) {
            return;
        }

        Component message = Component.literal("Tablero #" + board.boardId() + " completado.");
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }

    private static void evaluateTeamElimination(ServerPlayer deadPlayer) {
        if (deadPlayer.level().getServer() == null || !MinesweeperRoundManager.isActive()) {
            return;
        }

        PlayerTeam team = deadPlayer.getTeam();
        if (team == null || ELIMINATED_TEAMS.contains(team.getName())) {
            return;
        }

        boolean anyoneAlive = false;
        for (ServerPlayer player : deadPlayer.level().getServer().getPlayerList().getPlayers()) {
            PlayerTeam otherTeam = player.getTeam();
            if (otherTeam == null || !otherTeam.getName().equals(team.getName())) {
                continue;
            }

            boolean waitingRespawn = RESPAWN_RELEASE_TICK.containsKey(player.getUUID());
            if (player.isAlive() && !player.isSpectator() && !waitingRespawn) {
                anyoneAlive = true;
                break;
            }
        }

        if (!anyoneAlive) {
            ELIMINATED_TEAMS.add(team.getName());
            for (ServerPlayer player : deadPlayer.level().getServer().getPlayerList().getPlayers()) {
                PlayerTeam otherTeam = player.getTeam();
                if (otherTeam != null && otherTeam.getName().equals(team.getName())) {
                    player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                    player.displayClientMessage(Component.literal("Tu equipo fue eliminado."), false);
                }
            }
        }
    }

    private static long inactiveMineKey(int boardId, BlockPos minePos) {
        return (minePos.asLong() * 31L) ^ (boardId * 1_000_003L);
    }

    private enum PlayerInteractionAnimation {
        INTERACT((byte) 0, INTERACTION_ANIMATION_TICKS),
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

    private record PendingDisarm(int boardId, BlockPos cellPos, int token, int expireTick) {
    }

    private record PendingMineTrigger(int boardId, BlockPos minePos, int triggerTick) {
    }
}
