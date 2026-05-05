package ru.mesozoa.sim.model;

import ru.mesozoa.sim.ranger.Driver;
import ru.mesozoa.sim.ranger.Engineer;
import ru.mesozoa.sim.ranger.Hunter;
import ru.mesozoa.sim.ranger.Ranger;
import ru.mesozoa.sim.ranger.Scout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

public final class PlayerState {
    public final int id;
    public final RangerColor color;

    public final Set<Species> task = EnumSet.noneOf(Species.class);
    public final Set<Species> captured = EnumSet.noneOf(Species.class);

    /** Фигурка разведчика игрока. */
    public final Scout scoutRanger;

    /** Фигурка водителя игрока. */
    public final Driver driverRanger;

    /** Фигурка инженера игрока. */
    public final Engineer engineerRanger;

    /** Фигурка охотника игрока. */
    public final Hunter hunterRanger;

    public final List<Trap> traps = new ArrayList<>();

    /**
     * Жетоны следов, которые игрок сейчас держит на карте.
     *
     * Это такие же физические маркеры игрока, как ловушки: они лежат на поле,
     * ограничивают число попыток выслеживания и убираются при срыве цепочки или
     * после вывоза пойманного по следу динозавра.
     */
    public final List<TrailToken> trailTokens = new ArrayList<>();

    /** Активная засада охотника на M-хищника или null, если охота не начата. */
    public HuntAmbush activeHunt;

    /** Активная цепочка выслеживания M-травоядного или null, если след не ведётся. */
    public TrackingTrail activeTracking;

    /**
     * Клетки, где охотник уже ждал хищника слишком долго и решил перенести засаду.
     *
     * Эти клетки временно исключаются из новых планов охоты, чтобы AI не бросал
     * приманку в ту же самую клетку сразу после переноса засады.
     */
    public final Set<Point> rejectedHuntBaitPositions = new HashSet<>();

    /**
     * Счётчик проваленных охот с приманкой по конкретным M-хищникам.
     *
     * AI использует его как мягкий штраф, чтобы после провала засады заново
     * взвесить альтернативы и не повторять сразу явно неудачный план.
     */
    private final Map<Integer, Integer> failedHuntAttemptsByDinosaurId = new HashMap<>();

    /**
     * Счётчик сорванных цепочек выслеживания по конкретным M-травоядным.
     *
     * Это не запрет на повторную попытку, а лёгкий пинок AI посмотреть вокруг:
     * возможно, рядом уже есть более выгодная цель для охотника.
     */
    private final Map<Integer, Integer> failedTrackingChainsByDinosaurId = new HashMap<>();

    /** Количество оставшейся мясной приманки для охоты. */
    public int hunterBait = 3;
    public int turnsSkipped = 0;

    public PlayerState(int id, Point base) {
        this(id, RangerColor.forPlayerId(id), base);
    }

    public PlayerState(int id, RangerColor color, Point base) {
        this.id = id;
        this.color = color;
        this.scoutRanger = new Scout(base);
        this.driverRanger = new Driver(base);
        this.engineerRanger = new Engineer(base);
        this.hunterRanger = new Hunter(base);
    }

    public boolean isComplete() {
        return captured.containsAll(task);
    }

    public boolean needs(Species species) {
        return task.contains(species) && !captured.contains(species);
    }

    /**
     * Проверяет, лежит ли охотник в активной засаде.
     *
     * @return true, если охотник уже начал фазу охоты с приманкой
     */
    public boolean hasActiveHunt() {
        return activeHunt != null;
    }

    /**
     * Проверяет, ведёт ли охотник цепочку следов M-травоядного.
     *
     * @return true, если выслеживание уже начато и должно продолжаться
     */
    public boolean hasActiveTracking() {
        return activeTracking != null;
    }

    /**
     * Регистрирует провал охоты с приманкой на конкретного динозавра.
     *
     * @param dinosaurId ID динозавра, по которому сорвалась охота
     */
    public void registerFailedHuntAttempt(int dinosaurId) {
        failedHuntAttemptsByDinosaurId.merge(dinosaurId, 1, Integer::sum);
    }

    /**
     * Регистрирует провал цепочки выслеживания на конкретного динозавра.
     *
     * @param dinosaurId ID динозавра, по которому сорвался след
     */
    public void registerFailedTrackingChain(int dinosaurId) {
        failedTrackingChainsByDinosaurId.merge(dinosaurId, 1, Integer::sum);
    }

    /**
     * Сбрасывает накопленные штрафы выбора после успешного результата по динозавру.
     *
     * @param dinosaurId ID динозавра, по которому больше не нужен штраф выбора
     */
    public void clearCaptureFailures(int dinosaurId) {
        failedHuntAttemptsByDinosaurId.remove(dinosaurId);
        failedTrackingChainsByDinosaurId.remove(dinosaurId);
    }

    /**
     * Возвращает число проваленных охот по конкретному динозавру.
     *
     * @param dinosaurId ID динозавра
     * @return количество провалов засады
     */
    public int failedHuntAttempts(int dinosaurId) {
        return failedHuntAttemptsByDinosaurId.getOrDefault(dinosaurId, 0);
    }

    /**
     * Возвращает число сорванных цепочек выслеживания по конкретному динозавру.
     *
     * @param dinosaurId ID динозавра
     * @return количество сорванных цепочек следов
     */
    public int failedTrackingChains(int dinosaurId) {
        return failedTrackingChainsByDinosaurId.getOrDefault(dinosaurId, 0);
    }

    /**
     * Возвращает фигурку по роли.
     *
     * @param role роль в команде игрока
     * @return объект конкретного рейнджера
     */
    public Ranger rangerFor(RangerRole role) {
        return switch (role) {
            case SCOUT -> scoutRanger;
            case DRIVER -> driverRanger;
            case ENGINEER -> engineerRanger;
            case HUNTER -> hunterRanger;
        };
    }

    /**
     * Возвращает все фигурки команды.
     *
     * @return список рейнджеров игрока
     */
    public List<Ranger> rangers() {
        return List.of(scoutRanger, engineerRanger, hunterRanger, driverRanger);
    }

    public Point positionOf(RangerRole role) {
        return rangerFor(role).position();
    }

    public void setPosition(RangerRole role, Point position) {
        rangerFor(role).setPosition(position);
    }

    public void returnTeamToBase(Point base) {
        for (Ranger ranger : rangers()) {
            ranger.setPosition(base);
        }
        activeHunt = null;
        activeTracking = null;
        rejectedHuntBaitPositions.clear();
    }


}
