package ru.mesozoa.sim.model;

public enum Direction {
    NORTH(0, 1),
    NORTH_EAST(1, 1),
    EAST(1, 0),
    SOUTH_EAST(1, -1),
    SOUTH(0, -1),
    SOUTH_WEST(-1, -1),
    WEST(-1, 0),
    NORTH_WEST(-1, 1);

    public final int dx;
    public final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Point from(Point origin) {
        return new Point(origin.x + dx, origin.y + dy);
    }

    /**
     * Поворачивает направление вместе с физическим тайлом.
     *
     * turns:
     * 0 — без поворота;
     * 1 — 90 градусов по часовой;
     * 2 — 180 градусов;
     * 3 — 270 градусов по часовой.
     */
    public Direction rotateClockwiseQuarterTurns(int turns) {
        int normalized = Math.floorMod(turns, 4);

        Direction result = this;
        for (int i = 0; i < normalized; i++) {
            result = result.rotateClockwiseOnce();
        }

        return result;
    }

    private Direction rotateClockwiseOnce() {
        return switch (this) {
            case NORTH -> EAST;
            case NORTH_EAST -> SOUTH_EAST;
            case EAST -> SOUTH;
            case SOUTH_EAST -> SOUTH_WEST;
            case SOUTH -> WEST;
            case SOUTH_WEST -> NORTH_WEST;
            case WEST -> NORTH;
            case NORTH_WEST -> NORTH_EAST;
        };
    }
}
