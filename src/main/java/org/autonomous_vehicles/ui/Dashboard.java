package org.autonomous_vehicles.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.autonomous_vehicles.sim.DeathCause;
import org.autonomous_vehicles.sim.TireCompound;

import java.util.Locale;

/**
 * Car dashboard: rev counter with redline, engaged gear, speed, pedals, steering, tyre wear and lap timing.
 */
public class Dashboard extends Canvas {

    private static final Color BACKGROUND = Color.web("#15181d");
    private static final Color TEXT = Color.web("#e6e6e6");
    private static final Color DIM = Color.web("#8b949e");
    private static final double ARC_START = 225;
    private static final double ARC_EXTENT = -270;

    public Dashboard(double width, double height) {
        super(width, height);
    }

    public void draw(Telemetry t) {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        g.setFill(BACKGROUND);
        g.fillRoundRect(0, 0, w, h, 16, 16);

        if (t == null) {
            text(g, "No car to show yet", w / 2, h / 2, 14, DIM, TextAlignment.CENTER, false);
            return;
        }

        text(g, t.car(), 12, 16, 13, TEXT, TextAlignment.LEFT, true);
        status(g, t, w - 12, 16);

        double cx = 95, cy = 118, r = 72;
        tachometer(g, t, cx, cy, r);
        speed(g, t, w - 95, 78);
        pedals(g, t, w - 170, 145, 140);
        tyres(g, t, 12, h - 30, w - 24);
        lap(g, t, w / 2, h - 52);
    }

    private void tachometer(GraphicsContext g, Telemetry t, double cx, double cy, double r) {
        g.setLineWidth(10);
        g.setStroke(Color.web("#2a2f37"));
        g.strokeArc(cx - r, cy - r, 2 * r, 2 * r, ARC_START, ARC_EXTENT, ArcType.OPEN);

        if (!t.hasDrivetrain()) {
            text(g, "–", cx, cy - 8, 44, DIM, TextAlignment.CENTER, true);
            text(g, "simple model:", cx, cy + 26, 11, DIM, TextAlignment.CENTER, false);
            text(g, "no engine or gears", cx, cy + 40, 11, DIM, TextAlignment.CENTER, false);
            return;
        }

        double redZone = 0.9;
        g.setStroke(Color.web("#4ea1ff"));
        g.strokeArc(cx - r, cy - r, 2 * r, 2 * r, ARC_START, ARC_EXTENT * Math.min(t.rpmFraction(), redZone), ArcType.OPEN);

        if (t.rpmFraction() > redZone) {
            g.setStroke(Color.web("#ff3b30"));
            g.strokeArc(cx - r, cy - r, 2 * r, 2 * r, ARC_START + ARC_EXTENT * redZone,
                    ARC_EXTENT * (t.rpmFraction() - redZone), ArcType.OPEN);
        }

        g.setLineWidth(2);
        g.setStroke(Color.web("#ff3b30", 0.6));
        g.strokeArc(cx - r - 8, cy - r - 8, 2 * r + 16, 2 * r + 16, ARC_START + ARC_EXTENT * redZone,
                ARC_EXTENT * (1 - redZone), ArcType.OPEN);

        int thousands = (int) Math.floor(t.redlineRpm() / 1000);

        for (int k = 0; k <= thousands; k++) {
            double angle = Math.toRadians(ARC_START + ARC_EXTENT * k * 1000 / t.redlineRpm());
            double x = cx + (r - 20) * Math.cos(angle);
            double y = cy - (r - 20) * Math.sin(angle);

            if (thousands <= 8 || k % 2 == 0) {
                text(g, String.valueOf(k), x, y, 9, DIM, TextAlignment.CENTER, false);
            }
        }

        double needle = Math.toRadians(ARC_START + ARC_EXTENT * t.rpmFraction());
        g.setStroke(Color.web("#ffb000"));
        g.setLineWidth(3);
        g.strokeLine(cx, cy, cx + (r - 6) * Math.cos(needle), cy - (r - 6) * Math.sin(needle));

        text(g, t.gearLabel(), cx, cy - 4, 40, Color.web("#ffb000"), TextAlignment.CENTER, true);
        text(g, String.format(Locale.ROOT, "%,.0f rpm", t.rpm()), cx, cy + 32, 11, DIM, TextAlignment.CENTER, false);
        text(g, "gear " + t.gearLabel() + " / " + t.gearCount(), cx, cy + 46, 10, DIM, TextAlignment.CENTER, false);
    }

    private void speed(GraphicsContext g, Telemetry t, double x, double y) {
        text(g, String.format(Locale.ROOT, "%.0f", t.speedKmh()), x, y, 46, TEXT, TextAlignment.CENTER, true);
        text(g, "km/h", x, y + 30, 12, DIM, TextAlignment.CENTER, false);
    }

