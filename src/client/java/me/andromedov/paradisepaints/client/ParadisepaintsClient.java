package me.andromedov.paradisepaints.client;

import java.nio.ByteBuffer;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ParadisepaintsClient implements ClientModInitializer {
    public static final int PROTOCOL=6;
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
                ClientPlayNetworking.send(new PaintPayload(ByteBuffer.allocate(37).put((byte)0).putInt(PROTOCOL)
                        .put(ClientBuildIdentity.sha256()).array()));
            } else if(op==1 && in.remaining()>=16+2+1+1+16+16384+1024) {
                UUID session=new UUID(in.getLong(),in.getLong());
                int titleLength=Short.toUnsignedInt(in.getShort());
                if(titleLength<1 || titleLength>128 || in.remaining()!=titleLength+1+16+16384+1024) return;
                byte[] titleBytes=new byte[titleLength]; in.get(titleBytes);
                String title=new String(titleBytes,java.nio.charset.StandardCharsets.UTF_8);
                boolean pigments=in.get()!=0; int red=in.getInt(),green=in.getInt(),blue=in.getInt(),capacity=in.getInt();
                if(capacity<1 || red<0 || green<0 || blue<0 || red>capacity || green>capacity || blue>capacity) return;
                byte[] pixels=new byte[16384]; in.get(pixels);
                int[] palette=new int[256]; for(int i=0;i<256;i++) palette[i]=in.getInt();
                client.execute(() -> client.gui.setScreen(new PaintScreen(session,title,pixels,palette,pigments,red,green,blue,capacity)));
            } else if(op==3 && in.remaining()==17) {
                UUID session=new UUID(in.getLong(),in.getLong()); int result=Byte.toUnsignedInt(in.get());
                if(result>2) return;
                client.execute(() -> {
                    if(client.gui.screen() instanceof PaintScreen screen) screen.acknowledge(session,result);
                });
            } else if(op==5 && in.remaining()==16+16+1024) {
                UUID request=new UUID(in.getLong(),in.getLong());
                int page=in.getInt(),pages=in.getInt(),total=in.getInt(),expected=in.getInt();
                if(page<1 || pages<1 || page>pages || total<0 || expected<0 || expected>6) return;
                int[] palette=new int[256]; for(int i=0;i<256;i++) palette[i]=in.getInt();
                client.execute(()->client.gui.setScreen(new GalleryScreen(request,page,pages,total,expected,palette)));
            } else if(op==6) {
                GalleryEntry entry=galleryEntry(in);
                if(entry==null) return;
                client.execute(()->{
                    if(client.gui.screen() instanceof GalleryScreen screen && screen.request().equals(entry.request())) screen.add(entry);
                });
            } else if(op==7 && in.remaining()==16) {
                UUID session=new UUID(in.getLong(),in.getLong());
                client.execute(()->{
                    if(client.gui.screen() instanceof PaintScreen screen) screen.closeFromServer(session);
                });
            }
        });
    }
    private static GalleryEntry galleryEntry(ByteBuffer in) {
        if(in.remaining()<16+4+8+8+1+8+10+16384) return null;
        UUID request=new UUID(in.getLong(),in.getLong()); int map=in.getInt();
        long created=in.getLong(),updated=in.getLong(); boolean blocked=in.get()!=0; long blockedAt=in.getLong();
        String title=readString(in,128),author=readString(in,256),authorId=readString(in,64),moderator=readString(in,256),reason=readString(in,1024);
        if(title==null||author==null||authorId==null||moderator==null||reason==null||in.remaining()!=16384) return null;
        byte[] pixels=new byte[16384]; in.get(pixels);
        for(byte pixel:pixels) if(Byte.toUnsignedInt(pixel)>247) return null;
        return new GalleryEntry(request,map,created,updated,blocked,blockedAt,title,author,authorId,moderator,reason,pixels);
    }
    private static String readString(ByteBuffer in,int max) {
        if(in.remaining()<2) return null;
        int length=Short.toUnsignedInt(in.getShort());
        if(length>max || in.remaining()<length) return null;
        byte[] encoded=new byte[length]; in.get(encoded);
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded)).toString();
        } catch(java.nio.charset.CharacterCodingException error) { return null; }
    }
    public static void save(UUID session,long sequence,String title,byte[] pixels,boolean close) {
        byte[] encoded=title.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer out=ByteBuffer.allocate(25+pixels.length+(close?2+encoded.length:0));
        out.put((byte)(close?2:4)).putLong(session.getMostSignificantBits()).putLong(session.getLeastSignificantBits());
        out.putLong(sequence);
        if(close) out.putShort((short)encoded.length).put(encoded);
        out.put(pixels);
        ClientPlayNetworking.send(new PaintPayload(out.array()));
    }
}
