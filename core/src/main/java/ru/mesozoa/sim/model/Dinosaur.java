package ru.mesozoa.sim.model;

public final class Dinosaur {
    public final int id;
    public final Species species;
    public Point position;
    public int trailIndex;
    public boolean captured;
    public boolean removed;

    /**
     * Находится ли динозавр сейчас в выставленной ловушке.
     *
     * Динозавр в ловушке остаётся на карте, не двигается в фазу динозавров и
     * ещё не считается доставленным в штаб.
     */
    public boolean trapped;

    /**
     * ID игрока, чья ловушка удерживает динозавра.
     *
     * Значение 0 означает, что динозавр не удерживается ловушкой.
     */
    public int trappedByPlayerId;

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
