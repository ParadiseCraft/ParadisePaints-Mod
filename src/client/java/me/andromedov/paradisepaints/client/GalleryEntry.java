package me.andromedov.paradisepaints.client;

import java.util.UUID;

public record GalleryEntry(UUID request,int mapId,long createdAt,long updatedAt,boolean blocked,long blockedAt,
        String title,String author,String authorId,String moderator,String reason,byte[] pixels) {
    public GalleryEntry {
        if(pixels.length!=16384) throw new IllegalArgumentException("Canvas size");
        pixels=pixels.clone();
    }
    @Override public byte[] pixels() { return pixels.clone(); }
}
