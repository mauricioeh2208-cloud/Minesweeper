package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record RequestBoardSnapshotPayload(int boardId) implements CustomPacketPayload {
    public static final Type<RequestBoardSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "request_board_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestBoardSnapshotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RequestBoardSnapshotPayload::boardId,
            RequestBoardSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
