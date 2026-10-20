package org.autonomous_vehicles.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FpsCounterTest {

    private static final long SIXTIETH = 1_000_000_000L / 60;

    @Test
    public void testSixtyImagesPerSecondReadSixty() {
        FpsCounter counter = new FpsCounter();

        for (int i = 0; i <= 60; i++) {
            counter.frame(i * SIXTIETH);
        }

        assertEquals(60.0, counter.fps(), 0.5);
        assertEquals("FPS 60", counter.label());
    }

    @Test
    public void testNoValueBeforeTheFirstWindow() {
        FpsCounter counter = new FpsCounter();

        for (int i = 0; i < 10; i++) {
            counter.frame(i * SIXTIETH);
        }

        assertTrue(Double.isNaN(counter.fps()));
        assertEquals("FPS –", counter.label());
    }

    @Test
    public void testWindowAveragesUnevenImages() {
        FpsCounter counter = new FpsCounter();
        counter.frame(0);
        counter.frame(100_000_000L);
        counter.frame(150_000_000L);
        counter.frame(500_000_000L);

        // 3 images in 0.5 s
        assertEquals(6.0, counter.fps(), 1e-9);
    }

    @Test
    public void testClockGoingBackRestartsTheWindow() {
        FpsCounter counter = new FpsCounter();
        counter.frame(1_000_000_000L);
        counter.frame(0);
        counter.frame(250_000_000L);
        counter.frame(500_000_000L);

        assertEquals(4.0, counter.fps(), 1e-9);
    }
}
