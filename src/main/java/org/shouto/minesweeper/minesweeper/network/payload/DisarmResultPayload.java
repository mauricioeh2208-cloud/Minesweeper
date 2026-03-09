package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record DisarmResultPayload(int boardId, BlockPos cellPos, int token, boolean success) implements CustomPacketPayload {
    public static final Type<DisarmResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "disarm_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DisarmResultPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DisarmResultPayload::boardId,
            BlockPos.STREAM_CODEC,
            DisarmResultPayload::cellPos,
            ByteBufCodecs.VAR_INT,
            DisarmResultPayload::token,
            ByteBufCodecs.BOOL,
            DisarmResultPayload::success,
            DisarmResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
