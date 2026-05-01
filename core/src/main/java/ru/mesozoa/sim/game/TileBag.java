package ru.mesozoa.sim.game;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.TileBlueprint;
import ru.mesozoa.sim.model.TileCatalog;
import ru.mesozoa.sim.model.TileDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Мешочки тайлов:
 * - mainTiles — основная колода/мешочек исследования;
 * - extraTiles — дополнительная колода для автоматической достройки биомов.
 *
 * TileBag не придумывает состав тайлов. Он разворачивает статический TileCatalog
 * в конкретные TileDefinition и перемешивает их перед партией.
 */
public final class TileBag {


    /**
     * Мешочек основных тайлов.
     * Из него игроки вслепую вытягивают тайлы во время разведки новых участков острова.
     * Содержит обычные тайлы биомов и специальные спаун-тайлы динозавров.
     */
    private final ArrayList<TileDefinition> mainTiles = new ArrayList<>();

    /**
     * Мешочек дополнительных тайлов.
     * Используется для автоматической достройки биомов по переходам на уже выложенных тайлах.
     *
     * Например, если у выложенного тайла есть переход на северо-запад,
     * из этого мешочка берётся дополнительный тайл того же биома и кладётся
     * в указанном направлении, если клетка свободна.
     */
    private final ArrayList<TileDefinition> extraTiles = new ArrayList<>();

    /**
     * Источник случайности для перемешивания мешочков и вытягивания тайлов.
     * Передаётся извне, чтобы симуляцию можно было воспроизводить по seed.
     */
    private final Random random;

    /**
     * Создаёт пустой мешочек тайлов с заданным источником случайности.
     *
     * Конструктор закрыт, потому что корректный состав мешочков должен собираться
     * через фабричный метод createDefault(...).
     *
     * @param random источник случайности для перемешивания и вытягивания тайлов
     */
    private TileBag(Random random) {
        this.random = random;
    }

    /**
     * Создаёт стандартный набор мешочков для новой партии.
     *
     * Основной мешочек заполняется:
     * - статическими тайлами биомов из TileCatalog.mainTileBlueprints();
     * - спаун-тайлами динозавров из TileCatalog.spawnTileBlueprints(...).
     *
     * Дополнительный мешочек заполняется тайлами из TileCatalog.extraTileBlueprints().
     * После заполнения оба мешочка перемешиваются.
     *
     * @param config конфигурация партии, включая количество спаун-тайлов по видам
     * @param random источник случайности для перемешивания и последующего вытягивания тайлов
     * @return готовый TileBag для новой партии
     */
    public static TileBag createDefault(GameConfig config, Random random) {
        TileBag bag = new TileBag(random);

        bag.addMain(TileCatalog.mainTileBlueprints());
        bag.addMain(TileCatalog.spawnTileBlueprints(config.spawnTiles));
        bag.addExtra(TileCatalog.extraTileBlueprints());

        Collections.shuffle(bag.mainTiles, random);
        Collections.shuffle(bag.extraTiles, random);

        return bag;
    }

    /**
     * Добавляет набор blueprint-описаний в основной мешочек тайлов.
     *
     * Каждый TileBlueprint разворачивается в нужное количество конкретных TileDefinition.
     *
     * @param blueprints статические описания тайлов для основного мешочка
     */
    private void addMain(Iterable<TileBlueprint> blueprints) {
        for (TileBlueprint blueprint : blueprints) {
            addTo(mainTiles, blueprint);
        }
    }

    /**
     * Добавляет набор blueprint-описаний в дополнительный мешочек тайлов.
     *
     * Дополнительные тайлы используются не для обычной разведки,
     * а для автодостройки биомов по переходам.
     *
     * @param blueprints статические описания тайлов для дополнительного мешочка
     */
    private void addExtra(Iterable<TileBlueprint> blueprints) {
        for (TileBlueprint blueprint : blueprints) {
            addTo(extraTiles, blueprint);
        }
    }

    /**
     * Разворачивает один TileBlueprint в конкретные TileDefinition
     * и добавляет их в указанный список.
     *
     * Например blueprint "Луг с переходом NORTH × 3" превратится
     * в три отдельных TileDefinition с одинаковым биомом и направлением перехода.
     *
     * @param target список, в который нужно добавить созданные TileDefinition
     * @param blueprint описание типа тайла и количества его экземпляров
     */
    private void addTo(ArrayList<TileDefinition> target, TileBlueprint blueprint) {
        if (blueprint.isEmpty()) return;

        for (int i = 0; i < blueprint.count; i++) {
            target.add(new TileDefinition(
                    blueprint.biome,
                    blueprint.spawnSpecies,
                    blueprint.expansionDirections
            ));
        }
    }

    /**
     * Вытягивает случайный тайл из основного мешочка.
     *
     * Используется во время разведки, когда игрок открывает новый участок карты.
     * Тайл удаляется из мешочка, как физический тайл, который достали из мешка
     * и положили на стол.
     *
     * @return случайный TileDefinition из основного мешочка или null, если мешочек пуст
     */
    public TileDefinition draw() {
        if (mainTiles.isEmpty()) return null;
        return mainTiles.remove(random.nextInt(mainTiles.size()));
    }

    /**
     * Вытягивает из дополнительного мешочка первый доступный тайл указанного биома.
     *
     * Используется для автоматической достройки по переходам:
     * если выложенный тайл требует продолжить, например, лес или реку,
     * симуляция пытается найти дополнительный тайл того же биома.
     *
     * В отличие от draw(), здесь выбирается не полностью случайный тайл,
     * а тайл с нужным биомом. Найденный тайл удаляется из дополнительного мешочка.
     *
     * @param biome биом, который нужно достроить
     * @return TileDefinition указанного биома или null, если таких дополнительных тайлов больше нет
     */
    public TileDefinition drawExtraBiome(Biome biome) {
        for (int i = 0; i < extraTiles.size(); i++) {
            TileDefinition def = extraTiles.get(i);
            if (def.biome == biome) {
                return extraTiles.remove(i);
            }
        }

        return null;
    }

    /**
     * Возвращает количество тайлов, оставшихся в основном мешочке.
     *
     * @return число не вытянутых тайлов исследования
     */
    public int remainingMain() {
        return mainTiles.size();
    }

    /**
     * Возвращает количество тайлов, оставшихся в дополнительном мешочке.
     *
     * @return число не использованных дополнительных тайлов
     */
    public int remainingExtra() {
        return extraTiles.size();
    }

    /**
     * Проверяет, закончился ли основной мешочек тайлов.
     *
     * Используется как одно из условий завершения партии:
     * если исследовать больше нечего, симуляцию можно останавливать,
     * потому что остров внезапно закончился. География, как всегда, подвела.
     *
     * @return true, если в основном мешочке больше нет тайлов
     */
    public boolean isEmpty() {
        return mainTiles.isEmpty();
    }
}
