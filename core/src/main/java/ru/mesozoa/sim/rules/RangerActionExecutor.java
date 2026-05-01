package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.model.*;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Исполнение хода игрока.
 *
 * Здесь живёт "что делает выбранная роль".
 * Решение "какие две роли выбрать" живёт в RangerTurnPlanner.
 */
public final class RangerActionExecutor {
    private final GameSimulation simulation;
    private final RangerTurnPlanner planner;

    public RangerActionExecutor(GameSimulation simulation, RangerTurnPlanner planner) {
        this.simulation = simulation;
        this.planner = planner;
    }

    public void playTurn(PlayerState player) {
        if (player.isComplete()) {
            simulation.log("Игрок " + player.id + " уже выполнил задание.");
            return;
        }

        if (player.turnsSkipped > 0) {
            player.turnsSkipped--;
            simulation.log("Игрок " + player.id + " пропускает ход.");
            return;
        }

        List<RangerRole> roles = planner.chooseTwoRangersForTurn(player);

        simulation.log("Ход игрока " + player.id + " (" + player.color.assetSuffix + "): "
                + planner.roleListToText(roles));

        for (RangerRole role : roles) {
            performRangerAction(player, role, 2);
        }
    }

    private void performRangerAction(PlayerState player, RangerRole role, int movementPoints) {
        switch (role) {
            case SCOUT -> scoutAction(player, movementPoints);
            case ENGINEER -> engineerAction(player, movementPoints);
            case HUNTER -> hunterAction(player, movementPoints);
            case DRIVER -> driverAction(player, movementPoints);
        }
    }

    /**
     * Разведчик:
     * - имеет 2 очка действий;
     * - спецдействие открытия тайла используется не более одного раза за активацию.
     *
     * Пока для симуляции сохраняем текущую абстракцию:
     * разведчик вытягивает закрытый тайл из мешка и выкладывает его рядом с разведанной картой.
     */
    private void scoutAction(PlayerState player, int movementPoints) {
        if (simulation.tileBag.isEmpty()) {
            moveRoleTowardNearestFrontier(player, RangerRole.SCOUT, movementPoints);
            return;
        }

        exploreOneTile(player);
    }

    private void engineerAction(PlayerState player, int movementPoints) {
        boolean placedTrap = placeTrapForNeededTarget(player);
        if (placedTrap) {
            return;
        }

        Optional<Dinosaur> target = nearestNeededDinosaur(player, player.engineer, CaptureMethod.TRAP);
        if (target.isPresent()) {
            moveRoleToward(player, RangerRole.ENGINEER, target.get().position, movementPoints);
            return;
        }

        moveRoleToward(player, RangerRole.ENGINEER, player.scout, movementPoints);
    }

    private void hunterAction(PlayerState player, int movementPoints) {
        boolean acted = attemptCapture(player);
        if (acted) {
            return;
        }

        Optional<Dinosaur> target = nearestNeededDinosaur(
                player,
                player.hunter,
                CaptureMethod.TRACKING,
                CaptureMethod.HUNT
        );

        if (target.isPresent()) {
            moveRoleToward(player, RangerRole.HUNTER, target.get().position, movementPoints);
            return;
        }

        /*
         * Если охотнику пока некого ловить, он всё равно тратит свою активацию:
         * подтягивается к разведчику. Иначе в логе написано "разведчик + охотник",
         * а на столе охотник стоит на базе и изображает мебель.
         */
        moveRoleToward(player, RangerRole.HUNTER, player.scout, movementPoints);
    }

    private void driverAction(PlayerState player, int movementPoints) {
        Point target;

        if (!player.driver.equals(player.hunter)) {
            target = player.hunter;
        } else if (!player.driver.equals(player.engineer)) {
            target = player.engineer;
        } else {
            target = player.scout;
        }

        moveRoleToward(player, RangerRole.DRIVER, target, movementPoints);
    }

