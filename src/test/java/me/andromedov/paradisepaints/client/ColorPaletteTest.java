package me.andromedov.paradisepaints.client;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColorPaletteTest {
    @Test void exposesEveryAllowedOpaqueMapColorExactlyOnce() {
        var palette=new ColorPalette(new int[256]); var found=new HashSet<Integer>();
        for(int i=0;i<palette.size();i++) { assertTrue(palette.at(i)>=4 && palette.at(i)<248); assertTrue(found.add(palette.at(i))); }
        assertEquals(244,found.size());
    }
    @Test void hexSelectsOnlySupportedNearestColorsAndRejectsInvalidInput() {
        int[] colors=new int[256]; colors[9]=0xffff0000; colors[248]=0xffff0100;
        var palette=new ColorPalette(colors);
        assertEquals(9,palette.nearest("#FF0100").orElseThrow());
        assertEquals("#FF0000",palette.hex(9));
        for(String invalid:new String[]{"", "#FF", "#GG0000", "#FFFFFFFF", "1234567"}) assertTrue(palette.nearest(invalid).isEmpty());
        colors[9]=0; assertEquals("#FF0000",palette.hex(9));
    }
}
