package me.andromedov.paradisepaints.client;

import java.util.ArrayDeque;
import java.util.Deque;

/** Minecraft-independent drawing operations; one history entry per gesture. */
public final class CanvasModel {
    public static final int SIZE = 128;
    private byte[] pixels;
    private final Deque<byte[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    public CanvasModel(byte[] initial) {
        if (initial.length != SIZE * SIZE) throw new IllegalArgumentException("Canvas size");
        pixels = initial.clone();
    }
    public byte[] pixels() { return pixels.clone(); }
    public int color(int x, int y) { return Byte.toUnsignedInt(pixels[y * SIZE + x]); }
    public void beginStroke() {
        undo.addLast(pixels.clone());
        if (undo.size() > 50) undo.removeFirst();
        redo.clear();
    }
    public void undo() {
        if (!undo.isEmpty()) { redo.addLast(pixels.clone()); pixels = undo.removeLast(); }
    }
    public void redo() {
        if (!redo.isEmpty()) { undo.addLast(pixels.clone()); pixels = redo.removeLast(); }
    }
    public void line(int x0, int y0, int x1, int y1, int size, int color) {
        int dx = Math.abs(x1-x0), sx = x0<x1?1:-1;
        int dy = -Math.abs(y1-y0), sy = y0<y1?1:-1, error = dx+dy;
        while (true) {
            dab(x0,y0,size,color);
            if (x0==x1 && y0==y1) break;
            int twice=2*error;
            if (twice>=dy) { error+=dy; x0+=sx; }
            if (twice<=dx) { error+=dx; y0+=sy; }
        }
    }
    private void dab(int x, int y, int size, int color) {
        for (int row=y-size/2; row<y-size/2+size; row++)
            for (int col=x-size/2; col<x-size/2+size; col++)
                if (col>=0 && row>=0 && col<SIZE && row<SIZE) pixels[row*SIZE+col]=(byte)color;
    }
    public void fill(int x, int y, int color) {
        int old=color(x,y);
        if (old==color) return;
        int[] queue=new int[SIZE*SIZE]; int head=0, tail=0;
        int start=y*SIZE+x; queue[tail++]=start; pixels[start]=(byte)color;
        while(head<tail) {
            int at=queue[head++];
            int[] neighbors={at%SIZE>0?at-1:-1, at%SIZE<SIZE-1?at+1:-1,
                at>=SIZE?at-SIZE:-1, at<SIZE*(SIZE-1)?at+SIZE:-1};
            for(int next:neighbors) if(next>=0 && Byte.toUnsignedInt(pixels[next])==old) {
                pixels[next]=(byte)color; queue[tail++]=next;
            }
        }
    }
}

