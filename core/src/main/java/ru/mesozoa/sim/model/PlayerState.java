package ru.mesozoa.sim.model;

import ru.mesozoa.sim.ranger.Driver;
import ru.mesozoa.sim.ranger.Engineer;
import ru.mesozoa.sim.ranger.Hunter;
import ru.mesozoa.sim.ranger.Ranger;
import ru.mesozoa.sim.ranger.Scout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class PlayerState {
    public final int id;
    public final RangerColor color;

    public final Set<Species> task = EnumSet.noneOf(Species.class);
    public final Set<Species> captured = EnumSet.noneOf(Species.class);

    /** Фигурка разведчика игрока. */
    public final Scout scoutRanger;

    /** Фигурка водителя игрока. */
    public final Driver driverRanger;

    /** Фигурка инженера игрока. */
    public final Engineer engineerRanger;

    /** Фигурка охотника игрока. */
    public final Hunter hunterRanger;

    public final List<Trap> traps = new ArrayList<>();

    /** Активная засада охотника на M-хищника или null, если охота не начата. */
    public HuntAmbush activeHunt;

    /** Количество оставшейся мясной приманки для охоты. */
    public int hunterBait = 3;
    public int turnsSkipped = 0;

    public PlayerState(int id, Point base) {
        this(id, RangerColor.forPlayerId(id), base);
    }

    public PlayerState(int id, RangerColor color, Point base) {
        this.id = id;
        this.color = color;
        this.scoutRanger = new Scout(base);
        this.driverRanger = new Driver(base);
        this.engineerRanger = new Engineer(base);
        this.hunterRanger = new Hunter(base);
    }

    public boolean isComplete() {
        return captured.containsAll(task);
    }

    public boolean needs(Species species) {
        return task.contains(species) && !captured.contains(species);
    }

    /**
     * Проверяет, лежит ли охотник в активной засаде.
     *
     * @return true, если охотник уже начал фазу охоты с приманкой
     */
    public boolean hasActiveHunt() {
        return activeHunt != null;
    }

    /**
     * Возвращает фигурку по роли.
     *
     * @param role роль в команде игрока
     * @return объект конкретного рейнджера
     */
    public Ranger rangerFor(RangerRole role) {
        return switch (role) {
            case SCOUT -> scoutRanger;
            case DRIVER -> driverRanger;
            case ENGINEER -> engineerRanger;
            case HUNTER -> hunterRanger;
        };
    }

    /**
     * Возвращает все фигурки команды.
     *
     * @return список рейнджеров игрока
     */
    public List<Ranger> rangers() {
        return List.of(scoutRanger, engineerRanger, hunterRanger, driverRanger);
    }

    public Point positionOf(RangerRole role) {
        return rangerFor(role).position();
    }

    public void setPosition(RangerRole role, Point position) {
        rangerFor(role).setPosition(position);
    }

    public void returnTeamToBase(Point base) {
        for (Ranger ranger : rangers()) {
            ranger.setPosition(base);
        }
        activeHunt = null;
    }


}
