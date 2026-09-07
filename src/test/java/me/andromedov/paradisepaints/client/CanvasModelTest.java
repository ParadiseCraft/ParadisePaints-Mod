package me.andromedov.paradisepaints.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CanvasModelTest {
    @Test void toleranceFillsSimilarConnectedPixelsWithoutLeakingIntoTransparentCanvas() {
        int[] palette=new int[256]; palette[4]=0xff202020; palette[5]=0xff252525; palette[6]=0xffeeeeee;
        var c=new CanvasModel(new byte[16384]); c.line(0,0,10,0,1,4); c.line(0,1,10,1,1,5); c.line(0,2,10,2,1,6);
        c.beginStroke(); c.fill(0,0,5,15,palette,false);
        assertEquals(5,c.color(10,0)); assertEquals(5,c.color(10,1)); assertEquals(6,c.color(10,2)); assertEquals(0,c.color(11,0));
        c.undo(); assertEquals(4,c.color(0,0));
    }
    @Test void replacementChangesDisconnectedRegionsWhileNormalFillDoesNot() {
        var c=new CanvasModel(new byte[16384]); c.line(1,1,1,1,1,4); c.line(100,100,100,100,1,4);
        c.fill(1,1,8,0,null,false); assertEquals(4,c.color(100,100));
        c.line(1,1,1,1,1,4); c.beginStroke(); c.fill(1,1,8,0,null,true);
        assertEquals(8,c.color(1,1)); assertEquals(8,c.color(100,100)); assertEquals(0,c.color(50,50));
        c.undo(); assertEquals(4,c.color(100,100));
    }
    @Test void sameColorAndFullCanvasFillTerminateAndRespectHistory() {
        var c=new CanvasModel(new byte[16384]); c.beginStroke(); c.fill(0,0,0);
        c.fill(127,127,4); c.fill(0,0,4);
        for(byte pixel:c.pixels()) assertEquals(4,pixel);
        assertTrue(c.canUndo()); c.undo(); assertTrue(c.canRedo());
        assertThrows(IllegalArgumentException.class,()->c.fill(-1,0,4));
        assertThrows(IllegalArgumentException.class,()->c.fill(0,0,255));
    }
    @Test void fillDoesNotCrossBoundary() {
        var c=new CanvasModel(new byte[16384]); c.line(64,0,64,127,1,4); c.fill(0,0,8);
        assertEquals(8,c.color(63,127)); assertEquals(4,c.color(64,127)); assertEquals(0,c.color(65,127));
    }
    @Test void fastStrokeHasNoGapsAndUndoIsOneGesture() {
        var c=new CanvasModel(new byte[16384]); c.beginStroke(); c.line(0,0,127,127,1,12);
        for(int i=0;i<128;i++) assertEquals(12,c.color(i,i));
        c.undo(); assertEquals(0,c.color(127,127)); c.redo(); assertEquals(12,c.color(127,127));
    }
    @Test void brushClipsAndEraserIsTransparent() {
        var c=new CanvasModel(new byte[16384]); c.line(0,0,0,0,8,7);
        assertEquals(7,c.color(0,0)); assertEquals(0,c.color(127,127));
        c.line(0,0,0,0,1,0); assertEquals(0,c.color(0,0));
    }
    @Test void newStrokeDiscardsRedoAndDoesNotExposePixels() {
        var c=new CanvasModel(new byte[16384]); c.beginStroke(); c.fill(0,0,9);
        assertTrue(c.revision()>0);
        c.undo(); c.beginStroke(); c.line(1,1,1,1,1,4); c.redo();
        assertEquals(4,c.color(1,1)); assertEquals(0,c.color(0,0));
        c.pixels()[129]=22; assertEquals(4,c.color(1,1));
    }
}
