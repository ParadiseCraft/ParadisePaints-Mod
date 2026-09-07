package me.andromedov.paradisepaints.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClientBuildIdentityTest {
    @TempDir Path directory;
    @Test void hashesExactFileBytes() throws Exception {
        Path file=directory.resolve("mod.jar"); Files.writeString(file,"ParadisePaints",StandardCharsets.UTF_8);
        assertEquals("a207bb0139c0f74c2f6e7b8a8be50c15ce4db82826b9d5547f1518d756a72176",
                HexFormat.of().formatHex(ClientBuildIdentity.sha256(file)));
    }
}
