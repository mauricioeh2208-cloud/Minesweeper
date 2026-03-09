package org.shouto.minesweeper.minesweeper.client;

import org.shouto.minesweeper.minesweeper.network.payload.BoardSnapshotPayload;

import java.util.HashMap;
import java.util.Map;

public final class MinesweeperClientState {
    private static volatile boolean roundActive;
    private static volatile BoardSnapshotPayload lastSnapshot;
    private static final Map<Integer, ActivePlayerInteractionAnimation> PLAYER_INTERACTION_ANIMATIONS = new HashMap<>();

    private MinesweeperClientState() {
    }

    public static boolean isRoundActive() {
        return roundActive;
    }

    public static void setRoundActive(boolean active) {
        roundActive = active;
        if (!active) {
            lastSnapshot = null;
            PLAYER_INTERACTION_ANIMATIONS.clear();
        }
    }

    public static BoardSnapshotPayload getLastSnapshot() {
        return lastSnapshot;
    }

    public static void setLastSnapshot(BoardSnapshotPayload snapshot) {
        lastSnapshot = snapshot;
    }

    public static void startPlayerInteractionAnimation(
            int entityId,
            byte animationKindId,
            int durationTicks,
            long startGameTick
    ) {
        InteractionAnimationKind kind = InteractionAnimationKind.fromNetworkId(animationKindId);
        int safeDuration = Math.max(1, durationTicks);
        PLAYER_INTERACTION_ANIMATIONS.put(entityId, new ActivePlayerInteractionAnimation(kind, startGameTick, safeDuration));
    }

    public static PlayerInteractionAnimationSnapshot getPlayerInteractionAnimation(int entityId, long currentGameTick) {
        ActivePlayerInteractionAnimation active = PLAYER_INTERACTION_ANIMATIONS.get(entityId);
        if (active == null) {
            return null;
        }

        long elapsed = currentGameTick - active.startGameTick();
        if (elapsed < 0) {
            elapsed = 0;
        }

        if (elapsed >= active.durationTicks()) {
            PLAYER_INTERACTION_ANIMATIONS.remove(entityId);
            return null;
        }

        float progress = elapsed / (float) active.durationTicks();
        return new PlayerInteractionAnimationSnapshot(active.kind(), progress);
    }

    public enum InteractionAnimationKind {
        INTERACT((byte) 0),
        FLAG_PICKUP((byte) 1);

        private final byte networkId;

        InteractionAnimationKind(byte networkId) {
            this.networkId = networkId;
        }

        public static InteractionAnimationKind fromNetworkId(byte id) {
            for (InteractionAnimationKind kind : values()) {
                if (kind.networkId == id) {
                    return kind;
                }
            }
            return INTERACT;
        }
    }

    private record ActivePlayerInteractionAnimation(
            InteractionAnimationKind kind,
            long startGameTick,
            int durationTicks
    ) {
    }

    public record PlayerInteractionAnimationSnapshot(InteractionAnimationKind kind, float progress) {
    }
}
