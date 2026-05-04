package ru.mesozoa.sim.action;

import ru.mesozoa.sim.model.Direction;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.ranger.RangerPlan;
import ru.mesozoa.sim.simulation.GameSimulation;
import ru.mesozoa.sim.tile.ExtraTile;
import ru.mesozoa.sim.tile.MainTile;
import ru.mesozoa.sim.tile.Tile;

import java.util.Comparator;
import java.util.List;

public class ScoutAction {

    private final GameSimulation simulation;
    private final RangerActionExecutor rangerActionExecutor;
    private final DinosaurAction dinosaurAction;

    public ScoutAction(GameSimulation simulation,
                       RangerActionExecutor rangerActionExecutor,
                       DinosaurAction dinosaurAction) {

        this.simulation = simulation;
        this.rangerActionExecutor = rangerActionExecutor;
        this.dinosaurAction = dinosaurAction;
    }


    public void action(PlayerState player, RangerPlan plan) {
        int movementPoints = plan.ranger().currentActionPoints();
        action(player, movementPoints);
        plan.ranger().spendActionPoints(movementPoints);
    }

    public void action(PlayerState player, int movementPoints) {
        if (simulation.tileBag.isEmpty()) {
            moveRoleTowardNearestFrontier(player, RangerRole.SCOUT, movementPoints);
            return;
        }

        exploreOneTile(player);
    }

    private void moveRoleTowardNearestFrontier(PlayerState player, RangerRole role, int movementPoints) {
        Point start = player.positionOf(role);
        Point frontier = simulation.map.nearestUnexploredFrontier(start);
        if (frontier == null) return;

        rangerActionExecutor.moveRoleToward(player, role, frontier, movementPoints);
    }

    private void exploreOneTile(PlayerState player) {
        MainTile drawn = simulation.tileBag.draw();
        if (drawn == null) return;

        Point placement = choosePlacementPoint(player);
        if (placement == null) return;

        Tile placedTile = placeDrawnTile(player, drawn, placement, false);
        if (placedTile == null) return;

        for (Direction direction : placedTile.expansionDirections()) {
            Point extraPoint = direction.from(placement);

            if (!simulation.map.canPlaceExpansion(extraPoint)) {
                continue;
            }

            ExtraTile extra = simulation.tileBag.drawExtraBiome(placedTile.biome);
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
                .min(Comparator.comparingInt(p -> p.manhattan(player.scoutRanger.position())))
                .orElse(candidates.get(simulation.random.nextInt(candidates.size())));
    }

    private Tile placeDrawnTile(PlayerState player, Tile tile, Point placement, boolean automaticExpansion) {
        int rotationQuarterTurns = simulation.random.nextInt(4);
        tile.place(placement, rotationQuarterTurns);

        boolean placed = automaticExpansion
                ? simulation.map.placeExpansionTile(placement, tile)
                : simulation.map.placeTile(placement, tile);

        if (!placed) return null;

        if (!automaticExpansion) {
            player.setPosition(RangerRole.SCOUT, placement);
        }

        if (automaticExpansion && tile.hasSpawn() && !tile.isSpawnUsed()) {
            dinosaurAction.spawnDinosaur(tile.spawnSpecies(), placement);
            tile.markSpawnUsed();
        }

        return tile;
    }
}
