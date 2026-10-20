package org.autonomous_vehicles.ui;

import java.util.Locale;

/**
 * Displayed images per second, averaged over windows of half a second: a per-image value (1 / last interval)
 * flickers too much to read. Fed with the nanosecond timestamps of the {@code AnimationTimer}, so it is tested
 * without JavaFX.
 */
public final class FpsCounter {

    public static final long WINDOW_NANOS = 500_000_000L;

    private long windowStart = Long.MIN_VALUE;
    private int frames;
    private double fps = Double.NaN;

    /**
     * Records one displayed image at {@code nanos} (monotonic clock).
     */
    public void frame(long nanos) {
        if (windowStart == Long.MIN_VALUE || nanos < windowStart) {
            windowStart = nanos;
            frames = 0;
            return;
        }

        frames++;
        long elapsed = nanos - windowStart;

        if (elapsed >= WINDOW_NANOS) {
            fps = frames * 1e9 / elapsed;
            frames = 0;
            windowStart = nanos;
        }
    }

    /**
     * Images per second over the last complete window, NaN before the first one.
     */
    public double fps() {
        return fps;
    }

    public String label() {
        return Double.isNaN(fps) ? "FPS –" : String.format(Locale.ROOT, "FPS %.0f", fps);
    }
}
