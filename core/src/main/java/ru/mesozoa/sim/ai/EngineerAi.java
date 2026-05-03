package ru.mesozoa.sim.ai;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.simulation.GameSimulation;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class EngineerAi {

    private final GameSimulation simulation;

    public EngineerAi(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Инженер получает высокий вес, если на карте есть нужный S-динозавр,
     * который ловится ловушкой.
     */
    public double scoreEngineer(PlayerState player) {
        if (activeTrapCount(player) >= simulation.inventoryConfig.maxTrapsPerPlayer) {
            return -50.0;
        }

        Optional<Dinosaur> target = nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP);
        if (target.isEmpty()) {
            return 8.0;
        }

        int distance = player.engineer.manhattan(target.get().position);
        double score = 80.0 - Math.min(45.0, distance * 6.0);

        if (isAdjacentOrSame(player.engineer, target.get().position)) {
            score += 45.0;
        }

        return score;
    }

    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        EnumSet<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(List.of(methods));

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }

    private int activeTrapCount(PlayerState player) {
        return (int) player.traps.stream()
                .filter(trap -> trap.active)
                .count();
    }

    private boolean isAdjacentOrSame(Point a, Point b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return dx <= 1 && dy <= 1;
    }
}
