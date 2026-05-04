package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.Species;

public final class Dinosaur {

    /** Уникальный идентификатор динозавра в рамках партии. */
    public final int id;

    /** Вид динозавра и связанные с ним правила движения/поимки. */
    public final Species species;

    /** Текущая клетка динозавра на карте. */
    public Point position;

    /** Индекс текущей точки в био-тропе вида. */
    public int trailIndex;

    /** true, если динозавр уже доставлен в штаб и засчитан игроку. */
    public boolean captured;

    /** true, если динозавр обездвижен в ловушке и ждёт вывоза водителем. */
    public boolean trapped;

    /** ID игрока, чья ловушка держит динозавра, или 0, если динозавр не в ловушке. */
    public int trappedByPlayerId;

    /** true, если динозавр убран с карты без зачёта по заданию. */
    public boolean removed;

    /** Клетка, с которой динозавр пришёл на текущую позицию. */
    public Point lastPosition;

    public Dinosaur(int id, Species species, Point position) {
        this.id = id;
        this.species = species;
        this.position = position;
        this.lastPosition = position;
        this.trailIndex = 0;
        this.trapped = false;
        this.trappedByPlayerId = 0;
    }
}
