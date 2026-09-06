package me.andromedov.paradisepaints.client;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record PaintPayload(byte[] bytes) implements CustomPacketPayload {
    // createType(String) assumes the minecraft namespace; our channel needs an explicit namespace.
    public static final Type<PaintPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("paradisepaints", "paint"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PaintPayload> CODEC = new StreamCodec<>() {
        public @NonNull PaintPayload decode(RegistryFriendlyByteBuf buffer) {
            int length=buffer.readableBytes();
            if(length>18000) throw new IllegalArgumentException("Oversized paint packet");
            byte[] data=new byte[length]; buffer.readBytes(data); return new PaintPayload(data);
        }
        public void encode(@NonNull RegistryFriendlyByteBuf buffer, PaintPayload value) {
            if(value.bytes.length>18000) throw new IllegalArgumentException("Oversized paint packet");
            buffer.writeBytes(value.bytes);
        }
    };
    public @NonNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
