package me.andromedov.paradisepaints.client;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FavoriteColorsTest {
    @TempDir Path directory;
    @Test void persistsExactRgbAndMovesRetoggledColorToFront() throws Exception {
        Path file=directory.resolve("favorites.txt");
        var favorites=new FavoriteColors(file);
        assertTrue(favorites.toggle(0x123456));
        assertTrue(favorites.toggle(0xabcdef));
        assertFalse(favorites.toggle(0x123456));
        assertTrue(favorites.toggle(0x123456));
        assertEquals(java.util.List.of(0x123456,0xabcdef),new FavoriteColors(file).values());
    }
    @Test void boundsAndIgnoresMalformedInput() throws Exception {
        Path file=directory.resolve("favorites.txt");
        Files.writeString(file,"invalid\n#112233\n#112233\n");
        var favorites=new FavoriteColors(file);
        for(int i=0;i<20;i++) favorites.toggle(i);
        assertEquals(FavoriteColors.LIMIT,new FavoriteColors(file).values().size());
    }
}
