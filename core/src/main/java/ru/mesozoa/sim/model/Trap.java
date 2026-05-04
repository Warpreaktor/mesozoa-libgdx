package ru.mesozoa.sim.model;

public final class Trap {
    public final int playerId;
    public Point position;

    /**
     * Стоит ли ловушка сейчас на карте.
     *
     * Если false, ловушка считается лежащей в инвентаре инженера.
     */
    public boolean active = true;

    /**
     * ID динозавра, который сейчас сидит в этой ловушке.
     *
     * Значение 0 означает пустую ловушку.
     */
    public int trappedDinosaurId = 0;

    public Trap(int playerId, Point position) {
        this.playerId = playerId;
        this.position = position;
    }

    /**
     * Проверяет, удерживает ли ловушка динозавра.
     */
    public boolean hasDinosaur() {
        return trappedDinosaurId != 0;
    }
}
