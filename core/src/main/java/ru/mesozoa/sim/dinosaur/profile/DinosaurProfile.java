package ru.mesozoa.sim.dinosaur.profile;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.Species;

/**
 * Описание биологического поведения конкретного вида динозавра.
 */
public interface DinosaurProfile {

    /**
     * Возвращает вид динозавра, которому принадлежит профиль.
     *
     * @return вид динозавра
     */
    Species species();

    /**
     * Возвращает ловкость динозавра, то есть максимальную дальность его хода.
     *
     * @return количество клеток, которое динозавр может пройти за фазу движения
     */
    int agility();

    /**
     * Проверяет, может ли динозавр этого вида находиться на указанном биоме.
     *
     * @param biome проверяемый биом
     * @return true, если биом входит в био-тропу вида
     */
    boolean canEnter(Biome biome);

    /**
     * Возвращает следующий биом био-тропы после текущего биома.
     *
     * @param currentBiome текущий биом динозавра
     * @param fallbackTrailIndex запасной индекс маршрута из состояния фишки
     * @return следующий целевой биом
     */
    Biome nextBiomeAfter(Biome currentBiome, int fallbackTrailIndex);

    /**
     * Возвращает индекс биома в био-тропе.
     *
     * @param biome биом, который нужно найти
     * @return индекс биома или -1, если биом не входит в маршрут
     */
    int trailIndexOf(Biome biome);
}
