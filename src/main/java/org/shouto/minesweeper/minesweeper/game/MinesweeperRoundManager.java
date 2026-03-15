package org.shouto.minesweeper.minesweeper.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.shouto.minesweeper.minesweeper.network.payload.RoundStatePayload;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperItems;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MinesweeperRoundManager {
    private static final double ROUND_MAX_HEALTH = 6.0D;
    private static final Item[] MANAGED_ITEMS = new Item[]{
            MinesweeperItems.INTERACCION,
            MinesweeperItems.DESACTIVADOR_MINA,
            MinesweeperItems.TOTEM_INMORTALIDAD,
            MinesweeperItems.BANDERA
    };

    private static boolean active;
    private static RoundSettings configuredSettings = RoundSettings.defaultSettings();
    private static RoundSettings activeSettings = configuredSettings;
    private static int roundStartTick;
    private static int roundEndTick;
    private static final Set<UUID> PARTICIPANTS = new HashSet<>();
    private static final Map<UUID, SavedPlayerState> SAVED_PLAYER_STATES = new HashMap<>();
    private static final Set<String> TEMPORARY_TEAM_NAMES = new HashSet<>();

    private MinesweeperRoundManager() {
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isParticipant(ServerPlayer player) {
        return isParticipant(player.getUUID());
    }

    public static boolean isParticipant(UUID playerId) {
        return PARTICIPANTS.contains(playerId);
    }

    public static void ensureParticipant(ServerPlayer player) {
        if (!active || player == null) {
            return;
        }
        if (player.gameMode() == GameType.CREATIVE || player.gameMode() == GameType.SPECTATOR) {
            return;
        }

        UUID playerId = player.getUUID();
        if (PARTICIPANTS.contains(playerId)) {
            return;
        }

        PARTICIPANTS.add(playerId);
        SAVED_PLAYER_STATES.put(playerId, SavedPlayerState.capture(player));
        prepareRoundPlayer(player);

        MinecraftServer server = ((net.minecraft.server.level.ServerLevel) player.level()).getServer();
        if (server != null) {
            broadcast(server);
        }
    }

    public static RoundSettings configuredSettings() {
        return configuredSettings;
    }

    public static RoundSettings activeSettings() {
        return active ? activeSettings : configuredSettings;
    }

    public static GameType participantGameMode(UUID playerId) {
        SavedPlayerState state = SAVED_PLAYER_STATES.get(playerId);
        return state != null ? state.gameMode() : GameType.SURVIVAL;
    }

    public static void setConfiguredSettings(RoundSettings settings) {
        configuredSettings = settings;
    }

    public static void startRound(MinecraftServer server, Collection<ServerPlayer> participants, RoundSettings settings) {
        SAVED_PLAYER_STATES.clear();
        PARTICIPANTS.clear();
        TEMPORARY_TEAM_NAMES.clear();

        for (ServerPlayer player : participants) {
            PARTICIPANTS.add(player.getUUID());
            SAVED_PLAYER_STATES.put(player.getUUID(), SavedPlayerState.capture(player));
            prepareRoundPlayer(player);
        }

        activeSettings = settings;
        roundStartTick = server.getTickCount();
        roundEndTick = roundStartTick + settings.roundDurationTicks();
        active = true;
        broadcast(server);
    }

    public static void stopRound(MinecraftServer server) {
        restorePlayers(server);
        active = false;
        PARTICIPANTS.clear();
        SAVED_PLAYER_STATES.clear();
        TEMPORARY_TEAM_NAMES.clear();
        activeSettings = configuredSettings;
        roundStartTick = 0;
        roundEndTick = 0;
        broadcast(server);
    }

    public static boolean hasExpired(MinecraftServer server) {
        return active && roundEndTick > 0 && server.getTickCount() >= roundEndTick;
    }

    public static int remainingRoundTicks(MinecraftServer server) {
        if (!active || roundEndTick <= 0) {
            return 0;
        }
        return Math.max(0, roundEndTick - server.getTickCount());
    }

    public static void setRoundTiming(int startTick, int endTick) {
        roundStartTick = Math.max(0, startTick);
        roundEndTick = Math.max(roundStartTick, endTick);
    }

    public static void registerTemporaryTeam(PlayerTeam team) {
        TEMPORARY_TEAM_NAMES.add(team.getName());
    }

    public static void setActive(MinecraftServer server, boolean roundActive) {
        active = roundActive;
        broadcast(server);
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new RoundStatePayload(active && isParticipant(player)));
        }
    }

    private static void restorePlayers(MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        for (Map.Entry<UUID, SavedPlayerState> entry : SAVED_PLAYER_STATES.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }

            entry.getValue().restore(player, scoreboard);
        }

        for (String teamName : new ArrayList<>(TEMPORARY_TEAM_NAMES)) {
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                scoreboard.removePlayerTeam(team);
            }
        }
    }

    public enum RoundDifficulty {
        FACIL("facil", 10, 10, 10, 2, 20 * 5, 20 * 14, 20 * 60 * 5, 20 * 30, 20 * 60 * 20),
        MEDIO("medio", 18, 18, 40, 2, 20 * 8, 20 * 10, 20 * 60 * 5, 20 * 45, 20 * 60 * 20),
        DIFICIL("dificil", 24, 24, 99, 2, 20 * 12, 20 * 7, 20 * 60 * 5, 20 * 60, 20 * 60 * 20);

        private final String id;
        private final int boardWidth;
        private final int boardHeight;
        private final int mineCount;
        private final int mineTriggerDelayTicks;
        private final int respawnWaitTicks;
        private final int disarmTimeoutTicks;
        private final int disablerCooldownTicks;
        private final int totemCooldownTicks;
        private final int roundDurationTicks;

        RoundDifficulty(
                String id,
                int boardWidth,
                int boardHeight,
                int mineCount,
                int mineTriggerDelayTicks,
                int respawnWaitTicks,
                int disarmTimeoutTicks,
                int disablerCooldownTicks,
                int totemCooldownTicks,
                int roundDurationTicks
        ) {
            this.id = id;
            this.boardWidth = boardWidth;
            this.boardHeight = boardHeight;
            this.mineCount = mineCount;
            this.mineTriggerDelayTicks = mineTriggerDelayTicks;
            this.respawnWaitTicks = respawnWaitTicks;
            this.disarmTimeoutTicks = disarmTimeoutTicks;
            this.disablerCooldownTicks = disablerCooldownTicks;
            this.totemCooldownTicks = totemCooldownTicks;
            this.roundDurationTicks = roundDurationTicks;
        }

        public String id() {
            return id;
        }

        public int mineTriggerDelayTicks() {
            return mineTriggerDelayTicks;
        }

        public int boardWidth() {
            return boardWidth;
        }

        public int boardHeight() {
            return boardHeight;
        }

        public int mineCount() {
            return mineCount;
        }

        public int respawnWaitTicks() {
            return respawnWaitTicks;
        }

        public int disarmTimeoutTicks() {
            return disarmTimeoutTicks;
        }

        public int disablerCooldownTicks() {
            return disablerCooldownTicks;
        }

        public int totemCooldownTicks() {
            return totemCooldownTicks;
        }

        public int roundDurationTicks() {
            return roundDurationTicks;
        }

        public static RoundDifficulty byId(String id) {
            if ("normal".equalsIgnoreCase(id)) {
                return MEDIO;
            }
            for (RoundDifficulty difficulty : values()) {
                if (difficulty.id.equalsIgnoreCase(id)) {
                    return difficulty;
                }
            }
            throw new IllegalArgumentException("Dificultad invalida: " + id);
        }
    }

    public enum TeamMode {
        MANTENER("mantener"),
        ALEATORIOS("aleatorios");

        private final String id;

        TeamMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static TeamMode byId(String id) {
            for (TeamMode mode : values()) {
                if (mode.id.equalsIgnoreCase(id)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Modo de equipos invalido: " + id);
        }
    }

    public record RoundSettings(
            String label,
            TeamMode teamMode,
            int teamSize,
            boolean giveItems,
            int mineTriggerDelayTicks,
            int respawnWaitTicks,
            int disarmTimeoutTicks,
            int disablerCooldownTicks,
            int totemCooldownTicks,
            int roundDurationTicks
    ) {
        public static RoundSettings defaultSettings() {
            return custom(
                    RoundDifficulty.MEDIO.id(),
                    TeamMode.MANTENER,
                    1,
                    true,
                    RoundDifficulty.MEDIO.mineTriggerDelayTicks(),
                    RoundDifficulty.MEDIO.respawnWaitTicks(),
                    RoundDifficulty.MEDIO.disarmTimeoutTicks(),
                    RoundDifficulty.MEDIO.disablerCooldownTicks(),
                    RoundDifficulty.MEDIO.totemCooldownTicks(),
                    RoundDifficulty.MEDIO.roundDurationTicks()
            );
        }

        public static RoundSettings custom(
                String label,
                TeamMode teamMode,
                int teamSize,
                boolean giveItems,
                int mineTriggerDelayTicks,
                int respawnWaitTicks,
                int disarmTimeoutTicks,
                int disablerCooldownTicks,
                int totemCooldownTicks
        ) {
            return custom(
                    label,
                    teamMode,
                    teamSize,
                    giveItems,
                    mineTriggerDelayTicks,
                    respawnWaitTicks,
                    disarmTimeoutTicks,
                    disablerCooldownTicks,
                    totemCooldownTicks,
                    RoundDifficulty.MEDIO.roundDurationTicks()
            );
        }

        public static RoundSettings custom(
                String label,
                TeamMode teamMode,
                int teamSize,
                boolean giveItems,
                int mineTriggerDelayTicks,
                int respawnWaitTicks,
                int disarmTimeoutTicks,
                int disablerCooldownTicks,
                int totemCooldownTicks,
                int roundDurationTicks
        ) {
            return new RoundSettings(
                    label,
                    teamMode,
                    Math.max(1, teamSize),
                    giveItems,
                    Math.max(1, mineTriggerDelayTicks),
                    Math.max(20, respawnWaitTicks),
                    Math.max(20, disarmTimeoutTicks),
                    Math.max(20 * 60 * 5, disablerCooldownTicks),
                    Math.max(20, totemCooldownTicks),
                    Math.max(20, roundDurationTicks)
            );
        }

        public RoundSettings withDifficulty(RoundDifficulty difficulty) {
            return custom(
                    difficulty.id(),
                    teamMode,
                    teamSize,
                    giveItems,
                    difficulty.mineTriggerDelayTicks(),
                    difficulty.respawnWaitTicks(),
                    difficulty.disarmTimeoutTicks(),
                    difficulty.disablerCooldownTicks(),
                    difficulty.totemCooldownTicks(),
                    difficulty.roundDurationTicks()
            );
        }
    }

    private record SavedPlayerState(
            GameType gameMode,
            String teamName,
            boolean invulnerable,
            double maxHealthBase,
            float health,
            float absorption,
            int foodLevel,
            float saturation,
            int fireTicks,
            List<MobEffectInstance> activeEffects,
            Map<Item, Integer> managedItemCounts
    ) {
        private static SavedPlayerState capture(ServerPlayer player) {
            List<MobEffectInstance> effects = new ArrayList<>();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                effects.add(new MobEffectInstance(effect));
            }

            Map<Item, Integer> managedItems = new HashMap<>();
            for (Item item : MANAGED_ITEMS) {
                managedItems.put(item, countItem(player, item));
            }

            return new SavedPlayerState(
                    player.gameMode(),
                    player.getTeam() != null ? player.getTeam().getName() : null,
                    player.isInvulnerable(),
                    currentBaseMaxHealth(player),
                    player.getHealth(),
                    player.getAbsorptionAmount(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel(),
                    player.getRemainingFireTicks(),
                    effects,
                    managedItems
            );
        }

        private void restore(ServerPlayer player, ServerScoreboard scoreboard) {
            restoreTeam(player, scoreboard, teamName);

            player.stopUsingItem();
            player.setPose(Pose.STANDING);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            player.clearFire();
            player.setTicksFrozen(0);
            player.removeAllEffects();
            player.setInvulnerable(invulnerable);
            restoreMaxHealth(player, maxHealthBase);

            for (MobEffectInstance effect : activeEffects) {
                player.addEffect(new MobEffectInstance(effect));
            }

            player.setGameMode(gameMode);
            player.setHealth(Math.max(1.0F, Math.min(player.getMaxHealth(), health)));
            player.setAbsorptionAmount(absorption);
            player.getFoodData().setFoodLevel(foodLevel);
            player.getFoodData().setSaturation(saturation);
            if (fireTicks > 0) {
                player.setRemainingFireTicks(fireTicks);
            }

            restoreManagedItems(player, managedItemCounts);
            clearManagedCooldowns(player);
        }

        private static void restoreTeam(ServerPlayer player, ServerScoreboard scoreboard, String desiredTeamName) {
            PlayerTeam currentTeam = player.getTeam();
            if (currentTeam != null && (desiredTeamName == null || !currentTeam.getName().equals(desiredTeamName))) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), currentTeam);
            }

            if (desiredTeamName == null) {
                return;
            }

            PlayerTeam desiredTeam = scoreboard.getPlayerTeam(desiredTeamName);
            if (desiredTeam != null) {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), desiredTeam);
            }
        }

        private static int countItem(ServerPlayer player, Item item) {
            int total = 0;
            int size = player.getInventory().getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }

        private static void restoreManagedItems(ServerPlayer player, Map<Item, Integer> savedCounts) {
            int size = player.getInventory().getContainerSize();
            for (int slot = 0; slot < size; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                for (Item item : MANAGED_ITEMS) {
                    if (stack.is(item)) {
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        break;
                    }
                }
            }

            for (Item item : MANAGED_ITEMS) {
                int count = savedCounts.getOrDefault(item, 0);
                while (count > 0) {
                    int stackSize = Math.min(count, item.getDefaultMaxStackSize());
                    player.getInventory().add(new ItemStack(item, stackSize));
                    count -= stackSize;
                }
            }
        }

        private static void clearManagedCooldowns(ServerPlayer player) {
            for (Item item : MANAGED_ITEMS) {
                player.getCooldowns().removeCooldown(BuiltInRegistries.ITEM.getKey(item));
            }
        }

        private static double currentBaseMaxHealth(ServerPlayer player) {
            AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
            return attribute != null ? attribute.getBaseValue() : player.getMaxHealth();
        }

        private static void restoreMaxHealth(ServerPlayer player, double baseValue) {
            AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(baseValue);
            }
        }
    }

    private static void prepareRoundPlayer(ServerPlayer player) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(ROUND_MAX_HEALTH);
        }
        player.setInvulnerable(true);
        player.setHealth((float) ROUND_MAX_HEALTH);
        player.setAbsorptionAmount(0.0F);
        player.getFoodData().setFoodLevel(17);
        player.getFoodData().setSaturation(0.0F);
        player.clearFire();
        player.setTicksFrozen(0);
    }
}
