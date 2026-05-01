package ru.mesozoa.sim.config;

import ru.mesozoa.sim.model.Species;

import java.util.EnumMap;
import java.util.Map;

/**
 * Конфигурация общих параметров игры.
 * - сколько игроков
 * - сколько раундов
 * - Ограничения по тайлам
 * и проч.
 */
public final class GameConfig {

    /**
     * Количество игроков в партии.
     * Каждый игрок управляет собственной командой рейнджеров:
     * разведчиком, водителем, инженером и охотником.
     */
    public int players = 1;

    /**
     * Максимальное количество раундов партии.
     * Используется как ограничитель симуляции, чтобы партия не длилась бесконечно,
     * если игроки не смогли выполнить свои задания.
     */
    public int maxRounds = 280;

    /**
     * Количество спаун-тайлов для каждого вида динозавров.
     * Эти значения определяют, сколько специальных тайлов появления конкретного вида
     * будет добавлено в мешочек тайлов перед началом партии.
     *
     * Ключ — вид динозавра.
     * Значение — количество его спаун-тайлов.
     */
    public final Map<Species, Integer> spawnTiles = new EnumMap<>(Species.class);

    public GameConfig() {
        spawnTiles.put(Species.GALLIMIMON, 8);
        spawnTiles.put(Species.DRIORNIS, 8);
        spawnTiles.put(Species.CRYPTOGNATH, 8);
        spawnTiles.put(Species.VELOCITAURUS, 4);
        spawnTiles.put(Species.MONOCERATUS, 4);
        spawnTiles.put(Species.VOCAREZAUROLOPH, 4);
    }
}
