package me.andromedov.paradisepaints.client;

import java.util.Arrays;
import java.util.Comparator;
import java.util.OptionalInt;

/** The server's actual map colors, not arbitrary RGB values that cannot be saved. */
public final class ColorPalette {
    private final int[] colors,ordered;
    public ColorPalette(int[] colors) {
        if(colors.length!=256) throw new IllegalArgumentException("Palette size");
        this.colors=colors.clone();
        Integer[] indices=new Integer[244];
        for(int i=0;i<indices.length;i++) indices[i]=i+4;
        Arrays.sort(indices,Comparator.<Integer>comparingDouble(i->hue(colors[i]))
                .thenComparingInt(i->brightness(colors[i])).thenComparingInt(i->i));
        ordered=Arrays.stream(indices).mapToInt(Integer::intValue).toArray();
    }
    public int[] colors() { return colors.clone(); }
    public int argb(int index) { return index<4?0xffeee4cf:colors[index]; }
    public int at(int position) { return ordered[position]; }
    public int size() { return ordered.length; }
    public String hex(int index) { return String.format(java.util.Locale.ROOT,"#%06X",argb(index)&0xffffff); }
    public int position(int index) {
        for(int i=0;i<ordered.length;i++) if(ordered[i]==index) return i;
        return 0;
    }
    public OptionalInt nearest(String hex) {
        OptionalInt rgb=parseRgb(hex);
        return rgb.isPresent()?OptionalInt.of(nearest(rgb.getAsInt())):OptionalInt.empty();
    }
    public OptionalInt parseRgb(String hex) {
        if(hex==null || !hex.matches("#?[0-9a-fA-F]{6}")) return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(hex.startsWith("#")?hex.substring(1):hex,16));
    }
    public int nearest(int rgb) {
        int best=4;
        long distance=Long.MAX_VALUE;
        for(int index=4;index<248;index++) {
            int r=((rgb>>>16)&255)-((colors[index]>>>16)&255);
            int g=((rgb>>>8)&255)-((colors[index]>>>8)&255),b=(rgb&255)-(colors[index]&255);
            long score=2L*r*r+4L*g*g+3L*b*b;
            if(score<distance) { best=index; distance=score; }
        }
        return best;
    }
    private static int brightness(int rgb) { return Math.max((rgb>>>16)&255,Math.max((rgb>>>8)&255,rgb&255)); }
    private static double hue(int rgb) {
        double r=(rgb>>>16)&255,g=(rgb>>>8)&255,b=rgb&255;
        double max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;
        if(d<10) return -1;
        double hue=max==r?(g-b)/d:max==g?2+(b-r)/d:4+(r-g)/d;
        return hue<0?hue+6:hue;
    }
}
