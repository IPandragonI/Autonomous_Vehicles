package org.autonomous_vehicles.sim;

public record Environment(double width, double height) {

    public void update(Car car, double deltaTime) {
        if (car.isAlive()) {
            car.update(deltaTime);
            if (!isInside(car)) {
                car.crash();
            }
        }
    }

    public boolean isInside(Car car) {
        double x = car.getX();
        double y = car.getY();
        return x >= 0 && x <= width && y >= 0 && y <= height;
    }
}
