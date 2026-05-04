package ru.mesozoa.sim.dinosaur;

import ru.mesozoa.sim.model.Biome;
import ru.mesozoa.sim.model.CaptureMethod;
import ru.mesozoa.sim.model.DietType;
import ru.mesozoa.sim.model.Point;
import ru.mesozoa.sim.model.SizeClass;
import ru.mesozoa.sim.model.Species;

import java.util.LinkedList;
import java.util.List;

import static ru.mesozoa.sim.model.Biome.BROADLEAF_FOREST;
import static ru.mesozoa.sim.model.Biome.CONIFEROUS_FOREST;
import static ru.mesozoa.sim.model.Biome.FLOODPLAIN;
import static ru.mesozoa.sim.model.Biome.MEADOW;
import static ru.mesozoa.sim.model.Biome.RIVER;
import static ru.mesozoa.sim.model.Biome.SWAMP;

/**
 * Игровая фишка динозавра на карте.
 *
 * Класс хранит не только текущее состояние конкретной фишки, но и видовые
 * свойства, которые нужны механикам: размер, рацион, ловкость, метод поимки,
 * спаун-биом и циклическую био-тропу. Отдельные профильные классы для каждого
 * вида здесь не нужны: шесть актуальных видов спокойно описываются через
 * таблицу правил внутри этого класса, без зоопарка фабрик ради фабрик.
 */
public final class Dinosaur {

    /** Уникальный идентификатор динозавра в рамках партии. */
    public final int id;

    /** Вид динозавра как стабильный enum-идентификатор для заданий и спаунов. */
    public final Species species;

    /** Имя вида для UI и логов. */
    public final String displayName;

    /** Короткий код вида для компактных подписей. */
    public final String shortCode;

    /** Имя файла изображения фишки динозавра. */
    public final String imagePath;

    /** Размерная категория динозавра, влияющая на способ поимки. */
    public final SizeClass size;

    /** Рацион/поведенческий тип динозавра. */
    public final DietType diet;

    /** Метод, которым этот вид можно поймать. */
    public final CaptureMethod captureMethod;

    /** Ловкость динозавра: дальность движения и бонус в охоте. */
    public final int agility;

    /** Радиус охоты хищника, если механика охоты на рейнджеров включена. */
    public final int huntRadius;

    /** Биом, в котором вид появляется на дополнительном тайле. */
    public final Biome spawnBiome;

    /** Циклический биологический маршрут вида. */
    private final LinkedList<Biome> bioTrail;

    /** Текущая клетка динозавра на карте. */
    public Point position;

    /** Индекс текущей точки в био-тропе вида. */
    public int trailIndex;

    /** true, если динозавр уже доставлен в штаб и засчитан игроку. */
    public boolean captured;

    /** true, если динозавр обездвижен в ловушке и ждёт вывоза водителем. */
    public boolean trapped;

    /** ID игрока, чья ловушка держит динозавра, или 0, если динозавр не в ловушке. */
    public int trappedByPlayerId;

    /** true, если динозавр убран с карты без зачёта по заданию. */
    public boolean removed;

    /** Клетка, с которой динозавр пришёл на текущую позицию. */
    public Point lastPosition;

    /**
     * Создаёт фишку динозавра и копирует в неё видовые правила.
     *
     * @param id уникальный ID фишки в партии
     * @param species вид динозавра
     * @param position стартовая позиция на карте
     */
    public Dinosaur(int id, Species species, Point position) {
        DinosaurRules rules = rulesOf(species);

        this.id = id;
        this.species = species;
        this.displayName = species.displayName;
        this.shortCode = species.shortCode;
        this.imagePath = species.imagePath;
        this.size = rules.size;
        this.diet = rules.diet;
        this.captureMethod = rules.captureMethod;
        this.agility = rules.agility;
        this.huntRadius = rules.huntRadius;
        this.spawnBiome = rules.spawnBiome;
        this.bioTrail = new LinkedList<>(rules.bioTrail);
        this.position = position;
        this.lastPosition = position;
        this.trailIndex = 0;
        this.trapped = false;
        this.trappedByPlayerId = 0;
    }

    /**
     * Проверяет, может ли этот динозавр находиться на указанном биоме.
     *
     * @param biome проверяемый биом
     * @return true, если биом входит в био-тропу вида
     */
    public boolean canEnter(Biome biome) {
        return biome != null && bioTrail.contains(biome);
    }

    /**
     * Возвращает следующий биом био-тропы после текущего биома.
     *
     * Если текущий биом не найден в маршруте, используется сохранённый
     * {@link #trailIndex}. Это защищает от странных промежуточных состояний,
     * которые люди, конечно же, рано или поздно создадут.
     *
     * @param currentBiome биом, на котором сейчас стоит динозавр
     * @return следующий целевой биом био-тропы
     */
    public Biome nextBioTrailBiome(Biome currentBiome) {
        return nextBioTrailBiome(currentBiome, trailIndex);
    }

    /**
     * Возвращает следующий биом био-тропы с явным запасным индексом.
     *
     * @param currentBiome текущий биом динозавра
     * @param fallbackTrailIndex индекс, который используется, если текущий биом не найден
     * @return следующий целевой биом био-тропы
     */
    public Biome nextBioTrailBiome(Biome currentBiome, int fallbackTrailIndex) {
        int currentIndex = trailIndexOf(currentBiome);
        if (currentIndex < 0) {
            currentIndex = Math.floorMod(fallbackTrailIndex, bioTrail.size());
        }

        return bioTrail.get((currentIndex + 1) % bioTrail.size());
    }

