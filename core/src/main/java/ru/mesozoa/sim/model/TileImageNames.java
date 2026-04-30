package ru.mesozoa.sim.model;

import java.util.List;
import java.util.stream.Collectors;

public final class TileImageNames {
    private TileImageNames() {
    }

    public static String baseImagePath(Biome biome) {
        return "tiles/" + baseName(biome) + ".png";
    }

    public static String variantImagePath(Biome biome, List<Direction> directions) {
        if (directions == null || directions.isEmpty()) {
            return baseImagePath(biome);
        }

        String suffix = directions.stream()
                .map(TileImageNames::directionCode)
                .collect(Collectors.joining("_"));

        return "tiles/" + baseName(biome) + "_" + suffix + ".png";
    }

    public static String spawnImagePath(Biome biome, Species species) {
        return "tiles/" + baseName(biome) + "_spawn_" + speciesFileName(species) + ".png";
    }

    public static String baseName(Biome biome) {
        return switch (biome) {
            case BROADLEAF_FOREST -> "broadleaf_forest";
            case CONIFEROUS_FOREST -> "coniferous_forest";
            case MEADOW -> "meadow";
            case SWAMP -> "swamp";
            case RIVER -> "river";
            case LAKE -> "lake";
            case FLOODPLAIN -> "floodplain";
            case MOUNTAIN -> "mountain";
            case LANDING -> "landing";
        };
    }

    public static String directionCode(Direction direction) {
        return switch (direction) {
            case NORTH -> "n";
            case NORTH_EAST -> "ne";
            case EAST -> "e";
            case SOUTH_EAST -> "se";
            case SOUTH -> "s";
            case SOUTH_WEST -> "sw";
            case WEST -> "w";
            case NORTH_WEST -> "nw";
        };
    }

    public static String speciesFileName(Species species) {
        return switch (species) {
            case GALLIMIMON -> "gallimimon";
            case DRIORNIS -> "driornis";
            case CRYPTOGNATH -> "cryptognath";
            case VELOCITAURUS -> "velocitaurus";
            case MONOCERATUS -> "monoceratus";
            case VOCAREZAUROLOPH -> "vocarezauroloph";
        };
    }
}
