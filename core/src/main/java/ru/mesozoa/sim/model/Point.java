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

    /**
     * Возвращает расстояние в шагах по квадратной сетке, где разрешены диагонали.
     *
     * В правилах «Мезозои» рейнджеры могут переходить в любую соседнюю клетку,
     * включая диагональные. Поэтому для AI это расстояние точнее манхэттенского:
     * диагональ стоит один шаг, а не два, как будто рейнджер внезапно стал
     * налоговой декларацией и обязан ходить только по клеточкам.
     *
     * @param other другая клетка
     * @return минимальное количество восьминаправленных шагов
     */
    public int chebyshev(Point other) {
        return Math.max(Math.abs(x - other.x), Math.abs(y - other.y));
    }

    /**
     * Возвращает один шаг к цели с учётом диагонального движения.
     *
     * @param target целевая клетка
     * @return соседняя клетка по направлению к цели
     */
    public Point stepToward(Point target) {
        int dx = Integer.compare(target.x, x);
        int dy = Integer.compare(target.y, y);
        return new Point(x + dx, y + dy);
    }

    public List<Point> neighbors4() {
        ArrayList<Point> result = new ArrayList<>(4);
        result.add(new Point(x + 1, y));
        result.add(new Point(x - 1, y));
        result.add(new Point(x, y + 1));
        result.add(new Point(x, y - 1));
        return result;
    }

    /**
     * Возвращает все соседние клетки по восьми направлениям.
     *
     * @return ортогональные и диагональные соседи
     */
    public List<Point> neighbors8() {
        ArrayList<Point> result = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                result.add(new Point(x + dx, y + dy));
            }
        }
        return result;
    }

    /**
     * Проверяет соседство по восьми направлениям или совпадение клетки.
     *
     * @param other другая клетка
     * @return true, если клетки совпадают или касаются стороной/углом
     */
    public boolean isSameOrAdjacent8(Point other) {
        return other != null && chebyshev(other) <= 1;
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
