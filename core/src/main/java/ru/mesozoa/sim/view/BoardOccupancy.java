package ru.mesozoa.sim.view;

import ru.mesozoa.sim.model.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;
import ru.mesozoa.sim.rules.GameSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Временная карта занятости клеток для отрисовки.
 *
 * Модель игры по-прежнему хранит позиции в самих объектах:
 * Dinosaur.position, PlayerState.scout/driver/engineer/hunter.
 * Этот класс просто собирает их в Map<Point, List<BoardPiece>> на один кадр.
 */
public final class BoardOccupancy {
    private final Map<Point, List<BoardPiece>> piecesByPoint = new HashMap<>();

    private BoardOccupancy() {
    }

    public static BoardOccupancy from(GameSimulation simulation) {
        BoardOccupancy occupancy = new BoardOccupancy();

        for (Dinosaur dinosaur : simulation.dinosaurs) {
            if (!dinosaur.captured && !dinosaur.removed) {
                occupancy.add(BoardPiece.dinosaur(dinosaur));
            }
        }

        for (PlayerState player : simulation.players) {
            occupancy.add(BoardPiece.ranger(player, RangerRole.SCOUT));
            occupancy.add(BoardPiece.ranger(player, RangerRole.DRIVER));
            occupancy.add(BoardPiece.ranger(player, RangerRole.ENGINEER));
            occupancy.add(BoardPiece.ranger(player, RangerRole.HUNTER));
        }

        for (List<BoardPiece> pieces : occupancy.piecesByPoint.values()) {
            pieces.sort(Comparator.comparingInt(BoardPiece::sortOrder));
        }

        return occupancy;
    }

    public List<BoardPiece> at(Point point) {
        return piecesByPoint.getOrDefault(point, List.of());
    }

    private void add(BoardPiece piece) {
        piecesByPoint
                .computeIfAbsent(piece.position, ignored -> new ArrayList<>())
                .add(piece);
    }
}
