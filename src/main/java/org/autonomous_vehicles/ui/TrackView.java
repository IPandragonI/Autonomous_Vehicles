package org.autonomous_vehicles.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.autonomous_vehicles.geometry.Vec2;
import org.autonomous_vehicles.sim.Checkpoint;
import org.autonomous_vehicles.sim.Track;
import org.autonomous_vehicles.sim.Vehicles;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws a {@link SimulationFrame}: tarmac and walls, checkpoints, trails, cars, rays and ghost. Contains no
 * simulation rule; world y points up, screen y points down.
 * <p>
 * Camera: the whole track fits by default; the mouse wheel zooms around the cursor, dragging pans, a double-click
 * resets. In follow mode the camera stays centred on the inspected car. A click without dragging selects a car.
 */
public class TrackView extends Pane {

    private static final Color BACKGROUND = Color.web("#2d4a2b");
    private static final Color TARMAC = Color.web("#3b4048");
    private static final Color WALL = Color.web("#f2f2f2");
    private static final Color KERB_RED = Color.web("#d7263d");
    private static final Color KERB_WHITE = Color.web("#f4f4f4");
    private static final Color CAR = Color.web("#4ea1ff");
    private static final Color FOCUS = Color.web("#ffb000");
    private static final Color DEAD = Color.web("#6b7280", 0.6);
    private static final Color GHOST = Color.web("#c084fc", 0.55);
    private static final double FOLLOW_PIXELS_PER_METRE = 7.0;

    private final Canvas canvas = new Canvas();
    private double scale = 1.0;
    private double offsetX;
    private double offsetY;
    private Consumer<Vec2> clickListener = point -> { };

    private double zoom = 1.0;
    private Vec2 pan = new Vec2(0, 0);
    private boolean follow;
    private double pressX;
    private double pressY;
    private double lastDragX;
    private double lastDragY;

    private Track decoratedTrack;
    private TrackDecor decor;

    private String carType = Vehicles.KINEMATIC;
    private CarAppearance appearance = CarAppearance.forType(Vehicles.KINEMATIC);
    private Image sprite;

