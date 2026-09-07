package me.andromedov.paradisepaints.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One GPU texture replaces thousands of GUI rectangle commands per frame. */
public final class CanvasTexture implements AutoCloseable {
    private static final AtomicLong IDS=new AtomicLong();
    private final Minecraft minecraft;
    private final Identifier location;
    private final DynamicTexture texture;
    private boolean closed;

    public CanvasTexture(Minecraft minecraft,byte[] pixels,int[] palette) {
        this.minecraft=minecraft;
        location=Identifier.fromNamespaceAndPath("paradisepaints","canvas/"+IDS.incrementAndGet());
        texture=new DynamicTexture("ParadisePaints canvas",128,128,true);
        minecraft.getTextureManager().register(location,texture);
        update(pixels,palette);
    }

    public void update(byte[] pixels,int[] palette) {
        if(closed || pixels.length!=16384 || palette.length!=256) throw new IllegalArgumentException("Canvas texture data");
        NativeImage image=texture.getPixels();
        if(image==null) throw new IllegalStateException("Canvas texture is closed");
        for(int index=0;index<pixels.length;index++) {
            int color=Byte.toUnsignedInt(pixels[index]);
            image.setPixelABGR(index%128,index/128,color<4?0:abgr(palette[color]));
        }
        texture.upload();
    }

    public void draw(GuiGraphicsExtractor graphics,int x,int y,int width,int height) {
        // Destination may be scaled, but the sampled UV region is always the one 128x128 canvas.
        graphics.blit(RenderPipelines.GUI_TEXTURED,location,x,y,0,0,width,height,128,128,128,128);
    }

    private static int abgr(int argb) {
        return argb&0xff00ff00 | (argb&0x00ff0000)>>>16 | (argb&0x000000ff)<<16;
    }

    @Override public void close() {
        if(!closed) { closed=true; minecraft.getTextureManager().release(location); }
    }
}