    /**
     * Обновляет внутренний индекс био-тропы по достигнутому биому.
     *
     * @param biome биом, на который динозавр пришёл
     */
    public void updateTrailIndex(Biome biome) {
        int newTrailIndex = trailIndexOf(biome);
        if (newTrailIndex >= 0) {
            trailIndex = newTrailIndex;
        }
    }

    /**
     * Возвращает индекс биома в био-тропе.
     *
     * @param biome искомый биом
     * @return индекс биома или -1, если этот биом виду не подходит
     */
    public int trailIndexOf(Biome biome) {
        for (int i = 0; i < bioTrail.size(); i++) {
            if (bioTrail.get(i) == biome) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Возвращает длину циклической био-тропы.
     *
     * @return количество точек маршрута
     */
    public int bioTrailSize() {
        return bioTrail.size();
    }

    /**
     * Возвращает копию био-тропы для внешней логики планирования.
     *
     * @return неизменяемая копия маршрута вида
     */
    public List<Biome> bioTrail() {
        return List.copyOf(bioTrail);
    }

    /**
     * Проверяет, является ли динозавр травоядным.
     *
     * @return true для травоядных видов
     */
    public boolean isHerbivore() {
        return diet == DietType.HERBIVORE;
    }

    /**
     * Возвращает метод поимки для вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return метод поимки вида
     */
    public static CaptureMethod captureMethodOf(Species species) {
        return rulesOf(species).captureMethod;
    }

    /**
     * Возвращает спаун-биом вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return биом появления вида
     */
    public static Biome spawnBiomeOf(Species species) {
        return rulesOf(species).spawnBiome;
    }

    /**
     * Возвращает био-тропу вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return копия циклического маршрута вида
     */
    public static List<Biome> bioTrailOf(Species species) {
        return List.copyOf(rulesOf(species).bioTrail);
    }

    /**
     * Возвращает ловкость вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return ловкость вида
     */
    public static int agilityOf(Species species) {
        return rulesOf(species).agility;
    }

    /**
     * Возвращает размер вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return размерная категория вида
     */
    public static SizeClass sizeOf(Species species) {
        return rulesOf(species).size;
    }

    /**
     * Возвращает рацион/поведенческий тип вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return тип питания и поведения
     */
    public static DietType dietOf(Species species) {
        return rulesOf(species).diet;
    }

    /**
     * Возвращает радиус охоты вида без создания фишки динозавра.
     *
     * @param species вид динозавра
     * @return радиус охоты
     */
    public static int huntRadiusOf(Species species) {
        return rulesOf(species).huntRadius;
    }

    /**
     * Возвращает статические правила указанного вида.
     *
     * @param species вид динозавра
     * @return правила вида
     */
    private static DinosaurRules rulesOf(Species species) {
        return switch (species) {
            case GALLIMIMON -> new DinosaurRules(
                    SizeClass.S,
                    DietType.HERBIVORE,
                    CaptureMethod.TRAP,
                    4,
                    0,
                    MEADOW,
                    List.of(MEADOW, SWAMP, CONIFEROUS_FOREST));
            case DRIORNIS -> new DinosaurRules(
                    SizeClass.S,
                    DietType.HERBIVORE,
                    CaptureMethod.TRAP,
                    5,
                    0,
                    BROADLEAF_FOREST,
                    List.of(BROADLEAF_FOREST, FLOODPLAIN, MEADOW));
            case CRYPTOGNATH -> new DinosaurRules(
                    SizeClass.S,
                    DietType.SMALL_PREDATOR,
                    CaptureMethod.TRAP,
                    5,
                    0,
                    CONIFEROUS_FOREST,
                    List.of(CONIFEROUS_FOREST, SWAMP, CONIFEROUS_FOREST));
            case VELOCITAURUS -> new DinosaurRules(
                    SizeClass.M,
                    DietType.PREDATOR,
                    CaptureMethod.HUNT,
                    3,
                    1,
                    MEADOW,
                    List.of(MEADOW, BROADLEAF_FOREST, CONIFEROUS_FOREST, RIVER));
            case MONOCERATUS -> new DinosaurRules(
                    SizeClass.M,
                    DietType.HERBIVORE,
                    CaptureMethod.TRACKING,
                    2,
                    0,
                    FLOODPLAIN,
                    List.of(FLOODPLAIN, RIVER, MEADOW, CONIFEROUS_FOREST));
            case VOCAREZAUROLOPH -> new DinosaurRules(
                    SizeClass.M,
                    DietType.HERBIVORE,
                    CaptureMethod.TRACKING,
                    3,
                    0,
                    RIVER,
                    List.of(RIVER, MEADOW, BROADLEAF_FOREST, FLOODPLAIN));
        };
    }

    /** Статическое описание правил вида. */
    private record DinosaurRules(
            SizeClass size,
            DietType diet,
            CaptureMethod captureMethod,
            int agility,
            int huntRadius,
            Biome spawnBiome,
            List<Biome> bioTrail
    ) {
    }
}
