package me.andromedov.paradisepaints.client;

/** A final save and its retries always carry the same snapshot, even after a late result. */
final class SaveAttempt {
    private byte[] snapshot;
    private int remainingTicks;

    boolean frozen() { return snapshot != null; }
    boolean waiting() { return remainingTicks > 0; }

    byte[] begin(byte[] pixels) {
        if (waiting()) throw new IllegalStateException("Save already pending");
        if (snapshot == null) snapshot = pixels.clone();
        remainingTicks = 200;
        return snapshot.clone();
    }

    boolean tick() { return remainingTicks > 0 && --remainingTicks == 0; }
    void rejected() { remainingTicks = 0; }
}
