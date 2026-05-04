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

    /** @deprecated Переходный фасад старого кода. Используй scoutRanger или positionOf(...). */
    @Deprecated(forRemoval = false)
    public Point scout;

    /** @deprecated Переходный фасад старого кода. Используй driverRanger или positionOf(...). */
    @Deprecated(forRemoval = false)
    public Point driver;

    /** @deprecated Переходный фасад старого кода. Используй engineerRanger или positionOf(...). */
    @Deprecated(forRemoval = false)
    public Point engineer;

    /** @deprecated Переходный фасад старого кода. Используй hunterRanger или positionOf(...). */
    @Deprecated(forRemoval = false)
    public Point hunter;

    public final List<Trap> traps = new ArrayList<>();
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
        syncLegacyPositionsFromRangers();
    }

    public void returnTeamToBase(Point base) {
        for (Ranger ranger : rangers()) {
            ranger.setPosition(base);
        }
        syncLegacyPositionsFromRangers();
    }

    /**
     * Синхронизирует старые публичные поля позиций с объектами фигурок.
     *
     * Пока часть UI/action/AI кода читает player.hunter и похожие поля напрямую,
     * этот метод держит старый фасад в актуальном состоянии. Да, переходные слои
     * не украшают архитектуру, но зато проект не разваливается как картонный мост.
     */
    public void syncLegacyPositionsFromRangers() {
        scout = scoutRanger.position();
        driver = driverRanger.position();
        engineer = engineerRanger.position();
        hunter = hunterRanger.position();
    }
}
