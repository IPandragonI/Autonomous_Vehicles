package org.autonomous_vehicles.ui;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
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
    private static final double TRAIL_MIN_PIXELS = 2.0;
    private static final double TRAIL_DOT_PIXELS = 1.5;
    /** Border drawn around the view on the static layer: in follow mode at 7 px/m it lasts ~1 s at 200 km/h. */
    private static final double LAYER_MARGIN = 400.0;
    private static final Font OVERLAY_FONT = Font.font("Monospaced", 12);
    private static final Color OVERLAY_BACKGROUND = Color.web("#000000", 0.55);
    private static final Color TRAIL = Color.web("#4ea1ff", 0.45);
    private static final Color RAY = Color.web("#22c55e", 0.8);
    private static final Color RAY_END = Color.web("#22c55e");
    private static final Color CHEQUER_BLACK = Color.web("#111111");
    private static final Color CHECKPOINT_NEXT = Color.web("#facc15");
    private static final Color CHECKPOINT_PASSED = Color.web("#22c55e", 0.5);
    private static final Color CHECKPOINT_AHEAD = Color.web("#ffffff", 0.08);

    /** Static layer (track), redrawn only when the camera moves; {@link #canvas} on top holds what moves. */
    private final Canvas trackLayer = new Canvas();
    private final Canvas canvas = new Canvas();
    private Track layerTrack;
    private double layerScale, layerOffsetX, layerOffsetY, layerWidth, layerHeight;
    private boolean layerValid;
    private Track boundsTrack;
    private double[] bounds;
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
        getChildren().addAll(trackLayer, canvas);
        // The static layer is larger than the view (see drawStaticLayer): clip it to the view.
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        trackLayer.setManaged(false);
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
        draw(frame, List.of());
    }

    /**
     * Draws the frame, then {@code overlay} (e.g. FPS, replay banner) as lines of text in the top-left corner,
     * above the track so camera moves never hide it.
     */
    public void draw(SimulationFrame frame, List<String> overlay) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (frame != null) {
            drawFrame(g, frame);
        } else {
            drawStaticLayer(null);
        }

        drawOverlay(g, overlay);
    }

    /**
     * The static layer (grass, tarmac, kerbs, walls, finish line) is drawn with a {@link #LAYER_MARGIN} border
     * around the view. A camera that only moves (follow mode, dragging) just slides the layer — the GPU composites
     * it for free — and the layer is redrawn only when the move exceeds the margin, the zoom or the size changes.
     * Rasterising those large filled polygons was the frame-rate limit (measured: 7–17 FPS at 1500×900 when redrawn
     * every image; 21 FPS in follow mode where the camera moves every image).
     */
    private void drawStaticLayer(Track track) {
        double width = canvas.getWidth(), height = canvas.getHeight();
        double shiftX = offsetX - layerOffsetX, shiftY = offsetY - layerOffsetY;

        boolean reusable = layerValid && track == layerTrack && scale == layerScale && width == layerWidth
                && height == layerHeight && Math.abs(shiftX) <= LAYER_MARGIN && Math.abs(shiftY) <= LAYER_MARGIN;

        if (!reusable) {
            trackLayer.setWidth(width + 2 * LAYER_MARGIN);
            trackLayer.setHeight(height + 2 * LAYER_MARGIN);
            GraphicsContext g = trackLayer.getGraphicsContext2D();
            g.setFill(BACKGROUND);
            g.fillRect(0, 0, trackLayer.getWidth(), trackLayer.getHeight());

            if (track != null) {
                // Draw in layer coordinates: the view's (0, 0) is the layer's (margin, margin).
                double viewX = offsetX, viewY = offsetY;
                offsetX += LAYER_MARGIN;
                offsetY += LAYER_MARGIN;
                drawTrack(g, track);
                offsetX = viewX;
                offsetY = viewY;
            }

            layerTrack = track;
            layerScale = scale;
            layerOffsetX = offsetX;
            layerOffsetY = offsetY;
            layerWidth = width;
            layerHeight = height;
            layerValid = true;
            shiftX = 0;
            shiftY = 0;
        }

        trackLayer.setTranslateX(shiftX - LAYER_MARGIN);
        trackLayer.setTranslateY(shiftY - LAYER_MARGIN);
    }

    private void drawOverlay(GraphicsContext g, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }

        double lineHeight = 16;
        double width = lines.stream().mapToInt(String::length).max().orElse(0) * 7.2 + 16;
        g.setFill(OVERLAY_BACKGROUND);
        g.fillRoundRect(8, 8, width, lines.size() * lineHeight + 10, 8, 8);
        g.setFill(Color.WHITE);
        g.setFont(OVERLAY_FONT);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.TOP);

        for (int i = 0; i < lines.size(); i++) {
            g.fillText(lines.get(i), 16, 13 + i * lineHeight);
        }
    }

    private void drawFrame(GraphicsContext g, SimulationFrame frame) {
        SimulationFrame.CarSprite focus = frame.cars().stream().filter(car -> car.id() == frame.focusId())
                .findFirst().orElse(null);
        camera(frame.track(), focus);
        drawStaticLayer(frame.track());

        if (frame.nextCheckpoint() >= 0) {
            drawCheckpoints(g, frame.track(), frame.nextCheckpoint());
        }

        drawTrails(g, frame.trails());

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
            g.setStroke(RAY);
            g.setFill(RAY_END);

            for (Vec2 end : frame.rayEnds()) {
                Vec2 screen = toScreen(end);
                g.strokeLine(origin.x(), origin.y(), screen.x(), screen.y());
                g.fillOval(screen.x() - 2.5, screen.y() - 2.5, 5, 5);
            }
        }

        if (focus != null) {
            drawCar(g, focus, focus.alive() ? FOCUS : FOCUS.darker());
        }
    }

    /**
     * Trails as dots {@link #TRAIL_MIN_PIXELS} apart on screen, which read as a line. Measured: 100 trails of 300
     * points stroked as polylines (one per car, or one shared path) held the view at 13–35 FPS against 86–100
     * without trails, although building them took 1.4 ms: an antialiased stroke is rasterised in software into a
     * mask the size of its bounding box (here the whole view) on every image. Small axis-aligned rectangles are
     * drawn by the GPU directly.
     */
    private void drawTrails(GraphicsContext g, List<List<Vec2>> trails) {
        double minSquared = TRAIL_MIN_PIXELS * TRAIL_MIN_PIXELS;
        double size = TRAIL_DOT_PIXELS, half = size / 2;
        g.setFill(TRAIL);

        for (List<Vec2> trail : trails) {
            double lastX = Double.NaN, lastY = Double.NaN;

            for (Vec2 point : trail) {
                double x = offsetX + point.x() * scale;
                double y = offsetY - point.y() * scale;
                double dx = x - lastX, dy = y - lastY;

                if (Double.isNaN(lastX) || dx * dx + dy * dy >= minSquared) {
                    g.fillRect(x - half, y - half, size, size);
                    lastX = x;
                    lastY = y;
                }
            }
        }
    }

    /**
     * The base scale fits the track in the canvas and zoom multiplies it; the view is centred on the track centre
     * plus the pan offset, or on the inspected car in follow mode.
     */
    private void camera(Track track, SimulationFrame.CarSprite focus) {
        if (track != boundsTrack) {
            // Bounding box of the outer wall, once per track (a real circuit has thousands of wall points).
            bounds = new double[]{Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};

            for (Vec2 point : track.getOuterWall()) {
                bounds[0] = Math.min(bounds[0], point.x());
                bounds[1] = Math.min(bounds[1], point.y());
                bounds[2] = Math.max(bounds[2], point.x());
                bounds[3] = Math.max(bounds[3], point.y());
            }

            boundsTrack = track;
        }

        double minX = bounds[0], minY = bounds[1], maxX = bounds[2], maxY = bounds[3];
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
            fillQuad(g, square, square.paint() == TrackDecor.Paint.CHEQUER_BLACK ? CHEQUER_BLACK : Color.WHITE);
        }
    }

    private final double[] quadXs = new double[8];
    private final double[] quadYs = new double[8];

    /**
     * Fills one kerb stripe or chequer square; skipped when entirely off screen (in follow mode most of the
     * hundreds of kerb stripes of a real circuit are).
     */
    private void fillQuad(GraphicsContext g, TrackDecor.Quad quad, Color color) {
        int n = Math.min(quad.xs().length, quadXs.length);
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            quadXs[i] = offsetX + quad.xs()[i] * scale;
            quadYs[i] = offsetY - quad.ys()[i] * scale;
            minX = Math.min(minX, quadXs[i]);
            maxX = Math.max(maxX, quadXs[i]);
            minY = Math.min(minY, quadYs[i]);
            maxY = Math.max(maxY, quadYs[i]);
        }

        // Only called while drawing the static layer, whose coordinates these are.
        if (maxX < 0 || maxY < 0 || minX > trackLayer.getWidth() || minY > trackLayer.getHeight()) {
            return;
        }

        g.setFill(color);
        g.fillPolygon(quadXs, quadYs, n);
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
            Color color = i == next ? CHECKPOINT_NEXT : i < next ? CHECKPOINT_PASSED : CHECKPOINT_AHEAD;

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