    public TrackView() {
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        setMinSize(300, 200);

        canvas.setOnScroll(event -> zoomAround(event.getX(), event.getY(), event.getDeltaY() > 0 ? 1.2 : 1 / 1.2));
        canvas.setOnMousePressed(event -> {
            pressX = lastDragX = event.getX();
            pressY = lastDragY = event.getY();
        });
        canvas.setOnMouseDragged(event -> {
            if (!follow) {
                pan = pan.subtract(new Vec2((event.getX() - lastDragX) / scale, -(event.getY() - lastDragY) / scale));
            }

            lastDragX = event.getX();
            lastDragY = event.getY();
        });
        canvas.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                resetView();
            } else if (Math.hypot(event.getX() - pressX, event.getY() - pressY) < 4) {
                clickListener.accept(toWorld(event.getX(), event.getY()));
            }
        });
    }

    /**
     * Multiplies the zoom by {@code factor} keeping the world point under the screen point (x, y) in place.
     */
    private void zoomAround(double x, double y, double factor) {
        double newZoom = Math.clamp(zoom * factor, 0.5, 200.0);
        double applied = newZoom / zoom;
        zoom = newZoom;

        if (follow) {
            return;
        }

        Vec2 under = toWorld(x, y);
        Vec2 centre = toWorld(canvas.getWidth() / 2.0, canvas.getHeight() / 2.0);
        double newScale = scale * applied;
        Vec2 newCentre = new Vec2(under.x() - (x - canvas.getWidth() / 2.0) / newScale,
                under.y() + (y - canvas.getHeight() / 2.0) / newScale);
        pan = pan.add(newCentre.subtract(centre));
    }

    public void setOnWorldClick(Consumer<Vec2> listener) {
        this.clickListener = listener;
    }

    /**
     * Selects the drawing (and imported image, if any) of a car type.
     */
    public void setCarType(String carType) {
        this.carType = carType;
        this.appearance = CarAppearance.forType(carType);
        this.sprite = CarSprites.get(carType);
    }

    /**
     * Re-reads the imported image of the current car type (after importing or clearing one).
     */
    public void refreshSprite() {
        this.sprite = CarSprites.get(carType);
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
        zoom = 1.0;
    }

    public void resetView() {
        zoom = 1.0;
        pan = new Vec2(0, 0);
    }

    public void draw(SimulationFrame frame) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(BACKGROUND);
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (frame == null) {
            return;
        }

        SimulationFrame.CarSprite focus = frame.cars().stream().filter(car -> car.id() == frame.focusId())
                .findFirst().orElse(null);
        camera(frame.track(), focus);
        drawTrack(g, frame.track());

        if (frame.nextCheckpoint() >= 0) {
            drawCheckpoints(g, frame.track(), frame.nextCheckpoint());
        }

        g.setLineWidth(1.0);
        g.setStroke(Color.web("#4ea1ff", 0.25));

        for (List<Vec2> trail : frame.trails()) {
            strokePolyline(g, trail, false);
        }

        if (frame.ghost() != null) {
            drawCar(g, frame.ghost(), GHOST);
        }

        for (SimulationFrame.CarSprite car : frame.cars()) {
            if (car != focus) {
                drawCar(g, car, car.alive() ? CAR : DEAD);
            }
        }

        if (frame.rayOrigin() != null) {
            Vec2 origin = toScreen(frame.rayOrigin());
            g.setLineWidth(1.0);

            for (Vec2 end : frame.rayEnds()) {
                Vec2 screen = toScreen(end);
                g.setStroke(Color.web("#22c55e", 0.8));
                g.strokeLine(origin.x(), origin.y(), screen.x(), screen.y());
                g.setFill(Color.web("#22c55e"));
                g.fillOval(screen.x() - 2.5, screen.y() - 2.5, 5, 5);
            }
        }

        if (focus != null) {
            drawCar(g, focus, focus.alive() ? FOCUS : FOCUS.darker());
        }
    }

    /**
     * The base scale fits the track in the canvas and zoom multiplies it; the view is centred on the track centre
     * plus the pan offset, or on the inspected car in follow mode.
     */
    private void camera(Track track, SimulationFrame.CarSprite focus) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (Vec2 point : track.getOuterWall()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }

        double margin = 20.0;
        double fitScale = Math.max(0.01, Math.min((canvas.getWidth() - 2 * margin) / (maxX - minX),
                (canvas.getHeight() - 2 * margin) / (maxY - minY)));
        Vec2 centre;

        if (follow && focus != null) {
            scale = Math.max(fitScale, FOLLOW_PIXELS_PER_METRE) * zoom;
            centre = new Vec2(focus.x(), focus.y());
        } else {
            scale = fitScale * zoom;
            centre = new Vec2((minX + maxX) / 2, (minY + maxY) / 2).add(pan);
        }

        offsetX = canvas.getWidth() / 2.0 - centre.x() * scale;
        offsetY = canvas.getHeight() / 2.0 + centre.y() * scale;
    }

    private Vec2 toScreen(Vec2 world) {
        return new Vec2(offsetX + world.x() * scale, offsetY - world.y() * scale);
    }

    private Vec2 toWorld(double screenX, double screenY) {
        return new Vec2((screenX - offsetX) / scale, (offsetY - screenY) / scale);
    }

    /**
     * Grass all around, tarmac between the walls, red-and-white kerbs in the bends, white track limits and a
     * chequered finish line. The decoration is computed once per track.
     */
    private void drawTrack(GraphicsContext g, Track track) {
        if (track != decoratedTrack) {
            decoratedTrack = track;
            decor = TrackDecor.of(track);
        }

        fillPolygon(g, track.getOuterWall(), TARMAC);
        fillPolygon(g, track.getInnerWall(), BACKGROUND);

        // Below ~1 px per kerb stripe the pattern only adds noise: draw the kerbs once they can be seen.
        if (scale * TrackDecor.KERB_WIDTH >= 1.0) {
            for (TrackDecor.Quad kerb : decor.kerbs()) {
                fillQuad(g, kerb, kerb.paint() == TrackDecor.Paint.KERB_RED ? KERB_RED : KERB_WHITE);
            }
        }

        g.setStroke(WALL);
        g.setLineWidth(Math.clamp(scale * 0.3, 1.0, 3.0));
        strokePolyline(g, track.getOuterWall(), true);
        strokePolyline(g, track.getInnerWall(), true);

        for (TrackDecor.Quad square : decor.finishLine()) {
            fillQuad(g, square, square.paint() == TrackDecor.Paint.CHEQUER_BLACK ? Color.web("#111111") : Color.WHITE);
        }
    }

    private void fillQuad(GraphicsContext g, TrackDecor.Quad quad, Color color) {
        double[] xs = new double[quad.xs().length];
        double[] ys = new double[quad.ys().length];

        for (int i = 0; i < xs.length; i++) {
            xs[i] = offsetX + quad.xs()[i] * scale;
            ys[i] = offsetY - quad.ys()[i] * scale;
        }

        g.setFill(color);
        g.fillPolygon(xs, ys, xs.length);
    }

    private void fillPolygon(GraphicsContext g, List<Vec2> polygon, Color color) {
        double[] xs = new double[polygon.size()];
        double[] ys = new double[polygon.size()];

        for (int i = 0; i < polygon.size(); i++) {
            Vec2 screen = toScreen(polygon.get(i));
            xs[i] = screen.x();
            ys[i] = screen.y();
        }

        g.setFill(color);
        g.fillPolygon(xs, ys, xs.length);
    }

    /**
     * Checkpoints already passed in the current lap are lit green, the next one yellow, the finish line red.
     */
    private void drawCheckpoints(GraphicsContext g, Track track, int next) {
        List<Checkpoint> checkpoints = track.getCheckpoints();
        g.setLineWidth(1.5);

        for (int i = 0; i < checkpoints.size(); i++) {
            Color color = i == next ? Color.web("#facc15") : i < next ? Color.web("#22c55e", 0.5) : Color.web("#ffffff", 0.08);

            if (i == checkpoints.size() - 1 && i != next) {
                continue; // the finish line is painted as a chequer
            }

            g.setStroke(color);
            Vec2 a = toScreen(checkpoints.get(i).gate().start());
            Vec2 b = toScreen(checkpoints.get(i).gate().end());
            g.strokeLine(a.x(), a.y(), b.x(), b.y());
        }
    }

    private void drawCar(GraphicsContext g, SimulationFrame.CarSprite car, Color color) {
        Vec2 center = toScreen(new Vec2(car.x(), car.y()));

        g.save();
        g.translate(center.x(), center.y());
        g.rotate(-Math.toDegrees(car.angle()));
        CarPainter.paint(g, appearance, scale, color, sprite);
        g.restore();
    }

    private void strokePolyline(GraphicsContext g, List<Vec2> points, boolean closed) {
        if (points.size() < 2) {
            return;
        }

        int count = points.size() + (closed ? 1 : 0);
        double[] xs = new double[count];
        double[] ys = new double[count];

        for (int i = 0; i < count; i++) {
            Vec2 screen = toScreen(points.get(i % points.size()));
            xs[i] = screen.x();
            ys[i] = screen.y();
        }

        g.strokePolyline(xs, ys, count);
    }
}
