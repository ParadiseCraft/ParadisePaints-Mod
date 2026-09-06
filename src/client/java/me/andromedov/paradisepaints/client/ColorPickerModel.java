package me.andromedov.paradisepaints.client;

/** Small dependency-free HSV model; hue, saturation and value are always normalized to 0..1. */
public final class ColorPickerModel {
    private float hue,saturation,value;

    public ColorPickerModel(int rgb) { setRgb(rgb); }
    public float hue() { return hue; }
    public float saturation() { return saturation; }
    public float value() { return value; }
    public void setHue(float next) { hue=clamp(next); }
    public void setSaturationValue(float nextSaturation,float nextValue) {
        saturation=clamp(nextSaturation); value=clamp(nextValue);
    }
    public void setRgb(int rgb) {
        float r=((rgb>>>16)&255)/255f,g=((rgb>>>8)&255)/255f,b=(rgb&255)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),delta=max-min;
        value=max;
        saturation=max==0?0:delta/max;
        if(delta==0) hue=0;
        else if(max==r) hue=((g-b)/delta%6)/6f;
        else if(max==g) hue=((b-r)/delta+2)/6f;
        else hue=((r-g)/delta+4)/6f;
        if(hue<0) hue+=1;
    }
    public int rgb() { return rgb(hue,saturation,value); }
    public static int rgb(float hue,float saturation,float value) {
        float h=clamp(hue)*6,s=clamp(saturation),v=clamp(value);
        int sector=Math.min(5,(int)h); float fraction=h-sector;
        float p=v*(1-s),q=v*(1-fraction*s),t=v*(1-(1-fraction)*s);
        float r,g,b;
        switch(sector) {
            case 0 -> { r=v; g=t; b=p; }
            case 1 -> { r=q; g=v; b=p; }
            case 2 -> { r=p; g=v; b=t; }
            case 3 -> { r=p; g=q; b=v; }
            case 4 -> { r=t; g=p; b=v; }
            default -> { r=v; g=p; b=q; }
        }
        return Math.round(r*255)<<16 | Math.round(g*255)<<8 | Math.round(b*255);
    }
    private static float clamp(float value) { return Math.max(0,Math.min(1,value)); }
}
