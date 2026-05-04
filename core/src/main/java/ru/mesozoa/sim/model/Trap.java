package ru.mesozoa.sim.model;

public final class Trap {
    /** ID игрока, которому принадлежит ловушка. */
    public final int playerId;

    /** Клетка, где ловушка лежит на карте. */
    public Point position;

    /** true, если ловушка находится на карте и занимает лимит инвентаря. */
    public boolean active = true;

    /** ID динозавра, сидящего в этой ловушке, или 0, если ловушка пустая. */
    public int trappedDinosaurId = 0;

    public Trap(int playerId, Point position) {
        this.playerId = playerId;
        this.position = position;
    }

    /**
     * Проверяет, удерживает ли ловушка динозавра.
     *
     * @return true, если ловушка занята пойманным, но ещё не вывезенным динозавром
     */
    public boolean holdsDinosaur() {
        return trappedDinosaurId > 0;
    }

    /**
     * Проверяет, может ли ловушка сработать на нового динозавра.
     *
     * @return true, если ловушка активна и ещё никого не удерживает
     */
    public boolean canCatchDinosaur() {
        return active && !holdsDinosaur();
    }
}
