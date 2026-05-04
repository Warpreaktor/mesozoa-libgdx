package ru.mesozoa.sim.map;

import ru.mesozoa.sim.model.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PathFinder {

    /**
     * Восстанавливает путь BFS от старта до найденной цели.
     */
    private List<Point> restorePath(HashMap<Point, Point> previous, Point target) {
        ArrayList<Point> path = new ArrayList<>();
        Point step = target;

        while (step != null) {
            path.add(0, step);
            step = previous.get(step);
        }

        return path;
    }
}
