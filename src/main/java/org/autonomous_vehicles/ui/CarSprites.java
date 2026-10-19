package org.autonomous_vehicles.ui;

import javafx.scene.image.Image;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Optional top-view images of the cars, one per car type, used instead of the vector drawings.
 * <p>
 * Convention: a PNG (transparent background recommended) seen from above with the <b>front of the car pointing
 * right</b>; it is stretched to the car's real length × width. A file chosen in the UI is remembered between
 * sessions (Java preferences); a resource {@code /cars/<type>.png} on the classpath is used when no file is chosen.
 */
public final class CarSprites {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(CarSprites.class);
    private static final Map<String, Image> CACHE = new HashMap<>();

    private CarSprites() {
    }

    /**
     * @return the image for this car type, or null to use the vector drawing
     */
    public static Image get(String carType) {
        return CACHE.computeIfAbsent(carType, CarSprites::load);
    }

    /**
     * Uses {@code file} for this car type from now on.
     *
     * @throws IllegalArgumentException if the file is not a readable image
     */
    public static void set(String carType, File file) {
        Image image = new Image(file.toURI().toString());

        if (image.isError() || image.getWidth() <= 0) {
            throw new IllegalArgumentException("Cannot read an image from " + file.getName() + ".");
        }

        PREFERENCES.put(key(carType), file.getAbsolutePath());
        CACHE.put(carType, image);
    }

    /**
     * Goes back to the built-in drawing for this car type.
     */
    public static void clear(String carType) {
        PREFERENCES.remove(key(carType));
        CACHE.put(carType, loadResource(carType));
    }

    private static Image load(String carType) {
        String path = PREFERENCES.get(key(carType), null);

        if (path != null && new File(path).isFile()) {
            Image image = new Image(new File(path).toURI().toString());

            if (!image.isError()) {
                return image;
            }
        }

        return loadResource(carType);
    }

    private static Image loadResource(String carType) {
        try (InputStream stream = CarSprites.class.getResourceAsStream("/cars/" + carType + ".png")) {
            return stream == null ? null : new Image(stream);
        } catch (Exception e) {
            return null;
        }
    }

    private static String key(String carType) {
        return "sprite." + carType;
    }
}
