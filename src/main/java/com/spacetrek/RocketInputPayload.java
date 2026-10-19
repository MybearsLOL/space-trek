package com.spacetrek;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RocketInputPayload(int flags) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RocketInputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("space-trek", "rocket_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RocketInputPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeByte(payload.flags()),
            buf -> new RocketInputPayload(buf.readByte())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
