package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record BoardSnapshotPayload(
        int boardId,
        BlockPos origin,
        int width,
        int height,
        int playerLocalX,
        int playerLocalZ,
        byte[] cells
) implements CustomPacketPayload {
    public static final byte CELL_HIDDEN = 0;
    public static final byte CELL_NUMBER_0 = 1;
    public static final byte CELL_NUMBER_1 = 2;
    public static final byte CELL_NUMBER_2 = 3;
    public static final byte CELL_NUMBER_3 = 4;
    public static final byte CELL_NUMBER_4 = 5;
    public static final byte CELL_NUMBER_5 = 6;
    public static final byte CELL_FLAG = 7;
    public static final byte CELL_DISARMED_MINE = 8;
    public static final byte CELL_REVEALED_MINE = 9;

    public static final Type<BoardSnapshotPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "board_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BoardSnapshotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            BoardSnapshotPayload::boardId,
            BlockPos.STREAM_CODEC,
            BoardSnapshotPayload::origin,
            ByteBufCodecs.VAR_INT,
            BoardSnapshotPayload::width,
            ByteBufCodecs.VAR_INT,
            BoardSnapshotPayload::height,
            ByteBufCodecs.VAR_INT,
            BoardSnapshotPayload::playerLocalX,
            ByteBufCodecs.VAR_INT,
            BoardSnapshotPayload::playerLocalZ,
            ByteBufCodecs.BYTE_ARRAY,
            BoardSnapshotPayload::cells,
            BoardSnapshotPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public byte cellAt(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= width || localZ >= height) {
            return CELL_HIDDEN;
        }
        int index = localZ * width + localX;
        if (index < 0 || index >= cells.length) {
            return CELL_HIDDEN;
        }
        return cells[index];
    }
}
