package ru.mesozoa.sim.tile;

import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.Point;

/**
 * Стартовый тайл экспедиции.
 *
 * BaseTile не является обычным тайлом местности: у него нет биома, на нём не
 * могут появляться динозавры, он не попадает в мешочки тайлов и не участвует
 * в автодостройке биомов. Это отдельная фиксированная точка старта и компас
 * игрового поля.
 */
public final class BaseTile {

    /**
     * Фиксированная координата базы на динамической карте.
     */
    public static final Point DEFAULT_POSITION = new Point(0, 0);

    /**
     * Путь к изображению базы-компаса в assets.
     */
    public static final String IMAGE_PATH = "tiles/landing.png";

    /**
     * Координата базы на игровом поле.
     */
    public final Point position;

    /**
     * Направление, считающееся севером относительно базы.
     */
    public final Direction north = Direction.NORTH;

    /**
     * Направление, считающееся востоком относительно базы.
     */
    public final Direction east = Direction.EAST;

    /**
     * Направление, считающееся югом относительно базы.
     */
    public final Direction south = Direction.SOUTH;

    /**
     * Направление, считающееся западом относительно базы.
     */
    public final Direction west = Direction.WEST;

    /**
     * Создаёт базовый тайл в стандартной координате (0, 0).
     */
    public BaseTile() {
        this(DEFAULT_POSITION);
    }

    /**
     * Создаёт базовый тайл в заданной координате.
     *
     * Конструктор оставлен параметризованным, чтобы позже можно было сделать
     * сценарии с другой стартовой точкой, если внезапно картонка потребует ещё
     * больше уважения к себе.
     *
     * @param position координата базы на игровом поле
     */
    public BaseTile(Point position) {
        this.position = position;
    }
}
