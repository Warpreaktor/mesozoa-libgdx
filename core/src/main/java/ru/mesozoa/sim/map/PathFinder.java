package ru.mesozoa.sim.map;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * Общие небольшие pathfinding-утилиты.
 *
 * Полноценные правила движения всё ещё живут в GameMap и профильных AI-классах,
 * а здесь остаются только переиспользуемые технические операции: восстановление
 * BFS-пути и простой шаг разведчика по уже открытым клеткам. Да, наконец-то
 * кусок маршрутизации перестал прятаться в GameSimulation, где ему было так же
 * уютно, как мосту в бухгалтерии.
 */
public final class PathFinder {

    private PathFinder() {
    }

    /**
     * Восстанавливает путь BFS от старта до найденной цели.
     *
     * @param previous карта предыдущих клеток, построенная BFS-обходом
     * @param target конечная клетка
     * @return путь от старта до target включительно
     */
    public static List<Point> restorePath(HashMap<Point, Point> previous, Point target) {
        ArrayList<Point> path = new ArrayList<>();
        Point step = target;

        while (step != null) {
            path.add(step);
            step = previous.get(step);
        }

        Collections.reverse(path);
        return path;
    }

    /**
     * Возвращает жадный шаг по открытой карте в сторону цели.
     *
     * Метод используется для свободного перемещения по открытой карте. Шаг идёт
     * сразу по восьминаправленной сетке: если цель диагональна, фигурка делает
     * диагональный шаг, а не изображает шахматную ладью на нервном срыве.
     *
     * @param map карта партии
     * @param from текущая клетка
     * @param target целевая клетка
     * @param passableRule правило допустимости открытой клетки
     * @return следующая клетка или from, если шаг невозможен
     */
    public static Point stepTowardPlaced(
            GameMap map,
            Point from,
            Point target,
            Predicate<Point> passableRule
    ) {
        if (from == null || target == null || map == null) {
            return from;
        }

        Point direct = from.stepToward(target);
        if (map.isPlaced(direct) && passableRule.test(direct)) {
            return direct;
        }

        return map.placedNeighbors(from).stream()
                .filter(passableRule)
                .min((left, right) -> Integer.compare(left.chebyshev(target), right.chebyshev(target)))
                .orElse(from);
    }

    /**
     * Проверяет базовую проходимость клетки для старого режима движения разведчика.
     *
     * Разведчик теперь не тратит очки на перемещение, но метод оставлен для общих
     * восьминаправленных утилит pathfinding-а и возможного ручного управления.
     *
     * @param map карта партии
     * @param point проверяемая клетка
     * @return true, если клетка уже открыта
     */
    public static boolean isScoutPassable(GameMap map, Point point) {
        return map != null && point != null && map.isPlaced(point);
    }
}
