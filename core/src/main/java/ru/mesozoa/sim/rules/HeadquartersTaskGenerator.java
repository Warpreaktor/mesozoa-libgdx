package ru.mesozoa.sim.rules;

import ru.mesozoa.sim.config.GameConfig;
import ru.mesozoa.sim.dinosaur.Dinosaur;
import ru.mesozoa.sim.model.DietType;
import ru.mesozoa.sim.model.SizeClass;
import ru.mesozoa.sim.model.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Генерирует случайное задание штаба для игрока.
 *
 * Базовая структура задания повторяет настольное правило текущего прототипа:
 * один любой S-динозавр, один M-травоядный и один M-хищник. Остальные цели,
 * если в конфиге задано больше трёх динозавров, добираются из доступного
 * парка без дублей. Так новые виды начинают участвовать в заданиях после
 * добавления их в {@link Species} и настройки их спаун-тайлов в {@link GameConfig}.
 */
public final class HeadquartersTaskGenerator {

    /** Игровой конфиг, из которого берутся размер задания и доступные виды. */
    private final GameConfig config;

    /**
     * Создаёт генератор заданий штаба.
     *
     * @param config игровой конфиг партии
     */
    public HeadquartersTaskGenerator(GameConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Создаёт случайное задание штаба для одного игрока.
     *
     * @param random источник случайности партии
     * @return набор видов, которых игрок должен поймать
     */
    public Set<Species> createTask(Random random) {
        Objects.requireNonNull(random, "random");

        int targetCount = Math.max(0, config.headquartersTaskDinosaurCount);
        EnumSet<Species> task = EnumSet.noneOf(Species.class);
        if (targetCount == 0) {
            return task;
        }

        List<TaskSlot> mandatorySlots = List.of(
                new TaskSlot(this::isSmallDinosaur),
                new TaskSlot(this::isMediumHerbivore),
                new TaskSlot(this::isMediumPredator)
        );

        for (TaskSlot slot : mandatorySlots) {
            if (task.size() >= targetCount) {
                return task;
            }
            pickRandomAvailable(slot.filter(), task, random).ifPresent(task::add);
        }

        List<Species> remaining = availableTaskSpecies().stream()
                .filter(species -> !task.contains(species))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(remaining, random);

        for (Species species : remaining) {
            if (task.size() >= targetCount) {
                break;
            }
            task.add(species);
        }

        return task;
    }

    /**
     * Проверяет, является ли вид любым малым динозавром для первой обязательной цели.
     *
     * @param species вид динозавра
     * @return true, если вид относится к размеру S
     */
    private boolean isSmallDinosaur(Species species) {
        return Dinosaur.sizeOf(species) == SizeClass.S;
    }

    /**
     * Проверяет, подходит ли вид под слот M-травоядного.
     *
     * @param species вид динозавра
     * @return true, если это средний травоядный динозавр
     */
    private boolean isMediumHerbivore(Species species) {
        return Dinosaur.sizeOf(species) == SizeClass.M
                && Dinosaur.dietOf(species) == DietType.HERBIVORE;
    }

    /**
     * Проверяет, подходит ли вид под слот M-хищника.
     *
     * @param species вид динозавра
     * @return true, если это средний хищник
     */
    private boolean isMediumPredator(Species species) {
        return Dinosaur.sizeOf(species) == SizeClass.M
                && Dinosaur.dietOf(species) == DietType.PREDATOR;
    }

    /**
     * Выбирает случайный вид из доступного пула с учётом уже выбранных целей.
     *
     * @param filter фильтр слота задания
     * @param alreadySelected уже выбранные цели этого игрока
     * @param random источник случайности партии
     * @return выбранный вид или пустой результат, если подходящих видов нет
     */
    private Optional<Species> pickRandomAvailable(
            Predicate<Species> filter,
            Set<Species> alreadySelected,
            Random random
    ) {
        List<Species> candidates = availableTaskSpecies().stream()
                .filter(filter)
                .filter(species -> !alreadySelected.contains(species))
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    /**
     * Возвращает виды, которые разрешено выдавать в заданиях штаба.
     *
     * Сейчас вид считается доступным, если для него в конфиге есть хотя бы один
     * спаун-тайл. Это защищает генератор от задания на динозавра, который вообще
     * не может появиться на карте в текущей настройке партии.
     *
     * @return список доступных видов
     */
    private List<Species> availableTaskSpecies() {
        return config.spawnTiles.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .map(java.util.Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Обязательный слот задания штаба. */
    private record TaskSlot(Predicate<Species> filter) {
    }
}
