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
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
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
        fill(x,y,color,0,null,false);
    }
    /** Four-connected flood fill, or global replacement; tolerance is RGB distance per channel. */
    public void fill(int x,int y,int color,int tolerance,int[] palette,boolean global) {
        if(x<0 || y<0 || x>=SIZE || y>=SIZE || color<0 || color>=248 || tolerance<0 || tolerance>100)
            throw new IllegalArgumentException("Fill bounds");
        if(tolerance>0 && (palette==null || palette.length!=256)) throw new IllegalArgumentException("Palette required");
        int old=color(x,y);
        if(global) {
            for(int i=0;i<pixels.length;i++) if(matches(Byte.toUnsignedInt(pixels[i]),old,tolerance,palette)) pixels[i]=(byte)color;
            return;
        }
        // Visited is separate from output: similar replacement colors must not be revisited.
        boolean[] visited=new boolean[SIZE*SIZE];
        int[] queue=new int[SIZE*SIZE]; int head=0,tail=0;
        int start=y*SIZE+x; queue[tail++]=start; visited[start]=true;
        while(head<tail) {
            int at=queue[head++];
            pixels[at]=(byte)color;
            for(int direction=0;direction<4;direction++) {
                int next=switch(direction) {
                    case 0 -> at%SIZE>0?at-1:-1;
                    case 1 -> at%SIZE<SIZE-1?at+1:-1;
                    case 2 -> at>=SIZE?at-SIZE:-1;
                    default -> at<SIZE*(SIZE-1)?at+SIZE:-1;
                };
                if(next>=0 && !visited[next] && matches(Byte.toUnsignedInt(pixels[next]),old,tolerance,palette)) {
                    visited[next]=true; queue[tail++]=next;
                }
            }
        }
    }
    private static boolean matches(int candidate,int old,int tolerance,int[] palette) {
        if(candidate<4 || old<4) return candidate<4 && old<4;
        if(tolerance==0) return candidate==old;
        int a=palette[candidate],b=palette[old];
        int r=((a>>>16)&255)-((b>>>16)&255),g=((a>>>8)&255)-((b>>>8)&255),blue=(a&255)-(b&255);
        return r*r+g*g+blue*blue<=3*tolerance*tolerance;
    }
}
