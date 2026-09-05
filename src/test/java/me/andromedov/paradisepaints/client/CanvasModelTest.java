package me.andromedov.paradisepaints.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CanvasModelTest {
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
        c.undo(); c.beginStroke(); c.line(1,1,1,1,1,4); c.redo();
        assertEquals(4,c.color(1,1)); assertEquals(0,c.color(0,0));
        c.pixels()[129]=22; assertEquals(4,c.color(1,1));
    }
}
