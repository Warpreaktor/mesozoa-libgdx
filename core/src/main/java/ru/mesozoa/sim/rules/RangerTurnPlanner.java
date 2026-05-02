package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор двух рейнджеров для хода игрока.
 *
 * Здесь живёт именно AI-решение "кем ходить".
 * Исполнение действий находится в RangerActionExecutor.
 */
public final class RangerTurnPlanner {
    private final GameSimulation simulation;

    public RangerTurnPlanner(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Упрощённый AI выбора двух фигурок.
     *
     * Приоритеты:
     * 1. Разведчик, пока есть тайлы в мешке.
     * 2. Инженер, если есть нужная цель для ловушки.
     * 3. Охотник, если есть нужная цель для охоты/выслеживания.
     * 4. Водитель, если команда растянулась и ему есть куда подтягиваться.
     * 5. Добивка дефолтными ролями, чтобы игрок активировал две разные фигурки.
     */
    public List<RangerRole> chooseTwoRangersForTurn(PlayerState player) {
        ArrayList<RangerRole> roles = new ArrayList<>(2);

        if (!simulation.tileBag.isEmpty()) {
            roles.add(RangerRole.SCOUT);
        }

        if (roles.size() < 2 && hasUsefulEngineerAction(player) && !roles.contains(RangerRole.ENGINEER)) {
            roles.add(RangerRole.ENGINEER);
        }

        if (roles.size() < 2 && hasUsefulHunterAction(player) && !roles.contains(RangerRole.HUNTER)) {
            roles.add(RangerRole.HUNTER);
        }

        if (roles.size() < 2 && hasUsefulDriverAction(player) && !roles.contains(RangerRole.DRIVER)) {
            roles.add(RangerRole.DRIVER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.HUNTER)) {
            roles.add(RangerRole.HUNTER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.ENGINEER)) {
            roles.add(RangerRole.ENGINEER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.DRIVER)) {
            roles.add(RangerRole.DRIVER);
        }

        if (roles.size() < 2 && !roles.contains(RangerRole.SCOUT)) {
            roles.add(RangerRole.SCOUT);
        }

        return List.copyOf(roles.subList(0, Math.min(2, roles.size())));
    }

    public String roleListToText(List<RangerRole> roles) {
        ArrayList<String> names = new ArrayList<>();
        for (RangerRole role : roles) {
            names.add(roleToText(role));
        }
        return String.join(" + ", names);
    }

    private String roleToText(RangerRole role) {
        return switch (role) {
            case SCOUT -> "разведчик";
            case DRIVER -> "водитель";
            case ENGINEER -> "инженер";
            case HUNTER -> "охотник";
        };
    }

    private boolean hasUsefulEngineerAction(PlayerState player) {
        if (player.traps.stream().filter(t -> t.active).count() >= simulation.inventoryConfig.maxTrapsPerPlayer) {
            return false;
        }

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .anyMatch(d -> d.species.captureMethod == CaptureMethod.TRAP);
    }

    private boolean hasUsefulHunterAction(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .anyMatch(d -> d.species.captureMethod == CaptureMethod.TRACKING
                        || d.species.captureMethod == CaptureMethod.HUNT);
    }

    private boolean hasUsefulDriverAction(PlayerState player) {
        Point target;

        if (!player.driver.equals(player.hunter)) {
            target = player.hunter;
        } else if (!player.driver.equals(player.engineer)) {
            target = player.engineer;
        } else {
            target = player.scout;
        }

        return !player.driver.equals(target) && simulation.map.hasDriverPath(player.driver, target);
    }
}
