package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record InteractionProgressPayload(byte kindId, byte progressPercent) implements CustomPacketPayload {
    public static final byte KIND_CLEAR = -1;
    public static final byte KIND_REVEAL = 0;
    public static final byte KIND_DISARM = 1;

    public static final Type<InteractionProgressPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "interaction_progress")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, InteractionProgressPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            InteractionProgressPayload::kindId,
            ByteBufCodecs.BYTE,
            InteractionProgressPayload::progressPercent,
            InteractionProgressPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
