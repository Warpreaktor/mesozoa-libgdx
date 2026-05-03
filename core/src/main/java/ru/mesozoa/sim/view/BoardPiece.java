package ru.mesozoa.sim.view;

import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.PlayerState;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.RangerRole;

public final class BoardPiece {
    public enum Type {
        DINOSAUR,
        RANGER
    }

    public final Type type;
    public final Point position;
    public final Dinosaur dinosaur;
    public final PlayerState player;
    public final RangerRole role;

    private BoardPiece(Type type, Point position, Dinosaur dinosaur, PlayerState player, RangerRole role) {
        this.type = type;
        this.position = position;
        this.dinosaur = dinosaur;
        this.player = player;
        this.role = role;
    }

    public static BoardPiece dinosaur(Dinosaur dinosaur) {
        return new BoardPiece(Type.DINOSAUR, dinosaur.position, dinosaur, null, null);
    }

    public static BoardPiece ranger(PlayerState player, RangerRole role) {
        return new BoardPiece(Type.RANGER, player.positionOf(role), null, player, role);
    }

    public int sortOrder() {
        if (type == Type.DINOSAUR) {
            return dinosaur.id;
        }

        return 10_000 + player.id * 10 + roleOrder(role);
    }

    private static int roleOrder(RangerRole role) {
        return switch (role) {
            case SCOUT -> 0;
            case DRIVER -> 1;
            case ENGINEER -> 2;
            case HUNTER -> 3;
        };
    }
}
