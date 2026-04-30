package ru.mesozoa.sim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Point {
    public final int x;
    public final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int manhattan(Point other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }

    public Point stepToward(Point target) {
        int dx = Integer.compare(target.x, x);
        int dy = Integer.compare(target.y, y);
        if (Math.abs(target.x - x) >= Math.abs(target.y - y)) {
            return new Point(x + dx, y);
        }
        return new Point(x, y + dy);
    }

    public List<Point> neighbors4() {
        ArrayList<Point> result = new ArrayList<>(4);
        result.add(new Point(x + 1, y));
        result.add(new Point(x - 1, y));
        result.add(new Point(x, y + 1));
        result.add(new Point(x, y - 1));
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Point point)) return false;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
