package ru.mesozoa.sim.ranger;

import ru.mesozoa.sim.model.AiScore;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

/**
 * Конкретный план активации рейнджера.
 *
 * План связывает AI-оценку с исполнением: какая фигурка ходит, зачем она выбрана,
 * какой тип действия ожидается и какая точка считается целевой.
 */
public final class RangerPlan {
    private final Ranger ranger;
    private final RangerPlanType type;
    private final AiScore score;
    private final Point target;

    public RangerPlan(Ranger ranger, RangerPlanType type, AiScore score, Point target) {
        this.ranger = ranger;
        this.type = type;
        this.score = score;
        this.target = target;
    }

    public Ranger ranger() {
        return ranger;
    }

    public RangerRole role() {
        return ranger.role();
    }

    public RangerPlanType type() {
        return type;
    }

    public AiScore score() {
        return score;
    }

    public Point target() {
        return target;
    }

    public double scoreValue() {
        return score.value();
    }

    public String reason() {
        return score.reason();
    }
}
