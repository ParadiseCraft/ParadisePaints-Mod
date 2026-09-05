package me.andromedov.paradisepaints.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaveAttemptTest {
    @Test void timeoutKeepsSnapshotFrozenForLateResultsAndRetries() {
        var save = new SaveAttempt();
        byte[] original = {1, 2};
        byte[] sent = save.begin(original);
        original[0] = 3;
        sent[0] = 4;
        for (int i = 0; i < 199; i++) assertFalse(save.tick());
        assertTrue(save.tick());
        assertFalse(save.waiting());
        assertTrue(save.frozen());
        assertArrayEquals(new byte[]{1, 2}, save.begin(new byte[]{5, 6}));
    }

    @Test void rejectionAllowsOnlyIdenticalRetry() {
        var save = new SaveAttempt();
        save.begin(new byte[]{7});
        save.rejected();
        assertTrue(save.frozen());
        assertArrayEquals(new byte[]{7}, save.begin(new byte[]{8}));
    }

    @Test void pendingSaveCannotBeStartedAgain() {
        var save = new SaveAttempt();
        assertFalse(save.frozen());
        save.begin(new byte[]{1});
        assertThrows(IllegalStateException.class, () -> save.begin(new byte[]{2}));
    }
}
