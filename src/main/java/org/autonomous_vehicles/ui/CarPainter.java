package org.autonomous_vehicles.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

/**
 * Draws a car seen from above, centred on the origin and facing +x, either from an imported image
 * (see {@link CarSprites}) or as a simple vector drawing matching its {@link CarAppearance} style.
 * The caller translates/rotates the graphics context and sets the scale in pixels per metre.
 */
final class CarPainter {

    /** Cars are never drawn shorter than this on screen, so they stay visible on big tracks. */
    private static final double MIN_LENGTH_PX = 14.0;

    private static final Color TYRE = Color.web("#141414");
    private static final Color GLASS = Color.web("#0b1320", 0.85);
    private static final Color HELMET = Color.web("#f5f5f5");

    private CarPainter() {
    }

    /**
     * @param body    body colour (state-dependent: normal, inspected, dead, ghost)
     * @param sprite  imported top-view image facing right, or null for the vector drawing
     */
    static void paint(GraphicsContext g, CarAppearance appearance, double pixelsPerMetre, Color body, Image sprite) {
        double scale = Math.max(pixelsPerMetre, MIN_LENGTH_PX / appearance.length());
        double length = appearance.length() * scale;
        double width = appearance.width() * scale;

        if (sprite != null) {
            g.setGlobalAlpha(body.getOpacity());
            g.drawImage(sprite, -length / 2, -width / 2, length, width);
            g.setGlobalAlpha(1.0);
        } else {
            switch (appearance.style()) {
                case KART -> kart(g, length, width, body);
                case OPEN_WHEEL -> openWheel(g, length, width, body);
                case HATCHBACK -> closedCar(g, length, width, body, 0.30, 0.18);
                case SPORTS -> closedCar(g, length, width, body, 0.22, 0.30);
                case GENERIC -> closedCar(g, length, width, body, 0.25, 0.22);
            }
        }
    }

    private static void wheels(GraphicsContext g, double length, double width, double inset, double wheelLength,
                               double wheelWidth, double track) {
        g.setFill(TYRE);

        for (double x : new double[]{length / 2 - inset - wheelLength, -length / 2 + inset}) {
            for (double y : new double[]{-track / 2 - wheelWidth / 2, track / 2 - wheelWidth / 2}) {
                g.fillRoundRect(x, y, wheelLength, wheelWidth, wheelWidth / 2, wheelWidth / 2);
            }
        }
    }

    /** Road car: wheels tucked under a rounded body, windscreen, roof and rear window. */
    private static void closedCar(GraphicsContext g, double length, double width, Color body, double bonnet,
                                  double boot) {
        wheels(g, length, width, length * 0.12, length * 0.18, width * 0.22, width * 0.92);

        g.setFill(body);
        g.fillRoundRect(-length / 2, -width / 2 + width * 0.04, length, width * 0.92, width * 0.45, width * 0.45);

        double roofStart = length / 2 - length * bonnet;
        double roofEnd = -length / 2 + length * boot;
        g.setFill(GLASS);
        g.fillRoundRect(roofStart - length * 0.10, -width * 0.36, length * 0.10, width * 0.72, width * 0.15, width * 0.15);
        g.fillRoundRect(roofEnd, -width * 0.33, length * 0.07, width * 0.66, width * 0.12, width * 0.12);

        g.setFill(body.darker());
        g.fillRoundRect(roofEnd + length * 0.07, -width * 0.32, roofStart - length * 0.10 - roofEnd - length * 0.07,
                width * 0.64, width * 0.1, width * 0.1);

        g.setFill(Color.web("#fff6c2"));
        g.fillOval(length / 2 - length * 0.05, -width * 0.42, length * 0.04, width * 0.16);
        g.fillOval(length / 2 - length * 0.05, width * 0.26, length * 0.04, width * 0.16);
    }

    /** Single-seater: narrow body and nose, exposed wheels, front and rear wings, driver's helmet. */
    private static void openWheel(GraphicsContext g, double length, double width, Color body) {
        wheels(g, length, width, length * 0.06, length * 0.13, width * 0.24, width * 0.78);

        g.setFill(body);
        g.fillRect(length / 2 - length * 0.05, -width / 2, length * 0.05, width);
        g.fillRect(-length / 2, -width * 0.38, length * 0.06, width * 0.76);
        g.fillPolygon(new double[]{length / 2 - length * 0.03, length * 0.12, -length * 0.32, -length * 0.44,
                        -length * 0.32, length * 0.12},
                new double[]{0, -width * 0.12, -width * 0.26, 0, width * 0.26, width * 0.12}, 6);
        g.setFill(body.darker());
        g.fillRoundRect(-length * 0.30, -width * 0.30, length * 0.24, width * 0.12, 4, 4);
        g.fillRoundRect(-length * 0.30, width * 0.18, length * 0.24, width * 0.12, 4, 4);

        g.setFill(HELMET);
        g.fillOval(-length * 0.08, -width * 0.10, width * 0.20, width * 0.20);
    }

    /** Kart: tubular frame with the four wheels at the corners and a seated driver. */
    private static void kart(GraphicsContext g, double length, double width, Color body) {
        wheels(g, length, width, 0, length * 0.22, width * 0.26, width * 0.80);

        g.setStroke(body);
        g.setLineWidth(Math.max(1.5, width * 0.08));
        g.strokeRoundRect(-length * 0.36, -width * 0.28, length * 0.72, width * 0.56, width * 0.2, width * 0.2);
        g.setFill(body);
        g.fillRoundRect(length * 0.30, -width * 0.30, length * 0.14, width * 0.60, width * 0.2, width * 0.2);
        g.fillRoundRect(-length * 0.20, -width * 0.20, length * 0.30, width * 0.40, width * 0.15, width * 0.15);

        g.setFill(HELMET);
        g.fillOval(-length * 0.12, -width * 0.14, width * 0.28, width * 0.28);
    }
}
