package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Выбор рейнджера для очередной активации игрока.
 *
 * Planner больше не выбирает сразу две роли в начале хода игрока.
 * Каждая активация выбирается отдельно, прямо перед выполнением действия.
 * Поэтому если разведчик открыл тайл со спауном, следующая роль выбирается
 * уже с учётом нового динозавра на карте.
 */
public final class RangerTurnPlanner {
    private final GameSimulation simulation;

    public RangerTurnPlanner(GameSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Выбирает следующую роль для текущего игрока.
     *
     * @param player игрок, который сейчас ходит
     * @param alreadyUsedRoles роли, уже активированные этим игроком в текущем ходе
     * @return лучшая роль для следующей активации или null, если доступных ролей нет
     */
    public RangerRole chooseNextRangerForTurn(PlayerState player, Set<RangerRole> alreadyUsedRoles) {
        return List.of(RangerRole.SCOUT, RangerRole.ENGINEER, RangerRole.HUNTER, RangerRole.DRIVER)
                .stream()
                .filter(role -> !alreadyUsedRoles.contains(role))
                .max(Comparator.comparingDouble(role -> scoreRole(player, role)))
                .orElse(null);
    }

    /**
     * Старый совместимый метод: возвращает две роли, но выбирает их через ту же весовую систему.
     *
     * Используется только там, где ещё нужен полный ход без пошагового разбиения.
     */
    public List<RangerRole> chooseTwoRangersForTurn(PlayerState player) {
        EnumSet<RangerRole> usedRoles = EnumSet.noneOf(RangerRole.class);
        ArrayList<RangerRole> roles = new ArrayList<>(2);

        for (int i = 0; i < 2; i++) {
            RangerRole role = chooseNextRangerForTurn(player, usedRoles);
            if (role == null) break;

            roles.add(role);
            usedRoles.add(role);
        }

        return List.copyOf(roles);
    }

    public String roleListToText(List<RangerRole> roles) {
        ArrayList<String> names = new ArrayList<>();
        for (RangerRole role : roles) {
            names.add(roleToText(role));
        }
        return String.join(" + ", names);
    }

    public String roleToText(RangerRole role) {
        return switch (role) {
            case SCOUT -> "разведчик";
            case DRIVER -> "водитель";
            case ENGINEER -> "инженер";
            case HUNTER -> "охотник";
        };
    }

    private double scoreRole(PlayerState player, RangerRole role) {
        return switch (role) {
            case SCOUT -> scoreScout(player);
            case ENGINEER -> scoreEngineer(player);
            case HUNTER -> scoreHunter(player);
            case DRIVER -> scoreDriver(player);
        };
    }

    /**
     * Разведчик полезен, пока есть тайлы в мешке.
     *
     * Но он больше не получает абсолютный приоритет всегда и везде.
     * Если на карте уже есть конкретная цель для охотника или инженера,
     * эти роли смогут перебить разведчика по весу.
     */
    private double scoreScout(PlayerState player) {
        if (simulation.tileBag.isEmpty()) {
            return -100.0;
        }

        double score = 35.0;

        if (!hasVisibleNeededDinosaur(player)) {
            score += 25.0;
        }

        if (hasUsefulEngineerAction(player) || hasUsefulHunterAction(player)) {
            score -= 10.0;
        }

        return score;
    }

    /**
     * Инженер получает высокий вес, если на карте есть нужный S-динозавр,
     * который ловится ловушкой.
     */
    private double scoreEngineer(PlayerState player) {
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

    /**
     * Охотник получает высокий вес, если есть нужная цель для охоты или выслеживания.
     */
    private double scoreHunter(PlayerState player) {
        Optional<Dinosaur> target = nearestNeededDinosaur(
                player,
                player.hunter,
                CaptureMethod.TRACKING,
                CaptureMethod.HUNT
        );

        if (target.isEmpty()) {
            return 10.0;
        }

        Dinosaur dinosaur = target.get();
        int distance = player.hunter.manhattan(dinosaur.position);
        double score = 75.0 - Math.min(45.0, distance * 5.0);

        if (dinosaur.species.captureMethod == CaptureMethod.TRACKING && distance <= 1) {
            score += 55.0;
        }

        if (dinosaur.species.captureMethod == CaptureMethod.HUNT && distance <= 2 && player.hunterBait > 0) {
            score += 55.0;
        }

        if (dinosaur.species.captureMethod == CaptureMethod.HUNT && player.hunterBait <= 0) {
            score -= 40.0;
        }

        return score;
    }

    /**
     * Водитель пока имеет низкий вес и выбирается только как логистическая поддержка.
     *
     * Важно: фактическое ограничение движения по дорогам/мостам должно проверяться
     * в действии водителя. Planner только решает, стоит ли вообще пытаться его активировать.
     */
    private double scoreDriver(PlayerState player) {
        if (player.driver.equals(player.hunter)
                && player.driver.equals(player.engineer)
                && player.driver.equals(player.scout)) {
            return 2.0;
        }

        return 18.0;
    }

    private boolean hasVisibleNeededDinosaur(PlayerState player) {
        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .anyMatch(d -> player.needs(d.species));
    }

    private boolean hasUsefulEngineerAction(PlayerState player) {
        if (activeTrapCount(player) >= simulation.inventoryConfig.maxTrapsPerPlayer) {
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

    private int activeTrapCount(PlayerState player) {
        return (int) player.traps.stream()
                .filter(trap -> trap.active)
                .count();
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

    private boolean isAdjacentOrSame(Point a, Point b) {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return dx <= 1 && dy <= 1;
    }
}