    private void pedals(GraphicsContext g, Telemetry t, double x, double y, double width) {
        bar(g, x, y - 12, width, "Throttle", t.throttle(), Color.web("#22c55e"));
        bar(g, x, y + 10, width, "Brake", t.brake(), Color.web("#ff3b30"));

        double steerY = y + 32;
        text(g, "Steer", x, steerY + 4, 10, DIM, TextAlignment.LEFT, false);
        double sx = x + 50, sw = width - 50;
        g.setFill(Color.web("#2a2f37"));
        g.fillRoundRect(sx, steerY, sw, 8, 4, 4);
        double centre = sx + sw / 2;
        double offset = -t.steer() * sw / 2;
        g.setFill(Color.web("#4ea1ff"));
        g.fillRoundRect(Math.min(centre, centre + offset), steerY, Math.abs(offset), 8, 4, 4);
        g.setFill(TEXT);
        g.fillRect(centre - 1, steerY - 2, 2, 12);
    }

    private void bar(GraphicsContext g, double x, double y, double width, String label, double value, Color color) {
        text(g, label, x, y + 5, 10, DIM, TextAlignment.LEFT, false);
        double bx = x + 50, bw = width - 50;
        g.setFill(Color.web("#2a2f37"));
        g.fillRoundRect(bx, y, bw, 8, 4, 4);
        g.setFill(color);
        g.fillRoundRect(bx, y, bw * Math.clamp(value, 0.0, 1.0), 8, 4, 4);
    }

    private void tyres(GraphicsContext g, Telemetry t, double x, double y, double width) {
        if (t.tire() == null) {
            text(g, "Tyres: unlimited grip (simple model)", x, y + 6, 11, DIM, TextAlignment.LEFT, false);
            return;
        }

        Color compound = switch (t.tire()) {
            case SOFT -> Color.web("#ff3b30");
            case MEDIUM -> Color.web("#facc15");
            case HARD -> Color.web("#f5f5f5");
        };

        g.setStroke(compound);
        g.setLineWidth(3);
        g.strokeOval(x, y - 2, 16, 16);
        text(g, t.tire() + " tyres", x + 24, y + 6, 11, TEXT, TextAlignment.LEFT, false);

        double bx = x + 110, bw = width - 230;
        double life = 1.0 - t.tireWear();
        g.setFill(Color.web("#2a2f37"));
        g.fillRoundRect(bx, y + 2, bw, 8, 4, 4);
        g.setFill(life > 0.5 ? Color.web("#22c55e") : life > 0.25 ? Color.web("#facc15") : Color.web("#ff3b30"));
        g.fillRoundRect(bx, y + 2, bw * life, 8, 4, 4);
        text(g, String.format(Locale.ROOT, "%.0f %% · grip %.0f %%", 100 * life,
                100 * (1 - TireCompound.WEAR_GRIP_LOSS * t.tireWear())), x + width, y + 6, 10, DIM, TextAlignment.RIGHT, false);
    }

    private void lap(GraphicsContext g, Telemetry t, double x, double y) {
        String best = Double.isNaN(t.bestLapTime()) ? "–" : lapTime(t.bestLapTime());
        text(g, String.format(Locale.ROOT, "Lap %d   %s   best %s   checkpoint %d/%d", t.lap(), lapTime(t.currentLapTime()),
                best, t.nextCheckpoint(), t.checkpointCount()), x, y, 12, TEXT, TextAlignment.CENTER, false);
    }

    private void status(GraphicsContext g, Telemetry t, double x, double y) {
        if (t.sliding()) {
            badge(g, "SLIDING", x, y, Color.web("#ff3b30"));
        } else if (!t.alive()) {
            String reason = switch (t.deathCause()) {
                case CRASHED -> "CRASHED";
                case STAGNATED -> "STUCK";
                case TIMEOUT -> "TIME UP";
                case FINISHED -> "FINISHED";
                case ALIVE -> "STOPPED";
            };
            badge(g, reason, x, y, t.deathCause() == DeathCause.FINISHED ? Color.web("#22c55e") : Color.web("#6b7280"));
        }
    }

    private void badge(GraphicsContext g, String label, double right, double y, Color color) {
        double width = 8 + label.length() * 8;
        g.setFill(color);
        g.fillRoundRect(right - width, y - 9, width, 18, 9, 9);
        text(g, label, right - width / 2, y, 11, Color.BLACK, TextAlignment.CENTER, true);
    }

    static String lapTime(double seconds) {
        int minutes = (int) (seconds / 60);
        return String.format(Locale.ROOT, "%d:%05.2f", minutes, seconds - 60 * minutes);
    }

    private static void text(GraphicsContext g, String text, double x, double y, double size, Color color,
                             TextAlignment alignment, boolean bold) {
        g.setFont(Font.font("System", bold ? FontWeight.BOLD : FontWeight.NORMAL, size));
        g.setFill(color);
        g.setTextAlign(alignment);
        g.setTextBaseline(VPos.CENTER);
        g.fillText(text, x, y);
    }
}
