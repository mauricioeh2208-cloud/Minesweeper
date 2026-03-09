package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record PlayerInteractionAnimPayload(int entityId, byte animationKind, int durationTicks) implements CustomPacketPayload {
    public static final Type<PlayerInteractionAnimPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "player_interaction_anim")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerInteractionAnimPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PlayerInteractionAnimPayload::entityId,
            ByteBufCodecs.BYTE,
            PlayerInteractionAnimPayload::animationKind,
            ByteBufCodecs.VAR_INT,
            PlayerInteractionAnimPayload::durationTicks,
            PlayerInteractionAnimPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
