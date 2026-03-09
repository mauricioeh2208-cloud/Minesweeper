package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record OpenDisarmPayload(int boardId, BlockPos cellPos, int token) implements CustomPacketPayload {
    public static final Type<OpenDisarmPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "open_disarm")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenDisarmPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            OpenDisarmPayload::boardId,
            BlockPos.STREAM_CODEC,
            OpenDisarmPayload::cellPos,
            ByteBufCodecs.VAR_INT,
            OpenDisarmPayload::token,
            OpenDisarmPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
