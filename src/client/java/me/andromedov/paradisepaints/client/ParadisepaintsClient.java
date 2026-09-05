package me.andromedov.paradisepaints.client;

import java.nio.ByteBuffer;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ParadisepaintsClient implements ClientModInitializer {
    public static final int PROTOCOL=1;
    @Override public void onInitializeClient() {
        PayloadTypeRegistry.clientboundPlay().register(PaintPayload.TYPE, PaintPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PaintPayload.TYPE, PaintPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PaintPayload.TYPE, (payload, context) -> {
            // Borrowed game instance: its lifecycle belongs to Minecraft, not this receiver.
            @SuppressWarnings("resource") var client=context.client();
            ByteBuffer in=ByteBuffer.wrap(payload.bytes());
            if(!in.hasRemaining()) return;
            int op=Byte.toUnsignedInt(in.get());
            if(op==0 && in.remaining()==4) {
                in.getInt();
                // Advertise our own version even on mismatch so the server can explain rejection.
                ClientPlayNetworking.send(new PaintPayload(ByteBuffer.allocate(5).put((byte)0).putInt(PROTOCOL).array()));
            } else if(op==1 && in.remaining()==16+16384+1024) {
                UUID session=new UUID(in.getLong(),in.getLong());
                byte[] pixels=new byte[16384]; in.get(pixels);
                int[] palette=new int[256]; for(int i=0;i<256;i++) palette[i]=in.getInt();
                client.execute(() -> client.gui.setScreen(new PaintScreen(session,pixels,palette)));
            } else if(op==3 && in.remaining()==17) {
                UUID session=new UUID(in.getLong(),in.getLong()); boolean ok=in.get()==1;
                client.execute(() -> {
                    if(client.gui.screen() instanceof PaintScreen screen) screen.acknowledge(session,ok);
                });
            }
        });
    }
    public static void save(UUID session, byte[] pixels, boolean close) {
        ByteBuffer out=ByteBuffer.allocate(17+pixels.length);
        out.put((byte)(close?2:4)).putLong(session.getMostSignificantBits()).putLong(session.getLeastSignificantBits()).put(pixels);
        ClientPlayNetworking.send(new PaintPayload(out.array()));
    }
}