    private Optional<Dinosaur> nearestNeededDinosaur(PlayerState player, Point from, CaptureMethod... methods) {
        Set<CaptureMethod> allowedMethods = EnumSet.noneOf(CaptureMethod.class);
        allowedMethods.addAll(Arrays.asList(methods));

        return simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> allowedMethods.contains(d.species.captureMethod))
                .min(Comparator.comparingInt(d -> d.position.manhattan(from)));
    }

    private void moveRoleTowardNearestFrontier(PlayerState player, RangerRole role, int movementPoints) {
        Point start = player.positionOf(role);
        Point frontier = simulation.map.nearestUnexploredFrontier(start);
        if (frontier == null) return;

        moveRoleToward(player, role, frontier, movementPoints);
    }

    private void moveRoleToward(PlayerState player, RangerRole role, Point target, int movementPoints) {
        Point position = player.positionOf(role);

        for (int i = 0; i < movementPoints; i++) {
            if (position.equals(target)) break;

            Point next = simulation.stepTowardPlaced(position, target);
            if (next.equals(position)) break;

            position = next;
        }

        player.setPosition(role, position);
    }

    private void exploreOneTile(PlayerState player) {
        TileDefinition drawn = simulation.tileBag.draw();
        if (drawn == null) return;

        Point placement = choosePlacementPoint(player);
        if (placement == null) return;

        Tile placedTile = placeDrawnTile(player, drawn, placement, false);
        if (placedTile == null) return;

        for (Direction direction : placedTile.expansionDirections) {
            Point extraPoint = direction.from(placement);

            if (!simulation.map.canPlaceExpansion(extraPoint)) {
                simulation.log("Переход " + direction + " заблокирован: клетка занята " + extraPoint);
                continue;
            }

            TileDefinition extra = simulation.tileBag.drawExtraBiome(placedTile.biome);
            if (extra == null) {
                simulation.log("Нет доп. тайла для биома " + placedTile.biome.displayName);
                continue;
            }

            placeDrawnTile(player, extra, extraPoint, true);
        }
    }

    private Point choosePlacementPoint(PlayerState player) {
        List<Point> candidates = simulation.map.availablePlacementPoints();
        if (candidates.isEmpty()) return null;

        return candidates.stream()
                .min(Comparator.comparingInt(p -> p.manhattan(player.scout)))
                .orElse(candidates.get(simulation.random.nextInt(candidates.size())));
    }

    private Tile placeDrawnTile(PlayerState player, TileDefinition drawn, Point placement, boolean automaticExpansion) {
        int rotationQuarterTurns = simulation.random.nextInt(4);
        Tile tile = drawn.toPlacedTile(rotationQuarterTurns);

        boolean placed = automaticExpansion
                ? simulation.map.placeExpansionTile(placement, tile)
                : simulation.map.placeTile(placement, tile);

        if (!placed) return null;

        if (!automaticExpansion) {
            player.scout = placement;
        }

        String prefix = automaticExpansion ? "Автодостройка" : "Игрок " + player.id + " выложил";
        simulation.log(prefix + ": " + tile.biome.displayName + " " + placement
                + ", поворот " + (rotationQuarterTurns * 90) + "°");

        if (tile.hasSpawn() && !tile.spawnUsed) {
            spawnDinosaur(tile.spawnSpecies, placement);
            tile.spawnUsed = true;
        }

        return tile;
    }

    private void spawnDinosaur(Species species, Point position) {
        Dinosaur dino = new Dinosaur(simulation.nextDinoId++, species, position);
        simulation.dinosaurs.add(dino);
        simulation.result.firstSpawnRound.putIfAbsent(species, simulation.round);
        simulation.log("СПАУН: " + species.displayName + " на " + position);
    }

    private boolean placeTrapForNeededTarget(PlayerState player) {
        if (player.traps.stream().filter(t -> t.active).count() >= simulation.inventoryConfig.maxTrapsPerPlayer) {
            return false;
        }

        Optional<Dinosaur> target = simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .filter(d -> d.species.captureMethod == CaptureMethod.TRAP)
                .min(Comparator.comparingInt(d -> d.position.manhattan(player.engineer)));

        if (target.isEmpty()) return false;

        Point predicted = predictNextBioStep(target.get());
        if (predicted != null && simulation.map.isPlaced(predicted)) {
            player.traps.add(new Trap(player.id, predicted));
            player.engineer = predicted;
            simulation.log("Игрок " + player.id + " поставил ловушку на " + predicted);
            return true;
        }

        return false;
    }

    private Point predictNextBioStep(Dinosaur dinosaur) {
        Point target = predictNextBioTarget(dinosaur);
        if (target == null) return null;
        return simulation.stepTowardPlaced(dinosaur.position, target);
    }

    private Point predictNextBioTarget(Dinosaur dinosaur) {
        Biome nextBiome = dinosaur.species.bioTrail.get((dinosaur.trailIndex + 1) % dinosaur.species.bioTrail.size());
        return simulation.map.nearestPlacedBiome(dinosaur.position, nextBiome);
    }

    private boolean attemptCapture(PlayerState player) {
        List<Dinosaur> needed = simulation.dinosaurs.stream()
                .filter(d -> !d.captured && !d.removed)
                .filter(d -> player.needs(d.species))
                .sorted(Comparator.comparingInt(d -> d.position.manhattan(player.hunter)))
                .toList();

        for (Dinosaur dinosaur : needed) {
            if (dinosaur.species.captureMethod == CaptureMethod.TRACKING) {
                if (player.hunter.manhattan(dinosaur.position) <= 1) {
                    double chance = simulation.gameMechanicConfig.trackingBaseSuccess
                            + simulation.gameMechanicConfig.trackingStepBonus
                            * simulation.random.nextInt(simulation.gameMechanicConfig.trackingMaxSteps);

                    if (simulation.random.nextDouble() < chance) {
                        capture(player, dinosaur, "выслеживание");
                        simulation.result.trackingCaptures++;
                    }
                    return true;
                }

                player.hunter = simulation.stepTowardPlaced(player.hunter, dinosaur.position);
                return true;
            }

            if (dinosaur.species.captureMethod == CaptureMethod.HUNT) {
                if (player.hunterBait <= 0) return false;

                if (player.hunter.manhattan(dinosaur.position) <= 2) {
                    double chance = simulation.random.nextBoolean()
                            ? simulation.gameMechanicConfig.huntBaseSuccess
                            : simulation.gameMechanicConfig.huntPreparedSuccess;

                    if (simulation.random.nextDouble() < chance) {
                        capture(player, dinosaur, "охота");
                        simulation.result.huntCaptures++;
                    }

                    player.hunterBait--;
                    return true;
                }

                player.hunter = simulation.stepTowardPlaced(player.hunter, dinosaur.position);
                return true;
            }
        }

        return false;
    }

    private void capture(PlayerState player, Dinosaur dinosaur, String method) {
        dinosaur.captured = true;
        player.captured.add(dinosaur.species);
        simulation.log("ПОЙМАН: игрок " + player.id + " поймал "
                + dinosaur.species.displayName + " (" + method + ")");
    }
}
