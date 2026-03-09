package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record RoundStatePayload(boolean active) implements CustomPacketPayload {
    public static final Type<RoundStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "round_state")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RoundStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RoundStatePayload::active,
            RoundStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
