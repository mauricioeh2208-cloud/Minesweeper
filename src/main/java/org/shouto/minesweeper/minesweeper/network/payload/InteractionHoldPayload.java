package org.shouto.minesweeper.minesweeper.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public record InteractionHoldPayload(int x, int y, int z, byte handId) implements CustomPacketPayload {
    public static final Type<InteractionHoldPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "interaction_hold")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, InteractionHoldPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            InteractionHoldPayload::x,
            ByteBufCodecs.VAR_INT,
            InteractionHoldPayload::y,
            ByteBufCodecs.VAR_INT,
            InteractionHoldPayload::z,
            ByteBufCodecs.BYTE,
            InteractionHoldPayload::handId,
            InteractionHoldPayload::new
    );

    public InteractionHand hand() {
        return handId == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
