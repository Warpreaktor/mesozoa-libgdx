package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/**
 * Базовая реализация общего состояния рейнджера.
 *
 * Здесь хранится одинаковое для всех фигурок состояние: роль, позиция и очки
 * действий. Специальные вещи вроде приманок и ловушек пока остаются в PlayerState
 * как переходный слой, чтобы не превратить рефакторинг в метеорит.
 */
public abstract class AbstractRanger implements Ranger {

    private final RangerRole role;
    private final String displayName;
    private final int maxActionPoints;
    private Point position;
    private int currentActionPoints;

    protected AbstractRanger(RangerRole role, String displayName, Point startPosition, int maxActionPoints) {
        this.role = role;
        this.displayName = displayName;
        this.position = startPosition;
        this.maxActionPoints = maxActionPoints;
        this.currentActionPoints = 0;
    }

    @Override
    public RangerRole role() {
        return role;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Point position() {
        return position;
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public int maxActionPoints() {
        return maxActionPoints;
    }

    @Override
    public int currentActionPoints() {
        return currentActionPoints;
    }

    @Override
    public void startActivation() {
        currentActionPoints = maxActionPoints;
    }

    @Override
    public void spendActionPoints(int points) {
        currentActionPoints = Math.max(0, currentActionPoints - Math.max(0, points));
    }

    @Override
    public void finishActivation() {
        currentActionPoints = 0;
    }
}
